package org.example.logisticsback.maintenance;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class WorkOrderRequests {
    private WorkOrderRequests() {}

    public record Create(@NotBlank String assetId,
                         @NotBlank @Size(max = 120) String title,
                         @NotBlank @Size(max = 1000) String description,
                         @NotBlank String priority) {}

    public record Edit(@NotNull @Min(0) Integer version,
                       @NotBlank @Size(max=120) String title,
                       @NotBlank @Size(max=1000) String description,
                       @NotBlank String priority) {}

    public record Action(@NotBlank String action, @NotNull @Min(0) Integer version,
                         @Size(max = 80) String assignee,
                         @Size(max = 1000) String notes,
                         String resolutionAvailability) {}
}
