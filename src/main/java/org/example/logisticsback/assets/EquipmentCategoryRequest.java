package org.example.logisticsback.assets;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EquipmentCategoryRequest(
        String parentId,
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 120) String name,
        Integer sortOrder,
        String status) {}
