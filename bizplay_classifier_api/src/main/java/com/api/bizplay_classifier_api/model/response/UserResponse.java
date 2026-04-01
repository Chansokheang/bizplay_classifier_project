package com.api.bizplay_classifier_api.model.response;

import com.api.bizplay_classifier_api.model.dto.CompanyDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    private String username;
    private String firstname;
    private String lastname;
    private String email;
    private Character gender;
    private LocalDate dob;
    private Boolean isVerified;
}
