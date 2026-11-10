package org.example.logisticsback.maintenance;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.example.logisticsback.approval.ApprovalRequests;
import org.example.logisticsback.approval.ApprovalService;
import org.example.logisticsback.assets.AssetRepository;
import org.example.logisticsback.common.BusinessException;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static org.example.logisticsback.maintenance.MaintenanceSupport.*;
import static org.example.logisticsback.maintenance.MaintenanceModels.*;

@Service
public class PlanTaskService {
    private static final Set<String> CONFIRMABLE = Set.of("PENDING_CONFIRMATION", "REJECTED");
    private final PlanTaskRepository repository;
    private final MaintenancePlanService plans;
    private final AssetRepository assets;
    private final WorkOrderService orders;
    private final WorkOrderRepository workOrders;
    private final ApprovalService approvals;
    private final MaintenanceSupport db;

    public PlanTaskService(PlanTaskRepository repository, MaintenancePlanService plans,
                           AssetRepository assets, WorkOrderService orders,
                           WorkOrderRepository workOrders, ApprovalService approvals,
                           MaintenanceSupport db) {
        this.repository = repository;
        this.plans = plans;
        this.assets = assets;
        this.orders = orders;
        this.workOrders = workOrders;
        this.approvals = approvals;
        this.db = db;
    }

    public List<PlanTask> list(String status, String keyword) {
        return repository.list(status, keyword);
    }

    public PlanTask get(String id) {
        return repository.find(id).orElseThrow(() -> new BusinessException(404, "维保任务不存在"));
    }

    @Transactional
    public int generateDueTasks() {
        int created = 0;
        var today = LocalDate.now();
        for (var plan : plans.list()) {
            if (!"ACTIVE".equals(plan.status()) || plan.nextDueDate() == null) continue;
            var reminderDays = plan.reminderDays() == null ? 30 : plan.reminderDays();
            if (plan.nextDueDate().minusDays(reminderDays).isAfter(today)) continue;
            if (repository.existsOpen(plan.id(), plan.nextDueDate())) continue;
            var id = UUID.randomUUID().toString();
            repository.insert(id, plan.id(), plan.assetId(), plan.nextDueDate(),
                    plan.name() + " · 到期维保", plan.checklist());
            db.audit("PLAN_TASK", id, "GENERATE",
                    "系统检测到计划到期，生成维保任务：" + plan.name(), null);
            created++;
        }
        return created;
    }

    @Transactional
    public PlanTask confirm(String id, PlanTaskConfirm input, Long operator) {
        return confirm(id, input, null, operator);
    }

    @Transactional
    public PlanTask confirm(String id, PlanTaskConfirm input, Boolean needsApproval, Long operator) {
        var task = lockTask(id);
        version(input.version(), task.version());
        require(CONFIRMABLE.contains(task.status()), "当前任务不能确认，请刷新后重试");
        var plan = plans.get(task.planId());
        repository.confirm(id, input, operator);
        var needApproval = needsApproval == null ? plan.needsApproval() : needsApproval;
        if (needApproval) {
            var asset = assets.findById(task.assetId()).orElse(null);
            var approval = approvals.create(new ApprovalRequests.Create("PREVENTIVE", id, task.title(),
                    asset == null ? null : asset.departmentId(), task.assetId(), input.content().trim(),
                    plan.checklist(), input.estimatedCost(), false,
                    "EXTERNAL".equals(plan.maintenanceMethod()), false), operator);
            repository.linkApproval(id, approval.id());
            db.audit("PLAN_TASK", id, "CONFIRM", "确认维保任务并提交审批：" + approval.code(), operator);
        } else {
            createWorkOrder(task, plan, input.content().trim(), operator);
            db.audit("PLAN_TASK", id, "CONFIRM", "确认维保任务并生成维修工单", operator);
        }
        return get(id);
    }

