# 에이전트 턴 응답 규약 (출장계획서 / 출장정산서)

문의해 주신 네 가지에 대한 답변이자, 클라이언트가 코딩 대상으로 삼을 수 있는 규약입니다.

1. `intent: "DESTINATION_ASK"` 턴에서 `pendingChoices`가 `null`로 내려오던 문제 — **수정 완료.**
2. 이동경로도 동일하게 `null`이던 문제 — **수정 완료.**
3. "별도 API를 호출해야 한다면, 어떤 intent에서 어떤 API를 호출하는지에 대한 약속" —
   **응답 자체에 담았습니다.** `resources`(저희 API)와 `upstream`(BizPlay API) 필드입니다.
4. `intent` 전체 목록과 `pendingChoices`가 채워지는 조건 — 아래 문서에 정리했고,
   **`GET /agents/contract`가 같은 내용을 내려줍니다**(코드와 어긋날 수 없도록 코드에서 생성).

한 턴을 화면에 그리는 데 필요한 값은 선택지까지 포함해 모두 응답 안에 있습니다. 추가 호출이 실제로
필요한 경우(전체 목록 조회, 파일 업로드)에는 그 엔드포인트를 응답이 함께 알려 줍니다 — 클라이언트가
미리 알고 있어야 하는 호출은 없습니다.

---

## 1. 한 턴의 응답 구조

모든 에이전트 엔드포인트는 동일한 봉투로 응답하며, **턴의 내용은 `data` 안에** 들어갑니다.

```jsonc
{ "success": true, "message": "Success", "createdAt": "2026-09-06T14:02:40.17",
  "data": { /* 아래 필드 전부 */ } }
```

**값이 없는 필드는 JSON에서 생략됩니다.** 즉 필드가 없다는 것 자체가 답입니다
(`ui` 키가 없으면 이 턴에는 위젯이 필요 없고, `pendingChoices` 키가 없으면 고를 것이 없습니다).

| 필드 | 타입 | 의미 |
|---|---|---|
| `sessionId` | string (UUID) | 대화 세션. 이후 모든 턴에 그대로 넣어 주세요. |
| `status` | string | 세션 상태 (`COLLECTING`, `READY_FOR_REVIEW`, `POSTED` …). |
| `intent` | string | 이 턴이 무엇인지. 전체 목록은 6장. |
| `reply` | string | 사용자에게 보여줄 문장. 대화 언어(한/영)는 사용자의 입력을 따릅니다. |
| `pendingChoices` | array | **이 턴의 질문에 대한 선택지.** 3장. |
| `render` *(선택지 안)* | string | **선택지**를 어떤 위젯으로 그릴지. 4장. |
| `ui` | string | 선택지 목록 외에 **이 턴이 요구하는 위젯**(달력, 경비 입력폼, 파일 첨부). 4–5장. |
| `action` | string | 그 위젯이 **POST할 엔드포인트**. 5장. |
| `formFields` | array | `ui: "expense-form"`일 때 폼의 입력 항목 정의. 5장. |
| `resources` | object | 이 턴과 관련된 **저희 API** 목록. 7장. |
| `upstream` | object | 그 뒤에 있는 **BizPlay API** 목록. 7장. |
| `draftJson` | array | 현재까지 작성된 문서(저장 body와 동일 구조). |
| `approvalLines` | array | 현재 결재선 (`corporationUserId`, `approvalKindType`, `name`). |
| `missingFields` | array | 아직 비어 있는 필수 항목. |
| `travelers` / `travelerIds` / `destination` / `destinationCountry` | | 확정된 값 요약(미리보기용). |

### `intent` · `kind` · `render` · `ui` — 서로 다른 네 가지

이 네 가지는 각각 다른 단위에 붙습니다. 혼동하기 가장 쉬운 부분이므로 먼저 정리합니다.

