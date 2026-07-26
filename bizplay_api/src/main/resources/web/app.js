/* ============================================================
 * Traditional Business Trip Plan — manual input demo
 * Talks to the same-origin REST API:
 *   GET  /api/v1/plans?corpNo=...
 *   POST /api/v1/plans
 * ============================================================ */

/* Corp number is editable from the header and remembered across reloads. */
let CORP_NO = localStorage.getItem("bizplay.corpNo") || "1234567890";
const PLAN_TYPE = "Business Trip Plan";
let currentTab = "plan";

/* ================================================================
 *  Language (ENG / KOR) — dictionary-based chrome translation.
 *  Exact trimmed strings are swapped in text nodes + placeholder/title/
 *  aria-label attributes; a MutationObserver keeps dynamically rendered
 *  tables translated. Strings not in the dictionary stay in English.
 * ================================================================ */
let LANG = localStorage.getItem("bizplay.lang") || "en";
const I18N = {
  // Header / tabs
  "Business Trip Workflow": "출장 업무 워크플로우",
  "Request · Approve · Report": "신청 · 승인 · 보고",
  "CORP No.": "법인번호", "Enter corp no.": "법인번호 입력",
  "Request Plan": "출장 신청", "Approve Plan": "출장 승인", "Expense Report": "지출 보고", "Audit": "감사",
  // Request Plan tab
  "Create with Agent": "에이전트로 작성", "Resume draft": "임시저장 열기", "Create manually": "직접 작성",
  "Staff & Departments": "직원 · 부서 관리",
  "· sorted by most recent": "· 최신순 정렬",
  "entire": "전체", "Approval Requested": "승인 요청", "Approved": "승인됨", "Canceled": "취소됨",
  "Search doc no, title, author, traveler, dept, purpose": "문서번호·제목·작성자·출장자·부서·목적 검색",
  "Show only cancelled": "취소된 항목만 보기",
  "selected": "건 선택됨", "Clear": "선택 해제", "Remove": "제거",
  "Document": "문서번호", "Title": "제목", "Author": "작성자", "Traveler": "출장자", "Dept.": "부서",
  "Purpose": "목적", "Period": "기간", "Route": "경로",
  "Showing": "표시", "of": "/", "plans": "건의 계획", "reports": "건의 보고서", "audits": "건의 감사",
  "100 items": "100건", "newest first": "최신순",
  // Approve tab
  "entries · approve or cancel requests": "건 · 승인/취소 처리",
  "Search doc no, title, traveler, dept": "문서번호·제목·출장자·부서 검색",
  "Status": "상태", "Actions": "작업", "Approve": "승인", "Cancel": "취소",
  "Request for approval": "승인 요청중", "Approval complete": "승인 완료", "Business trip cancellation": "출장 취소",
  // Expense Report tab
  "expense reports": "건의 지출 보고서", "+ Create Report": "+ 보고서 작성", "Refresh": "새로고침",
  "Search doc no, trip, traveler, dept, purpose, status": "문서번호·출장·출장자·부서·목적·상태 검색",
  "Trip": "출장", "Lines": "항목", "Total": "합계", "Updated": "수정일", "View": "보기",
  "Passed": "통과됨", "Failed": "실패", "Not audited": "감사 전",
  // Audit tab
  "audits · R10 requisition mismatch — runs automatically when an expense report is created":
    "감사 · R10 청구 불일치 — 지출 보고서 생성 시 자동 실행",
  "Normal": "정상", "Suspicion": "의심",
  "Search audit id, trip plan, rule, status, summary": "감사ID·출장계획·규칙·상태·요약 검색",
  "Trip Plan": "출장 계획", "Rule": "규칙", "Result": "결과", "Compliance": "준수",
  "Confidence": "신뢰도", "Summary": "요약", "Created": "생성일",
  "PASS": "일치", "FAIL": "불일치", "SKIPPED": "평가 제외", "Pass": "통과",
  "HIGH": "높음", "MEDIUM": "중간", "LOW": "낮음",
  // Create-plan modal
  "Create Business Trip Plan": "출장 계획 작성",
  "Create Business Trip Plan with Agent": "에이전트로 출장 계획 작성",
  "Describe the trip or attach a staff list / itinerary — the agent fills the form, and you can edit any field.":
    "출장을 설명하거나 직원 명단·일정표를 첨부하세요 — 에이전트가 양식을 채우며, 모든 항목을 직접 수정할 수 있습니다.",
  "Agent": "에이전트",
  "A travel-expense limit is granted upon completion.": "작성 완료 시 출장비 한도가 부여됩니다.",
  "Trip Information": "출장 정보", "Travel Purpose": "출장 목적", "Trip Period": "출장 기간",
  "Travel Classification": "출장 구분", "Destination": "출장지", "Content": "내용", "Classification": "구분",
  "Region / City": "지역", "Country": "국가", "Institution / Venue": "교육기관",
  "Additional Information": "추가 정보", "Cost center": "코스트센터", "Enter the content": "내용을 입력하세요",
  "Project code": "과제코드", "Education type": "교육유형", "Remarks": "비고", "Duration": "기간 구분", "One-way / Round": "편도/왕복",
  "Select a purpose…": "출장 목적 선택…", "Select a purpose first…": "출장 목적을 먼저 선택하세요…", "Select…": "선택…",
  "e.g. Busan": "예: 부산", "Give the trip a name": "출장 이름을 입력하세요",
  "Notes, agenda, context…": "메모, 일정, 배경…",
  "Travellers": "출장자 명단", "Traveller": "출장자", "+ Add another traveller": "+ 출장자 추가",
  "Budget Department": "예산 부서", "Travel Route": "이동 경로",
  "Select Traveler…": "출장자 선택…", "Select Budget Department…": "예산 부서 선택…",
  "Set departure · destination · arrival": "출발·목적지·도착 설정",
  "Attachment": "첨부", "Register a URL": "URL 등록",
  "No reference documents attached": "첨부된 참고 문서 없음",
  "Register a URL to attach an itinerary": "URL을 등록해 일정표를 첨부하세요",
  "Save draft": "임시 저장", "Creation Complete": "작성 완료",
  // Route popup
  "Route Setup": "경로 설정", "Departure": "출발지", "Arrival / Return": "도착 / 복귀",
  "Confirm route": "경로 확인",
  "Search or select a route. You may type an address directly.": "경로를 검색하거나 선택하세요. 주소를 직접 입력할 수도 있습니다.",
  "Where the journey begins": "출발 위치", "Where they travel to": "목적지 위치", "Where the journey ends": "도착 위치",
  // Agent modal
  "Create Plan with Agent": "에이전트로 계획 작성",
  "Describe the trip in plain language, or attach a staff spreadsheet / itinerary PDF. The agent fills the draft for you.":
    "출장을 자유롭게 설명하거나 직원 명단(.xlsx)·일정표(.pdf)를 첨부하세요. 에이전트가 초안을 작성합니다.",
  "Draft preview": "초안 미리보기", "No draft": "초안 없음", "Send": "전송",
  "Model": "모델", "applies to all agents": "모든 에이전트에 적용", "Auto": "자동",
  "Create this plan": "이 계획 생성", "Submit report": "보고서 제출",
  // Detail / resume
  "Business Trip Plan": "출장 계획서", "Plan detail": "계획 상세", "Delete plan": "계획 삭제", "Close": "닫기",
  "Resume a session": "세션 이어하기",
  "Pick a saved draft to continue chatting or editing.": "이어서 작업할 임시저장을 선택하세요.",
  // Report modal
  "New Expense Report": "신규 지출 보고서",
  "Import an approved business trip plan, attach receipts, then review.": "승인된 출장 계획을 불러오고 영수증을 첨부한 뒤 검토하세요.",
  "Import": "불러오기", "Please select an approved plan to settle": "정산할 승인된 계획을 선택하세요",
  "Cost Items": "비용 항목",
  "Import a plan, then add receipts — the agent extracts expense lines from each PDF.":
    "계획을 불러온 뒤 영수증을 추가하세요 — 에이전트가 PDF에서 지출 항목을 추출합니다.",
  "Draft": "임시저장", "Create": "생성", "Delete": "삭제",
  "Cost": "비용", "Transportation": "교통비", "Etc": "기타",
  "+ Load Evidence": "+ 증빙 추가", "No items — add receipts.": "항목 없음 — 영수증을 추가하세요.",
  "Evidence": "증빙", "Description": "설명", "Note": "비고", "Vendor": "거래처",
  // Plan picker
  "Select Plan": "계획 선택",
  "Choose an approved business trip plan to settle. Only plans with approval complete can be settled.":
    "정산할 승인된 출장 계획을 선택하세요. 승인 완료된 계획만 정산할 수 있습니다.",
  "Search by title, document number, traveler, author, dept": "제목·문서번호·출장자·작성자·부서 검색",
  "Trip Date": "출장일", "Import selected": "선택 불러오기",
  // Audit detail modal
  "Re-run audit": "감사 재실행", "Download": "다운로드",
  "The 5 checks": "5가지 점검", "Expense lines reviewed — by report section": "검토된 지출 항목 — 보고서 섹션별",
  "Approval Gate": "승인 게이트", "Date Alignment": "날짜 일치", "Location Alignment": "장소 일치",
  "Amount Alignment": "금액 일치", "Receipt Backing": "영수증 증빙",
  "Was the trip plan approved before expenses were claimed?": "지출 청구 전에 출장 계획이 승인되었나요?",
  "Does every expense date fall inside the approved trip period?": "모든 지출 날짜가 승인된 출장 기간 내에 있나요?",
  "Do expense locations match the planned destination and route?": "지출 장소가 계획된 목적지·경로와 일치하나요?",
  "Is the claimed total within the plan’s budget?": "청구 총액이 계획 예산 이내인가요?",
  "Is every expense line backed by an uploaded receipt?": "모든 지출 항목에 업로드된 영수증이 있나요?",
  "Outside trip period": "출장 기간 외", "Unplanned location": "계획에 없는 장소", "No receipt": "영수증 없음",
  "Receipt": "영수증", "Check": "점검", "✓ ok": "✓ 정상", "Date": "날짜", "Route / Place": "경로 / 장소", "Amount": "금액",
  "Skip": "건너뛰기",
  "Admin": "관리자",
  "LLM Models": "LLM 모델",
  "Manage the language models your conversational agents can use. DB models are hot-registered live; config models are read-only.":
    "대화형 에이전트가 사용할 언어 모델을 관리합니다. DB 모델은 실시간 등록되며 config 모델은 읽기 전용입니다.",
  "Active model for agents": "에이전트 활성 모델",
  "All conversational sub-agents use this model. “Auto” lets each agent fall back to its own configured default.":
    "모든 대화형 서브 에이전트가 이 모델을 사용합니다. “자동”은 각 에이전트가 자체 기본값을 사용하도록 합니다.",
  "Auto — each agent’s default": "자동 — 각 에이전트 기본값",
  "Models": "모델", "+ New model": "+ 새 모델", "New model": "새 모델",
  "Name / key": "이름 / 키", "Model id": "모델 ID", "Base URL": "기본 URL",
  "API key": "API 키", "Auth scheme": "인증 방식", "API key header": "API 키 헤더",
  "Completions path": "완성 경로", "Temperature": "온도", "Max tokens": "최대 토큰",
  "Enabled (registered & usable by agents)": "활성화 (등록되어 에이전트가 사용 가능)",
  "Save model": "모델 저장", "Update model": "모델 수정", "Reset": "초기화",
  "This model comes from app config and can’t be edited here.": "이 모델은 앱 config에서 온 것으로 여기서 수정할 수 없습니다.",
  "Demo sample": "데모 샘플",
  "Download a sample travel-reservation PDF, then attach it in the agent or as a receipt to try the flow.":
    "샘플 출장 예약 PDF를 다운로드한 뒤 에이전트나 영수증으로 첨부해 흐름을 체험해 보세요.",
  "Download sample PDF": "샘플 PDF 다운로드",
  "There is still a pending choice — pick one to continue:": "아직 선택이 남아 있습니다 — 계속하려면 하나를 선택하세요:",
  "Analyst override": "분석가 수정", "Save verdict": "판정 저장",
  "Manually correct the compliance / confidence verdict of this audit.": "이 감사의 준수/신뢰도 판정을 수동으로 수정합니다.",
  "Audit report — CSV": "감사 보고서 — CSV", "Raw audit — JSON": "원본 감사 — JSON",
  "checks + expense lines, opens in Excel": "점검 + 지출 항목, 엑셀에서 열림",
  "full payload incl. expense lines": "지출 항목 포함 전체 데이터",
  // Master data modal
  "Master data for this corp — travellers and budget departments in trip plans are picked from here.":
    "이 법인의 마스터 데이터 — 출장 계획의 출장자와 예산 부서를 여기서 선택합니다.",
  "Departments": "부서 관리", "Staff": "직원", "New department name": "새 부서 이름", "+ Add": "+ 추가",
  "Click a department to filter its staff. A department with staff can’t be deleted.":
    "부서를 클릭하면 해당 직원만 표시됩니다. 직원이 있는 부서는 삭제할 수 없습니다.",
  "Name": "이름", "Position": "직급", "Department": "소속 부서", "Department…": "부서 선택…",
  "Save": "저장", "✎ Edit": "✎ 수정",
  // Empty / loading states
  "Loading…": "불러오는 중…",
  "No plans match this filter": "필터와 일치하는 계획이 없습니다",
  "Try a different search or status chip.": "다른 검색어나 상태 칩을 사용해 보세요.",
  "No business trip plans yet": "아직 출장 계획이 없습니다",
  "Create your first plan with the agent or manually.": "에이전트 또는 직접 작성으로 첫 계획을 만들어 보세요.",
  "No plans to review": "검토할 계획이 없습니다",
  "Requests appear here once a plan is submitted.": "계획이 제출되면 여기에 표시됩니다.",
  "No reports match this search": "검색과 일치하는 보고서가 없습니다",
  "Try different keywords.": "다른 키워드로 검색해 보세요.",
  "No expense reports yet": "아직 지출 보고서가 없습니다",
  "Import an approved plan and attach receipts to settle a trip.": "승인된 계획을 불러오고 영수증을 첨부해 정산하세요.",
  "No audits match this filter": "필터와 일치하는 감사가 없습니다",
  "Try a different search or compliance chip.": "다른 검색어나 준수 칩을 사용해 보세요.",
  "No audits yet": "아직 감사가 없습니다",
  "R10 runs automatically each time an expense report is created.": "지출 보고서가 생성될 때마다 R10이 자동 실행됩니다.",
  "Go to Expense Report": "지출 보고로 이동",
  "Confirm": "확인",
};
const I18N_REV = {};
Object.keys(I18N).forEach((k) => { I18N_REV[I18N[k]] = k; });

function i18nMapFor(lang) { return lang === "ko" ? I18N : I18N_REV; }

function i18nSwapText(node, map) {
  const raw = node.nodeValue;
  if (!raw) return;
  const t = raw.trim();
  if (!t || !map[t]) return;
  node.nodeValue = raw.replace(t, map[t]);
}

/* Translate every text node + placeholder/title/aria-label under root. */
function translateTree(root, lang) {
  if (!root) return;
  const map = i18nMapFor(lang);
  if (root.nodeType === 3) { i18nSwapText(root, map); return; }
  if (root.nodeType !== 1 && root.nodeType !== 11) return;
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  const nodes = [];
  while (walker.nextNode()) nodes.push(walker.currentNode);
  nodes.forEach((n) => i18nSwapText(n, map));
  const sel = "[placeholder],[title],[aria-label]";
  const els = root.querySelectorAll ? Array.from(root.querySelectorAll(sel)) : [];
  if (root.nodeType === 1 && root.matches && root.matches(sel)) els.push(root);
  els.forEach((el) => ["placeholder", "title", "aria-label"].forEach((attr) => {
    const v = el.getAttribute(attr);
    if (!v) return;
    const t = v.trim();
    if (map[t]) el.setAttribute(attr, v.replace(t, map[t]));
  }));
}

function setLanguage(lang) {
  lang = lang === "ko" ? "ko" : "en";
  // en→ko applies the dictionary; ko→en applies the reverse map. Re-applying
  // the active language is a no-op, so this is safe to call any time.
  translateTree(document.body, lang);
  LANG = lang;
  localStorage.setItem("bizplay.lang", LANG);
  document.documentElement.lang = LANG;
  document.querySelectorAll(".lang-btn").forEach((b) =>
    b.classList.toggle("active", b.getAttribute("data-lang") === LANG));
}

/* Keep dynamically rendered content (table rows, modals) translated in KOR mode. */
const i18nObserver = new MutationObserver((muts) => {
  if (LANG !== "ko") return;
  muts.forEach((m) => m.addedNodes.forEach((n) => translateTree(n, "ko")));
});

function initI18n() {
  document.querySelectorAll(".lang-btn").forEach((b) =>
    b.addEventListener("click", () => setLanguage(b.getAttribute("data-lang"))));
  i18nObserver.observe(document.body, { childList: true, subtree: true });
  if (LANG === "ko") setLanguage("ko");
}

/* ================================================================
 *  Role (Traveler / Admin) — gates which workflow tabs are visible.
 *  Traveler: request trip plans + settle expense reports.
 *  Admin:    approve plans + review compliance audits.
 * ================================================================ */
let ROLE = localStorage.getItem("bizplay.role") || "traveler";
const ROLE_TABS = { traveler: ["plan", "report"], admin: ["approve", "audit"] };

function roleAllows(tab) { return (ROLE_TABS[ROLE] || ROLE_TABS.traveler).includes(tab); }

function applyRole() {
  document.querySelectorAll(".tab").forEach((t) =>
    t.classList.toggle("hidden", !roleAllows(t.getAttribute("data-tab"))));
  document.querySelectorAll(".role-btn").forEach((b) =>
    b.classList.toggle("active", b.getAttribute("data-role") === ROLE));
  // Admin-only controls (e.g. the LLM Models button) show only in the admin view.
  document.querySelectorAll(".admin-only").forEach((el) => el.classList.toggle("hidden", ROLE !== "admin"));
  if (!roleAllows(currentTab)) showTab(ROLE_TABS[ROLE][0]);
}

function setRole(role) {
  role = role === "admin" ? "admin" : "traveler";
  if (role === ROLE) return;
  ROLE = role;
  localStorage.setItem("bizplay.role", ROLE);
  applyRole();
  toast(ROLE === "admin" ? "Admin view — approve plans & review audits." : "Traveler view — request trips & settle expenses.", "");
}

function initRole() {
  document.querySelectorAll(".role-btn").forEach((b) =>
    b.addEventListener("click", () => setRole(b.getAttribute("data-role"))));
  applyRole();
}

/* ================================================================
 *  LLM MODELS — admin CRUD over the ChatClient registry.
 *    GET/POST  /api/v1/agent-conversations/llm-models
 *    GET/PUT/DELETE  .../llm-models/{name}
 *  DB models are editable & hot-registered; CONFIG models are read-only.
 * ================================================================ */
// Built lazily (arrow evaluated at call time) because API_ORIGIN is declared
// further down the file — a top-level const referencing it here would hit the TDZ.
const llmUrl = (sub = "") => API_ORIGIN + "/api/v1/agent-conversations/llm-models" + sub;
const llmSettingsUrl = () => API_ORIGIN + "/api/v1/agent-conversations/llm-settings";
let llmModels = [];
let llmEditing = null;    // the model name currently loaded in the form, or null (= create)
let llmActiveModel = null;        // the active override ("" / null = Auto, each agent's default)
let llmAvailableModels = [];      // registered model names selectable as active

/* ---- Active-model selection (GET/PUT /llm-settings) ----
 * The same setting is surfaced in two places: the LLM Models modal (#llmActiveSelect)
 * and the agent composer (#agentModelSelect). Both are kept in sync. */
function renderModelSelectors() {
  const fill = (sel, autoLabel) => {
    if (!sel) return;
    sel.innerHTML = `<option value="">${esc(autoLabel)}</option>` +
      llmAvailableModels.map((m) => `<option value="${esc(m)}">${esc(m)}</option>`).join("");
    sel.value = llmActiveModel || "";
  };
  fill($("llmActiveSelect"), "Auto — each agent’s default");
  fill($("agentModelSelect"), "Auto");
}

async function fetchLlmSettings() {
  const res = await fetch(llmSettingsUrl());
  const json = await res.json().catch(() => ({}));
  if (!res.ok) throw apiError(json, res);
  const s = (json && (json.data || json.payload)) || {};
  llmActiveModel = s.activeModel || "";
  llmAvailableModels = s.availableModels || [];
}

/* Load current settings and refresh both selectors (LLM modal open/refresh, agent open). */
async function loadLlmSettings() {
  try { await fetchLlmSettings(); renderModelSelectors(); }
  catch (e) { const el = $("llmMsg"); if (el) el.textContent = friendlyError(e.message); }
}

async function setActiveLlm(model) {
  beginLoad();
  [$("llmActiveSelect"), $("agentModelSelect")].forEach((s) => { if (s) s.disabled = true; });
  try {
    const res = await fetch(llmSettingsUrl(), {
      method: "PUT", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ model: model || null }),
    });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    const s = (json && (json.data || json.payload)) || {};
    llmActiveModel = s.activeModel || "";
    if (s.availableModels) llmAvailableModels = s.availableModels;
    toast(llmActiveModel ? `Agents now use “${llmActiveModel}”.` : "Cleared — agents use their own defaults.", "ok");
    renderModelSelectors();
    renderLlmList();
  } catch (e) {
    toast("Couldn’t set active model: " + friendlyError(e.message), "err");
    renderModelSelectors();   // revert both selects to the last-known value
  } finally {
    [$("llmActiveSelect"), $("agentModelSelect")].forEach((s) => { if (s) s.disabled = false; });
    endLoad();
  }
}

async function loadLlmModels() {
  const box = $("llmList");
  box.innerHTML = `<div class="muted-pad"><span class="spin"></span>Loading…</div>`;
  beginLoad();
  try {
    const res = await fetch(llmUrl());
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    llmModels = (json && (json.data || json.payload)) || [];
    $("llmMsg").textContent = "";
  } catch (e) {
    llmModels = [];
    $("llmMsg").textContent = friendlyError(e.message);
  } finally {
    endLoad();
    renderLlmList();
  }
}

function renderLlmList() {
  $("llmCount").textContent = llmModels.length;
  const box = $("llmList");
  if (!llmModels.length) {
    box.innerHTML = `<div class="muted-pad">No models yet — add one with “New model”.</div>`;
    return;
  }
  box.innerHTML = llmModels.map((m) => {
    const src = (m.source || "DB").toUpperCase();
    const host = (m.baseUrl || "").replace(/^https?:\/\//, "").replace(/\/.*$/, "");
    return `<div class="llm-row ${m.name === llmEditing ? "md-active" : ""}" data-llm="${esc(m.name)}">
      <div class="llm-row-main">
        <span class="llm-name"><span class="reg-dot ${m.registered ? "on" : ""}" title="${m.registered ? "Registered" : "Not registered"}"></span>${esc(m.label || m.name)}</span>
        <span class="llm-sub" title="${esc(m.model || "")} · ${esc(host)}">${esc(m.model || m.name)}${host ? " · " + esc(host) : ""}</span>
      </div>
      <div class="llm-row-badges">
        ${m.name === llmActiveModel ? `<span class="badge-active">ACTIVE</span>` : ""}
        <span class="badge-src badge-${src === "CONFIG" ? "config" : "db"}">${esc(src)}</span>
        ${m.enabled ? "" : `<span class="badge-off">disabled</span>`}
      </div>
    </div>`;
  }).join("");
}

/* Reset the form to create-mode (blank). */
function llmFormReset() {
  llmEditing = null;
  $("llmSheet").classList.remove("llm-readonly");
  $("llmFormTitle").innerHTML = `<span class="b-ico">${svgIcon("sparkles")}</span> New model`;
  $("llmReadonlyNote").classList.add("hidden");
  $("llmName").disabled = false;
  ["llmName", "llmLabel", "llmModel", "llmBaseUrl", "llmApiKey", "llmApiKeyHeader", "llmCompletionsPath", "llmTemperature", "llmMaxTokens"].forEach((id) => { $(id).value = ""; });
  $("llmAuthScheme").value = "bearer";
  $("llmCompletionsPath").value = "/chat/completions";
  $("llmEnabled").checked = true;
  $("llmApiKey").placeholder = "Paste the API key";
  $("llmKeyHint").textContent = "";
  $("llmKeyReq").classList.remove("hidden");
  $("llmDeleteBtn").classList.add("hidden");
  $("llmSaveBtn").textContent = "Save model";
  $("llmMsg").textContent = "";
  renderLlmList();
}

/* Load a model into the form. CONFIG models are shown read-only. */
function llmFormLoad(name) {
  const m = llmModels.find((x) => x.name === name);
  if (!m) return;
  const readonly = (m.source || "").toUpperCase() === "CONFIG";
  llmEditing = name;
  $("llmSheet").classList.toggle("llm-readonly", readonly);
  $("llmFormTitle").innerHTML = `<span class="b-ico">${svgIcon("cpu")}</span> ${esc(m.label || m.name)}`;
  $("llmReadonlyNote").classList.toggle("hidden", !readonly);
  $("llmName").value = m.name || "";
  $("llmName").disabled = true;                     // name is the immutable key
  $("llmLabel").value = m.label || "";
  $("llmModel").value = m.model || "";
  $("llmBaseUrl").value = m.baseUrl || "";
  $("llmAuthScheme").value = ["bearer", "x-api-key"].includes(m.authScheme) ? m.authScheme : (m.authScheme ? "custom" : "bearer");
  $("llmApiKeyHeader").value = m.apiKeyHeader || "";
  $("llmCompletionsPath").value = m.completionsPath || "/chat/completions";
  $("llmTemperature").value = m.temperature ?? "";
  $("llmMaxTokens").value = m.maxTokens ?? "";
  $("llmEnabled").checked = !!m.enabled;
  // On edit the key is write-only: blank means "keep existing".
  $("llmApiKey").value = "";
  $("llmApiKey").placeholder = "•••• leave blank to keep existing key";
  $("llmKeyHint").textContent = m.apiKeyMasked ? `Stored key: ${m.apiKeyMasked}` : "No key stored.";
  $("llmKeyReq").classList.add("hidden");
  $("llmDeleteBtn").classList.toggle("hidden", readonly);
  $("llmSaveBtn").textContent = "Update model";
  $("llmMsg").textContent = "";
  renderLlmList();
}

/* Assemble the request body from the form. authScheme "custom" defers to apiKeyHeader. */
function llmReadForm() {
  const scheme = $("llmAuthScheme").value;
  const body = {
    label: $("llmLabel").value.trim() || null,
    model: $("llmModel").value.trim(),
    baseUrl: $("llmBaseUrl").value.trim(),
    authScheme: scheme === "custom" ? ($("llmApiKeyHeader").value.trim() || "bearer") : scheme,
    apiKeyHeader: $("llmApiKeyHeader").value.trim() || null,
    completionsPath: $("llmCompletionsPath").value.trim() || null,
    temperature: $("llmTemperature").value === "" ? null : Number($("llmTemperature").value),
    maxTokens: $("llmMaxTokens").value === "" ? null : parseInt($("llmMaxTokens").value, 10),
    enabled: $("llmEnabled").checked,
  };
  const key = $("llmApiKey").value;   // never trimmed — keys can contain spaces? keep raw
  if (key) body.apiKey = key;         // omit when blank so the server keeps the stored key
  return body;
}

async function llmSave() {
  const name = $("llmName").value.trim();
  const body = llmReadForm();
  // Required-field checks mirror the server (name/baseUrl/model on create).
  if (!name) { $("llmMsg").textContent = "Name is required."; return; }
  if (!body.baseUrl) { $("llmMsg").textContent = "Base URL is required."; return; }
  if (!body.model) { $("llmMsg").textContent = "Model id is required."; return; }
  if (!llmEditing && !body.apiKey) { $("llmMsg").textContent = "An API key is required to add a model."; return; }

  const btn = $("llmSaveBtn");
  btn.disabled = true; btn.textContent = llmEditing ? "Updating…" : "Saving…";
  beginLoad();
  try {
    let res;
    if (llmEditing) {
      res = await fetch(llmUrl("/" + encodeURIComponent(llmEditing)), {
        method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
      });
    } else {
      res = await fetch(llmUrl(), {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ ...body, name }),
      });
    }
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    toast(`Model “${name}” ${llmEditing ? "updated" : "added"}.`, "ok");
    await loadLlmModels();
    await loadLlmSettings();
    llmFormLoad(name);
  } catch (e) {
    $("llmMsg").textContent = (llmEditing ? "Update failed: " : "Create failed: ") + friendlyError(e.message);
  } finally {
    btn.disabled = false; btn.textContent = llmEditing ? "Update model" : "Save model";
    endLoad();
  }
}

async function llmDelete() {
  if (!llmEditing) return;
  const name = llmEditing;
  const ok = await confirmDialog({
    title: "Delete LLM model",
    message: `Delete “${name}”? It will be unregistered from the live registry and agents can no longer use it. This cannot be undone.`,
    confirmText: "Delete",
  });
  if (!ok) return;
  beginLoad();
  try {
    const res = await fetch(llmUrl("/" + encodeURIComponent(name)), { method: "DELETE" });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    toast(`Model “${name}” deleted.`, "ok");
    await loadLlmModels();
    await loadLlmSettings();
    llmFormReset();
  } catch (e) {
    $("llmMsg").textContent = "Delete failed: " + friendlyError(e.message);
  } finally {
    endLoad();
  }
}

function openLlm() { llmFormReset(); $("llmOverlay").classList.remove("hidden"); loadLlmSettings(); loadLlmModels(); }
function closeLlm() { $("llmOverlay").classList.add("hidden"); }
function refreshLlm() { loadLlmSettings(); loadLlmModels(); }

function initLlm() {
  $("openLlmBtn").addEventListener("click", openLlm);
  $("llmCloseBtn").addEventListener("click", closeLlm);
  $("llmCloseBtn2").addEventListener("click", closeLlm);
  $("llmOverlay").addEventListener("mousedown", (ev) => { if (ev.target === $("llmOverlay")) closeLlm(); });
  $("llmRefreshBtn").addEventListener("click", refreshLlm);
  $("llmActiveSelect").addEventListener("change", (ev) => setActiveLlm(ev.target.value));
  $("llmNewBtn").addEventListener("click", llmFormReset);
  $("llmCancelBtn").addEventListener("click", () => { llmEditing ? llmFormLoad(llmEditing) : llmFormReset(); });
  $("llmSaveBtn").addEventListener("click", llmSave);
  $("llmDeleteBtn").addEventListener("click", llmDelete);
  $("llmList").addEventListener("click", (ev) => {
    const row = ev.target.closest("[data-llm]");
    if (row) llmFormLoad(row.getAttribute("data-llm"));
  });
}