    @Transactional
    public PlanTask submitPlan(String planId, PlanSubmit input, Long operator) {
        var plan = plans.get(planId);
        require("ACTIVE".equals(plan.status()), "暂停的计划不能登记巡检");
        var result = text(input.result()).toUpperCase();
        require(Set.of("NORMAL", "ABNORMAL").contains(result), "巡检结果不合法");
        var existing = repository.findOpenByPlan(planId).orElse(null);
        require(existing == null || "PENDING_CONFIRMATION".equals(existing.status())
                || "REJECTED".equals(existing.status()),
                "该计划已提交审批或已生成工单，请查看审批结果");
        PlanTask task = existing;
        if (task == null) {
            var id = UUID.randomUUID().toString();
            var scheduled = plan.nextDueDate() == null ? LocalDate.now() : plan.nextDueDate();
            repository.insert(id, plan.id(), plan.assetId(), scheduled,
                    plan.name() + " · 到期巡检", plan.checklist());
            task = get(id);
        }
        if ("NORMAL".equals(result)) {
            return completeNormal(task, plan, input, operator);
        }
        require(!text(input.content()).isEmpty(), "发现异常时请填写故障描述");
        repository.confirm(task.id(), new PlanTaskConfirm(task.version(), input.content(),
                input.plannedDate(), input.estimatedCost(), input.notes()), operator);
        createWorkOrder(get(task.id()), plan, input.content(), operator);
        return get(task.id());
    }

    private PlanTask completeNormal(PlanTask task, Plan plan, PlanSubmit input, Long operator) {
        var today = LocalDate.now();
        repository.confirm(task.id(), new PlanTaskConfirm(task.version(), plan.checklist(),
                input.plannedDate(), null, input.notes()), operator);
        var executor = db.jdbc.queryForObject(
                "select coalesce(nullif(real_name, ''), username, '巡检人员') from users where id = ?",
                String.class, operator);
        db.jdbc.update("""
                insert into maintenance_plan_executions (id, plan_id, scheduled_date, executor,
                    result, checklist_result, notes, work_order_id)
                values (?, ?, ?, ?, 'NORMAL', ?, ?, null)
                """, UUID.randomUUID().toString(), plan.id(), task.scheduledDate(),
                executor == null ? "巡检人员" : executor,
                text(input.checklistResult()), text(input.notes()));
        db.jdbc.update("""
                update maintenance_plans set next_due_date = ?, version = version + 1,
                    updated_at = current_timestamp where id = ?
                """, today.plusDays(plan.intervalDays()), plan.id());
        if ("PREVENTIVE".equals(plan.planType())) {
            db.jdbc.update("update assets set last_maintenance_date = ? where id = ?",
                    today, plan.assetId());
        } else {
            db.jdbc.update("update assets set last_inspection_date = ? where id = ?",
                    today, plan.assetId());
        }
        repository.setStatus(task.id(), "COMPLETED", text(input.notes()));
        db.audit("PLAN_TASK", task.id(), "INSPECT_PASS",
                "巡检正常，直接通过；下次巡检日期 " + today.plusDays(plan.intervalDays()), operator);
        db.syncDates(plan.assetId());
        return get(task.id());
    }

    @Transactional
    public PlanTask cancel(String id, PlanTaskCancel input, Long operator) {
        var task = lockTask(id);
        version(input.version(), task.version());
        require(CONFIRMABLE.contains(task.status()), "当前任务不能取消");
        repository.setStatus(id, "CANCELLED", input.reason());
        db.audit("PLAN_TASK", id, "CANCEL", "取消维保任务：" + input.reason(), operator);
        return get(id);
    }

