# Trip-Plan Agent API

The conversational agent that builds and files BizPlay 출장계획서 documents. This API is the
product surface: every LLM call, provider call (BizPlay cloud, TMap), and lookup runs inside this
server — a client needs nothing but these endpoints. The bundled web demo is only a reference
client of this same API.

- **Base path:** `/api/v1/agent-conversations/bizplay` (deployed: port `9008`)
- **Auth:** every request carries the header `X-Bizplay-Token: <BizPlay JWT>`. The server forwards
  it as the Bearer token for all provider calls; who the user is (corpUserId, corporation) is read
  from this token.
- **Envelope:** non-error responses wrap the payload as `{ "success": true, "message": "Success",
  "data": { … } }` — read your result from `data`. Refusals and validation failures return
  HTTP 400 problem-details with the human-readable reason in `detail`.
- **Live schema:** `GET /v3/api-docs` (OpenAPI) and `/swagger-ui/index.html`.
- **Latency:** a chat turn runs several LLM calls; expect 10–60 s. Do not set aggressive client
  timeouts.
- **Language:** the agent replies in the language of the user's message (Korean/English).

---

## 1. Conversation model

State lives server-side in a **session**. One session = one plan being drafted.

| status | meaning |
|---|---|
| `COLLECTING` | The agent still needs something; `reply` contains exactly ONE question. |
| `READY_FOR_REVIEW` | Form complete and enrichable — the only remaining step is filing with an approval line. |
| `POSTED` | Filed to BizPlay. The session is immutable; the next message without a `sessionId` (or to a POSTED session) starts a fresh conversation. |

The agent asks **one question per turn** and binds the next message to that question
deterministically (destination → region master, transport → the transport enum, origin → place
extraction, 출장지 상세 → one-time optional ask). An answer about something else is never
force-bound: the message is interpreted normally (edits apply, questions get answered) and the
pending question re-asks afterward.

The draft (`draftJson`) is **exactly the BizPlay save body** — what you see in the response is
byte-for-byte what `create` will POST to the provider.

## 2. Chat turn — `POST /agents/plan`

Drive the whole flow with this one endpoint: send free text, read the reply, repeat.

**Request**

```json
{
  "corpNo": "1234567890",          // required — corp scope
  "corpUserId": "30447",           // required — drafting user (must match the token)
  "sessionId": "uuid",             // omit on the first turn; reuse afterwards
  "message": "9월 21일부터 22일까지 도쿄로 해외출장 일반 다녀올게. 출장자는 김충북",
  "travelerCorpUserIds": [30447],  // optional — pre-resolved travellers
  "fileIds": ["..."],              // optional — uploaded spreadsheets/PDFs to extract
  "destinationDetail": "본사 3층"   // optional — 출장지 상세 if your UI already asked ("" = asked & skipped)
}
```

**Response payload**

