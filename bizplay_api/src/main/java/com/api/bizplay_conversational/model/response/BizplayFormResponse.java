package com.api.bizplay_conversational.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * The dynamic form for a chosen purpose/segment: a field spec (drives filling, validation and
 * follow-up questions) plus a save-ready skeleton {@code document} shaped exactly like one entry of
 * the BizPlay plan-draft POST body (③) with all values empty. The skeleton's {@code issuedItems}
 * mirror the paper's custom items, so its shape is as dynamic as the retrieved form.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BizplayFormResponse {

    private long paperId;
    private String paperName;
    /** Title of the custom-items group section (paperItemOrderDto GROUP_TITLE value), may be blank. */
    private String sectionTitle;
    private List<FieldSpec> fields;
    private JsonNode document;

    /** One fillable slot of the dynamic form. */
    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldSpec {
        /** Stable key: "basic:BASIC_TITLE" for basics, "item:11505" for custom items. */
        private String key;
        /** Form label as configured by the corp (often Korean), e.g. "출장기간". */
        private String label;
        /** BASIC_* type or the custom itemType (BSTR_PERIOD, COST_CENTER, HTML, ...). */
        private String type;
        private boolean required;
        /** Custom-item id (null for basics); matches issuedItems[].item.id in the skeleton. */
        private Long itemId;
        /** Options for select-like items (name shown to user; erpCode kept for the payload). */
        private List<String> options;
        /**
         * BizPlay placement rule (paperItemOrderDto.travelerItemUsed): {@code true} = the item
         * renders INSIDE EACH TRAVELER card (one value per traveler); {@code false} = it renders
         * once at form level in the custom-items section.
         */
        private boolean travelerItem;
    }
}
