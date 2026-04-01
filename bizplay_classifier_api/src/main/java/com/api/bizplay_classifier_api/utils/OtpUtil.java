package com.api.bizplay_classifier_api.utils;

import com.api.bizplay_classifier_api.model.dto.OtpDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Random;
import java.util.UUID;

@Component
public class OtpUtil {
    public OtpDTO generateOTP(UUID userId) {
        Random random = new Random();
        int randomNumber = random.nextInt(999999);
        String output = Integer.toString(randomNumber);

        while (output.length() < 6) {
            output = "0" + output;
        }

        LocalDate issuedDate = LocalDate.now();
        LocalDate expiration = issuedDate.plusDays(1);
        return new OtpDTO(userId, output, issuedDate, expiration, false);
    }
}