| 필드 | 단위 | 답하는 질문 | 한 턴에 몇 개 |
|---|---|---|---|
| `intent` | **턴** | *대화가 지금 어느 지점인가* (`DESTINATION_ASK`, `CREATE_PLAN` …) | 정확히 1개 |
| `kind` | 턴 안의 **질문 하나** | *무엇을 묻고 있는가* (`DESTINATION`, `TRANKIND` …) | 0개 · 1개 · 여러 개 |
| `render` | 그 질문 | *그 선택지를 어떻게 그릴까* (`chips`, `dropdown` …) | 선택지 그룹당 1개 |
| `ui` | **턴** | *선택지 목록 외의 위젯이 필요한가* (`calendar`, `file-upload` …) | 0개 또는 1개 |

`intent`는 **상태**이고 `kind`는 **질문**입니다. 같은 것의 다른 이름이 아닙니다. 예를 들어
`EXPENSE_SLOT_PENDING` 턴 하나가 상세 항목을 묻는 질문(`EXPENSE_SLOT`)과 빠져나갈 방법
(`EXPENSE_ABANDON`)을 동시에 내려줍니다 — intent 1개, kind 2개. 반대로 같은 `kind`가 서로 다른
intent에 실리기도 합니다: `PLAN`은 `PLAN_SEARCH`에도 `PLAN_PICK_PENDING`에도 붙습니다.

실무 규칙: **화면 렌더링은 `ui`와 `render`로 분기하고, `intent`는 로그·통계·직접 만들고 싶은 특별
화면에만 쓰세요.** `intent`를 전혀 보지 않는 클라이언트도 두 에이전트를 끝까지 진행시켜 문서를
기안할 수 있습니다.

요청 형식은 두 에이전트가 동일합니다.

```http
POST /api/v1/agent-conversations/bizplay/agents/plan          # 출장계획서
POST /api/v1/agent-conversations/bizplay/agents/settlement    # 출장정산서
Content-Type: application/json
X-Bizplay-Token: <귀사 Bearer 토큰>

{ "corpNo": "1234567890", "corpUserId": "30447",
  "sessionId": "<첫 턴은 생략>", "message": "사용자가 입력한 문장 또는 선택한 sendText" }
```

---

## 2. 답을 보내는 방법 — 규칙은 하나

> **선택지를 클릭했으면 그 옵션의 `sendText`를, 직접 입력했으면 사용자의 문장을 그대로**
> 같은 에이전트 엔드포인트에 같은 `sessionId`로 POST합니다.

별도의 "선택 확정" API는 없습니다. `sendText`는 그대로 보내면 되는 값이며, `purpose:2952` 같은
기계용 토큰일 때도 있고 `"스위스 · 취리히"` 같은 사람의 문장일 때도 있습니다. 화면에는 `label`을,
서버에는 `sendText`를 보내시면 됩니다.

---

## 3. `pendingChoices` — 채워지는 조건과 구조

### 채워지는 조건

`pendingChoices`는 **"이번 턴의 질문에 대한 답이 이미 정해진 집합에서 나오는 경우"** 채워집니다.
그 집합은 BizPlay 목록(출장목적, 지역, 이동경로 후보, 계획서 목록, 경비항목, 임직원 명단 …)이거나,
BizPlay가 정한 고정 enum(편도/왕복, 좌석등급, 카드 종류)입니다.

반대로 **자유 입력 질문에는 선택지가 없습니다** — 답이 목록에 없기 때문입니다.
예: 출장지 상세("건물·층·장소"), 문서 제목, 금액, "더 가는 분 있나요?" 같은 질문.
이 경우 해당 필드는 응답에서 생략되며, 화면은 `reply`와 입력창만 그리면 됩니다.

> 참고: 자유 입력 질문이라도 사용자가 허용 목록 밖의 값을 말하면(예: 등록되지 않은 도시) 에이전트가
> 다시 `DESTINATION_ASK`로 돌아와 선택지를 **함께** 내려줍니다.

### 구조

