package com.api.bizplay_conversational.model.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ExpenseSectionDraft {

    @JsonProperty("Type")
    private String type;

    @JsonProperty("Attachemnt")
    @JsonAlias({"Attachment", "Attachments"})
    private List<AttachmentDraft> attachments = new ArrayList<>();

    @JsonProperty("Detail")
    private List<ExpenseDetailDraft> details = new ArrayList<>();

    public ExpenseSectionDraft(String type) {
        this.type = type;
    }
}
