# §6 Validation — what is checked, and how, once the ruled amount is filled in

> This is the whole of **③ validation** among the 3 ruled-amount layers. It covers the stage **after** ① raw data lookup and ② per-day condition application have finished and `ruledAmount` / `overseasRuledAmount` are on the receipt.
>
> Every assertion in this document carries a `file:line` source. Items for which no source could be attached are marked `⚠️unconfirmed` and collected in the "Open items" section at the end.
>
> Path base = the repository root `new-frontend/`.
> `PD` = `packages/domains/src/`

---

## 6.0 The questions this section answers

| Question | Section with the answer |
|---|---|
| How is the excess amount (self-pay) derived? | §6.1 |
| What is compared against what on a split receipt? | §6.2 |
| What is the maximum you can put in the claim amount (`reqAmt`)? | §6.3 |
| What does the claim amount become when auto-filled? | §6.4 |
| What is checked at submission, and what blocks it? | §6.5 |
| A checklist an external system can run itself | §6.6 |

**Premise**: validation running **at front-end submission time** is the source of truth. The pipeline entry point is `validateDraftConditions` (`PD/cloud-expense-report/utils/validation/validate-draft-pipeline.ts:79`), and there is no test job in the GitLab CI. That means **an external system assembling the requestBody directly bypasses this validation** — hence the need to run the §6.6 checklist yourself.

---

## 6.1 Deriving the excess amount (self-pay)

### 6.1.1 The formula

```
excess = MAX(0, usedAmount − ruledAmount)
```

- Source of truth: `calculateOverAmount` (`PD/bstr-policy/utils/over-amount.ts:115-117`)
- Within the rule (a negative result) is **truncated to 0** (`over-amount.ts:113`, `:116`).
- The backend does not return an excess-amount field, so **this derived value is the only source for the on-screen display** (`over-amount.ts:5`).

### 6.1.2 ★ The reference axis — the **used amount**, not the claim amount

**Requirement settled 2026-08-25** (`over-amount.ts:7`).

| | Value | Source |
|---|---|---|
| The usage side | **The used amount (actually incurred)** = `getReceiptUsedAmount` | `over-amount.ts:9-11` |
| The rule side | `ruledAmount` (KRW) | `over-amount.ts:24` |
| Not used | **The claim amount `reqAmt`** | `over-amount.ts:13-18` |

**Why not the claim amount** (based on the source comment at `over-amount.ts:13-18`):

> There are paths where the claim amount is entered only up to the ruled amount because of administrator settings or the rule cap, making `claim − ruled` equal 0. Then a receipt that **actually spent** over the rule shows an excess of 0, and the feature that separates the excess into self-pay never opens at all.
> The excess comes from "how much was spent," not "how much was claimed."

The design mock (2026-08-25) pins this axis down (`over-amount.ts:17-18`):

| Used | Ruled | Claim | Excess |
|---:|---:|---:|---:|
| 180,000 | 100,000 | 100,000 | **80,000** |

On a claim-amount basis this would be `100,000 − 100,000 = 0` and that screen would not hold.

> ⚠️ **This connects directly to the §0 diagnosis.** The Chungbuk National University revision had `ruledAmount = approvalAmount = 15,000`. Computed on the used-amount axis, `MAX(0, 15,000 − 15,000) = 0`, so the excess is **structurally always 0.**
>
> ⚠️ **But do not lump this together as "⑫ and ⑰ both die" — the two validations use different axes.**
>
> | Validation | Axis | When `ruled = approval` |
> | :--- | :--- | :--- |
> | **⑰** (`validate-excess-split-required.ts`) | This section's used-amount axis (calls `getOverAmountDisplayValue` directly) | Does not fire |
> | **⑫** single-item branch (`ACTUAL`, `ONE_DAY` non-FOOD) | **The claim-amount axis** (§6.5.7) | Does not fire (claim ≤ approved = ruled) |
> | **⑫** aggregate branch (`ONE_DAY` FOOD, `ALL`) | **The claim-amount aggregate** | **Can fire.** `ALL` re-queries the limit via `fetchDailyRuledAmount`, so it is independent of the receipt's `ruledAmount` |
> | **⑪** (`validate-total-exceed.ts`) | **The claim-amount aggregate** | **Can fire** |
>
> So copying the approved amount into the ruled amount neutralizes **only the per-item determination**; the aggregate determinations remain. That is why the symptom looks like "sometimes it works and sometimes it doesn't" rather than "validation is entirely missing."

### 6.1.3 The derivation rules for the used amount (`getReceiptUsedAmount`) — source of truth

`PD/bstr-policy/utils/receipt-used-amount.ts:78-89`.

| Priority | Condition | Used amount | Source |
|---|---|---|---|
| 1 | `tranKindType === 'DAILY_COST'` (per diem) | `supplyAmount + vatAmount` | `:79-82` |
| 2 | `divisionType === 'BASIC'` **AND** `supplyAmount != null` | `supplyAmount + own share of VAT` | `:83-87` |
| 3 | Otherwise | `approvalAmount ?? 0` | `:88` |

- **Why #1**: per diem is a fixed-amount receipt with no VAT, so `approvalAmount` comes back as 0 on save/restore (`receipt-used-amount.ts:10-11`).
- **Why #2**: `merge-divided-receipt` **preserves the full original in each child's `approvalAmount`**, so using it directly gives the original total on every child (`receipt-used-amount.ts:12-14`, `:53-54`).
  Rows with no supply value (automatic FOOD/ROOM receipts where `supplyAmount = null`) fall back to `approvalAmount`.
- **Own share of VAT**, `getReceiptOwnVatAmount` (`receipt-used-amount.ts:44-47`): the first split child (`divisionOrder = 0`) has a direct `vatAmount` of 0, so it uses `dividedParentVatAmount`. All others use their direct `vatAmount`.

The used amount is **the same value** as the numeric source of truth for the on-screen used-amount column (`CLOUD_APPROVAL_AMOUNT`) (`receipt-used-amount.ts:2`, `over-amount.ts:10-11`) — users can reconcile the difference between the two columns.

### 6.1.4 The currency axis — always KRW-converted vs KRW-converted

**Requirement settled 2026-08-23** (`over-amount.ts:20`).

| | The value used in the determination |
|---|---|
| The usage side | `getReceiptUsedAmount` (KRW) — `over-amount.ts:23` |
| The rule side | `ruledAmount` (KRW). Even a foreign-currency rule is filled in by `convertRuledAmountToKRW` at the exchange rate — `over-amount.ts:24` |
| **Not used in the determination** | `foreignAmount`, `overseasRuledAmount` (the original foreign amounts) — `over-amount.ts:25` |

**Why KRW** (`over-amount.ts:27-28`):

> The excess is **the amount separated out as self-pay and posted to a debit account subject**, so it must be a fixed KRW figure. If the excess-split screen used the foreign-currency notation directly, the amount to split would not be determined.

> ⚠️ **Not a bug (intended)** — the ruled-amount column (`CLOUD_RULED_AMOUNT`) **keeps the foreign-currency notation.** So on a single foreign-currency receipt row the ruled amount shows as `USD 80` while the excess shows as `28,000`, mixing currencies and making the difference between the two columns impossible to reconcile. This was an **explicitly accepted cost** when the requirement was settled (a fixed KRW figure takes priority). Changing the ruled-amount column to KRW as well was rejected because it would remove any on-screen means of comparing against the original rule (USD 80) on a foreign-currency receipt (`over-amount.ts:30-33`).

> ⚠️ **A known limitation** — on a rate-lookup failure there is an upstream fallback in `convertRuledAmountToKRW` that places the raw foreign amount into the KRW slot (`rule-update-helpers.ts`). In that case the excess amount is **understated.** Since the ruled-amount column uses the same value, this is not a problem specific to `over-amount` (`over-amount.ts:35-37`).

### 6.1.5 ★ A `ruledAmount` of `null` differs from `0`

**This distinction is the single most commonly mistaken point in all of §6.**

| `ruledAmount` | Excess determination | Return value | Result | Source |
|---|---|---|---|---|
| `null` | **Not performed** | `''` (an empty string) | Treated as no excess, ⑰ does not block | `over-amount.ts:156`, `:66` |
| `0` | **Performed** | `usedAmount.toLocaleString()` | **The entire used amount is the excess** | `over-amount.ts:157` |
| `> 0` | Performed | `MAX(0, used − ruled)` | Normal determination | `over-amount.ts:157` |

- `null` → determination not possible. It returns **an empty string, not 0** — "declaring a state with no ruled amount to be 'an excess of 0' would make a row with no rule configured look like it is within range" (`over-amount.ts:122-123`).
- The "determination not possible" condition exists **only on the ruled-amount axis.** The used amount is an actual incurred figure with no "not entered" state and `getReceiptUsedAmount` always returns a number, so the old `reqAmt == null` not-possible condition from the claim-amount basis is gone (`over-amount.ts:125-127`).
- Consumption rule for the empty string: `hasExcessAmount('')` → `false` (`PD/cloud-expense-report/utils/has-excess-amount.ts:30`).
  `'0'` is also `false` (no non-zero digits — `has-excess-amount.ts:11`, `:31`).
- "An empty string means 'determination not possible,' not 'no excess,' but **both are treated as false.** Treating 'not possible' as an excess would highlight rows with no ruled amount as if they were over and even open the split" (`has-excess-amount.ts:26-27`).

> **Implication for an external system**: sending `ruledAmount` as `null` turns off the excess determination entirely, while sending `0` makes the whole used amount an excess and blocks submission at ⑰ (when the setting is ON).
> The §0 sample's `TRANSPORT ruledAmount: 0` (with 47,000 used) is the latter.

### 6.1.6 No negative sign is applied to cancellation receipts

- `getReceiptUsedAmount` returns **absolute amounts only.** Handling the `approvalCanceled` sign is the caller's responsibility (`receipt-used-amount.ts:17`).
- So the excess derivation does not apply a negative sign to cancellation receipts — the same treatment as the ruled-amount column (`over-amount.ts:67-68`).
- **The summing functions are different, however**: `sumReqAmt`, `sumApprovalAmount`, `sumForeignReqAmt`, and `sumOverseasRuled` all **deduct** for `approvalCanceled` (`PD/bstr-policy/utils/foreign-currency-utils.ts:61-102`). ⑪ total overrun also deducts (`validate-total-exceed.ts:109-112`, `:131-134`).

---

## 6.2 Determination axes by split type

### 6.2.1 The axis table (requirement settled 2026-08-26)

Reproducing the table at `over-amount.ts:41-46` verbatim.

| Split type | Usage side | Rule side | Determined? |
|---|---|---|---|
| **None** (`divisionType = null`) | The used amount | `ruledAmount` | Yes |
| **USER** (cost center, companions) | **Each row's own share** | `ruledAmount` (**the full original**) | Yes |
| **BASIC** (IO/WBS ratio) | Each row's own share | `dividedRuledAmount` (**distributed**) | Yes |
| **EXCESS** (excess-amount split) | **The original used amount** | `ruledAmount` (the original) | **Only the ruled-amount row** |

The `divisionType` value set: `USER`, `BASIC`, `EXCESS`, `null` (`over-amount.ts:100`, `:108`; `build-excess-split-payload.ts:91`, `:94`).

> ⚠️ The type comment is stale — `/** Split type (BASIC | USER | null) */` at `PD/business-plan/types/detail/receipt-types.ts:152` omits `EXCESS`. The actual value set is the 4 above.

### 6.2.2 Why only `EXCESS` uses the original axis

`over-amount.ts:48-50`:

> Even after separating the excess into a self-pay row, "the amount separated out" must still be displayed, and on the own-share axis **the ruled-amount row's own share equals the ruled amount, so it is always 0.** Every other split determines on the own-share basis.

Reflected in code: `over-amount.ts:134-140` branches on `EXCESS` first and uses `getReceiptUsedAmount` (the original axis). Everything else uses `getExcessJudgmentAmount` (the own-share axis) at `:143`.

### 6.2.3 Why the rule side differs between `USER` and `BASIC`

`over-amount.ts:52-54`:

| Split | Nature | Rule-side handling |
|---|---|---|
| **BASIC** | Splits a single expense by an **accounting allocation ratio** | The limit is **split by the same ratio** — that is what preserves the group total's excess |
| **USER** | Divides the same expense **among people or cost centers** | The ruled amount is **the limit that applies to that person's share**, so each row is compared against the **full original limit** |

- Why `USER` has no separate code branch: the rule side is the same as the default path (`ruledAmount`), and the axis source of truth (`getExcessJudgmentAmount`) already provides the own share on the usage side (`over-amount.ts:64-65`).
- The `BASIC` branch (`over-amount.ts:145-154`):
  - `dividedRuledAmount != null` → determine with that value (`:148-150`)
  - `dividedRuledAmount == null` **AND** `divisionOrder !== 0` (a re-split child) → `''`, determination not possible (`:151-152`)
  - `dividedRuledAmount == null` **AND** the first child → falls back to the original basis (`ruledAmount`) below (`:153`)
  - **Why it does not fall back to the full original for a child with no distributed value**: "the save-side `ruledAmount` carries the full original, so falling back would **overstate** the excess on every child" (`:146-147`).

