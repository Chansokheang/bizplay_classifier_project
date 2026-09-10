# §5 The ruled amount on automatic receipts

> Within the 3 ruled-amount layers, this covers the ruled amount on **receipts the system creates automatically** (per diem, fuel, tolls, etc.).
> The transmission arrays and amount semantics differ from manual receipts that users register themselves, so read this separately.
>
> Every assertion in this document carries a `file:line` source. Anything unconfirmed is marked `⚠️unconfirmed` and collected in the "Open items" section at the end.

---

## 5.0 Automatic and manual receipts take different transmission paths

In the expense report's requestBody, receipts are carried in **two different arrays.** Which array a receipt goes into determines everything about who derives the ruled amount and what the field contract is.

| | Manual receipt | Automatic receipt |
|---|---|---|
| What | Card purchase-record import, other/simplified receipt registration | Per diem, fuel, and tolls (and the activity-expense path) that the system creates per the rules |
| When created | **Before the document** — a Receipt is created through a separate API to obtain a PK | **Together with the document** — no PK |
| Transmission fields | `receiptIds` + `issuedFields` | **`etcReceiptSaveRequests`** |
| Slip snapshot (`bstrReceipts`) | Included (has a PK) | **Not included** (§5.5) |
| Origin of the used amount | The card approval amount (a fact arriving from outside) | **The rule-derived amount** (a calculation result) |

Source: the existing shared material `../README.md:30` "Key conclusion 3" — "Only rule-based automatic receipts are created together with the document (`etcReceiptSaveRequests`), and the slip snapshot (`bstrReceipts`) carries **only receipts that have a PK.**" This section re-confirms that conclusion against the code, and there is nothing contradictory.

---

## 5.1 Overview of the 4 automatic receipt kinds