```jsonc
{
  "kind": "DESTINATION",          // 무엇을 고르는 질문인가 (아래 표)
  "name": "목적지",                // 화면 캡션
  "render": "dropdown",           // 어떤 위젯으로 그릴지 (4장)
  "options": [                    // 인라인 선택지 — 이것만으로 화면을 그릴 수 있습니다
    { "label": "스위스 · 취리히",   // 사용자에게 보여줄 문자열
      "sendText": "스위스 · 취리히", // 서버로 보낼 문자열
      "meta": { "id": "981" } }    // 선택적 부가정보 (표 컬럼, id 등)
  ],
  "optionsUrl": "/api/v1/agent-conversations/bizplay/agents/plan/destination-options?corpNo=1234567890",
                                  // 전체 목록이 필요할 때 부르는 저희 API (선택)
  "upstream": [                   // 그 목록의 원천인 BizPlay API (참고용)
    { "method": "GET", "path": "/api/v2/bstr/area/region/{regionType}" }
  ]
}
```

`options`는 **인라인으로 담을 수 있는 길이면 항상 인라인으로** 내려갑니다 — `lookup`을 제외한 모든
질문이 그렇습니다. `lookup`(터미널 415건, 역 목록 등)은 `options` 없이 `optionsUrl`만 내려갑니다:
사용자가 입력하는 대로 조회하시거나, 그냥 직접 입력하게 두셔도 에이전트가 해석합니다. 그 외
질문에서 `optionsUrl`은 "더 보기 / 검색"용 선택 사항이며, 호출하지 않아도 대화는 끝까지 진행됩니다.

### `kind` 목록

`kind`는 **그 질문이 무엇에 관한 것인지**를 나타냅니다. 특정 질문에 전용 위젯을 붙이고 싶다면 이
값으로 분기하시면 됩니다. 그리는 방법은 다음 장의 `render`가 알려 주며, 둘은 독립적입니다 —
목록이 길어지면 같은 `kind`가 다른 `render`로 내려올 수 있습니다.

| kind | 질문 | 주로 오는 `render` | 되돌려 보내는 값 |
|---|---|---|---|
| `PURPOSE` / `SEGMENT` | 출장 목적, 이어서 세부 구분 | `chips` | `purpose:2952` (기계용 토큰) |
| `DESTINATION` | 국가 · 도시 | `dropdown` (67건) | `"스위스 · 취리히"` |
| `ROUTE` | 이동경로: 출발지 · 목적지 · 복귀지 | `route-picker` | 구간별 장소명 |
| `TRAVELER` | 출장자 | `lookup` / `chips` | 사람 이름 |
| `APPROVAL_LINE` / `APPROVER` | 결재선 | `approval-line` | 사람 이름(역할을 덧붙여도 됩니다: `"김비플 합의"`) |
| `PLAN` / `PLAN_PENDING` | 정산할 출장(승인됨 / 결재 대기) | `table` | 옵션의 `sendText`(계획 토큰) |
| `TRANKIND` | 경비 항목(교통비 · 숙박비 …) — 계획서 양식이 지정한 항목, 지정이 없으면 해당 법인의 출장비 규정에 있는 항목(국내/해외로 구분) | `chips` | `trankind:11719` |
| `CARD_TYPE` | 조회할 카드 종류 | `chips` | `card-types:PERSONAL,MY_DATA` |
| `RECEIPT` | 첨부할 영수증 | `table` | `receipt:<id>` / `receipts-done` |
| `EVIDENCE_PERIOD` | 조회 기간 | `chips` (+ `ui: "calendar"`) | `"2026-09-03 ~ 2026-09-05"` |
| `EXPENSE_SLOT` | 경비 상세 항목(출발지 · 좌석등급 …) | `lookup` | 입력하거나 고른 값 |
| `EXPENSE_CONFIRM` / `EXPENSE_ABANDON` | 이 영수증을 등록할까요 / 취소 | `chips` | `expense-confirm` / `expense-cancel` |
| `MANUAL_EXPENSE_MODE` | 직접 입력 방식 | `chips` | `manual-expense` |
| `STOP` / `SUBMIT` / `DONE` | 그만 / 제출 / 완료 | `chips` | 옵션의 `sendText` |

