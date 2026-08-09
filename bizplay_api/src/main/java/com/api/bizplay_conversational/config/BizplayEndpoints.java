package com.api.bizplay_conversational.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * BizPlay cloud API endpoint PATH templates, centralized so the provider's API surface lives in one
 * place ({@code bizplay-endpoints.yml}) instead of being scattered as string literals in the
 * gateway. These are the contract paths only — the base URL, the {@code {productCode}} /
 * {@code {receiptProductCode}} segments (from {@link BizplayProperties}) and query strings are
 * applied by {@code BizplayGatewayServiceImple}. Path variables use {@code {name}} placeholders.
 *
 * <p>Values are bound from {@code bizplay-endpoints.yml} (via {@link YamlPropertySourceFactory});
 * the field defaults below duplicate them so the app still boots if the file is missing and give
 * the IDE something to navigate to. To re-point a path, edit the YAML — no code change needed.
 */
@Getter
@Setter
@Configuration
@PropertySource(
        value = "classpath:com/api/bizplay_conversational/config/bizplay-endpoints.yml",
        factory = YamlPropertySourceFactory.class)
@ConfigurationProperties(prefix = "bizplay.endpoints")
public class BizplayEndpoints {

    /** ① Purpose catalog: needs {corpUserId} and {paperKindType}. */
    private String purposeCatalog = "/api/v2/bstrPurpose/corporation-user/{corpUserId}/{paperKindType}";

    /** ② Paper definitions (untyped discovery): needs {purposeId}. */
    private String papers = "/api/v2/paper/purpose/{purposeId}";

    /** ② Paper definitions (typed variant the UI calls): needs {bstrType} and {purposeId}. */
    private String papersTyped = "/api/v2/paper/purpose/{bstrType}/{purposeId}";

    /** Corporation staff roster: needs {corporationId}. */
    private String corporationUsers = "/api/v2/popup/user/all/{corporationId}";

    /** One staff member's detail: needs {corporationUserId}. */
    private String user = "/api/v2/eacc-user/{corporationUserId}";

    /** ③ Plan draft save: needs {productCode}. */
    private String planDraft = "/api/v2/approval/{productCode}/bstr/plan/draft";

    /** ⑦ Settlement draft save — separate from the plan path: needs {productCode}. */
    private String settlementDraft = "/api/v2/approval/{productCode}/bstr/report/draft";

    /** ④ Plan list (settlement anchor search). Query string added in the gateway. */
    private String planList = "/api/v2/approval/bstr/plan/list";

    /** ⑤ One plan's detail: needs {approvalId}. */
    private String planDetail = "/api/v2/approval/bstr/{approvalId}";

    /** ⑥ Unattached receipt stream: needs {receiptProductCode}. Query string added in the gateway. */
    private String receiptStream = "/api/v2/receipt/{receiptProductCode}/not-attached/stream";

    /** ⑧ Manual expense create (기타카드 일괄 등록): POST array of EtcReceiptSaveRequest → receipt ids. */
    private String etcCard = "/api/v2/receipt/etc-card";

    /** ⑧ Receipt image upload (multipart): POST → UploadFileboxResponse[] (fileId). */
    private String fileboxUpload = "/api/v2/filebox/upload";

    /** ⑧ Issued receipt detail by receipt-id list: GET {ids} (comma-joined) → IssuedReceiptDto[]. */
    private String issuedBulk = "/api/v2/receipt/issued/bulk/{ids}";

    /** ⑧ Optional additional receipt detail (ReceiptEtcDto): PATCH {receiptId}. */
    private String receiptEtcDetail = "/api/v2/receipt-etc/{receiptId}";

    /** TranKind master (id → name/type): GET, no params. */
    private String trankindList = "/api/v2/trankind/list";

    /** Transport terminals (id, vehicleType, name): GET → TerminalDto[] for depart/arrival dropdowns. */
    private String etcCardTerminal = "/api/v2/receipt/etc-card/terminal";

    /** Personal-card general-expense browser: POST {startDate,endDate,approvalStatusTypeList,pageIndex,pageSize}. */
    private String generalExpense = "/api/v3/receipt/cloud/personal-card/my/general-expense";

    /** One receipt's full detail (used for NOT_ISSUED): GET {receiptId}. */
    private String receiptById = "/api/v2/receipt/{receiptId}";

    /** Attach uploaded files to a receipt: PATCH {receiptId} with body [fileId, …]. */
    private String receiptImage = "/api/v2/receipt/image/{receiptId}";
}
