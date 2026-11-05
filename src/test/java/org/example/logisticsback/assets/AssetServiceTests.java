package org.example.logisticsback.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.example.logisticsback.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AssetServiceTests {
    @Autowired
    private AssetService service;

    @Test
    void createsAndUpdatesWarehouseWithHistory() {
        var created = service.create(warehouse("新增常温仓", new BigDecimal("1000"),
                new BigDecimal("200")), 1L);
        assertNotNull(created.id());
        assertTrue(created.code().startsWith("WH-"));
        assertEquals("新增常温仓", service.get(created.id()).name());

        var updated = service.update(created.id(), warehouse("已更新常温仓",
                new BigDecimal("1000"), new BigDecimal("300")), 1L);
        assertEquals("已更新常温仓", updated.name());
        assertEquals(new BigDecimal("300.00"), updated.capacityUsed());
        assertEquals(2, service.events(created.id()).size());
        assertEquals(1, service.list(new AssetQuery("WAREHOUSE", "已更新", null, null, null)).size());
    }

    @Test
    void rejectsOverCapacityAndInvalidBinding() {
        var overCapacity = warehouse("超额仓", new BigDecimal("100"), new BigDecimal("101"));
        assertEquals("已占用容量不能大于设计容量",
                assertThrows(BusinessException.class, () -> service.create(overCapacity, 1L)).getMessage());

        var cabinet = new AssetUpsertRequest("MOBILE_CABINET", "测试冷柜", "冷藏柜", "OWNED",
                "百川物流智慧冷链中心", "百川物流智慧冷链中心", "园区东区", "设备组",
                "ACTIVE", "AVAILABLE", null, "CS-BC-001", null, null, null,
                new BigDecimal("2"), new BigDecimal("8"), null, null, null, null, "");
        assertEquals("移动冷柜只能绑定车辆",
                assertThrows(BusinessException.class, () -> service.create(cabinet, 1L)).getMessage());
    }

    @Test
    void preventsRetiringAssetWithActiveDependents() {
        var existing = service.get("VEH-001");
        var request = new AssetUpsertRequest(existing.kind(), existing.name(), existing.category(),
                existing.ownership(), existing.ownerName(), existing.site(), existing.location(),
                existing.custodian(), "RETIRED", "UNAVAILABLE", null, null,
                existing.capacityTotal(), existing.capacityUsed(), existing.capacityUnit(),
                existing.temperatureMin(), existing.temperatureMax(), existing.batteryPercent(),
                existing.ratedLoadTons(), existing.insuranceExpiry(), existing.lastSeen(), existing.notes());
        assertEquals("该资产仍绑定在用设备或冷柜，请先解除绑定",
                assertThrows(BusinessException.class, () -> service.update(existing.id(), request, 1L)).getMessage());
    }

    @Test
    void clearsBindingAndRecordsPreviousAssetCode() {
        var existing = service.get("MC-001");
        var request = new AssetUpsertRequest(existing.kind(), existing.name(), existing.category(),
                existing.ownership(), existing.ownerName(), existing.site(), existing.location(),
                existing.custodian(), existing.lifecycleStatus(), existing.availabilityStatus(),
                null, "", existing.capacityTotal(), existing.capacityUsed(), existing.capacityUnit(),
                existing.temperatureMin(), existing.temperatureMax(), existing.batteryPercent(),
                existing.ratedLoadTons(), existing.insuranceExpiry(), existing.lastSeen(), existing.notes());
        var updated = service.update(existing.id(), request, 1L);
        assertNull(updated.linkedAssetId());
        assertTrue(service.events(existing.id()).stream().anyMatch(event ->
                event.eventType().equals("BINDING_CHANGED")
                        && event.description().contains("VEH-001 → 未绑定")));
    }

    @Test
    void exportsFilteredRecordsWithBusinessLabelsAndRelatedAssetCode() {
        var table = service.export(new AssetQuery("MOBILE_CABINET", "MC-001", null, null, null));
        assertEquals(1, table.rows().size());
        var row = table.rows().get(0);
        assertEquals("移动冷柜", row.get(2));
        assertEquals("自有", row.get(4));
        assertEquals("在册", row.get(9));
        assertEquals("VEH-001", row.get(12));
    }

    private AssetUpsertRequest warehouse(String name, BigDecimal total, BigDecimal used) {
        return new AssetUpsertRequest("WAREHOUSE", name, "常温仓", "OWNED",
                "百川物流智慧冷链中心", "百川物流智慧冷链中心", "园区东区", "陈晓",
                "ACTIVE", "AVAILABLE", null, null, total, used, "m²", null, null,
                null, null, null, null, "");
    }
}
