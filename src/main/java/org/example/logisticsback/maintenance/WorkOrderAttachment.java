package org.example.logisticsback.maintenance;

import java.time.LocalDateTime;

public record WorkOrderAttachment(
        String id,
        String workOrderId,
        String category,
        String name,
        String mimeType,
        long fileSize,
        Long uploadedBy,
        LocalDateTime uploadedAt,
        String remark) {}
