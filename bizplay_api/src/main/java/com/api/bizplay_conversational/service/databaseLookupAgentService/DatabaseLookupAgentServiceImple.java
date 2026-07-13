package com.api.bizplay_conversational.service.databaseLookupAgentService;

import com.api.bizplay_conversational.model.response.DatabaseLookupAgentResponse;
import lombok.extern.slf4j.Slf4j;
import com.api.bizplay_conversational.service.llmSettingsService.LlmSettingsService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class DatabaseLookupAgentServiceImple implements DatabaseLookupAgentService {

    private static final Set<String> ALLOWED_TABLES = Set.of(
            "conversational_agent_session",
            "conversational_attachment",
            "conversational_cost_expense",
            "conversational_department",
            "conversational_staff",
            "conversational_transportation_expense",
            "conversational_traveler",
            "conversational_trip_plan",
            "conversational_trip_report",
            "corp",
            "corp_group"
    );

    private static final List<String> FORBIDDEN_TOKENS = List.of(
            "insert", "update", "delete", "drop", "alter", "truncate", "create", "grant", "revoke",
            "copy", "call", "execute", "merge", "replace", "comment", "vacuum", "analyze"
    );

    private final Map<String, ChatClient> chatClientRegistry;
    private final LlmSettingsService llmSettingsService;

    /**
     * Read-only, least-privilege JdbcTemplate ({@code bizplay_agent_ro} role). This is
     * intentionally NOT the app's primary JdbcTemplate: the role cannot insert/update/
     * delete or call procedures, so no gap in the SQL validator can turn into a write.
     */
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.conversational.database-lookup-agent.model:qwen3-14b}")
    private String modelName;

    public DatabaseLookupAgentServiceImple(
            Map<String, ChatClient> chatClientRegistry,
            @Qualifier("agentJdbcTemplate") JdbcTemplate jdbcTemplate,
            LlmSettingsService llmSettingsService) {
        this.chatClientRegistry = chatClientRegistry;
        this.jdbcTemplate = jdbcTemplate;
        this.llmSettingsService = llmSettingsService;
    }

    /** Total generation attempts: the first try plus self-correction retries. */
    @Value("${app.conversational.database-lookup-agent.max-attempts:3}")
    private int maxAttempts;

    @Override
    public DatabaseLookupAgentResponse lookup(String corpNo, String task) {
        return lookup(corpNo, task, List.of());
    }

    @Override
    public DatabaseLookupAgentResponse lookup(String corpNo, String task, List<Message> history) {
        int attempts = Math.max(1, maxAttempts);
        String lastBadSql = null;
        String lastError = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            String sql = null;
            try {
                // On retries, feed the previous broken SQL + error back so the model can self-correct.
                sql = generateSql(corpNo, task, history, lastBadSql, lastError);
                sql = sanitizeSql(sql);
                sql = enforceLimit(sql);
                validateSql(sql);
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
                return DatabaseLookupAgentResponse.builder()
                        .task(task)
                        .sql(sql)
                        .executed(true)
                        .rows(rows)
                        .build();
            } catch (RuntimeException e) {
                lastBadSql = sql;
                lastError = e.getMessage();
                log.warn("Database lookup attempt {}/{} failed: {}", attempt, attempts, lastError);
            }
        }

        log.warn("Database lookup sub-agent exhausted {} attempt(s); returning failure.", attempts);
        return DatabaseLookupAgentResponse.builder()
                .task(task)
                .sql(lastBadSql)
                .executed(false)
                .error(clientError(lastError))
                .rows(List.of())
                .build();
    }

    /** Translate an internal failure message into a safe, generic message for the client. */
    private String clientError(String rawError) {
        if (rawError == null) {
            return "The database lookup could not be completed.";
        }
        // Validation/generation failures are our own controlled messages -> safe to surface.
        if (rawError.startsWith("LLM ")
                || rawError.startsWith("Only SELECT")
                || rawError.startsWith("SQL ")
                || rawError.startsWith("Forbidden SQL")
                || rawError.startsWith("Table is not allowed")
                || rawError.startsWith("Database lookup agent model")) {
            return rawError;
        }
        // Anything else (raw JDBC/Postgres errors) is hidden to avoid leaking schema internals.
        return "The generated query could not be executed against the database.";
    }

    private String generateSql(String corpNo, String task, List<Message> history,
                               String previousBadSql, String previousError) {
        ChatClient client = chatClientRegistry.get(llmSettingsService.resolve(modelName));
        if (client == null) {
            throw new IllegalStateException("Database lookup agent model is not configured: " + modelName);
        }

        String systemPrompt = """
                        You are a PostgreSQL read-only SQL generator.
                        Generate exactly one SELECT statement and nothing else.
                        Do not use markdown. Do not explain.

                        Database schema (PostgreSQL) of the only tables you may query:

                        CREATE TABLE conversational_department (
                            id           UUID PRIMARY KEY,
                            corp_no      VARCHAR(50)  NOT NULL,   -- company/tenant identifier
                            name         VARCHAR(100) NOT NULL,   -- department name
                            created_date TIMESTAMP    NOT NULL,
                            UNIQUE (corp_no, name)
                        );

                        CREATE TABLE conversational_staff (
                            id            UUID PRIMARY KEY,
                            department_id UUID         NOT NULL REFERENCES conversational_department(id),
                            name          VARCHAR(100) NOT NULL,  -- staff/employee name
                            position      VARCHAR(100),           -- job title, may be NULL
                            created_date  TIMESTAMP    NOT NULL,
                            UNIQUE (department_id, name, position)
                        );

                        CREATE TABLE conversational_traveler (
                            trip_id              UUID         NOT NULL REFERENCES conversational_trip_plan(id),
                            staff_id             UUID         NOT NULL REFERENCES conversational_staff(id),
                            origin_location      VARCHAR(255) NOT NULL,  -- where this staff departed from for the trip
                            destination_location VARCHAR(255) NOT NULL,  -- where this staff travelled to
                            return_point         VARCHAR(255) NOT NULL,
                            created_date         TIMESTAMP    NOT NULL,
                            PRIMARY KEY (trip_id, staff_id)
                        );

                        CREATE TABLE conversational_trip_plan (
                            id                           UUID PRIMARY KEY,
                            corp_no                      VARCHAR(50)  NOT NULL,  -- company/tenant identifier
                            agent_session_id             UUID,                   -- -> conversational_agent_session.id
                            user_req_id                  VARCHAR(100) NOT NULL,
                            plan_type                    VARCHAR(255) NOT NULL,
                            purpose                      VARCHAR(255) NOT NULL,  -- why the trip happened
                            title                        VARCHAR(100) NOT NULL,
                            content                      TEXT,
                            business_period              VARCHAR(100) NOT NULL,
                            business_start_date          DATE,                   -- trip start
                            business_end_date            DATE,                   -- trip end
                            destination                  VARCHAR(255) NOT NULL,  -- overall trip destination
                            business_trip_classification VARCHAR(100) NOT NULL,
                            created_date                 TIMESTAMP    NOT NULL
                        );

                        CREATE TABLE conversational_trip_report (
                            id                        UUID PRIMARY KEY,
                            agent_session_id          UUID,                      -- -> conversational_agent_session.id
                            department_id             UUID NOT NULL,             -- -> conversational_department.id
                            trip_plan_id              UUID NOT NULL,             -- -> conversational_trip_plan.id
                            transportation_expense_id UUID,                      -- -> conversational_transportation_expense.id
                            cost_expense_id           UUID,                      -- -> conversational_cost_expense.id
                            section_code              VARCHAR(30) NOT NULL,      -- COST | TRANSPORTATION | ETC
                            excess_reason             TEXT,
                            briefs                    TEXT,
                            note                      TEXT,
                            approval_number           VARCHAR(100),
                            created_date              TIMESTAMP NOT NULL
                        );

                        CREATE TABLE conversational_cost_expense (
                            id               UUID PRIMARY KEY,
                            expense_type     VARCHAR(100) NOT NULL,  -- COST | ETC
                            tax_code         VARCHAR(50)  NOT NULL,
                            category         VARCHAR(100) NOT NULL,
                            use_purpose      VARCHAR(100) NOT NULL,
                            account          VARCHAR(100) NOT NULL,
                            start_date       DATE NOT NULL,
                            end_date         DATE NOT NULL,
                            description      TEXT,
                            proof_date       DATE NOT NULL,
                            amount_used      NUMERIC(15,2) NOT NULL,
                            regulated_amount NUMERIC(15,2) NOT NULL,
                            created_date     TIMESTAMP NOT NULL
                        );

                        CREATE TABLE conversational_transportation_expense (
                            id                    UUID PRIMARY KEY,
                            tax_code              VARCHAR(50)  NOT NULL,
                            category              VARCHAR(100) NOT NULL,
                            use_purpose           VARCHAR(100) NOT NULL,
                            account               VARCHAR(100) NOT NULL,
                            transportation_method VARCHAR(100) NOT NULL,
                            origin_location       VARCHAR(255) NOT NULL,
                            destination_location  VARCHAR(255) NOT NULL,
                            usage_date            DATE NOT NULL,
                            vendor                VARCHAR(255) NOT NULL,
                            supply_price          NUMERIC(15,2) NOT NULL,
                            tax                   NUMERIC(15,2) NOT NULL,
                            amount_used           NUMERIC(15,2) NOT NULL,
                            regulated_amount      NUMERIC(15,2) NOT NULL,
                            created_date          TIMESTAMP NOT NULL
                        );

                        CREATE TABLE conversational_attachment (
                            id              UUID PRIMARY KEY,
                            trip_plan_id    UUID,                    -- -> conversational_trip_plan.id (exactly one of plan/report set)
                            report_id       UUID,                    -- -> conversational_trip_report.id
                            file_id         VARCHAR(100) NOT NULL,
                            attachment_type VARCHAR(30)  NOT NULL,   -- PLAN | REPORT
                            url             VARCHAR(2048),
                            created_date    TIMESTAMP NOT NULL
                        );

                        CREATE TABLE conversational_agent_session (
                            id              UUID PRIMARY KEY,
                            corp_no         VARCHAR(50) NOT NULL,    -- company/tenant identifier
                            agent_type      VARCHAR(50) NOT NULL,    -- TRIP_PLAN | EXPENSE_REPORT
                            status          VARCHAR(30) NOT NULL,    -- COLLECTING | READY_FOR_REVIEW | APPROVED | POSTED | CANCELLED
                            created_date    TIMESTAMP NOT NULL,
                            updated_date    TIMESTAMP NOT NULL
                        );

                        CREATE TABLE corp (
                            corp_id       BIGINT PRIMARY KEY,
                            corp_no       VARCHAR(50)  NOT NULL,     -- company/tenant identifier
                            corp_group_id BIGINT       NOT NULL,     -- -> corp_group.corp_group_id
                            corp_name     VARCHAR(255) NOT NULL,
                            created_date  TIMESTAMP NOT NULL
                        );

                        CREATE TABLE corp_group (
                            corp_group_id BIGINT PRIMARY KEY,
                            corp_group_cd VARCHAR(20) NOT NULL
                        );

                        Relationships:
                        - conversational_staff.department_id -> conversational_department.id (a staff belongs to one department).
                        - conversational_traveler.staff_id -> conversational_staff.id (a traveler row is one staff on one trip; this table is the history of where staff have travelled).
                        - conversational_traveler.trip_id -> conversational_trip_plan.id (links a traveler to the trip's details).
                        - conversational_trip_report.trip_plan_id -> conversational_trip_plan.id; .department_id -> conversational_department.id; .cost_expense_id -> conversational_cost_expense.id; .transportation_expense_id -> conversational_transportation_expense.id.
                        - conversational_attachment.trip_plan_id -> conversational_trip_plan.id OR .report_id -> conversational_trip_report.id (exactly one is set).
                        - conversational_cost_expense and conversational_transportation_expense are referenced from conversational_trip_report; they have no corp_no of their own.
                        - corp.corp_group_id -> corp_group.corp_group_id.
                        - corp_no exists directly on: conversational_department, conversational_trip_plan, conversational_agent_session, and corp. All other tables reach corp scope only through joins to one of these.

                        Rules:
                        - Only SELECT is allowed.
                        - Only use the allowed tables listed above.
                        - Every query MUST filter by corp_no = '%s' on a table that has the corp_no column (conversational_department, conversational_trip_plan, conversational_agent_session, or corp).
                        - Tables without a corp_no column (staff, traveler, trip_report, cost_expense, transportation_expense, attachment, corp_group) MUST be joined to a table that has corp_no, and that corp_no MUST be filtered. Never return rows from these tables without an enforced corp_no filter through a join.
                          * staff -> department via conversational_staff.department_id = conversational_department.id
                          * traveler -> staff via conversational_traveler.staff_id = conversational_staff.id (then staff -> department), or traveler -> trip_plan via conversational_traveler.trip_id = conversational_trip_plan.id
                          * trip_report / cost_expense / transportation_expense / attachment -> conversational_trip_plan (then its corp_no)
                        - Only join the tables the user's question actually needs. Do not add expense, report, attachment, session, or corp tables unless the request is about that data.
                        - Use ILIKE for user-provided names, department names, or company names.
                        - Limit results to 50 rows.

                        Output only the raw SQL SELECT statement. No explanation, no reasoning, no markdown.
                        /no_think
                        """.formatted(escapeLiteral(corpNo));

        List<Message> prompt = new ArrayList<>();
        prompt.add(new SystemMessage(systemPrompt));
        if (history != null) {
            prompt.addAll(history);
        }

        StringBuilder userTurn = new StringBuilder("Translate this user request into one SQL SELECT statement:\n")
                .append(task);
        if (previousBadSql != null || previousError != null) {
            // Self-correction: show the model its previous broken attempt and the error, ask for a fix.
            userTurn.append("\n\nYour previous attempt failed. Fix it and output one corrected SELECT only.");
            if (previousBadSql != null) {
                userTurn.append("\nPrevious SQL:\n").append(previousBadSql);
            }
            if (previousError != null) {
                userTurn.append("\nError:\n").append(previousError);
            }
        }
        prompt.add(new UserMessage(userTurn.toString()));

        String content = client.prompt()
                .messages(prompt)
                .call()
                .content();

        log.info("Database lookup sub-agent generated SQL: {}", content);
        return content;
    }

    private String sanitizeSql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("LLM did not generate SQL.");
        }
        String cleaned = sql.trim();
        // Strip reasoning blocks emitted by thinking models (e.g. Qwen3 <think>...</think>).
        cleaned = cleaned.replaceAll("(?is)<think>.*?</think>", "").trim();
        // If a think block was opened but never closed (token budget exhausted), no SQL was produced.
        if (cleaned.toLowerCase(Locale.ROOT).contains("<think>")) {
            throw new IllegalArgumentException("LLM returned reasoning without a SQL statement.");
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replace("```sql", "")
                    .replace("```", "")
                    .trim();
        }
        while (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        return cleaned;
    }

    private void validateSql(String sql) {
        String normalized = sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (!normalized.startsWith("select ")) {
            throw new IllegalArgumentException("Only SELECT queries are allowed.");
        }
        if (normalized.contains("--") || normalized.contains("/*") || normalized.contains("*/") || normalized.contains(";")) {
            throw new IllegalArgumentException("SQL comments and multiple statements are not allowed.");
        }
        for (String token : FORBIDDEN_TOKENS) {
            if (normalized.matches(".*\\b" + token + "\\b.*")) {
                throw new IllegalArgumentException("Forbidden SQL token: " + token);
            }
        }
        boolean usesAllowedTable = ALLOWED_TABLES.stream().anyMatch(normalized::contains);
        if (!usesAllowedTable) {
            throw new IllegalArgumentException("SQL must use at least one allowed conversational lookup table.");
        }
        if (!normalized.contains("corp_no")) {
            throw new IllegalArgumentException("SQL must filter by corp_no for tenant isolation.");
        }
        for (String tableRef : List.of(" from ", " join ")) {
            int cursor = 0;
            while ((cursor = normalized.indexOf(tableRef, cursor)) >= 0) {
                int start = cursor + tableRef.length();
                int end = normalized.indexOf(' ', start);
                String table = (end >= 0 ? normalized.substring(start, end) : normalized.substring(start))
                        .replace("\"", "")
                        .trim();
                if (table.contains(".")) {
                    table = table.substring(table.lastIndexOf('.') + 1);
                }
                if (!ALLOWED_TABLES.contains(table)) {
                    throw new IllegalArgumentException("Table is not allowed: " + table);
                }
                cursor = start;
            }
        }
    }

    private String escapeLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private String enforceLimit(String sql) {
        String normalized = sql.toLowerCase(Locale.ROOT);
        if (normalized.matches("(?s).*\\blimit\\s+\\d+\\s*$")) {
            return sql;
        }
        return sql + " LIMIT 50";
    }
}
