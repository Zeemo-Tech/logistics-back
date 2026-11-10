package org.example.logisticsback.maintenance;

public record WorkOrderCompletedEvent(String workOrderId, String assetId) {}
