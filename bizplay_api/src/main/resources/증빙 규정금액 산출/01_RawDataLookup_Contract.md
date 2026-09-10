# §2 Raw Data Lookup Contract — the Purpose Limit Lookup API

> **Scope of this section** — of the 3 layers that produce the ruled amount (`ruledAmount`), this covers **① raw data lookup** only.
> ② Condition application (front-end calculation) is covered in §3, and ③ validation from §4 onward.
>
> **The first thing to know** — the `limitAmounts` this API returns is **the base amount, not the final ruled amount**.
> Producing the final amount by applying the conditions (`appliedConditions`) is **the caller's (client's) responsibility**. → [§2.5](#25--limitamounts-is-the-base-amount-core)

## Source file abbreviations

The `file:line` notation in this document uses the abbreviations below.

| Abbreviation | Actual path |
|------|-----------|
| `renewal-limit-api.ts` | `packages/domains/src/bstr-policy/api/renewal-limit-api.ts` |
| `calc-inputs.ts` | `packages/domains/src/bstr-policy/utils/calc-inputs.ts` |
| `rule-update-default.ts` | `packages/domains/src/bstr-policy/utils/rule-update-default.ts` |
| `rule-update-food.ts` | `packages/domains/src/bstr-policy/utils/rule-update-food.ts` |
| `rule-update-utils.ts` | `packages/domains/src/bstr-policy/utils/rule-update-utils.ts` |
| `rule-update-helpers.ts` | `packages/domains/src/bstr-policy/utils/rule-update-helpers.ts` |
| `ruled-amount-calculator.ts` | `packages/domains/src/bstr-policy/utils/ruled-amount-calculator.ts` |
| `bstr-route-utils.ts` | `packages/domains/src/bstr-policy/utils/bstr-route-utils.ts` |
| `vehicle-utils.ts` | `packages/domains/src/bstr-policy/utils/vehicle-utils.ts` |
| `transport-vocab.ts` | `packages/domains/src/bstr-policy/utils/transport-vocab.ts` |
| `currency-utils.ts` | `packages/domains/src/bstr-policy/utils/currency-utils.ts` |
| `diff-currency-converter.ts` | `packages/domains/src/bstr-policy/utils/diff-currency-converter.ts` |
| `BstrPolicyRenewalController.java` | `app-internal-api/.../business/bstr/controller/BstrPolicyRenewalController.java` |
| `BstrRenewalLimitService.java` | `app-internal-api/.../business/bstr/service/BstrRenewalLimitService.java` |
| `RenewalLimitRequestDto.java` | `domain/.../bstr/dto/RenewalLimitRequestDto.java` |
| `BstrTranKindLimitRequestDto.java` | `domain/.../bstr/dto/BstrTranKindLimitRequestDto.java` |
| `CalcConditionInputDto.java` | `domain/.../bstr/dto/CalcConditionInputDto.java` |
| `BstrTranKindLimitDto.java` | `domain/.../bstr/dto/BstrTranKindLimitDto.java` |
| `AppliedConditionDto.java` | `domain/.../bstr/dto/AppliedConditionDto.java` |

---

## 2.1  Endpoint

| Item | Value | Source |
|------|-----|------|
| Method and path | `POST /api/v2/bstr/policy/renewal/limit` | Class `@RequestMapping("/api/v2/bstr/policy/renewal")` (`BstrPolicyRenewalController.java:30`) + method `@PostMapping("/limit")` (`BstrPolicyRenewalController.java:44`) |
| Request body | `RenewalLimitRequestDto` (`@RequestBody`) | `BstrPolicyRenewalController.java:45-46` |
| Response body | `BstrTranKindLimitDto` (HTTP 200) | `BstrPolicyRenewalController.java:45,48` |
| Handling service | `BstrRenewalLimitService.calculateLimit(request)` | `BstrPolicyRenewalController.java:47` |
| Where the client constant is defined | `const RENEWAL_LIMIT_ENDPOINT = '/api/v2/bstr/policy/renewal/limit'` | `renewal-limit-api.ts:22` (the "endpoint" block at the top of the module; not exported outside the file) |
| Calling function | `fetchRenewalLimitAmounts(request)` → `apiPost<RenewalLimitResponse>(RENEWAL_LIMIT_ENDPOINT, request)` | `renewal-limit-api.ts:466,476` |

**The endpoint contract has two modes** (`RenewalLimitRequestDto.java:15-18`; implementation evidence at `BstrRenewalLimitService.java:145-151`)

| Mode | Request | Response |
|------|------|------|
| Base-amount mode | `calcInputs` not sent (`null`/`undefined`) | The same base amount as the existing `POST /api/v2/bstr/policy/limit` (based on BstrExpense rows) |
| Condition mode | `calcInputs` sent | Base amount plus the matched conditions (`appliedConditions`). **The client performs the amount adjustment** ([§2.5](#25--limitamounts-is-the-base-amount-core)) |

> ⚠️ **Two comments in our code say the opposite of this — we are stating it up front because opening the source may be confusing.**
>
> | Location | Text | Verdict |
> |---|---|---|
> | Backend Swagger description `BstrPolicyRenewalController.java:41-43` | "Returns the **final amount** with CalcCondition block adjustments applied" | **Wrong.** Line `:149` of the same service keeps `limitAmounts` as the base amount |
> | Client JSDoc `renewal-limit-api.ts:8-11` | "When `calcInputs` is sent → **responds with the final amount** after sequential CalcCondition application on the base amount" | **Ambiguous.** `fetchRenewalLimitAmounts` applies the conditions just before returning (§2.6), so it is correct for the **function's return value** but wrong for the **HTTP response** |
>
> **The HTTP response's `limitAmounts` is the base amount** → [§2.5](#25--limitamounts-is-the-base-amount-core).
> We will correct both comments.

---

## 2.2  Complete request field table

`RenewalLimitRequest` (`renewal-limit-api.ts:33-95`) — **20 fields**.
The field names are **all identical** to the backend's `BstrTranKindLimitRequestDto` / `RenewalLimitRequestDto` (stated in the comment at `renewal-limit-api.ts:31`). The **type notation**, however, differs between the two (see the "Backend type" column).

"Where the value comes from" was reverse-engineered from the current client's assembly points (`rule-update-default.ts:120-146`, `rule-update-food.ts:66-88`, `ruled-amount-calculator.ts:183-217`).

| Field | Type (client) | Required/nullable | Backend type | Meaning | Where the value comes from |
|------|------------------|---------------|--------------------|------|--------------------|
| `bstrDate` | `string` (`YYYY-MM-DD`) | **Required**, non-null | `LocalDate bstrDate` (`BstrTranKindLimitRequestDto.java:42`) | Trip start date (`renewal-limit-api.ts:34-35`) | **Document** — the receipt's usage start date (`receipt.usedStartDate`), or the report's trip start date if absent (`rule-update-default.ts:121`) |
| `bstrEndDate` | `string` | Optional | `LocalDate bstrEndDate` (`:44`) | Trip end date (`renewal-limit-api.ts:36-37`) | **Document** — falls back in the order `receipt.usedEndDate` → `receipt.usedStartDate` → the report's trip end date (`rule-update-default.ts:122`) |
| `corporationId` | `number` | **Required**, non-null | `Long corporationId` (`:28`) | Business site (corporation) ID (`renewal-limit-api.ts:38-39`) | **Document context** — the corporation ID the report belongs to (`rule-update-utils.ts:57`) |
| `corporationUserId` | `number` | **Required**, non-null | `Long corporationUserId` (`:30`) | User ID. Used to **determine the user group** based on rank, position, job, and title (`renewal-limit-api.ts:40-41`) | **Document** — the drafter's `draftUserId` (`rule-update-default.ts:124`). For meals, called N times, once each for the drafter and each companion (`rule-update-food.ts:57-58,70`) |
| `vehicleType` | `string \| null` | **Required key + nullable** | `VehicleType vehicleType` (enum, `:34`) | Transport mode type. **The actual enum constants are `KTX`, `AIR`, `BUS`, …** (source of truth `common-core/.../constant/VehicleType.java:31`). ⚠️ `"AIRPLANE"` is not an enum name but the **tmapMode code field** of the `AIR` constant (`:44`), and `fromTmapMode` is a static helper, not a Jackson path — sending that value produces a **400** | **Document** — the transport mode on a transportation receipt. Only for transportation (`TRANSPORT`) is it `normalizeVehicleType(receipt.vehicleType)`; for other purposes it is `null` (`rule-update-default.ts:108,125`). Normalization folds the KTX family into `'KTX'` (`vehicle-utils.ts:8-11`), and the backend folds `TRAIN·KTX·SRT·ITX·SAEMAEUL·MUGUNGHWA → KTX` once more (`BstrTranKindLimitRequestDto.java:84-92`) |
| `tranKindType` | `string` | **Required**, non-null | `TranKindType tranKindType` (enum, `:32`) | Purpose type. **The actual enum is `HD_DAILY_COST`, `HD_ROOM`, `HD_FOOD`, `HD_TRANSPORT`, `HD_FUEL`, …** (source of truth `common-core/.../constant/TranKindType.java:8-44`). ⚠️ The `HD_HOTEL` and `HD_MEAL` in the client comment (`renewal-limit-api.ts:44-45`) are **nonexistent values** — with no `@JsonValue`/`@JsonCreator`, JSON accepts only enum names, so sending those values causes **deserialization failure (400)** | **Document** — the receipt's purpose (`rule-update-default.ts:99,126`). **Calling with an empty string or null gives a backend 400** — the client skips the call entirely when it is empty (`rule-update-default.ts:96-102`) |
| `tranKindId` | `number` | Optional | `Long tranKindId` (`:66`) | Purpose (expense item) ID — for looking up a specific item (`renewal-limit-api.ts:46-47`) | **Document** — the receipt's expense item ID (`rule-update-default.ts:128`). Sent for every purpose for legacy equivalence (`rule-update-default.ts:127`) |
| `bstrPurposeId` | `number \| null` | **Required key + nullable** | `Long bstrPurposeId` (`:38`) | Trip purpose ID (`renewal-limit-api.ts:48-49`) | **Document** (inherited from the plan) — the report's trip purpose (`rule-update-default.ts:129`). The backend looks up the purpose **name** by this ID and string-compares it with the condition item `PURPOSE` (`BstrRenewalLimitService.java:113,317`) |
| `bstrSegmentId` | `number \| null` | **Required key + nullable** | `Long bstrSegmentId` (`:40`) | Trip classification ID (`renewal-limit-api.ts:50-51`) | **Document** — the report's trip classification (`rule-update-default.ts:130`). The backend converts it to the classification **name** and compares against the `SEGMENT` condition (`BstrRenewalLimitService.java:114,318`) |
| `bstrDepartureId` | `number \| null` | **Required key + nullable** | `Long bstrDepartureId` (`:46`) | Departure point ID (`renewal-limit-api.ts:52-53`) | **Document** — the **`departureId` of the first leg** of the route (`bstrRoutes`) (`bstr-route-utils.ts:9-12`, `rule-update-default.ts:131`) |
| `bstrDestinationId` | `number \| null` | **Required key + nullable** | `Long bstrDestinationId` (`:48`) | Destination ID (`renewal-limit-api.ts:54-55`) | **Document** — the **`arrivalId` of the last leg** of the route (`bstr-route-utils.ts:15-18`, `rule-update-default.ts:132`) |
| `bstrAreaCode` | `string \| null` | **Required key + nullable** | `String bstrAreaCode` (`:52` — the Swagger description says "grade zone code (**unused**)") | Grade zone code (`renewal-limit-api.ts:56-57`) | **None — always explicitly sends `null`.** Every current call site uses `bstrAreaCode: null` (`rule-update-default.ts:133`, `rule-update-food.ts:81`) |
| `bstrRegionId` | `number \| null` | **Required key + nullable** | `Long bstrRegionId` (`:50`) | Region ID — determines the grade zone (`renewal-limit-api.ts:58-59`) | **Document** — among the selections of the trip period input item (`BSTR_PERIOD`), the `selectionId` of the segment the receipt's usage date falls into, or the first segment if none (`rule-update-helpers.ts:153-172`); the final fallback is the document-level `bstrRegionId` (`rule-update-default.ts:104-105,137`). **Transportation must also send this or the rule will not match** (`rule-update-default.ts:134-136`) |
| `totalDistance` | `number` | Optional | `Double totalDistance` (`:75`) | Total travel distance in km (`renewal-limit-api.ts:60-61`) | **Document** — the sum of per-route `distance` (`bstr-route-utils.ts:21-24`). **Sent only for fuel (`FUEL`)**, otherwise `undefined` (`rule-update-default.ts:139-140`). Used by the backend's `DISTANCE` condition comparison (`BstrRenewalLimitService.java:347`) |
| `bstrType` | `string` | Optional | `BstrType bstrType` (enum, `:36`) | Trip type (`renewal-limit-api.ts:62-63`) | **Document** — the report's trip type. The client sends only two values, `'OVERSEA'` or `'DOMESTIC'` (`rule-update-default.ts:138`, `rule-update-food.ts:83`). ⚠️ The comment's example says `DOMESTIC, OVERSEAS` (with an S) but the value actually sent is `OVERSEA` — **use the source string `'OVERSEA'`** |
| `exceptionRuleInputItemIds` | `number[]` | Optional | `List<Long> exceptionRuleInputItemIds` (`:56`) | Exception rule item IDs (`renewal-limit-api.ts:64-65`) | **Document + user selection** — collected from 4 kinds of input items merged together → [§2.3.2](#232-exceptionruleinputitemids-collection-rules). Not sent when the array is empty (`rule-update-default.ts:141`) |
| `activityDivision` | `string \| null` | Optional | `ActivityDivisionType activityDivision` (enum, `:78`) | Activity expense classification (`ACTUAL`=actual cost / `FIXED`=fixed) (`renewal-limit-api.ts:66-70`) | **Document + user selection** — the value of the `ACTIVITY_EXPENSE_TYPE` input item. Sent **only for per diem (`DAILY_COST`, `HD_DAILY_COST`)**, and not sent if the value is neither `ACTUAL` nor `FIXED` (`rule-update-default.ts:111-119,142`; `calc-inputs.ts:214-218`). When not sent, the backend applies no matching (everything passes) (`renewal-limit-api.ts:67-68`) |
| `foodDivisionType` | `string \| null` | Optional | `FoodDivisionType foodDivisionType` (enum, `:81`) | Meal classification (enum names such as `BREAKFAST`/`LUNCH`/`DINNER`/`ALL`) (`renewal-limit-api.ts:72-76`) | **Document** — the meal classification on a meal receipt. Sent **only for meals (`FOOD`)** (`rule-update-food.ts:76`, `ruled-amount-calculator.ts:212-215`). **If not sent, the breakfast, lunch, and dinner rule rows all pass and which meal's limit applies becomes unpredictable** — always include it for meals (`ruled-amount-calculator.ts:209-211`) |
| `receiptEtcId` | `number \| null` | Optional | `Long receiptEtcId` (`:54`) | Receipt supplementary-info PK. The backend uses it for **round-trip ticket determination and terminal policy amount lookup** (`renewal-limit-api.ts:78-83`) | **Document** — the receipt's `receiptEtcId`. Not sent when absent → the backend treats it as one-way or falls back (`rule-update-default.ts:144-145`) |
| `calcInputs` | `CalcConditionInputs \| null` | Optional | `CalcConditionInputDto calcInputs` (`RenewalLimitRequestDto.java:28`) | Additional inputs for condition (CalcCondition) matching (`renewal-limit-api.ts:85-94`) | **Document + user selection + form settings** → [§2.3](#23--how-to-fill-calcinputs) |

### 2.2.1  What must NOT go into `calcInputs`

The rule at `renewal-limit-api.ts:91-93`:

> Trip purpose, grade zone, destination, and distance are **already passed in the top-level fields**, so `calcInputs` should contain only CalcCondition-specific items (trip items, transport mode, time, etc.).

### 2.2.2  Request fields that exist only on the backend (not declared in the client interface)

`BstrTranKindLimitRequestDto` has more fields than the client interface. These are things an external system **may additionally send**; the current client does not send them.

| Backend field | Type | Swagger description | Source |
|-------------|------|--------------|------|
| `jobClassCode` | `String` | Job rank (e.g. `G3`) | `BstrTranKindLimitRequestDto.java:68` |
| `exceptPayOption` | `Boolean` | Whether to apply the payment option across the full period. `true` applies it to the full period (an option specific to certain customers) | `BstrTranKindLimitRequestDto.java:70-72` |
| `departureTerminal` | `String` | Departure terminal (when there is no receipt ID) | `BstrTranKindLimitRequestDto.java:57-58` |
| `arrivalTerminal` | `String` | Arrival terminal (when there is no receipt ID) | `BstrTranKindLimitRequestDto.java:59-60` |
| `departureDate` | `LocalDate` | Departure date (when there is no receipt ID) | `BstrTranKindLimitRequestDto.java:61-62` |
| `returnDate` | `LocalDate` | Return date (when there is no receipt ID) | `BstrTranKindLimitRequestDto.java:63-64` |

> ⚠️ The actual behavior and required combinations of these 6 fields could not be confirmed within the source files covered by this section → [§2.8](#28--open-items).

---

## 2.3  How to fill `calcInputs`

`CalcConditionInputs` (`renewal-limit-api.ts:103-149`) — **12 fields**.
Each field maps 1:1 to a "condition item" on the renewal rule's "calculation basis" tab, and the backend uses these values to match a condition block's `items[].itemType` / `itemValue` (`renewal-limit-api.ts:99-101`, `CalcConditionInputDto.java:14-15`).

**The backend's `isItemMatched` switch is the source of truth for the mapping between match items and input fields** (`BstrRenewalLimitService.java:316-354`).

| Field | Type | Required/nullable | Matched `itemType` (both uppercase and abbreviation accepted) | Source — what fills it |
|------|------|---------------|--------------------------------------------|--------------------------|
| `isMealTwiceOrMore` | `boolean` | Optional | `YESNO_MEAL_TWICE` / `c1` (`BstrRenewalLimitService.java:320`) | **The current client does not fill this** (not set in `collectCalcInputs` — `calc-inputs.ts:42-82`). Source ⚠️unconfirmed |
| `isLodging` | `boolean` | Optional | `YESNO_LODGING` / `c2` (`:321`) | **The current client does not fill this.** Source ⚠️unconfirmed |
| `isFullDayDeparture` | `boolean` | Optional | `YESNO_FULL_DAY_DEPARTURE` / `c5` (`:322`) | **The current client does not fill this.** Full-day departure is instead passed via the `exceptionRuleInputItemIds` path (`calc-inputs.ts:196-204`, `:262-264`) → [§2.3.3](#233-the-two-paths-for-full-day-departure-and-next-day-arrival) |
| `isNextDayArrival` | `boolean` | Optional | `YESNO_NEXT_DAY_ARRIVAL` / `c6` (`:323`) | **The current client does not fill this.** Same as above |
| `transportModes` | `string[]` | Optional | `TRANSPORT` / `transport` (`:324`) | **Document** — each route row's (`bstrRoutes`) `transportType` converted to a token and deduplicated (`calc-inputs.ts:168-175`). The tokens carry **both the new optionKey and the old 3-bucket labels** (`transport-vocab.ts:74-82`) — the old labels are `렌트카` (rental car), `회사차` (company car), and `대중교통` (public transport), while the new keys are `rentalCar`, `businessCar`, `corpCar`, `mobility`, `airline`, `train`, `bus`, `publicTransport`, `cityTransport` (`transport-vocab.ts:61-65`). If there are no values, the field itself is omitted (`calc-inputs.ts:65-67`) |
| `departureTime` | `string \| null` (`HH:mm`) | Optional | `TIME_CONDITION`/`timeCondition` (duration axis, `:327-328`) and `DEPART_TIME`/`departTime` (absolute-time axis, `:330-331`) | **Document + form settings** — filled only when the form's `bstrPeriodTimeUsed` is `true`. `HH:mm` is extracted from the **`selectionName` of the first selection** of the `BSTR_PERIOD` input item (`calc-inputs.ts:45-56`). Extraction uses the **local** hour and minute of `new Date(value)` (`calc-inputs.ts:20-27`); if the string length is 10 or less (date-only) the result is `null` (`calc-inputs.ts:21`) |
| `arrivalTime` | `string \| null` (`HH:mm`) | Optional | Same as above (`ARRIVE_TIME`/`arriveTime`, `:332-333`) | **Document + form settings** — extracted from the **`selectionErpCode` of the last selection** of `BSTR_PERIOD` (`calc-inputs.ts:50`) |
| `bstrPeriodTimeRepeatUsed` | `boolean` | Optional | (An input flag — not an `itemType` match target; it switches the backend's duration calculation mode) | **Form settings** — `paper.bstrPeriodTimeRepeatUsed`. When `true`, the backend calculates departure-to-return times as **repeating daily (one day's duration)** (`calc-inputs.ts:59-61`, `renewal-limit-api.ts:127-128`). When `false`, the field is omitted |
| `departmentId` | `number \| null` | Optional | `DEPARTMENT` / `department` (`:326`) | **The current client does not fill this.** Source ⚠️unconfirmed |
| `companyCode` | `string \| null` | Optional | `COMPANY` / `company` (`:325`) | **The current client does not fill this.** Source ⚠️unconfirmed |
| `destinationIds` | `number[]` | Optional | `DESTINATION` / `destination` (`:343-345`) | **Document** — the `departureId` and `arrivalId` of every point on the route, deduplicated (`calc-inputs.ts:110-118`). Used for set comparison in round-trip mode and for the count determination in destination mode (`renewal-limit-api.ts:137-141`). Omitted when the array is empty (`calc-inputs.ts:70-73`) |
| `visitedDestinationCount` | `number` | Optional | `DESTINATION` (the "only when there is 1" determination in dest mode, `:343-345`) | **Document** — the number of points actually visited, **excluding the departure and return points**. The point identity key is `id:{n}` when an `id` exists, otherwise `name:{name or address}` (`calc-inputs.ts:139-143`). After sorting by `routeOrder`, the first leg's departure and the last leg's arrival are removed from the set (`calc-inputs.ts:134-157`). **Omitted when 0** — which produces the same result as the backend falling back to the registered destination count (`calc-inputs.ts:75-80`) |

### 2.3.1  `collectCalcInputs` omission rules (important)

`collectCalcInputs` **returns `undefined` when not a single field is filled**, and the caller passes that straight into the request, so the `calcInputs` key disappears entirely (`calc-inputs.ts:33-34,82`).

| Condition | Result |
|------|------|
| No time, transport mode, or destination at all | `calcInputs` not sent → **base-amount mode** |
| At least one present | An object containing only those fields is sent |
| The backend receives `calcInputs` as `null` | **It is replaced by an empty object** and condition matching still proceeds — conditions that need no input (`PURPOSE`, dates, etc.) match normally (`BstrRenewalLimitService.java:85-89`) |

> In other words, even if you never send `calcInputs`, conditions can still match on a `calcEnabled` rule.
> However, conditions that require input (`YESNO_*`, `TRANSPORT`, time, destination) will not match.

### 2.3.2  `exceptionRuleInputItemIds` collection rules

`collectExceptionRuleInputItemIds` (`calc-inputs.ts:189-205`) — **four kinds of ID are merged into a single array** (deduplicated).

| # | Input item `itemType` | Condition | Value collected | Source |
|---|---------------------|------|---------|------|
| 1 | `BSTR_SELECT` | (No condition; all items, however many) | The selected option's **`selection.selectionId`** | `calc-inputs.ts:190-193` |
| 2 | `EXPENSE_BEYOND_BSTR_PERIOD` | `value === 'true'` | **`item.id`** | `calc-inputs.ts:196-202` |
| 3 | `HOMETOWN` (hometown/home region, dormitory application) | `value === 'true'` | **`item.id`** | `calc-inputs.ts:196-202` |
| 4 | `REQUEST_STAFF_LODGE` (employee dormitory application) | `value === 'true'` | **`item.id`** | `calc-inputs.ts:196-202` |

**Note** — #1 uses `selectionId` while #2 through #4 use `item.id`. IDs from different axes are mixed into one array.
Unchecked items, `null`s, and other types are excluded, and those are then also excluded from the backend's exception-rule matching (`matchDynamicInputItem`) (`calc-inputs.ts:187`).
The backend consumes this array in the **`default` branch** of `isItemMatched` (`BstrRenewalLimitService.java:353`).

### 2.3.3  The two paths for full-day departure and next-day arrival

The same fact ("full-day departure is checked") can reach the backend by **two paths**.

| Path | Field | Matching | Current client |
|------|------|------|-----------------|
| A | `calcInputs.isFullDayDeparture` / `isNextDayArrival` | `YESNO_FULL_DAY_DEPARTURE` / `YESNO_NEXT_DAY_ARRIVAL` (`BstrRenewalLimitService.java:322-323`) | **Not used** |
| B | The `item.id` of the `EXPENSE_BEYOND_BSTR_PERIOD` item in `exceptionRuleInputItemIds` | `matchDynamicInputItem` (default branch, `BstrRenewalLimitService.java:353`) | **This is the path in use** (`calc-inputs.ts:196-204`) |

Path A's condition **cannot hold** without input — `matchYesNo` returns `false` when `inputValue == null` (`BstrRenewalLimitService.java:456-460`). The condition value strings are `"해당"` (applicable) and `"미해당"` (not applicable) (`:458-459`).

Separately, the client reads the same input item once more for **travel-day calculation (engine `opts`)**:
`item.erpCode === 'PREDEPART'` → `pre=1`, `'NEXTARRIVE'` → `post=1` (`calc-inputs.ts:221-222,262-268`).
That value is used in the §3 condition-application step and does not go into the request payload.

### 2.3.4  Multiple grade-zone segments — `applySelectionTime`

When one expense report has multiple trip-period segments (grade zones), the lookup is **performed separately per segment** while sending that segment's times (`calc-inputs.ts:94-104`; used at `ruled-amount-calculator.ts:281-284`).

| Situation | Behavior | Source |
|------|------|------|
| `bstrPeriodTimeUsed === false` | Returns `baseInputs` unchanged (no reassignment) | `calc-inputs.ts:99` |
| The selection has neither a departure nor an arrival time | Returns `baseInputs` unchanged (global fallback) | `calc-inputs.ts:102` |
| At least one time present | `{...baseInputs, departureTime, arrivalTime}` — **both values are overwritten together** (if only one is present, the other becomes `null`) | `calc-inputs.ts:100-103` |

`collectCalcInputs` builds a global time (first row's start to last row's end), but a per-segment lookup must match `timeCondition` against that segment's stay duration, hence the reassignment (`calc-inputs.ts:86-92`).

### 2.3.5  `extractActivityDivision`

| Item | Value | Source |
|------|-----|------|
| Input item read | The `value` of `itemType === 'ACTIVITY_EXPENSE_TYPE'` | `calc-inputs.ts:215-216` |
| Return | `'ACTUAL'` or `'FIXED'` as-is; anything else or a missing item → `null` | `calc-inputs.ts:217` |
| Where it goes | Not `calcInputs` but the **top-level request field `activityDivision`** | `renewal-limit-api.ts:70`, `ruled-amount-calculator.ts:205-208` |
| When not sent | The backend applies no matching (everything passes) | `renewal-limit-api.ts:67-68`, `calc-inputs.ts:212` |

---

## 2.4  Complete response field table

`RenewalLimitResponse` (`renewal-limit-api.ts:305-375`) — **23 fields**.
The wire response body is the backend's `BstrTranKindLimitDto` (`BstrPolicyRenewalController.java:45`).

| Field | Type | Required/nullable | Meaning | Backend counterpart |
|------|------|---------------|------|-------------|
| `id` | `number` | Optional | Rule item ID (`renewal-limit-api.ts:306-307`) | `Long id` (`BstrTranKindLimitDto.java:24`) |
| `limitAmount` | `number` | Optional | **The daily base limit amount.** The comment says "the final value after CalcCondition application," but the client applies the conditions → [§2.5](#25--limitamounts-is-the-base-amount-core) (`renewal-limit-api.ts:308-309`) | `Double limitAmount` (`:31`). It is the rule amount only when the payment basis is `LIMITED`, `FIXED`, or `ACTUAL_FIXED`; otherwise `0.0` (`BstrTranKindLimitDto.java:89-93`) |
| `limitAmounts` | `Record<string, number>` | Optional | **The key return value** — a per-date limit-amount map keyed by `"YYYY-MM-DD"` (`renewal-limit-api.ts:310-315`) | `Map<LocalDate, Double> limitAmounts` (`:53`) |
| `payType` | `string` | Optional | Payment method (`BASIC`=base unit price / `DIFF`=tiered payment). **With `DIFF` the scalar `limitAmount` is 0 and the tiered amounts exist only in `limitAmounts`** (`renewal-limit-api.ts:316-321`) | `PayType payType` (`:34`). When null it is filled in as `BASIC` before being returned (`BstrTranKindLimitDto.java:119`) |
| `bstrPayClassType` | `string` | Optional | Payment classification (`FIXED`, `ACTUAL`, `ACTUAL_LIMIT`, `NONE`, etc.) (`renewal-limit-api.ts:322-323`) | `BstrPayClassType bstrPayClassType` (`:35`). ⚠️ The client comment's `ACTUAL_LIMIT` differs from the `LIMITED` and `ACTUAL_FIXED` that appear in backend code (`BstrTranKindLimitDto.java:89-91`) — the full enum list was ⚠️unconfirmed at the time of writing (now settled; see §2.8-0) |
| `bstrPayOptionType` | `string` | Optional | Payment option (`ALL`, `EXCEPT_START`, `EXCEPT_END`, `FIX_ONE_DAY`, etc.) (`renewal-limit-api.ts:324-325`) | `BstrPayOptionType bstrPayOptionType` (`:36`) |
| `currencyCode` | `string` | Optional | Currency code (`renewal-limit-api.ts:326-327`) | `CurrencyCode currencyCode` (`:30`). Anything other than `KRW` and non-empty is treated as foreign currency (`currency-utils.ts:19-22`) |
| `tranKindType` | `string` | Optional | Purpose type (`renewal-limit-api.ts:328-329`) | `TranKindType tranKindType` (`:26`) |
| `tranKindId` | `number` | Optional | Purpose ID (`renewal-limit-api.ts:330-331`) | `Long tranKindId` (`:27`) |
| `tranKindIds` | `number[]` | Optional | List of linked purpose IDs (`renewal-limit-api.ts:332-333`) | `List<Long> tranKindIds` (`:28`). When null it is filled in as an empty list before being returned (`BstrTranKindLimitDto.java:110-114`) |
| `bstrCategoryType` | `string` | Optional | Payment-basis category (`MONEY` \| `GRADE` \| `STAR`) — identifies the transportation grade system and lodging star system (`renewal-limit-api.ts:334-335`) | `BstrCategoryType bstrCategoryType` (`:37`). Its origin is the entity's `bstrStandardType` (`BstrTranKindLimitDto.java:123`) |
| `bstrTransportGradeType` | `string` | Optional | Transportation grade. **It is a transport-mode suffix scheme** — `ECONOMY_AIR`, `PREMIUM_ECONOMY_AIR`, `BUSINESS_AIR`, `FIRST_AIR`, `ECONOMY_KTX`, `FIRST_KTX`, `GENERAL_BUS`, `ECONOMY_CBUS`, … (source of truth `common-core/.../pconstant/BstrTransportGradeType.java:10-30`). ⚠️ Suffix-less values such as `FIRST_CLASS` and `ECONOMY` **do not exist** | `BstrTransportGradeType bstrTransportGradeType` (`:38`) |
| `starGradeLimit` | `number \| null` | Optional + nullable | Star-rating cap (1–5) — when the lodging payment basis is `STAR`, restricts the receipt's star option to at most this value (`renewal-limit-api.ts:338-339`) | `Integer starGradeLimit` (`:40`) |
| `exceptionReasonUsed` | `boolean \| null` | Optional + nullable | Whether exception-reason entry is used — lifts the star/grade cap and reveals the receipt's "exception reason" field (`renewal-limit-api.ts:340-341`) | `boolean exceptionReasonUsed` (**primitive** — the backend never returns null, `:41`) |
| `exceptionReasonRequired` | `boolean \| null` | Optional + nullable | Exception reason **always required** (`renewal-limit-api.ts:342-343`) | `boolean exceptionReasonRequired` (primitive, `:42`) |
| `exceptionReasonConditionalRequired` | `boolean \| null` | Optional + nullable | Exception reason **conditionally required** — only when the selected star/grade exceeds the rule cap. Mutually exclusive with `exceptionReasonRequired` (`renewal-limit-api.ts:344-345`) | `boolean exceptionReasonConditionalRequired` (primitive, `:43`) |
| `baseAmount` | `number` | Optional | The base amount before CalcCondition application — the original amount determined from the BstrExpense row (`renewal-limit-api.ts:349-353`) | **⚠️ There is no corresponding field in `BstrTranKindLimitDto`** (verified exhaustively at `BstrTranKindLimitDto.java:24-72`) — it appears never to be filled in this API response → [§2.8](#28--open-items) |
| `dayTypeMap` | `Record<string, string>` | Optional | The day-type of each date in the trip period. Values are `"평일"` (weekday), `"공휴일"` (holiday), or `"주말"` (weekend) (**Korean strings**) (`renewal-limit-api.ts:354-355`) | `Map<LocalDate, String> dayTypeMap` (`:70`). Construction rule: holiday (`KR`) first → weekend → weekday (`BstrRenewalLimitService.java:216-231`) |
| `calcEnabled` | `boolean` | Optional | Whether the calculation formula is used (`renewal-limit-api.ts:356-357`) | `boolean calcEnabled` (primitive, `:67`). **When `false`, the backend returns only the base amount before condition matching** (`BstrRenewalLimitService.java:81-84`) |
| `appliedConditions` | `AppliedCalcCondition[]` | Optional | **The list of matched conditions** (in `sortOrder` order). When present, the client applies them per day (`renewal-limit-api.ts:358-359`) → [§2.4.1](#241-appliedconditions-element--appliedcalccondition) | `List<AppliedConditionDto> appliedConditions` (`:72`). **Unmatched conditions are excluded from the response** (`BstrRenewalLimitService.java:195-205`) |
| `calcBreakdown` | `CalcBreakdown` | Optional | The basis for the ruled-amount calculation (a condition summary). **Injected at the client boundary conversion, not by the backend** (`renewal-limit-api.ts:360-361`) → [§2.4.4](#244-calcbreakdown-and-its-elements) | No backend counterpart |
| `calcDeferred` | `boolean` | Optional | **A marker that engine application is deferred.** When `true`, `limitAmounts` is raw and only `calcBreakdown` has been set (`renewal-limit-api.ts:362-367`) → [§2.6](#26--what-the-client-does-immediately-after-the-lookup--apply-or-defer) | No backend counterpart (a client stamp) |
| `calcPeriod` | `{ start: string; end: string }` | Optional | The **absolute date axis** for formula determination — the request's `bstrDate` to `bstrEndDate`. **This period, not the `limitAmounts` map, is the source of truth for "departure day / arrival day"** (`renewal-limit-api.ts:368-374`) | No backend counterpart (a client stamp, `renewal-limit-api.ts:480`) |

### 2.4.0  Fields the backend sends in addition (not declared in the client interface)

The wire response is **wider** than the 23 fields above. There are **21** fields present in `BstrTranKindLimitDto` but not declared by `RenewalLimitResponse`. An external system reading the JSON directly will receive these as well.

| Backend field | Type | Meaning | Source |
|-------------|------|------|------|
| `bstrPolicyType` | `BstrPolicyType` | Rule type | `BstrTranKindLimitDto.java:25` |
| `vehicleType` | `VehicleType` | The transport mode of the matched rule row | `:29` |
| `firstDayAmount` | `Double` | First-day amount (the tiered value when `payType=DIFF`, otherwise the same as `limitAmount`) | `:32`, `BstrTranKindLimitDto.java:94-97` |
| `lastDayAmount` | `Double` | Last-day amount (same rule) | `:33`, `BstrTranKindLimitDto.java:99-102` |
| `policyAmountUsage` | `boolean` | Whether the rule amount is used | `:44` |
| `dayOptionUsage` | `boolean` | Whether the day-count option is used | `:45` |
| `dateOptionUsage` | `boolean` | Whether the date option is used | `:46` |
| `customStartDate` | `LocalDate` | Rule application start date | `:47` |
| `customEndDate` | `LocalDate` | Rule application end date | `:48` |
| `bstrDayOptionType` | `BstrDayOptionType` | Day-count option type | `:49` |
| `nDays` | `Double` | Trip day-count reference value | `:50` |
| `nDaysOptionLast` | `boolean` | (Currently hard-coded `true` — marked with a `todo` comment) | `:51`, `BstrTranKindLimitDto.java:139` |
| `nullCount` | `Long` | The **number of empty conditions** on the rule row — used for specificity priority | `:52`, `BstrTranKindLimitDto.java:75-88` |
| `itemId` | `Long` | Linked input item ID | `:54` |
| `inputItem` | `boolean` | Whether input-item linkage is enabled | `:55` |
| `bstrAreaId` | `Long` | The grade zone ID **of the matched rule row** | `:56` |
| `userGroupId` | `Long` | **The traveler's user group ID (resolved at runtime).** Distinct from the rule row's `userGroup` — used for exception-condition `group` matching | `:57-58` |
| `tripAreaId` | `Long` | **The trip's grade zone ID (resolved from `bstrRegionId`).** Distinct from the rule row's `bstrAreaId` — used for exception-condition `grade` matching | `:59-60` |
| `userGroupName` | `String` | For display — the traveler's user group name | `:61-62` |
| `tripAreaName` | `String` | For display — the trip's grade zone name | `:63-64` |
| `sortOrder` | `Long` | Rule row sort order (`9999` when null) | `:65`, `BstrTranKindLimitDto.java:141` |

> **`userGroupId` and `tripAreaId` are both inputs to condition matching and values returned in the response.**
> The backend pulls these two out of `baseResult`, passes them to `buildAppliedConditions` (`BstrRenewalLimitService.java:144`), and compares them against `GROUP`/`GRADE` conditions (`BstrRenewalLimitService.java:349-350`).
> They are **values the backend resolves at runtime**, not values the client sends, so an external system cannot put them in the request and can only read them from the response.

### 2.4.1  `appliedConditions[]` element — `AppliedCalcCondition`

The zod schema `AppliedCalcConditionSchema` (`renewal-limit-api.ts:223-248`) — **17 fields**, `.passthrough()`.

The nullability contract was made to **match the backend's `AppliedConditionDto` / `BstrCalcCondition` exactly** (`renewal-limit-api.ts:216-221`): Java primitives (`int`, `boolean`) are required, and all boxed/object types are `.nullish()`.
Since `AppliedConditionDto` has no `@JsonInclude(NON_NULL)`, null fields serialize as `"field": null`, so a strict schema would **reject a perfectly normal response** such as "weekday +10,000" (`renewal-limit-api.ts:218-221`).

| Field | zod type | Optional/nullable | Meaning | Backend counterpart |
|------|----------|-------------------|------|-------------|
| `id` | `z.number().nullish()` | **nullish** | Condition block ID (`renewal-limit-api.ts:225`) | `Long id` (`AppliedConditionDto.java:22`) |
| `sortOrder` | `z.number()` | **required** | Application order (`renewal-limit-api.ts:226`) | `int sortOrder` (primitive, `:23`) |
| `calcMethod` | `z.string().nullish()` | nullish | Calculation method. Values: `daily` \| `dailyDiff` \| `departArrive` \| `excludeDepart` \| `excludeArrive` \| `excludeN` (`renewal-limit-api.ts:227`) | `String calcMethod` (`:24`) |
| `operator` | `z.string().nullish()` | nullish | Operator. Values: `none` \| `fixed` \| `unpaid` \| `+` \| `-` \| `x` \| `/` (`renewal-limit-api.ts:228`) | `String operator` (`:25`) |
| `operatorValue` | `z.string().nullish()` | nullish | Operand value (`renewal-limit-api.ts:229`) | `String operatorValue` (`:26`) |
| `operatorUnit` | `z.string().nullish()` | nullish | Operand unit. Values: `AMOUNT` \| `PERCENT` (`renewal-limit-api.ts:230`) | `String operatorUnit` (`:27`) |
| `fixedAmount` | `z.number().nullish()` | nullish | Fixed amount (`renewal-limit-api.ts:231`) | `Long fixedAmount` (`:28`) |
| `fixedCurrency` | `z.string().nullish()` | nullish | Fixed-amount currency (`renewal-limit-api.ts:232`) | `String fixedCurrency` (`:29`) |
| `diffFirst` | `z.string().nullish()` | nullish | Tiered — first day (`renewal-limit-api.ts:233`) | `String diffFirst` (`:30`) |
| `diffMid` | `z.string().nullish()` | nullish | Tiered — middle days (`renewal-limit-api.ts:234`) | `String diffMid` (`:31`) |
| `diffLast` | `z.string().nullish()` | nullish | Tiered — last day (`renewal-limit-api.ts:235`) | `String diffLast` (`:32`) |
| `excludeDays` | `z.number().nullish()` | nullish | Excluded day count (the `excludeN` family) (`renewal-limit-api.ts:236`) | `Integer excludeDays` (`:33`) |
| `details` | `CalcConditionDetailsSchema.nullish()` | nullish | Detail-condition JSON → [§2.4.2](#242-details--calcconditiondetails) (`renewal-limit-api.ts:237`) | `Map<String, Object> details` (`:34`) — **free-form JSON** |
| `items` | `z.array(AppliedCalcConditionItemSchema).nullish()` | nullish | Condition items (AND-combined) → [§2.4.3](#243-items-element--appliedcalcconditionitem) (`renewal-limit-api.ts:238`) | `List<ItemDto> items` (`:35`). The DTO conversion turns null into `List.of()` (`AppliedConditionDto.java:71`), but the client accepts null too and the engine absorbs it with `?? []` (`renewal-limit-api.ts:249`) |
| `matched` | `z.boolean()` | **required** | Whether all condition items (AND) matched. **A backend determination — the client only trusts it** (`renewal-limit-api.ts:213-214,239`) | `boolean matched` (primitive, `:37`) |
| `overridden` | `z.boolean()` | **required** | A **summary value** meaning "an overriding higher-priority condition exists" (= `supersededByIds` is non-empty). **It does not mean "globally not applied"** (`renewal-limit-api.ts:240-242`) | `boolean overridden` (primitive, `:48`) |
| `supersededByIds` | `z.array(z.number()).nullish()` | nullish | The list of **higher-priority condition IDs** that strict-superset (are more specific than) this condition and take precedence (`renewal-limit-api.ts:243-246`) | `List<Long> supersededByIds` (`:56`). Older backends do not send it |

**Misreading `overridden` will throw off the amount.**
The engine interprets this relationship **per day** — if any higher-priority condition is applicable that day, this condition yields; if all higher-priority conditions are inapplicable that day, this condition takes over (fallback). In other words, **even with `overridden=true` the condition still applies on certain dates** (`renewal-limit-api.ts:240-245`, `AppliedConditionDto.java:38-54`).

### 2.4.2  `details` — `CalcConditionDetails`

`CalcConditionDetailsSchema` (`renewal-limit-api.ts:161-201`) — **10 declared keys**, `.passthrough()`.
On the backend side it is free-form JSON as `Map<String, Object>` (`AppliedConditionDto.java:34`).

| Key | zod type | Optional/nullable | Meaning |
|----|----------|-------------------|------|
| `transport` | `z.object({ values: z.array(z.string()) }).optional()` | Optional (`values` is required inside) | List of transport-mode condition values (`renewal-limit-api.ts:163`) |
| `timeCondition` | `z.object({...}).optional()` | Optional | Duration condition. All 5 inner fields are **required**: `fromHour: number`, `fromOp: string` (`'gte'`\|`'gt'`), `toHour: number`, `toOp: string` (`'lt'`\|`'lte'`), `includeLunch: boolean` (`renewal-limit-api.ts:164-172`) |
| `diffType` | `z.string().nullish()` | nullish | Tiered-calculation mode. The engine only compares `=== 'amt'` (`renewal-limit-api.ts:179`) |
| `diffBaseAmt` | `z.number().nullish()` | nullish | Tiered base amount (Long, ± allowed) (`renewal-limit-api.ts:180`) |
| `diffCurrency` | `z.string().nullish()` | nullish | Tiered currency (`renewal-limit-api.ts:181`) |
| `diffFirstAmt` | `z.number().nullish()` | nullish | Tiered first-day amount (`renewal-limit-api.ts:182`) |
| `diffMidAmt` | `z.number().nullish()` | nullish | Tiered middle-day amount (`renewal-limit-api.ts:183`) |
| `diffLastAmt` | `z.number().nullish()` | nullish | Tiered last-day amount (`renewal-limit-api.ts:184`) |
| `dateRange` | `z.object({ from, to }).nullish()` | nullish (`from` and `to` are each `z.string().nullish()`) | Date option — the per-day application range (`'YYYY-MM-DD'`). The engine applies the condition only on days falling in `[from, to]` (`renewal-limit-api.ts:185-191`) |
| `periodRange` | `z.object({ op, value }).nullish()` | nullish (`op` and `value` are each `z.string().nullish()`) | Period option — application by trip day number. `op` is `'gte'`\|`'gt'`\|`'lte'`\|`'lt'` and `value` is the reference day count (as a string) (`renewal-limit-api.ts:192-199`) |

**Undeclared keys that pass through via `.passthrough()`** — the backend matcher also reads `details` keys absent from the zod schema (`BstrRenewalLimitService.java:324-350`): `company` (`:325`), `department` (`:326`), `departTime` (`:331`), `arriveTime` (`:333`), `destination` (`:344`), `distance` (`:347`), `group` (`:349`), and `grade` (`:350`).
⚠️ The **exact JSON shape** of these keys could not be verified exhaustively within the source files covered by this section → [§2.8](#28--open-items).

The reason `diffType` was not narrowed to `z.enum(['pct','amt'])` is **fail-open consistency** — an unexpected casing or value would fail the entire `safeParse`, causing the whole response to pass through unmodified (`renewal-limit-api.ts:176-178`).

### 2.4.3  `items[]` element — `AppliedCalcConditionItem`

`AppliedCalcConditionItemSchema` (`renewal-limit-api.ts:205-209`) — **3 fields, all required**.

| Field | zod type | Optional/nullable | Meaning | Backend counterpart |
|------|----------|-------------------|------|-------------|
| `itemType` | `z.string()` | **required** | Condition item kind. Examples: `purpose` \| `dayType` \| `transport` \| `timeCondition` \| `YESNO_*` (`renewal-limit-api.ts:206`). The backend **supports both** uppercase and lowercase/abbreviated forms (`BstrRenewalLimitService.java:315-316`) | `String itemType` (`AppliedConditionDto.java:63`) |
| `itemValue` | `z.string()` | **required** | Condition item value (`renewal-limit-api.ts:207`) | `String itemValue` (`:64`) |
| `sortOrder` | `z.number()` | **required** | Item sort order (`renewal-limit-api.ts:208`) | `int sortOrder` (primitive, `:65`) |

> **Note** — if `itemType` or `itemValue` arrives as `null`, `safeParse` fails and the entire response falls back to a conditions-not-applied pass-through (`renewal-limit-api.ts:446-447`). The item array itself being `null` is allowed (`items` is `.nullish()`).

**Complete list of `itemType` values the backend recognizes** (`BstrRenewalLimitService.java:316-354`)

| `itemType` (uppercase / lowercase-abbrev) | Matching input | Source line |
|-----------------------------------|-----------|---------|
| `PURPOSE` / `purpose` | Request `bstrPurposeId` → purpose name | `:317` |
| `SEGMENT` / `segment` | Request `bstrSegmentId` → classification name | `:318` |
| `DAY_TYPE` / `dayType` | **Always `true`** (the client handles it during per-day application) | `:319` |
| `YESNO_MEAL_TWICE` / `c1` | `calcInputs.isMealTwiceOrMore` | `:320` |
| `YESNO_LODGING` / `c2` | `calcInputs.isLodging` | `:321` |
| `YESNO_FULL_DAY_DEPARTURE` / `c5` | `calcInputs.isFullDayDeparture` | `:322` |
| `YESNO_NEXT_DAY_ARRIVAL` / `c6` | `calcInputs.isNextDayArrival` | `:323` |
| `TRANSPORT` / `transport` | `details.transport` × `calcInputs.transportModes` | `:324` |
| `COMPANY` / `company` | `details` × `calcInputs.companyCode` | `:325` |
| `DEPARTMENT` / `department` | `details` × `calcInputs.departmentId` | `:326` |
| `TIME_CONDITION` / `timeCondition` | `details` × duration and lunch overlap | `:327-328` |
| `DEPART_TIME` / `departTime` | `details.departTime` × `calcInputs.departureTime` (**absolute-time axis**) | `:330-331` |
| `ARRIVE_TIME` / `arriveTime` | `details.arriveTime` × `calcInputs.arrivalTime` | `:332-333` |
| `LUNCH_TIME` / `lunchTime` | Lunch overlap hours > 0 (an independent condition; presence means it fires) | `:334-337` |
| `PERIOD_RANGE` / `periodRange` | `details.periodRange` × trip day count | `:339` |
| `DATE_RANGE` / `dateRange` | `details.dateRange` × trip start and end dates | `:341` |
| `DESTINATION` / `destination` | `details` × departure and destination IDs + `destinationIds` + `visitedDestinationCount` | `:343-345` |
| `DISTANCE` / `distance` | `details.distance` × request `totalDistance` | `:347` |
| `GROUP` / `group` | `details.group.values` × response `userGroupId` | `:349` |
| `GRADE` / `grade` | `details.grade.values` × response `tripAreaId` | `:350` |
| `DAY_INDEX` / `dayIndex` | **Always `true`** — ⚠️ **this item is unimplemented.** The backend passes it unconditionally and the client has no guard either (there is no `dayIndex` handling code anywhere in `bstr-policy` — only label strings) → **the day option is currently inert and applies to every day.** Do not interpret it as a day-number restriction | `:352` |
| (Everything else) | Compared against `exceptionRuleInputItemIds` (`matchDynamicInputItem`) | `:353` |

> **★ The matching-key convention of the default branch — external callers must know this.**
> This branch **parses the condition's `itemType` as a number** and checks whether that value is present in `exceptionRuleInputItemIds` (`BstrRenewalLimitService.java:441-450`). In other words, **`itemType` must be the numeric ID string of that input item** (e.g. `"15802"`) for it to hold; if it is not numeric, a `NumberFormatException` yields `false`.
> If a response's `items[].itemType` looks like a numeric string, it is **this path**, not one of the fixed keys above, and you must put that ID into the request's `exceptionRuleInputItemIds` for the condition to match. `itemValue` is for display.
> See [§2.3.2](#232-exceptionruleinputitemids-collection-rules) for which items take this path.

### 2.4.4  `calcBreakdown` and its elements

**The backend does not send this field** — the client injects it after applying the conditions, as a record of how the amount was derived (`renewal-limit-api.ts:360-361`; injection points at `:454-455,457`). An external system assembling this itself must build it on its own.

`CalcBreakdownSchema` (`renewal-limit-api.ts:287-294`) — **3 fields, all required**.

| Field | zod type | Optional/nullable | Meaning |
|------|----------|-------------------|------|
| `baseAmount` | `z.number()` | **required** | Base amount (before condition application) (`renewal-limit-api.ts:289-290`) |
| `items` | `z.array(CalcBreakdownItemSchema)` | **required** | The summary list of applied conditions (`renewal-limit-api.ts:291`) |
| `totalAmount` | `z.number()` | **required** | The **sum** of the per-day final amounts (`renewal-limit-api.ts:292-293`) |

`CalcBreakdownItemSchema` (`renewal-limit-api.ts:253-280`) — **5 fields**.

| Field | zod type | Optional/nullable | Meaning |
|------|----------|-------------------|------|
| `label` | `z.string()` | **required** | Condition label (e.g. `"평일"` weekday, `"국내출장·렌트카"` domestic trip/rental car) (`renewal-limit-api.ts:254-255`) |
| `effect` | `z.string()` | **required** | Effect notation (e.g. `"+10,000원"`, `"변경없음"` no change, `"×50%"`) (`renewal-limit-api.ts:256-257`) |
| `detailLabel` | `z.string().optional()` | Optional | Detail label — includes the configured value of yes/no items, joining multiple items with `', '` (e.g. `"전일출발 Yes, 합숙여부 No"`) (`renewal-limit-api.ts:258-263`) |
| `detailLabelItems` | `z.array(z.string()).optional()` | Optional | The per-item elements of `detailLabel` **before joining**. **Do not re-split `detailLabel` on `', '`** — transport modes join their own values with `', '`, so one item would break into two (`renewal-limit-api.ts:264-270`) |
| `formula` | `z.string().optional()` | Optional | The formula notation — the payment period plus the derived value on one line (e.g. `"출발일·종료일 지급. 정액 20,000 KRW"` paid on departure and end days, fixed 20,000 KRW). It is **deliberately different** from `effect` (`renewal-limit-api.ts:271-279`) |

### 2.4.5  Response boundary validation schema — what is validated

The client does not schema-validate the entire response. `RenewalLimitCalcFieldsSchema` (`renewal-limit-api.ts:382-388`) validates **only the 3 fields the engine traverses and consumes** and lets everything else through via `.passthrough()` (`renewal-limit-api.ts:377-381`).

| Validated | zod type |
|-----------|----------|
| `calcEnabled` | `z.boolean().optional()` |
| `dayTypeMap` | `z.record(z.string(), z.string()).nullish()` |
| `appliedConditions` | `z.array(AppliedCalcConditionSchema).nullish()` |

On validation failure the client **skips the engine and returns the original response unchanged** — preventing double calculation and throws (`renewal-limit-api.ts:400-403,446-447`).

---

## 2.5  ★ `limitAmounts` is the base amount (core)

> **This section is the core of the entire package.** If an external system uses `limitAmounts` as the final ruled amount, it will put an amount into the expense report with the condition adjustments missing entirely.

### Quoting the backend comments directly

**`BstrRenewalLimitService.java:145-151`** (the tail of `calculateLimit`)

```java
        Map<LocalDate, String> dayTypeMap = buildDayTypeMap(limitAmounts.keySet());

        baseResult.setAppliedConditions(appliedConditions);
        baseResult.setDayTypeMap(dayTypeMap);
        // limitAmounts stays as the base amount (the front end applies conditions to derive the final amount)

        return baseResult;
```

**`BstrRenewalLimitService.java:156-161`** (the `buildAppliedConditions` Javadoc)

```java
    /**
     * Matches CalcCondition blocks and builds the condition list to pass to the front end.
     *
     * <p>Previously the backend calculated the amount directly. Now it passes only the condition
     * information to the front end, and the front end applies the conditions to the base amount
     * in sortOrder order to derive the final amount.</p>
     */
```

The same determination appears once more in the body of `calculateLimit` — **`BstrRenewalLimitService.java:116`**

```java
        // 6. CalcCondition matching → pass the conditions to the front end (calculation is done on the front end)
```

### Stated as a contract

| Layer | Who | What | Source |
|----|------|--------|------|
| ① Raw data lookup | **Backend** | Selects the rule row → per-date **base amount** `limitAmounts` + day types `dayTypeMap` + **matched conditions** `appliedConditions` | `BstrRenewalLimitService.java:71-152` |
| ② Condition application | **Client** | Applies the conditions to the base amount per day in `sortOrder` order → the final amount | `BstrRenewalLimitService.java:159-160`, `renewal-limit-api.ts:358` (§3) |
| ③ Validation | Client | Compares the final amount against the receipt amount | (§4 onward) |

### Early-return points where the backend sends no conditions

`calculateLimit` returns `baseResult` containing **only the base amount** under the conditions below.
In these cases `appliedConditions` and `dayTypeMap` are not filled, so the client can use `limitAmounts` as-is.

| # | Condition | Return | Source |
|---|------|------|------|
| 1 | The existing logic finds no rule row (`baseResult == null`) | **`null`** (no rule exists) | `BstrRenewalLimitService.java:77-79` |
| 2 | The matched row's `calcEnabled == false` | `baseResult` (no conditions) | `:81-84` |
| 3 | Purpose type → `calcTab` conversion fails | `baseResult` | `:91-95` |
| 4 | No **active rule** as of the trip date | `baseResult` | `:97-103` |
| 5 | That `calcTab` has no condition blocks at all | `baseResult` | `:106-110` |
| 6 | `limitAmounts` is `null` or an empty map | `baseResult` | `:117-120` |

> Transportation (`HD_TRANSPORT`) takes the **round-trip-checking path** (`getTranKindLimitWithCheckRoundTrip`) at step 1, while other purposes take `getTranKindLimit` (`BstrRenewalLimitService.java:72-76`).

### Purpose type → `calcTab` mapping (the determination table for early return #3)

Condition blocks are stored per rule × **calculation tab (`calcTab`)**. If the purpose type is not in the table below, early return #3 fires and **no conditions are included in the response** (`BstrRenewalLimitService.java:929-939`).

| **The matched rule row's `tranKindType`** (the response value, not the request value) | `calcTab` |
|---------------------|-----------|
| `HD_DAILY_COST` · `DAILY_COST` | `DAILY` |
| `HD_FOOD` · `FOOD` | `MEAL` |
| `HD_ROOM` · `ROOM` | `ROOM` |
| `HD_TRANSPORT` · `TRANSPORT` | `TRANSPORT` |
| `HD_FUEL` · `FUEL` | `FUEL` |
| Everything else (including `null`) | `null` → **no conditions applied; only the base amount is returned** |

### Derived values the backend uses for condition matching (not present in the request)

| Derived value | What builds it | Source |
|--------|-------------------|------|
| `tripDays` (trip day count) | **`limitAmounts.size()`** — the number of dates for which a limit was derived | `BstrRenewalLimitService.java:134-135` |
| `tripStart` / `tripEnd` | The minimum and maximum of the `limitAmounts` keys | `:136-139` |
| `tripDurationHours` (duration) | The departure and arrival times from `calcInputs` plus the request period | `:129-130` |
| `lunchOverlapHours` (lunch overlap) | The above plus the per-rule, per-calculation-tab lunch-time setting (`BstrLunchTimeSetting`) | `:124-132` |
| `purposeName` / `segmentName` | The **names** looked up from the request's `bstrPurposeId` / `bstrSegmentId` | `:112-114` |

> **Note that `tripDays` is the size of `limitAmounts`.** If the payment option has removed dates from the map, the trip day count shrinks accordingly and the `periodRange` condition determination changes.

---

## 2.6  What the client does immediately after the lookup — apply or defer

`fetchRenewalLimitAmounts` (`renewal-limit-api.ts:466-485`) goes through **four steps** after receiving the response.

```
[0] Validate the request date format  → if invalid, return null without even calling the API   (:469-474)
[1] Look up via apiPost                                                                        (:476)
[2] Stamp calcPeriod                  → { start: bstrDate, end: bstrEndDate ?? bstrDate }       (:480)
[3] applyValidatedCalcConditions(response, request.tranKindType)                                (:478-482)
```

### The step-3 branch — apply immediately vs. defer

`applyValidatedCalcConditions` (`renewal-limit-api.ts:442-458`). **The first match from the top wins.**

| Order | Determination | `limitAmounts` | `calcBreakdown` | `calcDeferred` | Source |
|------|------|----------------|-----------------|----------------|------|
| 1 | zod `safeParse` fails | **Kept as-is** | Not set | Not set | `renewal-limit-api.ts:446-447` |
| 2 | `hasDeferredForeignCalc(response) === true` | **Kept as-is (deferred)** | Not set | Not set | `renewal-limit-api.ts:448-449` |
| 3 | Per-diem purpose (`isDailyCostTranKind`) | **Kept as-is (deferred)** | **Set** | **`true`** | `renewal-limit-api.ts:450-456` |
| 4 | Everything else | **The post-condition value** | Set | Not set | `renewal-limit-api.ts:457` |

> Only case 4 (immediate application) turns `limitAmounts` into the final amount. In cases 1 through 3 **the base amount remains.**
> To tell which case applies by looking only at the response, check `calcDeferred` (case 3) and whether `calcBreakdown` is present.

### The 3 deferral conditions

| # | Condition | Determining code | Source |
|---|------|-----------|------|
| ① | **A foreign-currency rule plus at least 1 active condition** — `currencyCode` is a foreign currency (not `KRW` and non-empty) and at least one condition has `matched && !overridden` | `isForeignCurrencyCode(response.currencyCode)` → `hasActiveCond` | `renewal-limit-api.ts:419-420` (`hasActiveCond` at `:418`), `currency-utils.ts:19-22` |
| ② | **A KRW rule but a foreign currency on the condition side** — an active condition's `details.diffCurrency`, or `fixedCurrency` (when the `operator` uses the fixed-amount field), is a foreign currency | `collectDiffCurrencies(response).length > 0` | `renewal-limit-api.ts:421-422`, `diff-currency-converter.ts:35-49` |
| ③ | **Per-diem purpose** — the request's or response's `tranKindType` is `'DAILY_COST'` or `'HD_DAILY_COST'` | `isDailyCostTranKind(response, requestTranKindType)` | `renewal-limit-api.ts:434-439,453` |

**A precondition that neutralizes ① and ②** — if `response.calcEnabled === false`, `hasDeferredForeignCalc` is immediately `false` (`renewal-limit-api.ts:417`). That is, a rule that does not use a formula is never deferred even in a foreign currency.

**Why ① and ② defer** — at the consumption point where the exchange rate is fixed, the flow must do **per-date conversion → re-application of the same engine (`applyCalcConditionsKRW`)**, so `limitAmounts` must not be altered at lookup time (`renewal-limit-api.ts:406-414`).
`applyCalcConditionsKRW` is re-exported so consuming paths can use it from the same import path (`renewal-limit-api.ts:487-488`).

**Why ③ defers** — per diem must have the **travel-day `opts`** for full-day departure and next-day arrival applied at the consumption point. Applying without `opts` at lookup time and pre-modifying `limitAmounts` means the **idempotency guard blocks re-application with `opts` and travel-day allocation is lost** (`renewal-limit-api.ts:426-432,450-452`). Hence only `calcBreakdown` is set for the on-screen tooltip while `limitAmounts` stays raw, marked with `calcDeferred: true` — the consuming path sees that marker and applies **exactly once** together with `opts` (`renewal-limit-api.ts:362-366`).

`isDailyCostTranKind` treats the **request value as the source of truth** because the response's `tranKindType` may be missing (`renewal-limit-api.ts:433,438`).

### The `calcPeriod` stamp — the source of truth for the date axis

| Item | Rule | Source |
|------|------|------|
| Who stamps it | **The client** (`fetchRenewalLimitAmounts`) — a field absent from the backend response | `renewal-limit-api.ts:368-370,480` |
| `start` | The request's `bstrDate` | `renewal-limit-api.ts:480` |
| `end` | The request's `bstrEndDate`, **falling back to `bstrDate` when not sent** | `renewal-limit-api.ts:479-480` (a comment notes this matches the backend's `BstrExpensePolicyService:458` rule) |
| Why it is needed | The backend's `applyPayOptionDays` **removes dates from the map** depending on the payment option, so this period, not the `limitAmounts` map, is the source of truth for "departure day / arrival day" | `renewal-limit-api.ts:370-372` |
| When absent | (Mocks, test fixtures, hand-assembled responses) the engine **falls back to the existing map index axis** | `renewal-limit-api.ts:373` |

> **An external system assembling or reproducing the response must fill in `calcPeriod` itself.**
> If a payment option (such as `EXCEPT_START`) removed the first or last day from the map, looking only at the map without `calcPeriod` gives a wrong "departure day" determination.

---

## 2.7  An invalid date returns `null` without even making the call

This is a **preceding guard** an external system must know about (`renewal-limit-api.ts:469-474`).

```typescript
  if (
    !isValidDateString(request.bstrDate) ||
    (request.bstrEndDate != null && !isValidDateString(request.bstrEndDate))
  ) {
    return Promise.resolve(null);
  }
```

| Item | Rule | Source |
|------|------|------|
| What is checked | `bstrDate` (always) and `bstrEndDate` (**only when not `null`/`undefined`**) | `renewal-limit-api.ts:470-471` |
| Format regex | `/^\d{4}-\d{2}-\d{2}$/` — **fixed-width `YYYY-MM-DD`** | `renewal-limit-api.ts:393` |
| Additional check | `!isNaN(new Date(value).getTime())` — whether it actually parses as a date | `renewal-limit-api.ts:394-396` |
| On violation | `Promise.resolve(null)` **without sending the HTTP request** | `renewal-limit-api.ts:473` |
| ⚠️ Enum field violation | Putting an out-of-definition string in an enum field such as `tranKindType`, `vehicleType`, or `bstrType` gives a **400** at deserialization (there is no `@JsonCreator`, so only enum names are accepted). See [§2.2](#22--complete-request-field-table) for the value sets | — |
| When the response is falsy | Condition application is skipped and the response is returned as-is (`res ? … : res`) | `renewal-limit-api.ts:477,483` |

**⚠️ Note on the wire shape** — when the backend cannot find a rule and returns `null`, the controller wraps it in `ResponseEntity.ok(result)` (`BstrPolicyRenewalController.java:48`), so the actual response is **HTTP 200 with an empty body** — not the JSON string `null`. Feeding that straight into a JSON parser gives a parse failure or an empty value, so you must **treat a body length of 0 as "no rule."** The client absorbs the falsy value with `res ? … : res`.

**Contractual meaning** — `null` (an empty body) represents two different events with the same value.

| Meaning of `null` | Where it originates |
|----------------|-----------|
| ① Date format violation → **no call made** | `renewal-limit-api.ts:469-474` |
| ② No rule exists → the backend returns a `null` body | `BstrRenewalLimitService.java:77-79` + `BstrPolicyRenewalController.java:48` |

> The client's consuming path interprets `rule=null` as **"rule not looked up"** and **returns the receipt unchanged** (it does not zero out the amount) — `rule-update-default.ts:148-154`.
> That means **sending a malformed date silently produces "no rule" and the ruled amount is never filled in.**
> This is the leading cause of an empty ruled amount, so an external system must not violate `YYYY-MM-DD` (`2026-9-8`, `2026/09/08`, and ISO datetimes are all violations).

Also, an empty string or `null` `tranKindType` produces a **backend 400** — the client skips the call before that point (`rule-update-default.ts:96-102`).

---

## 2.8-0  Enum value sets that appear in the response (settled from the source of truth)

Sets that the body of this document blurred with "etc." were verified exhaustively against the backend source of truth. The path is `common-core/src/main/java/com/newbizplay/{constant|pconstant}/`.

| Enum | Values (complete) |
|---|---|
| `TranKindType` | Source of truth `constant/TranKindType.java:8-44` — the `HD_` family is `HD_DAILY_COST`, `HD_ROOM`, `HD_FOOD`, `HD_TRANSPORT`, `HD_FUEL`, … (**there is no `HD_HOTEL` or `HD_MEAL`**) |
| `VehicleType` | `constant/VehicleType.java:31` — `KTX`, `AIR`, `BUS`, … (**`AIRPLANE` is not an enum name but a tmapMode code**) |
| `BstrPayClassType` | `NONE` · `ACTUAL` · `LIMITED` · `FIXED` · `ACTUAL_FIXED` · `FUEL` · `TOLL` (7 values) |
| `BstrType` | `DOMESTIC` · `OVERSEA` · `EXCEPTION` · `BOTH` (4 values) |
| `BstrCategoryType` | `MONEY` · `GRADE` · `STAR` (3 values) |
| `BstrPayOptionType` | `ALL` · `EXCEPT_START` · `EXCEPT_END` · `EXCEPT_TWO_DAYS` · `FIX_ONE_DAY` · `FIX_TWO_DAYS` · `N_DAYS` · `MOVEMENT_ROUTE` (8 values) |
| `FoodDivisionType` | `ALL` · `NONE` · `BREAKFAST` · `LUNCH` · `DINNER` · `SNACK` · `LATE_NIGHT` · `MEAL` · `ETC` (9 values) |
| `ActivityDivisionType` | `ACTUAL` · `FIXED` (2 values) |
| `BstrTransportGradeType` | `pconstant/BstrTransportGradeType.java:10-30` — a transport-mode suffix scheme: `ECONOMY_AIR`, `PREMIUM_ECONOMY_AIR`, `BUSINESS_AIR`, `FIRST_AIR`, `ECONOMY_KTX`, `FIRST_KTX`, `GENERAL_BUS`, `ECONOMY_CBUS`, … (**there is no suffix-less `ECONOMY` or `FIRST_CLASS`**) |

> **Putting an out-of-definition string in an enum field gives a 400** — with no `@JsonCreator`/`@JsonValue`, Jackson accepts only enum names. Do not use values absent from the table above.

## 2.8  ⚠️ Open items

| # | Item | What is unconfirmed |
|---|------|-------------------|
| 1 | The 4 fields `calcInputs.isMealTwiceOrMore`, `isLodging`, `isFullDayDeparture`, `isNextDayArrival` | **The outcome is settled** — across the entire repository these four fields have **zero** uses beyond their interface declaration (`renewal-limit-api.ts:106-112`), and `matchYesNo` unconditionally returns `false` when the input is `null` (`BstrRenewalLimitService.java:456-460`). → **The 4 `YESNO_*` conditions can never match in the current implementation.** Only their source (which input item should supply them) is unconfirmed |
| 2 | ~~Whether this is the same axis as `REQUEST_STAFF_LODGE`~~ → **Settled: different items** | "Group lodging" goes through the `selectionId` path under `BSTR_SELECT` (`utils/calc-condition-labels.ts:136-137`, `:144-146`), while `REQUEST_STAFF_LODGE` (employee dormitory application) is one of the 3 `YESNO_APPLY_ITEM_TYPES` (`calc-inputs.ts:196`). **They are different items** |
| 3 | ~~Which of the two paths is authoritative~~ → **Settled** | **The `exceptionRuleInputItemIds` path is the only working path**, and the `calcInputs` yes/no path is effectively dead code ([§2.3.3](#233-the-two-paths-for-full-day-departure-and-next-day-arrival)) |
| 4 | ~~The source of `isNextDayArrival`~~ → **Settled the same way as #3** | — |
| 5 | The source of `calcInputs.departmentId` | The backend's `DEPARTMENT` condition consumes it (`:326`) but the client does not fill it. Whether it is the drafter's department or the budget department is unclear |
| 6 | The source of `calcInputs.companyCode` | The backend's `COMPANY` condition consumes it (`:325`) but the client does not fill it. Whether it is a corporation code or a business-site code is unclear |
| 7 | ~~Who fills the response's `baseAmount`~~ → **Settled: nobody does** | The backend DTO has no such field, and the client engine does not set `response.baseAmount` either (the `baseAmount` at `utils/calc-condition-engine.ts:265` and `:272` is a value inside `calcBreakdown`). → **It is never filled by this endpoint.** If you need the base amount, read `limitAmounts` or `calcBreakdown.baseAmount` |
| 8 | ~~The full `bstrPayClassType` enum list~~ → **Settled (7 values)** | `common-core/.../pconstant/BstrPayClassType.java:9-15` = **`NONE` · `ACTUAL` · `LIMITED` · `FIXED` · `ACTUAL_FIXED` · `FUEL` · `TOLL`**. ⚠️ The client comment's `ACTUAL_LIMIT` (`renewal-limit-api.ts:322`) **is a nonexistent value** |
| 9 | ~~The `bstrType` value set~~ → **Settled (4 values)** | `common-core/.../pconstant/BstrType.java:9-12` = **`DOMESTIC` · `OVERSEA` · `EXCEPTION` · `BOTH`**. There is no `OVERSEAS` (with an S) — the client comment (`renewal-limit-api.ts:62`) is wrong. The client only ever sends `DOMESTIC`/`OVERSEA`, but **`EXCEPTION` and `BOTH` are also valid enum values** |
| 10 | The JSON shape of the undeclared `details` keys | `company`, `department`, `departTime`, `arriveTime`, `destination`, `distance`, `group`, `grade` (`BstrRenewalLimitService.java:325-350`). They are absent from the zod schema and pass only through `.passthrough()` — the structure of each key is unconfirmed |
| 11 | The behavior of the 6 backend-only request fields | `jobClassCode`, `exceptPayOption`, `departureTerminal`, `arrivalTerminal`, `departureDate`, `returnDate` (`BstrTranKindLimitRequestDto.java:57-72`). Their required combinations and relationship to round-trip ticket determination are unconfirmed |
| 12 | The upstream origin of `corporationId` | Confirmed only as far as `RuleUpdateContext.corporationId` (`rule-update-utils.ts:57`). The upstream point that assembles this context is outside the source files covered by this section |
