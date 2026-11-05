package org.example.logisticsback.coldstorage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ColdStorageArchiveRepository {
    private final JdbcTemplate jdbc;

    public ColdStorageArchiveRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ColdStorageArchive> search(String operator, String name, String storageType,
                                           Boolean hasDevice, String grade) {
        var sql = new StringBuilder("select * from cold_storage_archives where 1 = 1");
        var params = new ArrayList<Object>();

        if (operator != null && !operator.isBlank()) {
            sql.append(" and operator = ?");
            params.add(operator);
        }
        if (name != null && !name.isBlank()) {
            sql.append(" and lower(name) like lower(?)");
            params.add("%" + name + "%");
        }
        if (storageType != null && !storageType.isBlank()) {
            sql.append(" and storage_type = ?");
            params.add(storageType);
        }
        if (hasDevice != null) {
            sql.append(hasDevice ? " and device <> ''" : " and device = ''");
        }
        if (grade != null && !grade.isBlank()) {
            sql.append(" and grade = ?");
            params.add(grade);
        }

        sql.append(" order by id");
        return jdbc.query(sql.toString(), this::map, params.toArray());
    }

    private ColdStorageArchive map(ResultSet result, int rowNumber) throws SQLException {
        return new ColdStorageArchive(
                result.getLong("id"),
                result.getString("operator"),
                result.getString("name"),
                result.getString("storage_type"),
                result.getString("grade"),
                result.getString("device"),
                result.getString("region"));
    }
}
