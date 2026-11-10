package org.example.logisticsback.assets;

public record EquipmentCategory(
        String id,
        String parentId,
        int level,
        String code,
        String name,
        int sortOrder,
        String status) {}
