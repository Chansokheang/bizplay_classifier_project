# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

Spring Boot 3.4.5 / Java 21 / Maven (wrapper included). PowerShell is the default shell on this machine — use `./mvnw` syntax in the Bash tool or `.\mvnw.cmd` in PowerShell.

```bash
./mvnw clean package -DskipTests        # build jar
./mvnw spring-boot:run                  # run app on :8080 (must restart after code changes — tests in src/test/data/run_tests.ps1 assume this)
./mvnw test                             # run all tests
./mvnw test -Dtest=ClassName#method     # run a single test
docker compose up -d                    # start Postgres (pgvector/pg16 on :9005), MinIO (:9006/:9007), and the app (:9008)
```

`compose.yaml` mounts `src/main/resources/schemaV2.sql` as `01-schema.sql` into Postgres init — schema changes go in `schemaV2.sql`, **not** Flyway. `spring.flyway.enabled=false` is set deliberately; `spring.jpa.hibernate.ddl-auto=update` lets JPA backfill the conversational-module tables on top of the SQL bootstrap.

Local Docker Compose auto-start is **off** by default (`spring.docker.compose.enabled=${SPRING_DOCKER_COMPOSE_ENABLED:false}`). Set it to `true` if you want `spring-boot:run` to spin up the database for you; otherwise bring it up manually with `docker compose up -d postgres minio`.

`.env` at the project root is loaded via `spring.config.import=optional:file:./.env[.properties]`. Most secrets (LLM keys, MinIO creds, JWT secret, fallback AI endpoints, Korean compliance API keys) come from there, with defaults in `application.properties` for local dev. OS env vars take precedence.

## Repository Layout

This is a **multi-module Spring Boot app** — one JAR (`bizplay_classifier_api`), four feature packages under `com.api.*`, all wired together by `BizplayApplication.java`.

```
com.api/
├── BizplayApplication.java         # @SpringBootApplication; @MapperScan covers classifier + conversational; TimeZone forced to Asia/Seoul
├── bizplay_classifier_api/         # Corp-scoped expense classifier (rules + categories + AI fallback). Public API surface documented in README.md.
├── bizplay_chatbot/                # Multi-LLM chat infra: ChatClient registry, RAG, Telegram & Kakao integrations
├── bizplay_compliance/             # NTS businessman / Naver geocode / Korean holiday API integrations
└── bizplay_conversational/         # Conversational sub-agents for trip-plan / expense-report drafting (work-in-progress orchestrator)
```

`BizplayApplication` excludes Spring AI's auto-config (`OpenAiChatAutoConfiguration`, `OpenAiEmbeddingAutoConfiguration`) because the chatbot module builds its own per-model `ChatClient` registry — see below. Bean names use `FullyQualifiedAnnotationBeanNameGenerator` to avoid collisions between modules that have similarly-named classes.

`@MapperScan` is configured for the classifier and conversational repositories. The compliance module's MyBatis scan is **commented out** in `BizplayApplication.java` — if you add MyBatis mappers there, re-enable the scan.

## Multi-LLM ChatClient Registry

The chatbot module owns LLM wiring; the conversational sub-agents consume it.

- `app.llm.models[*]` in `application.properties` enumerates available LLMs (currently EXAONE 3.5 7.8B, Gemma 4 8B, Qwen3-14B) each with their own `baseUrl`, `apiKey`, and `authScheme` (`x-api-key` for internal vLLM, `bearer` for OpenAI-compatible gateways).
- `bizplay_chatbot/config/SpringAiConfig.java` builds a `Map<String, ChatClient> chatClientRegistry` keyed by model name. Sub-agents resolve their model by injecting this map and looking up the configured key (e.g. `app.conversational.staff-lookup-agent.model=qwen3-14b`).
- The classifier module has a separate AI fallback path (`app.ai.fallback.*`) used when rule-based classification doesn't match — it talks directly to vLLM/OpenAI/Gemini/Claude endpoints, **not** through the registry.

When adding a new sub-agent that needs an LLM: inject `Map<String, ChatClient> chatClientRegistry`, add `app.conversational.<agent>.model` to `application.properties`, and look it up by name. Empty slots in `app.llm.models[*]` (no `name`/`baseUrl`) are silently skipped — that's how `.env` controls which models are active in each environment.

