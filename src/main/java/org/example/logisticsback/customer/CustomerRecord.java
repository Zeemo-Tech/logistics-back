package org.example.logisticsback.customer;

public record CustomerRecord(
        String id,
        String code,
        String name,
        String customerNature,
        String creditCode,
        String legalPerson,
        String contactPerson,
        String contactPhone,
        String address,
        String bankName,
        String bankAccount,
        String type,
        String service,
        String source,
        String owner,
        String stage,
        String status,
        String lastFollowUpAt,
        String nextFollowUpAt,
        String notes,
        String createdAt,
        String updatedAt) {
}
