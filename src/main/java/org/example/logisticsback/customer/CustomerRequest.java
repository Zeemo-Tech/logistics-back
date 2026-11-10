package org.example.logisticsback.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @Size(max = 48) String id,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 20) String customerNature,
        @Size(max = 64) String creditCode,
        @Size(max = 80) String legalPerson,
        @Size(max = 80) String contactPerson,
        @Size(max = 40) String contactPhone,
        @Size(max = 300) String address,
        @Size(max = 160) String bankName,
        @Size(max = 80) String bankAccount,
        @Size(max = 160) String type,
        @Size(max = 300) String service,
        @Size(max = 80) String source,
        @Size(max = 80) String owner,
        @Size(max = 40) String stage,
        @Size(max = 40) String status,
        @Size(max = 32) String lastFollowUpAt,
        @Size(max = 32) String nextFollowUpAt,
        @Size(max = 1000) String notes) {
}
