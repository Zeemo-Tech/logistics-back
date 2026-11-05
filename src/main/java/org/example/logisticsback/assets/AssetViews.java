package org.example.logisticsback.assets;

import java.math.BigDecimal;
import java.util.List;

/** Read models keep overview, ledger, and binding queries independent. */
public final class AssetViews {
    private AssetViews() {}

    public record Option(String id, String code, String name, String kind) {}
    public record Page(List<AssetRecord> records, long total, int page, int pageSize,
                       List<Option> linkedAssets) {}
    public record Relations(Option linkedAsset, List<Option> relatedAssets) {}
    public record Metrics(long activeCount, long availableCount, long maintenanceCount,
                          long deviceIssueCount) {}
    public record KindSummary(String kind, long count) {}
    public record CapacitySummary(String kind, BigDecimal total, BigDecimal used, String unit) {}
    public record Overview(Metrics metrics, List<KindSummary> kindSummaries,
                           List<CapacitySummary> capacitySummaries, List<AssetRecord> attentionAssets) {}
}
