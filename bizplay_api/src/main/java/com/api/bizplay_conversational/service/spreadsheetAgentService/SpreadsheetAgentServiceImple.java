package com.api.bizplay_conversational.service.spreadsheetAgentService;

import com.api.bizplay_conversational.model.response.SpreadsheetAnalysisResult;
import com.api.bizplay_conversational.model.response.StaffLookupResult;
import com.api.bizplay_conversational.service.staffService.StaffService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import com.api.bizplay_conversational.service.llmSettingsService.LlmSettingsService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpreadsheetAgentServiceImple implements SpreadsheetAgentService {

    /** How many leading rows (incl. header) to show the LLM for column inference. */
    private static final int SAMPLE_ROWS = 6;
    /** Safety cap on data rows processed. */
    private static final int MAX_DATA_ROWS = 500;

    private final Map<String, ChatClient> chatClientRegistry;
    private final LlmSettingsService llmSettingsService;
    private final StaffService staffService;
    private final ObjectMapper objectMapper;

    @Value("${app.conversational.spreadsheet-agent.model:qwen3-14b}")
    private String modelName;

    @Override
    public SpreadsheetAnalysisResult analyze(String corpNo, byte[] content, String filename) {
        List<String> warnings = new ArrayList<>();
        log.info("Spreadsheet agent analyzing file={} ({} bytes) for corpNo={}",
                filename, content == null ? 0 : content.length, corpNo);

        List<List<String>> rows = readRows(content, warnings);
        if (rows.isEmpty()) {
            warnings.add("The spreadsheet had no readable rows.");
            return SpreadsheetAnalysisResult.builder()
                    .columnMapping(Map.of())
                    .totalRows(0)
                    .warnings(warnings)
                    .build();
        }

        // Infer which columns hold name / department / position from a small sample.
        Map<String, Integer> mapping = inferColumnMapping(rows, warnings);
        Integer nameCol = mapping.get("name");
        if (nameCol == null) {
            warnings.add("Could not identify a staff-name column; no rows were resolved.");
            return SpreadsheetAnalysisResult.builder()
                    .columnMapping(mapping)
                    .totalRows(0)
                    .warnings(warnings)
                    .build();
        }

        Integer deptCol = mapping.get("department");
        Integer posCol = mapping.get("position");
        int headerRow = mapping.getOrDefault("headerRow", 0);

        List<SpreadsheetAnalysisResult.ResolvedStaff> matched = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        int processed = 0;

        for (int i = headerRow + 1; i < rows.size(); i++) {
            if (processed >= MAX_DATA_ROWS) {
                warnings.add("Row limit (" + MAX_DATA_ROWS + ") reached; remaining rows were skipped.");
                break;
            }
            List<String> row = rows.get(i);
            String name = cell(row, nameCol);
            if (name == null || name.isBlank()) {
                continue; // skip blank / non-data rows
            }
            processed++;

            StaffLookupResult result = staffService.lookup(corpNo, name.trim());
            boolean isMatched = result != null && result.isMatched();
            if (isMatched) {
                matched.add(SpreadsheetAnalysisResult.ResolvedStaff.builder()
                        .rowNumber(i + 1)
                        .extractedName(name.trim())
                        .matched(true)
                        .staffId(result.getStaffId() == null ? null : result.getStaffId().toString())
                        .staffName(result.getStaffName())
                        .departmentName(result.getDepartmentName())
                        .position(result.getPosition())
                        .matchType(result.getMatchType())
                        .build());
            } else {
                // Fall back to the sheet's own department/position so the row is still usable.
                matched.add(SpreadsheetAnalysisResult.ResolvedStaff.builder()
                        .rowNumber(i + 1)
                        .extractedName(name.trim())
                        .matched(false)
                        .staffName(name.trim())
                        .departmentName(deptCol == null ? null : emptyToNull(cell(row, deptCol)))
                        .position(posCol == null ? null : emptyToNull(cell(row, posCol)))
                        .matchType("NONE")
                        .build());
                unmatched.add(name.trim());
            }
        }

        return SpreadsheetAnalysisResult.builder()
                .columnMapping(mapping)
                .totalRows(processed)
                .matched(matched)
                .unmatched(unmatched)
                .warnings(warnings)
                .build();
    }

    // --- POI parsing -------------------------------------------------------------

    private List<List<String>> readRows(byte[] content, List<String> warnings) {
        List<List<String>> rows = new ArrayList<>();
        if (content == null || content.length == 0) {
            warnings.add("No file was uploaded.");
            return rows;
        }
        try (InputStream in = new ByteArrayInputStream(content);
             Workbook workbook = WorkbookFactory.create(in)) {
            if (workbook.getNumberOfSheets() > 1) {
                warnings.add("Workbook has " + workbook.getNumberOfSheets()
                        + " sheets; only the first was read.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            int lastRow = sheet.getLastRowNum();
            for (int r = 0; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    int lastCol = row.getLastCellNum();
                    for (int c = 0; c < lastCol; c++) {
                        Cell cell = row.getCell(c);
                        cells.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
                    }
                }
                rows.add(cells);
            }
        } catch (Exception e) {
            log.warn("Failed to read spreadsheet: {}", e.getMessage(), e);
            warnings.add("Could not read the spreadsheet: " + e.getMessage());
        }
        return rows;
    }

    // --- LLM column inference ----------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Integer> inferColumnMapping(List<List<String>> rows, List<String> warnings) {
        Map<String, Integer> fallback = headerKeywordMapping(rows);
        ChatClient client = chatClientRegistry.get(llmSettingsService.resolve(modelName));
        if (client == null) {
            warnings.add("LLM model not configured; used keyword header matching.");
            return fallback;
        }

        String sample = buildSampleText(rows);
        try {
            String content = client.prompt()
                    .system("""
                            You are given the first rows of a spreadsheet containing staff for a business trip.
                            The header names are unknown and may be in any language.
                            Identify the ZERO-BASED column index for each field and which row is the header.
                            Return ONLY JSON, no prose, no markdown:
                            {"headerRow": int, "name": int|null, "department": int|null, "position": int|null}
                            - name: the column with a person's name (required if present).
                            - department / position: null if not present.
                            - headerRow: index of the header row (usually 0).
                            Use null when a field is absent. Output only the JSON object.
                            /no_think
                            """)
                    .user(sample)
                    .call()
                    .content();
            log.info("Spreadsheet agent column-mapping raw output={}", content);

            String json = extractJson(stripThink(content));
            if (json == null) {
                warnings.add("LLM produced no column mapping; used keyword header matching.");
                return fallback;
            }
            JsonNode node = objectMapper.readTree(json);
            Map<String, Integer> mapping = new LinkedHashMap<>();
            putIfInt(mapping, "headerRow", node.get("headerRow"), 0);
            putIfInt(mapping, "name", node.get("name"), null);
            putIfInt(mapping, "department", node.get("department"), null);
            putIfInt(mapping, "position", node.get("position"), null);
            if (!mapping.containsKey("name")) {
                warnings.add("LLM did not identify a name column; used keyword header matching.");
                return fallback;
            }
            return mapping;
        } catch (Exception e) {
            log.warn("Column inference failed, using keyword fallback: {}", e.getMessage());
            warnings.add("Column inference failed; used keyword header matching.");
            return fallback;
        }
    }

    private void putIfInt(Map<String, Integer> map, String key, JsonNode value, Integer dflt) {
        if (value != null && value.isInt()) {
            map.put(key, value.asInt());
        } else if (dflt != null) {
            map.put(key, dflt);
        }
    }

    /** Deterministic fallback: scan the first row for known header keywords (EN + KR). */
    private Map<String, Integer> headerKeywordMapping(List<List<String>> rows) {
        Map<String, Integer> mapping = new LinkedHashMap<>();
        mapping.put("headerRow", 0);
        if (rows.isEmpty()) {
            return mapping;
        }
        List<String> header = rows.get(0);
        for (int c = 0; c < header.size(); c++) {
            String h = header.get(c).toLowerCase();
            if (!mapping.containsKey("name")
                    && (h.contains("name") || h.contains("이름") || h.contains("성명") || h.contains("staff") || h.contains("employee"))) {
                mapping.put("name", c);
            } else if (!mapping.containsKey("department")
                    && (h.contains("dept") || h.contains("department") || h.contains("부서") || h.contains("팀") || h.contains("team"))) {
                mapping.put("department", c);
            } else if (!mapping.containsKey("position")
                    && (h.contains("position") || h.contains("title") || h.contains("직급") || h.contains("직책") || h.contains("role"))) {
                mapping.put("position", c);
            }
        }
        return mapping;
    }

    private String buildSampleText(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder("Spreadsheet sample (row index : cells):\n");
        int limit = Math.min(SAMPLE_ROWS, rows.size());
        for (int r = 0; r < limit; r++) {
            sb.append(r).append(" : ").append(String.join(" | ", rows.get(r))).append("\n");
        }
        return sb.toString();
    }

    // --- small helpers -----------------------------------------------------------

    private String cell(List<String> row, Integer col) {
        if (row == null || col == null || col < 0 || col >= row.size()) {
            return null;
        }
        return row.get(col);
    }

    private String emptyToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private String stripThink(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("(?is)<think>.*?</think>", "");
        cleaned = cleaned.replaceAll("(?is)</?think>", "");
        return cleaned.trim();
    }

    private String extractJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String cleaned = text.replace("```json", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end < start) {
            return null;
        }
        return cleaned.substring(start, end + 1);
    }
}
