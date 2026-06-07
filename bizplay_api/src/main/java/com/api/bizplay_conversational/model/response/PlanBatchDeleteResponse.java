package com.api.bizplay_conversational.model.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Result of a batch plan delete: how many were requested vs actually removed. */
@Getter
@Builder
public class PlanBatchDeleteResponse {
    private int requested;
    private int deleted;
    /** Ids that existed and were deleted (with their travelers + attachments). */
    private List<String> deletedIds;
    /** Requested ids that did not exist (nothing to delete). */
    private List<String> notFoundIds;
}