`sendText`에는 위처럼 두 가지 형태가 있으며 **둘 다 그대로 전송**하시면 됩니다: 기계용 토큰
(`purpose:2952`, `expense-confirm`) 또는 사람의 문장(`"스위스 · 취리히"`). 직접 조합하지 마시고,
옵션의 `sendText`를 그대로 보내고 `label`을 화면에 보여 주세요.

---

## 4. 무엇을 그릴 것인가: `render`와 `ui`

두 필드가 나눠서 알려 주며, 클라이언트는 다음 순서로 읽으면 됩니다. **해당되지 않으면 두 필드 모두
응답에서 생략됩니다** — 응답이 `NON_NULL`이므로, 필드가 없다는 것 자체가 답입니다.

1. **턴의 `ui`가 있으면** → 선택지 목록이 아닌 위젯이 필요한 턴입니다(달력, 경비 입력폼, 파일 선택).
   그 위젯을 그리고, `action`이 있으면 거기로 POST합니다. 5장.
2. **없고 `pendingChoices`가 있으면** → 골라서 답하는 질문입니다. 각 그룹을 자신의 `render`대로
   그리고(아래), `label`을 보여 주고 `sendText`를 보냅니다.
3. **둘 다 없으면** → `reply`와 입력창만 그립니다.

둘 다 있는 턴도 있습니다: `EXPENSE_PREVIEW`는 `ui: "expense-preview"`와 `EXPENSE_CONFIRM` 칩을
함께 내려줍니다. 둘 다 없는 턴도 있습니다: `CREATE_PLAN`은 질문이 아니라 결과 통지입니다. 그리고
**서로 다른 kind의 질문 두 개**가 한 턴에 실리기도 합니다 — 각각의 `render`대로 두 줄로 그리시면
됩니다.

```jsonc
// 한 턴: "출발지는 어디셨나요?"  (intent EXPENSE_SLOT_PENDING, ui 없음)
"pendingChoices": [
  { "kind": "EXPENSE_SLOT", "name": "출발지", "render": "lookup",
    "source": "terminals:BUS",
    "optionsUrl": ".../agents/settlement/terminals?corpNo=1234567890&vehicleType=BUS",
    "upstream": [ { "method": "GET", "path": "/api/v2/receipt/etc-card/terminal" }, … ] },
    // "options" 없음: 인라인으로 담기엔 너무 많음 — optionsUrl 조회 또는 직접 입력
  { "kind": "EXPENSE_ABANDON", "name": "또는", "render": "chips",
    "options": [ { "label": "이 영수증 취소", "sendText": "expense-cancel" } ] }
]
```

```jsonc
// PURPOSE_SELECTION — 고를 목록이 있고, 그 외 위젯은 없음
{ "intent": "PURPOSE_SELECTION",
  "pendingChoices": [ { "kind": "PURPOSE", "render": "chips", "options": [ … ] } ] }
  // "ui" 키 자체가 없음

// EXPENSE_IMAGE_REQUIRED — 고를 것은 없고, 파일을 선택해야 함
{ "intent": "EXPENSE_IMAGE_REQUIRED",
  "ui": "file-upload",
  "action": "…/agents/settlement/{sessionId}/manual-expense/attach?corpNo=…" }
  // "pendingChoices" 키 자체가 없음
```

### `render` — 선택지를 그리는 방법

60개짜리 목록이 버튼 60개가 되지 않도록, 서버가 위젯 형태를 함께 알려 드립니다.

| `render` | 위젯 | 쓰이는 곳 |
|---|---|---|
| `chips` | 알약 모양의 작은 버튼을 한 줄로 — 옵션 하나당 버튼 하나, 눌러서 답변 | 24개 이하의 일반 선택 |
| `dropdown` | 셀렉트 박스 | 24개를 넘는 목록(예: 목적지 67건) |
| `table` | 표(옵션의 `meta`가 컬럼이 됩니다) | 계획서 목록, 영수증 목록 |
| `route-picker` | 출발지 · 목적지 · 복귀지 선택 UI | `ROUTE` |
| `approval-line` | 결재선 카드 + 인원 추가 | `APPROVAL_LINE` |
| `lookup` | `optionsUrl`을 조회하는 검색형 입력 | 터미널 · 역 등 대용량 목록 |

