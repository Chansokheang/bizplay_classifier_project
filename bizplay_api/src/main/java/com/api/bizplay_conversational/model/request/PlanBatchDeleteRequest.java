package com.api.bizplay_conversational.model.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** Delete several trip plans at once, by their ids. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanBatchDeleteRequest {

    @JsonProperty("ids")
    @JsonAlias({"planIds", "plan_ids", "Ids"})
    @Schema(example = "[\"d42daf38-24cc-46db-9506-e4cd0df952bc\", \"8934d553-3343-46bb-8173-0a5ad1133ccf\"]",
            description = "conversational_trip_plan ids to delete")
    @NotEmpty(message = "ids must not be empty")
    private List<@NotBlank String> ids = new ArrayList<>();
}
