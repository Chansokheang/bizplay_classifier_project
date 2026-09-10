# Ruled-Amount Setup & Validation for Attached Receipts

> This document covers how to fill in a receipt's **ruled amount** and what to validate when assembling an expense report directly through the API.
> As of 2026-09-08

Reading this document (the main document) alone is enough to clear the current blocker. Section-level detail lives in the six companion files in the same folder.

| File | Contents |
|---|---|
| `01_RawDataLookup_Contract.md` | ① Complete request/response field list for the limit lookup API |
| `02_ConditionApplication_Rules.md` | ② The rules for applying conditions per day to produce the final ruled amount (**core**) |
| `03_WorkedExamples.md` | 3 step-by-step worked examples of ② with intermediate values, for checking your own implementation |
| `04_PerPurpose_CalculationPaths.md` | Path differences for transportation, lodging, meals, fuel, per-diem, and tolls |
| `05_AutomaticReceipts.md` | Ruled amounts for system-generated receipts (per-diem, fuel, tolls) |
| `06_Validation.md` | ③ Excess amount, claim-amount cap, submission blocking (3 items) |
| **`규정금액_골든벡터.json`** | **71 test vectors for validating ②** — input↔output pairs extracted by actually running the engine. For implementation comparison |
| `규정금액_requestBody_샘플.json` | 3 receipts with ruled amounts filled in |
| `증빙_규정금액_프로세스.pptx` | Diagrams of the above |

---

## §0 Summary — what is missing

### 0-1. The ruled amount is a computed result, not an API response

There are three layers between the API and the ruled amount that ends up on a receipt.

| Layer | What it does | Owner |
|---|---|---|
| ① Raw data lookup | Returns the **base amount** by expense purpose, grade zone, and period | Backend `POST /api/v2/bstr/policy/renewal/limit` |
| ② Condition application | Applies the response's `appliedConditions[]` **per day** to produce the final ruled amount | **The caller (client)** |
| ③ Validation | Excess-amount calculation, claim-amount cap, submission blocking | The caller |

**② belongs to the client.** The backend only determines the **condition match result** (which conditions apply) and returns it; it does not perform the arithmetic that applies those conditions to the amount.

```java
// BstrRenewalLimitService.java:149
baseResult.setAppliedConditions(appliedConditions);
baseResult.setDayTypeMap(dayTypeMap);
// limitAmounts stays as the base amount (the front end applies conditions to derive the final amount)
```

A comment on the `buildAppliedConditions` block in the same file explains the background.

> Previously the backend calculated the amount directly. Now it passes only the condition information to the front end, and the front end applies the conditions to the base amount in `sortOrder` order to derive the final amount.

### 0-2. ⚠️ Our Swagger description is written backwards from the implementation

```
BstrPolicyRenewalController.java:42-43   "returns the final amount with adjustments applied"   ← Swagger description (WRONG)
BstrRenewalLimitService.java:149          limitAmounts stays as the base amount                ← actual implementation
```

**Building against the API documentation alone is guaranteed to be wrong.** If you were reading `limitAmounts` as the final ruled amount, this description is the cause. We will correct the controller description separately.

### 0-3. Current-state diagnosis — validation is not missing; it always passes

Measured from `260819 문의/requestBody_settlement_fixed.json`.

| Path | Value |
|---|---:|
| `bstrReceipts[0].tranKindType` | `"TRANSPORT"` |
| `bstrReceipts[0].approvalAmount` | 15,000 |
| `bstrReceipts[0].ruledAmount` | **15,000** ← same as the approved amount |
| `bstrReceipts[0].bstrPayClassType` | **null** |
| `bstrReceipts[0].excessReason` | `""` |

Since the excess formula is `MAX(0, usedAmount − ruledAmount)`, this gives `MAX(0, 15,000 − 15,000) = 0`.

**Filling the ruled amount with the approved amount makes the excess structurally always 0.** Ruled-amount validation is not "not working"; the values are set such that **it always passes.**

The scope of what gets neutralized differs per validation, however — **there is more than one determination axis** (§1-3).

| Validation | Axis | When `ruledAmount = approvalAmount` |
|---|---|---|
| **⑰** Unhandled excess split | Used amount | **Never fires** — the excess is always 0 |
| **⑫** Missing excess reason (single-item comparison branch) | Claim amount | **Never fires** — claim ≤ approved = ruled |
| **⑫** Excess reason (meal daily aggregate, full-period `ALL`) | Claim amount **aggregate** | **Can fire** — the aggregate may exceed the limit, and `ALL` re-queries the limit on the server, so it is independent of the receipt's `ruledAmount` |
| **⑪** Lodging / meal total overrun | Claim amount **aggregate** | **Can fire** — same reason |

So it is not "everything passes": **only the per-item determination is neutralized**, and aggregate determinations remain. That is why the symptom may look like "sometimes it works and sometimes it doesn't" rather than "validation is entirely missing."

`bstrPayClassType` (payment type) being `null` is a separate gap. Without this value, the claim-amount cap determination falls through to the `default` branch, so **the cap intended by the payment type is not applied.**

| Payment type | Intended cap | Actual when `null` |
|---|---|---|
| `FIXED` | `ruledAmount` | `default` branch |
| `LIMITED` | `MIN(ruledAmount, approvalAmount)` | `default` branch |
| `FUEL` | `ruledAmount` | `default` branch |

The `default` branch's result depends on card type — miscellaneous receipts (`ETC`) get `MIN(ruled, approved)`, while card purchase records (non-`ETC`) are **unlimited**.

