package org.example.logisticsback.org;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.example.logisticsback.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentService {
    private static final Set<String> TYPES = Set.of("COMPANY", "CENTER", "DEPARTMENT", "TEAM");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");
    private final DepartmentRepository repository;

    public DepartmentService(DepartmentRepository repository) {
        this.repository = repository;
    }

    public List<Department> list() {
        return repository.list();
    }

    public Department get(String id) {
        return repository.find(id).orElseThrow(() -> new BusinessException(404, "组织部门不存在"));
    }

    @Transactional
    public Department create(DepartmentRequest input) {
        validate(input, null);
        var id = UUID.randomUUID().toString();
        repository.insert(id, input);
        return get(id);
    }

    @Transactional
    public Department update(String id, DepartmentRequest input) {
        get(id);
        validate(input, id);
        repository.update(id, input);
        return get(id);
    }

    @Transactional
    public void delete(String id) {
        get(id);
        if (repository.countChildren(id) > 0) {
            throw new BusinessException(409, "请先删除或调整下级部门");
        }
        if (repository.countAssets(id) > 0) {
            throw new BusinessException(409, "该部门仍关联设备档案，请先调整设备归属");
        }
        repository.delete(id);
    }

    private void validate(DepartmentRequest input, String currentId) {
        if (!TYPES.contains(input.type())) throw new BusinessException(400, "部门类型不合法");
        if (input.status() != null && !input.status().isBlank() && !STATUSES.contains(input.status())) {
            throw new BusinessException(400, "部门状态不合法");
        }
        if (currentId != null && currentId.equals(input.parentId())) {
            throw new BusinessException(400, "上级部门不能选择自身");
        }
        if (input.parentId() != null && !input.parentId().isBlank()) {
            get(input.parentId());
        }
        if (repository.codeExists(input.code().trim(), currentId)) {
            throw new BusinessException(409, "部门编码已存在");
        }
    }
}
