package com.api.bizplay_classifier_api.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionRequest {
    @NotNull(message = "Company id cannot be null.")
    @Schema(example = "1234567890")
    private String companyId;

    @NotBlank(message = "Approval date cannot be blank.")
    @Pattern(regexp = "^\\d{8}$", message = "Approval date must be in yyyyMMdd format.")
    @Schema(example = "20251125", defaultValue = "20251125")
    @Builder.Default
    private String approvalDate = "20251125";

    @NotBlank(message = "Approval time cannot be blank.")
    @Pattern(regexp = "^\\d{6}$", message = "Approval time must be in HHmmss format.")
    @Schema(example = "161309", defaultValue = "161309")
    @Builder.Default
    private String approvalTime = "161309";

    @NotBlank(message = "Merchant name cannot be blank.")
    @Size(max = 50, message = "Merchant name cannot exceed 50 characters.")
    @Schema(example = "대한항공직판인터넷", defaultValue = "대한항공직판인터넷")
    @Builder.Default
    private String merchantName = "대한항공직판인터넷";

    @Size(max = 5, message = "Merchant industry code cannot exceed 5 characters.")
    @Schema(example = "4078", defaultValue = "4078")
    @Builder.Default
    private String merchantIndustryCode = "4078";

    @Size(max = 50, message = "Merchant industry name cannot exceed 50 characters.")
    @Schema(example = "인터넷Mall", defaultValue = "인터넷Mall")
    @Builder.Default
    private String merchantIndustryName = "인터넷Mall";

    @NotBlank(message = "Merchant business registration number cannot be blank.")
    @Pattern(regexp = "^\\d{10}$", message = "Merchant business registration number must be 10 digits.")
    @Schema(example = "1108114794", defaultValue = "1108114794")
    @Builder.Default
    private String merchantBusinessRegistrationNumber = "1108114794";

    @Schema(example = "87700", defaultValue = "87700")
    @Builder.Default
    private Integer supplyAmount = 87700;

    @Schema(example = "0", defaultValue = "0")
    @Builder.Default
    private Integer vatAmount = 0;

    @Size(max = 10, message = "Tax type cannot exceed 10 characters.")
    @Schema(example = "일반", defaultValue = "일반")
    @Builder.Default
    private String taxType = "일반";

    @Size(max = 50, message = "Field name 1 cannot exceed 50 characters.")
    @Schema(example = "A1001")
    private String fieldName1;

    @Size(max = 255, message = "PK cannot exceed 255 characters.")
    @Schema(example = "TEST-PK-001")
    private String pk;

    @Size(max = 255, message = "User transaction id cannot exceed 255 characters.")
    @Schema(example = "USER-TX-001")
    private String userTxId;

    @Size(max = 255, message = "Writer transaction id cannot exceed 255 characters.")
    @Schema(example = "WRITER-TX-001")
    private String writerTxId;
}
