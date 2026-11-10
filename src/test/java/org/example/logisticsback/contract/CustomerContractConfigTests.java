package org.example.logisticsback.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.example.logisticsback.common.BusinessException;
import org.example.logisticsback.customer.CustomerActivityModels;
import org.example.logisticsback.customer.CustomerActivityService;
import org.example.logisticsback.customer.CustomerRequest;
import org.example.logisticsback.customer.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CustomerContractConfigTests {
    @Autowired CustomerService customers;
    @Autowired ContractService contracts;
    @Autowired ContractConfigService config;
    @Autowired CustomerActivityService activities;

    @Test
    void customerContractAndConfigurationArePersistedAndProtected() {
        var customer = customers.create(new CustomerRequest(
                null, "测试客户有限公司", "企业", "91530000TEST000001", "测试法人", "测试联系人",
                "13800000000", "测试地址", "", "", "仓储客户", "仓储服务", "测试", "测试负责人",
                "合作中", "已签约", "", "", ""));
        var intention = activities.createIntention(new CustomerActivityModels.IntentionRequest(
                null, customer.id(), "仓储服务", null, "新来访", "测试负责人", "测试", "", ""));
        var category = config.createCategory(new ContractConfigModels.CategoryRequest("测试合同类型", "ACTIVE", 1));
        config.createResourceType(new ContractConfigModels.ResourceTypeRequest("测试资源类型", "TEST_RESOURCE", "ACTIVE", 1));
        var resource = config.createResource(new ContractConfigModels.ResourceRequest(
                "测试园区资源", "测试资源类型", "测试地址", "", "ACTIVE", 1));
        var contract = contracts.create(new ContractRequest(
                "测试仓储合同", category.name(), customer.id(), resource.id(), null,
                resource.resourceType(), "2026-01-01", "2027-01-01", "1.00 万元", null, null, null,
                null, "测试负责人", "测试合同正文", "履约中", java.util.List.of(intention.id())));

        assertEquals(customer.id(), contract.customerId());
        assertEquals(java.util.List.of(intention.id()), contract.intentionIds());
        assertEquals("合作中", activities.intentions(customer.id(), null).get(0).stage());
        assertEquals(resource.id(), contract.resourceId());
        assertThrows(BusinessException.class, () -> customers.delete(customer.id()));
        assertThrows(BusinessException.class, () -> config.deleteCategory(category.id()));
        assertThrows(BusinessException.class, () -> config.deleteResource(resource.id()));
    }

    @Test
    void transportContractUsesConfiguredTemplateWithoutParkResource() {
        var customer = customers.create(new CustomerRequest(
                null, "运输测试客户", "企业", "91530000TEST000002", "测试法人", "运输联系人",
                "13800000001", "测试地址", "", "", "运输客户", "运输服务", "测试", "测试负责人",
                "待签约", "跟进中", "", "", ""));
        var intention = activities.createIntention(new CustomerActivityModels.IntentionRequest(
                null, customer.id(), "运输服务", "货车运输", null, "待签约", "测试负责人", "测试", "", "", null));
        var template = config.templates(true).stream()
                .filter(item -> "运输服务".equals(item.intentionBusiness())
                        && "货车运输".equals(item.businessDetail()))
                .findFirst().orElseThrow();
        var contract = contracts.create(new ContractRequest(
                template.title(), template.category(), customer.id(), null, template.id(), null,
                "2026-01-01", "2027-01-01", "100000", null, null, null,
                null, "测试负责人", "运输合同正文", "待生效", java.util.List.of(intention.id()), "货车运输"));

        assertEquals("货车运输", contract.businessDetail());
        assertEquals("", contract.resource());
        assertEquals(template.id(), contract.templateId());
        assertThrows(BusinessException.class, () -> activities.createIntention(
                new CustomerActivityModels.IntentionRequest(
                        null, customer.id(), "运输服务", null, "待签约", "测试负责人", "测试", "", "")));
    }
}
