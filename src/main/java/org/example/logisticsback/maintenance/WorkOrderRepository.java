package org.example.logisticsback.maintenance;

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
    private static final String SELECT = "select w.*, a.code as asset_code, a.name as asset_name "
            + "from maintenance_work_orders w join assets a on a.id = w.asset_id";
    private final JdbcTemplate jdbc;

    public WorkOrderRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<WorkOrder> search(String assetId, String status, String keyword) {
        var sql = new StringBuilder(SELECT + " where w.deleted_at is null");
        var params = new ArrayList<Object>();
        if (assetId != null && !assetId.isBlank()) {
            sql.append(" and w.asset_id = ?"); params.add(assetId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and w.status = ?"); params.add(status);
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

    public Optional<WorkOrder> find(String id) {
        return jdbc.query(SELECT + " where w.id = ? and w.deleted_at is null", this::map, id).stream().findFirst();
    }

    public boolean hasOpenOrder(String assetId) {
        return jdbc.queryForObject("select count(*) from maintenance_work_orders where active_asset_id = ?",
                Integer.class, assetId) > 0;
    }

    public void insert(String id, String code, WorkOrderRequests.Create input, String previous, Long reporter) {
        jdbc.update("""
                insert into maintenance_work_orders (id, code, asset_id, active_asset_id, title,
                    description, priority, status, previous_availability, reporter_id)
                values (?, ?, ?, ?, ?, ?, ?, 'REPORTED', ?, ?)
                """, id, code, input.assetId(), input.assetId(), input.title().trim(),
                input.description().trim(), input.priority(), previous, reporter);
    }

    public void transition(String id, String status, String assignee, String repairNotes,
                           String acceptanceNotes, String resolution) {
        boolean closed = "COMPLETED".equals(status) || "CANCELLED".equals(status);
        jdbc.update("""
                update maintenance_work_orders set status = ?, assignee = ?, repair_notes = ?,
                    acceptance_notes = ?, resolution_availability = ?, active_asset_id = case when ? then null else asset_id end,
                    version = version + 1, updated_at = current_timestamp,
                    completed_at = case when ? then current_timestamp else null end where id = ?
                """, status, assignee, repairNotes, acceptanceNotes, resolution, closed, closed, id);
    }

    public void edit(String id,WorkOrderRequests.Edit r) {
        jdbc.update("update maintenance_work_orders set title=?,description=?,priority=?,version=version+1,updated_at=current_timestamp where id=?",r.title().trim(),r.description().trim(),r.priority(),id);
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

    private WorkOrder map(ResultSet r, int n) throws SQLException {
        return new WorkOrder(r.getString("id"), r.getString("code"), r.getString("asset_id"),
                r.getString("asset_code"), r.getString("asset_name"), r.getString("title"),
                r.getString("description"), r.getString("priority"), r.getString("status"),
                r.getString("assignee"), r.getString("repair_notes"), r.getString("acceptance_notes"),
                r.getString("previous_availability"), r.getString("resolution_availability"),
                r.getLong("reporter_id"), r.getInt("version"), r.getObject("created_at", LocalDateTime.class),
                r.getObject("updated_at", LocalDateTime.class), r.getObject("completed_at", LocalDateTime.class));
    }
}