    @EventListener
    @Transactional
    public void onApprovalDecided(PlanTaskApprovalEvent event) {
        if (event.taskId() == null || event.taskId().isBlank()) return;
        var task = repository.find(event.taskId()).orElse(null);
        if (task == null) return;
        if (event.approved()) {
            if (task.workOrderId() == null) {
                var plan = plans.get(task.planId());
                createWorkOrder(task, plan, task.content(), event.operatorId());
            }
            db.audit("PLAN_TASK", task.id(), "APPROVED", "审批通过，生成维修工单", event.operatorId());
        } else {
            repository.setStatus(task.id(), "REJECTED",
                    event.note() == null || event.note().isBlank() ? "审批驳回" : event.note());
            db.audit("PLAN_TASK", task.id(), "REJECTED",
                    "审批驳回：" + (event.note() == null ? "" : event.note()), event.operatorId());
        }
    }

    @EventListener
    @Transactional
    public void onWorkOrderCompleted(WorkOrderCompletedEvent event) {
        var task = repository.findByWorkOrder(event.workOrderId()).orElse(null);
        if (task == null || "COMPLETED".equals(task.status())) return;
        var plan = plans.get(task.planId());
        var order = workOrders.find(event.workOrderId()).orElse(null);
        var executedAt = LocalDate.now();
        db.jdbc.update("""
                insert into maintenance_plan_executions (id, plan_id, scheduled_date, executor,
                    result, checklist_result, notes, work_order_id)
                values (?, ?, ?, ?, 'NORMAL', ?, ?, ?)
                """, UUID.randomUUID().toString(), plan.id(), task.scheduledDate(),
                order == null ? "维修人员" : order.assignee(),
                order == null ? "" : order.repairNotes(),
                order == null ? "" : order.acceptanceNotes(), event.workOrderId());
        db.jdbc.update("""
                update maintenance_plans set next_due_date = ?, version = version + 1,
                    updated_at = current_timestamp where id = ?
                """, executedAt.plusDays(plan.intervalDays()), plan.id());
        if ("PREVENTIVE".equals(plan.planType())) {
            db.jdbc.update("update assets set last_maintenance_date = ? where id = ?",
                    executedAt, plan.assetId());
        } else {
            db.jdbc.update("update assets set last_inspection_date = ? where id = ?",
                    executedAt, plan.assetId());
        }
        repository.setStatus(task.id(), "COMPLETED", task.notes());
        db.audit("PLAN_TASK", task.id(), "COMPLETE",
                "维修验收通过，任务归档；下次维保日期 " + executedAt.plusDays(plan.intervalDays()),
                order == null ? null : order.reporterId());
        db.syncDates(plan.assetId());
    }

    @Scheduled(cron = "0 0 8 * * *")
    public void scheduledGenerate() {
        generateDueTasks();
    }

    private PlanTask lockTask(String id) {
        var task = repository.lock(id).orElseThrow(() -> new BusinessException(404, "维保任务不存在"));
        db.equipment(task.assetId(), false);
        return task;
    }

    private void createWorkOrder(PlanTask task, Plan plan, String content, Long operator) {
        if (task.workOrderId() != null) return;
        if (workOrders.hasOpenOrder(plan.assetId())) {
            var existing = orders.list(plan.assetId(), null, null).stream()
                    .filter(item -> !Set.of("COMPLETED", "CANCELLED", "APPROVAL_REJECTED")
                            .contains(item.status()))
                    .findFirst();
            if (existing.isPresent()) {
                repository.linkWorkOrder(task.id(), existing.get().id());
                db.audit("PLAN_TASK", task.id(), "LINK_ORDER",
                        "该设备已有未结束工单，维保任务关联现有工单 " + existing.get().code(), operator);
                return;
            }
        }
        var order = orders.create(new WorkOrderRequests.Create(plan.assetId(),
                "计划维保：" + plan.name().substring(0, Math.min(plan.name().length(), 100)),
                text(content), "NORMAL", "OPERATION", null, false, null, false, true,
                "EXTERNAL".equals(plan.maintenanceMethod()), null, null, null, null, null, null,
                null, plan.checklist(), null, null, null, null), operator);
        repository.linkWorkOrder(task.id(), order.id());
    }
}
