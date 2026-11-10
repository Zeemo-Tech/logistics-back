package org.example.logisticsback.approval;

import java.time.LocalDateTime;

public record MaintenanceApprovalRecord(
        String id,
        String approvalId,
        String action,
        Long operatorId,
        String operatorName,
        String note,
        LocalDateTime createdAt) {}
