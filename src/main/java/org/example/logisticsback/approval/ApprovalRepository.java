package org.example.logisticsback.approval;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ApprovalRepository {
    private static final String SELECT = """
            select ap.*, d.name as department_name, a.code as asset_code, a.name as asset_name
            from maintenance_approvals ap
            left join departments d on d.id = ap.department_id
            left join assets a on a.id = ap.asset_id
            """;
    private final JdbcTemplate jdbc;

    public ApprovalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<MaintenanceApproval> search(String status, String businessType, String keyword) {
        var sql = new StringBuilder(SELECT + " where 1 = 1");
        var params = new ArrayList<Object>();
        if (status != null && !status.isBlank()) {
            sql.append(" and ap.status = ?");
            params.add(status);
        }
        if (businessType != null && !businessType.isBlank()) {
            sql.append(" and ap.business_type = ?");
            params.add(businessType);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" and (lower(ap.code) like lower(?) or lower(ap.title) like lower(?)"
                    + " or lower(ap.applicant_name) like lower(?) or lower(a.code) like lower(?))");
            var pattern = "%" + keyword.trim() + "%";
            for (int i = 0; i < 4; i++) params.add(pattern);
        }
        sql.append(" order by case when ap.status = 'PENDING' then 0 else 1 end, ap.created_at desc, ap.id");
        return jdbc.query(sql.toString(), this::map, params.toArray());
    }

    public Optional<MaintenanceApproval> find(String id) {
        return jdbc.query(SELECT + " where ap.id = ?", this::map, id).stream().findFirst();
    }

    public void insert(String id, String code, ApprovalRequests.Create input, Long applicantId,
                       String applicantName) {
        jdbc.update("""
                insert into maintenance_approvals (id, code, business_type, business_id, title, applicant_id,
                    applicant_name, department_id, asset_id, reason, repair_plan, estimated_cost,
                    needs_shutdown, needs_outsourcing, emergency, status)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
                """, id, code, input.businessType(), blankToNull(input.businessId()), input.title().trim(),
                applicantId, applicantName, blankToNull(input.departmentId()), blankToNull(input.assetId()),
                input.reason().trim(), text(input.repairPlan()), input.estimatedCost(),
                Boolean.TRUE.equals(input.needsShutdown()), Boolean.TRUE.equals(input.needsOutsourcing()),
                Boolean.TRUE.equals(input.emergency()));
    }

    public void transition(String id, String status, Long approverId, String approverName, String note) {
        boolean decided = !"PENDING".equals(status);
        jdbc.update("""
                update maintenance_approvals set status = ?, approver_id = coalesce(?, approver_id),
                    approver_name = case when ? = '' then approver_name else ? end,
                    decision_note = ?, version = version + 1, updated_at = current_timestamp,
                    decided_at = case when ? then current_timestamp else decided_at end
                where id = ?
                """, status, approverId, text(approverName), text(approverName), text(note), decided, id);
    }

    public void addRecord(String approvalId, String action, Long operatorId, String operatorName, String note) {
        jdbc.update("""
                insert into maintenance_approval_records (id, approval_id, action, operator_id, operator_name, note)
                values (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), approvalId, action, operatorId, text(operatorName), text(note));
    }

    public List<MaintenanceApprovalRecord> records(String approvalId) {
        return jdbc.query("""
                select * from maintenance_approval_records where approval_id = ? order by created_at, id
                """, (r, row) -> new MaintenanceApprovalRecord(r.getString("id"), r.getString("approval_id"),
                r.getString("action"), r.getObject("operator_id", Long.class), r.getString("operator_name"),
                r.getString("note"), r.getObject("created_at", LocalDateTime.class)), approvalId);
    }

    public String operatorName(Long userId) {
        if (userId == null) return "系统";
        return jdbc.query("select coalesce(nullif(real_name, ''), username, '未知用户') from users where id = ?",
                rs -> rs.next() ? rs.getString(1) : "未知用户", userId);
    }

    public void updateApprovalOnly(String workOrderId, String approvalId, String approvalStatus) {
        jdbc.update("""
                update maintenance_work_orders set approval_id = ?, approval_status = ?,
                    version = version + 1, updated_at = current_timestamp where id = ?
                """, approvalId, approvalStatus, workOrderId);
    }

    public void linkWorkOrder(String workOrderId, String approvalId, String approvalStatus, String workOrderStatus) {
        jdbc.update("""
                update maintenance_work_orders set approval_id = ?, approval_status = ?, status = ?,
                    active_asset_id = case when ? in ('REJECTED', 'CANCELLED') then null else asset_id end,
                    version = version + 1, updated_at = current_timestamp where id = ?
                """, approvalId, approvalStatus, workOrderStatus, approvalStatus, workOrderId);
        if ("REJECTED".equals(approvalStatus) || "CANCELLED".equals(approvalStatus)) {
            jdbc.update("""
                    update assets set availability_status = (
                            select case when previous_availability = 'MAINTENANCE' then 'UNAVAILABLE'
                                        else previous_availability end
                            from maintenance_work_orders where id = ?),
                        run_status = 'RUNNING', updated_at = current_timestamp
                    where id = (select asset_id from maintenance_work_orders where id = ?)
                    """, workOrderId, workOrderId);
        }
    }

    private MaintenanceApproval map(java.sql.ResultSet r, int row) throws java.sql.SQLException {
        return new MaintenanceApproval(r.getString("id"), r.getString("code"), r.getString("business_type"),
                r.getString("business_id"), r.getString("title"), r.getObject("applicant_id", Long.class),
                r.getString("applicant_name"), r.getString("department_id"), r.getString("department_name"),
                r.getString("asset_id"), r.getString("asset_code"), r.getString("asset_name"),
                r.getString("reason"), r.getString("repair_plan"), r.getBigDecimal("estimated_cost"),
                r.getBoolean("needs_shutdown"), r.getBoolean("needs_outsourcing"), r.getBoolean("emergency"),
                r.getString("status"), r.getObject("approver_id", Long.class), r.getString("approver_name"),
                r.getString("decision_note"), r.getInt("version"),
                r.getObject("created_at", LocalDateTime.class), r.getObject("decided_at", LocalDateTime.class));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
