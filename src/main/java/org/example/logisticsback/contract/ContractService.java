package org.example.logisticsback.contract;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.example.logisticsback.common.BusinessException;
import org.example.logisticsback.export.ExportTable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractService {
    private static final List<String> HEADERS = List.of(
            "合同编号", "合同名称", "合同类型", "合同相对方", "归属园区资源",
            "开始日期", "结束日期", "合同金额", "负责人", "单据数", "状态");
    private static final Set<String> STATUSES = Set.of("草稿", "履约中", "待生效", "即将到期", "已归档");
    private final ContractRepository repository;
    private final JdbcTemplate jdbc;

    public ContractService(ContractRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    public List<ContractRecord> list(String keyword, String category, String status, String resource) {
        return repository.search(keyword, category, status, resource);
    }

    public ContractRecord get(String id) {
        return repository.get(id);
    }

    @Transactional
    public ContractRecord create(ContractRequest input) {
        var customerName = validate(input, null);
        var resourceName = resourceName(input.resourceId());
        var intentionBusinesses = intentionBusinesses(input);
        var id = repository.nextId();
        repository.insert(id, input, customerName, resourceName,
                ContractRepository.join(input.intentionIds()), intentionBusinesses);
        applyIntentionStage(input);
        return get(id);
    }

    @Transactional
    public ContractRecord update(String id, ContractRequest input) {
        get(id);
        var customerName = validate(input, id);
        var resourceName = resourceName(input.resourceId());
        var intentionBusinesses = intentionBusinesses(input);
        repository.update(id, input, customerName, resourceName,
                ContractRepository.join(input.intentionIds()), intentionBusinesses);
        applyIntentionStage(input);
        return get(id);
    }

    @Transactional
    public void delete(String id) {
        var record = get(id);
        if (record.documentCount() > 0) {
            throw new BusinessException(409, "该合同仍有关联附件，请先删除附件");
        }
        var linked = jdbc.queryForObject("""
                select count(*) from pledge_businesses
                where contract_id = ? or agreement_contract_id = ?
                """, Integer.class, id, id);
        if (linked != null && linked > 0) {
            throw new BusinessException(409, "该合同已被仓单质押业务引用，不能删除");
        }
        var roadLinked = jdbc.queryForObject(
                "select count(*) from road_pickups where contract_id = ?", Integer.class, id);
        if (roadLinked != null && roadLinked > 0) {
            throw new BusinessException(409, "该合同已被公路运输提货单引用，不能删除");
        }
        repository.delete(id);
    }

    public ExportTable export(String keyword, String category, String status, String resource) {
        var records = repository.search(keyword, category, status, resource);
        var rows = new ArrayList<List<String>>();
        for (var record : records) {
            rows.add(List.of(
                    record.id(),
                    record.title(),
                    record.category(),
                    record.counterparty(),
                    record.resource(),
                    record.startDate(),
                    record.endDate(),
                    record.amount(),
                    record.owner(),
                    String.valueOf(record.documentCount()),
                    record.status()));
        }
        return new ExportTable("合同清单", HEADERS, rows);
    }

    private String validate(ContractRequest input, String currentId) {
        var customerName = customerName(input.customerId());
        if (categoryCount(input.category()) == 0) {
            throw new BusinessException(400, "合同类型不存在，请先在合同配置中维护");
        }
        if (input.endDate() == null || input.startDate() == null
                || LocalDate.parse(input.endDate()).isBefore(LocalDate.parse(input.startDate()))) {
            throw new BusinessException(400, "合同结束日期不能早于开始日期");
        }
        if (input.status() != null && !input.status().isBlank() && !STATUSES.contains(input.status())) {
            throw new BusinessException(400, "合同状态不正确");
        }
        var transportDetail = transportDetail(input);
        var transportTemplateCategory = jdbc.queryForObject(
                "select count(*) from contract_templates where category = ? and intention_business = '运输服务'",
                Integer.class, input.category());
        var transportContract = transportDetail != null || (transportTemplateCategory != null && transportTemplateCategory > 0);
        if (transportContract && transportDetail == null) {
            throw new BusinessException(400, "运输合同必须关联运输服务意向");
        }
        if (transportContract && input.intentionIds() != null && input.intentionIds().stream().anyMatch(id -> {
            if (id == null || id.isBlank()) return false;
            var business = jdbc.query("select business from customer_intentions where id = ?", (rs, row) -> rs.getString(1), id);
            return !business.isEmpty() && !"运输服务".equals(business.get(0));
        })) {
            throw new BusinessException(400, "运输合同只能关联运输服务意向");
        }
        if (!Set.of("监管协议", "仓储费服务", "监管费服务").contains(input.category())
                && !transportContract
                && (input.resourceId() == null || input.resourceId().isBlank())) {
            throw new BusinessException(400, "请选择归属园区资源");
        }
        if (transportContract && input.resourceId() != null && !input.resourceId().isBlank()) {
            throw new BusinessException(400, "运输合同不关联园区资源");
        }
        if (transportContract && (input.amount() == null || input.amount().isBlank())) {
            throw new BusinessException(400, "请填写运输合同金额");
        }
        if (input.resourceId() != null && !input.resourceId().isBlank() && resourceName(input.resourceId()) == null) {
            throw new BusinessException(400, "归属园区资源不存在");
        }
        if (input.templateId() != null && !input.templateId().isBlank() && templateCount(input.templateId()) == 0) {
            throw new BusinessException(400, "合同模板不存在");
        }
        if (transportContract && input.templateId() != null && !input.templateId().isBlank()) {
            var templates = jdbc.query("select intention_business, business_detail, category from contract_templates where id = ?",
                    (rs, row) -> List.of(rs.getString(1), rs.getString(2), rs.getString(3)), input.templateId());
            if (!templates.isEmpty()) {
                var template = templates.get(0);
                if (!"运输服务".equals(template.get(0)) || !input.category().equals(template.get(2))
                        || (template.get(1) != null && !template.get(1).isBlank() && !template.get(1).equals(transportDetail))) {
                    throw new BusinessException(400, "合同模板与运输意向方式不匹配");
                }
            }
        }
        if(currentId!=null) {
            var refs=jdbc.queryForList("select borrower_customer_id,owner_customer_id,agreement_contract_id,start_date,end_date,status from pledge_businesses where (contract_id=? or agreement_contract_id=?) and deleted_at is null",currentId,currentId);
            for(var ref:refs) {
                if(!input.customerId().equals(ref.get("BORROWER_CUSTOMER_ID"))&&!input.customerId().equals(ref.get("OWNER_CUSTOMER_ID")))
                    throw new BusinessException(409,"合同已关联监管业务，不能变更为其他客户");
                if(currentId.equals(ref.get("AGREEMENT_CONTRACT_ID"))) {
                    if(!"监管协议".equals(input.category()))throw new BusinessException(409,"已关联的监管协议不能变更合同类型");
                    if(LocalDate.parse(input.startDate()).isAfter(LocalDate.parse(ref.get("START_DATE").toString()))
                       ||LocalDate.parse(input.endDate()).isBefore(LocalDate.parse(ref.get("END_DATE").toString())))
                        throw new BusinessException(409,"协议有效期须覆盖已关联的监管业务期限");
                    if("ACTIVE".equals(ref.get("STATUS"))&&!Set.of("履约中","即将到期").contains(input.status()))
                        throw new BusinessException(409,"监管业务正在执行，不能将协议改为未生效或归档");
                }
            }
        }
        return customerName;
    }

    private String customerName(String id) {
        if (id == null || id.isBlank()) throw new BusinessException(400, "请选择客户档案");
        var names = jdbc.query("select name from customers where id = ?", (rs, row) -> rs.getString(1), id);
        if (names.isEmpty()) throw new BusinessException(400, "客户档案不存在");
        return names.get(0);
    }

    private String resourceName(String id) {
        if (id == null || id.isBlank()) return "";
        var names = jdbc.query("select name from park_resources where id = ?", (rs, row) -> rs.getString(1), id);
        return names.isEmpty() ? null : names.get(0);
    }

    private int categoryCount(String name) {
        var value = jdbc.queryForObject("select count(*) from contract_categories where name = ?", Integer.class, name);
        return value == null ? 0 : value;
    }

    private int templateCount(String id) {
        var value = jdbc.queryForObject("select count(*) from contract_templates where id = ?", Integer.class, id);
        return value == null ? 0 : value;
    }

    private String intentionBusinesses(ContractRequest input) {
        if (input.intentionIds() == null || input.intentionIds().isEmpty()) return "";
        var businesses = new ArrayList<String>();
        for (var intentionId : input.intentionIds()) {
            if (intentionId == null || intentionId.isBlank()) continue;
            var rows = jdbc.query("select customer_id, business, business_detail, stage from customer_intentions where id = ?",
                    (rs, row) -> List.of(rs.getString(1), rs.getString(2),rs.getString(3),rs.getString(4)), intentionId);
            if (rows.isEmpty()) throw new BusinessException(400, "关联的客户意向不存在");
            if (!input.customerId().equals(rows.get(0).get(0))) {
                throw new BusinessException(400, "关联的客户意向不属于当前客户");
            }
            if (Set.of("已流失", "已终止").contains(rows.get(0).get(3))) {
                throw new BusinessException(400, "已结束的客户意向不能关联合同");
            }
            var business = rows.get(0).get(1);
            var detail = rows.get(0).get(2);
            businesses.add("运输服务".equals(business) && detail != null && !detail.isBlank()
                    ? business + "·" + detail : business);
        }
        return String.join(",", businesses);
    }

    private String transportDetail(ContractRequest input) {
        if (input.intentionIds() == null || input.intentionIds().isEmpty()) return null;
        String detail = null;
        for (var id : input.intentionIds()) {
            if (id == null || id.isBlank()) continue;
            var rows = jdbc.query("select business, business_detail, customer_id, stage from customer_intentions where id = ?",
                    (rs, row) -> List.of(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)), id);
            if (rows.isEmpty()) throw new BusinessException(400, "关联的客户意向不存在");
            var row = rows.get(0);
            if ("运输服务".equals(row.get(0))) {
                var current = row.get(1) == null ? "" : row.get(1).trim();
                if (current.isBlank()) throw new BusinessException(400, "运输意向缺少运输方式");
                if (detail != null && !detail.equals(current)) throw new BusinessException(400, "同一合同只能关联一种运输方式");
                detail = current;
            }
        }
        return detail;
    }

    private void applyIntentionStage(ContractRequest input) {
        if (input.intentionIds() == null || input.intentionIds().isEmpty()) return;
        boolean effective=Set.of("履约中","即将到期").contains(input.status()==null?"":input.status())
            && !LocalDate.parse(input.startDate()).isAfter(LocalDate.now())
            && !LocalDate.parse(input.endDate()).isBefore(LocalDate.now());
        if("已归档".equals(input.status())) return;
        for (var intentionId : input.intentionIds()) {
            if (intentionId == null || intentionId.isBlank()) continue;
            jdbc.update("update customer_intentions set stage = ?, updated_at = current_timestamp where id = ? and stage not in ('已流失','已终止','合作中')",
                    effective?"合作中":"待签约",intentionId);
        }
        if(effective) jdbc.update("update customers set stage = '合作中', status = '已签约', updated_at = current_timestamp where id = ?",
                input.customerId());
    }
}
