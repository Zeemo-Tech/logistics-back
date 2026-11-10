package org.example.logisticsback.contract;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record ContractRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 80) String category,
        @NotBlank @Size(max = 36) String customerId,
        String resourceId,
        String templateId,
        @Size(max = 80) String resourceType,
        @NotBlank @Size(max = 16) String startDate,
        @NotBlank @Size(max = 16) String endDate,
        @Size(max = 32) String amount,
        BigDecimal deposit,
        BigDecimal unitPrice,
        BigDecimal quantity,
        BigDecimal area,
        @NotBlank @Size(max = 32) String owner,
        @Size(max = 2000) String content,
        @Size(max = 16) String status,
        List<String> intentionIds) {
}