전부 `chips`로 그려도 동작에는 문제가 없습니다. 답변은 언제나 `sendText`를 되돌려 보내는 것이므로
`render`는 "형태"에 대한 권장값이며, 클라이언트가 직접 판단하다가 한 화면에 버튼 67개를 깔지 않도록
존재합니다.

---

## 5. `ui` / `action` / `formFields` — 위젯이 필요한 턴

문장과 버튼만으로는 답할 수 없는 턴이 있습니다(파일 첨부, 기간 선택, 경비 입력폼). 이때 `ui`(무엇을
그릴지)와 `action`(어디로 POST할지)이 함께 내려갑니다.

| `ui` | 의미 | `action` |
|---|---|---|
| `calendar` | 기간 선택 | — (선택한 기간을 다음 `message`로 전송, 예: `"2026-09-03 ~ 2026-09-05"`) |
| `expense-form` | 경비 입력폼(`formFields` 참조) | `POST …/agents/settlement/{sessionId}/manual-expense/create` |
| `expense-preview` | 등록 전 영수증 미리보기 | — |
| `file-upload` | 영수증 이미지/PDF 첨부 | `POST …/agents/settlement/{sessionId}/manual-expense/attach` (multipart) |
| `receipt-table` | 영수증 목록 브라우저 | — |
| `settlement-preview` | 정산서 요약 | — |
| `confirm-submit` | 상신 확인 | `POST …/agents/plan/{sessionId}/create` |

`ui: "calendar"`는 특정 intent에 묶여 있지 않습니다. **열려 있는 질문이 기간(날짜 범위)인 모든 턴**에
붙습니다 — 계획서의 출장기간 질문(`FIELD_COMPLETION`)이나, 조건에 맞는 계획을 찾지 못해 다른 기간을
묻는 정산서의 `PLAN_SEARCH`도 포함됩니다. 그러니 달력을 띄울지 여부는 intent가 아니라 `ui`로
판단해 주세요. 선택한 기간은 일반 메시지로 보내면 되고, ISO(`"2026-09-03 ~ 2026-09-05"`)와 자연어
모두 인식합니다.

`formFields`는 `expense-form`의 각 입력 항목을 스스로 설명하므로, 폼을 하드코딩하지 않고 생성할 수
있습니다. 교통비 경비의 경우 현재 15개 항목이 내려갑니다.

| 항목의 키 | 의미 |
|---|---|
| `key` | 등록 호출에 그대로 실어 보낼 필드명(`mestName`, `approvalAmount` …). |
| `label` | 대화 언어로 된 화면 캡션. |
| `type` | `text`, `number`, `date`, `time`, `select`, `file`. |
| `required` | `true`면 필수(없으면 호출 불가), `false`면 비워 둘 수 있음. |
| `options` | 인라인 `{label, value}` 목록 — 고정 enum인 `select`에 붙습니다. |
| `optionsUrl` | 인라인으로 담기엔 긴 목록의 위치(통화 179건, 터미널 415건). |
| `source` / `upstream` | 그 목록이 속한 capability와, 그 뒤의 BizPlay 엔드포인트. |

```jsonc
{ "key": "mestName",     "label": "가맹점", "type": "text",   "required": true }
{ "key": "approvalDate", "label": "일자",   "type": "date",   "required": true }
{ "key": "approvalAmount", "label": "금액", "type": "number", "required": true }
{ "key": "vehicleType",  "label": "교통수단", "type": "select", "required": true,
  "options": [ { "label": "항공", "value": "AIR" }, … 13건 ] }            // 고정 enum, 인라인
{ "key": "currencyCode", "label": "통화",  "type": "select", "required": true,
  "source": "currencies",
  "optionsUrl": ".../agents/settlement/currencies?corpNo=…",
  "upstream": [ { "method": "GET", "path": "/api/v2/currency-code/combo" } ] }   // 179건
{ "key": "image",        "label": "영수증", "type": "file",   "required": false }
```

