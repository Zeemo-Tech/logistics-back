package org.example.logisticsback.assets;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AssetRepository {
    private static final String SELECT = """
            select a.*,
                (select d.name from departments d where d.id = a.department_id) as department_name,
                (select c.name from equipment_categories c where c.id = a.category_id) as category_name,
                (select p.name from equipment_categories c join equipment_categories p on p.id = c.parent_id
                    where c.id = a.category_id) as category_group_name
            from assets a
            """;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public AssetRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    public List<AssetRecord> search(AssetQuery query) {
        var filter = filter(query);
        return jdbc.query(SELECT + filter.sql() + " order by a.kind, a.code, a.id",
                this::mapAsset, filter.params().toArray());
    }

    public AssetViews.Page page(AssetQuery query, int page, int pageSize) {
        var filter = filter(query);
        Long total = jdbc.queryForObject("select count(*) from assets a" + filter.sql(),
                Long.class, filter.params().toArray());
        var params = new ArrayList<>(filter.params());
        params.add(pageSize);
        params.add(((long) page - 1) * pageSize);
        var records = jdbc.query(SELECT + filter.sql()
                + " order by a.code, a.id limit ? offset ?", this::mapAsset, params.toArray());
        var linkedIds = records.stream().map(AssetRecord::linkedAssetId)
                .filter(Objects::nonNull).distinct().toList();
        return new AssetViews.Page(records, total == null ? 0 : total, page, pageSize, optionsByIds(linkedIds));
    }

    public AssetViews.Overview overview() {
        var metrics = jdbc.queryForObject("""
                select coalesce(sum(case when lifecycle_status = 'ACTIVE' then 1 else 0 end), 0) as active_count,
                    coalesce(sum(case when lifecycle_status = 'ACTIVE' and availability_status = 'AVAILABLE' then 1 else 0 end), 0) as available_count,
                    coalesce(sum(case when lifecycle_status = 'ACTIVE' and availability_status = 'MAINTENANCE' then 1 else 0 end), 0) as maintenance_count,
                    coalesce(sum(case when lifecycle_status = 'ACTIVE' and kind = 'DEVICE' and condition_status in ('OFFLINE', 'ALERT') then 1 else 0 end), 0) as device_issue_count
                from assets
                """, (rs, row) -> new AssetViews.Metrics(rs.getLong("active_count"), rs.getLong("available_count"),
                        rs.getLong("maintenance_count"), rs.getLong("device_issue_count")));
        var kinds = jdbc.query("select kind, count(*) as asset_count from assets group by kind order by kind",
                (rs, row) -> new AssetViews.KindSummary(rs.getString("kind"), rs.getLong("asset_count")));
        var capacities = jdbc.query("""
                select kind, coalesce(sum(capacity_total), 0) as total, coalesce(sum(capacity_used), 0) as used
                from assets where lifecycle_status = 'ACTIVE' and kind in ('WAREHOUSE', 'COLD_STORAGE') group by kind
                """, (rs, row) -> new AssetViews.CapacitySummary(rs.getString("kind"), rs.getBigDecimal("total"),
                        rs.getBigDecimal("used"), "WAREHOUSE".equals(rs.getString("kind")) ? "m²" : "吨"));
        var attention = jdbc.query(SELECT + """
                where a.lifecycle_status = 'ACTIVE'
                    and (a.availability_status = 'MAINTENANCE'
                        or (a.kind = 'DEVICE' and a.condition_status in ('OFFLINE', 'ALERT'))
                        or a.maintenance_due_date <= ?
                        or a.inspection_due_date <= ?
                        or a.warranty_expiry <= ?
                        or a.maintenance_contract_expiry <= ?
                        or a.expected_retirement_date <= ?)
                order by case when a.availability_status = 'MAINTENANCE' then 0
                              when a.condition_status in ('OFFLINE', 'ALERT') then 1 else 2 end,
                         case when a.maintenance_due_date is null then a.inspection_due_date
                              when a.inspection_due_date is null then a.maintenance_due_date
                              when a.maintenance_due_date < a.inspection_due_date then a.maintenance_due_date
                              else a.inspection_due_date end, a.kind, a.code limit 12
                """, this::mapAsset, LocalDate.now().plusDays(30), LocalDate.now().plusDays(30),
                LocalDate.now().plusDays(30), LocalDate.now().plusDays(30), LocalDate.now().plusDays(30));
        return new AssetViews.Overview(metrics, kinds, capacities, attention);
    }

    public AssetViews.Relations relations(AssetRecord asset) {
        var linked = asset.linkedAssetId() == null ? null
                : optionsByIds(List.of(asset.linkedAssetId())).stream().findFirst().orElse(null);
        var related = jdbc.query("select id, code, name, kind from assets where linked_asset_id = ? order by kind, code, id",
                this::mapOption, asset.id());
        return new AssetViews.Relations(linked, related);
    }

    public List<AssetViews.Option> bindingOptions(String kind, String keyword, String excludeId, String selectedId) {
        var params = new ArrayList<Object>();
        var where = new StringBuilder(" where lifecycle_status = 'ACTIVE'");
        where.append("MOBILE_CABINET".equals(kind) ? " and kind = 'VEHICLE'" : " and kind <> 'DEVICE'");
        if (excludeId != null && !excludeId.isBlank()) {
            where.append(" and id <> ?");
            params.add(excludeId);
        }
        // Always include a valid current selection, even when it falls outside the first search page.
        var selectionWhere = where.toString();
        var selectionParams = new ArrayList<>(params);
        if (keyword != null && !keyword.isBlank()) {
            where.append(" and (lower(code) like lower(?) or lower(name) like lower(?))");
            params.add("%" + keyword.trim() + "%");
            params.add("%" + keyword.trim() + "%");
        }
        var options = new ArrayList<>(jdbc.query("select id, code, name, kind from assets" + where
                + " order by code, id limit 30", this::mapOption, params.toArray()));
        if (selectedId != null && !selectedId.isBlank()
                && options.stream().noneMatch(option -> option.id().equals(selectedId))) {
            selectionParams.add(selectedId);
            options.addAll(jdbc.query("select id, code, name, kind from assets" + selectionWhere + " and id = ?",
                    this::mapOption, selectionParams.toArray()));
        }
        return options;
    }

    private List<AssetViews.Option> optionsByIds(Collection<String> ids) {
        if (ids.isEmpty()) return List.of();
        return namedJdbc.query("select id, code, name, kind from assets where id in (:ids)",
                new MapSqlParameterSource("ids", ids), this::mapOption);
    }

    private AssetViews.Option mapOption(ResultSet result, int rowNumber) throws SQLException {
        return new AssetViews.Option(result.getString("id"), result.getString("code"),
                result.getString("name"), result.getString("kind"));
    }

    private record Filter(String sql, List<Object> params) {}

    private Filter filter(AssetQuery query) {
        var sql = new StringBuilder(" where 1 = 1");
        var params = new ArrayList<Object>();
        if (query.kind() != null && !query.kind().isBlank()) {
            if ("EQUIPMENT".equals(query.kind())) {
                sql.append(" and a.kind in ('FACILITY', 'DEVICE')");
            } else {
                sql.append(" and a.kind = ?");
                params.add(query.kind());
            }
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            sql.append(" and (lower(a.code) like lower(?) or lower(a.name) like lower(?) or lower(a.location) like lower(?))");
            var pattern = "%" + query.keyword().trim() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        if (query.category() != null && !query.category().isBlank()) {
            sql.append(" and a.category = ?");
            params.add(query.category());
        }
        if (query.subcategory() != null && !query.subcategory().isBlank()) {
            sql.append(" and a.subcategory = ?");
            params.add(query.subcategory());
        }
        if (query.departmentId() != null && !query.departmentId().isBlank()) {
            sql.append(" and a.department_id = ?");
            params.add(query.departmentId());
        }
        if (query.categoryId() != null && !query.categoryId().isBlank()) {
            sql.append(" and (a.category_id = ? or a.category_id in (select id from equipment_categories where parent_id = ?))");
            params.add(query.categoryId());
            params.add(query.categoryId());
        }
        if (query.lifecycleStatus() != null && !query.lifecycleStatus().isBlank()) {
            sql.append(" and a.lifecycle_status = ?");
            params.add(query.lifecycleStatus());
        }
        if (query.availabilityStatus() != null && !query.availabilityStatus().isBlank()) {
            sql.append(" and a.availability_status = ?");
            params.add(query.availabilityStatus());
        }
        warningFilter(sql, params, query.warningLevel());
        return new Filter(sql.toString(), params);
    }

    private void warningFilter(StringBuilder sql, List<Object> params, String level) {
        if (level == null || level.isBlank()) return;
        var today = LocalDate.now();
        var nearest = "case when a.maintenance_due_date is null then a.inspection_due_date"
                + " when a.inspection_due_date is null then a.maintenance_due_date"
                + " when a.maintenance_due_date < a.inspection_due_date then a.maintenance_due_date"
                + " else a.inspection_due_date end";
        switch (level) {
            case "OVERDUE" -> {
                sql.append(" and (a.maintenance_due_date < ? or a.inspection_due_date < ? or a.warranty_expiry < ?"
                        + " or a.maintenance_contract_expiry < ? or a.expected_retirement_date < ?)");
                for (int i = 0; i < 5; i++) params.add(today);
            }
            case "DUE_TODAY" -> {
                sql.append(" and (a.maintenance_due_date = ? or a.inspection_due_date = ? or a.warranty_expiry = ?"
                        + " or a.maintenance_contract_expiry = ? or a.expected_retirement_date = ?)");
                for (int i = 0; i < 5; i++) params.add(today);
            }
            case "CRITICAL" -> {
                sql.append(" and (" + nearest + " between ? and ?)");
                params.add(today);
                params.add(today.plusDays(7));
            }
            case "DUE_SOON" -> {
                sql.append(" and (" + nearest + " between ? and ?)");
                params.add(today.plusDays(8));
                params.add(today.plusDays(30));
            }
            case "NORMAL" -> {
                sql.append(" and (" + nearest + " is null or " + nearest + " > ?)");
                params.add(today.plusDays(30));
            }
            default -> { }
        }
    }

    public Optional<AssetRecord> findById(String id) {
        return jdbc.query(SELECT + " where a.id = ?", this::mapAsset, id).stream().findFirst();
    }

    public Optional<AssetRecord> lockById(String id) {
        return jdbc.query(SELECT + " where a.id = ? for update", this::mapAsset, id).stream().findFirst();
    }

    public void setAvailability(String id, String status) {
        jdbc.update("update assets set availability_status = ?, run_status = ?, updated_at = current_timestamp where id = ?",
                status, runStatusForAvailability(status), id);
    }

    public void setRunStatus(String id, String status) {
        jdbc.update("update assets set run_status = ?, updated_at = current_timestamp where id = ?", status, id);
    }

    public boolean departmentExists(String id) {
        Integer count = jdbc.queryForObject("select count(*) from departments where id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    public boolean categoryExists(String id) {
        Integer count = jdbc.queryForObject("select count(*) from equipment_categories where id = ?",
                Integer.class, id);
        return count != null && count > 0;
    }

    public boolean categoryHasChildren(String id) {
        Integer count = jdbc.queryForObject(
                "select count(*) from equipment_categories where parent_id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    public boolean hasActiveDependents(String id) {
        Integer count = jdbc.queryForObject(
                "select count(*) from assets where linked_asset_id = ? and lifecycle_status = 'ACTIVE'",
                Integer.class, id);
        return count != null && count > 0;
    }

    public void insert(String id, String code, AssetUpsertRequest request) {
        var sql = """
                insert into assets (id, code, kind, name, category, ownership, owner_name, site,
                    location, custodian, lifecycle_status, availability_status, condition_status,
                    linked_asset_id, capacity_total, capacity_used, capacity_unit, temperature_min,
                    temperature_max, battery_percent, rated_load_tons, insurance_expiry, last_seen, notes)
                values (:id, :code, :kind, :name, :category, :ownership, :ownerName, :site,
                    :location, :custodian, :lifecycleStatus, :availabilityStatus, :conditionStatus,
                    :linkedAssetId, :capacityTotal, :capacityUsed, :capacityUnit, :temperatureMin,
                    :temperatureMax, :batteryPercent, :ratedLoadTons, :insuranceExpiry, :lastSeen, :notes)
                """;
        namedJdbc.update(sql, parameters(request).addValue("id", id).addValue("code", code));
        updateEquipmentFields(id, request);
    }

    public void update(String id, AssetUpsertRequest request) {
        var sql = """
                update assets set name = :name, category = :category, ownership = :ownership,
                    owner_name = :ownerName, site = :site, location = :location,
                    custodian = :custodian, lifecycle_status = :lifecycleStatus,
                    availability_status = :availabilityStatus, condition_status = :conditionStatus,
                    linked_asset_id = :linkedAssetId, capacity_total = :capacityTotal,
                    capacity_used = :capacityUsed, capacity_unit = :capacityUnit,
                    temperature_min = :temperatureMin, temperature_max = :temperatureMax,
                    battery_percent = :batteryPercent, rated_load_tons = :ratedLoadTons,
                    insurance_expiry = :insuranceExpiry, last_seen = :lastSeen, notes = :notes,
                    updated_at = current_timestamp
                where id = :id
                """;
        namedJdbc.update(sql, parameters(request).addValue("id", id));
        updateEquipmentFields(id, request);
    }

    private void updateEquipmentFields(String id, AssetUpsertRequest r) {
        jdbc.update("""
                update assets set manufacturer=?,equipment_model=?,serial_number=?,commissioned_date=?,
                    service_provider=?,maintenance_cycle=?,subcategory=?,warranty_expiry=?,
                    maintenance_due_date=?,inspection_due_date=?,department_id=?,category_id=?,
                    purchase_date=?,install_date=?,service_life_years=?,expected_retirement_date=?,
                    maintenance_contract_expiry=?,run_status=?,warning_days=?,critical_warning_days=?,
                    responsible_person=?,maintenance_contact=?,maintenance_phone=?,
                    warehouse=?,building=?,floor=?,last_maintenance_date=?,last_inspection_date=?,specification=? where id=?
                """,r.manufacturer(),r.equipmentModel(),r.serialNumber(),r.commissionedDate(),r.serviceProvider(),
                r.maintenanceCycle(),r.subcategory(),r.warrantyExpiry(),r.maintenanceDueDate(),r.inspectionDueDate(),
                blankToNull(r.departmentId()),blankToNull(r.categoryId()),r.purchaseDate(),r.installDate(),
                r.serviceLifeYears(),r.expectedRetirementDate(),r.maintenanceContractExpiry(),
                runStatusFor(r),
                r.warningDays()==null?30:r.warningDays(),r.criticalWarningDays()==null?7:r.criticalWarningDays(),
                text(r.responsiblePerson()),text(r.maintenanceContact()),text(r.maintenancePhone()),
                text(r.warehouse()),text(r.building()),text(r.floor()),
                r.lastMaintenanceDate(),r.lastInspectionDate(),text(r.specification()),id);
    }

    public boolean hasMaintenanceReferences(String id) {
        return jdbc.queryForObject("""
                select (select count(*) from maintenance_plans where asset_id=?) +
                    (select count(*) from safety_compliance_items where asset_id=?) +
                    (select count(*) from utility_meters where asset_id=?) +
                    (select count(*) from maintenance_work_orders where asset_id=?) +
                    (select count(*) from assets where linked_asset_id=?)
                """,Integer.class,id,id,id,id,id)>0;
    }
    public boolean hasActiveMaintenance(String id) {
        return jdbc.queryForObject("""
                select (select count(*) from maintenance_plans where asset_id=? and status='ACTIVE') +
                    (select count(*) from safety_compliance_items where asset_id=? and status<>'ARCHIVED') +
                    (select count(*) from utility_meters where asset_id=? and lifecycle_status='ACTIVE')
                """,Integer.class,id,id,id)>0;
    }
    public void deleteUnreferenced(String id, Long operator) {
        jdbc.update("insert into maintenance_audit(id,entity_type,entity_id,action,description,operator_id) values(?,'ASSET',?,'DELETE',?,?)",
                UUID.randomUUID().toString(),id,"删除无业务引用设备档案："+findById(id).orElseThrow().code(),operator);
        jdbc.update("delete from asset_events where asset_id=?",id);
        jdbc.update("delete from equipment_attachments where asset_id=?",id);
        jdbc.update("delete from assets where id=?",id);
    }

    public void addEvent(String assetId, String eventType, String description, Long operatorId) {
        jdbc.update("insert into asset_events (id, asset_id, event_type, description, operator_id) values (?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), assetId, eventType, description, operatorId);
    }

    public List<AssetEvent> events(String assetId) {
        return jdbc.query("select * from asset_events where asset_id = ? order by created_at desc, id desc",
                (result, rowNumber) -> new AssetEvent(
                        result.getString("id"), result.getString("asset_id"),
                        result.getString("event_type"), result.getString("description"),
                        result.getObject("operator_id", Long.class),
                        result.getObject("created_at", LocalDateTime.class)), assetId);
    }

    private MapSqlParameterSource parameters(AssetUpsertRequest request) {
        return new MapSqlParameterSource()
                .addValue("kind", request.kind()).addValue("name", request.name().trim())
                .addValue("category", request.category().trim()).addValue("ownership", request.ownership())
                .addValue("ownerName", request.ownerName().trim()).addValue("site", request.site().trim())
                .addValue("location", request.location().trim()).addValue("custodian", request.custodian().trim())
                .addValue("lifecycleStatus", request.lifecycleStatus())
                .addValue("availabilityStatus", request.availabilityStatus())
                .addValue("conditionStatus", request.conditionStatus())
                .addValue("linkedAssetId", request.linkedAssetId() == null || request.linkedAssetId().isBlank()
                        ? null : request.linkedAssetId())
                .addValue("capacityTotal", request.capacityTotal()).addValue("capacityUsed", request.capacityUsed())
                .addValue("capacityUnit", request.capacityUnit())
                .addValue("temperatureMin", request.temperatureMin()).addValue("temperatureMax", request.temperatureMax())
                .addValue("batteryPercent", request.batteryPercent()).addValue("ratedLoadTons", request.ratedLoadTons())
                .addValue("insuranceExpiry", request.insuranceExpiry()).addValue("lastSeen", request.lastSeen())
                .addValue("notes", request.notes() == null ? "" : request.notes().trim());
    }

    private AssetRecord mapAsset(ResultSet result, int rowNumber) throws SQLException {
        var insuranceExpiry = result.getDate("insurance_expiry");
        var maintenanceDueDate = result.getDate("maintenance_due_date");
        var inspectionDueDate = result.getDate("inspection_due_date");
        var warrantyExpiry = result.getObject("warranty_expiry", LocalDate.class);
        var maintenanceContractExpiry = result.getObject("maintenance_contract_expiry", LocalDate.class);
        var expectedRetirementDate = result.getObject("expected_retirement_date", LocalDate.class);
        var warningDays = result.getInt("warning_days");
        var criticalDays = result.getInt("critical_warning_days");
        var deadline = nearestDeadline(maintenanceDueDate == null ? null : maintenanceDueDate.toLocalDate(),
                inspectionDueDate == null ? null : inspectionDueDate.toLocalDate(), warrantyExpiry,
                maintenanceContractExpiry, expectedRetirementDate);
        return new AssetRecord(
                result.getString("id"), result.getString("code"), result.getString("kind"),
                result.getString("name"), result.getString("category"), result.getString("ownership"),
                result.getString("owner_name"), result.getString("site"), result.getString("location"),
                result.getString("custodian"), result.getString("lifecycle_status"),
                result.getString("availability_status"), result.getString("condition_status"),
                result.getString("linked_asset_id"), result.getBigDecimal("capacity_total"),
                result.getBigDecimal("capacity_used"), result.getString("capacity_unit"),
                result.getBigDecimal("temperature_min"), result.getBigDecimal("temperature_max"),
                result.getBigDecimal("battery_percent"), result.getBigDecimal("rated_load_tons"),
                insuranceExpiry == null ? null : insuranceExpiry.toLocalDate(),
                maintenanceDueDate == null ? null : maintenanceDueDate.toLocalDate(),
                inspectionDueDate == null ? null : inspectionDueDate.toLocalDate(),
                result.getString("last_seen"), result.getString("notes"),
                result.getString("manufacturer"), result.getString("equipment_model"),
                result.getString("serial_number"), result.getObject("commissioned_date", LocalDate.class),
                result.getString("service_provider"), result.getString("maintenance_cycle"),
                result.getString("subcategory"), warrantyExpiry,
                result.getString("department_id"), result.getString("department_name"),
                result.getString("category_id"), result.getString("category_name"),
                result.getString("category_group_name"),
                result.getObject("purchase_date", LocalDate.class),
                result.getObject("install_date", LocalDate.class),
                result.getObject("service_life_years", Integer.class),
                expectedRetirementDate,
                maintenanceContractExpiry,
                result.getString("run_status"), warningDays, criticalDays,
                result.getString("responsible_person"), result.getString("maintenance_contact"),
                result.getString("maintenance_phone"),
                deadline == null ? "NONE" : warningLevel(deadline.date(), warningDays, criticalDays),
                deadline == null ? null : deadline.date(), deadline == null ? "" : deadline.source(),
                result.getString("warehouse"), result.getString("building"), result.getString("floor"),
                result.getObject("last_maintenance_date", LocalDate.class),
                result.getObject("last_inspection_date", LocalDate.class),
                result.getString("specification"),
                result.getObject("created_at", LocalDateTime.class),
                result.getObject("updated_at", LocalDateTime.class));
    }

    private record Deadline(LocalDate date, String source) {}

    private static Deadline nearestDeadline(LocalDate maintenance, LocalDate inspection, LocalDate warranty,
                                            LocalDate contract, LocalDate retirement) {
        return Stream.of(new Deadline(maintenance, "维保"), new Deadline(inspection, "检验"),
                        new Deadline(warranty, "质保"), new Deadline(contract, "维保合同"),
                        new Deadline(retirement, "报废"))
                .filter(item -> item.date() != null)
                .min(Comparator.comparing(Deadline::date)).orElse(null);
    }

    public static String warningLevel(LocalDate date, int warningDays, int criticalDays) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), date);
        if (days < 0) return "OVERDUE";
        if (days == 0) return "DUE_TODAY";
        if (days <= criticalDays) return "CRITICAL";
        if (days <= warningDays) return "DUE_SOON";
        return "NORMAL";
    }

    private static String runStatusFor(AssetUpsertRequest r) {
        if (r.runStatus() != null && !r.runStatus().isBlank()) return r.runStatus();
        if ("RETIRED".equals(r.lifecycleStatus())) return "RETIRED";
        if ("INACTIVE".equals(r.lifecycleStatus())) return "SUSPENDED";
        if ("MAINTENANCE".equals(r.availabilityStatus())) return "REPAIRING";
        if ("UNAVAILABLE".equals(r.availabilityStatus())) return "FAULT";
        if ("OFFLINE".equals(r.conditionStatus()) || "ALERT".equals(r.conditionStatus())) return "ABNORMAL";
        return "RUNNING";
    }

    private static String runStatusForAvailability(String availability) {
        return switch (availability == null ? "" : availability) {
            case "MAINTENANCE" -> "REPAIRING";
            case "UNAVAILABLE" -> "FAULT";
            default -> "RUNNING";
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
