package com.api.bizplay_conversational.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Create/update a staff member. departmentId and name are required; position is optional. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StaffRequest {

    @Schema(example = "3250fe6f-516e-42b0-86d2-ba84404b316c", description = "conversational_department.id")
    @Size(max = 36)
    private String departmentId;

    @Schema(example = "Chhun Rotanakkosal")
    @Size(max = 100)
    private String name;

    @Schema(example = "Developer")
    @Size(max = 100)
    private String position;
}
