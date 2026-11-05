package org.example.logisticsback.contract;

public record ContractRecord(
        String id,
        String title,
        String category,
        String counterparty,
        String resource,
        String startDate,
        String endDate,
        String amount,
        String owner,
        int documentCount,
        String status) {}
