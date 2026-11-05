package org.example.logisticsback.maintenance;

import java.time.LocalDateTime;

public record WorkOrderEvent(String id, String action, String description, Long operatorId,
                             String operatorName, LocalDateTime createdAt) {}
