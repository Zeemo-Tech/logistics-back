package org.example.logisticsback.pledge;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.constraints.*;

public final class PledgeRequests {
    private PledgeRequests() {}
    public record Business(
        @NotBlank @Size(max=160) String title, @NotBlank @Size(max=160) String borrower,
        @NotBlank @Size(max=160) String ownerName, @NotBlank @Size(max=160) String bankName,
        @NotBlank @Size(max=160) String issuer, @NotBlank @Size(max=160) String supervisor,
        @NotBlank @Size(max=100) String manager, @NotBlank @Size(max=100) String agreementNumber,
        @NotNull LocalDate startDate, @NotNull LocalDate endDate,
        @NotBlank @Size(max=8000) String rulesText,
        @NotBlank String borrowerCustomerId, @NotBlank String ownerCustomerId,
        String contractId, @NotBlank String agreementContractId, String sourceIntentionId) {
        public Business(String title,String borrower,String ownerName,String bankName,String issuer,String supervisor,
                        String manager,String agreementNumber,LocalDate startDate,LocalDate endDate,String rulesText,
                        String borrowerCustomerId,String ownerCustomerId,String contractId,String agreementContractId) {
            this(title,borrower,ownerName,bankName,issuer,supervisor,manager,agreementNumber,startDate,endDate,
                rulesText,borrowerCustomerId,ownerCustomerId,contractId,agreementContractId,null);
        }
    }
    public record Line(@NotBlank String lotId, @NotNull BigDecimal quantity) {}
    public record Command(
        @NotBlank String action, @NotNull Integer version, String targetId, String instructionId,
        String documentId, String type, String number, String receiptId, String lotId,
        String warehouseId, String location, String goodsName, String specification,
        String batchNumber, String unit, BigDecimal quantity, LocalDate validUntil,
        LocalDate productionDate, LocalDate expiryDate, String temperature,
        String note, Boolean abnormal, List<Line> lines) {}
}
