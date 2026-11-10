package org.example.logisticsback.approval;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.example.logisticsback.common.BusinessException;
import org.example.logisticsback.maintenance.PlanTaskApprovalEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovalService {
    private static final Set<String> BUSINESS_TYPES = Set.of(
            "WORK_ORDER", "PREVENTIVE", "PURCHASE", "OTHER");
    private static final Set<String> ACTIONS = Set.of("APPROVE", "REJECT", "TRANSFER", "CANCEL", "SUPPLEMENT");
    private final ApprovalRepository repository;
    private final ApplicationEventPublisher events;

    public ApprovalService(ApprovalRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public List<MaintenanceApproval> list(String status, String businessType, String keyword) {
        return repository.search(status, businessType, keyword);
    }

    public MaintenanceApproval get(String id) {
        return repository.find(id).orElseThrow(() -> new BusinessException(404, "审批记录不存在"));
    }

    public List<MaintenanceApprovalRecord> records(String id) {
        get(id);
        return repository.records(id);
    }

    @Transactional
    public MaintenanceApproval create(ApprovalRequests.Create input, Long operatorId) {
        if (!BUSINESS_TYPES.contains(input.businessType())) {
            throw new BusinessException(400, "审批业务类型不合法");
        }
        if (input.estimatedCost() != null && input.estimatedCost().signum() < 0) {
            throw new BusinessException(400, "预计费用不能为负数");
        }
        var name = repository.operatorName(operatorId);
        var id = UUID.randomUUID().toString();
        var code = "AP-" + id.substring(0, 12).toUpperCase();
        repository.insert(id, code, input, operatorId, name);
        repository.addRecord(id, "SUBMIT", operatorId, name, "提交审批：" + input.reason().trim());
        if ("WORK_ORDER".equals(input.businessType())
                && input.businessId() != null && !input.businessId().isBlank()) {
            repository.linkWorkOrder(input.businessId(), id, "PENDING",
                    Boolean.TRUE.equals(input.emergency()) ? "IN_PROGRESS" : "PENDING_APPROVAL");
        }
        return get(id);
    }

    @Transactional
    public MaintenanceApproval act(String id, ApprovalRequests.Action input, Long operatorId) {
        var approval = get(id);
        if (input.version() == null || input.version() != approval.version()) {
            throw new BusinessException(409, "审批记录已更新，请刷新后重试");
        }
        if (!ACTIONS.contains(input.action())) {
            throw new BusinessException(400, "不支持的审批操作");
        }
        var operatorName = repository.operatorName(operatorId);
        var note = input.note() == null ? "" : input.note().trim();
        switch (input.action()) {
            case "APPROVE" -> {
                require("PENDING", approval.status(), "仅待审批记录可以同意");
                repository.transition(id, "APPROVED", operatorId, operatorName, note);
                repository.addRecord(id, "APPROVE", operatorId, operatorName, note);
                if (isWorkOrder(approval)) {
                    if (approval.emergency()) {
                        repository.updateApprovalOnly(approval.businessId(), id, "APPROVED");
                    } else {
                        repository.linkWorkOrder(approval.businessId(), id, "APPROVED", "PENDING_DISPATCH");
                    }
                }
                if (isPreventive(approval)) {
                    events.publishEvent(new PlanTaskApprovalEvent(approval.businessId(), id, true, note, operatorId));
                }
            }
            case "REJECT" -> {
                require("PENDING", approval.status(), "仅待审批记录可以驳回");
                if (note.isBlank()) throw new BusinessException(400, "请填写驳回原因");
                repository.transition(id, "REJECTED", operatorId, operatorName, note);
                repository.addRecord(id, "REJECT", operatorId, operatorName, note);
                if (isWorkOrder(approval)) {
                    repository.linkWorkOrder(approval.businessId(), id, "REJECTED", "APPROVAL_REJECTED");
                }
                if (isPreventive(approval)) {
                    events.publishEvent(new PlanTaskApprovalEvent(approval.businessId(), id, false, note, operatorId));
                }
            }
            case "TRANSFER" -> {
                require("PENDING", approval.status(), "仅待审批记录可以转交");
                if (input.approverId() == null) throw new BusinessException(400, "请选择转交的审批人");
                var approverName = repository.operatorName(input.approverId());
                repository.transition(id, "PENDING", input.approverId(), approverName,
                        note.isBlank() ? "转交给 " + approverName : note);
                repository.addRecord(id, "TRANSFER", operatorId, operatorName,
                        "转交给 " + approverName + (note.isBlank() ? "" : "：" + note));
            }
            case "CANCEL" -> {
                require("PENDING", approval.status(), "仅待审批记录可以撤销");
                if (note.isBlank()) throw new BusinessException(400, "请填写撤销原因");
                repository.transition(id, "CANCELLED", operatorId, operatorName, note);
                repository.addRecord(id, "CANCEL", operatorId, operatorName, note);
                if (isWorkOrder(approval)) {
                    repository.linkWorkOrder(approval.businessId(), id, "CANCELLED", "CANCELLED");
                }
            }
            case "SUPPLEMENT" -> {
                require("APPROVED".equals(approval.status()) && approval.emergency(),
                        "仅紧急抢修工单可以补充审批记录");
                repository.addRecord(id, "SUPPLEMENT", operatorId, operatorName,
                        note.isBlank() ? "补充审批记录" : note);
            }
            default -> throw new BusinessException(400, "不支持的审批操作");
        }
        return get(id);
    }

    private static boolean isPreventive(MaintenanceApproval approval) {
        return "PREVENTIVE".equals(approval.businessType())
                && approval.businessId() != null && !approval.businessId().isBlank();
    }

    private static boolean isWorkOrder(MaintenanceApproval approval) {
        return "WORK_ORDER".equals(approval.businessType())
                && approval.businessId() != null && !approval.businessId().isBlank();
    }

    private static void require(String expected, String actual, String message) {
        if (!expected.equals(actual)) throw new BusinessException(400, message);
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new BusinessException(400, message);
    }
}
