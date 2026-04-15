package com.api.bizplay_classifier_api.model.entity;

import com.api.bizplay_classifier_api.model.enums.CompanyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {
    private UUID companyId;
    private UUID userId;
    private String companyName;
    private String businessNumber;
    private CompanyType types;
    private Timestamp createdDate;
}
