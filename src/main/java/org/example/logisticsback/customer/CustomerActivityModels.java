package org.example.logisticsback.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public final class CustomerActivityModels {
    private CustomerActivityModels() {
    }

    public record Intention(
            String id,
            String customerId,
            String business,
            String businessDetail,
            BigDecimal amount,
            String stage,
            String owner,
            String source,
            String nextFollowUpAt,
            String notes,
            String updatedAt, PledgeDemand pledgeDemand,
            List<ContractLink> linkedContracts, List<BusinessLink> linkedBusinesses) {
    }

    public record ContractLink(String id, String title, String category, String status) {}
    public record BusinessLink(String id, String code, String title, String status) {}

    public record IntentionRequest(
            @Size(max = 36) String id,
            @NotBlank String customerId,
            @NotBlank @Size(max = 80) String business,
            @Size(max = 80) String businessDetail,
            BigDecimal amount,
            @Size(max = 40) String stage,
            @Size(max = 80) String owner,
            @Size(max = 80) String source,
            @Size(max = 32) String nextFollowUpAt,
            @Size(max = 2000) String notes, PledgeDemand pledgeDemand) {
        public IntentionRequest(String id,String customerId,String business,BigDecimal amount,String stage,
                                String businessDetail, String owner,String source,String nextFollowUpAt,String notes) {
            this(id,customerId,business,businessDetail,amount,stage,owner,source,nextFollowUpAt,notes,null);
        }
        public IntentionRequest(String id,String customerId,String business,BigDecimal amount,String stage,
                                String owner,String source,String nextFollowUpAt,String notes) {
            this(id,customerId,business,"",amount,stage,owner,source,nextFollowUpAt,notes,null);
        }
    }

    public record Visit(
            String id,
            String customerId,
            String intentionId,
            String visitedAt,
            String visitor,
            String visitorPhone,
            String interest,
            String requirement,
            String expectedStart,
            String budget,
            String host,
            String note,
            String nextAction,
            String nextFollowUpAt,
            String stage) {
    }

    public record VisitRequest(
            @Size(max = 36) String id,
            @NotBlank String customerId,
            String intentionId,
            @Size(max = 32) String visitedAt,
            @Size(max = 80) String visitor,
            @Size(max = 40) String visitorPhone,
            @Size(max = 80) String interest,
            @Size(max = 1000) String requirement,
            @Size(max = 32) String expectedStart,
            @Size(max = 120) String budget,
            @Size(max = 80) String host,
            @Size(max = 2000) String note,
            @Size(max = 200) String nextAction,
            @Size(max = 32) String nextFollowUpAt,
            @Size(max = 40) String stage) {
    }
}
