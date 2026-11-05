package org.example.logisticsback.assets;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AssetRecord(
        String id,
        String code,
        String kind,
        String name,
        String category,
        String ownership,
        String ownerName,
        String site,
        String location,
        String custodian,
        String lifecycleStatus,
        String availabilityStatus,
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
        LocalDate maintenanceDueDate,
        LocalDate inspectionDueDate,
        String lastSeen,
        String notes,
        String manufacturer,
        String equipmentModel,
        String serialNumber,
        LocalDate commissionedDate,
        String serviceProvider,
        String maintenanceCycle,
        String subcategory,
        LocalDate warrantyExpiry,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
