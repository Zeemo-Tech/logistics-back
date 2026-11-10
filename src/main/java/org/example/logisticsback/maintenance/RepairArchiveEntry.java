package org.example.logisticsback.maintenance;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record RepairArchiveEntry(
        WorkOrder order,
        List<WorkOrderPart> parts,
        BigDecimal laborCost,
        BigDecimal partsCost,
        BigDecimal totalCost,
        Integer downtimeMinutes,
        LocalDateTime archivedAt) {}
