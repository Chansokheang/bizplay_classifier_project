package com.api.bizplay_classifier_api.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CategoryBatchItemRequest {

    @NotBlank(message = "Code can not be blank.")
    @Pattern(regexp = "^[A-Za-z0-9]{1,50}$", message = "Code must be 1 to 50 alphanumeric characters.")
    private String code;

    @NotBlank(message = "Category can not be blank.")
    private String category;

    @Builder.Default
    private Boolean isUsed = false;
}
