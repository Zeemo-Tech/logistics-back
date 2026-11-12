package org.example.logisticsback.rail;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.example.logisticsback.common.BusinessException;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RailService {
    private static final Map<String, Map<String, String>> WRITABLE = writable();

    private static final Set<String> RATE_STATUS = Set.of("生效", "停用");
    private static final Set<String> QUOTE_STATUS = Set.of("草稿", "待审批", "已通过", "已作废");
    private static final Set<String> DEMAND_STATUS = Set.of("草稿", "已提报", "已受理", "已装车", "已发运", "已完成", "已取消");
    private static final Set<String> DISPATCH_STATUS = Set.of("待派车", "已派车", "已到库", "装车中", "已装完", "已完成");
    private static final Set<String> SHIPMENT_STATUS = Set.of("待发运", "运输中", "已到站", "已交付", "异常");
    private static final Set<String> SETTLEMENT_STATUS = Set.of("待对账", "已对账", "已结清");
    private static final Set<String> EXCEPTIONS = Set.of("无", "箱型不足", "限高装不进", "吨数不符", "计划改号");
    private static final Set<String> PROJECT_STATUS = Set.of("生效", "停用");
    private static final Set<Integer> BOX_TYPES = Set.of(58, 60, 70);
    private static final Set<String> DELIVERY_STATUS = Set.of("待配送", "配送中", "已签收", "异常");

    private final JdbcTemplate jdbc;

    public RailService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(String entity, String keyword) {
        var rows = jdbc.queryForList(select(entity) + orderBy(entity));
        var query = keyword == null ? "" : keyword.trim().toLowerCase();
        return rows.stream()
                .filter(row -> query.isBlank() || row.values().stream()
                        .anyMatch(value -> value != null && value.toString().toLowerCase().contains(query)))
                .map(RailService::camelize)
                .toList();
    }

    public List<Map<String, Object>> attachments(String entity, String recordId) {
        requireEntity(entity);
        return jdbc.queryForList("""
                select id, entity, record_id as record_id, doc_type, file_name, mime_type,
                       uploaded_by, uploaded_at
                from rail_attachments where entity = ? and record_id = ?
                order by uploaded_at desc
                """, entity, recordId).stream().map(RailService::camelize).toList();
    }

    @Transactional
    public Map<String, Object> uploadAttachment(String entity, String recordId, String docType,
            String uploadedBy, MultipartFile file) {
        requireEntity(entity);
        var exists = scalar("select count(*) from " + table(entity) + " where id = ?", recordId);
        if (exists == 0) throw new BusinessException(400, "业务记录不存在，不能上传单据");
        if (file == null || file.isEmpty() || file.getSize() > 20L * 1024 * 1024) {
            throw new BusinessException(400, "文件不能为空且不得超过 20 MB");
        }
        var name = file.getOriginalFilename() == null ? "单据文件" : file.getOriginalFilename().replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (!name.matches("(?i).+\\.(pdf|jpg|jpeg|png|xls|xlsx|csv)")) {
            throw new BusinessException(400, "仅支持 PDF、图片、Excel 或 CSV 文件");
        }
        try {
            var id = UUID.randomUUID().toString();
            var mimeType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
            jdbc.update("""
                    insert into rail_attachments (id, entity, record_id, doc_type, file_name, mime_type, file_bytes, uploaded_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, entity, recordId, docType == null || docType.isBlank() ? "业务单据" : docType.trim(),
                    name, mimeType, file.getBytes(), uploadedBy == null ? "" : uploadedBy);
            return camelize(jdbc.queryForMap("""
                    select id, entity, record_id as record_id, doc_type, file_name, mime_type,
                           uploaded_by, uploaded_at from rail_attachments where id = ?
                    """, id));
        } catch (IOException exception) {
            throw new BusinessException(400, "单据文件读取失败");
        }
    }

    public byte[] attachmentFile(String id) {
        var rows = jdbc.queryForList("select file_bytes from rail_attachments where id = ?", id);
        if (rows.isEmpty()) throw new BusinessException(404, "单据不存在");
        return (byte[]) rows.get(0).get("FILE_BYTES");
    }

    public Map<String, Object> attachmentMetadata(String id) {
        return jdbc.queryForList("select id, file_name, mime_type from rail_attachments where id = ?", id)
                .stream().findFirst().orElseThrow(() -> new BusinessException(404, "单据不存在"));
    }

    @Transactional
    public void deleteAttachment(String id) {
        if (jdbc.update("delete from rail_attachments where id = ?", id) == 0) {
            throw new BusinessException(404, "单据不存在");
        }
    }

    public Map<String, Object> get(String entity, String id) {
        var rows = jdbc.queryForList(select(entity) + " where " + idColumn(entity) + " = ?", id);
        if (rows.isEmpty()) throw new BusinessException(404, "火车运输记录不存在");
        return camelize(rows.get(0));
    }

    public Map<String, Object> overview() {
        var result = new LinkedHashMap<String, Object>();
        result.put("planCars", scalar("select count(*) from rail_demands where status not in ('已完成', '已取消')"));
        result.put("planTons", decimal("select coalesce(sum(tons), 0) from rail_demands where status not in ('已完成', '已取消')"));
        var waitingDispatch = scalar("select count(*) from rail_dispatches where status in ('待派车', '已派车')");
        var noDispatch = scalar("""
                select count(*) from rail_demands d
                where d.status not in ('已完成', '已取消')
                  and not exists (select 1 from rail_dispatches p where p.demand_id = d.id and p.status not in ('已完成', '已取消'))
                """);
        result.put("pendingDispatches", waitingDispatch + noDispatch);
        result.put("loadedCars", scalar("select count(*) from rail_dispatches where status = '已装完'"));
        result.put("inTransitCars", scalar("select count(*) from rail_shipments where status = '运输中'"));
        result.put("lowLoads", scalar("select count(*) from rail_demands where tons < 55 and status not in ('已完成', '已取消')"));
        result.put("overHeight", scalar("select count(*) from rail_demands where stack_height > 2.6 and status not in ('已完成', '已取消')"));
        result.put("grossProfit", decimal("select coalesce(sum(gross_profit), 0) from rail_settlements"));
        result.put("pendingDeliveries", scalar("select count(*) from rail_deliveries where status in ('待配送', '配送中')"));
        result.put("recentDemands", list("demands", null).stream().limit(6).toList());
        var trend = new ArrayList<>(jdbc.queryForList("""
                select load_date as date, count(*) as cars, coalesce(sum(tons), 0) as tons
                from rail_demands group by load_date order by load_date desc limit 7
                """));
        java.util.Collections.reverse(trend);
        result.put("trend", trend.stream().map(RailService::camelize).toList());
        result.put("boxTypes", jdbc.queryForList("""
                select box_type, count(*) as cars, coalesce(sum(tons), 0) as tons
                from rail_demands group by box_type order by box_type desc
                """).stream().map(RailService::camelize).toList());
        result.put("routeTons", jdbc.queryForList("""
                select from_point, to_station, coalesce(sum(tons), 0) as tons
                from rail_demands group by from_point, to_station order by tons desc limit 6
                """).stream().map(RailService::camelize).toList());
        result.put("statusCounts", jdbc.queryForList("""
                select status, count(*) as count from rail_demands
                group by status order by count desc
                """).stream().map(RailService::camelize).toList());
        result.put("alerts", alerts());
        return result;
    }

    private List<Map<String, Object>> alerts() {
        var alerts = new ArrayList<Map<String, Object>>();
        jdbc.queryForList("""
                select d.code, d.from_point, d.to_station, d.tons, d.stack_height,
                       exists (select 1 from rail_dispatches p where p.demand_id = d.id
                               and p.status not in ('已完成', '已取消')) as dispatched
                from rail_demands d
                where d.status not in ('已完成', '已取消')
                order by d.load_date
                """).stream().limit(6).forEach(row -> {
                    var reasons = new ArrayList<String>();
                    var tons = decimalOf(row.get("TONS"));
                    var height = decimalOf(row.get("STACK_HEIGHT"));
                    if (tons.compareTo(BigDecimal.valueOf(55)) < 0) {
                        reasons.add("装载 " + trim(tons) + " 吨，低于 55 吨经济装载线");
                    }
                    if (height.compareTo(BigDecimal.valueOf(2.6)) > 0) {
                        reasons.add("堆高 " + trim(height) + " 米，超过 2.6 米限高");
                    }
                    if (!Boolean.TRUE.equals(row.get("DISPATCHED"))) {
                        reasons.add("尚未安排装车派单");
                    }
                    if (reasons.isEmpty()) return;
                    var danger = height.compareTo(BigDecimal.valueOf(2.6)) > 0;
                    alerts.add(Map.of(
                            "level", danger ? "danger" : reasons.size() > 1 ? "warning" : "info",
                            "code", value(row, "CODE"),
                            "route", value(row, "FROM_POINT") + " → " + value(row, "TO_STATION"),
                            "message", String.join("；", reasons)));
                });
        return alerts.stream().limit(5).toList();
    }

    @Transactional
    public Map<String, Object> create(String entity, Map<String, Object> input) {
        requireEntity(entity);
        requireFields(entity, input);
        validate(entity, input, null);
        var id = UUID.randomUUID().toString();
        var values = new LinkedHashMap<String, Object>();
        values.put("id", id);
        if (!"projectCodes".equals(entity)) {
            values.put("code", codePrefix(entity) + "-" + id.substring(0, 8).toUpperCase());
        }
        applyValues(entity, input, input, values, null);
        var names = new ArrayList<>(values.keySet());
        var sql = "insert into " + table(entity) + " (" + String.join(", ", names) + ") values ("
                + String.join(", ", java.util.Collections.nCopies(names.size(), "?")) + ")";
        jdbc.update(sql, names.stream().map(values::get).toArray());
        syncDemandStatus(entity, id);
        return get(entity, id);
    }

    @Transactional
    public Map<String, Object> update(String entity, String id, Map<String, Object> input) {
        requireEntity(entity);
        var merged = new LinkedHashMap<String, Object>(get(entity, id));
        input.forEach((key, value) -> {
            if (value != null) merged.put(key, value);
        });
        validate(entity, merged, id);
        var values = new LinkedHashMap<String, Object>();
        applyValues(entity, merged, input, values, id);
        if (values.isEmpty()) throw new BusinessException(400, "没有可更新的字段");
        var assignments = new ArrayList<String>();
        var args = new ArrayList<Object>();
        values.forEach((column, value) -> {
            if ("id".equals(column) || "code".equals(column)) return;
            assignments.add(column + " = ?");
            args.add(value);
        });
        if (assignments.isEmpty()) throw new BusinessException(400, "没有可更新的字段");
        assignments.add("updated_at = current_timestamp");
        args.add(id);
        jdbc.update("update " + table(entity) + " set " + String.join(", ", assignments) + " where id = ?",
                args.toArray());
        syncDemandStatus(entity, id);
        return get(entity, id);
    }

    private void syncDemandStatus(String entity, String id) {
        if ("dispatches".equals(entity)) {
            var row = jdbc.queryForList("select demand_id, status from rail_dispatches where id = ?", id);
            if (row.isEmpty()) return;
            var status = value(row.get(0), "STATUS");
            if (Set.of("已到库", "装车中", "已装完").contains(status)) {
                jdbc.update("update rail_demands set status = '已装车', updated_at = current_timestamp "
                        + "where id = ? and status in ('草稿', '已提报', '已受理')", row.get(0).get("DEMAND_ID"));
            }
        }
        if ("shipments".equals(entity)) {
            var row = jdbc.queryForList(
                    "select demand_id, status, depart_at, arrive_at, delivered_at from rail_shipments where id = ?", id);
            if (row.isEmpty()) return;
            var status = value(row.get(0), "STATUS");
            var now = LocalDateTime.now().toString().replace('T', ' ').substring(0, 16);
            var atOrBeyondTransit = Set.of("运输中", "已到站", "已交付").contains(status);
            var atOrBeyondArrival = Set.of("已到站", "已交付").contains(status);
            if (atOrBeyondTransit && value(row.get(0), "DEPART_AT").isBlank()) {
                jdbc.update("update rail_shipments set depart_at = ? where id = ?", now, id);
            }
            if (atOrBeyondArrival && value(row.get(0), "ARRIVE_AT").isBlank()) {
                jdbc.update("update rail_shipments set arrive_at = ? where id = ?", now, id);
            }
            if ("已交付".equals(status) && value(row.get(0), "DELIVERED_AT").isBlank()) {
                jdbc.update("update rail_shipments set delivered_at = ? where id = ?", now, id);
            }
            var next = switch (status) {
                case "运输中" -> "已发运";
                case "已交付" -> "已完成";
                default -> "";
            };
            if (!next.isBlank()) {
                jdbc.update("update rail_demands set status = ?, updated_at = current_timestamp where id = ?",
                        next, row.get(0).get("DEMAND_ID"));
            }
        }
        if ("deliveries".equals(entity)) {
            var row = jdbc.queryForList("select demand_id, status from rail_deliveries where id = ?", id);
            if (row.isEmpty()) return;
            if ("已签收".equals(value(row.get(0), "STATUS"))) {
                jdbc.update("update rail_demands set status = '已完成', updated_at = current_timestamp where id = ?",
                        row.get(0).get("DEMAND_ID"));
            }
        }
        if ("settlements".equals(entity)) {
            var row = jdbc.queryForList("select status, settled_at from rail_settlements where id = ?", id);
            if (row.isEmpty()) return;
            if ("已结清".equals(value(row.get(0), "STATUS")) && value(row.get(0), "SETTLED_AT").isBlank()) {
                jdbc.update("update rail_settlements set settled_at = ? where id = ?",
                        LocalDateTime.now().toLocalDate().toString(), id);
            }
        }
    }

    @Transactional
    public void delete(String entity, String id) {
        requireEntity(entity);
        get(entity, id);
        var attachments = scalar(
                "select count(*) from rail_attachments where entity = ? and record_id = ?", entity, id);
        if (attachments > 0) {
            throw new BusinessException(409, "该记录还有已上传单据，请先删除单据");
        }
        if (references(entity, id) > 0) {
            throw new BusinessException(409, "该记录已有业务关联，不能删除；请保留运输历史或先解除关联");
        }
        jdbc.update("delete from " + table(entity) + " where id = ?", id);
    }

    private void applyValues(String entity, Map<String, Object> input, Map<String, Object> changes,
            Map<String, Object> values, String id) {
        var columns = WRITABLE.get(entity);
        for (var entry : columns.entrySet()) {
            if (!input.containsKey(entry.getKey())) continue;
            var value = input.get(entry.getKey());
            if (value instanceof String text && text.isBlank()) continue;
            if (value == null) continue;
            values.put(entry.getValue(), value);
        }
        if ("rates".equals(entity)) fillRate(values);
        if ("quotes".equals(entity)) fillQuote(values, changes, id);
        if ("demands".equals(entity)) fillDemand(values, changes);
        if ("dispatches".equals(entity)) fillDispatch(values, changes);
        if ("shipments".equals(entity)) fillShipment(values);
        if ("deliveries".equals(entity)) fillDelivery(values);
        if ("projectCodes".equals(entity)) fillProjectMapping(values);
        if ("settlements".equals(entity)) fillSettlement(values, changes);
    }

    private void fillRate(Map<String, Object> values) {
        var boxType = integerOf(values.get("box_type"));
        if (!BOX_TYPES.contains(boxType)) throw new BusinessException(400, "箱型仅支持 58、60、70");
        var mapping = resolveProjectMapping(boxType, value(values, "project_code"));
        values.put("project_code", mapping.get("PROJECT_CODE"));
        if (value(values, "price_coeff").isBlank()) {
            values.put("price_coeff", value(mapping, "PRICE_COEFF"));
        }
        validateChoice(values, "status", RATE_STATUS);
    }

    private Map<String, Object> resolveProjectMapping(int boxType, String projectCode) {
        if (!projectCode.isBlank()) {
            var mapping = currentRow("""
                    select project_code, price_coeff from rail_project_codes
                    where project_code = ? and box_type = ? and status = '生效'
                    """, projectCode, boxType);
            if (mapping == null) throw new BusinessException(400, "所选项目号不属于该箱型或已停用");
            return mapping;
        }
        var mapping = currentRow("""
                select project_code, price_coeff from rail_project_codes
                where box_type = ? and status = '生效'
                order by effective_from desc, project_code limit 1
                """, boxType);
        if (mapping == null) throw new BusinessException(400, "请先在箱型配置中维护该箱型的项目号");
        return mapping;
    }

    private void fillProjectMapping(Map<String, Object> values) {
        var boxType = integerOf(values.get("box_type"));
        if (!BOX_TYPES.contains(boxType)) throw new BusinessException(400, "箱型仅支持 58、60、70");
        if (value(values, "project_code").isBlank()) throw new BusinessException(400, "请填写铁路项目号");
        validateChoice(values, "status", PROJECT_STATUS);
    }

    private void fillRailCustomer(Map<String, Object> values) {
        var customerId = values.get("customer_id");
        if (customerId == null) throw new BusinessException(400, "请选择客户档案");
        var customer = currentRow("select name from customers where id = ?", customerId);
        if (customer == null) throw new BusinessException(400, "客户档案不存在");
        var matched = scalar("""
                select count(*) from customer_intentions
                where customer_id = ? and business = '运输服务'
                  and business_detail = '火车运输' and stage not in ('已流失', '已终止')
                """, customerId);
        if (matched == 0) throw new BusinessException(400, "该客户没有有效的火车运输意向");
        values.put("customer_name", customer.get("NAME"));
    }

    private void fillQuote(Map<String, Object> values, Map<String, Object> changes, String id) {
        fillRailCustomer(values);
        var rateId = values.get("rate_id");
        var rateChanged = changes.containsKey("rateId");
        if (rateChanged && rateId != null) {
            var rate = currentRow("select id as rate_id, price_per_wagon, box_type, tax_rate from rail_rates where id = ?", rateId);
            if (rate == null) throw new BusinessException(400, "所选线路运价不存在");
            values.put("box_type", integerOf(rate.get("BOX_TYPE")));
            values.put("quote_price", decimalOf(rate.get("PRICE_PER_WAGON")));
            values.put("tax_rate", decimalOf(rate.get("TAX_RATE")));
            values.put("cost_total", rateCost(String.valueOf(rate.get("RATE_ID"))));
        } else if (id == null && rateId != null) {
            var rate = currentRow("select id as rate_id, price_per_wagon, box_type, tax_rate from rail_rates where id = ?", rateId);
            if (rate == null) throw new BusinessException(400, "所选线路运价不存在");
            if (values.get("box_type") == null) values.put("box_type", integerOf(rate.get("BOX_TYPE")));
            if (values.get("quote_price") == null) values.put("quote_price", decimalOf(rate.get("PRICE_PER_WAGON")));
            if (values.get("tax_rate") == null) values.put("tax_rate", decimalOf(rate.get("TAX_RATE")));
            if (values.get("cost_total") == null) values.put("cost_total", rateCost(String.valueOf(rate.get("RATE_ID"))));
        }
        var tons = decimalOf(values.getOrDefault("tons", 0));
        var price = decimalOf(values.getOrDefault("quote_price", 0));
        var cost = decimalOf(values.getOrDefault("cost_total", 0));
        if (tons.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException(400, "请填写装载吨数");
        values.put("unit_price", round(price.divide(tons, 4, RoundingMode.HALF_UP)));
        values.put("gross_profit", round(price.subtract(cost)));
        values.put("cost_total", cost);
        validateChoice(values, "status", QUOTE_STATUS);
    }

    private void fillDemand(Map<String, Object> values, Map<String, Object> changes) {
        var quoteId = values.get("quote_id");
        if (quoteId != null) {
            var quote = currentRow("""
                    select q.customer_id, q.box_type, q.tons, r.from_point, r.to_station,
                           r.project_code
                    from rail_quotes q left join rail_rates r on r.id = q.rate_id
                    where q.id = ?
                    """, quoteId);
            if (quote == null) throw new BusinessException(400, "关联报价单不存在");
            if (value(values, "customer_id").isBlank()) values.put("customer_id", quote.get("CUSTOMER_ID"));
            if (value(values, "from_point").isBlank()) values.put("from_point", quote.get("FROM_POINT"));
            if (value(values, "to_station").isBlank()) values.put("to_station", quote.get("TO_STATION"));
            if (values.get("box_type") == null) values.put("box_type", integerOf(quote.get("BOX_TYPE")));
            if (value(values, "project_code").isBlank()) values.put("project_code", quote.get("PROJECT_CODE"));
        }
        fillRailCustomer(values);
        var boxType = integerOf(values.get("box_type"));
        if (!BOX_TYPES.contains(boxType)) throw new BusinessException(400, "箱型仅支持 58、60、70");
        values.put("project_code", resolveProjectMapping(boxType, value(values, "project_code")).get("PROJECT_CODE"));
        if (changes.containsKey("boxType") && !changes.containsKey("tons")) values.put("tons", boxType);
        if (values.get("tons") == null || decimalOf(values.get("tons")).compareTo(BigDecimal.ZERO) <= 0) values.put("tons", boxType);
        var tons = decimalOf(values.get("tons"));
        if (tons.compareTo(BigDecimal.valueOf(80)) > 0) throw new BusinessException(400, "单车皮吨数不能超过 80 吨");
        var height = decimalOf(values.getOrDefault("stack_height", 2.6));
        if (height.compareTo(BigDecimal.valueOf(2.6)) > 0) throw new BusinessException(400, "货物堆高超过 2.6 米，装不进车皮");
        if (height.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException(400, "货物堆高必须大于 0");
        validateChoice(values, "status", DEMAND_STATUS);
    }

    private void fillDispatch(Map<String, Object> values, Map<String, Object> changes) {
        var demandId = values.get("demand_id");
        if (demandId == null) throw new BusinessException(400, "请选择需求计划");
        var demand = currentRow("select box_type, bales, from_point from rail_demands where id = ?", demandId);
        if (demand == null) throw new BusinessException(400, "需求计划不存在");
        values.put("box_type", integerOf(demand.get("BOX_TYPE")));
        if (changes.containsKey("demandId") && !changes.containsKey("bales")) {
            values.put("bales", integerOf(demand.get("BALES")));
        }
        if (value(values, "pickup_point").isBlank()) {
            values.put("pickup_point", demand.get("FROM_POINT"));
        }
        validateChoice(values, "status", DISPATCH_STATUS);
        validateChoice(values, "exception_type", EXCEPTIONS);
    }

    private void fillShipment(Map<String, Object> values) {
        validateChoice(values, "status", SHIPMENT_STATUS);
        var demandId = values.get("demand_id");
        if (demandId == null) throw new BusinessException(400, "请选择需求计划");
        var demand = currentRow("select id from rail_demands where id = ?", demandId);
        if (demand == null) throw new BusinessException(400, "需求计划不存在");
        if (value(values, "wagon_no").isBlank()) {
            var wagon = currentRow("""
                    select wagon_no from rail_dispatches
                    where demand_id = ? and wagon_no <> ''
                    order by created_at desc limit 1
                    """, demandId);
            if (wagon != null) values.put("wagon_no", wagon.get("WAGON_NO"));
        }
        if (value(values, "wagon_no").isBlank()) {
            throw new BusinessException(400, "该需求还没有装车派单车皮号");
        }
    }

    private void fillDelivery(Map<String, Object> values) {
        validateChoice(values, "status", DELIVERY_STATUS);
        var demandId = values.get("demand_id");
        if (demandId == null) throw new BusinessException(400, "请选择需求计划");
        var demand = currentRow("""
                select id, consignee, to_station, tons, bales from rail_demands where id = ?
                """, demandId);
        if (demand == null) throw new BusinessException(400, "需求计划不存在");
        if (value(values, "consignee_name").isBlank()) {
            values.put("consignee_name", demand.get("CONSIGNEE"));
        }
        if (value(values, "destination").isBlank()) {
            values.put("destination", demand.get("TO_STATION"));
        }
        if (decimalOf(values.getOrDefault("tons", 0)).compareTo(BigDecimal.ZERO) <= 0) {
            values.put("tons", decimalOf(demand.get("TONS")));
        }
        if (integerOf(values.getOrDefault("pieces", 0)) <= 0) {
            values.put("pieces", integerOf(demand.get("BALES")));
        }
        if (value(values, "wagon_no").isBlank()) {
            var wagon = currentRow("""
                    select wagon_no from rail_dispatches
                    where demand_id = ? and wagon_no <> ''
                    order by created_at desc limit 1
                    """, demandId);
            if (wagon != null) values.put("wagon_no", wagon.get("WAGON_NO"));
        }
        if (value(values, "consignee_name").isBlank()) {
            throw new BusinessException(400, "请填写收货人");
        }
        if (decimalOf(values.getOrDefault("tons", 0)).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "配送吨数必须大于 0");
        }
        if ("已签收".equals(value(values, "status"))) {
            if (value(values, "signed_by").isBlank()) throw new BusinessException(400, "已签收必须填写签收人");
            if (value(values, "delivered_at").isBlank()) {
                values.put("delivered_at", LocalDateTime.now().toString().replace('T', ' ').substring(0, 16));
            }
        }
    }

    private void fillSettlement(Map<String, Object> values, Map<String, Object> changes) {
        var quoteId = values.get("quote_id");
        if (changes.containsKey("quoteId") && quoteId != null) {
            var quote = currentRow("select quote_price, cost_total, tax_rate, customer_name from rail_quotes where id = ?", quoteId);
            if (quote == null) throw new BusinessException(400, "所选报价单不存在");
            values.put("quote_price", decimalOf(quote.get("QUOTE_PRICE")));
            values.put("cost_total", decimalOf(quote.get("COST_TOTAL")));
            values.put("tax_rate", decimalOf(quote.get("TAX_RATE")));
            if (values.get("customer_name") == null || String.valueOf(values.get("customer_name")).isBlank()) {
                values.put("customer_name", quote.get("CUSTOMER_NAME"));
            }
        }
        var price = decimalOf(values.getOrDefault("quote_price", 0));
        var cost = decimalOf(values.getOrDefault("cost_total", 0));
        values.put("gross_profit", round(price.subtract(cost)));
        validateChoice(values, "status", SETTLEMENT_STATUS);
    }

    private void validate(String entity, Map<String, Object> input, String id) {
        if ("rates".equals(entity)) {
            var boxType = integerOf(input.get("boxType"));
            if (boxType <= 0) throw new BusinessException(400, "请选择箱型");
            validateChoice(input, "status", RATE_STATUS);
        }
        if ("quotes".equals(entity) && id == null && text(input.get("customerId")).isBlank()) {
            throw new BusinessException(400, "请选择客户档案");
        }
        if ("quotes".equals(entity) && decimalOf(input.getOrDefault("tons", 1)).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "装载吨数必须大于 0");
        }
        if ("demands".equals(entity)) {
            var boxType = integerOf(input.get("boxType"));
            if (boxType <= 0) throw new BusinessException(400, "请选择箱型");
            if (input.containsKey("tons") && decimalOf(input.get("tons")).compareTo(BigDecimal.valueOf(80)) > 0) {
                throw new BusinessException(400, "单车皮吨数不能超过 80 吨");
            }
            if (input.containsKey("stackHeight")
                    && decimalOf(input.get("stackHeight")).compareTo(BigDecimal.valueOf(2.6)) > 0) {
                throw new BusinessException(400, "货物堆高超过 2.6 米，装不进车皮");
            }
        }
        if ("shipments".equals(entity) && input.containsKey("wagonNo") && text(input.get("wagonNo")).isBlank()) {
            throw new BusinessException(400, "请填写车皮号");
        }
        if ("projectCodes".equals(entity)) {
            var projectCode = value(input, "projectCode").trim();
            if (!projectCode.isBlank()) {
                var duplicated = scalar("""
                        select count(*) from rail_project_codes
                        where project_code = ? and id <> coalesce(?, '')
                        """, projectCode, id);
                if (duplicated > 0) throw new BusinessException(409, "项目号已存在，请勿重复配置");
            }
        }
    }

    private BigDecimal rateCost(String rateId) {
        var row = currentRow("""
                select coalesce(cost_rail, 0) + coalesce(cost_short_haul_from, 0)
                     + coalesce(cost_short_haul_to, 0) + coalesce(cost_wooden_frame, 0) as total
                from rail_rates where id = ?
                """, rateId);
        return row == null ? BigDecimal.ZERO : decimalOf(row.get("TOTAL"));
    }

    private void requireFields(String entity, Map<String, Object> input) {
        Set<String> required = switch (entity) {
            case "rates" -> Set.of("fromPoint", "toStation", "boxType", "pricePerWagon");
            case "quotes" -> Set.of("customerId", "rateId", "tons");
            case "demands" -> Set.of("loadDate", "fromPoint", "toStation", "boxType", "tons");
            case "dispatches" -> Set.of("demandId");
            case "shipments" -> Set.of("demandId");
            case "settlements" -> Set.of("customerName");
            case "deliveries" -> Set.of("demandId");
            case "projectCodes" -> Set.of("boxType", "projectCode");
            default -> Set.of();
        };
        for (var field : required) {
            if (!present(input, field)) throw new BusinessException(400, "请填写" + fieldLabel(field));
        }
    }

    private static String fieldLabel(String field) {
        return switch (field) {
            case "customerId" -> "客户档案";
            case "rateId" -> "线路运价";
            case "boxType" -> "箱型";
            case "projectCode" -> "铁路项目号";
            case "fromPoint" -> "装货点";
            case "toStation" -> "到站";
            case "pricePerWagon" -> "车皮报价";
            case "loadDate" -> "装车日期";
            case "tons" -> "吨数";
            case "demandId" -> "需求计划";
            case "consigneeName" -> "收货人";
            case "wagonNo" -> "车皮号";
            default -> field;
        };
    }

    private int references(String entity, String id) {
        return switch (entity) {
            case "rates" -> scalar("select count(*) from rail_quotes where rate_id = ?", id);
            case "quotes" -> scalar("select count(*) from rail_settlements where quote_id = ?", id)
                    + scalar("select count(*) from rail_demands where quote_id = ?", id);
            case "demands" -> scalar("select count(*) from rail_dispatches where demand_id = ?", id)
                    + scalar("select count(*) from rail_shipments where demand_id = ?", id)
                    + scalar("select count(*) from rail_deliveries where demand_id = ?", id);
            case "dispatches" -> 0;
            case "shipments" -> 0;
            case "settlements" -> 0;
            case "projectCodes" -> scalar(
                    "select count(*) from rail_rates where project_code = (select project_code from rail_project_codes where id = ?)", id)
                    + scalar(
                    "select count(*) from rail_demands where project_code = (select project_code from rail_project_codes where id = ?)", id);
            default -> 0;
        };
    }

    private String select(String entity) {
        requireEntity(entity);
        return switch (entity) {
            case "rates" -> "select * from rail_rates";
            case "quotes" -> """
                    select q.*, r.code as rate_code, r.from_point, r.to_station, r.price_per_wagon, r.project_code,
                           c.name as customer_name
                    from rail_quotes q left join rail_rates r on r.id = q.rate_id
                    left join customers c on c.id = q.customer_id
                    """;
            case "demands" -> """
                    select d.*, q.code as quote_code, q.quote_price
                    from rail_demands d left join rail_quotes q on q.id = d.quote_id
                    """;
            case "dispatches" -> """
                    select d.*, dm.code as demand_code, dm.from_point, dm.to_station,
                           dm.customer_name, dm.load_date, dm.project_code
                    from rail_dispatches d join rail_demands dm on dm.id = d.demand_id
                    """;
            case "shipments" -> """
                    select s.*, dm.code as demand_code, dm.from_point, dm.to_station,
                           dm.customer_name, dm.project_code
                    from rail_shipments s join rail_demands dm on dm.id = s.demand_id
                    """;
            case "settlements" -> """
                    select s.*, q.code as quote_code from rail_settlements s
                    left join rail_quotes q on q.id = s.quote_id
                    """;
            case "deliveries" -> """
                    select d.*, dm.code as demand_code, dm.customer_name, dm.from_point, dm.to_station
                    from rail_deliveries d join rail_demands dm on dm.id = d.demand_id
                    """;
            case "projectCodes" -> "select * from rail_project_codes";
            default -> throw new BusinessException(404, "火车运输业务模块不存在");
        };
    }

    private String orderBy(String entity) {
        return switch (entity) {
            case "rates" -> " order by from_point, to_station, box_type";
            case "quotes" -> " order by q.created_at desc";
            case "demands" -> " order by d.load_date desc, d.code desc";
            case "dispatches" -> " order by d.created_at desc";
            case "shipments" -> " order by s.created_at desc";
            case "settlements" -> " order by s.created_at desc";
            case "deliveries" -> " order by d.created_at desc";
            case "projectCodes" -> " order by box_type, project_code";
            default -> " order by 1";
        };
    }

    private String idColumn(String entity) {
        return switch (entity) {
            case "quotes" -> "q.id";
            case "demands" -> "d.id";
            case "dispatches" -> "d.id";
            case "shipments" -> "s.id";
            case "settlements" -> "s.id";
            case "deliveries" -> "d.id";
            default -> "id";
        };
    }

    private String table(String entity) {
        return switch (entity) {
            case "rates" -> "rail_rates";
            case "quotes" -> "rail_quotes";
            case "demands" -> "rail_demands";
            case "dispatches" -> "rail_dispatches";
            case "shipments" -> "rail_shipments";
            case "settlements" -> "rail_settlements";
            case "deliveries" -> "rail_deliveries";
            case "projectCodes" -> "rail_project_codes";
            default -> throw new BusinessException(404, "火车运输业务模块不存在");
        };
    }

    private static String codePrefix(String entity) {
        return switch (entity) {
            case "rates" -> "RA";
            case "quotes" -> "RQ";
            case "demands" -> "RD";
            case "dispatches" -> "RP";
            case "shipments" -> "RS";
            case "deliveries" -> "RDL";
            default -> "RB";
        };
    }

    private void requireEntity(String entity) {
        if (!WRITABLE.containsKey(entity)) throw new BusinessException(404, "火车运输业务模块不存在");
    }

    private Map<String, Object> currentRow(String sql, Object... args) {
        var rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private int scalar(String sql, Object... args) {
        var result = jdbc.queryForObject(sql, Integer.class, args);
        return result == null ? 0 : result;
    }

    private BigDecimal decimal(String sql, Object... args) {
        var result = jdbc.queryForObject(sql, BigDecimal.class, args);
        return result == null ? BigDecimal.ZERO : result;
    }

    private static int integerOf(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null || value.toString().isBlank() ? 0 : Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, "箱型必须是整数");
        }
    }

    private static BigDecimal decimalOf(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return new BigDecimal(number.toString());
        try {
            var text = value == null ? "" : value.toString().trim();
            return text.isEmpty() ? BigDecimal.ZERO : new BigDecimal(text);
        } catch (NumberFormatException exception) {
            throw new BusinessException(400, "金额或吨数必须是数字");
        }
    }

    private static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String trim(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static String value(Map<String, Object> row, String key) {
        var value = row.get(key);
        return value == null ? "" : value.toString();
    }

    private static boolean present(Map<String, Object> input, String key) {
        var value = input.get(key);
        return value != null && !value.toString().isBlank();
    }

    private static void validateChoice(Map<String, Object> input, String key, Set<String> allowed) {
        if (input.containsKey(key) && input.get(key) != null && !allowed.contains(input.get(key).toString())) {
            throw new BusinessException(400, key + " 取值不合法");
        }
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
            if (character == '_') {
                upper = true;
            } else {
                result.append(upper ? Character.toUpperCase(character) : character);
                upper = false;
            }
        }
        return result.toString();
    }

    private static Map<String, Map<String, String>> writable() {
        var result = new LinkedHashMap<String, Map<String, String>>();
        result.put("rates", columns(
                "fromPoint", "from_point", "toStation", "to_station", "boxType", "box_type",
                "projectCode", "project_code", "pricePerWagon", "price_per_wagon",
                "costRail", "cost_rail", "costShortHaulFrom", "cost_short_haul_from",
                "costShortHaulTo", "cost_short_haul_to", "costWoodenFrame", "cost_wooden_frame",
                "taxRate", "tax_rate", "priceCoeff", "price_coeff", "effectiveFrom", "effective_from",
                "status", "status", "notes", "notes"));
        result.put("quotes", columns(
                "customerId", "customer_id", "customerName", "customer_name", "rateId", "rate_id", "boxType", "box_type",
                "tons", "tons", "quotePrice", "quote_price", "costTotal", "cost_total",
                "taxRate", "tax_rate", "unitPrice", "unit_price", "marginTarget", "margin_target",
                "grossProfit", "gross_profit", "status", "status", "notes", "notes"));
        result.put("demands", columns(
                "quoteId", "quote_id", "customerId", "customer_id", "customerName", "customer_name",
                "loadDate", "load_date", "fromPoint", "from_point", "toStation", "to_station",
                "customerName", "customer_name", "cargo", "cargo", "category", "category",
                "consignor", "consignor", "consignee", "consignee", "wagonType", "wagon_type",
                "boxType", "box_type", "projectCode", "project_code", "tons", "tons",
                "bales", "bales", "volume", "volume", "stationFeePaid", "station_fee_paid",
                "stackHeight", "stack_height", "status", "status", "notes", "notes"));
        result.put("dispatches", columns(
                "demandId", "demand_id", "pickupPoint", "pickup_point", "bales", "bales",
                "boxType", "box_type", "wagonNo", "wagon_no", "driver", "driver", "phone", "phone",
                "arriveAt", "arrive_at", "actualTons", "actual_tons", "actualBales", "actual_bales",
                "status", "status", "exceptionType", "exception_type", "notes", "notes"));
        result.put("shipments", columns(
                "demandId", "demand_id", "wagonNo", "wagon_no", "departAt", "depart_at",
                "arriveAt", "arrive_at", "deliveredAt", "delivered_at", "status", "status",
                "notes", "notes"));
        result.put("deliveries", columns(
                "demandId", "demand_id", "wagonNo", "wagon_no", "consigneeName", "consignee_name",
                "consigneePhone", "consignee_phone", "address", "address", "destination", "destination",
                "tons", "tons", "pieces", "pieces", "shortHaulCompany", "short_haul_company",
                "distanceKm", "distance_km", "shortHaulFee", "short_haul_fee",
                "deliveredAt", "delivered_at", "signedBy", "signed_by", "status", "status", "notes", "notes"));
        result.put("projectCodes", columns(
                "boxType", "box_type", "projectCode", "project_code", "priceCoeff", "price_coeff",
                "status", "status", "effectiveFrom", "effective_from", "notes", "notes"));
        result.put("settlements", columns(
                "customerName", "customer_name", "quoteId", "quote_id", "quotePrice", "quote_price",
                "costTotal", "cost_total", "taxRate", "tax_rate", "grossProfit", "gross_profit",
                "status", "status", "settledAt", "settled_at", "notes", "notes"));
        return result;
    }

    private static Map<String, String> columns(String... values) {
        var result = new LinkedHashMap<String, String>();
        for (int index = 0; index < values.length; index += 2) result.put(values[index], values[index + 1]);
        return Map.copyOf(result);
    }
}
