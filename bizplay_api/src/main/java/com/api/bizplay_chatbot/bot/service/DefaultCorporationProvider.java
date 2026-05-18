package com.api.bizplay_chatbot.bot.service;

import com.api.bizplay_chatbot.domain.entity.Corporation;
import com.api.bizplay_chatbot.domain.repository.CorporationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves "the default tenant" for callers that omit {@code corpNo} on bot
 * create. Until real auth lands, this falls back to a single configured
 * corporation seeded by V1 (corp_no = 'DEFAULT'). Replace this component (or
 * its callers) when tenant identification moves to a token/header.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultCorporationProvider {

    private final CorporationRepository corporationRepository;

    /** Natural business code of the default corp row seeded by V1. Override
     *  via the {@code DEFAULT_CORP_NO} env var if you re-seed differently. */
    @Value("${app.tenant.default-corp-no:DEFAULT}")
    private String defaultCorpNo;

    /**
     * Return the default corporation. Throws if the configured corp_no row is
     * missing — usually a sign that V1 didn't run or the seed was overridden,
     * so callers can surface the problem loudly rather than silently degrade.
     */
    public Corporation current() {
        return corporationRepository.findByCorpNo(defaultCorpNo).orElseThrow(() ->
                new IllegalStateException("Default corp row not found: corp_no=" + defaultCorpNo
                        + ". Verify the V1 Flyway migration ran on this database."));
    }

    /** Convenience for places that only need the natural code (e.g. as the
     *  default value for {@code Bot.corpNo} when callers omit it). */
    public String currentNo() {
        return defaultCorpNo;
    }
}
