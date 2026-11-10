package org.example.logisticsback.assets;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.HashSet;
import org.example.logisticsback.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AssetReadModelTests {
    @Autowired private AssetService service;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void ledgerFiltersAndPaginatesInDatabaseWithLinksOutsideCurrentPage() {
        var query = new AssetQuery("DEVICE", null, null, "ACTIVE", null);
        var first = service.ledger(query, 1, 2);
        var second = service.ledger(query, 2, 2);
        assertEquals(5, first.total());
        assertEquals(2, first.records().size());
        assertEquals(2, second.records().size());
        var ids = new HashSet<String>();
        first.records().forEach(asset -> assertTrue(ids.add(asset.id())));
        second.records().forEach(asset -> assertTrue(ids.add(asset.id())));
        assertTrue(first.records().stream().allMatch(asset -> asset.kind().equals("DEVICE")));
        for (var asset : first.records()) {
            assertTrue(first.linkedAssets().stream().anyMatch(link -> link.id().equals(asset.linkedAssetId())));
        }
        var filtered = service.ledger(new AssetQuery("DEVICE", "库外", "温湿度设备", "ACTIVE", "IN_USE"), 1, 10);
        assertEquals(1, filtered.total());
        assertEquals("TH-88391", filtered.records().get(0).id());
        assertEquals(service.export(new AssetQuery("DEVICE", "库外", "温湿度设备", "ACTIVE", "IN_USE")).rows().size(), filtered.total());
        assertTrue(service.ledger(query, 100, 10).records().isEmpty());
        assertEquals(0, service.ledger(new AssetQuery("DEVICE", "不存在的关键字", null, null, null), 1, 10).total());
    }

    @Test
    void overviewPreservesLifecycleRulesAndBoundsAttentionList() {
        // Isolate the attention assertion from seed dates and the current calendar date.
        jdbc.update("update assets set maintenance_due_date = null, inspection_due_date = null, warranty_expiry = null, maintenance_contract_expiry = null, expected_retirement_date = null");
        jdbc.update("update assets set maintenance_due_date = ? where id = 'FAC-BC-002'",
                java.time.LocalDate.now().minusDays(1));
        var baseline = service.overview().metrics();
        jdbc.update("update assets set lifecycle_status = 'INACTIVE' where id = 'WH-BC-002'");
        jdbc.update("update assets set availability_status = 'MAINTENANCE' where id = 'MC-001'");
        var overview = service.overview();
        assertEquals(baseline.activeCount() - 1, overview.metrics().activeCount());
        assertEquals(baseline.availableCount(), overview.metrics().availableCount());
        assertEquals(1, overview.metrics().maintenanceCount());
        assertEquals(1, overview.metrics().deviceIssueCount());
        var warehouse = overview.capacitySummaries().stream().filter(item -> item.kind().equals("WAREHOUSE")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("8600.00"), warehouse.total());
        assertEquals(2, overview.kindSummaries().stream().filter(item -> item.kind().equals("WAREHOUSE")).findFirst().orElseThrow().count());
        assertTrue(overview.attentionAssets().size() <= 12);
        assertTrue(overview.attentionAssets().stream().anyMatch(asset -> asset.id().equals("FAC-BC-002")));
        jdbc.update("update assets set availability_status = 'MAINTENANCE' where lifecycle_status = 'ACTIVE'");
        assertEquals(12, service.overview().attentionAssets().size());
    }

    @Test
    void equipmentLedgerCombinesPhysicalAndMonitoringEquipmentWithoutDuplicateRecords() {
        var query = new AssetQuery("EQUIPMENT", null, null, null, null);
        var equipment = service.ledger(query, 1, 100);
        assertEquals(35, equipment.total());
        assertEquals(35, equipment.records().stream().map(AssetRecord::id).distinct().count());
        assertTrue(equipment.records().stream().anyMatch(asset -> asset.id().equals("CAM-3318")));
        assertTrue(equipment.records().stream().anyMatch(asset -> asset.category().equals("电梯")));
        assertTrue(equipment.records().stream().allMatch(asset -> asset.equipmentModel() != null
                && asset.manufacturer() != null && asset.commissionedDate() != null
                && asset.serviceProvider() != null && asset.maintenanceCycle() != null));
        var water = new AssetQuery("EQUIPMENT", null, "水电设施", "ACTIVE", null);
        assertEquals(service.ledger(water, 1, 100).total(), service.export(water).rows().size());
        var camera = service.ledger(new AssetQuery("EQUIPMENT", "CAM-3318", null, null, null), 1, 10);
        assertEquals(1, camera.total());
        assertEquals("CS-BC-001", service.get("CAM-3318").linkedAssetId());
        var table = service.export(query);
        assertTrue(table.headers().contains("下次检验"));
        assertEquals(table.headers().size(), table.rows().get(0).size());
    }

    @Test
    void attentionIncludesTodayOverdueAndThirtyDayBoundaryButExcludesFutureAndInactive() {
        jdbc.update("update assets set maintenance_due_date = null, inspection_due_date = null, warranty_expiry = null, maintenance_contract_expiry = null, expected_retirement_date = null");
        var today = java.time.LocalDate.now();
        jdbc.update("update assets set maintenance_due_date = ? where id = 'FAC-BC-002'", today.plusDays(60));
        jdbc.update("update assets set inspection_due_date = ? where id = 'FAC-BC-002'", today.minusDays(1));
        jdbc.update("update assets set maintenance_due_date = ? where id = 'FAC-BC-003'", today);
        jdbc.update("update assets set inspection_due_date = ? where id = 'FAC-BC-004'", today.plusDays(30));
        jdbc.update("update assets set maintenance_due_date = ? where id = 'FAC-BC-005'", today.plusDays(31));
        jdbc.update("update assets set lifecycle_status = 'INACTIVE', maintenance_due_date = ? where id = 'FAC-BC-006'", today.minusDays(5));
        var ids = service.overview().attentionAssets().stream().map(AssetRecord::id).toList();
        assertTrue(ids.containsAll(java.util.List.of("FAC-BC-002", "FAC-BC-003", "FAC-BC-004")));
        assertFalse(ids.contains("FAC-BC-005"));
        assertFalse(ids.contains("FAC-BC-006"));
        assertTrue(ids.indexOf("FAC-BC-002") < ids.indexOf("FAC-BC-003"));
    }

    @Test
    void overviewHandlesAnEmptyAssetRegister() {
        jdbc.update("delete from maintenance_work_order_events");
        jdbc.update("delete from maintenance_work_orders");
        jdbc.update("delete from utility_meter_readings");
        jdbc.update("delete from utility_meters");
        jdbc.update("delete from maintenance_plan_executions");
        jdbc.update("delete from maintenance_plans");
        jdbc.update("delete from safety_compliance_events");
        jdbc.update("delete from safety_compliance_items");
        jdbc.update("delete from asset_events");
        jdbc.update("update assets set linked_asset_id = null");
        jdbc.update("delete from assets");
        var overview = service.overview();
        assertEquals(new AssetViews.Metrics(0, 0, 0, 0), overview.metrics());
        assertTrue(overview.kindSummaries().isEmpty());
        assertTrue(overview.capacitySummaries().isEmpty());
        assertTrue(overview.attentionAssets().isEmpty());
    }

    @Test
    void validatesPaginationAndAssetTypes() {
        var query = new AssetQuery("DEVICE", null, null, null, null);
        assertThrows(BusinessException.class, () -> service.ledger(query, 0, 10));
        assertThrows(BusinessException.class, () -> service.ledger(query, 1, 101));
        assertThrows(BusinessException.class, () -> service.ledger(query, 1, 0));
        assertThrows(BusinessException.class, () -> service.ledger(new AssetQuery("INVALID", null, null, null, null), 1, 10));
        assertThrows(BusinessException.class, () -> service.bindingOptions("WAREHOUSE", null, null, null));
    }

    @Test
    void bindingSearchIsBoundedAndKeepsValidCurrentSelection() {
        for (int index = 0; index < 40; index++) {
            var id = "BIND-" + String.format("%02d", index);
            jdbc.update("""
                    insert into assets (id, code, kind, name, category, ownership, owner_name, site,
                        location, custodian, lifecycle_status, availability_status)
                    values (?, ?, 'VEHICLE', ?, '冷藏运输车', 'OWNED', '测试', '测试', '测试', '测试', 'ACTIVE', 'AVAILABLE')
                    """, id, id, "绑定测试车辆" + index);
        }
        assertEquals(30, service.bindingOptions("MOBILE_CABINET", "绑定测试", null, null).size());
        var choices = service.bindingOptions("MOBILE_CABINET", "绑定测试", "BIND-00", "BIND-39");
        assertEquals(31, choices.size());
        assertTrue(choices.stream().anyMatch(option -> option.id().equals("BIND-39")));
        assertTrue(choices.stream().noneMatch(option -> option.id().equals("BIND-00")));
        assertTrue(choices.stream().allMatch(option -> option.kind().equals("VEHICLE")));
        jdbc.update("update assets set lifecycle_status = 'RETIRED' where id = 'BIND-39'");
        assertTrue(service.bindingOptions("MOBILE_CABINET", "绑定测试", null, "BIND-39").stream().noneMatch(option -> option.id().equals("BIND-39")));
        assertTrue(service.bindingOptions("DEVICE", null, null, null).stream().noneMatch(option -> option.kind().equals("DEVICE")));
    }

    @Test
    void relationsDoNotDependOnLedgerPageOrFilter() {
        var relation = service.relations("MC-001");
        assertEquals("VEH-001", relation.linkedAsset().id());
        assertTrue(service.relations("VEH-001").relatedAssets().stream().anyMatch(asset -> asset.id().equals("MC-001")));
        assertThrows(BusinessException.class, () -> service.relations("missing"));
    }
}
