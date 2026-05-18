package com.api.bizplay_classifier_api.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionDTO {
    private UUID transactionId;
    @JsonProperty("corpNo")
    private String companyId;
    private String approvalDate;
    private String approvalTime;
    private String merchantName;
    private String merchantIndustryCode;
    private String merchantIndustryName;
    private String merchantBusinessRegistrationNumber;
    private Integer supplyAmount;
    private Integer vatAmount;
    private String taxType;
    private String fieldName1;
    private String pk;
    private String userTxId;
    private String writerTxId;
    private Timestamp createdDate;
}
