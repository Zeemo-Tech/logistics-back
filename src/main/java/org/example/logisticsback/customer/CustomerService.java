package org.example.logisticsback.customer;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.example.logisticsback.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {
    private static final Set<String> STAGES = Set.of(
            "新来访", "意向跟进", "方案报价", "待签约", "合作中", "暂缓跟进", "已流失", "已终止");
    private static final Set<String> NATURES = Set.of("企业", "个人");
    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    public List<CustomerRecord> list(String keyword, String nature, String status) {
        return repository.search(keyword, nature, status);
    }

    public CustomerRecord get(String id) {
        return repository.find(id).orElseThrow(() -> new BusinessException(404, "客户档案不存在"));
    }

    @Transactional
    public CustomerRecord create(CustomerRequest input) {
        var nature = nature(input.customerNature());
        var stage = stage(input.stage());
        var status = status(stage);
        validateCreditCode(input, nature, null);
        var id = input.id() == null || input.id().isBlank() ? code() : input.id().trim();
        if (repository.find(id).isPresent()) {
            throw new BusinessException(409, "客户编号已存在");
        }
        repository.insert(id, input, nature, stage, status);
        return get(id);
    }

    @Transactional
    public CustomerRecord update(String id, CustomerRequest input) {
        var current = get(id);
        var nature = nature(input.customerNature());
        var stage = stage(input.stage());
        var status = status(stage);
        validateCreditCode(input, nature, id);
        repository.update(id, input, nature, stage, status);
        if (!current.name().equals(input.name().trim())) {
            repository.renameReferences(id, input.name().trim());
        }
        return get(id);
    }

    @Transactional
    public void delete(String id) {
        get(id);
        if (repository.countContracts(id) > 0) {
            throw new BusinessException(409, "该客户仍有关联合同，请先处理合同档案");
        }
        if (repository.countPledges(id) > 0) {
            throw new BusinessException(409, "该客户仍有关联仓单质押业务，不能删除");
        }
        if(repository.countIntentions(id)>0) throw new BusinessException(409,"该客户仍被客户意向引用，不能删除");
        repository.delete(id);
    }

    private void validateCreditCode(CustomerRequest input, String nature, String excludeId) {
        if (!"企业".equals(nature) || input.creditCode() == null || input.creditCode().isBlank()) return;
        if (repository.creditCodeExists(input.creditCode().trim(), excludeId)) {
            throw new BusinessException(409, "统一社会信用代码已存在，请勿重复建档");
        }
    }

    private static String nature(String value) {
        var nature = value == null || value.isBlank() ? "企业" : value.trim();
        if (!NATURES.contains(nature)) throw new BusinessException(400, "客户性质不正确");
        return nature;
    }

    private static String stage(String value) {
        var stage = value == null || value.isBlank() ? "新来访" : value.trim();
        if (!STAGES.contains(stage)) throw new BusinessException(400, "客户阶段不正确");
        return stage;
    }

    private static String status(String stage) {
        return switch (stage) {
            case "合作中" -> "已签约";
            case "已流失" -> "已流失";
            case "已终止" -> "已终止";
            default -> "待跟进";
        };
    }

    private static String code() {
        return "KH-" + LocalDate.now().getYear() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
