package com.api.bizplay_classifier_api.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileRowPatchRequest {

    @NotNull(message = "companyId is required.")
    private String companyId;

    @NotEmpty(message = "updates list must not be empty.")
    @Valid
    private List<RowCellUpdateRequest> updates;
}
