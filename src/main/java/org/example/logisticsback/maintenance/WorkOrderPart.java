package org.example.logisticsback.maintenance;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WorkOrderPart(
        String id,
        String workOrderId,
        String name,
        String spec,
        BigDecimal quantity,
        String unit,
        BigDecimal unitPrice,
        BigDecimal amount,
        String supplier,
        String replacedPartCode,
        String remark,
        LocalDateTime createdAt) {}
