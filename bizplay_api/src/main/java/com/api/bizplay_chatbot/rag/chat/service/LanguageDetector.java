package com.api.bizplay_chatbot.rag.chat.service;

import org.springframework.stereotype.Component;

/**
 * Lightweight language detector for chat messages. Returns a BCP-47-ish
 * two-letter code persisted on {@code chat_messages.lang} and aggregated by
 * the analytics dashboard's "Distribution of languages used" widget.
 *
 * <p><b>Algorithm — majority vote.</b> Counts Hangul code points vs all other
 * letters; the side with the higher count wins. Numbers, punctuation, and
 * whitespace are ignored on both sides. This handles the bilingual cases the
 * bot routinely produces — e.g. an English RAG answer that quotes Korean
 * policy phrases stays classified as English, because the English content
 * dominates. The earlier "any Hangul → ko" rule mis-classified those rows.
 *
 * <p>Ties (and pure-non-letter messages) resolve to {@code "en"}; pure
 * blank/null input returns {@code null} so the persistence layer can record
 * "unknown" rather than guessing.
 *
 * <p>If support for more languages lands later, swap the implementation for
 * {@code optimaize/language-detector} or Apache Tika's {@code LanguageDetector}
 * without changing this class's signature — the call site only sees the
 * two-letter return code.
 */
@Component
public class LanguageDetector {

    /** BCP-47-ish code: {@code "ko"} or {@code "en"}, or {@code null} for blank. */
    public String detect(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int hangul = 0;
        int otherLetters = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (isHangul(cp)) {
                hangul++;
            } else if (Character.isLetter(cp)) {
                // Latin / Cyrillic / Greek / etc. — anything non-Hangul that
                // would shift the message *away* from Korean.
                otherLetters++;
            }
            i += Character.charCount(cp);
        }
        if (hangul == 0 && otherLetters == 0) {
            // Pure numbers / punctuation / whitespace — no language to claim.
            return null;
        }
        return hangul > otherLetters ? "ko" : "en";
    }

    private static boolean isHangul(int cp) {
        return (cp >= 0xAC00 && cp <= 0xD7A3)   // Hangul Syllables
            || (cp >= 0x1100 && cp <= 0x11FF)   // Hangul Jamo
            || (cp >= 0x3130 && cp <= 0x318F);  // Hangul Compatibility Jamo
    }
}
