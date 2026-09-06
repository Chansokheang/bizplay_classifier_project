# Agent turn contract — trip plan (출장계획서) & settlement (출장정산서)

This answers the four questions you raised and doubles as the contract a client can code against:

1. `pendingChoices` was `null` on `intent: "DESTINATION_ASK"` — **fixed.**
2. The travel route behaved the same way — **fixed.**
3. "If a separate API has to be called, there must be an agreement about which API for which
   intent" — **the turn now carries that**, in `resources` (our API) and `upstream` (BizPlay API).
4. The full list of `intent` values and the condition under which `pendingChoices` is filled —
   documented below **and served by `GET /agents/contract`**, so it cannot drift from the code.

Everything needed to draw a turn — including its options — is in the response. Where a further call
is genuinely needed (fetching a full list, uploading a file), the response names that endpoint. There
is no call a client has to know about in advance.

---

## 1. The shape of one turn

Every agent endpoint answers with the same envelope — **the turn is in `data`**:

```jsonc
{ "success": true, "message": "Success", "createdAt": "2026-09-06T14:02:40.17",
  "data": { /* everything below */ } }
```

**Fields with no value are omitted from the JSON**, so an absent field is an answer in itself
(no `ui` key = this turn needs no widget; no `pendingChoices` key = nothing to choose from).

| Field | Type | Meaning |
|---|---|---|
| `sessionId` | string (UUID) | The conversation. Send it back on every following turn. |
| `status` | string | Session state (`COLLECTING`, `READY_FOR_REVIEW`, `POSTED` …). |
| `intent` | string | What this turn is. Full list in §6. |
| `reply` | string | The sentence to show the user. Language follows the user's own (KO/EN). |
| `pendingChoices` | array | **The options for this turn's question.** §3. |
| `render` *(inside a choice)* | string | Which widget to draw **the options** with. §4. |
| `ui` | string | A **widget this turn needs** beyond a list of options (calendar, expense form, file upload). §4–5. |
| `action` | string | The endpoint **that widget posts to**. §5. |
| `formFields` | array | Field definitions when `ui: "expense-form"`. §5. |
| `resources` | object | **Our API** endpoints relevant to this turn. §7. |
| `upstream` | object | **BizPlay API** endpoints behind them. §7. |
| `draftJson` | array | The document as built so far (same structure as the save body). |
| `approvalLines` | array | The current approval line (`corporationUserId`, `approvalKindType`, `name`). |
| `missingFields` | array | Required fields still empty. |
| `travelers` / `travelerIds` / `destination` / `destinationCountry` | | Confirmed values, for previews. |

### `intent`, `kind`, `render`, `ui` — four different things

These four are read at different levels, and mixing them up is the easiest mistake to make:

| Field | Level | Answers | How many per turn |
|---|---|---|---|
| `intent` | the **turn** | *Where is the conversation?* (`DESTINATION_ASK`, `CREATE_PLAN` …) | exactly one |
| `kind` | one **question** inside the turn | *What is being asked for?* (`DESTINATION`, `TRANKIND` …) | zero, one, or several |
| `render` | that same question | *How do I draw its options?* (`chips`, `dropdown` …) | one per choice group |
| `ui` | the **turn** | *Do I need a widget beyond a list of options?* (`calendar`, `file-upload` …) | zero or one |

`intent` is a **state**, `kind` is a **question**. They are not two names for the same thing:
one `EXPENSE_SLOT_PENDING` turn asks a slot question (`EXPENSE_SLOT`) *and* offers a way out
(`EXPENSE_ABANDON`) — one intent, two kinds. Conversely the same `kind` appears under different
intents: `PLAN` rides both `PLAN_SEARCH` and `PLAN_PICK_PENDING`.

A practical rule: **branch your rendering on `ui` and `render`; use `intent` for logging, analytics
and any special screen you choose to build.** A client that never looks at `intent` can still drive
both agents to a filed document.

The request is identical for both agents:

```http
POST /api/v1/agent-conversations/bizplay/agents/plan          # trip plan
POST /api/v1/agent-conversations/bizplay/agents/settlement    # settlement
Content-Type: application/json
X-Bizplay-Token: <your Bearer token>

{ "corpNo": "1234567890", "corpUserId": "30447",
  "sessionId": "<omit on the first turn>", "message": "what the user typed, or the chosen sendText" }
```

