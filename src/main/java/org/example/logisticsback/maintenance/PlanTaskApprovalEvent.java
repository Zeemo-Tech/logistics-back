package org.example.logisticsback.maintenance;

public record PlanTaskApprovalEvent(
        String taskId,
        String approvalId,
        boolean approved,
        String note,
        Long operatorId) {}