> For reference, the 260819 receipt above has `bstrReceiptType = "ETC"`, so its cap is `MIN(15,000, 15,000) = 15,000` and it is **not a cap violation.** The only real problem in that receipt is the ruled amount. That said, for purposes whose payment type is `FIXED` or `LIMITED`, leaving this value empty decouples the cap from the ruled amount, so it is safer to fill it in **as a pair** with the ruled amount.

### 0-4. The sample we sent had this gap — corrections here

`정산서-공유자료/정산서_requestBody_샘플.json` (the original has already been distributed, so rather than editing it we correct it here)

| Location | Field | Distributed value | Correction |
|---|---|---:|---|
| `bstrReceipts[0]` (TRANSPORT) | `ruledAmount` | **0** | Insert the result of limit lookup → condition application. For a transportation grade system (`bstrCategoryType='GRADE'`), `approvalAmount` is correct → `04_PerPurpose_CalculationPaths.md` §4.4 |
| `bstrReceipts[0]` | `bstrPayClassType` | **null** | Insert the limit response's `bstrPayClassType` as-is (`FIXED`, `ACTUAL`, `ACTUAL_FIXED`, `LIMITED`, `FUEL`, `TOLL`, `NONE`) |
| `bstrReceipts[1]` (FOOD) | `bstrPayClassType` | **null** | Same as above |
| `bstrReceipts[*]` | `approvalTime` | `"081500"` / `"123200"` | `"08:15:00"` / `"12:32:00"` — it is a `LocalTime`, so `HH:mm:ss` (already corrected in §5-1 of the 260810 response; we confirmed it is reflected in your revision) |

For reference, the automatic receipt in the same sample (`etcReceiptSaveRequests[0]`, per-diem) is filled in **correctly** with `ruledAmount = 60,000` and `bstrPayClassType = "FIXED"`. This difference between automatic and manual receipts may have been the source of the confusion.

### 0-5. So what should you do

1. **Call ①** — `POST /api/v2/bstr/policy/renewal/limit`. Request fields and how to fill `calcInputs` are in `01_RawDataLookup_Contract.md`.
   - If there are multiple grade zones, or companions on a meal expense, you must **split the call per combination** → `04_PerPurpose_CalculationPaths.md` §4.10
2. **Implement ②** — apply the response's `appliedConditions[]` per day in `sortOrder` order. Rules are in `02_ConditionApplication_Rules.md`; worked examples in `03_WorkedExamples.md`.
3. **Put the values into the receipt fields** — see the §1 field map.
4. **Validate ② against the golden vectors** — feed the 71 entries in `규정금액_골든벡터.json` into your implementation and compare against `expected.limitAmounts`. **Do not skip this step after implementing from the documentation** — there are several places where an implementation drifts even when the prose is understood correctly (see the note below).
5. **Run ③** — use the §6.6 checklist in `06_Validation.md`.

> **What the golden vectors catch** — we got two things wrong ourselves while building these vectors.
> ① `fixed` and `silbiLimit` use **`fixedAmount`**, not `operatorValue`, and when that value is `null` they **leave the amount unchanged** (not 0 — see vector `B-09b`).
> ② `silbiLimit` is a **replacement**, not a `min`, so if the limit is larger than the base amount the amount goes **up** (`B-11b`).
> The rules were written correctly in `02_ConditionApplication_Rules.md`, and the implementation still diverged. Comparing against the vectors exposes it immediately.

---

## §1 The fields that carry the ruled amount

Three arrays are involved with the ruled amount, and **their shapes differ.**

| Array | Nature | Ruled-amount family fields |
|---|---|---|
| `issuedFields[].issuedReceiptDtos[]` | Manual receipts (issued-receipt links) | `ruledAmount`, `overseasRuledAmount`, `reqAmt` |
| `etcReceiptSaveRequests[]` | Automatic receipts (created **together with** the document) | `ruledAmount`, `reqAmt`, `bstrPayClassType` |
| `bstrReceipts[]` | Snapshot for slip lines (**receipts with a PK only**) | `ruledAmount`, `overseasRuledAmount`, `excessReason`, `reqAmt`, `bstrPayClassType` |

`bstrReceipts[]` elements carry **58 fields flat** (`slipAmt`, `accountSubject*`, `budgetDepartment*`, `taxCode*`), while the other two carry the same information in a **nested `slip` object**. Do not use one shape where the other is expected.

| `slip` object | Key count our client sends | Difference |
|---|---:|---|
| `issuedFields[].issuedReceiptDtos[].slip` | 19 | Also sends `docDate` (posting date) |
| `etcReceiptSaveRequests[].slip` | 18 | Does not send `docDate` |

> This is an **asymmetry in what we send, not a DTO constraint** — the backend uses the same `SlipDto` on both sides and `docDate` is part of it. Including `docDate` on an automatic receipt will not produce a 400.

### 1-1. Meaning of each field