/* Demo-sample download banner — shown until the user dismisses it. */
function initDemoBanner() {
  const banner = $("demoBanner");
  if (!banner) return;
  if (localStorage.getItem("bizplay.demoBannerDismissed") !== "1") banner.classList.remove("hidden");
  $("demoBannerClose").addEventListener("click", () => {
    banner.classList.add("hidden");
    localStorage.setItem("bizplay.demoBannerDismissed", "1");
  });
  $("demoDownloadBtn").addEventListener("click", () => toast("Downloading sample-trip-reservation.pdf…", "ok"));
}

/* ---------- Global loading indicator ----------
 * A counter so overlapping fetches keep the bar visible until the last finishes. */
let loadCount = 0;
function beginLoad() { loadCount++; const b = document.getElementById("loadbar"); if (b) b.classList.remove("hidden"); }
function endLoad() { loadCount = Math.max(0, loadCount - 1); if (loadCount === 0) { const b = document.getElementById("loadbar"); if (b) b.classList.add("hidden"); } }
/* Inline SVG icon from the sprite in index.html (colored via currentColor). */
function svgIcon(name, cls) {
  return `<svg class="ico${cls ? " " + cls : ""}" aria-hidden="true"><use href="#i-${name}"/></svg>`;
}
/* Skeleton placeholder rows shown while a table loads. */
function loadingRow(cols, rows = 3) {
  const w = [96, 64, 120, 80, 56, 110, 72];
  let html = "";
  for (let r = 0; r < rows; r++) {
    html += "<tr>" + Array.from({ length: cols }, (_, c) =>
      `<td><span class="skel-bar" style="max-width:${w[(r + c) % w.length]}px"></span></td>`).join("") + "</tr>";
  }
  return html;
}
/* Rich table empty state: icon + headline + optional sub-line and CTA button. */
function emptyRow(cols, { icon = "inbox", title = "Nothing here yet", sub = "", action = "" } = {}) {
  return `<tr><td colspan="${cols}" class="empty-row"><div class="empty-state">
    ${svgIcon(icon, "ico-lg")}
    <span class="es-title">${title}</span>
    ${sub ? `<span class="es-sub">${sub}</span>` : ""}
    ${action}
  </div></td></tr>`;
}

/* The REST API runs on the Spring app (:8080). When this page is served from the
 * same origin (http://localhost:8080/web/) we use a relative path. If it is opened
 * from somewhere else (Live Server on :5500, etc.) we target :8080 directly — the
 * server adds a dev CORS header for /api/v1/plans so that still works.
 * Note: opening via file:// cannot reach the API (browsers block it) — use the URL. */
const IS_LOCAL_DEV_HOST = ["localhost", "127.0.0.1"].includes(location.hostname);
// localStorage "bizplay.apiOrigin" overrides (e.g. "http://localhost:8081", or "" for same-origin).
const API_ORIGIN = localStorage.getItem("bizplay.apiOrigin") ??
  (location.protocol === "file:" || (IS_LOCAL_DEV_HOST && location.port && location.port !== "8080")
    ? "http://localhost:8080"
    : "");
const API = API_ORIGIN + "/api/v1/plans";

/* Seed staff/departments (mirror src/test/data/seed_staff_department.sql for corp 1234567890).
 * In the traditional method these are picked manually from fixed master-data lists. */
const STAFF = [
  { name: "John Doe",      department: "Sales",           position: "Manager" },
  { name: "Mike Ross",     department: "Sales",           position: "Staff" },
  { name: "Rachel Zane",   department: "Sales",           position: "Associate" },
  { name: "Jane Smith",    department: "Marketing",       position: "Specialist" },
  { name: "Tom Hardy",     department: "Marketing",       position: "Lead" },
  { name: "Alice Johnson", department: "Engineering",     position: "Senior Engineer" },
  { name: "Bob Martin",    department: "Engineering",     position: "Engineer" },
  { name: "Charlie Park",  department: "Engineering",     position: "Intern" },
  { name: "David Kim",     department: "Finance",         position: "Analyst" },
  { name: "Emma Wilson",   department: "Finance",         position: "Controller" },
  { name: "Grace Lee",     department: "Human Resources", position: "HR Manager" },
  { name: "Henry Cho",     department: "Human Resources", position: "Recruiter" },
];
const DEPARTMENTS = [...new Set(STAFF.map((s) => s.department))];
const LOCATIONS = [
  "에이치비솔루션 본사", "에이치비솔루션 인천지사", "에이치비솔루션 구미지사",
  "Seoul", "Busan", "Incheon", "Gumi", "Daegu", "Daejeon", "Gwangju",
  "Incheon Airport", "Gimpo Airport", "Tokyo", "Taipei", "Osaka",
];

/* ---------------------------------------------------------------- *
 *  Dynamic trip type — mirrors Bizplay's 출장 목적 → 출장 구분 cascade.
 *  Picking a purpose repopulates the Classification select and re-labels
 *  the Destination field (Region / Country / Institution), like the real form.
 * ---------------------------------------------------------------- */
const TRIP_REGIONS = ["Seoul", "Busan", "Incheon", "Daegu", "Daejeon", "Gwangju", "Gumi", "Ulsan", "Sejong", "Jeju"];
const TRIP_COUNTRIES = ["Japan", "China", "Vietnam", "Cambodia", "Singapore", "USA", "Germany", "United Kingdom", "India", "Thailand", "Indonesia", "UAE"];
/* One entry per 출장 목적, mirroring the real Bizplay templates: each has its own
 * classification cascade, destination labelling, and template-specific extra fields.
 * Extra-field types: text | search | select | radio | textarea | richtext. */
const TRIP_TYPES = {
  "해외출장": {
    ko: "해외출장",
    classifications: [["장기", "장기 · Long-term"]],
    destLabel: "Country", destKo: "국가", destPlaceholder: "e.g. Japan, Cambodia", options: TRIP_COUNTRIES,
    hint: "Overseas trip — pick the destination country.",
    extra: [],
  },
  "국내출장": {
    ko: "국내출장",
    classifications: [["일반", "일반 · General"], ["시내출장", "시내출장 · In-city"]],
    destLabel: "Region / City", destKo: "지역", destPlaceholder: "e.g. Seoul, Busan", options: TRIP_REGIONS,
    hint: "Domestic trip — pick a region/city.",
    extra: [],
  },
  "테스트(유성린)": {
    ko: "테스트(유성린)",
    classifications: [["성린4", "성린4"]],
    destLabel: "Destination", destKo: "출장지", destPlaceholder: "Where to", options: [],
    hint: "Custom template — cost center + rich note.",
    extra: [
      { id: "costCenter", type: "search", label: "Cost center", ko: "코스트센터", placeholder: "Search cost center…" },
      { id: "html111", type: "richtext", label: "HTML111", ko: "", placeholder: "내용을 입력하세요." },
    ],
  },
  "lg입력항목": {
    ko: "lg입력항목",
    classifications: [["테스트", "테스트"], ["귀향교통비", "귀향교통비 · Return fare"]],
    destLabel: "Destination", destKo: "출장지", destPlaceholder: "Where to", options: [],
    hint: "Custom template — education & travel options.",
    extra: [
      { id: "eduType", type: "radio", label: "Education type", ko: "교육유형", options: ["사내교육 · In-house", "사외교육 · External"] },
      { id: "tripWay", type: "radio", label: "One-way / Round", ko: "편도/왕복", options: ["편도 · One-way", "왕복 · Round-trip"] },
      { id: "over90", type: "radio", label: "Duration", ko: "90일 기준", options: ["90일이하 · ≤ 90d", "90일초과 · > 90d"] },
      { id: "remarks", type: "textarea", label: "Remarks", ko: "비고", placeholder: "Additional notes…" },
    ],
  },
  "테스트": {
    ko: "테스트",
    classifications: [["테스트1", "테스트1"]],
    destLabel: "Destination", destKo: "출장지", destPlaceholder: "Where to", options: [],
    hint: "Custom template — minimal.",
    extra: [],
  },
  "해양조선 목적테스트(CWB)": {
    ko: "해양조선",
    classifications: [["일반", "일반 · General"]],
    destLabel: "Destination", destKo: "출장지", destPlaceholder: "Where to", options: [],
    hint: "Custom template — project code + cost center.",
    extra: [
      { id: "projectCode", type: "text", label: "Project code", ko: "과제코드", placeholder: "e.g. CWB-2026-001" },
      { id: "costCenter", type: "search", label: "Cost center", ko: "코스트센터", placeholder: "Search cost center…" },
    ],
  },
};

/* ================================================================
 * Live BizPlay catalog — Travel Purpose, Trip Type (segment) and the dynamic
 * form are RETRIEVED from the private BizPlay API (proxied by our backend)
 * instead of the hardcoded TRIP_TYPES mirror. TRIP_TYPES stays as the offline
 * fallback so the modal still works when the private API is unreachable.
 * ================================================================ */
const BZ_API_BASE = () => API_ORIGIN + "/api/v1/agent-conversations/bizplay";
let BZ_CORP_USER_ID = localStorage.getItem("bizplay.corpUserId") || "30447";
let bzCatalog = null;        // { purposeName: { purposeId, segments: [{segmentId, segmentName, label}] } }
const bzFormCache = {};      // "purposeId:segmentId" -> form response (paperId, paperName, fields)
let bzActiveCfg = null;      // extra-field cfg generated from the live form (extraFieldsSummary uses it)
let bzActiveFormMeta = null; // { paperId, paperName } of the currently rendered live form

async function loadBizplayCatalog() {
  try {
    const res = await fetch(`${BZ_API_BASE()}/purposes?corpUserId=${encodeURIComponent(BZ_CORP_USER_ID)}`);
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    const options = (json && (json.data || json.payload)) || [];
    if (!options.length) throw new Error("Empty purpose catalog.");
    const cat = {};
    options.forEach((o) => {
      const name = o.purposeName || "";
      if (!cat[name]) cat[name] = { purposeId: o.purposeId, segments: [] };
      if (o.segmentId != null) {
        cat[name].segments.push({ segmentId: o.segmentId, segmentName: o.segmentName || "", label: o.segmentName || o.label || "" });
      }
    });
    bzCatalog = cat;
    // Rebuild the Travel Purpose select from the live catalog (same markup/design).
    const sel = $("tripPurpose");
    const prev = sel.value;
    sel.innerHTML = `<option value="">Select a purpose…</option>` +
      Object.keys(cat).map((n) => `<option value="${esc(n)}">${esc(n)}</option>`).join("");
    if (prev && cat[prev]) sel.value = prev;
    console.info("[bizplay] live catalog loaded:", Object.keys(cat).length, "purposes");
    // Preload the staff roster so live traveler pickers list real corp users immediately.
    bzLoadRoster().then(() => { if (bzActiveFormMeta) renderTravelers(); })
      .catch((e) => console.warn("[bizplay] roster preload failed:", e.message));
  } catch (e) {
    console.warn("[bizplay] live catalog unavailable — using built-in fallback:", e.message);
    bzCatalog = null;
  }
}

/* Item types BizPlay renders as a per-traveler "신청" (apply) toggle. */
const BZ_APPLY_TYPES = ["EXPENSE_BEYOND_BSTR_PERIOD", "DAILY_COST", "FUEL_COST", "REQUEST_STAFF_LODGE"];

/* Map a live form's field spec onto the designed renderers, split the way the
 * REAL BizPlay UI places them (paperItemOrderDto.travelerItemUsed):
 *   travelerItem=false -> once at form level (the GROUP_TITLE section)
 *   travelerItem=true  -> inside EACH traveler card
 * Basics and the trip period already have dedicated widgets, so they're skipped. */
function bzFieldsToCfg(form) {
  const skip = (f) => String(f.key || "").startsWith("basic:") || f.type === "BSTR_PERIOD";
  const mk = (f) => {
    let type = "text";
    if (BZ_APPLY_TYPES.includes(f.type)) type = "apply";
    else if (f.type === "HTML") type = "richtext";
    else if (f.type === "COST_CENTER") type = "search";
    else if (f.options && f.options.length) type = f.options.length <= 3 ? "radio" : "select";
    return {
      id: f.key, type,
      label: (f.label || f.key) + (f.required ? " *" : ""),
      rawLabel: f.label || f.key, required: !!f.required,
      bzType: f.type,
      ko: "", options: f.options || [], placeholder: "",
    };
  };
  const custom = (form.fields || []).filter((f) => !skip(f));
  return {
    ko: form.sectionTitle || form.paperName || "",
    extra: custom.filter((f) => !f.travelerItem).map(mk),
    travelerExtra: custom.filter((f) => f.travelerItem).map(mk),
  };
}

/* ---- Per-itemType widgets matching the REAL BizPlay card (lg입력항목 etc.) ----
 * a = value-collection attribute: "data-xf" (form level) or "data-bztf" (traveler).
 * Returns null for types without a dedicated widget (caller falls back to text). */
const BZ_YN_TYPES = ["PARTNER_SUPPORT", "NATIONAL_PROJECT", "NOTEBOOK_EXPORT"];
function bzWidgetHtml(f, a, uid) {
  const t = f.bzType;
  if (!t) return null;
  const key = esc(f.id);
  const name = `bzw-${uid}-${String(f.id).replace(/[^a-z0-9]/gi, "")}`;
  const radios = (nm, attr, opts, checkedIdx) => `<div class="bzw-radios">` + opts.map((v, i) =>
    `<label><input type="radio" name="${nm}" ${attr} value="${esc(v)}" ${i === checkedIdx ? "checked" : ""}/><span>${esc(v)}</span></label>`).join("") + `</div>`;
  if (BZ_YN_TYPES.includes(t)) {
    return radios(name, `${a}="${key}"`, ["Yes", "No"], 1);
  }
  if (t === "OVERSEAS_INSURANCE") {
    return `<div class="bzw-sub">항공권</div>` + radios(name, `${a}="${key}"`, ["편도", "왕복"], 0);
  }
  if (t === "BSTR_LINKED_LEAVE") {
    return `<div class="bzw-group" ${a}-group="${key}">
      <div class="bzw-row"><input type="date" data-part="시작"/><span class="bzw-tilde">~</span><input type="date" data-part="종료"/></div>
      <div class="bzw-row"><input type="text" data-part="체류지역" placeholder="체류지역을 입력하세요"/><input type="text" data-part="체류목적" data-primary placeholder="체류목적을 입력하세요"/></div>
    </div>`;
  }
  if (t === "EDUCATION_INFO") {
    return `<div class="bzw-group" ${a}-group="${key}">
      <div class="bzw-sub">구분</div>
      ${radios(name + "k", `data-part="구분"`, ["사내교육(인화원 교육 포함)", "사외교육"], 0)}
      <div class="bzw-row"><div class="search-field"><input type="text" data-part="교육과정" placeholder="교육과정을 검색하세요"/><span class="search-field-ico">${svgIcon("search")}</span></div><input type="text" data-part="교육클래스" placeholder="교육클래스를 입력하세요"/></div>
      <div class="bzw-row"><input type="date" data-part="교육시작일"/><span class="bzw-tilde">~</span><input type="date" data-part="교육종료일"/></div>
      <input type="text" data-part="교육기관" placeholder="교육기관을 입력하세요"/>
      <textarea data-part="내용" data-primary rows="2" placeholder="내용을 입력하세요"></textarea>
    </div>`;
  }
  if ((t === "BSTR_SELECT" || t === "SITE_BSTR_TYPE") && (!f.options || !f.options.length)) {
    // Options live server-side (itemList is empty for this corp) — placeholder select;
    // 여부-style items get the obvious 예/아니오 pair so required ones stay fillable.
    const opts = /여부/.test(f.rawLabel || "") ? ["예", "아니오"] : [];
    return `<div class="select-wrap"><select ${a}="${key}"><option value="">선택하세요.</option>${opts.map((o) => `<option>${o}</option>`).join("")}</select></div>`;
  }
  return null;
}

/* Aggregate a composite widget's parts into one readable saved value. Radio parts
 * always have a checked default, so the group only counts as filled when at least
 * one typed part has content. */
function bzReadGroup(g) {
  const hasReal = [...g.querySelectorAll("[data-part]")].some((el) => el.type !== "radio" && (el.value || "").trim());
  if (!hasReal) return "";
  const parts = [];
  g.querySelectorAll("[data-part]").forEach((el) => {
    if (el.type === "radio") { if (el.checked) parts.push(el.getAttribute("data-part") + ": " + el.value); return; }
    const v = (el.value || "").trim();
    if (v) parts.push(el.getAttribute("data-part") + ": " + v);
  });
  return parts.join(" / ");
}

/* Per-traveler custom items (travelerItemUsed=true), rendered inside each card
 * like the real BizPlay form: "apply" items as a 신청 toggle, the rest compact. */
function bzTravelerFieldsHtml(t) {
  const list = (bzActiveCfg && bzActiveCfg.travelerExtra) || [];
  if (!list.length) return "";
  return `<div class="field span-2"><div class="trav-extras">` + list.map((f) => {
    if (f.type === "apply") {
      return `<label class="trav-extra trav-extra-apply"><input type="checkbox" data-bztf="${esc(f.id)}" data-id="${t.id}" /><span>${esc(f.label)} · 신청</span></label>`;
    }
    // Dedicated widget per itemType (Yes/No radios, 교육정보 composite, …) — like the real card.
    const widget = bzWidgetHtml(f, "data-bztf", "t" + t.id);
    if (widget) {
      const wide = f.bzType === "EDUCATION_INFO" || f.bzType === "BSTR_LINKED_LEAVE";
      return `<div class="trav-extra ${wide ? "trav-extra-wide" : ""}"><span class="trav-extra-label">${esc(f.label)}</span>${widget}</div>`;
    }
    if (f.options && f.options.length) {
      return `<div class="trav-extra"><span class="trav-extra-label">${esc(f.label)}</span><div class="select-wrap"><select data-bztf="${esc(f.id)}" data-id="${t.id}"><option value="">Select…</option>${f.options.map((o) => `<option>${esc(o)}</option>`).join("")}</select></div></div>`;
    }
    const isSearch = f.type === "search";
    return `<div class="trav-extra"><span class="trav-extra-label">${esc(f.label)}</span><div class="${isSearch ? "search-field" : ""}"><input type="text" data-bztf="${esc(f.id)}" data-id="${t.id}" placeholder="${esc(f.label)}" />${isSearch ? `<span class="search-field-ico">${svgIcon("search")}</span>` : ""}</div></div>`;
  }).join("") + `</div></div>`;
}

/* Classification chosen → fetch the live form for (purpose, segment) and render
 * its custom items with the EXISTING extra-field renderer (design unchanged). */
async function bzOnClassificationChange() {
  const purposeName = $("tripPurpose").value;
  const live = bzCatalog && bzCatalog[purposeName];
  if (!live) return;   // fallback mode — TRIP_TYPES already rendered everything
  const clsSel = $("tripClassification");
  if (!clsSel.value) { bzActiveCfg = null; bzActiveFormMeta = null; renderExtraFields(null, false); return; }
  const opt = clsSel.selectedOptions && clsSel.selectedOptions[0];
  const sid = opt ? (opt.getAttribute("data-sid") || "") : "";
  const key = `${live.purposeId}:${sid}`;
  try {
    let form = bzFormCache[key];
    if (!form) {
      $("tripTypeHint").textContent = "Loading form from BizPlay…";
      const url = `${BZ_API_BASE()}/form?purposeId=${encodeURIComponent(live.purposeId)}${sid ? `&segmentId=${encodeURIComponent(sid)}` : ""}`;
      const res = await fetch(url);
      const json = await res.json().catch(() => ({}));
      if (!res.ok) throw apiError(json, res);
      form = (json && (json.data || json.payload)) || null;
      if (!form) throw new Error("Empty form response.");
      bzFormCache[key] = form;
    }
    bzActiveFormMeta = { paperId: form.paperId, paperName: form.paperName };
    bzActiveCfg = bzFieldsToCfg(form);
    $("tripTypeHint").textContent = `Form "${form.paperName}" loaded live from BizPlay (paper ${form.paperId}).`;
    renderExtraFields(bzActiveCfg, false);
    renderTravelers();   // per-traveler items (travelerItemUsed=true) render inside the cards
    updateTripDetailVisibility();
  } catch (e) {
    $("tripTypeHint").textContent = "Could not load the BizPlay form: " + friendlyError(e.message);
  }
}

/* Best-effort map an agent-extracted purpose (English or Korean) onto a template key. */
function normalizePurpose(p) {
  if (!p) return "";
  if (TRIP_TYPES[p]) return p;
  const s = String(p).toLowerCase();
  if (/해외|overseas|international|abroad/.test(s)) return "해외출장";
  if (/국내|domestic|in-country/.test(s)) return "국내출장";
  return "";
}

/* Repopulate Classification + relabel Destination when the purpose changes.
 * keepClass preserves the current classification if it's still valid (used on
 * re-render / draft load); otherwise it resets. */
function applyTripType(keepClass) {
  const purposeName = $("tripPurpose").value;
  const live = bzCatalog && bzCatalog[purposeName];
  const cfg = TRIP_TYPES[purposeName];
  const clsSel = $("tripClassification");
  const prev = keepClass ? clsSel.value : "";
  bzActiveCfg = null;
  bzActiveFormMeta = null;
  if (live) {
    // LIVE mode: classifications come from the BizPlay segments; the extra fields
    // render after the classification is picked (the form is per purpose+segment).
    clsSel.disabled = false;
    const segs = live.segments.length ? live.segments
      : [{ segmentId: "", segmentName: "-", label: "(no classification)" }];
    clsSel.innerHTML = `<option value="">Select…</option>` +
      segs.map((s) => `<option value="${esc(s.segmentName || "-")}" data-sid="${esc(String(s.segmentId ?? ""))}">${esc(s.label || s.segmentName || "-")}</option>`).join("");
    if (prev && [...clsSel.options].some((o) => o.value === prev)) clsSel.value = prev;
    $("tripTypeHint").textContent = "Loaded live from BizPlay — pick a classification to load its form.";
    // Destination labelling: reuse the designed labels when the static mirror has
    // this purpose; otherwise sensible defaults.
    const d = cfg || {};
    $("tripDestLabel").innerHTML = `${esc(d.destLabel || "Destination")} <span class="req">*</span> <span class="ko-label">${esc(d.destKo || "출장지")}</span>`;
    $("tripDestination").placeholder = d.destPlaceholder || "Where to";
    $("destOptions").innerHTML = (d.options || []).map((o) => `<option value="${esc(o)}">`).join("");
    renderExtraFields(null, false);
    renderTravelers();   // clear per-traveler extras until the new form loads
    if (clsSel.value) bzOnClassificationChange();
    updateTripDetailVisibility();
    return;
  }
  if (!cfg) {
    clsSel.innerHTML = `<option value="">Select a purpose first…</option>`;
    clsSel.disabled = true;
    $("tripTypeHint").textContent = "";
    $("tripDestLabel").innerHTML = `Destination <span class="req">*</span> <span class="ko-label" id="tripDestKo">출장지</span>`;
    $("tripDestination").placeholder = "e.g. Busan";
    $("destOptions").innerHTML = "";
    renderExtraFields(null, false);
    updateTripDetailVisibility();
    return;
  }
  clsSel.disabled = false;
  clsSel.innerHTML = `<option value="">Select…</option>` +
    cfg.classifications.map(([v, label]) => `<option value="${esc(v)}">${esc(label)}</option>`).join("");
  if (prev && cfg.classifications.some(([v]) => v === prev)) clsSel.value = prev;
  $("tripTypeHint").textContent = cfg.hint;
  $("tripDestLabel").innerHTML = `${esc(cfg.destLabel)} <span class="req">*</span> <span class="ko-label">${esc(cfg.destKo)}</span>`;
  $("tripDestination").placeholder = cfg.destPlaceholder;
  $("destOptions").innerHTML = cfg.options.map((o) => `<option value="${esc(o)}">`).join("");
  renderExtraFields(cfg, keepClass);
  updateTripDetailVisibility();
}

/* Render the template-specific fields for a purpose. keepValues=true preserves any
 * values already entered (used when only the classification changed). */
function renderExtraFields(cfg, keepValues) {
  const wrap = $("tripExtraFields");
  const prev = keepValues ? readExtraFields() : {};
  const fields = (cfg && cfg.extra) || [];
  $("tripExtraSection").classList.toggle("hidden", fields.length === 0);
  if (!fields.length) { wrap.innerHTML = ""; return; }
  $("tripExtraTitle").textContent = "Additional Information";
  $("tripExtraKo").textContent = cfg.ko || "";
  wrap.innerHTML = fields.map((f) => {
    const v = prev[f.id] != null ? prev[f.id] : "";
    const lab = `<label>${esc(f.label)}${f.ko ? ` <span class="ko-label">${esc(f.ko)}</span>` : ""}</label>`;
    let control = "";
    const bzw = bzWidgetHtml(f, "data-xf", "x");
    if (bzw) {
      control = bzw;   // dedicated per-itemType widget (Yes/No, 교육정보 composite, …)
    } else if (f.type === "search") {
      control = `<div class="search-field"><input type="text" data-xf="${esc(f.id)}" value="${esc(v)}" placeholder="${esc(f.placeholder || "")}" />
        <span class="search-field-ico">${svgIcon("search")}</span></div>`;
    } else if (f.type === "select") {
      control = `<div class="select-wrap"><select data-xf="${esc(f.id)}"><option value="">Select…</option>${
        f.options.map((o) => `<option value="${esc(o)}" ${o === v ? "selected" : ""}>${esc(o)}</option>`).join("")}</select></div>`;
    } else if (f.type === "radio") {
      control = `<div class="seg">${f.options.map((o) =>
        `<label class="seg-opt"><input type="radio" name="xf-${esc(f.id)}" data-xf="${esc(f.id)}" value="${esc(o)}" ${o === v ? "checked" : ""} /><span>${esc(o)}</span></label>`).join("")}</div>`;
    } else if (f.type === "textarea") {
      control = `<textarea data-xf="${esc(f.id)}" maxlength="500" placeholder="${esc(f.placeholder || "")}">${esc(v)}</textarea>`;
    } else if (f.type === "richtext") {
      control = `<div class="rte"><div class="rte-toolbar" data-xf-tb="${esc(f.id)}">
        <button type="button" class="rte-btn" data-cmd="bold" title="Bold"><b>B</b></button>
        <button type="button" class="rte-btn" data-cmd="italic" title="Italic"><i>I</i></button>
        <button type="button" class="rte-btn" data-cmd="underline" title="Underline"><u>U</u></button>
        <button type="button" class="rte-btn" data-cmd="insertUnorderedList" title="List">• List</button>
      </div><div class="rte-body" contenteditable="true" data-xf="${esc(f.id)}" data-placeholder="${esc(f.placeholder || "")}">${v}</div></div>`;
    } else {
      control = `<input type="text" data-xf="${esc(f.id)}" value="${esc(v)}" maxlength="200" placeholder="${esc(f.placeholder || "")}" />`;
    }
    return `<div class="field span-2">${lab}${control}</div>`;
  }).join("");
}

/* Collect extra-field values into { id: value }. */
function readExtraFields() {
  const out = {};
  $("tripExtraFields").querySelectorAll("[data-xf]").forEach((el) => {
    const id = el.getAttribute("data-xf");
    if (el.type === "radio") { if (el.checked) out[id] = el.value; }
    else if (el.isContentEditable) out[id] = rteText(el);
    else out[id] = (el.value || "").trim();
  });
  // Composite widgets (교육정보, 출장 연계 휴가, …): one aggregated value per group.
  $("tripExtraFields").querySelectorAll("[data-xf-group]").forEach((g) => {
    const v = bzReadGroup(g);
    if (v) out[g.getAttribute("data-xf-group")] = v;
  });
  return out;
}

/* Human-readable summary of the extra fields, appended to Content on submit so the
 * template-specific values are captured (our backend has no columns for them). */
function extraFieldsSummary() {
  const cfg = bzActiveCfg || TRIP_TYPES[$("tripPurpose").value];
  if (!cfg || !cfg.extra || !cfg.extra.length) return "";
  const vals = readExtraFields();
  const lines = cfg.extra.map((f) => (vals[f.id] ? `${f.ko || f.label}: ${vals[f.id]}` : null)).filter(Boolean);
  return lines.length ? `\n\n[${cfg.ko}]\n${lines.join("\n")}` : "";
}

/* Bizplay flow: the rest of the form is revealed only after BOTH the purpose and
 * the classification are chosen. Changing the purpose clears the classification,
 * so the detail collapses back to the placeholder until it's re-picked. */
function updateTripDetailVisibility() {
  const ready = !!$("tripPurpose").value && !!$("tripClassification").value;
  $("tripDetail").classList.toggle("hidden", !ready);
  $("tripDetailPlaceholder").classList.toggle("hidden", ready);
}

/* In-memory traveler models for the open form. */
let travelers = [];
let travelerSeq = 0;
/* Which traveler's route popup is open (id), and a resolver used by the "complete" loop. */
let activeRouteTravelerId = null;
let routeResolve = null;

const $ = (id) => document.getElementById(id);

/* ---------------------------------------------------------------- *
 *  Motion (GSAP core) — restrained, product-appropriate.
 *  Honors prefers-reduced-motion and degrades to no-op if GSAP is absent,
 *  so content is always visible by default (never gated on a reveal).
 * ---------------------------------------------------------------- */
const MOTION = typeof gsap !== "undefined" &&
  !window.matchMedia("(prefers-reduced-motion: reduce)").matches;

/* Stagger table rows in after a fresh data load (not on every filter keystroke). */
function animateRowsIn(tbodyId) {
  if (!MOTION) return;
  const rows = document.querySelectorAll("#" + tbodyId + " > tr");
  if (!rows.length) return;
  gsap.fromTo(rows,
    { opacity: 0, y: 6 },
    { opacity: 1, y: 0, duration: 0.3, ease: "power2.out", stagger: { each: 0.022, amount: Math.min(0.5, rows.length * 0.022) }, overwrite: true, clearProps: "transform,opacity" });
}

/* Soft crossfade + rise when a tab becomes visible. */
function animatePane(name) {
  if (!MOTION) return;
  gsap.fromTo("#tab-" + name,
    { opacity: 0, y: 8 },
    { opacity: 1, y: 0, duration: 0.32, ease: "power3.out", overwrite: true, clearProps: "transform,opacity" });
}

/* ---------------------------------------------------------------- *
 *  In-app confirmation dialog (replaces window.confirm)
 *  Usage: if (await confirmDialog({ title, message, confirmText })) { ... }
 * ---------------------------------------------------------------- */