**폼은 선택 사항입니다.** 같은 내용을 문장으로 입력해도(`"고속버스로 센트럴시티에서 강릉 9월 4일
25000원"`) 동일하게 처리되며, 빠진 항목만 되묻습니다. 위젯 경로와 대화 경로는 항상 같은 결과에
도달하도록 맞춰 두었습니다.

---

## 6. `intent` 전체 목록

`intent`는 **"이 턴이 무엇인가"** 를 알려 줍니다. 화면 분기에 쓰시되, **모르는 값이 오면 `reply` +
`pendingChoices`만으로 렌더링되도록** 기본 처리를 두시길 권장합니다(기능 추가에 따라 값이 늘어납니다).

### 출장계획서 (`POST /agents/plan`)

| intent | 뜻 | 선택지 | 위젯 |
|---|---|---|---|
| `PURPOSE_SELECTION` | 출장 목적을 고르는 중 | `PURPOSE` (chips) | |
| `SEGMENT_SELECTION` | 목적의 세부 구분 | `SEGMENT` (chips) | |
| `FORM_LOAD` | 양식을 불러옴 | | |
| `DESTINATION_ASK` | 국가 · 도시 질문 | `DESTINATION` (dropdown) | |
| `TRAVELER_PICK` | 어떤 사람인지 확인(동명이인 등) | `TRAVELER` | |
| `TRAVELER_MORE_ASK` | 동행자 추가 여부 | — (자유 입력) | |
| `TRAVELER_REMOVED` | 출장자 제외 완료 | | |
| `ROUTE_ASK` | 이동경로 질문 | `ROUTE` (route-picker) | |
| `FIELD_COMPLETION` | 남은 항목을 묻는 중 | — (자유 입력) | 물어보는 항목이 출장기간이면 `calendar` |
| `FIELD_EDITED` | 값 수정 반영 | | |
| `APPROVAL_LINE_ASK` | 결재선 질문 | `APPROVAL_LINE` (approval-line) | |
| `SUBMIT_REQUESTED` | 상신 요청을 인식 | | `confirm-submit` |
| `CREATE_PLAN` | **BizPlay에 기안 완료**(reply에 문서번호 포함) | | |
| `CREATE_PLAN_MANUAL` | 클라이언트가 보낸 문서로 기안 완료 | | |
| `DATA_QUERY` / `DRAFT_QUERY` | 데이터 / 작성 중 문서에 대한 질문에 답변 | | |
| `GUARDRAIL_BLOCKED` | 출장 업무와 무관한 요청 — 아무것도 바뀌지 않음 | | |

### 출장정산서 (`POST /agents/settlement`)

