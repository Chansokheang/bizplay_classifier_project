package com.api.bizplay_chatbot.corp.controller;

import com.api.bizplay_chatbot.common.dto.ApiResponse;
import com.api.bizplay_chatbot.corp.dto.CorpCreateRequest;
import com.api.bizplay_chatbot.corp.dto.CorpResponse;
import com.api.bizplay_chatbot.corp.dto.CorpUpdateRequest;
import com.api.bizplay_chatbot.corp.service.CorpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "Corporations",
        description = "Manage corp rows. corp_no is the natural business code used by bots as a soft "
                + "reference, and is the API path identifier here. To change a corp's corp_no, delete "
                + "and re-create.")
@RestController
@RequestMapping("/chatbot/api/v1/corps")
@RequiredArgsConstructor
public class CorpController {

    private final CorpService corpService;

    @Operation(summary = "Create a corporation",
            description = "Creates a corp with a unique corp_no. Returns 409 on collision, or 404 if "
                    + "corpGroupId does not exist (hard FK).")
    @PostMapping
    public ResponseEntity<ApiResponse<CorpResponse>> create(@Valid @RequestBody CorpCreateRequest req) {
        log.info("POST /chatbot/api/v1/corps - corp_no={}", req.getCorpNo());
        return ResponseEntity.ok(ApiResponse.ok(corpService.create(req)));
    }

    @Operation(summary = "List corporations",
            description = "Returns all corps, sorted by corp_no. Optional ?corpGroupId=N filter restricts "
                    + "to a single group.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<CorpResponse>>> list(
            @Parameter(description = "Optional corp_group_id filter") @RequestParam(required = false) Long corpGroupId) {
        return ResponseEntity.ok(ApiResponse.ok(corpService.list(corpGroupId)));
    }

    @Operation(summary = "Get a corporation by corp_no")
    @GetMapping("/{corpNo}")
    public ResponseEntity<ApiResponse<CorpResponse>> get(
            @Parameter(description = "Natural business code (corp_no)") @PathVariable String corpNo) {
        return ResponseEntity.ok(ApiResponse.ok(corpService.get(corpNo)));
    }

    @Operation(summary = "Update a corporation",
            description = "PATCH semantics: omitted fields stay unchanged. corp_no is immutable — to change "
                    + "it, delete and re-create. Returns 404 if corpGroupId points at a non-existent group.")
    @PutMapping("/{corpNo}")
    public ResponseEntity<ApiResponse<CorpResponse>> update(
            @Parameter(description = "Natural business code (corp_no)") @PathVariable String corpNo,
            @Valid @RequestBody CorpUpdateRequest req) {
        log.info("PUT /chatbot/api/v1/corps/{}", corpNo);
        return ResponseEntity.ok(ApiResponse.ok(corpService.update(corpNo, req)));
    }

    @Operation(summary = "Delete a corporation",
            description = "Removes the corp row. Bots that reference this corp_no keep their soft reference "
                    + "(it's intentional that dangling soft refs are tolerated).")
    @DeleteMapping("/{corpNo}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "Natural business code (corp_no)") @PathVariable String corpNo) {
        log.info("DELETE /chatbot/api/v1/corps/{}", corpNo);
        corpService.delete(corpNo);
        return ResponseEntity.ok(ApiResponse.ok("Corporation deleted", null));
    }
}
