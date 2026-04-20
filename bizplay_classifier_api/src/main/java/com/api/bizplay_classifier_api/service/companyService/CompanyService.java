package com.api.bizplay_classifier_api.service.companyService;

import com.api.bizplay_classifier_api.model.request.CompanyRequest;
import com.api.bizplay_classifier_api.model.response.CompanyResponse;

import java.util.List;
public interface CompanyService {
    List<CompanyResponse> getAllCompanyByUserId() throws Exception;

    CompanyResponse createCompany(CompanyRequest companyRequest);

    CompanyResponse getCompanyByCompanyId(String companyId);

    void deleteCompanyByCompanyId(String companyId);
}
