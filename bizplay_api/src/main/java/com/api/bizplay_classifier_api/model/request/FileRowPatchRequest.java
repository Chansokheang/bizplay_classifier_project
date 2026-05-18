package com.api.bizplay_classifier_api.model.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @JsonProperty("corpNo")
    @JsonAlias("companyId")
    @Schema(name = "corpNo", example = "1234567890")
    private String companyId;

    @NotEmpty(message = "updates list must not be empty.")
    @Valid
    private List<RowCellUpdateRequest> updates;
}