| Field | Currency | What to put in it |
|---|---|---|
| `ruledAmount` | **KRW** | The final result of ②. For a foreign-currency rule, the converted KRW amount. The basis for excess determination and the claim-amount cap |
| `overseasRuledAmount` | Foreign currency | The original-currency amount of a foreign-currency rule. **Not used for excess determination** — it is for displaying the ruled-amount column and as a foreign-currency comparison cap |
| `reqAmt` | KRW | Claim amount. The cap differs by payment type → `06_Validation.md` §6.3 |
| `excessReason` | — | Excess reason. Required when the administrator has enabled the setting for that purpose and determination ⑫ (**claim-amount axis** — §1-3) is over. It can diverge from the excess-amount column (used-amount axis) |
| `bstrPayClassType` | — | Payment type. Use the limit response's value as-is. **If left empty, the per-payment-type cap (`FIXED`→ruled amount, etc.) is not applied and it falls through to the `default` branch** |
| `dividedRuledAmount` | KRW | The child row's own share of the ruled amount under ratio splitting (IO/WBS). **Not transmitted** (absent from `prepare-save-data.ts`). It is not display-only, however — **excess determination** (`over-amount.ts:145-150`), the **claim-amount cap** (`autofill-req-amount.ts:145-151`), and suspected violation ⑧ all use this value |
| `divisionOrder` | — | Split sequence number. `null` when not split. **Always send it** — omitting it makes the backend fall back to a "first non-null" rule per parent `receiptId`, so **the first row's reason overwrites the remaining rows** (this was the cause of an unerasable reason lingering on excess rows) (`prepare-save-data.ts:218-221`) |
| `complianceTypesStr` | — | Suspected-violation tags (comma-separated). This is a **derived field our screen calculates and saves** — if you assemble it yourself it will be missing or wrong. Submission is not blocked, but the administrator's reading changes → see 1-4 below |

### 1-2. ⚠️ On save, `null` becomes `0`

```ts
// prepare-save-data.ts:204-205
ruledAmount: r.ruledAmount ?? 0,
overseasRuledAmount: r.overseasRuledAmount ?? 0,
```

In on-screen determination a ruled amount of `null` (= rule not looked up → **no excess determination**) is treated differently from `0` (= a limit of 0 KRW → **entirely in excess**), but in the save payload `null` is forced to `0`. That means **a receipt whose rule could not be looked up is indistinguishable from a receipt with a 0 KRW limit after saving.**

In practice this means:

- For a purpose where no rule can be looked up, inserting `0` matches the current behavior.
- However, `0` is subject to excess determination, so the entire used amount becomes the excess and an ⑫ excess reason may be required.
- `excessReason` also becomes an empty string via `?? ''` (`:224`), so if there is an excess but no reason, submission is blocked.

### 1-3. ★ There is more than one determination axis — the used-amount axis and the claim-amount axis coexist

**What the ruled amount is compared against differs by purpose.** Implementing a single unified axis will produce determinations that differ from our screen.

| Target | Compared against | Foreign-currency handling | Source of truth |
|---|---|---|---|
| Excess-amount (self-pay) column, **⑰** unhandled-excess-split blocking | **Used amount** (`approvalAmount`; for per-diem, `supplyAmount+vatAmount`) | **KRW-converted vs KRW-converted** | `over-amount.ts` |
| **⑪** Lodging/meal total overrun, **⑫** required excess reason, on-screen per-day determination | **Claim amount** (`reqAmt`) | **Original currency vs original currency** (`foreignAmount` vs `overseasRuledAmount`) | `validate-total-exceed.ts`, `validate-excess-reason.ts`, `exceed-check-utils.ts` |

- **Why they differ** — the excess amount (self-pay) is an accounting figure derived from *how much was actually spent*, so it uses the used-amount axis. ⑪ and ⑫, by contrast, compare *how much was claimed* against the rule limit, so they use the claim-amount axis.
- **Practical consequence** — a receipt whose claim amount was auto-filled only up to the ruled amount **shows an excess in the excess column but passes ⑪ and ⑫.** Conversely, if the used amount is within the rule but you enter an arbitrarily large claim amount, only ⑪ and ⑫ block it.
- **Foreign currency diverges especially** — the excess column compares KRW-converted amounts, while ⑪ and ⑫ compare original-currency amounts. The same receipt can be over on one side and within on the other.

⑫'s sub-branches (`ACTUAL` / `ONE_DAY` non-meal / `ONE_DAY` meal aggregate / `ALL` full period) are in `06_Validation.md` §6.5.7 along with their determination formulas.

**Complete list of consumers of each axis**

| Axis | Consumers |
|---|---|
| Used amount | Excess-amount column, ⑰ excess-split blocking, **suspected violation ⑧ corporate-card overrun** (`compliance-attach.ts:226`), **excess-amount split payload** (`build-excess-split-payload.ts:121`), `merge-divided-receipt.ts:190` — the last three also use `getExcessJudgmentAmount` |
| Claim amount | ⑪ total overrun, ⑫ excess reason, on-screen per-day determination, **suspected violation ⑦ rule overrun** (`compliance-attach.ts:215`) |

### 1-4. Suspected-violation tags (`complianceTypesStr`) are also computed from the ruled amount

This is a **derived field** our screen calculates and saves. If you assemble the requestBody yourself, these tags will be missing or wrong — **submission is not blocked, but the administrator's reading of the result changes.**

| Tag | Determination formula | Axis | Setting gate |
|---|---|---|---|
| `RULED_AMOUNT_EXCEED` (⑦ rule overrun) | `ruledAmount != null ∧ (reqAmt ?? 0) > ruledAmount` | **Claim amount** | **None — always attached** |
| `CORP_CARD_LIMIT_OVER` (⑧ corporate-card overrun) | `getExcessJudgmentAmount(r) > (dividedRuledAmount ?? ruledAmount)` ∧ `cardType === 'CORP'` | **Used amount** | `bstrCorpCardLimitOverUsed` |

