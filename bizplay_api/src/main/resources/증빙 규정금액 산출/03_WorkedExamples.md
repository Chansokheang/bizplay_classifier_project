# §3 Materials — 3 worked examples of ② condition application (2026-09-08)

> **Purpose** — to let the external system (Chungbuk National University) implement per-day application of `appliedConditions[]` and then **work out for itself which stage of its own implementation is wrong.**
> That is why this document gives not just the final amount but **every intermediate value at every step, in tables.**
>
> The front-end source of truth is `packages/domains/src/bstr-policy/utils/calc-condition-engine.ts` (472 lines), and every value in this document is that engine's behavior.
> File paths below are relative to `packages/domains/src/bstr-policy/` (exception: paths beginning with `cloud-expense-report/` are relative to `packages/domains/src/cloud-expense-report/`).

## 0. Notation conventions

| Notation | Meaning |
|---|---|
| (test-measured) | The value appears verbatim in a repository unit test. The `file:line` is given alongside |
| ⚠️computed | The test suite has no such combination, so the value was derived from the engine logic. It was confirmed by actually running the engine during this work |
| ⚠️unconfirmed | It could not be settled from the code. These are collected under "Open items" at the end |

**Three arithmetic rules pinned down first** (`utils/calc-condition-engine.ts:8`, `:332-334`, `:221-235`).

1. **The candidate set** = every condition with `matched === true`. They are applied sequentially in **ascending** `sortOrder` (`:332-334`). The result of the previous condition is the input to the next — **order changes the result** (`utils/calc-condition-engine.test.ts:114-131`: `×2 → fixed 10,000` = 10,000 / `fixed 10,000 → ×2` = 20,000).
2. **No rounding.** The engine calculates in floating point and the consuming stage (the receipt) does the truncation (`:8`). There is in fact a test where `37.5` comes out as-is (`utils/calc-condition-engine.test.ts:777`).
3. **`overridden` does not mean "globally not applied."** When `supersededByIds` arrives, the engine re-checks per date **whether the higher-priority condition is applicable that day** (per-day fallback, `:187-191`). If the higher-priority one does not fit that day, the lower one takes over (`utils/calc-condition-engine.test.ts:873-890`: weekday 5,600 / weekend 15,600).

---

# Example A — Lodging plus a trip-classification condition discount

> **Preamble — the "companion condition discount" was changed to a "trip-classification (`SEGMENT`) discount."** Three reasons.
>
> 1. **The request schema has no field that takes a headcount as a condition.** The headcount-related entries in `CalcConditionInputs` are only the four yes/no fields `isMealTwiceOrMore`, `isLodging`, `isFullDayDeparture`, and `isNextDayArrival` (`api/renewal-limit-api.ts:104-112`).
> 2. **Split lookups per companion are meals-only (FOOD).** Lodging has no companion axis at all (`utils/ruled-amount-calculator.ts:245-249` — only when `tranKindType === 'FOOD'` does it look up the drafter and each companion separately. Test: `utils/ruled-amount-calculator.test.ts:98-124`).
>    → The companion axis is covered separately in section C-7 of example C.
> 3. **The "group lodging (`YESNO_LODGING`)" condition does not match in the current code** — see pitfall 1 below. So it cannot be used as a worked example.
>
> So A is set as **lodging + trip classification ×50%**, with **weekday ×80%** laid on top, to show **two conditions applied sequentially on the same day.** Trip classification (`SEGMENT`) was chosen because **the backend matches it itself using the `bstrSegmentId` already carried in the request** (`BstrRenewalLimitService.java:318` `matchEquals(value, segmentName)`) — no separate input is needed, so POSTing this exact request JSON reproduces it.
>
> The amounts in the table are ⚠️computed (the individual operations each appear in tests — `utils/calc-condition-engine.test.ts:152-155` `x PERCENT`, `:342-345` dayType filter, `:114-131` order dependency).

## A-1. Scenario

| Item | Value |
|---|---|
| Trip period | 2026-08-27 (Thu) to 2026-08-30 (Sun) |
| Lodging receipt usage period | Check-in 2026-08-27, check-out 2026-08-30 (**3 nights**) |
| Purpose | Lodging `tranKindType: "ROOM"`, `tranKindId: 3301` |
| Grade zone | A single domestic zone (`bstrRegionId: 501`) |
| Currency | KRW (`currencyCode: "KRW"`) |
| Trip classification | `bstrSegmentId: 12` — the backend looks up the classification name by this id and compares it against the condition |
| Base amount | 100,000 KRW per day |
| Conditions | ① Weekday `× 80%` (`sortOrder=1`) / ② Trip classification `× 50%` (`sortOrder=2`) |

## A-2. Request JSON

Lodging **looks up the base rule once and then makes a separate call per grade zone (`selections`)** (`utils/rule-update-room.ts:78` baseRule → `:115-147` the grade-zone loop).
**With 1 grade zone that is 2 calls total** (1 baseRule + 1 zone). Tests pin this count — `utils/rule-update-room.test.ts:214-215` "baseRule + 1 selection = 2 calls", `:454-455` "3 calls: 1 baseRule + 2 selections".

Below is the request JSON for the **grade-zone call** (the base lookup goes out in the same shape with `bstrRegionId: null`).

```json
{
  "bstrDate": "2026-08-27",
  "bstrEndDate": "2026-08-30",
  "corporationId": 1001,
  "corporationUserId": 20551,
  "vehicleType": null,
  "tranKindType": "ROOM",
  "tranKindId": 3301,
  "bstrPurposeId": 71,
  "bstrSegmentId": 12,
  "bstrDepartureId": 9001,
  "bstrDestinationId": 9042,
  "bstrAreaCode": null,
  "bstrRegionId": 501,
  "bstrType": "DOMESTIC",
  "exceptionRuleInputItemIds": [],
  "calcInputs": {
    "departureTime": "08:00",
    "arrivalTime": "18:00"
  }
}
```

**⚠️ Pitfall 1 — the 4 `YESNO_*` conditions do not match in the current code.**

The backend matcher determines these 4 from the yes/no values in `calcInputs` (`BstrRenewalLimitService.java:320-323`).

| Condition `itemType` | The input the backend looks at |
|---|---|
| `YESNO_MEAL_TWICE` (`c1`) | `calcInputs.isMealTwiceOrMore` |
| `YESNO_LODGING` (`c2`) | `calcInputs.isLodging` |
| `YESNO_FULL_DAY_DEPARTURE` (`c5`) | `calcInputs.isFullDayDeparture` |
| `YESNO_NEXT_DAY_ARRIVAL` (`c6`) | `calcInputs.isNextDayArrival` |

