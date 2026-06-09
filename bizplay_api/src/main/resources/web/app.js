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

/* ---------- Global loading indicator ----------
 * A counter so overlapping fetches keep the bar visible until the last finishes. */
let loadCount = 0;
function beginLoad() { loadCount++; const b = document.getElementById("loadbar"); if (b) b.classList.remove("hidden"); }
function endLoad() { loadCount = Math.max(0, loadCount - 1); if (loadCount === 0) { const b = document.getElementById("loadbar"); if (b) b.classList.add("hidden"); } }
/* A table-row "Loading…" placeholder with a spinner. */
function loadingRow(cols) { return `<tr><td colspan="${cols}" class="empty-row"><span class="spin"></span>Loading…</td></tr>`; }

/* The REST API runs on the Spring app (:8080). When this page is served from the
 * same origin (http://localhost:8080/web/) we use a relative path. If it is opened
 * from somewhere else (Live Server on :5500, etc.) we target :8080 directly — the
 * server adds a dev CORS header for /api/v1/plans so that still works.
 * Note: opening via file:// cannot reach the API (browsers block it) — use the URL. */
const API_ORIGIN =
  location.protocol === "http:" && (location.port === "8080" || location.port === "")
    ? ""
    : "http://localhost:8080";
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
    const hint = location.protocol === "file:"
      ? " — open the page at http://localhost:8080/web/ instead of a file:// path."
      : "";
    plansCache = [];
    body.innerHTML = `<tr><td colspan="10" class="empty-row">Failed to load plans: ${esc(e.message)}${esc(hint)}</td></tr>`;
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
    const msg = plansCache.length
      ? "No plans match this filter."
      : "No plans yet — create your first with the agent or manually.";
    body.innerHTML = `<tr><td colspan="10" class="empty-row">${msg}</td></tr>`;
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
    if (!res.ok) throw new Error((json && (json.message || json.detail || json.title)) || `HTTP ${res.status}`);
    const r = (json && (json.data || json.payload)) || {};
    const deleted = r.deleted != null ? r.deleted : ids.length;
    const missing = (r.notFoundIds && r.notFoundIds.length) ? ` (${r.notFoundIds.length} not found)` : "";
    toast(`Removed ${deleted} plan${deleted === 1 ? "" : "s"}${missing}.`, "ok");
    loadPlans();
  } catch (e) {
    toast("Remove failed: " + e.message, "err");
  } finally {
    btn.disabled = false; btn.textContent = "🗑 Remove";
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
  document.querySelectorAll('input[name="classification"]').forEach((r) => (r.checked = false));
  $("attachmentList").innerHTML = attachPlaceholder();
  attachments = [];
  $("validationSummary").textContent = "";
  clearInvalid();
  addTraveler();           // start with one traveler, like the screenshots
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
  const staffOpts = (sel) =>
    `<option value="">Select Traveler…</option>` +
    STAFF.map((s) => `<option value="${esc(s.name)}" ${s.name === sel ? "selected" : ""}>${esc(s.name)} (${esc(s.department)} · ${esc(s.position)})</option>`).join("");
  const deptOpts = (sel) =>
    `<option value="">Select Budget Department…</option>` +
    DEPARTMENTS.map((d) => `<option value="${esc(d)}" ${d === sel ? "selected" : ""}>${esc(d)}</option>`).join("");

  $("travelerList").innerHTML = travelers.map((t, idx) => `
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
      </div>
    </div>`).join("");
}

/* ---- Attachments (URL only, to stay simple) ---- */
let attachments = [];
function attachPlaceholder() {
  return `<div class="att-placeholder">
      <div class="folder">🗂</div><div>No reference documents attached</div>
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
    `<div class="att-row">🔗 <a href="${esc(a.URL)}" target="_blank" rel="noopener">${esc(a.URL)}</a>
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
    classification: (document.querySelector('input[name="classification"]:checked') || {}).value || "",
  };
}