---

## 2. How to answer — one rule

> **If the user clicked an option, send that option's `sendText`; if they typed, send their text.**
> Same agent endpoint, same `sessionId`.

There is no separate "confirm selection" API. `sendText` is a value to be sent verbatim: sometimes a
machine token (`purpose:2952`), sometimes a human sentence (`"스위스 · 취리히"`). Show `label` to the
user, send `sendText` to the server.

---

## 3. `pendingChoices` — when it is filled, and its shape

### When it is filled

`pendingChoices` is present **whenever the answer to this turn's question comes from a known set**.
That set is either a BizPlay list (purposes, regions, route candidates, plans, expense types,
the staff directory …) or a fixed BizPlay enum (one-way/round-trip, seat class, card type).

**Free-text questions carry no choices** — there is no list to offer. Examples: the destination
detail ("building, floor, place"), the document title, an amount, "is anyone else travelling?".
For those turns the field is omitted and the client draws `reply` plus the text input.

> Note: even on a free-text question, if the user names something outside the allowed set (an
> unregistered city, say), the agent returns to `DESTINATION_ASK` **with** the choices.

### Shape

```jsonc
{
  "kind": "DESTINATION",          // what is being chosen (table below)
  "name": "목적지",                // caption
  "render": "dropdown",           // which widget to use (§4)
  "options": [                    // inline — enough on its own to draw the turn
    { "label": "스위스 · 취리히",   // show this
      "sendText": "스위스 · 취리히", // send this
      "meta": { "id": "981" } }    // optional extras (table columns, ids)
  ],
  "optionsUrl": "/api/v1/agent-conversations/bizplay/agents/plan/destination-options?corpNo=1234567890",
                                  // our API for the FULL list (optional)
  "upstream": [                   // the BizPlay API that list comes from (informational)
    { "method": "GET", "path": "/api/v2/bstr/area/region/{regionType}" }
  ]
}
```

`options` is inline **whenever the list is short enough to send** — which is every question except
a `lookup`. A `lookup` (터미널 415 rows, stations) sends `optionsUrl` and no `options`: query it as
the user types, or let them type the answer, which the agent resolves either way. For every other
question `optionsUrl` is a convenience for "show all / search", and the conversation completes
without ever calling it.

### `kind` values

`kind` names **what the question is about** — it is the key to write your `switch` on if you want a
custom widget for a particular question. `render` (next section) says how to draw it; the two are
independent, and the same `kind` can arrive with a different `render` when the list is longer.

| kind | The question | Usual `render` | What you send back |
|---|---|---|---|
| `PURPOSE` / `SEGMENT` | Trip purpose, then its segment | `chips` | `purpose:2952` (machine token) |
| `DESTINATION` | Country · city | `dropdown` (67 rows) | `"스위스 · 취리히"` |
| `ROUTE` | Travel route: departure · destination · return | `route-picker` | a place name per leg |
| `TRAVELER` | Who is travelling | `lookup` / `chips` | a person's name |
| `APPROVAL_LINE` / `APPROVER` | Approval line | `approval-line` | a person's name (a role may be added: `"김비플 합의"`) |
| `PLAN` / `PLAN_PENDING` | Trip to settle — approved / still awaiting approval | `table` | the option's `sendText` (a plan token) |
| `TRANKIND` | Expense type (교통비, 숙박비 …) — the kinds the plan's form pins, or, when it pins none, the kinds the corporation has a 출장비 규정 for (scoped 국내/해외) | `chips` | `trankind:11719` |
| `CARD_TYPE` | Which card types to search | `chips` | `card-types:PERSONAL,MY_DATA` |
| `RECEIPT` | Receipt to attach | `table` | `receipt:<id>` / `receipts-done` |
| `EVIDENCE_PERIOD` | Period to search | `chips` (with `ui: "calendar"`) | `"2026-09-03 ~ 2026-09-05"` |
| `EXPENSE_SLOT` | One detail of the expense (출발지, 좌석등급 …) | `lookup` | the value, typed or picked |
| `EXPENSE_CONFIRM` / `EXPENSE_ABANDON` | Register this receipt / cancel it | `chips` | `expense-confirm` / `expense-cancel` |
| `MANUAL_EXPENSE_MODE` | How to enter the expense by hand | `chips` | `manual-expense` |
| `STOP` / `SUBMIT` / `DONE` | Stop / file / finished | `chips` | the option's `sendText` |