But **the request assembler `collectCalcInputs` never sets these four fields** (`utils/calc-inputs.ts:36-83` — it fills only time, transport mode, destination, and destination count). They are declared in the interface (`api/renewal-limit-api.ts:104-112`). And `matchYesNo` returns **unconditionally `false`** when the input is `null` (`BstrRenewalLimitService.java:456-460`).

→ **Result: these 4 conditions always come down as `matched: false`.** So they cannot be used in a worked example.
Note as well that `matchYesNo` compares the condition value against `"해당"` (applicable) / `"미해당"` (not applicable) — `itemValue: "true"` is neither, so even sending `isLodging` would not match.

**⚠️ Pitfall 1-1 — "group lodging" and "employee dormitory application" are different items.**
The yes/no trip items that go through `exceptionRuleInputItemIds` are the three in `YESNO_APPLY_ITEM_TYPES = ['EXPENSE_BEYOND_BSTR_PERIOD', 'HOMETOWN', 'REQUEST_STAFF_LODGE']` (`utils/calc-inputs.ts:184-186`, collected at `:189-205`), and `REQUEST_STAFF_LODGE` is the **employee dormitory application** (KSOE, INNOTEK) — not group lodging. Group lodging, as `utils/calc-condition-labels.ts:137` and `:145` make clear, goes through the **selection-value id under `BSTR_SELECT`** (`selectionId`) path (`utils/calc-inputs.ts:190-193`).

For a condition arriving via that path, the `itemType` must be **the numeric id string of that item** for the backend's `matchDynamicInputItem` to compare it against `exceptionRuleInputItemIds` and match (`BstrRenewalLimitService.java:441-448`, `:355` default branch). `itemValue` is for display.

**⚠️ Pitfall 2 — the overseas value of `bstrType` is `"OVERSEA"`.**
The interface comment says `(DOMESTIC, OVERSEAS)` (`api/renewal-limit-api.ts:62-63`), but the value actually sent is `'OVERSEA'` (no S) (`utils/rule-update-room.ts:92`).

## A-3. Response JSON

```json
{
  "id": 88120,
  "limitAmount": 100000,
  "limitAmounts": {
    "2026-08-27": 100000,
    "2026-08-28": 100000,
    "2026-08-29": 100000,
    "2026-08-30": 100000
  },
  "dayTypeMap": {
    "2026-08-27": "평일",
    "2026-08-28": "평일",
    "2026-08-29": "주말",
    "2026-08-30": "주말"
  },
  "currencyCode": "KRW",
  "tranKindType": "ROOM",
  "tranKindId": 3301,
  "bstrPayClassType": "FIXED",
  "bstrPayOptionType": "ALL",
  "bstrCategoryType": "MONEY",
  "calcEnabled": true,
  "appliedConditions": [
    {
      "id": 11,
      "sortOrder": 1,
      "calcMethod": "daily",
      "operator": "x",
      "operatorValue": "80",
      "operatorUnit": "PERCENT",
      "fixedAmount": null,
      "fixedCurrency": null,
      "diffFirst": null,
      "diffMid": null,
      "diffLast": null,
      "excludeDays": null,
      "details": {},
      "items": [{ "itemType": "dayType", "itemValue": "평일", "sortOrder": 1 }],
      "matched": true,
      "overridden": false,
      "supersededByIds": []
    },
    {
      "id": 12,
      "sortOrder": 2,
      "calcMethod": "daily",
      "operator": "x",
      "operatorValue": "50",
      "operatorUnit": "PERCENT",
      "fixedAmount": null,
      "fixedCurrency": null,
      "diffFirst": null,
      "diffMid": null,
      "diffLast": null,
      "excludeDays": null,
      "details": {},
      "items": [{ "itemType": "SEGMENT", "itemValue": "단기", "sortOrder": 1 }],
      "matched": true,
      "overridden": false,
      "supersededByIds": []
    }
  ]
}
```

> `limitAmounts` is the **base amount** — not the final amount (backend comment at `BstrRenewalLimitService.java:149`; see §2.5 of `01_RawDataLookup_Contract.md`).

## A-4. Step-by-step derivation table ★

| Step | 2026-08-27 | 2026-08-28 | 2026-08-29 | 2026-08-30 | Note |
|---|---:|---:|---:|---:|---|
| Base amount (response `limitAmounts`) | 100,000 | 100,000 | 100,000 | 100,000 | All 4 days identical |
| `dayTypeMap` | 평일 (weekday) | 평일 | 주말 (weekend) | 주말 | The filter axis for condition ① |
| After condition ① `sortOrder=1` `dayType=평일` `× 80%` | **80,000** | **80,000** | 100,000 | 100,000 | The 2 weekend days are skipped at `isDayTypeMatched` → the input passes through unchanged |
| After condition ② `sortOrder=2` trip classification `× 50%` | **40,000** | **40,000** | **50,000** | **50,000** | No day-type constraint → applied on all 4 days. It multiplies **on top of condition ①'s result** |
| Engine output `limitAmounts` (per-day final) | 40,000 | 40,000 | 50,000 | 50,000 | ⚠️computed |
| Included in the receipt sum? (lodging = check-out day excluded) | Yes | Yes | Yes | **No** | 08-30 is the check-out day |
| **Receipt ruled amount, cumulative** | 40,000 | 80,000 | **130,000** | 130,000 | 3 nights = 130,000 |

**The engine's calculation detail (`calcBreakdown`) output at the same time** — ⚠️computed

| Field | Value |
|---|---|
| `baseAmount` | 100,000 (no tiered condition, so the rule base is kept — `utils/calc-condition-engine.ts:253-262`) |
| `items[0]` | `{ label: "평일", effect: "×80%" }` |
| `items[1]` | `{ label: "<trip classification name>", effect: "×50%" }` — the label is built from `itemType` and `itemValue` by `utils/calc-condition-labels.ts`. **Compare against the `effect` string (`×50%`), not the label string** (⚠️the label is unverified) |
| `totalAmount` | 180,000 (**the sum of all 4 days** — different from the receipt ruled amount of 130,000) |

**⚠️ Pitfall 3 — `calcBreakdown.totalAmount` is not the receipt's ruled amount.**
The engine sums the entire response map (`utils/calc-condition-engine.ts:437`), while the receipt takes **only its own usage period.** In this example 180,000 ≠ 130,000.

