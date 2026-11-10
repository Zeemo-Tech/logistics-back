package org.example.logisticsback.maintenance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class WorkOrderRequests {
    private WorkOrderRequests() {}

    public record Create(
            @NotBlank String assetId,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 1000) String description,
            @NotBlank String priority,
            String faultType,
            @Size(max = 160) String faultLocation,
            Boolean impactOperations,
            String reporterDepartmentId,
            Boolean emergency,
            Boolean needsApproval,
            Boolean externalRepair,
            String vendorType,
            @Size(max = 160) String vendorName,
            @Size(max = 80) String vendorContact,
            @Size(max = 32) String vendorPhone,
            @Size(max = 80) String technician,
            @Size(max = 64) String specialty,
            @Size(max = 160) String qualification,
            @Size(max = 1000) String repairPlan,
            LocalDateTime expectedArrival,
            LocalDateTime expectedCompletion,
            BigDecimal quotedAmount,
            String secondaryOf) {
        public Create(String assetId, String title, String description, String priority) {
            this(assetId, title, description, priority, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, null, null);
        }

        public Create(String assetId, String title, String description, String priority, String faultType,
                String faultLocation, Boolean impactOperations, String reporterDepartmentId, Boolean emergency,
                Boolean needsApproval, Boolean externalRepair, String vendorType, String vendorName,
                String vendorContact, String vendorPhone, String technician, String specialty,
                String qualification, String repairPlan, LocalDateTime expectedArrival,
                LocalDateTime expectedCompletion, BigDecimal quotedAmount) {
            this(assetId, title, description, priority, faultType, faultLocation, impactOperations,
                    reporterDepartmentId, emergency, needsApproval, externalRepair, vendorType, vendorName,
                    vendorContact, vendorPhone, technician, specialty, qualification, repairPlan,
                    expectedArrival, expectedCompletion, quotedAmount, null);
        }
    }

    public record Edit(@NotNull @Min(0) Integer version,
                       @NotBlank @Size(max=120) String title,
                       @NotBlank @Size(max=1000) String description,
                       @NotBlank String priority,
                       String faultType,
                       @Size(max=160) String faultLocation,
                       Boolean impactOperations,
                       String reporterDepartmentId) {
        public Edit(Integer version, String title, String description, String priority) {
            this(version, title, description, priority, null, null, null, null);
        }
    }

    public record Action(
            @NotBlank String action,
            @NotNull @Min(0) Integer version,
            @Size(max = 80) String assignee,
            @Size(max = 1500) String notes,
            String resolutionAvailability,
            LocalDateTime expectedCompletion,
            BigDecimal laborCost,
            BigDecimal partsCost,
            Integer downtimeMinutes,
            String secondaryOf,
            BigDecimal quotedAmount,
            String repairPlan,
            LocalDateTime actualArrival,
            String siteTechnician,
            String sitePhone,
            String siteNotes) {
        public Action(String action, Integer version, String assignee, String notes,
                      String resolutionAvailability) {
            this(action, version, assignee, notes, resolutionAvailability, null, null, null, null, null,
                    null, null, null, null, null, null);
        }

        public Action(String action, Integer version, String assignee, String notes,
                      String resolutionAvailability, LocalDateTime expectedCompletion,
                      BigDecimal laborCost, BigDecimal partsCost, Integer downtimeMinutes,
                      String secondaryOf) {
            this(action, version, assignee, notes, resolutionAvailability, expectedCompletion,
                    laborCost, partsCost, downtimeMinutes, secondaryOf, null, null, null, null, null, null);
        }
    }

    public record Repair(
            @NotNull @Min(0) Integer version,
            Boolean externalRepair,
            String vendorType,
            @Size(max = 160) String vendorName,
            @Size(max = 80) String vendorContact,
            @Size(max = 32) String vendorPhone,
            @Size(max = 80) String technician,
            @Size(max = 64) String specialty,
            @Size(max = 160) String qualification,
            @Size(max = 1000) String repairPlan,
            LocalDateTime expectedArrival,
            LocalDateTime expectedCompletion,
            @DecimalMin(value = "0") BigDecimal quotedAmount,
            @DecimalMin(value = "0") BigDecimal laborCost,
            @DecimalMin(value = "0") BigDecimal partsCost,
            @Min(0) Integer downtimeMinutes) {}

    public record Part(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 160) String spec,
            @NotNull @DecimalMin(value = "0.01") BigDecimal quantity,
            @Size(max = 24) String unit,
            @NotNull @DecimalMin(value = "0") BigDecimal unitPrice,
            @Size(max = 160) String supplier,
            @Size(max = 80) String replacedPartCode,
            @Size(max = 500) String remark) {}
}
