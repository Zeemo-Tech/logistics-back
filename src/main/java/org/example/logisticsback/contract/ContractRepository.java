package org.example.logisticsback.contract;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.example.logisticsback.common.BusinessException;

@Repository
public class ContractRepository {
    private final JdbcTemplate jdbc;

    public ContractRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ContractRecord> search(String keyword, String category, String status, String resource) {
        var sql = new StringBuilder("select contracts.*, (select count(*) from contract_attachments a where a.contract_id = contracts.id and a.deleted_at is null) as active_document_count from contracts where 1 = 1");
        var params = new ArrayList<Object>();

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" and (lower(id) like lower(?) or lower(title) like lower(?) or lower(counterparty) like lower(?))");
            var pattern = "%" + keyword + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        if (category != null && !category.isBlank()) {
            sql.append(" and category = ?");
            params.add(category);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            params.add(status);
        }
        if (resource != null && !resource.isBlank()) {
            sql.append(" and resource = ?");
            params.add(resource);
        }

        sql.append(" order by id desc");
        return jdbc.query(sql.toString(), this::map, params.toArray());
    }

    private ContractRecord map(ResultSet result, int rowNumber) throws SQLException {
        return new ContractRecord(
                result.getString("id"),
                result.getString("title"),
                result.getString("category"),
                result.getString("counterparty"),
                result.getString("resource"),
                result.getString("start_date"),
                result.getString("end_date"),
                result.getString("amount"),
                result.getString("owner"),
                result.getInt("active_document_count"),
                result.getString("status"));
    }

    public ContractRecord get(String id) {
        var records = jdbc.query("""
                select contracts.*, (select count(*) from contract_attachments a
                    where a.contract_id = contracts.id and a.deleted_at is null) as active_document_count
                from contracts where id = ?
                """, this::map, id);
        if (records.isEmpty()) throw new BusinessException(404, "合同不存在");
        return records.get(0);
    }
}