**⚠️ Pitfall 4 — check-out-day exclusion for lodging exists only on the "grade zone (`selections`) path."**
`usedEnd = usedEndDate − 1 day` exists only inside the grade-zone split branch (`utils/rule-update-room.ts:171-174`). It is a **separate axis unrelated** to the payment option (`bstrPayOptionType`), but **it does not always apply to every lodging receipt.**

| Path | Condition | Check-out day |
|---|---|---|
| Grade-zone split | `selections.length > 0` | **Excluded** (`usedEndDate − 1`) |
| Base rule alone | `selections.length === 0` → `resolveRuledAmount(baseRule, receipt)` (`:107`) | **Included** — it sums `usedStartDate` through `usedEndDate` as-is (`utils/rule-update-helpers.ts:197-208`; there is no `−1`) |

An exhaustive check of the repository found only that one place where `−1` is applied, and the regression tests are all cases that have `selections` (`utils/rule-update-room.test.ts:222`, `:251`, `:282`).

**This example (A) is on the path with grade-zone selections**, so 3 nights at 130,000 is correct. For a lodging receipt with no grade-zone selections, the same response would give **the full 4 days at 180,000.**

There was an actual regression where omitting this exclusion turned 1 night into 2 days' worth (70,000 → 140,000) (comment in the same file at `:159-171`).
If check-in equals check-out (0 nights), `usedEnd < usedStart` and the result is **0 KRW**.

**⚠️ Pitfall 5 — `× PERCENT` and `× AMOUNT` are different operations.**
If `operatorUnit` is `"PERCENT"` or `"%"`, it is `amount × (value/100)`; otherwise (`"AMOUNT"` or **`null`**) it is `amount × value` (`utils/calc-condition-engine.ts:131-136`, `:36`).
Mistaking `operatorUnit: null` for PERCENT turns 50,000 into 100,000 (tests: `utils/calc-condition-engine.test.ts:152-155`, `:462-474`).
Conversely, `+` and `-` **ignore `operatorUnit` and are always amounts** (`:137-140`, test `:158-159`).

## A-5. Final receipt fields

Path: `utils/rule-update-room.ts:265-268` → `buildRoomResult` (`:282-303`).

| Field | Value | Source |
|---|---:|---|
| `ruledAmount` | **130,000** | `grandTotalKrw` (the per-day sum within the usage period) → `resolvedRuledAmount` (`utils/rule-update-room.ts:301-303`) |
| `overseasRuledAmount` | `null` | A KRW rule → `convertRuledAmountToKRW` returns `null` (`utils/rule-update-helpers.ts:46-48`) |
| `dividedRuledAmount` | `null` | Not a split (IO/WBS ratio) receipt. It is a distributed value filled only on ratio-split children, and **while it is not carried in the save payload, it is not display-only** — the claim-amount cap (`utils/autofill-req-amount.ts:145-151`) and the excess determination (`utils/over-amount.ts:145-150`) use it |

**⚠️ Pitfall 6 — for an actual-cost payment classification, the ruled amount is replaced by the approved amount.**
If `bstrPayClassType` is in the actual-cost family (`isActualLikePayClass`), then `ruledAmount = receipt.approvalAmount` (`utils/rule-update-room.ts:300-303`, `utils/rule-update-food.ts:124-126`). This example is `FIXED`, so the computed 130,000 goes in.
Putting the computed value in for an actual-cost case produces a wrong excess amount.

## A-6. Arithmetic re-check

- Condition ①: `100,000 × 80 / 100 = 80,000` (2 weekdays). The 2 weekend days are not applied, so 100,000 is kept.
- Condition ②: `80,000 × 50 / 100 = 40,000` (weekdays) and `100,000 × 50 / 100 = 50,000` (weekend).
- 4-day total: `40,000 + 40,000 + 50,000 + 50,000 = 180,000` = `calcBreakdown.totalAmount`. ✔
- 3-night total (excluding 08-30): `40,000 + 40,000 + 50,000 = 130,000` = `ruledAmount`. ✔
- Order-reversal check: with the order ②→①, a weekday gives `100,000 × 0.5 = 50,000 → × 0.8 = 40,000` — **the same**, because multiplication commutes. Order changes the result when non-commutative operations such as `fixed` and `-` are mixed in (see the test under rule 1 in §0). This example deliberately **avoids** that pitfall.

---

# Example B — Per diem plus travel-day adjustment (full-day departure, next-day arrival)

> **Preamble — every per-day amount in the table is test-measured.**
> Case adopted: `utils/calc-condition-engine.test.ts:938-993` (`describe('applyCalcConditions — travel-day assignment guard')`), with the golden case at `:968-977`.
> The condition definitions are at `:939-958` and the opts at `:959`.
> It is a regression gate arising from a real INNOTEK incident (per diem shrinking from 211,216 KRW to 30,000 KRW), so the values are exactly as they occur in production.
> Both `TravelDayOpts` and `calcDeferred` (consumption-time application) are involved.

## B-1. Scenario

| Item | Value |
|---|---|
| Request period (including travel days) | 2026-08-06 to 2026-08-09 (4 days) |
| Core trip days | 2026-08-07 to 2026-08-08 (2 days) |
| Travel days | Full-day departure 08-06 (1 day), next-day arrival 08-09 (1 day) |
| Purpose | Per diem `tranKindType: "DAILY_COST"`, `tranKindId: 4401` |
| Currency | KRW |
| Base amount | **90,606 KRW** per day |
| Conditions | Next-day-arrival tiered (`id=1775`, `sortOrder=2`) and full-day-departure tiered (`id=1776`, `sortOrder=3`) — both `calcMethod: "dailyDiff"`, `diffType: "pct"`, **`diffBaseAmt: 30000`** |
| Travel-day identifiers | Full-day departure `item.id = 259`, next-day arrival `item.id = 260` |

## B-2. Request JSON

```json
{
  "bstrDate": "2026-08-06",
  "bstrEndDate": "2026-08-09",
  "corporationId": 1001,
  "corporationUserId": 20551,
  "vehicleType": null,
  "tranKindType": "DAILY_COST",
  "tranKindId": 4401,
  "bstrPurposeId": 71,
  "bstrSegmentId": 12,
  "bstrDepartureId": 9001,
  "bstrDestinationId": 9042,
  "bstrAreaCode": null,
  "bstrRegionId": 501,
  "bstrType": "DOMESTIC",
  "activityDivision": "FIXED",
  "exceptionRuleInputItemIds": [259, 260],
  "calcInputs": {
    "departureTime": "07:00",
    "arrivalTime": "21:00"
  }
}
```

