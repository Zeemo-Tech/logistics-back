package org.example.logisticsback.maintenance;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UtilityMeter(
        String id,
        String code,
        String assetId,
        String name,
        String kind,
        String location,
        BigDecimal currentReading,
        BigDecimal previousReading,
        String unit,
        LocalDate readDate,
        LocalDate nextReadDueDate,
        String status, BigDecimal multiplier, String owner, String lifecycleStatus,
        int version, int cycleDays, BigDecimal initialReading, long readingCount,
        BigDecimal consumption, String area, String model, LocalDate installDate,
        LocalDate lastInspectionDate, LocalDate nextInspectionDate) {}