let confirmResolver = null;
function confirmDialog({ title = "Confirm", message = "", confirmText = "Delete", danger = true } = {}) {
  $("confirmTitle").textContent = title;
  $("confirmMsg").textContent = message;
  const ok = $("confirmOkBtn");
  ok.textContent = confirmText;
  ok.className = "btn " + (danger ? "btn-danger" : "btn-primary");
  $("confirmOverlay").classList.remove("hidden");
  setTimeout(() => ok.focus(), 0);
  return new Promise((resolve) => { confirmResolver = resolve; });
}
function resolveConfirm(result) {
  if ($("confirmOverlay").classList.contains("hidden")) return;
  $("confirmOverlay").classList.add("hidden");
  const r = confirmResolver; confirmResolver = null;
  if (r) r(result);
}

/* ---------------------------------------------------------------- *
 *  List page
 * ---------------------------------------------------------------- */
/* All plans for the active corp; the table view is filtered client-side
   (the demo has no search/status query API). */
let plansCache = [];
let planFilter = "all";   // all | request | approved | cancelled

function planStatusKey(p) {
  const s = p.approvalStatus || "Request for approval";
  if (s === "Approval complete") return "approved";
  if (s === "Business trip cancellation") return "cancelled";
  return "request";
}
function planMatchesSearch(p, q) {
  if (!q) return true;
  const t0 = (p.travelers && p.travelers[0]) || {};
  const hay = [
    p.title, p.purpose, p.destination, t0.name, t0.department, "2026-출장계획서", p.id,
    ...((p.travelers || []).map((t) => t.name)),
  ].filter(Boolean).join(" ").toLowerCase();
  return hay.includes(q);
}

async function loadPlans() {
  const body = $("plansBody");
  body.innerHTML = loadingRow(10);
  beginLoad();
  try {
    const res = await fetch(`${API}?corpNo=${encodeURIComponent(CORP_NO)}`);
    const json = await res.json();
    // API envelope is { success, message, data }.
    plansCache = (json && (json.data || json.payload)) || [];
    applyPlanFilters();
    animateRowsIn("plansBody");
  } catch (e) {
    plansCache = [];
    body.innerHTML = emptyRow(10, { icon: "alert", title: "Couldn’t load plans", sub: esc(friendlyError(e.message)) });
    updateChipCounts();
  } finally {
    endLoad();
  }
}

/* Status chip counts always reflect the full corp dataset, not the filtered view. */
function updateChipCounts() {
  let req = 0, app = 0, can = 0;
  plansCache.forEach((p) => {
    const k = planStatusKey(p);
    if (k === "approved") app++; else if (k === "cancelled") can++; else req++;
  });
  $("cAll").textContent = plansCache.length;
  $("cReq").textContent = req;
  $("cApp").textContent = app;
  $("cCan").textContent = can;
}

/* Apply chip + search + cancelled-only filters, then render. */
function applyPlanFilters() {
  updateChipCounts();
  const q = ($("planSearch").value || "").trim().toLowerCase();
  const cancelOnly = $("cancelOnly").checked;
  const list = plansCache.filter((p) => {
    const k = planStatusKey(p);
    if (cancelOnly && k !== "cancelled") return false;
    if (planFilter !== "all" && k !== planFilter) return false;
    return planMatchesSearch(p, q);
  });
  list.sort((a, b) => String(b.createdAt || "").localeCompare(String(a.createdAt || "")));
  renderPlans(list);
  $("rowCount").textContent = `${list.length} ${list.length === 1 ? "entry" : "entries"}`;
  $("planShown").textContent = list.length;
  $("planTotal").textContent = plansCache.length;
}

function renderPlans(plans) {
  const body = $("plansBody");
  if (!plans.length) {
    body.innerHTML = plansCache.length
      ? emptyRow(10, { icon: "search", title: "No plans match this filter", sub: "Try a different search or status chip." })
      : emptyRow(10, { icon: "inbox", title: "No business trip plans yet", sub: "Create your first plan with the agent or manually.",
          action: `<button class="btn btn-primary btn-sm" onclick="openAgent()">${svgIcon("sparkles")} Create with Agent</button>` });
    updateSelectionBar();
    return;
  }
  body.innerHTML = plans.map((p, i) => {
    const t0 = (p.travelers && p.travelers[0]) || {};
    const extra = p.travelers && p.travelers.length > 1 ? ` 외 ${p.travelers.length - 1}명` : "";
    const traveler = (t0.name || "—") + extra;
    const dept = t0.department || "—";
    const periodFull = p.businessPeriod || joinDates(p.businessStartDate, p.businessEndDate) || "—";
    const period = formatPeriod(p);
    const orig = t0.origin || "—";
    const dest = t0.destination || p.destination || "—";
    const route = (t0.origin || t0.destination)
      ? `<span class="leg">${esc(orig)}</span><i class="arr">→</i><span class="leg">${esc(dest)}</span>`
      : `<span class="leg">${esc(p.destination || "—")}</span>`;
    const docNo = `2026-출장계획서-${shortNo(p.id, plans.length - i)}`;
    const tag = planStatusKey(p) === "cancelled" ? `<span class="tag-cancel">cancellation</span> ` : "";
    return `<tr class="row-click" data-plan="${esc(p.id)}" style="--i:${i}">
      <td class="c-check"><input type="checkbox" class="chk row-chk" data-id="${esc(p.id)}" data-title="${esc(p.title || docNo)}" aria-label="Select row" /></td>
      <td class="c-no">${i + 1}</td>
      <td title="${esc(docNo)}"><span class="doc-no">${esc(docNo)}</span></td>
      <td class="c-title" title="${esc(p.title || "")}">${tag}${esc(p.title || "—")}</td>
      <td class="c-author" title="${esc(t0.name || "")}">${esc(t0.name || "—")}</td>
      <td class="c-trav" title="${esc(traveler)}">${esc(traveler)}</td>
      <td class="c-dept" title="${esc(dept)}">${esc(dept)}</td>
      <td><span class="purpose-tag" title="${esc(p.purpose || "")}">${esc(shortPurpose(p.purpose))}</span></td>
      <td class="c-period" title="${esc(periodFull)}">${esc(period)}</td>
      <td title="${esc(orig + " → " + dest)}"><span class="route-line route-pass">${route}</span></td>
    </tr>`;
  }).join("");
  updateSelectionBar();
}

/* ---------------------------------------------------------------- *
 *  Bulk selection + batch delete  (DELETE /api/v1/plans/batch)
 * ---------------------------------------------------------------- */
function selectedRowChecks() {
  return Array.from(document.querySelectorAll(".row-chk")).filter((c) => c.checked);
}
function updateSelectionBar() {
  const all = Array.from(document.querySelectorAll(".row-chk"));
  const checked = all.filter((c) => c.checked);
  $("selCount").textContent = checked.length;
  $("selectionBar").classList.toggle("hidden", checked.length === 0);
  // Reflect select-all tri-state.
  const head = $("selectAllChk");
  if (head) {
    head.checked = all.length > 0 && checked.length === all.length;
    head.indeterminate = checked.length > 0 && checked.length < all.length;
  }
}
function toggleSelectAll() {
  const on = $("selectAllChk").checked;
  document.querySelectorAll(".row-chk").forEach((c) => { c.checked = on; });
  updateSelectionBar();
}
function clearSelection() {
  document.querySelectorAll(".row-chk").forEach((c) => { c.checked = false; });
  const head = $("selectAllChk"); if (head) { head.checked = false; head.indeterminate = false; }
  updateSelectionBar();
}
async function deleteSelectedPlans() {
  const checks = selectedRowChecks();
  const ids = checks.map((c) => c.getAttribute("data-id"));
  if (!ids.length) return;
  const label = ids.length === 1
    ? `“${checks[0].getAttribute("data-title")}”`
    : `${ids.length} plans`;
  const ok = await confirmDialog({
    title: "Remove business trip plan" + (ids.length === 1 ? "" : "s"),
    message: `Remove ${label}? This also deletes their travelers and attachments and cannot be undone.`,
    confirmText: "Remove",
  });
  if (!ok) return;

  const btn = $("selDeleteBtn");
  btn.disabled = true; btn.textContent = "Removing…";
  beginLoad();
  try {
    const res = await fetch(`${API}/batch`, {
      method: "DELETE",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ids }),
    });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    const r = (json && (json.data || json.payload)) || {};
    const deleted = r.deleted != null ? r.deleted : ids.length;
    const missing = (r.notFoundIds && r.notFoundIds.length) ? ` (${r.notFoundIds.length} not found)` : "";
    toast(`Removed ${deleted} plan${deleted === 1 ? "" : "s"}${missing}.`, "ok");
    loadPlans();
  } catch (e) {
    toast("Remove failed: " + friendlyError(e.message), "err");
  } finally {
    btn.disabled = false; btn.innerHTML = svgIcon("trash") + " Remove";
    endLoad();
  }
}

/* Short purpose label for the table chip (full text shown on hover). */
function shortPurpose(p) {
  if (!p) return "—";
  return p.replace(/\s*business trip\s*/i, "").trim() || p;
}
/* Compact "2026-06-20 → 06-25" period; drops the repeated year when it matches. */
function formatPeriod(p) {
  let s = p.businessStartDate, e = p.businessEndDate;
  if (!s || !e) {
    const m = (p.businessPeriod || "").split(/\s+to\s+/i);
    if (m.length === 2) { s = m[0].trim(); e = m[1].trim(); }
    else return p.businessPeriod || "—";
  }
  s = String(s); e = String(e);
  if (s.slice(0, 4) === e.slice(0, 4)) return `${s} → ${e.slice(5)}`;
  return `${s} → ${e}`;
}

/* ---------------------------------------------------------------- *
 *  Create modal
 * ---------------------------------------------------------------- */
function openCreate() {
  // reset
  travelers = [];
  travelerSeq = 0;
  $("tripPurpose").value = "";
  $("startDate").value = "";
  $("endDate").value = "";
  $("tripDestination").value = "";
  $("tripTitle").value = "";
  $("tripContent").value = "";
  updateCharCounts();
  applyTripType(false);   // reset the dependent Classification select + Destination labels
  $("attachmentList").innerHTML = attachPlaceholder();
  attachments = [];
  $("validationSummary").textContent = "";
  clearInvalid();
  addTraveler();           // start with one traveler, like the screenshots
  // Chat pane starts hidden ("Create manually"); openAgent() turns it on.
  $("agentThread").innerHTML = "";
  resetAgent();
  setChatPane(false);
  $("createOverlay").classList.remove("hidden");
}
function closeCreate() { $("createOverlay").classList.add("hidden"); }

/* ---- Travelers ---- */
function addTraveler() {
  travelerSeq += 1;
  travelers.push({
    id: travelerSeq, name: "", department: "", position: "",
    origin: "", destination: "", returnPoint: "",
  });
  renderTravelers();
}
function removeTraveler(id) {
  travelers = travelers.filter((t) => t.id !== id);
  if (travelers.length === 0) addTraveler();
  renderTravelers();
}
function renderTravelers() {
  $("travelerCount").textContent = travelers.length;
  const staffOpts = (sel) => {
    const list = liveStaffList();
    // Agent-resolved BizPlay names may not exist in the local demo staff list —
    // add such a name as its own option so the selection stays visible.
    const extra = sel && !list.some((s) => s.name === sel)
      ? `<option value="${esc(sel)}" selected>${esc(sel)}</option>` : "";
    return `<option value="">Select Traveler…</option>` + extra +
      list.map((s) => `<option value="${esc(s.name)}" ${s.name === sel ? "selected" : ""}>${esc(s.name)} (${esc(s.department)} · ${esc(s.position)})</option>`).join("");
  };
  const deptOpts = (sel) =>
    `<option value="">Select Budget Department…</option>` +
    liveDeptNames().map((d) => `<option value="${esc(d)}" ${d === sel ? "selected" : ""}>${esc(d)}</option>`).join("");

  // LIVE BizPlay form active: the real traveler card holds ONLY the traveler picker
  // plus the form's own travelerItem fields — no Budget Department / Travel Route
  // (those exist nowhere in the retrieved paper). The picker lists the REAL BizPlay
  // roster (corporationUserId values) so manual saves fan out one document per traveler.
  const bzStaffOpts = (t) => {
    const roster = bzApproval.roster;
    const isSel = (u) => (t.bzId != null && String(t.bzId) === String(u.id)) || (!t.bzId && t.name === u.name);
    const known = roster.some(isSel);
    return `<option value="">Select Traveler…</option>` +
      (t.name && !known ? `<option value="" selected>${esc(t.name)}</option>` : "") +
      roster.map((u) => `<option value="${esc(String(u.id))}" ${isSel(u) ? "selected" : ""}>${esc(u.name)} (${esc(u.dept || "-")} · ${esc(u.position || "-")})</option>`).join("");
  };
  const liveCard = (t, idx) => `
    <div class="trav-card" data-id="${t.id}">
      <div class="trav-head">
        <span class="trav-num">${idx + 1}</span>
        <span class="trav-label">Traveller ${idx + 1}</span>
        <button class="trav-x" data-act="remove" data-id="${t.id}" title="Remove">✕</button>
      </div>
      <div class="trav-body">
        <div class="field span-2">
          <label>Traveller <span class="req">✦</span> <span class="ko-label">출장자</span></label>
          <div class="select-wrap"><select data-field="name" data-id="${t.id}">${bzStaffOpts(t)}</select></div>
        </div>
        ${bzTravelerFieldsHtml(t)}
      </div>
    </div>`;

  const designedCard = (t, idx) => `
    <div class="trav-card" data-id="${t.id}">
      <div class="trav-head">
        <span class="trav-num">${idx + 1}</span>
        <span class="trav-label">Traveller ${idx + 1}</span>
        <button class="trav-x" data-act="remove" data-id="${t.id}" title="Remove">✕</button>
      </div>
      <div class="trav-body">
        <div class="field">
          <label>Traveller <span class="req">✦</span></label>
          <div class="select-wrap"><select data-field="name" data-id="${t.id}">${staffOpts(t.name)}</select></div>
        </div>
        <div class="field">
          <label>Budget Department <span class="req">✦</span></label>
          <div class="select-wrap"><select data-field="department" data-id="${t.id}">${deptOpts(t.department)}</select></div>
        </div>
        <div class="field span-2">
          <label>Travel Route <span class="req">✦</span></label>
          <div class="route-trigger ${t.origin ? "" : "route-empty"}" data-act="route" data-id="${t.id}">
            ${t.origin
              ? `<span class="route-pass"><span class="leg">${esc(t.origin)}</span><i class="arr">→</i><span class="leg">${esc(t.destination || "?")}</span><i class="arr">→</i><span class="leg">${esc(t.returnPoint || "?")}</span></span>`
              : `<span>Set departure · destination · arrival</span>`}
            <span class="route-set">✎ ${t.origin ? "Edit" : "Set"} route</span>
          </div>
        </div>
        ${bzTravelerFieldsHtml(t)}
      </div>
    </div>`;

  const card = bzActiveFormMeta ? liveCard : designedCard;
  $("travelerList").innerHTML = travelers.map((t, idx) => card(t, idx)).join("");
}

/* ---- Attachments (URL only, to stay simple) ---- */
let attachments = [];
function attachPlaceholder() {
  return `<div class="att-placeholder">
      <div class="folder">${svgIcon("folder", "ico-lg")}</div><div>No reference documents attached</div>
      <div class="ap-sub">Register a URL to attach an itinerary</div>
    </div>`;
}
function addUrl() {
  const url = prompt("Reference document URL (e.g. https://example.com/itinerary.pdf):");
  if (!url) return;
  attachments.push({ Type: "URL", URL: url.trim() });
  renderAttachments();
}
function renderAttachments() {
  const box = $("attachmentList");
  if (!attachments.length) { box.innerHTML = attachPlaceholder(); return; }
  box.innerHTML = attachments.map((a, i) =>
    `<div class="att-row">${svgIcon("link")} <a href="${esc(a.URL)}" target="_blank" rel="noopener">${esc(a.URL)}</a>
       <button class="att-remove" data-rm="${i}" title="Remove">✕</button></div>`).join("");
}

/* ---------------------------------------------------------------- *
 *  Route Setup popup — returns a Promise that resolves true(saved)/false(cancel)
 * ---------------------------------------------------------------- */
function openRoute(travelerId, note) {
  const t = travelers.find((x) => x.id === travelerId);
  if (!t) return Promise.resolve(false);
  activeRouteTravelerId = travelerId;
  const who = t.name || `Traveler ${travelers.indexOf(t) + 1}`;
  $("routeFor").textContent = note || `Set the route for ${who}. Departure, destination and arrival are all required.`;
  $("routeDeparture").value = t.origin || "";
  $("routeDestination").value = t.destination || "";
  $("routeArrival").value = t.returnPoint || "";
  $("routeError").textContent = "";
  $("routeOverlay").classList.remove("hidden");
  return new Promise((resolve) => { routeResolve = resolve; });
}
function saveRoute() {
  const t = travelers.find((x) => x.id === activeRouteTravelerId);
  if (!t) return closeRoute(false);
  const dep = $("routeDeparture").value.trim();
  const dest = $("routeDestination").value.trim();
  const arr = $("routeArrival").value.trim();
  if (!dep || !dest || !arr) {
    $("routeError").textContent = "Departure, destination and arrival are all required.";
    return;
  }
  t.origin = dep; t.destination = dest; t.returnPoint = arr;
  renderTravelers();
  closeRoute(true);
}
function closeRoute(saved) {
  $("routeOverlay").classList.add("hidden");
  const r = routeResolve; routeResolve = null; activeRouteTravelerId = null;
  if (r) r(!!saved);
}

/* ---------------------------------------------------------------- *
 *  Validation — mirrors RequiredFieldValidationServiceImple, then
 *  loops the Route Setup popup until every traveler has a route.
 * ---------------------------------------------------------------- */
function readTripFields() {
  return {
    purpose: $("tripPurpose").value.trim(),
    start: $("startDate").value,
    end: $("endDate").value,
    destination: $("tripDestination").value.trim(),
    title: $("tripTitle").value.trim(),
    content: $("tripContent").value.trim(),
    classification: $("tripClassification").value.trim(),
  };
}

/* Plain text of a contenteditable field (used by template rich-text extras like HTML111). */
function rteText(el) { return (el.innerText || "").replace(/ /g, " ").trim(); }

/* "(n/50)"-style live counters on Title and Content, matching the real form. */
function updateCharCounts() {
  $("tripTitleCount").textContent = `(${$("tripTitle").value.length}/50)`;
  $("tripContentCount").textContent = `(${$("tripContent").value.length}/500)`;
}

/* Validate trip-level fields. Returns a list of missing-field messages. */
function validateTrip(trip) {
  const missing = [];
  clearInvalid();
  if (!trip.purpose) { missing.push("travel purpose"); mark("tripPurpose"); }
  if (!trip.classification) { missing.push("travel classification"); mark("tripClassification"); }
  if (!trip.start || !trip.end) { missing.push("travel dates"); if (!trip.start) mark("startDate"); if (!trip.end) mark("endDate"); }
  else if (trip.end < trip.start) { missing.push("end date must be on/after start date"); mark("endDate"); }
  if (!trip.destination) { missing.push("trip destination"); mark("tripDestination"); }
  if (!trip.title) { missing.push("title"); mark("tripTitle"); }
  if (!trip.content) { missing.push("content"); mark("tripContent"); }
  if (!travelers.length) missing.push("at least one traveler");
  travelers.forEach((t) => {
    if (!t.name) missing.push(`traveler #${travelers.indexOf(t) + 1} name`);
    if (!t.department) missing.push(`budget department for ${t.name || "a traveler"}`);
  });
  return missing;
}

/* The "popup to choose until it meets all required fields" behaviour:
 * walk every traveler missing an origin and force the Route Setup popup,
 * one after another, until all are filled or the user cancels. */
async function ensureAllRoutes() {
  for (const t of travelers) {
    if (!t.origin || !t.destination || !t.returnPoint) {
      const who = t.name || `Traveler ${travelers.indexOf(t) + 1}`;
      const saved = await openRoute(t.id, `Route required for ${who}. Set departure, destination and arrival to continue.`);
      if (!saved) return false; // user cancelled -> abort completion
    }
  }
  return true;
}

/* LIVE BizPlay mode: validate only what the RETRIEVED form actually requires —
 * the designed extras (Budget Department, Travel Route) don't exist in it. */
function validateLiveTrip(trip) {
  const missing = [];
  clearInvalid();
  if (!trip.purpose) { missing.push("Travel Purpose (출장 목적)"); mark("tripPurpose"); }
  if (!trip.classification) { missing.push("Travel Classification (출장 구분)"); mark("tripClassification"); }
  if (!trip.start || !trip.end) {
    missing.push("Trip period (출장기간)");
    if (!trip.start) mark("startDate");
    if (!trip.end) mark("endDate");
  } else if (trip.end < trip.start) {
    missing.push("end date must be on/after start date");
    mark("endDate");
  }
  if (!trip.destination) { missing.push("Destination (출장지)"); mark("tripDestination"); }
  if (!trip.title) { missing.push("Title (제목)"); mark("tripTitle"); }
  if (!trip.content) { missing.push("Content (내용)"); mark("tripContent"); }
  if (!travelers.length || travelers.some((t) => !t.name)) missing.push("Traveler (출장자)");
  // Required custom items of the live form: form-level (data-xf) + per traveler card (data-bztf).
  const cfg = bzActiveCfg || { extra: [], travelerExtra: [] };
  const xf = readExtraFields();
  cfg.extra.filter((f) => f.required).forEach((f) => {
    if (!(xf[f.id] || "").trim()) missing.push(f.rawLabel);
  });
  cfg.travelerExtra.filter((f) => f.required).forEach((f) => {
    const empty = [...document.querySelectorAll(".trav-card")].some((card) => {
      const g = card.querySelector(`[data-bztf-group="${f.id}"]`);
      if (g) return !bzReadGroup(g);
      const els = [...card.querySelectorAll(`[data-bztf="${f.id}"]`)];
      if (!els.length) return true;
      if (els[0].type === "checkbox") return false;
      if (els[0].type === "radio") return !els.some((el) => el.checked);
      return !(els[0].value || "").trim();
    });
    if (empty) missing.push(f.rawLabel);
  });
  return missing;
}

async function completeCreate() {
  const trip = readTripFields();

  // LIVE BizPlay form: validate against the retrieved form, then continue into the
  // real save flow (Set approval order -> 출장계획확인 -> POST ③) instead of the demo save.
  if (bzActiveFormMeta) {
    const liveMissing = validateLiveTrip(trip);
    if (liveMissing.length) {
      $("validationSummary").textContent = "Missing required: " + liveMissing.join(", ");
      toast("Missing required: " + liveMissing.join(", "), "err");
      return;
    }
    $("validationSummary").textContent = "";
    // Saving FROM THE FORM always saves what is on screen (agent-filled values live in
    // the DOM too, plus any manual edits) — the manual path rebuilds the ③ draft from
    // the retrieved form server-side. The chat chip remains the session-driven save.
    bzOpenApprovalFlow(true);
    return;
  }

  // 1) trip-level + traveler identity must be valid first
  let missing = validateTrip(trip);
  if (missing.length) {
    $("validationSummary").textContent = "Missing required: " + missing.join(", ");
    toast("Missing required: " + missing.join(", "), "err");
    return;
  }

  // 2) keep popping the Route Setup dialog until every traveler has a full route
  const ok = await ensureAllRoutes();
  if (!ok) {
    $("validationSummary").textContent = "Every traveler needs a route (departure / destination / arrival).";
    return;
  }

  // 3) re-validate after routes (safety) and build the payload
  missing = validateTrip(trip);
  const routeMissing = travelers.filter((t) => !t.origin);
  if (missing.length || routeMissing.length) {
    $("validationSummary").textContent = "Still missing required fields. Please review.";
    return;
  }
  $("validationSummary").textContent = "";

  const payload = {
    CorpNo: CORP_NO,
    PlanType: PLAN_TYPE,
    TripInformation: {
      Purpose: trip.purpose,
      BusinessPeriod: `${trip.start} to ${trip.end}`,
      Destination: trip.destination,
      Title: trip.title,
      Content: (trip.content + extraFieldsSummary()).slice(0, 500),
      BusinessTripClassification: trip.classification,
      Travelers: travelers.map((t) => ({
        Name: t.name,
        Department: t.department,
        Position: t.position,
        Origin: t.origin,
        Destination: t.destination || trip.destination,
        ReturnPoint: t.returnPoint,
      })),
    },
    Attachemnt: attachments,
  };

  // 4) POST
  const btns = [$("createCompleteBtn"), $("createCompleteBtn2")];
  btns.forEach((b) => (b.disabled = true));
  try {
    const res = await fetch(API, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) {
      // Error bodies are either the ApiResponse {message} or a ProblemDetail {detail/title}.
      throw apiError(json, res);
    }
    toast("Business trip plan created.", "ok");
    closeCreate();
    loadPlans();
  } catch (e) {
    $("validationSummary").textContent = "Create failed: " + friendlyError(e.message);
    toast("Create failed: " + friendlyError(e.message), "err");
  } finally {
    btns.forEach((b) => (b.disabled = false));
  }
}

/* ---------------------------------------------------------------- *
 *  Helpers
 * ---------------------------------------------------------------- */
