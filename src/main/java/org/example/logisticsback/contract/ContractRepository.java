package org.example.logisticsback.contract;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.example.logisticsback.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ContractRepository {
    private final JdbcTemplate jdbc;

    public ContractRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ContractRecord> search(String keyword, String category, String status, String resource) {
        var sql = new StringBuilder("""
                select contracts.*, (select count(*) from contract_attachments a
                    where a.contract_id = contracts.id and a.deleted_at is null) as active_document_count
                from contracts where 1 = 1
                """);
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

    public ContractRecord get(String id) {
        var records = jdbc.query("""
                select contracts.*, (select count(*) from contract_attachments a
                    where a.contract_id = contracts.id and a.deleted_at is null) as active_document_count
                from contracts where id = ?
                """, this::map, id);
        if (records.isEmpty()) throw new BusinessException(404, "合同不存在");
        return records.get(0);
    }

    public void insert(String id, ContractRequest input, String counterparty, String resourceName,
                       String intentionIds, String intentionBusinesses) {
        jdbc.update("""
                insert into contracts (id, title, category, counterparty, resource, start_date, end_date,
                    amount, owner, document_count, status, customer_id, resource_id, resource_type, business_detail,
                    template_id, content, deposit, unit_price, quantity, area, intention_ids, intention_businesses)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, input.title().trim(), input.category().trim(), counterparty, resourceName,
                input.startDate(), input.endDate(), text(input.amount()), input.owner().trim(),
                status(input.status()), input.customerId(), blank(input.resourceId()), blank(input.resourceType()), text(input.businessDetail()),
                blank(input.templateId()), text(input.content()), input.deposit(), input.unitPrice(),
                input.quantity(), input.area(), intentionIds, intentionBusinesses);
    }

    public void update(String id, ContractRequest input, String counterparty, String resourceName,
                       String intentionIds, String intentionBusinesses) {
        jdbc.update("""
                update contracts set title = ?, category = ?, counterparty = ?, resource = ?,
                    start_date = ?, end_date = ?, amount = ?, owner = ?, status = ?, customer_id = ?,
                    resource_id = ?, resource_type = ?, business_detail = ?, template_id = ?, content = ?, deposit = ?,
                    unit_price = ?, quantity = ?, area = ?, intention_ids = ?, intention_businesses = ?,
                    updated_at = current_timestamp
                where id = ?
                """,
                input.title().trim(), input.category().trim(), counterparty, resourceName,
                input.startDate(), input.endDate(), text(input.amount()), input.owner().trim(),
                status(input.status()), input.customerId(), blank(input.resourceId()), blank(input.resourceType()), text(input.businessDetail()),
                blank(input.templateId()), text(input.content()), input.deposit(), input.unitPrice(),
                input.quantity(), input.area(), intentionIds, intentionBusinesses, id);
    }

    public void delete(String id) {
        jdbc.update("delete from contracts where id = ?", id);
    }

    public String nextId() {
        var prefix = "HT-" + LocalDate.now().getYear() + "-";
        var ids = jdbc.queryForList("select id from contracts where id like ?", String.class, prefix + "%");
        var max = 0;
        for (var id : ids) {
            try {
                max = Math.max(max, Integer.parseInt(id.substring(prefix.length())));
            } catch (NumberFormatException ignored) {
                // Ignore non-standard codes.
            }
        }
        return prefix + String.format("%03d", max + 1);
    }

    public int countByCustomer(String customerId) {
        var value = jdbc.queryForObject("select count(*) from contracts where customer_id = ?", Integer.class, customerId);
        return value == null ? 0 : value;
    }

    private ContractRecord map(ResultSet result, int rowNumber) throws SQLException {
        return new ContractRecord(
                result.getString("id"),
                result.getString("title"),
                result.getString("category"),
                result.getString("counterparty"),
                result.getString("resource"),
                result.getString("resource_type"),
                result.getString("business_detail"),
                result.getString("customer_id"),
                result.getString("resource_id"),
                result.getString("template_id"),
                result.getString("start_date"),
                result.getString("end_date"),
                result.getString("amount"),
                result.getBigDecimal("deposit"),
                result.getBigDecimal("unit_price"),
                result.getBigDecimal("quantity"),
                result.getBigDecimal("area"),
                result.getString("owner"),
                result.getInt("active_document_count"),
                result.getString("status"),
                result.getString("content"),
                split(result.getString("intention_ids")),
                split(result.getString("intention_businesses")));
    }

    private static String status(String value) {
        return value == null || value.isBlank() ? "待生效" : value.trim();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String join(List<String> values) {
        return values == null ? "" : String.join(",", values);
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return List.of(value.split(","));
    }
}
