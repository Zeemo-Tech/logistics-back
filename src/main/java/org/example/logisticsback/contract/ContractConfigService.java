package org.example.logisticsback.contract;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.example.logisticsback.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractConfigService {
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");
    private final ContractConfigRepository repository;

    public ContractConfigService(ContractConfigRepository repository) {
        this.repository = repository;
    }

    public List<ContractConfigModels.Category> categories(boolean activeOnly) {
        return repository.listCategories(activeOnly);
    }

    @Transactional
    public ContractConfigModels.Category createCategory(ContractConfigModels.CategoryRequest input) {
        var name = input.name().trim();
        if (repository.categoryNameExists(name, null)) throw new BusinessException(409, "合同类型已存在");
        var id = id("CC-");
        repository.insertCategory(id, name, status(input.status()), sort(input.sortOrder()));
        return getCategory(id);
    }

    @Transactional
    public ContractConfigModels.Category updateCategory(String id, ContractConfigModels.CategoryRequest input) {
        var current = getCategory(id);
        var name = input.name().trim();
        if (repository.categoryNameExists(name, id)) throw new BusinessException(409, "合同类型已存在");
        repository.updateCategory(id, name, status(input.status()), sort(input.sortOrder()));
        if (!current.name().equals(name)) repository.renameCategory(current.name(), name);
        return getCategory(id);
    }

    @Transactional
    public void deleteCategory(String id) {
        var current = getCategory(id);
        if (repository.countContractsByCategory(current.name()) > 0) {
            throw new BusinessException(409, "该合同类型仍有关联合同，不能删除");
        }
        if (repository.countTemplatesByCategory(current.name()) > 0) {
            throw new BusinessException(409, "该合同类型仍有关联模板，不能删除");
        }
        repository.deleteCategory(id);
    }

    public List<ContractConfigModels.Template> templates(boolean activeOnly) {
        return repository.listTemplates(activeOnly);
    }

    @Transactional
    public ContractConfigModels.Template createTemplate(ContractConfigModels.TemplateRequest input) {
        var title = input.title().trim();
        if (repository.templateTitleExists(title, null)) throw new BusinessException(409, "合同模板名称已存在");
        var id = id("CT-");
        repository.insertTemplate(id, input, status(input.status()), sort(input.sortOrder()));
        return getTemplate(id);
    }

    @Transactional
    public ContractConfigModels.Template updateTemplate(String id, ContractConfigModels.TemplateRequest input) {
        getTemplate(id);
        var title = input.title().trim();
        if (repository.templateTitleExists(title, id)) throw new BusinessException(409, "合同模板名称已存在");
        repository.updateTemplate(id, input, status(input.status()), sort(input.sortOrder()));
        return getTemplate(id);
    }

    @Transactional
    public void deleteTemplate(String id) {
        getTemplate(id);
        if (repository.countContractsByTemplate(id) > 0) {
            throw new BusinessException(409, "该模板仍有关联合同，不能删除");
        }
        repository.deleteTemplate(id);
    }

    public List<ContractConfigModels.ResourceType> resourceTypes(boolean activeOnly) {
        return repository.listResourceTypes(activeOnly);
    }

    @Transactional
    public ContractConfigModels.ResourceType createResourceType(ContractConfigModels.ResourceTypeRequest input) {
        var name = input.name().trim();
        var code = input.code() == null || input.code().isBlank() ? "RT-" + shortId() : input.code().trim().toUpperCase();
        if (repository.resourceTypeNameExists(name, null)) throw new BusinessException(409, "资源类型已存在");
        if (repository.resourceTypeCodeExists(code, null)) throw new BusinessException(409, "资源类型编码已存在");
        var id = id("PRT-");
        repository.insertResourceType(id, code, name, status(input.status()), sort(input.sortOrder()));
        return getResourceType(id);
    }

    @Transactional
    public ContractConfigModels.ResourceType updateResourceType(String id, ContractConfigModels.ResourceTypeRequest input) {
        var current = getResourceType(id);
        var name = input.name().trim();
        var code = input.code() == null || input.code().isBlank() ? current.code() : input.code().trim().toUpperCase();
        if (repository.resourceTypeNameExists(name, id)) throw new BusinessException(409, "资源类型已存在");
        if (repository.resourceTypeCodeExists(code, id)) throw new BusinessException(409, "资源类型编码已存在");
        repository.updateResourceType(id, code, name, status(input.status()), sort(input.sortOrder()));
        if (!current.name().equals(name)) repository.renameResourceType(current.name(), name);
        return getResourceType(id);
    }

    @Transactional
    public void deleteResourceType(String id) {
        var current = getResourceType(id);
        if (repository.countResourcesByType(current.name()) > 0) {
            throw new BusinessException(409, "该资源类型仍有关联园区资源，不能删除");
        }
        if (repository.countTemplatesByResourceType(current.name()) > 0) {
            throw new BusinessException(409, "该资源类型仍有关联合同模板，不能删除");
        }
        repository.deleteResourceType(id);
    }

    public List<ContractConfigModels.Resource> resources(boolean activeOnly) {
        return repository.listResources(activeOnly);
    }

    @Transactional
    public ContractConfigModels.Resource createResource(ContractConfigModels.ResourceRequest input) {
        var name = input.name().trim();
        var code = "RES-" + shortId();
        if (repository.resourceNameExists(name, null)) throw new BusinessException(409, "园区资源已存在");
        var id = id("RES-");
        repository.insertResource(id, code, input, status(input.status()), sort(input.sortOrder()));
        return getResource(id);
    }

    @Transactional
    public ContractConfigModels.Resource updateResource(String id, ContractConfigModels.ResourceRequest input) {
        var current = getResource(id);
        var name = input.name().trim();
        if (repository.resourceNameExists(name, id)) throw new BusinessException(409, "园区资源已存在");
        repository.updateResource(id, current.code(), input, status(input.status()), sort(input.sortOrder()));
        if (!current.name().equals(name)) repository.renameResource(id, name);
        return getResource(id);
    }

    @Transactional
    public void deleteResource(String id) {
        getResource(id);
        if (repository.countContractsByResource(id) > 0) {
            throw new BusinessException(409, "该园区资源仍有关联合同，不能删除");
        }
        repository.deleteResource(id);
    }

    private ContractConfigModels.Category getCategory(String id) {
        return repository.findCategory(id).orElseThrow(() -> new BusinessException(404, "合同类型不存在"));
    }

    private ContractConfigModels.Template getTemplate(String id) {
        return repository.findTemplate(id).orElseThrow(() -> new BusinessException(404, "合同模板不存在"));
    }

    private ContractConfigModels.ResourceType getResourceType(String id) {
        return repository.findResourceType(id).orElseThrow(() -> new BusinessException(404, "资源类型不存在"));
    }

    private ContractConfigModels.Resource getResource(String id) {
        return repository.findResource(id).orElseThrow(() -> new BusinessException(404, "园区资源不存在"));
    }

    private static String status(String value) {
        var status = value == null || value.isBlank() ? "ACTIVE" : value.trim();
        if (!STATUSES.contains(status)) throw new BusinessException(400, "状态不正确");
        return status;
    }

    private static int sort(Integer value) {
        return value == null ? 0 : value;
    }

    private static String id(String prefix) {
        var random = UUID.randomUUID().toString().replace("-", "");
        return prefix + random.substring(0, Math.min(random.length(), 36 - prefix.length()));
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