Two shapes of `sendText` appear above, and both are sent **verbatim**: a machine token
(`purpose:2952`, `expense-confirm`) or a human phrase (`"스위스 · 취리히"`). Never construct one
yourself — copy the option's `sendText`, show the option's `label`.

---

## 4. What to draw: `render` and `ui`

Two different fields carry it, and a client reads them in this order. **Both are omitted when they
do not apply** — the response is `NON_NULL`, so an absent field is an answer, not an oversight.

1. **`ui` on the turn** → this turn needs a widget that is not a list of options (a date picker, an
   expense form, a file picker). Draw it, and post it to `action` when `action` is present. §5.
2. **otherwise `pendingChoices`** → the question is answered by choosing. Draw each group with its
   own `render` (below), showing `label` and sending `sendText`.
3. **otherwise** → `reply` and a text box.

A turn can carry both: `EXPENSE_PREVIEW` has `ui: "expense-preview"` *and* an `EXPENSE_CONFIRM`
chip row. A turn can carry neither: `CREATE_PLAN` is a statement, not a question. And one turn can
carry **two questions of different kinds**, each with its own `render` — draw them as two rows:

```jsonc
// one turn: "출발지는 어디셨나요?"  (intent EXPENSE_SLOT_PENDING, no ui)
"pendingChoices": [
  { "kind": "EXPENSE_SLOT", "name": "출발지", "render": "lookup",
    "source": "terminals:BUS",
    "optionsUrl": ".../agents/settlement/terminals?corpNo=1234567890&vehicleType=BUS",
    "upstream": [ { "method": "GET", "path": "/api/v2/receipt/etc-card/terminal" }, … ] },
    // no "options": too many to inline — query optionsUrl, or let the user type
  { "kind": "EXPENSE_ABANDON", "name": "또는", "render": "chips",
    "options": [ { "label": "이 영수증 취소", "sendText": "expense-cancel" } ] }
]
```

```jsonc
// PURPOSE_SELECTION — a list to choose from, no widget beyond it
{ "intent": "PURPOSE_SELECTION",
  "pendingChoices": [ { "kind": "PURPOSE", "render": "chips", "options": [ … ] } ] }
  // no "ui" key at all

// EXPENSE_IMAGE_REQUIRED — nothing to choose; a file has to be picked
{ "intent": "EXPENSE_IMAGE_REQUIRED",
  "ui": "file-upload",
  "action": "…/agents/settlement/{sessionId}/manual-expense/attach?corpNo=…" }
  // no "pendingChoices" key at all
```

### `render` — how to draw the options

So that a 60-item list never becomes 60 buttons, the server states the widget shape.

