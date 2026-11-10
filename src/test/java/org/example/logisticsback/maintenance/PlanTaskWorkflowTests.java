package org.example.logisticsback.maintenance;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.example.logisticsback.approval.ApprovalRequests;
import org.example.logisticsback.approval.ApprovalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PlanTaskWorkflowTests {
    @Autowired private PlanTaskService tasks;
    @Autowired private MaintenancePlanService plans;
    @Autowired private ApprovalService approvals;
    @Autowired private WorkOrderService orders;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void misCreatedPlanWithPendingTaskCanBeDeleted() {
        var plan = plans.list().stream().filter(p -> "ACTIVE".equals(p.status())).findFirst().orElseThrow();
        var today = LocalDate.now();
        jdbc.update("delete from maintenance_plan_tasks where plan_id = ?", plan.id());
        jdbc.update("update maintenance_plans set next_due_date = ?, reminder_days = 30 where id = ?",
                today, plan.id());
        tasks.generateDueTasks();
        var current = plans.get(plan.id());
        plans.delete(plan.id(), current.version(), 1L);
        assertTrue(plans.list().stream().noneMatch(item -> item.id().equals(plan.id())));
    }

    @Test
    void normalInspectionPassesWithoutApprovalOrWorkOrder() {
        var plan = plans.list().stream().filter(p -> "ACTIVE".equals(p.status())).findFirst().orElseThrow();
        var today = LocalDate.now();
        jdbc.update("update maintenance_plan_tasks set status = 'CANCELLED' where plan_id = ?", plan.id());
        var task = tasks.submitPlan(plan.id(), new MaintenanceModels.PlanSubmit("NORMAL",
                "巡检正常", "各项检查正常", today, null, "无需维修"), 1L);
        assertEquals("COMPLETED", task.status());
        assertNull(task.approvalId());
        assertNull(task.workOrderId());
        assertTrue(plans.get(plan.id()).nextDueDate().isAfter(today));
        Integer executions = jdbc.queryForObject(
                "select count(*) from maintenance_plan_executions where plan_id = ? and result = 'NORMAL'",
                Integer.class, plan.id());
        assertEquals(1, executions);
    }

    @Test
    void abnormalInspectionCreatesRepairWorkOrderAwaitingApproval() {
        var plan = plans.list().stream().filter(p -> "ACTIVE".equals(p.status())).findFirst().orElseThrow();
        var today = LocalDate.now();
        jdbc.update("update maintenance_plan_tasks set status = 'CANCELLED' where plan_id = ?", plan.id());
        var task = tasks.submitPlan(plan.id(), new MaintenanceModels.PlanSubmit("ABNORMAL",
                "巡检发现异响，需要维修", "检查记录", today, new BigDecimal("600"), "转维修"), 1L);
        assertEquals("APPROVED", task.status());
        assertNotNull(task.workOrderId());
        var order = orders.get(task.workOrderId());
        assertEquals("PENDING_APPROVAL", order.status());
        assertNotNull(order.approvalId());
    }

    @Test
    void abnormalInspectionLinksToExistingOpenWorkOrder() {
        var plan = plans.list().stream().filter(p -> "ACTIVE".equals(p.status())).findFirst().orElseThrow();
        var today = LocalDate.now();
        jdbc.update("update maintenance_plan_tasks set status = 'CANCELLED' where plan_id = ?", plan.id());
        var existing = orders.create(new WorkOrderRequests.Create(plan.assetId(),
                "现场抢修", "已有未结束工单", "URGENT"), 1L);
        var submitted = tasks.submitPlan(plan.id(), new MaintenanceModels.PlanSubmit("ABNORMAL",
                "计划维保", "检查记录", today, new BigDecimal("300"), ""), 1L);
        assertEquals("APPROVED", submitted.status());
        assertEquals(existing.id(), submitted.workOrderId());
    }

    @Test
    void planBudgetIsNotCopiedToWorkOrderActualCost() {
        var plan = plans.list().stream().filter(p -> "ACTIVE".equals(p.status())).findFirst().orElseThrow();
        var today = LocalDate.now();
        jdbc.update("update maintenance_plan_tasks set status = 'CANCELLED' where plan_id = ?", plan.id());
        var task = tasks.submitPlan(plan.id(), new MaintenanceModels.PlanSubmit("ABNORMAL",
                "巡检异常，需要维修", "检查记录", today, new BigDecimal("456.78"), "预算参考"), 1L);
        assertEquals("APPROVED", task.status());
        assertNotNull(task.workOrderId());
        assertEquals(0, new BigDecimal("456.78").compareTo(task.estimatedCost()));
        var order = orders.get(task.workOrderId());
        assertNull(order.quotedAmount());
    }

    @Test
    void duePlanCanStillUseLegacyConfirmApprovalFlow() {
        var plan = plans.list().stream().filter(p -> "ACTIVE".equals(p.status())).findFirst().orElseThrow();
        var today = LocalDate.now();
        jdbc.update("update maintenance_plan_tasks set status = 'CANCELLED'");
        jdbc.update("update maintenance_plans set next_due_date = ? where id = ?", today.plusDays(365), plan.id());
        jdbc.update("""
                update maintenance_plans set next_due_date = ?, reminder_days = 30, needs_approval = true,
                    estimated_cost = 800 where id = ?
                """, today, plan.id());
        assertTrue(tasks.generateDueTasks() >= 1);
        var task = tasks.list("PENDING_CONFIRMATION", null).stream()
                .filter(item -> item.planId().equals(plan.id())).findFirst().orElseThrow();
        var confirmed = tasks.confirm(task.id(), new MaintenanceModels.PlanTaskConfirm(task.version(),
                "确认维保内容与预算", today.plusDays(1), new BigDecimal("800"), "按计划执行"), 1L);
        assertEquals("PENDING_APPROVAL", confirmed.status());
        var planApproval = approvals.get(confirmed.approvalId());
        approvals.act(planApproval.id(), new ApprovalRequests.Action("APPROVE", planApproval.version(),
                null, "同意维保"), 1L);
        var approved = tasks.get(task.id());
        assertEquals("APPROVED", approved.status());
        assertNotNull(approved.workOrderId());
        var order = orders.get(approved.workOrderId());
        assertEquals("PENDING_APPROVAL", order.status());
        var workOrderApproval = approvals.get(order.approvalId());
        approvals.act(workOrderApproval.id(), new ApprovalRequests.Action("APPROVE",
                workOrderApproval.version(), null, "同意维修"), 1L);
        var dispatchable = orders.get(order.id());
        orders.act(dispatchable.id(), new WorkOrderRequests.Action("DISPATCH",
                dispatchable.version(), "维保班组", "派单", null), 1L);
    }
}