- Receipts that have had their excess amount split (`isExcessSplitReceipt`) are **excluded from both tags' determination** — otherwise you would see "excess is 0 but the remarks say rule overrun."
- **The axes diverge here too** — ⑦ uses the claim amount, ⑧ uses the used amount (the third case in §1-3).
- Tags are joined with commas. If none apply, the value is an empty string.
- Details are in Appendix A-0 of `06_Validation.md`.

---

## §7 Common misunderstandings

| # | Misunderstanding | Reality |
|---|---|---|
| 1 | The response's `limitAmounts` is the final ruled amount | It is the **base amount**. It becomes the final amount only after conditions are applied. The Swagger description says the opposite (§0-2) |
| 2 | You can just fill the ruled amount with the approved amount | Doing that on an arbitrary receipt makes the excess always 0 and neutralizes validation (§0-3). Substituting the approved amount is **correct** in **two cases** — ① the transportation grade system (`bstrCategoryType='GRADE'`, transportation only) and ② **actual-cost payment types (`ACTUAL`, `ACTUAL_FIXED`)**, which apply across all purposes and are evaluated **before** the grade system in claim-amount auto-fill (`autofill-req-amount.ts:160-162` vs `:163-165`). The claim-amount **cap** (`receipt-amount-limit.ts`) has no grade-system branch at all. In every other case you must insert the result of the rule lookup and condition application |
| 3 | You multiply the daily limit by the number of trip days | `limitAmounts` is already a **per-day map**. Do not multiply; sum only the days you need. If grade zones are mixed you must split the call per zone |
| 4 | With multiple grade zones, add up the per-zone amounts | Merging across grade zones is not a sum but a **per-date `Math.max`** (to prevent double-counting per diem). By contrast, **meal companions** are summed — the merge rules are opposite |
| 5 | If `matched: true`, apply that condition to every day | The backend only determines whether there is **at least one** day where it would apply. For a 40-day trip with an `lte 31` condition, days 32–40 must not have it applied |
| 6 | If a condition guard fails, skip that day | It is the exact opposite depending on the guard type. A `dayType`, `dateRange`, or `periodRange` failure means **skip (keep the amount)**; a `calcMethod` coverage failure (`isDayCovered=false`) means **that day's amount becomes 0**, wiping out even the accumulation from preceding conditions |
| 7 | Without `dayTypeMap`, all conditions apply | **Only "concrete-value" conditions that have a `dayType` item are treated as non-matching** (`calc-condition-guards.ts:29-41`). Conditions with no `dayType` item, and wildcards (`weekday/holiday`, empty string), are **applied as-is** |
| 8 | For foreign currency, apply the conditions and then convert | The order is reversed. It is **convert → apply conditions**. The engine **is not currency-aware** and the condition operands have no currency field, so reversing the order adds KRW values directly on top of a foreign-currency amount — worked example C shows roughly a **362×** difference (mixed currencies). The non-linearity of `max(0,·)` and truncation is a **separate**, third reason → `02_ConditionApplication_Rules.md` §6-3 |
| 9 | A foreign-currency receipt has a single comparison currency | **It depends on which check is running** (§1-3). The excess-amount column and ⑰ compare **KRW-converted amounts** (they do not use `overseasRuledAmount` or `foreignAmount`); ⑪ and ⑫ compare **original-currency amounts** (`foreignAmount` vs `overseasRuledAmount`) |
| 9-1 | What the ruled amount is compared against is fixed | **Two axes coexist** — the excess-amount column and ⑰ use the **used-amount** axis, while ⑪, ⑫, and the on-screen per-day determination use the **claim-amount** axis. Implementing a single unified axis will produce determinations that differ from our screen → §1-3 |
| 10 | Mixed currencies between the ruled amount and the excess amount on a foreign-currency row is a bug | It is **intended**. The ruled-amount column keeps the foreign-currency notation (for comparison against the original rule), while the excess amount is a fixed KRW figure. Not being able to reconcile the difference between the two columns is a cost accepted when the requirement was finalized |
| 11 | Filling in the `calcInputs` fields declared in the interface makes the condition match | Four of them — `isLodging`, `isFullDayDeparture`, `isNextDayArrival`, `isMealTwiceOrMore` — are **declared but never actually transmitted**, so those conditions **can never match** in the current implementation. The alternate path also differs per field: full-day departure, next-day arrival, and employee dormitory go through **`item.id`** in `exceptionRuleInputItemIds` (3 kinds only); group lodging uses **`selectionId`**; and `isMealTwiceOrMore` has **no alternate path** → `01_RawDataLookup_Contract.md` §2.3.2, §2.3.3 |
| 12 | The overseas value of `bstrType` is `OVERSEAS` | The value actually transmitted is **`"OVERSEA"`** (no S). The type comment is stale |
| 13 | `bstrPayClassType` includes `DAILY` | **That value does not exist.** Across all of `packages/` and `apps/`, there are **zero** `=== 'DAILY'` comparisons **used as a payment type** (comparisons of `periodUnit === 'DAILY'` use the same string but are a separate axis), and the 2 assignments are all test fixtures. The type JSDoc in 2 places (`receipt-types.ts:248`, `receipt-dto-types.ts:36`) is stale, and because zod uses `z.string()`, **a wrong value passes parsing and silently falls through to the `default` branch** (no error is raised). The actual set is the **7 values `NONE`, `ACTUAL`, `LIMITED`, `FIXED`, `ACTUAL_FIXED`, `FUEL`, `TOLL`** (backend `BstrPayClassType`). `TOTAL` also does not exist — ⚠️ **however, some of our automatic-receipt code uses `'TOTAL'`** (§8-1 F-7). Even if you have seen that value on screen, insert **only one of the 7 values or `null`** |
| 13-1 | `DAILY_COST` is a payment-type value | It is a **purpose (`tranKindType`) value** whose label is "per diem." The **payment type for a per-diem receipt is `FIXED`**, written directly by the builder. This confusion is the likely origin of the `DAILY` error |
| 14 | A malformed date produces an error | It **silently becomes "no rule."** Violating the fixed `YYYY-MM-DD` width means the HTTP request is never even issued and the result is `null`, and the consuming path reads that as "rule not looked up" and returns the receipt unchanged. This is the leading path by which the ruled amount ends up empty |
| 15 | If `calcBreakdown` is present, the response has conditions applied | Per diem sets `calcDeferred: true`, filling in only `calcBreakdown` while leaving `limitAmounts` raw. Judging solely by the presence of `calcBreakdown` leaves conditions unapplied; reading only the raw value applies them **twice** |
| 16 | `calcBreakdown.totalAmount` is the receipt's ruled amount | The engine sums the **entire map**, while the receipt covers **only the usage period**. Excluding the lodging check-out day (`usedEndDate − 1`) is ⚠️ **specific to the grade-zone (`selections`) split path**; without grade zones the sum includes through `usedEndDate` → `04_PerPurpose_CalculationPaths.md` §4.5 |
| 17 | Activity expense is a separate purpose type | It is **not** an independent `tranKindType`. The activity-expense actual-cost path uses `tranKindType='DAILY_COST'`, the same builder as per diem. Do not insert a value like `'ACTIVITY'` |
| 17-1 | All automatic receipts have "ruled amount = used amount" | **Meals (`FOOD`) and incidentals (`INCIDENTAL`) are exceptions** — `ruledAmount` receives the **daily unit price** while the other amount fields receive the total (unit price × days). For 2 or more days an excess arises on the claim-amount axis, making them subject to ⑪ and ⑫. "Ruled amount = used amount" applies only to the 4 kinds: per diem, fuel, tolls, and employee dormitory → `05_AutomaticReceipts.md` §5.2 |
| 17-2 | A toll automatic receipt aggregates tolls for all vehicles | **Only private vehicles (`PRIVATE`).** Corporate and company vehicle tolls are excluded (confirmed by product planning) |
| 18 | Automatic receipts are sent in `bstrReceipts` too | `bstrReceipts` carries **only receipts that have a PK**. Automatic receipts are created together with the document and have no PK, so they are not included |
| 19 | Extra fields not in the response are simply ignored | **The entire request returns 400.** `JacksonConfig` creates `new ObjectMapper()` directly, leaving `FAIL_ON_UNKNOWN_PROPERTIES` enabled → `05_AutomaticReceipts.md` §5.3.1 |
| 20 | If the payment type is empty, the claim-amount cap is unlimited | It depends on card type. In the `default` branch, miscellaneous receipts (`ETC`) get **`MIN(ruled, approved)`**, which is actually stricter (a ruled amount of 0 means a limit of 0); only card purchase records (non-`ETC`) are unlimited |

