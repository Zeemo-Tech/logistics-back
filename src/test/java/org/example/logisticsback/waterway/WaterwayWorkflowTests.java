package org.example.logisticsback.waterway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.example.logisticsback.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class WaterwayWorkflowTests {
    @Autowired WaterwayService waterway;
    @Autowired JdbcTemplate jdbc;

    @Test
    void portObservationsKeepSeparateDecimalTemperaturesAndSortByTime() {
        var first = waterway.create("checkpoints", Map.of(
                "voyageId", "WVO-050", "containerId", "WC-001", "portName", "关累",
                "occurredAt", "2026-11-11 09:00", "temperature", -18.25, "location", "1号泊位"));
        waterway.create("checkpoints", Map.of(
                "voyageId", "WVO-050", "containerId", "WC-001", "portName", "清盛",
                "occurredAt", "2026-11-12 10:00", "temperature", -17.5));
        var rows = waterway.list("checkpoints", "");
        assertEquals(2, rows.size());
        assertEquals("清盛", rows.get(0).get("portName"));
        assertEquals(-18.25, ((Number) first.get("temperature")).doubleValue());
        assertEquals("BTUU8585091", first.get("containerNo"));
        assertEquals("云南交投1201", first.get("vesselName"));
        assertThrows(BusinessException.class, () -> waterway.delete("voyages", "WVO-050"));
        assertThrows(BusinessException.class, () -> waterway.delete("containers", "WC-001"));
    }

    @Test
    void checkpointRejectsWrongVoyageMalformedTimeAndAmbiguousTemperature() {
        assertThrows(BusinessException.class, () -> waterway.create("checkpoints", Map.of(
                "voyageId", "WVO-051", "containerId", "WC-001", "portName", "清盛", "occurredAt", "2026-11-11 09:00")));
        assertThrows(BusinessException.class, () -> waterway.create("checkpoints", Map.of(
                "voyageId", "WVO-050", "portName", "清盛", "occurredAt", "2026-02-30 09:00")));
        assertThrows(BusinessException.class, () -> waterway.create("checkpoints", Map.of(
                "voyageId", "WVO-050", "portName", "清盛", "occurredAt", "2026-11-11 09:00", "temperature", 10)));
        assertThrows(BusinessException.class, () -> waterway.create("checkpoints", Map.of(
                "voyageId", "WVO-050", "containerId", "WC-001", "portName", "清盛", "occurredAt", "2026-11-11 09:00", "temperature", 81)));
    }

    @Test
    void checkpointPartialEditValidatesMergedVoyageAndKeepsOtherValues() {
        var row = waterway.create("checkpoints", Map.of(
                "voyageId", "WVO-050", "containerId", "WC-001", "portName", "关累", "occurredAt", "2026-11-11 09:00"));
        var updated = waterway.update("checkpoints", row.get("id").toString(), Map.of("temperature", -18.5));
        assertEquals("关累", updated.get("portName"));
        assertThrows(BusinessException.class, () -> waterway.update("checkpoints", row.get("id").toString(), Map.of("voyageId", "WVO-051")));
        assertThrows(BusinessException.class, () -> waterway.update("checkpoints", row.get("id").toString(), Map.of("portName", "")));
    }

    @Test
    void containerInfersCustomerAndVoyageFromBooking() {
        var row = waterway.create("containers", Map.of("containerNo", "TEST-CUSTOMER-001", "bookingId", "WB-005"));
        assertEquals("KH-2026-018", row.get("customerId"));
        assertThrows(BusinessException.class, () -> waterway.create("containers", Map.of(
                "containerNo", "TEST-CUSTOMER-002", "bookingId", "WB-005", "customerId", "KH-2026-023")));
        var returned = waterway.create("emptyReturns", Map.of("containerId", "WC-004", "returnVoyageId", "WVO-051"));
        assertEquals("KH-2026-023", returned.get("customerId"));
    }

    @Test
    void confirmedReturnSupportsTemperatureObservationsWithoutChangingOriginalVoyage() {
        waterway.create("emptyReturns", Map.of("containerId", "WC-004", "returnVoyageId", "WVO-051", "status", "已确认"));
        var row = waterway.create("checkpoints", Map.of("voyageId", "WVO-051", "containerId", "WC-004", "portName", "清盛", "occurredAt", "2026-11-11 09:00", "temperature", 0));
        assertEquals("WVO-051", row.get("voyageId"));
        assertEquals("WVO-050", jdbc.queryForObject("select voyage_id from waterway_containers where id = 'WC-004'", String.class));
    }

    @Test
    void reeferInspectionKeepsZeroSetpointIndependentReadingsGpsAndSnapshots() {
        var first = waterway.create("checkpoints", Map.of(
                "voyageId", "WVO-050", "containerId", "WC-002", "portName", "索累",
                "occurredAt", "2026-11-02 09:15", "supplyAir", 0.1, "returnAir", 1.4,
                "latitude", 20.123456, "longitude", 100.123456, "customerName", "不能覆盖档案"));
        var second = waterway.create("checkpoints", Map.of(
                "voyageId", "WVO-050", "containerId", "WC-002", "portName", "班相果",
                "occurredAt", "2026-11-02 11:55", "setTemperature", 0, "supplyAir", 0.2, "returnAir", 2.7));
        assertEquals(0, ((Number) first.get("setTemperature")).doubleValue());
        assertEquals(0.1, ((Number) first.get("supplyAir")).doubleValue());
        assertEquals(1.4, ((Number) first.get("returnAir")).doubleValue());
        assertEquals(20.123456, ((Number) first.get("latitude")).doubleValue());
        assertEquals(100.123456, ((Number) first.get("longitude")).doubleValue());
        assertEquals("葡萄", first.get("product"));
        assertEquals(jdbc.queryForObject("select name from customers where id = 'KH-2026-023'", String.class), first.get("customerName"));
        assertEquals(null, second.get("latitude"));
        assertEquals(2.7, ((Number) second.get("returnAir")).doubleValue());
        assertEquals(2, waterway.list("checkpoints", "").size());
        jdbc.update("update waterway_containers set cargo = '已变更货物' where id = 'WC-002'");
        var edited = waterway.update("checkpoints", first.get("id").toString(), Map.of("notes", "补充备注", "product", "伪造货物"));
        assertEquals("葡萄", edited.get("product"));
        assertEquals(1.4, ((Number) edited.get("returnAir")).doubleValue());
    }

    @Test
    void reeferInspectionRejectsInvalidOrIncompleteGpsAndUnassignedReadings() {
        var base = Map.<String, Object>of("voyageId", "WVO-050", "containerId", "WC-002", "portName", "索累", "occurredAt", "2026-11-02 09:15");
        for (var invalid : java.util.List.of(Map.of("latitude", 91, "longitude", 100), Map.of("latitude", 20, "longitude", 181), Map.of("latitude", 20), Map.of("supplyAir", 81), Map.of("returnAir", -81))) {
            var input = new java.util.LinkedHashMap<String, Object>(base);
            input.putAll(invalid);
            assertThrows(BusinessException.class, () -> waterway.create("checkpoints", input));
        }
        var noContainer = new java.util.LinkedHashMap<String, Object>(base);
        noContainer.remove("containerId");
        noContainer.put("returnAir", 1.4);
        assertThrows(BusinessException.class, () -> waterway.create("checkpoints", noContainer));
        var row = waterway.create("checkpoints", base);
        assertEquals(null, row.get("supplyAir"));
        assertEquals(null, row.get("returnAir"));
        assertEquals(null, row.get("latitude"));
        assertThrows(BusinessException.class, () -> waterway.update("checkpoints", row.get("id").toString(), Map.of("longitude", 100)));
    }

    @Test
    void bookingRequiresMatchingActiveWaterwayTransportIntention() {
        jdbc.update("""
                insert into customer_intentions (id, customer_id, business, business_detail, stage)
                values ('CI-WATERWAY-TEST', 'KH-2026-018', '运输服务', '水路运输', '方案报价')
                """);

        var booking = waterway.create("bookings", Map.of(
                "customerId", "KH-2026-018",
                "intentionId", "CI-WATERWAY-TEST",
                "direction", "下水",
                "plannedDate", "2026-11-20",
                "containerCount", 1));

        assertEquals("CI-WATERWAY-TEST", booking.get("intentionId"));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from waterway_bookings where id = ? and intention_id = ?",
                Integer.class, booking.get("id"), "CI-WATERWAY-TEST"));
    }

    @Test
    void bookingRejectsIntentionFromAnotherCustomerOrTransportMode() {
        jdbc.update("""
                insert into customer_intentions (id, customer_id, business, business_detail, stage)
                values ('CI-ROAD-TEST', 'KH-2026-018', '运输服务', '货车运输', '方案报价')
                """);

        assertThrows(BusinessException.class, () -> waterway.create("bookings", Map.of(
                "customerId", "KH-2026-023",
                "intentionId", "CI-ROAD-TEST",
                "direction", "下水",
                "plannedDate", "2026-11-20",
                "containerCount", 1)));
        assertThrows(BusinessException.class, () -> waterway.create("bookings", Map.of(
                "customerId", "KH-2026-018",
                "intentionId", "CI-ROAD-TEST",
                "direction", "下水",
                "plannedDate", "2026-11-20",
                "containerCount", 1)));
    }

    @Test
    void releasesOnlyUnarrivedBookingCapacityAndKeepsReleaseRecord() {
        var booking = waterway.releaseBooking("WB-005", Map.of("quantity", 1, "reason", "车辆未按时到港"));

        assertEquals(1, booking.get("releasedCount"));
        assertEquals("部分释放", booking.get("status"));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from waterway_booking_releases where booking_id = 'WB-005'", Integer.class));
    }

    @Test
    void cannotReleaseCapacityAlreadyConfirmedAtPort() {
        assertThrows(BusinessException.class, () -> waterway.releaseBooking(
                "WB-001", Map.of("quantity", 3, "reason", "测试释放")));
    }

    @Test
    void cannotLowerVoyageCapacityBelowAllocatedBookings() {
        var voyage = jdbc.queryForMap("select * from waterway_voyages where id = 'WVO-050'");
        assertThrows(BusinessException.class, () -> waterway.update("voyages", "WVO-050", Map.of(
                "availableCapacity", 4,
                "vesselId", voyage.get("VESSEL_ID"),
                "voyageNo", "0050")));
    }

    @Test
    void departureRequiresEveryReservedContainerToArriveAndLoad() {
        assertThrows(BusinessException.class, () -> waterway.update("voyages", "WVO-051", Map.of(
                "status", "运输中")));
    }

    @Test
    void confirmedEmptyReturnCanBeLoadedOnItsTargetVoyage() {
        waterway.create("emptyReturns", Map.of(
                "containerId", "WC-004",
                "sourceVoyageId", "WVO-050",
                "returnVoyageId", "WVO-051",
                "currentPort", "清盛",
                "status", "已确认"));

        var loading = waterway.create("loading", Map.of(
                "voyageId", "WVO-051",
                "containerId", "WC-004",
                "state", "空柜",
                "status", "已装船"));

        assertEquals("WVO-051", loading.get("voyageId"));
        assertEquals("已装船", loading.get("status"));
    }
}
