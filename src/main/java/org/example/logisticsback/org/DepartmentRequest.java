package org.example.logisticsback.org;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DepartmentRequest(
        String parentId,
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 120) String name,
        @NotBlank String type,
        @Size(max = 80) String leader,
        @Size(max = 32) String phone,
        Integer sortOrder,
        String status,
        @Size(max = 500) String notes) {}
