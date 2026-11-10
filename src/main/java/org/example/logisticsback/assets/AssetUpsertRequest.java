package org.example.logisticsback.assets;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AssetUpsertRequest(
        @NotBlank String kind,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 64) String category,
        @NotBlank String ownership,
        @NotBlank @Size(max = 120) String ownerName,
        @NotBlank @Size(max = 120) String site,
        @NotBlank @Size(max = 160) String location,
        @NotBlank @Size(max = 80) String custodian,
        @NotBlank String lifecycleStatus,
        @NotBlank String availabilityStatus,
        String conditionStatus,
        String linkedAssetId,
        BigDecimal capacityTotal,
        BigDecimal capacityUsed,
        String capacityUnit,
        BigDecimal temperatureMin,
        BigDecimal temperatureMax,
        BigDecimal batteryPercent,
        BigDecimal ratedLoadTons,
        LocalDate insuranceExpiry,
        String lastSeen,
        @Size(max = 500) String notes,
        @Size(max = 120) String manufacturer,
        @Size(max = 120) String equipmentModel,
        @Size(max = 120) String serialNumber,
        LocalDate commissionedDate,
        @Size(max = 120) String serviceProvider,
        @Size(max = 80) String maintenanceCycle,
        @Size(max = 64) String subcategory,
        LocalDate warrantyExpiry,
        LocalDate maintenanceDueDate,
        LocalDate inspectionDueDate,
        String departmentId,
        String categoryId,
        LocalDate purchaseDate,
        LocalDate installDate,
        Integer serviceLifeYears,
        LocalDate expectedRetirementDate,
        LocalDate maintenanceContractExpiry,
        String runStatus,
        Integer warningDays,
        Integer criticalWarningDays,
        @Size(max = 80) String responsiblePerson,
        @Size(max = 80) String maintenanceContact,
        @Size(max = 32) String maintenancePhone,
        @Size(max = 120) String warehouse,
        @Size(max = 120) String building,
        @Size(max = 64) String floor,
        LocalDate lastMaintenanceDate,
        LocalDate lastInspectionDate,
        @Size(max = 160) String specification) {

    public AssetUpsertRequest(String kind, String name, String category, String ownership, String ownerName,
            String site, String location, String custodian, String lifecycleStatus, String availabilityStatus,
            String conditionStatus, String linkedAssetId, BigDecimal capacityTotal, BigDecimal capacityUsed,
            String capacityUnit, BigDecimal temperatureMin, BigDecimal temperatureMax, BigDecimal batteryPercent,
            BigDecimal ratedLoadTons, LocalDate insuranceExpiry, String lastSeen, String notes) {
        this(kind,name,category,ownership,ownerName,site,location,custodian,lifecycleStatus,availabilityStatus,
            conditionStatus,linkedAssetId,capacityTotal,capacityUsed,capacityUnit,temperatureMin,temperatureMax,
            batteryPercent,ratedLoadTons,insuranceExpiry,lastSeen,notes,null,null,null,null,null,null,null,null,null,null,
            null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,null);
    }
}
