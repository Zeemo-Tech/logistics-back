package org.example.logisticsback.assets;

public record AssetQuery(String kind, String keyword, String category,
                         String lifecycleStatus, String availabilityStatus) {}
