package org.example.logisticsback.maintenance;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class MaintenanceModels {
    private MaintenanceModels() {}

    public record Plan(String id, String code, String name, String assetId, String assetCode,
            String assetName, String category, String planType, String frequency, Integer intervalDays,
            LocalDate nextDueDate, String owner, String checklist, String status, int version,
            String notes, Integer reminderDays, String maintenanceMethod, boolean needsApproval,
            BigDecimal estimatedCost, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record PlanInput(@NotBlank @Size(max=120) String name, @NotBlank String assetId,
            @NotBlank String planType, @NotBlank @Size(max=80) String frequency,
            @NotNull @Min(1) @Max(3650) Integer intervalDays, @NotNull LocalDate nextDueDate,
            @NotBlank @Size(max=80) String owner, @NotBlank @Size(max=1000) String checklist,
            @Size(max=500) String notes, @Min(0) Integer version,
            @Min(0) Integer reminderDays, String maintenanceMethod, Boolean needsApproval,
            BigDecimal estimatedCost) {
        public PlanInput(String name, String assetId, String planType, String frequency, Integer intervalDays,
                LocalDate nextDueDate, String owner, String checklist, String notes, Integer version) {
            this(name, assetId, planType, frequency, intervalDays, nextDueDate, owner, checklist, notes,
                    version, 30, "INTERNAL", false, null);
        }
    }
    public record PlanExecute(@NotNull @Min(0) Integer version, @NotBlank @Size(max=80) String executor,
            @NotBlank String result, @NotBlank @Size(max=1000) String checklistResult,
            @NotBlank @Size(max=1000) String notes) {}
    public record Execution(String id, String planId, LocalDate scheduledDate, LocalDateTime executedAt,
            String executor, String result, String checklistResult, String notes, String workOrderId) {}
    public record StateInput(@NotNull @Min(0) Integer version, @NotBlank String status,
            @NotBlank @Size(max=500) String reason) {}
    public record Audit(String id, String action, String description, Long operatorId,
            String operatorName, LocalDateTime createdAt) {}

    public record Compliance(String id, String code, String name, String itemType, String assetId,
            String assetName, String location, String authority, String owner, LocalDate dueDate,
            String status, String reportNumber, LocalDate lastInspectionDate, LocalDate nextDueDate,
            int version, String notes, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record ComplianceInput(@NotBlank @Size(max=120) String name, @NotBlank String itemType,
            String assetId, @NotBlank @Size(max=160) String location,
            @NotBlank @Size(max=120) String authority, @NotBlank @Size(max=80) String owner,
            @NotNull LocalDate dueDate, @Size(max=500) String notes, @Min(0) Integer version) {}
    public record ComplianceAction(@NotNull @Min(0) Integer version, @NotBlank String action,
            @NotNull LocalDate eventDate, @NotBlank @Size(max=120) String authority,
            @Size(max=80) String reportNumber, LocalDate nextDueDate,
            @NotBlank @Size(max=1000) String notes) {}
    public record ComplianceEvent(String id, String itemId, String action, LocalDate eventDate,
            String authority, String reportNumber, String result, LocalDate nextDueDate,
            String notes, LocalDateTime createdAt) {}

    public record MeterInput(@NotBlank @Size(max=40) String code, @NotBlank String assetId,
            @NotBlank @Size(max=120) String name, @NotBlank String kind,
            @NotBlank @Size(max=160) String location, @NotBlank @Size(max=80) String owner,
            @NotNull @DecimalMin("0.0001") @DecimalMax("100000") BigDecimal multiplier,
            @NotNull @Min(1) @Max(366) Integer cycleDays,
            @NotNull @DecimalMin("0") @DecimalMax("9999999999999") BigDecimal initialReading,
            @NotNull LocalDate readDate, @NotNull LocalDate nextReadDueDate,
            @Min(0) Integer version,
            @Size(max=120) String area, @Size(max=160) String model, LocalDate installDate,
            LocalDate lastInspectionDate, LocalDate nextInspectionDate) {
        public MeterInput(String code, String assetId, String name, String kind, String location,
                String owner, BigDecimal multiplier, Integer cycleDays, BigDecimal initialReading,
                LocalDate readDate, LocalDate nextReadDueDate, Integer version) {
            this(code, assetId, name, kind, location, owner, multiplier, cycleDays, initialReading,
                    readDate, nextReadDueDate, version, null, null, null, null, null);
        }
    }
    public record ReadingInput(@NotNull @Min(0) Integer version,
            @NotNull @DecimalMin("0") @DecimalMax("9999999999999") BigDecimal currentReading,
            @NotNull LocalDate readDate, @NotNull LocalDate nextReadDueDate,
            @NotBlank String status, @Size(max=500) String note,
            @Size(max=255) String photoName, @Size(max=128) String photoMime, String photoBase64) {
        public ReadingInput(Integer version, BigDecimal currentReading, LocalDate readDate,
                LocalDate nextReadDueDate, String status, String note) {
            this(version, currentReading, readDate, nextReadDueDate, status, note, null, null, null);
        }
    }
    public record Reading(String id, String meterId, BigDecimal previousReading, BigDecimal currentReading,
            BigDecimal multiplier, BigDecimal consumption, LocalDate readDate, LocalDate nextReadDueDate,
            String status, String note, String correctionOf, LocalDateTime createdAt,
            String photoName, String photoMime) {}
    public record ReadingPhoto(String name, String mimeType, String base64) {}

    public record PlanTask(String id, String planId, String planCode, String planName, String planType,
            String assetId, String assetCode, String assetName, LocalDate scheduledDate, String title,
            String content, LocalDate plannedDate, BigDecimal estimatedCost, String status,
            boolean needsApproval, String maintenanceMethod,
            String approvalId, String workOrderId, Long confirmedBy, LocalDateTime confirmedAt,
            int version, String notes, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record PlanTaskConfirm(@NotNull @Min(0) Integer version,
            @NotBlank @Size(max=1000) String content, @NotNull LocalDate plannedDate,
            @DecimalMin("0") BigDecimal estimatedCost, @Size(max=1000) String notes) {}
    public record PlanTaskCancel(@NotNull @Min(0) Integer version,
            @NotBlank @Size(max=500) String reason) {}
    public record PlanSubmit(@NotBlank String result, @Size(max=1000) String content,
            @Size(max=1000) String checklistResult, @NotNull LocalDate plannedDate,
            @DecimalMin("0") BigDecimal estimatedCost, @Size(max=1000) String notes) {}
}
