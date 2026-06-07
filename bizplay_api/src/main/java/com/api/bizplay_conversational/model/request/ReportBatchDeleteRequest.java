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

/** Delete several expense-report lines at once, by their conversational_trip_report ids. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReportBatchDeleteRequest {

    @JsonProperty("ids")
    @JsonAlias({"reportIds", "report_ids", "Ids"})
    @Schema(description = "conversational_trip_report ids to delete")
    @NotEmpty(message = "ids must not be empty")
    private List<@NotBlank String> ids = new ArrayList<>();
}
