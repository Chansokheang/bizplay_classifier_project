package com.api.bizplay_conversational.model.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ReportSectionDraft {

    @JsonProperty("SectionCode")
    private String sectionCode;

    @JsonProperty("TransportationExpense")
    private TransportationExpenseDraft transportationExpense;

    @JsonProperty("CostExpense")
    private CostExpenseDraft costExpense;

    @JsonProperty("ExcessReason")
    private String excessReason;

    @JsonProperty("Briefs")
    private String briefs;

    @JsonProperty("Note")
    private String note;

    @JsonProperty("ApprovalNumber")
    private String approvalNumber;

    @JsonProperty("Attachment")
    private List<AttachmentDraft> attachments = new ArrayList<>();
}
