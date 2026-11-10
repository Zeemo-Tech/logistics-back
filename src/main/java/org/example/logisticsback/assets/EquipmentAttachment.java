package org.example.logisticsback.assets;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record EquipmentAttachment(
        String id,
        String assetId,
        String category,
        String name,
        String mimeType,
        long fileSize,
        Long uploadedBy,
        LocalDateTime uploadedAt,
        String remark) {}