### 6.2.4 ⚠️ Not a bug (intended) — a `USER` split consumes the limit once per row

`over-amount.ts:56-58`, verbatim:

> So a USER split ends up consuming the limit once per row — **splitting used 86,000 / ruled 60,000 in half puts both rows within the rule and the group's total excess of 26,000 disappears.**
> **This is a cost accepted when the requirement was settled** (the per-person limit interpretation takes priority).

| | Used amount (own share) | Ruled amount (compared against) | Excess |
|---|---:|---:|---:|
| Before splitting | 86,000 | 60,000 | 26,000 |
| USER split row 1 | 43,000 | **60,000** (the full original) | 0 |
| USER split row 2 | 43,000 | **60,000** (the full original) | 0 |

**An external reader must not mistake this for a bug.** It is the logical consequence of the "per-person limit" interpretation and a behavior product planning chose explicitly.

### 6.2.5 The axis source-of-truth function `getExcessJudgmentAmount`

`PD/bstr-policy/utils/receipt-used-amount.ts:69-75`.

```
if divisionType != null → getDivisionRowOwnAmount(r)  (own share)
  = supplyAmount + getReceiptOwnVatAmount(r)      (when supplyAmount != null)
  = null                                          (supplyAmount == null → cannot be derived)
if the own share is null, or unsplit → getReceiptUsedAmount(r)  (fallback)
```

| Rule | Content | Source |
|---|---|---|
| A split row's own share | Supply value + **its own** VAT | `:56-59` |
| The first child's VAT | Its direct `vatAmount` is 0 → use the **preserved VAT** `dividedParentVatAmount` | `:44-47` |
| Rows with no supply value | `null` → fall back to the used amount | `:57`, `:71-74` |
| Why `approvalAmount` is not used | `merge-divided-receipt` preserves the full original on **every** child row (so the representative row can display the used amount), so using it for the own-share determination gives the original total on every row | `:53-54` |

**This function is the sole source of truth for the axis** (`:62-67`). All three of the following use it:

1. The excess-amount column — `getOverAmountDisplayValue` (`over-amount.ts:143`)
2. Suspected violation ⑧ corporate-card overrun — `compliance-attach`
3. The excess-split payload — `build-excess-split-payload.ts:121`

**If the three compute it separately they diverge** — in a real measurement (2026-08-26) the column was corrected to the own share while the split payload remained on the full original, so an own-share excess of 17,818 was **truncated and saved as 26,000** (`receipt-used-amount.ts:65-67`, `build-excess-split-payload.ts:117-120`).

### 6.2.6 After an excess split — the ruled-amount row keeps showing the excess

**Requirement changed 2026-09-01 (Flow 84317778)** (`over-amount.ts:70`).

Previously (the 2026-08-25 planning flow chart) the split was considered to have resolved the excess and the ruled-amount row showed `0`; it was changed so that **the excess separated out as self-pay stays visible in the column even after the split.**
The trigger was a report from the live screen — "I split it and the excess (self-pay) column is 0" was **perceived as the change not having been applied** (`over-amount.ts:72-74`).

The amount example table at `over-amount.ts:76-80`:

| Row | Used | Ruled | Claim | Excess |
|---|---:|---:|---:|---:|
| Original receipt before split | 86,000 | 60,000 | 86,000 | 26,000 |
| After split, **the ruled-amount row** (order 0) | 86,000 | 60,000 | 60,000 | **26,000** |
| After split, **the excess row** (order 1) | (blank) | (blank) | 26,000 | **(blank)** |

- **The used amount and ruled amount are not divided** (the original is kept). **Only the claim amount is divided** (`over-amount.ts:82`, `build-excess-split-payload.ts:9`).
- So the ruled-amount row computes the same excess as before the split, on the original axis (`getReceiptUsedAmount − ruledAmount`) (`over-amount.ts:83-84`).
- The excess row **stays blank** — that row is itself the self-pay amount, which is visible in the claim-amount column. This function returns an empty string so that the same result appears outside the desktop table too (mobile cards, etc.) (`over-amount.ts:84-86`, code at `:135`).
- **The exception** — a ruled-amount row from re-splitting a cost-center split child (`divisionOrder ≠ 0`) has no pre-split own share left on the row (`approvalAmount` is the full original), so it cannot be derived and shows **`0` as before** (`over-amount.ts:88-89`, code at `:137`). An undetermined ruled amount (`null`) takes the same branch.

> ⚠️ The determination consumers are unaffected by this display change — submission blocking ⑰, the split icons (`ReceiptRowActions`, `MobileReceiptCard`), and the split menu (`split-menu-variant`) each **exclude `EXCESS` up front before** using this function in a determination (`over-amount.ts:91-93`).

### 6.2.7 Identifying the ruled-amount vs. excess row — by a marker, not `divisionOrder`

Source of truth: `isExcessOverRow` (`PD/bstr-policy/utils/excess-split-row.ts:45-49`).

```
divisionType !== 'EXCESS'        → false
excessOver != null              → excessOver as-is (the source of truth)
excessOver == null (old data)   → divisionOrder !== 0 (the fallback)
```

**Why not the order** (`excess-split-row.ts:7-23`): the premise broke once an excess split could also be opened on a cost-center split child row — **because the row holding the group VAT must be `order 0`**, splitting a child row pushes the excess pair to `order 1` and `2`, and order-based determination **mistakes the ruled-amount row for the excess row.**

| order | Row | Order-based determination | Actual |
|---:|---|---|---|
| 0 | The cost-center split sibling (holds the VAT) | — | — |
| 1 | The excess split's ruled-amount row | Excess row (**wrong**) | Ruled-amount row |
| 2 | The excess split's excess row | Excess row | Excess row |

The marker field: backend `issued_receipt.excess_over` → DTO `excessOver` (`excess-split-row.ts:32-33`). A row **explicitly** marked `excessOver === false` is the ruled-amount row and does not take the order fallback (`:43`).

### 6.2.8 The amount-decomposition rules of the excess-split payload

`PD/cloud-expense-report/utils/build-excess-split-payload.ts`.