function esc(s) {
  return String(s == null ? "" : s).replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

/* ---------------------------------------------------------------- *
 *  API error normalization
 *  Backends can return raw MyBatis dumps ("### Error updating database…"),
 *  PostgreSQL exceptions (incl. Korean 오류 text), FK/unique violations, or
 *  network failures. friendlyError() collapses those into one short, human
 *  sentence so the UI never shows a stack trace. Idempotent: passing an
 *  already-clean message through again is safe.
 * ---------------------------------------------------------------- */
function friendlyError(msg, status) {
  let s = String(msg == null ? "" : msg).trim();
  if (!s) return status ? `Request failed (HTTP ${status}).` : "Request failed.";

  // Network / connectivity (fetch rejects before any response).
  if (/failed to fetch|networkerror|load failed|err_connection|err_network/i.test(s)) {
    const hint = location.protocol === "file:"
      ? " Open the page at http://localhost:8080/web/ instead of a file:// path."
      : " The API server may be offline — check that the app is running on :8080.";
    return "Can’t reach the server." + hint;
  }

  // Foreign-key violation on corp_no → corp: the corp isn't seeded in the DB.
  if (/corp_no_fkey/i.test(s) || (/corp_no/i.test(s) && /(foreign key|참조키|fkey)/i.test(s))) {
    const m = s.match(/\(corp_no\)=\(([^)]*)\)/);
    const corp = (m && m[1]) || CORP_NO;
    return `Corp “${corp}” isn’t registered in the database, so nothing could be saved. Seed this corp in the “corp” table, or switch to a corp that already exists.`;
  }
  // Generic FK violation (some other referenced row is missing).
  if (/(foreign key|참조키).*(violat|위배)|(violat|위배).*(foreign key|참조키)/i.test(s)) {
    return "This references a record that doesn’t exist yet. Make sure the related data is set up first.";
  }
  // Unique / duplicate key.
  if (/duplicate key|unique constraint|already exists|중복/i.test(s)) {
    return "A record with these details already exists.";
  }
  // Not-null violation.
  if (/not-null|null value in column|널 값|NOT NULL/i.test(s)) {
    const m = s.match(/column ["“]?([a-z_]+)["”]?/i);
    return "A required field is missing" + (m ? ` (${m[1]}).` : ".");
  }

  // Strip MyBatis/PSQL scaffolding down to the most meaningful line.
  if (/###|PSQLException|SQLException|nested exception|org\.springframework|오류:/i.test(s)) {
    const detail = s.match(/Detail:\s*([^\n;#]+)/i);
    const cause = s.match(/Cause:[^:]*Exception:?\s*([^\n#]+)/i);
    let core = (detail && detail[1]) || (cause && cause[1]) || s.split(/\n|###/)[0] || s;
    core = core.replace(/\s+/g, " ").trim();
    if (core.length > 180) core = core.slice(0, 177) + "…";
    return "Server error: " + (core || "the request could not be completed.");
  }

  // Plain message — just tidy whitespace and cap length.
  s = s.replace(/\s+/g, " ").trim();
  if (s.length > 240) s = s.slice(0, 237) + "…";
  return s;
}

/* Build a cleaned Error from a failed fetch Response + parsed body. */
function apiError(json, res, fallback) {
  const raw = json && (json.message || json.detail || json.title || json.error);
  const status = res && res.status;
  return new Error(friendlyError(raw || fallback || "", status));
}
function joinDates(a, b) { return a && b ? `${a} ~ ${b}` : (a || b || ""); }
function shortNo(id, fallback) {
  if (!id) return fallback;
  const n = parseInt(String(id).replace(/\D/g, "").slice(-2) || "0", 10);
  return n || fallback;
}
function mark(id) { const el = $(id); if (el) el.classList.add("invalid"); }
function clearInvalid() { document.querySelectorAll(".invalid").forEach((el) => el.classList.remove("invalid")); }
let toastTimer;
function toast(msg, kind) {
  const el = $("toast");
  el.textContent = msg;
  el.className = "toast " + (kind || "");
  if (MOTION) {
    // xPercent:-50 preserves the CSS translateX(-50%) centering that GSAP's transform would otherwise clobber.
    gsap.fromTo(el, { xPercent: -50, y: 14, opacity: 0 }, { xPercent: -50, y: 0, opacity: 1, duration: 0.34, ease: "power3.out", overwrite: true });
  }
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.add("hidden"), 3200);
}

/* ---------------------------------------------------------------- *
 *  Wiring
 * ---------------------------------------------------------------- */
function init() {
  // --- Editable corp number (persisted + reloads the active tab on change) ---
  const corpInput = $("corpInput");
  corpInput.value = CORP_NO;
  const applyCorp = () => {
    const next = corpInput.value.trim();
    if (!next || next === CORP_NO) { corpInput.value = CORP_NO; return; }
    CORP_NO = next;
    localStorage.setItem("bizplay.corpNo", CORP_NO);
    reloadActiveTab();
    loadMasterDataSilent();
    toast(`Switched to corp ${CORP_NO}.`, "");
  };
  corpInput.addEventListener("change", applyCorp);
  corpInput.addEventListener("keydown", (ev) => { if (ev.key === "Enter") { ev.preventDefault(); corpInput.blur(); } });

  $("locations").innerHTML = LOCATIONS.map((l) => `<option value="${esc(l)}">`).join("");

  $("openCreateBtn").addEventListener("click", openCreate);
  $("closeSheetBtn").addEventListener("click", closeCreate);
  $("addTravelerBtn").addEventListener("click", addTraveler);
  $("addUrlBtn").addEventListener("click", addUrl);
  $("createCompleteBtn").addEventListener("click", completeCreate);
  $("createCompleteBtn2").addEventListener("click", completeCreate);
  $("tempSaveBtn").addEventListener("click", () => toast("Saved to temporary storage (demo only).", ""));
  $("tempSaveBtn2").addEventListener("click", () => toast("Saved to temporary storage (demo only).", ""));

  // Route popup buttons
  $("routeCheckBtn").addEventListener("click", saveRoute);
  $("routeCancelBtn").addEventListener("click", () => closeRoute(false));
  $("routeCloseBtn").addEventListener("click", () => closeRoute(false));

  // BizPlay save flow: Set approval order -> 출장계획확인 preview -> POST ③.
  $("bzStaffSearchIco").innerHTML = svgIcon("search");
  $("bzStaffSearch").addEventListener("input", bzRenderStaffRows);
  $("bzDeptFilter").addEventListener("change", bzRenderStaffRows);
  $("bzStaffRows").addEventListener("click", (ev) => {
    const tr = ev.target.closest("tr[data-uid]");
    if (tr) bzToggleLine(isNaN(Number(tr.dataset.uid)) ? tr.dataset.uid : Number(tr.dataset.uid));
  });
  $("bzLineList").addEventListener("click", (ev) => {
    const x = ev.target.closest("[data-x]");
    if (x) bzToggleLine(isNaN(Number(x.dataset.x)) ? x.dataset.x : Number(x.dataset.x));
  });
  $("bzLineList").addEventListener("change", (ev) => {
    const sel = ev.target.closest("select[data-kind]");
    if (!sel) return;
    const line = bzApproval.lines.find((l) => String(l.id) === String(sel.dataset.kind));
    if (line) line.kind = sel.value;
  });
  $("bzApprovalCloseBtn").addEventListener("click", () => $("bzApprovalOverlay").classList.add("hidden"));
  $("bzApprovalCancelBtn").addEventListener("click", () => $("bzApprovalOverlay").classList.add("hidden"));
  $("bzApprovalCheckBtn").addEventListener("click", bzShowPreview);
  $("bzPreviewCloseBtn").addEventListener("click", () => $("bzPreviewOverlay").classList.add("hidden"));
  $("bzPreviewCancelBtn").addEventListener("click", () => $("bzPreviewOverlay").classList.add("hidden"));
  $("bzPreviewEditLineBtn").addEventListener("click", () => {
    $("bzPreviewOverlay").classList.add("hidden");
    $("bzApprovalOverlay").classList.remove("hidden");
  });
  $("bzPreviewSaveBtn").addEventListener("click", bzSubmitCreate);

  // Dynamic trip type: purpose drives the Classification options + Destination labels;
  // the rest of the form appears only once both are chosen.
  $("tripPurpose").addEventListener("change", () => applyTripType(false));
  $("tripClassification").addEventListener("change", () => { updateTripDetailVisibility(); bzOnClassificationChange(); });
  // Replace the hardcoded purpose/type catalog with the live one from the private BizPlay API.
  loadBizplayCatalog();

  // Live "(n/50)" character counters on Title and Content, like the real form.
  ["tripTitle", "tripContent"].forEach((id) => $(id).addEventListener("input", updateCharCounts));
  // Delegated toolbar for dynamically-rendered extra rich-text fields (e.g. HTML111).
  $("tripExtraFields").addEventListener("mousedown", (ev) => {
    const btn = ev.target.closest(".rte-toolbar .rte-btn");
    if (!btn) return;
    ev.preventDefault();
    const body = btn.closest(".rte").querySelector(".rte-body");
    if (body) body.focus();
    document.execCommand(btn.getAttribute("data-cmd"), false, null);
  });

  // Date hint
  const upd = () => {
    const s = $("startDate").value, e = $("endDate").value;
    $("periodHint").textContent = s && e ? `(Trip Period: ${dayCount(s, e)} day(s))` : "";
  };
  $("startDate").addEventListener("change", upd);
  $("endDate").addEventListener("change", upd);

  // Delegated events inside the traveler list
  $("travelerList").addEventListener("click", (ev) => {
    const act = ev.target.closest("[data-act]");
    if (!act) return;
    const id = Number(act.getAttribute("data-id"));
    if (act.getAttribute("data-act") === "remove") removeTraveler(id);
    if (act.getAttribute("data-act") === "route") openRoute(id);
  });
  $("travelerList").addEventListener("change", (ev) => {
    const sel = ev.target.closest("[data-field]");
    if (!sel) return;
    const id = Number(sel.getAttribute("data-id"));
    const field = sel.getAttribute("data-field");
    const t = travelers.find((x) => x.id === id);
    if (!t) return;
    // LIVE mode traveler picker: option value is the BizPlay corporationUserId.
    if (field === "name" && bzActiveFormMeta) {
      const u = bzApproval.roster.find((r) => String(r.id) === sel.value);
      t.bzId = u ? u.id : null;
      t.name = u ? u.name : "";
      renderTravelers();
      return;
    }
    t[field] = sel.value;
    if (field === "name") {
      const staff = liveStaffList().find((s) => s.name === sel.value);
      if (staff) { t.position = staff.position; if (!t.department) t.department = staff.department; }
      renderTravelers();
    }
  });

  // Attachment remove (delegated)
  $("attachmentList").addEventListener("click", (ev) => {
    const rm = ev.target.closest("[data-rm]");
    if (!rm) return;
    attachments.splice(Number(rm.getAttribute("data-rm")), 1);
    renderAttachments();
  });

  // Row click -> detail (ignore clicks on the checkbox cell)
  $("plansBody").addEventListener("click", (ev) => {
    if (ev.target.closest(".c-check")) return;
    const tr = ev.target.closest(".row-click");
    if (!tr) return;
    openDetail(tr.getAttribute("data-plan"));
  });
  // Row checkbox toggled -> refresh the bulk-selection toolbar
  $("plansBody").addEventListener("change", (ev) => {
    if (ev.target.classList.contains("row-chk")) updateSelectionBar();
  });
  // Bulk selection toolbar
  $("selectAllChk").addEventListener("change", toggleSelectAll);
  $("selClearBtn").addEventListener("click", clearSelection);
  $("selDeleteBtn").addEventListener("click", deleteSelectedPlans);

  // Status filter chips + client-side search
  $("planChips").addEventListener("click", (ev) => {
    const c = ev.target.closest(".statchip");
    if (!c) return;
    planFilter = c.getAttribute("data-pf");
    document.querySelectorAll("#planChips .statchip").forEach((x) => x.classList.toggle("active", x === c));
    applyPlanFilters();
  });
  $("planSearch").addEventListener("input", () => {
    $("planSearchClear").classList.toggle("hidden", !$("planSearch").value);
    applyPlanFilters();
  });
  $("planSearchClear").addEventListener("click", () => {
    $("planSearch").value = "";
    $("planSearchClear").classList.add("hidden");
    applyPlanFilters();
    $("planSearch").focus();
  });
  $("cancelOnly").addEventListener("change", applyPlanFilters);
  $("detailCloseBtn").addEventListener("click", closeDetail);
  $("detailCloseBtn2").addEventListener("click", closeDetail);
  $("detailDeleteBtn").addEventListener("click", deleteCurrentPlan);

  // --- Resume ---
  $("openResumeBtn").addEventListener("click", openResume);
  $("resumeCloseBtn").addEventListener("click", closeResume);
  $("resumeList").addEventListener("click", (ev) => {
    const it = ev.target.closest("[data-sid]");
    if (it) loadSession(it.getAttribute("data-sid"));
  });

  // --- Agent chat (lives inside the Create Trip Plan modal) ---
  $("openAgentBtn").addEventListener("click", openAgent);
  $("chatToggleBtn").addEventListener("click", toggleChatPane);
  $("agentSendBtn").addEventListener("click", sendAgent);
  $("agentModelSelect").addEventListener("change", (ev) => setActiveLlm(ev.target.value));
  $("agentFileInput").addEventListener("change", onAgentFiles);
  $("agentFiles").addEventListener("click", (ev) => {
    const rm = ev.target.closest("[data-fchip]");
    if (!rm) return;
    agent.pending.splice(Number(rm.getAttribute("data-fchip")), 1);
    renderAgentFiles();
  });
  // Enter to send (Shift+Enter = newline)
  $("agentInput").addEventListener("keydown", (ev) => {
    // Ctrl+Space: predefined prompt-flow templates.
    if (ev.code === "Space" && ev.ctrlKey) { ev.preventDefault(); togglePromptMenu(); return; }
    if (!$("promptMenu").classList.contains("hidden")) {
      if (ev.key === "ArrowDown") { ev.preventDefault(); movePromptSel(1); return; }
      if (ev.key === "ArrowUp") { ev.preventDefault(); movePromptSel(-1); return; }
      if (ev.key === "Enter") { ev.preventDefault(); pickPrompt(promptSel); return; }
      if (ev.key === "Escape") { ev.preventDefault(); closePromptMenu(); return; }
    }
    if (ev.key === "Enter" && !ev.shiftKey) { ev.preventDefault(); sendAgent(); }
  });
  $("promptMenu").addEventListener("mousedown", (ev) => {
    const item = ev.target.closest(".prompt-item");
    if (item) { ev.preventDefault(); pickPrompt(Number(item.dataset.idx)); }
  });
  document.addEventListener("click", (ev) => {
    if (!ev.target.closest(".composer")) closePromptMenu();
  });

  // --- Tabs ---
  document.querySelector(".tabbar-inner").addEventListener("click", (ev) => {
    const t = ev.target.closest(".tab");
    if (t) showTab(t.getAttribute("data-tab"));
  });

  // --- Approve tab ---
  $("approveRefresh").addEventListener("click", loadApprovals);
  $("approveChips").addEventListener("click", (ev) => {
    const c = ev.target.closest(".statchip");
    if (!c) return;
    approveFilterKey = c.getAttribute("data-af");
    document.querySelectorAll("#approveChips .statchip").forEach((x) => x.classList.toggle("active", x === c));
    renderApprovals();
  });
  $("approveSearch").addEventListener("input", () => {
    $("approveSearchClear").classList.toggle("hidden", !$("approveSearch").value);
    renderApprovals();
  });
  $("approveSearchClear").addEventListener("click", () => {
    $("approveSearch").value = "";
    $("approveSearchClear").classList.add("hidden");
    renderApprovals();
    $("approveSearch").focus();
  });
  $("approveBody").addEventListener("click", (ev) => {
    const a = ev.target.closest("[data-approve]");
    const c = ev.target.closest("[data-cancel]");
    if (a) { setApproval(a.getAttribute("data-approve"), "Approval complete"); return; }
    if (c) {
      confirmDialog({
        title: "Cancel business trip",
        message: "Cancel this business trip plan? It will be marked as a trip cancellation.",
        confirmText: "Cancel trip",
      }).then((ok) => { if (ok) setApproval(c.getAttribute("data-cancel"), "Business trip cancellation"); });
      return;
    }
    // Anywhere else on the row opens the plan detail.
    const row = ev.target.closest("[data-plan-row]");
    if (row) openDetail(row.getAttribute("data-plan-row"));
  });

  // --- Report tab (structured) ---
  $("reportRefresh").addEventListener("click", loadReports);
  $("reportSearch").addEventListener("input", () => {
    $("reportSearchClear").classList.toggle("hidden", !$("reportSearch").value);
    renderReports();
  });
  $("reportSearchClear").addEventListener("click", () => {
    $("reportSearch").value = "";
    $("reportSearchClear").classList.add("hidden");
    renderReports();
    $("reportSearch").focus();
  });
  $("openReportCreateBtn").addEventListener("click", () => openReportCreate());
  $("reportBody").addEventListener("click", (ev) => {
    if (ev.target.closest(".c-check")) return;
    const viewBtn = ev.target.closest("[data-report-key]");
    if (viewBtn) openReportDetail(viewBtn.getAttribute("data-report-key"));
  });
  $("reportBody").addEventListener("change", (ev) => {
    if (ev.target.classList.contains("report-chk")) updateReportSelBar();
  });
  $("reportSelectAll").addEventListener("change", toggleReportSelectAll);
  $("reportSelClear").addEventListener("click", clearReportSelection);
  $("reportSelDelete").addEventListener("click", deleteSelectedReports);
  $("rcDeleteBtn").addEventListener("click", deleteCurrentReport);

  // Confirm dialog
  $("confirmOkBtn").addEventListener("click", () => resolveConfirm(true));
  $("confirmCancelBtn").addEventListener("click", () => resolveConfirm(false));
  $("confirmCloseBtn").addEventListener("click", () => resolveConfirm(false));
  $("confirmOverlay").addEventListener("click", (ev) => { if (ev.target === $("confirmOverlay")) resolveConfirm(false); });
  document.addEventListener("keydown", (ev) => {
    if ($("confirmOverlay").classList.contains("hidden")) return;
    if (ev.key === "Escape") resolveConfirm(false);
    else if (ev.key === "Enter") resolveConfirm(true);
  });
  $("rcImportBtn").addEventListener("click", openPlanPicker);
  $("rcClearPlan").addEventListener("click", clearReportPlan);
  $("rcCloseBtn").addEventListener("click", closeReportCreate);
  $("rcHeadClose").addEventListener("click", closeReportCreate);
  $("rcSaveBtn").addEventListener("click", tempSaveReport);
  $("rcSubmitBtn").addEventListener("click", submitReportCreate);
  $("rcFileInput").addEventListener("change", onReceiptFiles);
  $("rcSections").addEventListener("click", (ev) => { if (ev.target.closest("[data-loadev]")) $("rcFileInput").click(); });
  $("planPickerCloseBtn").addEventListener("click", closePlanPicker);
  $("planPickerCancelBtn").addEventListener("click", closePlanPicker);
  $("planPickerConfirmBtn").addEventListener("click", confirmPlanPicker);
  $("planPickerSearch").addEventListener("input", () => {
    $("planPickerSearchClear").classList.toggle("hidden", !$("planPickerSearch").value);
    renderPlanPicker();
  });
  $("planPickerSearchClear").addEventListener("click", () => {
    $("planPickerSearch").value = "";
    $("planPickerSearchClear").classList.add("hidden");
    renderPlanPicker();
    $("planPickerSearch").focus();
  });
  // Click a row (or its radio) to select; double-click to select + import.
  $("planPickerList").addEventListener("click", (ev) => {
    const row = ev.target.closest("[data-pickplan]");
    if (row) selectPickerPlan(row.getAttribute("data-pickplan"));
  });
  $("planPickerList").addEventListener("dblclick", (ev) => {
    const row = ev.target.closest("[data-pickplan]");
    if (row) { selectPickerPlan(row.getAttribute("data-pickplan")); confirmPlanPicker(); }
  });

  // Close popups when the backdrop (outside the panel) is clicked. mousedown —
  // not click — so a text-selection drag that ends on the backdrop doesn't close
  // a half-filled form.
  [
    ["createOverlay", closeCreate],
    ["routeOverlay", () => closeRoute(false)],
    ["detailOverlay", closeDetail],
    ["resumeOverlay", closeResume],
    ["reportCreateOverlay", closeReportCreate],
    ["planPickerOverlay", closePlanPicker],
  ].forEach(([id, close]) => {
    $(id).addEventListener("mousedown", (ev) => { if (ev.target === $(id)) close(); });
  });

  loadPlans();
  loadMasterDataSilent();   // live staff/department lists for the traveller dropdowns
}

function dayCount(s, e) {
  const a = new Date(s), b = new Date(e);
  return Math.max(1, Math.round((b - a) / 86400000) + 1);
}

/* ================================================================
 *  AGENT — conversational create-plan flow
 *  Endpoints:
 *    POST /api/v1/agent-conversations/files/create-batch   (multipart)
 *    POST /api/v1/agent-conversations/agents/trip-plan      (chat turn)
 *    POST /api/v1/plans                                     (commit draft)
 * ================================================================ */
const AGENT_API = API_ORIGIN + "/api/v1/agent-conversations";

/* Download URL for a previously-uploaded evidence file (receipt, invoice, spreadsheet…).
 * The server streams the original bytes with Content-Disposition: attachment. */
function fileDownloadUrl(fileId) {
  return `${AGENT_API}/files/${encodeURIComponent(fileId)}/download`;
}
/* Compact download anchor for an uploaded file reference. */
function receiptLink(fileId, label) {
  if (!fileId) return "";
  return `<a class="receipt-link" href="${esc(fileDownloadUrl(fileId))}" title="Download ${esc(label || "evidence file")}">${svgIcon("import", "ico-xs")} ${esc(label || "Receipt")}</a>`;
}

/* POST a FormData via XHR so callers can observe real upload progress
 * (fetch has no upload-progress API). onPct receives 0..1. */
function xhrUpload(url, formData, onPct) {
  return new Promise((resolve) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", url);
    if (xhr.upload && onPct) {
      xhr.upload.onprogress = (ev) => { if (ev.lengthComputable) onPct(ev.loaded / ev.total); };
    }
    xhr.onload = () => {
      let json = {};
      try { json = JSON.parse(xhr.responseText || "{}"); } catch {}
      resolve({ ok: xhr.status >= 200 && xhr.status < 300, status: xhr.status, json });
    };
    xhr.onerror = () => resolve({ ok: false, status: 0, json: { message: "Failed to fetch" } });
    xhr.send(formData);
  });
}

async function uploadAgentFiles(files, onPct) {
  const fileList = Array.from(files || []).filter(Boolean);
  if (!fileList.length) return [];

  const fd = new FormData();
  fileList.forEach((f) => fd.append("files", f));
  const batch = await xhrUpload(`${AGENT_API}/files/create-batch`, fd, onPct);
  if (batch.ok) {
    return (batch.json && (batch.json.data || batch.json.payload)) || [];
  }

  // Some production proxies block the batch multipart path while allowing the
  // single-file upload endpoint. Fall back to the narrower endpoint in that case.
  if (![403, 404, 405].includes(batch.status)) {
    throw apiError(batch.json, batch, "Upload failed.");
  }

  const uploaded = [];
  for (let i = 0; i < fileList.length; i++) {
    const one = new FormData();
    one.append("file", fileList[i]);
    const res = await xhrUpload(`${AGENT_API}/files/create`, one,
      onPct ? (p) => onPct((i + p) / fileList.length) : null);
    if (!res.ok) {
      throw apiError(res.json, res, "Upload failed.");
    }
    const data = res.json && (res.json.data || res.json.payload);
    if (data) uploaded.push(data);
  }
  return uploaded;
}

const agent = {
  sessionId: null,
  status: null,
  draft: null,
  pending: [],   // [{fileId, filename}] uploaded, waiting to be sent with next turn
  busy: false,
  editing: false,
  editTravelers: [],
  mode: "plan",   // "plan" | "report"
  planId: null,   // for report mode: the trip plan being reported on
  live: false,    // true = session runs on the BizPlay form-driven agent (/bizplay/agents/plan)
};

function resetAgent() {
  agent.sessionId = null;
  agent.status = null;
  agent.draft = null;
  agent.pending = [];
  agent.busy = false;
  agent.mode = "plan";
  agent.planId = null;
  agent.live = false;
  $("agentInput").value = "";
  $("agentFileInput").value = "";
  renderAgentFiles();
}

/* Show/hide the chat pane of the unified Create Trip Plan modal. */
function setChatPane(on) {
  $("createBody").classList.toggle("with-chat", !!on);
  $("chatToggleBtn").classList.toggle("on", !!on);
  $("createTitle").textContent = on ? "Create Business Trip Plan with Agent" : "Create Business Trip Plan";
  $("createSub").textContent = on
    ? "Describe the trip or attach a staff list / itinerary — the agent fills the form, and you can edit any field."
    : "A travel-expense limit is granted upon completion.";
  if (on) {
    loadLlmSettings();   // populate the composer model switcher
    setTimeout(() => $("agentInput").focus(), 50);
  }
}
function toggleChatPane() {
  const on = !$("createBody").classList.contains("with-chat");
  if (on && !$("agentThread").childElementCount) seedAgentGreeting();
  setChatPane(on);
}
function seedAgentGreeting() {
  $("agentThread").innerHTML =
    `<div class="msg msg-assistant"><div class="bubble">Hi! Tell me about the business trip — who travels, where, when, and why. You can also attach a staff list (.xlsx) or a booking/itinerary (.pdf).</div></div>`;
}

/* "Create with Agent" — the same modal, opened with the chat pane showing. */
function openAgent() {
  openCreate();
  resetAgent();
  seedAgentGreeting();
  setChatPane(true);
}

/* Open the create modal pre-loaded with a saved agent session (Resume draft). */
function openAgentResumed(d) {
  openCreate();
  resetAgent();
  agent.sessionId = d.sessionId;
  agent.status = d.status || null;
  agent.draft = d.draftJson || null;
  // Rebuild the conversation from saved history.
  $("agentThread").innerHTML =
    `<div class="msg msg-assistant"><div class="bubble">${svgIcon("refresh")} Resumed session <code>${esc(String(d.sessionId).slice(0, 8))}</code> — continue chatting, or edit the form directly.</div></div>`;
  (d.chatEventJson || []).forEach((t) => {
    if (!t || !t.content) return;
    appendMsg(t.role === "assistant" ? "assistant" : "user", t.content, {});
  });
  // If the session was saved mid-disambiguation, re-offer the choice as chips.
  if (d.pendingChoices && d.pendingChoices.length) {
    appendMsg("assistant", "There is still a pending choice — pick one to continue:", { choiceGroups: d.pendingChoices });
  }
  if (agent.draft) applyDraftToForm(agent.draft);
  setChatPane(true);
}

/* ---- File upload ---- */
async function onAgentFiles(ev) {
  const files = Array.from(ev.target.files || []);
  if (!files.length) return;
  setAgentBusy(true, "Uploading…");
  try {
    const uploaded = await uploadAgentFiles(files);
    uploaded.forEach((u) => agent.pending.push({ fileId: u.fileId, filename: u.filename }));
    renderAgentFiles();
  } catch (e) {
    toast("Upload failed: " + friendlyError(e.message), "err");
  } finally {
    ev.target.value = "";
    setAgentBusy(false);
  }
}
function renderAgentFiles() {
  const box = $("agentFiles");
  if (!agent.pending.length) { box.innerHTML = ""; return; }
  box.innerHTML = agent.pending.map((f, i) => {
    const icon = /\.pdf$/i.test(f.filename) ? svgIcon("file-text") : svgIcon("table");
    return `<span class="fchip">${icon} ${esc(f.filename || f.fileId)}<button data-fchip="${i}" title="Remove">✕</button></span>`;
  }).join("");
}

/* ---- Chat turn ---- */
async function sendAgent() {
  if (agent.busy) return;
  const message = $("agentInput").value.trim();
  const fileIds = agent.pending.map((f) => f.fileId);
  if (!message && !fileIds.length) { toast("Type a message or attach a file.", "err"); return; }

  // Optimistic user bubble (text + any file chips)
  appendMsg("user", message, { files: agent.pending.map((f) => f.filename) });
  $("agentInput").value = "";
  const sentFiles = agent.pending.slice();
  agent.pending = [];
  renderAgentFiles();

  setAgentBusy(true);
  const typing = appendTyping();
  try {
    // A NEW plan session goes to the BizPlay form-driven agent when the live
    // catalog is up (files still ride the PoC agent — no upload path yet in ③).
    if (!agent.sessionId && agent.mode === "plan") {
      agent.live = !!bzCatalog && !fileIds.length;
    }
    if (agent.live && fileIds.length) {
      typing.remove();
      toast("Attachments aren't supported in the live BizPlay flow yet — remove the file or start a new chat.", "err");
      agent.pending = sentFiles;
      renderAgentFiles();
      setAgentBusy(false);
      return;
    }
    let url, body;
    if (agent.live) {
      url = `${BZ_API_BASE()}/agents/plan`;
      body = { corpNo: CORP_NO, corpUserId: BZ_CORP_USER_ID, message: message || null };
      if (agent.sessionId) body.sessionId = agent.sessionId;
    } else {
      body = { corpNo: CORP_NO, message: message || null, fileIds };
      if (agent.sessionId) body.sessionId = agent.sessionId;
      // A new report session must reference the plan it reports on.
      if (agent.mode === "report" && !agent.sessionId && agent.planId) body.planId = agent.planId;
      const endpoint = agent.mode === "report" ? "/agents/expense-report" : "/agents/trip-plan";
      url = `${AGENT_API}${endpoint}`;
    }
    const res = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    const json = await res.json().catch(() => ({}));
    typing.remove();
    if (!res.ok) throw apiError(json, res);
    const data = (json && (json.data || json.payload)) || {};
    agent.sessionId = data.sessionId || agent.sessionId;
    agent.status = data.status || null;
    agent.draft = data.draftJson || agent.draft;
    agent.lastData = data;   // full last turn (missingFields etc.) for panels/tests
    appendMsg("assistant", data.reply || "(no reply)", {
      intent: data.intent, subAgents: data.subAgents,
      choiceGroups: data.pendingChoices,
    });
    if (agent.live) {
      await applyBizplayTurnToForm(data);          // ③-shaped draft -> the designed form
      if (data.status === "READY_FOR_REVIEW") offerBizplayCreate();
    } else {
      applyDraftToForm(agent.draft);               // PoC draft -> the designed form
    }
  } catch (e) {
    typing.remove();
    appendMsg("assistant", "⚠ " + friendlyError(e.message), { error: true });
    // restore the attachments the user tried to send so they aren't lost
    agent.pending = sentFiles;
    renderAgentFiles();
  } finally {
    setAgentBusy(false);
  }
}

function setAgentBusy(b, label) {
  agent.busy = b;
  const send = $("agentSendBtn");
  send.disabled = b;
  send.textContent = b ? (label || "Sending…") : "Send";
}

/* ================================================================
 * Predefined prompt flows — Ctrl+Space in the agent composer opens a
 * picker with three ready-made conversation starters. Selecting one
 * only fills the textarea (the user can edit before sending).
 * ================================================================ */
function bzNextWeekday(dow) {   // next occurrence of weekday dow (1=Mon), never today
  const d = new Date();
  d.setDate(d.getDate() + (((dow - d.getDay() + 7) % 7) || 7));
  return d;
}
function bzIso(d) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
/* Canned demo answer for one missing-field label (used by the follow-up templates). */
function bzDemoAnswerFor(label) {
  const mon = bzNextWeekday(1);
  const wed = new Date(mon); wed.setDate(mon.getDate() + 2);
  if (/출장자/.test(label)) return "출장자는 김충북";
  if (/제목/.test(label)) return `제목은 ${$("tripPurpose").value || "출장"} 데모 계획`;
  if (/내용/.test(label)) return "내용은 업무 협의 및 현장 방문";
  if (/기간/.test(label)) return `출장기간은 ${bzIso(mon)}부터 ${bzIso(wed)}까지`;
  if (/출장지|지역/.test(label)) return "출장지는 서울";
  // custom item of the live form: prefer its first configured option, else a demo value
  const cfg = bzActiveCfg || { extra: [], travelerExtra: [] };
  const f = cfg.extra.concat(cfg.travelerExtra).find((x) => x.rawLabel === label);
  return `${label}: ${f && f.options && f.options.length ? f.options[0] : "테스트"}`;
}

/* While the agent is asking follow-up questions, Ctrl+Space offers ANSWER templates
 * for exactly the fields still missing — the whole demo can run on Ctrl+Space alone. */
function followUpTemplates(missing) {
  const t = [{
    title: "Answer all missing fields",
    desc: `Fills everything the agent asked for: ${missing.join(", ")}.`,
    text: missing.map(bzDemoAnswerFor).join(", ") + ".",
  }];
  if (missing.length > 1) {
    t.push({
      title: `Answer one · ${missing[0]}`,
      desc: "Answer just the first field — the agent keeps asking for the rest.",
      text: bzDemoAnswerFor(missing[0]) + ".",
    });
  }
  if (missing.some((m) => /출장자/.test(m))) {
    t.push({
      title: "Two travelers instead",
      desc: "Answer the traveler question with 김도하 + 김충북 (one document each on save).",
      text: "출장자는 김도하랑 김충북 두 명이야.",
    });
  }
  return t;
}

function promptTemplates() {
  // Follow-up mode: an active chat is mid-collection -> offer answers, not starters.
  const missing = (agent.live && agent.sessionId && agent.status === "COLLECTING"
    && agent.lastData && agent.lastData.missingFields) || [];
  if (missing.length) return followUpTemplates(missing);
  const mon = bzNextWeekday(1);
  const fri = new Date(mon); fri.setDate(mon.getDate() + 4);
  const wed = new Date(mon); wed.setDate(mon.getDate() + 2);
  return [
    {
      title: "One-shot · complete plan",
      desc: "Everything in one message — purpose, dates, destination, traveler, title, content.",
      text: `해외출장 장기로 출장 계획 만들어줘. ${bzIso(mon)}부터 ${bzIso(fri)}까지 오사카로 가고, 출장자는 김충북이야. 제목은 오사카 공급사 실사, 내용은 현지 공급사 품질 실사 및 단가 협상.`,
    },
    {
      title: "Step-by-step · guided",
      desc: "Start with just the trip type — the agent asks follow-up questions for the rest.",
      text: "국내출장 일반으로 계획서 하나 작성해줘",
    },
    {
      title: "Team trip · multi-traveler",
      desc: "Two travelers on one plan — becomes one document per traveler on save.",
      text: `국내출장 일반으로 ${bzIso(mon)}부터 ${bzIso(wed)}까지 부산 출장. 출장자는 김도하랑 김충북 두 명이야. 제목은 부산 고객사 방문, 내용은 고객사 정기 미팅 및 현장 점검.`,
    },
    {
      title: "Rich-text form · 성린4 template",
      desc: "Different form template: the 테스트(유성린) paper with a 코스트센터 + HTML text-editor section.",
      text: `테스트(유성린) 성린4로 출장 계획 만들어줘. ${bzIso(mon)}부터 ${bzIso(wed)}까지 서울로 가고, 출장자는 김충북이야. 제목은 성린4 양식 데모, 내용은 리치텍스트 폼 시나리오 데모. 코스트센터: CC-2001. HTML111: 1일차 킥오프 회의, 2일차 결과 정리 및 공유.`,
    },
  ];
}
let promptSel = 0;
function togglePromptMenu() {
  const menu = $("promptMenu");
  if (!menu.classList.contains("hidden")) { closePromptMenu(); return; }
  promptSel = 0;
  menu.innerHTML = promptTemplates().map((t, i) => `
    <div class="prompt-item ${i === 0 ? "sel" : ""}" data-idx="${i}" role="option">
      <div class="pi-title">${esc(t.title)}</div>
      <div class="pi-desc">${esc(t.desc)}</div>
      <div class="pi-preview">${esc(t.text.length > 90 ? t.text.slice(0, 90) + "…" : t.text)}</div>
    </div>`).join("");
  menu.classList.remove("hidden");
}
function closePromptMenu() { $("promptMenu").classList.add("hidden"); }
function movePromptSel(delta) {
  const items = [...document.querySelectorAll("#promptMenu .prompt-item")];
  if (!items.length) return;
  promptSel = (promptSel + delta + items.length) % items.length;
  items.forEach((el, i) => el.classList.toggle("sel", i === promptSel));
  items[promptSel].scrollIntoView({ block: "nearest" });
}
function pickPrompt(idx) {
  const t = promptTemplates()[idx];
  if (!t) return;
  const input = $("agentInput");
  input.value = t.text;
  closePromptMenu();
  input.focus();
  input.setSelectionRange(input.value.length, input.value.length);
}

/* ---- Thread rendering ---- */
function appendMsg(role, text, meta = {}) {
  const thread = $("agentThread");
  const wrap = document.createElement("div");
  wrap.className = "msg msg-" + role;
  let metaHtml = "";
  if (meta.files && meta.files.length) {
    metaHtml += `<div class="bubble-files">${meta.files.map((f) => `<span>${svgIcon("paperclip")} ${esc(f)}</span>`).join("")}</div>`;
  }
  let foot = "";
  if (meta.subAgents && meta.subAgents.length) {
    foot = `<div class="msg-meta">${meta.intent ? `<span class="chip-intent">${esc(meta.intent)}</span>` : ""}${meta.subAgents.map((a) => `<span class="chip-agent">${esc(prettyAgent(a))}</span>`).join("")}</div>`;
  }
  wrap.innerHTML = `<div class="bubble ${meta.error ? "bubble-error" : ""}">${text ? esc(text) : "<i>(file only)</i>"}${metaHtml}</div>${foot}`;
  // Interactive disambiguation chips (pendingChoices from the agent): one row per
  // ambiguous name; clicking a chip sends its sendText as the next chat turn.
  if (meta.choiceGroups && meta.choiceGroups.length) {
    meta.choiceGroups.forEach((g) => {
      const row = document.createElement("div");
      row.className = "choice-row";
      if (g.name) {
        const cap = document.createElement("span");
        cap.className = "choice-cap";
        cap.textContent = `“${g.name}”:`;
        row.appendChild(cap);
      }
      const addChip = (label, sendText, extraClass) => {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "choice-chip" + (extraClass ? " " + extraClass : "");
        btn.textContent = label;
        btn.addEventListener("click", () => {
          if (agent.busy || row.classList.contains("choice-done")) return;
          row.classList.add("choice-done");
          btn.classList.add("choice-picked");
          $("agentInput").value = sendText;
          sendAgent();
        });
        row.appendChild(btn);
      };
      (g.options || []).forEach((opt) => {
        const isSkip = !opt.staffId && /^skip$/i.test(opt.label || "");
        addChip(opt.label || opt.sendText || "?", opt.sendText || opt.label || "", isSkip ? "choice-skip" : "");
      });
      // Opt-out fallback for older backends whose options don't include a Skip.
      if (!(g.options || []).some((opt) => /^skip$/i.test(opt.label || ""))) {
        addChip("Skip", `Don't add ${g.name || "that person"} to this trip.`, "choice-skip");
      }
      wrap.appendChild(row);
    });
  }
  thread.appendChild(wrap);
  thread.scrollTop = thread.scrollHeight;
}
function appendTyping() {
  const thread = $("agentThread");
  const wrap = document.createElement("div");
  wrap.className = "msg msg-assistant";
  wrap.innerHTML = `<div class="bubble bubble-typing"><span></span><span></span><span></span></div>`;
  thread.appendChild(wrap);
  thread.scrollTop = thread.scrollHeight;
  return wrap;
}
function prettyAgent(code) {
  return String(code).toLowerCase().replace(/_/g, " ").replace(/\bagent\b/g, "").trim()
    .replace(/\b\w/g, (c) => c.toUpperCase());
}
function initials(name) {
  if (!name) return "?";
  const parts = String(name).trim().split(/\s+/);
  const a = parts[0] ? parts[0][0] : "";
  const b = parts.length > 1 ? parts[parts.length - 1][0] : "";
  return (a + b).toUpperCase() || "?";
}

/* ---- Agent draft -> the real form ----
 * Every agent turn repopulates the form. A field the user is actively editing is
 * left alone (so a turn landing mid-typing cannot clobber input); fields the agent
 * actually changed flash briefly. */
function setFormField(id, value) {
  const el = $(id);
  if (!el || value == null || value === "") return;
  if (document.activeElement === el) return;         // never overwrite what is being typed
  if (String(el.value) === String(value)) return;    // unchanged - no flash
  el.value = value;
  el.classList.remove("field-changed");
  void el.offsetWidth;                               // restart the flash animation
  el.classList.add("field-changed");
}

function applyDraftToForm(draft) {
  if (!draft) return;
  const ti = draft.TripInformation || {};
  setFormField("tripPurpose", normalizePurpose(ti.Purpose));
  // Purpose drives the Classification options — rebuild them, then apply the value.
  applyTripType(true);
  if (ti.BusinessTripClassification &&
      $("tripPurpose").value && document.activeElement !== $("tripClassification")) {
    const cls = $("tripClassification");
    if ([...cls.options].some((o) => o.value === ti.BusinessTripClassification)) {
      setFormField("tripClassification", ti.BusinessTripClassification);
      updateTripDetailVisibility();   // reveal the rest once the agent fills both selects
      bzOnClassificationChange();     // live mode: fetch this purpose+type's form from BizPlay
    }
  }
  setFormField("tripDestination", ti.Destination);
  setFormField("tripTitle", ti.Title);
  setFormField("tripContent", ti.Content);
  updateCharCounts();

  // Dates: prefer explicit start/end, else split a "A to B" period string.
  let s = ti.BusinessStartDate, e = ti.BusinessEndDate;
  if ((!s || !e) && ti.BusinessPeriod) {
    const p = String(ti.BusinessPeriod).split(/\s+to\s+/i);
    if (p.length === 2) { s = s || p[0].trim(); e = e || p[1].trim(); }
  }
  setFormField("startDate", s);
  setFormField("endDate", e);
  const sv = $("startDate").value, ev = $("endDate").value;
  $("periodHint").textContent = sv && ev ? "(Trip Period: " + dayCount(sv, ev) + " day(s))" : "";

  // Travelers: the agent owns the roster, so replace it wholesale.
  const list = ti.Travelers || [];
  if (list.length) {
    travelers = list.map((t) => ({
      id: ++travelerSeq,
      name: t.Name || "", department: t.Department || "", position: t.Position || "",
      origin: t.Origin || "", destination: t.Destination || ti.Destination || "",
      returnPoint: t.ReturnPoint || "",
    }));
    renderTravelers();
  }

  // Attachments (URLs the user registered + files the agent extracted from).
  const atts = draft.Attachemnt || draft.Attachment || [];
  const mapped = atts.filter((a) => a && (a.URL || a.FileID))
    .map((a) => ({ Type: a.Type || (a.URL ? "URL" : "File"), URL: a.URL, FileID: a.FileID }));
  if (mapped.length) { attachments = mapped; renderAttachments(); }
}

/* ---- BizPlay agent turn -> the real form (live mode) ----
 * draftJson is EXACTLY the ③ save-body array; doc[0] carries purpose/segment ids,
 * dates, title/content, and the issuedItems values. Purpose/segment ids map back
 * to names through the live catalog, which drives the two selects + form load. */
async function applyBizplayTurnToForm(data) {
  const docs = Array.isArray(data.draftJson) ? data.draftJson : [];
  const doc = docs[0] || null;

  if (doc && bzCatalog) {
    const pname = Object.keys(bzCatalog).find((n) => bzCatalog[n].purposeId === doc.bstrPurposeId);
    if (pname) {
      setFormField("tripPurpose", pname);
      applyTripType(true);   // rebuild the Classification options for this purpose
      const cls = $("tripClassification");
      const sid = doc.bstrSegmentId == null ? "" : String(doc.bstrSegmentId);
      const opt = [...cls.options].find((o) => o.getAttribute("data-sid") === sid);
      if (opt && document.activeElement !== cls) {
        setFormField("tripClassification", opt.value);
        updateTripDetailVisibility();
        await bzOnClassificationChange();   // load this purpose+type's form fields
      }
    }
  }

  if (doc) {
    setFormField("tripTitle", doc.title);
    setFormField("tripContent", doc.content);
    setFormField("startDate", String(doc.bstrStartDate || "").slice(0, 10));
    setFormField("endDate", String(doc.bstrEndDate || "").slice(0, 10));
    const sv = $("startDate").value, ev = $("endDate").value;
    $("periodHint").textContent = sv && ev ? "(Trip Period: " + dayCount(sv, ev) + " day(s))" : "";
  }
  setFormField("tripDestination", data.destination);
  updateCharCounts();

  // Travelers: the agent owns the roster (resolved BizPlay names) — attach the
  // corporationUserId so a manual save can fan out documents. Prefer a roster match
  // by name; fall back to the response's resolved travelerIds (covers any case where
  // the display name still differs from the roster's userName).
  const names = data.travelers || [];
  const tids = data.travelerIds || [];
  if (names.length) {
    try { await bzLoadRoster(); } catch (e) { /* selects fall back to name-only */ }
    travelers = names.map((n, i) => {
      let u = bzApproval.roster.find((r) => r.name === n);
      if (!u && tids[i] != null) u = bzApproval.roster.find((r) => String(r.id) === String(tids[i]));
      return {
        id: ++travelerSeq, name: u ? u.name : n,
        bzId: u ? u.id : (tids[i] != null ? tids[i] : null),
        department: "", position: "",
        origin: "", destination: data.destination || "", returnPoint: "",
      };
    });
    renderTravelers();
  }

  // Custom item values -> the dynamic inputs (form-level data-xf + traveler data-bztf).
  ((doc && doc.issuedItems) || []).forEach((it) => {
    if (!it.item || it.item.itemType === "BSTR_PERIOD") return;
    const key = "item:" + it.item.id;
    let v = it.value;
    if ((v == null || v === "") && it.selections && it.selections.length) {
      v = it.selections[0].selectionName;
    }
    if (v == null || v === "") return;
    const direct = document.querySelectorAll(`[data-xf="${key}"], [data-bztf="${key}"]`);
    direct.forEach((el) => {
      if (document.activeElement === el) return;
      if (el.type === "checkbox") { el.checked = true; return; }
      if (el.type === "radio") { el.checked = String(el.value) === String(v); return; }
      if (el.isContentEditable) { el.innerText = v; return; }   // rich-text (HTML) editor body
      el.value = v;
    });
    if (!direct.length) {
      // Composite widget (교육정보 …): put the agent's text into the group's primary field.
      document.querySelectorAll(`[data-xf-group="${key}"] [data-primary], [data-bztf-group="${key}"] [data-primary]`)
        .forEach((el) => { if (document.activeElement !== el) el.value = v; });
    }
  });
}

/* READY_FOR_REVIEW: offer the real BizPlay save flow (approval order -> preview -> save). */
function offerBizplayCreate() {
  const thread = $("agentThread");
  const wrap = document.createElement("div");
  wrap.className = "msg msg-assistant";
  const row = document.createElement("div");
  row.className = "choice-row";
  const btn = document.createElement("button");
  btn.type = "button";
  btn.className = "choice-chip";
  btn.textContent = "✓ Save to BizPlay (set approval order)";
  btn.addEventListener("click", () => {
    if (agent.busy) return;
    bzOpenApprovalFlow();
  });
  row.appendChild(btn);
  wrap.appendChild(row);
  thread.appendChild(wrap);
  thread.scrollTop = thread.scrollHeight;
}

/* ================================================================
 * The REAL BizPlay save flow, replicated:
 *   save -> "Set approval order" dialog -> 출장계획확인 preview -> POST ③.
 * ================================================================ */
const bzApproval = { roster: [], lines: [] };   // lines: [{id, name, dept, empNo, position, kind}]
let bzManualSave = false;   // true = flow was entered from the manual form (no agent session)
// Values are BizPlay's ApprovalKindType enum (server rejects anything else):
// DRAFT, APPROVAL, ACCEPT, AUDIT, AGREE, REFERENCE.
const BZ_LINE_KINDS = [
  ["APPROVAL", "결재"], ["AGREE", "합의"],
  ["ACCEPT", "수신"], ["REFERENCE", "참조"],
];

/* Load the corporation's staff once (shared by traveler selects + the approval picker). */
async function bzLoadRoster() {
  if (bzApproval.roster.length) return bzApproval.roster;
  const res = await fetch(`${BZ_API_BASE()}/corporation-users`);
  const json = await res.json().catch(() => ({}));
  if (!res.ok) throw apiError(json, res);
  const data = (json && (json.data || json.payload)) || {};
  const users = data.users || data.list || (Array.isArray(data) ? data : []);
  bzApproval.roster = users.map((u) => {
    const depts = u.departments || [];
    const main = depts.find((d) => d.mainDepartment) || depts[0] || {};
    return {
      id: u.corporationUserId ?? u.id,
      name: u.userName || u.name || "",
      dept: u.departmentName || main.departmentName || "",
      empNo: u.employeeNumber || "",
      position: u.positionName || u.position || "",
    };
  }).filter((u) => u.id != null && u.name);
  return bzApproval.roster;
}

async function bzOpenApprovalFlow(manual) {
  bzManualSave = !!manual || !(agent.live && agent.sessionId);
  bzApproval.lines = [];
  $("bzApprovalOverlay").classList.remove("hidden");
  $("bzStaffSearch").value = "";
  $("bzDeptFilter").value = "";
  bzRenderLineList();
  if (!bzApproval.roster.length) {
    $("bzStaffRows").innerHTML = `<tr><td colspan="5" class="bz-empty">Loading staff…</td></tr>`;
    try {
      await bzLoadRoster();
    } catch (e) {
      $("bzStaffRows").innerHTML = `<tr><td colspan="5" class="bz-empty">Could not load staff: ${esc(friendlyError(e.message))}</td></tr>`;
      return;
    }
  }
  bzRenderDeptFilter();
  bzRenderStaffRows();
}

/* Departments come from the same private-API roster (departments[] per user). */
function bzRenderDeptFilter() {
  const sel = $("bzDeptFilter");
  const prev = sel.value;
  const depts = [...new Set(bzApproval.roster.map((u) => u.dept).filter(Boolean))].sort();
  sel.innerHTML = `<option value="">All departments</option>` +
    depts.map((d) => `<option value="${esc(d)}">${esc(d)}</option>`).join("");
  if (prev && depts.includes(prev)) sel.value = prev;
}

function bzRenderStaffRows() {
  const q = $("bzStaffSearch").value.trim().toLowerCase();
  const dept = $("bzDeptFilter").value;
  const rows = bzApproval.roster.filter((u) =>
    (!dept || u.dept === dept)
    && (!q || u.name.toLowerCase().includes(q) || u.dept.toLowerCase().includes(q)
      || String(u.empNo).toLowerCase().includes(q)));
  $("bzStaffRows").innerHTML = rows.length ? rows.map((u) => {
    const picked = bzApproval.lines.some((l) => l.id === u.id);
    return `<tr data-uid="${esc(String(u.id))}">
      <td><input type="checkbox" ${picked ? "checked" : ""} /></td>
      <td>${esc(u.name)}</td><td>${esc(u.dept)}</td><td>${esc(String(u.empNo))}</td><td>${esc(u.position)}</td>
    </tr>`;
  }).join("") : `<tr><td colspan="5" class="bz-empty">No staff match.</td></tr>`;
}

function bzRenderLineList() {
  const box = $("bzLineList");
  box.innerHTML = bzApproval.lines.length ? bzApproval.lines.map((l, i) => `
    <div class="bz-line-item" data-uid="${esc(String(l.id))}">
      <span class="bz-line-order">${i + 1}</span>
      <span class="bz-line-who"><span class="nm">${esc(l.name)}</span>
        <div class="sub2">${esc([l.dept, l.position].filter(Boolean).join(" · "))}</div></span>
      <select data-kind="${esc(String(l.id))}">${BZ_LINE_KINDS.map(([v, label]) =>
        `<option value="${v}" ${l.kind === v ? "selected" : ""}>${label}</option>`).join("")}</select>
      <button class="bz-line-x" data-x="${esc(String(l.id))}" title="Remove">✕</button>
    </div>`).join("") : `<div class="bz-empty">Add approvers</div>`;
}

function bzToggleLine(uid) {
  const i = bzApproval.lines.findIndex((l) => l.id === uid);
  if (i >= 0) {
    bzApproval.lines.splice(i, 1);
  } else {
    const u = bzApproval.roster.find((r) => r.id === uid);
    if (u) bzApproval.lines.push({ ...u, kind: "APPROVAL" });
  }
  bzRenderStaffRows();
  bzRenderLineList();
}

/* Step 2: 출장계획확인-style preview built from the live form + agent state. */
function bzShowPreview() {
  $("bzApprovalOverlay").classList.add("hidden");
  const me = bzApproval.roster.find((u) => String(u.id) === String(BZ_CORP_USER_ID));
  const kindKo = { APPROVAL: "결재", AGREE: "합의", ACCEPT: "수신", REFERENCE: "참조" };
  const col = (hd, u) => `<div class="bz-appr-col"><div class="hd">${esc(hd)}</div>
    <div class="bd">${u ? `${esc(u.name)}<div class="sub2">${esc(String(u.empNo || ""))}</div>
      <div class="sub2">${esc(u.dept || "")}</div><div class="sub2">${esc(u.position || "")}</div>` : ""}</div></div>`;
  const approvalCols = [col("기안", me || { name: "(me)", empNo: BZ_CORP_USER_ID, dept: "", position: "" })]
    .concat(bzApproval.lines.map((l) => col(kindKo[l.kind] || l.kind, l))).join("");

  const period = [$("startDate").value, $("endDate").value].filter(Boolean).join(" ~ ");
  const dest = $("tripDestination").value;
  const costCenters = [...document.querySelectorAll(".trav-card")].map((c) => {
    const inp = [...c.querySelectorAll("input[data-bztf]")].find((el) => el.type === "text" && el.value.trim());
    return inp ? inp.value.trim() : "";
  });
  const travLine = travelers.map((t, i) =>
    t.name ? `${t.name}${costCenters[i] ? " / " + costCenters[i] : ""}` : "").filter(Boolean).join(", ");

  const row = (k, v) => `<div class="bz-prow"><span class="k">${esc(k)}</span><span class="v">${v ? esc(v) : "—"}</span></div>`;
  // Segment-less purposes carry the "-" placeholder classification — show it as empty like BizPlay.
  const cls = $("tripClassification").value === "-" ? "" : $("tripClassification").value;
  $("bzPreviewBody").innerHTML =
    `<div class="bz-preview-approvals">${approvalCols}</div>
     <div class="bz-preview-rows">
       ${row("출장 목적", $("tripPurpose").value)}
       ${row("출장 구분", cls)}
       ${row("출장기간/국가", [period, dest].filter(Boolean).join(", "))}
       ${row("제목", $("tripTitle").value)}
       ${row("내용", $("tripContent").value)}
       ${row("출장자", travLine)}
       ${row("첨부파일", String(attachments.length))}
     </div>`;
  $("bzPreviewOverlay").classList.remove("hidden");
}

/* After a successful BizPlay save, mirror the plan into the demo's own list so it
 * is immediately visible here — BizPlay's own list only shows 결재요청+ documents
 * of accounts involved, so a DRAFT_ONLY save would otherwise look like "nothing". */
async function bzMirrorLocalPlan() {
  try {
    const trip = readTripFields();
    const payload = {
      CorpNo: CORP_NO,
      PlanType: PLAN_TYPE,
      // Link the mirror to its chat session so the two are deleted together.
      AgentSessionId: (agent.live && agent.sessionId) ? agent.sessionId : undefined,
      TripInformation: {
        Purpose: trip.purpose,
        BusinessPeriod: `${trip.start} to ${trip.end}`,
        Destination: trip.destination,
        Title: trip.title,
        Content: (trip.content + extraFieldsSummary()).slice(0, 500),
        BusinessTripClassification: trip.classification,
        Travelers: travelers.filter((t) => t.name).map((t) => ({
          Name: t.name, Department: t.department || "", Position: t.position || "",
          Origin: t.origin || "", Destination: t.destination || trip.destination, ReturnPoint: t.returnPoint || "",
        })),
      },
      Attachemnt: attachments,
    };
    const res = await fetch(API, {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload),
    });
    if (res.ok) loadPlans();
  } catch (e) {
    console.warn("[bizplay] local mirror failed:", e.message);
  }
}

/* Manual save: values from the form -> the backend writes them into the RETRIEVED
 * form's ③ skeleton (same writer paths as the agent) and POSTs to BizPlay. */
async function bzSubmitManualCreate() {
  const live = bzCatalog && bzCatalog[$("tripPurpose").value];
  if (!live) { toast("Pick a travel purpose first.", "err"); return; }
  const opt = $("tripClassification").selectedOptions && $("tripClassification").selectedOptions[0];
  const sid = opt ? (opt.getAttribute("data-sid") || "") : "";
  const itemValues = {};
  Object.entries(readExtraFields()).forEach(([k, v]) => { if (v) itemValues[k] = v; });
  document.querySelectorAll(".trav-card [data-bztf]").forEach((el) => {
    const k = el.getAttribute("data-bztf");
    if (itemValues[k]) return;
    if (el.type === "checkbox") { if (el.checked) itemValues[k] = "신청"; }
    else if (el.type === "radio") { if (el.checked) itemValues[k] = el.value; }
    else if ((el.value || "").trim()) itemValues[k] = el.value.trim();
  });
  document.querySelectorAll(".trav-card [data-bztf-group]").forEach((g) => {
    const k = g.getAttribute("data-bztf-group");
    if (itemValues[k]) return;
    const v = bzReadGroup(g);
    if (v) itemValues[k] = v;
  });
  const payload = {
    corpUserId: BZ_CORP_USER_ID,
    // WYSIWYG sync: the posted documents are written back into this session's draft_json.
    agentSessionId: (agent.live && agent.sessionId) ? agent.sessionId : null,
    corpNo: CORP_NO,
    purposeId: live.purposeId,
    segmentId: sid ? Number(sid) : null,
    title: $("tripTitle").value.trim(),
    content: $("tripContent").value.trim(),
    startDate: $("startDate").value,
    endDate: $("endDate").value,
    destination: $("tripDestination").value.trim(),
    travelerCorpUserIds: travelers.map((t) => t.bzId).filter(Boolean),
    itemValues,
    approvalLines: bzApproval.lines.map((l) => ({ corporationUserId: l.id, approvalKindType: l.kind })),
  };
  $("bzPreviewOverlay").classList.add("hidden");
  const btns = [$("createCompleteBtn"), $("createCompleteBtn2")];
  btns.forEach((b) => (b.disabled = true));
  try {
    const res = await fetch(`${BZ_API_BASE()}/plans`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    const data = (json && (json.data || json.payload)) || {};
    toast(data.reply || "Plan saved to BizPlay ✓", "ok");
    await bzMirrorLocalPlan();   // show it in the demo list right away
    if (agent.live && agent.sessionId) {
      // Chat-driven save: stay in the modal and confirm in the thread (the session's
      // draft_json was synced server-side to the exact documents that were posted).
      agent.status = data.status || agent.status;
      appendMsg("assistant", data.reply || "Plan saved to BizPlay.", {
        intent: data.intent, subAgents: data.subAgents,
      });
    } else {
      closeCreate();
    }
  } catch (e) {
    $("validationSummary").textContent = "Save failed: " + friendlyError(e.message);
    toast("Save failed: " + friendlyError(e.message), "err");
  } finally {
    btns.forEach((b) => (b.disabled = false));
  }
}

/* Final step of BOTH save entry points (chat chip and the form's Complete button).
 * WYSIWYG invariant: the save always posts what is ON SCREEN — agent-filled values
 * live in the DOM too, plus any manual edits the user typed into the form. When a
 * chat session exists, the backend syncs the posted documents into its draft_json
 * (the session-path /create endpoint remains for API/headless callers). */
async function bzSubmitCreate() {
  bzSubmitManualCreate();
}
async function openDetail(id) {
  if (!id) return;
  currentDetailId = id;
  $("detailTitle").textContent = "Plan detail";
  $("detailSub").textContent = "";
  $("detailBody").innerHTML = `<div class="draft-empty-wrap"><span class="ico">⏳</span><p>Loading plan…</p></div>`;
  $("detailDeleteBtn").disabled = false;
  $("detailOverlay").classList.remove("hidden");
  try {
    const res = await fetch(`${API}/${encodeURIComponent(id)}`);
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    const plan = (json && (json.data || json.payload)) || null;
    if (!plan) throw new Error("Plan not found.");
    renderDetail(plan);
  } catch (e) {
    $("detailBody").innerHTML = `<div class="draft-empty-wrap">${svgIcon("alert", "ico-lg")}<p>${esc(friendlyError(e.message))}</p></div>`;
  }
}
function closeDetail() { $("detailOverlay").classList.add("hidden"); currentDetailId = null; }

function renderDetail(p) {
  const travelers = p.travelers || [];
  const atts = p.attachments || [];
  const period = formatPeriod(p);
  const created = p.createdAt ? String(p.createdAt).replace("T", " ").slice(0, 16) : null;

  $("detailTitle").textContent = p.title || "Untitled trip";
  $("detailSub").textContent = `${p.destination || "—"} · ${p.businessPeriod || period || ""}`.trim();

  const row = (k, v) => `<div class="dl-row"><dt>${k}</dt><dd>${v ? esc(v) : '<span class="empty">Not set</span>'}</dd></div>`;

  let html = `<div class="dp-hero">
      <div class="h-title">${p.title ? esc(p.title) : '<span class="ph">Untitled trip</span>'}</div>
      <div class="h-route"><span class="pin">${svgIcon("pin")}</span><span>${p.destination ? esc(p.destination) : '<span class="ph">—</span>'}</span></div>
      <div class="h-meta">
        ${p.purpose ? `<span class="tagpill accent">${esc(p.purpose)}</span>` : ""}
        ${period ? `<span class="tagpill">${svgIcon("calendar")} ${esc(period)}</span>` : ""}
        ${p.businessTripClassification ? `<span class="tagpill">${esc(p.businessTripClassification)}</span>` : ""}
        ${p.agentSessionId ? `<span class="tagpill agent">${svgIcon("sparkles")} via Agent</span>` : `<span class="tagpill">✍ Manual</span>`}
      </div>
    </div>`;

  html += `<div class="dp-block">
      <div class="dp-block-h">Trip details</div>
      <dl class="dl">
        ${row("Plan type", p.planType)}
        ${row("Purpose", p.purpose)}
        ${row("Destination", p.destination)}
        ${row("Period", p.businessPeriod || period)}
        ${row("Classification", p.businessTripClassification)}
        ${p.content ? row("Content", p.content) : ""}
        ${row("Corp No.", p.corpNo)}
        ${created ? row("Created", created) : ""}
      </dl>
    </div>`;

  html += `<div class="dp-block"><div class="dp-block-h">Travellers <span class="sec-count">${travelers.length}</span></div>`;
  if (!travelers.length) {
    html += `<p style="color:#9098a6;font-size:12.5px;margin:0">No travellers.</p>`;
  } else {
    html += travelers.map((t) => {
      const dep = [t.department, t.position].filter(Boolean).map(esc).join(" · ");
      return `<div class="trav-mini">
        <div class="tm-avatar">${esc(initials(t.name))}</div>
        <div class="tm-info">
          <div class="tm-name">${esc(t.name || "Unnamed")}</div>
          ${dep ? `<div class="tm-dept">${dep}</div>` : ""}
          <div class="tm-route">${esc(t.origin || "?")} <i>→</i> ${esc(t.destination || p.destination || "?")}${t.returnPoint ? ` <i>→</i> ${esc(t.returnPoint)}` : ""}</div>
        </div>
      </div>`;
    }).join("");
  }
  html += `</div>`;

  if (atts.length) {
    html += `<div class="dp-block"><div class="dp-block-h">Attachments <span class="sec-count">${atts.length}</span></div>
      ${atts.map((a) => {
        if (a.url) return `<div class="att-mini">${svgIcon("link")} <a href="${esc(a.url)}" target="_blank" rel="noopener">${esc(a.url)}</a></div>`;
        if (a.fileId) return `<div class="att-mini">${svgIcon("paperclip")} ${receiptLink(a.fileId, a.filename || a.fileId)}</div>`;
        return `<div class="att-mini">${svgIcon("paperclip")} ${esc(a.type || "file")}</div>`;
      }).join("")}</div>`;
  }

  $("detailBody").innerHTML = html;
}

async function deleteCurrentPlan() {
  if (!currentDetailId) return;
  if (!(await confirmDialog({
    title: "Delete business trip plan",
    message: "Delete this business trip plan? This cannot be undone.",
    confirmText: "Delete",
  }))) return;
  const btn = $("detailDeleteBtn");
  btn.disabled = true; btn.textContent = "Deleting…";
  try {
    const res = await fetch(`${API}/${encodeURIComponent(currentDetailId)}`, { method: "DELETE" });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    toast("Plan deleted.", "ok");
    closeDetail();
    loadPlans();
  } catch (e) {
    toast("Delete failed: " + friendlyError(e.message), "err");
    btn.disabled = false;
  } finally {
    btn.textContent = "Delete plan";
  }
}

/* ================================================================
 *  RESUME — GET /sessions?corpNo= , GET /sessions/{id}
 * ================================================================ */
function openResume() {
  $("resumeList").innerHTML = `<div class="draft-empty-wrap"><span class="ico">⏳</span><p>Loading sessions…</p></div>`;
  $("resumeOverlay").classList.remove("hidden");
  loadSessions();
}
function closeResume() { $("resumeOverlay").classList.add("hidden"); }

async function loadSessions() {
  try {
    const res = await fetch(`${AGENT_API}/sessions?corpNo=${encodeURIComponent(CORP_NO)}`);
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    renderResumeList((json && (json.data || json.payload)) || []);
  } catch (e) {
    $("resumeList").innerHTML = `<div class="draft-empty-wrap">${svgIcon("alert", "ico-lg")}<p>${esc(friendlyError(e.message))}</p></div>`;
  }
}
function renderResumeList(sessions) {
  const box = $("resumeList");
  const trip = sessions.filter((s) => (s.agentType || "") === "TRIP_PLAN");
  if (!trip.length) {
    box.innerHTML = `<div class="draft-empty-wrap">${svgIcon("folder", "ico-lg")}<p>No saved sessions yet. Start one with “Create with Agent”.</p></div>`;
    return;
  }
  trip.sort((a, b) => String(b.updatedDate || "").localeCompare(String(a.updatedDate || "")));
  const docIcon = `<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/><path d="M9 13h6M9 17h4"/></svg>`;
  box.innerHTML = trip.map((s) => {
    const st = s.status || "COLLECTING";
    const cls = st === "READY_FOR_REVIEW" ? "status-ready" : (st === "COLLECTING" ? "status-collecting" : "status-none");
    const upd = s.updatedDate ? String(s.updatedDate).replace("T", " ").slice(0, 16) : "—";
    const sid = String(s.sessionId).slice(0, 8);
    return `<button class="resume-item resume-row" data-sid="${esc(s.sessionId)}">
        <span class="rr-icon">${docIcon}</span>
        <span class="rr-body">
          <span class="rr-top"><span class="rr-name">Trip plan draft</span><span class="status-pill ${cls}">${esc(st.replace(/_/g, " "))}</span></span>
          <span class="rr-meta"><code>${esc(sid)}</code> · updated ${esc(upd)}</span>
        </span>
        <span class="ri-go">Open →</span>
      </button>`;
  }).join("");
}
async function loadSession(id) {
  try {
    const res = await fetch(`${AGENT_API}/sessions/${encodeURIComponent(id)}`);
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    const detail = (json && (json.data || json.payload)) || null;
    if (!detail) throw new Error("Session not found.");
    closeResume();
    openAgentResumed(detail);
  } catch (e) {
    toast("Could not load session: " + friendlyError(e.message), "err");
  }
}

/* ================================================================
 *  DRAFT EDIT — PUT /sessions/{id}/draft (manual checkpoint)
 * ================================================================ */
function showTab(name) {
  if (!roleAllows(name)) name = ROLE_TABS[ROLE][0];   // role gate: never open a hidden tab
  currentTab = name;
  document.querySelectorAll(".tab").forEach((t) => t.classList.toggle("active", t.getAttribute("data-tab") === name));
  ["plan", "approve", "report", "audit"].forEach((n) => $("tab-" + n).classList.toggle("hidden", n !== name));
  animatePane(name);
  if (name === "approve") loadApprovals();
  if (name === "report") loadReports();
  if (name === "audit") loadAudits();
}

/* Re-fetch whichever tab is visible (used after the corp number changes). */
function reloadActiveTab() {
  if (currentTab === "approve") loadApprovals();
  else if (currentTab === "report") loadReports();
  else if (currentTab === "audit") loadAudits();
  else loadPlans();
}

/* ================================================================
 *  TAB 2 — APPROVE  (PATCH /api/v1/plans/approval_status)
 * ================================================================ */
let approveCache = [];
let approveFilterKey = "all";   // all | pending | approved | cancelled

async function loadApprovals() {
  const body = $("approveBody");
  body.innerHTML = loadingRow(8);
  beginLoad();
  try {
    const res = await fetch(`${API}?corpNo=${encodeURIComponent(CORP_NO)}`);
    const json = await res.json();
    approveCache = (json && (json.data || json.payload)) || [];
    renderApprovals();
    animateRowsIn("approveBody");
  } catch (e) {
    body.innerHTML = emptyRow(8, { icon: "alert", title: "Couldn’t load plans", sub: esc(friendlyError(e.message)) });
  } finally {
    endLoad();
  }
}
function approvalClass(s) {
  if (s === "Approval complete") return "appr-done";
  if (s === "Business trip cancellation") return "appr-cancel";
  return "appr-request";
}
function updateApproveChips() {
  let req = 0, app = 0, can = 0;
  approveCache.forEach((p) => {
    const k = planStatusKey(p);
    if (k === "approved") app++; else if (k === "cancelled") can++; else req++;
  });
  $("acAll").textContent = approveCache.length;
  $("acReq").textContent = req;
  $("acApp").textContent = app;
  $("acCan").textContent = can;
}
function renderApprovals() {
  updateApproveChips();
  const q = ($("approveSearch").value || "").trim().toLowerCase();
  const plans = approveCache.slice().filter((p) => {
    const k = planStatusKey(p);
    if (approveFilterKey === "pending" && k !== "request") return false;
    if (approveFilterKey === "approved" && k !== "approved") return false;
    if (approveFilterKey === "cancelled" && k !== "cancelled") return false;
    return planMatchesSearch(p, q);
  });
  plans.sort((a, b) => String(b.createdAt || "").localeCompare(String(a.createdAt || "")));
  $("approveCount").textContent = plans.length;
  $("approveShown").textContent = plans.length;
  $("approveTotal").textContent = approveCache.length;
  const body = $("approveBody");
  if (!plans.length) {
    body.innerHTML = approveCache.length
      ? emptyRow(8, { icon: "search", title: "No plans match this filter", sub: "Try a different search or status chip." })
      : emptyRow(8, { icon: "check-circle", title: "No plans to review", sub: "Requests appear here once a plan is submitted." });
    return;
  }
  body.innerHTML = plans.map((p, i) => {
    const t0 = (p.travelers && p.travelers[0]) || {};
    const extra = p.travelers && p.travelers.length > 1 ? ` 외 ${p.travelers.length - 1}명` : "";
    const period = formatPeriod(p);
    const s = p.approvalStatus || "Request for approval";
    const docNo = `2026-출장계획서-${shortNo(p.id, i + 1)}`;
    const isDone = s === "Approval complete";
    const isCancel = s === "Business trip cancellation";
    return `<tr class="row-click" data-plan-row="${esc(p.id)}">
      <td class="c-no">${i + 1}</td>
      <td title="${esc(docNo)}"><span class="doc-no">${esc(docNo)}</span></td>
      <td class="c-title" title="${esc(p.title || "")}">${esc(p.title || "—")}</td>
      <td title="${esc((t0.name || "—") + extra)}">${esc((t0.name || "—") + extra)}</td>
      <td>${esc(t0.department || "—")}</td>
      <td class="c-period">${esc(period)}</td>
      <td><span class="appr-pill ${approvalClass(s)}">${esc(s)}</span></td>
      <td><div class="row-actions">
        ${isDone ? "" : `<button class="btn-xs btn-approve" data-approve="${esc(p.id)}">Approve</button>`}
        ${isCancel ? "" : `<button class="btn-xs btn-cancel" data-cancel="${esc(p.id)}">Cancel</button>`}
      </div></td>
    </tr>`;
  }).join("");
}
async function setApproval(id, status) {
  try {
    const res = await fetch(`${API}/approval_status`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ id, approvalStatus: status }),
    });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    const data = json && (json.data || json.payload);
    const idx = approveCache.findIndex((p) => p.id === id);
    if (idx >= 0) approveCache[idx] = data || { ...approveCache[idx], approvalStatus: status };
    renderApprovals();
    toast(status === "Approval complete" ? "Plan approved." : status === "Business trip cancellation" ? "Plan cancelled." : "Status updated.", "ok");
  } catch (e) {
    toast("Update failed: " + friendlyError(e.message), "err");
  }
}

/* ================================================================
 *  TAB 3 — EXPENSE REPORT
 * ================================================================ */
function num(v) { const n = Number(String(v ?? "").replace(/[^\d.-]/g, "")); return isFinite(n) ? n : 0; }
/* Gross amount actually used (Supply price + Tax). */
function lineAmount(it) {
  const v = it.AmountUsed ?? it.amountUsed ?? it["Supply price"] ?? it.supplyPrice ?? 0;
  return num(v);
}
function lineDesc(it) { return it.Description || it["거래처"] || it.Category || it.Type || "Expense"; }
function lineDate(it) { return it.UsageDate || it.ProofDate || it.StartDate || ""; }
/* Per-field extractors (handle English + Korean keys) for the full line view. */
function lineSeq(it, i) { return it["순번"] ?? it.Seq ?? it.seq ?? (i + 1); }
function lineVendor(it) { return it["거래처"] || it.Vendor || it.Merchant || ""; }
function lineMethod(it) { return it["교통수단"] || it.TransportationMethod || it.Method || ""; }
function lineRoute(it) { const o = it.Origin || it.From, d = it.Destination || it.To; return (o || d) ? `${o || "?"} → ${d || "?"}` : ""; }
function lineDateRange(it) {
  const s = it.StartDate, e = it.EndDate;
  if (s && e && s !== e) return `${s} ~ ${e}`;
  return it.UsageDate || it.ProofDate || s || e || "";
}
function fmtMoney(n) { return "₩" + Math.round(n).toLocaleString(); }
/* Money cell that shows "—" when the field is truly absent (0 is a real value). */
function moneyCell(v) { return (v === null || v === undefined || v === "") ? "—" : fmtMoney(num(v)); }
function txtCell(v) { return (v === null || v === undefined || v === "") ? "—" : esc(String(v)); }

function reportLineAmount(line) {
  const e = line.costExpense || line.transportationExpense || {};
  return num(e.applicationAmount ?? e.amountUsed);
}
function reportLineDate(line) {
  const t = line.transportationExpense, c = line.costExpense;
  return (t && (t.usageDate || t.evidenceDate)) || (c && (c.evidenceDate || c.proofDate || c.startDate)) || "";
}

async function loadReports() {
  const body = $("reportBody");
  body.innerHTML = loadingRow(14);
  beginLoad();
  try {
    // Reports, plus plans (trip title/traveler) and audits (latest R10 verdict per plan).
    const [rRes, pRes, aRes] = await Promise.all([
      fetch(`${EXPENSE_API}?corpNo=${encodeURIComponent(CORP_NO)}`),
      fetch(`${API}?corpNo=${encodeURIComponent(CORP_NO)}`),
      fetch(`${RULE_ENGINE_API}/audits?corpNo=${encodeURIComponent(CORP_NO)}`).catch(() => null),
    ]);
    const rJson = await rRes.json();
    if (!rRes.ok) throw new Error((rJson && (rJson.message || rJson.detail)) || `HTTP ${rRes.status}`);
    const pJson = await pRes.json().catch(() => ({}));
    const reports = (rJson && (rJson.data || rJson.payload)) || [];
    const plans = (pJson && (pJson.data || pJson.payload)) || [];
    const planMap = {};
    plans.forEach((p) => { planMap[p.id] = p; });
    // Latest audit per trip plan (audit display is best-effort — never fails the table).
    const auditByPlan = {};
    if (aRes) {
      const aJson = await aRes.json().catch(() => ({}));
      if (aRes.ok) {
        const audits = ((aJson && (aJson.payload || aJson.data)) || []).map(normalizeAudit).filter(Boolean);
        audits.sort((a, b) => String(b.createdDate || "").localeCompare(String(a.createdDate || "")));
        audits.forEach((a) => { if (a.tripPlanId && !auditByPlan[a.tripPlanId]) auditByPlan[a.tripPlanId] = a; });
      }
    }

    reportsCache = reports.map((rep) => {
      const lines = rep.costItems || rep.lines || [];
      let total = 0, date = "";
      lines.forEach((ln) => {
        total += reportLineAmount(ln);
        const d = reportLineDate(ln);
        if (d && d > date) date = d;
      });
      const plan = rep.tripPlanId ? planMap[rep.tripPlanId] : null;
      return {
        key: rep.id,
        audit: rep.tripPlanId ? auditByPlan[rep.tripPlanId] || null : null,
        tripPlanId: rep.tripPlanId || null,
        sessionId: rep.agentSessionId || null,
        department: rep.department || "",
        approvalNumber: rep.approvalNumber || "",
        approvalStatus: rep.approvalStatus || "Request for approval",
        createdAt: rep.createdAt || "",
        lineCount: lines.length,
        total, date, lines, plan,
        title: (plan && plan.title) || (rep.tripPlanId ? "(trip plan removed)" : "Untitled report"),
      };
    });
    renderReports();
    animateRowsIn("reportBody");
  } catch (e) {
    reportsCache = [];
    $("reportCount").textContent = 0; $("reportShown").textContent = 0; $("reportTotal").textContent = 0;
    body.innerHTML = emptyRow(14, { icon: "alert", title: "Couldn’t load reports", sub: esc(friendlyError(e.message)) });
  } finally {
    endLoad();
  }
}

/* Totals for an in-progress draft (used by the create modal preview), kept for that flow. */
function draftTotals(draft) {
  let total = 0, lines = 0;
  SECTIONS.forEach(({ key }) => {
    const items = ((draft && draft[key]) || {}).Detail || [];
    lines += items.length;
    items.forEach((x) => (total += lineAmount(x)));
  });
  return { total, lines };
}

function renderReports() {
  const q = ($("reportSearch").value || "").trim().toLowerCase();
  const list = reportsCache.slice().filter((g) => {
    if (!q) return true;
    const p = g.plan || {};
    const t0 = (p.travelers && p.travelers[0]) || {};
    const aprStatus = g.approvalStatus || "Request for approval";
    const aprLabel = aprStatus === "Approval complete" ? "Approved"
      : aprStatus === "Business trip cancellation" ? "Cancelled" : "Request for approval";
    const hay = [g.title, p.purpose, p.destination, t0.name, t0.department, aprStatus, aprLabel, "2026-지출보고서",
      g.audit && ("audit " + g.audit.status + " " + g.audit.complianceStatus)]
      .filter(Boolean).join(" ").toLowerCase();
    return hay.includes(q);
  });
  list.sort((a, b) => String(b.date || "").localeCompare(String(a.date || "")));
  $("reportCount").textContent = list.length;
  $("reportShown").textContent = list.length;
  $("reportTotal").textContent = reportsCache.length;
  const body = $("reportBody");
  if (!list.length) {
    body.innerHTML = reportsCache.length
      ? emptyRow(14, { icon: "search", title: "No reports match this search", sub: "Try different keywords." })
      : emptyRow(14, { icon: "receipt", title: "No expense reports yet", sub: "Import an approved plan and attach receipts to settle a trip.",
          action: `<button class="btn btn-primary btn-sm" onclick="openReportCreate()">+ Create Report</button>` });
    updateReportSelBar();
    return;
  }
  body.innerHTML = list.map((g, i) => {
    const doc = `2026-지출보고서-${shortNo(g.key, i + 1)}`;
    const p = g.plan || {};
    const t0 = (p.travelers && p.travelers[0]) || {};
    const extra = p.travelers && p.travelers.length > 1 ? ` 외 ${p.travelers.length - 1}명` : "";
    const traveler = (t0.name || "—") + extra;
    const dept = g.department || t0.department || "—";
    const purpose = p.purpose ? shortPurpose(p.purpose) : "—";
    const period = (p.businessStartDate || p.businessPeriod) ? formatPeriod(p) : (g.date || "—");
    const aprStatus = g.approvalStatus || "Request for approval";
    const aprLabel = aprStatus === "Approval complete" ? "Approved"
      : aprStatus === "Business trip cancellation" ? "Cancelled" : "Request for approval";
    const a = g.audit;
    // Read-only status for the traveler — audit review/override lives in the Admin role.
    const auditCell = a
      ? `<span class="audit-pill ${resultClass(a.status)}" title="${esc(a.summary || "")}">${esc(a.status === "Pass" ? "Passed" : a.status)}</span>`
      : `<span class="muted" title="No R10 audit has run for this trip plan yet">Not audited</span>`;
    return `<tr class="row-click" data-report-key="${esc(g.key)}">
      <td class="c-check"><input type="checkbox" class="chk report-chk" data-id="${esc(g.key)}" data-title="${esc(g.title)}" aria-label="Select report" /></td>
      <td class="c-no">${i + 1}</td>
      <td title="${esc(doc)}"><span class="doc-no">${esc(doc)}</span></td>
      <td class="c-title" title="${esc(g.title)}">${esc(g.title)}</td>
      <td class="c-trav" title="${esc(traveler)}">${esc(traveler)}</td>
      <td class="c-dept" title="${esc(dept)}">${esc(dept)}</td>
      <td>${purpose === "—" ? "—" : `<span class="purpose-tag" title="${esc(p.purpose || "")}">${esc(purpose)}</span>`}</td>
      <td class="c-period" title="${esc(p.businessPeriod || "")}">${esc(period)}</td>
      <td>${g.lineCount}</td>
      <td class="er-amt">${fmtMoney(g.total)}</td>
      <td><span class="appr-pill ${approvalClass(aprStatus)}">${esc(aprLabel)}</span></td>
      <td>${auditCell}</td>
      <td class="c-period">${esc(g.date || "—")}</td>
      <td><div class="row-actions">
        <button class="btn-xs btn-view" data-report-key="${esc(g.key)}">View</button>
      </div></td>
    </tr>`;
  }).join("");
  updateReportSelBar();
}

/* ---- report selection + delete (DELETE /api/v1/reports/batch and /{id}) ---- */
function updateReportSelBar() {
  const all = Array.from(document.querySelectorAll(".report-chk"));
  const checked = all.filter((c) => c.checked);
  $("reportSelCount").textContent = checked.length;
  $("reportSelBar").classList.toggle("hidden", checked.length === 0);
  const head = $("reportSelectAll");
  if (head) {
    head.checked = all.length > 0 && checked.length === all.length;
    head.indeterminate = checked.length > 0 && checked.length < all.length;
  }
}
function toggleReportSelectAll() {
  const on = $("reportSelectAll").checked;
  document.querySelectorAll(".report-chk").forEach((c) => { c.checked = on; });
  updateReportSelBar();
}
function clearReportSelection() {
  document.querySelectorAll(".report-chk").forEach((c) => { c.checked = false; });
  const head = $("reportSelectAll"); if (head) { head.checked = false; head.indeterminate = false; }
  updateReportSelBar();
}
async function deleteSelectedReports() {
  const checks = Array.from(document.querySelectorAll(".report-chk")).filter((c) => c.checked);
  const ids = checks.map((c) => c.getAttribute("data-id")).filter(Boolean);
  if (!ids.length) return;
  const label = ids.length === 1 ? `“${checks[0].getAttribute("data-title")}”` : `${ids.length} reports`;
  const ok = await confirmDialog({
    title: "Remove expense report" + (ids.length === 1 ? "" : "s"),
    message: `Remove ${label}? This deletes the report${ids.length === 1 ? "" : "s"} and all expense lines, and cannot be undone.`,
    confirmText: "Remove",
  });
  if (!ok) return;
  const btn = $("reportSelDelete");
  btn.disabled = true; btn.textContent = "Removing…";
  beginLoad();
  try {
    const res = await fetch(`${EXPENSE_API}/batch`, {
      method: "DELETE",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ids }),
    });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    const r = (json && (json.data || json.payload)) || {};
    const deleted = r.deleted != null ? r.deleted : ids.length;
    toast(`Removed ${deleted} report${deleted === 1 ? "" : "s"}.`, "ok");
    loadReports();
  } catch (e) {
    toast("Remove failed: " + friendlyError(e.message), "err");
  } finally {
    btn.disabled = false; btn.innerHTML = svgIcon("trash") + " Remove";
    endLoad();
  }
}
/* Delete the report currently open in the detail popup. Uses /{id} for a single
   line, /batch for multiple. */