| `render` | Widget | Where it appears |
|---|---|---|
| `chips` | A row of small pill buttons — one per option, tap to answer | Ordinary choices, 24 options or fewer |
| `dropdown` | Select box | Lists longer than 24 (e.g. 67 destinations) |
| `table` | Table (each option's `meta` supplies the columns) | Plan list, receipt list |
| `route-picker` | Departure / destination / return picker | `ROUTE` |
| `approval-line` | Approval-line card with an "add person" control | `APPROVAL_LINE` |
| `lookup` | Search input backed by `optionsUrl` | Terminals, stations — very large lists |

Drawing everything as `chips` still works — the answer is always just `sendText` posted back, so
`render` is a recommendation about shape, not a requirement. It exists so a client doesn't have to
decide for itself and end up with 67 buttons on one screen.

---

## 5. `ui` / `action` / `formFields` — turns that need a widget

Some turns cannot be answered by text and buttons alone (a file upload, a date range, an expense
form). Those carry `ui` (what to draw) and `action` (where it posts).

| `ui` | Meaning | `action` |
|---|---|---|
| `calendar` | Pick a date range | — (send the picked range as the next `message`, e.g. `"2026-09-03 ~ 2026-09-05"`) |
| `expense-form` | Expense entry form (see `formFields`) | `POST …/agents/settlement/{sessionId}/manual-expense/create` |
| `expense-preview` | Receipt preview before registering | — |
| `file-upload` | Attach the receipt image/PDF | `POST …/agents/settlement/{sessionId}/manual-expense/attach` (multipart) |
| `receipt-table` | Receipt browser | — |
| `settlement-preview` | Settlement summary | — |
| `confirm-submit` | Confirm filing | `POST …/agents/plan/{sessionId}/create` |

`ui: "calendar"` is not tied to one intent: it appears on **any turn whose open question is a date
range**, including the trip-period question of the plan (`FIELD_COMPLETION`) and a settlement
`PLAN_SEARCH` that found nothing and is asking for another period. So branch on `ui`, not on the
intent, when deciding to open a date picker. The range goes back as an ordinary message — ISO
(`"2026-09-03 ~ 2026-09-05"`) or natural language, both are read.

`formFields` describes each input of the `expense-form`, so the form can be generated rather than
hard-coded. A transport expense currently returns 15 fields.

| Key of a field | Meaning |
|---|---|
| `key` | The field name to send back in the create call (`mestName`, `approvalAmount` …). |
| `label` | The caption, in the conversation's language. |
| `type` | `text`, `number`, `date`, `time`, `select`, `file`. |
| `required` | `true` blocks the call; `false` may be left empty. |
| `options` | Inline `{label, value}` list — present on a `select` with a fixed enum. |
| `optionsUrl` | Where the full list lives, when it is too long to inline (통화 179 rows, 터미널 415). |
| `source` / `upstream` | Which capability that list belongs to, and the BizPlay endpoint behind it. |

```jsonc
{ "key": "mestName",     "label": "가맹점", "type": "text",   "required": true }
{ "key": "approvalDate", "label": "일자",   "type": "date",   "required": true }
{ "key": "approvalAmount", "label": "금액", "type": "number", "required": true }
{ "key": "vehicleType",  "label": "교통수단", "type": "select", "required": true,
  "options": [ { "label": "항공", "value": "AIR" }, … 13 ] }              // fixed enum, inline
{ "key": "currencyCode", "label": "통화",  "type": "select", "required": true,
  "source": "currencies",
  "optionsUrl": ".../agents/settlement/currencies?corpNo=…",
  "upstream": [ { "method": "GET", "path": "/api/v2/currency-code/combo" } ] }   // 179 rows
{ "key": "image",        "label": "영수증", "type": "file",   "required": false }
```

**The form is optional.** The same content typed as a sentence — `"고속버스로 센트럴시티에서 강릉
9월 4일 25000원"` — is handled identically, and the agent asks only for what is still missing. The
widget path and the conversational path are kept aligned so both reach the same result.

---

## 6. Every `intent`

`intent` says **what this turn is**. Use it for branching, but **fall back to rendering `reply` +
`pendingChoices` for any value you don't recognise** — the set grows as features are added.

### Trip plan (`POST /agents/plan`)

| intent | Meaning | Choices | Widget |
|---|---|---|---|
| `PURPOSE_SELECTION` | Choosing the trip purpose | `PURPOSE` (chips) | |
| `SEGMENT_SELECTION` | Choosing the purpose's segment | `SEGMENT` (chips) | |
| `FORM_LOAD` | The form was loaded | | |
| `DESTINATION_ASK` | Asking country and city | `DESTINATION` (dropdown) | |
| `TRAVELER_PICK` | Confirming which person the traveller is | `TRAVELER` | |
| `TRAVELER_MORE_ASK` | Asking whether anyone else travels | — (free text) | |
| `TRAVELER_REMOVED` | A traveller was taken off the plan | | |
| `ROUTE_ASK` | Asking the travel route | `ROUTE` (route-picker) | |
| `FIELD_COMPLETION` | Asking for a remaining field | — (free text) | `calendar` when the field asked for is the trip period |
| `FIELD_EDITED` | A value was changed | | |
| `APPROVAL_LINE_ASK` | Asking who approves the plan | `APPROVAL_LINE` (approval-line) | |
| `SUBMIT_REQUESTED` | The user asked to file it | | `confirm-submit` |
| `CREATE_PLAN` | **Filed in BizPlay** (reply carries the document number) | | |
| `CREATE_PLAN_MANUAL` | Filed from a document the client supplied | | |
| `DATA_QUERY` / `DRAFT_QUERY` | Answered a question about the data / the draft | | |
| `GUARDRAIL_BLOCKED` | Off-topic request; nothing changed | | |

### Settlement (`POST /agents/settlement`)

| intent | Meaning | Choices | Widget |
|---|---|---|---|
| `AWAIT_PERIOD` / `AWAIT_PLAN_PERIOD` | Asking which period to search | `EVIDENCE_PERIOD` (when candidates exist) | `calendar` |
| `PLAN_SEARCH` | Listing settleable trips (with no period given, a default period is searched) | `PLAN` (table) | `calendar` when nothing matched and it is asking for another period |
| `PLAN_PICK_PENDING` | Waiting for a trip to be picked | `PLAN` | |
| `PLAN_IMPORT` | A plan was imported into a settlement | `TRANKIND` | |
| `PENDING_PLANS` | Plans still awaiting approval (not settleable) | `PLAN_PENDING` | |
| `TRANKIND_PENDING` / `TRANKIND_PICKED` | Asking / confirming the expense type | `TRANKIND`, `CARD_TYPE` | |
| `CARD_TYPES_PENDING` | Asking which card types to search | `CARD_TYPE` | |
| `EVIDENCE_PERIOD_PENDING` | Asking the evidence period | | `calendar` |
| `EVIDENCE_LOAD` | Listing unattached receipts | `RECEIPT` (table) | |
| `EVIDENCE_PICK_PENDING` | Waiting for a receipt to be picked | `RECEIPT` | |
| `EVIDENCE_ATTACH` | A receipt was attached | | |
| `MANUAL_EXPENSE_CHOOSE` | How to enter the expense by hand | `MANUAL_EXPENSE_MODE` | |
| `MANUAL_EXPENSE_PROMPT` / `_FULL` | Asking for the expense | | `expense-form` |
| `EXPENSE_SLOT_PENDING` | Asking one detail (departure, seat class …) | `EXPENSE_SLOT` (lookup) | |
| `EXPENSE_PREVIEW` | Confirm before registering | `EXPENSE_CONFIRM` | `expense-preview` |
| `EXPENSE_IMAGE_REQUIRED` | The receipt image is still missing | | `file-upload` |
| `EXPENSE_IMAGE_SUBMIT` | Asked to upload the file already chosen | | |
| `MANUAL_EXPENSE_CREATED` / `MANUAL_EXPENSE_ADDED` | The expense was registered | | |
| `EXPENSE_UPDATED` / `EXPENSE_CANCELLED` | Expense corrected / abandoned | | |
| `TITLE_SET` | Document title set | | |
| `RECEIPT_BROWSE` / `RECEIPT_BROWSE_NOT_ISSUED` | Receipt browser | `RECEIPT` | `receipt-table` |
| `SETTLEMENT_READY` | Ready to file | `SUBMIT` | `settlement-preview` |
| `APPROVAL_LINE_ASK` | **Confirming the approval line before filing** | `APPROVAL_LINE` | |
| `APPROVER_PICKED` | An approver was set | | |
| `CREATE_SETTLEMENT` | **Filed in BizPlay** (reply carries the document number) | | |
| `SETTLEMENT_SAVED` / `SESSION` / `DRAFT_QUERY` | Saved on our side / session read back / question answered | | |
| `STOP_PICK_PENDING` | Asking whether to stop | `STOP` | |
| `GUARDRAIL_BLOCKED` | Off-topic request; nothing changed | | |

> On `CREATE_PLAN` / `CREATE_SETTLEMENT` the document has been filed in BizPlay. There is nothing
> left to call, so `resources` and `upstream` are absent on those turns.

---

## 7. `resources` / `upstream` — which API for which intent

A turn carries **only the endpoints that turn is about**.

* **`resources`** — our (agent) API: where to fetch a fuller list, or where a widget posts.
* **`upstream`** — the **BizPlay API** the data comes from. Since you call BizPlay directly with
  your own token, each turn also names the BizPlay endpoint behind its question.

Both use the same keys (`destinationOptions`, `plans`, `terminals`, `registerExpense` …) and the same
shape: `{ "key": [ { "method": "GET", "path": "…" } ] }`. `{braces}` in a path are yours to fill.

| intent | `resources` | `upstream` (BizPlay) |
|---|---|---|
| `PURPOSE_SELECTION` | `GET /purposes` | `GET /api/v2/bstrPurpose/corporation-user/{corpUserId}/{paperKindType}`, `GET /api/v2/paper/purpose/{purposeId}?segmentId={segmentId}` |
| `DESTINATION_ASK` | `GET /agents/plan/destination-options` | `GET /api/v2/bstr/area/region/{regionType}`, `…/city/{countryCode}`, `…/used/{regionType}`, `…/used/CITY/{countryCode}` |
| `ROUTE_ASK` | `GET /agents/plan/route-options` | `GET /api/v2/bstr/destination/active/list` |
| `APPROVAL_LINE_ASK` | `GET /corporation-users` | `GET /api/v2/popup/user/all/{corporationId}` |
| `PLAN_SEARCH` | `GET /plans` | `GET /api/v2/approval/seah/bstr/plan/list?travelerId=…`, `GET /api/v2/approval/bstr/plan/list?…` |
| `MANUAL_EXPENSE_PROMPT_FULL` | `POST …/manual-expense/create` | `POST /api/v2/receipt/etc-card`, `POST /api/v2/filebox/upload`, `GET /api/v2/receipt/issued/bulk/{ids}`, `POST /api/v2/bstr/policy/limit` |
| `EXPENSE_IMAGE_REQUIRED` | `POST …/manual-expense/attach` | `POST /api/v2/filebox/upload`, `PATCH /api/v2/receipt/image/{receiptId}` |
| `SUBMIT_REQUESTED` | `POST …/agents/plan/{sessionId}/create` | `POST /api/v2/approval/{productCode}/bstr/plan/draft` |
| `CREATE_PLAN` / `CREATE_SETTLEMENT` | — | — |

> A note on the paper lookup: `/api/v2/paper/purpose/{purposeId}?segmentId={segmentId}` is the path
> we advertise, because it answers for every purpose. The typed variant
> `/api/v2/paper/purpose/{bstrType}/{purposeId}?segmentId={segmentId}` also works — and additionally
> **filters by trip type**, which matters: a settlement form registered as OVERSEA is invisible to a
> DOMESTIC trip on that path. Called without the `segmentId` of a purpose that has segments, it
> answers 400 `COMM_ERROR`.

### Reading the whole surface at once

```http
GET /api/v1/agent-conversations/bizplay/agents/contract?corpNo=1234567890
```

Returns, for both agents: the complete `resources` and `upstream` maps, the **`intents` catalogue**
(each entry: `intent`, `means`, the `choices` kinds that ride with it, its `ui`, and the
`resourceKeys` it implies), the intent → widget map, and the list of `render` shapes. The catalogue
is generated from the same maps the live turns use, so it cannot disagree with a response.

```jsonc
// agents.plan.intents[3]
{ "intent": "DESTINATION_ASK", "means": "Asking country and city",
  "choices": ["DESTINATION"], "ui": null, "resourceKeys": ["destinationOptions"] }

// agents.settlement.intents[0]
{ "intent": "AWAIT_PERIOD", "means": "Asking the period to search",
  "choices": ["EVIDENCE_PERIOD"], "ui": "calendar", "resourceKeys": [] }
```

An entry with `"choices": []` is a free-text question — that turn carries no `pendingChoices`.

---

## 8. Real responses

### 8.1 `DESTINATION_ASK` (after the fix)

```jsonc
{
  "sessionId": "78da818e-ded2-4337-bfac-2be381ad14d3",
  "status": "COLLECTING",
  "intent": "DESTINATION_ASK",
  "reply": "좋아요 — \"해외출장 · 장기\" 출장 계획을 시작할게요. 어디로 가시나요 — 어느 나라, 어느 도시인가요? (허용 국가: 스위스, 일본)",
  "pendingChoices": [
    {
      "kind": "DESTINATION",
      "name": "목적지",
      "render": "dropdown",
      "optionsUrl": "/api/v1/agent-conversations/bizplay/agents/plan/destination-options?corpNo=1234567890",
      "upstream": [
        { "method": "GET", "path": "/api/v2/bstr/area/region/{regionType}" },
        { "method": "GET", "path": "/api/v2/bstr/area/region/city/{countryCode}" },
        { "method": "GET", "path": "/api/v2/bstr/area/region/used/{regionType}" },
        { "method": "GET", "path": "/api/v2/bstr/area/region/used/CITY/{countryCode}" }
      ],
      "options": [
        { "label": "스위스 · 뉴샤텔",       "sendText": "스위스 · 뉴샤텔" },
        { "label": "스위스 · 다보스 도르프", "sendText": "스위스 · 다보스 도르프" },
        { "label": "스위스 · 로잔",         "sendText": "스위스 · 로잔" }
        /* … 67 in total */
      ]
    }
  ],
  "resources": {
    "destinationOptions": [
      { "method": "GET", "path": "/api/v1/agent-conversations/bizplay/agents/plan/destination-options?corpNo=1234567890" }
    ]
  },
  "upstream": { "destinationOptions": [ /* same as the choice's upstream */ ] }
}
```

### 8.2 `ROUTE_ASK` (after the fix)

```jsonc
{
  "intent": "ROUTE_ASK",
  "reply": "이동경로가 어떻게 되나요? 아래에서 출발지·목적지·복귀지를 고르시거나 말씀해 주세요.",
  "pendingChoices": [
    {
      "kind": "ROUTE",
      "name": "이동경로",
      "render": "route-picker",
      "optionsUrl": "/api/v1/agent-conversations/bizplay/agents/plan/route-options?corpNo=1234567890",
      "upstream": [ { "method": "GET", "path": "/api/v2/bstr/destination/active/list" } ],
      "options": [
        { "label": "비즈플레이", "sendText": "비즈플레이",
          "meta": { "id": "1652", "address": "서울 영등포구 영신로 220", "sido": "서울" } },
        { "label": "티엑스알로보틱스(본사)", "sendText": "티엑스알로보틱스(본사)",
          "meta": { "id": "1653", "address": "서울 강서구 마곡중앙8로1길 81", "sido": "서울" } }
        /* … 11 in total */
      ]
    }
  ],
  "resources": { "routeOptions": [ { "method": "GET", "path": "/api/v1/agent-conversations/bizplay/agents/plan/route-options?corpNo=1234567890" } ] },
  "upstream":  { "routeOptions": [ { "method": "GET", "path": "/api/v2/bstr/destination/active/list" } ] }
}
```

### 8.3 A free-text question — no choices

```jsonc
{
  "intent": "FIELD_COMPLETION",
  "reply": "선택 사항 하나만요 — 출장지 상세(건물·층·장소, 10자 이내)가 있다면 알려주세요. 없으면 없다고 말씀해 주세요."
  /* no pendingChoices: the answer does not come from a list */
}
```

---

## 9. Notes for implementers

1. **Session** — first turn without `sessionId`; reuse the one you get back. Once the document is
   filed (`CREATE_PLAN` / `CREATE_SETTLEMENT`) that session is finished; a further message rolls
   over to a new one automatically.
2. **Token** — BizPlay is called with the Bearer token in `X-Bizplay-Token`. Without the header the
   server's own (development) token is used.
3. **Language** — `reply` follows the language the user writes in (Korean/English). There is no
   language parameter.
4. **Approval line** — before filing, the settlement confirms its 결재선 once (`APPROVAL_LINE_ASK`),
   the same way BizPlay's own screen does. The plan's approver is shown as the starting point; it
   can be changed in words ("김비플 님도 합의로 넣어줘") or by picking from the choices. Nothing is
   filed until that turn is answered.
5. **Unknown values** — `intent`, `kind`, `render` and `ui` are open sets. Render an unknown value
   from `reply` + `pendingChoices` (as chips) and the conversation keeps working.
6. **`draftJson`** — the same structure as the BizPlay save body, so it can be used directly for
   previews.