---

## §8 Open items and follow-ups

### 8-1. What we will correct or supplement

| # | Item | Evidence |
|---|---|---|
| F-1 | The **limit lookup controller's Swagger description** contradicts the implementation ("returns the final amount" → base amount) | `BstrPolicyRenewalController.java:42-43` vs `BstrRenewalLimitService.java:149` |
| F-2 | The `bstrPayClassType` **type JSDoc is stale in 2 places** — it lists the nonexistent values `DAILY` and `TOTAL`, and omits `ACTUAL_FIXED` and `TOLL`, which are actually used (`ACTUAL_LIMIT` is not in these two places but in `renewal-limit-api.ts:322` — see F-9). Because zod uses `z.string()`, wrong values pass silently (`autofill-receipt-defaults.test.ts:611-622` plainly shows `success === true` even with `'DAILY'`) | `receipt-types.ts:248-249`, `receipt-dto-types.ts:36` |
| F-3 | The `bstrType` comment says `OVERSEAS` but the transmitted value is `OVERSEA` | `renewal-limit-api.ts:62` vs `rule-update-default.ts:138` |
| F-4 | The validation pipeline header says "19 rows / 18 items" but measurement gives **20 rows / 19 items** | `validate-draft-pipeline.ts:6` |
| F-5 | The response wire format is **21 fields wider** than the client interface, and conversely the client-declared `baseAmount` has no corresponding field in the backend DTO | `renewal-limit-api.ts:353` vs `BstrTranKindLimitDto.java:24-72` |
| F-6 | On save, `ruledAmount: r.ruledAmount ?? 0` **loses the distinction between `null` and `0`** | `prepare-save-data.ts:204` |
| F-7 | ⚠️ **Three automatic-receipt builders use `bstrPayClassType: 'TOTAL'`, which is not in the backend enum** — the shared meal/incidental builder (`auto-receipt-utils.ts:259` — a single `createFixedCostReceipt` covers both) and employee dormitory (`:351`), so 2 code locations. That value is transmitted as-is into `etcReceiptSaveRequests` (`expense-report-save-utils.ts:153`) and the backend DTO receives this field as a **Java enum** (`EtcReceiptSaveRequest.java:145`) → **risk of a 400 on deserialization.** We have not verified this with an actual request. The employee-dormitory path is currently globally suspended, so it does not manifest | `BstrPayClassType.java:8-15` (7 values, no `TOTAL`) |
| F-8 | ⚠️ **The incidental automatic receipt uses `tranKindType: 'INCIDENTAL'`, which is not in the backend enum** — the backend `TranKindType` only has `HD_INCIDENTAL_COST`. `tranKindType` is also an enum field, so there is a 400 risk | `auto-receipt-utils.ts:293` vs `TranKindType.java:8-44` |
| F-9 | **Code comments and JSDoc are stale in many places.** If an outside reader opens the source it will appear to contradict the documentation — we will correct these in one pass | See each row |

