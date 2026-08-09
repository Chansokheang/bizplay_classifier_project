/* ============================================================
 * Traditional Business Trip Plan — manual input demo
 * Talks to the same-origin REST API:
 *   GET  /api/v1/plans?corpNo=...
 *   POST /api/v1/plans
 * ============================================================ */

/* Corp number is editable from the header and remembered across reloads. */
let CORP_NO = localStorage.getItem("bizplay.corpNo")
  || (window.APP_CONFIG && window.APP_CONFIG.corpNo) || "1234567890";
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
  "Chat": "채팅", "Trip plan form": "출장 계획 양식", "Create Plan — Chat": "채팅으로 계획 작성",
  "Where is your business trip taking you?": "이번 출장은 어디로 가시나요?",
  "Describe it in your own words — I will draft the whole plan for you, from the trip form and dates to travellers and attachments.":
    "자유롭게 설명해 주세요 — 출장 양식과 일정부터 출장자와 첨부까지 계획 전체를 대신 작성해 드립니다.",
  "Or pick the form type yourself": "직접 양식 유형을 선택할게요",
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
  $("llmApiKey").placeholder = "Leave blank for open endpoints (no auth)";
  $("llmKeyHint").textContent = "";
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
  $("llmKeyHint").textContent = m.apiKeyMasked ? `Stored key: ${m.apiKeyMasked}` : "No key stored (open endpoint).";
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
  // API key is optional — open endpoints (no auth) are registered without one.

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

/* LLM model management: popup opened from the Settings page's "LLM models…" button. */
function openLlm() { llmFormReset(); $("llmOverlay").classList.remove("hidden"); loadLlmSettings(); loadLlmModels(); }
function closeLlm() { $("llmOverlay").classList.add("hidden"); }
function refreshLlm() { loadLlmSettings(); loadLlmModels(); }

function initLlm() {
  $("llmManageBtn").addEventListener("click", openLlm);
  $("llmCloseBtn").addEventListener("click", closeLlm);
  $("llmCloseBtn2").addEventListener("click", closeLlm);
  $("llmOverlay").addEventListener("mousedown", (ev) => { if (ev.target === $("llmOverlay")) closeLlm(); });
  $("llmRefreshBtn").addEventListener("click", refreshLlm);
  $("llmNewBtn").addEventListener("click", llmFormReset);
  $("llmCancelBtn").addEventListener("click", () => { llmEditing ? llmFormLoad(llmEditing) : llmFormReset(); });
  $("llmSaveBtn").addEventListener("click", llmSave);
  $("llmDeleteBtn").addEventListener("click", llmDelete);
  $("llmList").addEventListener("click", (ev) => {
    const row = ev.target.closest("[data-llm]");
    if (row) llmFormLoad(row.getAttribute("data-llm"));
  });
}

/* ================================================================
 * Agent Settings page (admin): per-sub-agent cards with an on/off toggle
 * and the DEFAULT prompt always visible; a Starter-conversation card
 * (greeting + GPT-style conversation-starter rows); the Orchestrator
 * model select. Everything is saved per corp and applies on the next turn.
 * ================================================================ */
const AG_CARDS = [
  { name: "guardrail", api: "LLM only — no external data API", label: "Guardrail", fixed: true, promptable: true, llm: true,
    desc: "Safety-classifies every message before any agent runs (SAFE / DATA_QUERY / DB_MUTATION / INJECTION)." },
  { name: "purpose-segment", api: "LLM only (the purpose catalog itself comes from the BizPlay API via the orchestrator)", label: "Purpose · Segment", fixed: true, promptable: true, llm: true,
    desc: "Maps the user's words to 출장 목적 and 출장 구분, loading the matching form." },
  { name: "field-mapper", api: "LLM only", label: "Field Mapper", fixed: true, promptable: true, llm: true,
    desc: "Maps free text (and extracted file facts) onto the loaded form's fields — every turn." },
  { name: "form-builder", api: "BizPlay API — retrieves the corp's form/paper definitions (endpoint configurable in Integrations)", label: "Form Builder", fixed: true, promptable: false, llm: false,
    desc: "Builds the save-body skeleton from the retrieved BizPlay form — structure is mirrored 1:1, never invented." },
  { name: "form-follow-up", api: "LLM only", label: "Follow-up", promptable: true, llm: true,
    desc: "Phrases ONE natural question asking for the fields still missing." },
  { name: "traveler-resolver", api: "LLM only — the roster is fetched from the BizPlay API by the orchestrator, not by this agent", label: "Traveler Resolver", promptable: true, llm: true,
    desc: "Resolves names, romanizations and department references against the staff roster." },
  { name: "place-validator", api: "LLM first; Naver Geocode API as the fallback", label: "Place Validator", promptable: true, llm: true,
    desc: "Checks that domestic destinations are real Korean places and normalizes them." },
  { name: "database-lookup", api: "Local PostgreSQL (SELECT-only) — no external API", label: "DB Lookup (NL→SQL)", promptable: false, llm: true,
    desc: "Answers read-only data questions (staff, departments, past plans) mid-conversation." },
  { name: "spreadsheet", api: "Local file parsing + LLM", label: "Spreadsheet", promptable: false, llm: true,
    desc: "Reads uploaded staff-list spreadsheets and queues the travelers." },
  { name: "pdf", api: "Local file parsing + LLM", label: "PDF", promptable: false, llm: true,
    desc: "Reads uploaded trip documents (bookings, itineraries) into destination, dates, title and content." },
];
let apData = [];
let apModules = [];

/* Full-page settings view: hides the tab content while open ("← To Chat" returns). */
function openAp() {
  $("apCorpPill").textContent = `corp ${CORP_NO}`;
  document.body.classList.add("ap-open");
  window.scrollTo(0, 0);
  apLoad();
  apSettleStarterLoad();   // its own endpoint — a failure here must not blank the rest
  caLoad();
  mcpLoad();
}
function closeAp() { document.body.classList.remove("ap-open"); }

async function apLoad() {
  $("apAgentDetail").innerHTML = `<div class="muted-pad"><span class="spin"></span>Loading…</div>`;
  try {
    const q = `?corpNo=${encodeURIComponent(CORP_NO)}`;
    const [pRes, mRes] = await Promise.all([
      fetch(`${AGENT_API}/agent-prompts${q}`),
      fetch(`${AGENT_API}/agent-modules${q}`),
    ]);
    const pJson = await pRes.json();
    const mJson = await mRes.json();
    if (!pRes.ok) throw apiError(pJson, pRes);
    if (!mRes.ok) throw apiError(mJson, mRes);
    apData = pJson.data || pJson.payload || [];
    apModules = mJson.data || mJson.payload || [];
    renderStarterCard();
    renderAgentCards();
  } catch (e) {
    $("apAgentDetail").innerHTML = `<div class="muted-pad">${esc(friendlyError(e.message))}</div>`;
  }
}

/* ---- Starter conversation: greeting + GPT-style starter rows (✕ per row) ----
 * Two cards share this code: the plan chat's starter (saved through /agent-prompts)
 * and the settlement chat's (its own endpoint, /bizplay/agents/settlement/starter). ---- */
const AP_STARTER_PH = "e.g. Please create a business trip plan to Busan for next Tuesday.";
const AP_SETTLE_STARTER_PH = "e.g. 지난달 부산 출장 정산해줘";
function apStarterRowHtml(value, placeholder) {
  return `<div class="ap-starter-row"><input type="text" maxlength="160" value="${esc(value)}"
      placeholder="${esc(placeholder || AP_STARTER_PH)}" />
      <button type="button" class="ap-row-x" title="Remove">✕</button></div>`;
}
function renderStarterCard() {
  const msg = apData.find((p) => p.name === "starter-message") || {};
  const sug = apData.find((p) => p.name === "starter-suggestions") || {};
  $("apStarterMsg").value = msg.effectivePrompt || "";
  const lines = (sug.effectivePrompt || "").split(/\r?\n/).map((s) => s.trim()).filter(Boolean);
  $("apStarterRows").innerHTML = lines.map(apStarterRowHtml).join("") + apStarterRowHtml("");
  $("apStarterMsgNote").textContent =
    (msg.source === "CUSTOM" || sug.source === "CUSTOM") ? "Customized for this corp." : "";
}
function apStarterValues(rowsId) {
  return [...$(rowsId || "apStarterRows").querySelectorAll("input")]
    .map((i) => i.value.trim()).filter(Boolean).slice(0, 6);
}
async function apStarterSave() {
  const q = `?corpNo=${encodeURIComponent(CORP_NO)}`;
  const greeting = $("apStarterMsg").value.trim();
  const lines = apStarterValues();
  try {
    const calls = [];
    calls.push(greeting
      ? fetch(`${AGENT_API}/agent-prompts/starter-message${q}`, { method: "PUT",
          headers: { "Content-Type": "application/json" }, body: JSON.stringify({ prompt: greeting }) })
      : fetch(`${AGENT_API}/agent-prompts/starter-message${q}`, { method: "DELETE" }));
    calls.push(lines.length
      ? fetch(`${AGENT_API}/agent-prompts/starter-suggestions${q}`, { method: "PUT",
          headers: { "Content-Type": "application/json" }, body: JSON.stringify({ prompt: lines.join("\n") }) })
      : fetch(`${AGENT_API}/agent-prompts/starter-suggestions${q}`, { method: "DELETE" }));
    const results = await Promise.all(calls);
    for (const r of results) if (!r.ok) throw apiError(await r.json().catch(() => ({})), r);
    toast("Starter conversation saved ✓", "ok");
    starterMessage = null; starterSuggestions = null;   // chat hero refetches
    apLoad();
  } catch (e) {
    $("apStarterMsgNote").textContent = friendlyError(e.message);
  }
}
async function apStarterReset() {
  const q = `?corpNo=${encodeURIComponent(CORP_NO)}`;
  await Promise.all([
    fetch(`${AGENT_API}/agent-prompts/starter-message${q}`, { method: "DELETE" }),
    fetch(`${AGENT_API}/agent-prompts/starter-suggestions${q}`, { method: "DELETE" }),
  ]);
  toast("Starter conversation reset to defaults.", "ok");
  starterMessage = null; starterSuggestions = null;
  apLoad();
}

/* ---- The settlement chat's own starter. One endpoint carries both pieces:
 * GET/PUT/DELETE /bizplay/agents/settlement/starter?corpNo= with {greeting, suggestions[]}.
 * A blank greeting or an empty suggestion list resets THAT piece to the default. ---- */
