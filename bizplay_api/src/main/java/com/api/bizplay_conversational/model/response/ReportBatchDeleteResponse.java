package com.api.bizplay_conversational.model.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Result of a batch report-line delete: how many were requested vs actually removed. */
@Getter
@Builder
public class ReportBatchDeleteResponse {
    private int requested;
    private int deleted;
    /** Ids that existed and were deleted (with their linked expense + attachments). */
    private List<String> deletedIds;
    /** Requested ids that did not exist. */
    private List<String> notFoundIds;
}
