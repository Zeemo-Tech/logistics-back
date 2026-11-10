package org.example.logisticsback.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ContractConfigModels {
    private ContractConfigModels() {
    }

    public record Category(String id, String name, String status, int sortOrder) {
    }

    public record CategoryRequest(
            @NotBlank @Size(max = 80) String name,
            String status,
            Integer sortOrder) {
    }

    public record Template(
            String id,
            String title,
            String resourceType,
            String intentionBusiness,
            String businessDetail,
            String category,
            String subject,
            String purpose,
            String content,
            String status,
            int sortOrder) {
    }

    public record TemplateRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 80) String resourceType,
            @Size(max = 80) String intentionBusiness,
            @Size(max = 80) String businessDetail,
            @Size(max = 80) String category,
            @Size(max = 300) String subject,
            @Size(max = 500) String purpose,
            String content,
            String status,
            Integer sortOrder) {
    }

    public record ResourceType(
            String id,
            String code,
            String name,
            String status,
            int sortOrder) {
    }

    public record ResourceTypeRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 40) String code,
            String status,
            Integer sortOrder) {
    }

    public record Resource(
            String id,
            String code,
            String name,
            String resourceType,
            String address,
            String note,
            String status,
            int sortOrder) {
    }

    public record ResourceRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 80) String resourceType,
            @Size(max = 300) String address,
            @Size(max = 500) String note,
            String status,
            Integer sortOrder) {
    }
}