async function deleteCurrentReport() {
  const ids = (rc.detailIds || []).filter(Boolean);
  if (!ids.length) return;
  if (!(await confirmDialog({
    title: "Delete expense report",
    message: "Delete this expense report? This cannot be undone.",
    confirmText: "Delete",
  }))) return;
  const btn = $("rcDeleteBtn");
  btn.disabled = true; btn.textContent = "Deleting…";
  beginLoad();
  try {
    let res;
    if (ids.length === 1) {
      res = await fetch(`${EXPENSE_API}/${encodeURIComponent(ids[0])}`, { method: "DELETE" });
    } else {
      res = await fetch(`${EXPENSE_API}/batch`, {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ids }),
      });
    }
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    toast("Report deleted.", "ok");
    closeReportCreate();
    loadReports();
  } catch (e) {
    toast("Delete failed: " + friendlyError(e.message), "err");
  } finally {
    btn.disabled = false; btn.innerHTML = svgIcon("trash") + " Delete";
    endLoad();
  }
}

/* ---- create modal ---- */
function setReportModalMode(readonly) {
  $("rcSheet").classList.toggle("readonly", !!readonly);
  $("rcTitle").textContent = readonly ? "Expense Report" : "New Expense Report";
  $("rcSub").textContent = readonly
    ? "Trip plan and settled expense lines for this report."
    : "Import an approved business trip plan, attach receipts, then review.";
  $("rcCloseBtn").textContent = readonly ? "Close" : "Cancel";
  $("rcDeleteBtn").classList.toggle("hidden", !readonly);
}
function openReportCreate() {
  rc.planId = null; rc.plan = null; rc.sessionId = null; rc.draft = null;
  setReportModalMode(false);
  $("rcPlanLabel").textContent = "Please select an approved plan to settle";
  $("rcClearPlan").classList.add("hidden");
  $("rcTripInfo").classList.add("hidden"); $("rcTripInfo").innerHTML = "";
  $("rcReceiptHint").textContent = "Import a plan, then add receipts — the agent extracts expense lines from each PDF.";
  $("rcMsg").textContent = "";
  rcProgressHide();
  renderRcSections();
  $("reportCreateOverlay").classList.remove("hidden");
}
function closeReportCreate() { rcProgressHide(); $("reportCreateOverlay").classList.add("hidden"); }