| field | meaning |
|---|---|
| `sessionId` | carry into every following turn |
| `status` / `intent` | see the tables in §1 and §6 |
| `reply` | the agent's message — show it verbatim |
| `missingFields` | required form fields still empty (labels from the corp's own form) |
| `pendingChoices` | option groups the user must pick from (e.g. ambiguous traveller names) |
| `destination`, `destinationCountry`, `destinationDetail`, `origin` | resolved place slots. `destinationCountry` is display-only (the save body stores the city's region id). `destinationDetail`: `null` = not asked yet, `""` = asked and skipped |
| `travelers` / `travelerIds` | resolved traveller names / corporationUserIds |
| `draftJson` | the exact provider save body being built |
| `subAgents` | which sub-agents ran this turn (diagnostics) |

**Typical flow**

1. First message → the agent picks/asks the trip type, loads the corp's real form, extracts every
   field the message contains.
2. `COLLECTING` turns → answer the one question asked (dates, travellers, route, …).
3. Near the end the agent verifies the lookup-driven requirements: the destination must resolve in
   the paper's region lists (`intent: DESTINATION_ASK` when it doesn't — offer your country/city
   picker or just let the user type), the transport type when routes require one, and one optional
   출장지 상세 question. A proceed-style answer at any OPTIONAL ask ("create the plan",
   "그냥 진행해") skips all remaining optional questions in one turn; required asks (the
   region) never skip.
4. `READY_FOR_REVIEW` → call `create`.
5. A turn that changes nothing and clearly says "file it" returns `intent: SUBMIT_REQUESTED` —
   run `create` in response.

## 3. File the plan — `POST /agents/plan/{sessionId}/create?corpNo=...`

Body (optional): the approval line, appended after the drafter's automatic DRAFT line.

```json
{ "approvalLines": [
    { "approvalKindType": "DRAFT",    "approvalOrder": 0, "corporationUserId": 30447 },
    { "approvalKindType": "APPROVAL", "approvalOrder": 1, "corporationUserId": 30192 } ] }
```

`approvalKindType`: `DRAFT` | `APPROVAL` | `AGREE` | `ACCEPT` | `REFERENCE`.

On success the provider's confirmation ("작성되었습니다.") comes back in `reply` and the session
becomes `POSTED`. Before saving, the server enriches the draft with everything the provider's own
screen would add: the region `selectionId` (국가/도시), `selectionMemo` (출장지 상세, ≤10 chars),
period times on time-enabled forms, and the `bstrRoutes` legs. Each leg carries the ids,
addresses, 시도, admin codes and coordinates of the registered destinations the traveller
named, plus the TMap driving distance; when no route was named, an origin → destination →
origin round trip is written instead. With several travellers the draft holds ONE document
per traveller and every one of them is enriched — each carrying that person's own route
when they were given one, and the trip's shared route otherwise.
An unresolvable required region refuses with HTTP 400 instead of saving a broken document.

## 4. One-shot save — `POST /plans`

Non-conversational path for a client that collected the fields itself. Same writers, same
enrichment, same validation as the chat create.

```json
{
  "corpNo": "1234567890", "corpUserId": "30447",
  "purposeId": 2952, "segmentId": 1354,
  "title": "도쿄 파트너 미팅", "content": "…",
  "startDate": "2026-09-21", "endDate": "2026-09-22",
  "destination": "도쿄", "destinationDetail": "본사 3층",
  "travelerCorpUserIds": [30447],
  "itemValues": { "item:11505": {"choice": "옵션명"} },
  "approvalLines": [ … ],
  "agentSessionId": "uuid"   // optional: link a chat session — its transport/origin/detail merge in, and the session becomes POSTED
}
```

## 5. Supporting lookups

| endpoint | purpose |
|---|---|
| `GET /whoami` | who the token belongs to (sanity check) |
| `GET /purposes?corpNo&corpUserId` | trip types: `purposeId`/`segmentId` pairs with labels |
| `GET /corporation-users?corpNo` | roster for traveller/approver pickers |
| `GET /form?corpNo&corpUserId&purposeId&segmentId` | the raw dynamic form definition |
| `GET /agents/plan/destination-options?corpNo&purpose&segment` (or `purposeId`/`segmentId`, or `citiesOf=<countryCode>`) | what the form's region rules actually allow: `source` = `policy` (flat allowed list), `sido`, `countries` (then cascade with `citiesOf`), or `any` |
| `POST /agents/plan/destination-pick` `{message, options[], context}` | LLM semantic pick when typed text matches no option — returns the 0-based `index` or null |
| `GET /plans/by-status?corpNo&travelerId&status&startDate&endDate` | the user's plans (`APPROVED` ones are the settleable set) |
| `GET /agents/plan/route-options?corpNo` | the corporation's registered travel destinations (출장지) — `{id, name, address, sido}`. A route leg's `departureId`/`arrivalId`, addresses, admin codes and coordinates all come from here; offer them as a picker, or let the user type the route in words |
| `POST /agents/plan/{sessionId}/note` `{user, assistant}` | record a CLIENT-handled turn into the session transcript (no pipeline runs) — keeps the agent's history complete when your UI answers something locally, e.g. approval-line picks |
| `POST /agents/plan/approval-intent` `{message, pendingRolePerson, awaitingSaveConfirm, lines, people[]}` | LLM judgment of what the user MEANS at a client-side approval-line step → `{action: pick_person\|assign_role\|no_more\|save_now\|not_yet\|remove_person\|other, person?, role?}` — use it instead of word lists in your own UI |

## 5a. Self-correction (alignment gate)

Every turn that changes the draft is checked by a verifier sub-agent before the reply is sent:
does what the agent DID match what the user asked? On a mismatch the draft is rewound to exactly
where the turn began and the turn is run once more, told what was missed — then that second
answer stands. One correction, never a loop; a verifier that cannot answer never delays a reply.
Verdicts appear in the server log as `[VERIFY] gate` lines.

Responses also carry `uiRefresh` — the parts this turn changed (`travellers`, `route`, or `all`)
— so a client redraws exactly those, instead of inferring it from the reply text.

## 6. Intents a client may want to react to

| intent | react by |
|---|---|
| `PURPOSE_SELECTION` / `SEGMENT_SELECTION` | offering the `/purposes` list as buttons (optional — typing works) |
| `FORM_LOAD`, `FIELD_COMPLETION` | just showing `reply` |
| `TRAVELER_PICK` | rendering `pendingChoices` |
| `DESTINATION_ASK` | offering `destination-options` as a picker (optional — typing works; the server binds the answer) |
| `ROUTE_ASK` | offering `route-options` as a picker for 이동경로(출장) (optional — typing the route in words works; the server resolves it against the same master). Asked once for the trip; naming a traveller ("김도하는 …") gives that person their own legs |
| `SUBMIT_REQUESTED` | calling `create` |
| `GUARDRAIL_BLOCKED` | showing `reply`; the turn was off-topic/unsafe |
| `DRAFT_QUERY` / `DATA_QUERY` | showing `reply` (the agent answered a question about the draft/data) |

## 7. Worked example (curl)

```bash
BASE="http://<server>:9008/api/v1/agent-conversations/bizplay"
TOKEN="<BizPlay JWT>"

# turn 1
curl -s -X POST "$BASE/agents/plan" -H "Content-Type: application/json" \
  -H "X-Bizplay-Token: $TOKEN" \
  -d '{"corpNo":"1234567890","corpUserId":"30447","message":"9월 21일부터 22일까지 도쿄로 해외출장 일반 다녀올게. 출장자는 김충북"}'
# → status COLLECTING, reply asks the transport

# turn 2 — answer (binding maps 신칸센 → PUBLIC_TRAIN)
curl -s -X POST "$BASE/agents/plan" -H "Content-Type: application/json" \
  -H "X-Bizplay-Token: $TOKEN" \
  -d '{"corpNo":"1234567890","corpUserId":"30447","sessionId":"<SID>","message":"신칸센 타고 갈게"}'
# → reply asks the optional 출장지 상세

# turn 3 — optional detail (or "없어" to skip)
curl -s -X POST "$BASE/agents/plan" -H "Content-Type: application/json" \
  -H "X-Bizplay-Token: $TOKEN" \
  -d '{"corpNo":"1234567890","corpUserId":"30447","sessionId":"<SID>","message":"본사 3층이야"}'
# → status READY_FOR_REVIEW

# file it
curl -s -X POST "$BASE/agents/plan/<SID>/create?corpNo=1234567890" \
  -H "Content-Type: application/json" -H "X-Bizplay-Token: $TOKEN" \
  -d '{"approvalLines":[{"approvalKindType":"DRAFT","approvalOrder":0,"corporationUserId":30447},{"approvalKindType":"APPROVAL","approvalOrder":1,"corporationUserId":30192}]}'
# → "작성되었습니다." — the plan is in BizPlay with region, routes, and memo filled
```
