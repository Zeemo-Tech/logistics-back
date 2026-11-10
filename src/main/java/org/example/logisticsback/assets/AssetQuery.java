package org.example.logisticsback.assets;

public record AssetQuery(
        String kind,
        String keyword,
        String category,
        String departmentId,
        String categoryId,
        String subcategory,
        String lifecycleStatus,
        String availabilityStatus,
        String warningLevel) {

    public AssetQuery(String kind, String keyword, String category,
                      String lifecycleStatus, String availabilityStatus) {
        this(kind, keyword, category, null, null, null, lifecycleStatus, availabilityStatus, null);
    }
}
