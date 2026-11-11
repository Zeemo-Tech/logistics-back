package org.example.logisticsback.waterway;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.io.IOException;
import java.util.LinkedHashMap;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.example.logisticsback.common.BusinessException;
import org.springframework.web.multipart.MultipartFile;
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
                    "customsCode", "customs_code", "englishName", "english_name", "status", "status", "notes", "notes"),
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
                    "operator", "operator", "status", "status", "loadedAt", "loaded_at", "queueNo", "queue_no",
                    "plannedAt", "planned_at", "rescheduledAt", "rescheduled_at", "delayReason", "delay_reason",
                    "customerNotified", "customer_notified", "notes", "notes"),
            "emptyReturns", columns(
                    "containerId", "container_id", "planId", "plan_id", "customerId", "customer_id", "currentPort", "current_port",
                    "sourceVoyageId", "source_voyage_id", "returnVoyageId", "return_voyage_id",
                    "confirmedAt", "confirmed_at", "status", "status", "notes", "notes"),
            "returnPlans", columns(
                    "customerId", "customer_id", "currentPort", "current_port", "sourceVoyageId", "source_voyage_id",
                    "returnVoyageId", "return_voyage_id", "plannedDate", "planned_date", "plannedCount", "planned_count",
                    "status", "status", "notes", "notes"),
            "fees", columns(
                    "voyageId", "voyage_id", "containerId", "container_id", "category", "category", "amount", "amount",
                    "currency", "currency", "payer", "payer", "payee", "payee", "status", "status",
                    "incurredAt", "incurred_at", "invoiceNo", "invoice_no", "notes", "notes"),
            "checkpoints", columns(
                    "voyageId", "voyage_id", "containerId", "container_id", "portName", "port_name",
                    "eventType", "event_type", "occurredAt", "occurred_at", "location", "location",
                    "temperature", "temperature", "setTemperature", "set_temperature",
                    "supplyAir", "supply_air", "returnAir", "return_air", "latitude", "latitude", "longitude", "longitude",
                    "customerName", "customer_name", "product", "product", "status", "status", "notes", "notes"));

    // Empty form controls are sent as empty strings. Only these database columns are nullable;
    // other text columns are defined NOT NULL and must retain an empty-string value.
    private static final Map<String, Set<String>> NULLABLE_FIELDS = Map.of(
            "bookings", Set.of("preferredVesselId", "voyageId"),
            "containers", Set.of("customerId", "voyageId", "bookingId", "vehicleId", "setTemperature"),
            "vehicles", Set.of("containerId", "voyageId"),
            "loading", Set.of("vehicleId"),
            "emptyReturns", Set.of("planId", "customerId", "sourceVoyageId", "returnVoyageId"),
            "returnPlans", Set.of("customerId", "sourceVoyageId"),
            "fees", Set.of("containerId"),
            "checkpoints", Set.of("containerId", "temperature", "setTemperature", "supplyAir", "returnAir", "latitude", "longitude"));

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
                    """,
            "checkpoints", """
                    select p.*, v.voyage_no, s.name as vessel_name, c.container_no
                    from waterway_transport_checkpoints p
                    join waterway_voyages v on v.id = p.voyage_id
                    join waterway_vessels s on s.id = v.vessel_id
                    left join waterway_containers c on c.id = p.container_id
                    """);

    private static final Map<String, String> ADDITIONAL_SELECTS = Map.of(
            "returnPlans", """
                    select p.*, customer.name as customer_name, source.voyage_no as source_voyage_no,
                           target.voyage_no as return_voyage_no,
                           coalesce((select count(*) from waterway_empty_returns r where r.plan_id = p.id and r.status <> '已取消'), 0) as assigned_count,
                           greatest(0, p.planned_count - coalesce((select count(*) from waterway_empty_returns r where r.plan_id = p.id and r.status <> '已取消'), 0)) as pending_count
                    from waterway_empty_return_plans p left join customers customer on customer.id = p.customer_id
                    left join waterway_voyages source on source.id = p.source_voyage_id
                    left join waterway_voyages target on target.id = p.return_voyage_id
                    """,
            "fees", """
                    select f.*, v.voyage_no, c.container_no from waterway_fees f
                    join waterway_voyages v on v.id = f.voyage_id
                    left join waterway_containers c on c.id = f.container_id
                    """);

    private final JdbcTemplate jdbc;

    public WaterwayService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(String entity, String keyword) {
        var select = select(entity);
        var rows = jdbc.queryForList(select + ("checkpoints".equals(entity) ? " order by p.occurred_at desc, p.created_at desc" : " order by 1"));
        if (keyword == null || keyword.isBlank()) return rows.stream().map(WaterwayService::camelize).toList();
        var query = keyword.trim().toLowerCase();
        return rows.stream()
                .filter(row -> row.values().stream().anyMatch(value -> value != null && value.toString().toLowerCase().contains(query)))
                .map(WaterwayService::camelize)
                .toList();
    }

    public List<Map<String, Object>> voyageDocuments(String voyageId) {
        get("voyages", voyageId);
        return jdbc.queryForList("select id, voyage_id as voyage_id, document_type, file_name, mime_type, version_no, uploaded_by, uploaded_at, review_status, submit_status from waterway_voyage_documents where voyage_id = ? order by uploaded_at desc, version_no desc", voyageId).stream().map(WaterwayService::camelize).toList();
    }

    @Transactional
    public Map<String, Object> uploadVoyageDocument(String voyageId, String documentType, String uploadedBy, MultipartFile file) {
        get("voyages", voyageId);
        require(Set.of("集装箱计划表", "货物信息登记表").contains(documentType), "请选择海关资料类型");
        require(file != null && !file.isEmpty() && file.getSize() <= 20L * 1024 * 1024, "文件不能为空且不得超过 20 MB");
        var name = file.getOriginalFilename() == null ? "资料文件" : file.getOriginalFilename().replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        require(name.matches("(?i).+\\.(xlsx|xls|csv)"), "仅支持 XLS、XLSX 或 CSV 文件");
        try {
            var version = scalar("select coalesce(max(version_no), 0) from waterway_voyage_documents where voyage_id = ? and document_type = ?", voyageId, documentType) + 1;
            var id = UUID.randomUUID().toString();
            var mimeType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
            var uploadedAt = java.time.LocalDateTime.now().toString().replace('T', ' ');
            jdbc.update("insert into waterway_voyage_documents (id, voyage_id, document_type, file_name, mime_type, version_no, uploaded_by, uploaded_at, file_bytes) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, voyageId, documentType, name, mimeType, version, uploadedBy == null ? "" : uploadedBy, uploadedAt, file.getBytes());
            return jdbc.queryForMap("select id, voyage_id as voyage_id, document_type, file_name, mime_type, version_no, uploaded_by, uploaded_at, review_status, submit_status from waterway_voyage_documents where id = ?", id);
        } catch (IOException exception) {
            throw new BusinessException(400, "资料文件读取失败");
        }
    }

    public byte[] voyageDocumentFile(String id) {
        var rows = jdbc.queryForList("select file_bytes from waterway_voyage_documents where id = ?", id);
        if (rows.isEmpty()) throw new BusinessException(404, "航次资料不存在");
        return (byte[]) rows.get(0).get("FILE_BYTES");
    }

    public Map<String, Object> voyageDocumentMetadata(String id) {
        return jdbc.queryForList("select id, file_name, mime_type from waterway_voyage_documents where id = ?", id).stream().findFirst()
                .orElseThrow(() -> new BusinessException(404, "航次资料不存在"));
    }

    @Transactional
    public Map<String, Object> importSpreadsheet(String entity, MultipartFile file) {
        require(Set.of("containers", "vehicles").contains(entity), "仅支持导入集装箱和车辆资料");
        require(file != null && !file.isEmpty() && file.getSize() <= 20L * 1024 * 1024, "请选择不超过 20 MB 的表格");
        try (var workbook = WorkbookFactory.create(file.getInputStream())) {
            var sheet = workbook.getSheetAt(0);
            require(sheet.getPhysicalNumberOfRows() > 1, "表格没有可导入的数据");
            var formatter = new DataFormatter();
            var header = sheet.getRow(0);
            var columns = new LinkedHashMap<String, Integer>();
            for (int index = 0; index < header.getLastCellNum(); index++) {
                var key = importField(entity, formatter.formatCellValue(header.getCell(index)).trim());
                if (key != null) columns.put(key, index);
            }
            require(columns.containsKey("containerNo") || columns.containsKey("plate"), "未识别柜号或车牌列，请使用导入模板列名");
            var imported = 0;
            var errors = new java.util.ArrayList<String>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                var row = sheet.getRow(rowIndex);
                if (row == null) continue;
                var input = new LinkedHashMap<String, Object>();
                columns.forEach((key, index) -> input.put(key, formatter.formatCellValue(row.getCell(index)).trim()));
                if (input.values().stream().allMatch(value -> value == null || value.toString().isBlank())) continue;
                try {
                    if ("vehicles".equals(entity) && input.containsKey("containerNo")) {
                        var container = jdbc.queryForList("select id, voyage_id from waterway_containers where container_no = ?", input.get("containerNo").toString());
                        if (container.isEmpty()) throw new BusinessException(400, "柜号未登记");
                        input.put("containerId", container.get(0).get("ID"));
                        if (!input.containsKey("voyageId")) input.put("voyageId", container.get(0).get("VOYAGE_ID"));
                        input.remove("containerNo");
                    }
                    create(entity, input);
                    imported++;
                } catch (RuntimeException exception) {
                    errors.add("第 " + (rowIndex + 1) + " 行：" + (exception instanceof BusinessException ? exception.getMessage() : "数据校验失败"));
                }
            }
            return Map.of("imported", imported, "errors", errors);
        } catch (IOException exception) {
            throw new BusinessException(400, "表格解析失败");
        }
    }

    private String importField(String entity, String header) {
        var common = Map.ofEntries(Map.entry("柜号", "containerNo"), Map.entry("集装箱柜号", "containerNo"), Map.entry("TEU NO.", "containerNo"),
                Map.entry("货物", "cargo"), Map.entry("货物名称", "cargo"), Map.entry("Product", "cargo"), Map.entry("重量", "weight"),
                Map.entry("提单号", "billOfLading"), Map.entry("客户", "customerId"), Map.entry("航次ID", "voyageId"),
                Map.entry("车牌", "plate"), Map.entry("车牌号", "plate"), Map.entry("挂车", "trailer"), Map.entry("挂车牌", "trailer"),
                Map.entry("司机", "driver"), Map.entry("司机姓名", "driver"), Map.entry("电话", "phone"), Map.entry("联系电话", "phone"),
                Map.entry("车主", "fleet"), Map.entry("进港时间", "actualGateIn"), Map.entry("计划进港时间", "expectedArrival"));
        var field = common.get(header);
        return field != null && (!"containers".equals(entity) || !Set.of("plate", "trailer", "driver", "phone", "fleet", "actualGateIn", "expectedArrival").contains(field))
                && (!"vehicles".equals(entity) || !Set.of("cargo", "weight", "billOfLading", "customerId").contains(field)) ? field : null;
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
        input = withCheckpointContext(entity, withInferredCustomer(entity, input), null);
        if ("customers".equals(entity)) throw new BusinessException(405, "客户档案请在资产 > 客户与合同中统一维护");
        var columns = WRITABLE.get(entity);
        requireFields(entity, input, true);
        validate(entity, input, null);
        var id = UUID.randomUUID().toString();
        var code = codePrefix(entity) + "-" + id.substring(0, 8).toUpperCase();
        var values = new LinkedHashMap<String, Object>();
        values.put("id", id);
        if (Set.of("bookings", "vessels", "voyages", "loading", "checkpoints", "returnPlans", "fees").contains(entity)) values.put("code", code);
        for (var entry : columns.entrySet()) {
            if (input.containsKey(entry.getKey())) {
                values.put(entry.getValue(), normalize(entity, entry.getKey(), input.get(entry.getKey())));
            }
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
        input = withCheckpointContext(entity, withInferredCustomer(entity, input), id);
        if ("customers".equals(entity)) throw new BusinessException(405, "客户档案请在资产 > 客户与合同中统一维护");
        var columns = WRITABLE.get(entity);
        requireFields(entity, input, false);
        validate(entity, input, id);
        var assignments = new ArrayList<String>();
        var args = new ArrayList<Object>();
        for (var entry : columns.entrySet()) {
            if (!input.containsKey(entry.getKey())) continue;
            assignments.add(entry.getValue() + " = ?");
            args.add(normalize(entity, entry.getKey(), input.get(entry.getKey())));
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

    // Snapshot the inspected cargo/customer so subsequent archive edits do not rewrite history.
    private Map<String, Object> withCheckpointContext(String entity, Map<String, Object> input, String id) {
        if (!"checkpoints".equals(entity)) return input;
        var result = new LinkedHashMap<>(input);
        var previous = id == null ? Map.<String, Object>of() : get(entity, id);
        var containerId = input.containsKey("containerId") ? value(input, "containerId") : value(previous, "containerId");
        var changed = id == null || !containerId.equals(value(previous, "containerId"));
        if (changed) {
            var container = containerId.isBlank() ? Map.<String, Object>of() : get("containers", containerId);
            result.put("customerName", value(container, "customerName"));
            result.put("product", value(container, "cargo"));
            if (!input.containsKey("setTemperature")) result.put("setTemperature", container.get("setTemperature"));
        } else {
            result.put("customerName", previous.get("customerName"));
            result.put("product", previous.get("product"));
        }
        return result;
    }

    private Map<String, Object> withInferredCustomer(String entity, Map<String, Object> input) {
        if (!Set.of("containers", "emptyReturns").contains(entity)) return input;
        var result = new LinkedHashMap<>(input);
        Object customerId = null;
        if ("containers".equals(entity) && present(input, "bookingId")) {
            var booking = get("bookings", value(input, "bookingId"));
            customerId = booking.get("customerId");
            if (!present(input, "voyageId") && booking.get("voyageId") != null) result.put("voyageId", booking.get("voyageId"));
        } else if ("emptyReturns".equals(entity) && present(input, "containerId")) {
            customerId = get("containers", value(input, "containerId")).get("customerId");
        } else if ("containers".equals(entity) && present(input, "voyageId")) {
            var ids = jdbc.queryForList("select distinct customer_id from waterway_bookings where voyage_id = ? and status <> '已取消'", String.class, input.get("voyageId"));
            if (ids.size() == 1) customerId = ids.get(0);
        }
        if (customerId != null) {
            require(!present(input, "customerId") || customerId.equals(input.get("customerId")), "客户必须与订舱或集装箱所属客户一致");
            result.put("customerId", customerId);
        }
        return result;
    }

    private void validate(String entity, Map<String, Object> input, String id) {
        if ("checkpoints".equals(entity) && id != null) {
            var merged = new LinkedHashMap<>(get(entity, id));
            merged.putAll(input);
            input = merged;
            requireFields(entity, input, true);
        }
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
                if (present(input, "planId")) get("returnPlans", value(input, "planId"));
                if (present(input, "returnVoyageId")) validateReturnCapacity(input, id);
            }
            case "returnPlans" -> {
                validateChoice(input, "status", Set.of("待确认", "待补充", "已确认", "已取消"));
                require(integer(input, "plannedCount", 0) > 0, "计划空柜数必须大于 0");
                if (present(input, "returnVoyageId")) validateReturnPlanCapacity(input, id);
            }
            case "fees" -> {
                require(present(input, "voyageId") && present(input, "category") && present(input, "amount"), "请填写航次、费用项目和金额");
                get("voyages", value(input, "voyageId"));
                require(decimal(input, "amount") >= 0, "费用金额不能小于 0");
                validateChoice(input, "status", Set.of("待对账", "已对账", "已支付", "已取消"));
            }
            case "checkpoints" -> {
                for (var field : Set.of("portName", "occurredAt")) {
                    require(present(input, field), "请填写港口和记录时间");
                }
                validateChoice(input, "eventType", Set.of("到港", "离港", "中转", "温度检查", "异常"));
                validateChoice(input, "status", Set.of("正常", "异常", "待复核"));
                if (present(input, "voyageId")) get("voyages", String.valueOf(input.get("voyageId")));
                if (present(input, "containerId")) {
                    var container = get("containers", String.valueOf(input.get("containerId")));
                    var voyageId = value(input, "voyageId");
                    var linked = voyageId.equals(container.get("voyageId"))
                            || scalar("select count(*) from waterway_loading_operations where voyage_id = ? and container_id = ? and status = '已装船'", voyageId, input.get("containerId")) > 0
                            || scalar("select count(*) from waterway_empty_returns where return_voyage_id = ? and container_id = ? and status = '已确认'", voyageId, input.get("containerId")) > 0;
                    require(linked, "集装箱必须属于所选航次或该航次已确认的空柜返运计划");
                }
                try {
                    java.time.LocalDateTime.parse(value(input, "occurredAt"), java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT));
                } catch (java.time.DateTimeException exception) {
                    throw new BusinessException(400, "记录时间格式应为 yyyy-MM-dd HH:mm");
                }
                require(value(input, "portName").length() <= 160 && value(input, "location").length() <= 240 && value(input, "notes").length() <= 1000, "港口、位置或备注内容过长");
                for (var field : Set.of("temperature", "setTemperature", "supplyAir", "returnAir")) {
                    if (!present(input, field)) continue;
                    require(present(input, "containerId"), "记录温度时请选择对应集装箱");
                    var temperature = decimal(input, field);
                    require(Double.isFinite(temperature) && temperature >= -80 && temperature <= 80, "温度应在 -80℃ 至 80℃ 之间");
                }
                require(present(input, "latitude") == present(input, "longitude"), "GPS 经度和纬度请一起填写");
                for (var field : Set.of("latitude", "longitude")) {
                    if (!present(input, field)) continue;
                    var coordinate = decimal(input, field);
                    var limit = "latitude".equals(field) ? 90 : 180;
                    require(Double.isFinite(coordinate) && coordinate >= -limit && coordinate <= limit,
                            "latitude".equals(field) ? "纬度应在 -90 至 90 之间" : "经度应在 -180 至 180 之间");
                }
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
        var planned = scalar("select coalesce(sum(greatest(0, p.planned_count - (select count(*) from waterway_empty_returns r where r.plan_id = p.id and r.status <> '已取消'))), 0) from waterway_empty_return_plans p where p.return_voyage_id = ? and p.status <> '已取消'", voyageId);
        var planId = value(input, "planId");
        if (!planId.isBlank()) {
            var plan = get("returnPlans", planId);
            require(voyageId.equals(plan.get("returnVoyageId")), "空柜所选航次与返运计划不一致");
            var alreadyAssigned = scalar("select count(*) from waterway_empty_returns where plan_id = ? and status <> '已取消' and id <> coalesce(?, '')", planId, id);
            require(alreadyAssigned < integer(plan, "plannedCount", 0), "该返运计划的柜号已补齐");
            planned -= Math.max(0, integer(plan, "plannedCount", 0) - alreadyAssigned);
        }
        if (booked + returns + planned + 1 > integer(voyage, "availableCapacity", 0)) throw new BusinessException(409, "空柜返运计划超出本航次剩余容量");
    }

    private void validateReturnPlanCapacity(Map<String, Object> input, String id) {
        var voyageId = value(input, "returnVoyageId");
        var voyage = get("voyages", voyageId);
        var booked = scalar("select coalesce(sum(container_count - released_count), 0) from waterway_bookings where voyage_id = ? and status <> '已取消'", voyageId);
        var returns = scalar("select count(*) from waterway_empty_returns where return_voyage_id = ? and status <> '已取消'", voyageId);
        var plans = scalar("select coalesce(sum(planned_count), 0) from waterway_empty_return_plans where return_voyage_id = ? and status <> '已取消' and id <> coalesce(?, '')", voyageId, id);
        if (booked + returns + plans + integer(input, "plannedCount", 0) > integer(voyage, "availableCapacity", 0)) throw new BusinessException(409, "空柜数量计划超出本航次剩余舱位");
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
                    + scalar("select count(*) from waterway_empty_returns where return_voyage_id = ? or source_voyage_id = ?", id, id)
                    + scalar("select count(*) from waterway_empty_return_plans where return_voyage_id = ? or source_voyage_id = ?", id, id)
                    + scalar("select count(*) from waterway_fees where voyage_id = ?", id)
                    + scalar("select count(*) from waterway_transport_checkpoints where voyage_id = ?", id);
            case "containers" -> scalar("select count(*) from waterway_loading_operations where container_id = ?", id)
                    + scalar("select count(*) from waterway_vehicles where container_id = ?", id)
                    + scalar("select count(*) from waterway_empty_returns where container_id = ?", id)
                    + scalar("select count(*) from waterway_transport_checkpoints where container_id = ?", id);
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
            case "returnPlans" -> Set.of("returnVoyageId", "plannedCount");
            case "fees" -> Set.of("voyageId", "category", "amount");
            case "checkpoints" -> Set.of("voyageId", "portName", "occurredAt");
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
        return SELECTS.containsKey(entity) ? SELECTS.get(entity) : ADDITIONAL_SELECTS.get(entity);
    }

    private void requireEntity(String entity) {
        if (!(SELECTS.containsKey(entity) || ADDITIONAL_SELECTS.containsKey(entity)) || !WRITABLE.containsKey(entity)) throw new BusinessException(404, "水路运输业务模块不存在");
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
            case "returnPlans" -> "waterway_empty_return_plans";
            case "fees" -> "waterway_fees";
            case "checkpoints" -> "waterway_transport_checkpoints";
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
        case "returnPlans" -> "p.id";
        case "fees" -> "f.id";
        case "checkpoints" -> "p.id";
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

    private static double decimal(Map<String, Object> input, String key) {
        var value = input.get(key);
        try { return value instanceof Number number ? number.doubleValue() : Double.parseDouble(value.toString()); }
        catch (RuntimeException exception) { throw new BusinessException(400, key + "必须是数字"); }
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

    private static Object normalize(String entity, String field, Object value) {
        if ("loading".equals(entity) && "customerNotified".equals(field) && value instanceof String text) {
            return "是".equals(text) || "true".equalsIgnoreCase(text);
        }
        if (value instanceof String text && text.isBlank()
                && NULLABLE_FIELDS.getOrDefault(entity, Set.of()).contains(field)) return null;
        return value;
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
            case "checkpoints" -> "WCP";
            default -> "WR";
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new BusinessException(400, message);
    }
}
