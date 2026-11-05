package org.example.logisticsback.maintenance;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record WorkOrder(String id, String code, String assetId, String assetCode, String assetName,
                        String title, String description, String priority, String status,
                        String assignee, String repairNotes, String acceptanceNotes,
                        String previousAvailability, String resolutionAvailability, Long reporterId,
                        int version, LocalDateTime createdAt, LocalDateTime updatedAt,
                        LocalDateTime completedAt) {}
