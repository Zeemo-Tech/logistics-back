package org.example.logisticsback.approval;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record MaintenanceApproval(
        String id,
        String code,
        String businessType,
        String businessId,
        String title,
        Long applicantId,
        String applicantName,
        String departmentId,
        String departmentName,
        String assetId,
        String assetCode,
        String assetName,
        String reason,
        String repairPlan,
        BigDecimal estimatedCost,
        boolean needsShutdown,
        boolean needsOutsourcing,
        boolean emergency,
        String status,
        Long approverId,
        String approverName,
        String decisionNote,
        int version,
        LocalDateTime createdAt,
        LocalDateTime decidedAt) {}
