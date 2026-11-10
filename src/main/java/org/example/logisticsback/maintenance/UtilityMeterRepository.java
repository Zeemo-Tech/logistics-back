package org.example.logisticsback.maintenance;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UtilityMeterRepository {
    private final JdbcTemplate jdbc;

    public UtilityMeterRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UtilityMeter> list() {
        return jdbc.query("""
                select m.*, (select count(*) from utility_meter_readings r where r.meter_id=m.id) as reading_count,
                    coalesce((select r.consumption from utility_meter_readings r where r.meter_id=m.id and r.status <> 'VOID'
                        order by r.sequence_id desc limit 1), 0) as consumption
                from utility_meters m
                order by case when next_read_due_date < ? then 0 else 1 end, next_read_due_date, code
                """, this::map, LocalDate.now());
    }

    private UtilityMeter map(ResultSet rs, int row) throws SQLException {
        return new UtilityMeter(
                rs.getString("id"), rs.getString("code"), rs.getString("asset_id"),
                rs.getString("name"), rs.getString("kind"), rs.getString("location"),
                rs.getBigDecimal("current_reading"), rs.getBigDecimal("previous_reading"),
                rs.getString("unit"), rs.getDate("read_date").toLocalDate(),
                rs.getDate("next_read_due_date").toLocalDate(), rs.getString("status"),
                rs.getBigDecimal("multiplier"), rs.getString("owner"), rs.getString("lifecycle_status"),
                rs.getInt("version"),rs.getInt("cycle_days"),rs.getBigDecimal("initial_reading"),
                rs.getLong("reading_count"),rs.getBigDecimal("consumption"),
                rs.getString("area"), rs.getString("model"),
                rs.getObject("install_date", LocalDate.class),
                rs.getObject("last_inspection_date", LocalDate.class),
                rs.getObject("next_inspection_date", LocalDate.class));
    }
}
