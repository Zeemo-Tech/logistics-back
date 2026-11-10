package org.example.logisticsback.maintenance;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static org.example.logisticsback.maintenance.MaintenanceModels.*;

@Repository
public class PlanTaskRepository {
    private static final String SELECT = """
            select t.*, p.code as plan_code, p.name as plan_name, p.plan_type,
                p.needs_approval, p.maintenance_method,
                a.code as asset_code, a.name as asset_name
            from maintenance_plan_tasks t
            join maintenance_plans p on p.id = t.plan_id
            join assets a on a.id = t.asset_id
            """;
    private final JdbcTemplate jdbc;

    public PlanTaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PlanTask> list(String status, String keyword) {
        var sql = new StringBuilder(SELECT + " where 1 = 1");
        var params = new ArrayList<Object>();
        if (status != null && !status.isBlank()) {
            sql.append(" and t.status = ?");
            params.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" and (lower(t.title) like lower(?) or lower(p.name) like lower(?)"
                    + " or lower(a.code) like lower(?) or lower(a.name) like lower(?))");
            var pattern = "%" + keyword.trim() + "%";
            for (int i = 0; i < 4; i++) params.add(pattern);
        }
        sql.append(" order by case when t.status = 'PENDING_CONFIRMATION' then 0"
                + " when t.status = 'PENDING_APPROVAL' then 1 else 2 end, t.scheduled_date, t.id");
        return jdbc.query(sql.toString(), this::map, params.toArray());
    }

    public Optional<PlanTask> find(String id) {
        return jdbc.query(SELECT + " where t.id = ?", this::map, id).stream().findFirst();
    }

    public Optional<PlanTask> lock(String id) {
        return jdbc.query(SELECT + " where t.id = ? for update", this::map, id).stream().findFirst();
    }

    public Optional<PlanTask> findOpenByPlan(String planId) {
        return jdbc.query(SELECT + """
                where t.plan_id = ? and t.status in ('PENDING_CONFIRMATION', 'REJECTED',
                    'PENDING_APPROVAL', 'APPROVED') order by t.created_at desc, t.id limit 1
                """, this::map, planId).stream().findFirst();
    }

    public boolean existsOpen(String planId, LocalDate scheduledDate) {
        Integer count = jdbc.queryForObject("""
                select count(*) from maintenance_plan_tasks
                where plan_id = ? and scheduled_date = ? and status not in ('CANCELLED')
                """, Integer.class, planId, scheduledDate);
        return count != null && count > 0;
    }

    public void insert(String id, String planId, String assetId, LocalDate scheduledDate,
                       String title, String content) {
        jdbc.update("""
                insert into maintenance_plan_tasks (id, plan_id, asset_id, scheduled_date, title, content)
                values (?, ?, ?, ?, ?, ?)
                """, id, planId, assetId, scheduledDate, title, content);
    }

    public void confirm(String id, PlanTaskConfirm input, Long operator) {
        jdbc.update("""
                update maintenance_plan_tasks set content = ?, planned_date = ?, estimated_cost = ?,
                    notes = ?, confirmed_by = ?, confirmed_at = current_timestamp,
                    version = version + 1, updated_at = current_timestamp where id = ?
                """, input.content().trim(), input.plannedDate(), input.estimatedCost(),
                input.notes() == null ? "" : input.notes().trim(), operator, id);
    }

    public void linkApproval(String id, String approvalId) {
        jdbc.update("""
                update maintenance_plan_tasks set approval_id = ?, status = 'PENDING_APPROVAL',
                    version = version + 1, updated_at = current_timestamp where id = ?
                """, approvalId, id);
    }

    public void linkWorkOrder(String id, String workOrderId) {
        jdbc.update("""
                update maintenance_plan_tasks set work_order_id = ?, status = 'APPROVED',
                    version = version + 1, updated_at = current_timestamp where id = ?
                """, workOrderId, id);
    }

    public void setStatus(String id, String status, String note) {
        jdbc.update("""
                update maintenance_plan_tasks set status = ?, notes = ?, version = version + 1,
                    updated_at = current_timestamp where id = ?
                """, status, note == null ? "" : note.trim(), id);
    }

    public Optional<PlanTask> findByWorkOrder(String workOrderId) {
        return jdbc.query(SELECT + " where t.work_order_id = ?", this::map, workOrderId)
                .stream().findFirst();
    }

    private PlanTask map(java.sql.ResultSet r, int row) throws java.sql.SQLException {
        return new PlanTask(r.getString("id"), r.getString("plan_id"), r.getString("plan_code"),
                r.getString("plan_name"), r.getString("plan_type"), r.getString("asset_id"),
                r.getString("asset_code"), r.getString("asset_name"),
                r.getObject("scheduled_date", LocalDate.class), r.getString("title"),
                r.getString("content"), r.getObject("planned_date", LocalDate.class),
                r.getBigDecimal("estimated_cost"), r.getString("status"), r.getBoolean("needs_approval"),
                r.getString("maintenance_method"), r.getString("approval_id"),
                r.getString("work_order_id"), r.getObject("confirmed_by", Long.class),
                r.getObject("confirmed_at", LocalDateTime.class), r.getInt("version"),
                r.getString("notes"), r.getObject("created_at", LocalDateTime.class),
                r.getObject("updated_at", LocalDateTime.class));
    }
}
