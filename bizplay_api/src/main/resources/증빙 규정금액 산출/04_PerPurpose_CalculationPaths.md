# §4 Differences in the calculation path by purpose type

> Even for the same "ruled amount" (`ruledAmount`), **the calculation path is completely different depending on the purpose type (`tranKindType`).**
> **How many times** the limit lookup API (`POST /api/v2/bstr/policy/renewal/limit`) is called, **which response field** is used (the scalar `limitAmount` vs. the per-date map `limitAmounts`), and **the exceptions that do not use the response at all** all diverge by purpose.
>
> Every assertion in this section carries a `file:line` source. The root for the source files is `packages/domains/src/`.
> Anything unconfirmed is marked `⚠️unconfirmed` and collected in §4.13 at the end.

---

## 4.1 The branch point — where the split by purpose happens

Recalculation of receipt ruled amounts on an expense report branches into per-purpose modules in one place, `updateReceiptsWithRuleInfo`.

```
receipt.tranKindType === 'FOOD'  → updateFoodReceipt      (rule-update-food.ts)
receipt.tranKindType === 'ROOM'  → updateRoomReceipt      (rule-update-room.ts)
receipt.tranKindType === 'FUEL'  → updateFuelReceipt      (rule-update-fuel.ts)
everything else                   → updateDefaultReceipt   (rule-update-default.ts)
```

Source: `bstr-policy/utils/rule-update-utils.ts:119-130`

Precondition guard — if any one of the 4 below is missing, **recalculation is skipped entirely and the original receipt is returned unchanged** (`rule-update-utils.ts:107-109`).

| Context | Source |
|---|---|
| `bstrStartDate` and `bstrEndDate` (the trip period) | `rule-update-utils.ts:107` |
| `bstrPurposeId` (the trip purpose) | Same line |
| `draftUserId` (the drafter = the user for limit determination) | Same line |

Also, if recalculation of one receipt fails with an exception, **only that receipt keeps its original values** and other receipts are unaffected (`rule-update-utils.ts:117-134`). When an external system implements this calculation itself, the same isolation is the safe choice — it prevents one failure from zeroing out the whole document's amounts.

> ⚠️ **The branch determination looks only at the unprefixed values (`FOOD`/`ROOM`/`FUEL`).** If `HD_ROOM`, `HD_FOOD`, or `HD_FUEL` arrives, it **falls through to the generic path (`updateDefaultReceipt`)** rather than the dedicated module (`rule-update-utils.ts:119-130` — there is no call inside this function that normalizes `HD_`).
> Only per diem accepts both forms (see the §4.2 table). → §4.13 open item ①

---

## 4.2 The complete `tranKindType` value set

The values actually confirmed in the source form **two families**. No values were invented, and the definition site for each is given.

### (a) The unprefixed family — the backend `TranKindType` source of truth

> ⚠️ **There is no type called `TranKindTypeEnum` on the backend.** The source of truth is the single enum `common-core/src/main/java/com/newbizplay/constant/TranKindType.java:8-44`, and **the 12 prefixed and 17 unprefixed values in (a) and (b) below are members of that same enum** (it also contains 8 vehicle-management `CARMN_*` values).
> That is, the `HD_` family is not "legacy fallback only" but **a set of values that can normally be transmitted** — although the receipt ruled-amount branch looks only at the unprefixed values (see "Which family is used where" below).

`bstr-tran-kind/types/bstr-tran-kind-types.ts:34-51` (comment: "identical to the backend TranKindTypeEnum")

| Value | Korean | Ruled-amount path |
|---|---|---|
| `DAILY_COST` | 일비 (per diem) | Generic + per-diem-specific deferred application (§4.7) |
| `ROOM` | 숙박비 (lodging) | `updateRoomReceipt` (§4.4) |
| `TRANSPORT` | 교통비 (transportation) | `updateDefaultReceipt` + the GRADE exception (§4.3) |
| `FUEL` | 유류비 (fuel) | `updateFuelReceipt` (§4.6) |
| `FOOD` | 식비 (meals) | `updateFoodReceipt` (§4.5) |
| `COMPANION_INSURANCE` | 동승 보험료 (passenger insurance) | Generic |
| `CANCEL_FEE` | 취소 수수료 (cancellation fee) | Generic |
| `CAR_DEDUCTIBLE` | 자차 본인부담금 (own-car deductible) | Generic |
| `TOLL` | 통행료 (tolls) | Generic + the automatic-receipt gate (§4.8) |
| `PARKING_COST` | 주차 요금 (parking) | Generic |
| `AIR_COST` | 항공료 (airfare) | Generic |
| `COMMUNICATION_CHARGE` | 통신비 (communications) | Generic |
| `VISA_FEE` | 비자발급비 (visa fee) | Generic |
| `BOARDING_FEE` | 하숙비 (boarding) | Generic |
| `ROAMING_CHARGE` | 로밍비 (roaming) | Generic |
| `STAY_COST` | 체류비 (stay costs) | Generic |
| `ETC_COST` | 기타비용 (other costs) | Generic |

The subset the expense-report drawer narrows to separately (`BstrTranKindType`) includes `INCIDENTAL` (miscellaneous), which is not in the list above — `business-plan/features/receipt-import/etc-receipt-drawer/trankind-types.ts:7-17`.

### (b) The `HD_` prefixed family — legacy fallback responses and rule-configuration tab labels

`bstr-expense-rule/constants/index.ts:16-30`

> A similar table exists at `expense-policy/views/expense-policy-detail-helpers.ts:18-30`, but it is **not identical** — that one has 10 entries, omits `HD_FOOD` and `HD_ETC_COST`, and labels `ETC_COST` differently as "기타" (other).

| Value | Korean |
|---|---|
| `HD_DAILY_COST` | 일비 (per diem) |
| `HD_ROOM` | 숙박비 (lodging) |
| `HD_TRANSPORT` | 교통비 (transportation) |
| `HD_FUEL` | 유류비 (fuel) |
| `HD_FOOD` | 식비 (meals) |
| `HD_TOLL` | 통행료 (tolls) |
| `HD_CANCEL_FEE` | 취소수수료 (cancellation fee) |
| `HD_COMPANION_INSURANCE` | 동승보험료 (passenger insurance) |
| `HD_CAR_DEDUCTIBLE` | 자차본인부담금 (own-car deductible) |
| `HD_LODGING_COST` | 직원숙소이용금 (employee dormitory usage charge) |
| `HD_INCIDENTAL_COST` | 잡비 (miscellaneous) |
| `HD_ETC_COST` | 기타비용 (other costs) |

Why the `HD_` prefix exists and the normalization rule — the fallback path `/api/v2/trankind/list` can return values such as `HD_ROOM`, so the prefix is stripped before form-field mapping: `business-plan/features/receipt-import/etc-receipt-drawer/etc-receipt-form-helpers.ts:36-40` (`type.startsWith('HD_') ? type.slice(3) : type`).

> ⚠️ **The `INCIDENTAL` used by automatic receipts is not in the backend enum.**
> The miscellaneous-expense automatic-receipt builder writes `tranKindType: 'INCIDENTAL'` into the receipt DTO (`cloud-expense-report/utils/auto-receipt-utils.ts:293`), but the backend `TranKindType` only has `HD_INCIDENTAL_COST` and no `INCIDENTAL`. Which value to send when assembling a miscellaneous-expense automatic receipt externally is **unconfirmed**, and since it is an enum field a wrong value gives a 400 — we will confirm and reply.

### Which family is used where

| Site | Family used | Source |
|---|---|---|
| Receipt ruled-amount branch (`updateReceiptsWithRuleInfo`) | **Unprefixed values only** | `rule-update-utils.ts:119-130` |
| Per-diem determination (deferred application at lookup) | **Both** (`DAILY_COST` \|\| `HD_DAILY_COST`) | `bstr-policy/api/renewal-limit-api.ts:434-440` |
| Per-diem `activityDivision` transmission determination | **Both** | `rule-update-default.ts:112`, `ruled-amount-calculator.ts:206` |
| Grade-zone / companion iteration request assembly (`TRANSPORT`/`FOOD` axes) | Unprefixed values | `ruled-amount-calculator.ts:190`, `:213`, `:368-369` |
| Toll automatic receipts | Unprefixed `TOLL` | `bstr-policy/constants/toll-evidence.ts:14` |
| Rule-configuration screen tab labels | A **mixed mapping** of the `HD_` family and the unprefixed family | `bstr-expense-rule/constants/index.ts:16-30` |
| Form field mapping | Unprefixed values after stripping `HD_` | `etc-receipt-form-helpers.ts:36-40` |