**⚠️ Pitfall 7 — the request period must be widened to include the travel days.**
`bstrDate` and `bstrEndDate` are not the core trip days but **the range extended forward and backward by the allowed day count (allowDays)** (`utils/ruled-amount-calculator.ts:251-265`; for lodging `utils/rule-update-room.ts:78-79`).
Without the extension, `limitAmounts` has no travel-day keys and the engine **abandons travel-day assignment** (pitfall 11).

**⚠️ Pitfall 8 — omitting `activityDivision` or `foodDivisionType` matches a different rule row.**
Per diem must carry `activityDivision` (`"ACTUAL"` | `"FIXED"`) and meals must carry `foodDivisionType` (`utils/ruled-amount-calculator.ts:204-215`). If not sent, the backend skips the row filter and picks the single highest-priority row, making **which limit you get unpredictable** (comment in the same file at `:210-211`).

## B-3. Response JSON

```json
{
  "limitAmount": 90606,
  "limitAmounts": {
    "2026-08-06": 90606,
    "2026-08-07": 90606,
    "2026-08-08": 90606,
    "2026-08-09": 90606
  },
  "dayTypeMap": {
    "2026-08-06": "평일",
    "2026-08-07": "평일",
    "2026-08-08": "평일",
    "2026-08-09": "평일"
  },
  "currencyCode": "KRW",
  "tranKindType": "DAILY_COST",
  "bstrPayClassType": "FIXED",
  "bstrPayOptionType": "ALL",
  "calcEnabled": true,
  "appliedConditions": [
    {
      "id": 1775,
      "sortOrder": 2,
      "calcMethod": "dailyDiff",
      "operator": "none",
      "operatorValue": null,
      "operatorUnit": null,
      "fixedAmount": null,
      "diffFirst": "100",
      "diffMid": "100",
      "diffLast": "50",
      "excludeDays": null,
      "details": { "diffType": "pct", "diffBaseAmt": 30000 },
      "items": [{ "itemType": "260", "itemValue": "익일도착", "sortOrder": 1 }],
      "matched": true,
      "overridden": false
    },
    {
      "id": 1776,
      "sortOrder": 3,
      "calcMethod": "dailyDiff",
      "operator": "none",
      "operatorValue": null,
      "operatorUnit": null,
      "fixedAmount": null,
      "diffFirst": "50",
      "diffMid": "100",
      "diffLast": "100",
      "excludeDays": null,
      "details": { "diffType": "pct", "diffBaseAmt": 30000 },
      "items": [{ "itemType": "259", "itemValue": "전일출발", "sortOrder": 2 }],
      "matched": true,
      "overridden": false
    }
  ]
}
```

**Engine opts (built by the caller, not returned in the response)**

```json
{ "preCount": 1, "postCount": 1, "predepartType": "259", "nextarriveType": "260" }
```

Origin: `utils/calc-inputs.ts:239-273` (`detectTravelDays` — `itemType` is `EXPENSE_BEYOND_BSTR_PERIOD`, `value === "true"`, and `item.erpCode` is `PREDEPART`/`NEXTARRIVE`) → `cloud-expense-report/features/build-travel-day-opts.ts:17-26` (where `String(item.id)` turns them into strings).

**⚠️ Pitfall 9 — `predepartType`/`nextarriveType` must be strings.**
The engine compares with `String(it.itemType) === t` (`utils/calc-condition-engine.ts:349-352`).
Passing the number `259` **silently fails to match** and the travel day is treated as core (regression test: `utils/calc-condition-engine.test.ts:812-827`).

## B-4. Step-by-step derivation table ★ (per-day amounts are test-measured — `utils/calc-condition-engine.test.ts:968-977`)

| Step | 2026-08-06 | 2026-08-07 | 2026-08-08 | 2026-08-09 | Note |
|---|---:|---:|---:|---:|---|
| Base amount (response `limitAmounts`) | 90,606 | 90,606 | 90,606 | 90,606 | All 4 days identical |
| Position assignment (`resolveDayPositions`) | `pre` | `core` `isFirst` | `core` `isLast` | `post` | `preCount=1`/`postCount=1` → core = 08-07 to 08-08 |
| Assigned condition (by `itemType`) | 1776 (full-day departure) | — | — | 1775 (next-day arrival) | The core has **no assigned conditions at all** (`:377-379`) |
| Tiered base amount (`details.diffBaseAmt`) | 30,000 | (n/a) | (n/a) | 30,000 | **90,606 is not used** |
| Tier rate applied | `diffFirst = 50%` | — | — | `diffLast = 50%` | `pre` → `isFirst=true` (`:415`) / `post` → `isLast=true` (`:420`) |
| After condition application | **15,000** | **90,606** | **90,606** | **15,000** | The core has no conditions → **the base amount passes through unchanged** |
| Engine output `limitAmounts` | 15,000 | 90,606 | 90,606 | 15,000 | Test-measured `:971-976` |
| Per-date KRW truncation (`Math.trunc`) | 15,000 | 90,606 | 90,606 | 15,000 | Already integers → no change |
| Included in `eligibleDates`? | Yes | Yes | Yes | Yes | No weekend, already-claimed-day, or non-working-day exclusions |
| **Receipt ruled amount, cumulative** | 15,000 | 105,606 | 196,212 | **211,212** | ⚠️computed (summation) |

**The engine's calculation detail (`calcBreakdown`)** — ⚠️computed

| Field | Value |
|---|---|
| `baseAmount` | **30,000** (not the rule base of 90,606 — `resolveEffectiveBaseAmount`, `utils/calc-condition-engine.ts:253-262`. Test: `:419-433`) |
| `items[0]` | `{ label: "익일도착", effect: "익일도착일 50%, 기준 30,000원" }` |
| `items[1]` | `{ label: "전일출발", effect: "전일출발일 50%, 기준 30,000원" }` |
| `totalAmount` | 211,212 |