F-9 details:

| Location | What it says | Reality |
|---|---|---|
| `BstrPolicyRenewalController.java:41-43` (Swagger) | "Returns the **final amount** with adjustments applied" | Keeps the base amount (F-1) |
| `renewal-limit-api.ts:8-11` (JSDoc) | "**Responds** with the final amount after sequential CalcCondition application" | Correct for the function's return value, **wrong for the HTTP response** |
| `renewal-limit-api.ts:44` | `HD_HOTEL`, `HD_MEAL` | **Nonexistent values.** The real ones are `HD_ROOM` and `HD_FOOD` |
| `renewal-limit-api.ts:42-43` | `AIRPLANE` | **Not an enum name.** It is the tmapMode code of the `AIR` constant |
| `renewal-limit-api.ts:336-337` | `FIRST_CLASS`, `ECONOMY` | **Nonexistent values.** The scheme uses suffixes (`ECONOMY_AIR`, etc.) |
| `renewal-limit-api.ts:322` | `ACTUAL_LIMIT` | **Nonexistent value** |
| `renewal-limit-api.ts:62` | `OVERSEAS` | Actually `OVERSEA` (no S) |
| `validate-excess-reason.ts:19` | `GET /api/v3/cloud-expense-report/exceed-reason-settings` | The v3 path **does not exist.** It is `/api/v2/bstr/expense-exceed-reason` |
| `validate-total-exceed.ts:289`, `:290` | 2 v3 path strings | Stale for the same reason |
| `validate-total-exceed.ts:9` (header) | Aggregate exclusion is "corporate card only" | Actually `CORP` **plus excess rows** |
| `autofill-req-amount.ts:23-30` | `GET /api/v3/requested-amount-setting` plus a 5-level priority | Actually `/api/v2/business-setting/etc/BSTR/requestedAmount`, and the order differs too |
| `receipt-types.ts:248`, `receipt-dto-types.ts:36` | `DAILY`, `TOTAL`, etc. | Nonexistent values (F-2) |
| `validate-draft-pipeline.ts:6` | "19 rows / 18 items" | Measured: 20 rows / 19 items (F-4) |
| `toll-cost-receipt-creator.ts:8` | "Sums the fare of non-PUBLIC routes" | Actually sums **PRIVATE only** |

### 8-2. Items needing further confirmation (40 total)

Each section-level document has its own table at the end. Only those that **affect ruled-amount calculation** are reproduced here.