## Conversational Sub-Agents

`bizplay_conversational` exposes sub-agents at `/api/v1/agent-conversations`:

- `sub-agents/staff-lookup` — LLM extracts a person's name from free text, then `StaffService` resolves it against the corp's staff table.
- `sub-agents/text-analysis` — generic structured-text extraction (`TextAnalysisAgentService`).
- `sub-agents/spreadsheet` — multipart upload; parses staff-list spreadsheets via `SpreadsheetAgentService`.
- `sub-agents/pdf` — PDF extraction via `PdfAgentService` (PDFBox).
- `agents/trip-plan` — composite agent that fans out staff-lookup + text-analysis + (optional) file extraction into a draft trip plan.

`AgentExecutorConfig` defines a dedicated `agentTaskExecutor` thread pool (4 core / 8 max / 50 queue) so the orchestrator can fan sub-agents out in parallel — per-turn latency tracks the slowest LLM call, not the sum. **Use this executor for new fan-out paths**; don't create ad-hoc thread pools.

`AgentOrchestratorServiceImple` is currently a stub — the orchestration logic lives in `tripPlanAgentService` and the controllers for now. Drafts persist via JPA entities under `model/entity/` (`TripPlanDraft`, `TripReportDraft`, `*Draft` variants of the expense entities).

The conversational module has its **own** MinIO client (`ConversationalMinioConfig` → bean name `conversationalMinioClient`, properties `app.conversational.minio.*`, default bucket `bizplay-conversational`) — independent from the classifier's `app.storage.minio.*` bucket. Don't cross-wire them.

## Classifier API Surface

The classifier is the production-stable surface — see `README.md` for the full endpoint catalogue. Key invariants:

- `corpNo` is the primary scope key everywhere. There is no login / JWT requirement on the active classifier flow (`/api/v1/auths/login` was removed).
- API uses camelCase (`corpNo`, `isUsed`, `merchantIndustryName`); SQL uses snake_case (`corp_no`, `is_used`, `merchant_industry_name`). DTOs still accept Korean-encoded aliases (`가맹점업종명`, `가맹점업종코드`) and the legacy `categoryIds` → `categoryCodes` for backward compatibility — keep those aliases working when editing request DTOs.
- All non-download endpoints return the common envelope `{ payload, message, code, status, token, fileUrl }` (`ApiResponse`).

## Persistence

- **JDBC datasource:** Postgres 16 + pgvector. Default for local: `jdbc:postgresql://localhost:5432/bizplay`, user `postgres`, pw `123` (override via `SPRING_DATASOURCE_*` env vars).
- **Two persistence stacks coexist:** MyBatis (mappers under `bizplay_classifier_api/repository` and `bizplay_conversational/repository`) and Spring Data JPA (chatbot + conversational entities). `JsonNodeTypeHandler` (under conversational `config/`) is a MyBatis type handler — registered via `mybatis.type-handlers-package` in `application.properties`.
- **Vector store:** Spring AI PGVector (`vector_store` table); 1024-dim embeddings via the bge-m3 embedding server defined by `app.embed.*`. `initialize-schema=false` — vector tables are created by `schemaV2.sql`.
- **Spring Session JDBC:** enabled but `initialize-schema=never` — session tables must already exist in Postgres.

## CORS / Deployment

Origins are hard-coded to specific domains in the controllers — see the latest commit (`update CrossOrigin to use domain name`). The deployed Kakao public base URL is `https://bizplay-api.aiconvergencelab.com/`; the UI sits in the sibling `bizplay_ui` repo. CI/CD pushes to GHCR and SSH-deploys via `compose.prod.yaml` — see `docs/CI_CD_SETUP.md` for the GitHub secrets list.

## Testing

There is essentially no JUnit coverage — `src/test/java/` contains only the Spring Boot smoke test `BizplayClassifierApiApplicationTests`. **Real testing happens via `src/test/data/run_tests.ps1`**: it expects the app already running on `localhost:8080`, MinIO + the qwen3-14b vLLM endpoint reachable, and Postgres seeded with `seed_staff_department.sql` (corp `1234567890`). Generate fixtures with `python gen_spreadsheets.py` and `python gen_trip_pdfs.py` in that directory before running. Manual HTTP requests live in `src/test/http/conversational-agent.http`.