/* ---- View a posted report (read-only) in the same popup ----
   GET /api/v1/reports/{id} per line, then reconstruct the report's sections. */
/* ctx = report-header fields shared by all lines: { department, approvalNumber } */
function lineToDetail(ln, i, ctx) {
  const ce = ln.costExpense, te = ln.transportationExpense;
  const dept = (ctx && ctx.department) || "";
  const aprNo = (ctx && ctx.approvalNumber) || "";
  if (te) {
    return {
      "순번": i + 1, Type: "법인", Use: te.usePurpose || "", BudgetDept: dept,
      AccountSubjects: te.account || "", EvidenceDate: te.evidenceDate || te.usageDate || "",
      "교통수단": te.transportationMethod || "", Grade: te.grade || "",
      Origin: te.originLocation || "", Destination: te.destinationLocation || "",
      UsageDate: te.usageDate || "", "거래처": te.vendor || "",
      "Supply price": te.supplyPrice, Tax: te.tax,
      AmountUsed: te.applicationAmount, ApplicationAmount: te.applicationAmount,
      PolicyAmount: te.policyAmount, RegulatedAmount: te.policyAmount,
      ExcessReason: te.excessReason || "", Description: te.description || "",
      Note: te.note || "", TaxCode: te.taxCode || "", ApprovalNumber: aprNo,
      FileID: ln.attachmentFileId || "",
    };
  }
  const c = ce || {};
  return {
    "순번": i + 1, Type: "법인", Use: c.usePurpose || "", BudgetDept: dept,
    AccountSubjects: c.account || "", TaxCode: c.taxCode || "",
    StartDate: c.startDate || "", EndDate: c.endDate || "",
    EvidenceDate: c.evidenceDate || "", UsageDate: c.evidenceDate || "", "거래처": "",
    Description: c.description || "",
    AmountUsed: c.applicationAmount, ApplicationAmount: c.applicationAmount,
    PolicyAmount: c.policyAmount, RegulatedAmount: c.policyAmount,
    ExcessReason: c.excessReason || "", Note: c.note || "", ApprovalNumber: aprNo,
    FileID: ln.attachmentFileId || "",
  };
}
function buildReportDraftFromLines(lines, plan, ctx) {
  const draft = plan ? planToReportDraft(plan) : {
    TripInformation: {}, CostInformation: { Detail: [] },
    TransportationInformation: { Detail: [] }, Etc: { Detail: [] },
  };
  draft.CostInformation = { Detail: [] };
  draft.TransportationInformation = { Detail: [] };
  draft.Etc = { Detail: [] };
  const ci = { COST: 0, TRANSPORTATION: 0, ETC: 0 };
  lines.forEach((ln) => {
    const sc = (ln.sectionCode || "COST").toUpperCase();
    const bucket = sc === "TRANSPORTATION" ? draft.TransportationInformation
      : sc === "ETC" ? draft.Etc : draft.CostInformation;
    bucket.Detail.push(lineToDetail(ln, ci[sc] !== undefined ? ci[sc]++ : 0, ctx));
  });
  return draft;
}
async function openReportDetail(key) {
  const g = reportsCache.find((x) => x.key === key);
  if (!g) return;
  openReportCreate();
  setReportModalMode(true);
  $("rcMsg").textContent = "Loading report…";
  rc.sessionId = null; rc.planId = g.tripPlanId || null; rc.plan = g.plan || null;
  rc.detailIds = [g.key];   // the report id (for delete)
  $("rcPlanLabel").innerHTML = `<b>${esc(g.title)}</b>${g.tripPlanId ? ` <span class="muted">(${esc(String(g.tripPlanId).slice(0, 8))})</span>` : ""}`;
  try {
    // Fetch the full report fresh via GET /api/v1/reports/{id}; fall back to the cached row.
    let rep = null;
    const res = await fetch(`${EXPENSE_API}/${encodeURIComponent(g.key)}`);
    const json = await res.json().catch(() => ({}));
    if (res.ok) rep = (json && (json.data || json.payload)) || null;
    const lines = (rep && (rep.costItems || rep.lines)) || g.lines || [];
    const ctx = { department: (rep && rep.department) || g.department, approvalNumber: (rep && rep.approvalNumber) || g.approvalNumber };
    rc.draft = buildReportDraftFromLines(lines, g.plan, ctx);
    renderRcTrip(rc.draft.TripInformation || {});
    renderRcSections();
    $("rcMsg").textContent = "";
  } catch (e) {
    $("rcMsg").textContent = "Load failed: " + friendlyError(e.message);
  }
}

async function openReportFromSession(id) {
  openReportCreate();
  $("rcMsg").textContent = "Loading report…";
  try {
    const res = await fetch(`${REPORTS_API}/sessions/${encodeURIComponent(id)}`);
    const json = await res.json();
    const d = json && (json.data || json.payload);
    if (!res.ok || !d) throw apiError(json, res, "Report not found.");
    rc.sessionId = d.sessionId; rc.draft = d.draftJson || {}; rc.planId = rc.draft.TripPlanId || null;
    $("rcPlanLabel").innerHTML = rc.planId
      ? `<b>${esc((rc.draft.TripInformation || {}).Title || "Imported plan")}</b> <span class="muted">(${esc(String(rc.planId).slice(0, 8))})</span>`
      : "From report session";
    $("rcClearPlan").classList.add("hidden");
    renderRcTrip(rc.draft.TripInformation || {});
    renderRcSections();
    $("rcReceiptHint").textContent = "Add more receipts, or review and complete.";
    $("rcMsg").textContent = "";
  } catch (e) {
    $("rcMsg").textContent = "Load failed: " + friendlyError(e.message);
  }
}

/* ---- approved-plan picker (Select Plan table) ---- */
let pickerCache = [];
let pickerSelectedId = null;

async function openPlanPicker() {
  $("planPickerList").innerHTML = loadingRow(8);
  $("planPickerSearch").value = "";
  $("planPickerSearchClear").classList.add("hidden");
  $("planPickerCount").textContent = "";
  pickerSelectedId = null;
  $("planPickerConfirmBtn").disabled = true;
  $("planPickerOverlay").classList.remove("hidden");
  beginLoad();
  try {
    // Only approved plans can be settled into an expense report.
    const url = `${API}/by-approval-status?corpNo=${encodeURIComponent(CORP_NO)}&approvalStatus=${encodeURIComponent("Approval complete")}`;
    const res = await fetch(url);
    const json = await res.json();
    if (!res.ok) throw apiError(json, res);
    pickerCache = (json && (json.data || json.payload)) || [];
    pickerCache.sort((a, b) => String(b.createdAt || "").localeCompare(String(a.createdAt || "")));
    renderPlanPicker();
  } catch (e) {
    pickerCache = [];
    $("planPickerList").innerHTML = emptyRow(8, { icon: "alert", title: "Couldn’t load approved plans", sub: esc(friendlyError(e.message)) });
  } finally {
    endLoad();
  }
}

function renderPlanPicker() {
  const q = ($("planPickerSearch").value || "").trim().toLowerCase();
  const list = pickerCache.filter((p) => planMatchesSearch(p, q));
  $("planPickerCount").innerHTML = `<strong>${list.length}</strong> approved plan${list.length === 1 ? "" : "s"}`;
  const body = $("planPickerList");
  if (!list.length) {
    body.innerHTML = pickerCache.length
      ? emptyRow(8, { icon: "search", title: "No plans match this search", sub: "Try different keywords." })
      : emptyRow(8, { icon: "check-circle", title: "No approved plans yet", sub: "Approve a plan in the “Approve Plan” tab first." });
    return;
  }
  body.innerHTML = list.map((p, i) => {
    const t0 = (p.travelers && p.travelers[0]) || {};
    const extra = p.travelers && p.travelers.length > 1 ? ` 외 ${p.travelers.length - 1}명` : "";
    const traveler = (t0.name || "—") + extra;
    const period = formatPeriod(p);
    const orig = t0.origin || "—", dest = t0.destination || p.destination || "—";
    const route = (t0.origin || t0.destination)
      ? `<span class="leg">${esc(orig)}</span><i class="arr">→</i><span class="leg">${esc(dest)}</span>`
      : `<span class="leg">${esc(p.destination || "—")}</span>`;
    const docNo = `2026-출장계획서-${shortNo(p.id, list.length - i)}`;
    const checked = pickerSelectedId === p.id ? " checked" : "";
    return `<tr class="row-click${checked ? " row-selected" : ""}" data-pickplan="${esc(p.id)}">
      <td class="c-radio"><input type="radio" name="pickplan" class="pick-radio" value="${esc(p.id)}"${checked} aria-label="Select ${esc(p.title || "plan")}" /></td>
      <td class="c-title" title="${esc(p.title || "")}">${esc(p.title || "—")}</td>
      <td title="${esc(docNo)}"><span class="doc-no">${esc(docNo)}</span></td>
      <td class="c-period" title="${esc(p.businessPeriod || "")}">${esc(period)}</td>
      <td title="${esc(t0.name || "")}">${esc(t0.name || "—")}</td>
      <td class="c-trav" title="${esc(traveler)}">${esc(traveler)}</td>
      <td class="c-dept" title="${esc(t0.department || "")}">${esc(t0.department || "—")}</td>
      <td title="${esc(orig + " → " + dest)}"><span class="route-line route-pass">${route}</span></td>
    </tr>`;
  }).join("");
}
/* Mark a plan as selected (radio + footer button), without importing yet. */
function selectPickerPlan(id) {
  pickerSelectedId = id;
  document.querySelectorAll(".pick-radio").forEach((r) => { r.checked = (r.value === id); });
  document.querySelectorAll("#planPickerList tr").forEach((tr) => {
    tr.classList.toggle("row-selected", tr.getAttribute("data-pickplan") === id);
  });
  $("planPickerConfirmBtn").disabled = !id;
}
function confirmPlanPicker() {
  if (!pickerSelectedId) return;
  importPlan(pickerSelectedId);
  closePlanPicker();
}
function closePlanPicker() { $("planPickerOverlay").classList.add("hidden"); }

