package org.example.logisticsback.assets;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EquipmentCategoryRepository {
    private final JdbcTemplate jdbc;

    public EquipmentCategoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<EquipmentCategory> list() {
        return jdbc.query("select * from equipment_categories order by level, sort_order, code",
                this::map);
    }

    public List<EquipmentCategory> listActive() {
        return jdbc.query("select * from equipment_categories where status = 'ACTIVE' order by level, sort_order, code",
                this::map);
    }

    public Optional<EquipmentCategory> find(String id) {
        return jdbc.query("select * from equipment_categories where id = ?", this::map, id)
                .stream().findFirst();
    }

    public boolean codeExists(String code, String excludeId) {
        Integer count = excludeId == null
                ? jdbc.queryForObject("select count(*) from equipment_categories where code = ?",
                        Integer.class, code)
                : jdbc.queryForObject("select count(*) from equipment_categories where code = ? and id <> ?",
                        Integer.class, code, excludeId);
        return count != null && count > 0;
    }

    public int countChildren(String id) {
        Integer count = jdbc.queryForObject("select count(*) from equipment_categories where parent_id = ?",
                Integer.class, id);
        return count == null ? 0 : count;
    }

    public int countAssets(String id) {
        Integer count = jdbc.queryForObject("select count(*) from assets where category_id = ?",
                Integer.class, id);
        return count == null ? 0 : count;
    }

    public void insert(String id, String parentId, int level, EquipmentCategoryRequest input) {
        jdbc.update("""
                insert into equipment_categories (id, parent_id, level, code, name, sort_order, status)
                values (?, ?, ?, ?, ?, ?, ?)
                """, id, blankToNull(parentId), level, input.code().trim(), input.name().trim(),
                input.sortOrder() == null ? 0 : input.sortOrder(),
                input.status() == null || input.status().isBlank() ? "ACTIVE" : input.status());
    }

    public void update(String id, String parentId, int level, EquipmentCategoryRequest input) {
        jdbc.update("""
                update equipment_categories set parent_id = ?, level = ?, code = ?, name = ?,
                    sort_order = ?, status = ? where id = ?
                """, blankToNull(parentId), level, input.code().trim(), input.name().trim(),
                input.sortOrder() == null ? 0 : input.sortOrder(),
                input.status() == null || input.status().isBlank() ? "ACTIVE" : input.status(), id);
    }

    public void delete(String id) {
        jdbc.update("delete from equipment_categories where id = ?", id);
    }

    private EquipmentCategory map(java.sql.ResultSet r, int row) throws java.sql.SQLException {
        return new EquipmentCategory(r.getString("id"), r.getString("parent_id"),
                r.getInt("level"), r.getString("code"), r.getString("name"),
                r.getInt("sort_order"), r.getString("status"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
