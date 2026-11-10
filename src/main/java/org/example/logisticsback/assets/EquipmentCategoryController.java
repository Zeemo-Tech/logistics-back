package org.example.logisticsback.assets;

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
@RequestMapping("/api/equipment-categories")
public class EquipmentCategoryController {
    private final EquipmentCategoryService service;
    private final EquipmentCategoryRepository repository;

    public EquipmentCategoryController(EquipmentCategoryService service,
                                       EquipmentCategoryRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @GetMapping
    public ApiResponse<List<EquipmentCategory>> list(
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ApiResponse.ok(activeOnly ? repository.listActive() : service.list());
    }

    @PostMapping
    public ApiResponse<EquipmentCategory> create(@Valid @RequestBody EquipmentCategoryRequest input) {
        return ApiResponse.ok(service.create(input), "设备分类已创建");
    }

    @PutMapping("/{id}")
    public ApiResponse<EquipmentCategory> update(@PathVariable String id,
                                                 @Valid @RequestBody EquipmentCategoryRequest input) {
        return ApiResponse.ok(service.update(id, input), "设备分类已更新");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ApiResponse.ok(null, "设备分类已删除");
    }
}