| intent | 뜻 | 선택지 | 위젯 |
|---|---|---|---|
| `AWAIT_PERIOD` / `AWAIT_PLAN_PERIOD` | 조회 기간 질문 | `EVIDENCE_PERIOD`(후보가 있을 때) | `calendar` |
| `PLAN_SEARCH` | 정산 가능한 출장 목록 제시(기간을 말하지 않으면 기본 기간으로 조회) | `PLAN` (table) | 일치하는 계획이 없어 다른 기간을 물을 때 `calendar` |
| `PLAN_PICK_PENDING` | 계획서 선택 대기 | `PLAN` | |
| `PLAN_IMPORT` | 계획서를 정산서로 불러옴 | `TRANKIND` | |
| `PENDING_PLANS` | 결재 대기 계획서 안내(정산 불가) | `PLAN_PENDING` | |
| `TRANKIND_PENDING` / `TRANKIND_PICKED` | 경비 항목 질문 / 선택됨 | `TRANKIND`, `CARD_TYPE` | |
| `CARD_TYPES_PENDING` | 카드 종류 질문 | `CARD_TYPE` | |
| `EVIDENCE_PERIOD_PENDING` | 증빙 조회 기간 질문 | | `calendar` |
| `EVIDENCE_LOAD` | 미첨부 증빙 목록 | `RECEIPT` (table) | |
| `EVIDENCE_PICK_PENDING` | 증빙 선택 대기 | `RECEIPT` | |
| `EVIDENCE_ATTACH` | 증빙 첨부 완료 | | |
| `MANUAL_EXPENSE_CHOOSE` | 직접 입력 방식 선택 | `MANUAL_EXPENSE_MODE` | |
| `MANUAL_EXPENSE_PROMPT` / `_FULL` | 경비 직접 입력 요청 | | `expense-form` |
| `EXPENSE_SLOT_PENDING` | 경비 상세 항목 질문(출발지 · 좌석등급 …) | `EXPENSE_SLOT` (lookup) | |
| `EXPENSE_PREVIEW` | 등록 전 확인 | `EXPENSE_CONFIRM` | `expense-preview` |
| `EXPENSE_IMAGE_REQUIRED` | 영수증 이미지가 아직 없음 | | `file-upload` |
| `EXPENSE_IMAGE_SUBMIT` | 이미 선택된 파일을 올려 달라는 요청 | | |
| `MANUAL_EXPENSE_CREATED` / `MANUAL_EXPENSE_ADDED` | 경비 등록됨 | | |
| `EXPENSE_UPDATED` / `EXPENSE_CANCELLED` | 경비 수정 / 취소 | | |
| `TITLE_SET` | 문서 제목 지정 | | |
| `RECEIPT_BROWSE` / `RECEIPT_BROWSE_NOT_ISSUED` | 영수증 조회 화면 | `RECEIPT` | `receipt-table` |
| `SETTLEMENT_READY` | 제출 준비 완료 | `SUBMIT` | `settlement-preview` |
| `APPROVAL_LINE_ASK` | **제출 전 결재선 확인** | `APPROVAL_LINE` | |
| `APPROVER_PICKED` | 결재자 지정됨 | | |
| `CREATE_SETTLEMENT` | **BizPlay에 기안 완료**(reply에 문서번호 포함) | | |
| `SETTLEMENT_SAVED` / `SESSION` / `DRAFT_QUERY` | 임시저장 / 세션 조회 / 문서 질의 응답 | | |
| `STOP_PICK_PENDING` | 중단 여부 확인 | `STOP` | |
| `GUARDRAIL_BLOCKED` | 정산 업무와 무관한 요청 — 아무것도 바뀌지 않음 | | |

> `CREATE_PLAN` / `CREATE_SETTLEMENT` 턴은 문서가 BizPlay에 기안된 시점입니다. 호출할 것이 없으므로
> 이 턴에는 `resources` · `upstream`이 없습니다.

---

## 7. `resources` / `upstream` — 어떤 intent에서 어떤 API인가

응답에는 **그 턴에 관련된 엔드포인트만** 담깁니다.

* **`resources`** — 저희(에이전트) API. 더 긴 목록을 받아오거나, 위젯이 POST할 곳입니다.
* **`upstream`** — 그 데이터의 원천인 **BizPlay API**. 귀사는 자체 토큰으로 BizPlay를 직접
  호출하는 구성이므로, 각 질문에 대응하는 BizPlay 엔드포인트를 함께 명시합니다.

두 필드는 같은 키(`destinationOptions`, `plans`, `terminals`, `registerExpense` …)와 같은 형식
`{ "키": [ { "method": "GET", "path": "…" } ] }`을 씁니다. `path`의 `{중괄호}`는 클라이언트가 채웁니다.

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

> 양식 조회 경로에 대한 참고: 저희가 안내하는 경로는 모든 용도에서 응답하는
> `/api/v2/paper/purpose/{purposeId}?segmentId={segmentId}` 입니다. 타입 포함 경로
> `/api/v2/paper/purpose/{bstrType}/{purposeId}?segmentId={segmentId}` 도 동작하며, 추가로
> **국내/해외 유형으로 필터링**합니다 — 즉 OVERSEA로 등록된 정산서 양식은 DOMESTIC 출장에서는
> 조회되지 않습니다. 세부구분이 있는 용도를 `segmentId` 없이 호출하면 400 `COMM_ERROR`가 납니다.

### 전체 목록을 한 번에 보기

