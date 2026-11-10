package org.example.logisticsback.maintenance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.example.logisticsback.common.BusinessException;
import org.springframework.stereotype.Service;

@Service
public class RepairArchiveService {
    private final WorkOrderRepository orders;
    private final WorkOrderService workOrders;

    public RepairArchiveService(WorkOrderRepository orders, WorkOrderService workOrders) {
        this.orders = orders;
        this.workOrders = workOrders;
    }

    public List<RepairArchiveEntry> list(String assetId, String keyword, LocalDate from, LocalDate to) {
        return orders.archive(assetId, keyword, from, to).stream().map(this::entry).toList();
    }

    public RepairArchiveEntry get(String workOrderId) {
        var order = workOrders.get(workOrderId);
        if (!"COMPLETED".equals(order.status())) {
            throw new BusinessException(400, "仅已完成的维修工单会进入维修档案");
        }
        return entry(order);
    }

    private RepairArchiveEntry entry(WorkOrder order) {
        var parts = orders.parts(order.id());
        var labor = order.laborCost() == null ? BigDecimal.ZERO : order.laborCost();
        var partsCost = order.partsCost() == null ? BigDecimal.ZERO : order.partsCost();
        var total = order.totalCost() == null ? labor.add(partsCost) : order.totalCost();
        return new RepairArchiveEntry(order, parts, labor, partsCost, total,
                order.downtimeMinutes(), order.completedAt());
    }
}