| Item | Rule | Source |
|---|---|---|
| Base amount | `getExcessJudgmentAmount` (**the own share**) | `:117-121` |
| The ruled-amount row's claim amount | `usedAmount − excess` = `MIN(used, ruled)` | `:127`, `:18` |
| The excess row's claim amount | `MAX(0, usedAmount − ruledAmount)` | `:125`, `:18` |
| `ruledAmount == null` | Returns `null` = **there is nothing to split** | `:122-123` |
| An excess of 0 | Returns `null` | `:126` |
| VAT | **Concentrated entirely on the first row** (not distributed by ratio). The excess row is 0 | `:109-111`, `:140` |
| A non-deductible (`nonDeduction`) receipt | VAT 0 | `:133`, `:28` |
| The first row's VAT cap | `MIN(the original VAT, the ruled-amount row's claim amount)` — prevents a negative supply value | `:134-136` |
| Fields not sent | `reqAmt`, `approvalAmount`, `ruledAmount` | `:21-33` |
| Fields sent | `splAmt`, `vatAmt`, `issuedAmt` (=`splAmt`), `slip.slipSplAmt`, `slip.slipVatAmt` | `:29-30`, `:183-203` |
| The meaning of `divisionOrder = 0` | **Always the ruled-amount row** — the backend list query passes only the single row matching `EXCESS ∧ divisionOrder = 0`, preventing duplicate display | `:42-43` |
| `corporationUser` | `null` on `EXCESS` rows (a condition for passing the list gate) | `:44-45`, `:192-194` |
| Endpoint | `PATCH /api/v2/receipt/divide/{receiptId}` (the same as a cost-center split) | `:4-5` |

`✔` The sum of the claim amounts = the original used amount (`:19`).

**Coexistence with a cost-center split** (requirement settled 2026-08-26, `:47-58`): the backend's `divideIssuedReceipt` deactivates **all** existing children of that receipt on every request and creates only the payload's rows anew.
So if the payload has only 2 rows, the cost-center split **loses even its budget department and assignees** (measured 2026-08-26). The solution is **to send the entire desired child set** — the 2 excess-split rows plus the sibling rows unchanged (`:53`, code at `:274-276`).

The ordering convention: **the row holding the group VAT keeps `order 0`** (`:57-58`, `:254-260`).

| Target | Order layout |
|---|---|
| A representative row (order 0, holds the VAT) | 0 = the EXCESS ruled-amount row (inherits the VAT), 1 = the EXCESS excess row, 2.. = siblings |
| A cost-center child (order ≠ 0) | 0.. = siblings (including the VAT-holding row), n = the EXCESS ruled-amount row, n+1 = the EXCESS excess row |

Post-save processing reuses **the same path** as a cost-center split (`handleDivisionSave`) — re-query → `mergeDividedReceiptsWithOriginal` → rule recalculation → store replacement (`PD/cloud-expense-report/features/use-excess-split-save.ts:6-12`).
It is called with `receiptId` (the Receipt PK), which differs from the store key `id` (the IssuedReceipt PK) (`use-excess-split-save.ts:14-15`, `:70-74`). Receipts with no `receiptId` (automatically created per diem, lodging, fuel, etc. that do not exist on the server) **are not split targets.**

---

## 6.3 The claim-amount (`reqAmt`) cap table

Source of truth: `PD/bstr-policy/utils/receipt-amount-limit.ts` (a 100% port of the legacy `isAllowedReqAmt` at `SeahExpenseReportUtils.tsx:6510-6577`, `:4-5`).

### 6.3.1 ★ The actual `bstrPayClassType` value set

**The `DAILY` used in past samples is a value that does not exist.** Exhaustively verified across `packages/` and `apps/`:

| Checked | Result |
|---|---|
| `bstrPayClassType === 'DAILY'` **comparison branches** | **0** — no code matches this value |
| `bstrPayClassType: 'DAILY'` **assignments** | **2, all test fixtures** (0 in production) |

The two fixtures are in fact evidence that "`DAILY` means nothing":

| Location | Nature |
|---|---|
| `PD/business-plan/features/TravelerDailyCostField.test.tsx:23` | An arbitrary placeholder in a hook mock. The component only looks at `bstrPayClassType === 'NONE'` (`TravelerDailyCostField.tsx:136`) → anything other than `NONE` behaves the same, whatever the string |
| `PD/cloud-expense-report/utils/autofill-receipt-defaults.test.ts:611` | A zod "all fields" pass test. **The fact that `result.success === true` even with `'DAILY'` (`:622`) is itself the evidence that the schema does not validate the value set** |

The likely origin of the confusion: **`DAILY_COST`** is not a payment type but a **purpose (`tranKindType`)** value whose label is "per diem" (`PD/expense-policy/types/expense-policy-types.ts:69`, `:80`; `PD/bstr-policy/constants/activity-actual-daily-evidence.ts:16`). The **payment type for a per-diem receipt is `FIXED`**, written directly by the builder (`auto-receipt-utils` — `pay-class-utils.ts:29-31`).

The actual value set — 1:1 with the backend's `common-core/.../pconstant/BstrPayClassType.java` (`PD/bstr-policy/constants/toll-evidence.ts:17-18`):

| Value | Label | Definition site | Treatment in the amount pipeline |
|---|---|---|---|
| `NONE` | Unpaid | `expense-policy-types.ts:27`, `bstr-expense-rule/utils/mappers.ts:23` | The `getNonDivisionMax` default path |
| `ACTUAL` | Actual cost | `expense-policy-types.ts:25`, `mappers.ts:19` | Cap = `approvalAmount` |
| `ACTUAL_FIXED` | Actual cost + fixed | `mappers.ts:20`, `bstr-policy/constants/activity-actual-daily-evidence.ts:19` | **Manual receipts are treated as actual cost** |
| `LIMITED` | Actual cost (capped) | `expense-policy-types.ts:26`, `mappers.ts:21` | Cap = `MIN(ruled, approved)` |
| `FIXED` | Fixed | `expense-policy-types.ts:24`, `mappers.ts:18` | Cap = `ruledAmount` |
| `FUEL` | Fuel | `expense-policy-types.ts:28`, `mappers.ts:22` | Cap = `ruledAmount` (unsplit only) |
| `TOLL` | Tolls | `bstr-policy/constants/toll-evidence.ts:20`, `mappers.ts:24` | No dedicated branch → default |
| `null` | Rule not looked up | — | The default path. For `ETC`, `MIN(ruled, approved)`; otherwise `Infinity` |

> ⚠️ **Two type comments are stale — the `DAILY` error in past samples very likely came from here.**
>
> | Location | Comment text | Reality |
> |---|---|---|
> | `PD/business-plan/types/detail/receipt-types.ts:248` | `Payment basis (DAILY \| TOTAL \| FUEL \| ACTUAL \| NONE)` | No `DAILY` or `TOTAL` |
> | `PD/cloud-expense-report/types/create/receipt-dto-types.ts:36` | `Payment basis (DAILY \| TOTAL \| FUEL \| NONE)` | No `DAILY` or `TOTAL` |
>
> Both fields have the zod type `z.string().nullable().optional()`, so there is **no runtime validation** — sending `DAILY` passes parsing, matches no branch, and **silently flows to the default path (`Infinity` = no limit).** No signal is raised that the value is wrong.
>
> Conversely, `ACTUAL_FIXED` and `TOLL` appear in neither comment yet are **values actually in use.**

`isActualLikePayClass` (`PD/bstr-policy/utils/pay-class-utils.ts:40-47`):

```
ACTUAL  or  ACTUAL_FIXED  →  true
```

**Why `ACTUAL_FIXED` is treated as actual cost** (`pay-class-utils.ts:28-38`): the "fixed" component of an `ACTUAL_FIXED` rule is **handled entirely by the automatic per-diem receipt** (the builder writes `bstrPayClassType='FIXED'` directly onto the receipt — `auto-receipt-utils`), while **ordinary (manually attached or imported) receipts, which inherit the rule response's payment type, follow the actual-cost rule.** Application sites: ruled-amount determination (`rule-update-*`), the claim amount (`autofill-req-amount`), input caps, excess determination, and all validation.

`isNonePayClass` (`pay-class-utils.ts:21-25`): with `NONE`, receipt attachment itself is blocked (except the receipt-import popup) and per-diem and fuel automatic receipts are not added to the table either (`pay-class-utils.ts:14-18`). BZP has no `NONE` rule, so it is always `false`.

### 6.3.2 Unsplit receipts — a 1:1 comparison of the 7 `getNonDivisionMax` rows

The JSDoc table (`receipt-amount-limit.ts:29-38`) vs. the code (`:41-80`).

| # | Condition | Cap formula | Rationale | JSDoc | Code | Match |
|---|---|---|---|---|---|---|
| 1 | `cardType === 'CORP'` | `approvalAmount` | A corporate card **cannot exceed the amount the card issuer settled** | `:31` | `:46-47` | ✔ |
| 2 | `tranKindType === 'FOOD'` | `approvalAmount` | Meals are **always on the approved-amount basis** | `:32` | `:48` | ✔ |
| 3 | `ACTUAL` (+`ACTUAL_FIXED`) | `approvalAmount` | Actual-cost payment | `:33` | `:60-61` | ✔ |
| 4 | `LIMITED` | `MIN(ruledAmount, approvalAmount)` | The **smaller** of the rule and the actual cost | `:34` | `:64-65` | ✔ |
| 5 | `FIXED` | `ruledAmount` | Fixed payment | `:35` | `:66-67` | ✔ |
| 6 | `FUEL` | `ruledAmount` | Fixed fuel payment | `:36` | `:68-69` | ✔ |
| 7 | Otherwise (`NONE`, `TOLL`, `null`, etc.) | `Infinity` | No limit | `:37` | `:78` | ✔ |

**All 7 rows match the source 1:1.** However, **the code has 2 more branches that are absent from the JSDoc table**:

| # | Condition | Cap | Rationale | Code | JSDoc table |
|---|---|---|---|---|---|
| **1.5** | `isCompareInForeign` (entering foreign-currency comparison) | `foreignAmount ≤ overseasRuled` → `approvalAmount`<br>Over → `ruledAmount` | Aligns the cap with the auto-fill (`calculateDefaultReqAmt`) | `:50-58` | **Absent** |
| **7.5** | Within the default, `cardType === 'ETC'` | `MIN(ruledAmount, approvalAmount)` | Miscellaneous receipts get `MIN(ruled, used)` regardless of whether a rule exists. A ruled amount of 0 → **a limit of 0** | `:71-77` | **Absent** |

**The order of application matters.** The code executes in the order `CORP → FOOD → foreign currency → ACTUAL-like → switch`.
So **the foreign-currency branch is evaluated before `ACTUAL`/`LIMITED`/`FIXED`/`FUEL`** — rows 3 through 6 of the JSDoc table are only reached when the foreign-comparison condition does not hold.

**1.5 (the foreign-currency cap) in detail** (`:50-58`):

```
isCompareInForeign({ currencyCode, overseasRuledAmount })
  → foreignAmount ≤ overseasRuledAmount  ?  approvalAmount  (KRW at the receipt's rate)
                                         :  ruledAmount     (KRW converted at the rule's rate)
```
"Resolves the bug where, when the receipt rate exceeded the rule rate, the KRW `ruledAmount` blocked the claim" (`:53`).

`isCompareInForeign` (`PD/bstr-policy/utils/foreign-currency-utils.ts:117-122`):
```
isForeignReceipt(currencyCode)  AND  (overseasRuledAmount ?? 0) > 0
```
- `isForeignReceipt`: `true` when `currencyCode` is anything other than `null`, `''`, or `'KRW'` (`:26-30`).
- **`overseasRuledAmount = 0` is interpreted as "no foreign limit defined."** The backend returns `0` rather than `null` for receipts with no foreign-currency rule, so `null` and `0` must both be guarded as "foreign comparison not possible" — this blocks the bug where a 0 with only a currency label, like `JPY 0`, was exposed in the cell (`:110-113`).
- The operational definition: **"foreign notation and comparison only when both the rule and the receipt are in a foreign currency"** (`:115`).

**7.5 (ETC) in detail** (`:71-74`):

> For miscellaneous receipts (`ETC`), the claim-amount limit is `MIN(ruledAmount, usedAmount)` regardless of whether a rule exists.
> The same applies when the rule was not looked up (`payClass` unset, e.g. air transportation) or is unpaid (`NONE`) — **a ruled amount of 0 means a limit of 0.** The cell stays editable, but with a ruled amount of 0 a keyboard entry above 0 is rejected → the value does not change.
> Card purchase records (non-`ETC`) keep the existing unlimited (`Infinity`).

### 6.3.3 ★ The split-receipt path — a double check

`isAllowedReqAmt` (`receipt-amount-limit.ts:143-172`) requires **both** conditions (`:135-136`, `:8-9`).

```
if (row.divisionType) {                                        // a split receipt
  ① individual line:  newValue ≤ (row.supplyAmount ?? 0)        // :152-153
     immediately false on violation
  if (cardType === 'CORP') return newValue ≤ supply;            // :155-156 (no total check)
  ② total for the same receiptId:
     otherLinesTotal = Σ { r.reqAmt | r.receiptId === row.receiptId ∧ r.id !== row.id }
     (otherLinesTotal + newValue) ≤ getDivisionTotalMax(row)     // :158-166
} else {
  newValue ≤ getNonDivisionMax(row)                             // :169-171
}
```

- **① The individual-line cap is the supply value (`supplyAmount`)** — an individual split line cannot exceed its supply value (`:152-153`).
- **A corporate-card split checks only ①** — there is no total limit (`:98-99`, `:155-156`).
- The group key is **`receiptId`** (the Receipt PK) and the row itself is excluded by `id` (the IssuedReceipt PK) (`:159-162`).

**② The total cap by payment option — `getDivisionTotalMax`** (`:94-126`):

| # | Condition | Total cap | Compared with unsplit (`getNonDivisionMax`) | Source |
|---|---|---|---|---|
| 1 | `cardType === 'CORP'` | **`Infinity`** | ≠ (unsplit is `approvalAmount`) | `:98-99` |
| 2 | `tranKindType === 'FOOD'` | `approvalAmount` | = | `:100-101` |
| 3 | `isCompareInForeign` | `foreign ≤ overseasRuled ? approval : ruled` | = (identical semantics, `:103`) | `:104-108` |
| 4 | `ACTUAL` (+`ACTUAL_FIXED`) | `approvalAmount` | = | `:110-111` |
| 5 | `LIMITED` | `MIN(ruled, approval)` | = | `:113-115` |
| 6 | `FIXED` | `ruledAmount` | = | `:116-117` |
| 7 | **`FUEL`** | **Falls through to the default path** (`MIN` for `ETC`, otherwise `Infinity`) | **≠** (unsplit is `ruledAmount`) | `:113-125` |
| 8 | Within the default, `cardType === 'ETC'` | `MIN(ruled, approval)` | = | `:119-123` |
| 9 | Otherwise | `Infinity` | = | `:124` |

> ⚠️ **The `FUEL` asymmetry**: the `switch` in `getDivisionTotalMax` has **no `FUEL` case** (`:113-117` — only `LIMITED` and `FIXED`). Unsplit gives `FUEL → ruledAmount`, but the split total is `Infinity` for non-`ETC`. There is no comment in the source explaining the difference, so **there is no basis for judging whether it is intended or an omission** → open item U-1.

**The cap used for clamping — `getReqAmtMax`** (`:183-189`): used by `handleAmountBlur` to `Math.min` the entered value against the cap. **For a split it returns only the individual-line cap (`supplyAmount`)**, and the total cap is checked separately in `isAllowedReqAmt` (`:177-179`).

**Sharing scope**: the expense report (`cloud-expense-report/utils/receipt-amount-limit`) re-exports this module, and the plan's advance-payment cell (`AdvanceCostCell`) uses the same cap — "fixing only one side creates drift where the same receipt is blocked on the expense report but accepted on the plan" (`:11-13`).
The plan's advance-payment actual cost has no splitting, so it takes only the unsplit path (`:9`).

### 6.3.4 The `cardType` value set (as used in cap determination)

The `CARD_TYPE_TO_EXPENSE_TYPE` mapping (`PD/bstr-policy/utils/autofill-req-amount.ts:281-289`) is the source of truth for the set the front end recognizes.

| `cardType` | API `expenseType` | Special treatment in cap determination |
|---|---|---|
| `CORP` | `CORP_CARD` | Unsplit cap = the approved amount / split total = `Infinity` |
| `CHECK` | `CHECK` | — |
| `PERSONAL` | `PERSONAL_CARD` | — |
| `ETC` | `ETC_CARD` | `MIN(ruled, approved)` on the default path |
| `ZERO` | `ZERO_PAY` | — |
| `BZP_POINT` | `BZP_POINT` | Included in the reservation-prepaid determination set |
| `BZP_MONEY` | `BZP_MONEY` | **Excluded** from the reservation-prepaid determination (confirmed by product planning) |

The reservation-prepaid payment-method set `RESERVATION_PREPAID_CARD_TYPES = ['CORP', 'BZP_POINT']` (`PD/bstr-policy/utils/reservation-prepaid-receipt.ts:40`). `BZP_MONEY` (BeePle Money) is deliberately excluded (`:37-39`).

---

## 6.4 Claim-amount auto-fill — `calculateDefaultReqAmt`

`PD/bstr-policy/utils/autofill-req-amount.ts:33-274`. A port of the legacy `getSeahDefaultReqAmtBySetting()` (`:10`). It is an **`async` function** and calls the settings API.

Return type: `Promise<number | null>` — **`null` means "do not auto-fill; leave it to the user's direct entry"** and differs from `0`.

### 6.4.1 Input parameters

| Parameter | Type | Meaning | Line |
|---|---|---|---|
| `approvalAmount` | `number` | The approved (used) amount | `:34` |
| `supplyAmount` | `number` | The supply value | `:35` |
| `vatAmount` | `number?` | VAT | `:36` |
| **`ruledAmount`** | `number` | **The ruled amount (KRW)** | `:37` |
| `cardType` | `string` | Card type | `:38` |
| `tranKindType` | `string?` | Purpose type | `:39` |
| `bstrPayClassType` | `string?` | Payment type | `:40` |
| `divisionType` | `string?` | Split type (`USER` \| `BASIC` \| `null`) | `:41-42` |
| `isDivisionFirstLine` | `boolean?` | `divisionOrder === 0` | `:43-44` |
| `bstrCategoryType` | `string?` | Trip category (`GRADE`, etc.) | `:45-46` |
| `alreadyUsedReqAmt` | `number?` | The sum of claim amounts already used on **other expense reports** under the same plan (same date + same `tranKindType`) | `:47-52` |
| `currencyCode` | `string?` | The foreign currency | `:53-54` |
| `foreignAmount` | `number?` | The original foreign used amount | `:55-56` |
| **`overseasRuledAmount`** | `number?` | **The original foreign ruled amount** | `:57-58` |
| `alreadyUsedForeignReqAmt` | `number?` | The foreign running total from other expense reports in the same currency | `:59-63` |
| `isNonDeduct` | `boolean?` | Whether non-deductible | `:64-69` |
| `dividedParentVatAmount` | `number?` | The **preserved** original VAT of a split's first child | `:70-76` |
| **`dividedRuledAmount`** | `number?` | **A split child's own share of the ruled amount** | `:77-83` |
| `dividedOverseasRuledAmount` | `number?` | The own share of the foreign ruled amount | `:84-85` |
| `dividedForeignAmount` | `number?` | The own share of the original foreign amount | `:97-103` |
| `isReservationPrepaid` | `boolean?` | A reservation-prepaid receipt | `:86-96` |

### 6.4.2 The decision order (exactly as executed)

| Step | Condition | Return / action | Line |
|---|---|---|---|
| **1** | `divisionType` is present and it is **not a non-CORP BASIC child** | `CORP` → first line `supplyAmount + (dividedParentVatAmount ?? vatAmount ?? 0)`, thereafter `supplyAmount` | `:128-134` |
| | ↳ Otherwise (non-CORP `USER`) | **`null`** (prompting manual entry) | `:135` |
| **2** | For a non-CORP BASIC child, **substitute the effective amounts with the own share** | `effectiveApproval = supplyAmount + selfVat`<br>`effectiveSupply = supplyAmount`<br>`effectiveRuled = dividedRuledAmount`<br>`effectiveOverseasRuled = dividedOverseasRuledAmount`<br>`effectiveForeign = dividedForeignAmount ?? foreignAmount` | `:144-154` |
| | ↳ `dividedRuledAmount == null` | Settings cannot be evaluated → **return the own-share approved amount** | `:147-148` |
| **3** | `isActualLikePayClass` (`ACTUAL`/`ACTUAL_FIXED`) | `effectiveApproval` — **returned immediately, regardless of settings** | `:160-162` |
| **4** | `bstrCategoryType === 'GRADE'` | `effectiveApproval` | `:163-165` |
| **5** | `isCompareInForeign` (entering foreign comparison) | See 6.4.3 below | `:171-183` |
| **6** | `tranKindType === 'FOOD'` (KRW) | See 6.4.4 below | `:190-198` |
| **7** | The settings lookup `fetchRequestedAmountSetting()` **fails** | **`0`** | `:200-205` |
| **8** | `!setting.requestedAmountUsed` | **`0`** | `:207-209` |
| **9** | `!bstrPayClassType` **AND** `effectiveRuled ≤ 0` (the rule-not-looked-up guard) | Non-CORP BASIC child → `ETC ? 0 : effectiveApproval`<br>`ETC` → **`null`**<br>Otherwise (CORP, etc.) → **pass through** (continue below) | `:219-227` |
| **10** | Determine the reference field by card type | `resolveStandardField(setting, cardType)` | `:230`, `:305-315` |
| **11** | Derive the remaining rule limit | `remainingRuled = (effectiveRuled > 0 ‖ ETC) ? MAX(0, effectiveRuled − alreadyUsedReqAmt) : Infinity` | `:238-241` |
| **12** | Whether to apply the cap | `shouldCapToRuled = !isReservationPrepaid ∧ (ETC ‖ non-CORP BASIC child)` | `:255` |
| **13** | The final value per reference field | See 6.4.5 below | `:257-273` |

### 6.4.3 The foreign-currency path (step 5)

```
ruledForeign     = effectiveOverseasRuled ?? 0
remainingForeign = ruledForeign > 0 ? MAX(0, ruledForeign − alreadyUsedForeignReqAmt) : Infinity

if (effectiveForeign ≤ remainingForeign)  return effectiveApproval;  // within the foreign rule → the full KRW used amount
if (isReservationPrepaid)                 return effectiveApproval;  // cap exemption path ①
                                          return effectiveRuled;     // over the foreign rule → the KRW-converted ruled amount
```
`:172-183`. Foreign-currency FOOD is also handled by this branch (an operational policy — `:168`).
If `overseasRuledAmount = null`, it falls back to the KRW path (`:169`). A BASIC child falls back to the KRW path when it has no own-share foreign rule (usually not wired) (`:170`).

### 6.4.4 The FOOD (KRW) path (step 6)

```
if (isReservationPrepaid)                       return effectiveApproval;      // cap exemption path ②
if (alreadyUsedReqAmt != null && > 0) {
  remainingRuled = MAX(0, effectiveRuled − alreadyUsedReqAmt);
  return remainingRuled > effectiveApproval ? effectiveApproval : remainingRuled;
}
return effectiveApproval;
```
`:191-197`. A port of the legacy `getSeahDefaultReqAmtBySettingForFood:1059-1065` (`:189`).

### 6.4.5 The final value per reference field (step 13)

`resolveStandardField` (`:305-315`) **always** takes the `requestedAmountDefaultType` matching the card type from the per-expense-type settings (`requestedAmountTypeList`). If nothing matches, it falls back to `'EMPTY'` (`:292`, `:314`). A 2026-07 planning change **abolished** the `requestedAmountType` (DEFAULT/EXPENSE_TYPE) distinction, and that field remains only for server-response compatibility (`:296-302`, `PD/bstr-policy/api/requested-amount-api.ts:18-21`).

| `standardField` | Return value | Line |
|---|---|---|
| `APPROVAL_AMOUNT` | `shouldCapToRuled ? MIN(effectiveApproval, remainingRuled) : effectiveApproval` | `:258-259` |
| `SUPPLY_AMOUNT` | `supplyBase = isNonDeduct ? effectiveApproval : effectiveSupply`<br>`shouldCapToRuled ? MIN(supplyBase, remainingRuled) : supplyBase` | `:260-266` |
| `RULED_AMOUNT` | `isReservationPrepaid → effectiveApproval` (cap exemption path ④)<br>Otherwise `remainingRuled > effectiveApproval ? effectiveApproval : remainingRuled` | `:267-270` |
| `EMPTY` / no match (default) | **`0`** | `:271-272` |

**Applying the rule-limit cap only to the `RULED_AMOUNT` basis is the legacy source-of-truth behavior** (`getSeahDefaultReqAmtBySetting:1154-1166` — `APPROVAL_AMOUNT`/`SUPPLY_AMOUNT` return as-is with no cap, `:243-245`). When the setting is "approved amount" or "supply value," the full used amount is set as the claim amount, as with meals, and **an excess over the rule is handled only as an excess-reason warning (in red)** (`:245-247`). Only 2 exceptions cap even on `APPROVAL`/`SUPPLY`: `ETC` and a non-CORP BASIC child (`:248-250`).

### 6.4.6 ★ What happens when there is no ruled amount

| State | Result | Line |
|---|---|---|
| `!bstrPayClassType` **AND** `ruledAmount ≤ 0` **AND** `cardType === 'ETC'` | **`null`** — no auto-fill, direct user entry | `:223-224` |
| `!bstrPayClassType` **AND** `ruledAmount ≤ 0` **AND** a card purchase record (`CORP`, etc.) | The guard is **passed** → the used amount is set as the claim amount per `standardField` | `:225-226`, `:214-216` |
| `!bstrPayClassType` **AND** `ruledAmount ≤ 0` **AND** a non-CORP BASIC child | `ETC ? 0 : effectiveApproval` | `:220-222` |
| `ruledAmount > 0` but on the `RULED_AMOUNT` basis | `MIN(remainingRuled, effectiveApproval)` | `:270` |
| Settings lookup fails / `requestedAmountUsed = false` | **`0`** | `:204`, `:208` |

The rationale for the guard (`:211-217`):
- Miscellaneous receipts (`ETC`, e.g. air transportation): with no rule, do not auto-set the used amount — **blocking the regression where "no ruled amount" was misread as "unlimited"** (commit `8ea776a63`).
- Card purchase records (`CORP`, etc.): even with no rule, set the used amount as the claim amount per the settings — blocking the regression where a corporate-card transportation claim amount stayed at 0.
- `ACTUAL`, `GRADE`, `FOOD`, and foreign currency are **already handled by earlier branches** and are unaffected by this guard.

**The 4 cap-exemption paths for a reservation-prepaid receipt (`isReservationPrepaid`)** — payment is already complete in the reservation system and the user cannot adjust the amount, so cutting it would leave an unsettled remainder (`:86-96`):

| Path | Location |
|---|---|
| ① The ruled-amount cap on a foreign-currency overrun | `:179-181` |
| ② The FOOD already-used running-total deduction cap | `:191-192` |
| ③ Neutralizing `shouldCapToRuled` | `:251-255` |
| ④ Replacing the `RULED_AMOUNT` basis with the approved amount | `:268-269` |

The base the settings choose is still respected, however — `APPROVAL_AMOUNT`→approved amount, `SUPPLY_AMOUNT`→supply value, no match (`EMPTY`)→0 (`:92-94`).

Determination: `isReservationPrepaidReceipt` = **a reservation receipt (`additionalReceiptType ∈ {FLIGHT, TRANS, ACCOM}`) AND a payment method (`cardType ∈ {CORP, BZP_POINT}`)** (`PD/bstr-policy/utils/reservation-prepaid-receipt.ts:25`, `:40`, `:55-63`). It is a local determination with no API call.

### 6.4.7 The settings API

| Item | Value |
|---|---|
| Endpoint | `GET /api/v2/business-setting/etc/BSTR/requestedAmount` |
| Source | `PD/bstr-policy/api/requested-amount-api.ts:9-12` |
| DTO | `RequestedAmountSettingDto` (`:23-38`) — the server's `EtcRequestedAmountDto` as-is |
| Key fields | `requestedAmountUsed` (whether in use), `requestedAmountTypeList[]` (the basis per card type, **the only source for the claim-amount calculation basis**) |
| Item DTO | `RequestedAmountExpenseTypeItem { expenseType, requestedAmountDefaultType }` (`:41-46`) |
| Reference-field values | `EMPTY` \| `APPROVAL_AMOUNT` \| `SUPPLY_AMOUNT` \| `RULED_AMOUNT` (`:26`) |

---

## 6.5 The submission validation pipeline

Entry point: `validateDraftConditions(ctx: AsyncValidationContext)` (`PD/cloud-expense-report/utils/validation/validate-draft-pipeline.ts:79-285`).
A port of the legacy `useSeahDraftConditionValidation.handleCheckSeahDraftCondition` (`:5`).

### 6.5.1 ★ Fail-fast

**It returns a `CreateValidationError` immediately on the first violation and does not execute the remaining rules** (`:33`, `:74`). If everything passes, it returns `null` (`:284`).

In code, every rule takes the form `if (xxxErr) return xxxErr;` (`:119`, `:123`, `:127`, `:131`, `:135`, `:150`, `:163`, `:168`, `:174`, `:181`, `:190`, `:198`, `:206`, `:217`, `:226`, `:259`, `:266`, `:271`, `:281`).

**Implication for an external system**: you cannot get the full list of violations from a single submission attempt. It becomes a **fix-one-and-retry** loop. That is why running the entire §6.6 checklist in advance is better.

### 6.5.2 Validation order — the full table

**The basis for the validation order is not cost but inheritance of the legacy validation numbers** (① and ③ are missing numbers). Sync and async actually alternate, so **you must not reorder to "put the sync ones first"** (`:8-9`).

Comparing the JSDoc table (`:11-31`) with the code's call order (`:112-283`).

| No. | Rule | Async | API called | Gate | Code lines |
|---|---|---|---|---|---|
| ② | Region/country not selected | N | — | **Temporarily disabled (commented out)** | `:112-115` |
| ②-1 | Internal-order `BUDGET_DEPARTMENT` multi-row ratios total 100% | N | — | Always | `:118-119` |
| ②-2 | WBS `DIRECT_INPUT` multi-row ratios total 100% | N | — | Always | `:122-123` |
| ④ | Trip-purpose restrictions | Y | `fetchPurposeRestrict` | Always | `:126-127` |
| ⑤ | Duplicate per-diem dates | Y | `fetchPreDailyCostDates` | Always | `:130-131` |
| ⑥ | Usage-date range (adjusted for pre-trip allowed days; lodging excluded) | Y | `fetchAllowBeyondDays` | Always | `:134-135` |
| ⑦ | Description required | N | — | **opt-in** `features.bstrSummaryRequired` (default false) ∧ the `CLOUD_SUMMARY` form item ON | `:137-151` |
| ⑧ | Claim amount of 0 | Y | `fetchRequestedAmountSetting` | `requestedAmountUsed = true` ∧ `hasAdvancePayment !== true` | `:153-164` |
| ⑧-1 | Posting date required (`required=true` but blank) | N | — | Always | `:166-168` |
| ⑨ | Receipt date > posting date | N | `fetchBudatBeforeBldatDays` (looked up beforehand) | Always | `:170-174` |
| ⑩-0 | Employee-dormitory report date ↔ lodging usage date overlap | N | — | Always (automatic receipts excluded) | `:176-181` |
| ⑩ | Lodging date outside the period / duplicated | Y | `fetchAllowBeyondDays` | Always | `:183-190` |
| **⑪** | **Lodging/meal total overrun** | Y | `fetchTranKindsLimitAmounts` + `fetchPreDraftedReceiptsForCreate` | Always | `:192-198` |
| **⑫** | **Excess reason not entered** | Y | `fetchExceedReasonSettings` | When an active setting exists | `:200-206` |
| ⑬ | Payment-method restrictions for pre-settlement (future trip) receipts | Y | `fetchPreSettlementScopeSetting` | Always (when the setting is in use it replaces the hard-coded list) | `:208-217` |
| ⑬-1 | Backstop for pre-settlement disallowed purposes | N (reuses the ⑬ setting) | — | Only when `preSettlementUsed = true` | `:219-226` |
| ⑭ | SAP budget control | Y | `fetchExpenseReportPolicySetting` | **KSOE opt-in** — **skipped** when `features.budgetDeptBalance = true` | `:228-260` |
| ⑮ | Area mapping validation | N | — | Always | `:262-266` |
| ⑯ | Cancellation reason required on cancelled receipts | Y | `fetchCancelReceiptReasonPolicy` | When the setting is ON | `:268-271` |
| **⑰** | **Unhandled excess split blocking** | Y | `fetchExcessSplitSetting` | Only when `splitPopupUsed = true` | `:273-282` |

**Row count — measured 20 rows / 19 executed.**

> ⚠️ **The source's own description is stale.** Line `:6` says "of the 19 rows in the table below, ② is commented out (temporarily disabled), so 18 rules actually execute," but counting the table in the same file (`:11-31`) actually gives **20 rows**, and the code has **19** validation call sites (only ② is disabled).
> It appears the header number was not updated when ⑰ was added later.
> **The accurate values are 20 table rows / 19 executed.**

**Validations that depend on the ruled amount = 3** — ⑪, ⑫, and ⑰.
(⑧ claim amount of 0 looks only at `reqAmt`, so it is not ruled-amount dependent. ⑭ is on the budget-balance axis.)

### 6.5.3 Current state — ② temporarily disabled, ⑦ opt-in

| Item | State | Source |
|---|---|---|
| **② Region/country not selected** | **Temporarily disabled.** The 2 call lines are commented out and the `validateRegionSelection` import has been removed. The restoration method is stated in the comment | `:42`, `:112-115` |
| **⑦ Description required** | **Currently disabled entirely** (default `false`, an opt-in pattern). "Whether the description is required is to be controlled by a server setting in future. Until then, **skipped in all tenants.**" To re-enable, switch `features.bstrSummaryRequired = true` (or wire it to a setting) | `:137-139`, `:148` |

⑦ has a double gate — `features.bstrSummaryRequired` **AND** the form item `CLOUD_SUMMARY` with `used !== false` (`:140-151`). Legacy forms with no `receiptItems` keep the existing behavior via `every → true` (= used) (`:141`).

### 6.5.4 Composing the validation target set

`:87-110`.

```
baseReceipts        = ctx.receipts                        // user-registered receipts (a flat array)
paperSummaries      = paper.paperApprovalInfoSettingDto?.paperSummaries ?? []
mappedBaseReceipts  = baseReceipts if paperSummaries is empty
                      otherwise, groupReceiptsBySummary's result minus the unmapped ones
allReceipts         = [...mappedBaseReceipts, ...autoReceipts (type-converted)]
```

- **Unmappable receipts are excluded from validations ② through ⑭** — including them could raise an unintended error first, and since ⑮ checks all unmappable receipts it is harmless (`:92-97`).
- Only ⑮ looks at `baseReceipts` (everything including the unmapped) (`:263`, `:265`).
- Only ⑩-0 looks at `mappedBaseReceipts` (automatic receipts excluded) — a payment-type employee-dormitory ROOM automatic receipt is derived from the report date, so including it would give **a self-conflicting false positive the moment it is claimed** (`:178-180`).
- The automatic-receipt type conversion is consolidated into `toValidationReceipts` alone — per CLAUDE.md's ban on `as unknown as`, **the double-casting sites were gathered into one place** (`:58-67`).

### 6.5.5 The error shape — `CreateValidationError`

Definition: `PD/cloud-expense-report/utils/expense-report-validation-utils.ts:39-52` (re-exported by `validation-types.ts:26`).

```ts
interface CreateValidationError {
  message: string;              // shown to the user
  field?: string;               // the field where the error occurred — for focus and highlighting
  code?: string;                // for post-processing (redirects, etc.)
  detail?: string;              // additional detail (e.g. an amount breakdown)
  failedReceiptIds?: number[];  // UNMATCHED_RECEIPT_TO_SUMMARY only
}
```

The error-code constant `ValidationErrorCode` (`validation-types.ts:38-62`) has related entries: `ROOM_EXCEED`, `FOOD_EXCEED`, and `EXCESS_REASON_REQUIRED`.

> ⚠️ **The three validations ⑪, ⑫, and ⑰ do not actually set `code`** — they return only `message` and `field` (plus `detail` for ⑪) (`validate-total-exceed.ts:381`, `:391-395`, `:416-420`, `:432-437`; `validate-excess-reason.ts:295-298`; `validate-excess-split-required.ts:69-73`).
> Also, **there is no `ValidationErrorCode` entry corresponding to ⑰.** Consumers must branch on the `field` string, not on `code`.

---

### 6.5.6 ⑪ Lodging/meal total overrun — `validateTotalExceed`

`PD/cloud-expense-report/utils/validation/validate-total-exceed.ts:296-441`.
A port of the legacy `seahTotalExceedValidation` (`:20`).

#### Targets

**Only the two purposes lodging (`ROOM`) and meals (`FOOD`)** (`:308-311`). If both are absent, it returns `null` immediately (`:313`).

#### ★ Two exclusion axes — do not conflate them

`:7-10`, `:47-77`.

| Axis | The question | Excluded | Function |
|---|---|---|---|
| **Determination** | "Is this receipt **itself** blocked as over?" | `cardType === 'CORP'` **OR** a reservation-prepaid receipt | `isExcludedFromJudgment` (`:54-56`) |
| **Aggregation** | "Does this receipt's amount **consume the limit**?" | `cardType === 'CORP'` **OR** `isExcessOverRow` (an excess row) | `isExcludedFromAggregate` (`:75-77`) |

- Corporate-card exclusion: a legacy carry-over — the company paid directly (`:50`).
- Reservation-prepaid exclusion (determination only): payment is already complete in the reservation system and the user cannot adjust the amount, so **they cannot be held responsible for the excess** (`:51-52`).
- **Reservation points are included in the aggregate** — the legacy filtered only on `cardType !== CORP` and POINT was subject to aggregation. **Settled specification (2026-08-06)**: a reservation-prepaid receipt **is exempt from the determination on itself only, and still consumes the limit** (`:67-70`).
- Excess-row exclusion (aggregation only): it is self-pay (a receivable) and does not consume the company limit — leaving it in would block submission of another receipt for the same purpose as a total overrun because of an amount the person paid themselves.
  **The ruled-amount row, by contrast, stays in** — that amount was borne by the company, and removing it too would make an excess split **a bypass that resets the limit** (`:61-65`).

> ⚠️ **Binding the two axes under one predicate produces behavior where "reservation amounts do not consume the limit," diverging from the legacy** (`:72-73`).

#### The determination method

`:283-286`, `:376-438`. The order is **KRW ROOM → KRW FOOD → foreign ROOM → foreign FOOD.**

**(a) The KRW path** — `checkKrwTotalExceedForType` (`:246-264`) → `checkTotalExceed` (`:90-141`)

```
If even 1 foreign-currency receipt is among the candidates
(the new aggregation set + already-drafted + other-report running totals) → null
  (preventing a mismatch with the limitAmounts foreign unit — :241-242, :259)

Iterating over the determination targets (newReceipts):
  allSameType  = [already-drafted, other-report totals, new aggregation set] with the same tranKindType
  totalReqAmt  = Σ reqAmt   (approvalCanceled is deducted — :109-112)

  If ACTUAL-like:                                              // :114-121
    totalApproval = Σ approvalAmount
    totalReqAmt > totalApproval  → over { totalRuled: totalApproval }
    (otherwise continue to the next receipt)

  limits = limitAmounts[receipt.tranKindId]
  if limits is absent, continue                                 // :123-124
  totalRuled = Object.keys(limits).length > 0
                 ? Σ the limits values
                 : Σ allSameType.ruledAmount (cancellations deducted)  // fallback :126-134
  totalReqAmt > totalRuled  → over
```

- **Why the empty-limit-map fallback exists** (`:126-128`): if the limit API returns an empty map because, say, the grade zone (`BSTR_PERIOD` selections) was not chosen, the ruled total cannot be treated as 0 (treating it as 0 would conflict with the grid receipts' `ruledAmount` → **a false overrun**). It falls back to the sum of the `ruledAmount` values `rule-update` already wrote onto the receipts, matching the grid's displayed values.

**(b) The foreign-currency path** — `checkForeignTotalExceedByGroup` (`:158-222`)

```
sameKind      = candidates with the same tranKindType
foreignGroup  = those in sameKind that are isForeignReceipt ∧ have a matching currencyCode
if foreignGroup is empty → null

hasActual (any ACTUAL-like in sameKind)?                        // :170-181
  → the KRW pattern: Σ reqAmt vs Σ approvalAmount  (isActual: true, unit KRW)

isGroupForeign (the group shares one currency)?                 // :183-192
  → foreign aggregation: Σ foreignAmount vs Σ overseasRuledAmount
    (null if totalRuled ≤ 0)

Otherwise (foreign+KRW mixed / multiple currencies) → "unified KRW comparison"   // :194-221
  totalReqAmt = Σ reqAmt
  totalRuled  = Σ each row's contribution:
      Rows passing the foreign cap (isCompareInForeign ∧ foreign ≤ overseasRuled)
        → effectiveKrwRuledAtReceiptRate(r)   (recomputed at the receipt's rate)
      Otherwise → r.ruledAmount
  totalRuled > 0 ∧ totalReqAmt > totalRuled → over (currencyCode: 'KRW')
```

- **The KRW→foreign back-conversion logic was fully abandoned** (`:154`). When group uniformity is not met, comparison is in KRW.
- **Why the receipt-rate recomputation exists** (`:199-202`): it removes the problem where a rate difference between `ruledAmount` (KRW converted at the rule rate) and `reqAmt` (KRW at the receipt rate) produced **a phantom KRW overrun.** When the foreign cap is exceeded, `ruledAmount` is kept (a real overrun).
- `effectiveKrwRuledAtReceiptRate` (`PD/bstr-policy/utils/foreign-currency-utils.ts:190-216`): first the receipt's `exchangeRate`, second a back-derivation from `approvalAmount / foreignAmount`. 100-unit currencies (JPY/IDR/VND) get a `/100` correction. If back-derivation is impossible it returns `null` → the caller falls back to `ruledAmount`.
- **The foreign path also has a determination-axis gate** (`:407-414`, `:428-431`): if the determination targets (`roomReceipts` / `foodReceipts`) number 0, the foreign check is **skipped.** The KRW path iterates over `newReceipts` and so passes automatically, but `checkForeignTotalExceedByGroup` compares only group totals and has no iteration concept.
  Without the gate, **"a type containing only reservation-prepaid receipts" would block submission on its own excess.**
- `isGroupForeign` (`foreign-currency-utils.ts:160-172`): `true` only when the target satisfies the foreign-comparison entry conditions **and every member of the group shares the same foreign currency.** **An empty group is also `false`** — defending against the JS trap where `[].every()` is vacuously true (`:154-156`).

#### APIs called

| API | Purpose | Source |
|---|---|---|
| `GET /api/v2/approval/bstr/plan/{planApprovalId}/pre-receipts/form` | Looks up **already-drafted (previously submitted) receipts.** Called twice, with and without the `corpUserId` parameter, for the companion-inclusive and own-share sets | `:289`, `:320-323`; `api/validation-api.ts:56-62` |
| `POST /api/v2/bstr/policy/renewal/limit` (multiple, in parallel) | Looks up **per-purpose daily limits** → uses `byTranKind` | `:290`, `:358-371`; `bstr-policy/api/renewal-limit-api.ts:22`; `bstr-policy/utils/ruled-amount-calculator.ts:379-434` |

- If the already-drafted load fails, it **proceeds with an empty array** (`:324-326`). If the limit lookup fails, **validation is skipped** (`:372-374` — `limitAmounts = {}` is retained).
- The running-total mapping differs by purpose (`:285-286`, `:376-387`, `:399-401`):

| Purpose | Already drafted | Other-report running total |
|---|---|---|
| **ROOM** | `preDraftedPersonal` (own share) | `priorSettledReceipts` (own share) |
| **FOOD** | `preDraftedWithCompanion` (companions included) | `priorSettledReceiptsWithCompanion` |

- The limit lookup axis (`:329-343`): `TranKindLimitTarget { id, type, foodDivisionType }`.
  **It carries the meal classification (breakfast/lunch/dinner) along with it** — if not sent, the backend skips the filter and **one of the meal rows is picked arbitrarily, making the limit differ from reality** (`:331-332`).
- **⑪ uses `byTranKind` (per-date aggregation = the day's total limit).** The per-meal limit (`byDivision`) is ⑫-only — "using it here would shrink the day's total limit down to a single meal's limit and **block a perfectly normal expense report**" (`:353-355`, `ruled-amount-calculator.ts:348-357`).

#### Error messages

| Path | `field` | `message` | `detail` |
|---|---|---|---|
| ROOM KRW (`:380-382`) | `roomExceed` | `숙박비 총금액이 허용된 한도를 초과했습니다.` (The total lodging amount exceeds the allowed limit.) | — |
| FOOD KRW (`:389-396`) | `foodExceed` | `규정금액(총액) 초과로 결재할 수 없습니다. 신청금액을 규정금액(총액) 이내로 수정해 주세요.` (Cannot be approved because the ruled amount (total) is exceeded. Please adjust the claim amount to within the ruled amount (total).) | `식비 규정금액(총액) : {n}원\n식비 신청금액(총액) : {n}원\n초과 금액 : {n}원` |
| ROOM foreign (`:415-421`) | `roomExceed` | `숙박비 규정금액(총액) 초과로 결재할 수 없습니다.` (Cannot be approved because the lodging ruled amount (total) is exceeded.) | `formatForeignExceedDetail('숙박비', …)` |
| FOOD foreign (`:432-438`) | `foodExceed` | (Same as FOOD KRW) | `formatForeignExceedDetail('식비', …)` |

`formatForeignExceedDetail` (`:225-236`):
- `isActual: true` (bypassing the foreign cap, in KRW) → `{prefix} 규정금액(총액) : {n}원\n{prefix} 신청금액(총액) : {n}원\n초과 금액 : {n}원`
- Foreign → `{prefix} 규정금액(총액) : {currency} {n}\n{prefix} 신청금액(총액) : {currency} {n}\n초과 금액 : {currency} {n}`

---

### 6.5.7 ⑫ Excess reason not entered — `validateExcessReasonRequired`

`PD/cloud-expense-report/utils/validation/validate-excess-reason.ts:228-302`.

#### Firing conditions (a 3-stage gate)

```
1. Settings lookup succeeds ∧ settings.length > 0                 // :237-244
2. activeSettings = settings.filter(s => s.activated); length > 0 // :246-247
3. The per-receipt iteration gate:                                // :265-283
     !receipt.tranKindId                       → skip
     isReservationPrepaidReceipt(receipt)      → skip  (reservation prepaid)
     isExcessOverRow(receipt)                  → skip  (an excess row)
     no tranKindId match in activeSettings     → skip
4. checkReceiptExcess(...) === true  ∧  !receipt.excessReason  → error  // :285-299
```

- Settings lookup failure → `return null` (validation skipped, `:240-242`).
- **Why reservation-prepaid is skipped** (`:267-270`): payment is already complete in the reservation system and the user cannot adjust the amount, so **they cannot be held responsible for the excess.** The on-screen determination (`isExcessBySetting`) must be exempted by the same predicate — exempting only one side creates an asymmetry.
- **Why excess rows are skipped** (`:272-281`): they are self-pay (a receivable), unrelated to the company limit, and are already out of the aggregation set, so leaving only the determination would misalign the axes.
  **The ruled-amount row is a determination target (requirement settled 2026-09-02)** — if the daily aggregate exceeds the limit, the receipts that participated in that day's aggregation are still required to give an excess reason, split or not.

> ⚠️ **The original defect (being asked again for an excess reason after merely splitting) is blocked by the aggregation axis, not by this gate** — removing the excess portion from the aggregate makes the ruled-amount row's standalone total equal the ruled amount, so no excess arises.
> Therefore **do not widen this gate to include the ruled-amount row**: when another receipt exceeds the limit, only the split rows would quietly drop out, creating a **hole where the day is over but nobody writes a reason** (`:276-279`).

#### The `excessReason` field

- Determination: `isExcess && !receipt.excessReason` (`:294`). An empty string, `null`, and `undefined` all count as "not entered" (a falsy test).
- **The §0 sample had `excessReason: ""`** — if an excess holds, it is blocked as-is.

#### The excess criterion — `checkReceiptExcess`

`:78-213`. Branches on the payment method (`bstrPayClassType`) and the limit type (`bstrLimitType`) (`:7-11`).

| Branch | Condition | Excess formula | Line |
|---|---|---|---|
| **ACTUAL** | `isActualLikePayClass` | `approvalAmount < reqAmt` (the same for foreign and KRW) | `:104-106` |
| **ONE_DAY / FOOD** | `bstrLimitType='ONE_DAY'` ∧ `tranKindType='FOOD'` | Same-date, same-group aggregate vs. the limit | `:117-138` |
| **ONE_DAY / non-FOOD** | `bstrLimitType='ONE_DAY'` | Foreign: `overseasRuled > 0 ∧ foreignAmount > overseasRuled`<br>KRW: `ruledAmount < reqAmt` | `:140-145` |
| **ALL** | `bstrLimitType='ALL'` | An aggregate comparison against the **total** of the per-day ruled amounts across grade zones and companions | `:148-210` |
| Otherwise | — | `false` | `:212` |

**ONE_DAY / FOOD in detail** (`:117-138`):
```
useDate          = (useDate ?? usedStartDate).slice(0,10)
combinedForFood  = [...allReceipts, ...priorSettledReceiptsWithCompanion]   // companion-inclusive totals
sameDaySameType  = from combinedForFood, those where
                     tranKindType matches
                   ∧ isSameFoodDivisionGroup(r, receipt, usesFoodDivision)
                   ∧ !isExcessOverRow(r)                      // aggregation exclusion
                   ∧ the same date
foreign ∧ isGroupForeign  → Σ foreignAmount > Σ overseasRuledAmount  (when Σruled > 0)
otherwise                 → Σ reqAmt > (ruledAmount ?? 0)
```
On the KRW path it **uses `receipt.ruledAmount` (the receipt's own value) as the limit** — for a per-meal determination that value is already that meal's limit, so it becomes a "lunch total vs. lunch limit" comparison (`:121-122`).

**ALL in detail** (`:148-210`):
```
false if there is no tranKindId / tranKindType                  // :150
false if there is no bstrStartDate / bstrEndDate                // :151
allowDays = calculateAllowDays(issuedItems, tranKindId, null, fetchAllowBeyondDays)  // :154-159
daily     = fetchDailyRuledAmount(ctx, allowDays,
              { tranKindType, tranKindId, vehicleType, foodDivisionType })  // :160-183
combinedForAll = [...allReceipts, ...priorSettledReceipts]      // own-share totals
sameType       = tranKindType matches ∧ the same meal group ∧ !isExcessOverRow
foreign ∧ isGroupForeign → Σ foreignAmount > Σ overseasRuledAmount
otherwise                → Σ reqAmt > Σ(the daily values)
wrapped in try/catch; false on failure                          // :153, :207-209
```
**`foodDivisionType` must be included for that meal's rule row to match** — if not sent, the backend skips the filter, one of breakfast/lunch/dinner is picked arbitrarily, and the limit differs from reality (`:179-181`).

**The running-total basis matches ⑪** (`:67-70`, `:222-224`):

| Branch | Running-total source |
|---|---|
| FOOD ONE_DAY | `priorSettledReceiptsWithCompanion` (companions included) |
| ALL | `priorSettledReceipts` (own share) |

**Whether the determination is per meal, `usesFoodDivision`** (`:251-257`): decided by **whether meals are actually registered in the active rule.** It uses the **same lookup and the same determination function** as the screen (`useExceedReasonData → isExcessBySetting`) — `fetchFoodDivisionTypes` → `hasRegisteredFoodDivision` — because "splitting by meal on only one side creates an asymmetry where **the screen demands a reason but submission does not validate it.**" A lookup failure falls back to `false` (aggregating the whole purpose) so as not to block submission.

`isSameFoodDivisionGroup` (`exceed-check-utils.ts:33-41`): when `usesFoodDivision` is `false`, everything is the same group. Splitting for a company that does not use meal classifications would produce "40,000 for breakfast plus 40,000 for lunch, **over in total but within the limit individually**," which is looser than before (a regression) (`:29-31`).

#### The aggregation-set exclusion axis

`isExcludedFromAggregate(receipt) = isExcessOverRow(receipt)` (`:56-58`).
**Only excess rows** are removed. The same criterion as ⑪ and the screen's `exceed-check-utils` — **the three must not diverge** (`:48-50`).

> ⚠️ **The determination axis (the iteration gate) and the aggregation axis have the same scope** — both are `isExcessOverRow`.
> What separates the two axes is not their scope but their **question**: the determination asks "is this receipt itself blocked," and the aggregation asks "does this amount consume the limit." **The ruled-amount row stays in both** (`:52-54`).

#### APIs called

| API | Purpose | Source |
|---|---|---|
| `GET /api/v2/bstr/expense-exceed-reason` | **The excess-reason settings list** | `api/validation-api.ts:143-145`; JSDoc `:19` (which says `/api/v3/...`, but the code uses v2) |
| `fetchFoodDivisionTypes(bstrType)` | Whether meals are registered | `:255` |
| `fetchAllowBeyondDays` (the ALL branch) | Allowed days | `:158` |
| `POST /api/v2/bstr/policy/renewal/limit` (the ALL branch, via `fetchDailyRuledAmount`) | Per-day ruled amounts | `:160-183` |

`ExceedReasonSettingDto` (`api/validation-api.ts:148-159`):

| Field | Type | Note |
|---|---|---|
| `id` | `number` | |
| `tranKindId` | `number` | The matching key (`:282`) |
| `tranKindCd` | `string?` | The purpose code |
| `tranKindName` | `string?` | The purpose name (e.g. `"숙박비"`) |
| `tranKindType` | `string` | **Not returned by the API** — enriched from `allTranKinds` in `useExceedReasonData` |
| `bstrLimitType` | `string \| null` | `ONE_DAY` \| `ALL` (any other value makes the determination `false`) |
| `activated` | `boolean` | **The active-filter condition** (`:246`) |

> ⚠️ The endpoint in the JSDoc (`:19`) differs from the code —
> the comment says `GET /api/v3/cloud-expense-report/exceed-reason-settings` while the actual call is `GET /api/v2/bstr/expense-exceed-reason` (`validation-api.ts:144`).
> **The code is the source of truth.**

#### The error message

```
{ message: '초과사유를 입력해주세요.', field: 'excessReason' }
```
("Please enter an excess reason.") `:294-299`. No `code` or `detail`. **Fail-fast — it returns immediately on the first violating receipt** (`:220`, `:295`). Which receipt it was is not conveyed in the error object.

---

### 6.5.8 ⑰ Unhandled excess split blocking — `validateExcessSplitRequired`

`PD/cloud-expense-report/utils/validation/validate-excess-split-required.ts:58-74`.

#### Firing conditions

**It runs only when the setting is ON.** The calling pipeline gates it (`:5`, `validate-draft-pipeline.ts:278-282`):

```
const excessSplitSetting = await fetchExcessSplitSetting();
if (excessSplitSetting.splitPopupUsed) {
  const excessSplitErr = validateExcessSplitRequired(allReceipts);
  if (excessSplitErr) return excessSplitErr;
}
```

- **When the setting is OFF or the lookup fails, it passes immediately** (preserving existing behavior). `fetchExcessSplitSetting` returns a `splitPopupUsed = false` fallback on failure, so there is no separate `catch` (`validate-draft-pipeline.ts:274-275`).

#### The settings API

| Item | Value |
|---|---|
| Endpoint | `GET /api/v2/business-setting/etc/BSTR/excessDebitSplit` |
| Source | `PD/cloud-expense-report/api/policy-setting-api.ts:257-268` |
| Admin screen | Settings > Business trip > Other settings > expense report "excess-amount split setting" (**the same source**) |
| Authorization | Authentication only (no role restriction or `@PreAuthorize`) → an expense-report author can also read it |
| Response validation | The zod `ExcessSplitSettingSchema` (`types/excess-split-setting.ts:65-72`) |
| Fallback | `EXCESS_SPLIT_SETTING_FALLBACK = { splitPopupUsed: false, allowedAccounts: [], evidenceAccounts: [] }` (`:83-87`) |

Schema fields (`types/excess-split-setting.ts:65-72`):

| Field | Meaning |
|---|---|
| `splitPopupUsed: boolean` | Whether the split-method selection popover is shown. When `false` or unset, the cost-center split runs directly |
| `allowedAccounts[]` | The whitelist of pre-designated debit account subjects — **the only source of the drawer's selection candidates** |
| `evidenceAccounts[]` | Per-receipt-type usage and designated account subjects — the source for automatic/manual mapping determination |

Why `splitPopupUsed: false` is the safe default (`:79-82`): showing the new UI while the setting could not be read would **strand the user on a screen where they cannot pick a debit account subject.**
Error notification is delegated to the interceptor — no toast is added here (to prevent duplicate notifications, `policy-setting-api.ts:255`).

#### The determination

```
unsplit = receipts.filter(r =>
            canSplitExcessOnScreen(r) ∧ hasExcessAmount(getOverAmountDisplayValue(r))
          );
unsplit.length === 0 → null
otherwise → error
```
`:61-73`.

**The excess check uses the same functions as the on-screen excess-amount column** (`getOverAmountDisplayValue` + `hasExcessAmount`) — "determining it on a different basis creates a state where **the column shows no excess but submission is blocked**, and the user cannot find the cause" (`:7-9`).

#### Exclusions — receipts that cannot be split on screen

`canSplitExcessOnScreen` (`:44-50`). "Blocking even receipts whose split icon is disabled leaves **a dead end with no way for the user to resolve it**" (`:13`). Each condition is 1:1 with the actual UI gate in `ReceiptRowActions` (`:14`).

| Exclusion | Condition | Rationale (the UI gate) | Code |
|---|---|---|---|
| Already excess-split | `divisionType === 'EXCESS'` | The split is complete | `:45` |
| Ratio split | `divisionType === 'BASIC'` | Manual splitting and reset are blocked (auto-sync only) | `:46` |
| Automatic receipt | `documentBound === true` | The split button is `disabled` | `:47` |
| Reservation-prepaid receipt | `isReservationReceipt(receipt)` | The split, attach, and delete icons are hidden | `:48` |

`isReservationReceipt` = `additionalReceiptType ∈ {ACCOM, FLIGHT, TRANS}` (`PD/cloud-expense-report/features/receipt-reservation.ts:10-15`).
It **differs from** `isReservationPrepaidReceipt` (which ⑪ and ⑫ use) — ⑰ excludes all reservation receipts with no payment-method condition.

> **Split child rows (`divisionOrder !== 0`) are not excluded (requirement settled 2026-08-26)** (`:22-25`).
> A cost-center split child can also open an excess split when its own share exceeds the rule, so a means of resolution exists.
> The previous rationale for excluding them was "the action icons are hidden so it cannot be resolved," and **that premise has changed.**
> Without opening it, the screen shows an excess amount while submission passes.

#### The error message

```
{
  message: '규정금액을 초과한 증빙이 있습니다. 증빙 관리의 [분할]에서 초과금액 분할을 완료해 주세요.',
  field: 'excessSplit'
}
```
("There is a receipt exceeding the ruled amount. Please complete the excess-amount split under [Split] in receipt management.")
`:69-73`. No `code` or `detail`. **No** corresponding constant in `ValidationErrorCode`.

---

## 6.6 The validation checklist — for an external system to run itself after building the requestBody

**How to use it**: run it in order right after assembling the requestBody, before sending. Because the pipeline is fail-fast, the server response tells you only one thing at a time, so **catching everything here is the only efficient approach.**

`⓿` = precondition, `❶` onward = items. The target arrays are the three from §0 and §4: `bstrReceipts[]`, `issuedFields[].issuedReceiptDtos[]`, and `etcReceiptSaveRequests[]`.

### C-0 Preconditions — check before filling in values

| # | What to check | Passing condition | What to fill in on a violation |
|---|---|---|---|
| ⓿-1 | Is the `bstrPayClassType` value a real enum member? | `∈ {FIXED, ACTUAL, ACTUAL_FIXED, LIMITED, FUEL, TOLL, NONE}` or an intentional `null` | `DAILY` and `TOTAL` are **values that do not exist** (§6.3.1). Carry the rule lookup response's `RenewalLimitResponse.bstrPayClassType` as-is. ⚠️ **The front end and backend differ** — the front-end zod is `z.string()` so a wrong value passes and silently flows to the `default` path (miscellaneous receipts `ETC`→`MIN(ruled,approved)`, otherwise `Infinity`), but **the backend receives this field as a Java enum** (`EtcReceiptSaveRequest.java:145`, `bstr/dto/BstrReceiptDto.java:306`) → **it can be rejected with a deserialization 400** (not verified with a real request). **Use only the 7 values or `null`** (§6.3.2 rows 7 and 7.5) |
| ⓿-2 | Is `ruledAmount` **a calculated result**? | The rule lookup's `limitAmounts` (the base amount) → the value after per-day condition application | Do not copy the approved amount (the §0 case), hard-code `0`, or leave `null`. Check §6.1.5 for the outcome of each state |
| ⓿-3 | The `ruledAmount` currency | **KRW** | Do not put the original foreign amount in. The original foreign amount goes separately into `overseasRuledAmount` (§6.1.4) |
| ⓿-4 | `overseasRuledAmount` | `> 0` if there is a foreign rule, otherwise `0`/`null` | `0` is interpreted as "no foreign limit defined" and turns off the foreign-comparison path (`foreign-currency-utils.ts:110-113`) |
| ⓿-5 | `ruledAmount` consistency across the 3 arrays | The same value in all three for the same receipt | `bstrReceipts[]` and `issuedFields[].issuedReceiptDtos[]` have different shapes (§0, §4) — filling only one leaves them inconsistent |

### C-1 The excess-amount axis

| # | What to check | Passing condition | What to fill in on a violation |
|---|---|---|---|
| ❶-1 | Deriving the used amount | `DAILY_COST` → `supplyAmount + vatAmount` ≠ 0<br>`BASIC` child → `supplyAmount` present<br>Otherwise → `approvalAmount` ≠ 0 | For per diem, `approvalAmount` may be 0, but `supplyAmount` and `vatAmount` must be present (§6.1.3) |
| ❶-2 | Computing the excess itself | For each receipt, compute and retain `MAX(0, usedAmount − ruledAmount)` yourself | If this value is **0 on every receipt**, revisit ⓿-2 — it is a signal that the ruled amount equals the approved amount |
| ❶-3 | The sign on cancellation receipts | **Do not enter negative amounts** on receipts with `approvalCanceled = true` | Enter absolute amounts. The front end handles deduction when summing (§6.1.6) |

### C-2 The claim-amount axis

| # | What to check | Passing condition | What to fill in on a violation |
|---|---|---|---|
| ❷-1 | The `reqAmt` cap on unsplit receipts | `reqAmt ≤ getNonDivisionMax(row)` — evaluate the §6.3.2 table **in application order** | Clamp to the cap. `CORP`→approved, `FOOD`→approved, foreign→conditional, `ACTUAL/ACTUAL_FIXED`→approved, `LIMITED`→`MIN`, `FIXED`/`FUEL`→ruled, `ETC` default→`MIN`, otherwise→unlimited |
| ❷-2 | Split receipts, **individual lines** | `reqAmt ≤ supplyAmount` | Bring each line's `reqAmt` to at most its own `supplyAmount` (§6.3.3 ①) |
| ❷-3 | Split receipts, **the total** (excluding `CORP`) | `Σ reqAmt ≤ getDivisionTotalMax(row)` for the same `receiptId` | Compute the per-payment-option cap from the §6.3.3 ② table. **A `CORP` split has no total limit** |
| ❷-4 | The claim-amount distribution on an excess-split receipt | Ruled-amount row `= MIN(used, ruled)`, excess row `= MAX(0, used − ruled)`, **total = the original used amount** | §6.2.8. The used amount and ruled amount are **not divided** (the original is kept) |
| ❷-5 | VAT on an excess split | **All on the first row**, 0 on the excess row. 0 if non-deductible (`nonDeduction`). The first row's VAT ≤ the first row's claim amount | §6.2.8 |
| ❷-6 | A `reqAmt` of 0 (⑧) | **Two stages** — ① **every receipt has `reqAmt > 0`** (a single 0 is an immediate error, `validate-sync-rules.ts:184-190`) ② the cancellation-adjusted total is `> 0` (`:192-200`). A document settling only an advance payment (prepaid per diem) may legitimately be 0 | Checked only when the claim-amount setting `requestedAmountUsed = true` (`validate-draft-pipeline.ts:160-164`) |

### C-3 The split axis

| # | What to check | Passing condition | What to fill in on a violation |
|---|---|---|---|
| ❸-1 | The `divisionType` value | `∈ {USER, BASIC, EXCESS}` or `null` | The type comment (`receipt-types.ts:152`) omits `EXCESS` but it is a real value (§6.2.1) |
| ❸-2 | The `excessOver` marker on `EXCESS` rows | Ruled-amount row `excessOver = false`, excess row `excessOver = true` | **Leaving it `null` takes the `divisionOrder !== 0` order fallback**, and when it coexists with a cost-center split it **mistakes the ruled-amount row for the excess row** (§6.2.7) |
| ❸-3 | The `divisionOrder` on `EXCESS` rows | **Do not give the excess row `order 0`** (an `EXCESS` row at `order 0` is always the ruled-amount row). ⚠️ The converse does not hold — re-splitting a cost-center child puts the EXCESS pair at `order n` and `n+1` while `order 0` is held by the VAT-carrying sibling (`build-excess-split-payload.ts:260`, `:274-276`) | The backend list query passes only the single row matching `EXCESS ∧ divisionOrder = 0` (`build-excess-split-payload.ts:42-43`) |
| ❸-4 | `corporationUser` on `EXCESS` rows | `null` | A condition for passing the backend list gate (`build-excess-split-payload.ts:44-45`) |
| ❸-5 | `dividedRuledAmount` on `BASIC` children | Fill in the own-share distributed value | **Without it**, the excess determination is turned off (`divisionOrder ≠ 0` → not possible) or falls back to the full original, **overstating the excess** (§6.2.3) |
| ❸-6 | `dividedParentVatAmount` on a split's first child | Carry the preserved original VAT | The direct `vatAmount` is 0, so without it the first child's own share and claim amount are off by the VAT (§6.1.3) |
| ❸-7 | Re-sending sibling split rows | Include **all existing sibling rows** in the excess-split payload | The backend's `divide` deactivates all existing children, so omitting them **loses even the cost-center split's budget department and assignees** (§6.2.8) |
| ❸-8 | Duplicate limit consumption on a `USER` split | (Not a check item — **intended behavior**) | Splitting used 86,000 / ruled 60,000 in half puts both rows within the rule. **Do not report it as a bug** (§6.2.4) |

### C-4 The 3 submission blockers (⑪, ⑫, ⑰)

| # | What to check | Passing condition | What to fill in on a violation |
|---|---|---|---|
| ❹-1 (⑪ ROOM) | The sum of `ROOM` receipt claim amounts | The aggregate **excluding only `CORP` and excess rows** is within the ruled total<br>⚠️ **Reservation-prepaid stays in the aggregate** — it is excluded only from the determination (settled specification 2026-08-06, `validate-total-exceed.ts:67-77`). Binding the two axes under one predicate makes reservation amounts not consume the limit, diverging from the legacy<br>For ACTUAL-like, `Σ reqAmt ≤ Σ approvalAmount` (**this total does not deduct cancellations** — `:116`) | Lower `reqAmt` to within the total. **The running-total scope**: already-drafted (own share) plus other expense reports under the same plan (own share) are also aggregated (§6.5.6) |
| ❹-2 (⑪ FOOD) | The sum of `FOOD` receipt claim amounts | The same, except the running totals are **companion-inclusive** | The same as above |
| ❹-3 (⑪ foreign) | Foreign-group aggregation | Same-currency group: `Σ foreignAmount ≤ Σ overseasRuledAmount`<br>Mixed: `Σ reqAmt ≤ Σ (the KRW limit at the receipt rate)` | Unify the group's currency or align on a KRW basis (§6.5.6 (b)) |
| ❹-4 (⑫) | `excessReason` on over-limit receipts | If a receipt whose `tranKindId` has an `activated` excess-reason setting is over, **`excessReason` must be non-empty** | Fill in the reason string. `""` counts as not entered. Reservation-prepaid receipts and excess rows are exempt (§6.5.7) |
| ❹-5 (⑫ formula) | Reproduce the excess determination yourself | **KRW** — ACTUAL: `approvalAmount < reqAmt` / ONE_DAY FOOD: same-date, same-meal `Σ reqAmt > ruledAmount` (**companion-inclusive totals**, `:120`; **excess rows excluded**, `:127`) / ONE_DAY non-FOOD: `ruledAmount < reqAmt` / ALL: `Σ reqAmt > Σ the per-day rules`<br>⚠️ **The foreign formulas differ** — ONE_DAY non-FOOD: `overseasRuled > 0 ∧ foreignAmount > overseasRuled` (`:140-144`); FOOD and ALL: original-currency group aggregation (`:131-135`, `:198-202`). Using only the KRW formulas will always diverge on foreign-currency receipts | Check each receipt's `bstrLimitType` in the settings and test with the corresponding formula (§6.5.7) |
| ❹-6 (⑫ meals) | `foodDivisionType` on `FOOD` | Fill it in for a corporation that uses per-meal rules | If not sent, the backend skips the meal filter and determines against **an arbitrary meal's limit** (`validate-excess-reason.ts:179-181`) |
| ❹-7 (⑰) | Unsplit over-limit receipts | When the excess-split setting (`splitPopupUsed`) is ON, no **splittable receipt** may still carry an excess amount | Separate the excess into 2 `EXCESS` rows (§6.2.8). The exclusions are `EXCESS`, `BASIC`, `documentBound=true`, and reservation receipts (§6.5.8) |
| ❹-8 (⑰ setting check) | The setting state | The `splitPopupUsed` value from `GET /api/v2/business-setting/etc/BSTR/excessDebitSplit` | When `false`, ⑰ does not run at all. **The moment the setting is turned on your existing requestBody may be blocked**, so prepare in advance |

### C-5 Other things that block submission (outside the ruled-amount axis — for reference)

Because it is fail-fast, if these trigger first you never reach ⑪, ⑫, or ⑰.
See the §6.5.2 table for the numbers and gates.

②-1 internal-order ratios total 100%, ②-2 WBS ratios total 100%, ④ trip-purpose restrictions, ⑤ duplicate per-diem dates, ⑥ usage-date range, ⑧ claim amount of 0, ⑧-1 posting date required, ⑨ receipt date > posting date, ⑩-0 employee-dormitory report-date overlap, ⑩ lodging date outside the period / duplicated, ⑬ pre-settlement payment methods, ⑬-1 pre-settlement disallowed purposes, ⑭ SAP budget control, ⑮ area mapping, ⑯ cancellation reason required.

② region/country not selected is **currently disabled** and is not checked (§6.5.3).

---

## Appendix A-0 — Suspected-violation tags (`complianceTypesStr`) are also on the ruled-amount axis

They sit outside the pipeline but **depend on the ruled amount and are a derived field the front end calculates and saves.**
If you assemble the requestBody directly these tags will be missing or wrong — **submission is not blocked, but the administrator's reading of the result changes.**

| Item | Formula | Axis | Source |
|---|---|---|---|
| ⑦ Rule overrun | `ruledAmount != null ∧ (reqAmt ?? 0) > ruledAmount` | **Claim amount** | `compliance-attach.ts:215` |
| ⑧ Corporate-card overrun | `getExcessJudgmentAmount(r) > (dividedRuledAmount ?? ruledAmount)` | **Used amount** | `compliance-attach.ts:225-233` |

> ★ **The axes diverge even within the same tag set** — this is the third case of the "two determination axes" from §6.1.
> If `ruledAmount` is `null`, ⑦ is not attached (the same criterion as §6.1.5).
>
> ⚠️ The numbers the source uses for these two items differ by file — `over-amount.ts:66` says "⑦ rule overrun" while `receipt-used-amount.ts:64-65` says "⑧ corporate-card overrun." The source-of-truth ordering is `compliance-attach.ts:3`.

## Appendix A — The "not a bug (intended)" list

Items recorded in the source as "requirement settled," "an accepted cost," or "settled specification." **An external reader must not mistake them for defects.**

| # | Behavior | Nature | Source |
|---|---|---|---|
| A-1 | On a foreign-currency receipt the ruled amount is `USD 80` while the excess is `28,000` — mixed currencies make the difference between the two columns impossible to reconcile | **A cost explicitly accepted when the requirement was settled** (the fixed KRW figure takes priority). Changing the ruled-amount column to KRW as well was rejected because it would remove the means of comparing against the original rule | `over-amount.ts:30-33` |
| A-2 | A `USER` split consumes the limit once per row — splitting used 86,000 / ruled 60,000 in half makes the group's excess of 26,000 disappear | **A cost accepted when the requirement was settled** (the per-person limit interpretation takes priority) | `over-amount.ts:56-58` |
| A-3 | Even after the split, the ruled-amount row keeps showing an excess of 26,000 | **Requirement changed 2026-09-01 (Flow 84317778).** The previous `0` display was perceived as "the change was not applied" | `over-amount.ts:70-86` |
| A-4 | A ruled-amount row from re-splitting a cost-center child (`order ≠ 0`) shows an excess of `0` | **An intended exception** — the pre-split own share is not left on the row, so it cannot be derived | `over-amount.ts:88-89` |
| A-5 | A reservation-points receipt **consumes the limit but is not itself blocked as over** | **Settled specification 2026-08-06.** The legacy filtered only on `cardType !== CORP` and POINT was subject to aggregation | `validate-total-exceed.ts:67-70`, `validation-types.ts:104-105` |
| A-6 | The **ruled-amount row of an excess-split receipt is still subject to the excess-reason determination** | **Requirement settled 2026-09-02.** Widening the gate to the ruled-amount row would create a hole where "the day is over but nobody writes a reason" | `validate-excess-reason.ts:15-17`, `:272-281` |
| A-7 | A future trip with 1 per-diem receipt cannot be submitted (⑬) | **Settled specification** (when the pre-settlement allowed-scope setting is not in use). The automatic receipt has `cardType='ETC'`, outside the whitelist `{CORP}` | `validate-draft-pipeline.ts:208-211` |
| A-8 | ⑭ SAP budget control is **skipped** at KSOE | **An intended skip.** KSOE's balance is a single document-level value, so a per-group comparison would be a false negative (fail-open). The determination is handled downstream by `checkBudgetInsufficient` | `validate-draft-pipeline.ts:228-236` |

> ⚠️ **The next two are not "intended" but "temporary states" — if enabled, an external requestBody may be blocked.**
> The other items in this appendix are recorded in the source as "requirement settled / an accepted cost / settled specification / confirmed by planning," but these two are **debt scheduled for restoration.** Treat them the same way as the warning that ⑰ "may block the moment the setting is turned on."

| # | Item | Current state | If enabled | Source |
|---|---|---|---|---|
| T-1 | ⑦ Description required | Skipped in all tenants (default `false`, opt-in) | A receipt with no description (`summary`) is blocked at submission | `validate-draft-pipeline.ts:137-139` |
| T-2 | ② Region/country not selected | **Temporarily disabled** — the call is commented out with `TODO(temporary)` stating the restoration method | A document whose trip-period item has no country/region selection is blocked entirely | `validate-draft-pipeline.ts:42`, `:112-115` |
| A-11 | In the on-screen per-day determination, if the limit map has no key for the usage date it falls back to the receipt's `ruledAmount` | **An intended fallback.** If only the on-screen determination turned off for a usage date outside the trip period, you would get a deadlock where "submission demands an excess reason but the screen has no input field." Note it **does not become exactly the same axis as ⑫** | `exceed-check-utils.ts:134-142` |
| A-12 | `BZP_MONEY` (BeePle Money) is **not** subject to the reservation-prepaid exemption | **Confirmed by planning.** Do not reuse `POINT_CARD_TYPES` | `reservation-prepaid-receipt.ts:37-39` |

### Tenant-specific branches (differing from the default BZP behavior)

| Branch | Tenant | Effect | Source |
|---|---|---|---|
| `features.budgetDeptBalance` | **KSOE** opt-in (`expense-report.budgetDeptBalance`) | `true` **skips** ⑭. KSOE currently measures `sapBudgetControl=false`, so there is no behavior change | `validate-draft-pipeline.ts:228-237`, `validation-types.ts:129-131` |
| `features.futureTripAllowPoint` | **KSOE** opt-in | Adds points to the ⑬ whitelist | `validate-draft-pipeline.ts:208-209`, `:215` |
| `hasAdvancePayment` | **KSOE** opt-in (`expense-report.advancePayment`) | `true` **skips** the ⑧ claim-amount-of-0 validation | `validation-types.ts:134-139`, `validate-draft-pipeline.ts:161` |
| `features.internalOrderBalance` | **Innotek** opt-in | Adds an internal-order axis to the ⑭ group key | `validation-types.ts:124-125` |
| `features.internalOrderRatioSplit` / `wbsRatioSplit` | Each opt-in | Expands the ⑭ input into N ratio-split children of the automatic receipt. When `false` (the default), the base is 1 row — **unchanged for BZP** | `validation-types.ts:126-128`, `validate-draft-pipeline.ts:238-255` |
| `features.bstrSummaryRequired` | `false` in all tenants | Disables ⑦ description-required entirely | `validate-draft-pipeline.ts:137-139` |

The `DraftValidationFeatureKey` union pins the keys at compile time — when it was `Record<string, boolean>`, a mistyped key silently became `undefined` and **the rule was skipped** (`validation-types.ts:66-80`). When changing a key you must update both injection sites (`use-expense-report-submit.ts` on authoring completion, and `use-expense-report-save.ts` for re-validation just before submission) (`:70-71`).

---

## Appendix B — Points where the determination axis **must be the same** (drift risk)

Implementing the same determination separately in several places produces the symptom "the screen and submission disagree." The pairs the source calls out are gathered here.

| Axis | Source of truth | Where the same axis must be used | Source |
|---|---|---|---|
| The usage-side amount for excess determination | `getExcessJudgmentAmount` | The excess-amount column, suspected violation ⑧ corporate-card overrun, the excess-split payload | `receipt-used-amount.ts:62-67` |
| Identifying the excess vs. ruled-amount row | `isExcessOverRow` | ⑪ aggregation, ⑫ determination and aggregation, the screen's `exceed-check-utils` aggregation, the excess-amount column | `excess-split-row.ts:37-48` |
| Aggregation-set exclusion | `isExcessOverRow` (plus `CORP` for ⑪) | ⑪ (`validate-total-exceed.ts:75-77`), ⑫ (`validate-excess-reason.ts:56-58`), the screen (`exceed-check-utils.ts:69-71`) — **three places** | Comments in each file |
| Determining whether an excess is displayed | `getOverAmountDisplayValue` + `hasExcessAmount` | Excess-column highlighting (`ExcessAmountCell`), split entry (`use-receipt-split-menu`), ⑰ | `has-excess-amount.ts:5-7`, `validate-excess-split-required.ts:7-9` |
| The reservation exemption | `isReservationPrepaidReceipt` | ⑪ determination, ⑫ determination, the 4 auto-fill cap paths, the screen's `isExcessBySetting` | `reservation-prepaid-receipt.ts:9` |
| Whether the determination is per meal | `fetchFoodDivisionTypes` → `hasRegisteredFoodDivision` | ⑫ (`validate-excess-reason.ts:251-257`), the screen's `useExceedReasonData` | The same comment |
| Actual-cost treatment | `isActualLikePayClass` | `rule-update-{default,food,room}`, `autofill-req-amount`, `receipt-amount-limit`, `receipt-amount-cell-renderers`, `exceed-check`/`total-exceed`/`excess-reason` | `pay-class-utils.ts:36-38` |
| The claim-amount cap | `receipt-amount-limit` (bstr-policy) | The expense report (re-export), the plan's advance-payment `AdvanceCostCell` | `receipt-amount-limit.ts:11-13` |
| The reservation-prepaid payment-method set | `RESERVATION_PREPAID_CARD_TYPES` | `AMOUNT_READONLY_CARD_TYPES` (cloud-expense-report) — a drift-guard test protects it | `reservation-prepaid-receipt.ts:30-35` |

---

## Open items

| # | Item | Why it is unconfirmed |
|---|---|---|
| **U-1** | Whether it is intended that `getDivisionTotalMax` has **no `FUEL` case.** Unsplit gives `FUEL → ruledAmount` while the split total is `Infinity` for non-`ETC` (`receipt-amount-limit.ts:113-125` vs `:63-79`) → **Settled: it is not a porting omission but the original legacy behavior.** The legacy (old front-end) original was checked — `libs/core/src/utils/expense-report/SeahExpenseReportUtils.tsx` at `:6789-6822` (the split branch) **also has no `FUEL` case**, while the unsplit branch (`:6824-6846`) does. So the asymmetry is as in the original and the port is 1:1. (The line numbers `6510-6577` cited in the source have moved to `6780-6847`) |
| **U-2** | ⑫'s endpoint — JSDoc `GET /api/v3/cloud-expense-report/exceed-reason-settings` (`validate-excess-reason.ts:19`) vs. the code's `GET /api/v2/bstr/expense-exceed-reason` (`api/validation-api.ts:144`) → **Settled: the code (v2) is right.** The backend `BstrExpenseExceedReasonController.java:28` = `@RequestMapping("/api/v2/bstr/expense-exceed-reason")` plus a pathless `@GetMapping` at `:64`, and an exhaustive backend search finds **0** occurrences of the string `exceed-reason-settings` → **the v3 path does not exist.** The JSDoc at `:19` is stale |
| **U-3** | The **endpoint strings** for ④ `fetchPurposeRestrict`, ⑤ `fetchPreDailyCostDates`, ⑥/⑩ `fetchAllowBeyondDays`, ⑬ `fetchPreSettlementScopeSetting`, ⑭ `fetchExpenseReportPolicySetting`, and ⑯ `fetchCancelReceiptReasonPolicy` | They fall outside this document's scope (the 3 ruled-amount-dependent validations), so the individual API files were not read. Check `PD/cloud-expense-report/api/` if needed |
| **U-4** | The **error message strings and `field` values** for ②, ②-1, ②-2, ④–⑩, and ⑬–⑯ | For the same reason, the individual validation files (`validate-sync-rules.ts`, `validate-async-rules.ts`, `validate-budget.ts`, `validate-cancel-reason.ts`, `validate-staff-lodge.ts`, `validate-summary-mapping.ts`) were not read. The rule names, gates, and async flags in the §6.5.2 table were confirmed from the pipeline JSDoc plus the code |
| **U-5** | The details of `convertRuledAmountToKRW`'s exchange-rate fallback (`rule-update-helpers.ts`) | The exact conditions and behavior of the upstream fallback mentioned at `over-amount.ts:35-37` are outside §6's scope (they belong to layer ②, condition application), so it was not read |
| **U-6** | Whether it is intended that **there is no `ValidationErrorCode` constant corresponding to ⑰** → **Settled: harmless.** There are only 3 real consumers of `ValidationErrorCode` — `PURPOSE_RESTRICTED` (`use-expense-report-submit.ts:283`, `use-expense-report-save.ts:146`), `INTERNAL_ORDER_RATIO_INCOMPLETE`, and `WBS_RATIO_INCOMPLETE` (`ExpenseReportCommonDialogs.tsx:130-131`). `ROOM_EXCEED`, `FOOD_EXCEED`, and `EXCESS_REASON_REQUIRED` are declared but never set or consumed — effectively dead constants. → The absence of a constant for ⑰ has no effect, and **the §6.5.5 conclusion to branch on `field` stands** |
| **U-7** | The "19 rows / 18 rules" statement at `validate-draft-pipeline.ts:6` was judged stale (measured 20 rows / 19 executed), but the commit evidence for the guess that **it was not updated when ⑰ was added** was not checked | The row count itself is settled by measurement. Only the guess at the cause is unconfirmed |
| **U-8** | Whether the **backend** performs runtime validation on `bstrPayClassType` | **It is settled that the front end does not** — the zod is `z.string().nullable().optional()` (`receipt-types.ts:249`) and a schema test with `'DAILY'` passes with `success === true` (`autofill-receipt-defaults.test.ts:611`, `:622` — §6.3.1). Whether the **backend** rejects an unknown value cannot be determined from this repository |
