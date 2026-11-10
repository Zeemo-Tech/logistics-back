package org.example.logisticsback.assets;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.example.logisticsback.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentCategoryService {
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");
    private final EquipmentCategoryRepository repository;

    public EquipmentCategoryService(EquipmentCategoryRepository repository) {
        this.repository = repository;
    }

    public List<EquipmentCategory> list() {
        return repository.list();
    }

    public List<EquipmentCategory> listActive() {
        return repository.listActive();
    }

    @Transactional
    public EquipmentCategory create(EquipmentCategoryRequest input) {
        var level = validate(input, null);
        var id = UUID.randomUUID().toString();
        repository.insert(id, input.parentId(), level, input);
        return get(id);
    }

    @Transactional
    public EquipmentCategory update(String id, EquipmentCategoryRequest input) {
        get(id);
        var level = validate(input, id);
        repository.update(id, input.parentId(), level, input);
        return get(id);
    }

    @Transactional
    public void delete(String id) {
        get(id);
        if (repository.countChildren(id) > 0) {
            throw new BusinessException(409, "请先删除下级分类");
        }
        if (repository.countAssets(id) > 0) {
            throw new BusinessException(409, "该分类仍有关联设备档案，请先调整设备分类");
        }
        repository.delete(id);
    }

    private EquipmentCategory get(String id) {
        return repository.find(id).orElseThrow(() -> new BusinessException(404, "设备分类不存在"));
    }

    private int validate(EquipmentCategoryRequest input, String currentId) {
        if (input.status() != null && !input.status().isBlank() && !STATUSES.contains(input.status())) {
            throw new BusinessException(400, "分类状态不合法");
        }
        if (repository.codeExists(input.code().trim(), currentId)) {
            throw new BusinessException(409, "分类编码已存在");
        }
        int level = 1;
        if (input.parentId() != null && !input.parentId().isBlank()) {
            if (input.parentId().equals(currentId)) {
                throw new BusinessException(400, "上级分类不能选择自身");
            }
            var parent = get(input.parentId());
            assertNotDescendant(input.parentId(), currentId);
            level = parent.level() + 1;
            if (level > 3) throw new BusinessException(400, "设备分类最多支持三级");
        }
        return level;
    }

    private void assertNotDescendant(String parentId, String currentId) {
        if (currentId == null) return;
        var cursor = repository.find(parentId).orElse(null);
        while (cursor != null) {
            if (currentId.equals(cursor.id())) {
                throw new BusinessException(400, "不能将分类移动到自己的下级");
            }
            cursor = cursor.parentId() == null ? null : repository.find(cursor.parentId()).orElse(null);
        }
    }
}
