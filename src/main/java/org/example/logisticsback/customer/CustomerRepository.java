package org.example.logisticsback.customer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerRepository {
    private final JdbcTemplate jdbc;

    public CustomerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CustomerRecord> search(String keyword, String nature, String status) {
        var sql = new StringBuilder("select * from customers where 1 = 1");
        var params = new ArrayList<Object>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" and (lower(code) like lower(?) or lower(name) like lower(?)")
                    .append(" or lower(contact_person) like lower(?) or lower(credit_code) like lower(?))");
            var pattern = "%" + keyword.trim() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        if (nature != null && !nature.isBlank()) {
            sql.append(" and customer_nature = ?");
            params.add(nature);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            params.add(status);
        }
        sql.append(" order by updated_at desc, id");
        return jdbc.query(sql.toString(), this::map, params.toArray());
    }

    public Optional<CustomerRecord> find(String id) {
        return jdbc.query("select * from customers where id = ?", this::map, id).stream().findFirst();
    }

    public void insert(String id, CustomerRequest input, String nature, String stage, String status) {
        jdbc.update("""
                insert into customers (id, code, name, customer_nature, credit_code, legal_person,
                    contact_person, contact_phone, address, bank_name, bank_account, type, service,
                    source, owner, stage, status, last_follow_up_at, next_follow_up_at, notes)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, id, input.name().trim(), nature, text(input.creditCode()), text(input.legalPerson()),
                text(input.contactPerson()), text(input.contactPhone()), text(input.address()),
                text(input.bankName()), text(input.bankAccount()), text(input.type()), text(input.service()),
                text(input.source()), text(input.owner()), stage, status,
                text(input.lastFollowUpAt()), text(input.nextFollowUpAt()), text(input.notes()));
    }

    public void update(String id, CustomerRequest input, String nature, String stage, String status) {
        jdbc.update("""
                update customers set name = ?, customer_nature = ?, credit_code = ?, legal_person = ?,
                    contact_person = ?, contact_phone = ?, address = ?, bank_name = ?, bank_account = ?,
                    type = ?, service = ?, source = ?, owner = ?, stage = ?, status = ?,
                    last_follow_up_at = ?, next_follow_up_at = ?, notes = ?,
                    updated_at = current_timestamp
                where id = ?
                """,
                input.name().trim(), nature, text(input.creditCode()), text(input.legalPerson()),
                text(input.contactPerson()), text(input.contactPhone()), text(input.address()),
                text(input.bankName()), text(input.bankAccount()), text(input.type()), text(input.service()),
                text(input.source()), text(input.owner()), stage, status,
                text(input.lastFollowUpAt()), text(input.nextFollowUpAt()), text(input.notes()), id);
    }

    public void renameReferences(String id, String name) {
        jdbc.update("update contracts set counterparty = ? where customer_id = ?", name, id);
        jdbc.update("""
                update pledge_businesses set
                    borrower = case when borrower_customer_id = ? then ? else borrower end,
                    owner_name = case when owner_customer_id = ? then ? else owner_name end
                where borrower_customer_id = ? or owner_customer_id = ?
                """, id, name, id, name, id, id);
    }

    public void delete(String id) {
        jdbc.update("delete from customers where id = ?", id);
    }

    public boolean creditCodeExists(String creditCode, String excludeId) {
        var sql = "select count(*) from customers where credit_code = ? and credit_code <> ''";
        var count = excludeId == null || excludeId.isBlank()
                ? jdbc.queryForObject(sql, Integer.class, creditCode)
                : jdbc.queryForObject(sql + " and id <> ?", Integer.class, creditCode, excludeId);
        return count != null && count > 0;
    }

    public int countIntentions(String id) {
        return jdbc.queryForObject("select count(*) from customer_intentions where customer_id=? or pledge_demand like ?",Integer.class,id,
            "%\"ownerCustomerId\":\""+id+"\"%");
    }
    public int countContracts(String id) {
        var count = jdbc.queryForObject("select count(*) from contracts where customer_id = ?", Integer.class, id);
        return count == null ? 0 : count;
    }

    public int countPledges(String id) {
        var count = jdbc.queryForObject("""
                select count(*) from pledge_businesses
                where borrower_customer_id = ? or owner_customer_id = ?
                """, Integer.class, id, id);
        return count == null ? 0 : count;
    }

    private CustomerRecord map(ResultSet result, int rowNumber) throws SQLException {
        return new CustomerRecord(
                result.getString("id"),
                result.getString("code"),
                result.getString("name"),
                result.getString("customer_nature"),
                result.getString("credit_code"),
                result.getString("legal_person"),
                result.getString("contact_person"),
                result.getString("contact_phone"),
                result.getString("address"),
                result.getString("bank_name"),
                result.getString("bank_account"),
                result.getString("type"),
                result.getString("service"),
                result.getString("source"),
                result.getString("owner"),
                result.getString("stage"),
                result.getString("status"),
                result.getString("last_follow_up_at"),
                result.getString("next_follow_up_at"),
                result.getString("notes"),
                result.getTimestamp("created_at") == null ? "" : result.getTimestamp("created_at").toLocalDateTime().toString(),
                result.getTimestamp("updated_at") == null ? "" : result.getTimestamp("updated_at").toLocalDateTime().toString());
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
