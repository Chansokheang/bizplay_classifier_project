package com.api.bizplay_classifier_api.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionRequest {
    @NotNull(message = "Company id cannot be null.")
    private UUID companyId;

    @NotBlank(message = "Approval date cannot be blank.")
    @Pattern(regexp = "^\\d{8}$", message = "Approval date must be in yyyyMMdd format.")
    private String approvalDate = "20260327";

    @NotBlank(message = "Approval time cannot be blank.")
    @Pattern(regexp = "^\\d{6}$", message = "Approval time must be in HHmmss format.")
    private String approvalTime = "120000";

    @NotBlank(message = "Merchant name cannot be blank.")
    @Size(max = 50, message = "Merchant name cannot exceed 50 characters.")
    private String merchantName = "Test Merchant";

    @Size(max = 5, message = "Merchant industry code cannot exceed 5 characters.")
    private String merchantIndustryCode;

    @Size(max = 50, message = "Merchant industry name cannot exceed 50 characters.")
    private String merchantIndustryName;

    @NotBlank(message = "Merchant business registration number cannot be blank.")
    @Pattern(regexp = "^\\d{10}$", message = "Merchant business registration number must be 10 digits.")
    private String merchantBusinessRegistrationNumber = "1234567890";

    private Integer supplyAmount;
    private Integer vatAmount;

    @Size(max = 10, message = "Tax type cannot exceed 10 characters.")
    private String taxType;

    @Size(max = 50, message = "Field name 1 cannot exceed 50 characters.")
    private String fieldName1;

    @Size(max = 255, message = "PK cannot exceed 255 characters.")
    private String pk;

    @Size(max = 255, message = "User transaction id cannot exceed 255 characters.")
    private String userTxId;

    @Size(max = 255, message = "Writer transaction id cannot exceed 255 characters.")
    private String writerTxId;
}
