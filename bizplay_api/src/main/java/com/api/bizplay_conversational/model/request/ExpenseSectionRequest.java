package com.api.bizplay_conversational.model.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ExpenseSectionRequest {

    @JsonProperty("Type")
    @Size(max = 100)
    private String type;

    @JsonProperty("Attachemnt")
    @JsonAlias({"Attachment", "Attachments"})
    @Valid
    private List<PlanAttachmentRequest> attachments = new ArrayList<>();

    @JsonProperty("Detail")
    @Valid
    private List<ExpenseDetailRequest> details = new ArrayList<>();
}
