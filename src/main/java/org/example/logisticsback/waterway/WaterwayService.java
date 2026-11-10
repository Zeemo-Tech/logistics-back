package org.example.logisticsback.waterway;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.example.logisticsback.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WaterwayService {
    private static final Map<String, Map<String, String>> WRITABLE = Map.of(
            "bookings", columns(
                    "customerId", "customer_id", "intentionId", "intention_id", "direction", "direction", "plannedDate", "planned_date",
                    "preferredVesselId", "preferred_vessel_id", "voyageId", "voyage_id",
                    "containerCount", "container_count", "heavyCount", "heavy_count", "emptyCount", "empty_count",
                    "containerNo", "container_no", "cargoType", "cargo_type", "estimatedWeight", "estimated_weight",
                    "status", "status", "notes", "notes", "bookedAt", "booked_at"),
            "vessels", columns(
                    "name", "name", "type", "vessel_type", "designCapacity", "design_capacity",
                    "availableCapacity", "available_capacity", "captain", "captain", "phone", "phone",
                    "status", "status", "notes", "notes"),
            "voyages", columns(
                    "voyageNo", "voyage_no", "vesselId", "vessel_id", "direction", "direction",
                    "originPort", "origin_port", "destinationPort", "destination_port",
                    "plannedDeparture", "planned_departure", "actualDeparture", "actual_departure",
                    "estimatedArrival", "estimated_arrival", "actualArrival", "actual_arrival",
                    "availableCapacity", "available_capacity", "status", "status", "notes", "notes",
                    "capacityReason", "capacity_reason"),
            "containers", columns(
                    "containerNo", "container_no", "type", "container_type", "state", "container_state",
                    "cargo", "cargo", "weight", "weight", "billOfLading", "bill_of_lading",
                    "customerId", "customer_id", "voyageId", "voyage_id", "bookingId", "booking_id", "vehicleId", "vehicle_id",
                    "setTemperature", "set_temperature", "sensorId", "sensor_id", "transportStatus", "transport_status",
                    "gateStatus", "gate_status", "reviewStatus", "review_status", "reviewNote", "review_note"),
            "vehicles", columns(
                    "plate", "plate", "trailer", "trailer", "vehicleType", "vehicle_type", "fleet", "fleet",
                    "driver", "driver", "phone", "phone", "containerId", "container_id", "voyageId", "voyage_id",
                    "expectedArrival", "expected_arrival", "actualGateIn", "actual_gate_in", "gateStatus", "gate_status",
                    "notes", "notes"),
            "loading", columns(
                    "voyageId", "voyage_id", "containerId", "container_id", "vehicleId", "vehicle_id",
                    "state", "container_state", "startedAt", "started_at", "completedAt", "completed_at",
                    "operator", "operator", "status", "status", "loadedAt", "loaded_at", "notes", "notes"),
            "emptyReturns", columns(
                    "containerId", "container_id", "customerId", "customer_id", "currentPort", "current_port",
                    "sourceVoyageId", "source_voyage_id", "returnVoyageId", "return_voyage_id",
                    "confirmedAt", "confirmed_at", "status", "status", "notes", "notes"));

    private static final Map<String, String> SELECTS = Map.of(
            "customers", """
                    select c.id, c.code, c.name, c.customer_nature as customer_nature,
                           c.type as type, c.service as service, c.contact_person as contact,
                           c.contact_phone as phone, c.status as status, '' as contract, c.notes
                    from customers c
                    """,
            "bookings", """
                    select b.*, c.name as customer_name, v.voyage_no as voyage_no,
                           concat(i.business, ' · ', i.business_detail) as intention_label,
                           s.name as vessel_name, pv.name as preferred_vessel_name,
                           greatest(0, b.container_count - b.released_count) as effective_count,
                           (select count(*) from waterway_containers wc where wc.booking_id = b.id and wc.gate_status = '已确认到港') as arrived_count,
                           greatest(0, b.container_count - b.released_count -
                             (select count(*) from waterway_containers wc where wc.booking_id = b.id and wc.gate_status = '已确认到港')) as pending_arrival_count
                    from waterway_bookings b join customers c on c.id = b.customer_id
                    left join customer_intentions i on i.id = b.intention_id
                    left join waterway_voyages v on v.id = b.voyage_id
                    left join waterway_vessels s on s.id = v.vessel_id
                    left join waterway_vessels pv on pv.id = b.preferred_vessel_id
                    """,
            "vessels", "select *, vessel_type as type from waterway_vessels",
            "voyages", """
                    select v.*, s.name as vessel_name, s.design_capacity,
                           coalesce((select sum(b.container_count - b.released_count) from waterway_bookings b
                                     where b.voyage_id = v.id and b.status <> '已取消'), 0) as booked_count,
                           (select count(*) from waterway_containers c where c.voyage_id = v.id) as allocated_count,
                           (select count(*) from waterway_loading_operations l
                            where l.voyage_id = v.id and l.status = '已装船') as loaded_count,
                           greatest(0, v.available_capacity - coalesce((select sum(b.container_count - b.released_count)
                             from waterway_bookings b where b.voyage_id = v.id and b.status <> '已取消'), 0)) as remaining_capacity
                    from waterway_voyages v join waterway_vessels s on s.id = v.vessel_id
                    """,
            "containers", """
                    select c.*, c.container_type as type, c.container_state as state,
                           coalesce(customer.name, '') as customer_name,
                           coalesce(v.voyage_no, '') as voyage_no, coalesce(t.plate, '') as truck_plate,
                           coalesce(book.code, '') as booking_code
                    from waterway_containers c left join customers customer on customer.id = c.customer_id
                    left join waterway_voyages v on v.id = c.voyage_id
                    left join waterway_vehicles t on t.id = c.vehicle_id
                    left join waterway_bookings book on book.id = c.booking_id
                    """,
            "vehicles", """
                    select t.*, coalesce(c.container_no, '') as container_no,
                           coalesce(customer.name, '') as customer_name,
                           coalesce(v.voyage_no, '') as voyage_no
                    from waterway_vehicles t left join waterway_containers c on c.id = t.container_id
                    left join customers customer on customer.id = c.customer_id
                    left join waterway_voyages v on v.id = t.voyage_id
                    """,
            "loading", """
                    select l.*, l.container_state as state, v.voyage_no, s.name as vessel_name, c.container_no,
                           t.plate, c.container_state as current_container_state
                    from waterway_loading_operations l
                    join waterway_voyages v on v.id = l.voyage_id
                    join waterway_vessels s on s.id = v.vessel_id
                    join waterway_containers c on c.id = l.container_id
                    left join waterway_vehicles t on t.id = l.vehicle_id
                    """,
            "emptyReturns", """
                    select r.*, c.container_no, customer.name as customer_name,
                           source.voyage_no as source_voyage_no, target.voyage_no as return_voyage_no,
                           target.planned_departure as planned_return_date
                    from waterway_empty_returns r join waterway_containers c on c.id = r.container_id
                    left join customers customer on customer.id = r.customer_id
                    left join waterway_voyages source on source.id = r.source_voyage_id
                    left join waterway_voyages target on target.id = r.return_voyage_id
                    """);

    private final JdbcTemplate jdbc;

    public WaterwayService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(String entity, String keyword) {
        var select = select(entity);
        var rows = jdbc.queryForList(select + " order by 1");
        if (keyword == null || keyword.isBlank()) return rows.stream().map(WaterwayService::camelize).toList();
        var query = keyword.trim().toLowerCase();
        return rows.stream()
                .filter(row -> row.values().stream().anyMatch(value -> value != null && value.toString().toLowerCase().contains(query)))
                .map(WaterwayService::camelize)
                .toList();
    }

    public Map<String, Object> overview() {
        var today = LocalDate.now().toString();
        var result = new LinkedHashMap<String, Object>();
        result.put("plannedDepartures", scalar("select count(*) from waterway_voyages where substring(planned_departure, 1, 10) = ?", today));
        result.put("plannedContainers", scalar("select coalesce(sum(container_count), 0) from waterway_bookings where planned_date = ? and status <> '已取消'", today));
        result.put("loadedContainers", scalar("select count(*) from waterway_loading_operations where loaded_at like ? and status = '已装船'", today + "%"));
        result.put("sailingVessels", scalar("select count(*) from waterway_voyages where status = '运输中'"));
        result.put("sailingContainers", scalar("select count(*) from waterway_containers where transport_status = '运输中'"));
        result.put("vehiclesWaiting", scalar("select count(*) from waterway_vehicles where gate_status = '待进场'"));
        result.put("liftingTasks", scalar("select count(*) from waterway_loading_operations where status in ('待吊柜', '吊柜中', '吊柜已完成')"));
        result.put("capacityAvailable", scalar("select coalesce(sum(greatest(0, v.available_capacity - (select coalesce(sum(b.container_count - b.released_count), 0) from waterway_bookings b where b.voyage_id = v.id and b.status <> '已取消') - (select count(*) from waterway_empty_returns r where r.return_voyage_id = v.id and r.status <> '已取消'))), 0) from waterway_voyages v where v.status not in ('已完成', '已取消')"));
        result.put("upcomingVoyages", list("voyages", null).stream().limit(8).toList());
        return result;
    }

    @Transactional
    public Map<String, Object> create(String entity, Map<String, Object> input) {
        requireEntity(entity);
        if ("customers".equals(entity)) throw new BusinessException(405, "客户档案请在资产 > 客户与合同中统一维护");
        var columns = WRITABLE.get(entity);
        requireFields(entity, input, true);
        validate(entity, input, null);
        var id = UUID.randomUUID().toString();
        var code = codePrefix(entity) + "-" + id.substring(0, 8).toUpperCase();
        var values = new LinkedHashMap<String, Object>();
        values.put("id", id);
        if (Set.of("bookings", "vessels", "voyages", "loading").contains(entity)) values.put("code", code);
        for (var entry : columns.entrySet()) {
            if (input.containsKey(entry.getKey())) values.put(entry.getValue(), normalize(input.get(entry.getKey())));
        }
        if ("bookings".equals(entity) && !values.containsKey("booked_at")) values.put("booked_at", "");
        var names = new ArrayList<>(values.keySet());
        var placeholders = String.join(", ", java.util.Collections.nCopies(names.size(), "?"));
        var sql = "insert into " + table(entity) + " (" + String.join(", ", names) + ") values (" + placeholders + ")";
        jdbc.update(sql, names.stream().map(values::get).toArray());
        return get(entity, id);
    }

    @Transactional
    public Map<String, Object> releaseBooking(String id, Map<String, Object> input) {
        var booking = get("bookings", id);
        var requested = integer(input, "quantity", 0);
        var reason = value(input, "reason");
        var total = integer(booking, "containerCount", 0);
        var released = integer(booking, "releasedCount", 0);
        var arrived = scalar("select count(*) from waterway_containers where booking_id = ? and gate_status = '已确认到港'", id);
        require(requested > 0 && requested <= total - released - arrived, "只能释放尚未到港的订舱柜数");
        require(!reason.isBlank(), "请填写释放原因");
        var now = java.time.LocalDateTime.now().toString().replace('T', ' ');
        var pendingContainerIds = jdbc.queryForList(
                "select id from waterway_containers where booking_id = ? and gate_status <> '已确认到港' order by created_at desc",
                String.class, id);
        for (var containerId : pendingContainerIds.stream().limit(requested).toList()) {
            jdbc.update("update waterway_containers set booking_id = null, voyage_id = null, transport_status = '预约已释放', updated_at = current_timestamp where id = ?", containerId);
        }
        jdbc.update("insert into waterway_booking_releases (id, booking_id, released_count, reason, released_at) values (?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), id, requested, reason, now);
        var nextReleased = released + requested;
        var nextStatus = nextReleased == total ? "已取消" : "部分释放";
        jdbc.update("update waterway_bookings set released_count = ?, status = ?, updated_at = current_timestamp where id = ?",
                nextReleased, nextStatus, id);
        return get("bookings", id);
    }

    @Transactional
    public Map<String, Object> update(String entity, String id, Map<String, Object> input) {
        requireEntity(entity);
        get(entity, id);
        if ("customers".equals(entity)) throw new BusinessException(405, "客户档案请在资产 > 客户与合同中统一维护");
        var columns = WRITABLE.get(entity);
        requireFields(entity, input, false);
        validate(entity, input, id);
        var assignments = new ArrayList<String>();
        var args = new ArrayList<Object>();
        for (var entry : columns.entrySet()) {
            if (!input.containsKey(entry.getKey())) continue;
            assignments.add(entry.getValue() + " = ?");
            args.add(normalize(input.get(entry.getKey())));
        }
        if (assignments.isEmpty()) throw new BusinessException(400, "没有可更新的字段");
        assignments.add("updated_at = current_timestamp");
        args.add(id);
        jdbc.update("update " + table(entity) + " set " + String.join(", ", assignments) + " where id = ?", args.toArray());
        return get(entity, id);
    }

    @Transactional
    public void delete(String entity, String id) {
        requireEntity(entity);
        get(entity, id);
        if ("customers".equals(entity)) throw new BusinessException(405, "客户档案请在资产 > 客户与合同中统一维护");
        if (references(entity, id) > 0) throw new BusinessException(409, "该记录已有业务关联，不能删除；请保留运输历史或先解除关联");
        if ("customers".equals(entity)) {
            jdbc.update("delete from waterway_customer_profiles where customer_id = ?", id);
            jdbc.update("delete from customers where id = ?", id);
            return;
        }
        jdbc.update("delete from " + table(entity) + " where id = ?", id);
    }

    private Map<String, Object> createCustomer(Map<String, Object> input) {
        requireFields("customers", input, true);
        var id = UUID.randomUUID().toString();
        var code = "WT-" + LocalDate.now().getYear() + "-" + id.substring(0, 6).toUpperCase();
        jdbc.update("""
                insert into customers (id, code, name, customer_nature, contact_person, contact_phone,
                    type, service, stage, status, notes)
                values (?, ?, ?, '企业', ?, ?, '水路运输客户', '水路运输', '合作中', '已签约', ?)
                """, id, code, value(input, "name"), value(input, "contact"), value(input, "phone"), value(input, "notes"));
        jdbc.update("insert into waterway_customer_profiles (customer_id, customer_type, cooperation_status, framework_contract) values (?, ?, ?, ?)",
                id, valueOr(input, "type", "货主"), valueOr(input, "status", "合作中"), value(input, "contract"));
        return get("customers", id);
    }

    private Map<String, Object> updateCustomer(String id, Map<String, Object> input) {
        requireFields("customers", input, false);
        var updated = jdbc.update("""
                update customers set name = coalesce(?, name), contact_person = coalesce(?, contact_person),
                    contact_phone = coalesce(?, contact_phone), notes = coalesce(?, notes), updated_at = current_timestamp
                where id = ?
                """, nullable(input, "name"), nullable(input, "contact"), nullable(input, "phone"), nullable(input, "notes"), id);
        if (updated == 0) throw new BusinessException(404, "客户不存在");
        jdbc.update("""
                update waterway_customer_profiles set customer_type = coalesce(?, customer_type),
                    cooperation_status = coalesce(?, cooperation_status), framework_contract = coalesce(?, framework_contract),
                    updated_at = current_timestamp where customer_id = ?
                """, nullable(input, "type"), nullable(input, "status"), nullable(input, "contract"), id);
        return get("customers", id);
    }

    private Map<String, Object> get(String entity, String id) {
        requireEntity(entity);
        return jdbc.queryForList(select(entity) + " where " + idColumn(entity) + " = ?", id).stream()
                .findFirst().map(WaterwayService::camelize)
                .orElseThrow(() -> new BusinessException(404, "水路运输记录不存在"));
    }

    private void validate(String entity, Map<String, Object> input, String id) {
        switch (entity) {
            case "bookings" -> {
                validateBookingIntention(input, id);
                validateChoice(input, "direction", Set.of("上水", "下水"));
                validateChoice(input, "status", Set.of("待确认", "已确认", "已分配航次", "部分释放", "已取消"));
                var count = integer(input, "containerCount", 0);
                var heavy = integer(input, "heavyCount", 0);
                var empty = integer(input, "emptyCount", 0);
                require(count > 0 && heavy >= 0 && empty >= 0 && heavy + empty <= count, "重柜和空柜数量不能超过订舱总数");
                if (present(input, "voyageId")) {
                    var voyage = get("voyages", String.valueOf(input.get("voyageId")));
                    if (!String.valueOf(voyage.get("direction")).equals(input.get("direction"))) throw new BusinessException(400, "订舱方向与航次方向不一致");
                    if ("已取消".equals(input.get("status"))) break;
                    var booked = scalar("select coalesce(sum(container_count - released_count), 0) from waterway_bookings where voyage_id = ? and status <> '已取消' and id <> coalesce(?, '')", input.get("voyageId"), id);
                    var capacity = integer(voyage, "availableCapacity", 0);
                    var releaseCount = integer(input, "releasedCount", 0);
                    if (booked + count - releaseCount > capacity) throw new BusinessException(409, "本航次剩余舱位不足，请调整订舱数量或航次");
                }
            }
            case "vessels" -> {
                var design = integer(input, "designCapacity", -1);
                var available = integer(input, "availableCapacity", -1);
                require(design > 0 && available >= 0 && available <= design, "可用容量不能超过设计载柜量");
                if (present(input, "designCapacity")) {
                    var assigned = scalar("select count(*) from waterway_voyages where vessel_id = ? and available_capacity > ? and status not in ('已完成', '已取消')", id, design);
                    if (assigned > 0) throw new BusinessException(409, "船舶已有航次使用更高容量，不能降低设计载柜量");
                }
            }
            case "voyages" -> {
                validateChoice(input, "direction", Set.of("上水", "下水"));
                validateChoice(input, "status", Set.of("计划中", "订舱中", "待装船", "装船中", "待发船", "运输中", "已到港", "已完成", "已取消"));
                if (input.containsKey("availableCapacity") && input.containsKey("vesselId")) {
                    var vessel = get("vessels", String.valueOf(input.get("vesselId")));
                    require(integer(input, "availableCapacity", 0) <= integer(vessel, "designCapacity", 0), "本航次容量不能超过船舶设计容量");
                }
                if (input.containsKey("availableCapacity")) {
                    var capacity = integer(input, "availableCapacity", 0);
                    var booked = scalar("select coalesce(sum(container_count - released_count), 0) from waterway_bookings where voyage_id = ? and status <> '已取消' and id <> coalesce(?, '')", id, "");
                    var returns = scalar("select count(*) from waterway_empty_returns where return_voyage_id = ? and status <> '已取消'", id);
                    require(capacity >= booked + returns, "本航次容量不能低于已分配订舱和空柜返运数量");
                }
                if ("运输中".equals(input.get("status"))) validateReadyToDepart(id);
                if (present(input, "voyageNo")) {
                    var duplicate = scalar("select count(*) from waterway_voyages where voyage_no = ? and id <> coalesce(?, '')", input.get("voyageNo"), id);
                    if (duplicate > 0) throw new BusinessException(409, "航次号已存在");
                }
            }
            case "containers" -> {
                validateChoice(input, "state", Set.of("重柜", "空柜"));
                validateChoice(input, "gateStatus", Set.of("待进港", "已进港", "已确认到港"));
                validateChoice(input, "reviewStatus", Set.of("待审核", "已审核", "退回修改"));
                if ("已确认到港".equals(input.get("gateStatus")) && !"已审核".equals(input.getOrDefault("reviewStatus", "已审核"))) {
                    throw new BusinessException(409, "集装箱资料审核通过后才能确认到港");
                }
                if (present(input, "bookingId")) {
                    var booking = get("bookings", String.valueOf(input.get("bookingId")));
                    if (present(input, "voyageId") && !String.valueOf(booking.get("voyageId")).equals(input.get("voyageId"))) {
                        throw new BusinessException(400, "集装箱航次必须与订舱分配航次一致");
                    }
                    if (present(input, "customerId") && !String.valueOf(booking.get("customerId")).equals(input.get("customerId"))) {
                        throw new BusinessException(400, "集装箱所属客户必须与订舱客户一致");
                    }
                    var registered = scalar("select count(*) from waterway_containers where booking_id = ? and id <> coalesce(?, '') and transport_status <> '预约已释放'", input.get("bookingId"), id);
                    require(registered < integer(booking, "containerCount", 0) - integer(booking, "releasedCount", 0), "该订舱已登记完有效柜数");
                }
                if (present(input, "voyageId")) {
                    var assigned = scalar("select count(*) from waterway_containers where voyage_id = ? and booking_id is null and transport_status <> '预约已释放' and id <> coalesce(?, '')", input.get("voyageId"), id);
                    var booked = scalar("select coalesce(sum(container_count - released_count), 0) from waterway_bookings where voyage_id = ? and status <> '已取消'", input.get("voyageId"));
                    var returns = scalar("select count(*) from waterway_empty_returns where return_voyage_id = ? and status <> '已取消'", input.get("voyageId"));
                    var capacity = scalar("select available_capacity from waterway_voyages where id = ?", input.get("voyageId"));
                    if (!present(input, "bookingId") && booked + returns + assigned + 1 > capacity) throw new BusinessException(409, "本航次剩余舱位不足，不能继续分配集装箱");
                }
                if (present(input, "containerNo")) {
                    var duplicate = scalar("select count(*) from waterway_containers where container_no = ? and id <> coalesce(?, '')", input.get("containerNo"), id);
                    if (duplicate > 0) throw new BusinessException(409, "集装箱柜号已登记");
                }
            }
            case "vehicles" -> validateChoice(input, "gateStatus", Set.of("待进场", "已进场", "已完成接驳"));
            case "loading" -> {
                validateChoice(input, "status", Set.of("待吊柜", "吊柜中", "吊柜已完成", "已装船", "异常"));
                if (present(input, "containerId")) {
                    var container = get("containers", String.valueOf(input.get("containerId")));
                    var returnPlan = scalar("select count(*) from waterway_empty_returns where container_id = ? and return_voyage_id = ? and status = '已确认'", input.get("containerId"), input.get("voyageId"));
                    if (( !"已审核".equals(container.get("reviewStatus")) || !"已确认到港".equals(container.get("gateStatus"))) && returnPlan == 0) {
                        throw new BusinessException(409, "集装箱资料审核通过并确认到港后才能吊柜");
                    }
                }
                if ("已装船".equals(input.get("status"))) validateLoadingCapacity(input, id);
            }
            case "emptyReturns" -> {
                validateChoice(input, "status", Set.of("待确认", "已确认", "已取消"));
                if (input.containsKey("containerId")) require(present(input, "containerId"), "请选择空柜");
                if (input.containsKey("returnVoyageId")) require(present(input, "returnVoyageId"), "请选择计划返运航次");
                if (present(input, "containerId")) {
                    var container = get("containers", String.valueOf(input.get("containerId")));
                    require("空柜".equals(container.get("state")), "只能安排空柜返运");
                }
                if (present(input, "returnVoyageId")) validateReturnCapacity(input, id);
            }
            default -> { }
        }
    }

    private void validateReadyToDepart(String voyageId) {
        var unarrived = scalar("""
                select count(*) from waterway_bookings b
                where b.voyage_id = ? and b.status <> '已取消'
                  and (select count(*) from waterway_containers c where c.booking_id = b.id and c.gate_status = '已确认到港') < b.container_count - b.released_count
                """, voyageId);
        if (unarrived > 0) throw new BusinessException(409, "仍有订舱集装箱未全部确认到港，不能发船");
        var unreviewed = scalar("select count(*) from waterway_containers where voyage_id = ? and (review_status <> '已审核' or gate_status <> '已确认到港')", voyageId);
        if (unreviewed > 0) throw new BusinessException(409, "航次内仍有集装箱未审核或未确认到港，不能发船");
        var notLoaded = scalar("""
                select count(*) from waterway_containers c
                where c.voyage_id = ? and not exists (
                  select 1 from waterway_loading_operations l where l.voyage_id = c.voyage_id
                    and l.container_id = c.id and l.status = '已装船')
                """, voyageId);
        if (notLoaded > 0) throw new BusinessException(409, "仍有已到港集装箱未完成装船确认，不能发船");
        var unconfirmedReturns = scalar("select count(*) from waterway_empty_returns where return_voyage_id = ? and status = '已确认' and not exists (select 1 from waterway_loading_operations l where l.voyage_id = ? and l.container_id = waterway_empty_returns.container_id and l.status = '已装船')", voyageId, voyageId);
        if (unconfirmedReturns > 0) throw new BusinessException(409, "仍有计划返运空柜未完成装船确认，不能发船");
    }

    private void validateReturnCapacity(Map<String, Object> input, String id) {
        var voyageId = String.valueOf(input.get("returnVoyageId"));
        var voyage = get("voyages", voyageId);
        var booked = scalar("select coalesce(sum(container_count - released_count), 0) from waterway_bookings where voyage_id = ? and status <> '已取消'", voyageId);
        var returns = scalar("select count(*) from waterway_empty_returns where return_voyage_id = ? and status <> '已取消' and id <> coalesce(?, '')", voyageId, id);
        if (booked + returns + 1 > integer(voyage, "availableCapacity", 0)) throw new BusinessException(409, "空柜返运计划超出本航次剩余容量");
    }

    private void validateLoadingCapacity(Map<String, Object> input, String id) {
        var voyageId = String.valueOf(input.get("voyageId"));
        var loaded = scalar("select count(*) from waterway_loading_operations where voyage_id = ? and status = '已装船' and id <> coalesce(?, '')", voyageId, id);
        var capacity = scalar("select available_capacity from waterway_voyages where id = ?", voyageId);
        if (loaded + 1 > capacity) throw new BusinessException(409, "本航次可用舱位不足，不能确认装船");
        var duplicate = scalar("select count(*) from waterway_loading_operations where voyage_id = ? and container_id = ? and status = '已装船' and id <> coalesce(?, '')", voyageId, input.get("containerId"), id);
        if (duplicate > 0) throw new BusinessException(409, "该集装箱已在本航次确认装船");
        var container = get("containers", String.valueOf(input.get("containerId")));
        var returnPlan = scalar("select count(*) from waterway_empty_returns where container_id = ? and return_voyage_id = ? and status = '已确认'", input.get("containerId"), voyageId);
        if (container.get("voyageId") != null && !voyageId.equals(container.get("voyageId")) && returnPlan == 0) {
            throw new BusinessException(400, "集装箱所属航次与装船作业不一致");
        }
    }

    private int references(String entity, String id) {
        return switch (entity) {
            case "customers" -> scalar("select count(*) from waterway_bookings where customer_id = ?", id)
                    + scalar("select count(*) from waterway_containers where customer_id = ?", id)
                    + scalar("select count(*) from contracts where customer_id = ?", id)
                    + scalar("select count(*) from customer_intentions where customer_id = ?", id);
            case "vessels" -> scalar("select count(*) from waterway_voyages where vessel_id = ?", id)
                    + scalar("select count(*) from waterway_bookings where preferred_vessel_id = ?", id);
            case "voyages" -> scalar("select count(*) from waterway_bookings where voyage_id = ?", id)
                    + scalar("select count(*) from waterway_containers where voyage_id = ?", id)
                    + scalar("select count(*) from waterway_loading_operations where voyage_id = ?", id)
                    + scalar("select count(*) from waterway_empty_returns where return_voyage_id = ? or source_voyage_id = ?", id, id);
            case "containers" -> scalar("select count(*) from waterway_loading_operations where container_id = ?", id)
                    + scalar("select count(*) from waterway_vehicles where container_id = ?", id)
                    + scalar("select count(*) from waterway_empty_returns where container_id = ?", id);
            case "vehicles" -> scalar("select count(*) from waterway_loading_operations where vehicle_id = ?", id)
                    + scalar("select count(*) from waterway_containers where vehicle_id = ?", id);
            default -> 0;
        };
    }

    private void requireFields(String entity, Map<String, Object> input, boolean creating) {
        var allowed = "customers".equals(entity)
                ? Set.of("name", "type", "contact", "phone", "status", "contract", "notes")
                : WRITABLE.get(entity).keySet();
        if (input.keySet().stream().noneMatch(allowed::contains)) throw new BusinessException(400, "请求中没有可保存的字段");
        if (!creating) return;
        Set<String> required = switch (entity) {
            case "customers" -> Set.of("name");
            case "bookings" -> Set.of("customerId", "intentionId", "direction", "plannedDate", "containerCount");
            case "vessels" -> Set.of("name", "designCapacity", "availableCapacity");
            case "voyages" -> Set.of("voyageNo", "vesselId", "plannedDeparture", "availableCapacity");
            case "containers" -> Set.of("containerNo");
            case "vehicles" -> Set.of("plate");
            case "loading" -> Set.of("voyageId", "containerId");
            case "emptyReturns" -> Set.of("containerId", "returnVoyageId");
            default -> Set.of();
        };
        for (var field : required) if (!present(input, field)) throw new BusinessException(400, "请填写" + field);
    }

    private void validateBookingIntention(Map<String, Object> input, String id) {
        var exists = id != null && scalar("select count(*) from waterway_bookings where id = ?", id) > 0;
        var currentCustomerId = exists
                ? jdbc.queryForObject("select customer_id from waterway_bookings where id = ?", String.class, id) : null;
        var currentIntentionId = exists
                ? jdbc.queryForObject("select intention_id from waterway_bookings where id = ?", String.class, id) : null;
        var customerId = input.containsKey("customerId") ? input.get("customerId") : currentCustomerId;
        var intentionId = input.containsKey("intentionId") ? input.get("intentionId") : currentIntentionId;
        var mustValidate = !exists || input.containsKey("customerId") || input.containsKey("intentionId");
        if (!mustValidate) return;
        require(customerId != null && intentionId != null && !customerId.toString().isBlank()
                && !intentionId.toString().isBlank(), "订舱必须关联客户的水路运输意向");
        var matched = scalar("""
                select count(*) from customer_intentions
                where id = ? and customer_id = ? and business = '运输服务'
                  and business_detail = '水路运输' and stage not in ('已流失', '已终止')
                """, intentionId, customerId);
        require(matched == 1, "所选意向必须属于该客户，且为有效的水路运输服务意向");
    }

    private void validateChoice(Map<String, Object> input, String key, Set<String> allowed) {
        if (input.containsKey(key) && input.get(key) != null && !allowed.contains(input.get(key).toString())) throw new BusinessException(400, key + "不合法");
    }

    private static Map<String, Object> camelize(Map<String, Object> row) {
        var result = new LinkedHashMap<String, Object>();
        row.forEach((key, value) -> result.put(toCamelCase(key), value));
        return result;
    }

    private static String toCamelCase(String key) {
        var result = new StringBuilder();
        var upper = false;
        for (var character : key.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            if (character == '_') upper = true;
            else {
                result.append(upper ? Character.toUpperCase(character) : character);
                upper = false;
            }
        }
        return result.toString();
    }

    private static Map<String, String> columns(String... values) {
        var result = new LinkedHashMap<String, String>();
        for (int index = 0; index < values.length; index += 2) result.put(values[index], values[index + 1]);
        return Map.copyOf(result);
    }

    private String select(String entity) {
        requireEntity(entity);
        return SELECTS.get(entity);
    }

    private void requireEntity(String entity) {
        if (!SELECTS.containsKey(entity) || !WRITABLE.containsKey(entity)) throw new BusinessException(404, "水路运输业务模块不存在");
    }

    private String table(String entity) {
        return switch (entity) {
            case "bookings" -> "waterway_bookings";
            case "vessels" -> "waterway_vessels";
            case "voyages" -> "waterway_voyages";
            case "containers" -> "waterway_containers";
            case "vehicles" -> "waterway_vehicles";
            case "loading" -> "waterway_loading_operations";
            case "emptyReturns" -> "waterway_empty_returns";
            default -> throw new BusinessException(404, "水路运输业务模块不存在");
        };
    }

    private String idColumn(String entity) { return "customers".equals(entity) ? "c.id" : switch (entity) {
        case "bookings" -> "b.id";
        case "vessels" -> "id";
        case "voyages" -> "v.id";
        case "containers" -> "c.id";
        case "vehicles" -> "t.id";
        case "loading" -> "l.id";
        case "emptyReturns" -> "r.id";
        default -> throw new BusinessException(404, "水路运输业务模块不存在");
    }; }

    private int scalar(String sql, Object... args) {
        var result = jdbc.queryForObject(sql, Integer.class, args);
        return result == null ? 0 : result;
    }

    private static int integer(Map<String, Object> input, String key, int fallback) {
        var value = input.get(key);
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? fallback : Integer.parseInt(value.toString()); }
        catch (NumberFormatException exception) { throw new BusinessException(400, key + "必须是整数"); }
    }

    private static String value(Map<String, Object> input, String key) {
        var value = input.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private static String valueOr(Map<String, Object> input, String key, String fallback) {
        var result = value(input, key);
        return result.isBlank() ? fallback : result;
    }

    private static Object nullable(Map<String, Object> input, String key) {
        return input.containsKey(key) ? normalize(input.get(key)) : null;
    }

    private static Object normalize(Object value) {
        return value instanceof String text && text.isBlank() ? null : value;
    }

    private static boolean present(Map<String, Object> input, String key) {
        var value = input.get(key);
        return value != null && !value.toString().isBlank();
    }

    private static String codePrefix(String entity) {
        return switch (entity) {
            case "bookings" -> "WB";
            case "vessels" -> "WV";
            case "voyages" -> "WVO";
            case "loading" -> "WLO";
            default -> "WR";
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new BusinessException(400, message);
    }
}