| Automatic receipt | `tranKindType` | Creation conditions (all must hold) | How the ruled amount is derived | Module | Source |
|---|---|---|---|---|---|
| **Per diem** | `DAILY_COST` | ① The rule's payment type is not unpaid (NONE) ② There is a payable amount (`hasDailyCostPayableAmount` — scalar `limitAmount>0` **or** at least 1 positive entry in the per-date map `limitAmounts`) ③ At least 1 target date ④ The purpose can be mapped to a form area (`checkAutoReceiptMappable`) ⑤ The already-claimed-days lookup is complete ⑥ It is not a pre-settlement segment | If any one of an active formula (`appliedConditions`), `payType=DIFF`, or a payment-option reduction applies → **truncate the per-date ruled-amount map per date and sum.** If none apply, the scalar fallback of **the KRW-converted unit price × the number of valid days** | `features/daily-cost-receipt-builder.ts` (`buildDailyCostReceipt`), `utils/auto-receipt-utils.ts:102` (`createDailyCostReceipt`), `utils/daily-cost-amount-utils.ts:321` (`resolveDailyCostRuledAmount`) | `daily-cost-receipt-builder.ts:72,76-86,89-105,108-115,136`, `auto-receipt-utils.ts:123-134,142-170` |
| **Fuel** | `FUEL` | ① Not a pre-settlement segment ② No FUEL receipt already drafted ③ At least 1 route leg with `transportType='PRIVATE'` ④ The `FUEL_COST` item value in `issuedItems` is not `'false'`, null, or `''` ⑤ The `FUEL` purpose exists in the rules ⑥ Mappable to a form area ⑦ The rule's payment type is not NONE | Branches by payment type (`bstrPayClassType`) — for `'FUEL'` or an undetermined payment type: **OPINET-based fuel cost + tolls**; otherwise, if `limitAmount` exists: **the policy amount (`limitAmount`)**; otherwise: OPINET fuel cost + tolls. The fuel amount itself is **rounded up** to the nearest 10 KRW (`Math.ceil(raw/10)*10`) | `features/fuel-cost-receipt-creator.ts` (`createFuelCostReceipt`), `features/fuel-price-calculator.ts`, `features/fuel-cost-receipt-utils.ts:79` (preconditions) | `fuel-cost-receipt-creator.ts:80,88-98,109-111,139,143-144,146-157`, `fuel-cost-receipt-utils.ts:84-118` |
| **Tolls** | `TOLL` (the constant `TOLL_TRAN_KIND_TYPE`) | ① **The same preconditions as fuel** (tolls have no input item of their own, so they follow the fuel input item's auto-creation checkbox) — except that the duplicate-prevention slot checks for an already-drafted TOLL ② The rule's **toll payment type is [toll]** (`checkBstrTollPayClass`) ③ The purpose is **not** subject to "include tolls in fuel" ④ The toll amount > 0 ⑤ The `TOLL` purpose exists in the rules ⑥ Mappable to a form area | **The T-map estimated toll** = the sum of `fare` over **private-vehicle (PRIVATE) route legs** (`calcTollFare`=`sumTollFare`, `bstr-policy/constants/toll-evidence.ts:41`, `:62-68` — `TOLL_FARE_TRANSPORT_TYPES = new Set(['PRIVATE'])` plus `fare > 0`). Corporate and company vehicle tolls are excluded (confirmed by product planning, `:31-39`). No separate limit lookup | `features/toll-cost-receipt-creator.ts` (`createTollCostReceipt`) | `toll-cost-receipt-creator.ts:10-12,96,106-128,131-132,135-137,143-145,193-196` |
| **Activity expense (actual cost) path** | **`DAILY_COST`** (not a separate type) | INNOTEK **opt-in.** ① The gate is active (`useActivityActualDailyEvidenceGate`) ② Only when the per-diem section is not visible (exclusive ownership) ③ The remaining conditions are identical to per diem — not pre-settlement / rule lookup complete / already-claimed-days lookup complete / not NONE / a payable amount exists / a period exists / a `DAILY_COST` input item exists / mappable to a form area / target dates > 0 | **Completely identical to per diem** — it reuses the same builder (`createDailyCostReceipt`) | `features/use-activity-actual-daily-receipt.ts` | `use-activity-actual-daily-receipt.ts:2-9,111-121,177-253,257-275` |

### A correction about activity expense — it is not a 5th `tranKindType`

"Activity expense" is **not an independent kind of automatic receipt.** When the activity-expense settlement type is actual cost (ACTUAL), the per-diem section does not render and no per-diem automatic receipt is created; to fill that gap, this is a **path** that creates **1 `DAILY_COST` automatic receipt** outside the section using the same builder (`use-activity-actual-daily-receipt.ts:16-19`). Therefore:

- The field and amount contract is 100% identical to per diem (`use-activity-actual-daily-receipt.ts:257-275`).
- When assembling a requestBody externally, **you must not put a value like `'ACTIVITY'` in `tranKindType`.** It is `'DAILY_COST'`, the same as per diem.
- This path is INNOTEK opt-in, so **it does not operate on the default BZP tenant** (`use-activity-actual-daily-receipt.ts:2-3,113-121`).

### Other automatic receipts (for reference — outside this section's 4 kinds)

Besides the 4 kinds above, the following can also appear in `etcReceiptSaveRequests`. The evidence is that the amount aggregator counts a separate subtotal per type (`utils/expense-report-save-utils.ts:69-73`).

| Automatic receipt | `tranKindType` | Derivation | Source |
|---|---|---|---|
| Meals | `FOOD` | 1 receipt summing limit × the full period | `utils/auto-receipt-utils.ts:269-278` |
| Miscellaneous | `INCIDENTAL` | 1 receipt summing limit × the full period | `utils/auto-receipt-utils.ts:285-294` |
| Employee dormitory (lodging) | `ROOM` | 1 receipt summing limit × **the number of nights** (excluding the last day, the check-out day) | `utils/auto-receipt-utils.ts:302-304,319-355` |
| Advance-payment reversal (negative receipt) | Inherits the original receipt's | Reversal only. `approvalCanceled=true` + `displayHidden=true` | `utils/prepayment-receipt.ts:130-136`, `utils/prepare-save-data.ts:277-297` |

---

## 5.2 The "ruled amount is the amount incurred" structure

### An automatic receipt puts the same value in 4 amount fields

All three builders assign the single derived total (`totalAmount` / `defaultAmount` / `tollFare`) **directly to the approved amount, supply value, ruled amount, and claim amount.**

| Automatic receipt | Assignment code | Source |
|---|---|---|
| Per diem | `approvalAmount = settleAmount = reqAmt = ruledAmount = supplyAmount = totalAmount` | `daily-cost-receipt-builder.ts:157-161`, `auto-receipt-utils.ts:185-189` |
| Fuel | `approvalAmount = supplyAmount = ruledAmount = reqAmt = defaultAmount` | `fuel-cost-receipt-creator.ts:199-202` |
| Tolls | `approvalAmount = supplyAmount = ruledAmount = reqAmt = tollFare` | `toll-cost-receipt-creator.ts:193-196` |
| Employee dormitory | `approvalAmount = settleAmount = reqAmt = ruledAmount = supplyAmount = totalAmount` | `auto-receipt-utils.ts:344-350` |

Since **ruled amount = used amount = claim amount**, the excess (`MAX(0, usedAmount − ruledAmount)`) is structurally always 0. That is why no excess reason is required on automatic receipts.

VAT is not set by the automatic-receipt builders — the converter puts in 0 via `vatAmount ?? 0` and uses `supplyAmount` as-is (`expense-report-save-utils.ts:134`; fuel and tolls look up a non-deductible tax code with `nonDeduction: true` — `fuel-cost-receipt-creator.ts:178-184,206`, `toll-cost-receipt-creator.ts:172-178,199`).

### Contrast with manual receipts

| Axis | Manual receipt | Automatic receipt |
|---|---|---|
| Used amount (`approvalAmount`) | **The card approval amount** — a fact arriving from outside. The front end does not calculate it | **The rule-derived amount** — the front end calculates it |
| Ruled amount (`ruledAmount`) | The result of a separate rule lookup. Determined independently of the used amount | **The same value** as the used amount |
| Claim amount (`reqAmt`) | Auto-filled subject to the rule cap, in the `MIN(usedAmount, ruledAmount)` family | **The same value** as the used amount |
| Excess amount | `MAX(0, usedAmount − ruledAmount)` — can be non-zero | Structurally **always 0** |
| Excess reason | Required when over (validation ⑫) | Not applicable |
| User editing | Editable, e.g. the claim-amount cell | `documentBound: true` — splitting disabled, delete icon hidden (`auto-receipt-utils.ts:310-312`) |
| Transmission array | `receiptIds` + `issuedFields` | `etcReceiptSaveRequests` |
| PK | Present | **Absent** (created together with the document) |

> ⚠️ **A caution when assembling externally — ⑪ and ⑫ use the "claim-amount axis."**
> That is a different axis from `MAX(0, usedAmount − ruledAmount)` (the excess-amount column and ⑰). ⑪ and ⑫ determine **whether `reqAmt` exceeds `ruledAmount`** (`utils/validation/exceed-check-utils.ts:156`, `utils/validation/validate-total-exceed.ts:110`, `:195`; except that for the `ACTUAL` payment-basis family it is the `approvalAmount` axis — `exceed-check-utils.ts:144`, `:153`).
> When an automatic receipt fills all four fields with the same value, `reqAmt == ruledAmount` and ⑪ and ⑫ also do not fire.
> **Meals and miscellaneous expenses are the exception — see the next section.**

### ★ Exception — for meals (FOOD) and miscellaneous (INCIDENTAL), `ruledAmount` is the "daily unit price"

`createFixedCostReceipt` (`utils/auto-receipt-utils.ts:253-258`) sets the amounts as follows.

| Field | Value |
|---|---|
| `approvalAmount`, `settleAmount`, `reqAmt`, `supplyAmount` | **Limit × days** (the total) |
| `ruledAmount` | **`limitAmount`** — the daily unit price as-is |

**So for 2 or more days, `reqAmt > ruledAmount` and an excess arises on the claim-amount axis above.**
Example: a daily limit of 20,000 over 3 days → `reqAmt = 60,000`, `ruledAmount = 20,000` → subject to ⑪ and ⑫.

The "ruled amount = used amount = claim amount" statement in the previous section applies **only to the 4 kinds: per diem, fuel, tolls, and employee dormitory.** For employee dormitory, the comment at `auto-receipt-utils.ts:347-348` says "`ruledAmount` = the summed limit (the per-diem pattern)," meaning it was **deliberately changed to the total**, so the unit-price semantics remain in **only meals and miscellaneous.**

> If you assemble meal or miscellaneous automatic receipts externally, whether you put the total or the unit price in `ruledAmount` decides whether ⑪ and ⑫ fire. To match the current screen behavior you must put in the **unit price**, and then an excess reason may be required. We will confirm whether this asymmetry is intended and reply (open item M-6).

### The `slip` defaults for automatic receipts are filled by a separate stage, not the builder

For per-diem, meal, miscellaneous, and lodging automatic receipts, `slip.accountSubjectId`, `taxCodeId`, `taxName`, `summary`, `branchOfficeId`, and `internalOrderId` are **left empty by the builder** and filled by `enrichAutoReceiptsWithDefaults` (`utils/autofill-batch.ts:62`, called from `hooks/use-expense-report-rule-effects.ts:200`). Only fuel and tolls look them up directly inside their creator.

> ⚠️ **In some tenants this stage recalculates `vatAmount` and `supplyAmount`.**
> When `vatBasedDeductionEnabled = false` (INNOTEK, KSOE) it recalculates via `calculateVatAndSupply` (`utils/autofill-batch.ts:213-223`), so **supply value ≠ approved amount**, and the "same value in 4 amount fields" statement above and the field table's "`vatAmount` is effectively unset" break in those tenants.
> The amount descriptions in this document are **based on BZP.**

---

## 5.3 The `etcReceiptSaveRequests` field mapping table

### 5.3.1 ★ Warning — a single field not in the DTO makes the whole request a 400

The backend's `JacksonConfig` creates the `@Primary ObjectMapper` **directly with `new ObjectMapper()`.**

```java
// backend/app-internal-api/src/main/java/com/newbizplay/config/JacksonConfig.java:17-24
@Bean
@Primary
public ObjectMapper primaryObjectMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return objectMapper;
}
```

What this means:

- Spring Boot's default is to **disable** `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` on a mapper built by the auto-configured `Jackson2ObjectMapperBuilder`.
- But the code above bypasses that builder, creates `new ObjectMapper()` directly, and registers it as `@Primary`. **Jackson's own default is `FAIL_ON_UNKNOWN_PROPERTIES = enabled`**, and this code has no `disable(...)` call to turn it off (across all 39 lines of the file there is not a single line touching `DeserializationFeature`). The same file has a second `ObjectMapper` bean (`customObjectMapper`, `:26-36`), but it is not `@Primary` and is not used for request deserialization, so the conclusion is unchanged.
- Therefore **a single field not declared in the DTO makes the entire request a 400.** There is no partial success where some fields are ignored.

The front-end type file's header states the same fact — "sending fields the server does not have (`useDate`, `settleAmount`, `cardType`, etc.) causes a Jackson error, so we keep this 1:1 with the backend DTO" (`bstr-policy/save-request-types.ts:8-9`).
The converter's comment says the same — "including client-only fields (`useDate`, `settleAmount`, `cardType`, `remark`, etc.) causes a Jackson Unrecognized field error, so we explicitly map only the fields we need" (`expense-report-save-utils.ts:107-109`).

There is a record in the code comments of actually hitting this trap:

| Field that produces a 400 if sent | Source |
|---|---|
| `slip.wbsName`, `slip.wbsErpCode` (only `wbsId` exists) | `save-request-types.ts:96-101`, `expense-report-save-utils.ts:188-192` |
| `slip.internalOrderName`, `slip.internalOrderErpCode` (only `internalOrderId` exists) | `save-request-types.ts:93-95` |
| `bstrReceipts[].wbsId`/`wbsName`/`wbsErpCode` (BstrReceiptDto has no wbs field at all) | `prepare-save-data.ts:250-254` |
| Top-level `useDate`, `settleAmount`, `cardType`, `remark` on automatic receipts (**store-only fields**) | `save-request-types.ts:8-9`, `expense-report-save-utils.ts:107-109` |
| `slip.branchOfficeCode`, `slip.branchOfficeName` (the backend ignores them on request — response-only) | `save-request-types.ts:80-84` |

### 5.3.2 The complete field table (front-end `EtcReceiptSaveRequest`, 51 fields)

Source of truth: `packages/domains/src/bstr-policy/save-request-types.ts:11-115`.
The "Actually sent for automatic receipts" column indicates whether `toEtcReceiptSaveRequests` (`utils/expense-report-save-utils.ts:119-200`) actually emits it —
**○** = always sent, **cond.** = only on certain paths, **✕** = present in the DTO but not sent by the automatic-receipt converter.

| # | Field | Type | Required | Meaning | What goes in it for automatic receipts | Actually sent | Source |
|---|---|---|---|---|---|---|---|
| 1 | `receiptId` | `number \| null` | Optional | The existing Receipt PK — **present means update, absent means create new** | **Left empty.** The authoring path is always new. It is filled only when a settlement administrator updates an existing automatic receipt — leaving it empty makes the server create an extra receipt | ✕ (not in the converter) | `save-request-types.ts:12-17` |
| 2 | `tranKindId` | `number \| null` | **Required** | The purpose (transaction type) PK | The looked-up purpose id — per diem: `useTranKindInfo('DAILY_COST')`; fuel: `fetchTranKindListByType('FUEL',…)[0].id`; tolls: `fetchTranKindListByType('TOLL',…)[0].id` | ○ | `expense-report-save-utils.ts:144`, `fuel-cost-receipt-creator.ts:88-92`, `toll-cost-receipt-creator.ts:135-139` |
| 3 | `tranKindType` | `string \| null` | Effectively required | The purpose type enum | `'DAILY_COST'` / `'FUEL'` / `'TOLL'` (the activity-expense path is also `'DAILY_COST'`) | ○ | `expense-report-save-utils.ts:145` |
| 4 | `mestName` | `string \| null` | Optional | Merchant name | A fixed string — per diem `'일비용 증빙'`, fuel `'유류비 증빙'`, tolls `'통행료 증빙'`, employee dormitory `'숙박비'` | ○ | `expense-report-save-utils.ts:146`, `daily-cost-receipt-builder.ts:149`, `fuel-cost-receipt-creator.ts:205`, `toll-cost-receipt-creator.ts:36,198` |
| 5 | `approvalDate` | `string \| null` | Effectively required | Approval date | The receipt date (`evidenceDateStr`). The converter falls back with `approvalDate ?? useDate` | ○ | `expense-report-save-utils.ts:147` |
| 6 | `approvalTime` | `string \| null` | Optional | Approval time | Automatic receipts do not set it (manual receipts use `HH:mm:ss`) | ✕ | `save-request-types.ts:22` |
| 7 | `approvalAmount` | `number \| null` | **Required** | **The used amount (approved amount)** | **The rule-derived total** | ○ | `expense-report-save-utils.ts:148` |
| 8 | `supplyAmount` | `number \| null` | Effectively required | Supply value | **The rule-derived total** (an ETC card, so supply value = approved amount) | ○ | `expense-report-save-utils.ts:149`, `auto-receipt-utils.ts:97` |
| 9 | `originalSupplyAmount` | `number \| null` | Optional | Original supply value | Automatic receipts do not set it (the backend fills it with `supplyAmount ?? approvalAmount`) | ✕ | `save-request-types.ts:25`, `EtcReceiptSaveRequest.java:289` |
| 10 | `vatAmount` | `number \| null` | Optional | VAT amount | The builder does not set it, so it is effectively `undefined`. `?? 0` is applied only when deriving the slip | ○ (usually unset in value) | `expense-report-save-utils.ts:150`, `:135` |
| 11 | `originalVatAmount` | `number \| null` | Optional | Original VAT amount | Not set | ✕ | `save-request-types.ts:27` |
| 12 | `overseasApprovalAmount` | `number \| null` | Optional | Overseas approval amount (the original foreign amount) | Filled in the store but **not sent by the converter** (§5.6) | ✕ | `daily-cost-receipt-builder.ts:181` vs `expense-report-save-utils.ts:143-199` |
| 13 | `overseasUsed` | `boolean \| null` | Optional | Whether used overseas | Same — exists only in the store | ✕ | Same |
| 14 | `exchangeRate` | `number \| null` | Optional | Exchange rate | `exchangeRate ?? 0` — 0 for KRW | ○ | `expense-report-save-utils.ts:166` |
| 15 | `currencyCode` | `string \| null` | Effectively required | Currency code | `currencyCode ?? 'KRW'` | ○ | `expense-report-save-utils.ts:156` |
| 16 | **`reqAmt`** | `number \| null` | **Required** | **★ The field the claim amount goes into** | **The rule-derived total** (for automatic receipts, claim amount = ruled amount) | ○ | `expense-report-save-utils.ts:151` |
| 17 | **`ruledAmount`** | `number \| null` | **Required** | **★ The field the ruled amount goes into** | **The rule-derived total** | ○ | `expense-report-save-utils.ts:152` |
| 18 | `overseasRuledAmount` | `number \| null` | Optional | **The overseas (foreign-currency) ruled amount** | Filled in the store but **not sent by the converter** (§5.6) | ✕ | `daily-cost-receipt-builder.ts:180` vs `expense-report-save-utils.ts:143-199` |
| 19 | `bstrPayClassType` | `string \| null` | Optional | Trip-expense payment type | Per diem `'FIXED'`, employee dormitory `'TOTAL'`, fuel `policyType ?? 'FUEL'`, **tolls unset** (the fuel rule owns it) | ○ | `expense-report-save-utils.ts:153`, `daily-cost-receipt-builder.ts:162`, `fuel-cost-receipt-creator.ts:203`, `toll-cost-receipt-creator.ts:180`, `auto-receipt-utils.ts:351` |
| 20 | `bldat` | `string \| null` | Effectively required | **The receipt date** | `approvalDate ?? useDate` = the receipt date | ○ | `expense-report-save-utils.ts:163-164` |
| 21 | `usedStartDate` | `string \| null` | Effectively required | Usage start date | `useDate` (= the receipt date). For automatic receipts, start = end, a single day | ○ | `expense-report-save-utils.ts:154` |
| 22 | `usedEndDate` | `string \| null` | Effectively required | Usage end date | Same | ○ | `expense-report-save-utils.ts:155` |
| 23 | `depart` | `string \| null` | Optional | Departure point | Not set (for manual transportation receipts) | ✕ | `save-request-types.ts:39` |
| 24 | `arrival` | `string \| null` | Optional | Arrival point | Not set | ✕ | `save-request-types.ts:40` |
| 25 | `departNodeId` | `string \| null` | Optional | Departure node ID | Not set | ✕ | `save-request-types.ts:41` |
| 26 | `arrivalNodeId` | `string \| null` | Optional | Arrival node ID | Not set | ✕ | `save-request-types.ts:42` |
| 27 | `departTerminalId` | `number \| null` | Optional | Departure terminal ID | **Explicitly sent as `null`** | ○ (`null`) | `expense-report-save-utils.ts:167` |
| 28 | `arrivalTerminalId` | `number \| null` | Optional | Arrival terminal ID | **Explicitly sent as `null`** | ○ (`null`) | `expense-report-save-utils.ts:168` |
| 29 | `cancelReason` | `string \| null` | Optional | Cancellation reason | Not set | ✕ | `save-request-types.ts:45` |
| 30 | `seatClass` | `string \| null` | Optional | Seat class | Not set | ✕ | `save-request-types.ts:46` |
| 31 | `mestTaxType` | `string \| null` | Optional | Merchant tax type | Not set (backend default `NONE`) | ✕ | `save-request-types.ts:47`, `EtcReceiptSaveRequest.java:112` |
| 32 | `vehicleType` | `string \| null` | Optional | Transport mode type | Not set | ✕ | `save-request-types.ts:48` |
| 33 | `bstrRegionId` | `number \| null` | Optional | Trip region (grade zone) ID | Not set | ✕ | `save-request-types.ts:49` |
| 34 | `etcReceiptType` | `string \| null` | Effectively required | Miscellaneous receipt type | **Hard-coded `'RECEIPT'`** | ○ | `expense-report-save-utils.ts:165` |
| 35 | `approvalCanceled` | `boolean` | Effectively required | Whether the approval was cancelled | **Hard-coded `false`.** Only advance-payment reversal receipts are `true` | ○ | `expense-report-save-utils.ts:157`, `prepayment-receipt.ts:7` |
| 36 | `displayHidden` | `boolean \| null` | Optional | Hidden on screen (reversal/offset only — still counted in calculations) | `displayHidden ?? false`. `true` on a full advance payment (automatic per diem == the advance total) | ○ | `expense-report-save-utils.ts:159-161`, `save-request-types.ts:52-57` |
| 37 | `documentBound` | `boolean \| null` | Effectively required | Whether bound to the document | **`documentBound ?? true`** — the marker of an automatic receipt. Splitting disabled, delete hidden | ○ | `expense-report-save-utils.ts:158`, `auto-receipt-utils.ts:310-312` |
| 38 | `nonDeductionCustom` | `boolean \| null` | Effectively required | Whether non-deduction is user-specified | **Hard-coded `true`** | ○ | `expense-report-save-utils.ts:162` |
| 39 | `complianceTypesStr` | `string \| null` | Optional | Suspected-violation (compliance) type string | Not set | ✕ | `save-request-types.ts:60` |
| 40 | `routeType` | `string \| null` | Optional | Route type | Not set | ✕ | `save-request-types.ts:61` |
| 41 | `imageIds` | `number[]` | Optional | Attached image ID list | Not set (automatic receipts have no attachments) | ✕ | `save-request-types.ts:62` |
| 42 | `receiptUserCardUserIds` | `number[]` | Conditional | Card user ID list | **Only for delegated authoring**, `[traveler id, delegate drafter id]` is injected into every item | cond. | `prepare-save-data.ts:299-310` |
| 43 | `issuedItems` | `unknown[]` | Effectively required | Issued item list | `[r.issuedItem]` when `r.issuedItem` exists, otherwise `[]` | ○ | `expense-report-save-utils.ts:124-126,169` |
| 44 | `bstrRoutes` | `unknown[]` | Optional | Trip route list | Not set (even fuel does not put it at the top level) | ✕ | `save-request-types.ts:65` |
| 45 | `slip` | An object (below) | **Effectively required** | Slip information — cost center / account subject / tax code / internal order plus 3 amounts | §5.3.3 | ○ | `expense-report-save-utils.ts:171-197` |
| 46 | `receiptEtc` | `unknown` | Optional | Miscellaneous receipt detail | Not set | ✕ | `save-request-types.ts:109` |
| 47 | `issuedReceiptEtc` | `unknown` | Optional | Issued-receipt other info | Not set | ✕ | `save-request-types.ts:110` |
| 48 | `excludeType` | `string \| null` | Optional | Usage-record exclusion type | Not set | ✕ | `save-request-types.ts:111` |
| 49 | `etcIncomeRateId` | `number \| null` | Optional | EtcIncomeRate id (tax-rate classification) | Not set | ✕ | `save-request-types.ts:112` |
| 50 | `incomeRateName` | `string \| null` | Optional | Tax-rate classification name | Not set | ✕ | `save-request-types.ts:113` |
| 51 | `incomeRateValue` | `number \| null` | Optional | Tax rate | Not set | ✕ | `save-request-types.ts:114` |

> **What the "Required" column means** — the backend DTO declares no `@NotNull`-style constraints (`EtcReceiptSaveRequest.java` has no validation annotations at all). "Required" in the table above means **it is confirmed in code that leaving the field empty breaks something downstream.** Example: omitting `slip.slipAmt` causes the backend to save `slipSplAmt=null`, producing an NPE at the slip-creation stage (`save-request-types.ts:68`, backend reply 2026-06-10 — though the backend `toDto()` does have a `slipSplAmt==null` correction block: `EtcReceiptSaveRequest.java:250-261`).
> If `tranKindId` is null, the backend does not create `issuedReceipts` at all (`EtcReceiptSaveRequest.java:294` — `tranKindId != null ? issuedReceiptDtos : null`), so **sending without `tranKindId` means the receipt itself is never created.**

### 5.3.3 The `slip` sub-fields (**18** sent for automatic receipts)

> Manual receipts (`issuedFields[].issuedReceiptDtos[].slip`) add `docDate` (the posting date), for **19.**
> The two arrays' `slip` key counts differ — do not put `docDate` on an automatic receipt.

Source of truth: `save-request-types.ts:67-108`, transmission code `expense-report-save-utils.ts:171-197`, backend `backend/domain/src/main/java/com/newbizplay/comm/dto/SlipDto.java`.

| Field | Type | What goes in it for automatic receipts | Source |
|---|---|---|---|
| `slipAmt` | number | The slip used amount — derived by `computeSlipAmounts`. Unsplit, so the used amount (`approvalAmount`) | `expense-report-save-utils.ts:129-141,172`, `SlipDto.java:22` |
| `slipSplAmt` | number | The slip supply value — same deriver. When the claim-amount setting (`isRequestAmountUsed`) is ON, back-derived from `reqAmt` | `expense-report-save-utils.ts:129-141,173`, `SlipDto.java:23` |
| `slipVatAmt` | number | The slip VAT amount — same deriver. Effectively 0 since automatic receipts are non-deductible | `expense-report-save-utils.ts:174`, `SlipDto.java:24` |
| `budgetDepartmentId` | number\|null | Budget department (cost center) id — the default of the top `COST_CENTER` input item. Existing values are preserved on regeneration | `expense-report-save-utils.ts:175`, `daily-cost-receipt-builder.ts:165-170` |
| `budgetDepartmentName` | string\|null | Budget department name | `expense-report-save-utils.ts:176` |
| `budgetDepartmentErpCode` | string\|null | Budget department ERP code | `expense-report-save-utils.ts:177` |
| `accountSubjectId` | number\|null | Account subject id — `fetchDefaultAccountSubject(purpose, objective, null, classification)` | `expense-report-save-utils.ts:178`, `fuel-cost-receipt-creator.ts:160,208` |
| `accountSubjectName` | string\|null | Account subject name | `expense-report-save-utils.ts:179` |
| `accountSubjectErpCode` | string\|null | Account subject ERP code | `expense-report-save-utils.ts:180` |
| `branchOfficeId` | number\|null | VAT business-site id — **the only VAT business-site field the front end sends on a request.** `code` and `name` are response-only (ignored by the backend on request) | `expense-report-save-utils.ts:181-184`, `save-request-types.ts:80-84` |
| `taxCodeId` | number\|null | Tax code id — fuel and tolls look it up with `nonDeduction:true` | `expense-report-save-utils.ts:185`, `fuel-cost-receipt-creator.ts:178-184,215` |
| `taxName` | string\|null | **The tax code name. The field is called `taxName`, not `taxCodeName`** (the store's canonical `taxCodeName` becomes `taxName` on transmission) | `expense-report-save-utils.ts:186`, `save-request-types.ts:86-87`, `SlipDto.java:36` |
| `summary` | string\|null | Description — the value auto-generated by `generateDefaultSummaryAsync`. `null` when not entered | `expense-report-save-utils.ts:196`, `fuel-cost-receipt-creator.ts:166-175,212` |
| `internalOrderId` | number\|null | Internal order id. **`internalOrderName`/`ErpCode` are not in SlipDto — sending them gives a 400** | `expense-report-save-utils.ts:187`, `save-request-types.ts:93-95` |
| `wbsId` | number\|null | WBS id (identifies KSOE ratio-split children). **`wbsName`/`wbsErpCode` do not exist — sending them gives a 400** | `expense-report-save-utils.ts:188-192`, `save-request-types.ts:96-101` |
| `projectId` | number\|null | Project id | `expense-report-save-utils.ts:193`, `SlipDto.java:30` |
| `projectName` | string\|null | Project name (unlike WBS, all 3 project fields persist) | `expense-report-save-utils.ts:194`, `SlipDto.java:31` |
| `projectErpCode` | string\|null | Project ERP code | `expense-report-save-utils.ts:195`, `SlipDto.java:32` |

Fields present in SlipDto that automatic receipts do not send: `shkzg`, `docDate`, `supplierId`, `supplierName`, `branchOfficeCode`, `branchOfficeName` (`SlipDto.java:21,26,33,38,44,45`).

### 5.3.4 Front-end ↔ backend DTO comparison results

The files were read in full and compared (extracting only `private X y;` with a regex would miss declarations with initializers such as `private boolean approvalCanceled = false;` (`:44`) and declarations without an access modifier — this DTO had no modifier-less declarations).

| Item | Count |
|---|---|
| Fields declared in the front-end `EtcReceiptSaveRequest` | **51** |
| Fields declared in the backend `EtcReceiptSaveRequest.java` | **61** |
| Fields present only on the front end and absent on the backend (= 400 risk) | **0** ✅ |
| Fields present only on the backend (undeclared on the front end — cannot be sent) | **10** |

**The 10 backend-only fields** (absent from the front-end type, so they cannot be sent on the automatic-receipt path):

| Field | Type | Source |
|---|---|---|
| `originCurrencyCode` | `CurrencyCode` | `EtcReceiptSaveRequest.java:48` |
| `mest` | `Mest` (a merchant object) | `:52` |
| `mestCorpNo` | `String` (merchant business registration number) | `:110` |
| `starRating` | `Integer` (lodging star rating 1–5) | `:150` |
| `roomType` | `String` (room type) | `:153` |
| `partnerHotel` | `Boolean` (whether it is a partner hotel) | `:156` |
| `personCount` | `Integer` (meal headcount 1–100) | `:159` |
| `foodDivisionType` | `FoodDivisionType` (BREAKFAST/LUNCH/DINNER) | `:162` |
| `outPolicyReason` | `String` (exception reason) | `:165` |
| `modifyReason` | `String` (modification reason) | `:168` |

> These 10 are fields used on the **manual other/simplified receipt registration API path** (lodging star rating, room type, partner hotel, meal headcount, meal classification, etc.). They do not apply to automatic receipts, which are per diem, fuel, and tolls.
> There is an automatic meal (`FOOD`) receipt that would need `foodDivisionType`, but that builder does not set it either (`auto-receipt-utils.ts:269-278`) — ⚠️unconfirmed (open item M-2).

**Enum value strings** — everything used in this document was confirmed in the source:

| Field | Confirmed values | Source |
|---|---|---|
| `tranKindType` | `'DAILY_COST'`, `'FUEL'`, `'TOLL'`, `'FOOD'`, `'INCIDENTAL'`, `'ROOM'` | `daily-cost-receipt-builder.ts:148`, `fuel-cost-receipt-creator.ts:191`, `bstr-policy/constants/toll-evidence.ts:14` (`TOLL_TRAN_KIND_TYPE = 'TOLL'`), `expense-report-save-utils.ts:69-73` |
| `bstrPayClassType` | `'FIXED'` (per diem), `'FUEL'` (fuel), `'TOTAL'` (employee dormitory) | `daily-cost-receipt-builder.ts:162`, `fuel-cost-receipt-creator.ts:203`, `auto-receipt-utils.ts:351` |
| `etcReceiptType` | `'RECEIPT'` | `expense-report-save-utils.ts:165` |
| `currencyCode` | `'KRW'` (default) | `expense-report-save-utils.ts:156` |

> Re-confirming a correction from the existing shared material — there is **no** value `'DAILY'` in `bstrPayClassType`. Per diem is `'FIXED'` (consistent with the correction table at `../README.md:67`).

---

## 5.4 Per diem's separate path — per-day ruled amounts and decimal handling

### 5.4.1 There are two paths, and the fork is "is there a reason to treat the per-date map as the source of truth?"

`resolveDailyCostRuledAmount` (`utils/daily-cost-amount-utils.ts:321-418`) decides.
If **any one** of the three below is true, it takes the per-date summing path; if all three are false, it takes the scalar fallback (`:337-351`).

| Condition | Test | Source |
|---|---|---|
| An active formula exists | At least 1 `appliedConditions` entry with `matched && !overridden` | `:337-339` |
| Tiered payment | `rule.payType === 'DIFF'` — the scalar `limitAmount` is 0 and the first-day/last-day amounts exist only in the map | `:344` |
| Payment-option reduction | At least 1 selected day where `limitAmounts[date] == null` (`EXCEPT_START`/`FIX_TWO_DAYS`, etc. removed dates on the backend) | `:350` |

Also, if `limitAmounts` is empty or `rule.calcEnabled === false`, it immediately takes the scalar fallback (`:332-334`).

### 5.4.2 The 5-step execution order of the per-date summing path

```
① Convert the tiered amounts' (diffCurrency) foreign currency   resolveDiffCurrencyRates → applyDiffCurrencyToRule
      ↓                                  daily-cost-amount-utils.ts:357-369
② For foreign currency, convert per date to KRW (build the base map)   exchangeToKRW(amount, currencyCode, rate) per date
      ↓                                  :374-396   (rate failure → returns null = defers to the scalar fallback :386-392)
      ↓                                  For KRW, limitAmounts is used as the base directly :397-400
③ Re-apply the formula engine (on the KRW base)   applyCalcConditionsKRW(convertedRule, krwBaseMap, opts)
      ↓                                  :404-405   ★ the engine does not round — fractions remain
④ ★ Truncate to whole KRW per date         Math.trunc(amount)   ← decimal handling lives here, in one place
      ↓                                  :411-413
⑤ Sum only the valid dates (eligibleDates)   accumulate after filtering on eligibleSet.has(date)
                                          :408-414
```

**The order must not be reversed** — ② (conversion) comes before ③ (formula). If a foreign-currency rule has a formula, the engine was deferred at lookup time and `limitAmounts` still holds the foreign originals, so the engine must be re-applied on the KRW base after conversion or currencies get mixed (`:302-314`).

### 5.4.3 ★ Where decimal handling happens — precisely

**It is "per date," not on the total. The method is `Math.trunc` (truncation toward zero), and the location is `packages/domains/src/cloud-expense-report/utils/daily-cost-amount-utils.ts:413`.**

```ts
// daily-cost-amount-utils.ts:410-414
for (const [date, amount] of Object.entries(finalMap)) {
  // Rule: the engine does not round → truncate to whole KRW per date (Math.trunc) then sum
  //   (consistent with the legacy "per-date floor after applying conditions").
  //   Without truncation, 0.5 KRW from things like an integer KRW base × 50% leaks into
  //   receipts and labels (e.g. 121,972.5 KRW).
  if (eligibleSet.has(date)) totalAmount += Math.trunc(amount);
}
```

- **Why per date** — legacy consistency. The legacy code applied `Math.floor` at the end to the **per-date** amount after conditions were applied (comment at `daily-cost-amount-utils.ts:248-250`, citing legacy `SeahExpenseReportUtils` line 1631). Applying it once to the total gives a different value.
- **Why `trunc` and not `floor`** — `Math.floor` increases the absolute value by 1 KRW for negatives, throwing off the sum of an original-plus-cancellation pair (`bstr-policy/utils/currency-utils.ts:53-55`).
- **The symptom without truncation** — 0.5 KRW from things like an integer KRW base × 50% leaks into receipts and labels (e.g. 121,972.5 KRW) (comment at `:412`).

The same rule is applied in **5 places** across the domain (including the plan path `business-plan/utils/bstr-plan-limit-enrich.ts:120` — whose comment at `:116-119` states that "this is the same truncation as the expense-report source of truth, which keeps plan and expense-report amounts consistent"), and all of them are unified as "per-date `Math.trunc`." Changing only one throws off the amounts, the display, and the day count.

| Site | Purpose | Source |
|---|---|---|
| `daily-cost-amount-utils.ts:413` | **The automatic receipt's amount (source of truth)** — the total on the per-date summing path | `resolveDailyCostRuledAmount` |
| `daily-cost-amount-utils.ts:251` | The label display amount (fallback when no receipt is created) | `calcRuleBasedDailyCostLabelAmount` |
| `daily-cost-amount-utils.ts:288` | **The paid-day count** — days truncated to 0 are also removed from the day count | `calcRuleBasedDailyCostPaidDayCount` |
| `daily-cost-breakdown.ts:141,146` | The per-date display in the calculation-detail tooltip (KRW rules only) | `buildDailyCostBreakdown` |

### 5.4.4 Decimals on the scalar fallback path

The scalar path **does not use `Math.trunc`.** Instead it is handled inside the conversion step.

```
convertDailyCostUnitToKRW(limitAmount, currencyCode, dates)      daily-cost-amount-utils.ts:168-209
  KRW → the raw amount as-is (conversion skipped)                 :177-180
  Foreign → exchangeToKRW(limitAmount, currencyCode, rate)        :207
           = Math.trunc(amount × rate)  or, for JPY/IDR/VND, Math.trunc(amount × rate / 100)
                                                                  bstr-policy/utils/currency-utils.ts:64,66
  Rate failure → fall back to keeping the raw amount (to avoid NaN)  :196-204
Total = krwUnit × the number of valid days                        daily-cost-receipt-builder.ts:136
```

That is, the scalar path **truncates once on the unit price** and then multiplies by the day count. When reproducing this externally, applying an extra `floor` outside `exchangeToKRW` results in double truncation (comment at `daily-cost-amount-utils.ts:156-158` — "do not apply an additional floor externally").

### 5.4.5 The exchange-rate lookup must be identical to the one used in ruled-amount validation

For per diem, **ruled amount = claim amount**, so if the automatic receipt's amount and the ruled-amount validation use different exchange rates you get "claim amount > ruled amount" and a total-overrun false positive. That is why both paths are hard-wired to the same lookup (`fetchCachedExchangeRateWithSetting`, `businessType='BSTR'`, `useExceptionTiming: true`).

- The automatic receipt's amount: `daily-cost-amount-utils.ts:182-192,357-368,376-385`
- Ruled-amount validation: `utils/validation/daily-ruled-amount-utils.ts:29-36` — the header states the reason (`:8-10`).

`daily-ruled-amount-utils.ts` itself is a **thin wrapper** (62 lines). The calculation source of truth moved to `bstr-policy` (`fetchDailyRuledAmount` → `bstr-policy/utils/ruled-amount-calculator.ts`), and this file only injects the 2 expense-report-specific dependencies (the rate lookup `fetchCachedExchangeRateWithSetting` and the allowed-day lookup `fetchAllowBeyondDays`) (`:16-36,44-51,57-62`).

---

## 5.5 Automatic receipts are not carried in `bstrReceipts`

The composition of the slip-snapshot array `bstrReceipts` is **gated by a PK filter.**

```ts
// utils/prepare-save-data.ts:176-177
const bstrReceipts = allReceipts
  .filter((r) => r.id != null && (isPrepaymentNegativeReceipt(r) || (r.id > 0 && issuedIdSet.has(r.id))))
```

- The input is only `allReceipts` = `store.receipts` (the 4 manual-receipt groups flattened) — `autoReceipts` **does not enter this array** (`prepare-save-data.ts:128-133`).
- The condition `r.id != null && r.id > 0 && issuedIdSet.has(r.id)` — **only receipts that have a PK and whose PK actually exists in the `issuedFields` lookup result** pass (`issuedIdSet` is built at `:141-151`).
- Automatic receipts are created together with the document and have no PK → for two reasons (a different array, and failing the PK filter) they cannot appear in `bstrReceipts`.
- The sole exception is the **advance-payment reversal negative receipt** — `isPrepaymentNegativeReceipt(r)` bypasses the `issuedIdSet` gate so that negative-id receipts are carried too (for creating EACC accounting slip offset lines, `:171-175`). Note, though, that this refers to something already present in `store.receipts` on the restore path (M-2/M-3); a newly created reversal attaches to `etcReceiptSaveRequests`, does not create a negative id, and therefore does not appear in `bstrReceipts` (`:277-280`).

**Consistency with the existing shared material `../README.md`** ✅

| Source | Statement | Consistent |
|---|---|---|
| `../README.md:30` (key conclusion 3) | "Only rule-based automatic receipts are created together with the document (`etcReceiptSaveRequests`), and the slip snapshot (`bstrReceipts`) carries **only receipts that have a PK**" | Consistent with the code |
| `../README.md:66` (verification-history correction table) | "`bstrReceipts` — not 'always sent together' — only receipts with a PK (automatic receipts excluded)" | Consistent with the code |

Nothing to correct. This section is a re-confirmation.

---

## 5.6 What happens to automatic receipts in the pre-submission processing

Only the steps applied to automatic receipts inside `prepareExpenseReportSaveData` (`utils/prepare-save-data.ts:90-345`) are extracted here, in order.

| Step | What it does | Code | Detail |
|---|---|---|---|
| **A. Ratio-split expansion** | `autoReceipts` → **`effectiveAutoReceipts`**. Internal order (IO) then WBS applied **sequentially**. If the flag is off or the gate (multiple rows, 100% ratio) is not met, **the input array is returned by the same reference** (which is how "no split" is determined) | `prepare-save-data.ts:162-169` → `utils/resolve-effective-auto-receipts.ts:29-47` | Flags: `internalOrderRatioSplitEnabled` (lginnotek), `wbsRatioSplitEnabled` (KSOE). **The total is preserved → the overall amount is unchanged.** Validation ⑭ (SAP budget control) reuses the same function, structurally preventing the "N rows on save, 1 row in validation" asymmetry |
| **B. Amount aggregation** | `calcAllAmounts(allReceipts, effectiveAutoReceipts)` — computes **per-`tranKindType` subtotals for automatic receipts in a single pass.** `amt = r.reqAmt ?? 0` (**the claim-amount axis**) | `prepare-save-data.ts:262` → `expense-report-save-utils.ts:45-100` | Subtotal fields: `dailyCostAmount` (DAILY_COST), `fuelCostAmount` (FUEL), `foodCostAmount` (FOOD), `incidentalCostAmount` (INCIDENTAL), `lodgingCostAmount` (manual ROOM/HD_ROOM/ACCOM **plus** automatic ROOM). `publicFixCostAmount` is always 0. The automatic-receipt total is added into `totalBstrAmount` and `totalSettleAmount`, and `totalPersonalAmount = totalSettleAmount` |
| **C. DTO conversion** | `toEtcReceiptSaveRequests(effectiveAutoReceipts, isRequestAmountUsed)` — an allowlist approach mapping **only the needed fields** explicitly | `prepare-save-data.ts:275` → `expense-report-save-utils.ts:119-200` | The output is the fields marked ○ in §5.3.2. Store-only fields (`useDate`, `settleAmount`, `cardType`, `tmpId`, `tranKindName`, `dailySelectedDates`, etc.) are **deliberately dropped** — sending them gives a 400 |
| **C-1. The 3 slip amounts** | Inside the conversion, `computeSlipAmounts` derives `slipAmt`/`slipSplAmt`/`slipVatAmt`. Automatic receipts are unsplit, so `divisionType`/`divisionOrder`/`dividedParentVatAmount`/`nonDeduction` are **fixed to `null`** and fed into the `resolveVatSplit` path | `expense-report-save-utils.ts:128-141` | **The same deriver and the same settings** as manual receipts (`issuedFields.slip`). Omitting them gives backend `slipSplAmt=null` → an NPE at slip creation |
| **C-2. `bldat` fallback** | `bldat = approvalDate ?? useDate` | `expense-report-save-utils.ts:163-164` | For automatic receipts `approvalDate` is the receipt date, so it usually passes through unchanged |
| **D. Advance-payment reversal append** | Only when `prepaymentReceipts` exist and there is not yet a negative receipt in `allReceipts` (= new plan linkage, M-4), the result of `buildPrepaymentReversalEtcReceipts` is **appended after** `etcReceiptSaveRequests`. At the same time the reversal total is deducted from `totalSettleAmount` | `prepare-save-data.ts:281-297` | On restore (M-2/M-3) the negative receipt is already in `allReceipts`, so it is not duplicated. Part of the KSOE opt-in family |
| **E. Delegated-authoring injection** | For delegated authoring, `receiptUserCardUserIds = [traveler id, delegate drafter id]` is spread into **every item** of `etcReceiptSaveRequests` | `prepare-save-data.ts:299-310` | Test: `delegateCorpUserId != null && draftUserId != null && draftUserId !== delegateCorpUserId` |
| **F. Final assembly** | `etcReceiptSaveRequests` becomes a top-level key and `...amounts` is spread to complete `saveRequest` | `prepare-save-data.ts:318-337` | It is a **top-level array separate from** `bstrReceipts` |

### The upstream entry point

`buildExpenseReportSavePayload` (`expense-report-save-utils.ts:219-273`) wraps `prepareExpenseReportSaveData`. The only things it does specifically for automatic receipts:

1. Looks up the **claim-amount setting** (`requestedAmountUsed`) with `fetchRequestedAmountSetting()` (falling back to `false` on failure) — this value changes the back-derivation axis for the slip amounts in C-1 (`:253-254,268-270`).
2. Calls `buildIssuedFields(receipts, isRequestAmountUsed)` **for manual receipts only** (`:255`). Automatic receipts do not take this path.

### ⚠️ Three foreign-currency fields are dropped from the save payload

The builder sets `overseasRuledAmount`, `overseasApprovalAmount`, and `overseasUsed` on a foreign-currency automatic receipt (per diem) (`daily-cost-receipt-builder.ts:176-189`, `auto-receipt-utils.ts:197-200`).
All three fields are also declared in the DTO (`save-request-types.ts:28,29,34`; backend `EtcReceiptSaveRequest.java:60,62,127`).

Yet these three keys are **absent** from the object returned by `toEtcReceiptSaveRequests` (verified exhaustively at `expense-report-save-utils.ts:143-199`). They exist in the store but are not carried in the save request. `exchangeRate` and `currencyCode` are sent (`:156,166`).

Whether this is intended (an extension of the D3 rule that a formula-applied foreign currency is unified into KRW notation, leaving `overseasRuledAmount` as `null` — `daily-cost-receipt-builder.ts:125,183-189`) or an omission could not be settled from the code comments → **open item M-1.**

---

## Open items

| Key | Item | What needs confirming |
|---|---|---|
| **M-1** | ~~Intended or a gap~~ → **Settled as a gap.** The backend does consume all three fields — `overseasRuledAmount` is persisted to an `IssuedReceiptApproval` column via `ApprovalService.java:13993-14009` → `Approval.java:1436-1452` (and the admin attachment path at `:13668-13674`), while `overseasApprovalAmount` and `overseasUsed` are carried into ReceiptDto by `EtcReceiptSaveRequest.toDto():286-287`. So **the backend is set up to store them and the front-end converter simply does not send them.** For formula-applied foreign currency the `null` is intended per D3 (KRW unification), but **the original foreign amount and foreign ruled amount of a scalar foreign-currency per diem with no formula are not saved** | Ours to fix — when assembling externally, the backend will accept these three fields if you fill them in |
| **M-2** | The automatic meal (`FOOD`) receipt does not set `foodDivisionType` (the meal classification) (`auto-receipt-utils.ts:269-278`). Validation ⑫ determines per meal (domain CLAUDE.md §5 ⑫) but the automatic meal receipt has no meal classification | **Partly settled** — the automatic meal receipt is activated not by a tenant flag but by **the presence of the `BSTR_FOOD_COST` form input item + a limit > 0 + a user checkbox** (`FoodCostSection.tsx:48`, `:69`, `:101-118`), so **it also occurs on BZP.** `foodDivisionType` is **not declared at all** in the front-end `EtcReceiptSaveRequest`, so it cannot be sent through this array by any route. Only the effect of an unspecified meal on the ⑫ determination is unconfirmed |
| **M-3** | The backend DTO has **no** Bean Validation annotations such as `@NotNull` anywhere (`EtcReceiptSaveRequest.java` in full). So "required" cannot be pinned down as a backend contract, and the "Required" column in this document marks **only what is confirmed in code to break something downstream** | The official contract for required fields (whether a separate validation layer exists) |
| **M-4** | ~~Whether the front-end comment is stale~~ → **It is not stale.** The correction block has three limitations — ① it exists **only on the create path `toDto()`** (the update path `toDto(ReceiptDto)` `:301-357` does not have it, and that path does not map `slip` into `IssuedReceiptDto` at all, `:328-338`) ② its trigger condition is `slip != null && getSlipSplAmt() == null`, so **the case where `slipSplAmt` was sent but only `slipAmt` is missing is not corrected** ③ if `slip` itself is not sent, it is not a target. An unguarded dereference also remains at a consumer (`SlipBulkService.java:1255`) | **Conclusion: always calculate and send all 3 slip amounts (`slipAmt`, `slipSplAmt`, `slipVatAmt`) together.** (Only the causal claim in the front-end comment — "slipAmt missing → slipSplAmt=null" — is inaccurate) |
| **M-5** | ~~How the backend handles null~~ → **Settled: handled normally.** On save this value is stored as-is in `IssuedReceiptApproval.bstrPayClassType` ("the rule's payment type at authoring time," `IssuedReceiptApproval.java:52-55` — **a nullable column**) (`ApprovalService.java:14005`). `toDto()` does not use this field. So **a toll receipt with null is saved without exception**, and all that is lost is one slot of payment-type history | — |
| **M-6** | **Whether it is intended that `ruledAmount` on meal and miscellaneous automatic receipts is the "daily unit price"** — the other 4 kinds use the total, and employee dormitory was deliberately changed to the total (`auto-receipt-utils.ts:347-348`). Leaving it as a unit price makes an excess arise on the claim-amount axis for 2 or more days, firing ⑪ and ⑫ (`auto-receipt-utils.ts:253-258`) | We will confirm and reply |

---

## Source file list

```
packages/domains/src/bstr-policy/
├── save-request-types.ts                     EtcReceiptSaveRequest DTO (field source of truth, 51 fields)
├── constants/toll-evidence.ts                TOLL_TRAN_KIND_TYPE, sumTollFare
└── utils/currency-utils.ts:53-66             exchangeToKRW (Math.trunc + the 100-unit branch)

packages/domains/src/cloud-expense-report/
├── features/daily-cost-receipt-builder.ts    Per-diem builder (buildDailyCostReceipt)
├── features/fuel-cost-receipt-creator.ts     Fuel (createFuelCostReceipt)
├── features/fuel-cost-receipt-utils.ts:79    Shared preconditions for fuel and tolls
├── features/toll-cost-receipt-creator.ts     Tolls (createTollCostReceipt)
├── features/use-activity-actual-daily-receipt.ts   The activity-expense (actual cost) path — INNOTEK opt-in
├── features/daily-cost-hooks.ts:207          useDailyCostAutoReceipts (creation, sync, auto-fill)
├── features/use-daily-cost-breakdown.ts      The calculation-detail tooltip (INNOTEK opt-in)
├── utils/auto-receipt-utils.ts               createDailyCostReceipt, the FOOD/INCIDENTAL/ROOM builders
├── utils/daily-cost-amount-utils.ts:321-418  resolveDailyCostRuledAmount ★ decimal handling at :413
├── utils/daily-cost-breakdown.ts:141-146     Per-date trunc in the tooltip
├── utils/expense-report-save-utils.ts         calcAllAmounts, toEtcReceiptSaveRequests
├── utils/prepare-save-data.ts                Pre-submission processing, the bstrReceipts PK filter at :176-177
├── utils/resolve-effective-auto-receipts.ts   The IO→WBS ratio-split SSOT
├── utils/prepayment-receipt.ts:130           Building the advance-payment reversal EtcReceiptSaveRequest
└── utils/validation/daily-ruled-amount-utils.ts   The ruled-amount lookup wrapper (delegates to bstr-policy)

backend/
├── app-internal-api/.../receipt/service/dto/EtcReceiptSaveRequest.java   Backend DTO (61 fields)
├── app-internal-api/.../config/JacksonConfig.java:17-24                  ★ FAIL_ON_UNKNOWN_PROPERTIES enabled
└── domain/.../comm/dto/SlipDto.java                                      The slip sub-fields
```