> `HD_HOTEL` and `HD_MEAL` appear only in a **request-field comment** (`renewal-limit-api.ts:44`) and **test fixtures** (`renewal-limit-api.test.ts:24`, `:297-299`), and are absent from the (b) label table above.
> Whether they are ever used as actual response values is not confirmed in this repository. → §4.13 open item ②

---

## 4.3 Per-purpose calculation path table (summary)

| Purpose type | Module | Notable limit-lookup parameters | How the ruled amount is derived | Exceptions and pitfalls | Source |
|---|---|---|---|---|---|
| `TRANSPORT` | `rule-update-default.ts` | `vehicleType` = `normalizeVehicleType(receipt.vehicleType)` (**sent only for transportation**; `null` otherwise)<br>`bstrRegionId` sent (required for grade-zone matching)<br>`receiptEtcId` sent (round-trip and terminal policy) | `resolveRuledAmount(rule, receipt)` — sums `limitAmounts` over the usage period, or `limitAmount` if absent | **★ When `bstrCategoryType === 'GRADE'`, the ruled amount = `approvalAmount` (the approved amount)** — the limit response's amount is not used | `:125`, `:137`, `:145`, `:157-159`, `:36-73` |
| `ROOM` | `rule-update-room.ts` | `vehicleType: null`<br>**N split calls**, changing `bstrRegionId` per grade zone (`selections`)<br>The period is extended before and after by the allowed day count (Beyond Days) | Sums the per-zone responses' `limitAmounts` **per date**. The summing range is `usedStartDate` through `usedEndDate − 1 day` | **The check-out day is excluded** (the last day is not a stay) — ⚠️ **but only on the split-summing path where there is at least 1 grade zone (`selections`).** With no grade zone, the `resolveRuledAmount` fallback sums **through** `usedEndDate` and the same receipt picks up an extra day (`:106-109` early return → `rule-update-helpers.ts:197-208`). A same-day stay (0 nights) or a missing check-out gives 0.<br>Date removal by the payment option (`bstrPayOptionType`) is a **separate axis the backend has already applied** | `:84`, `:116-154`, `:171-174`, `:157-170` |
| `FOOD` | `rule-update-food.ts` | **N calls, changing `corporationUserId` for the drafter and each active companion**<br>`foodDivisionType` sent (breakfast/lunch/dinner, etc.) | **Simply sums the scalar `limitAmount`** from each user's response | The limit rises in proportion to the companion count. Rows with `bstrStatus === 'DRAFT_ONLY'` **must be excluded** (not excluding them inflates the limit).<br>A lookup failure for some users is ignored (partial success); if all fail, the original is kept | `:57-58`, `:63-93`, `:114`, `companion-utils.ts:16-22` |
| `FUEL` | `rule-update-fuel.ts` | `tranKindType: 'FUEL'` hard-coded, `bstrRegionId: null` (grade zones unused), `totalDistance` = the sum of route distances | Response scalar `limitAmount` → (conditionally) add tolls → **round up to the nearest 10 KRW** | **Sets `approvalAmount`, `supplyAmount`, and `ruledAmount` all to the same value**, with `vatAmount: 0` and `cardType: 'ETC'` hard-coded | `:56`, `:62`, `:64`, `:74`, `:92`, `:117-124` |
| `DAILY_COST` / `HD_DAILY_COST` | `rule-update-default.ts` + a per-diem-specific path | `activityDivision` (`ACTUAL`/`FIXED`) sent — only for per diem | **Defers** condition application at fetch time (`calcDeferred: true`) and keeps the original `limitAmounts` → applies **once** at consumption time together with the travel-day `opts` | Applying twice throws off the amount (idempotency guard). For automatic receipts, ruled amount = claim amount = approved amount | `:111-119`, `renewal-limit-api.ts:442-458`, `daily-cost-amount-utils.ts:321-418` |
| `TOLL` | The ruled amount itself uses the generic path | Only the automatic-receipt gate determination is separate (a lookup with `tranKindType: 'TOLL'` hard-coded) | The automatic receipt's amount = **the sum of T-map estimated tolls** (`sumTollFare`) — not the limit response's amount | If the payment type is not `'TOLL'`, no automatic receipt is created. Only private-vehicle (`PRIVATE`) legs are summed | `toll-policy-api.ts:22-31`, `toll-evidence.ts:20`, `:41`, `:62-69` |
| All other purposes | `rule-update-default.ts` | No purpose-specific parameters | Same `resolveRuledAmount` | If `tranKindType` is an empty string or `null`, the original is returned **without even calling the API** (400 defense) | `:99-102` |

---

## 4.4 Transportation (`TRANSPORT`)

### (1) The generic path

`updateDefaultReceipt` does 1 limit lookup and then fixes the amount with `resolveRuledAmount` (`rule-update-default.ts:120-146`, `:194`).

Transportation-specific request parameters:

| Parameter | Value | Source |
|---|---|---|
| `vehicleType` | `normalizeVehicleType(receipt.vehicleType)` — **only for transportation**; `null` for other purposes | `:125` |
| `bstrRegionId` | The grade zone ID. `findRegionIdForReceipt(bstrPeriodItem, receipt) ?? ctx.bstrRegionId ?? null` | `:104-105`, `:137` |
| `bstrDepartureId` | `getBstrDepartureId(ctx.bstrRoutes)` = **the first route's `departureId`** | `:131`, `bstr-route-utils.ts:9-12` |
| `bstrDestinationId` | `getBstrDestinationId(ctx.bstrRoutes)` = **the last route's `arrivalId`** | `:132`, `bstr-route-utils.ts:15-18` |
| `totalDistance` | Sent only for fuel (`FUEL`); `undefined` for transportation | `:139-140` |
| `receiptEtcId` | Sent when present — the backend uses it for round-trip ticket and terminal policy amount determination | `:145` |

`vehicleType` normalization — `SRT`, `ITX`, `SAEMAEUL`, and `MUGUNGHWA` **share the `KTX` rule**, so they are replaced with `KTX`. Other values pass through, and empty values become `null` (`bstr-policy/utils/vehicle-utils.ts:6-11`).

```ts
const KTX_EQUIVALENTS = ['SRT', 'ITX', 'SAEMAEUL', 'MUGUNGHWA'];
// → 'KTX'
```

> Transportation also sends the grade zone (`bstrRegionId`). The legacy old rule API (`/limit`) looked up transportation independently of grade zone, but **the renewal rule (`/renewal/limit`) looks up by grade zone, so not sending it means no rule matches** (comment at `rule-update-default.ts:134-136`).

### (2) ★ The grade-system (GRADE) exception — ruled amount = approved amount

**If an external system does not know this, its transportation ruled amounts will be entirely wrong.**

```ts
// GAP-5: transportation GRADE handling
if (tk === 'TRANSPORT' && rule.bstrCategoryType === 'GRADE') {
  return buildTransportGradeResult(receipt, rule);
}
```
`rule-update-default.ts:157-159`

`buildTransportGradeResult` **does not use the limit response's amount and puts the approved amount straight into the ruled amount**:

| Setting | Value | Source |
|---|---|---|
| `ruledAmount` | `receipt.approvalAmount ?? 0` | `rule-update-default.ts:67` |
| The `ruledAmount` argument to `calculateDefaultReqAmt` | `receipt.approvalAmount ?? 0` | `:46` |
| `bstrCategoryType` | `rule.bstrCategoryType` | `:70` |
| `overseasRuledAmount` | `rule.limitAmount` if the rule is in a foreign currency, otherwise `null`. Falls back to `null` if the receipt currency differs from the rule currency | `:59-63` |

The same exception exists on the claim-amount side — when `bstrCategoryType === 'GRADE'`, the approved amount is returned immediately, **before** the setting and foreign-currency branches (`bstr-policy/utils/autofill-req-amount.ts:163-165`).

