package org.example.logisticsback.pledge;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.example.logisticsback.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PledgeRepository {
    private final JdbcTemplate jdbc;
    public PledgeRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void lock() { jdbc.queryForObject("select id from pledge_inventory_lock where id=1 for update", Integer.class); }
    public List<Map<String,Object>> query(String sql, Object... args) { return jdbc.query(sql, this::map, args); }
    public Map<String,Object> one(String table, String id) {
        return query("select * from " + table + " where id=?", id).stream().findFirst()
            .orElseThrow(() -> new BusinessException(404, "记录不存在或已移除"));
    }
    public int update(String sql, Object... args) { return jdbc.update(sql, args); }
    public void insert(String table, Map<String,Object> row) {
        var columns = new ArrayList<>(row.keySet());
        update("insert into " + table + " (" + String.join(",", columns) + ") values (" +
            String.join(",", Collections.nCopies(columns.size(), "?")) + ")", columns.stream().map(row::get).toArray());
    }
    public BigDecimal reserved(String lotId) {
        return jdbc.queryForObject("""
            select coalesce(sum(l.quantity-l.released),0) from pledge_receipt_lines l
            join pledge_receipts r on r.id=l.receipt_id where l.lot_id=? and r.business_id in (select id from pledge_businesses where deleted_at is null) and r.status not in ('CANCELLED','REJECTED')
            """, BigDecimal.class, lotId);
    }
    public List<Map<String,Object>> lots() {
        return query("""
            select l.*, coalesce((select sum(rl.quantity-rl.released) from pledge_receipt_lines rl
                join pledge_receipts r on r.id=rl.receipt_id where rl.lot_id=l.id and r.business_id in (select id from pledge_businesses where deleted_at is null) and r.status not in ('CANCELLED','REJECTED')),0) as reserved_quantity,
                (select count(*) from pledge_inspections p where p.lot_id=l.id and p.status='OPEN' and p.business_id in (select id from pledge_businesses where deleted_at is null)) as open_issues
            from pledge_lots l where l.business_id in (select id from pledge_businesses where deleted_at is null) order by l.created_at desc,l.id
            """);
    }
    public boolean frozen(String lotId) {
        return !query("select id from pledge_inspections where lot_id=? and status='OPEN' and business_id in (select id from pledge_businesses where deleted_at is null)",lotId).isEmpty();
    }
    private Map<String,Object> map(ResultSet rs, int row) throws SQLException {
        var result = new LinkedHashMap<String,Object>();
        var metadata = rs.getMetaData();
        for (int i=1; i<=metadata.getColumnCount(); i++) {
            var raw = metadata.getColumnLabel(i).toLowerCase(Locale.ROOT);
            var name = new StringBuilder(); boolean upper=false;
            for (char c: raw.toCharArray()) {
                if (c=='_') upper=true; else { name.append(upper ? Character.toUpperCase(c) : c); upper=false; }
            }
            Object value = rs.getObject(i);
            if (value instanceof java.sql.Timestamp timestamp) value=timestamp.toLocalDateTime().toString();
            if (value instanceof java.sql.Date date) value=date.toLocalDate().toString();
            if (value instanceof java.sql.Clob) value=rs.getString(i);
            result.put(name.toString(), value);
        }
        return result;
    }
}
