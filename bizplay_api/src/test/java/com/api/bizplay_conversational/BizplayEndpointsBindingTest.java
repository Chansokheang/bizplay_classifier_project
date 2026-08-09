package com.api.bizplay_conversational;

import com.api.bizplay_conversational.config.BizplayEndpoints;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves {@code bizplay-endpoints.yml} is genuinely loaded and bound into {@link BizplayEndpoints}.
 * Binding here reads ONLY the YAML property source (never the Java field defaults), so a wrong
 * resource path, a mis-keyed entry, or a broken kebab→camel mapping fails the assertions rather
 * than silently falling back to the in-code defaults.
 */
class BizplayEndpointsBindingTest {

    @Test
    void yamlBindsEveryEndpointTemplate() throws java.io.IOException {
        ClassPathResource resource =
                new ClassPathResource("com/api/bizplay_conversational/config/bizplay-endpoints.yml");
        List<org.springframework.core.env.PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("test-bizplay-endpoints", resource);
        MutablePropertySources propertySources = new MutablePropertySources();
        sources.forEach(propertySources::addLast);

        BizplayEndpoints ep = new Binder(ConfigurationPropertySources.from(propertySources))
                .bind("bizplay.endpoints", BizplayEndpoints.class).get();

        assertEquals("/api/v2/bstrPurpose/corporation-user/{corpUserId}/{paperKindType}", ep.getPurposeCatalog());
        assertEquals("/api/v2/paper/purpose/{purposeId}", ep.getPapers());
        assertEquals("/api/v2/paper/purpose/{bstrType}/{purposeId}", ep.getPapersTyped());
        assertEquals("/api/v2/popup/user/all/{corporationId}", ep.getCorporationUsers());
        assertEquals("/api/v2/eacc-user/{corporationUserId}", ep.getUser());
        assertEquals("/api/v2/approval/{productCode}/bstr/plan/draft", ep.getPlanDraft());
        assertEquals("/api/v2/approval/{productCode}/bstr/report/draft", ep.getSettlementDraft());
        assertEquals("/api/v2/approval/bstr/plan/list", ep.getPlanList());
        assertEquals("/api/v2/approval/bstr/{approvalId}", ep.getPlanDetail());
        assertEquals("/api/v2/receipt/{receiptProductCode}/not-attached/stream", ep.getReceiptStream());
        assertEquals("/api/v2/receipt/etc-card", ep.getEtcCard());
        assertEquals("/api/v2/filebox/upload", ep.getFileboxUpload());
        assertEquals("/api/v2/receipt/issued/bulk/{ids}", ep.getIssuedBulk());
        assertEquals("/api/v2/receipt-etc/{receiptId}", ep.getReceiptEtcDetail());
        assertEquals("/api/v2/trankind/list", ep.getTrankindList());
    }
}