Meaning: **for transportation whose payment basis is "grade," the ruled amount equals the actual approved amount, so no excess ever arises.** The grade constraint applies not as an amount cap but as a **seat-class selection constraint** (a UI branch based on the public-fare API — `business-plan/features/receipt-import/etc-receipt-drawer/EtcReceiptTransportFields.tsx:164-166`).

The 3 `bstrCategoryType` values: `MONEY` (amount) / `GRADE` (grade) / `STAR` (star rating) — `renewal-limit-api.ts:334-335`. The label map (`expense-policy/views/expense-policy-detail-helpers.ts:48-49` `mapStandard`) has **only two entries**, `MONEY`→"금액" and `GRADE`→"등급"; **there is no `STAR` label** (the value is returned as-is).

> Lodging's `STAR` (star-rating system) **does not replace the ruled amount.** It only restricts the receipt form's star option to at most `starGradeLimit` (`business-plan/features/receipt-import/etc-receipt-drawer/room-star-policy.ts:22`, `renewal-limit-api.ts:338-339`).
>
> ⚠️ **Note, though, that approved-amount substitution has two axes — the grade system is not the only one.**
>
> | Axis | Condition | Scope | Source |
> |---|---|---|---|
> | ① Grade system | `bstrCategoryType === 'GRADE'` | **Transportation only** | `rule-update-default.ts:157-159`, `:36-73` |
> | ② Actual-cost family | `isActualLikePayClass(bstrPayClassType)` = `ACTUAL`, `ACTUAL_FIXED` | **Common to all purposes** | `pay-class-utils.ts:40-47` → `rule-update-default.ts:204-205`, `rule-update-food.ts:124-126`, `rule-update-room.ts:302-304` |
>
> In the claim-amount cap, **② is evaluated before ①** (`autofill-req-amount.ts:160-162` vs `:163-165`).
> Lodging's `STAR` has neither axis (it only restricts options).

---

## 4.5 Lodging (`ROOM`)

`updateRoomReceipt` (`rule-update-room.ts:55-269`) — **the most complex ruled-amount path** in this repository.

### Processing order

| Step | Content | Source |
|---|---|---|
| 1 | Beyond Days lookup — the allowed lodging days before and after the trip, `[before, after]` | `:62-70` |
| 2 | `baseRule` lookup — based on the first grade zone. **Used solely to determine the payment type (`bstrPayClassType`)** | `:77-97`, `:102` |
| 3 | If there is no `baseRule` (including lookup failure), return the original receipt | `:99` |
| 4 | If there are no grade zones (`selections`), apply `resolveRuledAmount(baseRule, receipt)` once and finish | `:105-109` |
| 5 | If there are grade zones, sort by ascending `selectionId` and make **a parallel split limit lookup per zone** | `:111-154` |
| 6 | Sum the responses' `limitAmounts` in KRW **for dates within the receipt's usage period only** | `:183-263` |
| 7 | Assemble the final result with `buildRoomResult` (claim amount, remaining limit) | `:268`, `:282-371` |

### Allowed-day (Beyond Days) extension

```
baseRule lookup period = [trip start − allowDays[0], trip end + allowDays[1]]
```
`rule-update-room.ts:79-80`. `additionalReceiptType` is passed as `'ACCOM'` (`:67`).

Allowed-day calculation rules — `bstr-policy/utils/allow-days.ts:20-68`:

| Rule | Source |
|---|---|
| Only items among `issuedItems` where `item.itemType === 'EXPENSE_BEYOND_BSTR_PERIOD'` **AND** `item.activated === true` **AND** `value === 'true'` | `:31-36` |
| If there are no such items, `[0, 0]` | `:38` |
| From each item's `periodCondition.tranKindConditionDtos`, only conditions whose `tranKindId` matches are used | `:55-56` |
| When one purpose has multiple conditions, take the **maximum** of `preBstrAllowanceDays` and `postBstrAllowanceDays` separately | `:60-62` |
| If there is no `tranKindId` or the API fails, `[0, 0]` | `:28`, `:65-67` |

In the per-zone calls, **`−allowDays[0]` is applied only to the first zone and `+allowDays[1]` only to the last zone** (`rule-update-room.ts:118-126`).

### The date fields of a grade-zone segment (easy to confuse)

| Field | Actual meaning | Source |
|---|---|---|
| `selection.selectionName` | The **start date** of that zone segment | `rule-update-room.ts:121-123` |
| `selection.selectionErpCode` | The **end date** of that zone segment | `:124-126` |
| `selection.selectionId` | The grade zone (region grade) ID → the request's `bstrRegionId` | `:142` |

### Counting lodging nights — the check-out day is excluded (⚠️ grade-zone split path only)

> **This exclusion does not apply to every lodging receipt.** The `usedEnd = usedEndDate − 1` computation is inside the grade-zone loop (`:171-174`), and the preceding `:106-109` **early-returns** with `resolveRuledAmount(baseRule, receipt)` when `selections.length === 0`. That fallback sums `usedStartDate` through `usedEndDate` **inclusively** (`rule-update-helpers.ts:197-208`; there is no `−1`).
> → **A lodging receipt with no grade-zone selections is summed through the check-out day.**

```ts
const usedStart = receipt.usedStartDate ? new Date(receipt.usedStartDate) : null;
const usedEnd = receipt.usedEndDate
  ? addDays(new Date(receipt.usedEndDate), -1)      // exclude the check-out day
  : (usedStart ? addDays(usedStart, -1) : null);    // no check-out entered → 0 nights
```
`rule-update-room.ts:171-174`

| Situation | Summing range | Result |
|---|---|---|
| 1 night (`usedStart=D`, `usedEnd=D+1`) | `D` to `D` | 1 day's worth |
| Same day (`usedStart = usedEnd = D`, 0 nights) | `D` to `D−1` (an empty range) | **0 KRW** |
| No check-out entered | `D` to `D−1` (an empty range) | **0 KRW** |

> ⚠️ **This exclusion is a separate axis, unrelated to the payment option (`bstrPayOptionType`: `EXCEPT_END`, etc.)** (comment at `rule-update-room.ts:160-170`). The payment option makes the backend's `applyPayOptionDays` **pre-remove** dates from `limitAmounts` according to the rule configuration, whereas check-out-day exclusion applies **to every receipt regardless of configuration.** They look the same on the surface ("drop the last day"), so mistaking one for a duplicate of the other and removing it causes **the regression where 1 night is calculated as 2 days** (a real case: 70,000 → 140,000).

### The 3 paths for per-zone summing

`rule-update-room.ts:183-263` — each zone's response branches by currency and formula state.

| Path | Condition | Handling | Source |
|---|---|---|---|
| A | Foreign-currency rule + successful rate lookup + `hasDeferredForeignCalc` | Per-date `exchangeToKRW` on `limitAmounts` → re-apply via `applyCalcConditionsKRW` → sum in KRW within the usage period. `overseasRuledAmount` is **not accumulated** (foreign comparison impossible) | `:205-228` |
| B | A KRW rule but the conditions carry a foreign `diffCurrency`/`fixedCurrency` | Uses `limitAmounts` as the KRW base without conversion, converting only the diff/fixed amounts and re-applying the engine | `:229-249` |
| C | Everything else | Sums `limitAmount` within the usage period as-is. If in a foreign currency, accumulates KRW via `exchangeToKRW` plus the original foreign amount separately | `:250-262` |

Taking path A even once sets `hasDeferredForeignCalcApplied = true`, which passes `currencyCode = null` into the later `buildRoomResult` to **prevent re-conversion (double conversion)** (`:266-268`).

### Remaining limit (deducting already-used amounts)

`buildRoomResult` deducts lodging already claimed for the same dates on **other expense reports** under the same plan (`rule-update-room.ts:306-321`).

| Filter | Source |
|---|---|
| `tranKindType === 'ROOM'` | `:313` |
| **Excludes** `cardType === 'CORP'` (corporate card) | `:314` |
| **Excludes** the **excess rows** of an excess-amount split (`isExcessOverRow`) — they are self-pay (a receivable) and do not consume the company limit | `:315-317` |
| The first 10 characters (the date) of `usedStartDate` match the target receipt's | `:310`, `:318-319` |
| Prepaid-reservation receipts are **not excluded** (summing keeps the legacy policy) | Comment at `:308` |

