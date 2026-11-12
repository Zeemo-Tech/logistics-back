package org.example.logisticsback.customer;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.example.logisticsback.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerActivityService {
    private static final Set<String> STAGES = Set.of(
            "新来访", "意向跟进", "方案报价", "待签约", "合作中", "暂缓跟进", "已流失", "已终止");
    private final CustomerActivityRepository repository;
    private final CustomerRepository customers;

    public CustomerActivityService(CustomerActivityRepository repository, CustomerRepository customers) {
        this.repository = repository;
        this.customers = customers;
    }

    public List<CustomerActivityModels.Intention> intentions(String customerId, String stage) {
        return repository.intentions(customerId, stage);
    }

    @Transactional
    public CustomerActivityModels.Intention createIntention(CustomerActivityModels.IntentionRequest input) {
        customer(input.customerId());
        validateDemand(input);
        var stage = stage(input.stage());
        var id = input.id() == null || input.id().isBlank() ? code("YJ") : input.id().trim();
        if (repository.findIntention(id).isPresent()) throw new BusinessException(409, "客户意向已存在");
        repository.insertIntention(id, input, stage);
        return getIntention(id);
    }

    @Transactional
    public CustomerActivityModels.Intention updateIntention(String id, CustomerActivityModels.IntentionRequest input) {
        var current=getIntention(id);
        customer(input.customerId());
        if(!current.customerId().equals(input.customerId())) throw new BusinessException(400,"意向的关联客户不能变更");
        if((repository.countContractsByIntention(id)>0 || repository.countPledgesByIntention(id)>0)
            && (!current.business().equals(input.business().trim())
                || !java.util.Objects.equals(current.businessDetail(), normalizedBusinessDetail(input))))
            throw new BusinessException(409,"已关联业务或合同，不能变更意向业务类型");
        validateDemand(input);
        validateStageChange(current.stage(),stage(input.stage()));
        repository.updateIntention(id, input, stage(input.stage()));
        return getIntention(id);
    }

    @Transactional
    public void deleteIntention(String id) {
        getIntention(id);
        if (repository.countContractsByIntention(id) > 0 || repository.countPledgesByIntention(id)>0) {
            throw new BusinessException(409, "该客户意向已关联合同或监管业务，不能删除");
        }
        if (repository.countRoadPickupsByIntention(id) > 0) {
            throw new BusinessException(409, "该客户意向已被公路运输提货单引用，不能删除");
        }
        repository.deleteIntention(id);
    }

    public List<CustomerActivityModels.Visit> visits(String customerId, String intentionId) {
        return repository.visits(customerId, intentionId);
    }

    @Transactional
    public CustomerActivityModels.Visit createVisit(CustomerActivityModels.VisitRequest input) {
        customer(input.customerId());
        var intention = input.intentionId() == null || input.intentionId().isBlank()
                ? null
                : getIntention(input.intentionId());
        if (intention != null && !intention.customerId().equals(input.customerId())) {
            throw new BusinessException(400, "跟进记录与客户意向不匹配");
        }
        var stage = input.stage() == null || input.stage().isBlank()
                ? intention == null ? "新来访" : intention.stage()
                : stage(input.stage());
        if(intention!=null) validateStageChange(intention.stage(),stage);
        var id = input.id() == null || input.id().isBlank() ? code("VF") : input.id().trim();
        repository.insertVisit(id, input, stage);
        repository.applyVisitToIntention(input.intentionId(), stage, input.nextFollowUpAt(), input.note());
        repository.applyVisitToCustomer(input.customerId(), stage, input.visitedAt(),
                input.nextFollowUpAt(), input.visitor(), input.visitorPhone());
        return repository.visits(input.customerId(), input.intentionId()).stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException(500, "跟进记录保存失败"));
    }

    @Transactional
    public void deleteVisit(String id) {
        repository.deleteVisit(id);
    }

    public CustomerActivityModels.Intention getIntention(String id) {
        return repository.findIntention(id)
                .orElseThrow(() -> new BusinessException(404, "客户意向不存在"));
    }

    private void validateDemand(CustomerActivityModels.IntentionRequest input) {
        var business = input.business().trim();
        var detail = normalizedBusinessDetail(input);
        if ("运输服务".equals(business) && detail.isBlank()) {
            throw new BusinessException(400, "运输服务必须选择水路运输、公路运输或火车运输");
        }
        if ("运输服务".equals(business)
                && !Set.of("水路运输", "公路运输", "火车运输").contains(detail)) {
            throw new BusinessException(400, "运输方式不正确");
        }
        if (!"运输服务".equals(business) && !detail.isBlank()) {
            throw new BusinessException(400, "只有运输服务可以填写运输方式");
        }
        if(!"仓单质押".equals(input.business().trim())) return;
        PledgeDemand.validate(input.pledgeDemand(),false);
        var owner=input.pledgeDemand().ownerCustomerId();
        if(owner!=null && !owner.isBlank()) customer(owner);
    }
    private String normalizedBusinessDetail(CustomerActivityModels.IntentionRequest input) {
        return input.businessDetail() == null ? "" : input.businessDetail().trim();
    }
    private void validateStageChange(String current,String next) {
        if(Set.of("已流失","已终止").contains(current) && !current.equals(next))
            throw new BusinessException(409,"已结束意向不能变更阶段");
        if("合作中".equals(current) && !Set.of("合作中","已终止").contains(next))
            throw new BusinessException(409,"合作中的意向只能转为已终止");
    }
    private void customer(String id) {
        if (id == null || id.isBlank() || customers.find(id).isEmpty()) {
            throw new BusinessException(400, "客户档案不存在");
        }
    }

    private static String stage(String value) {
        var stage = value == null || value.isBlank() ? "新来访" : value.trim();
        if (!STAGES.contains(stage)) throw new BusinessException(400, "客户阶段不正确");
        return stage;
    }

    private static String code(String prefix) {
        return prefix + "-" + LocalDate.now().getYear() + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