```http
GET /api/v1/agent-conversations/bizplay/agents/contract?corpNo=1234567890
```

두 에이전트의 `resources` · `upstream` 전체, **`intents` 카탈로그**(각 항목: `intent`, `means`,
함께 내려가는 `choices` kind, 그 intent의 `ui`, 그리고 필요한 `resourceKeys`), intent → 위젯 매핑,
`render` 종류를 한 번에 돌려줍니다. 카탈로그는 실제 턴이 사용하는 것과 같은 매핑에서 생성되므로
응답과 어긋날 수 없습니다.

```jsonc
// agents.plan.intents[3]
{ "intent": "DESTINATION_ASK", "means": "Asking country and city",
  "choices": ["DESTINATION"], "ui": null, "resourceKeys": ["destinationOptions"] }

// agents.settlement.intents[0]
{ "intent": "AWAIT_PERIOD", "means": "Asking the period to search",
  "choices": ["EVIDENCE_PERIOD"], "ui": "calendar", "resourceKeys": [] }
```

`"choices": []`인 항목은 자유 입력 질문입니다 — 그 턴에는 `pendingChoices`가 없습니다.

---

## 8. 실제 응답 예시

### 8.1 `DESTINATION_ASK` (수정 후)

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
        /* … 총 67건 */
      ]
    }
  ],
  "resources": {
    "destinationOptions": [
      { "method": "GET", "path": "/api/v1/agent-conversations/bizplay/agents/plan/destination-options?corpNo=1234567890" }
    ]
  },
  "upstream": { "destinationOptions": [ /* 위 선택지의 upstream과 동일 */ ] }
}
```

### 8.2 `ROUTE_ASK` (수정 후)

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
        /* … 총 11건 */
      ]
    }
  ],
  "resources": { "routeOptions": [ { "method": "GET", "path": "/api/v1/agent-conversations/bizplay/agents/plan/route-options?corpNo=1234567890" } ] },
  "upstream":  { "routeOptions": [ { "method": "GET", "path": "/api/v2/bstr/destination/active/list" } ] }
}
```

### 8.3 자유 입력 질문 — 선택지 없음

```jsonc
{
  "intent": "FIELD_COMPLETION",
  "reply": "선택 사항 하나만요 — 출장지 상세(건물·층·장소, 10자 이내)가 있다면 알려주세요. 없으면 없다고 말씀해 주세요."
  /* pendingChoices 없음: 답이 목록에서 나오지 않는 질문입니다 */
}
```

---

## 9. 구현 시 참고 사항

1. **세션** — 첫 턴은 `sessionId` 없이 보내고, 응답의 값을 이후 턴에 재사용합니다. 문서가 기안되면
   (`CREATE_PLAN` / `CREATE_SETTLEMENT`) 그 세션은 종료되고, 이후 메시지는 자동으로 새 세션으로
   넘어갑니다.
2. **토큰** — `X-Bizplay-Token` 헤더의 Bearer 토큰으로 BizPlay를 호출합니다. 헤더가 없으면 서버의
   기본(개발용) 토큰이 사용됩니다.
3. **언어** — `reply`는 사용자가 쓴 언어(한국어/영어)를 따라갑니다. 별도 파라미터는 없습니다.
4. **결재선** — 정산서는 제출 직전에 결재선을 한 번 확인합니다(`APPROVAL_LINE_ASK`). BizPlay 화면과
   동일한 절차이며, 계획서의 결재자가 기본값으로 표시됩니다. `"김비플 님도 합의로 넣어줘"`처럼 말로
   바꿀 수 있고 선택지로도 고를 수 있습니다. 이 턴에 답하기 전에는 문서가 기안되지 않습니다.
5. **모르는 값 처리** — `intent`, `kind`, `render`, `ui`는 앞으로 값이 추가될 수 있습니다. 모르는
   값은 `reply` + `pendingChoices`(chips) 기본 렌더로 처리하시면 대화가 끊기지 않습니다.
6. **`draftJson`** — BizPlay 저장 body와 같은 구조이므로, 화면 미리보기에 그대로 쓰실 수 있습니다.