The result becomes `alreadyUsedReqAmt` and enters the claim-amount calculation (`:321`, `:349`).
The foreign-currency running total (`alreadyUsedForeignReqAmt`) is computed **only when the current receipt is a foreign-comparison target and the entire already-used group is in the same foreign currency**; if even one differs, it becomes `undefined` and falls back to KRW comparison (`:323-337`).

### Non-payment on weekends and holidays

`rule-update-room.ts` has **no weekend/holiday-specific branch.** That handling belongs to the §3 axis (condition application):

- The response's `dayTypeMap` (`Record<YYYY-MM-DD, '평일'|'공휴일'|'주말'>`) — `renewal-limit-api.ts:354-355`
- If a condition has a `dayType` item, it is applied only on days where `dayTypeMap[date] === itemValue`. No value, or `'평일/공휴일'`, means no day-type constraint — `bstr-policy/utils/calc-condition-guards.ts:17-41`
- Non-payment is expressed as `operator: 'unpaid'` — `bstr-policy/utils/calc-condition-engine.ts:29`

So "no lodging payment on weekends" is **a condition created by the rule configuration**, not hard-coded per purpose.
The specific value combinations are §4.13 open item ③.

---

## 4.6 Meals (`FOOD`)

`updateFoodReceipt` (`rule-update-food.ts:43-193`).

### ★ The companion axis — the limit lookup is split per user

```ts
const companionUserIds = extractActiveCompanionDraftUserIds(ctx.companionPlanDtos);
const allUserIds = [ctx.draftUserId!, ...companionUserIds];
// fetchRenewalLimitAmounts is called in parallel per user
const totalRuledAmount = validRules.reduce((sum, r) => sum + (r.limitAmount ?? 0), 0);
```
`rule-update-food.ts:57-58`, `:63-93`, `:114`

**Why it must be split** — the limit is **determined per user.** The request's `corporationUserId` determines the "user group based on rank, position, job, and title" (`renewal-limit-api.ts:40-41`), and that group changes which rule row matches. So multiplying the drafter's limit by the companion count **gives a wrong amount when companions of different ranks are mixed in.** You must call per person, receive each one's limit, and sum them.
(Module header, `rule-update-food.ts:5-6`: "the business rule that the ruled amount rises in proportion to the companion count")

### What must be excluded from the companion list

```ts
const DRAFT_ONLY_STATUS = 'DRAFT_ONLY';
// exclude rows where bstrStatus === 'DRAFT_ONLY'
```
`bstr-policy/utils/companion-utils.ts:9`, `:16-22`

Besides actual companions, the backend also includes a **supplementary row for the original drafter (`bstrStatus === 'DRAFT_ONLY'`)** in `companionPlanDtos`. Counting it **inflates the limit** (comment at `rule-update-food.ts:55-56`, `companion-utils.ts:5-6`).

### Meal-specific parameters

| Parameter | Value | Source |
|---|---|---|
| `corporationUserId` | Different per user (the drafter plus active companions) | `rule-update-food.ts:70` |
| `tranKindType` | `receipt.tranKindType ?? 'FOOD'` | `:72` |
| `foodDivisionType` | The meal-classification enum name (`BREAKFAST`/`LUNCH`/`DINNER`/`ALL`, etc.). Not sent when absent | `:76`, `renewal-limit-api.ts:72-76` |
| `bstrRegionId` | The grade zone — `findRegionIdForReceipt(...) ?? ctx.bstrRegionId ?? null` | `:50-52`, `:82` |
| `vehicleType` | `null` | `:71` |
| `receiptEtcId` | The **same value** is shared across all N companions | `:87` |

> **Omitting `foodDivisionType` makes the amount unpredictable.** If not sent, the backend skips the rule-row filter, all of the breakfast, lunch, and dinner rows pass, and **the single highest-priority one is picked** (comment at `ruled-amount-calculator.ts:209-215`). You cannot tell which meal's limit you got, so always include it.

### Amount summing — based on the scalar `limitAmount` (different from lodging!)

| Path | Handling | Source |
|---|---|---|
| No formula deferral | **Sums the scalar `limitAmount`** across `validRules` → `convertRuledAmountToKRW` | `rule-update-food.ts:113-121` |
| Foreign currency + formula deferral (`hasDeferredForeignCalc(firstRule)`) | Sums each user's `resolveForeignCalcRuledAmount` result (in KRW), with `overseasRuledAmount = null` | `:106-112` |

The payment type and currency are fixed **based on the first response (`validRules[0]`)** (`:98-101`).

### Failure tolerance policy

| Situation | Handling | Source |
|---|---|---|
| Lookup fails for some users | Exclude only those users and sum the rest (partial success) | `:89-91`, `:95` |
| All fail | Return the original receipt unchanged | `:96` |

### Remaining limit

Structurally the same as lodging, but the source is the **companion-inclusive list** (`ctx.priorSettledReceiptsWithCompanion`) (`rule-update-food.ts:131`). The filters are `tranKindType === 'FOOD'`, `cardType !== 'CORP'`, excess rows excluded, and the same date (`:135-143`).

---

## 4.7 Fuel (`FUEL`)

`updateFuelReceipt` (`rule-update-fuel.ts:44-125`).

### Lookup parameters

