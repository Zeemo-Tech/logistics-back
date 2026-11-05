package org.example.logisticsback.assets;

import java.time.LocalDateTime;

public record AssetEvent(String id, String assetId, String eventType, String description,
                         Long operatorId, LocalDateTime createdAt) {}
