package com.api.bizplay_classifier_api.model.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RowCellUpdateRequest {

    /**
     * 1-based data row number (row 1 = first data row after the header).
     */
    @NotNull(message = "rowIndex is required.")
    @Min(value = 1, message = "rowIndex must be at least 1.")
    private Integer rowIndex;

    /**
     * New 용도코드 to apply.  Must be exactly 5 alphanumeric characters.
     */
    @NotBlank(message = "usageCode is required.")
    @Pattern(regexp = "^[A-Za-z0-9]{1,50}$", message = "usageCode must be 1 to 50 alphanumeric characters.")
    private String usageCode;
}
