package org.example.logisticsback.maintenance;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkOrderRepository {
    private static final String SELECT = """
            select w.*, a.code as asset_code, a.name as asset_name,
                (select code from maintenance_approvals ap where ap.id = w.approval_id) as approval_code,
                (select name from departments d where d.id = w.reporter_department_id) as reporter_department_name
            from maintenance_work_orders w join assets a on a.id = w.asset_id
            """;
    private final JdbcTemplate jdbc;

    public WorkOrderRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<WorkOrder> search(String assetId, String status, String keyword) {
        return search(assetId, status, keyword, null);
    }

    public List<WorkOrder> search(String assetId, String status, String keyword, String approvalStatus) {
        var sql = new StringBuilder(SELECT + " where w.deleted_at is null");
        var params = new ArrayList<Object>();
        if (assetId != null && !assetId.isBlank()) {
            sql.append(" and w.asset_id = ?"); params.add(assetId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and w.status = ?"); params.add(status);
        }
        if (approvalStatus != null && !approvalStatus.isBlank()) {
            sql.append(" and w.approval_status = ?"); params.add(approvalStatus);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" and (lower(w.code) like lower(?) escape '!' or lower(w.title) like lower(?) escape '!'"
                    + " or lower(a.code) like lower(?) escape '!' or lower(a.name) like lower(?) escape '!')");
            var pattern = "%" + keyword.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            for (int i = 0; i < 4; i++) params.add(pattern);
        }
        sql.append(" order by w.created_at desc, w.id");
        return jdbc.query(sql.toString(), this::map, params.toArray());
    }

    public List<WorkOrder> archive(String assetId, String keyword, java.time.LocalDate from, java.time.LocalDate to) {
        var sql = new StringBuilder(SELECT + " where w.deleted_at is null and w.status = 'COMPLETED'");
        var params = new ArrayList<Object>();
        if (assetId != null && !assetId.isBlank()) {
            sql.append(" and w.asset_id = ?"); params.add(assetId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" and (lower(w.code) like lower(?) or lower(w.title) like lower(?)"
                    + " or lower(a.code) like lower(?) or lower(a.name) like lower(?))");
            var pattern = "%" + keyword.trim() + "%";
            for (int i = 0; i < 4; i++) params.add(pattern);
        }
        if (from != null) {
            sql.append(" and w.completed_at >= ?"); params.add(from.atStartOfDay());
        }
        if (to != null) {
            sql.append(" and w.completed_at < ?"); params.add(to.plusDays(1).atStartOfDay());
        }
        sql.append(" order by w.completed_at desc, w.id");
        return jdbc.query(sql.toString(), this::map, params.toArray());
    }

    public Optional<WorkOrder> find(String id) {
        return jdbc.query(SELECT + " where w.id = ? and w.deleted_at is null", this::map, id).stream().findFirst();
    }

    public boolean hasOpenOrder(String assetId) {
        return jdbc.queryForObject("select count(*) from maintenance_work_orders where active_asset_id = ?",
                Integer.class, assetId) > 0;
    }

    public void insert(String id, String code, WorkOrderRequests.Create input, String previous,
                       Long reporter, String reporterName, String status, String approvalStatus) {
        var faultLocation = input.faultLocation() == null || input.faultLocation().isBlank()
                ? jdbc.queryForObject("select location from assets where id = ?", String.class, input.assetId())
                : input.faultLocation().trim();
        jdbc.update("""
                insert into maintenance_work_orders (id, code, asset_id, active_asset_id, title, description,
                    priority, status, previous_availability, reporter_id, reporter_name, approval_status,
                    fault_type, fault_location, impact_operations, reporter_department_id, emergency,
                    external_repair, vendor_type, vendor_name, vendor_contact, vendor_phone, technician,
                    specialty, qualification, repair_plan, expected_arrival, expected_completion, quoted_amount, secondary_of)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, code, input.assetId(), input.assetId(), input.title().trim(),
                input.description().trim(), input.priority(), status, previous, reporter, text(reporterName),
                approvalStatus, text(input.faultType()), faultLocation, Boolean.TRUE.equals(input.impactOperations()),
                blankToNull(input.reporterDepartmentId()), Boolean.TRUE.equals(input.emergency()),
                Boolean.TRUE.equals(input.externalRepair()), text(input.vendorType()), text(input.vendorName()),
                text(input.vendorContact()), text(input.vendorPhone()), text(input.technician()),
                text(input.specialty()), text(input.qualification()), text(input.repairPlan()),
                input.expectedArrival(), input.expectedCompletion(), input.quotedAmount(),
                blankToNull(input.secondaryOf()));
    }

    public void transition(String id, String status, String assignee, String repairNotes,
                           String acceptanceNotes, String resolution, BigDecimal laborCost,
                           BigDecimal partsCost, Integer downtimeMinutes) {
        boolean closed = "COMPLETED".equals(status) || "CANCELLED".equals(status);
        jdbc.update("""
                update maintenance_work_orders set status = ?, assignee = ?, repair_notes = ?,
                    acceptance_notes = ?, resolution_availability = ?, active_asset_id = case when ? then null else asset_id end,
                    labor_cost = coalesce(?, labor_cost), parts_cost = coalesce(?, parts_cost),
                    total_cost = coalesce(?, labor_cost, 0) + coalesce(?, parts_cost, 0),
                    downtime_minutes = coalesce(?, downtime_minutes),
                    version = version + 1, updated_at = current_timestamp,
                    completed_at = case when ? then current_timestamp else completed_at end where id = ?
                """, status, assignee, repairNotes, acceptanceNotes, resolution, closed,
                laborCost, partsCost, laborCost, partsCost, downtimeMinutes, closed, id);
    }

    public void edit(String id,WorkOrderRequests.Edit r) {
        jdbc.update("""
                update maintenance_work_orders set title = ?, description = ?, priority = ?,
                    fault_type = coalesce(?, fault_type),
                    fault_location = coalesce(?, fault_location),
                    impact_operations = coalesce(?, impact_operations),
                    reporter_department_id = coalesce(?, reporter_department_id),
                    version = version + 1, updated_at = current_timestamp where id = ?
                """, r.title().trim(), r.description().trim(), r.priority(),
                blankToNull(r.faultType()), blankToNull(r.faultLocation()), r.impactOperations(),
                blankToNull(r.reporterDepartmentId()), id);
    }

    public void updateRepair(String id, WorkOrderRequests.Repair r, boolean externalRepair) {
        jdbc.update("""
                update maintenance_work_orders set external_repair = ?, vendor_type = ?, vendor_name = ?,
                    vendor_contact = ?, vendor_phone = ?, technician = ?, specialty = ?, qualification = ?,
                    repair_plan = ?, expected_arrival = ?, expected_completion = ?, quoted_amount = ?,
                    labor_cost = coalesce(?, labor_cost), parts_cost = coalesce(?, parts_cost),
                    total_cost = coalesce(?, labor_cost, 0) + coalesce(?, parts_cost, 0),
                    downtime_minutes = coalesce(?, downtime_minutes),
                    version = version + 1, updated_at = current_timestamp where id = ?
                """, externalRepair, text(r.vendorType()), text(r.vendorName()), text(r.vendorContact()),
                text(r.vendorPhone()), text(r.technician()), text(r.specialty()), text(r.qualification()),
                text(r.repairPlan()), r.expectedArrival(), r.expectedCompletion(), r.quotedAmount(),
                r.laborCost(), r.partsCost(), r.laborCost(), r.partsCost(), r.downtimeMinutes(), id);
    }

    public void updateStartInfo(String id, java.time.LocalDateTime actualArrival, String siteTechnician,
                                String sitePhone, String siteNotes) {
        jdbc.update("""
                update maintenance_work_orders set actual_arrival = ?, site_technician = ?, site_phone = ?,
                    site_notes = ?, updated_at = current_timestamp where id = ?
                """, actualArrival, siteTechnician, sitePhone, siteNotes, id);
    }

    public void pause(String id, String reason) {
        jdbc.update("""
                update maintenance_work_orders set paused_reason = ?, version = version + 1,
                    updated_at = current_timestamp where id = ?
                """, text(reason), id);
    }

    public void resume(String id) {
        jdbc.update("""
                update maintenance_work_orders set paused_reason = '', version = version + 1,
                    updated_at = current_timestamp where id = ?
                """, id);
    }

    public void extend(String id, LocalDateTime expectedCompletion) {
        jdbc.update("""
                update maintenance_work_orders set expected_completion = ?, version = version + 1,
                    updated_at = current_timestamp where id = ?
                """, expectedCompletion, id);
    }

    public void updateApprovalFields(String id, BigDecimal quotedAmount, String repairPlan) {
        jdbc.update("""
                update maintenance_work_orders set quoted_amount = coalesce(?, quoted_amount),
                    repair_plan = case when ? = '' then repair_plan else ? end,
                    version = version + 1, updated_at = current_timestamp where id = ?
                """, quotedAmount, text(repairPlan), text(repairPlan), id);
    }

    public void updateApprovalOnly(String id, String approvalId, String approvalStatus) {
        jdbc.update("""
                update maintenance_work_orders set approval_id = ?, approval_status = ?,
                    version = version + 1, updated_at = current_timestamp where id = ?
                """, approvalId, approvalStatus, id);
    }

    public void softDelete(String id) { jdbc.update("update maintenance_work_orders set deleted_at=current_timestamp,version=version+1 where id=?",id); }

    public void addEvent(String id, String action, String description, Long operatorId) {
        jdbc.update("insert into maintenance_work_order_events (id, work_order_id, action, description, operator_id, step_version) "
                        + "values (?, ?, ?, ?, ?, (select version from maintenance_work_orders where id = ?))",
                UUID.randomUUID().toString(), id, action, description, operatorId, id);
    }

    public List<WorkOrderEvent> events(String id) {
        return jdbc.query("""
                select e.*, coalesce(nullif(u.real_name, ''), u.username, '未知用户') as operator_name
                from maintenance_work_order_events e left join users u on u.id = e.operator_id
                where e.work_order_id = ? order by e.step_version, e.id
                """, (r, n) -> new WorkOrderEvent(r.getString("id"), r.getString("action"),
                r.getString("description"), r.getLong("operator_id"), r.getString("operator_name"),
                r.getObject("created_at", LocalDateTime.class)), id);
    }

    public void addPart(String workOrderId, WorkOrderRequests.Part input) {
        var amount = input.quantity().multiply(input.unitPrice());
        jdbc.update("""
                insert into maintenance_work_order_parts (id, work_order_id, name, spec, quantity, unit,
                    unit_price, amount, supplier, replaced_part_code, remark)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), workOrderId, input.name().trim(), text(input.spec()),
                input.quantity(), input.unit() == null || input.unit().isBlank() ? "件" : input.unit().trim(),
                input.unitPrice(), amount, text(input.supplier()), text(input.replacedPartCode()),
                text(input.remark()));
        recalculateParts(workOrderId);
    }

    public void removePart(String workOrderId, String partId) {
        jdbc.update("delete from maintenance_work_order_parts where id = ? and work_order_id = ?", partId, workOrderId);
        recalculateParts(workOrderId);
    }

    public List<WorkOrderPart> parts(String workOrderId) {
        return jdbc.query("select * from maintenance_work_order_parts where work_order_id = ? order by created_at, id",
                (r, n) -> new WorkOrderPart(r.getString("id"), r.getString("work_order_id"),
                        r.getString("name"), r.getString("spec"), r.getBigDecimal("quantity"),
                        r.getString("unit"), r.getBigDecimal("unit_price"), r.getBigDecimal("amount"),
                        r.getString("supplier"), r.getString("replaced_part_code"), r.getString("remark"),
                        r.getObject("created_at", LocalDateTime.class)), workOrderId);
    }

    private void recalculateParts(String workOrderId) {
        jdbc.update("""
                update maintenance_work_orders set parts_cost = coalesce((select sum(amount)
                        from maintenance_work_order_parts where work_order_id = ?), 0),
                    total_cost = coalesce(labor_cost, 0) + coalesce((select sum(amount)
                        from maintenance_work_order_parts where work_order_id = ?), 0),
                    version = version + 1, updated_at = current_timestamp where id = ?
                """, workOrderId, workOrderId, workOrderId);
    }

    public String operatorName(Long userId) {
        if (userId == null) return "系统";
        return jdbc.query("select coalesce(nullif(real_name, ''), username, '未知用户') from users where id = ?",
                rs -> rs.next() ? rs.getString(1) : "未知用户", userId);
    }

    private WorkOrder map(ResultSet r, int n) throws SQLException {
        return new WorkOrder(r.getString("id"), r.getString("code"), r.getString("asset_id"),
                r.getString("asset_code"), r.getString("asset_name"), r.getString("title"),
                r.getString("description"), r.getString("priority"), r.getString("status"),
                r.getString("assignee"), r.getString("repair_notes"), r.getString("acceptance_notes"),
                r.getString("previous_availability"), r.getString("resolution_availability"),
                r.getLong("reporter_id"), r.getInt("version"), r.getObject("created_at", LocalDateTime.class),
                r.getObject("updated_at", LocalDateTime.class), r.getObject("completed_at", LocalDateTime.class),
                r.getString("approval_id"), r.getString("approval_code"), r.getString("approval_status"),
                r.getString("fault_type"), r.getString("fault_location"), r.getBoolean("impact_operations"),
                r.getString("reporter_name"), r.getString("reporter_department_id"),
                r.getString("reporter_department_name"), r.getBoolean("emergency"),
                r.getBoolean("external_repair"), r.getString("vendor_type"), r.getString("vendor_name"),
                r.getString("vendor_contact"), r.getString("vendor_phone"), r.getString("technician"),
                r.getString("specialty"), r.getString("qualification"), r.getString("repair_plan"),
                r.getObject("expected_arrival", LocalDateTime.class),
                r.getObject("expected_completion", LocalDateTime.class),
                r.getBigDecimal("quoted_amount"), r.getBigDecimal("labor_cost"), r.getBigDecimal("parts_cost"),
                r.getBigDecimal("total_cost"), r.getObject("downtime_minutes", Integer.class),
                r.getString("paused_reason"), r.getString("secondary_of"),
                r.getObject("actual_arrival", LocalDateTime.class), r.getString("site_technician"),
                r.getString("site_phone"), r.getString("site_notes"));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
