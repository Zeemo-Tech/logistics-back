package org.example.logisticsback.maintenance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.example.logisticsback.assets.AssetQuery;
import org.example.logisticsback.assets.AssetRecord;
import org.example.logisticsback.assets.AssetRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class StatisticsService {
    private static final Set<String> CLOSED = Set.of("COMPLETED", "CANCELLED");
    private final JdbcTemplate jdbc;
    private final AssetRepository assets;
    private final WorkOrderRepository orders;

    public StatisticsService(JdbcTemplate jdbc, AssetRepository assets, WorkOrderRepository orders) {
        this.jdbc = jdbc;
        this.assets = assets;
        this.orders = orders;
    }

    public MaintenanceStatistics overview() {
        var equipment = assets.search(new AssetQuery("EQUIPMENT", null, null, null, null));
        var allOrders = orders.search(null, null, null);
        var today = LocalDate.now();

        long active = equipment.stream().filter(a -> "ACTIVE".equals(a.lifecycleStatus())).count();
        long running = equipment.stream().filter(a -> "RUNNING".equals(a.runStatus())).count();
        long abnormal = equipment.stream().filter(a -> "ABNORMAL".equals(a.runStatus())).count();
        long fault = equipment.stream().filter(a -> "FAULT".equals(a.runStatus())).count();
        long repairing = equipment.stream().filter(a -> "REPAIRING".equals(a.runStatus())).count();
        long retired = equipment.stream().filter(a -> "RETIRED".equals(a.lifecycleStatus())).count();
        long pendingApprovals = count("select count(*) from maintenance_approvals where status = 'PENDING'");
        long openOrders = allOrders.stream().filter(o -> !CLOSED.contains(o.status())).count();
        long overdueWarnings = equipment.stream().filter(a -> "OVERDUE".equals(a.warningLevel())).count();

        var completed = allOrders.stream().filter(o -> "COMPLETED".equals(o.status())).toList();
        var labor = completed.stream().map(WorkOrder::laborCost).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var parts = completed.stream().map(WorkOrder::partsCost).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var total = completed.stream().map(WorkOrder::totalCost).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() == 0) total = labor.add(parts);
        var avg = completed.isEmpty() ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(completed.size()), 2, RoundingMode.HALF_UP);

        var statusCounts = allOrders.stream()
                .collect(Collectors.groupingBy(WorkOrder::status, LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream().map(e -> new MaintenanceStatistics.CountItem(e.getKey(), e.getValue())).toList();
        var faultTypes = allOrders.stream().filter(o -> o.faultType() != null && !o.faultType().isBlank())
                .collect(Collectors.groupingBy(WorkOrder::faultType, LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream().map(e -> new MaintenanceStatistics.CountItem(e.getKey(), e.getValue())).toList();

        var trend = new ArrayList<MaintenanceStatistics.TrendItem>();
        for (int i = 5; i >= 0; i--) {
            var month = YearMonth.from(today).minusMonths(i);
            var monthOrders = allOrders.stream().filter(o -> o.createdAt() != null
                    && YearMonth.from(o.createdAt()).equals(month)).toList();
            var monthCost = monthOrders.stream().filter(o -> "COMPLETED".equals(o.status()))
                    .map(WorkOrder::totalCost).filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            trend.add(new MaintenanceStatistics.TrendItem(month.toString(), monthOrders.size(), monthCost));
        }

        var equipmentById = equipment.stream().collect(Collectors.toMap(AssetRecord::id, a -> a, (a, b) -> a));
        var categoryEquipment = equipment.stream().collect(Collectors.groupingBy(
                a -> nameOr(a.categoryGroupName(), "未分类"), LinkedHashMap::new, Collectors.counting()));
        var categoryRepairs = allOrders.stream().filter(o -> equipmentById.containsKey(o.assetId()))
                .collect(Collectors.groupingBy(o -> nameOr(equipmentById.get(o.assetId()).categoryGroupName(), "未分类"),
                        LinkedHashMap::new, Collectors.counting()));
        var categoryCost = completed.stream().filter(o -> equipmentById.containsKey(o.assetId()))
                .collect(Collectors.groupingBy(o -> nameOr(equipmentById.get(o.assetId()).categoryGroupName(), "未分类"),
                        LinkedHashMap::new, Collectors.reducing(BigDecimal.ZERO,
                                o -> o.totalCost() == null ? BigDecimal.ZERO : o.totalCost(), BigDecimal::add)));
        var categoryStats = categoryEquipment.entrySet().stream().map(e -> new MaintenanceStatistics.CategoryStat(
                e.getKey(), e.getValue(), categoryRepairs.getOrDefault(e.getKey(), 0L),
                categoryCost.getOrDefault(e.getKey(), BigDecimal.ZERO))).toList();

        var departmentEquipment = equipment.stream().collect(Collectors.groupingBy(
                a -> nameOr(a.departmentName(), "未分配"), LinkedHashMap::new, Collectors.counting()));
        var departmentOpen = allOrders.stream().filter(o -> !CLOSED.contains(o.status())
                        && equipmentById.containsKey(o.assetId()))
                .collect(Collectors.groupingBy(o -> nameOr(equipmentById.get(o.assetId()).departmentName(), "未分配"),
                        LinkedHashMap::new, Collectors.counting()));
        var departmentCost = completed.stream().filter(o -> equipmentById.containsKey(o.assetId()))
                .collect(Collectors.groupingBy(o -> nameOr(equipmentById.get(o.assetId()).departmentName(), "未分配"),
                        LinkedHashMap::new, Collectors.reducing(BigDecimal.ZERO,
                                o -> o.totalCost() == null ? BigDecimal.ZERO : o.totalCost(), BigDecimal::add)));
        var departmentStats = departmentEquipment.entrySet().stream().map(e -> new MaintenanceStatistics.DepartmentStat(
                e.getKey(), e.getValue(), departmentOpen.getOrDefault(e.getKey(), 0L),
                departmentCost.getOrDefault(e.getKey(), BigDecimal.ZERO))).toList();

        long recentCompleted = completed.stream().filter(o -> o.completedAt() != null
                && o.completedAt().isAfter(LocalDateTime.now().minusDays(30))).count();
        double failureRate = active == 0 ? 0 : round(recentCompleted * 100.0 / active);
        double integrityRate = active == 0 ? 0 : round(running * 100.0 / active);
        double mttr = completed.stream().map(WorkOrder::downtimeMinutes)
                .filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0);
        double planCompletionRate = planCompletionRate();

        var metrics = new MaintenanceStatistics.Metrics(equipment.size(), active, running, abnormal, fault,
                repairing, retired, pendingApprovals, openOrders, overdueWarnings);
        return new MaintenanceStatistics(metrics, statusCounts, faultTypes, trend, categoryStats, departmentStats,
                total, labor, parts, avg, failureRate, integrityRate, planCompletionRate, round(mttr));
    }

    private double planCompletionRate() {
        long activePlans = count("select count(*) from maintenance_plans where status = 'ACTIVE'");
        if (activePlans == 0) return 0;
        long executed = count("""
                select count(distinct plan_id) from maintenance_plan_executions
                where executed_at >= ?
                """, LocalDateTime.now().minusDays(30));
        return round(executed * 100.0 / activePlans);
    }

    private long count(String sql, Object... params) {
        Long value = jdbc.queryForObject(sql, Long.class, params);
        return value == null ? 0 : value;
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static String nameOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