async function apSettleStarterLoad() {
  const note = $("apSetStarterMsgNote");
  try {
    const res = await fetch(`${BZ_API_BASE()}/agents/settlement/starter?corpNo=${encodeURIComponent(CORP_NO)}`);
    const json = await res.json();
    if (!res.ok) throw apiError(json, res);
    const d = json.data || json.payload || {};
    $("apSetStarterMsg").value = d.greeting || "";
    $("apSetStarterRows").innerHTML =
      (d.suggestions || []).map((s) => apStarterRowHtml(s, AP_SETTLE_STARTER_PH)).join("")
      + apStarterRowHtml("", AP_SETTLE_STARTER_PH);
    note.textContent = (d.greetingSource === "CUSTOM" || d.suggestionsSource === "CUSTOM")
      ? "Customized for this corp." : "";
  } catch (e) {
    $("apSetStarterRows").innerHTML = apStarterRowHtml("", AP_SETTLE_STARTER_PH);
    note.textContent = friendlyError(e.message);
  }
}
async function apSettleStarterSave() {
  try {
    const res = await fetch(`${BZ_API_BASE()}/agents/settlement/starter?corpNo=${encodeURIComponent(CORP_NO)}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        greeting: $("apSetStarterMsg").value.trim(),
        suggestions: apStarterValues("apSetStarterRows"),
      }),
    });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    toast("Settlement starter saved ✓", "ok");
    settleStarterMessage = null; settleStarterSuggestions = null;   // settle chat refetches
    apSettleStarterLoad();
  } catch (e) {
    $("apSetStarterMsgNote").textContent = friendlyError(e.message);
  }
}
async function apSettleStarterReset() {
  await fetch(`${BZ_API_BASE()}/agents/settlement/starter?corpNo=${encodeURIComponent(CORP_NO)}`,
    { method: "DELETE" });
  toast("Settlement starter reset to defaults.", "ok");
  settleStarterMessage = null; settleStarterSuggestions = null;
  apSettleStarterLoad();
}

/* ---- Sub-agents master-detail: list on the right, selected agent's card on the left.
 * ONE prompt editor per agent — prefilled with the effective prompt (custom if saved,
 * else the default); Save overrides, Reset restores the default. ---- */
let apSelectedAgent = "guardrail";

function renderAgentList() {
  const item = (c) => {
    const p = apData.find((x) => x.name === c.name);
    const m = apModules.find((x) => x.name === c.name);
    const on = c.fixed || !m || m.enabled;
    return `<button type="button" class="ap-md-item ${c.name === apSelectedAgent ? "active" : ""}" data-ag-pick="${esc(c.name)}">
        <span class="ap-md-dot ${on ? "on" : ""}"></span>
        <span class="ap-md-name">${esc(c.label)}</span>
        ${p && p.source === "CUSTOM" ? `<span class="badge-src badge-db">CUSTOM</span>` : ""}
        ${on ? "" : `<span class="badge-src badge-off">OFF</span>`}
      </button>`;
  };
  const llm = AG_CARDS.filter((c) => c.llm);
  const nonLlm = AG_CARDS.filter((c) => !c.llm);
  $("apAgentList").innerHTML =
    `<div class="ap-md-subtitle">LLM agents</div>` + llm.map(item).join("") +
    `<div class="ap-md-subtitle">Non-LLM agents (deterministic)</div>` + nonLlm.map(item).join("");
}

function renderAgentDetail() {
  const c = AG_CARDS.find((x) => x.name === apSelectedAgent) || AG_CARDS[0];
  const p = apData.find((x) => x.name === c.name);
  const m = apModules.find((x) => x.name === c.name);
  const on = c.fixed || !m || m.enabled;
  const effective = p ? (p.customPrompt || p.defaultPrompt || "") : "";
  const head = `<div class="ag-head">
      <span class="ag-title">${esc(c.label)}
        <span class="badge-src ${c.llm ? "badge-config" : "badge-off"}">${c.llm ? "LLM" : "NON-LLM"}</span>
        ${p && p.source === "CUSTOM" ? `<span class="badge-src badge-db">CUSTOM</span>` : ""}</span>
      ${c.fixed
        ? `<span class="ag-fixed" title="Core module — always on">Always on</span>`
        : `<label class="ag-switch" title="Turn this sub-agent on/off for this corp">
             <input type="checkbox" data-ag-toggle="${esc(c.name)}" ${on ? "checked" : ""} />
             <span class="ag-slider"></span>
           </label>`}
    </div>`;
  const body = c.promptable
    ? `<label class="ag-lbl">Prompt <span class="hint-inline">(${p && p.source === "CUSTOM" ? "customized — Reset restores the default" : "built-in default — edit and Save to override"})</span></label>
       <textarea data-ag-prompt="${esc(c.name)}" rows="12">${esc(effective)}</textarea>
       <div class="ag-actions">
         <button class="btn btn-danger btn-sm" data-ag-reset="${esc(c.name)}"><svg class="ico" aria-hidden="true"><use href="#i-refresh"/></svg> Reset to default</button>
         <span class="spacer"></span>
         <button class="btn btn-primary btn-sm" data-ag-save="${esc(c.name)}">Save prompt</button>
       </div>`
    : `<p class="ag-noprompt">Prompt not customizable — ${c.name === "form-builder"
          ? "deterministic (mirrors the retrieved form)."
          : c.name === "database-lookup" ? "its prompt is dynamic (embeds the live DB schema)." : "it has no own prompt."}</p>`;
  const apiLine = c.api ? `<p class="ag-api"><span class="ag-lbl">External API</span> ${esc(c.api)}</p>` : "";
  $("apAgentDetail").innerHTML = `${head}<p class="ag-desc">${esc(c.desc)}</p>${apiLine}${body}`;
  $("apAgentDetail").classList.toggle("ag-off", !on);
}

function renderAgentCards() {   // kept as the single refresh entry point
  renderAgentList();
  renderAgentDetail();
}

async function apAgentSave(name) {
  const ta = document.querySelector(`[data-ag-prompt="${name}"]`);
  const prompt = (ta.value || "").trim();
  const p = apData.find((x) => x.name === name);
  if (!prompt) { toast("Write the prompt first — or use Reset to restore the default.", "err"); return; }
  if (p && prompt === (p.defaultPrompt || "").trim() && p.source !== "CUSTOM") {
    toast("Same as the default — nothing to save.", "");
    return;
  }
  try {
    const res = await fetch(`${AGENT_API}/agent-prompts/${encodeURIComponent(name)}?corpNo=${encodeURIComponent(CORP_NO)}`, {
      method: "PUT", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ prompt, enabled: true }),
    });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    toast("Prompt saved ✓ — live on the next chat turn.", "ok");
    apLoad();
  } catch (e) {
    toast(friendlyError(e.message), "err");
  }
}
async function apAgentReset(name) {
  try {
    const res = await fetch(`${AGENT_API}/agent-prompts/${encodeURIComponent(name)}?corpNo=${encodeURIComponent(CORP_NO)}`, { method: "DELETE" });
    if (!res.ok) throw new Error("Reset failed.");
    toast("Reset to the built-in default.", "ok");
    apLoad();
  } catch (e) {
    toast(friendlyError(e.message), "err");
  }
}
async function apModuleToggle(name, enabled, checkbox) {
  try {
    const res = await fetch(`${AGENT_API}/agent-modules/${encodeURIComponent(name)}?corpNo=${encodeURIComponent(CORP_NO)}`, {
      method: "PUT", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ enabled }),
    });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    toast(`${name} ${enabled ? "enabled" : "disabled"} for this corp.`, "ok");
    const m = apModules.find((x) => x.name === name);
    if (m) m.enabled = enabled;
    renderAgentCards();
  } catch (e) {
    toast(friendlyError(e.message), "err");
    checkbox.checked = !enabled;   // revert
  }
}

/* ---- Custom agents (Phase 1): builder card + list on the Settings page ---- */
let caData = [];
let caToolCatalog = {};
let caSelected = null;   // null = building a new agent

async function caLoad() {
  try {
    const q = `?corpNo=${encodeURIComponent(CORP_NO)}`;
    const [aRes, tRes] = await Promise.all([
      fetch(`${AGENT_API}/custom-agents${q}`),
      fetch(`${AGENT_API}/custom-agents/tools?corpNo=${encodeURIComponent(CORP_NO)}`),
    ]);
    const aJson = await aRes.json();
    const tJson = await tRes.json();
    if (!aRes.ok) throw apiError(aJson, aRes);
    caData = aJson.data || aJson.payload || [];
    caToolCatalog = (tJson.data || tJson.payload) || {};
    renderCaList();
    caFill(caSelected ? caData.find((a) => a.name === caSelected) : null);
  } catch (e) {
    $("caMsg").textContent = friendlyError(e.message);
  }
}

function renderCaList() {
  $("caList").innerHTML = caData.length ? caData.map((a) => `
    <button type="button" class="ap-md-item ${a.name === caSelected ? "active" : ""}" data-ca-pick="${esc(a.name)}">
      <span class="ap-md-dot ${a.enabled ? "on" : ""}"></span>
      <span class="ap-md-name">${esc(a.name)}</span>
      ${a.enabled ? "" : `<span class="badge-src badge-off">OFF</span>`}
    </button>`).join("")
    : `<p class="card-note">No custom agents yet — build the first one on the left.</p>`;
}

function caFill(a) {
  caSelected = a ? a.name : null;
  $("caTitle").textContent = a ? a.name : "New custom agent";
  $("caName").value = a ? a.name : "";
  $("caName").disabled = !!a;                        // name is the key
  $("caDesc").value = a ? (a.description || "") : "";
  $("caPrompt").value = a ? (a.prompt || "") : "";
  $("caEnabled").checked = a ? a.enabled !== false : true;
  renderCaTools(new Set(a ? (a.tools || []) : []));
  $("caDeleteBtn").classList.toggle("hidden", !a);
  $("caMsg").textContent = "";
  $("caTestOut").classList.add("hidden");
  renderCaList();
}

/* Tool picker: built-in tools stay flat; MCP tools fold into one group per server so a
 * long registry doesn't swamp the form. Groups with a selected tool open by default. */
function renderCaTools(picked) {
  const builtIn = [];
  const byServer = new Map();
  Object.entries(caToolCatalog).forEach(([key, desc]) => {
    if (key.startsWith("mcp:")) {
      const rest = key.slice(4);                     // "server:tool" (server names have no colons)
      const server = rest.slice(0, rest.indexOf(":"));
      const tool = rest.slice(rest.indexOf(":") + 1);
      if (!byServer.has(server)) byServer.set(server, []);
      byServer.get(server).push({
        key, tool,
        untrusted: /NOT TRUSTED/.test(desc),
        desc: desc.replace(/^\[MCP[^\]]*\]\s*/, ""),
      });
    } else {
      builtIn.push({ key, tool: key, desc });
    }
  });
  const row = (t) => `
    <label class="ca-tool"><input type="checkbox" value="${esc(t.key)}" ${picked.has(t.key) ? "checked" : ""} />
      <span><strong>${esc(t.tool)}</strong> — <span class="ca-tool-desc">${esc(t.desc)}</span></span></label>`;
  let html = builtIn.map(row).join("");
  byServer.forEach((tools, server) => {
    const sel = tools.filter((t) => picked.has(t.key)).length;
    const untrusted = tools.some((t) => t.untrusted);
    html += `
    <details class="ca-mcp-group" ${sel ? "open" : ""} data-mcp-server="${esc(server)}">
      <summary><strong>MCP · ${esc(server)}</strong>
        ${untrusted ? `<span class="badge-src badge-off">not trusted</span>` : ""}
        <span class="ca-mcp-count">${tools.length} tool${tools.length === 1 ? "" : "s"}${sel ? ` · ${sel} selected` : ""}</span>
      </summary>
      ${tools.map(row).join("")}
    </details>`;
  });
  $("caTools").innerHTML = html;
  // Keep each group's "· N selected" live as boxes are (un)ticked.
  $("caTools").onchange = () => {
    $("caTools").querySelectorAll(".ca-mcp-group").forEach((g) => {
      const total = g.querySelectorAll("input").length;
      const sel = g.querySelectorAll("input:checked").length;
      g.querySelector(".ca-mcp-count").textContent =
        `${total} tool${total === 1 ? "" : "s"}${sel ? ` · ${sel} selected` : ""}`;
    });
  };
}

async function caSave() {
  const name = $("caName").value.trim();
  if (!name) { $("caMsg").textContent = "Name is required."; return; }
  const body = {
    description: $("caDesc").value.trim(),
    prompt: $("caPrompt").value.trim(),
    tools: [...$("caTools").querySelectorAll("input:checked")].map((i) => i.value),
    enabled: $("caEnabled").checked,
  };
  try {
    const res = await fetch(`${AGENT_API}/custom-agents/${encodeURIComponent(name)}?corpNo=${encodeURIComponent(CORP_NO)}`, {
      method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
    });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    toast(`Custom agent “${name}” saved ✓ — the chat routes matching messages to it.`, "ok");
    caSelected = name;
    caLoad();
  } catch (e) {
    $("caMsg").textContent = friendlyError(e.message);
  }
}

async function caDelete() {
  if (!caSelected) return;
  try {
    const res = await fetch(`${AGENT_API}/custom-agents/${encodeURIComponent(caSelected)}?corpNo=${encodeURIComponent(CORP_NO)}`, { method: "DELETE" });
    if (!res.ok) throw new Error("Delete failed.");
    toast("Custom agent deleted.", "ok");
    caSelected = null;
    caLoad();
  } catch (e) {
    $("caMsg").textContent = friendlyError(e.message);
  }
}

async function caTest() {
  const msg = $("caTestInput").value.trim();
  if (!caSelected) { $("caMsg").textContent = "Save the agent first, then test it."; return; }
  if (!msg) return;
  const out = $("caTestOut");
  out.classList.remove("hidden");
  out.innerHTML = `<span class="spin"></span> Running…`;
  try {
    const res = await fetch(`${AGENT_API}/custom-agents/${encodeURIComponent(caSelected)}/test?corpNo=${encodeURIComponent(CORP_NO)}`, {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ message: msg }),
    });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    const d = json.data || json.payload || {};
    out.innerHTML = `<div class="ca-test-reply">${esc(d.reply || "(no reply)")}</div>
      ${d.toolsUsed && d.toolsUsed.length ? `<div class="ca-test-tools">tools used: ${d.toolsUsed.map(esc).join(", ")}</div>` : `<div class="ca-test-tools">no tools used</div>`}`;
  } catch (e) {
    out.innerHTML = `<div class="ca-test-reply">${esc(friendlyError(e.message))}</div>`;
  }
}

function initCa() {
  $("caNewBtn").addEventListener("click", () => caFill(null));
  $("caSaveBtn").addEventListener("click", caSave);
  $("caDeleteBtn").addEventListener("click", caDelete);
  $("caTestBtn").addEventListener("click", caTest);
  $("caTestInput").addEventListener("keydown", (ev) => { if (ev.key === "Enter") { ev.preventDefault(); caTest(); } });
  $("caList").addEventListener("click", (ev) => {
    const pick = ev.target.closest("[data-ca-pick]");
    if (pick) caFill(caData.find((a) => a.name === pick.getAttribute("data-ca-pick")));
  });
}


/* ---- MCP servers: per-corp registry; tools feed the custom-agent builder ---- */
let mcpData = [];

function mcpUrlFor(name) {
  return `${AGENT_API}/mcp-servers${name ? "/" + encodeURIComponent(name) : ""}?corpNo=${encodeURIComponent(CORP_NO)}`;
}

async function mcpLoad() {
  try {
    const res = await fetch(mcpUrlFor(null));
    const json = await res.json();
    if (!res.ok) throw apiError(json, res);
    mcpData = json.data || json.payload || [];
    renderMcpList();
  } catch (e) {
    $("mcpMsg").textContent = friendlyError(e.message);
  }
}

function renderMcpList() {
  $("mcpList").innerHTML = mcpData.length ? mcpData.map((s) => `
    <div class="mcp-row" data-mcp="${esc(s.name)}">
      <span class="ap-md-dot ${s.enabled ? "on" : ""}"></span>
      <span class="mcp-name">${esc(s.name)}</span>
      <span class="mcp-url" title="${esc(s.url)}">${esc(s.url)}</span>
      <label class="mcp-trust ${s.trusted ? "on" : ""}" title="Tools execute only when trusted">
        <input type="checkbox" data-mcp-trust="${esc(s.name)}" ${s.trusted ? "checked" : ""} /> trusted
      </label>
      <button class="btn btn-quiet btn-sm" data-mcp-test="${esc(s.name)}">Connection test</button>
      <button class="ap-row-x" data-mcp-del="${esc(s.name)}" title="Remove">✕</button>
      <div class="mcp-status" data-mcp-status="${esc(s.name)}"></div>
    </div>`).join("")
    : `<p class="card-note">No MCP servers yet — add one below.</p>`;
}

async function mcpAdd() {
  const name = $("mcpName").value.trim();
  const url = $("mcpUrl").value.trim();
  if (!name || !url) { $("mcpMsg").textContent = "Name and URL are required."; return; }
  try {
    const res = await fetch(mcpUrlFor(name), {
      method: "PUT", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ url, authHeader: $("mcpAuth").value.trim() || null, trusted: false, enabled: true }),
    });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw apiError(json, res);
    toast(`MCP server “${name}” registered — run the connection test, then mark it trusted.`, "ok");
    $("mcpName").value = ""; $("mcpUrl").value = ""; $("mcpAuth").value = "";
    $("mcpMsg").textContent = "";
    mcpLoad();
    caLoad();   // tool list may change
  } catch (e) {
    $("mcpMsg").textContent = friendlyError(e.message);
  }
}

async function mcpTest(name) {
  const status = document.querySelector(`[data-mcp-status="${name}"]`);
  status.innerHTML = `<span class="spin"></span> Testing…`;
  try {
    const res = await fetch(`${AGENT_API}/mcp-servers/${encodeURIComponent(name)}/test?corpNo=${encodeURIComponent(CORP_NO)}`, { method: "POST" });
    const json = await res.json();
    const d = json.data || json.payload || {};
    if (d.ok) {
      status.innerHTML = `<span class="mcp-ok">✓ ${esc(d.serverInfo || "connected")}</span> — tools: `
        + (d.tools || []).map((t) => `<code>${esc(t.name)}</code>`).join(", ");
      caLoad();
    } else {
      status.innerHTML = `<span class="mcp-bad">✕ Connection failure (${esc(d.error || "unreachable")})</span>`;
    }
  } catch (e) {
    status.innerHTML = `<span class="mcp-bad">✕ ${esc(friendlyError(e.message))}</span>`;
  }
}

async function mcpTrust(name, trusted, checkbox) {
  const s = mcpData.find((x) => x.name === name);
  if (!s) return;
  try {
    const res = await fetch(mcpUrlFor(name), {
      method: "PUT", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ url: s.url, trusted, enabled: s.enabled }),
    });
    if (!res.ok) throw new Error("Update failed.");
    toast(trusted ? `“${name}” marked trusted — its tools can now execute.` : `“${name}” trust revoked.`, "ok");
    mcpLoad();
    caLoad();
  } catch (e) {
    checkbox.checked = !trusted;
    toast(friendlyError(e.message), "err");
  }
}

async function mcpDelete(name) {
  await fetch(mcpUrlFor(name), { method: "DELETE" });
  toast(`MCP server “${name}” removed.`, "ok");
  mcpLoad();
  caLoad();
}

function initMcp() {
  $("mcpAddBtn").addEventListener("click", mcpAdd);
  $("mcpList").addEventListener("click", (ev) => {
    const t = ev.target.closest("[data-mcp-test]");
    if (t) { mcpTest(t.getAttribute("data-mcp-test")); return; }
    const d = ev.target.closest("[data-mcp-del]");
    if (d) mcpDelete(d.getAttribute("data-mcp-del"));
  });
  $("mcpList").addEventListener("change", (ev) => {
    const c = ev.target.closest("[data-mcp-trust]");
    if (c) mcpTrust(c.getAttribute("data-mcp-trust"), c.checked, c);
  });
}

function initAp() {
  $("openApBtn").addEventListener("click", openAp);
  $("apBackBtn").addEventListener("click", closeAp);
  $("apStarterSaveBtn").addEventListener("click", apStarterSave);
  $("apStarterResetBtn").addEventListener("click", apStarterReset);
  $("apSetStarterSaveBtn").addEventListener("click", apSettleStarterSave);
  $("apSetStarterResetBtn").addEventListener("click", apSettleStarterReset);
  // Starter rows: ✕ removes; typing in the last row grows a fresh empty one.
  // Both starter cards (plan + settlement) behave the same way.
  const wireStarterRows = (id, placeholder) => {
    const box = $(id);
    box.addEventListener("click", (ev) => {
      const x = ev.target.closest(".ap-row-x");
      if (!x) return;
      const rows = box.querySelectorAll(".ap-starter-row");
      if (rows.length > 1) x.closest(".ap-starter-row").remove();
      else x.closest(".ap-starter-row").querySelector("input").value = "";
    });
    box.addEventListener("input", () => {
      const rows = [...box.querySelectorAll(".ap-starter-row input")];
      if (rows.length && rows[rows.length - 1].value.trim() && rows.length < 6) {
        box.insertAdjacentHTML("beforeend", apStarterRowHtml("", placeholder));
      }
    });
  };
  wireStarterRows("apStarterRows", AP_STARTER_PH);
  wireStarterRows("apSetStarterRows", AP_SETTLE_STARTER_PH);
  $("apAgentList").addEventListener("click", (ev) => {
    const pick = ev.target.closest("[data-ag-pick]");
    if (pick) { apSelectedAgent = pick.getAttribute("data-ag-pick"); renderAgentCards(); }
  });
  $("apAgentDetail").addEventListener("click", (ev) => {
    const save = ev.target.closest("[data-ag-save]");
    if (save) { apAgentSave(save.getAttribute("data-ag-save")); return; }
    const reset = ev.target.closest("[data-ag-reset]");
    if (reset) apAgentReset(reset.getAttribute("data-ag-reset"));
  });
  $("apAgentDetail").addEventListener("change", (ev) => {
    const t = ev.target.closest("[data-ag-toggle]");
    if (t) apModuleToggle(t.getAttribute("data-ag-toggle"), t.checked, t);
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

/* Staff and departments come ONLY from the lookup APIs (/api/v1/staff,
 * /api/v1/departments) — there is no local seed roster. */
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
let BZ_CORP_USER_ID = localStorage.getItem("bizplay.corpUserId")
  || (window.APP_CONFIG && window.APP_CONFIG.corpUserId) || "30447";
/* The signed-in demo user — "I"/"me" in chat defaults to this person. */
/* Who "I"/"me" refers to in chat. MUST be the same person as BZ_CORP_USER_ID — that id is the
 * drafter of every document — otherwise "plan a trip for me" drafts as one user and travels as
 * another. Seeded from the roster once it loads (bzResolveCurrentUser); the literal here is only
 * a pre-roster placeholder, and an explicit localStorage value always wins. */
let CURRENT_USER_NAME = localStorage.getItem("bizplay.userName") || "";
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
    // Real encoding: value "true"/"false"; PARTNER_SUPPORT carries a sub-choice in value2,
    // the partner company in a selections row, and the visit purpose in the "text" slot.
    const sub = t === "PARTNER_SUPPORT"
      ? `<div class="select-wrap"><select ${a}2="${key}"><option value="">협력사 구분…</option><option value="PARTNER_REGISTERED">등록업체</option></select></div>`
        + `<input type="text" ${a}p="${key}" placeholder="협력사명을 입력하세요"/>`
        + `<input type="text" ${a}t="${key}" placeholder="방문목적을 입력하세요"/>`
      : "";
    return radios(name, `${a}="${key}"`, ["true", "false"], 1).replace(/>true</, ">Yes<").replace(/>false</, ">No<") + sub;
  }
  if (t === "OVERSEAS_INSURANCE") {
    // value: ONE_WAY|ROUND_TRIP · value2: OVER_90_DAYS|WITHIN_90_DAYS (captured codes).
    return `<div class="bzw-sub">항공권</div>`
      + radios(name, `${a}="${key}"`, ["ONE_WAY", "ROUND_TRIP"], 0).replace(/>ONE_WAY</, ">편도<").replace(/>ROUND_TRIP</, ">왕복<")
      + `<div class="bzw-sub">기간</div>`
      + radios(name + "d", `${a}2="${key}"`, ["WITHIN_90_DAYS", "OVER_90_DAYS"], 0)
          .replace(/>WITHIN_90_DAYS</, ">90일 이내<").replace(/>OVER_90_DAYS</, ">90일 초과<");
  }
  if (t === "BSTR_LINKED_LEAVE") {
    return `<div class="bzw-group" ${a}-group="${key}" data-leave="1">
      <div class="bzw-row"><input type="date" data-part="시작"/><span class="bzw-tilde">~</span><input type="date" data-part="종료"/></div>
      <div class="bzw-row"><input type="text" data-part="체류지역" placeholder="체류지역을 입력하세요"/><input type="text" data-part="체류목적" data-primary placeholder="체류목적을 입력하세요"/></div>
    </div>`;
  }
  if (t === "EDUCATION_INFO") {
    return `<div class="bzw-group" ${a}-group="${key}" data-edu="1">
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

/* 출장 연계 휴가 composite -> structured {start,end,region,purpose} (writer contract:
 * leave start = value, end = value2, region/purpose = selections row). */
function bzReadLeaveObject(g) {
  const map = { "시작": "start", "종료": "end", "체류지역": "region", "체류목적": "purpose" };
  const out = {};
  let hasReal = false;
  g.querySelectorAll("[data-part]").forEach((el) => {
    const v = (el.value || "").trim();
    if (v) { out[map[el.getAttribute("data-part")] || el.getAttribute("data-part")] = v; hasReal = true; }
  });
  return hasReal ? out : null;
}

/* 교육정보 composite -> structured object (keys match the writer/mapper contract);
 * null when no typed part has content (the 구분 radio default alone is not a value). */
function bzReadEduObject(g) {
  const out = {};
  let hasReal = false;
  g.querySelectorAll("[data-part]").forEach((el) => {
    const part = el.getAttribute("data-part");
    if (el.type === "radio") { if (el.checked) out[part] = el.value; return; }
    const v = (el.value || "").trim();
    if (v) { out[part] = v; hasReal = true; }
  });
  return hasReal ? out : null;
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
    else if (el.isContentEditable) {
      // HTML items keep their MARKUP (bold, lists…) — innerText would strip it.
      out[id] = rteText(el) ? el.innerHTML.trim() : "";
    }
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
  body.innerHTML = loadingRow(11);
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
    body.innerHTML = emptyRow(11, { icon: "alert", title: "Couldn’t load plans", sub: esc(friendlyError(e.message)) });
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
      ? emptyRow(11, { icon: "search", title: "No plans match this filter", sub: "Try a different search or status chip." })
      : emptyRow(11, { icon: "inbox", title: "No business trip plans yet", sub: "Create your first plan in the chat.",
          // RETIRED: hybrid entry — was onclick="openAgent()" ("Create with Agent").
          action: `<button class="btn btn-primary btn-sm" onclick="openChatMode()">${svgIcon("chat")} Chat</button>` });
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
      <td class="c-json"><button class="btn-json" data-json="${esc(p.id)}" title="Download this plan's JSON">${svgIcon("download")}</button></td>
    </tr>`;
  }).join("");
  updateSelectionBar();
}

/* Download one plan's document JSON. Prefers the BizPlay-shaped draft held by the linked agent
 * session (the ③ save body); falls back to the plan record when a row has no session. */
async function downloadPlanJson(planId) {
  const plan = plansCache.find((p) => String(p.id) === String(planId));
  if (!plan) { toast("That plan is no longer in the list.", "err"); return; }
  let payload = null, label = "plan";
  try {
    if (plan.agentSessionId) {
      const res = await fetch(`${AGENT_API}/sessions/${encodeURIComponent(plan.agentSessionId)}`);
      const json = await res.json().catch(() => ({}));
      if (res.ok) {
        const d = (json && (json.data || json.payload)) || {};
        if (d.draftJson) { payload = d.draftJson; label = "draft"; }
      }
    }
  } catch (e) {
    console.warn("[bizplay] draft fetch failed, falling back to the plan record:", e.message);
  }
  if (!payload) payload = plan;   // no session (or no draft yet) — the row's own record
  saveJsonFile(payload, `${label}_${jsonFileStem(plan.title, planId)}.json`);
}

/* A session's draft_json straight from the database (GET /sessions/{id}), or null. */
async function fetchSessionDraft(sessionId) {
  if (!sessionId) return null;
  try {
    const res = await fetch(`${AGENT_API}/sessions/${encodeURIComponent(sessionId)}`);
    const json = await res.json().catch(() => ({}));
    if (!res.ok) return null;
    const d = (json && (json.data || json.payload)) || {};
    return d.draftJson || null;
  } catch (e) {
    console.warn("[bizplay] draft fetch failed:", e.message);
    return null;
  }
}

/* Download one settlement's draft_json — the agent session's stored document (the 정산서
 * save body). Rows with no session, or one that never got a draft, fall back to the
 * stored report record so the button is never a dead end; the filename says which. */
async function downloadReportJson(key) {
  const rep = reportsCache.find((r) => String(r.key) === String(key));
  if (!rep) { toast("That report is no longer in the list.", "err"); return; }
  const draft = await fetchSessionDraft(rep.sessionId);
  const label = draft ? "draft" : "report";
  const payload = draft || rep;
  if (!draft) toast("No draft_json for this row — downloading the stored report instead.", "");
  saveJsonFile(payload, `${label}_${jsonFileStem(rep.title, key)}.json`);
}

function jsonFileStem(title, fallback) {
  return String(title || fallback).replace(/[^\w가-힣.-]+/g, "_").slice(0, 60) || String(fallback);
}

function saveJsonFile(payload, name) {
  const url = URL.createObjectURL(new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" }));
  const a = document.createElement("a");
  a.href = url; a.download = name;
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  toast(`Downloaded ${name}`, "ok");
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
  prevCards = { info: null, details: null, extra: null, trav: null };
  wizAsked = null;
  wizExtrasAsked = new Set();
  wizTravDone = false;
  chatLang = LANG === "ko" ? "ko" : "en";
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
  chatOnly = false;
  $("createBody").classList.remove("chat-only");
  $("chatToggleBtn").classList.remove("hidden");
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

  // RETIRED: manual create-plan modal entry (button commented out in index.html).
  // $("openCreateBtn").addEventListener("click", openCreate);
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
  // Chat mode: the live BizPlay form reveals the template-extra section asynchronously
  // (after its fetch) — move it into the thread whenever it becomes visible.
  new MutationObserver(() => {
    if (chatOnly && !$("tripExtraSection").classList.contains("hidden")) ensureWizardSections();
  }).observe($("tripExtraSection"), { attributes: true, attributeFilter: ["class"] });
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
    // The JSON button must download, not open the row's detail panel.
    const dl = ev.target.closest("[data-json]");
    if (dl) { ev.stopPropagation(); downloadPlanJson(dl.getAttribute("data-json")); return; }
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
  // RETIRED: "Resume draft" resumed old PoC trip-plan sessions (button commented out).
  // $("openResumeBtn").addEventListener("click", openResume);
  $("resumeCloseBtn").addEventListener("click", closeResume);
  $("resumeList").addEventListener("click", (ev) => {
    const it = ev.target.closest("[data-sid]");
    if (it) loadSession(it.getAttribute("data-sid"));
  });

  // --- Agent chat (lives inside the Create Trip Plan modal) ---
  // RETIRED: "Create with Agent" hybrid (form + chat) entry (button commented out).
  // $("openAgentBtn").addEventListener("click", openAgent);
  $("openChatBtn").addEventListener("click", openChatMode);
  $("openSettleChatBtn").addEventListener("click", openSettlementChat);
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
    // The JSON button must not also open the row detail behind it.
    const dl = ev.target.closest("[data-report-json]");
    if (dl) { ev.stopPropagation(); downloadReportJson(dl.getAttribute("data-report-json")); return; }
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
  settle: false,  // true = session runs on the settlement agent (/bizplay/agents/settlement)
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
  agent.settle = false;
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

/* ================================================================
 *  CHAT MODE — pure-chat plan creation. The (hidden) real form stays
 *  the single data model; an editable form CARD is rendered inside the
 *  thread after each agent turn, writing through to the form, so
 *  validation, the route loop, and Create all work unchanged.
 * ================================================================ */
let chatOnly = false;

/* In chat mode the REAL form sections are moved into the thread as answers land,
 * so the chat shows exactly the same section UI as the Create Plan form —
 * including whatever the live BizPlay form loader renders into them. The nodes
 * (with their listeners intact) move back to the modal on the next open. */
let prevCards = { info: null, details: null, extra: null, trav: null };   // in-thread preview cards
let wizAsked = null;   // last locally-asked question: { step, el } — prevents repeats
let wizTravDone = false;   // travellers confirmed (Done) — gates the section preview
/* Mirror the user's language: Korean input → Korean questions, English → English. */
let chatLang = (localStorage.getItem("bizplay.lang") || "en") === "ko" ? "ko" : "en";
function T(en, ko) { return chatLang === "ko" ? ko : en; }

/* Read-only preview card in the thread: renders once when its section completes,
 * then updates in place as values change. No inputs — the chat is the editor. */
function previewCard(key, title, icon, rowsHtml) {
  let card = prevCards[key];
  if (!card || !card.isConnected) {
    const thread = $("agentThread");
    const wrap = document.createElement("div");
    wrap.className = "msg msg-assistant chat-section";
    wrap.innerHTML = `<div class="prev-card">
      <div class="pc-head">${svgIcon(icon)} <span>${esc(title)}</span>${svgIcon("check-circle", "pc-check")}</div>
      <div class="pc-body"></div>
    </div>`;
    thread.appendChild(wrap);
    card = wrap.querySelector(".prev-card");
    prevCards[key] = card;
    if (MOTION) gsap.fromTo(wrap, { opacity: 0, y: 10 },
      { opacity: 1, y: 0, duration: 0.35, ease: "power2.out", clearProps: "transform,opacity" });
    thread.scrollTop = thread.scrollHeight;
    card._prevHtml = null;
  }
  // A CHANGED section rides down with the newest message (an update far above the
  // current conversation point would otherwise go unnoticed).
  if (card._prevHtml !== rowsHtml) {
    card.querySelector(".pc-body").innerHTML = rowsHtml;
    if (card._prevHtml != null) {
      const thread = $("agentThread");
      thread.appendChild(card.closest(".msg"));
      thread.scrollTop = thread.scrollHeight;
    }
    card._prevHtml = rowsHtml;
  }
}

function pcRow(label, value) {
  if (value == null || value === "") return "";
  return `<div class="pc-row"><span class="pc-k">${esc(label)}</span><span class="pc-v">${esc(value)}</span></div>`;
}

function pcPerson(t) {
  const init = (t.name || "?").trim().slice(0, 1).toUpperCase();
  const sub = [t.department, t.position].filter(Boolean).join(" · ");
  return `<div class="pc-person"><span class="pc-avatar">${esc(init)}</span>
    <span class="pc-pname">${esc(t.name)}</span>${sub ? `<span class="pc-pdept">${esc(sub)}</span>` : ""}</div>`;
}

/* Ask a question only if the same one isn't already pending in the thread. */
function askOnce(step, appendFn) {
  const a = wizAsked;
  if (a && a.step === step && a.el && a.el.isConnected &&
      !a.el.classList.contains("choice-done") && !a.el.classList.contains("guide-done")) return;
  wizAsked = { step, el: appendFn() };
}

/* Compact state summary sent ahead of free-text so the agent doesn't re-ask
 * for fields the user already filled via the local wizard chips. */
function formContextPrefix() {
  const t = readTripFields();
  const parts = [];
  if (t.purpose) parts.push("purpose=" + t.purpose);
  if (t.classification) parts.push("classification=" + t.classification);
  if (t.start && t.end) parts.push("period=" + t.start + " to " + t.end);
  if (t.destination) parts.push("destination=" + t.destination);
  if (t.title) parts.push("title=" + t.title);
  const names = travelers.filter((x) => x.name).map((x) => x.name).join(", ");
  if (names) parts.push("travelers=" + names);
  // Tell the agent what the form still needs, so its replies ask for the right
  // things in natural language instead of re-asking what is already set.
  const missing = [];
  if (!t.purpose) missing.push("trip form/purpose");
  if (t.purpose && !t.classification) missing.push("classification");
  if (!t.start || !t.end) missing.push("travel dates");
  if (!t.destination) missing.push("destination");
  if (!t.title) missing.push("title");
  if (!t.content) missing.push("content");
  if (!travelers.some((x) => x.name)) missing.push("traveler names");
  extraDefsFromDom().forEach((d) => { if (!d.value) missing.push(d.label); });
  const lang = chatLang === "ko"
    ? "Respond in Korean only, no English."
    : "Respond in English only, no Korean.";
  return (CURRENT_USER_NAME ? "(Current user: " + CURRENT_USER_NAME + " — \"I\"/\"me\" refers to them. " : "(")
    + "Form state: " + (parts.join("; ") || "empty")
    + (missing.length ? ". Still needed: " + missing.join(", ") : "")
    + ". " + lang + ") ";
}

/* Preview a section only once it is COMPLETE — partially-filled sections stay
 * out of the thread while the follow-up questions collect the rest. */
/* "show all" / "preview all" / "전체 보여줘": re-present every section preview
 * (and the approval line) at the current point of the conversation. */
function showAllPreviews(quiet) {
  const thread = $("agentThread");
  if (!quiet) appendMsg("assistant", T("Here's the whole plan so far:", "지금까지의 계획 전체예요:"));
  ensureWizardSections();   // refresh values first
  ["info", "details", "extra", "trav"].forEach((k) => {
    const card = prevCards[k];
    if (card && card.isConnected) thread.appendChild(card.closest(".msg"));
  });
  const appr = thread.querySelector(".chat-appr-card");
  if (appr) thread.appendChild(appr.closest(".msg"));
  thread.scrollTop = thread.scrollHeight;
}

function ensureWizardSections() {
  const t = readTripFields();
  const cfg = TRIP_TYPES[t.purpose] || {};
  if (t.purpose && t.classification) {
    previewCard("info", T("Trip Information", "출장 정보"), "briefcase",
      pcRow(T("Travel Purpose", "출장 목적"), t.purpose) +
      pcRow(T("Classification", "출장 구분"), t.classification));
  }
  const detailsDone = t.start && t.end && t.destination && t.title && t.content;
  if (detailsDone) {
    const days = dayCount(t.start, t.end);
    previewCard("details", T("Trip Details", "출장 상세"), "calendar",
      pcRow(T("Period", "출장 기간"), `${t.start} → ${t.end} (${days}${T(days === 1 ? " day" : " days", "일")})`) +
      pcRow(cfg.destLabel ? T(cfg.destLabel, cfg.destKo || cfg.destLabel) : T("Destination", "출장지"), t.destination) +
      pcRow(T("Title", "제목"), t.title) +
      pcRow(T("Content", "내용"), t.content));
    const extras = extraDefsFromDom().filter((d) => d.value);
    if (!$("tripExtraSection").classList.contains("hidden") && !nextUnaskedExtra() && extras.length) {
      previewCard("extra", T("Additional Information", "추가 정보"), "cpu",
        extras.map((d) => pcRow(d.label, d.value)).join(""));
    }
  }
  // Travellers preview once confirmed (Done) — or when the agent resolved them.
  if (travelers.some((x) => x.name) && (wizTravDone || !(wizAsked && wizAsked.step === "travelers"))) {
    previewCard("trav", T("Travellers", "출장자"), "users",
      travelers.filter((x) => x.name).map(pcPerson).join(""));
  }
}

/* Final call-to-action once every section is fulfilled. */
function appendCreateAction(quiet) {
  // Chat mode: the form is complete — flow straight into the approval line
  // (asked in the thread); saving happens on Done ✓ there. "quiet" skips the
  // completion bubble when the backend reply already announced this step.
  if (chatOnly) {
    if (document.querySelector("#agentThread .wiz-create")) return;   // once per chat
    const thread = $("agentThread");
    const wrap = document.createElement("div");
    wrap.className = "msg msg-assistant wiz-create" + (quiet ? " hidden" : "");
    wrap.innerHTML = quiet ? "" : `<div class="bubble">${esc(agent.live
      ? T("That completes the form — everything is filled in above. Now let's set the approval line.",
          "양식이 모두 완성됐어요 — 위 내용을 확인해 주세요. 이제 결재선을 정할게요.")
      : T("That completes the form — everything is filled in above. Whenever you're ready, just tell me to save it.",
          "양식이 모두 완성됐어요 — 위 내용을 확인하시고, 저장을 원하시면 \"저장해 줘\"라고 말씀해 주세요."))}</div>`;
    thread.appendChild(wrap);
    thread.scrollTop = thread.scrollHeight;
    if (agent.live) bzChatApprovalFlow();
    return;
  }
  if (document.querySelector("#agentThread .wiz-create:not(.guide-done)")) return;   // one live CTA at a time
  document.querySelectorAll("#agentThread .wiz-create").forEach((n) => n.classList.add("guide-done"));
  const thread = $("agentThread");
  const wrap = document.createElement("div");
  wrap.className = "msg msg-assistant";
  wrap.innerHTML = `<div class="bubble">${esc(T("That completes the form — everything is filled in above. Give it a quick look and create the plan when you are happy.",
    "양식이 모두 완성됐어요 — 위 내용을 확인하시고, 마음에 드시면 계획을 생성해 주세요."))}</div>
    <div class="guide-widget wiz-create"><button type="button" class="btn btn-primary">${esc(T("Create this plan", "이 계획 생성"))}</button></div>`;
  wrap.querySelector(".btn").addEventListener("click", completeCreate);
  thread.appendChild(wrap);
  thread.scrollTop = thread.scrollHeight;
}

/* "Chat" button — same engine as Create Plan, but everything happens in the thread:
 * greeting → pick a form type → follow-up questions for each missing field → review card. */
function openChatMode() {
  openCreate();                                   // full reset (also clears chat-only)
  chatOnly = true;
  wizAsked = null;                                        // fresh question tracking
  $("createBody").classList.add("chat-only");
  $("chatToggleBtn").classList.add("hidden");
  setChatPane(true);
  $("createTitle").textContent = "Create Plan — Chat";
  renderChatHero();                               // POC-style opener: hero + example prompts
  loadStarterMessage();                           // server-customized opener, swapped in when fetched
}

/* "Settle in Chat" — the settlement agent (fixed chip-driven flow, all server-side).
 * The UI only renders reply + chips; each chip click sends its sendText back as the
 * next message. It opens on the corp's settlement starter (greeting + clickable
 * starters, both configurable in Settings); if that can't be fetched we fall back to
 * the old behavior — auto-send an opener so the agent speaks first. */
function openSettlementChat() {
  openCreate();                                   // full reset (also clears agent state)
  chatOnly = true;
  wizAsked = null;
  $("createBody").classList.add("chat-only");
  $("chatToggleBtn").classList.add("hidden");
  setChatPane(true);
  $("createTitle").textContent = T("Settle Expenses — Chat", "채팅으로 출장 정산");
  $("createSub").textContent = T(
    "Pick the finished trip, the evidence period and the card receipts — the agent assembles the settlement.",
    "정산할 출장과 증빙 기간, 카드 영수증을 고르면 에이전트가 정산서를 만들어 드려요.");
  $("agentThread").innerHTML = "";                // no trip hero — settlement has its own
  agent.settle = true;
  loadSettlementStarter();
}

/* The settlement opener, per corp: GET /bizplay/agents/settlement/starter. Rendered as
 * the chat hero; the agent then speaks on the first real message. If the endpoint is
 * unreachable we keep the previous behavior and auto-send the opener turn instead. */
let settleStarterMessage = null;         // greeting (null until fetched for THIS corp)
let settleStarterSuggestions = null;     // clickable starters
async function loadSettlementStarter() {
  try {
    const res = await fetch(`${BZ_API_BASE()}/agents/settlement/starter?corpNo=${encodeURIComponent(CORP_NO)}`);
    const json = await res.json();
    if (!res.ok) throw new Error("starter unavailable");
    const d = json.data || json.payload || {};
    settleStarterMessage = d.greeting || null;
    settleStarterSuggestions = (d.suggestions || []).slice(0, 4);
    if (!agent.settle || agent.sessionId) return;     // user already started talking
    renderSettlementHero();
  } catch {
    if (!agent.settle || agent.sessionId) return;
    $("agentInput").value = LANG === "ko"
      ? "출장 정산을 시작할게요"
      : "I want to settle my business-trip expenses";
    sendAgent({ keepLang: true });
  }
}

function renderSettlementHero() {
  const thread = $("agentThread");
  const starters = settleStarterSuggestions && settleStarterSuggestions.length
    ? settleStarterSuggestions : [];
  thread.innerHTML = `<div class="chat-hero" id="chatHero">
    <span class="ch-ico">${svgIcon("import", "ico-lg")}</span>
    <h3 class="ch-title">${esc(T("Which trip should we settle?", "어떤 출장을 정산할까요?"))}</h3>
    <p class="ch-sub">${esc(settleStarterMessage
      || T("Tell me which trip to settle — I'll pull the plan and its card receipts and draft the 정산서 for you.",
           "정산할 출장을 알려주시면 계획과 카드 영수증을 불러와 정산서를 만들어 드릴게요."))}</p>
    <div class="ch-cards">
      ${starters.map((s) => `<button type="button" class="ch-card">${svgIcon("chat")} <span>${esc(s)}</span></button>`).join("")}
    </div>
  </div>`;
  thread.querySelectorAll(".ch-card").forEach((b) => b.addEventListener("click", () => {
    $("agentInput").value = b.textContent.trim();
    sendAgent();
  }));
  if (MOTION) {
    gsap.fromTo("#chatHero > *", { opacity: 0, y: 14 },
      { opacity: 1, y: 0, duration: 0.45, ease: "power3.out", stagger: 0.07, clearProps: "transform,opacity" });
  }
}

/* Period questions in the settlement flow reuse the plan wizard's click-range
 * calendar — typing dates ("last month", "2026-07-01 ~ 2026-07-31") still works;
 * the calendar is an alternative, and past days are pickable here. */
function settlementDateAsk() {
  guideDates(
    T("Or pick the period on the calendar:", "달력에서 기간을 선택하셔도 돼요:"),
    (start, end) => {
      $("agentInput").value = `${start} ~ ${end}`;
      sendAgent({ keepLang: true });   // machine-formatted dates, not the user's language
    },
    // echo:false — sendAgent() prints the "start ~ end" bubble; the calendar must not
    // print a second "start → end" one right above it.
    { allowPast: true, echo: false });
}

/* Final settlement summary — read-only card. Provider save is NOT implemented yet,
 * so the flow deliberately ends here with no save button. */
function settlementSummaryCard(draft) {
  const doc = (Array.isArray(draft) && draft[0]) || {};
  const receipts = doc.bstrReceipts || [];
  const won = (v) => "₩" + Math.round(num(v)).toLocaleString();
  const rows = receipts.map((r) => pcRow(
    `${r.mestName || "?"} · ${r.approvalDate || ""}`,
    `${won(r.approvalAmount)}${r.bstrReceiptType ? " · " + r.bstrReceiptType : ""}`)).join("");
  const body = rows
    + pcRow(T("Receipts", "영수증"), String(receipts.length))
    + pcRow(T("Total", "총액"), won(doc.totalBstrAmount))
    + pcRow(T("Reimbursable", "개인 정산"), won(doc.totalSettleAmount));
  const thread = $("agentThread");
  const wrap = document.createElement("div");
  wrap.className = "msg msg-assistant chat-section";
  wrap.innerHTML = `<div class="prev-card">
      <div class="pc-head"><span class="b-ico">${svgIcon("import")}</span> ${esc(T("Settlement summary", "정산 요약"))}</div>
      <div class="pc-body">${body}</div></div>`;
  thread.appendChild(wrap);
  thread.scrollTop = thread.scrollHeight;
}

/* Manual expense entry (⑧) — the trip has no matching card receipt, so the user types
 * the expense and attaches its (required) receipt image. A file can't ride a chat turn:
 * this posts multipart to /agents/settlement/{sessionId}/manual-expense, which registers
 * a 기타카드 receipt + the image and maps them into the draft's etcReceiptSaveRequests.
 * Every amount is typed, never derived here — the agent stores the keys exactly as given. */
function settlementManualExpenseForm() {
  const today = new Date().toISOString().slice(0, 10);
  const f = (label, input) => `<label class="mx-f"><span>${esc(label)}</span>${input}</label>`;
  const money = (k) => `<input type="number" min="0" step="1" data-k="${k}" placeholder="0">`;
  // Type-specific detail fields the agent asked for (missingFields), rendered as extra inputs and
  // submitted as the `detail` part (PATCH /receipt-etc). Which fields appear depends on the TranKind.
  const detailFields = (agent.lastData && agent.lastData.missingFields) || [];
  const detailSpec = {
    usedStartDate: [T("Used from", "사용 시작일"), `<input type="date" data-d="usedStartDate">`],
    usedEndDate: [T("Used to", "사용 종료일"), `<input type="date" data-d="usedEndDate">`],
    vehicleType: [T("Vehicle", "교통수단"), `<input type="text" data-d="vehicleType" placeholder="KTX / AIR / BUS">`],
    routeType: [T("Route", "편도/왕복"), `<input type="text" data-d="routeType" placeholder="ONEWAY / ROUNDTRIP">`],
    seatClass: [T("Seat class", "좌석 등급"), `<input type="text" data-d="seatClass">`],
    departTerminalId: [T("Depart terminal id", "출발 터미널 ID"), `<input type="number" data-d="departTerminalId">`],
    arrivalTerminalId: [T("Arrival terminal id", "도착 터미널 ID"), `<input type="number" data-d="arrivalTerminalId">`],
    depart: [T("From", "출발지"), `<input type="text" data-d="depart">`],
    arrival: [T("To", "도착지"), `<input type="text" data-d="arrival">`],
    starRating: [T("Star rating", "성급"), `<input type="number" data-d="starRating">`],
    roomType: [T("Room type", "객실 유형"), `<input type="text" data-d="roomType">`],
    partnerHotel: [T("Partner hotel", "제휴 호텔"), `<input type="text" data-d="partnerHotel">`],
    foodDivisionType: [T("Meal category", "식대 구분"), `<input type="text" data-d="foodDivisionType">`],
    personCount: [T("Headcount", "인원수"), `<input type="number" data-d="personCount">`],
  };
  const detailHtml = detailFields
    .filter((k) => detailSpec[k])
    .map((k) => f(detailSpec[k][0], detailSpec[k][1]))
    .join("");
  const detailSection = detailHtml
    ? `<div class="mx-sub">${esc(T("Additional details", "추가 정보"))}</div>${detailHtml}`
    : "";
  const html = `<div class="mx-grid">
      <label class="mx-f mx-wide"><span>${esc(T("Merchant", "가맹점"))}</span>
        <input type="text" data-k="mestName" placeholder="${esc(T("e.g. Seoul Station Cafe", "예: 서울역 카페"))}"></label>
      ${f(T("Date", "일자"), `<input type="date" data-k="approvalDate" value="${today}">`)}
      ${f(T("Time", "시각"), `<input type="time" step="1" data-k="approvalTime" value="12:00:00">`)}
      ${f(T("Currency", "통화"), `<input type="text" data-k="currencyCode" value="KRW">`)}
      <label class="mx-f mx-check"><input type="checkbox" data-k="overseasUsed">
        <span>${esc(T("Overseas use", "해외 사용"))}</span></label>
      ${f(T("Amount", "승인금액"), money("approvalAmount"))}
      ${f(T("Supply", "공급가액"), money("supplyAmount"))}
      ${f(T("VAT", "부가세"), money("vatAmount"))}
      ${f(T("Original supply", "원공급가액"), money("originalSupplyAmount"))}
      ${f(T("Original VAT", "원부가세"), money("originalVatAmount"))}
      ${detailSection}
      <label class="mx-f mx-wide"><span>${esc(T("Receipt image (optional)", "영수증 이미지 (선택)"))}</span>
        <input type="file" accept="image/*" data-k="image"></label>
    </div>
    <div class="mx-actions"><span class="mx-note"></span>
      <button type="button" class="btn btn-primary mx-add">${esc(T("Add expense", "경비 추가"))}</button></div>`;

  // No prompt bubble of our own: the agent's reply right above already asked for this,
  // and a second canned sentence would just repeat it.
  const thread = $("agentThread");
  const wrap = document.createElement("div");
  wrap.className = "msg msg-assistant";
  wrap.innerHTML = `<div class="guide-widget">${html}</div>`;
  const w = wrap.querySelector(".guide-widget");
  thread.appendChild(wrap);
  thread.scrollTop = thread.scrollHeight;
  const done = (shown) => { w.classList.add("guide-done"); appendMsg("user", shown, {}); };

  const el = (k) => w.querySelector(`[data-k="${k}"]`);
  const note = w.querySelector(".mx-note");
  const warn = (msg) => { note.textContent = msg; note.classList.add("mx-warn"); };
  w.querySelector(".mx-add").addEventListener("click", async () => {
    const file = el("image").files[0];
    const mestName = el("mestName").value.trim();
    const approvalDate = el("approvalDate").value;
    const approvalAmount = num(el("approvalAmount").value);
    if (!mestName) return warn(T("Merchant is required.", "가맹점을 입력해 주세요."));
    if (!approvalDate) return warn(T("Date is required.", "일자를 입력해 주세요."));
    if (approvalAmount <= 0) return warn(T("Enter the approved amount.", "승인금액을 입력해 주세요."));
    const time = el("approvalTime").value || "00:00:00";
    const expense = {
      approvalDate,
      approvalTime: time.length === 5 ? time + ":00" : time,   // <input type=time> drops seconds
      currencyCode: el("currencyCode").value.trim() || "KRW",
      mestName,
      overseasUsed: el("overseasUsed").checked,
      approvalAmount,
      supplyAmount: num(el("supplyAmount").value),
      originalSupplyAmount: num(el("originalSupplyAmount").value),
      vatAmount: num(el("vatAmount").value),
      originalVatAmount: num(el("originalVatAmount").value),
      // mestCorpNo + tranKindId are left out on purpose — the agent fills them.
    };
    // The TranKind detail (data-d inputs) → the `detail` part (PATCH /receipt-etc). Only sent when
    // the form actually had detail fields; empty inputs go through as null.
    let detail = null;
    const dInputs = w.querySelectorAll("[data-d]");
    if (dInputs.length) {
      detail = { etcReceiptType: "RECEIPT" };
      dInputs.forEach((inp) => {
        const k = inp.getAttribute("data-d");
        const v = inp.value;
        detail[k] = v === "" ? null : (inp.type === "number" ? num(v) : v);
      });
    }
    note.textContent = "";
    note.classList.remove("mx-warn");
    done(`${mestName} · ${approvalDate} · ₩${approvalAmount.toLocaleString()}`
      + (file ? ` · ${file.name}` : ""));
    // A rejected submit reopens the form with everything still typed in.
    if (!await submitManualExpense(expense, detail, file)) w.classList.remove("guide-done");
  });
  return w;
}

async function submitManualExpense(expense, detail, file) {
  if (!agent.sessionId) {
    appendMsg("assistant", "⚠ " + T("Import a plan before adding a manual expense.",
                                    "먼저 출장 계획을 불러온 뒤 경비를 입력해 주세요."), { error: true });
    return false;
  }
  const typing = appendTyping();
  setAgentBusy(true);
  try {
    const fd = new FormData();
    fd.append("expense", JSON.stringify(expense));   // plain text part -> @RequestPart String
    if (detail) fd.append("detail", JSON.stringify(detail));   // optional additional info
    if (file) fd.append("image", file, file.name);             // image is optional
    const url = `${BZ_API_BASE()}/agents/settlement/${encodeURIComponent(agent.sessionId)}`
      + `/manual-expense?corpNo=${encodeURIComponent(CORP_NO)}`;
    const res = await fetch(url, { method: "POST", body: fd });
    const json = await res.json().catch(() => ({}));
    typing.remove();
    if (!res.ok) throw apiError(json, res);
    const data = (json && (json.data || json.payload)) || {};
    agent.status = data.status || agent.status;
    agent.draft = data.draftJson || agent.draft;
    agent.lastData = data;
    appendMsg("assistant", data.reply || T("Expense added.", "경비를 추가했어요."));
    manualExpenseFollowUp();
    return true;
  } catch (e) {
    typing.remove();
    appendMsg("assistant", "⚠ " + friendlyError(e.message), { error: true });
    return false;
  } finally {
    setAgentBusy(false);
  }
}

/* The expense landed but the evidence stage is still open: add another, or close it. */
function manualExpenseFollowUp() {
  const thread = $("agentThread");
  const wrap = document.createElement("div");
  wrap.className = "msg msg-assistant";
  const row = document.createElement("div");
  row.className = "choice-row";
  const chip = (label, onPick) => {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "choice-chip";
    btn.textContent = label;
    btn.addEventListener("click", () => {
      if (agent.busy || row.classList.contains("choice-done")) return;
      row.classList.add("choice-done");
      btn.classList.add("choice-picked");
      onPick();
    });
    row.appendChild(btn);
  };
  chip(T("Add another expense", "경비 더 입력"), settlementManualExpenseForm);
  chip(T("Done", "첨부 완료"), () => {
    $("agentInput").value = "receipts-done";
    sendAgent({ keepLang: true });   // machine chip token, not the user's language
  });
  wrap.appendChild(row);
  thread.appendChild(wrap);
  thread.scrollTop = thread.scrollHeight;
}

/* Natural-language example prompts, POC-flow style, tuned to plan creation. */
const CHAT_SUGGESTIONS = [
  "Please create a business trip plan to Busan for next Tuesday.",
  "I am going to the KSHRD Center in Phnom Penh from 2026-08-20 to 2026-08-21 to train IT instructors.",
  "Prepare an overseas trip to the Osaka exhibition for me and my team lead.",
];

/* Server-customizable chat opener + example prompts (/agent-prompts).
 * ALWAYS refetched for the CURRENT corp on chat open — the values are per-corp,
 * so a cached copy goes stale the moment the corp number (or a save) changes. */
let starterMessage = null;
let starterSuggestions = null;   // array of suggestion strings, one hero card each
async function loadStarterMessage() {
  try {
    const res = await fetch(`${AGENT_API}/agent-prompts?corpNo=${encodeURIComponent(CORP_NO)}`);
    const json = await res.json();
    const list = (json && (json.data || json.payload)) || [];
    const msg = list.find((p) => p.name === "starter-message");
    const sug = list.find((p) => p.name === "starter-suggestions");
    starterMessage = (msg && msg.effectivePrompt) || null;
    starterSuggestions = (sug && sug.effectivePrompt)
      ? sug.effectivePrompt.split(/\r?\n/).map((s) => s.trim()).filter(Boolean).slice(0, 4)
      : null;
    // Hero already open: re-render it in place (opener + cards).
    const thread = $("agentThread");
    if (thread && thread.querySelector(".chat-hero") && thread.children.length === 1) renderChatHero();
  } catch { /* keep the built-in defaults */ }
}

function renderChatHero() {
  const thread = $("agentThread");
  thread.innerHTML = `<div class="chat-hero" id="chatHero">
    <span class="ch-ico">${svgIcon("plane", "ico-lg")}</span>
    <h3 class="ch-title">Where is your business trip taking you?</h3>
    <p class="ch-sub">${esc(starterMessage || "Describe it in your own words — I will draft the whole plan for you, from the trip form and dates to travellers and attachments.")}</p>
    <div class="ch-cards">
      ${(starterSuggestions || CHAT_SUGGESTIONS).map((s) => `<button type="button" class="ch-card">${svgIcon("chat")} <span>${esc(s)}</span></button>`).join("")}
    </div>
    <button type="button" class="ch-alt" id="chatHeroManual">Or pick the form type yourself</button>
  </div>`;
  thread.querySelectorAll(".ch-card").forEach((b) => b.addEventListener("click", () => {
    $("agentInput").value = b.textContent.trim();
    sendAgent();
  }));
  $("chatHeroManual").addEventListener("click", () => { dismissChatHero(); nextWizardStep(); });
  if (MOTION) {
    gsap.fromTo("#chatHero > *", { opacity: 0, y: 14 },
      { opacity: 1, y: 0, duration: 0.45, ease: "power3.out", stagger: 0.07, clearProps: "transform,opacity" });
  }
}

function dismissChatHero() {
  const hero = $("chatHero");
  if (!hero) return;
  // Removal must not depend on the tween finishing — rAF (and therefore GSAP)
  // freezes in background tabs, which would leave the hero stuck above the chat.
  if (MOTION) {
    gsap.to(hero, { opacity: 0, y: -8, duration: 0.25, ease: "power2.in" });
    setTimeout(() => hero.remove(), 300);
  } else hero.remove();
}

/* ---- Guided wizard: ask for the first missing field, one question at a time ---- */

/* Assistant bubble + locally-handled option chips (no LLM round-trip). */
function guideChips(prompt, options, onPick) {
  const thread = $("agentThread");
  const wrap = document.createElement("div");
  wrap.className = "msg msg-assistant";
  const bubble = document.createElement("div");
  bubble.className = "bubble"; bubble.textContent = prompt;
  wrap.appendChild(bubble);
  const row = document.createElement("div");
  row.className = "choice-row";
  options.forEach((opt) => {
    const b = document.createElement("button");
    b.type = "button";
    b.className = "choice-chip" + (opt.quiet ? " choice-skip" : "");
    b.textContent = opt.label;
    b.addEventListener("click", () => {
      if (row.classList.contains("choice-done")) return;
      row.classList.add("choice-done"); b.classList.add("choice-picked");
      appendMsg("user", opt.label, {});
      onPick(opt);
    });
    row.appendChild(b);
  });
  wrap.appendChild(row);
  thread.appendChild(wrap);
  thread.scrollTop = thread.scrollHeight;
  return row;
}

/* Assistant bubble + an inline answer widget (text or date-range). */
/* opts.echo === false: the caller sends the answer through sendAgent(), which draws its
 * own user bubble — echoing here too would print the same period twice. */
function guideWidget(prompt, innerHtml, wire, opts) {
  const thread = $("agentThread");
  const wrap = document.createElement("div");
  wrap.className = "msg msg-assistant";
  wrap.innerHTML = `<div class="bubble">${esc(prompt)}</div><div class="guide-widget">${innerHtml}</div>`;
  const w = wrap.querySelector(".guide-widget");
  thread.appendChild(wrap);
  thread.scrollTop = thread.scrollHeight;
  wire(w, (shownText) => {
    w.classList.add("guide-done");
    if (!opts || opts.echo !== false) appendMsg("user", shownText, {});
  });
  return w;
}

/* Plain question — no inline input. The user answers in the composer and the
 * agent's LLM maps the free-text answer (however ambiguous) onto the field. */
function guideAsk(prompt) {
  const thread = $("agentThread");
  const wrap = document.createElement("div");
  wrap.className = "msg msg-assistant";
  wrap.innerHTML = `<div class="bubble">${esc(prompt)}</div>`;
  thread.appendChild(wrap);
  thread.scrollTop = thread.scrollHeight;
  return wrap;
}

/* Inline range calendar: click the start day, then the end day (same day twice =
 * a one-day trip) — the range highlights and confirms itself, no typing, no OK. */
function guideDates(prompt, onSubmit, opts) {
  // allowPast: settlement searches PAST trips — the plan wizard's "no past days"
  // rule (and its locked prev-month arrow) must not apply there.
  const allowPast = !!(opts && opts.allowPast);
  const now = new Date();
  let view = new Date(now.getFullYear(), now.getMonth(), 1);
  let selStart = null, selEnd = null;   // ISO "YYYY-MM-DD"
  const WD = chatLang === "ko" ? ["일", "월", "화", "수", "목", "금", "토"]
                               : ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
  const iso = (y, m, d) => `${y}-${String(m + 1).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
  const todayIso = iso(now.getFullYear(), now.getMonth(), now.getDate());   // past days can't be picked

  return guideWidget(prompt, `<div class="cal"></div>`, (w, done) => {   // opts passed through below
    const cal = w.querySelector(".cal");
    const hint = () => !selStart
      ? T("Pick the start date.", "시작일을 선택해 주세요.")
      : T("Now pick the end date — the same day works for a one-day trip.",
          "이제 종료일을 선택해 주세요 — 당일 출장이면 같은 날을 한 번 더 누르세요.");
    const render = () => {
      const y = view.getFullYear(), m = view.getMonth();
      const title = chatLang === "ko" ? `${y}년 ${m + 1}월`
        : view.toLocaleDateString("en-US", { month: "long", year: "numeric" });
      const lead = new Date(y, m, 1).getDay();
      const days = new Date(y, m + 1, 0).getDate();
      let cells = "";
      for (let i = 0; i < lead; i++) cells += `<span class="cal-d cal-blank"></span>`;
      for (let d = 1; d <= days; d++) {
        const v = iso(y, m, d);
        const dow = (lead + d - 1) % 7;
        const past = !allowPast && v < todayIso;
        const cls = ["cal-d"];
        if (dow === 0) cls.push("cal-sun");
        if (dow === 6) cls.push("cal-sat");
        if (past) cls.push("cal-off");
        if (v === selStart || v === selEnd) cls.push("cal-sel");
        else if (selStart && selEnd && v > selStart && v < selEnd) cls.push("cal-range");
        cells += `<button type="button" class="${cls.join(" ")}"${past ? " disabled" : ` data-iso="${v}"`}>${d}</button>`;
      }
      const atCurrentMonth = y === now.getFullYear() && m === now.getMonth();
      cal.innerHTML = `
        <div class="cal-head">
          <button type="button" class="cal-nav" data-nav="-1" aria-label="Previous month"${!allowPast && atCurrentMonth ? " disabled" : ""}>‹</button>
          <span class="cal-title">${title}</span>
          <button type="button" class="cal-nav" data-nav="1" aria-label="Next month">›</button>
        </div>
        <div class="cal-grid cal-wd">${WD.map((n, i) =>
          `<span class="${i === 0 ? "cal-sun" : i === 6 ? "cal-sat" : ""}">${n}</span>`).join("")}</div>
        <div class="cal-grid">${cells}</div>
        <div class="cal-hint">${esc(hint())}</div>`;
      cal.querySelectorAll(".cal-nav").forEach((b) => b.addEventListener("click", () => {
        view = new Date(y, m + Number(b.dataset.nav), 1);
        render();
      }));
      cal.querySelectorAll(".cal-d[data-iso]").forEach((b) => b.addEventListener("click", () => {
        const v = b.dataset.iso;
        if (!selStart || selEnd || v < selStart) { selStart = v; selEnd = null; render(); return; }
        selEnd = v;
        render();
        done(`${selStart} → ${selEnd}`);
        onSubmit(selStart, selEnd);
      }));
    };
    render();
  }, opts);
}

/* Travellers: offer staff chips (from master data); "Done" moves on. */
function askTravelers() {
  const staff = liveStaffList();
  const picked = new Set(travelers.filter((t) => t.name).map((t) => t.name));
  const opts = staff.filter((s) => !picked.has(s.name)).slice(0, 8)
    .map((s) => ({ label: `${s.name} (${s.department})`, value: s.name }));
  if (picked.size) opts.push({ label: T("Done ✓", "완료 ✓"), quiet: true, done: true });
  return guideChips(picked.size
      ? T("Added! Is anyone else joining, or shall we move on?", "추가했어요! 더 가는 분이 있나요, 아니면 넘어갈까요?")
      : T("Now, who is traveling? Pick teammates below — or just tell me their names in a message.",
          "이제 누가 출장을 가나요? 아래에서 선택하시거나 이름을 말씀해 주세요."),
    opts, (opt) => {
      if (opt.done) { wizTravDone = true; ensureWizardSections(); nextWizardStep(); return; }
      const s = staff.find((x) => x.name === opt.value);
      const empty = travelers.find((t) => !t.name);   // fill the auto-added blank row first
      if (empty) { empty.name = s.name; empty.department = s.department; empty.position = s.position; }
      else travelers.push({ id: ++travelerSeq, name: s.name, department: s.department, position: s.position, origin: "", destination: "", returnPoint: "" });
      renderTravelers();
      ensureWizardSections();   // reveal/refresh the Travellers section card
      askTravelers();
    });
}

/* Template extras (코스트센터, HTML111, …) are optional but must be OFFERED before
 * the flow closes — the core-fields check alone would skip them. */
function wizardHasPendingExtra() {
  const t = readTripFields();
  return !!(t.purpose && t.classification && nextUnaskedExtra());
}

function wizardIncomplete() {
  const t = readTripFields();
  return !t.purpose || !t.classification || !t.start || !t.end || !t.destination
    || !t.title || !t.content || !travelers.some((x) => x.name);
}

/* ---- Template-aware extra fields (코스트센터, 과제코드, HTML111, radios …) ----
 * Read live from the DOM so both the static templates and the BizPlay live
 * loader are covered; each empty extra is asked once, in natural language. */
let wizExtrasAsked = new Set();

function extraDefsFromDom() {
  const seen = new Set(), defs = [];
  $("tripExtraFields").querySelectorAll("[data-xf]").forEach((el) => {
    const id = el.getAttribute("data-xf");
    if (seen.has(id)) return;
    seen.add(id);
    const field = el.closest(".field");
    const labEl = field && field.querySelector("label");
    const label = (labEl ? labEl.textContent : id).replace(/\s+/g, " ").trim();
    let type = "text", options = null, value = "";
    if (el.tagName === "SELECT") { type = "select"; options = [...el.options].map((o) => o.value).filter(Boolean); value = el.value; }
    else if (el.type === "radio") {
      type = "radio";
      const group = [...$("tripExtraFields").querySelectorAll(`[data-xf="${id}"]`)];
      options = group.map((r) => r.value);
      value = (group.find((r) => r.checked) || {}).value || "";
    } else if (el.isContentEditable) { type = "rich"; value = rteText(el); }
    else { value = (el.value || "").trim(); }
    defs.push({ id, label, type, options, value, el });
  });
  return defs;
}

function nextUnaskedExtra() {
  if ($("tripExtraSection").classList.contains("hidden")) return null;
  return extraDefsFromDom().find((d) => !d.value && !wizExtrasAsked.has(d.id)) || null;
}

function askExtraField(d) {
  const done = () => { wizExtrasAsked.add(d.id); ensureWizardSections(); nextWizardStep(); };
  const formKo = (TRIP_TYPES[$("tripPurpose").value] || {}).ko || "this";
  if (d.type === "select" || d.type === "radio") {
    return guideChips(
      T(`This form also has a “${d.label}” choice — which one applies to your trip?`,
        `이 양식에는 “${d.label}” 항목도 있어요 — 어떤 것이 해당하나요?`),
      d.options.map((o) => ({ label: o, value: o })).concat([{ label: T("Skip", "건너뛰기"), value: "", quiet: true }]),
      (o) => {
        if (o.value) {
          if (d.type === "select") { d.el.value = o.value; d.el.dispatchEvent(new Event("change", { bubbles: true })); }
          else {
            const r = [...$("tripExtraFields").querySelectorAll(`[data-xf="${d.id}"]`)].find((x) => x.value === o.value);
            if (r) { r.checked = true; r.dispatchEvent(new Event("change", { bubbles: true })); }
          }
        }
        done();
      });
  }
  // Free-text extras: plain question + Skip chip — the typed answer arrives via
  // the composer and the agent maps it onto the field.
  return guideChips(
    T(`The ${formKo} form also asks for “${d.label}” — what should I put there? Or Skip if it doesn't apply.`,
      `${formKo} 양식의 “${d.label}”에는 무엇을 적을까요? 해당 없으면 건너뛰기를 눌러 주세요.`),
    [{ label: T("Skip", "건너뛰기"), value: "", quiet: true }],
    () => done());
}

/* Swap a wizard question's bubble for an LLM-composed one (same Follow-up
 * sub-agent as the plan flow) — the hardcoded string is only the instant
 * placeholder / offline fallback, so questions never feel predefined. */
async function askNaturally(el, labels) {
  const bubble = el && el.closest && el.closest(".msg")?.querySelector(".bubble");
  if (!bubble) return;
  try {
    const res = await fetch(`${AGENT_API}/follow-up-question`, {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ corpNo: CORP_NO, missing: labels, korean: chatLang === "ko" }),
    });
    const json = await res.json();
    const q = ((json.data || json.payload) || {}).question;
    if (q && q.length > 5 && bubble.isConnected) bubble.textContent = q;
  } catch { /* placeholder stays */ }
}

/* Ask for the first missing field, in conversational language that carries the
 * context of what has already been chosen. */
function nextWizardStep() {
  if (!chatOnly) return;
  const t = readTripFields();
  const cfg = TRIP_TYPES[t.purpose] || {};
  const purposeLabel = cfg.ko || t.purpose || "";
  const pendingExtra = (t.purpose && t.classification) ? nextUnaskedExtra() : null;

  if (!t.purpose) {
    askOnce("purpose", () => guideChips(
      T("To get us started — which trip form should we use? Overseas and domestic are the usual ones, and the rest are your company’s special templates.",
        "먼저 어떤 출장 양식을 사용할까요? 해외출장과 국내출장이 일반적이고, 나머지는 회사 전용 템플릿이에요."),
      Object.keys(TRIP_TYPES).map((k) => ({ label: k, value: k })),
      (o) => {
        const sel = $("tripPurpose");
        sel.value = o.value;
        sel.dispatchEvent(new Event("change", { bubbles: true }));   // runs applyTripType incl. live BizPlay wiring
        ensureWizardSections(); nextWizardStep();
      }));
  } else if (!t.classification) {
    const clsSel = $("tripClassification");
    const opts = [...clsSel.options].filter((o) => o.value)
      .map((o) => ({ label: o.textContent, value: o.value }));
    askOnce("classification", () => guideChips(
      T(`A ${purposeLabel} trip — noted. Which classification fits this one best?`,
        `${purposeLabel} 출장이군요. 어떤 구분이 가장 잘 맞나요?`),
      opts.length ? opts : (cfg.classifications || []).map(([v, l]) => ({ label: l, value: v })),
      (o) => {
        clsSel.value = o.value;
        clsSel.dispatchEvent(new Event("change", { bubbles: true })); // triggers the live form load too
        ensureWizardSections(); nextWizardStep();
      }));
  } else if (!t.start || !t.end) {
    askOnce("period", () => {
      const el = guideDates(
        T("When will you be traveling? Pick the start and end dates below.",
          "언제 다녀오시나요? 아래에서 시작일과 종료일을 선택해 주세요."),
        (s, e) => { $("startDate").value = s; $("endDate").value = e; ensureWizardSections(); nextWizardStep(); });
      askNaturally(el, [T("trip period (start and end dates)", "출장 기간(시작일과 종료일)")]);
      return el;
    });
  } else if (!t.destination) {
    const q = /국가/.test(cfg.destKo || "") ? T("Which country are you headed to?", "어느 나라로 가시나요?")
      : /지역/.test(cfg.destKo || "") ? T("Which city or region will you be visiting?", "어느 도시나 지역으로 가시나요?")
      : T("Where will this trip take place?", "어디에서 진행되는 출장인가요?");
    askOnce("destination", () => {
      const opts = (cfg.options || []).slice(0, 6).map((o) => ({ label: o, value: o }));
      const el = opts.length
        ? guideChips(q, opts, (o) => { $("tripDestination").value = o.value; ensureWizardSections(); nextWizardStep(); })
        : guideAsk(q);
      askNaturally(el, [T("destination (city or region)", "출장지(도시/지역)")]);
      return el;
    });
  } else if (!t.title) {
    askOnce("title", () => {
      const el = guideAsk(T("What would you like to call this plan?", "이 계획의 이름을 뭐라고 할까요?"));
      askNaturally(el, [T("a short title for the plan", "계획 제목")]);
      return el;
    });
  } else if (!t.content) {
    askOnce("content", () => {
      const el = guideAsk(T("And what is the trip for? A sentence or two is plenty.",
        "이번 출장의 목적을 한두 문장으로 알려 주시겠어요?"));
      askNaturally(el, [T("a short description of the trip's purpose", "출장 내용(목적 설명)")]);
      return el;
    });
  } else if (pendingExtra) {
    askOnce("extra:" + pendingExtra.id, () => askExtraField(pendingExtra));
  } else if (!travelers.some((x) => x.name)) {
    askOnce("travelers", () => {
      const el = askTravelers();
      if (!travelers.some((x) => x.name)) askNaturally(el, [T("who is going on the trip (travellers)", "출장자(누가 가는지)")]);
      return el;
    });
  } else {
    appendCreateAction();
  }
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
async function sendAgent(opts) {
  if (agent.busy) return;
  const message = $("agentInput").value.trim();
  const fileIds = agent.pending.map((f) => f.fileId);
  // Nothing to send: no error — like any chat app, just put the cursor back.
  if (!message && !fileIds.length) { $("agentInput").focus(); return; }

  // Mirror the user's language — but only from text they actually typed. Chip
  // clicks send composed text that may contain Korean template/staff names
  // (e.g. "Trip type: 테스트(유성린)") and must not flip an English conversation.
  const keepLang = !!(opts && opts.keepLang === true);
  // Proportion, not presence: "Which department is 김도하 in" is an English
  // sentence quoting a Korean name — it must not flip the conversation to Korean.
  if (message && !keepLang) {
    const hangul = (message.match(/[가-힣]/g) || []).length;
    const latin = (message.match(/[A-Za-z]/g) || []).length;
    chatLang = hangul > 0 && hangul * 3 > latin ? "ko" : "en";
  }
  if (chatOnly) dismissChatHero();   // conversation starts: clear the empty-state hero

  // RETIRED: predefined "show all" phrase-matching - we don't guess what users might ask.
  // Draft questions go to the backend's dynamic draft-QA (intent DRAFT_QUERY), which answers
  // ANY phrasing over any subset of the draft; for view-style requests the UI re-presents
  // the preview cards below the answer (see the DRAFT_QUERY branch in the reply handler).
  // // "Show all" / "전체 보여줘": re-present every preview at the bottom — local, no LLM.
  // if (chatOnly && message && message.length <= 40
  // && (/(show|preview|see|view|summar)\w*\s.*(all|plan|everything|summary|form)|^(show|preview) all$/i.test(message)
  // || /(전체|계획|요약|다)\s*(를|을)?\s*(보여|볼래|미리보기)|미리보기/.test(message))
  // && Object.values(prevCards).some((c) => c && c.isConnected)) {
  // appendMsg("user", message);
  // $("agentInput").value = "";
  // showAllPreviews();
  // return;
  // }

  // "Save it" / "저장해 줘" in chat runs the real save flow instead of a chat turn —
  // saving is only ever user-initiated (there is no save button in chat mode).
  const saveIntent = chatOnly && !agent.settle && message && message.length <= 40 && /\bsave\b|\bsubmit\b|저장/i.test(message);
  if (saveIntent
      && !wizardIncomplete() && (!agent.live || agent.status === "READY_FOR_REVIEW")) {
    appendMsg("user", message);
    $("agentInput").value = "";
    if (agent.live) {
      if (chatOnly) bzChatApprovalFlow();   // in-thread approval line, no popup
      else bzOpenApprovalFlow();
    } else completeCreate();
    return;
  }
  // Mid-approval line edits ("remove 합의", "remove 김철수") are a UI-only concern — the
  // approval line lives in the browser until save, so sending them to the form agent
  // yields a nonsense reply and a stale card. Handle them here.
  const apprActive = chatOnly && agent.live && bzApproval.lines.length
    && document.querySelector("#agentThread .chat-appr-card");
  if (apprActive && message && message.length <= 40
      && /\b(remove|delete|drop)\b|빼|삭제|제거|취소/i.test(message)) {
    // Name match first; else match a role word ("합의") — with "결재선"/"approval line"
    // stripped so the phrase itself can't masquerade as the 결재 role.
    let idx = bzApproval.lines.findIndex((l) => message.toLowerCase().includes(l.name.toLowerCase()));
    if (idx < 0) {
      const rest = message.replace(/결재선|approval\s*line/gi, "");
      const kindWords = { APPROVAL: /결재|approval/i, AGREE: /합의|agree/i,
                          ACCEPT: /수신|receive|accept/i, REFERENCE: /참조|reference/i };
      const kind = Object.keys(kindWords).find((k) => kindWords[k].test(rest));
      if (kind) idx = bzApproval.lines.findIndex((l) => l.kind === kind);
    }
    if (idx >= 0) {
      const gone = bzApproval.lines.splice(idx, 1)[0];
      appendMsg("user", message);
      $("agentInput").value = "";
      const kindKo = (BZ_LINE_KINDS.find((k) => k[0] === gone.kind) || [])[1] || gone.kind;
      appendMsg("assistant", T(`Removed ${gone.name} (${kindKo}) from the approval line.`,
        `결재선에서 ${gone.name} 님(${kindKo})을 뺐어요.`));
      bzChatApprovalCard();     // re-render + ride down with the newest message
      bzChatAskApprover();
      return;
    }
  }
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
    // catalog is up. Files ride along too — the live agent runs the same
    // spreadsheet/PDF sub-agents as the PoC flow.
    // RETIRED: the PoC trip-plan fallback. Plan chats now ALWAYS use the live BizPlay
    // agent (an unreachable catalog surfaces as an error instead of silently degrading).
    // if (!agent.sessionId && agent.mode === "plan") {
    //   agent.live = !!bzCatalog;
    // }
    if (agent.mode === "plan") {
      agent.live = true;
    }
    // A NEW chat that asks for settlement (정산) rides the settlement agent — and the
    // whole session stays there (period question -> plan pick -> evidence attach).
    if (!agent.sessionId && /정산|settle/i.test(message || "")) agent.settle = true;
    let url, body;
    if (agent.settle) {
      url = `${BZ_API_BASE()}/agents/settlement`;
      body = { corpNo: CORP_NO, corpUserId: BZ_CORP_USER_ID, message: message || null };
      if (agent.sessionId) body.sessionId = agent.sessionId;
    } else if (agent.live) {
      url = `${BZ_API_BASE()}/agents/plan`;
      body = { corpNo: CORP_NO, corpUserId: BZ_CORP_USER_ID, message: message || null };
      if (fileIds.length) body.fileIds = fileIds;
      if (agent.sessionId) body.sessionId = agent.sessionId;
    } else {
      // Expense-report chats still ride the report agent; the trip-plan PoC endpoint
      // is no longer reachable from here (plan mode is forced live above).
      body = { corpNo: CORP_NO, message: message || null, fileIds };
      if (agent.sessionId) body.sessionId = agent.sessionId;
      // A new report session must reference the plan it reports on.
      if (agent.mode === "report" && !agent.sessionId && agent.planId) body.planId = agent.planId;
      const endpoint = agent.mode === "report" ? "/agents/expense-report" : "/agents/trip-plan";
      url = `${AGENT_API}${endpoint}`;
    }
    // Chat mode: prepend the local form state so the agent doesn't re-ask for
    // fields the user already filled via wizard chips (shown bubble stays clean).
    // Settlement chats skip it — the trip-form context is another flow's state.
    if (chatOnly && body.message && !agent.settle) body.message = formContextPrefix() + body.message;
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
    // Form first, reply second: the turn reads as USER -> updated previews ->
    // the agent's message -> any follow-up chips. (A form-apply failure must not
    // swallow the reply, hence the try.)
    try {
      if (agent.settle) {
        // Settlement drafts are not trip-form shaped — nothing to mirror into the wizard.
      } else if (agent.live) {
        await applyBizplayTurnToForm(data);        // ③-shaped draft -> the designed form
      } else {
        applyDraftToForm(agent.draft);             // PoC draft -> the designed form
      }
    } catch (formErr) {
      console.error("apply draft failed", formErr);
    }
    // A typed answer resolves whichever extra-field question was pending — the
    // agent has captured the value; don't keep waiting for the Skip chip.
    if (chatOnly && message && wizAsked && /^extra:/.test(wizAsked.step)) {
      wizExtrasAsked.add(wizAsked.step.slice("extra:".length));
      if (wizAsked.el && wizAsked.el.classList) wizAsked.el.classList.add("choice-done");
    }
    if (chatOnly) ensureWizardSections();          // completed sections preview first…
    appendMsg("assistant", data.reply || "(no reply)", {
      // Chat mode reads as a plain conversation — internal intent/sub-agent
      // badges (PURPOSE_SELECTION etc.) stay on the developer-facing form view.
      intent: chatOnly ? null : data.intent,
      subAgents: chatOnly ? null : data.subAgents,
      // chat mode detaches the chips from the reply so they can land BELOW the preview
      choiceGroups: chatOnly ? null : data.pendingChoices,
    });
    const hasChoices = !!(data.pendingChoices && data.pendingChoices.length);
    if (chatOnly) {
      // Dynamic draft-QA answered a view-style request: put the preview cards (and the
      // approval card, if the flow reached it) right below the text answer.
      if (data.intent === "DRAFT_QUERY"
          && /show|preview|display|all|보여|전체|요약|정리|summar/i.test(message || "")) {
        showAllPreviews(true);
      }
      // Settlement chats drive their own question flow — never the trip-form wizard.
      // Period questions get the same click-range calendar as the plan wizard (past
      // days allowed — settled trips already happened); the flow closes with a
      // read-only summary card (provider save is not implemented yet: no save button).
      if (agent.settle) {
        if (hasChoices) appendMsg("assistant", "", { choiceGroups: data.pendingChoices });
        const wantsDates = data.intent === "AWAIT_PERIOD" || data.intent === "EVIDENCE_PERIOD_PENDING"
          || (data.pendingChoices || []).some((g) => g.kind === "EVIDENCE_PERIOD");
        if (wantsDates) settlementDateAsk();
        // No card receipt for this trip: the expense is typed in and its image attached,
        // then posted multipart (a file can't ride a chat turn).
        else if (data.intent === "MANUAL_EXPENSE_PROMPT") settlementManualExpenseForm();
        else if (data.intent === "SETTLEMENT_READY") settlementSummaryCard(data.draftJson);
      }
      else if (hasChoices) appendMsg("assistant", "", { choiceGroups: data.pendingChoices });   // …follow-up below
      else if (wizardIncomplete() || wizardHasPendingExtra()) nextWizardStep();
      // Mid-approval correction: the section previews just updated — continue the
      // approval conversation at the bottom instead of going quiet. But when the agent
      // itself just asked something (a clarify question ends with "?"), let the user
      // answer first — stacking the approval ask on top buries the question.
      else if (agent.live && document.querySelector("#agentThread .chat-appr-card, #agentThread .appr-row")
               && !/\?\s*$/.test((data.reply || "").trim())) bzChatAskApprover();
      // Everything arrived in one turn: the backend reply already announced the
      // approval-line step — start it without a duplicate completion bubble.
      else appendCreateAction(agent.live && !!data.reply);
    }
    // The final CTA always comes last, and never while something is still being asked.
    // Chat mode never auto-offers a save button — the user asks to save in chat.
    if (agent.live) {
      if (data.status === "READY_FOR_REVIEW" && !chatOnly && !hasChoices) offerBizplayCreate();
      // The user just asked to save and this very turn made the draft ready
      // (locally-picked values sync on the way in) — honor the ask right now
      // instead of replying "save whenever you're ready".
      if (chatOnly && saveIntent && data.status === "READY_FOR_REVIEW" && !hasChoices) bzChatApprovalFlow();
    } else if (chatOnly && !hasChoices && !wizardIncomplete()) {
      appendCreateAction();
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
/* Settlement ④ plan picker: the candidate trip plans as a table (a chip can't carry six
 * columns legibly). One clickable row per plan; picking one sends the very same
 * `settle-plan:{approvalId}` token the chips sent, so the state machine is untouched. */
function planPickTable(group) {
  const picks = (group.options || []).filter((o) => o.meta);
  const COLS = [
    ["purpose", T("Purpose", "목적")],
    ["title", T("Title", "제목")],
    ["docNo", T("Doc no.", "문서번호")],
    ["period", T("Period", "기간")],
    ["drafter", T("Drafter", "기안자")],
    ["registrar", T("Registered by", "등록자")],
  ];
  const cell = (m, key) => (key === "period"
    ? [m.startDate, m.endDate].filter(Boolean).join(" ~ ")
    : m[key]) || "—";
  const box = document.createElement("div");
  box.className = "plan-pick";
  box.innerHTML = `
    ${group.name ? `<div class="pp-cap">${esc(group.name)}</div>` : ""}
    <div class="pp-scroll">
      <table class="pp-table">
        <thead><tr>${COLS.map(([k, label]) => `<th class="pp-${k}">${esc(label)}</th>`).join("")}</tr></thead>
        <tbody>${picks.map((o, i) => `<tr class="pp-row" data-i="${i}" tabindex="0">${
          COLS.map(([k]) => {
            const v = cell(o.meta, k);
            return `<td class="pp-${k}" title="${esc(v)}">${esc(v)}</td>`;
          }).join("")}</tr>`).join("")}</tbody>
      </table>
    </div>`;
  box.querySelectorAll(".pp-row").forEach((tr) => {
    const pick = () => {
      if (agent.busy || box.classList.contains("pp-done")) return;
      box.classList.add("pp-done");
      tr.classList.add("pp-picked");
      $("agentInput").value = picks[Number(tr.dataset.i)].sendText || "";
      sendAgent({ keepLang: true });   // machine token, not the user's language
    };
    tr.addEventListener("click", pick);
    tr.addEventListener("keydown", (e) => {
      if (e.key === "Enter" || e.key === " ") { e.preventDefault(); pick(); }
    });
  });
  return box;
}

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
  // chips-only messages (no text) render without an empty bubble
  const chipsOnly = !text && !metaHtml && meta.choiceGroups && meta.choiceGroups.length;
  wrap.innerHTML = chipsOnly ? foot
    : `<div class="bubble ${meta.error ? "bubble-error" : ""}">${text ? esc(text) : "<i>(file only)</i>"}${metaHtml}</div>${foot}`;
  // Interactive disambiguation chips (pendingChoices from the agent): one row per
  // ambiguous name; clicking a chip sends its sendText as the next chat turn.
  if (meta.choiceGroups && meta.choiceGroups.length) {
    meta.choiceGroups.forEach((g) => {
      // Trip plans carry too many columns for a chip — they get a table of their own.
      if (g.kind === "PLAN" && (g.options || []).some((o) => o.meta)) {
        wrap.appendChild(planPickTable(g));
        return;
      }
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
          sendAgent({ keepLang: true });   // chip text is composed, not the user's language
        });
        row.appendChild(btn);
      };
      // "I"/"me"-style ambiguity defaults to the signed-in user, offered first.
      if (CURRENT_USER_NAME && g.name && /^(i|me|myself|나|저|본인)$/i.test(String(g.name).trim())) {
        addChip(`${CURRENT_USER_NAME} · that’s me`, `The traveler is ${CURRENT_USER_NAME}.`);
      }
      (g.options || []).forEach((opt) => {
        const isSkip = !opt.staffId && /^skip$/i.test(opt.label || "");
        addChip(opt.label || opt.sendText || "?", opt.sendText || opt.label || "", isSkip ? "choice-skip" : "");
      });
      // Opt-out fallback for older backends whose options don't include a Skip. Only
      // person groups get it — its text ("Don't add … to this trip") is nonsense on a
      // settlement card-type / receipt / manual-expense group, and clicking it there
      // used to derail the chip state machine.
      const personGroup = !g.kind || g.kind === "STAFF" || g.kind === "TRAVELER";
      if (personGroup && !(g.options || []).some((opt) => /^skip$/i.test(opt.label || ""))) {
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
  btn.textContent = T("✓ Save to BizPlay (set approval order)", "✓ BizPlay에 저장 (결재선 지정)");
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
  bzResolveCurrentUser();
  return bzApproval.roster;
}

/* Bind "I"/"me" to the corp user we actually draft as. Without this the chat could tell the
 * agent one name while the documents carry another user's id. */
function bzResolveCurrentUser() {
  if (localStorage.getItem("bizplay.userName")) return;   // explicit override wins
  const me = bzApproval.roster.find((u) => String(u.id) === String(BZ_CORP_USER_ID));
  if (me && me.name) CURRENT_USER_NAME = me.name;
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

/* ---- Chat-mode approval line: picked IN THE THREAD, no popup ----
 * "save it" -> the assistant asks who approves; roster chips build the 결재선 in
 * order (shown as a card); Done ✓ saves through the same bzSubmitManualCreate
 * path the modal uses. */
async function bzChatApprovalFlow() {
  bzManualSave = !(agent.live && agent.sessionId);
  bzApproval.lines = [];
  if (!bzApproval.roster.length) {
    try {
      await bzLoadRoster();
    } catch (e) {
      appendMsg("assistant", "⚠ " + T("I couldn't load the staff list for the approval line: ",
        "결재선을 위한 직원 명단을 불러오지 못했어요: ") + friendlyError(e.message), { error: true });
      return;
    }
  }
  bzChatAskApprover();
}

/* The 결재선 as a read-only thread card (same look as the section previews). */
function bzChatApprovalCard() {
  const thread = $("agentThread");
  let card = thread.querySelector(".chat-appr-card");
  if (!card) {
    const wrap = document.createElement("div");
    wrap.className = "msg msg-assistant chat-section";
    wrap.innerHTML = `<div class="prev-card chat-appr-card">
        <div class="pc-head"><span class="b-ico">${svgIcon("shield")}</span> ${esc(T("Approval line", "결재선"))}</div>
        <div class="pc-body"></div></div>`;
    thread.appendChild(wrap);
    card = wrap.querySelector(".chat-appr-card");
  }
  card.querySelector(".pc-body").innerHTML = bzApproval.lines.length
    ? bzApproval.lines.map((l, i) => {
        const kindKo = (BZ_LINE_KINDS.find((k) => k[0] === l.kind) || [])[1] || l.kind;
        return `<div class="pc-row"><span class="pc-k">${i + 1}. ${esc(kindKo)}</span>
            <span class="pc-v">${esc(l.name)} · ${esc(l.dept || "?")}${l.position ? " · " + esc(l.position) : ""}</span></div>`;
      }).join("")
    : `<div class="pc-row"><span class="pc-v">${esc(T("No approvers yet.", "아직 결재자가 없어요."))}</span></div>`;
  thread.appendChild(card.closest(".msg"));   // the 결재선 rides with the newest message
  thread.scrollTop = thread.scrollHeight;
}

/* English labels for the real ApprovalKindType values (Korean comes from BZ_LINE_KINDS). */
const BZ_KIND_EN = { APPROVAL: "Approval", AGREE: "Agree", ACCEPT: "Receive", REFERENCE: "Reference" };

function bzChatAskApprover() {
  // Retire any earlier, unanswered approval chip rows — the question re-asks at
  // the bottom after corrections, and two active rows would conflict.
  document.querySelectorAll("#agentThread .choice-row.appr-row:not(.choice-done)")
    .forEach((r) => r.classList.add("choice-done"));
  const picked = new Set(bzApproval.lines.map((l) => l.id));
  const opts = bzApproval.roster.filter((u) => !picked.has(u.id)).slice(0, 10)
    .map((u) => ({ label: `${u.name} · ${u.dept || "?"}${u.position ? " · " + u.position : ""}`, value: String(u.id) }));
  if (bzApproval.lines.length) opts.push({ label: T("Done ✓ — save now", "완료 ✓ — 저장하기"), value: "", done: true });
  const row = guideChips(bzApproval.lines.length
    ? T("Anyone else for the approval line? Or choose Done to save.", "결재선에 더 추가할 분이 있나요? 없으면 완료를 눌러 저장할게요.")
    : T("Almost done — who should be in the approval line? Pick people in order.",
        "거의 다 됐어요 — 결재선에 누가 들어가나요? 순서대로 선택해 주세요."),
    opts, (opt) => {
      if (opt.done) {
        appendMsg("assistant", T("Saving to BizPlay…", "BizPlay에 저장하는 중…"));
        bzSubmitManualCreate();
        return;
      }
      const u = bzApproval.roster.find((x) => String(x.id) === opt.value);
      if (u) bzChatAskKind(u); else bzChatAskApprover();
    });
  row.classList.add("appr-row");
  return row;
}

/* Each person gets a role, exactly like the real UI's per-line dropdown:
 * 결재 / 합의 / 수신 / 참조 (ApprovalKindType — the server rejects anything else). */
function bzChatAskKind(u) {
  const row = guideChips(
    T(`What role should ${u.name} have in the approval line?`, `${u.name} 님은 어떤 유형인가요?`),
    BZ_LINE_KINDS.map(([value, ko]) => ({
      label: chatLang === "ko" ? ko : `${ko} · ${BZ_KIND_EN[value] || value}`,
      value,
    })),
    (opt) => {
      bzApproval.lines.push({ id: u.id, name: u.name, dept: u.dept, empNo: u.empNo, position: u.position, kind: opt.value });
      bzChatApprovalCard();
      bzChatAskApprover();
    });
  row.classList.add("appr-row");
  return row;
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
    if (el.type === "checkbox") { if (el.checked) itemValues[k] = "true"; }   // real-UI encoding
    else if (el.type === "radio") { if (el.checked) itemValues[k] = el.value; }
    else if ((el.value || "").trim()) itemValues[k] = el.value.trim();
  });
  document.querySelectorAll(".trav-card [data-bztf-group]").forEach((g) => {
    const k = g.getAttribute("data-bztf-group");
    if (itemValues[k]) return;
    const v = bzReadGroup(g);
    if (v) itemValues[k] = v;
  });
  // 교육정보 composites (form-level and per traveler) override with the STRUCTURED
  // object — the backend writes it into the real bstrEdus[] encoding.
  document.querySelectorAll("[data-xf-group][data-edu], .trav-card [data-bztf-group][data-edu]").forEach((g) => {
    const k = g.getAttribute("data-xf-group") || g.getAttribute("data-bztf-group");
    const v = bzReadEduObject(g);
    if (v) itemValues[k] = v; else delete itemValues[k];
  });
  // 출장 연계 휴가 composites override with the STRUCTURED object (value/value2/selections).
  document.querySelectorAll("[data-xf-group][data-leave], .trav-card [data-bztf-group][data-leave]").forEach((g) => {
    const k = g.getAttribute("data-xf-group") || g.getAttribute("data-bztf-group");
    const v = bzReadLeaveObject(g);
    if (v) itemValues[k] = v; else delete itemValues[k];
  });
  // Sub-choice widgets (value2: 협력사 구분, 보험 기간) merge into {choice, sub}.
  document.querySelectorAll(".trav-card [data-bztf2], #tripExtraFields [data-xf2]").forEach((el) => {
    const k = el.getAttribute("data-bztf2") || el.getAttribute("data-xf2");
    let sub = null;
    if (el.type === "radio") { if (el.checked) sub = el.value; }
    else sub = (el.value || "").trim() || null;
    if (sub && typeof itemValues[k] === "string") itemValues[k] = { choice: itemValues[k], sub };
  });
  // PARTNER_SUPPORT extras: partner company -> selections row, visit purpose -> "text" slot.
  document.querySelectorAll(".trav-card [data-bztfp], #tripExtraFields [data-xfp], .trav-card [data-bztft], #tripExtraFields [data-xft]").forEach((el) => {
    const isPartner = el.hasAttribute("data-bztfp") || el.hasAttribute("data-xfp");
    const k = el.getAttribute("data-bztfp") || el.getAttribute("data-xfp")
      || el.getAttribute("data-bztft") || el.getAttribute("data-xft");
    const v = (el.value || "").trim();
    if (!v || !itemValues[k]) return;   // details only ride along with a Y/N choice
    if (typeof itemValues[k] === "string") itemValues[k] = { choice: itemValues[k] };
    itemValues[k][isPartner ? "partner" : "purpose"] = v;
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
    closeCreate();               // done — the toast + list entry are the confirmation
  } catch (e) {
    const msg = "Save failed: " + friendlyError(e.message);
    $("validationSummary").textContent = msg;
    toast(msg, "err");
    // Chat mode hides the footer where validationSummary lives — the failure
    // must survive in the thread, not just in a 4-second toast.
    if (chatOnly) appendMsg("assistant", "⚠ " + msg, { error: true });
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
  document.body.classList.remove("ap-open");          // leaving Agent Settings: tabs take over again
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

/* ================================================================
 *  EXPENSE REPORTS (structured) — list table + Create Report modal
 *  list:   GET  /agent-conversations/agents/expense-report/sessions
 *  parse:  POST /agent-conversations/agents/expense-report (planId + fileIds)
 *  submit: POST /api/v1/reports
 * ================================================================ */
const REPORTS_API = AGENT_API + "/agents/expense-report";   // agent draft/session flow (create + resume)
const EXPENSE_API = API_ORIGIN + "/api/v1/reports";          // persisted/posted report lines
const SECTIONS = [
  { key: "CostInformation", label: "Cost", ko: "비용" },
  { key: "TransportationInformation", label: "Transportation", ko: "교통비" },
  { key: "Etc", label: "Etc", ko: "기타" },
];
const rc = { planId: null, plan: null, sessionId: null, draft: null, uploading: false };

/* ---- Reports table: GET /api/v1/reports returns one report per row
        (header + its lines[]). Each row is keyed by the report id. ---- */
let reportsCache = [];   // [{ key (=report id), tripPlanId, sessionId, department, approvalStatus, lineCount, total, date, lines, plan, title }]

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
  body.innerHTML = loadingRow(15);
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
    body.innerHTML = emptyRow(15, { icon: "alert", title: "Couldn’t load reports", sub: esc(friendlyError(e.message)) });
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
      ? emptyRow(15, { icon: "search", title: "No reports match this search", sub: "Try different keywords." })
      : emptyRow(15, { icon: "receipt", title: "No expense reports yet", sub: "Import an approved plan and attach receipts to settle a trip.",
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
      <td class="c-json"><button class="btn-json" data-report-json="${esc(g.key)}"
        title="Download this settlement's draft JSON">${svgIcon("download")}</button></td>
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
  return mdStaff.map((s) => ({ name: s.name, department: s.departmentName || "", position: s.position || "" }));
}
function liveDeptNames() {
  return mdDepts.map((d) => d.name);
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
  // RETIRED: /api/v1/staff and /api/v1/departments are no longer served — traveler data
  // comes from the external BizPlay API. Skip the fetch instead of collecting 404s.
  mdDepts = []; mdStaff = [];
  // try { await fetchMasterData(); } catch { mdDepts = []; mdStaff = []; }
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
  // RETIRED: local Staff & Departments admin (button commented out in index.html).
  // $("openMasterBtn").addEventListener("click", openMaster);
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

document.addEventListener("DOMContentLoaded", () => { initI18n(); init(); initAuditTab(); initMasterData(); initRole(); initDemoBanner(); initLlm(); initAp(); initCa(); initMcp(); });
