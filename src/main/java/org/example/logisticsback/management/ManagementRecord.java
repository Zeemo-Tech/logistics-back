package org.example.logisticsback.management;

public record ManagementRecord(
        String id,
        String type,
        String name,
        String category,
        String operator,
        String region,
        String status,
        String metric,
        String contact,
        String updatedAt) {}
