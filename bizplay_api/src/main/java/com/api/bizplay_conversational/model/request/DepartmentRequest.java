package com.api.bizplay_conversational.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Create/update a department. corpNo is required on create; name is required on both. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DepartmentRequest {

    @Schema(example = "1234567890", description = "Owning corp (required on create; ignored on update)")
    @Size(max = 50)
    private String corpNo;

    @Schema(example = "Sales")
    @Size(max = 100)
    private String name;
}