async function importPlan(id) {
  try {
    const res = await fetch(`${API}/${encodeURIComponent(id)}`);
    const json = await res.json();
    const p = json && (json.data || json.payload);
    if (!res.ok || !p) throw apiError(json, res, "Plan not found.");
    rc.planId = id; rc.plan = p; rc.sessionId = null;
    rc.draft = planToReportDraft(p);
    $("rcPlanLabel").innerHTML = `<b>${esc(p.title || "Plan")}</b> <span class="muted">(${esc(String(id).slice(0, 8))})</span>`;
    $("rcClearPlan").classList.remove("hidden");
    renderRcTrip(rc.draft.TripInformation);
    renderRcSections();
    $("rcReceiptHint").textContent = "Now add receipts to populate the expense lines below.";
  } catch (e) {
    toast("Import failed: " + friendlyError(e.message), "err");
  }
}
function clearReportPlan() {
  rc.planId = null; rc.plan = null; rc.sessionId = null; rc.draft = null;
  $("rcPlanLabel").textContent = "Please select an approved plan to settle";
  $("rcClearPlan").classList.add("hidden");
  $("rcTripInfo").classList.add("hidden"); $("rcTripInfo").innerHTML = "";
  renderRcSections();
}
function planToReportDraft(p) {
  return {
    CorpNo: p.corpNo || CORP_NO, PlanType: p.planType || PLAN_TYPE, TripPlanId: p.id, Attachemnt: [],
    TripInformation: {
      Title: p.title, Purpose: p.purpose, Content: p.content, Destination: p.destination,
      BusinessPeriod: p.businessPeriod, BusinessStartDate: p.businessStartDate, BusinessEndDate: p.businessEndDate,
      "Business Trip Classifcation": p.businessTripClassification,
      Travelers: (p.travelers || []).map((t) => ({
        Name: t.name, Department: t.department, Position: t.position,
        Origin: t.origin, Destination: t.destination, ReturnPoint: t.returnPoint,
      })),
    },
    CostInformation: { Type: null, Detail: [], Attachemnt: [] },
    TransportationInformation: { Type: null, Detail: [], Attachemnt: [] },
    Etc: { Type: null, Detail: [], Attachemnt: [] },
  };
}

function renderRcTrip(ti) {
  ti = ti || {};
  const trav = (ti.Travelers || []).map((t) => esc(t.Name || "")).filter(Boolean).join(", ") || "—";
  const dept = (ti.Travelers && ti.Travelers[0] && ti.Travelers[0].Department) || "—";
  const period = ti.BusinessPeriod || ((ti.BusinessStartDate && ti.BusinessEndDate) ? `${ti.BusinessStartDate} → ${ti.BusinessEndDate}` : "—");
  $("rcTripInfo").innerHTML = `
    <div class="rc-row"><div class="rc-label">Travel Purpose</div><div class="rc-val">${esc(ti.Purpose || "—")}</div></div>
    <div class="rc-row"><div class="rc-label">출장자 · Traveler</div><div class="rc-val">${trav} <span class="muted">· ${esc(dept)}</span></div></div>
    <div class="rc-row"><div class="rc-label">출장 기간 · Period</div><div class="rc-val">${esc(period)}</div></div>
    <div class="rc-row"><div class="rc-label">Destination</div><div class="rc-val">${esc(ti.Destination || "—")}</div></div>
    <div class="rc-row"><div class="rc-label">제목 · Title</div><div class="rc-val">${esc(ti.Title || "—")}</div></div>
    <div class="rc-row"><div class="rc-label">내용 · Content</div><div class="rc-val">${ti.Content ? esc(ti.Content) : '<span class="muted">—</span>'}</div></div>`;
  $("rcTripInfo").classList.remove("hidden");
}

/* Product-style placeholder control for fields the demo has no data/API for
   (tax code, account subject, budget dept) — looks like the Bizplay "선택" select. */
function selCell(ph, icon) {
  const ic = icon === "search" ? svgIcon("search", "ico-xs") : "▾";
  return `<span class="cell-sel"><span class="cell-sel-ph">${esc(ph)}</span><span class="cell-sel-ic">${ic}</span></span>`;
}
/* Column layouts mirroring the Bizplay product — different per section.
   kind: seq | money | sel | (default) text via get(). */
const RC_COLUMNS = {
  CostInformation: [
    { h: "Seq", w: 46, kind: "seq" },
    { h: "Tax Code", w: 120, kind: "sel", ph: "세금코드 선택", get: (it) => it.TaxCode },
    { h: "Type", w: 70, get: (it) => it.Type || "법인" },
    { h: "Use", w: 110, get: (it) => it.Use },
    { h: "Account Subjects", w: 140, kind: "sel", ph: "계정과목 선택", get: (it) => it.AccountSubjects },
    { h: "Budget Dept.", w: 120, kind: "sel", ph: "선택", icon: "search", get: (it) => it.BudgetDept },
    { h: "Usage Start Date", w: 128, get: (it) => it.StartDate || it.UsageDate },
    { h: "Usage End Date", w: 128, get: (it) => it.EndDate || it.UsageDate },
    { h: "Vendor", w: 170, get: (it) => lineVendor(it) },
    { h: "Evidence Date", w: 120, get: (it) => it.EvidenceDate || it.UsageDate },
    { h: "Amount Used", w: 120, kind: "money", total: true, get: (it) => lineAmount(it) },
    { h: "Policy Amount", w: 116, kind: "money", get: (it) => it.PolicyAmount ?? it.RegulatedAmount },
    { h: "Application Amount", w: 130, kind: "money", get: (it) => it.ApplicationAmount ?? it.AmountUsed ?? lineAmount(it) },
    { h: "Excess Reason", w: 140, get: (it) => it.ExcessReason },
    { h: "Description", w: 180, get: (it) => it.Description },
    { h: "Note", w: 120, get: (it) => it.Note },
    { h: "Evidence", w: 104, kind: "file", get: (it) => it.FileID || it.attachmentFileId || it.fileId },
  ],
  TransportationInformation: [
    { h: "Seq", w: 46, kind: "seq" },
    { h: "Type", w: 70, get: (it) => it.Type || "법인" },
    { h: "Use", w: 110, get: (it) => it.Use },
    { h: "Budget Dept.", w: 120, kind: "sel", ph: "선택", icon: "search", get: (it) => it.BudgetDept },
    { h: "Account Subjects", w: 140, kind: "sel", ph: "계정과목 선택", get: (it) => it.AccountSubjects },
    { h: "Evidence Date", w: 120, get: (it) => it.EvidenceDate || it.UsageDate },
    { h: "Means of Transport", w: 130, get: (it) => lineMethod(it) },
    { h: "Grade", w: 90, get: (it) => it.Grade },
    { h: "Departure", w: 120, get: (it) => it.Origin || it.From },
    { h: "Destination", w: 130, get: (it) => it.Destination || it.To },
    { h: "Usage Date", w: 120, get: (it) => it.UsageDate },
    { h: "Vendor", w: 170, get: (it) => lineVendor(it) },
    { h: "Supply Price", w: 110, kind: "money", get: (it) => it["Supply price"] ?? it.supplyPrice },
    { h: "VAT", w: 100, kind: "money", get: (it) => it.Tax },
    { h: "Amount Used", w: 120, kind: "money", total: true, get: (it) => lineAmount(it) },
    { h: "Policy Amount", w: 116, kind: "money", get: (it) => it.PolicyAmount ?? it.RegulatedAmount },
    { h: "Application Amount", w: 130, kind: "money", get: (it) => it.ApplicationAmount ?? it.AmountUsed ?? lineAmount(it) },
    { h: "Excess Reason", w: 140, get: (it) => it.ExcessReason },
    { h: "Description", w: 150, get: (it) => it.Description },
    { h: "Note", w: 120, get: (it) => it.Note },
    { h: "Tax Code", w: 120, kind: "sel", ph: "세금코드 선택", get: (it) => it.TaxCode },
    { h: "Approval No.", w: 130, get: (it) => it.ApprovalNumber },
    { h: "Evidence", w: 104, kind: "file", get: (it) => it.FileID || it.attachmentFileId || it.fileId },
  ],
};
RC_COLUMNS.Etc = RC_COLUMNS.CostInformation;

function rcCell(col, it, i) {
  if (col.kind === "seq") return `<td>${esc(String(lineSeq(it, i)))}</td>`;
  if (col.kind === "file") {
    const v = col.get ? col.get(it) : null;
    return `<td>${v ? receiptLink(v, "PDF") : `<span class="muted">—</span>`}</td>`;
  }
  if (col.kind === "sel") {
    const v = col.get ? col.get(it) : null;
    if (v) return `<td title="${esc(String(v))}">${txtCell(v)}</td>`;
    return `<td>${selCell(col.ph, col.icon)}</td>`;
  }
  if (col.kind === "money") {
    const v = col.get(it);
    return `<td class="amt${col.total ? " total" : ""}">${col.total ? fmtMoney(num(v)) : moneyCell(v)}</td>`;
  }
  const v = col.get(it);
  return `<td title="${esc(v == null ? "" : String(v))}">${txtCell(v)}</td>`;
}

function renderRcSections() {
  const box = $("rcSections");
  if (!rc.draft) { box.innerHTML = `<div class="muted-pad">Import a plan to begin.</div>`; return; }
  box.innerHTML = SECTIONS.map(({ key, label, ko }) => {
    const sec = rc.draft[key] || {};
    const items = sec.Detail || [];
    const cols = RC_COLUMNS[key] || RC_COLUMNS.CostInformation;
    const minW = cols.reduce((s, c) => s + c.w, 0);
    let total = 0, requested = 0;
    items.forEach((it) => { total += lineAmount(it); requested += num(it.RegulatedAmount ?? it.regulatedAmount); });
    const colgroup = cols.map((c) => `<col style="width:${c.w}px" />`).join("");
    const head = cols.map((c) => `<th>${esc(c.h)}</th>`).join("");
    const rows = items.length
      ? items.map((it, i) => `<tr>${cols.map((c) => rcCell(c, it, i)).join("")}</tr>`).join("")
      : `<tr class="er-empty-row"><td colspan="${cols.length}">No items — add receipts.</td></tr>`;
    return `<div class="er-sec">
        <div class="er-sec-head">
          <div class="er-sec-title">${esc(label)} <span class="ko-sub">${esc(ko)}</span> <span class="sec-count">${items.length}</span></div>
          <div class="er-sec-right">
            <span class="er-sec-total">총 ${fmtMoney(total)} <span class="muted">(신청금액 ${fmtMoney(requested)})</span></span>
            <button class="btn-xs btn-view" data-loadev="${esc(key)}">+ Load Evidence</button>
          </div>
        </div>
        <div class="er-table-wrap"><table class="er-table er-table-full" style="min-width:${minW}px">
          <colgroup>${colgroup}</colgroup>
          <thead><tr>${head}</tr></thead>
          <tbody>${rows}</tbody>
        </table></div>
      </div>`;
  }).join("");
  updateRcSubmitState();
}

/* Submit needs at least one expense line (backend rejects empty reports). */
function updateRcSubmitState() {
  const lines = rc.draft ? draftTotals(rc.draft).lines : 0;
  [$("rcSubmitBtn")].forEach((b) => { if (b) b.disabled = lines === 0; });
}

/* ---- Receipt upload progress bar (Create Report modal) ----
 * Upload is measured for real via XHR (0→35%); the agent-extraction phase has
 * no progress events, so the bar crawls asymptotically toward 92% until the
 * response lands, then snaps to 100%. */
let rcCrawlTimer = null;
function rcProgressSet(label, pct) {
  const box = $("rcProgress");
  box.classList.remove("hidden");
  const v = Math.max(0, Math.min(100, pct));
  $("rcProgressLabel").textContent = label;
  $("rcProgressFill").style.width = v + "%";
  $("rcProgressPct").textContent = Math.round(v) + "%";
  box.setAttribute("aria-valuenow", String(Math.round(v)));
}
function rcProgressCrawl(label, from, to) {
  clearInterval(rcCrawlTimer);
  let cur = from;
  rcProgressSet(label, cur);
  rcCrawlTimer = setInterval(() => {
    cur = Math.min(to, cur + Math.max(0.15, (to - cur) * 0.03));
    rcProgressSet(label, cur);
  }, 180);
}
function rcProgressFinish(label) {
  clearInterval(rcCrawlTimer); rcCrawlTimer = null;
  rcProgressSet(label || "Done", 100);
  setTimeout(() => $("rcProgress").classList.add("hidden"), 700);
}
function rcProgressHide() {
  clearInterval(rcCrawlTimer); rcCrawlTimer = null;
  $("rcProgress").classList.add("hidden");
}

async function onReceiptFiles(ev) {
  const files = Array.from(ev.target.files || []);
  ev.target.value = "";
  if (!files.length) return;
  if (!rc.planId) { toast("Import a plan first.", "err"); return; }
  if (rc.uploading) { toast("Still processing the previous upload — one moment.", ""); return; }
  rc.uploading = true;
  const nm = files.length === 1 ? `“${files[0].name}”` : `${files.length} files`;
  $("rcMsg").textContent = "";
  rcProgressSet(`Uploading ${nm}…`, 2);
  const busyBtns = [$("rcSaveBtn"), $("rcSubmitBtn")];
  busyBtns.forEach((b) => (b.disabled = true));
  try {
    const uploaded = await uploadAgentFiles(files, (p) => rcProgressSet(`Uploading ${nm}…`, 2 + p * 33));
    const fileIds = uploaded.map((u) => u.fileId);
    rcProgressCrawl("Extracting expense lines with the agent… (this can take a moment)", 35, 92);
    const body = { corpNo: CORP_NO, fileIds };
    if (rc.sessionId) body.sessionId = rc.sessionId; else body.planId = rc.planId;
    const res = await fetch(REPORTS_API, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    const d = (json && (json.data || json.payload)) || {};
    rc.sessionId = d.sessionId || rc.sessionId;
    rc.draft = d.draftJson || rc.draft;
    renderRcTrip((rc.draft && rc.draft.TripInformation) || {});
    renderRcSections();
    rcProgressFinish("Receipts processed");
    toast("Receipts processed.", "ok");
  } catch (e) {
    rcProgressHide();
    $("rcMsg").textContent = "Receipt processing failed: " + friendlyError(e.message);
    toast("Failed: " + friendlyError(e.message), "err");
  } finally {
    rc.uploading = false;
    busyBtns.forEach((b) => (b.disabled = false));
    updateRcSubmitState();
  }
}

async function submitReportCreate() {
  if (!rc.draft || !rc.planId) { toast("Import a plan first.", "err"); return; }
  if (draftTotals(rc.draft).lines === 0) {
    $("rcMsg").textContent = "Add at least one receipt — a report needs expense lines.";
    toast("Add at least one receipt first.", "err");
    return;
  }
  const payload = JSON.parse(JSON.stringify(rc.draft));
  payload.CorpNo = payload.CorpNo || CORP_NO;
  payload.PlanType = payload.PlanType || PLAN_TYPE;
  payload.TripPlanId = payload.TripPlanId || rc.planId;
  if (rc.sessionId) payload.AgentSessionId = rc.sessionId;
  const btns = [$("rcSubmitBtn")];
  btns.forEach((b) => (b.disabled = true));
  $("rcMsg").textContent = "Submitting…";
  try {
    const res = await fetch(`${API_ORIGIN}/api/v1/reports`, {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload),
    });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    const data = (json && (json.data || json.payload)) || {};
    toast(`Expense report created (${(data.costItems||data.lines) ? (data.costItems||data.lines).length + " lines" : "ok"}).`, "ok");
    closeReportCreate();
    loadReports();
    // After a report is created, automatically run the R10 requisition-mismatch audit.
    // Only jump to the Audit tab when the active role can see it (admins); travelers
    // just get the verdict toast and the audit pill on their report row.
    const planId = data.tripPlanId || rc.planId;
    if (planId) runR10Audit(planId, { switchTab: roleAllows("audit"), source: "auto" });
  } catch (e) {
    $("rcMsg").textContent = "Submit failed: " + friendlyError(e.message);
  } finally {
    btns.forEach((b) => (b.disabled = false));
  }
}

function tempSaveReport() {
  toast(rc.sessionId ? "Saved — the report session auto-saves each upload." : "Nothing to save yet.", rc.sessionId ? "ok" : "");
}

/* ================================================================
 *  TAB 4 — AUDIT  (rule engine v2, server-backed)
 *    POST /api/v2/rule-engine/r10            run an audit
 *    GET  /api/v2/rule-engine/audits?corpNo= list persisted audits
 *    GET  /api/v2/rule-engine/audits/{id}    one persisted audit
 * ================================================================ */
const RULE_ENGINE_API = API_ORIGIN + "/api/v2/rule-engine";
let auditsCache = [];          // newest-first list of normalized audits
let auditFilter = "all";       // all | normal | suspicion

/* The live R10 response and the persisted ComplianceAudit row have different
 * shapes (auditId vs id, findings vs rulesJson; the row stores no ruleId /
 * status / summary). Normalize both into one canonical audit object. */
function normalizeAudit(raw) {
  if (!raw) return null;
  const findings = raw.findings || raw.rulesJson || [];
  const up = (f) => (f.status || "").toUpperCase();
  const fails = findings.filter((f) => up(f) === "FAIL").length;
  const passes = findings.filter((f) => up(f) === "PASS").length;
  return {
    auditId: raw.auditId || raw.id,
    corpNo: raw.corpNo || "",
    tripPlanId: raw.tripPlanId || null,
    reportId: raw.reportId || null,
    ruleId: raw.ruleId || "R10",
    ruleName: raw.ruleName || "Requisition Mismatch",
    status: raw.status || (fails ? "Failed" : passes ? "Pass" : "SKIPPED"),
    complianceStatus: raw.complianceStatus || "",
    confidenceLevel: raw.confidenceLevel || "",
    summary: raw.summary || (fails
      ? `R10 found ${fails} mismatch dimension(s) between the trip plan and its expense lines.`
      : "R10 found no mismatch between the trip plan and its expense lines."),
    findings,
    createdDate: raw.createdDate || "",
  };
}

/* Run R10 for a trip plan. Returns the normalized audit (or null on failure). */
async function runR10Audit(tripPlanId, opts = {}) {
  if (!tripPlanId) { toast("No trip plan to audit.", "err"); return null; }
  const { switchTab = false, source = "manual" } = opts;
  beginLoad();
  if (source === "auto") toast("Running R10 compliance audit…", "");
  try {
    const res = await fetch(`${RULE_ENGINE_API}/r10`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ corpNo: CORP_NO, tripPlanId }),
    });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    const payload = normalizeAudit((json && (json.payload || json.data)) || null);
    if (!payload) throw new Error("Audit returned no result.");
    // Keep the in-memory list fresh; the tab re-fetches from the server when shown.
    auditsCache = [payload, ...auditsCache.filter((x) => x.auditId !== payload.auditId)];
    const sus = payload.complianceStatus === "SUSPICION";
    toast(`R10 ${sus ? "flagged a mismatch" : "passed"} — ${payload.status}.`, sus ? "err" : "ok");
    if (switchTab) showTab("audit"); else if (currentTab === "audit") renderAudits();
    return payload;
  } catch (e) {
    toast("Audit failed: " + friendlyError(e.message), "err");
    return null;
  } finally {
    endLoad();
  }
}

/* The Audit tab lists the persisted audits for the active corp. */
async function loadAudits() {
  const body = $("auditBody");
  body.innerHTML = loadingRow(9);
  beginLoad();
  try {
    const res = await fetch(`${RULE_ENGINE_API}/audits?corpNo=${encodeURIComponent(CORP_NO)}`);
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    const list = (json && (json.payload || json.data)) || [];
    auditsCache = list.map(normalizeAudit).filter(Boolean);
    auditsCache.sort((a, b) => String(b.createdDate || "").localeCompare(String(a.createdDate || "")));
    renderAudits();
    animateRowsIn("auditBody");
  } catch (e) {
    auditsCache = [];
    updateAuditChipCounts();
    $("auditCount").textContent = 0; $("auditShown").textContent = 0; $("auditTotal").textContent = 0;
    body.innerHTML = emptyRow(9, { icon: "alert", title: "Couldn’t load audits", sub: esc(friendlyError(e.message)) });
  } finally {
    endLoad();
  }
}

function auditComplianceKey(a) {
  return (a.complianceStatus || "").toUpperCase() === "SUSPICION" ? "suspicion" : "normal";
}
function resultClass(status) {
  const s = (status || "").toLowerCase();
  if (s === "pass") return "audit-pass";
  if (s === "failed" || s === "fail") return "audit-fail";
  return "audit-skip";
}
function confidenceClass(c) {
  const s = (c || "").toLowerCase();
  if (s === "high") return "audit-conf-high";
  if (s === "medium") return "audit-conf-medium";
  if (s === "low") return "audit-conf-low";
  return "audit-skip";
}
function shortDateTime(s) {
  if (!s) return "—";
  return String(s).replace("T", " ").slice(0, 16);
}
function planTitleFor(tripPlanId) {
  if (!tripPlanId) return "—";
  const p = (plansCache || []).find((x) => x.id === tripPlanId);
  return (p && p.title) || ("…" + String(tripPlanId).slice(-8));
}

function updateAuditChipCounts() {
  let norm = 0, sus = 0;
  auditsCache.forEach((a) => { auditComplianceKey(a) === "suspicion" ? sus++ : norm++; });
  $("cfAll").textContent = auditsCache.length;
  $("cfNorm").textContent = norm;
  $("cfSus").textContent = sus;
}

function renderAudits() {
  updateAuditChipCounts();
  const q = ($("auditSearch").value || "").trim().toLowerCase();
  const list = auditsCache.filter((a) => {
    if (auditFilter !== "all" && auditComplianceKey(a) !== auditFilter) return false;
    if (!q) return true;
    const hay = [a.auditId, a.tripPlanId, a.ruleId, a.ruleName, a.status, a.complianceStatus, a.confidenceLevel, a.summary, planTitleFor(a.tripPlanId)]
      .filter(Boolean).join(" ").toLowerCase();
    return hay.includes(q);
  });
  $("auditCount").textContent = auditsCache.length;
  $("auditShown").textContent = list.length;
  $("auditTotal").textContent = auditsCache.length;
  const body = $("auditBody");
  if (!list.length) {
    body.innerHTML = auditsCache.length
      ? emptyRow(9, { icon: "search", title: "No audits match this filter", sub: "Try a different search or compliance chip." })
      : emptyRow(9, { icon: "shield", title: "No audits yet", sub: "R10 runs automatically each time an expense report is created.",
          action: roleAllows("report") ? `<button class="btn btn-primary btn-sm" onclick="showTab('report')">Go to Expense Report</button>` : "" });
    updateAuditSelBar();
    return;
  }
  body.innerHTML = list.map((a, i) => {
    const compKey = auditComplianceKey(a);
    const compLabel = compKey === "suspicion" ? "Suspicion" : "Normal";
    return `<tr class="row-click" data-audit-id="${esc(a.auditId)}">
      <td class="c-check"><input type="checkbox" class="chk audit-chk" data-id="${esc(a.auditId)}" aria-label="Select audit" /></td>
      <td class="c-no">${i + 1}</td>
      <td class="c-title" title="${esc(a.tripPlanId || "")}">${esc(planTitleFor(a.tripPlanId))}</td>
      <td><span class="purpose-tag" title="${esc(a.ruleName || "")}">${esc(a.ruleId || "R10")}</span></td>
      <td><span class="audit-pill ${resultClass(a.status)}">${esc(a.status || "—")}</span></td>
      <td><span class="audit-pill audit-${compKey}">${esc(compLabel)}</span></td>
      <td><span class="audit-pill ${confidenceClass(a.confidenceLevel)}">${esc(a.confidenceLevel || "—")}</span></td>
      <td class="audit-summary" title="${esc(a.summary || "")}">${esc(a.summary || "—")}</td>
      <td class="c-period">${esc(shortDateTime(a.createdDate))}</td>
    </tr>`;
  }).join("");
  updateAuditSelBar();
}

/* ---- audit selection + delete (DELETE /audits/batch and /audits/{id}) ---- */
function updateAuditSelBar() {
  const all = Array.from(document.querySelectorAll(".audit-chk"));
  const checked = all.filter((c) => c.checked);
  $("auditSelCount").textContent = checked.length;
  $("auditSelBar").classList.toggle("hidden", checked.length === 0);
  const head = $("auditSelectAll");
  if (head) {
    head.checked = all.length > 0 && checked.length === all.length;
    head.indeterminate = checked.length > 0 && checked.length < all.length;
  }
}
function toggleAuditSelectAll() {
  const on = $("auditSelectAll").checked;
  document.querySelectorAll(".audit-chk").forEach((c) => { c.checked = on; });
  updateAuditSelBar();
}
function clearAuditSelection() {
  document.querySelectorAll(".audit-chk").forEach((c) => { c.checked = false; });
  const head = $("auditSelectAll"); if (head) { head.checked = false; head.indeterminate = false; }
  updateAuditSelBar();
}
async function deleteSelectedAudits() {
  const ids = Array.from(document.querySelectorAll(".audit-chk"))
    .filter((c) => c.checked).map((c) => c.getAttribute("data-id")).filter(Boolean);
  if (!ids.length) return;
  const ok = await confirmDialog({
    title: "Remove audit" + (ids.length === 1 ? "" : "s"),
    message: `Remove ${ids.length === 1 ? "this audit" : ids.length + " audits"}? This deletes the persisted audit result${ids.length === 1 ? "" : "s"} and cannot be undone.`,
    confirmText: "Remove",
  });
  if (!ok) return;
  const btn = $("auditSelDelete");
  btn.disabled = true; btn.textContent = "Removing…";
  beginLoad();
  try {
    let deleted = ids.length;
    if (ids.length === 1) {
      const res = await fetch(`${RULE_ENGINE_API}/audits/${encodeURIComponent(ids[0])}`, { method: "DELETE" });
      const json = await res.json().catch(() => ({}));
      if (!res.ok) throw apiError(json, res);
    } else {
      const res = await fetch(`${RULE_ENGINE_API}/audits/batch`, {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ids }),
      });
      const json = await res.json().catch(() => ({}));
      if (!res.ok) throw apiError(json, res);
      const r = (json && (json.payload || json.data)) || {};
      if (r.deleted != null) deleted = r.deleted;
    }
    toast(`Removed ${deleted} audit${deleted === 1 ? "" : "s"}.`, "ok");
    loadAudits();
  } catch (e) {
    toast("Remove failed: " + friendlyError(e.message), "err");
  } finally {
    btn.disabled = false; btn.innerHTML = svgIcon("trash") + " Remove";
    endLoad();
  }
}

