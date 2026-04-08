package com.api.bizplay_classifier_api.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileTransactionsPageResponse {
    private UUID fileId;
    private int page;
    private int limit;
    private int totalRows;
    private int totalPages;
    private List<Map<String, String>> items;
}
