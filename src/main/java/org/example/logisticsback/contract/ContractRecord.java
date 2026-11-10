package org.example.logisticsback.contract;

import java.math.BigDecimal;
import java.util.List;

public record ContractRecord(
        String id,
        String title,
        String category,
        String counterparty,
        String resource,
        String resourceType,
        String businessDetail,
        String customerId,
        String resourceId,
        String templateId,
        String startDate,
        String endDate,
        String amount,
        BigDecimal deposit,
        BigDecimal unitPrice,
        BigDecimal quantity,
        BigDecimal area,
        String owner,
        int documentCount,
        String status,
        String content,
        List<String> intentionIds,
        List<String> intentionBusinesses) {
}
