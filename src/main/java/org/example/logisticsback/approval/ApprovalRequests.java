package org.example.logisticsback.approval;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class ApprovalRequests {
    private ApprovalRequests() {}

    public record Create(
            @NotBlank String businessType,
            String businessId,
            @NotBlank @Size(max = 160) String title,
            String departmentId,
            String assetId,
            @NotBlank @Size(max = 1500) String reason,
            @Size(max = 1500) String repairPlan,
            BigDecimal estimatedCost,
            Boolean needsShutdown,
            Boolean needsOutsourcing,
            Boolean emergency) {}

    public record Action(
            @NotBlank String action,
            @NotNull @Min(0) Integer version,
            Long approverId,
            @Size(max = 1000) String note) {}
}
