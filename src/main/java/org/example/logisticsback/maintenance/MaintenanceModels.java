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
            String notes, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record PlanInput(@NotBlank @Size(max=120) String name, @NotBlank String assetId,
            @NotBlank String planType, @NotBlank @Size(max=80) String frequency,
            @NotNull @Min(1) @Max(3650) Integer intervalDays, @NotNull LocalDate nextDueDate,
            @NotBlank @Size(max=80) String owner, @NotBlank @Size(max=1000) String checklist,
            @Size(max=500) String notes, @Min(0) Integer version) {}
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
            @Min(0) Integer version) {}
    public record ReadingInput(@NotNull @Min(0) Integer version,
            @NotNull @DecimalMin("0") @DecimalMax("9999999999999") BigDecimal currentReading,
            @NotNull LocalDate readDate, @NotNull LocalDate nextReadDueDate,
            @NotBlank String status, @Size(max=500) String note) {}
    public record Reading(String id, String meterId, BigDecimal previousReading, BigDecimal currentReading,
            BigDecimal multiplier, BigDecimal consumption, LocalDate readDate, LocalDate nextReadDueDate,
            String status, String note, String correctionOf, LocalDateTime createdAt) {}
}
