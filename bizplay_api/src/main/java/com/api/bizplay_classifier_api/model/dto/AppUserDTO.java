package com.api.bizplay_classifier_api.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUserDTO {
    private String username;
    private String firstname;
    private String lastname;
    private String email;
    private Character gender;
    private LocalDate dob;
    private Boolean isVerified;
//    private List<CompanyDTO> companyDTOList;
}
