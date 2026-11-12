package org.example.logisticsback.customer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerActivityRepository {
    private final JdbcTemplate jdbc;
    private final tools.jackson.databind.ObjectMapper mapper;

    public CustomerActivityRepository(JdbcTemplate jdbc, tools.jackson.databind.ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public List<CustomerActivityModels.Intention> intentions(String customerId, String stage) {
        var sql = new StringBuilder("select * from customer_intentions where 1 = 1");
        var params = new ArrayList<Object>();
        if (customerId != null && !customerId.isBlank()) {
            sql.append(" and customer_id = ?");
            params.add(customerId);
        }
        if (stage != null && !stage.isBlank()) {
            sql.append(" and stage = ?");
            params.add(stage);
        }
        sql.append(" order by updated_at desc, id");
        return jdbc.query(sql.toString(), this::mapIntention, params.toArray());
    }

    public Optional<CustomerActivityModels.Intention> findIntention(String id) {
        return jdbc.query("select * from customer_intentions where id = ?", this::mapIntention, id)
                .stream().findFirst();
    }

    public void insertIntention(String id, CustomerActivityModels.IntentionRequest input, String stage) {
        jdbc.update("""
                insert into customer_intentions (id, customer_id, business, business_detail, amount, stage, owner, source,
                    next_follow_up_at, notes, pledge_demand)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, input.customerId(), input.business().trim(), text(input.businessDetail()), "仓单质押".equals(input.business().trim()) ? null : input.amount(), stage,
                text(input.owner()), text(input.source()), text(input.nextFollowUpAt()), text(input.notes()), demandJson(input));
    }

    public void updateIntention(String id, CustomerActivityModels.IntentionRequest input, String stage) {
        jdbc.update("""
                update customer_intentions set business = ?, business_detail = ?, amount = ?, stage = ?, owner = ?, source = ?,
                    next_follow_up_at = ?, notes = ?, pledge_demand = ?, updated_at = current_timestamp where id = ?
                """, input.business().trim(), text(input.businessDetail()), "仓单质押".equals(input.business().trim()) ? null : input.amount(), stage, text(input.owner()), text(input.source()),
                text(input.nextFollowUpAt()), text(input.notes()), demandJson(input), id);
    }

    public void deleteIntention(String id) {
        jdbc.update("delete from customer_visits where intention_id = ?", id);
        jdbc.update("delete from customer_intentions where id = ?", id);
    }

    public List<CustomerActivityModels.Visit> visits(String customerId, String intentionId) {
        var sql = new StringBuilder("select * from customer_visits where 1 = 1");
        var params = new ArrayList<Object>();
        if (customerId != null && !customerId.isBlank()) {
            sql.append(" and customer_id = ?");
            params.add(customerId);
        }
        if (intentionId != null && !intentionId.isBlank()) {
            sql.append(" and intention_id = ?");
            params.add(intentionId);
        }
        sql.append(" order by visited_at desc, id");
        return jdbc.query(sql.toString(), this::mapVisit, params.toArray());
    }

    public void insertVisit(String id, CustomerActivityModels.VisitRequest input, String stage) {
        jdbc.update("""
                insert into customer_visits (id, customer_id, intention_id, visited_at, visitor, visitor_phone,
                    interest, requirement, expected_start, budget, host, note, next_action, next_follow_up_at, stage)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, input.customerId(), blank(input.intentionId()), text(input.visitedAt()),
                text(input.visitor()), text(input.visitorPhone()), text(input.interest()), text(input.requirement()),
                text(input.expectedStart()), text(input.budget()), text(input.host()), text(input.note()),
                text(input.nextAction()), text(input.nextFollowUpAt()), stage);
    }

    public void deleteVisit(String id) {
        jdbc.update("delete from customer_visits where id = ?", id);
    }

    public void applyVisitToIntention(String intentionId, String stage, String nextFollowUpAt, String note) {
        if (intentionId == null || intentionId.isBlank()) return;
        var current = jdbc.query("select notes from customer_intentions where id = ?",
                (rs, row) -> rs.getString(1), intentionId);
        var existing = current.isEmpty() || current.get(0) == null ? "" : current.get(0);
        var added = text(note);
        var merged = added.isBlank() ? existing : existing.isBlank() ? added : existing + "；" + added;
        jdbc.update("""
                update customer_intentions set stage = ?, next_follow_up_at = ?, notes = ?,
                    updated_at = current_timestamp where id = ?
                """, stage, text(nextFollowUpAt), merged, intentionId);
    }

    public void applyVisitToCustomer(String customerId, String stage, String visitedAt,
                                     String nextFollowUpAt, String visitor, String visitorPhone) {
        var date = visitedAt == null || visitedAt.length() < 10 ? text(visitedAt) : visitedAt.substring(0, 10);
        jdbc.update("""
                update customers set stage = ?, status = ?, last_follow_up_at = ?,
                    next_follow_up_at = ?,
                    contact_person = case when ? = '' then contact_person else ? end,
                    contact_phone = case when ? = '' then contact_phone else ? end,
                    updated_at = current_timestamp
                where id = ?
                """, stage, status(stage), date, text(nextFollowUpAt),
                text(visitor), text(visitor), text(visitorPhone), text(visitorPhone), customerId);
    }

    public int countContractsByIntention(String intentionId) {
        var value = jdbc.queryForObject("select count(*) from contracts where locate(?, concat(',', intention_ids, ',')) > 0",
                Integer.class, "," + intentionId + ",");
        return value == null ? 0 : value;
    }

    public int countRoadPickupsByIntention(String id) {
        var value = jdbc.queryForObject(
                "select count(*) from road_pickups where intention_id = ?", Integer.class, id);
        return value == null ? 0 : value;
    }

    public int countPledgesByIntention(String id) {
        return jdbc.queryForObject("select count(*) from pledge_businesses where source_intention_id=?",Integer.class,id);
    }
    private String demandJson(CustomerActivityModels.IntentionRequest input) {
        return "仓单质押".equals(input.business().trim()) && input.pledgeDemand()!=null
            ? mapper.writeValueAsString(input.pledgeDemand()) : null;
    }
    private List<CustomerActivityModels.ContractLink> contractLinks(String id) {
        return jdbc.query("select id,title,category,status from contracts where locate(?,concat(',',intention_ids,','))>0 order by id",
            (rs,n)->new CustomerActivityModels.ContractLink(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4)),","+id+",");
    }
    private List<CustomerActivityModels.BusinessLink> businessLinks(String id) {
        return jdbc.query("select id,code,title,status from pledge_businesses where source_intention_id=? and deleted_at is null order by created_at desc",
            (rs,n)->new CustomerActivityModels.BusinessLink(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4)),id);
    }
    private CustomerActivityModels.Intention mapIntention(ResultSet result, int rowNumber) throws SQLException {
        return new CustomerActivityModels.Intention(
                result.getString("id"), result.getString("customer_id"), result.getString("business"), result.getString("business_detail"),
                "仓单质押".equals(result.getString("business")) ? null : result.getBigDecimal("amount"), result.getString("stage"), result.getString("owner"),
                result.getString("source"), result.getString("next_follow_up_at"),
                result.getString("notes"),
                result.getTimestamp("updated_at") == null ? "" : result.getTimestamp("updated_at").toLocalDateTime().toString(),
                result.getString("pledge_demand")==null ? null : mapper.readValue(result.getString("pledge_demand"),PledgeDemand.class),
                contractLinks(result.getString("id")),businessLinks(result.getString("id")));
    }

    private CustomerActivityModels.Visit mapVisit(ResultSet result, int rowNumber) throws SQLException {
        return new CustomerActivityModels.Visit(
                result.getString("id"), result.getString("customer_id"), result.getString("intention_id"),
                result.getString("visited_at"), result.getString("visitor"), result.getString("visitor_phone"),
                result.getString("interest"), result.getString("requirement"), result.getString("expected_start"),
                result.getString("budget"), result.getString("host"), result.getString("note"),
                result.getString("next_action"), result.getString("next_follow_up_at"), result.getString("stage"));
    }

    private static String status(String stage) {
        return switch (stage) {
            case "合作中" -> "已签约";
            case "已流失" -> "已流失";
            case "已终止" -> "已终止";
            default -> "待跟进";
        };
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
