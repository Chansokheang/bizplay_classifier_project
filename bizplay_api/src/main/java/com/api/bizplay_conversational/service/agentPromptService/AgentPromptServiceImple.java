package com.api.bizplay_conversational.service.agentPromptService;

import com.api.bizplay_conversational.model.entity.ConversationalAgentPrompt;
import com.api.bizplay_conversational.model.request.AgentPromptRequest;
import com.api.bizplay_conversational.model.response.AgentModuleResponse;
import com.api.bizplay_conversational.model.response.AgentPromptResponse;
import com.api.bizplay_conversational.repository.ConversationalAgentPromptRepo;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentPromptServiceImple implements AgentPromptService {

    /** Default chat opener shown by the web UI's chat hero. */
    private static final String STARTER_DEFAULT =
            "Describe it in your own words — I will draft the whole plan for you, "
                    + "from the trip form and dates to travellers and attachments.";

    /** Default example prompts on the chat hero — ONE per line. */
    private static final String SUGGESTIONS_DEFAULT = String.join("\n",
            "Please create a business trip plan to Busan for next Tuesday.",
            "I am going to the KSHRD Center in Phnom Penh from 2026-08-20 to 2026-08-21 to train IT instructors.",
            "Prepare an overseas trip to the Osaka exhibition for me and my team lead.");

    /** Agent name -> compiled-in default, registered by each agent on startup / first call. */
    private final Map<String, String> defaults = new ConcurrentHashMap<>();

    /** Module rows share the prompt table under this name prefix (enabled column = state). */
    private static final String MODULE_PREFIX = "module:";

    /** The Plan agent's toggleable modules; core ones are fixed ON. */
    private static final Map<String, Boolean> MODULES = new java.util.LinkedHashMap<>() {{
        put("guardrail", true);           // fixed
        put("purpose-segment", true);     // fixed
        put("field-mapper", true);        // fixed
        put("form-builder", true);        // fixed
        put("form-follow-up", false);
        put("traveler-resolver", false);
        put("place-validator", false);
        put("database-lookup", false);
        put("spreadsheet", false);
        put("pdf", false);
    }};

    private final ConversationalAgentPromptRepo promptRepo;
    private final JdbcTemplate jdbcTemplate;

    /** MyBatis-mapped table: ddl-auto can't create it, so bootstrap idempotently at startup. */
    @PostConstruct
    void init() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS conversational_agent_prompt (
                        corp_no      VARCHAR(50)  NOT NULL,
                        name         VARCHAR(100) NOT NULL,
                        prompt       TEXT NOT NULL,
                        enabled      BOOLEAN NOT NULL DEFAULT TRUE,
                        created_date TIMESTAMP NOT NULL DEFAULT NOW(),
                        updated_date TIMESTAMP NOT NULL DEFAULT NOW(),
                        PRIMARY KEY (corp_no, name)
                    )""");
            // Upgrade a pre-tenant table in place: add corp_no and re-key on (corp_no, name).
            Integer hasCorpNo = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_name = 'conversational_agent_prompt' AND column_name = 'corp_no'
                    """, Integer.class);
            if (hasCorpNo != null && hasCorpNo == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE conversational_agent_prompt ADD COLUMN corp_no VARCHAR(50) NOT NULL DEFAULT '1234567890'");
                jdbcTemplate.execute(
                        "ALTER TABLE conversational_agent_prompt DROP CONSTRAINT conversational_agent_prompt_pkey");
                jdbcTemplate.execute(
                        "ALTER TABLE conversational_agent_prompt ADD PRIMARY KEY (corp_no, name)");
                jdbcTemplate.execute(
                        "ALTER TABLE conversational_agent_prompt ALTER COLUMN corp_no DROP DEFAULT");
                log.info("conversational_agent_prompt upgraded to per-corp keys (existing rows -> corp 1234567890).");
            }
        } catch (Exception e) {
            log.warn("Could not bootstrap conversational_agent_prompt ({}) — customization disabled until it exists.",
                    e.getMessage());
        }
        defaults.put(STARTER_MESSAGE, STARTER_DEFAULT);
        defaults.put(STARTER_SUGGESTIONS, SUGGESTIONS_DEFAULT);
    }

    @Override
    public String resolve(String name, String builtInDefault) {
        registerDefault(name, builtInDefault);
        String corpNo = AgentTenantContext.get();
        if (corpNo == null) {
            return builtInDefault;   // no tenant on this thread — defaults apply
        }
        try {
            ConversationalAgentPrompt row = promptRepo.findByName(corpNo, name);
            if (row != null && row.isEnabled() && row.getPrompt() != null && !row.getPrompt().isBlank()) {
                return row.getPrompt();
            }
        } catch (Exception e) {
            log.warn("Agent prompt lookup failed for '{}/{}' ({}) — using the built-in default.",
                    corpNo, name, e.getMessage());
        }
        return builtInDefault;
    }

    @Override
    public void registerDefault(String name, String defaultPrompt) {
        if (name != null && defaultPrompt != null) {
            defaults.putIfAbsent(name, defaultPrompt);
        }
    }

    @Override
    public List<AgentPromptResponse> list(String corpNo) {
        requireCorp(corpNo);
        Map<String, ConversationalAgentPrompt> rows = new HashMap<>();
        try {
            promptRepo.findAll(corpNo.trim()).forEach(r -> rows.put(r.getName(), r));
        } catch (Exception e) {
            log.warn("Agent prompt listing failed for corp {}: {}", corpNo, e.getMessage());
        }
        List<AgentPromptResponse> out = new ArrayList<>();
        TreeSet<String> names = new TreeSet<>(defaults.keySet());
        names.addAll(rows.keySet());
        for (String name : names) {
            if (name.startsWith(MODULE_PREFIX) || name.startsWith("setting:")) {
                continue;   // module toggles and integration settings are not prompts
            }
            out.add(toResponse(name, rows.get(name)));
        }
        return out;
    }

    // --- sub-agent module on/off ---------------------------------------------------------

    @Override
    public boolean isModuleEnabled(String name) {
        Boolean fixed = MODULES.get(name);
        if (fixed == null || fixed) {
            return true;   // unknown or core module — always on
        }
        String corpNo = AgentTenantContext.get();
        if (corpNo == null) {
            return true;
        }
        try {
            ConversationalAgentPrompt row = promptRepo.findByName(corpNo, MODULE_PREFIX + name);
            return row == null || row.isEnabled();
        } catch (Exception e) {
            return true;   // never let a DB hiccup switch features off
        }
    }

    @Override
    public List<AgentModuleResponse> listModules(String corpNo) {
        requireCorp(corpNo);
        Map<String, ConversationalAgentPrompt> rows = new HashMap<>();
        try {
            promptRepo.findAll(corpNo.trim()).forEach(r -> rows.put(r.getName(), r));
        } catch (Exception e) {
            log.warn("Agent module listing failed for corp {}: {}", corpNo, e.getMessage());
        }
        List<AgentModuleResponse> out = new ArrayList<>();
        MODULES.forEach((name, fixed) -> {
            ConversationalAgentPrompt row = rows.get(MODULE_PREFIX + name);
            out.add(AgentModuleResponse.builder()
                    .name(name)
                    .fixed(fixed)
                    .enabled(fixed || row == null || row.isEnabled())
                    .build());
        });
        return out;
    }

    @Override
    public AgentModuleResponse setModule(String corpNo, String name, boolean enabled) {
        requireCorp(corpNo);
        Boolean fixed = MODULES.get(name);
        if (fixed == null) {
            throw new IllegalArgumentException("Unknown agent module: '" + name + "'.");
        }
        if (fixed) {
            throw new IllegalArgumentException("'" + name + "' is a core module and is always on.");
        }
        ConversationalAgentPrompt row = new ConversationalAgentPrompt();
        row.setCorpNo(corpNo.trim());
        row.setName(MODULE_PREFIX + name);
        row.setPrompt("-");   // state rides the enabled column
        row.setEnabled(enabled);
        promptRepo.upsert(row);
        log.info("Agent module '{}' set to {} for corp {}.", name, enabled ? "ON" : "OFF", corpNo);
        return AgentModuleResponse.builder().name(name).fixed(false).enabled(enabled).build();
    }

    @Override
    public AgentPromptResponse get(String corpNo, String name) {
        requireCorp(corpNo);
        ConversationalAgentPrompt row = null;
        try {
            row = promptRepo.findByName(corpNo.trim(), name);
        } catch (Exception ignored) {
            // listed with default only
        }
        if (row == null && !defaults.containsKey(name)) {
            throw new IllegalArgumentException("Unknown agent prompt: '" + name + "'.");
        }
        return toResponse(name, row);
    }

    @Override
    public AgentPromptResponse put(String corpNo, String name, AgentPromptRequest request) {
        requireCorp(corpNo);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required.");
        }
        String prompt = request == null || request.getPrompt() == null ? "" : request.getPrompt().trim();
        if (prompt.isBlank()) {
            throw new IllegalArgumentException("prompt is required — use DELETE to reset to the default.");
        }
        String key = name.trim();
        if (key.startsWith(MODULE_PREFIX) || !defaults.containsKey(key)) {
            throw new IllegalArgumentException("Unknown agent prompt: '" + key + "'.");
        }
        // Structural placeholders from the default (e.g. the follow-up agent's %1$s language
        // slot) must survive a customization, or the agent's formatting breaks.
        String def = defaults.get(key);
        if (def != null && def.contains("%1$s") && !prompt.contains("%1$s")) {
            throw new IllegalArgumentException(
                    "This agent's prompt must keep the %1$s placeholder (it carries the reply language).");
        }
        ConversationalAgentPrompt row = new ConversationalAgentPrompt();
        row.setCorpNo(corpNo.trim());
        row.setName(key);
        row.setPrompt(prompt);
        row.setEnabled(request.getEnabled() == null || request.getEnabled());
        promptRepo.upsert(row);
        log.info("Agent prompt '{}' customized for corp {} ({} chars, enabled={}).",
                key, corpNo, prompt.length(), row.isEnabled());
        return get(corpNo, key);
    }

    @Override
    public void reset(String corpNo, String name) {
        requireCorp(corpNo);
        int deleted = promptRepo.deleteByName(corpNo.trim(), name);
        log.info("Agent prompt '{}' reset to default for corp {} ({} row removed).", name, corpNo, deleted);
    }

    private static void requireCorp(String corpNo) {
        if (corpNo == null || corpNo.isBlank()) {
            throw new IllegalArgumentException("corpNo is required.");
        }
    }

    private AgentPromptResponse toResponse(String name, ConversationalAgentPrompt row) {
        String def = defaults.get(name);
        boolean customLive = row != null && row.isEnabled()
                && row.getPrompt() != null && !row.getPrompt().isBlank();
        return AgentPromptResponse.builder()
                .name(name)
                .defaultPrompt(def)
                .customPrompt(row == null ? null : row.getPrompt())
                .enabled(row == null ? null : row.isEnabled())
                .source(customLive ? "CUSTOM" : "DEFAULT")
                .effectivePrompt(customLive ? row.getPrompt() : def)
                .updatedDate(row == null ? null : row.getUpdatedDate())
                .build();
    }
}
