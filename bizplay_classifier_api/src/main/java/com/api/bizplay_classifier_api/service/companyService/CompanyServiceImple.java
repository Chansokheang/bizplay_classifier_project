package com.api.bizplay_classifier_api.service.companyService;

import com.api.bizplay_classifier_api.exception.CustomNotFoundException;
import com.api.bizplay_classifier_api.model.dto.CompanyDTO;
import com.api.bizplay_classifier_api.model.request.CompanyRequest;
import com.api.bizplay_classifier_api.model.response.CompanyResponse;
import com.api.bizplay_classifier_api.repository.CompanyRepo;
import com.api.bizplay_classifier_api.service.userService.AppUserService;
import com.api.bizplay_classifier_api.utils.GetCurrentUser;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Log4j2
@Service
@AllArgsConstructor
public class CompanyServiceImple implements CompanyService {

    private final CompanyRepo companyRepo;
    private final AppUserService appUserService;
    private final GetCurrentUser getCurrentUser;
    private final ModelMapper modelMapper;

    @Override
    public List<CompanyResponse> getAllCompanyByUserId() throws UsernameNotFoundException {
        UUID userId = getCurrentUser.getCurrentUserId();
        List<CompanyDTO> companyDTOList = companyRepo.getAllCompanyByUserId(userId);
        return companyDTOList.stream()
                .map(companyDTO -> modelMapper.map(companyDTO, CompanyResponse.class))
                .toList();
    }

    @Override
    @Transactional
    public CompanyResponse createCompany(CompanyRequest companyRequest) throws UsernameNotFoundException {
        UUID userId = getCurrentUser.getCurrentUserId();
        companyRequest.setBusinessNumber(normalizeBusinessNumber(companyRequest.getBusinessNumber()));
        CompanyDTO companyDTO = companyRepo.createCompany(companyRequest, userId);
        return modelMapper.map(companyDTO, CompanyResponse.class);
    }

    @Override
    public CompanyResponse getCompanyByCompanyId(String companyId) {
        UUID userId = getCurrentUser.getCurrentUserId();
        CompanyDTO companyDTO = companyRepo.getCompanyByCompanyId(userId, companyId);
        if (companyDTO == null) {
            throw new CustomNotFoundException("Company was not found with Id: " + companyId);
        }
        return modelMapper.map(companyDTO, CompanyResponse.class);
    }

    @Override
    @Transactional
    public void deleteCompanyByCompanyId(String companyId) {
        UUID userId = getCurrentUser.getCurrentUserId();
        int deletedRows = companyRepo.deleteCompanyByCompanyId(userId, companyId);
        if (deletedRows == 0) {
            throw new CustomNotFoundException("Company was not found with Id: " + companyId);
        }
    }

    private String normalizeBusinessNumber(String businessNumber) {
        if (businessNumber == null || businessNumber.isBlank()) {
            return null;
        }

        String digitsOnly = businessNumber.replaceAll("\\D", "");
        if (digitsOnly.length() != 10) {
            throw new IllegalArgumentException("Business number must be exactly 10 digits.");
        }
        return digitsOnly;
    }

}
