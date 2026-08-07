package com.api.bizplay_conversational.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * BizPlay cloud integration (form-driven trip plans). The company's API is IP-allow-listed, so it is
 * reachable from the lab machines / the deploy server only. Auth is the END USER's Bearer token,
 * passed per request from the UI ({@code X-Bizplay-Token} header); {@code devToken} is an optional
 * local-dev fallback so the flow can be exercised without the UI.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.bizplay")
public class BizplayProperties {

    /** e.g. https://cloud-dev.bizplay.biz */
    private String baseUrl = "https://cloud-dev.bizplay.biz";

    /** Optional dev-only fallback token used when the request doesn't carry X-Bizplay-Token. */
    private String devToken = "";

    /** Cache TTL (seconds) for purpose catalogs and paper definitions. */
    private long cacheTtlSeconds = 300;

    /**
     * Default DRAFTER (BizPlay corporationUserId) used when a request omits corpUserId — the demo
     * runs as one static user. In production the caller passes the logged-in user's id; this is
     * only the fallback so the flow works without a session. Matches the dev token's
     * currentCorpUserId so the API identity and the document's draftUserId agree.
     */
    private String defaultCorpUserId = "30447";

    /** Product/tenant segment of the draft-save path: /api/v2/approval/{productCode}/bstr/plan/draft. */
    private String productCode = "seah";

    /**
     * Product segment of the receipt endpoints: /api/v2/receipt/{receiptProductCode}/….
     * Independent from {@link #productCode} — on cloud-dev the draft save rides "bics"
     * while the receipt stream only exists under "seah" (captured from the real UI).
     */
    private String receiptProductCode = "seah";
}