**⚠️ Pitfall 10 (the core of this example) — when `diffBaseAmt` is present, the base amount is discarded.**
`applyDailyDiff`'s base is `details.diffBaseAmt ?? that day's amount` (`utils/calc-condition-engine.ts:113`). Since 30,000 is set here, the result is **not** `90,606 × 50% = 45,303` but `30,000 × 50% = 15,000`.
"A tiered rate is a rate against a fixed base amount unrelated to accumulation effects" is the settled specification (same file, `:66-72`).
Ignoring `diffBaseAmt` makes just this one date wrong by 30,303 KRW.
With `diffType: "amt"` it is not a rate but `max(0, (diffBaseAmt ?? that day's amount) + diff{First|Mid|Last}Amt)` (`:90-104`, tests `:253-322`).

**⚠️ Pitfall 11 — travel-day assignment is abandoned entirely when there is no room.**
If `preCount + postCount` eats into the day count and the core becomes empty (`coreStart > coreEnd`), the engine **reverts all days to core** (`utils/calc-day-position.ts:91-94` absolute axis, `:124-127` relative axis; `utils/calc-condition-engine.ts:355-360`). The judgment is that no assignment is safer than a wrong assignment, and the basis is a measured incident (a 2-day map with pre/post=1 shrinking per diem from 211,216 KRW to 30,000 KRW).
The regression test `utils/calc-condition-engine.test.ts:979-985` pins the result in that case as `{08-07: 15,000, 08-08: 30,000}` (all days core, with first-day/last-day tiers).

**⚠️ Pitfall 12 — travel-day conditions are not applied on core days, and core conditions are not applied on travel days.**
The candidate conditions are **partitioned exclusively** into `preConds` / `postConds` / `coreConds` (`utils/calc-condition-engine.ts:371-379`). So the 2 core days in this example have **0 conditions** and the base amount of 90,606 remains — the final amount equalling the base amount is normal here.
(Other tests with the same structure: `utils/calc-condition-engine.test.ts:747-778`, `:780-809`.)

**⚠️ Pitfall 13 — for per diem, the engine is deferred (`calcDeferred`) at lookup time.**
When `tranKindType` is `DAILY_COST`/`HD_DAILY_COST`, the front end fills only `calcBreakdown` in the lookup response, **keeps the original base amount** in `limitAmounts`, and sets `calcDeferred: true` (`api/renewal-limit-api.ts` — the `applyValidatedCalcConditions` / `isDailyCostTranKind` branch).
The reason is that travel-day opts are known only at the consumption point. The consuming side's idempotency guard skips re-application only when `calcBreakdown && !calcDeferred` (`utils/ruled-amount-calculator.ts:113-116`. Test: `utils/ruled-amount-calculator.test.ts:409-446` — the prior day 100,000 → 50,000, the core kept at 100,000).
**An external system does not have this flag** — the response's `limitAmounts` is always the base amount, so you simply apply once yourself. Just fix the application to a single point so that **you do not apply twice.**

## B-5. Final receipt fields

Per diem is an automatic receipt (`etcReceiptSaveRequests`).
Path: `cloud-expense-report/features/daily-cost-receipt-builder.ts:89-105` → `cloud-expense-report/utils/daily-cost-amount-utils.ts:321-418`.

| Field | Value | Source |
|---|---:|---|
| `ruledAmount` | **211,212** | `totalOverride = calcResult.totalAmount` → `ruledAmount: totalAmount` (`daily-cost-receipt-builder.ts:136`, `:160`) |
| `overseasRuledAmount` | `null` | Formula-applied amounts are unified into KRW notation (rule D3, `daily-cost-amount-utils.ts:417`). `suppressOverseas` (`daily-cost-receipt-builder.ts:102`) |
| `dividedRuledAmount` | n/a | Automatic receipts are not subject to ratio splitting |

For a per-diem automatic receipt the **ruled amount is the amount incurred**, so `approvalAmount` and `reqAmt` use the same total.

**⚠️ Pitfall 14 — truncation happens per date before summing (`Math.trunc`), not after.**
`daily-cost-amount-utils.ts:407-414` applies `Math.trunc` per date and then adds.
This is the point that stops `0.5 KRW` from a condition such as `× 50%` leaking into receipts and labels, and it can give a different result from "truncate once after summing" (up to `days − 1` KRW of difference when every date produces a fraction).

## B-6. Arithmetic re-check

- Full-day-departure day: `30,000 × 50 / 100 = 15,000`. ✔ (test `:972`)
- Next-day-arrival day: `30,000 × 50 / 100 = 15,000`. ✔ (test `:975`)
- The 2 core days: 0 assigned conditions → `90,606` unchanged, ×2. ✔ (test `:973-974`)
- Total: `15,000 + 90,606 + 90,606 + 15,000 = 211,212`. ✔
- Wrong-answer check ①: ignoring `diffBaseAmt` → `90,606 × 0.5 = 45,303` ×2 → total `271,818` (+60,606).
- Wrong-answer check ②: **if you do not pass travel-day opts**, the engine **reverts both conditions to core conditions and applies them** (the else branch at `utils/calc-condition-engine.ts:377-379`) → `{08-06:15,000, 08-07:30,000, 08-08:30,000, 08-09:30,000}` = **105,000** (−106,212). A state where "the conditions attach nowhere" does not occur in this engine. ⚠️computed (confirmed by running the engine).
- Wrong-answer check ③: applying the travel-day conditions to the core as well → all 4 days shrink to the 30,000 family (the real-incident pattern).

---

# Example C — Foreign-currency grade-zone meal (convert → re-apply via `applyCalcConditionsKRW`)

> **Preamble — the arithmetic is test-measured; only the purpose was changed to meals.**
> The convert-then-re-apply path and its values appear verbatim in `utils/calc-condition-engine.test.ts:521-535` (`applyCalcConditionsKRW`, USD 100 × 1300 = 130,000 → `+50,000` → **180,000**) and in `utils/ruled-amount-calculator.test.ts:238-265` (the same value reproduced through the full lookup path, **180,000**).
> The latter's `tranKindType` is `ROOM`, so **the change to meals differs only on the lookup axis** (meals add a split lookup per companion — see C-7). The arithmetic is identical.
> The 08-28 column is ⚠️computed (the test covers only 08-27).

## C-1. Scenario

| Item | Value |
|---|---|
| Trip period | 2026-08-27 (Thu) to 2026-08-28 (Fri) — 1 grade-zone segment |
| Purpose | Meals `tranKindType: "FOOD"`, `tranKindId: 2201`, `foodDivisionType: "DINNER"` |
| Grade zone | An overseas zone (`bstrRegionId: 780`), `bstrType: "OVERSEA"` |
| Currency | **USD** (`currencyCode: "USD"`) |
| Base amount | **USD 100** per day |
| Exchange rate | 1 USD = **1,300 KRW** |
| Condition | Weekday `+ 50,000 KRW` (`sortOrder=1`) |
| Receipt | 1 dinner on 2026-08-27 (usage date = the single day 08-27) |
| Companions | None (drafter only) — the companion axis is C-7 |

**⚠️ Pitfall 15 — without `dayTypeMap` the result is "nothing applies," not "everything applies."**
`dayTypeMap` is an optional field present only on newer responses (`api/renewal-limit-api.ts`).
When absent, the engine leaves it as an empty map (`utils/calc-condition-engine.ts:394`), and a condition carrying a `dayType` item fails to match because `dayTypeMap[date]` is `undefined` → **that day is skipped** (`utils/calc-condition-guards.ts:34-40`).

## C-2. Request JSON

```json
{
  "bstrDate": "2026-08-27",
  "bstrEndDate": "2026-08-28",
  "corporationId": 1001,
  "corporationUserId": 20551,
  "vehicleType": null,
  "tranKindType": "FOOD",
  "tranKindId": 2201,
  "foodDivisionType": "DINNER",
  "bstrPurposeId": 71,
  "bstrSegmentId": 12,
  "bstrDepartureId": 9001,
  "bstrDestinationId": 9310,
  "bstrAreaCode": null,
  "bstrRegionId": 780,
  "bstrType": "OVERSEA",
  "calcInputs": {
    "departureTime": "09:00",
    "arrivalTime": "22:00"
  }
}
```

## C-3. Response JSON

```json
{
  "limitAmount": 100,
  "limitAmounts": {
    "2026-08-27": 100,
    "2026-08-28": 100
  },
  "dayTypeMap": {
    "2026-08-27": "평일",
    "2026-08-28": "평일"
  },
  "currencyCode": "USD",
  "tranKindType": "FOOD",
  "tranKindId": 2201,
  "bstrPayClassType": "FIXED",
  "bstrPayOptionType": "ALL",
  "calcEnabled": true,
  "appliedConditions": [
    {
      "id": 21,
      "sortOrder": 1,
      "calcMethod": "daily",
      "operator": "+",
      "operatorValue": "50000",
      "operatorUnit": null,
      "fixedAmount": null,
      "fixedCurrency": null,
      "diffFirst": null,
      "diffMid": null,
      "diffLast": null,
      "excludeDays": null,
      "details": {},
      "items": [{ "itemType": "dayType", "itemValue": "평일", "sortOrder": 1 }],
      "matched": true,
      "overridden": false
    }
  ]
}
```

## C-4. Step-by-step derivation table ★

**The order is the specification: ① convert at the exchange rate → ② apply conditions.** Doing it the other way mixes currencies (`utils/calc-condition-engine.ts:453-464`, `utils/rule-update-helpers.ts:69-83`).

| Step | 2026-08-27 | 2026-08-28 | Note |
|---|---:|---:|---|
| Base amount (response `limitAmounts`, **USD**) | USD 100 | USD 100 | In the foreign currency unit as-is |
| Exchange rate | 1,300 | 1,300 | Looked up with the grade-zone period as the reference-date context (`utils/ruled-amount-calculator.ts:304-315`) |
| Conversion formula | `trunc(100 × 1300)` | `trunc(100 × 1300)` | `exchangeToKRW` (`utils/currency-utils.ts:57-67`) |
| **KRW base after conversion** (`krwBaseMap`) | **130,000** | **130,000** | Test-measured (`utils/calc-condition-engine.test.ts:532`) |
| `dayTypeMap` | 평일 (weekday) | 평일 (weekday) | The condition filter axis |
| After condition ① `sortOrder=1` `dayType=평일` `+ 50,000` | **180,000** | **180,000** | 08-27 test-measured `:534` / 08-28 ⚠️computed — **08-28 is a Friday and therefore a weekday. Both days are applied** |
| Engine output `limitAmounts` (KRW) | 180,000 | 180,000 | |
| Included in the receipt (08-27 dinner) sum? | Yes | No | `resolveRuledAmount` sums only `usedStartDate` through `usedEndDate` |
| **Receipt ruled amount** | **180,000** | — | |

> Note on this table: the Korean original listed 08-28 as `주말` (weekend) in the `dayTypeMap` row and 130,000 in the engine-output row, which contradicts both its own response JSON (where 08-28 is `평일`) and its own note that 08-28 is a Friday with the condition applied. The values above follow the response JSON and the note.

**⚠️ Pitfall 16 — 100-unit currencies use a different conversion formula.**
For `JPY`, `IDR`, and `VND` the published rate is "KRW per 100 units of foreign currency," so it is `trunc(amount × rate / 100)` (`utils/currency-utils.ts:11`, `:62-65`). Everything else is `trunc(amount × rate)`.
The truncation is `Math.trunc` (toward zero), not `Math.floor` — a deliberate choice so that on negative (cancellation/refund) receipts the original and the cancellation still sum correctly (comment in the same file at `:52-56`).

**⚠️ Pitfall 17 — the currency of a condition's amount is unrelated to `currencyCode`.**
The `+ 50,000` is in KRW while the rule currency is USD. The condition schema has no corresponding currency field (`utils/calc-condition-engine.ts:322-328`), so the front end treats only the `%` operation as currency-neutral (test `:537-546`: `130,000 × 50% = 65,000`) and performs amount operations **on the KRW base.**
The only things that carry their own currency are the tiered amounts (`details.diffCurrency`) and the fixed amount (`fixedCurrency`), and in those cases they are converted **first** at that currency's rate before entering the engine (`utils/diff-currency-converter.ts`; case ① of `hasDeferredForeignCalc` — see the same function's comment in `api/renewal-limit-api.ts`).

**⚠️ Pitfall 18 — on this path `overseasRuledAmount` is always `null`.**
A limit with a formula applied is unified into KRW, making foreign-currency comparison impossible (`utils/rule-update-helpers.ts:80-83`, `utils/rule-update-food.ts:110-112`).
Filling `overseasRuledAmount` in order to compare original-currency amounts gives a wrong excess determination.
`overseasRuledAmount = the original foreign-currency amount` is set only for a pure foreign-currency rule with **no** formula (`utils/rule-update-helpers.ts:64-66`).

## C-5. Final receipt fields

Path: `utils/rule-update-food.ts:105-118` → `resolveForeignCalcRuledAmount` (`utils/rule-update-helpers.ts:87-137`) → `resolveRuledAmount` (`:192-215`).

| Field | Value | Source |
|---|---:|---|
| `ruledAmount` | **180,000** | The per-day KRW sum within the receipt's usage period (08-27) (`utils/rule-update-food.ts:186`) |
| `overseasRuledAmount` | `null` | Formula-applied foreign currency → unified to KRW (`utils/rule-update-food.ts:112`) |
| `dividedRuledAmount` | `null` | Not a split receipt |

**⚠️ Pitfall 19 — `resolveRuledAmount`'s fallback silently sums the entire period.**
If the sum of the days overlapping the usage period is **0**, it sums and returns the entire map (`utils/rule-update-helpers.ts:200-212` — the full-map summing `return` after `if (total > 0) return total;`).
If the date keys are misaligned and nothing overlaps, you get not 0 but **360,000** (the full-period sum = 180,000 × 2 days).
You must check this carefully against the correct answer of 180,000.

**⚠️ Pitfall 20 — the branch axis for meals between "per-date map" and "scalar" is the presence of foreign currency, not the presence of a formula.**
The path where `hasDeferredForeignCalc` is false adds each user's `r.limitAmount` (the daily unit price) (`utils/rule-update-food.ts:113-114`). Only the path with a formula sums the per-date map (`:106-112`).
The denominators of the two paths differ, so the presence of a formula changes the derivation itself.

## C-6. Arithmetic re-check

- Conversion: `trunc(100 × 1300) = 130,000` (USD is not a 100-unit currency). ✔ test `:532`
- Condition: `130,000 + 50,000 = 180,000` (08-27, a weekday). ✔ test `:534`
- 08-28 (Fri, a weekday): condition applied → `130,000 + 50,000 = 180,000`. ⚠️computed (confirmed by running the engine)
- Receipt (the single day 08-27): `ruledAmount = 180,000`. ✔
- Wrong-answer check: applying the condition first and converting afterward gives `trunc((100 + 50,000) × 1300) = 65,130,000` — currency mixing balloons the amount by roughly **362×.** That is why the order is pinned down as the specification.

## C-7. A meals-only axis — companion aggregation (not applied in this example)

Meals alone **look up the drafter and each companion separately and sum the same dates** (`utils/ruled-amount-calculator.ts:245-249`, `:141-166`).

| Fact | Value | Source |
|---|---|---|
| Number of lookups | 1 companion + the drafter = **2** | `utils/ruled-amount-calculator.test.ts:98-109` |
| Same-date summing | `10,000 + 15,000 = 25,000` | `utils/ruled-amount-calculator.test.ts:139-155` |
| A companion with `bstrStatus === "DRAFT_ONLY"` | Excluded from the lookup | `utils/ruled-amount-calculator.test.ts:660-684` |
| Dates where grade-zone / trip-period segments overlap | Merged by **maximum**, not summed | `utils/ruled-amount-calculator.ts:319-329` (per diem is once per day) |

**⚠️ Pitfall 21 — the merge rules for the companion axis and the grade-zone axis are opposites.**
Companions are **summed** (within a segment), while multiple grade-zone / trip-period rows are merged with **`Math.max`** (across segments).
Treating both with the same rule either doubles a day (double-counting grade zones) or loses the companions' share.

---

# How to use these tables

> The 3 examples in this document are **for human reading and understanding.** To compare an implementation mechanically, use **`규정금액_골든벡터.json` (71 entries)** in the same folder — input↔output pairs extracted by actually running the engine, covering all 8 operators, all 6 calcMethods, dayType, dateRange/periodRange, dailyDiff, travel days, accumulation order, overridden fallback, and foreign-currency re-application.

1. Call `POST /api/v2/bstr/policy/renewal/limit` with the same request JSON (A-2, B-2, C-2) and receive the response. First confirm that the response's `limitAmounts` is the **base amount.**
2. Run your own implementation and **print the intermediate value corresponding to each row of the step-by-step table** (base amount → after the dayType filter → after applying conditions ① and ② individually → after travel-day assignment → after the exchange conversion → the per-day final → the receipt sum).
3. Compare the table against your values **from top to bottom** and find **the first row that diverges.**
   The stage of that row is what your implementation gets wrong; every difference below it is a consequence, not the cause.
4. The "Note" column of the diverging row and that example's ⚠️ pitfalls are the candidate causes —
   for a post-condition row, the `operatorUnit` PERCENT/AMOUNT determination and the `dayType` filter (pitfalls 5 and 15); for the tiered-base-amount row, `details.diffBaseAmt` (pitfall 10); for the position-assignment row, travel-day assignment and partitioning (pitfalls 9, 11, 12); for the post-conversion row, 100-unit currencies, `trunc`, and application order (pitfalls 16 and 17).
5. You are not done until the last two rows (`receipt ruled amount` and "final receipt fields") also match — the engine output and the receipt value differ (lodging check-out-day exclusion, receipt usage-period filtering, actual-cost substitution, per-date `trunc`).

---

# Open items

| # | Item | Why it could not be settled |
|---|---|---|
| 1 | ⚠️Unconfirmed — whether a group-lodging condition's `items[].itemType` is `"YESNO_LODGING"` in an actual response or a numeric `item.id` string | The label map has `YESNO_LODGING` as a fixed key (`utils/calc-condition-labels.ts:14`), but `:137` and `:145` in the same file state "full-day departure = item.id, group lodging = the selection-value id under BSTR_SELECT," so both forms coexist. ⚠️ **Do not generalize that `itemType` is irrelevant to the amount** — the engine uses `hasItemType` (`utils/calc-condition-engine.ts:349-352`) to find `predepartType` and `nextarriveType` and partition into pre/post/core (`:371-379`). That is example B. What is irrelevant is **everything other than `dayType` and the travel-day family**, and for that remainder the backend's `matched` is the source of truth |
| 2 | ⚠️Unconfirmed — the **identifier values** in examples A, B, and C: `corporationId`, `corporationUserId`, `bstrPurposeId`, `bstrSegmentId`, `bstrRegionId`, `tranKindId`, `bstrDepartureId`, `bstrDestinationId`, and so on | They are per-tenant master data. The values in this document are placeholders to show the shape, and the real values must be looked up individually. They have no effect on the amount arithmetic |
| 3 | ~~The full `foodDivisionType` enum~~ → **Settled** | The source of truth is on the front end — `business-plan/features/receipt-import/etc-receipt-drawer/etc-receipt-form-schema.ts:24-33` has `FOOD_DIVISION_TYPE_VALUES = [BREAKFAST, LUNCH, DINNER, SNACK, LATE_NIGHT, MEAL, ETC]` (its comment declares itself "the SSOT for the valid enum set"). `ALL` is a wildcard used only in rule configuration and is not a receipt value |
| 4 | ~~`exceptionRuleInputItemIds: [4102]` in example A~~ → **Removed from the example** | Changing condition ② to `SEGMENT` removed the need for an exception input item (see the preamble). To use the `exceptionRuleInputItemIds` path, the condition's `itemType` must be **the numeric id string of that item**, and that id is tenant form data — only the collection rules are settled in code (`utils/calc-inputs.ts:189-205`) |
| 5 | ⚠️Unconfirmed — the weekday/weekend assignment in example A's `dayTypeMap` | The days of the week were computed from a calendar, but holiday handling is the backend's responsibility. The engine only does string equality comparison (`utils/calc-condition-guards.ts:36-49`). `"평일/공휴일"` is a special value meaning "no constraint" (`DAY_TYPE_ALWAYS`, same file `:16-17`, used at `:37`) |
| 6 | ⚠️Unconfirmed — the assumption in example B that `eligibleDates` includes the 2 travel days | The generating path's comment states that "travel days are already included in `targetDates`" (`cloud-expense-report/features/daily-cost-hooks.ts:266-268`), but the set shrinks if already-claimed days, non-working days (INNOTEK opt-in), or payment-option exclusions apply (`:242-263`). In that case the total of 211,212 drops by the amount of the excluded days |

---

# Source index (file:line)

Path base: `packages/domains/src/bstr-policy/` (but paths beginning with `cloud-expense-report/` are relative to `packages/domains/src/cloud-expense-report/`).

**Engine source of truth**

| Subject | Location |
|---|---|
| The 8 operators (`none`/`fixed`/`silbiLimit`/`unpaid`/`+`/`-`/`x`/`/`) | `utils/calc-condition-engine.ts:25-34`; arithmetic at `:118-148` |
| PERCENT determination | `utils/calc-condition-engine.ts:36`, `:131-136` |
| Tiered (`dailyDiff`) amt/pct branching | `utils/calc-condition-engine.ts:79-115` |
| The per-day application loop and sequential application | `utils/calc-condition-engine.ts:203-236`, `:399-435` |
| Candidate filter + `sortOrder` sorting | `utils/calc-condition-engine.ts:332-334` |
| Per-day fallback (`supersededByIds`) | `utils/calc-condition-engine.ts:187-191` |
| Determining the calculation detail's base amount | `utils/calc-condition-engine.ts:253-262` |
| Travel-day opts definition | `utils/calc-condition-engine.ts:288-297` |
| Three-way partition of travel-day conditions | `utils/calc-condition-engine.ts:371-379` |
| Foreign-currency KRW re-application (`applyCalcConditionsKRW`) | `utils/calc-condition-engine.ts:453-472` |
| Date position axis (absolute/relative) and assignment abandonment | `utils/calc-day-position.ts:57-166` |
| `calcMethod` date coverage (`departArrive`, `excludeN`, etc.) | `utils/calc-day-position.ts:150-169` |
| dayType/dateRange/periodRange guards | `utils/calc-condition-guards.ts:29-82` |

**Request and response contract**

| Subject | Location |
|---|---|
| Complete `RenewalLimitRequest` fields | `api/renewal-limit-api.ts` (`bstrDate` through `calcInputs`) |
| `CalcConditionInputs` | `api/renewal-limit-api.ts:100-160` |
| The `AppliedCalcCondition` zod schema | `api/renewal-limit-api.ts` (`AppliedCalcConditionSchema`) |
| `RenewalLimitResponse` | Same file (`limitAmounts`, `dayTypeMap`, `appliedConditions`, `calcEnabled`, `calcDeferred`, `calcPeriod`) |
| Apply/defer determination at lookup time | Same file (`applyValidatedCalcConditions`, `hasDeferredForeignCalc`, `isDailyCostTranKind`) |
| The actual request assemblers | `utils/calc-inputs.ts:36-83`, `utils/ruled-amount-calculator.ts:176-218`, `utils/rule-update-room.ts:78-147` |
| Travel-day detection | `utils/calc-inputs.ts:239-273` → `cloud-expense-report/features/build-travel-day-opts.ts:17-26` |

**Receipt field mapping**

| Purpose | Location |
|---|---|
| Lodging | `utils/rule-update-room.ts:156-268` (summing and check-out-day exclusion), `:282-303` (fields) |
| Meals | `utils/rule-update-food.ts:105-190` |
| Per diem (automatic receipt) | `cloud-expense-report/utils/daily-cost-amount-utils.ts:321-418`, `cloud-expense-report/features/daily-cost-receipt-builder.ts:89-160` |
| Shared derivation for plans and validation | `utils/ruled-amount-calculator.ts:82-121`, `:235-332` |
| Foreign-currency conversion | `utils/currency-utils.ts:11`, `:57-67`; `utils/rule-update-helpers.ts:40-137` |
| `resolveRuledAmount` (usage-period summing plus fallback) | `utils/rule-update-helpers.ts:192-215` |
| `dividedRuledAmount` (ratio-split children) | `utils/autofill-req-amount.ts:78-84`, `:145-151`; `utils/over-amount.ts:145-150` |

**Tests (where the values come from)**

| Value | Location |
|---|---|
| `x AMOUNT` / `x PERCENT` | `utils/calc-condition-engine.test.ts:152-155` |
| `+` ignores `operatorUnit` | `utils/calc-condition-engine.test.ts:158-159` |
| Order dependency | `utils/calc-condition-engine.test.ts:114-131` |
| The 4 dayType matching cases | `utils/calc-condition-engine.test.ts:342-345` |
| Tiered amt/pct and the many `diffBaseAmt` cases | `utils/calc-condition-engine.test.ts:253-322` |
| `calcBreakdown.baseAmount` = `diffBaseAmt` | `utils/calc-condition-engine.test.ts:419-433` |
| **Example B golden case (4 days with travel days)** | `utils/calc-condition-engine.test.ts:968-977` (condition definitions at `:939-959`) |
| The travel-day assignment abandonment guard | `utils/calc-condition-engine.test.ts:979-985` |
| A numeric `predepartType` → no match | `utils/calc-condition-engine.test.ts:812-827` |
| first/last selection when a travel-day condition is `dailyDiff` | `utils/calc-condition-engine.test.ts:747-809` |
| **Example C convert→condition (engine alone)** | `utils/calc-condition-engine.test.ts:521-535` |
| `%` is currency-neutral | `utils/calc-condition-engine.test.ts:537-546` |
| **Example C convert→condition (the full lookup path)** | `utils/ruled-amount-calculator.test.ts:238-265` |
| `calcDeferred` + travel-day opts | `utils/ruled-amount-calculator.test.ts:409-446` |
