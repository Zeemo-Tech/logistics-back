package org.example.logisticsback.maintenance;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import org.example.logisticsback.assets.AssetService;
import org.example.logisticsback.assets.AssetUpsertRequest;
import org.example.logisticsback.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class WorkOrderServiceTests {
    @Autowired private WorkOrderService service;
    @Autowired private AssetService assets;

    @Test
    void completesRepairAndRestoresExplicitAvailabilityWithOrderedAudit() {
        var order = report("VEH-004");
        assertEquals("MAINTENANCE", assets.get(order.assetId()).availabilityStatus());
        order = act(order, "ASSIGN", "张维修", null, null);
        order = act(order, "START", null, null, null);
        order = act(order, "SUBMIT", null, "已更换损坏部件".repeat(120), null);
        assertEquals("PENDING_ACCEPTANCE", order.status());
        assertEquals("MAINTENANCE", assets.get(order.assetId()).availabilityStatus());
        order = act(order, "ACCEPT", null, "试运行正常", "IN_USE");
        assertEquals("COMPLETED", order.status());
        assertEquals("IN_USE", assets.get(order.assetId()).availabilityStatus());
        assertNotNull(order.completedAt());
        assertEquals(List.of("REPORT", "ASSIGN", "START", "SUBMIT", "ACCEPT"),
                service.events(order.id()).stream().map(WorkOrderEvent::action).toList());
        assertTrue(service.events(order.id()).stream().allMatch(event -> !event.operatorName().isBlank()));
        assertTrue(assets.events(order.assetId()).stream().allMatch(event -> event.description().length() <= 500));
        assertEquals("REPORTED", report(order.assetId()).status());
    }

    @Test
    void rejectsDuplicateOpenOrdersAndStaleOrInvalidActions() {
        var order = report("VEH-004");
        assertEquals(409, assertThrows(BusinessException.class, () -> report("VEH-004")).getCode());
        var reported = order;
        assertThrows(BusinessException.class, () -> act(reported, "START", null, null, null));
        order = act(order, "ASSIGN", "维修组", null, null);
        assertEquals(409, assertThrows(BusinessException.class,
                () -> act(reported, "CANCEL", null, "重复提交", null)).getCode());
        assertEquals("ASSIGNED", service.get(order.id()).status());
    }

    @Test
    void acceptanceCanReturnToRepairAndRequiresResolution() {
        var order = act(report("VEH-004"), "ASSIGN", "服务商", null, null);
        order = act(order, "START", null, null, null);
        var repairing = order;
        assertThrows(BusinessException.class, () -> act(repairing, "CANCEL", null, "取消", null));
        order = act(order, "SUBMIT", null, "初次维修完成", null);
        var pending = order;
        assertThrows(BusinessException.class, () -> act(pending, "ACCEPT", null, "通过", null));
        order = act(order, "REJECT", null, "仍存在异响", null);
        assertEquals("ACCEPTANCE_REJECTED", order.status());
        order = act(order, "RESTART", null, null, null);
        assertEquals("IN_PROGRESS", order.status());
        assertEquals("MAINTENANCE", assets.get(order.assetId()).availabilityStatus());
        order = act(order, "SUBMIT", null, "返修完成", null);
        order = act(order, "ACCEPT", null, "复验通过", "AVAILABLE");
        assertEquals("AVAILABLE", assets.get(order.assetId()).availabilityStatus());
        assertEquals(8, service.events(order.id()).size());
    }

    @Test
    void cancellationRestoresPreviousStateAndReleasesAsset() {
        var previous = assets.get("VEH-004").availabilityStatus();
        var order = act(report("VEH-004"), "CANCEL", null, "误报", null);
        assertEquals("CANCELLED", order.status());
        assertEquals(previous, assets.get(order.assetId()).availabilityStatus());
        assertEquals("REPORTED", report(order.assetId()).status());
    }

    @Test
    void openOrderPreventsBypassingAssetStateAndInactiveAssetCannotReport() {
        report("VEH-004");
        assertThrows(BusinessException.class, () -> updateState("VEH-004", "ACTIVE", "AVAILABLE"));
        assertThrows(BusinessException.class, () -> updateState("VEH-004", "INACTIVE", "UNAVAILABLE"));
        updateState("WH-BC-001", "INACTIVE", "UNAVAILABLE");
        assertThrows(BusinessException.class, () -> report("WH-BC-001"));
    }

    @Test
    void cancellingPreviouslyUntrackedMaintenanceLeavesAssetUnavailable() {
        updateState("VEH-004", "ACTIVE", "MAINTENANCE");
        var order = act(report("VEH-004"), "CANCEL", null, "转线下核实", null);
        assertEquals("UNAVAILABLE", assets.get(order.assetId()).availabilityStatus());
    }

    private WorkOrder report(String assetId) {
        return service.create(new WorkOrderRequests.Create(assetId, "制冷设备故障", "运行时出现异响", "NORMAL"), 1L);
    }

    private WorkOrder act(WorkOrder order, String action, String assignee, String notes, String resolution) {
        if ("START".equals(action)) {
            return service.act(order.id(), new WorkOrderRequests.Action(action, order.version(), assignee,
                    notes, resolution, null, null, null, null, null, null, null,
                    LocalDateTime.now(), "测试维修员", null, null), 1L);
        }
        return service.act(order.id(), new WorkOrderRequests.Action(action, order.version(), assignee, notes, resolution), 1L);
    }

    private void updateState(String id, String lifecycle, String availability) {
        var a = assets.get(id);
        assets.update(id, new AssetUpsertRequest(a.kind(), a.name(), a.category(), a.ownership(), a.ownerName(),
                a.site(), a.location(), a.custodian(), lifecycle, availability, a.conditionStatus(), a.linkedAssetId(),
                a.capacityTotal(), a.capacityUsed(), a.capacityUnit(), a.temperatureMin(), a.temperatureMax(),
                a.batteryPercent(), a.ratedLoadTons(), a.insuranceExpiry(), a.lastSeen(), a.notes()), 1L);
    }
}
