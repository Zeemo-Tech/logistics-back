package org.example.logisticsback.road;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
public class RoadService {
    private static final Set<String> VEHICLE_STATUS = Set.of("可用", "在途", "维修", "停用");
    private static final Set<String> PICKUP_STATUS = Set.of("待派车", "已派车", "已提货", "已送站", "已完成", "已取消");
    private static final Set<String> APPOINTMENT_STATUS = Set.of("待受理", "受理通过", "已进站", "已作废");
    private static final Set<String> DRIVER_ACCEPT = Set.of("未接单", "已接单", "已拒单");
    private static final Set<String> WEIGHING_STATUS = Set.of("有效", "作废");
    private static final Set<String> DELIVERY_STATUS = Set.of("待配送", "配送中", "已签收", "异常");
    private static final Set<String> QUOTE_STATUS = Set.of("草稿", "已报价", "已成交", "已作废");
    private static final Set<String> PAYMENT_STATUS = Set.of("待收款", "部分收款", "已收款");
    private static final Set<String> SETTLEMENT_STATUS = Set.of("待对账", "已对账", "已开票", "已收款", "已结清");
    private static final Set<String> PREPAYMENT_STATUS = Set.of("有效", "已用完", "已退款");

    private static final Map<String, Map<String, String>> WRITABLE = writable();

    private final JdbcTemplate jdbc;

    public RoadService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(String entity, String keyword) {
        var rows = jdbc.queryForList(select(entity) + orderBy(entity));
        var query = keyword == null ? "" : keyword.trim().toLowerCase();
        return rows.stream()
                .filter(row -> query.isBlank() || row.values().stream()
                        .anyMatch(value -> value != null && value.toString().toLowerCase().contains(query)))
                .map(RoadService::camelize)
                .toList();
    }