/* ---- analyst override (PUT /audits/{id}) ---- */
async function saveAuditOverride(auditId) {
  const complianceStatus = $("aoCompliance").value;
  const confidenceLevel = $("aoConfidence").value;
  const btn = $("aoSaveBtn");
  btn.disabled = true; btn.textContent = "Saving…";
  beginLoad();
  try {
    const res = await fetch(`${RULE_ENGINE_API}/audits/${encodeURIComponent(auditId)}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ complianceStatus, confidenceLevel }),
    });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    const updated = normalizeAudit((json && (json.payload || json.data)) || null);
    if (updated) {
      auditsCache = auditsCache.map((x) => (x.auditId === updated.auditId ? updated : x));
      if (currentTab === "audit") renderAudits();
      toast("Audit verdict updated.", "ok");
      await renderAuditDetail(updated);
    }
  } catch (e) {
    toast("Update failed: " + friendlyError(e.message), "err");
    btn.disabled = false; btn.textContent = "Save verdict";
  } finally {
    endLoad();
  }
}

/* ---- Audit detail modal ---- */
let auditModalPlanId = null;
function findingClass(status) {
  const s = (status || "").toUpperCase();
  if (s === "PASS") return "finding-pass";
  if (s === "FAIL") return "finding-fail";
  return "finding-skip";
}
function findingIcon(status) {
  const s = (status || "").toUpperCase();
  if (s === "PASS") return "✓";
  if (s === "FAIL") return "✕";
  return "–";
}
function prettyDimension(d) {
  return String(d || "").replace(/_/g, " ").toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
}
/* Plain-language explanation of what each R10 dimension verifies. */
const DIMENSION_INFO = {
  APPROVAL_GATE:      { name: "Approval Gate",      what: "Was the trip plan approved before expenses were claimed?" },
  DATE_ALIGNMENT:     { name: "Date Alignment",     what: "Does every expense date fall inside the approved trip period?" },
  LOCATION_ALIGNMENT: { name: "Location Alignment", what: "Do expense locations match the planned destination and route?" },
  AMOUNT_ALIGNMENT:   { name: "Amount Alignment",   what: "Is the claimed total within the plan’s budget?" },
  RECEIPT_BACKING:    { name: "Receipt Backing",    what: "Is every expense line backed by an uploaded receipt?" },
};

/* The approved trip period for an audit: prefer the cached plan, fall back to
 * the "[YYYY-MM-DD .. YYYY-MM-DD]" range embedded in the DATE_ALIGNMENT detail. */
function auditPlanPeriod(a) {
  const p = (plansCache || []).find((x) => x.id === a.tripPlanId);
  if (p && p.businessStartDate && p.businessEndDate) return [String(p.businessStartDate), String(p.businessEndDate)];
  if (p && p.businessPeriod) {
    const m = String(p.businessPeriod).split(/\s+to\s+/i);
    if (m.length === 2) return [m[0].trim(), m[1].trim()];
  }
  const df = (a.findings || []).find((f) => f.dimension === "DATE_ALIGNMENT");
  const m = df && String(df.detail || "").match(/\[(\d{4}-\d{2}-\d{2}) \.\. (\d{4}-\d{2}-\d{2})\]/);
  return m ? [m[1], m[2]] : null;
}

/* Locations the rule engine flagged as unrelated (parsed from the FAIL detail,
 * e.g. "…unrelated to the planned destination/legs: Phnom Penh."). */
function auditFlaggedLocations(a) {
  const lf = (a.findings || []).find((f) => f.dimension === "LOCATION_ALIGNMENT" && (f.status || "").toUpperCase() === "FAIL");
  if (!lf) return [];
  const m = String(lf.detail || "").match(/:\s*([^:]+?)\.?\s*$/);
  return m ? m[1].split(/,\s*/).map((x) => x.trim()).filter(Boolean) : [];
}

/* Normalize one report line for the audit breakdown table. */
function auditLineView(ln) {
  const te = ln.transportationExpense, c = ln.costExpense || {};
  if (te) {
    return {
      desc: te.description || te.transportationMethod || te.vendor || "Transport",
      date: te.usageDate || te.evidenceDate || "",
      place: [te.originLocation, te.destinationLocation].filter(Boolean).join(" → "),
      amount: num(te.applicationAmount),
    };
  }
  return {
    desc: c.description || c.category || c.account || "Expense",
    date: c.evidenceDate || c.startDate || "",
    place: c.note || c.usePurpose || "",
    amount: num(c.applicationAmount),
  };
}

/* All expense lines of the report(s) settling this trip plan. */
async function fetchAuditReportLines(tripPlanId) {
  if (!tripPlanId) return [];
  const res = await fetch(`${EXPENSE_API}?corpNo=${encodeURIComponent(CORP_NO)}`);
  const json = await res.json().catch(() => ({}));
  if (!res.ok) throw apiError(json, res);
  const reports = (json && (json.data || json.payload)) || [];
  return reports.filter((r) => r.tripPlanId === tripPlanId).flatMap((r) => r.costItems || r.lines || []);
}

/* Section catalogue + per-line flag computation, shared by the on-screen
 * breakdown and the CSV export so both always agree. */
const AUDIT_SECTIONS = [
  { code: "COST", label: "Cost — accommodation, meals & other costs", ko: "비용" },
  { code: "TRANSPORTATION", label: "Transportation", ko: "교통비" },
  { code: "ETC", label: "Etc", ko: "기타" },
];
function auditLineSection(ln) {
  return String(ln.sectionCode || (ln.transportationExpense ? "TRANSPORTATION" : "COST")).toUpperCase();
}
function auditFlagContext(a) {
  return {
    period: auditPlanPeriod(a),
    badLocs: auditFlaggedLocations(a).map((l) => l.toLowerCase()),
    receiptFail: (a.findings || []).some((f) => f.dimension === "RECEIPT_BACKING" && (f.status || "").toUpperCase() === "FAIL"),
  };
}
/* Plain-text flags for one line (["Outside trip period", ...]; empty = ok). */
function auditLineFlags(ctx, ln, v) {
  const flags = [];
  if (ctx.period && v.date && (String(v.date) < ctx.period[0] || String(v.date) > ctx.period[1])) flags.push("Outside trip period");
  const hay = (v.place + " " + v.desc).toLowerCase();
  if (ctx.badLocs.some((l) => hay.includes(l))) flags.push("Unplanned location");
  if (ctx.receiptFail && !ln.attachmentFileId) flags.push("No receipt");
  return flags;
}

/* Per-section (Cost / Transportation / Etc) breakdown with problem lines flagged. */
function renderAuditLines(a, lines) {
  if (!lines.length) {
    return `<p class="muted-pad">No expense lines found for this trip plan — the report may have been deleted since this audit ran.</p>`;
  }
  const ctx = auditFlagContext(a);
  return AUDIT_SECTIONS.map((sec) => {
    const items = lines.filter((ln) => auditLineSection(ln) === sec.code);
    if (!items.length) return "";
    const rows = items.map((ln) => {
      const v = auditLineView(ln);
      const flags = auditLineFlags(ctx, ln, v);
      const chips = flags.map((f) =>
        `<span class="flag-chip"${f === "Outside trip period" && ctx.period ? ` title="Trip period is ${esc(ctx.period[0])} ~ ${esc(ctx.period[1])}"` : ""}>${esc(f)}</span>`);
      return `<tr class="${flags.length ? "al-bad" : ""}">
        <td title="${esc(v.desc)}">${esc(v.desc)}</td>
        <td class="c-period">${esc(v.date || "—")}</td>
        <td title="${esc(v.place)}">${esc(v.place || "—")}</td>
        <td class="amt">${fmtMoney(v.amount)}</td>
        <td>${ln.attachmentFileId ? receiptLink(ln.attachmentFileId, "Receipt") : `<span class="muted">—</span>`}</td>
        <td>${chips.join(" ") || `<span class="flag-ok">✓ ok</span>`}</td>
      </tr>`;
    }).join("");
    return `<div class="al-sec">
      <div class="al-sec-head">${esc(sec.label)} <span class="ko-sub">${esc(sec.ko)}</span><span class="al-count">${items.length} line${items.length === 1 ? "" : "s"}</span></div>
      <div class="er-table-wrap"><table class="er-table">
        <thead><tr><th style="width:30%">Description</th><th>Date</th><th style="width:21%">Route / Place</th><th style="text-align:right">Amount</th><th style="width:11%">Receipt</th><th style="width:19%">Check</th></tr></thead>
        <tbody>${rows}</tbody>
      </table></div>
    </div>`;
  }).join("");
}

/* Open the detail modal: show a loading shell, fetch the persisted audit by id
 * (GET /audits/{id}), and fall back to the listing copy if the fetch fails. */
async function openAuditDetail(auditId) {
  $("auditModalMsg").textContent = "";
  $("auditModalTitle").textContent = "Audit detail";
  $("auditKicker").textContent = "R10";
  $("auditModalSub").textContent = "";
  $("auditModalBody").innerHTML = `<div class="draft-empty-wrap"><span class="spin spin-lg"></span><p>Loading audit…</p></div>`;
  $("auditRerunBtn").disabled = true;
  $("auditDownloadBtn").disabled = true;
  $("auditDlMenu").classList.add("hidden");
  auditModalPlanId = null; auditModalAudit = null; auditModalLines = [];
  $("auditOverlay").classList.remove("hidden");

  let a = null;
  try {
    const res = await fetch(`${RULE_ENGINE_API}/audits/${encodeURIComponent(auditId)}`);
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    a = normalizeAudit((json && (json.payload || json.data)) || null);
  } catch (e) {
    a = auditsCache.find((x) => x.auditId === auditId) || null;
    if (!a) {
      $("auditModalBody").innerHTML = `<div class="draft-empty-wrap">${svgIcon("alert", "ico-lg")}<p>${esc(friendlyError(e.message))}</p></div>`;
      return;
    }
  }
  await renderAuditDetail(a);
}

async function renderAuditDetail(a) {
  auditModalPlanId = a.tripPlanId || null;
  auditModalAudit = a;
  auditModalLines = [];
  $("auditDownloadBtn").disabled = false;
  $("auditModalMsg").textContent = "";
  $("auditModalTitle").textContent = a.ruleName || "Audit detail";
  $("auditKicker").textContent = `${a.ruleId || "R10"} · ${shortDateTime(a.createdDate)}`;
  $("auditModalSub").textContent = `Trip plan: ${planTitleFor(a.tripPlanId)}`;
  const findings = a.findings || [];
  const fails = findings.filter((f) => (f.status || "").toUpperCase() === "FAIL").length;
  const passes = findings.filter((f) => (f.status || "").toUpperCase() === "PASS").length;
  const skips = findings.length - fails - passes;
  const sus = auditComplianceKey(a) === "suspicion";
  $("auditModalBody").innerHTML = `
    <div class="audit-verdict ${sus ? "av-bad" : "av-ok"}">
      ${svgIcon(sus ? "alert" : "check-circle", "ico-lg")}
      <div>
        <strong>${sus
          ? `${fails} of ${findings.length} checks failed — this expense report needs review before approval.`
          : "All evaluable checks passed — the expenses match the approved trip plan."}</strong>
        <span>${passes} passed · ${fails} failed · ${skips} skipped — details below, with the affected expense lines flagged per section.</span>
      </div>
    </div>
    <div class="audit-detail-head">
      <div class="adh-item"><span class="adh-k">Result</span><span class="adh-v"><span class="audit-pill ${resultClass(a.status)}">${esc(a.status || "—")}</span></span></div>
      <div class="adh-item"><span class="adh-k">Compliance</span><span class="adh-v"><span class="audit-pill audit-${auditComplianceKey(a)}">${esc(a.complianceStatus || "—")}</span></span></div>
      <div class="adh-item"><span class="adh-k">Confidence</span><span class="adh-v"><span class="audit-pill ${confidenceClass(a.confidenceLevel)}">${esc(a.confidenceLevel || "—")}</span></span></div>
      <div class="adh-item"><span class="adh-k">Audit ID</span><span class="adh-v"><span class="audit-id">${esc(a.auditId)}</span></span></div>
    </div>
    <div class="audit-override">
      <span class="ao-label">Analyst override</span>
      <div class="select-wrap"><select id="aoCompliance" aria-label="Compliance status">
        ${["NORMAL", "SUSPICION"].map((v) => `<option value="${v}" ${v === (a.complianceStatus || "").toUpperCase() ? "selected" : ""}>${v}</option>`).join("")}
      </select></div>
      <div class="select-wrap"><select id="aoConfidence" aria-label="Confidence level">
        ${["HIGH", "MEDIUM", "LOW"].map((v) => `<option value="${v}" ${v === (a.confidenceLevel || "").toUpperCase() ? "selected" : ""}>${v}</option>`).join("")}
      </select></div>
      <button class="btn btn-quiet btn-sm" id="aoSaveBtn">Save verdict</button>
      <span class="ao-hint">Manually correct the compliance / confidence verdict of this audit.</span>
    </div>
    <div class="al-title-h">The 5 checks</div>
    <div class="findings">
      ${findings.map((f) => {
        const info = DIMENSION_INFO[String(f.dimension || "").toUpperCase()] || { name: prettyDimension(f.dimension), what: "" };
        return `
        <div class="finding ${findingClass(f.status)}">
          <span class="finding-ico">${findingIcon(f.status)}</span>
          <div class="finding-main">
            <div class="finding-dim">${esc(info.name)}</div>
            ${info.what ? `<div class="finding-what">${esc(info.what)}</div>` : ""}
            <div class="finding-detail">${esc(f.detail || "")}</div>
          </div>
          <span class="audit-pill ${resultClass(f.status === "PASS" ? "pass" : f.status === "FAIL" ? "failed" : "skip")}">${esc(f.status || "—")}</span>
        </div>`;
      }).join("") || `<p class="draft-empty">No dimension findings.</p>`}
    </div>
    <div class="al-title-h">Expense lines reviewed — by report section</div>
    <div id="auditLines"><p class="muted-pad"><span class="spin"></span>Loading expense lines…</p></div>`;
  $("aoSaveBtn").addEventListener("click", () => saveAuditOverride(a.auditId));
  $("auditRerunBtn").disabled = !auditModalPlanId;
  $("auditOverlay").classList.remove("hidden");
  // Load the report lines asynchronously so the verdict shows instantly.
  try {
    const lines = await fetchAuditReportLines(a.tripPlanId);
    auditModalLines = lines;
    const el = $("auditLines");
    if (el) el.innerHTML = renderAuditLines(a, lines);
  } catch (e) {
    const el = $("auditLines");
    if (el) el.innerHTML = `<p class="muted-pad">Couldn’t load expense lines: ${esc(friendlyError(e.message))}</p>`;
  }
}
function closeAuditDetail() {
  $("auditOverlay").classList.add("hidden");
  $("auditDlMenu").classList.add("hidden");
  auditModalPlanId = null; auditModalAudit = null; auditModalLines = [];
}
async function rerunCurrentAudit() {
  if (!auditModalPlanId) return;
  const btn = $("auditRerunBtn");
  btn.disabled = true; btn.textContent = "Running…";
  const payload = await runR10Audit(auditModalPlanId, { source: "manual" });
  btn.disabled = false; btn.innerHTML = svgIcon("refresh") + " Re-run audit";
  if (payload) openAuditDetail(payload.auditId);
}

/* ---- Download the open audit (CSV report / raw JSON) ---- */
let auditModalAudit = null;   // normalized audit shown in the modal
let auditModalLines = [];     // its expense lines (loaded async)

function downloadBlob(filename, mime, content) {
  const blob = new Blob([content], { type: mime });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url; link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/* One CSV with three blocks: audit header, the five checks, expense lines.
 * UTF-8 BOM so Excel renders the Korean section names correctly. */
function auditToCsv(a, lines) {
  const q = (v) => `"${String(v == null ? "" : v).replace(/"/g, '""')}"`;
  const row = (...cells) => cells.map(q).join(",");
  const out = [];
  out.push(row(`${a.ruleId} — ${a.ruleName} audit`));
  out.push(row("Audit ID", a.auditId));
  out.push(row("Corp No", a.corpNo || CORP_NO));
  out.push(row("Trip plan", planTitleFor(a.tripPlanId)), row("Trip plan ID", a.tripPlanId || ""));
  out.push(row("Result", a.status), row("Compliance", a.complianceStatus), row("Confidence", a.confidenceLevel));
  out.push(row("Created", a.createdDate), row("Summary", a.summary));
  out.push("");
  out.push(row("Checks"));
  out.push(row("Check", "What it verifies", "Status", "Detail"));
  (a.findings || []).forEach((f) => {
    const info = DIMENSION_INFO[String(f.dimension || "").toUpperCase()] || { name: prettyDimension(f.dimension), what: "" };
    out.push(row(info.name, info.what, f.status, f.detail));
  });
  out.push("");
  out.push(row("Expense lines"));
  out.push(row("Section", "Description", "Date", "Route / Place", "Amount", "Flags", "Receipt download"));
  const ctx = auditFlagContext(a);
  AUDIT_SECTIONS.forEach((sec) => {
    lines.filter((ln) => auditLineSection(ln) === sec.code).forEach((ln) => {
      const v = auditLineView(ln);
      const flags = auditLineFlags(ctx, ln, v);
      const dl = ln.attachmentFileId ? fileDownloadUrl(ln.attachmentFileId) : "";
      out.push(row(sec.label, v.desc, v.date, v.place, v.amount, flags.join("; ") || "ok",
        dl && !/^https?:/i.test(dl) ? location.origin + dl : dl));
    });
  });
  return "\uFEFF" + out.join("\r\n");
}

function downloadAudit(kind) {
  const a = auditModalAudit;
  if (!a) return;
  const stem = `r10-audit-${String(a.auditId).slice(0, 8)}`;
  if (kind === "json") {
    downloadBlob(`${stem}.json`, "application/json;charset=utf-8",
      JSON.stringify({ ...a, tripPlanTitle: planTitleFor(a.tripPlanId), expenseLines: auditModalLines }, null, 2));
  } else {
    downloadBlob(`${stem}.csv`, "text/csv;charset=utf-8", auditToCsv(a, auditModalLines));
  }
  $("auditDlMenu").classList.add("hidden");
  toast(`Downloaded ${stem}.${kind === "json" ? "json" : "csv"}`, "ok");
}

/* ---- Audit tab wiring ---- */
function initAuditTab() {
  $("auditRefresh").addEventListener("click", loadAudits);
  $("auditChips").addEventListener("click", (ev) => {
    const c = ev.target.closest(".statchip");
    if (!c) return;
    auditFilter = c.getAttribute("data-cf");
    document.querySelectorAll("#auditChips .statchip").forEach((x) => x.classList.toggle("active", x === c));
    renderAudits();
  });
  $("auditSearch").addEventListener("input", () => {
    $("auditSearchClear").classList.toggle("hidden", !$("auditSearch").value);
    renderAudits();
  });
  $("auditSearchClear").addEventListener("click", () => {
    $("auditSearch").value = "";
    $("auditSearchClear").classList.add("hidden");
    renderAudits();
    $("auditSearch").focus();
  });
  $("auditBody").addEventListener("click", (ev) => {
    if (ev.target.closest(".c-check")) return;
    const row = ev.target.closest("[data-audit-id]");
    if (row) openAuditDetail(row.getAttribute("data-audit-id"));
  });
  $("auditBody").addEventListener("change", (ev) => {
    if (ev.target.classList.contains("audit-chk")) updateAuditSelBar();
  });
  $("auditSelectAll").addEventListener("change", toggleAuditSelectAll);
  $("auditSelClear").addEventListener("click", clearAuditSelection);
  $("auditSelDelete").addEventListener("click", deleteSelectedAudits);
  $("auditModalClose").addEventListener("click", closeAuditDetail);
  $("auditModalClose2").addEventListener("click", closeAuditDetail);
  $("auditOverlay").addEventListener("click", (ev) => { if (ev.target === $("auditOverlay")) closeAuditDetail(); });
  $("auditRerunBtn").addEventListener("click", rerunCurrentAudit);
  // Download menu: toggle on the button, act on an item, dismiss on outside click / Esc.
  $("auditDownloadBtn").addEventListener("click", (ev) => {
    ev.stopPropagation();
    $("auditDlMenu").classList.toggle("hidden");
  });
  $("auditDlMenu").addEventListener("click", (ev) => {
    const item = ev.target.closest("[data-dl]");
    if (item) downloadAudit(item.getAttribute("data-dl"));
  });
  document.addEventListener("click", (ev) => {
    if (!ev.target.closest(".dl-wrap")) $("auditDlMenu").classList.add("hidden");
  });
  document.addEventListener("keydown", (ev) => {
    if (ev.key === "Escape") $("auditDlMenu").classList.add("hidden");
  });
}

/* ================================================================
 *  STAFF & DEPARTMENTS — master-data CRUD (modal on the Request Plan tab)
 *    GET / POST  /api/v1/departments (?corpNo=)   PUT / DELETE  /{id}
 *    GET / POST  /api/v1/staff       (?corpNo=)   PUT / DELETE  /{id}
 *  Also feeds the create-plan traveller dropdowns (liveStaffList / liveDeptNames).
 * ================================================================ */
const DEPT_API = API_ORIGIN + "/api/v1/departments";
const STAFF_API = API_ORIGIN + "/api/v1/staff";
let mdDepts = [], mdStaff = [];
let mdDeptFilter = null;                       // department id, or null = all staff
let mdEditDeptId = null, mdEditStaffId = null; // rows currently in edit mode

/* Live lists for the create-plan dropdowns; fall back to the seed constants
 * while the corp has no master data (or the API is unreachable). */
function liveStaffList() {
  return mdStaff.length
    ? mdStaff.map((s) => ({ name: s.name, department: s.departmentName || "", position: s.position || "" }))
    : STAFF;
}
function liveDeptNames() {
  return mdDepts.length ? mdDepts.map((d) => d.name) : DEPARTMENTS;
}

async function fetchMasterData() {
  const [dRes, sRes] = await Promise.all([
    fetch(`${DEPT_API}?corpNo=${encodeURIComponent(CORP_NO)}`),
    fetch(`${STAFF_API}?corpNo=${encodeURIComponent(CORP_NO)}`),
  ]);
  const dJson = await dRes.json().catch(() => ({}));
  const sJson = await sRes.json().catch(() => ({}));
  if (!dRes.ok) throw apiError(dJson, dRes);
  if (!sRes.ok) throw apiError(sJson, sRes);
  mdDepts = (dJson && (dJson.data || dJson.payload)) || [];
  mdStaff = (sJson && (sJson.data || sJson.payload)) || [];
}

/* Background refresh (app start / corp switch) — errors keep the seed fallback. */
async function loadMasterDataSilent() {
  try { await fetchMasterData(); } catch { mdDepts = []; mdStaff = []; }
}

async function loadMasterData() {
  beginLoad();
  try {
    await fetchMasterData();
    $("masterMsg").textContent = "";
  } catch (e) {
    $("masterMsg").textContent = friendlyError(e.message);
  } finally {
    endLoad();
    renderMdDepts();
    renderMdStaff();
  }
}

function openMaster() {
  mdDeptFilter = null; mdEditDeptId = null; mdEditStaffId = null;
  $("mdDeptName").value = ""; $("mdStaffName").value = ""; $("mdStaffPosition").value = "";
  $("masterMsg").textContent = "";
  $("masterOverlay").classList.remove("hidden");
  loadMasterData();
}
function closeMaster() { $("masterOverlay").classList.add("hidden"); }

function renderMdDepts() {
  $("mdDeptCount").textContent = mdDepts.length;
  // keep the add-staff department select in sync
  const keep = $("mdStaffDept").value;
  $("mdStaffDept").innerHTML = `<option value="">Department…</option>` +
    mdDepts.map((d) => `<option value="${esc(d.id)}" ${d.id === keep ? "selected" : ""}>${esc(d.name)}</option>`).join("");
  const box = $("mdDeptList");
  if (!mdDepts.length) {
    box.innerHTML = `<div class="muted-pad">No departments yet — add the first one above.</div>`;
    return;
  }
  box.innerHTML = mdDepts.map((d) => {
    if (mdEditDeptId === d.id) {
      return `<div class="md-row md-active">
        <input type="text" id="mdDeptEditInput" maxlength="100" value="${esc(d.name)}" />
        <span class="md-acts">
          <button class="btn-xs btn-view" data-act="dept-save" data-id="${esc(d.id)}">Save</button>
          <button class="btn-xs" data-act="dept-cancel">Cancel</button>
        </span>
      </div>`;
    }
    const n = mdStaff.filter((s) => s.departmentId === d.id).length;
    return `<div class="md-row ${mdDeptFilter === d.id ? "md-active" : ""}" data-act="dept-filter" data-id="${esc(d.id)}">
      <span class="md-name" title="${esc(d.name)}">${esc(d.name)}</span>
      <span class="md-sub">${n} staff</span>
      <span class="md-acts">
        <button class="btn-xs btn-view" data-act="dept-edit" data-id="${esc(d.id)}" title="Rename">✎</button>
        <button class="btn-xs btn-cancel" data-act="dept-del" data-id="${esc(d.id)}" title="Delete">${svgIcon("trash")}</button>
      </span>
    </div>`;
  }).join("");
}

function renderMdStaff() {
  const list = mdDeptFilter ? mdStaff.filter((s) => s.departmentId === mdDeptFilter) : mdStaff;
  $("mdStaffCount").textContent = list.length;
  const fd = mdDeptFilter ? mdDepts.find((d) => d.id === mdDeptFilter) : null;
  const tag = $("mdStaffFilterTag");
  tag.classList.toggle("hidden", !fd);
  tag.textContent = fd ? `${fd.name} ✕` : "";
  const body = $("mdStaffBody");
  if (!list.length) {
    body.innerHTML = `<tr><td colspan="4" class="empty-row">${mdStaff.length ? "No staff in this department." : "No staff yet — add the first one above."}</td></tr>`;
    return;
  }
  body.innerHTML = list.map((s) => {
    if (mdEditStaffId === s.id) {
      const opts = mdDepts.map((d) => `<option value="${esc(d.id)}" ${d.id === s.departmentId ? "selected" : ""}>${esc(d.name)}</option>`).join("");
      return `<tr>
        <td><input class="md-cell-input" id="mdsName" maxlength="100" value="${esc(s.name)}" /></td>
        <td><input class="md-cell-input" id="mdsPos" maxlength="100" value="${esc(s.position || "")}" /></td>
        <td><div class="select-wrap"><select id="mdsDept"><option value="">Department…</option>${opts}</select></div></td>
        <td><span class="md-acts">
          <button class="btn-xs btn-view" data-act="staff-save" data-id="${esc(s.id)}">Save</button>
          <button class="btn-xs" data-act="staff-cancel">Cancel</button>
        </span></td>
      </tr>`;
    }
    return `<tr>
      <td title="${esc(s.name)}">${esc(s.name)}</td>
      <td>${esc(s.position || "—")}</td>
      <td>${esc(s.departmentName || "—")}</td>
      <td><span class="md-acts">
        <button class="btn-xs btn-view" data-act="staff-edit" data-id="${esc(s.id)}">✎ Edit</button>
        <button class="btn-xs btn-cancel" data-act="staff-del" data-id="${esc(s.id)}">${svgIcon("trash")} Delete</button>
      </span></td>
    </tr>`;
  }).join("");
}

/* Shared JSON request for the CRUD calls. */
async function mdRequest(url, method, bodyObj) {
  const res = await fetch(url, {
    method,
    headers: bodyObj ? { "Content-Type": "application/json" } : undefined,
    body: bodyObj ? JSON.stringify(bodyObj) : undefined,
  });
  const json = await res.json().catch(() => ({}));
  if (!res.ok) throw apiError(json, res);
  return (json && (json.data || json.payload)) || null;
}

async function mdAddDept() {
  const name = $("mdDeptName").value.trim();
  if (!name) { toast("Enter a department name.", "err"); return; }
  try {
    await mdRequest(DEPT_API, "POST", { corpNo: CORP_NO, name });
    $("mdDeptName").value = "";
    toast(`Department “${name}” created.`, "ok");
    await loadMasterData();
  } catch (e) { toast("Create failed: " + friendlyError(e.message), "err"); }
}
async function mdSaveDept(id) {
  const input = $("mdDeptEditInput");
  const name = input ? input.value.trim() : "";
  if (!name) { toast("Name is required.", "err"); return; }
  try {
    await mdRequest(`${DEPT_API}/${encodeURIComponent(id)}`, "PUT", { corpNo: CORP_NO, name });
    mdEditDeptId = null;
    toast("Department renamed.", "ok");
    await loadMasterData();
  } catch (e) { toast("Update failed: " + friendlyError(e.message), "err"); }
}
async function mdDeleteDept(id) {
  const d = mdDepts.find((x) => x.id === id);
  const n = mdStaff.filter((s) => s.departmentId === id).length;
  const ok = await confirmDialog({
    title: "Delete department",
    message: `Delete “${(d && d.name) || id}”?` + (n
      ? ` It still has ${n} staff member${n === 1 ? "" : "s"} — move or delete them first if the server rejects this.`
      : " This cannot be undone."),
    confirmText: "Delete",
  });
  if (!ok) return;
  try {
    await mdRequest(`${DEPT_API}/${encodeURIComponent(id)}`, "DELETE");
    if (mdDeptFilter === id) mdDeptFilter = null;
    toast("Department deleted.", "ok");
    await loadMasterData();
  } catch (e) { toast("Delete failed: " + friendlyError(e.message), "err"); }
}

async function mdAddStaff() {
  const name = $("mdStaffName").value.trim();
  const position = $("mdStaffPosition").value.trim();
  const departmentId = $("mdStaffDept").value;
  if (!name) { toast("Enter a staff name.", "err"); return; }
  if (!departmentId) { toast("Pick a department for the new staff member.", "err"); return; }
  try {
    await mdRequest(STAFF_API, "POST", { departmentId, name, position });
    $("mdStaffName").value = ""; $("mdStaffPosition").value = "";
    toast(`Staff “${name}” created.`, "ok");
    await loadMasterData();
  } catch (e) { toast("Create failed: " + friendlyError(e.message), "err"); }
}
async function mdSaveStaff(id) {
  const nameEl = $("mdsName"), posEl = $("mdsPos"), deptEl = $("mdsDept");
  const name = nameEl ? nameEl.value.trim() : "";
  const position = posEl ? posEl.value.trim() : "";
  const departmentId = deptEl ? deptEl.value : "";
  if (!name) { toast("Name is required.", "err"); return; }
  if (!departmentId) { toast("Department is required.", "err"); return; }
  try {
    await mdRequest(`${STAFF_API}/${encodeURIComponent(id)}`, "PUT", { departmentId, name, position });
    mdEditStaffId = null;
    toast("Staff updated.", "ok");
    await loadMasterData();
  } catch (e) { toast("Update failed: " + friendlyError(e.message), "err"); }
}
async function mdDeleteStaff(id) {
  const s = mdStaff.find((x) => x.id === id);
  const ok = await confirmDialog({
    title: "Delete staff member",
    message: `Delete “${(s && s.name) || id}”? This cannot be undone.`,
    confirmText: "Delete",
  });
  if (!ok) return;
  try {
    await mdRequest(`${STAFF_API}/${encodeURIComponent(id)}`, "DELETE");
    toast("Staff deleted.", "ok");
    await loadMasterData();
  } catch (e) { toast("Delete failed: " + friendlyError(e.message), "err"); }
}

function initMasterData() {
  $("openMasterBtn").addEventListener("click", openMaster);
  $("masterCloseBtn").addEventListener("click", closeMaster);
  $("masterCloseBtn2").addEventListener("click", closeMaster);
  $("masterOverlay").addEventListener("click", (ev) => { if (ev.target === $("masterOverlay")) closeMaster(); });
  $("masterRefreshBtn").addEventListener("click", loadMasterData);
  $("mdDeptAddBtn").addEventListener("click", mdAddDept);
  $("mdDeptName").addEventListener("keydown", (ev) => { if (ev.key === "Enter") { ev.preventDefault(); mdAddDept(); } });
  $("mdStaffAddBtn").addEventListener("click", mdAddStaff);
  [$("mdStaffName"), $("mdStaffPosition")].forEach((el) =>
    el.addEventListener("keydown", (ev) => { if (ev.key === "Enter") { ev.preventDefault(); mdAddStaff(); } }));
  $("mdStaffFilterTag").addEventListener("click", () => { mdDeptFilter = null; renderMdDepts(); renderMdStaff(); });
  // Delegated actions: innermost [data-act] wins, so buttons beat the row's filter action.
  $("mdDeptList").addEventListener("click", (ev) => {
    const act = ev.target.closest("[data-act]");
    if (!act) return;
    const id = act.getAttribute("data-id");
    const kind = act.getAttribute("data-act");
    if (kind === "dept-edit") {
      mdEditDeptId = id; mdEditStaffId = null;
      renderMdDepts();
      const i = $("mdDeptEditInput"); if (i) { i.focus(); i.select(); }
    } else if (kind === "dept-save") mdSaveDept(id);
    else if (kind === "dept-cancel") { mdEditDeptId = null; renderMdDepts(); }
    else if (kind === "dept-del") mdDeleteDept(id);
    else if (kind === "dept-filter") { mdDeptFilter = mdDeptFilter === id ? null : id; renderMdDepts(); renderMdStaff(); }
  });
  $("mdStaffBody").addEventListener("click", (ev) => {
    const act = ev.target.closest("[data-act]");
    if (!act) return;
    const id = act.getAttribute("data-id");
    const kind = act.getAttribute("data-act");
    if (kind === "staff-edit") {
      mdEditStaffId = id; mdEditDeptId = null;
      renderMdStaff();
      const i = $("mdsName"); if (i) { i.focus(); i.select(); }
    } else if (kind === "staff-save") mdSaveStaff(id);
    else if (kind === "staff-cancel") { mdEditStaffId = null; renderMdStaff(); }
    else if (kind === "staff-del") mdDeleteStaff(id);
  });
}

document.addEventListener("DOMContentLoaded", () => { initI18n(); init(); initAuditTab(); initMasterData(); initRole(); initDemoBanner(); initLlm(); });
