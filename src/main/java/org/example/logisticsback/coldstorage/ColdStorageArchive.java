package org.example.logisticsback.coldstorage;

public record ColdStorageArchive(
        long id,
        String operator,
        String name,
        String storageType,
        String grade,
        String device,
        String region) {}