    public List<Map<String, Object>> attachments(String entity, String recordId) {
        requireEntity(entity);
        return jdbc.queryForList("""
                select id, entity, record_id as record_id, doc_type, file_name, mime_type,
                       uploaded_by, uploaded_at
                from road_attachments where entity = ? and record_id = ?
                order by uploaded_at desc
                """, entity, recordId).stream().map(RoadService::camelize).toList();
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
                    insert into road_attachments (id, entity, record_id, doc_type, file_name, mime_type, file_bytes, uploaded_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, entity, recordId, docType == null || docType.isBlank() ? "业务单据" : docType.trim(),
                    name, mimeType, file.getBytes(), uploadedBy == null ? "" : uploadedBy);
            return camelize(jdbc.queryForMap("""
                    select id, entity, record_id as record_id, doc_type, file_name, mime_type,
                           uploaded_by, uploaded_at from road_attachments where id = ?
                    """, id));
        } catch (IOException exception) {
            throw new BusinessException(400, "单据文件读取失败");
        }
    }

    public byte[] attachmentFile(String id) {
        var rows = jdbc.queryForList("select file_bytes from road_attachments where id = ?", id);
        if (rows.isEmpty()) throw new BusinessException(404, "单据不存在");
        return (byte[]) rows.get(0).get("FILE_BYTES");
    }

    public Map<String, Object> attachmentMetadata(String id) {
        return jdbc.queryForList("select id, file_name, mime_type from road_attachments where id = ?", id)
                .stream().findFirst().orElseThrow(() -> new BusinessException(404, "单据不存在"));
    }

    @Transactional
    public void deleteAttachment(String id) {
        if (jdbc.update("delete from road_attachments where id = ?", id) == 0) {
            throw new BusinessException(404, "单据不存在");
        }
    }

    public Map<String, Object> get(String entity, String id) {
        var rows = jdbc.queryForList(select(entity) + " where " + idColumn(entity) + " = ?", id);
        if (rows.isEmpty()) throw new BusinessException(404, "公路运输记录不存在");
        return camelize(rows.get(0));
    }

    public Map<String, Object> overview() {
        var result = new LinkedHashMap<String, Object>();
        result.put("pendingPickups", scalar("select count(*) from road_pickups where status = '待派车'"));
        result.put("vehiclesInTransit", scalar("select count(*) from road_vehicles where status = '在途'"));
        result.put("pendingEntries", scalar("select count(*) from road_appointments where status in ('待受理', '受理通过')"));
        result.put("weighedTrips", scalar("select count(*) from road_weighings where status = '有效'"));
        result.put("weighedTons", decimal("select coalesce(sum(net_weight), 0) from road_weighings where status = '有效'"));
        result.put("pendingDeliveries", scalar("select count(*) from road_deliveries where status in ('待配送', '配送中')"));
        result.put("pendingReceivable", decimal("select coalesce(sum(greatest(0, freight_amount - received_amount)), 0) from road_settlements where payment_status <> '已收款'"));
        result.put("prepaymentBalance", decimal("select coalesce(sum(balance), 0) from road_prepayments where status = '有效'"));
        result.put("recentPickups", list("pickups", null).stream().limit(6).toList());
        var trend = new ArrayList<>(jdbc.queryForList("""
                select pickup_date as date, count(*) as trips, coalesce(sum(tons), 0) as tons
                from road_pickups group by pickup_date order by pickup_date desc limit 7
                """));
        java.util.Collections.reverse(trend);
        result.put("trend", trend.stream().map(RoadService::camelize).toList());
        result.put("factoryTons", jdbc.queryForList("""
                select factory, coalesce(sum(tons), 0) as tons
                from road_pickups group by factory order by tons desc limit 6
                """).stream().map(RoadService::camelize).toList());
        result.put("statusCounts", jdbc.queryForList("""
                select status, count(*) as count from road_pickups
                group by status order by count desc
                """).stream().map(RoadService::camelize).toList());
        result.put("weighByPlate", jdbc.queryForList("""
                select plate, coalesce(sum(net_weight), 0) as tons
                from road_weighings where status = '有效'
                group by plate order by tons desc limit 5
                """).stream().map(RoadService::camelize).toList());
        result.put("alerts", alerts());
        return result;
    }

    private List<Map<String, Object>> alerts() {
        var alerts = new ArrayList<Map<String, Object>>();
        var now = LocalDateTime.now().toString().replace('T', ' ').substring(0, 16);
        jdbc.queryForList("""
                select code, appointment_no, cutoff_at, driver_accepted, status from road_appointments
                where status in ('待受理', '受理通过')
                """).forEach(row -> {
                    var reasons = new ArrayList<String>();
                    if ("已拒单".equals(value(row, "DRIVER_ACCEPTED"))) reasons.add("司机已拒单，需重新分配");
                    var cutoff = value(row, "CUTOFF_AT");
                    if (!cutoff.isBlank() && cutoff.compareTo(now) < 0) reasons.add("已超过进站截止时间");
                    if (reasons.isEmpty()) return;
                    alerts.add(Map.of(
                            "level", "danger",
                            "code", value(row, "CODE"),
                            "route", "预约号 " + value(row, "APPOINTMENT_NO"),
                            "message", String.join("；", reasons)));
                });
        jdbc.queryForList("select code, plate, net_weight from road_weighings where status = '有效' and net_weight > 70")
                .forEach(row -> alerts.add(Map.of(
                        "level", "warning",
                        "code", value(row, "CODE"),
                        "route", value(row, "PLATE"),
                        "message", "净重 " + trim(decimalOf(row.get("NET_WEIGHT"))) + " 吨，超过 70 吨")));
        jdbc.queryForList("select code, customer_name, factory from road_pickups where status = '待派车'")
                .forEach(row -> alerts.add(Map.of(
                        "level", "info",
                        "code", value(row, "CODE"),
                        "route", value(row, "FACTORY"),
                        "message", value(row, "CUSTOMER_NAME") + " 提货单尚未派车")));
        jdbc.queryForList("select code, consignee_name, destination from road_deliveries where status = '待配送'")
                .forEach(row -> alerts.add(Map.of(
                        "level", "info",
                        "code", value(row, "CODE"),
                        "route", value(row, "DESTINATION"),
                        "message", "待配送至 " + value(row, "CONSIGNEE_NAME"))));
        jdbc.queryForList("select code, customer_name, freight_amount, received_amount from road_settlements where payment_status <> '已收款'")
                .forEach(row -> alerts.add(Map.of(
                        "level", "warning",
                        "code", value(row, "CODE"),
                        "route", value(row, "CUSTOMER_NAME"),
                        "message", "待收款 " + trim(decimalOf(row.get("FREIGHT_AMOUNT")).subtract(decimalOf(row.get("RECEIVED_AMOUNT"))))
                                + " 元")));
        return alerts.stream().limit(6).toList();
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
        applyValues(entity, input, input, values);
        var names = new ArrayList<>(values.keySet());
        var sql = "insert into " + table(entity) + " (" + String.join(", ", names) + ") values ("
                + String.join(", ", java.util.Collections.nCopies(names.size(), "?")) + ")";
        jdbc.update(sql, names.stream().map(values::get).toArray());
        syncStatus(entity, id);
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
        applyValues(entity, merged, input, values);
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
        syncStatus(entity, id);
        return get(entity, id);
    }

    @Transactional
    public void delete(String entity, String id) {
        requireEntity(entity);
        get(entity, id);
        var attachments = scalar(
                "select count(*) from road_attachments where entity = ? and record_id = ?", entity, id);
        if (attachments > 0) {
            throw new BusinessException(409, "该记录还有已上传单据，请先删除单据");
        }
        if (references(entity, id) > 0) {
            throw new BusinessException(409, "该记录已有业务关联，不能删除；请先解除关联");
        }
        jdbc.update("delete from " + table(entity) + " where id = ?", id);
    }

    private void syncVehicleStatusForPickup(String pickupId) {
        var rows = jdbc.queryForList("select vehicle_id, status from road_pickups where id = ?", pickupId);
        if (rows.isEmpty() || rows.get(0).get("VEHICLE_ID") == null) return;
        var vehicleId = String.valueOf(rows.get(0).get("VEHICLE_ID"));
        var active = scalar("""
                select count(*) from road_pickups where vehicle_id = ?
                  and status in ('已派车', '已提货', '已送站')
                """, vehicleId);
        var status = active > 0 ? "在途" : "可用";
        jdbc.update("update road_vehicles set status = ?, updated_at = current_timestamp "
                + "where id = ? and status not in ('维修', '停用')", status, vehicleId);
    }

    private void syncStatus(String entity, String id) {
        if ("pickups".equals(entity)) {
            syncVehicleStatusForPickup(id);
        }
        if ("appointments".equals(entity)) {
            var rows = jdbc.queryForList("select pickup_id, status, sent_tons from road_appointments where id = ?", id);
            if (rows.isEmpty()) return;
            var pickupId = rows.get(0).get("PICKUP_ID");
            if (pickupId == null) return;
            if ("已进站".equals(value(rows.get(0), "STATUS"))) {
                var updated = jdbc.update("update road_pickups set status = '已送站', updated_at = current_timestamp "
                        + "where id = ? and status in ('待派车', '已派车', '已提货')", pickupId);
                if (updated > 0) syncVehicleStatusForPickup(String.valueOf(pickupId));
            }
        }
        if ("deliveries".equals(entity)) {
            var rows = jdbc.queryForList("select pickup_id, status from road_deliveries where id = ?", id);
            if (rows.isEmpty()) return;
            var pickupId = rows.get(0).get("PICKUP_ID");
            if (pickupId != null && "已签收".equals(value(rows.get(0), "STATUS"))) {
                jdbc.update("update road_pickups set status = '已完成', updated_at = current_timestamp where id = ?",
                        pickupId);
                syncVehicleStatusForPickup(String.valueOf(pickupId));
            }
        }
    }

    private void applyValues(String entity, Map<String, Object> input, Map<String, Object> changes,
            Map<String, Object> values) {
        for (var entry : WRITABLE.get(entity).entrySet()) {
            if (!input.containsKey(entry.getKey())) continue;
            var value = input.get(entry.getKey());
            if (value instanceof String text && text.isBlank()) continue;
            if (value == null) continue;
            values.put(entry.getValue(), value);
        }
        if ("pickups".equals(entity)) fillPickup(values, changes);
        if ("appointments".equals(entity)) fillAppointment(values);
        if ("weighings".equals(entity)) fillWeighing(values);
        if ("deliveries".equals(entity)) fillDelivery(values);
        if ("quotes".equals(entity)) fillQuote(values);
        if ("settlements".equals(entity)) fillSettlement(values);
        if ("prepayments".equals(entity)) fillPrepayment(values);
        validateChoice(values, "status", statusSet(entity));
    }

    private void fillPickup(Map<String, Object> values, Map<String, Object> changes) {
        var customerId = values.get("customer_id");
        if (customerId == null) throw new BusinessException(400, "请选择客户档案");
        var customer = currentRow("select name from customers where id = ?", customerId);
        if (customer == null) throw new BusinessException(400, "客户档案不存在");
        values.put("customer_name", customer.get("NAME"));
        var intentionId = values.get("intention_id");
        if (intentionId == null) throw new BusinessException(400, "请选择客户的公路运输意向");
        var matched = scalar("""
                select count(*) from customer_intentions
                where id = ? and customer_id = ? and business = '运输服务'
                  and business_detail = '公路运输' and stage not in ('已流失', '已终止')
                """, intentionId, customerId);
        if (matched == 0) throw new BusinessException(400, "所选意向必须属于该客户，且为有效的公路运输服务意向");
        var contractId = values.get("contract_id");
        if (contractId != null) {
            var contract = currentRow("""
                    select customer_id, category, business_detail from contracts where id = ?
                    """, contractId);
            if (contract == null) throw new BusinessException(400, "所选合同不存在");
            if (!customerId.equals(contract.get("CUSTOMER_ID"))
                    || !"公路运输合同".equals(value(contract, "CATEGORY"))
                    || !"公路运输".equals(value(contract, "BUSINESS_DETAIL"))) {
                throw new BusinessException(400, "所选合同必须是该客户的公路运输合同");
            }
        }
        if (changes.containsKey("vehicleId") && values.get("vehicle_id") != null) {
            var vehicle = currentRow("select plate, driver from road_vehicles where id = ?", values.get("vehicle_id"));
            if (vehicle == null) throw new BusinessException(400, "所选车辆不存在");
            values.put("plate", vehicle.get("PLATE"));
            values.put("driver", vehicle.get("DRIVER"));
        }
    }

    private void fillAppointment(Map<String, Object> values) {
        var pickupId = values.get("pickup_id");
        if (pickupId != null) {
            var pickup = currentRow("select id from road_pickups where id = ?", pickupId);
            if (pickup == null) throw new BusinessException(400, "关联提货单不存在");
        }
        var tons = decimalOf(values.getOrDefault("tons", 0));
        var sentTons = decimalOf(values.getOrDefault("sent_tons", 0));
        if (tons.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException(400, "预约吨数必须大于 0");
        if (sentTons.compareTo(BigDecimal.ZERO) < 0 || sentTons.compareTo(tons) > 0) {
            throw new BusinessException(400, "已送吨数必须在 0 到预约吨数之间");
        }
        if ("已进站".equals(value(values, "status")) && sentTons.compareTo(BigDecimal.ZERO) == 0) {
            values.put("sent_tons", tons);
        }
    }

    private void fillDelivery(Map<String, Object> values) {
        var pickupId = values.get("pickup_id");
        if (pickupId != null) {
            var pickup = currentRow("select id from road_pickups where id = ?", pickupId);
            if (pickup == null) throw new BusinessException(400, "关联提货单不存在");
        }
        if ("已签收".equals(value(values, "status"))) {
            var signedBy = value(values, "signed_by");
            if (signedBy.isBlank()) throw new BusinessException(400, "已签收必须填写签收人");
            var deliveryAt = value(values, "delivery_at");
            if (deliveryAt.isBlank()) {
                values.put("delivery_at", LocalDateTime.now().toString().replace('T', ' ').substring(0, 16));
            }
        }
    }

    private void fillWeighing(Map<String, Object> values) {
        var gross = decimalOf(values.get("gross_weight"));
        var tare = decimalOf(values.get("tare_weight"));
        if (gross.compareTo(tare) <= 0) throw new BusinessException(400, "毛重必须大于皮重");
        values.put("net_weight", gross.subtract(tare).setScale(2, RoundingMode.HALF_UP));
    }

    private void fillQuote(Map<String, Object> values) {
        fillCustomerName(values);
        var tons = decimalOf(values.get("actual_tons"));
        if (tons.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException(400, "请填写实际装载吨数");
        if (tons.compareTo(BigDecimal.valueOf(80)) > 0) throw new BusinessException(400, "实际装载吨数不能超过 80 吨");
        var railFreight = decimalOf(values.getOrDefault("rail_freight", 0));
        var fixedFee = decimalOf(values.getOrDefault("fixed_fee", 60));
        var shortHaul = decimalOf(values.getOrDefault("short_haul_per_ton", 0));
        var profit = decimalOf(values.getOrDefault("profit_per_ton", 15));
        var costPerTon = railFreight.divide(tons, 4, RoundingMode.HALF_UP).add(fixedFee).add(shortHaul);
        var quotePerTon = costPerTon.add(profit).setScale(0, RoundingMode.HALF_UP);
        values.put("cost_per_ton", costPerTon.setScale(2, RoundingMode.HALF_UP));
        values.put("quote_per_ton", quotePerTon);
        values.put("total_price", quotePerTon.multiply(tons).setScale(2, RoundingMode.HALF_UP));
    }

    private void fillPrepayment(Map<String, Object> values) {
        var customerId = values.get("customer_id");
        if (customerId == null) throw new BusinessException(400, "请选择客户档案");
        if (currentRow("select id from customers where id = ?", customerId) == null) {
            throw new BusinessException(400, "客户档案不存在");
        }
        var amount = decimalOf(values.get("amount"));
        var used = decimalOf(values.getOrDefault("used_amount", 0));
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException(400, "预付金额必须大于 0");
        if (used.compareTo(BigDecimal.ZERO) < 0 || used.compareTo(amount) > 0) {
            throw new BusinessException(400, "已用金额必须在 0 到预付金额之间");
        }
        var balance = amount.subtract(used).setScale(2, RoundingMode.HALF_UP);
        values.put("balance", balance);
        if (!"已退款".equals(value(values, "status"))) {
            values.put("status", balance.compareTo(BigDecimal.ZERO) == 0 ? "已用完" : "有效");
        }
        if (value(values, "paid_at").isBlank()) values.put("paid_at", LocalDate.now().toString());
    }

    private void fillSettlement(Map<String, Object> values) {
        fillCustomerName(values);
        var deliveryId = values.get("delivery_id");
        if (deliveryId != null) {
            var delivery = currentRow("""
                    select d.wagon_no, d.pickup_id, d.consignee_name, d.destination, d.short_haul_company,
                           p.customer_id, p.factory, p.customer_name, p.consignee_name as pickup_consignee,
                           p.destination as pickup_destination, p.cargo
                    from road_deliveries d left join road_pickups p on p.id = d.pickup_id
                    where d.id = ?
                    """, deliveryId);
            if (delivery == null) throw new BusinessException(400, "所选配送单不存在");
            if (values.get("pickup_id") == null) values.put("pickup_id", delivery.get("PICKUP_ID"));
            if (value(values, "factory").isBlank()) values.put("factory", delivery.get("FACTORY"));
            if (value(values, "destination").isBlank()) values.put("destination", delivery.get("DESTINATION"));
            if (value(values, "consignee_name").isBlank()) values.put("consignee_name", delivery.get("CONSIGNEE_NAME"));
            if (value(values, "wagon_no").isBlank()) values.put("wagon_no", delivery.get("WAGON_NO"));
            if (value(values, "cargo").isBlank()) values.put("cargo", delivery.get("CARGO"));
        }
        var pickupId = values.get("pickup_id");
        if (pickupId != null) {
            var pickup = currentRow("""
                    select factory, destination, consignee_name, cargo from road_pickups where id = ?
                    """, pickupId);
            if (pickup != null) {
                if (value(values, "factory").isBlank()) values.put("factory", pickup.get("FACTORY"));
                if (value(values, "destination").isBlank()) values.put("destination", pickup.get("DESTINATION"));
                if (value(values, "consignee_name").isBlank()) values.put("consignee_name", pickup.get("CONSIGNEE_NAME"));
                if (value(values, "cargo").isBlank()) values.put("cargo", pickup.get("CARGO"));
            }
        }
        var freight = decimalOf(values.getOrDefault("freight_amount", 0));
        var received = decimalOf(values.getOrDefault("received_amount", 0));
        if (freight.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException(400, "请填写应收运费");
        if (received.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException(400, "已收金额不能为负数");
        if (received.compareTo(freight) > 0) throw new BusinessException(400, "已收金额不能大于应收运费");
        var status = value(values, "status");
        var invoiceNo = value(values, "invoice_no");
        var invoiceRequired = Set.of("已开票", "已收款", "已结清").contains(status);
        if (invoiceRequired && invoiceNo.isBlank()) throw new BusinessException(400, "开票后状态必须填写发票号");
        var fullyReceived = Set.of("已收款", "已结清").contains(status);
        if (fullyReceived && received.compareTo(freight) < 0) {
            throw new BusinessException(400, "已收款/已结清状态必须收齐应收运费");
        }
        if (received.compareTo(freight) >= 0) values.put("payment_status", "已收款");
        else if (received.compareTo(BigDecimal.ZERO) > 0) values.put("payment_status", "部分收款");
        if (received.compareTo(BigDecimal.ZERO) > 0
                && (values.get("received_at") == null || String.valueOf(values.get("received_at")).isBlank())) {
            values.put("received_at", LocalDate.now().toString());
        }
        if (!invoiceNo.isBlank()
                && (values.get("invoice_date") == null || String.valueOf(values.get("invoice_date")).isBlank())) {
            values.put("invoice_date", LocalDate.now().toString());
        }
        validateChoice(values, "payment_status", PAYMENT_STATUS);
    }

    private void fillCustomerName(Map<String, Object> values) {
        var customerId = values.get("customer_id");
        if (customerId == null) throw new BusinessException(400, "请选择客户档案");
        var customer = currentRow("select name from customers where id = ?", customerId);
        if (customer == null) throw new BusinessException(400, "客户档案不存在");
        values.put("customer_name", customer.get("NAME"));
    }

    private void validate(String entity, Map<String, Object> input, String id) {
        if ("pickups".equals(entity)) {
            if (!input.containsKey("tons") || decimalOf(input.get("tons")).compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(400, "提货吨数必须大于 0");
            }
            validateChoice(input, "status", PICKUP_STATUS);
        }
        if ("appointments".equals(entity)) {
            validateChoice(input, "status", APPOINTMENT_STATUS);
            validateChoice(input, "driver_accepted", DRIVER_ACCEPT);
            var appointmentNo = value(input, "appointmentNo").trim();
            if (!appointmentNo.isBlank()) {
                var duplicated = scalar("""
                        select count(*) from road_appointments
                        where appointment_no = ? and id <> coalesce(?, '')
                        """, appointmentNo, id);
                if (duplicated > 0) throw new BusinessException(409, "进站预约号已存在，请核对");
            }
        }
        if ("weighings".equals(entity)) {
            validateChoice(input, "status", WEIGHING_STATUS);
        }
        if ("deliveries".equals(entity)) {
            validateChoice(input, "status", DELIVERY_STATUS);
        }
        if ("quotes".equals(entity)) {
            validateChoice(input, "status", QUOTE_STATUS);
        }
        if ("settlements".equals(entity)) {
            validateChoice(input, "status", SETTLEMENT_STATUS);
        }
        if ("vehicles".equals(entity)) {
            validateChoice(input, "status", VEHICLE_STATUS);
        }
    }

    private Set<String> statusSet(String entity) {
        return switch (entity) {
            case "vehicles" -> VEHICLE_STATUS;
            case "pickups" -> PICKUP_STATUS;
            case "appointments" -> APPOINTMENT_STATUS;
            case "weighings" -> WEIGHING_STATUS;
            case "deliveries" -> DELIVERY_STATUS;
            case "quotes" -> QUOTE_STATUS;
            case "settlements" -> SETTLEMENT_STATUS;
            case "prepayments" -> PREPAYMENT_STATUS;
            default -> Set.of();
        };
    }

    private void requireFields(String entity, Map<String, Object> input) {
        Set<String> required = switch (entity) {
            case "vehicles" -> Set.of("plate");
            case "pickups" -> Set.of("customerId", "intentionId", "cargo", "tons");
            case "appointments" -> Set.of("station", "tons");
            case "weighings" -> Set.of("plate", "grossWeight", "tareWeight");
            case "deliveries" -> Set.of("consigneeName", "tons");
            case "quotes" -> Set.of("customerId", "toStation", "actualTons");
            case "settlements" -> Set.of("customerId", "freightAmount");
            case "prepayments" -> Set.of("customerId", "amount");
            default -> Set.of();
        };
        for (var field : required) {
            if (!present(input, field)) throw new BusinessException(400, "请填写" + fieldLabel(field));
        }
    }

    private static String fieldLabel(String field) {
        return switch (field) {
            case "customerId" -> "客户档案";
            case "intentionId" -> "公路运输意向";
            case "plate" -> "车牌";
            case "cargo" -> "货名";
            case "tons" -> "吨数";
            case "station" -> "送货车站";
            case "grossWeight" -> "毛重";
            case "tareWeight" -> "皮重";
            case "consigneeName" -> "收货人";
            case "toStation" -> "终点 / 到站";
            case "actualTons" -> "实际装载吨数";
            case "freightAmount" -> "应收运费";
            default -> field;
        };
    }

    private int references(String entity, String id) {
        return switch (entity) {
            case "vehicles" -> scalar("select count(*) from road_pickups where vehicle_id = ?", id);
            case "pickups" -> scalar("select count(*) from road_appointments where pickup_id = ?", id)
                    + scalar("select count(*) from road_deliveries where pickup_id = ?", id)
                    + scalar("select count(*) from road_settlements where pickup_id = ?", id);
            default -> 0;
        };
    }

    private String select(String entity) {
        requireEntity(entity);
        return switch (entity) {
            case "vehicles" -> "select * from road_vehicles";
            case "pickups" -> """
                    select p.*, ct.id as contract_code, ct.title as contract_title,
                           v.trailer as trailer, v.vehicle_type as vehicle_type
                    from road_pickups p left join road_vehicles v on v.id = p.vehicle_id
                    left join customers c on c.id = p.customer_id
                    left join contracts ct on ct.id = p.contract_id
                    """;
            case "appointments" -> """
                    select a.*, p.customer_name, p.plate, p.factory
                    from road_appointments a left join road_pickups p on p.id = a.pickup_id
                    """;
            case "weighings" -> "select * from road_weighings";
            case "deliveries" -> """
                    select d.*, p.customer_name, p.customer_id as pickup_customer_id, p.plate,
                           p.factory, p.cargo
                    from road_deliveries d left join road_pickups p on p.id = d.pickup_id
                    """;
            case "quotes" -> """
                    select q.*, c.name as customer_name from road_quotes q
                    left join customers c on c.id = q.customer_id
                    """;
            case "settlements" -> """
                    select s.*, c.name as customer_name, p.code as pickup_code, p.plate
                    from road_settlements s left join road_pickups p on p.id = s.pickup_id
                    left join customers c on c.id = s.customer_id
                    """;
            case "prepayments" -> """
                    select p.*, c.name as customer_name from road_prepayments p
                    join customers c on c.id = p.customer_id
                    """;
            default -> throw new BusinessException(404, "公路运输业务模块不存在");
        };
    }

    private String idColumn(String entity) {
        return switch (entity) {
            case "pickups" -> "p.id";
            case "appointments" -> "a.id";
            case "deliveries" -> "d.id";
            case "quotes" -> "q.id";
            case "settlements" -> "s.id";
            case "prepayments" -> "p.id";
            default -> "id";
        };
    }

    private String orderBy(String entity) {
        return switch (entity) {
            case "vehicles" -> " order by plate";
            case "pickups" -> " order by pickup_date desc, code desc";
            case "appointments" -> " order by entry_date desc, code desc";
            case "weighings" -> " order by weigh_time desc, code desc";
            case "deliveries" -> " order by created_at desc";
            case "quotes" -> " order by q.created_at desc";
            case "settlements" -> " order by s.created_at desc";
            case "prepayments" -> " order by p.created_at desc";
            default -> " order by 1";
        };
    }

    private String table(String entity) {
        return switch (entity) {
            case "vehicles" -> "road_vehicles";
            case "pickups" -> "road_pickups";
            case "appointments" -> "road_appointments";
            case "weighings" -> "road_weighings";
            case "deliveries" -> "road_deliveries";
            case "quotes" -> "road_quotes";
            case "settlements" -> "road_settlements";
            case "prepayments" -> "road_prepayments";
            default -> throw new BusinessException(404, "公路运输业务模块不存在");
        };
    }

    private static String codePrefix(String entity) {
        return switch (entity) {
            case "vehicles" -> "RV";
            case "pickups" -> "PU";
            case "appointments" -> "RM";
            case "weighings" -> "RW";
            case "deliveries" -> "RD";
            case "quotes" -> "RQ";
            case "prepayments" -> "RPP";
            default -> "RB";
        };
    }

    private void requireEntity(String entity) {
        if (!WRITABLE.containsKey(entity)) throw new BusinessException(404, "公路运输业务模块不存在");
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

    private static String trim(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
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
        if (input.containsKey(key) && input.get(key) != null && !input.get(key).toString().isBlank()
                && !allowed.contains(input.get(key).toString())) {
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
        result.put("vehicles", columns(
                "plate", "plate", "trailer", "trailer", "vehicleType", "vehicle_type",
                "driver", "driver", "phone", "phone", "idCard", "id_card",
                "fleet", "fleet", "status", "status", "notes", "notes"));
        result.put("pickups", columns(
                "entrustNo", "entrust_no", "customerId", "customer_id", "intentionId", "intention_id",
                "contractId", "contract_id", "customerName", "customer_name", "factory", "factory",
                "cargo", "cargo", "tons", "tons", "pieces", "pieces",
                "consigneeName", "consignee_name", "consigneePhone", "consignee_phone",
                "destination", "destination", "pickupDate", "pickup_date", "vehicleId", "vehicle_id",
                "plate", "plate", "driver", "driver", "status", "status", "notes", "notes"));
        result.put("appointments", columns(
                "appointmentNo", "appointment_no", "pickupId", "pickup_id", "station", "station",
                "yard", "yard", "cargo", "cargo", "tons", "tons", "entryDate", "entry_date",
                "cutoffAt", "cutoff_at", "toStation", "to_station", "driverAccepted", "driver_accepted",
                "sentTons", "sent_tons", "status", "status", "notes", "notes"));
        result.put("weighings", columns(
                "weighTime", "weigh_time", "plate", "plate", "cargo", "cargo", "shipper", "shipper",
                "transporter", "transporter", "grossWeight", "gross_weight", "tareWeight", "tare_weight",
                "netWeight", "net_weight", "flowNo", "flow_no", "wagonNo", "wagon_no",
                "operator", "operator", "status", "status", "notes", "notes"));
        result.put("deliveries", columns(
                "pickupId", "pickup_id", "consigneeName", "consignee_name",
                "consigneePhone", "consignee_phone", "address", "address", "destination", "destination",
                "wagonNo", "wagon_no", "tons", "tons", "pieces", "pieces",
                "shortHaulCompany", "short_haul_company", "distanceKm", "distance_km",
                "shortHaulFee", "short_haul_fee", "deliveryAt", "delivery_at", "signedBy", "signed_by",
                "status", "status", "notes", "notes"));
        result.put("quotes", columns(
                "customerId", "customer_id", "customerName", "customer_name", "fromPoint", "from_point", "toStation", "to_station",
                "cargo", "cargo", "mode", "mode", "actualTons", "actual_tons", "railFreight", "rail_freight",
                "fixedFee", "fixed_fee", "shortHaulPerTon", "short_haul_per_ton",
                "profitPerTon", "profit_per_ton", "costPerTon", "cost_per_ton",
                "quotePerTon", "quote_per_ton", "totalPrice", "total_price", "status", "status", "notes", "notes"));
        result.put("prepayments", columns(
                "customerId", "customer_id", "amount", "amount",
                "usedAmount", "used_amount", "balance", "balance", "paidAt", "paid_at",
                "bankRef", "bank_ref", "status", "status", "notes", "notes"));
        result.put("settlements", columns(
                "customerId", "customer_id", "customerName", "customer_name", "pickupId", "pickup_id",
                "deliveryId", "delivery_id", "serviceMode", "service_mode", "factory", "factory",
                "origin", "origin", "destination", "destination", "consigneeName", "consignee_name",
                "wagonNo", "wagon_no", "cargo", "cargo", "freightAmount", "freight_amount",
                "taxRate", "tax_rate", "invoiceNo", "invoice_no", "invoiceDate", "invoice_date",
                "paymentStatus", "payment_status", "receivedAmount", "received_amount",
                "receivedAt", "received_at", "bankRef", "bank_ref", "prepaymentDeducted", "prepayment_deducted",
                "status", "status", "notes", "notes"));
        return result;
    }

    private static Map<String, String> columns(String... values) {
        var result = new LinkedHashMap<String, String>();
        for (int index = 0; index < values.length; index += 2) result.put(values[index], values[index + 1]);
        return Map.copyOf(result);
    }
}
