package com.api.bizplay_compliance.controller;

import com.api.bizplay_compliance.model.request.*;
import com.api.bizplay_compliance.model.response.ApiResponse;
import com.api.bizplay_compliance.model.response.RuleCheckResponse;
import com.api.bizplay_compliance.service.ruleService.RuleEngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@AllArgsConstructor
@Log4j2
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001", "http://10.255.78.89:9009", "http://203.255.78.89:9009"})
@RequestMapping("/compliance/api/rule-engine")
@Tag(
        name = "Rule Engine",
        description = "Compliance rule test endpoints for validating individual rules and the full rule pipeline."
)
public class RuleEngineController {

    private final RuleEngineService ruleEngineService;

    @PostMapping("/r01")
    @Operation(
            summary = "R01 - Split payment detection",
            description = "Stores the submitted amount in a short-lived in-memory test cache and checks whether repeated amounts near the 50,000 threshold indicate a split-payment pattern."
    )
    public ResponseEntity<ApiResponse<RuleCheckResponse>> checkR01(@RequestBody SplitPaymentCheckRequest request) {
        return ResponseEntity.ok(
                ApiResponse.<RuleCheckResponse>builder()
                        .payload(toResponse(ruleEngineService.testSplitPaymentAmount(request.amount())))
                        .message("Run R01 successfully.")
                        .code(HttpStatus.OK.value())
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @PostMapping("/r02")
    @Operation(
            summary = "R02 - Nighttime transaction",
            description = "Checks whether the transaction time falls in the restricted nighttime window."
    )
    public ResponseEntity<ApiResponse<RuleCheckResponse>> checkR02(@RequestBody NighttimeTransactionCheckRequest request) {
        return ResponseEntity.ok(
                ApiResponse.<RuleCheckResponse>builder()
                        .payload(toResponse(ruleEngineService.checkNighttimeTransaction(request.transactionDate())))
                        .message("Run R02 successfully.")
                        .code(HttpStatus.OK.value())
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @PostMapping("/r03")
    @Operation(
            summary = "R03 - Limit exceed",
            description = "Checks whether the transaction amount exceeds a temporary dummy limit by category for endpoint testing."
    )
    public ResponseEntity<ApiResponse<RuleCheckResponse>> checkR03(
            @RequestParam Integer amount,
            @RequestParam(required = false) String category
    ) {
        return ResponseEntity.ok(
                ApiResponse.<RuleCheckResponse>builder()
                        .payload(toResponse(ruleEngineService.checkLimitExceedAmount(
                                amount,
                                category
                        )))
                        .message("Run R03 successfully.")
                        .code(HttpStatus.OK.value())
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @PostMapping("/r04")
    @Operation(
            summary = "R04 - MCC prohibited",
            description = "Checks whether the merchant category code (MCC) is included in the blocked MCC list. If no blocked list is provided, a dummy test list is used."
    )
    public ResponseEntity<ApiResponse<RuleCheckResponse>> checkR04(
            @Parameter(description = "MCC code to check.", example = "5812")
            @RequestParam String mccCode,
            @Parameter(
                    description = "Blocked MCC list. Optional. Defaults to dummy test MCCs: 5812, 7995, 6012.",
                    example = "5812"
            )
            @RequestParam(required = false, defaultValue = "5812,7995,6012") List<String> blockedMccCodes
    ) {
        return ResponseEntity.ok(
                ApiResponse.<RuleCheckResponse>builder()
                        .payload(toResponse(ruleEngineService.checkProhibitedMcc(
                                mccCode,
                                blockedMccCodes
                        )))
                        .message("Run R04 successfully.")
                        .code(HttpStatus.OK.value())
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @PostMapping(value = "/r05", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "R05 - Duplicate receipt",
            description = "Uploads a receipt image, extracts its receipt number with the Gemma endpoint, and checks whether it matches the provided receipt number."
    )
    public ResponseEntity<ApiResponse<RuleCheckResponse>> checkR05(
            @RequestParam String receiptNumber,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(
                ApiResponse.<RuleCheckResponse>builder()
                        .payload(toResponse(ruleEngineService.checkDuplicateReceiptUpload(
                                receiptNumber,
                                file
                        )))
                        .message("Run R05 successfully.")
                        .code(HttpStatus.OK.value())
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @PostMapping(value = "/r06", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "R06 - Card mismatch",
            description = "Uploads a receipt image, extracts the visible masked card pattern with the Gemma endpoint, and checks whether it matches the provided card number."
    )
    public ResponseEntity<ApiResponse<RuleCheckResponse>> checkR06(
            @RequestParam String cardNumber,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(
                ApiResponse.<RuleCheckResponse>builder()
                        .payload(toResponse(ruleEngineService.checkCardMismatchUpload(
                                cardNumber,
                                file
                        )))
                        .message("Run R06 successfully.")
                        .code(HttpStatus.OK.value())
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @PostMapping("/r07")
    @Operation(
            summary = "R07 - Business registration check",
            description = "Looks up a business registration number with the NTS businessman status API and returns the resolved business status data as the endpoint payload."
    )
    public ResponseEntity<ApiResponse<com.api.bizplay_compliance.service.corpService.BusinessStatusLookupService.BusinessStatus>> checkR07(
            @RequestBody BusinessRegistrationCheckRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.<com.api.bizplay_compliance.service.corpService.BusinessStatusLookupService.BusinessStatus>builder()
                        .payload(ruleEngineService.lookupBusinessRegistration(request.businessNumber()))
                        .message("Run R07 successfully.")
                        .code(HttpStatus.OK.value())
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @PostMapping("/r08")
    @Operation(
            summary = "R08 - Location anomaly",
            description = "Geocodes a single address with the Naver geocode API and returns the resolved location data as the endpoint payload."
    )
    public ResponseEntity<ApiResponse<com.api.bizplay_compliance.service.corpService.LocationGeocodeService.GeocodedLocation>> checkR08(
            @RequestBody LocationAnomalyCheckRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.<com.api.bizplay_compliance.service.corpService.LocationGeocodeService.GeocodedLocation>builder()
                        .payload(ruleEngineService.lookupAddress(request.address()))
                        .message("Run R08 successfully.")
                        .code(HttpStatus.OK.value())
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @PostMapping("/r09")
    @Operation(
            summary = "R09 - Holiday use",
            description = "Returns a holiday-style payload when the date is a public holiday or weekend. Otherwise returns a message that the date is not a holiday."
    )
    public ResponseEntity<ApiResponse<com.api.bizplay_compliance.service.corpService.HolidayInfoLookupService.HolidayInfo>> checkR09(@RequestBody HolidayUseCheckRequest request) {
        var holidayInfo = ruleEngineService.lookupHolidayUse(request.transactionDate());
        return ResponseEntity.ok(
                ApiResponse.<com.api.bizplay_compliance.service.corpService.HolidayInfoLookupService.HolidayInfo>builder()
                        .payload(holidayInfo.orElse(null))
                        .message(holidayInfo.isPresent() ? "Run R09 successfully." : "It is not a holiday.")
                        .code(HttpStatus.OK.value())
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @PostMapping("/r10")
    @Operation(
            summary = "R10 - Requisition mismatch",
            description = "Reserved for checking whether the transaction is inconsistent with related purchase requisition or approval data."
    )
    public ResponseEntity<ApiResponse<RuleCheckResponse>> checkR10(@RequestBody RequisitionMismatchCheckRequest request) {
        return ResponseEntity.ok(
                ApiResponse.<RuleCheckResponse>builder()
                        .payload(toResponse(ruleEngineService.checkRequisitionMismatch(
                                request.requisitionId(),
                                request.transactionReference()
                        )))
                        .message("Run R10 successfully.")
                        .code(HttpStatus.OK.value())
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    @PostMapping(value = "/run-all", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Run all rules",
            description = "Runs the flat rule pipeline for one transaction input and returns every detected rule result. Send a JSON `payload` part and optionally a receipt image `file` part for R05 and R06."
    )
    public ResponseEntity<ApiResponse<List<RuleCheckResponse>>> runAllRules(
            @Parameter(
                    description = "JSON payload part for R01-R10.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RuleEngineService.PipelineRequest.class),
                            examples = @ExampleObject(
                                    name = "RunAllPayload",
                                    value = """
                                            {
                                              "id": "tx-2026-0512-001",
                                              "employeeId": "EMP-1001",
                                              "merchantId": "3150140032",
                                              "transactionDate": "2026-05-12T12:46:52",
                                              "amount": 70000,
                                              "category": "MEAL",
                                              "mccCode": "5812",
                                              "receiptNumber": "03712301",
                                              "cardNumber": "5327501212342536",
                                              "receiptCardNumber": "5327-50**-****-2536",
                                              "businessNumber": "3158300467",
                                              "address": "충북 청주시 흥덕구 복대동 1657",
                                              "requisitionId": "REQ-2026-0512-001",
                                              "transactionReference": "03712301"
                                            }
                                            """
                            )
                    )
            )
            @RequestPart("payload") RuleEngineService.PipelineRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        return ResponseEntity.ok(
                ApiResponse.<List<RuleCheckResponse>>builder()
                        .payload(ruleEngineService.runPipeline(request, file).stream().map(this::toResponse).toList())
                        .message("Run all rules successfully.")
                        .code(HttpStatus.OK.value())
                        .status(HttpStatus.OK)
                        .build()
        );
    }

    private RuleCheckResponse toResponse(RuleEngineService.RuleResult result) {
        if (result == null) {
            return null;
        }

        return new RuleCheckResponse(
                result.ruleId(),
                result.ruleName(),
                result.detail()
        );
    }
}
