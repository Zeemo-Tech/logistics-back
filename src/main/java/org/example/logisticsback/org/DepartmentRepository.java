package org.example.logisticsback.org;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DepartmentRepository {
    private final JdbcTemplate jdbc;

    public DepartmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Department> list() {
        return jdbc.query("select * from departments order by sort_order, code, id", this::map);
    }

    public Optional<Department> find(String id) {
        return jdbc.query("select * from departments where id = ?", this::map, id).stream().findFirst();
    }

    public boolean codeExists(String code, String excludeId) {
        Integer count = excludeId == null
                ? jdbc.queryForObject("select count(*) from departments where code = ?", Integer.class, code)
                : jdbc.queryForObject("select count(*) from departments where code = ? and id <> ?",
                        Integer.class, code, excludeId);
        return count != null && count > 0;
    }

    public int countChildren(String id) {
        Integer count = jdbc.queryForObject("select count(*) from departments where parent_id = ?",
                Integer.class, id);
        return count == null ? 0 : count;
    }

    public int countAssets(String id) {
        Integer count = jdbc.queryForObject("select count(*) from assets where department_id = ?",
                Integer.class, id);
        return count == null ? 0 : count;
    }

    public void insert(String id, DepartmentRequest input) {
        jdbc.update("""
                insert into departments (id, parent_id, code, name, type, leader, phone, sort_order, status, notes)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, blankToNull(input.parentId()), input.code().trim(), input.name().trim(),
                input.type(), text(input.leader()), text(input.phone()),
                input.sortOrder() == null ? 0 : input.sortOrder(),
                input.status() == null || input.status().isBlank() ? "ACTIVE" : input.status(),
                text(input.notes()));
    }

    public void update(String id, DepartmentRequest input) {
        jdbc.update("""
                update departments set parent_id = ?, code = ?, name = ?, type = ?, leader = ?,
                    phone = ?, sort_order = ?, status = ?, notes = ?, updated_at = current_timestamp
                where id = ?
                """, blankToNull(input.parentId()), input.code().trim(), input.name().trim(),
                input.type(), text(input.leader()), text(input.phone()),
                input.sortOrder() == null ? 0 : input.sortOrder(),
                input.status() == null || input.status().isBlank() ? "ACTIVE" : input.status(),
                text(input.notes()), id);
    }

    public void delete(String id) {
        jdbc.update("delete from departments where id = ?", id);
    }

    private Department map(java.sql.ResultSet r, int row) throws java.sql.SQLException {
        return new Department(r.getString("id"), r.getString("parent_id"), r.getString("code"),
                r.getString("name"), r.getString("type"), r.getString("leader"), r.getString("phone"),
                r.getInt("sort_order"), r.getString("status"), r.getString("notes"),
                r.getObject("created_at", LocalDateTime.class),
                r.getObject("updated_at", LocalDateTime.class));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
