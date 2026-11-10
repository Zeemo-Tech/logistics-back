package org.example.logisticsback.org;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record Department(
        String id,
        String parentId,
        String code,
        String name,
        String type,
        String leader,
        String phone,
        int sortOrder,
        String status,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
