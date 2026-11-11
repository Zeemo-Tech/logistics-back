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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RailService {
    private static final Map<Integer, String> PROJECT_CODES = Map.of(
            58, "KM2601076e",
            60, "KM2601076c",
            70, "KM2601076d");

    private static final Map<Integer, String> PRICE_COEFFS = Map.of(
            58, "34.1%",
            60, "-27%",
            70, "-35.6%");

    private static final Map<String, Map<String, String>> WRITABLE = writable();

    private static final Set<String> RATE_STATUS = Set.of("生效", "停用");
    private static final Set<String> QUOTE_STATUS = Set.of("草稿", "待审批", "已通过", "已作废");
    private static final Set<String> DEMAND_STATUS = Set.of("草稿", "已提报", "已受理", "已装车", "已发运", "已完成", "已取消");
    private static final Set<String> DISPATCH_STATUS = Set.of("待派车", "已派车", "已到库", "装车中", "已装完", "已完成");
    private static final Set<String> SHIPMENT_STATUS = Set.of("待发运", "运输中", "已到站", "已交付", "异常");
    private static final Set<String> SETTLEMENT_STATUS = Set.of("待对账", "已对账", "已结清");
    private static final Set<String> EXCEPTIONS = Set.of("无", "箱型不足", "限高装不进", "吨数不符", "计划改号");

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
        result.put("recentDemands", list("demands", null).stream().limit(6).toList());
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
        values.put("code", codePrefix(entity) + "-" + id.substring(0, 8).toUpperCase());
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
        if ("settlements".equals(entity)) fillSettlement(values, changes);
    }

    private void fillRate(Map<String, Object> values) {
        var boxType = integerOf(values.get("box_type"));
        if (boxType == 0) throw new BusinessException(400, "请选择箱型");
        if (!PROJECT_CODES.containsKey(boxType)) throw new BusinessException(400, "箱型仅支持 58、60、70");
        values.put("project_code", PROJECT_CODES.get(boxType));
        if (values.get("price_coeff") == null || String.valueOf(values.get("price_coeff")).isBlank()) {
            values.put("price_coeff", PRICE_COEFFS.get(boxType));
        }
        validateChoice(values, "status", RATE_STATUS);
    }

    private void fillQuote(Map<String, Object> values, Map<String, Object> changes, String id) {
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
        var boxType = integerOf(values.get("box_type"));
        if (boxType == 0) throw new BusinessException(400, "请选择箱型");
        if (!PROJECT_CODES.containsKey(boxType)) throw new BusinessException(400, "箱型仅支持 58、60、70");
        values.put("project_code", PROJECT_CODES.get(boxType));
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
        var demand = currentRow("select box_type, bales from rail_demands where id = ?", demandId);
        if (demand == null) throw new BusinessException(400, "需求计划不存在");
        values.put("box_type", integerOf(demand.get("BOX_TYPE")));
        if (changes.containsKey("demandId") && !changes.containsKey("bales")) {
            values.put("bales", integerOf(demand.get("BALES")));
        }
        validateChoice(values, "status", DISPATCH_STATUS);
        validateChoice(values, "exception_type", EXCEPTIONS);
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
            if (boxType != 0 && !PROJECT_CODES.containsKey(boxType)) {
                throw new BusinessException(400, "箱型仅支持 58、60、70");
            }
            validateChoice(input, "status", RATE_STATUS);
        }
        if ("quotes".equals(entity) && id == null && text(input.get("customerName")).isBlank()) {
            throw new BusinessException(400, "请填写客户名称");
        }
        if ("quotes".equals(entity) && decimalOf(input.getOrDefault("tons", 1)).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "装载吨数必须大于 0");
        }
        if ("demands".equals(entity)) {
            var boxType = integerOf(input.get("boxType"));
            if (boxType != 0 && !PROJECT_CODES.containsKey(boxType)) {
                throw new BusinessException(400, "箱型仅支持 58、60、70");
            }
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
            case "quotes" -> Set.of("customerName", "rateId", "tons");
            case "demands" -> Set.of("loadDate", "fromPoint", "toStation", "boxType", "tons");
            case "dispatches" -> Set.of("demandId");
            case "shipments" -> Set.of("demandId", "wagonNo");
            case "settlements" -> Set.of("customerName");
            default -> Set.of();
        };
        for (var field : required) {
            if (!present(input, field)) throw new BusinessException(400, "请填写" + field);
        }
    }

    private int references(String entity, String id) {
        return switch (entity) {
            case "rates" -> scalar("select count(*) from rail_quotes where rate_id = ?", id);
            case "quotes" -> scalar("select count(*) from rail_settlements where quote_id = ?", id);
            case "demands" -> scalar("select count(*) from rail_dispatches where demand_id = ?", id)
                    + scalar("select count(*) from rail_shipments where demand_id = ?", id);
            case "dispatches" -> 0;
            case "shipments" -> 0;
            case "settlements" -> 0;
            default -> 0;
        };
    }

    private String select(String entity) {
        requireEntity(entity);
        return switch (entity) {
            case "rates" -> "select * from rail_rates";
            case "quotes" -> """
                    select q.*, r.code as rate_code, r.from_point, r.to_station, r.price_per_wagon
                    from rail_quotes q left join rail_rates r on r.id = q.rate_id
                    """;
            case "demands" -> "select * from rail_demands";
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
            default -> throw new BusinessException(404, "火车运输业务模块不存在");
        };
    }

    private String orderBy(String entity) {
        return switch (entity) {
            case "rates" -> " order by from_point, to_station, box_type";
            case "quotes" -> " order by q.created_at desc";
            case "demands" -> " order by load_date desc, code desc";
            case "dispatches" -> " order by d.created_at desc";
            case "shipments" -> " order by s.created_at desc";
            case "settlements" -> " order by s.created_at desc";
            default -> " order by 1";
        };
    }

    private String idColumn(String entity) {
        return switch (entity) {
            case "quotes" -> "q.id";
            case "dispatches" -> "d.id";
            case "shipments" -> "s.id";
            case "settlements" -> "s.id";
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
                "customerName", "customer_name", "rateId", "rate_id", "boxType", "box_type",
                "tons", "tons", "quotePrice", "quote_price", "costTotal", "cost_total",
                "taxRate", "tax_rate", "unitPrice", "unit_price", "marginTarget", "margin_target",
                "grossProfit", "gross_profit", "status", "status", "notes", "notes"));
        result.put("demands", columns(
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
