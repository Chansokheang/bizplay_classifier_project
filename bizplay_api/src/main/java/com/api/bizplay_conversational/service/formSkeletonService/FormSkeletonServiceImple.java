package com.api.bizplay_conversational.service.formSkeletonService;

import com.api.bizplay_conversational.model.response.BizplayFormResponse;
import com.api.bizplay_conversational.model.response.BizplayFormResponse.FieldSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FormSkeletonServiceImple implements FormSkeletonService {

    private final ObjectMapper objectMapper;

    @Override
    public BizplayFormResponse buildPlanSkeleton(JsonNode papers, long purposeId, Long segmentId) {
        JsonNode paper = selectPlanPaper(papers);
        if (paper == null) {
            throw new IllegalArgumentException(
                    "No BSTR_PLAN paper found for purposeId=" + purposeId + ", segmentId=" + segmentId + ".");
        }

        List<FieldSpec> fields = extractFields(paper);
        ObjectNode document = buildDocument(paper, purposeId, segmentId);

        return BizplayFormResponse.builder()
                .paperId(paper.path("id").asLong())
                .paperName(paper.path("name").asText(null))
                .sectionTitle(extractSectionTitle(paper))
                .fields(fields)
                .document(document)
                .build();
    }

    /** First activated paper of kind BSTR_PLAN (plan creation must never pick a settlement paper). */
    private JsonNode selectPlanPaper(JsonNode papers) {
        if (papers == null || !papers.isArray()) {
            return null;
        }
        for (JsonNode p : papers) {
            if ("BSTR_PLAN".equals(p.path("paperKind").path("paperKindType").asText())) {
                return p;
            }
        }
        return null;
    }

    // --- field spec --------------------------------------------------------------

    private List<FieldSpec> extractFields(JsonNode paper) {
        List<FieldSpec> fields = new ArrayList<>();
        for (JsonNode entry : paper.path("paperItemOrderDto")) {
            if (!entry.path("used").asBoolean(false)) {
                continue; // corp turned this slot off
            }
            String kind = entry.path("paperItemType").asText();
            if ("BASIC_ITEM".equals(kind)) {
                String basicType = entry.path("basicItemType").asText();
                if (isServerGeneratedBasic(basicType)) {
                    continue; // paper no / drafter name / approval datetime: not user-fillable
                }
                fields.add(FieldSpec.builder()
                        .key("basic:" + basicType)
                        .label(basicLabel(basicType))
                        .type(basicType)
                        .required(entry.path("required").asBoolean(false))
                        .build());
            } else if ("ITEM".equals(kind)) {
                JsonNode item = entry.path("itemDto");
                List<String> options = new ArrayList<>();
                for (JsonNode opt : item.path("itemList")) {
                    String name = opt.path("name").asText(null);
                    if (name != null) {
                        options.add(name);
                    }
                }
                fields.add(FieldSpec.builder()
                        .key("item:" + item.path("id").asLong())
                        .label(item.path("name").asText(null))
                        .type(item.path("itemType").asText(null))
                        .required(entry.path("required").asBoolean(false)
                                || item.path("required").asBoolean(false))
                        .itemId(item.path("id").asLong())
                        .options(options.isEmpty() ? null : options)
                        .travelerItem(entry.path("travelerItemUsed").asBoolean(false))
                        .build());
            }
            // SECTION_ITEM (GROUP_TITLE / DIVIDER) is layout only — skipped.
        }
        // The trip period is a top-level pair in the save body; destination only when the form uses it.
        if (paper.path("bstrDestinationUsed").asBoolean(false)) {
            fields.add(FieldSpec.builder()
                    .key("basic:DESTINATION").label("출장지").type("DESTINATION").required(true).build());
        }
        return fields;
    }

    /** The custom-items group heading (GROUP_TITLE section value), e.g. "테스트"; null when blank. */
    private String extractSectionTitle(JsonNode paper) {
        for (JsonNode entry : paper.path("paperItemOrderDto")) {
            if ("GROUP_TITLE".equals(entry.path("basicItemType").asText())
                    && entry.path("used").asBoolean(false)) {
                String v = entry.path("value").asText("").trim();
                if (!v.isEmpty()) {
                    return v;
                }
            }
        }
        return null;
    }

    private boolean isServerGeneratedBasic(String basicType) {
        return "BASIC_PAPER_NO".equals(basicType)
                || "BASIC_DRAFT_USER_NAME".equals(basicType)
                || "BASIC_APPROVAL_DATETIME".equals(basicType)
                || "BASIC_BSTR_PURPOSE".equals(basicType)   // already chosen before the form loads
                || "BASIC_BSTR_EXCEPT".equals(basicType);   // policy-exception widget, server-driven
    }

    private String basicLabel(String basicType) {
        return switch (basicType) {
            case "BASIC_TITLE" -> "제목";
            case "BASIC_CONTENT" -> "내용";
            case "BASIC_TRAVELER" -> "출장자";
            default -> basicType;
        };
    }

    // --- skeleton document (one entry of the ③ POST body, all values empty) -------

    private ObjectNode buildDocument(JsonNode paper, long purposeId, Long segmentId) {
        ObjectNode d = objectMapper.createObjectNode();
        d.put("paperId", paper.path("id").asLong());
        d.putNull("title");
        d.putNull("content");
        d.set("files", d.arrayNode());
        d.set("approvalLines", d.arrayNode());
        d.set("referenceLines", d.arrayNode());
        d.set("issuedItems", buildIssuedItems(paper));
        d.putNull("bstrStartDate");
        d.putNull("bstrEndDate");
        d.put("bstrPurposeId", purposeId);
        if (segmentId != null) {
            d.put("bstrSegmentId", segmentId.longValue());
        } else {
            d.putNull("bstrSegmentId");
        }
        d.putNull("bstrType");
        d.set("bstrRoutes", d.arrayNode());
        d.set("bstrEdus", d.arrayNode());
        d.set("bstrCommutes", d.arrayNode());
        d.putNull("draftUserId");
        d.set("revokeCorpUserIds", d.arrayNode());
        d.set("inviteCorpUserIds", d.arrayNode());
        d.putNull("bstrPayType");
        // Exactly as the working capture: content must be an EMPTY STRING (null 500s the server)
        // and deleteRestriction is null.
        ObjectNode opinion = d.putObject("opinion");
        opinion.put("content", "");
        opinion.putNull("deleteRestriction");
        opinion.set("fileIds", opinion.arrayNode());
        d.set("externalLinks", d.arrayNode());
        d.putNull("agreeAutoRecalled");
        // Numbers, not strings — the working capture posts these as integers and the server's
        // deserializer rejects string values with a generic COMM_ERROR 500.
        d.put("orders", 0);
        d.put("bstrStatusType", "DRAFT_ONLY");
        for (String amount : new String[]{
                "dailyCostAmount", "roomCostAmount", "foodCostAmount", "lodgingCostAmount",
                "fuelCostAmount", "transportCostAmount", "tollCostAmount", "publicFixCostAmount",
                "incidentalCostAmount", "totalSettleAmount", "totalBstrAmount"}) {
            d.put(amount, 0);
        }
        return d;
    }

    /**
     * One issuedItems entry per used custom ITEM, echoing the item definition exactly as retrieved —
     * the save body sends the whole item object back — with empty value slots.
     */
    private ArrayNode buildIssuedItems(JsonNode paper) {
        ArrayNode issued = objectMapper.createArrayNode();
        for (JsonNode entry : paper.path("paperItemOrderDto")) {
            if (!"ITEM".equals(entry.path("paperItemType").asText()) || !entry.path("used").asBoolean(false)) {
                continue;
            }
            ObjectNode row = issued.addObject();
            row.set("item", entry.path("itemDto").deepCopy());
            row.putNull("value");
            row.putNull("value2");
            row.set("selections", row.arrayNode());
        }
        return issued;
    }
}
