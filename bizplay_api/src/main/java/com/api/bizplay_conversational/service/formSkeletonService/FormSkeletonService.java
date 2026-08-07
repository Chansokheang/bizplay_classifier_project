package com.api.bizplay_conversational.service.formSkeletonService;

import com.api.bizplay_conversational.model.response.BizplayFormResponse;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Deterministic "Form Builder": turns a BizPlay paper definition (②) into a save-ready plan-draft
 * skeleton shaped exactly like one document of the ③ POST body, plus the field spec that drives
 * filling / validation / follow-up questions. No LLM involved — the structure comes 1:1 from the
 * retrieved form, so {@code issuedItems} is exactly as dynamic as the corp's configuration.
 */
public interface FormSkeletonService {

    /**
     * Build the skeleton for the FIRST paper in {@code papers} whose paperKind.paperKindType is
     * BSTR_PLAN. Throws IllegalArgumentException when none exists.
     *
     * @param papers    the ② response (array of papers)
     * @param purposeId chosen bstrPurposeId
     * @param segmentId chosen segmentId (nullable)
     */
    BizplayFormResponse buildPlanSkeleton(JsonNode papers, long purposeId, Long segmentId);

    /**
     * Build the settlement (출장정산서) skeleton from the FIRST paper in {@code papers} whose
     * paperKind.paperKindType is EXPENSE_REPORT. The document is shaped exactly like one entry of
     * the captured 정산서 request body — same keys, same order, evidence arrays empty — and its
     * {@code issuedItems} carry the SLIM item echo ({itemType, id, name}) that body uses.
     * Throws IllegalArgumentException when the purpose has no settlement paper.
     *
     * @param papers    the ② response (array of papers)
     * @param purposeId the settled trip's bstrPurposeId
     * @param segmentId the settled trip's bstrSegmentId (nullable)
     */
    BizplayFormResponse buildSettlementSkeleton(JsonNode papers, long purposeId, Long segmentId);
}
