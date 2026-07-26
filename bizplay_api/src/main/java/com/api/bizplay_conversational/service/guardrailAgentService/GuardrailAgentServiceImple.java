package com.api.bizplay_conversational.service.guardrailAgentService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Deterministic guardrail — no LLM call, so it cannot be jailbroken, adds no latency and
 * behaves identically on every model. Legitimate FORM edits ("제목을 수정해줘", "update the
 * title") stay allowed: the DB-mutation rule fires only when a mutation verb appears
 * TOGETHER with an explicit database target (table / DB / SQL / query / record …) or as a
 * raw SQL statement.
 */
@Slf4j
@Service
public class GuardrailAgentServiceImple implements GuardrailAgentService {

    private static final int MAX_MESSAGE_LENGTH = 4000;

    /** Raw SQL mutation statements pasted straight into chat. */
    private static final Pattern RAW_SQL = Pattern.compile(
            "(?is)\\b(insert\\s+into|delete\\s+from|drop\\s+(table|database|schema|index)|"
                    + "truncate(\\s+table)?|alter\\s+table|create\\s+(table|database|schema)|"
                    + "update\\s+\\w+\\s+set|grant\\s+|revoke\\s+)\\b.*");

    /** An explicit database-ish target word (EN + KO). */
    private static final Pattern DB_TARGET = Pattern.compile(
            "(?iu)(테이블|디비|데이터베이스|쿼리|레코드|\\bdb\\b|\\bdatabase\\b|\\bsql\\b|\\bquery\\b|"
                    + "\\btable\\b|\\brecords?\\b|\\brows?\\b|\\bschema\\b)");

    /** A mutation verb (EN + KO stems). */
    private static final Pattern MUTATION_VERB = Pattern.compile(
            "(?iu)(insert|update|delete|drop|truncate|alter|remove|erase|wipe|"
                    + "삭제|지워|지우|수정|변경|추가|넣어|넣고|바꿔|바꾸|만들|생성|없애)");

    /** Prompt-injection phrasing (EN + KO). */
    private static final Pattern INJECTION = Pattern.compile(
            "(?ius)((ignore|disregard|forget|override)[^.]{0,40}(instruction|rule|prompt|guideline)|"
                    + "(reveal|show|print)[^.]{0,30}(system\\s*prompt)|\\bjailbreak\\b|"
                    + "(프롬프트|지침|지시|규칙)[^.]{0,15}(무시|잊어)|시스템\\s*프롬프트)");

    private static final String DB_MUTATION_REPLY =
            "I can only READ reference data (staff, departments, past plans) — I can't insert, update or "
                    + "delete database records. 데이터 조회만 가능하며 DB 추가/수정/삭제는 지원하지 않습니다. "
                    + "To change a plan, edit the form or tell me the new field values.";

    private static final String INJECTION_REPLY =
            "I can't act on instructions that override how this assistant works. "
                    + "Let's continue with the business-trip plan — tell me who travels, where and when.";

    private static final String TOO_LONG_REPLY =
            "That message is too long for one turn. Please shorten it (or attach the content as a file).";

    @Override
    public GuardrailResult check(String message) {
        if (message == null || message.isBlank()) {
            return GuardrailResult.ok();   // blank handling belongs to the orchestrators
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            return blockedWithLog("INPUT_TOO_LONG", TOO_LONG_REPLY, message);
        }
        if (INJECTION.matcher(message).find()) {
            return blockedWithLog("INJECTION", INJECTION_REPLY, message);
        }
        if (RAW_SQL.matcher(message).matches()) {
            return blockedWithLog("DB_MUTATION", DB_MUTATION_REPLY, message);
        }
        if (DB_TARGET.matcher(message).find() && MUTATION_VERB.matcher(message).find()) {
            return blockedWithLog("DB_MUTATION", DB_MUTATION_REPLY, message);
        }
        return GuardrailResult.ok();
    }

    private GuardrailResult blockedWithLog(String category, String reply, String message) {
        log.warn("Guardrail blocked turn [{}]: {}", category,
                message.length() > 120 ? message.substring(0, 120) + "…" : message);
        return GuardrailResult.blocked(category, reply);
    }

    /** Exposed for tests/diagnostics. */
    public List<String> categories() {
        return List.of("DB_MUTATION", "INJECTION", "INPUT_TOO_LONG");
    }
}