| # | Item | Where |
|---|---|---|
| U-1 | The **source** of 6 `calcInputs` fields (`isMealTwiceOrMore`, `isLodging`, `isFullDayDeparture`, `isNextDayArrival`, `departmentId`, `companyCode`) — the backend consumes them but the client does not fill them. Which of two paths is authoritative is undecided | `01` §2.8 ①–⑥ |
| U-2 | The `silbiLimit` operator has no uppercase alias — if the backend sends it uppercase, **the amount is left unchanged** | `02` open item ① |
| U-3 | **The engine has no guard** that reads the `dayIndex` (day-option) condition | `02` open item ③ |
| U-4 | Whether it is intended that a `weekend` condition does not apply on a day that is both a weekend and a holiday | `02` open item ④ |
| U-5 | Whether it is intended or a gap that `HD_ROOM`, `HD_FOOD`, and `HD_FUEL` fall through to the generic path rather than a dedicated module (only per diem accepts both forms) | `04` §4.13 ① |
| U-6 | The **silent fallback** in `resolveRuledAmount` — if the sum overlapping the usage period is 0, it sums and returns the **entire** map. When date keys are misaligned, the result is the full-period sum rather than 0 | `04` §4.13 ⑤ |
| U-7 | `convertRuledAmountToKRW` **inserts the raw foreign-currency amount into the KRW slot when the exchange-rate lookup fails** → the excess amount is understated. The grade-zone path chose to `throw` in the same situation (inconsistency between paths) | `04` §4.9 |
| U-8 | Whether it is a settled policy that lodging `STAR` (star-rating system) has no approved-amount substitution exception like transportation `GRADE` | `04` §4.13 ⑦ |
| U-9 | Automatic meal receipts do not set `foodDivisionType` (which meal), yet ⑫ determines per meal | `05` M-2 |
| U-10 | The 3 foreign-currency fields (`overseasRuledAmount`, `overseasApprovalAmount`, `overseasUsed`) are **dropped** from the automatic-receipt save payload | `05` M-1 |
| U-11 | Toll automatic receipts do not set `bstrPayClassType` — how the backend handles `null` | `05` M-5 |
| U-12 | Whether it is intended that `getDivisionTotalMax` has no `FUEL` case (unsplit gives `FUEL → ruledAmount`, but the split total gives `Infinity`) | `06` U-1 |
| U-13 | Whether the backend validates `bstrPayClassType` at runtime (the client's zod does not validate the value set) | `06` U-8 |
| U-14 | **Whether it is intended that the excess-amount column and ⑰ use the used-amount axis while ⑪ and ⑫ use the claim-amount axis** — the excess-amount (self-pay) axis was explicitly decided by the 2026-08-25 requirement, while ⑪ and ⑫ are legacy carry-overs. That is how you get the combination "the screen shows an excess but no excess reason is required" (or the reverse). With foreign currency even the comparison currency diverges (KRW-converted vs original currency). We will confirm and reply | §1-3, `06` §6.1, §6.5.7, Appendix B |

### 8-3. Out of scope

- The **advance-payment (imprest) settlement flow** is a KSOE opt-in and is not covered in these materials.
- **Tenant-specific branches** are marked "tenant-specific" in the section documents. Do not implement them mixed in with the BZP default behavior. The applicable ones: activity-expense actual-cost per-diem receipts (INNOTEK opt-in), `activityDivision` limit matching (LG), meal-classification screen display (INNOTEK), SAP budget-control skip (KSOE), and `HD_LODGING_COST` employee-dormitory usage charges (creation suspended pending product-planning sign-off).
- Validation ② unselected region/country is **temporarily disabled**, and ⑦ required description is **skipped for all tenants**.

---

## §9 Sample walkthrough — `규정금액_requestBody_샘플.json`

One expense report containing 3 receipts. Business trip **2026-08-27 to 2026-08-30** (4 days, Busan, a single domestic grade zone).

**The field set is nearly identical to the `정산서_requestBody_샘플.json` we sent previously** — the only difference is the **addition of `divisionOrder`** to `bstrReceipts[]` (it was missing from the previous sample, and our client always sends it — see §1-1). Not a single key was added or removed at any level: top level, `issuedFields`, `issuedReceiptDtos`, `etcReceiptSaveRequests`, `bstrReceipts`, or `slip`. Only the **values** changed. (Since `FAIL_ON_UNKNOWN_PROPERTIES` is enabled, adding fields produces a 400, so we matched it deliberately.)

### 9-1. Amount consistency across the 3 receipts

| Receipt | Purpose | Card | Payment type | Used | Ruled | Claim | Excess<br>(used-amount axis) | ⑫ result<br>(claim-amount axis) | `excessReason` |
|---|---|---|---|---:|---:|---:|---:|---|---|
| ① KTX | `TRANSPORT` | `CORP` | `LIMITED` | 47,000 | 60,000 | 47,000 | **0** | Within | `""` |
| ② Hotel, 3 nights | `ROOM` | `CORP` | `FIXED` | 180,000 | 130,000 | 180,000 | **50,000** | **Over** | **Provided** |
| ③ Per diem (automatic) | `DAILY_COST` | — | `FIXED` | 120,000 | 120,000 | 120,000 | **0** | Within | — |

- **Why ①'s claim amount is 47,000** — `cardType='CORP'` is priority 1 for the cap, so it applies before the payment type. The cap is `approvalAmount` = 47,000. The ruled amount of 60,000 is not the cap.
- **Why ② uses a corporate card (important)** — with `CORP` the cap is `approvalAmount` (180,000), so **the claim amount can exceed the ruled amount.** That makes **both** determination axes register an excess.
  - Used-amount axis (excess column, ⑰): `MAX(0, 180,000 − 130,000)` = 50,000
  - Claim-amount axis (⑫): `ruledAmount(130,000) < reqAmt(180,000)` → over → `excessReason` required.
    ⚠️ **Two preconditions** — ① the administrator has turned the excess-reason setting **ON** for that purpose, and ② the limit type is **`ONE_DAY`** (single-item comparison). With `ALL`, the server re-queries the limit, making it independent of the receipt's `ruledAmount`
  - The real business flow is to pay the over-limit portion with a corporate card and then split off only that portion as self-pay (a receivable); excess-amount splitting (⑰) is exactly that feature.
- **⚠️ If you instead set ② to a personal card (`ETC`) + `FIXED`, the two axes diverge** — the cap becomes `ruledAmount` (130,000), so the claim amount cannot exceed the ruled amount. **⑫ therefore does not fire** (`130,000 < 130,000` = false) and only the excess column retains 50,000. In that case `excessReason` is not required and only ⑰ (when the setting is ON) demands a split. **This is a real example of the same receipt with the same amounts producing divergent determinations** → §1-3
- **③'s used amount for per diem** — for an automatic per-diem receipt, `approvalAmount` comes back as 0 on restore, so the used amount is read as **`supplyAmount + vatAmount`**. That is why all three amounts are 120,000.
- **If the ⑰ excess-split setting is ON, this sample will not submit as-is** — ② still has 50,000 of excess, so a split must be performed first. Since the purpose of the sample is to demonstrate ruled-amount and excess-reason setup, no split rows were included.

### 9-2. Where each ruled amount came from

| Receipt | How the ruled amount was derived |
|---|---|
| ① 47,000 → ruled 60,000 | A simple case where no condition applies to the base amount in the limit lookup response |
| ② 180,000 → ruled 130,000 | **The same figures as worked example A in `03_WorkedExamples.md`.** 100,000/day → weekday ×80% → **trip classification ×50%** → per-day 40,000, 40,000, 50,000, 50,000 → **excluding the check-out day (08-30)**, 3 nights sum to 130,000. See that document for step-by-step intermediate values |
| ③ 120,000 | 4 days × 30,000, no conditions, summed after per-day `Math.trunc`. **For a case involving tiered rates and travel days, see worked example B in `03_WorkedExamples.md`** (the same structure yields 211,212) |

Only ② was given conditions so that within a single document you can compare how values diverge depending on whether conditions apply.

### 9-3. Slip amounts (`slip`) are back-derived from the claim amount

These are the rules for unsplit receipts (`bstr-policy/utils/compute-slip-amounts.ts` → `tax-integration/utils/vat-split-calculator.ts`).

- `slipAmt` = **`approvalAmount`** (used amount). Not the claim amount.
- `slipSplAmt` and `slipVatAmt` are separated out of the claim amount, **in exactly this evaluation order**:
  - ⓿ **Claim-amount setting not in use** (`isRequestAmountUsed = false`) → use the receipt's supply value and VAT as-is (**this is the first branch** — in this case nothing is separated out of the claim amount)
  - Claim == approved → use the receipt's supply value and VAT as-is
  - Claim == supply value → supply value as-is plus the receipt's VAT
  - Receipt VAT == 0 → the full claim amount goes to the debit, VAT is 0
  - Otherwise (direct entry) → `supply = ceil(claim × 10 ÷ 11)`, `VAT = claim − supply`

Here is how the sample actually resolved.

| Receipt | Branch | `slipAmt` | `slipSplAmt` | `slipVatAmt` |
|---|---|---:|---:|---:|
| ① | Claim == approved → receipt values as-is | 47,000 | 42,727 | 4,273 |
| ② | Claim == approved → receipt values as-is | 180,000 | 163,636 | 16,364 |
| ③ | Receipt VAT 0 → full amount to debit | 120,000 | 120,000 | 0 |

In this sample all three receipts either have the claim equal to the approved amount or have zero VAT, so none take the back-derivation path. **When the claim amount is filled only up to the ruled amount** (for example `ETC` + `FIXED`), the `10/11` back-derivation kicks in, giving `supply = ceil(claim × 10 ÷ 11)` and `VAT = claim − supply`. In that case `slipAmt` (= approved amount) and `slipSplAmt + slipVatAmt` (= claim amount) **differing is normal** — the slip total is the used amount while the debit and VAT reflect the claim amount.

### 9-4. Total fields

| Field | Value | Derivation |
|---|---:|---|
| `totalBstrAmount` | 347,000 | Sum of manual receipt `reqAmt` plus automatic receipts (`47,000 + 180,000 + 120,000`) |
| `totalPointAmount` | 0 | Sum of `reqAmt` where `cardType ∈ {BZP_POINT, BZP_MONEY}` |
| `totalSettleAmount` | 120,000 | Sum of `reqAmt` where `cardType ∈ {ETC, PERSONAL, MY_DATA}` plus automatic receipts (amount to be reimbursed) |
| `totalPersonalAmount` | 120,000 | **The same value** as `totalSettleAmount` |
| `lodgingCostAmount` | 180,000 | Sum of `ROOM` receipt `reqAmt` plus automatic lodging |
| `dailyCostAmount` | 120,000 | Sum of automatic per-diem receipts |

The corporate-card (`CORP`) transportation of 47,000 and lodging of 180,000 are excluded from the amount to be reimbursed, because the company already paid. That leaves only the automatic per-diem receipt's 120,000.

⚠️ **Approval-cancellation (`approvalCanceled=true`) deduction applies to only 3 totals** — `totalBstrAmount`, `totalPointAmount`, and `totalSettleAmount` (`receipt-utils.ts:90-97`, `:109`, `:165-172`). `lodgingCostAmount` and the per-purpose subtotals (`dailyCostAmount`, etc.) simply sum `reqAmt` and **do not deduct** (`expense-report-save-utils.ts:65-83`).

### 9-5. What this sample does not verify

| Field | Status |
|---|---|
| All identifiers (`paperId`, `tranKindId`, `accountSubjectId`, `budgetDepartmentId`, `taxCodeId`, `bstrPurposeId`, `bstrSegmentId`, `corporationUserId`, `receiptId`, `id`, `imageIds`, `fileIds`) | **Placeholders.** These are master data per tenant and per form, so the real values must be looked up individually. They have no effect on amount arithmetic |
| `settleAmount` | We used the same value as the approved amount (matching the previously distributed sample). Whether this field is the used amount or the confirmed settlement amount in an over-limit case is ⚠️ **unverified** — the code only passes it through and does not derive it |
| ~~`REQUEST_STAFF_LODGE` item~~ | **Removed from the sample.** That item is not "group lodging" but an **employee-dormitory request** (KSOE and INNOTEK only); group lodging goes through the `selectionId` path under `BSTR_SELECT` → `01_RawDataLookup_Contract.md` §2.3.2, `03_WorkedExamples.md` pitfall 1-1 |
| Approval line (`approvalLines`) | A minimal form with the drafter plus one approver. The real approval line follows the form configuration |
| `complianceTypesStr` | We set `"RULED_AMOUNT_EXCEED"` on ② — ⑦ rule overrun has no setting gate and is attached unconditionally whenever `reqAmt > ruledAmount` (§1-4). ⑧ `CORP_CARD_LIMIT_OVER` would be attached alongside it when the `bstrCorpCardLimitOverUsed` setting is ON, so this value **assumes that setting is OFF** |
| Budget department name, ERP code, account subject code, tax code | Replaced with **neutral placeholders** (`예산부서A`, `DEPT001`, `ACCT001`, `TX1`). The real values are customer master data |
| The per-diem automatic receipt's `mestName` and usage period | The sample uses `"일비 증빙"` with a range of `08-27` to `08-30`, but the actual value our builder produces is `"일비용 증빙"`, and it fills `usedStartDate` and `usedEndDate` with **the same single receipt date** (`auto-receipt-utils.ts:178`, `:184-185`). The backend accepts a range too, but it differs from "the value we produce" |