/* Validate trip-level fields. Returns a list of missing-field messages. */
function validateTrip(trip) {
  const missing = [];
  clearInvalid();
  if (!trip.purpose) { missing.push("business-trip type (purpose)"); mark("tripPurpose"); }
  if (!trip.start || !trip.end) { missing.push("travel dates"); if (!trip.start) mark("startDate"); if (!trip.end) mark("endDate"); }
  else if (trip.end < trip.start) { missing.push("end date must be on/after start date"); mark("endDate"); }
  if (!trip.destination) { missing.push("trip destination"); mark("tripDestination"); }
  if (!trip.title) { missing.push("title"); mark("tripTitle"); }
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

async function completeCreate() {
  const trip = readTripFields();

  // 1) trip-level + traveler identity must be valid first
  let missing = validateTrip(trip);
  if (missing.length) {
    $("validationSummary").textContent = "Missing required: " + missing.join(", ");
    toast("Please complete the highlighted required fields.", "err");
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
      Content: trip.content,
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
      throw new Error((json && (json.message || json.detail || json.title)) || `HTTP ${res.status}`);
    }
    toast("Business trip plan created.", "ok");
    closeCreate();
    loadPlans();
  } catch (e) {
    $("validationSummary").textContent = "Create failed: " + e.message;
    toast("Create failed: " + e.message, "err");
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
    t[field] = sel.value;
    if (field === "name") {
      const staff = STAFF.find((s) => s.name === sel.value);
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

  // --- Draft edit (PUT /sessions/{id}/draft) ---
  $("draftEditBtn").addEventListener("click", () => { agent.editing ? cancelEdit() : enterEdit(); });
  $("draftSaveBtn").addEventListener("click", saveDraftEdit);
  $("draftBody").addEventListener("click", (ev) => {
    const rm = ev.target.closest("[data-edrm]");
    if (!rm) return;
    syncEditTravelersFromInputs();
    agent.editTravelers.splice(Number(rm.getAttribute("data-edrm")), 1);
    renderDraft();
  });

  // --- Agent ---
  $("openAgentBtn").addEventListener("click", openAgent);
  $("agentCloseBtn").addEventListener("click", closeAgent);
  $("agentSendBtn").addEventListener("click", sendAgent);
  $("agentCreateBtn").addEventListener("click", createFromAgent);
  $("agentFileInput").addEventListener("change", onAgentFiles);
  $("agentFiles").addEventListener("click", (ev) => {
    const rm = ev.target.closest("[data-fchip]");
    if (!rm) return;
    agent.pending.splice(Number(rm.getAttribute("data-fchip")), 1);
    renderAgentFiles();
  });
  // Enter to send (Shift+Enter = newline)
  $("agentInput").addEventListener("keydown", (ev) => {
    if (ev.key === "Enter" && !ev.shiftKey) { ev.preventDefault(); sendAgent(); }
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
    const v = ev.target.closest("[data-view]");
    const a = ev.target.closest("[data-approve]");
    const c = ev.target.closest("[data-cancel]");
    if (v) openDetail(v.getAttribute("data-view"));
    else if (a) setApproval(a.getAttribute("data-approve"), "Approval complete");
    else if (c) {
      confirmDialog({
        title: "Cancel business trip",
        message: "Cancel this business trip plan? It will be marked as a trip cancellation.",
        confirmText: "Cancel trip",
      }).then((ok) => { if (ok) setApproval(c.getAttribute("data-cancel"), "Business trip cancellation"); });
    }
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
    const row = ev.target.closest("[data-report-key]");
    if (row) openReportDetail(row.getAttribute("data-report-key"));
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

  loadPlans();
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
};

/* Apply mode-specific labels to the shared agent modal. */
function applyAgentMode() {
  const report = agent.mode === "report";
  $("agentTitle").textContent = report ? "Expense Report — Agent" : "Create Plan with Agent";
  $("agentSub").textContent = report
    ? "Describe expenses or attach PDF receipts. The agent builds the report from your finished trip."
    : "Describe the trip in plain language, or attach a staff spreadsheet / itinerary PDF. The agent fills the draft for you.";
  $("agentCreateBtn").textContent = report ? "Submit report" : "Create this plan";
}

function resetAgent() {
  agent.sessionId = null;
  agent.status = null;
  agent.draft = null;
  agent.pending = [];
  agent.busy = false;
  agent.editing = false;
  $("agentInput").value = "";
  $("agentFileInput").value = "";
  renderAgentFiles();
}

function openAgent() {
  resetAgent();
  agent.mode = "plan";
  agent.planId = null;
  applyAgentMode();
  $("agentThread").innerHTML = `
    <div class="msg msg-assistant"><div class="bubble">Hi! Tell me about the business trip — who travels, where, when, and why. You can also attach a staff list (.xlsx) or a booking/itinerary (.pdf).</div></div>`;
  renderDraft();
  $("agentOverlay").classList.remove("hidden");
  setTimeout(() => $("agentInput").focus(), 50);
}

/* Start an expense-report conversation for a finished plan. */
function openReport(planId, title) {
  resetAgent();
  agent.mode = "report";
  agent.planId = planId;
  applyAgentMode();
  $("agentThread").innerHTML = `
    <div class="msg msg-assistant"><div class="bubble">Let's build the expense report for <b>${esc(title || "this trip")}</b>. Tell me the expenses (e.g. “Hotel 300,000 KRW on 2026-07-01”, “Taxi 20,000”) or attach PDF receipts.</div></div>`;
  renderDraft();
  $("agentOverlay").classList.remove("hidden");
  setTimeout(() => $("agentInput").focus(), 50);
}

/* Open the agent modal pre-loaded with a saved session (resume), plan or report. */
function openAgentResumed(d, mode = "plan") {
  resetAgent();
  agent.mode = mode;
  agent.planId = (d.draftJson && d.draftJson.TripPlanId) || null;
  applyAgentMode();
  agent.sessionId = d.sessionId;
  agent.status = d.status || null;
  agent.draft = d.draftJson || null;
  // Rebuild the conversation from saved history.
  const label = mode === "report" ? "report" : "session";
  $("agentThread").innerHTML =
    `<div class="msg msg-assistant"><div class="bubble">↻ Resumed ${label} <code>${esc(String(d.sessionId).slice(0, 8))}</code> — continue chatting${mode === "report" ? "." : " or edit the draft."}</div></div>`;
  (d.chatEventJson || []).forEach((t) => {
    if (!t || !t.content) return;
    appendMsg(t.role === "assistant" ? "assistant" : "user", t.content, {});
  });
  renderDraft();
  $("agentOverlay").classList.remove("hidden");
}
function closeAgent() { $("agentOverlay").classList.add("hidden"); }

/* ---- File upload ---- */
async function onAgentFiles(ev) {
  const files = Array.from(ev.target.files || []);
  if (!files.length) return;
  setAgentBusy(true, "Uploading…");
  try {
    const fd = new FormData();
    files.forEach((f) => fd.append("files", f));
    const res = await fetch(`${AGENT_API}/files/create-batch`, { method: "POST", body: fd });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error((json && (json.message || json.detail)) || `HTTP ${res.status}`);
    const uploaded = (json && (json.data || json.payload)) || [];
    uploaded.forEach((u) => agent.pending.push({ fileId: u.fileId, filename: u.filename }));
    renderAgentFiles();
  } catch (e) {
    toast("Upload failed: " + e.message, "err");
  } finally {
    ev.target.value = "";
    setAgentBusy(false);
  }
}
function renderAgentFiles() {
  const box = $("agentFiles");
  if (!agent.pending.length) { box.innerHTML = ""; return; }
  box.innerHTML = agent.pending.map((f, i) => {
    const icon = /\.pdf$/i.test(f.filename) ? "📄" : "📊";
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
    const body = { corpNo: CORP_NO, message: message || null, fileIds };
    if (agent.sessionId) body.sessionId = agent.sessionId;
    // A new report session must reference the plan it reports on.
    if (agent.mode === "report" && !agent.sessionId && agent.planId) body.planId = agent.planId;
    const endpoint = agent.mode === "report" ? "/agents/expense-report" : "/agents/trip-plan";
    const res = await fetch(`${AGENT_API}${endpoint}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    const json = await res.json().catch(() => ({}));
    typing.remove();
    if (!res.ok) throw new Error((json && (json.message || json.detail || json.title)) || `HTTP ${res.status}`);
    const data = (json && (json.data || json.payload)) || {};
    agent.sessionId = data.sessionId || agent.sessionId;
    agent.status = data.status || null;
    agent.draft = data.draftJson || agent.draft;
    appendMsg("assistant", data.reply || "(no reply)", { intent: data.intent, subAgents: data.subAgents });
    renderDraft();
  } catch (e) {
    typing.remove();
    appendMsg("assistant", "⚠ " + e.message, { error: true });
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

/* ---- Thread rendering ---- */
function appendMsg(role, text, meta = {}) {
  const thread = $("agentThread");
  const wrap = document.createElement("div");
  wrap.className = "msg msg-" + role;
  let metaHtml = "";
  if (meta.files && meta.files.length) {
    metaHtml += `<div class="bubble-files">${meta.files.map((f) => `<span>📎 ${esc(f)}</span>`).join("")}</div>`;
  }
  let foot = "";
  if (meta.subAgents && meta.subAgents.length) {
    foot = `<div class="msg-meta">${meta.intent ? `<span class="chip-intent">${esc(meta.intent)}</span>` : ""}${meta.subAgents.map((a) => `<span class="chip-agent">${esc(prettyAgent(a))}</span>`).join("")}</div>`;
  }
  wrap.innerHTML = `<div class="bubble ${meta.error ? "bubble-error" : ""}">${text ? esc(text) : "<i>(file only)</i>"}${metaHtml}</div>${foot}`;
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

/* ---- Draft preview / verify ---- */
function renderDraft() {
  const body = $("draftBody");
  const pill = $("draftStatus");
  const btn = $("agentCreateBtn");
  const hint = $("agentCreateHint");

  const d = agent.draft;
  const ti = (d && d.TripInformation) || null;
  const status = agent.status || (d ? "COLLECTING" : null);

  // status pill
  pill.className = "status-pill " + (status === "READY_FOR_REVIEW" ? "status-ready" : status ? "status-collecting" : "status-none");
  pill.textContent = status ? status.replace(/_/g, " ") : "No draft";

  // Report mode renders a different body (expense sections) and has no draft edit.
  if (agent.mode === "report") {
    $("draftEditBtn").classList.add("hidden");
    $("draftSaveBtn").classList.add("hidden");
    btn.classList.remove("hidden");
    renderReportBody(d, hint, btn);
    return;
  }

  // Edit / Save controls: only available once a session + draft exist.
  const editBtn = $("draftEditBtn");
  const saveBtn = $("draftSaveBtn");
  const hasDraft = !!(d && ti && agent.sessionId);
  editBtn.classList.toggle("hidden", !hasDraft);

  if (!d || !ti) {
    agent.editing = false;
    body.innerHTML = `<div class="draft-empty-wrap"><span class="ico">📝</span><p>No draft yet — send a message and the plan will build here.</p></div>`;
    btn.disabled = true; btn.classList.remove("hidden"); saveBtn.classList.add("hidden"); hint.textContent = "";
    return;
  }

  // Edit mode: render the form, swap the footer to Save.
  if (agent.editing) {
    editBtn.textContent = "✕ Cancel";
    saveBtn.classList.remove("hidden");
    btn.classList.add("hidden");
    hint.textContent = "Editing — adjust fields, then save a checkpoint.";
    renderDraftEdit(d, ti);
    return;
  }
  editBtn.textContent = "✎ Edit";
  saveBtn.classList.add("hidden");
  btn.classList.remove("hidden");

  const missing = d.missingFields || [];
  const travelers = ti.Travelers || [];
  const period = ti.BusinessPeriod
    || (ti.BusinessStartDate && ti.BusinessEndDate ? `${ti.BusinessStartDate} → ${ti.BusinessEndDate}` : "");
  const classification = ti["Business Trip Classifcation"] || ti.BusinessTripClassification;

  // Hero summary
  let html = `<div class="dp-hero">
      <div class="h-title">${ti.Title ? esc(ti.Title) : '<span class="ph">Untitled trip</span>'}</div>
      <div class="h-route">
        <span class="pin">📍</span>
        <span>${ti.Destination ? esc(ti.Destination) : '<span class="ph">—</span>'}</span>
      </div>
      <div class="h-meta">
        ${ti.Purpose ? `<span class="tagpill accent">${esc(ti.Purpose)}</span>` : ""}
        ${period ? `<span class="tagpill">🗓 ${esc(period)}</span>` : ""}
        ${classification ? `<span class="tagpill">${esc(classification)}</span>` : ""}
      </div>
    </div>`;

  // Details
  const row = (k, v) => `<div class="dl-row"><dt>${k}</dt><dd>${v ? esc(v) : '<span class="empty">Not set</span>'}</dd></div>`;
  html += `<div class="dp-block">
      <div class="dp-block-h">Trip details</div>
      <dl class="dl">
        ${row("Purpose", ti.Purpose)}
        ${row("Destination", ti.Destination)}
        ${row("Period", period)}
        ${ti.Content ? row("Content", ti.Content) : ""}
      </dl>
    </div>`;

  // Travellers
  html += `<div class="dp-block">
      <div class="dp-block-h">Travellers <span class="sec-count">${travelers.length}</span></div>`;
  if (!travelers.length) {
    html += `<p class="draft-empty sm" style="color:#9098a6;font-size:12.5px;margin:0">No travellers captured yet.</p>`;
  } else {
    html += travelers.map((t) => {
      const dep = [t.Department, t.Position].filter(Boolean).map(esc).join(" · ");
      return `<div class="trav-mini">
        <div class="tm-avatar">${esc(initials(t.Name))}</div>
        <div class="tm-info">
          <div class="tm-name">${esc(t.Name || "Unnamed")}</div>
          ${dep ? `<div class="tm-dept">${dep}</div>` : ""}
          <div class="tm-route">${esc(t.Origin || "?")} <i>→</i> ${esc(t.Destination || ti.Destination || "?")}${t.ReturnPoint ? ` <i>→</i> ${esc(t.ReturnPoint)}` : ""}${t.TransportationMethod ? `<span class="tm-method">${esc(t.TransportationMethod)}</span>` : ""}</div>
        </div>
      </div>`;
    }).join("");
  }
  html += `</div>`;

  // Attachments
  const atts = d.Attachemnt || d.Attachment || [];
  if (atts.length) {
    html += `<div class="dp-block"><div class="dp-block-h">Attachments <span class="sec-count">${atts.length}</span></div>
      ${atts.map((a) => `<div class="att-mini">📎 ${esc(a.FileID || a.URL || a.Type || "file")}</div>`).join("")}</div>`;
  }

  // Missing fields
  if (missing.length) {
    html += `<div class="draft-missing"><strong>⚠ Still needed</strong>${missing.map((m) => `<span>${esc(m)}</span>`).join("")}</div>`;
  }
  body.innerHTML = html;

  const ready = status === "READY_FOR_REVIEW" && missing.length === 0 && travelers.length > 0;
  btn.disabled = !ready;
  hint.textContent = ready ? "Ready — review above, then create." : (missing.length ? "Keep chatting to fill the missing fields." : "");
}

/* ---- Commit: map agent draft -> /api/v1/plans ---- */
/* Finalize button dispatcher: plan -> POST /plans, report -> POST /reports. */
function createFromAgent() {
  if (agent.mode === "report") return submitReport();
  return createPlanFromDraft();
}

async function createPlanFromDraft() {
  const d = agent.draft;
  if (!d || !d.TripInformation) return;
  const ti = d.TripInformation;
  let period = ti.BusinessPeriod;
  if (!period && ti.BusinessStartDate && ti.BusinessEndDate) period = `${ti.BusinessStartDate} to ${ti.BusinessEndDate}`;

  const payload = {
    CorpNo: d.CorpNo || CORP_NO,
    PlanType: d.PlanType || PLAN_TYPE,
    AgentSessionId: agent.sessionId,
    TripInformation: {
      Purpose: ti.Purpose,
      BusinessPeriod: period,
      Destination: ti.Destination,
      Title: ti.Title,
      Content: ti.Content,
      BusinessTripClassification: ti["Business Trip Classifcation"] || ti.BusinessTripClassification,
      Travelers: (ti.Travelers || []).map((t) => ({
        Name: t.Name, Department: t.Department, Position: t.Position,
        Origin: t.Origin, Destination: t.Destination || ti.Destination, ReturnPoint: t.ReturnPoint,
      })),
    },
    Attachemnt: (d.Attachemnt || d.Attachment || [])
      .filter((a) => a && (a.FileID || a.URL))
      .map((a) => ({ Type: a.Type || (a.URL ? "URL" : "File"), FileID: a.FileID, URL: a.URL })),
  };

  const btn = $("agentCreateBtn");
  btn.disabled = true; btn.textContent = "Creating…";
  try {
    const res = await fetch(API, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error((json && (json.message || json.detail || json.title)) || `HTTP ${res.status}`);
    toast("Plan created from the agent draft.", "ok");
    closeAgent();
    loadPlans();
  } catch (e) {
    toast("Create failed: " + e.message, "err");
    btn.disabled = false;
  } finally {
    btn.textContent = "Create this plan";
  }
}

/* ================================================================
 *  DETAIL — view one plan via GET /api/v1/plans/{id}
 * ================================================================ */
let currentDetailId = null;

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
    if (!res.ok) throw new Error((json && (json.message || json.detail || json.title)) || `HTTP ${res.status}`);
    const plan = (json && (json.data || json.payload)) || null;
    if (!plan) throw new Error("Plan not found.");
    renderDetail(plan);
  } catch (e) {
    $("detailBody").innerHTML = `<div class="draft-empty-wrap"><span class="ico">⚠️</span><p>${esc(e.message)}</p></div>`;
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
      <div class="h-route"><span class="pin">📍</span><span>${p.destination ? esc(p.destination) : '<span class="ph">—</span>'}</span></div>
      <div class="h-meta">
        ${p.purpose ? `<span class="tagpill accent">${esc(p.purpose)}</span>` : ""}
        ${period ? `<span class="tagpill">🗓 ${esc(period)}</span>` : ""}
        ${p.businessTripClassification ? `<span class="tagpill">${esc(p.businessTripClassification)}</span>` : ""}
        ${p.agentSessionId ? `<span class="tagpill agent">✨ via Agent</span>` : `<span class="tagpill">✍ Manual</span>`}
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
        const label = a.url || a.fileId || a.type || "file";
        return a.url
          ? `<div class="att-mini">🔗 <a href="${esc(a.url)}" target="_blank" rel="noopener">${esc(a.url)}</a></div>`
          : `<div class="att-mini">📎 ${esc(label)}</div>`;
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
    if (!res.ok) throw new Error((json && (json.message || json.detail || json.title)) || `HTTP ${res.status}`);
    toast("Plan deleted.", "ok");
    closeDetail();
    loadPlans();
  } catch (e) {
    toast("Delete failed: " + e.message, "err");
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
    if (!res.ok) throw new Error((json && (json.message || json.detail)) || `HTTP ${res.status}`);
    renderResumeList((json && (json.data || json.payload)) || []);
  } catch (e) {
    $("resumeList").innerHTML = `<div class="draft-empty-wrap"><span class="ico">⚠️</span><p>${esc(e.message)}</p></div>`;
  }
}
function renderResumeList(sessions) {
  const box = $("resumeList");
  const trip = sessions.filter((s) => (s.agentType || "") === "TRIP_PLAN");
  if (!trip.length) {
    box.innerHTML = `<div class="draft-empty-wrap"><span class="ico">🗂</span><p>No saved sessions yet. Start one with “Create with Agent”.</p></div>`;
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
    if (!res.ok) throw new Error((json && (json.message || json.detail)) || `HTTP ${res.status}`);
    const detail = (json && (json.data || json.payload)) || null;
    if (!detail) throw new Error("Session not found.");
    closeResume();
    openAgentResumed(detail);
  } catch (e) {
    toast("Could not load session: " + e.message, "err");
  }
}

/* ================================================================
 *  DRAFT EDIT — PUT /sessions/{id}/draft (manual checkpoint)
 * ================================================================ */
function enterEdit() {
  if (!agent.draft || !agent.sessionId) { toast("Send a message to start a draft first.", "err"); return; }
  agent.editing = true;
  agent.editTravelers = JSON.parse(JSON.stringify(((agent.draft.TripInformation) || {}).Travelers || []));
  renderDraft();
}
function cancelEdit() { agent.editing = false; renderDraft(); }

function renderDraftEdit(d, ti) {
  const start = ti.BusinessStartDate || "";
  const end = ti.BusinessEndDate || "";
  const cls = ti["Business Trip Classifcation"] || ti.BusinessTripClassification || "";
  const purposes = ["General business trip", "Educational business trip", "Overseas business trip"];
  const purposeOpts = `<option value="">—</option>` +
    purposes.map((p) => `<option value="${esc(p)}" ${p === ti.Purpose ? "selected" : ""}>${esc(p)}</option>`).join("");
  const travelers = agent.editTravelers;

  let html = `<div class="dp-block">
      <div class="dp-block-h">Edit trip</div>
      <div class="ed-field"><label>Title</label><input id="ed-title" type="text" value="${esc(ti.Title || "")}" /></div>
      <div class="ed-field"><label>Destination</label><input id="ed-dest" type="text" value="${esc(ti.Destination || "")}" /></div>
      <div class="ed-field"><label>Purpose</label><div class="select-wrap"><select id="ed-purpose">${purposeOpts}</select></div></div>
      <div class="ed-row">
        <div class="ed-field"><label>Start</label><input id="ed-start" type="date" value="${esc(start)}" /></div>
        <div class="ed-field"><label>End</label><input id="ed-end" type="date" value="${esc(end)}" /></div>
      </div>
      <div class="ed-field"><label>Classification</label><input id="ed-class" type="text" value="${esc(cls)}" placeholder="e.g. CS" /></div>
      <div class="ed-field"><label>Content</label><textarea id="ed-content" rows="2">${esc(ti.Content || "")}</textarea></div>
    </div>`;

  html += `<div class="dp-block"><div class="dp-block-h">Travellers <span class="sec-count">${travelers.length}</span></div>`;
  if (!travelers.length) {
    html += `<p style="color:#9098a6;font-size:12.5px;margin:0">No travellers. Add one via chat, e.g. “add John Doe”.</p>`;
  } else {
    html += travelers.map((t, i) => `
      <div class="ed-trav">
        <div class="ed-trav-head"><b>${esc(t.Name || "Unnamed")}</b>${t.Department ? `<span class="tm-dept">${esc(t.Department)}</span>` : ""}<button class="ed-rm" data-edrm="${i}" title="Remove">✕</button></div>
        <div class="ed-row">
          <div class="ed-field"><label>Origin</label><input data-ed="Origin" data-i="${i}" type="text" placeholder="Origin" value="${esc(t.Origin || "")}" /></div>
          <div class="ed-field"><label>Destination</label><input data-ed="Destination" data-i="${i}" type="text" placeholder="Destination" value="${esc(t.Destination || "")}" /></div>
        </div>
        <div class="ed-row">
          <div class="ed-field"><label>Return point</label><input data-ed="ReturnPoint" data-i="${i}" type="text" placeholder="Return" value="${esc(t.ReturnPoint || "")}" /></div>
          <div class="ed-field"><label>Transport</label><input data-ed="TransportationMethod" data-i="${i}" type="text" placeholder="e.g. KTX" value="${esc(t.TransportationMethod || "")}" /></div>
        </div>
      </div>`).join("");
  }
  html += `</div>`;
  $("draftBody").innerHTML = html;
}

/* Pull current input values back into the working traveller copy (so a re-render keeps edits). */
function syncEditTravelersFromInputs() {
  agent.editTravelers.forEach((t, i) => {
    ["Origin", "Destination", "ReturnPoint", "TransportationMethod"].forEach((f) => {
      const el = document.querySelector(`[data-ed="${f}"][data-i="${i}"]`);
      if (el) t[f] = el.value;
    });
  });
}

function emptyNull(v) { v = (v == null ? "" : String(v)).trim(); return v || null; }

function collectEditedDraft() {
  const d = agent.draft || {};
  const ti = d.TripInformation || {};
  syncEditTravelersFromInputs();
  const start = $("ed-start").value, end = $("ed-end").value;
  const period = (start && end) ? `${start} to ${end}` : (ti.BusinessPeriod || null);
  const travelers = agent.editTravelers.map((t) => ({
    Name: t.Name, Department: t.Department, Position: t.Position,
    Origin: emptyNull(t.Origin), Destination: emptyNull(t.Destination),
    ReturnPoint: emptyNull(t.ReturnPoint), TransportationMethod: emptyNull(t.TransportationMethod),
  }));
  return {
    CorpNo: d.CorpNo || CORP_NO,
    PlanType: d.PlanType || PLAN_TYPE,
    Attachemnt: d.Attachemnt || d.Attachment || [],
    missingFields: [],
    TripInformation: {
      Title: emptyNull($("ed-title").value),
      Destination: emptyNull($("ed-dest").value),
      Purpose: $("ed-purpose").value || null,
      "Business Trip Classifcation": emptyNull($("ed-class").value),
      Content: emptyNull($("ed-content").value),
      BusinessStartDate: start || null,
      BusinessEndDate: end || null,
      BusinessPeriod: period,
      Travelers: travelers,
    },
  };
}

async function saveDraftEdit() {
  if (!agent.sessionId) { toast("No session yet — send a message first.", "err"); return; }
  const draft = collectEditedDraft();
  const btn = $("draftSaveBtn");
  btn.disabled = true; btn.textContent = "Saving…";
  try {
    const res = await fetch(`${AGENT_API}/sessions/${encodeURIComponent(agent.sessionId)}/draft`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(draft),
    });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error((json && (json.message || json.detail || json.title)) || `HTTP ${res.status}`);
    const data = (json && (json.data || json.payload)) || {};
    agent.draft = data.draftJson || draft;
    agent.status = data.status || agent.status;
    agent.editing = false;
    renderDraft();
    toast("Draft checkpoint saved.", "ok");
  } catch (e) {
    toast("Save failed: " + e.message, "err");
  } finally {
    btn.disabled = false; btn.textContent = "Save draft";
  }
}

/* ================================================================
 *  TABS
 * ================================================================ */
function showTab(name) {
  currentTab = name;
  document.querySelectorAll(".tab").forEach((t) => t.classList.toggle("active", t.getAttribute("data-tab") === name));
  ["plan", "approve", "report"].forEach((n) => $("tab-" + n).classList.toggle("hidden", n !== name));
  animatePane(name);
  if (name === "approve") loadApprovals();
  if (name === "report") loadReports();
}

/* Re-fetch whichever tab is visible (used after the corp number changes). */
function reloadActiveTab() {
  if (currentTab === "approve") loadApprovals();
  else if (currentTab === "report") loadReports();
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
    body.innerHTML = `<tr><td colspan="8" class="empty-row">Failed to load: ${esc(e.message)}</td></tr>`;
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
    const msg = approveCache.length ? "No plans match this filter." : "No plans to review.";
    body.innerHTML = `<tr><td colspan="8" class="empty-row">${msg}</td></tr>`;
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
    return `<tr>
      <td class="c-no">${i + 1}</td>
      <td title="${esc(docNo)}"><span class="doc-no">${esc(docNo)}</span></td>
      <td class="c-title" title="${esc(p.title || "")}">${esc(p.title || "—")}</td>
      <td title="${esc((t0.name || "—") + extra)}">${esc((t0.name || "—") + extra)}</td>
      <td>${esc(t0.department || "—")}</td>
      <td class="c-period">${esc(period)}</td>
      <td><span class="appr-pill ${approvalClass(s)}">${esc(s)}</span></td>
      <td><div class="row-actions">
        <button class="btn-xs btn-view" data-view="${esc(p.id)}">View</button>
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
    if (!res.ok) throw new Error((json && (json.message || json.detail || json.title)) || `HTTP ${res.status}`);
    const data = json && (json.data || json.payload);
    const idx = approveCache.findIndex((p) => p.id === id);
    if (idx >= 0) approveCache[idx] = data || { ...approveCache[idx], approvalStatus: status };
    renderApprovals();
    toast(status === "Approval complete" ? "Plan approved." : status === "Business trip cancellation" ? "Plan cancelled." : "Status updated.", "ok");
  } catch (e) {
    toast("Update failed: " + e.message, "err");
  }
}

/* ================================================================
 *  TAB 3 — EXPENSE REPORT
 * ================================================================ */
function loadReportTab() { loadReportPlanList(); loadReportSessions(); }

async function loadReportPlanList() {
  const box = $("reportPlanList");
  box.innerHTML = `<div class="muted-pad">Loading…</div>`;
  try {
    const res = await fetch(`${API}?corpNo=${encodeURIComponent(CORP_NO)}`);
    const json = await res.json();
    let plans = (json && (json.data || json.payload)) || [];
    plans.sort((a, b) =>
      ((b.approvalStatus === "Approval complete") - (a.approvalStatus === "Approval complete")) ||
      String(b.createdAt || "").localeCompare(String(a.createdAt || "")));
    if (!plans.length) { box.innerHTML = `<div class="muted-pad">No plans yet.</div>`; return; }
    box.innerHTML = plans.map((p) => {
      const approved = p.approvalStatus === "Approval complete";
      return `<button class="mini-item" data-reportplan="${esc(p.id)}" data-title="${esc(p.title || "")}">
          <div class="mi-main">
            <div class="mi-title">${esc(p.title || "Untitled")}</div>
            <div class="mi-sub">${esc(p.destination || "—")} · ${esc(formatPeriod(p))}${approved ? ' · <span style="color:#157a47">approved</span>' : ""}</div>
          </div>
          <span class="mi-go">📝 Report →</span>
        </button>`;
    }).join("");
  } catch (e) {
    box.innerHTML = `<div class="muted-pad">Failed: ${esc(e.message)}</div>`;
  }
}
async function loadReportSessions() {
  const box = $("reportSessionList");
  box.innerHTML = `<div class="muted-pad">Loading…</div>`;
  try {
    const res = await fetch(`${AGENT_API}/agents/expense-report/sessions?corpNo=${encodeURIComponent(CORP_NO)}`);
    const json = await res.json();
    const sessions = (json && (json.data || json.payload)) || [];
    if (!sessions.length) { box.innerHTML = `<div class="muted-pad">No reports in progress.</div>`; return; }
    box.innerHTML = sessions.map((s) => {
      const st = s.status || "COLLECTING";
      const upd = s.updatedDate ? String(s.updatedDate).replace("T", " ").slice(0, 16) : "—";
      return `<button class="mini-item" data-reportsession="${esc(s.sessionId)}">
          <div class="mi-main">
            <div class="mi-title"><code>${esc(String(s.sessionId).slice(0, 8))}</code></div>
            <div class="mi-sub">${esc(st.replace(/_/g, " "))} · updated ${esc(upd)}</div>
          </div>
          <span class="mi-go">Resume →</span>
        </button>`;
    }).join("");
  } catch (e) {
    box.innerHTML = `<div class="muted-pad">Failed: ${esc(e.message)}</div>`;
  }
}
async function loadReportSession(id) {
  try {
    const res = await fetch(`${AGENT_API}/agents/expense-report/sessions/${encodeURIComponent(id)}`);
    const json = await res.json();
    const d = json && (json.data || json.payload);
    if (!res.ok || !d) throw new Error((json && (json.message || json.detail)) || "Report not found.");
    openAgentResumed(d, "report");
  } catch (e) {
    toast("Could not load report: " + e.message, "err");
  }
}

/* ---- Report draft preview (expense sections) ---- */
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

function renderReportBody(d, hint, btn) {
  const body = $("draftBody");
  if (!d) {
    body.innerHTML = `<div class="draft-empty-wrap"><span class="ico">🧾</span><p>No report yet — describe an expense or attach a receipt.</p></div>`;
    btn.disabled = true; hint.textContent = "";
    return;
  }
  const ti = d.TripInformation || {};
  const sections = [["Cost", d.CostInformation], ["Transportation", d.TransportationInformation], ["Etc", d.Etc]];

  let head = `<div class="dp-hero">
      <div class="h-title">${ti.Title ? esc(ti.Title) : '<span class="ph">Trip report</span>'}</div>
      <div class="h-route"><span class="pin">📍</span><span>${ti.Destination ? esc(ti.Destination) : "—"}</span></div>
      <div class="h-meta">${ti.BusinessPeriod ? `<span class="tagpill">🗓 ${esc(ti.BusinessPeriod)}</span>` : ""}<span class="tagpill accent">Expense report</span></div>
    </div>`;

  let grand = 0, count = 0, secHtml = "";
  sections.forEach(([name, sec]) => {
    const items = (sec && sec.Detail) || [];
    if (!items.length) return;
    count += items.length;
    let subtotal = 0;
    const rows = items.map((it) => {
      const amt = lineAmount(it); subtotal += amt;
      return `<div class="er-line"><span class="er-desc">${esc(lineDesc(it))}</span><span class="er-meta">${esc(lineDate(it))}</span><span class="er-amt">${fmtMoney(amt)}</span></div>`;
    }).join("");
    grand += subtotal;
    secHtml += `<div class="dp-block"><div class="dp-block-h">${name} <span class="sec-count">${items.length}</span></div>${rows}<div class="er-total">Subtotal <b>${fmtMoney(subtotal)}</b></div></div>`;
  });

  let html = head;
  if (count) html += `<div class="er-grand"><span>Total</span><span>${fmtMoney(grand)}</span></div>`;
  html += secHtml || `<p class="muted-pad">No expense lines yet. Describe one in the chat.</p>`;

  const missing = d.missingFields || [];
  if (missing.length) html += `<div class="draft-missing"><strong>⚠ Still needed</strong>${missing.map((m) => `<span>${esc(m)}</span>`).join("")}</div>`;

  body.innerHTML = html;
  btn.disabled = count === 0;
  hint.textContent = count === 0 ? "Add at least one expense line." : "Review the lines, then submit.";
}

/* ---- Submit report: POST /api/v1/reports (draft already matches the request shape) ---- */
async function submitReport() {
  const d = agent.draft;
  if (!d) return;
  const payload = JSON.parse(JSON.stringify(d));
  payload.CorpNo = d.CorpNo || CORP_NO;
  payload.PlanType = d.PlanType || PLAN_TYPE;
  payload.AgentSessionId = agent.sessionId;
  payload.TripPlanId = d.TripPlanId || agent.planId;

  const btn = $("agentCreateBtn");
  btn.disabled = true; btn.textContent = "Submitting…";
  try {
    const res = await fetch(`${API_ORIGIN}/api/v1/reports`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error((json && (json.message || json.detail || json.title)) || `HTTP ${res.status}`);
    const data = (json && (json.data || json.payload)) || {};
    toast(`Expense report submitted (${data.totalLines != null ? data.totalLines + " lines" : "ok"}).`, "ok");
    closeAgent();
    loadReportTab();
  } catch (e) {
    toast("Submit failed: " + e.message, "err");
    btn.disabled = false;
  } finally {
    btn.textContent = "Submit report";
  }
}

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
const rc = { planId: null, plan: null, sessionId: null, draft: null };

/* ---- Posted reports table: GET /api/v1/reports returns flat expense LINES;
        we group them into one row per report (session, or trip plan as fallback). ---- */
let reportsCache = [];   // grouped: [{ key, sessionId, tripPlanId, title, lines, total, date }]

function reportLineAmount(line) {
  const e = line.costExpense || line.transportationExpense || {};
  return num(e.amountUsed);
}
function reportLineDate(line) {
  const t = line.transportationExpense, c = line.costExpense;
  return (t && t.usageDate) || (c && (c.proofDate || c.startDate)) || "";
}

async function loadReports() {
  const body = $("reportBody");
  body.innerHTML = loadingRow(13);
  beginLoad();
  try {
    // Lines from the reports API, plus plans (to resolve each report's trip title).
    const [rRes, pRes] = await Promise.all([
      fetch(`${EXPENSE_API}?corpNo=${encodeURIComponent(CORP_NO)}`),
      fetch(`${API}?corpNo=${encodeURIComponent(CORP_NO)}`),
    ]);
    const rJson = await rRes.json();
    if (!rRes.ok) throw new Error((rJson && (rJson.message || rJson.detail)) || `HTTP ${rRes.status}`);
    const pJson = await pRes.json().catch(() => ({}));
    const lines = (rJson && (rJson.data || rJson.payload)) || [];
    const plans = (pJson && (pJson.data || pJson.payload)) || [];
    const planMap = {};
    plans.forEach((p) => { planMap[p.id] = p; });

    const groups = {};
    lines.forEach((ln) => {
      const key = ln.agentSessionId || ln.tripPlanId || ln.id;
      if (!groups[key]) {
        groups[key] = { key, sessionId: ln.agentSessionId || null, tripPlanId: ln.tripPlanId || null, approvalStatus: null, lines: 0, total: 0, date: "", items: [], itemIds: [] };
      }
      const g = groups[key];
      g.lines += 1;
      g.total += reportLineAmount(ln);
      if (ln.approvalStatus) g.approvalStatus = ln.approvalStatus;
      g.items.push(ln);
      if (ln.id) g.itemIds.push(ln.id);
      const d = reportLineDate(ln);
      if (d && d > g.date) g.date = d;
      if (!g.tripPlanId && ln.tripPlanId) g.tripPlanId = ln.tripPlanId;
    });
    reportsCache = Object.values(groups).map((g) => {
      const plan = g.tripPlanId ? planMap[g.tripPlanId] : null;
      g.plan = plan;
      g.title = (plan && plan.title) || (g.tripPlanId ? "(trip plan removed)" : "Untitled report");
      return g;
    });
    renderReports();
    animateRowsIn("reportBody");
  } catch (e) {
    reportsCache = [];
    $("reportCount").textContent = 0; $("reportShown").textContent = 0; $("reportTotal").textContent = 0;
    body.innerHTML = `<tr><td colspan="13" class="empty-row">Failed to load: ${esc(e.message)}</td></tr>`;
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
    const hay = [g.title, p.purpose, p.destination, t0.name, t0.department, aprStatus, aprLabel, "2026-지출보고서"]
      .filter(Boolean).join(" ").toLowerCase();
    return hay.includes(q);
  });
  list.sort((a, b) => String(b.date || "").localeCompare(String(a.date || "")));
  $("reportCount").textContent = list.length;
  $("reportShown").textContent = list.length;
  $("reportTotal").textContent = reportsCache.length;
  const body = $("reportBody");
  if (!list.length) {
    const msg = reportsCache.length ? "No reports match this search." : "No reports yet — click “Create Report”.";
    body.innerHTML = `<tr><td colspan="13" class="empty-row">${msg}</td></tr>`;
    updateReportSelBar();
    return;
  }
  body.innerHTML = list.map((g, i) => {
    const doc = `2026-지출보고서-${shortNo(g.key, i + 1)}`;
    const p = g.plan || {};
    const t0 = (p.travelers && p.travelers[0]) || {};
    const extra = p.travelers && p.travelers.length > 1 ? ` 외 ${p.travelers.length - 1}명` : "";
    const traveler = (t0.name || "—") + extra;
    const dept = t0.department || "—";
    const purpose = p.purpose ? shortPurpose(p.purpose) : "—";
    const period = (p.businessStartDate || p.businessPeriod) ? formatPeriod(p) : (g.date || "—");
    const aprStatus = g.approvalStatus || "Request for approval";
    const aprLabel = aprStatus === "Approval complete" ? "Approved"
      : aprStatus === "Business trip cancellation" ? "Cancelled" : "Request for approval";
    return `<tr class="row-click" data-report-key="${esc(g.key)}">
      <td class="c-check"><input type="checkbox" class="chk report-chk" data-ids="${esc((g.itemIds || []).join(","))}" data-title="${esc(g.title)}" aria-label="Select report" /></td>
      <td class="c-no">${i + 1}</td>
      <td title="${esc(doc)}"><span class="doc-no">${esc(doc)}</span></td>
      <td class="c-title" title="${esc(g.title)}">${esc(g.title)}</td>
      <td class="c-trav" title="${esc(traveler)}">${esc(traveler)}</td>
      <td class="c-dept" title="${esc(dept)}">${esc(dept)}</td>
      <td>${purpose === "—" ? "—" : `<span class="purpose-tag" title="${esc(p.purpose || "")}">${esc(purpose)}</span>`}</td>
      <td class="c-period" title="${esc(p.businessPeriod || "")}">${esc(period)}</td>
      <td>${g.lines}</td>
      <td class="er-amt">${fmtMoney(g.total)}</td>
      <td><span class="appr-pill ${approvalClass(aprStatus)}">${esc(aprLabel)}</span></td>
      <td class="c-period">${esc(g.date || "—")}</td>
      <td><button class="btn-xs btn-view" data-report-key="${esc(g.key)}">View</button></td>
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
  const ids = checks.flatMap((c) => (c.getAttribute("data-ids") || "").split(",").filter(Boolean));
  if (!ids.length) return;
  const label = checks.length === 1 ? `“${checks[0].getAttribute("data-title")}”` : `${checks.length} reports`;
  const ok = await confirmDialog({
    title: "Remove expense report" + (checks.length === 1 ? "" : "s"),
    message: `Remove ${label}? This deletes ${ids.length} expense line${ids.length === 1 ? "" : "s"} and cannot be undone.`,
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
    if (!res.ok) throw new Error((json && (json.message || json.detail || json.title)) || `HTTP ${res.status}`);
    const r = (json && (json.data || json.payload)) || {};
    const deleted = r.deleted != null ? r.deleted : ids.length;
    toast(`Removed ${deleted} line${deleted === 1 ? "" : "s"}.`, "ok");
    loadReports();
  } catch (e) {
    toast("Remove failed: " + e.message, "err");
  } finally {
    btn.disabled = false; btn.textContent = "🗑 Remove";
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
    if (!res.ok) throw new Error((json && (json.message || json.detail || json.title)) || `HTTP ${res.status}`);
    toast("Report deleted.", "ok");
    closeReportCreate();
    loadReports();
  } catch (e) {
    toast("Delete failed: " + e.message, "err");
  } finally {
    btn.disabled = false; btn.textContent = "🗑 Delete";
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
  renderRcSections();
  $("reportCreateOverlay").classList.remove("hidden");
}
function closeReportCreate() { $("reportCreateOverlay").classList.add("hidden"); }

/* ---- View a posted report (read-only) in the same popup ----
   GET /api/v1/reports/{id} per line, then reconstruct the report's sections. */
function lineToDetail(ln, i) {
  const ce = ln.costExpense, te = ln.transportationExpense;
  const dept = ln.department || "";
  if (te) {
    return {
      "순번": i + 1, Type: "법인", Use: te.usePurpose || "", BudgetDept: dept,
      AccountSubjects: te.account || "", EvidenceDate: te.usageDate || "",
      "교통수단": te.transportationMethod || "", Grade: te.category || "",
      Origin: te.originLocation || "", Destination: te.destinationLocation || "",
      UsageDate: te.usageDate || "", "거래처": te.vendor || "",
      "Supply price": te.supplyPrice, Tax: te.tax, AmountUsed: te.amountUsed,
      PolicyAmount: te.regulatedAmount, ApplicationAmount: te.amountUsed,
      RegulatedAmount: te.regulatedAmount,
      ExcessReason: ln.excessReason || "", Description: ln.briefs || "",
      Note: ln.note || "", TaxCode: te.taxCode || "", ApprovalNumber: ln.approvalNumber || "",
    };
  }
  const c = ce || {};
  return {
    "순번": i + 1, Type: "법인", Use: c.usePurpose || "", BudgetDept: dept,
    AccountSubjects: c.account || "", TaxCode: c.taxCode || "",
    StartDate: c.startDate || "", EndDate: c.endDate || "",
    EvidenceDate: c.proofDate || "", UsageDate: c.proofDate || "", "거래처": "",
    Description: c.description || "", "Supply price": c.amountUsed, Tax: null,
    AmountUsed: c.amountUsed, PolicyAmount: c.regulatedAmount, ApplicationAmount: c.amountUsed,
    RegulatedAmount: c.regulatedAmount,
    ExcessReason: ln.excessReason || "", Note: ln.note || "",
  };
}
function buildReportDraftFromLines(lines, plan) {
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
    bucket.Detail.push(lineToDetail(ln, ci[sc] !== undefined ? ci[sc]++ : 0));
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
  rc.detailIds = (g.itemIds || []).slice();
  $("rcPlanLabel").innerHTML = `<b>${esc(g.title)}</b>${g.tripPlanId ? ` <span class="muted">(${esc(String(g.tripPlanId).slice(0, 8))})</span>` : ""}`;
  try {
    // Fetch each line fresh via GET /api/v1/reports/{id}; fall back to cached items.
    let lines = g.items || [];
    if (g.itemIds && g.itemIds.length) {
      const fetched = await Promise.all(g.itemIds.map((id) =>
        fetch(`${EXPENSE_API}/${encodeURIComponent(id)}`)
          .then((r) => r.json()).then((j) => (j && (j.data || j.payload)) || null)
          .catch(() => null)
      ));
      const ok = fetched.filter(Boolean);
      if (ok.length) lines = ok;
    }
    rc.draft = buildReportDraftFromLines(lines, g.plan);
    renderRcTrip(rc.draft.TripInformation || {});
    renderRcSections();
    $("rcMsg").textContent = "";
  } catch (e) {
    $("rcMsg").textContent = "Load failed: " + e.message;
  }
}

async function openReportFromSession(id) {
  openReportCreate();
  $("rcMsg").textContent = "Loading report…";
  try {
    const res = await fetch(`${REPORTS_API}/sessions/${encodeURIComponent(id)}`);
    const json = await res.json();
    const d = json && (json.data || json.payload);
    if (!res.ok || !d) throw new Error((json && (json.message || json.detail)) || "Report not found.");
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
    $("rcMsg").textContent = "Load failed: " + e.message;
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
    if (!res.ok) throw new Error((json && (json.message || json.detail)) || `HTTP ${res.status}`);
    pickerCache = (json && (json.data || json.payload)) || [];
    pickerCache.sort((a, b) => String(b.createdAt || "").localeCompare(String(a.createdAt || "")));
    renderPlanPicker();
  } catch (e) {
    pickerCache = [];
    $("planPickerList").innerHTML = `<tr><td colspan="8" class="empty-row">Failed: ${esc(e.message)}</td></tr>`;
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
    const msg = pickerCache.length
      ? "No plans match this search."
      : "No approved plans yet. Approve a plan in the “Approve Plan” tab first.";
    body.innerHTML = `<tr><td colspan="8" class="empty-row">${msg}</td></tr>`;
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
    if (!res.ok || !p) throw new Error((json && (json.message || json.detail)) || "Plan not found.");
    rc.planId = id; rc.plan = p; rc.sessionId = null;
    rc.draft = planToReportDraft(p);
    $("rcPlanLabel").innerHTML = `<b>${esc(p.title || "Plan")}</b> <span class="muted">(${esc(String(id).slice(0, 8))})</span>`;
    $("rcClearPlan").classList.remove("hidden");
    renderRcTrip(rc.draft.TripInformation);
    renderRcSections();
    $("rcReceiptHint").textContent = "Now add receipts to populate the expense lines below.";
  } catch (e) {
    toast("Import failed: " + e.message, "err");
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
  const ic = icon === "search" ? "🔍" : "▾";
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
  ],
};
RC_COLUMNS.Etc = RC_COLUMNS.CostInformation;

function rcCell(col, it, i) {
  if (col.kind === "seq") return `<td>${esc(String(lineSeq(it, i)))}</td>`;
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

async function onReceiptFiles(ev) {
  const files = Array.from(ev.target.files || []);
  ev.target.value = "";
  if (!files.length) return;
  if (!rc.planId) { toast("Import a plan first.", "err"); return; }
  $("rcMsg").textContent = "Uploading & extracting receipts… (this can take a moment)";
  try {
    const fd = new FormData(); files.forEach((f) => fd.append("files", f));
    const up = await fetch(`${AGENT_API}/files/create-batch`, { method: "POST", body: fd });
    const upj = await up.json().catch(() => ({}));
    if (!up.ok) throw new Error((upj && (upj.message || upj.detail)) || `upload HTTP ${up.status}`);
    const fileIds = ((upj && (upj.data || upj.payload)) || []).map((u) => u.fileId);
    const body = { corpNo: CORP_NO, fileIds };
    if (rc.sessionId) body.sessionId = rc.sessionId; else body.planId = rc.planId;
    const res = await fetch(REPORTS_API, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
    const json = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error((json && (json.message || json.detail || json.title)) || `HTTP ${res.status}`);
    const d = (json && (json.data || json.payload)) || {};
    rc.sessionId = d.sessionId || rc.sessionId;
    rc.draft = d.draftJson || rc.draft;
    renderRcTrip((rc.draft && rc.draft.TripInformation) || {});
    renderRcSections();
    $("rcMsg").textContent = "";
    toast("Receipts processed.", "ok");
  } catch (e) {
    $("rcMsg").textContent = "Receipt processing failed: " + e.message;
    toast("Failed: " + e.message, "err");
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
    if (!res.ok) throw new Error((json && (json.message || json.detail || json.title)) || `HTTP ${res.status}`);
    const data = (json && (json.data || json.payload)) || {};
    toast(`Expense report created (${data.totalLines != null ? data.totalLines + " lines" : "ok"}).`, "ok");
    closeReportCreate();
    loadReports();
  } catch (e) {
    $("rcMsg").textContent = "Submit failed: " + e.message;
  } finally {
    btns.forEach((b) => (b.disabled = false));
  }
}

function tempSaveReport() {
  toast(rc.sessionId ? "Saved — the report session auto-saves each upload." : "Nothing to save yet.", rc.sessionId ? "ok" : "");
}

document.addEventListener("DOMContentLoaded", init);