| Parameter | Value | Source |
|---|---|---|
| `tranKindType` | **Hard-coded** `'FUEL'` (not the receipt's value) | `:56` |
| `totalDistance` | `totalDistance(ctx.bstrRoutes) \|\| undefined` = the sum of route `distance` | `:64`, `bstr-route-utils.ts:21-24` |
| `bstrRegionId` | **`null`** — fuel does not use grade zones | `:62` |
| `vehicleType` | `null` | `:55` |
| `tranKindId` | **Not sent** (other modules do send it) | The key is absent from the request object at `:50-69` |

> **The distance × unit-price calculation itself does not exist on the front end.** The front end sends `totalDistance` and receives the response's `limitAmount` (`:74`). The unit-price and distance-band rules are the backend's responsibility. → §4.13 open item ④

### Derivation order

```
defaultAmount = rule.limitAmount ?? 0
  ↓  (bstrPayClassType === 'FUEL' AND bstrPurposeId present)
  ↓  if isBstrIncludeFuelWithToll(bstrPurposeId) === true
defaultAmount += sumTollFare(ctx.bstrRoutes)
  ↓
defaultAmount = Math.ceil(defaultAmount / 10) * 10        // round up to 10 KRW
```
`rule-update-fuel.ts:74`, `:77-89`, `:92`

Whether tolls are included is a **2-stage settings lookup** — `bstr-policy/api/fuel-policy-api.ts:17-27`:

1. `GET /api/v2/business-setting/etc/BSTR/bstr-setting` → if `fuelWithTollUsed` is `false`, immediately `false`
2. `GET /api/v2/business-setting/etc/bstr/fuel-with-toll` → whether `bstrPurposeId` is in the purpose ID array
3. An exception at any stage gives `false` (`:25-27`). `rule-update-fuel.ts:86-88` also keeps the amount on failure.

The tolls summed are **only the `fare` of private-vehicle (`PRIVATE`) legs**:

```ts
export const TOLL_FARE_TRANSPORT_TYPES: ReadonlySet<string> = new Set(['PRIVATE']);
// fare > 0 AND transportType ∈ TOLL_FARE_TRANSPORT_TYPES
```
`bstr-policy/constants/toll-evidence.ts:41`, `:62-69`

`fare` is not user input but **"the toll for that leg if driven by car," filled in by T-map from the coordinates alone**, and because route calculation runs over every leg regardless of transport mode, values appear on taxi, train, and air legs too. Not counting only the vehicle family piles **tolls that never occurred** onto the fuel amount (`toll-evidence.ts:50-58`).

### ★ Settings unique to fuel

```ts
return {
  ...receipt,
  approvalAmount: defaultAmount,
  supplyAmount:   defaultAmount,
  ruledAmount:    defaultAmount,
  bstrPayClassType,
  ...(reqAmt != null ? { reqAmt, settleAmount: reqAmt } : {}),
};
```
`rule-update-fuel.ts:117-124`

| Item | Value | Source |
|---|---|---|
| `approvalAmount` = `supplyAmount` = `ruledAmount` | The same amount | `:119-121` |
| `vatAmount` (input to the claim-amount calculation) | `0` | `:102` |
| `cardType` (input to the claim-amount calculation) | Hard-coded `'ETC'` | `:104` |
| `overseasRuledAmount` (input to the claim-amount calculation) | `null` — no separate foreign-currency ruled amount | `:114` |

Also, **the payment-type fallback differs from other modules.** Fuel uses `rule.bstrPayClassType ?? receipt.bstrPayClassType ?? null`, **falling back even to the existing receipt value** (`:73`), while transportation, meals, and lodging use **only the API response value** (`rule-update-default.ts:161-168`, `rule-update-food.ts:100-101`, `rule-update-room.ts:101-102`).

When no rule is found (`rule === null`), the original receipt is returned (`rule-update-fuel.ts:71`).

---

## 4.8 Per diem (`DAILY_COST`), activity expense, and tolls

### (1) Per diem — a per-day ruled-amount map plus deferred condition application

Unlike other purposes, per diem **does not apply conditions at fetch time.**

```ts
function isDailyCostTranKind(response, requestTranKindType): boolean {
  const tranKindType = requestTranKindType ?? response.tranKindType;
  return tranKindType === 'DAILY_COST' || tranKindType === 'HD_DAILY_COST';
}
// applyValidatedCalcConditions
if (isDailyCostTranKind(response, requestTranKindType)) {
  const applied = applyCalcConditions(response);
  return { ...response, calcBreakdown: applied.calcBreakdown, calcDeferred: true };
}
```
`bstr-policy/api/renewal-limit-api.ts:434-440`, `:254-257`

| Field | State right after fetch | Source |
|---|---|---|
| `limitAmounts` | **Kept as the original (base amount)** — conditions not applied | `:251-256` |
| `calcBreakdown` | Only the calculation detail is filled (for the on-screen tooltip) | `:256` |
| `calcDeferred` | `true` | `:256`; field description at `:163-168` |

**The reason** — the full-day-departure / next-day-arrival travel-day `opts` are determined **at consumption time**, so applying without `opts` at fetch time makes the idempotency guard block re-application with `opts` and **travel-day assignment is lost** (`:228-232`).

It is applied exactly once at consumption time (= when the travel-day `opts` are known).

| Consuming path | What it does | Source |
|---|---|---|
| Expense report — `resolveDailyCostRuledAmount` | For foreign currency, per-date conversion → `applyCalcConditionsKRW(rule, base, opts)` → `Math.trunc` and sum **only for dates in `eligibleDates`** | `cloud-expense-report/utils/daily-cost-amount-utils.ts:321-418` |
| Plan / validation — `ruled-amount-calculator` path C | Even when `calcBreakdown` exists, if `calcDeferred === true` it applies once together with `opts` | `bstr-policy/utils/ruled-amount-calculator.ts:107-119` |
| Rule recalculation — the `updateDefaultReceipt` KRW branch | If `rule.calcDeferred`, applies `applyCalcConditionsKRW` **once here** and then `resolveRuledAmount` | `rule-update-default.ts:191-193` |

Conditions under which `resolveDailyCostRuledAmount` returns `null` (= scalar fallback) — `daily-cost-amount-utils.ts`:

| Condition | Source |
|---|---|
| `limitAmounts` missing or an empty map | `:333` |
| `rule.calcEnabled === false` | `:334` |
| 0 active conditions (`matched && !overridden`) **AND** `payType !== 'DIFF'` **AND** no payment-option reduction | `:337-351` |
| Foreign currency but the rate lookup failed | `:387-392` |

Conversely, there are **two cases where the per-day map must be summed even with no active conditions**:

| Case | Why | Source |
|---|---|---|
| `payType === 'DIFF'` (tiered payment) | The scalar `limitAmount` is 0 and the first-day/last-day tiered amounts exist only in `limitAmounts`. The scalar fallback would give `0 × days = 0` | `:344`, comment at `:61-64` |
| The payment option removes dates (`EXCEPT_START`/`EXCEPT_END`/`FIX_ONE_DAY`, etc.) | `limitAmounts` diverges from "all days × unit price." The test is "is there at least one selected day absent from `limitAmounts`?" | `:350`, comment at `:66-71` |

The per-date truncation rule — since the engine does not round, **`Math.trunc` is applied per date and then summed.**
Otherwise 0.5 KRW from things like `integer × 50%` leaks into receipts and labels (`:410-414`).

Amount settings for the per-diem automatic receipt — `cloud-expense-report/features/daily-cost-receipt-builder.ts`:

| Item | Value | Source |
|---|---|---|
| Total | `calcResult.totalAmount` when a formula applies, otherwise `krwUnit × eligibleDates.length` | `:136` |
| `approvalAmount` = `settleAmount` = `reqAmt` = `ruledAmount` | **All equal to the total** | `:157-160` |
| `tranKindType` | `'DAILY_COST'` | `:148` |
| Creation skipped | Payment type `NONE` (`isNonePayClass`) | `:72`, `bstr-policy/utils/pay-class-utils.ts:21-25` |
| Creation skipped | `eligibleDates` is empty or there is no payable amount (`hasDailyCostPayableAmount`) | `:76-81`, `daily-cost-amount-utils.ts:39-45` |

### (2) Activity expense — not an independent purpose type (a sub-classification of per diem)

There is **no** `tranKindType` value like `ACTIVITY*` in the source. Activity expense is expressed as the value of the form input item `ACTIVITY_EXPENSE_TYPE` and is used as **a matching axis in the per-diem (`DAILY_COST`) limit lookup.**

| Value | Meaning | Source |
|---|---|---|
| `ACTUAL` | Actual cost | `bstr-policy/constants/activity-expense-type.ts:12-17` |
| `FIXED` | Fixed | Same place |

If it is neither `ACTUAL` nor `FIXED`, it is left as `null`/`undefined` and **not sent** — then the backend applies no matching and other companies are unaffected (`bstr-policy/utils/calc-inputs.ts:214-218`, `rule-update-default.ts:111-119`).

The request field is `activityDivision` and it is sent **only on a per-diem lookup** (`renewal-limit-api.ts:66-70`, `rule-update-default.ts:112`, `ruled-amount-calculator.ts:204-208`).

> **Tenant-specific (do not mix with the default behavior)**
>
> | Item | Scope | Source |
> |---|---|---|
> | `activityDivision` matching | "For LG tenant activity-expense (per-diem) limit matching." No matching applied when not sent | `renewal-limit-api.ts:66-69` |
> | Per-diem receipt attachment and automatic receipts for actual-cost activity expense | **INNOTEK opt-in flag** `expense-report.activityActualDailyEvidence` | `bstr-policy/constants/activity-actual-daily-evidence.ts:11-13` |
> | Payment type `NONE` skip | "BZP has no NONE rule, so this is always false → no change to existing behavior" | `pay-class-utils.ts:19` |

A **manual** per-diem receipt in an actual-cost (`ACTUAL`) activity-expense context is **treated as actual cost (ruled amount = approved amount)** regardless of the policy payment type (`FIXED`/`LIMIT`, etc.):

```ts
const bstrPayClassType =
  !isActualLikePayClass(policyPayClass)
  && isActivityActualManualDailyCost(activityValue, tk, receipt.documentBound)
    ? ACTIVITY_EXPENSE_TYPE_VALUE.ACTUAL
    : policyPayClass;
```
`rule-update-default.ts:168-175`

The 3 determination conditions (`activity-actual-daily-evidence.ts:46-56`):
`activityValue === 'ACTUAL'` **AND** `tranKindType === 'DAILY_COST'` **AND** `documentBound !== true` (automatic receipts are excluded — the fixed component is handled entirely by the automatic per-diem receipt).

Related constant: `DAILY_COST_PAY_CLASS_ACTUAL_FIXED = 'ACTUAL_FIXED'` (`:19`), with the check `isDailyCostActualFixed` (`:25-29`).

### (3) Tolls (`TOLL`)

Ruled-amount recalculation itself takes the generic path (`updateDefaultReceipt`). The toll-specific logic is **the gate deciding whether to create an automatic receipt** and **that receipt's amount.**

| Item | Value / rule | Source |
|---|---|---|
| Purpose type constant | `TOLL_TRAN_KIND_TYPE = 'TOLL'` (backend `TranKindType.TOLL`) | `bstr-policy/constants/toll-evidence.ts:14` |
| Auto-creation gate | Created only when the rule lookup response's **payment type is `'TOLL'`** (`TOLL_PAY_CLASS`, backend `BstrPayClassType.TOLL`) | `:20`, `:26-28` |
| Gate lookup | One `fetchRenewalLimitAmounts` with `tranKindType: 'TOLL'` | `bstr-policy/api/toll-policy-api.ts:22-31` |
| On lookup failure | **`false` (do not create)** — creating a receipt without having confirmed the rule would leave a wrong amount behind | `toll-policy-api.ts:18-20`, `:29-31` |
| Automatic receipt amount | **The sum of T-map estimated tolls = `sumTollFare(bstrRoutes)`** — not the limit response's amount | `cloud-expense-report/features/toll-cost-receipt-creator.ts:8`, `toll-evidence.ts:62-69` |
| What is summed | Only legs with `transportType === 'PRIVATE'` (own vehicle) **AND** `fare > 0` | `toll-evidence.ts:41`, `:62-69` |
| Where it is created | As a miscellaneous receipt (`cardType: 'ETC'`) | `toll-cost-receipt-creator.ts:7` |
| Relationship to fuel | For a purpose subject to "include tolls in fuel," **the tolls are folded into the fuel receipt, so no separate toll receipt is created** | `toll-cost-receipt-creator.ts:10-12`, `:103-120` |
| The role of the other setting | It only decides "issue separately vs. fold into fuel." Whether to create at all is decided by **the rule's payment type** | `toll-evidence.ts:5-8` |

> Why a blacklist (`!== 'PUBLIC'`) is not used: in the legacy scheme a single `PUBLIC` represented air, rail, and bus, so that filter worked; the new enum splits it into `PUBLIC_AIRLINE`, `PUBLIC_TRAIN`, `PUBLIC_BUS`, and so on, and the same filter silently leaks (`toll-evidence.ts:36-39`).

---

## 4.9 The role of the shared helpers

Source of truth: `bstr-policy/utils/rule-update-helpers.ts` (some in `foreign-currency-utils.ts`).

### `resolveRuledAmount(rule, receipt)` — the rule for fixing the ruled amount

`rule-update-helpers.ts:192-215`

| Order | Condition | Result |
|---|---|---|
| 1 | `limitAmounts` exists and is non-empty, and the usage period can be derived | **Sums only the amounts for dates within the usage period (`usedStartDate` to `usedEndDate`).** If the sum is `> 0`, that value (`:196-208`) |
| 2 | The above sum is `0` | **Falls back to summing all dates** (`:211`) |
| 3 | `limitAmounts` itself is absent | The scalar `rule.limitAmount ?? 0` (`:214`) |

If `usedEndDate` is absent, `usedStartDate` is treated as the end date (`:198`).

> ⚠️ Because of fallback #2, **a receipt whose usage period does not overlap the rule period at all can produce the full-period sum rather than 0.** The lodging path does not use this function; it runs its own summing loop with no fallback (`rule-update-room.ts:250-262`) — so the two paths can give different results.

### `convertRuledAmountToKRW(raw, currencyCode, receipt, ctx)` — KRW conversion

`rule-update-helpers.ts:40-66`

| Situation | `krwRuledAmount` | `overseasRuledAmount` | Source |
|---|---|---|---|
| No `currencyCode`, or `'KRW'` | The raw amount as-is | `null` | `:46-48` |
| Foreign currency + successful rate lookup | `exchangeToKRW(raw, code, rate)` | `raw` (the original foreign amount) | `:64-65` |
| **Foreign currency + failed rate lookup** | **`raw` (the foreign amount placed straight into the KRW slot!)** | `raw` | `:60-62` |

The exchange-rate reference-date context passes 4 values — `bstrStartDate`, `bstrEndDate`, `receipt.bldat` (approval date), and `receipt.usedStartDate` — and picks among them by configuration (`:50-58`).

> ### ★ Warning — the rate-lookup-failure fallback **distorts** the excess amount (the direction depends on the currency)
>
> `if (!exchangeRate) return { krwRuledAmount: rawRuledAmount, overseasRuledAmount: rawRuledAmount };`
> (`rule-update-helpers.ts:60-62`)
>
> Example: a rule of `USD 100` with a failed rate lookup → the ruled amount is filled in as **100 KRW.** Since the excess is `MAX(0, usedAmount − ruledAmount)`, a smaller ruled amount means a larger excess, and **the direction of the distortion reverses depending on the currency** — for currencies where 1 unit is worth **more** than 1 KRW (USD, EUR, JPY, etc.) the ruled amount shrinks and the excess is **overstated**, while for currencies worth **less** than 1 KRW (VND, IDR, etc.) the ruled amount grows and it is **understated.** The warning at `over-amount.ts:35-37` is written assuming the latter and does not hold unconditionally:
>
> > ⚠️ On a rate-lookup failure there is an upstream fallback in `convertRuledAmountToKRW` that places the raw foreign amount into the KRW slot (`rule-update-helpers.ts`). In that case **the excess amount is understated** — and since the ruled-amount column uses the same value, this is not a problem specific to this function.
>
> **When an external system assembles the ruled amount itself, it is safer to treat a rate-lookup failure as an error rather than passing it through as "the raw amount."** The grade-zone / companion iteration path (`ruled-amount-calculator.ts:63-66`) in fact chose to `throw` instead of falling back — "summing foreign amounts as if they were KRW without a rate contaminates the amount, so block it with a throw rather than an implicit fallback."

### `findRegionIdForReceipt(bstrPeriodItem, receipt)` — grade-zone determination

`rule-update-helpers.ts:153-172`

| Order | Rule | Source |
|---|---|---|
| 1 | If there are no `selections`, `null` | `:157` |
| 2 | The usage date = `receipt.usedStartDate ?? receipt.approvalDate`. If both are absent, `null` | `:158-159` |
| 3 | If the usage date falls in the `selectionName` (start) to `selectionErpCode` (end) range, that `selectionId` | `:163-169` |
| 4 | If no segment matches, **fall back to the first `selection`'s ID** | `:171` |

If `selectionErpCode` is empty, the start date is treated as the end date (`:165`).
The call site falls back to `ctx.bstrRegionId` when this returns `null` (`rule-update-default.ts:104-105`, `rule-update-food.ts:50-52`).

### `resolveForeignCalcRuledAmount(rule, receipt, ctx)` — the simultaneous foreign-currency and formula path

`rule-update-helpers.ts:84-137`. Precondition: `hasDeferredForeignCalc(rule) === true` (= a rule whose engine application was deferred at fetch time).

The order is **"convert diff/fixed → (per-date conversion) → formula"**, and reversing it mixes currencies (`:69`).

| Case | Handling | Return | Source |
|---|---|---|---|
| `limitAmounts` is an empty map | Guard — nothing to calculate | `{ 0, null }` | `:95-97` |
| A KRW rule (or `currencyCode === null`) with foreign diff/fixed on the conditions | Uses `limitAmounts` as the KRW base as-is, converting only diff/fixed, then `applyCalcConditionsKRW` | `{ KRW, null }` | `:112-119` |
| Foreign-currency rule + successful rate | Per-date `exchangeToKRW` on `limitAmounts` → `applyCalcConditionsKRW` | `{ KRW, null }` | `:130-136` |
| Foreign-currency rule + failed rate | **No formula applied**; the sum of the foreign originals falls back into the KRW slot (to prevent mixing) | `{ sum, null }` | `:124-128` |

`overseasRuledAmount` is **always `null`** — a limit with a formula applied is unified into KRW, making foreign comparison impossible (`:81-82`). As a result it automatically falls back to `isCompareInForeign === false`.

Since the result is already in KRW, **the caller does not call `convertRuledAmountToKRW` again** (preventing double conversion, `:80`).

### `reconcileOverseasRuled(receiptCurrency, ruleCurrency, overseasRuled)` — invalidation on currency mismatch

`foreign-currency-utils.ts:134-145`

| Condition | Result | Source |
|---|---|---|
| `overseasRuled == null` or `<= 0` | `null` (0 and negatives also mean "no foreign limit defined") | `:141` |
| No rule currency, or `'KRW'` | `null` | `:142` |
| **Receipt currency ≠ rule currency** | `null` → use the KRW conversion path | `:143` |
| Otherwise | `overseasRuled` as-is | `:144` |

Why it is needed — if the rule is `USD 100` but the receipt is in `JPY`, leaving `overseasRuledAmount = 100` composes it with the receipt currency at display time and it is **wrongly shown as "JPY 100"**, and the foreign-currency cap comparison would directly compare amounts in different currencies (`:125-133`).

Application sites: `rule-update-default.ts:200`, `:59-63` (GRADE), `rule-update-food.ts:120`, `rule-update-room.ts:299`.

Related check — `isCompareInForeign` enters foreign-currency comparison only when **the receipt is in a foreign currency and `overseasRuledAmount > 0`.** Because the backend returns `0` rather than `null` for receipts with no foreign-currency rule, `0` is also guarded as "not possible" (`foreign-currency-utils.ts:117-122`).

### Other things that bear directly on the ruled amount

| Function | Rule | Source |
|---|---|---|
| `isActualLikePayClass(payClass)` | `'ACTUAL'` or `'ACTUAL_FIXED'` → **ruled amount = approved amount.** Common to all 4 modules | `pay-class-utils.ts:40-47`; applied at `rule-update-default.ts:204-205`, `rule-update-food.ts:124-126`, `rule-update-room.ts:302-304` |
| `exchangeToKRW(amount, code, rate)` | For 100-unit currencies (`JPY`, `IDR`, `VND`) it is `(amount × rate) / 100`, otherwise `amount × rate`. The result is **`Math.trunc`** (toward zero, including negatives) | `currency-utils.ts:11`, `:57-67` |
| `normalizeVehicleType` | `SRT`/`ITX`/`SAEMAEUL`/`MUGUNGHWA` → `KTX` | `vehicle-utils.ts:6-11` |
| `sumRouteFares(routes)` | A plain sum of `fare` over all routes. **The current fuel path does not use this; it uses `sumTollFare` (with the PRIVATE filter)** | `rule-update-helpers.ts:227-229` vs `rule-update-fuel.ts:83` |

---

## 4.10 The grade-zone (region grade) iteration structure — **you must call multiple times**

Source of truth: `bstr-policy/utils/ruled-amount-calculator.ts` (a single source of truth shared by plans and expense reports, `:1-11`).

> **If an external system assumes "one call is enough," the amount will be wrong on any trip with mixed grade zones.**
> Changing the grade zone changes which rule row matches, and even the currency may differ.

### Call count = number of grade zones × number of users

```
corpUserIds = (tranKindType === 'FOOD')
  ? [...active companion draftUserIds, draftUserId]
  : [draftUserId]

for each grade-zone selection (in parallel)
  for each corpUserId (in parallel)
     POST /api/v2/bstr/policy/renewal/limit
```
`ruled-amount-calculator.ts:245-249`, `:281-302`

So **only meals multiply along the companion axis**; every other purpose looks up only the drafter's own limit (comment at `:245-246`).

### Date extension of grade-zone segments

```
First zone : selectionName    − allowDays[0]
Last zone  : selectionErpCode + allowDays[1]
Middle zones: no extension
```
`ruled-amount-calculator.ts:251-265`

`allowDays` is obtained **per purpose** via `calculateAllowDays(issuedItems, tranKindId, null, fetchAllowBeyondDays)` (`:399-405`). Segments where both `selectionName` and `selectionErpCode` are absent are excluded from the lookup (`:277-279`).

The request is assembled by `buildLimitAmountsRequest` (`:176-218`), and the key points on the grade-zone axis are:

| Field | Value | Source |
|---|---|---|
| `bstrRegionId` | `sel.selectionId ?? null` — **different per segment** | `:198` |
| `bstrDate` | `sel.selectionName.slice(0, 10)` | `:195` |
| `bstrEndDate` | `sel.selectionErpCode.slice(0, 10)` | `:196` |
| `corporationUserId` | Different per user | `:186` |
| `vehicleType` | `normalizeVehicleType` only for `TRANSPORT`; `null` otherwise | `:189-192` |
| `activityDivision` | Only for `DAILY_COST`/`HD_DAILY_COST` | `:204-208` |
| `foodDivisionType` | Only for `FOOD` | `:212-215` |
| `calcInputs` | Per-segment time reassignment (`applySelectionTime`) — **that segment's times** rather than the global span | `:284` |

A lookup failure is **silently excluded** with `.catch(() => null)` and the rest continues to be summed (`:300`).
By contrast, **a rate-lookup failure `throw`s** and aborts the entire calculation for that grade zone (`:63-66`, `:126-130`) — the asymmetry is deliberate.

### Per-zone partial maps → per-day map summing

Each zone's responses are turned into a `Record<YYYY-MM-DD, KRW>` partial map (`buildSelectionPartial`, `:132-167`), and **within a zone, per-companion amounts are accumulated with `+=` on the same date** (`mergeKrwAmountsIntoPartial`, `:99-119`).

The 3 paths of `mergeKrwAmountsIntoPartial` (`:82-121`):

| Path | Condition | Handling |
|---|---|---|
| A | Foreign currency + `hasDeferredForeignCalc` | Per-date `exchangeToKRW` → `applyCalcConditionsKRW(res, base, opts)` then accumulate (`:92-101`) |
| B | Foreign currency, no formula deferral | Convert and accumulate as-is (total unchanged) (`:102-106`) |
| C | KRW | If `calcBreakdown` is present and it is not `calcDeferred`, it has already been applied → do not re-apply (idempotency). Otherwise apply `applyCalcConditions(res, opts)` **once** and accumulate (`:107-119`) |

### ★ Merging across grade zones is `Math.max`, not a sum

```ts
for (const partial of perSelectionMaps) {
  for (const [dateKey, amount] of Object.entries(partial)) {
    dailyRules[dateKey] = Math.max(dailyRules[dateKey] ?? 0, amount);
  }
}
```
`ruled-amount-calculator.ts:325-329`

**The reason** — per diem, lodging, and meals are "a single amount per date (per diem)," so even when the same date spans multiple segments (trip-period rows or grade zones) it is **counted only once.** Different dates each count once (identical to summing), while a duplicated date is merged by maximum to prevent double counting (for example, 2 trip-period rows both on `06-22` → preventing `56,000 × 2 = 112,000`) (comment at `:319-324`).

**Companion summing is already finished inside the zone (`buildSelectionPartial`), so it is unaffected by this `Math.max`** (`:324`). Swapping the two axes makes the meal companion limits disappear.

### Exchange-rate cache

Rate lookups for the same currency and reference date are performed only once via a **cache shared across purposes** (eliminating even companion and grade-zone duplicates) — `ruled-amount-calculator.ts:393-395`, `:240-242`. The in-flight `Promise` is shared, so even in parallel, lookups for the same key collapse into one (`:274-276`).

### Bulk lookup across multiple purposes — the deduplication axis

`fetchTranKindsLimitAmounts` (`:379-435`) performs **a separate lookup when the lookup axis differs**, even for the same `tranKindId`.

```ts
function tranKindLimitFetchKey(tk) {
  if (tk.type === 'TRANSPORT') return `${tk.id}:${tk.vehicleType ?? ''}`;
  if (tk.type === 'FOOD')      return `${tk.id}:${tk.foodDivisionType ?? ''}`;
  return String(tk.id);
}
```
`:367-371`

Two return shapes (`TranKindLimitAmounts`, `:348-359`):

| Key | Structure | Used for |
|---|---|---|
| `byTranKind` | `tranKindId → date → amount`. Results from multiple axes for the same id are **summed per date** | ⑪ total overrun validation |
| `byDivision` | `` `tranKindId:mealClassification` → date → amount ``. Filled **for meals only** | ⑫ per-meal excess-reason determination |

Because `byTranKind` sums across meals and the per-meal limit cannot be recovered from it, `byDivision` is kept separately (`:355-358`, `:421-422`). Even when empty, the `byTranKind[id]` key is guaranteed to exist, preventing a silent pass in validation ⑪ (`:417-418`).

### Context fields (`DailyRuledAmountContext`)

`bstr-policy/types.ts:63-80`

| Field | Meaning |
|---|---|
| `corpId`, `draftUserId`, `bstrPurposeId`, `bstrSegmentId`, `bstrType` | The basic rule-matching axes |
| `bstrRoutes` | Derives departure, destination, and distance |
| `bstrRegionSelections` | **Per-zone period selections** = the `selections` of `BSTR_PERIOD` among `issuedItems` |
| `companionPlanDtos` | Used for split calls per companion, **meals only** |
| `issuedItems` | Collecting `calcInputs` and `exceptionRuleInputItemIds`, plus the allowed-day calculation |
| `bstrPeriodTimeUsed`, `bstrPeriodTimeRepeatUsed` | Whether trip-time conditions are sent |

---

## 4.11 Exceptions that are fatal when reproducing externally (summary)

| # | Exception | What goes wrong if you don't know it | Source |
|---|---|---|---|
| 1 | **The transportation grade system** — when `bstrCategoryType === 'GRADE'`, the ruled amount = `approvalAmount` | Transportation ruled amounts are entirely wrong | `rule-update-default.ts:157-159`, `:36-73`, `autofill-req-amount.ts:163-165` |
| 2 | **The meal companion axis** — split limit lookups per user, then summed. Exclude `DRAFT_ONLY` rows | With companions of different ranks the amount is wrong, and counting `DRAFT_ONLY` inflates the limit | `rule-update-food.ts:57-58`, `:114`, `companion-utils.ts:16-22` |
| 3 | **Grade zones × users = N calls, merged per date with `Math.max`** | The amount is wrong on trips with mixed grade zones, and overlapping segments on the same date are double-counted | `ruled-amount-calculator.ts:281-302`, `:325-329` |
| 4 | **Lodging check-out-day exclusion** (`usedEndDate − 1 day`) — a separate axis from the payment option, but **grade-zone-split path only** | Omitting it calculates 1 night as 2 days (a real regression). Conversely, applying it to a receipt with no grade zone loses a day | `rule-update-room.ts:171-174`, `:160-170` |
| 5 | **Setting the 3 fuel fields together** plus rounding up to 10 KRW | The approved amount and supply value end up empty, throwing off the claim amount and VAT | `rule-update-fuel.ts:92`, `:117-124` |
| 6 | **Per-diem conditions applied exactly once** (`calcDeferred`) | Applying twice throws off the amount; not applying at all loses the conditions and gives the base-amount sum | `renewal-limit-api.ts:450-455`, `rule-update-default.ts:191-193` |
| 7 | **`ACTUAL`/`ACTUAL_FIXED` → ruled amount = approved amount** | Putting the limit amount on an actual-cost receipt produces a phantom excess | `pay-class-utils.ts:40-47` |
| 8 | **The rate-lookup-failure fallback** (the foreign amount placed into the KRW slot) | The excess amount is understated | `rule-update-helpers.ts:60-62`, `over-amount.ts:35-37` |
| 9 | **The toll amount is the sum of T-map `fare`** (PRIVATE legs only), not the limit response's amount | Tolls that never occurred get added, or the amount becomes 0 | `toll-evidence.ts:41`, `:62-69` |
| 10 | **If `tranKindType` is an empty string or `null`, no lookup is made at all** | An incomplete payload produces a 400 | `rule-update-default.ts:96-102` |

---

## 4.12 Things determined to be tenant-specific (do not mix with the default behavior)

| Item | Scope | Source |
|---|---|---|
| `activityDivision` (activity expense actual/fixed) limit matching | ⚠️ **There is no tenant branch in the code.** The gate is whether the form has an `ACTIVITY_EXPENSE_TYPE` input item (`rule-update-default.ts:111-119`, `calc-inputs.ts:214-218`) — "for the LG tenant" is comment text, and the real axis is **form configuration.** No matching is applied when not sent | `renewal-limit-api.ts:66-69`, `calc-inputs.ts:214-218` |
| Per-diem receipt attachment plus actual+fixed automatic receipts for actual-cost activity expense | **INNOTEK opt-in** flag `expense-report.activityActualDailyEvidence` | `activity-actual-daily-evidence.ts:1-13` |
| Payment type `NONE` (unpaid) skip | Fires only in tenants whose rules contain `NONE`. **BZP has no `NONE` rule, so it is always `false`** | `pay-class-utils.ts:19` |
| **Screen display** of the meal classification (`foodDivisionType`) field | Hidden only in tenants where `features['expense-report.hideFoodAndCancelFields']` is true (currently INNOTEK). **The request field itself is common to all tenants** | `etc-receipt-form-helpers.ts:57-58` |
| The 5 extended receipt fields (`starRating`, `roomType`, etc.) | **INNOTEK-only** display (`features.etcReceiptExtendedFields`). The validation and payload logic are identical for all tenants | `etc-receipt-form-helpers.ts:45-52` |
| ~~`HD_LODGING_COST` (employee dormitory usage charge)~~ → **not tenant-specific** | The suspension flag is a global constant with no tenant gate — `const IS_STAFF_LODGE_AUTO_RECEIPT_SUSPENDED = true` (`StaffLodgeCostSection.tsx:58`, used at `:94`, `:147`). **Creation is suspended for all tenants including BZP** (a product decision common to all tenants) | Same file `:58` |

> The items above **do not fire on the default BZP path.** When an external system reproduces this for the BZP tenant, implementing only the default behavior (the `else`/`default` branches) is sufficient.

---

## 4.13 ⚠️ Open items

| # | Item | Why it is unconfirmed |
|---|---|---|
| ① | Whether it is **intended or a gap** that receipts arriving as `HD_ROOM`, `HD_FOOD`, or `HD_FUEL` fall through to the generic path rather than the dedicated module | There is no `HD_` normalization at `rule-update-utils.ts:119-130`. No basis was found in the source for the asymmetry where only per diem accepts both forms |
| ② | ~~Whether `HD_HOTEL` and `HD_MEAL` are actual response values~~ → **Settled: they do not exist** | The backend source of truth `TranKindType.java:8-44` has only `HD_ROOM` and `HD_FOOD` and neither of these two (an exhaustive grep of the backend Java found 0). The client comment (`renewal-limit-api.ts:44`) is a stale **phantom value**, and because it is an enum, sending it gives a **400** |
| ③ | The specific condition combinations for lodging **non-payment on weekends and holidays** (the actual rule configuration of `dayType` values `'주말'`/`'공휴일'` plus `operator: 'unpaid'`) | The front end merely consumes `dayTypeMap` and `operator` as-is; which combinations are the actual operational configuration lives in the rule data |
| ④ | ~~The fuel distance × unit-price formula~~ → **the role of `totalDistance` is settled** | This value is not an input to a unit-price multiplication but **a matching axis for the calculation condition** — backend `BstrRenewalLimitService.java:347` `case "DISTANCE","distance" -> matchDistance(...)`, and `:629-653` compares it against `details.distance{value, op}` (op `lt`/`lte`/`gt`/`gte`, default `lt`). ⚠️ **`:645` `if (totalDistance == null) return false;`** — if it is not sent, a rule row carrying a distance condition **does not match.** The front end sends it only for fuel (`rule-update-default.ts:139-140`), so putting a distance condition on transportation or the like silently drops it. The actual unit-price table is still the backend's responsibility |
| ⑤ | Whether the "fall back to the full sum when the usage-period sum is 0" behavior in `resolveRuledAmount` (`rule-update-helpers.ts:211`) is intended policy | The lodging module's own loop has no such fallback (`rule-update-room.ts:250-262`), so the two paths can diverge. There is no explanatory comment |
| ⑥ | ~~Why only fuel has a different fallback~~ → **it is not "only fuel"** | There are **two** paths that fall back to the receipt value — fuel (`rule-update-fuel.ts:73`) and the **transportation grade system** (`rule-update-default.ts:40`). The three that use the response value only are the generic KRW path (`:168`), meals (`:101`), and lodging (`:102`). Why only those two fall back is still unconfirmed |
| ⑦ | Whether it is settled policy that lodging's `STAR` (star-rating system) has **no approved-amount substitution exception** like transportation's `GRADE` | `room-star-policy.ts:22` is used only to restrict star options and there is no ruled-amount branch. There is no evidence to the contrary either |
| ⑧ | ~~The current consumers of `sumRouteFares`~~ → **Settled: dead code** | There are 0 production consumers and only tests remain. A repository comment states it — `rule-update-utils.test.ts:70` "sumRouteFares has no production consumers left (dead code)" |
