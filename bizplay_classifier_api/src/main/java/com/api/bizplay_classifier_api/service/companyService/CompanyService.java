package com.api.bizplay_classifier_api.service.companyService;

import com.api.bizplay_classifier_api.model.enums.CompanyType;
import com.api.bizplay_classifier_api.model.dto.CompanyDTO;
import com.api.bizplay_classifier_api.model.request.CompanyRequest;
import com.api.bizplay_classifier_api.model.response.CompanyResponse;

import java.util.List;
import java.util.UUID;

public interface CompanyService {
    List<CompanyResponse> getAllCompanyByUserId() throws Exception;

    CompanyResponse createCompany(CompanyRequest companyRequest, CompanyType companyType);

    CompanyResponse getCompanyByCompanyId(UUID companyId);

    void deleteCompanyByCompanyId(UUID companyId);
}
