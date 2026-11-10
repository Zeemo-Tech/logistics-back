package org.example.logisticsback.contract;

import jakarta.validation.Valid;
import java.util.List;
import org.example.logisticsback.common.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ContractConfigController {
    private final ContractConfigService service;

    public ContractConfigController(ContractConfigService service) {
        this.service = service;
    }

    @GetMapping("/contract-categories")
    public ApiResponse<List<ContractConfigModels.Category>> categories(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ApiResponse.ok(service.categories(activeOnly));
    }

    @PostMapping("/contract-categories")
    public ApiResponse<ContractConfigModels.Category> createCategory(
            @Valid @RequestBody ContractConfigModels.CategoryRequest input) {
        return ApiResponse.ok(service.createCategory(input), "合同类型已创建");
    }

    @PutMapping("/contract-categories/{id}")
    public ApiResponse<ContractConfigModels.Category> updateCategory(
            @PathVariable String id, @Valid @RequestBody ContractConfigModels.CategoryRequest input) {
        return ApiResponse.ok(service.updateCategory(id, input), "合同类型已更新");
    }

    @DeleteMapping("/contract-categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable String id) {
        service.deleteCategory(id);
        return ApiResponse.ok(null, "合同类型已删除");
    }

    @GetMapping("/contract-templates")
    public ApiResponse<List<ContractConfigModels.Template>> templates(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ApiResponse.ok(service.templates(activeOnly));
    }

    @PostMapping("/contract-templates")
    public ApiResponse<ContractConfigModels.Template> createTemplate(
            @Valid @RequestBody ContractConfigModels.TemplateRequest input) {
        return ApiResponse.ok(service.createTemplate(input), "合同模板已创建");
    }

    @PutMapping("/contract-templates/{id}")
    public ApiResponse<ContractConfigModels.Template> updateTemplate(
            @PathVariable String id, @Valid @RequestBody ContractConfigModels.TemplateRequest input) {
        return ApiResponse.ok(service.updateTemplate(id, input), "合同模板已更新");
    }

    @DeleteMapping("/contract-templates/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable String id) {
        service.deleteTemplate(id);
        return ApiResponse.ok(null, "合同模板已删除");
    }

    @GetMapping("/park-resource-types")
    public ApiResponse<List<ContractConfigModels.ResourceType>> resourceTypes(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ApiResponse.ok(service.resourceTypes(activeOnly));
    }

    @PostMapping("/park-resource-types")
    public ApiResponse<ContractConfigModels.ResourceType> createResourceType(
            @Valid @RequestBody ContractConfigModels.ResourceTypeRequest input) {
        return ApiResponse.ok(service.createResourceType(input), "资源类型已创建");
    }

    @PutMapping("/park-resource-types/{id}")
    public ApiResponse<ContractConfigModels.ResourceType> updateResourceType(
            @PathVariable String id, @Valid @RequestBody ContractConfigModels.ResourceTypeRequest input) {
        return ApiResponse.ok(service.updateResourceType(id, input), "资源类型已更新");
    }

    @DeleteMapping("/park-resource-types/{id}")
    public ApiResponse<Void> deleteResourceType(@PathVariable String id) {
        service.deleteResourceType(id);
        return ApiResponse.ok(null, "资源类型已删除");
    }

    @GetMapping("/park-resources")
    public ApiResponse<List<ContractConfigModels.Resource>> resources(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ApiResponse.ok(service.resources(activeOnly));
    }

    @PostMapping("/park-resources")
    public ApiResponse<ContractConfigModels.Resource> createResource(
            @Valid @RequestBody ContractConfigModels.ResourceRequest input) {
        return ApiResponse.ok(service.createResource(input), "园区资源已创建");
    }

    @PutMapping("/park-resources/{id}")
    public ApiResponse<ContractConfigModels.Resource> updateResource(
            @PathVariable String id, @Valid @RequestBody ContractConfigModels.ResourceRequest input) {
        return ApiResponse.ok(service.updateResource(id, input), "园区资源已更新");
    }

    @DeleteMapping("/park-resources/{id}")
    public ApiResponse<Void> deleteResource(@PathVariable String id) {
        service.deleteResource(id);
        return ApiResponse.ok(null, "园区资源已删除");
    }
}
