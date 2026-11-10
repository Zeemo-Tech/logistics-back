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
