package org.example.logisticsback.maintenance;

import java.math.BigDecimal;
import java.util.List;

public record MaintenanceStatistics(
        Metrics metrics,
        List<CountItem> statusCounts,
        List<CountItem> faultTypes,
        List<TrendItem> monthlyTrend,
        List<CategoryStat> categoryStats,
        List<DepartmentStat> departmentStats,
        BigDecimal totalCost,
        BigDecimal laborCost,
        BigDecimal partsCost,
        BigDecimal avgCost,
        double failureRate,
        double integrityRate,
        double planCompletionRate,
        double mttrMinutes) {

    public record Metrics(
            long equipmentTotal,
            long activeEquipment,
            long running,
            long abnormal,
            long fault,
            long repairing,
            long retired,
            long pendingApprovals,
            long openWorkOrders,
            long overdueWarnings) {}

    public record CountItem(String key, long count) {}

    public record TrendItem(String month, long count, BigDecimal cost) {}

    public record CategoryStat(String categoryName, long equipmentCount, long repairCount, BigDecimal cost) {}

    public record DepartmentStat(String departmentName, long equipmentCount, long openOrders, BigDecimal cost) {}
}
