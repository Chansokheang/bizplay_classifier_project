# Receipt Ruled Amount — Implementation Status and Follow-up Questions (2026-09-14)

We have reviewed the 2026-09-09 reply and all 10 enclosed materials, and implemented everything the materials
settle on their own. **All 71 golden vectors match, and no vector diverged.**

Four features remain unimplemented. **Section 3 asks the 5 questions that unblock them.**

Source paths we quote (`*.ts`, `*.java`) are paths in your repository, which we cannot access. They are
reproduced from your documents. A Korean version of this document is `00_260914_회신_및_질의서.md`.

---

# 1. Completed (summary)

We followed the "good order to start in" from §8 of your reply.

| Step | What we did | Status | Reference |
|---|---|---|---|
| ① Raw data lookup | `POST /api/v2/bstr/policy/renewal/limit` per **grade-zone segment × traveler**. All 20 request fields audited | Done | `01_RawDataLookup_Contract` §2.2, §2.3 |
| ② Condition application | An engine applying `appliedConditions[]` **per day**: 8 operators, 6 calcMethods, dayType / dateRange / periodRange, `dailyDiff`, the travel-day partition, `supersededByIds`, and the **convert-then-apply** order for foreign currency | Done | `02_ConditionApplication_Rules` |
| Verification | **71 of 71 golden vectors match**, amounts plus the calculation detail's labels and effect strings | 71/71 | `규정금액_골든벡터.json` |
| Verification | Examples A, B and C **reproduced row by row**, with pitfalls 3, 9, 10, 11, 15, 18 and 19 pinned as regression tests | 3/3 | `03_WorkedExamples` |
| ③ Validation | Excess on the used-amount axis, claim cap `getNonDivisionMax`, auto-fill, ⑫ excess reason | Done | `06_Validation` §6.1, §6.3.2, §6.4, §6.5.7 |
| ③ Validation | ⑰ excess split via `PATCH /api/v2/receipt/divide/{receiptId}`, 2 EXCESS rows, submission blocked until done | Done | `06_Validation` §6.2.8, §6.5.8 |
| Document totals | Totals are sums of `reqAmt`, cancellations deducted, lodging bucket includes manual ROOM claims | Done | `Receipt_RuledAmount_Overview` §9-4 |
| Currency | Converted to KRW with your rate first, then conditions. `Math.trunc` per date | Done | `02` §6-2, §6-3 |
| Foreign notation | `overseasRuledAmount` is `null` once a condition applied | Done | `03` pitfall 18 |
| Meal companions | Looked up **once per active companion and summed**, `DRAFT_ONLY` rows excluded | Done | `04` §4.6 |
| Meal classification | `foodDivisionType` and the usage date now sent on manual meal receipts | Done | `04` §4.6 |
| Self-check | `06` §6.6 checklist **C-0 through C-4** pass, except the 2 items in Q3 and Q4 | Pass | `06_Validation` §6.6 |

**How it was verified.** On the development tenant (`cloud-dev`, corporation 1234567890) we ran the whole path
against the real API: look up the plan, register receipts, derive the ruled amount, split the excess, file the
settlement, then re-read `pre-receipts/form` on the filed document to confirm the stored values.

**Measured examples**

| Case | Rule | Result |
|---|---|---|
| KTX 60,000 KRW | FIXED 30,000/day | Ruled 30,000, filed as 2 EXCESS rows of 30,000 / 30,000 |
| Hotel USD 400, 2 nights | LIMITED USD 100/day + weekday +10,000 | Ruled 282,958, excess 242,958 |
| Meal 50,000 KRW, 1 companion | LIMITED USD 10/day | Ruled 27,206, versus 13,147 without the companion |

---

# 2. Not implemented

| Feature | Blocked by |
|---|---|
| ⑪ Lodging and meal total overrun | **Q1** |
| Per-diem automatic receipt | **Q2** |
| A clean pass on your §6.6 checklist | **Q3** and **Q4** |
| C-5, the rest of the submission pipeline (17 checks outside the ruled-amount axis) | **Q5** |
| Fuel and toll automatic receipts | **Not a question.** These need per-route fuel prices (OPINET) and tolls (T-map), which our system has no source for. Implementing them by guesswork would produce different amounts. Please tell us if you need them and we will discuss separately |

---

# 3. The 5 questions that unblock work

## Q1. Where does ⑪'s "other-report running total" come from?

Your §6.5.6 "APIs called" table lists exactly **2** APIs, `pre-receipts/form` and `renewal/limit`.
But the running-total mapping in the same section uses **3** data sets:

> | Purpose | Already drafted | Other-report running total |
> |---|---|---|
> | **ROOM** | `preDraftedPersonal` (own share) | `priorSettledReceipts` (own share) |
> | **FOOD** | `preDraftedWithCompanion` (companions included) | `priorSettledReceiptsWithCompanion` |

**Our questions:**

1. These 4 names appear only in the prose of `04` and `06`. They are **not in
   `규정금액_requestBody_샘플.json`** — we checked all 36 top-level keys of the sample and none of them
   matches. Are they **internal to your client**, never sent in the requestBody? That is our reading. If any
   of them **must** be sent, please tell us where in the body and in what shape.
2. `priorSettledReceipts` has **no API behind it** anywhere in the section. Where does it come from?
3. The rows `pre-receipts/form` returns **carry no user identifier field**, so we cannot tell an own-share row
   from a companion's. How should the two sets be separated?

**References**: `06_Validation` §6.5.6 "APIs called" (`:280`, `:290`, `:358-371`) and the running-total mapping
table (`:285-286`, `:376-387`, `:399-401`); §6.5.7 (`:67-70`, `:222-224`); §6.6 C-4 ❹-1, ❹-2

**Why it blocks us**: treating the running total as 0 **misses limit already consumed by other reports**, so a
document that should be blocked passes. Everything else in ⑪ is ready. **This one answer completes it.**

---

## Q2. Per-diem automatic receipt — the two conditions we cannot execute

Your `05_AutomaticReceipts` per-diem row lists 6 creation conditions:

> "① The rule's payment type is not unpaid (NONE) ② There is a payable amount
> (`hasDailyCostPayableAmount`) ③ At least 1 target date ④ The purpose can be **mapped to a form area**
> (`checkAutoReceiptMappable`) ⑤ The **already-claimed-days lookup is complete** ⑥ It is not a
> pre-settlement segment"

We can evaluate ①, ②, ③ and ⑥ from data we already hold. We cannot evaluate ④ and ⑤.

1. Condition ⑤, "the already-claimed-days lookup is complete" — what is the **endpoint path and request /
   response shape** of `fetchPreDailyCostDates`? (Validation ⑤ in §6.5.2 uses the same function.)
2. Condition ④, "mappable to a form area" (`checkAutoReceiptMappable`) — **what should an external system
   compare against** to decide this?
3. **The receipt's shape.** Your sample and your field table in `05` disagree. The sample's per-diem entry
   is **one row spanning 4 days** (`usedStartDate` `2026-08-27`, `usedEndDate` `2026-08-30`), while `05`
   rows 21-22 say an automatic receipt has "**start = end, a single day**". Is a per diem **one row for the
   whole trip**, or **one row per day**? This decides what we build.
   (Same pair, minor: the sample's `mestName` is `"일비 증빙"`, `05` row 4 says `'일비용 증빙'`.)

**References**: `05_AutomaticReceipts`, the `DAILY_COST` row, conditions ① to ⑥; `06_Validation` §6.5.2
validation ⑤ (`:130-131`); `03_WorkedExamples` B-5

**Why it blocks us**: without the already-claimed days we would create **duplicate claims on the same date**,
which validation ⑤ then rejects. The arithmetic itself is already implemented and verified against your
Example B.

---

## Q3. `overseasRuledAmount` — your checklist and your derivation rules disagree

| Material | Instruction |
|---|---|
| `06_Validation` §6.6 **C-0 ⓿-4** | "`> 0` if there is a foreign rule" |
| `03_WorkedExamples` **pitfall 18** | "**always `null`** … filling it **gives a wrong excess determination**" |
| `04_PerPurpose_CalculationPaths` | "**always `null`**. Set only for a pure foreign rule with **no** formula" |

**We chose `null`**, following `03` and `04`, because C-0 ⓿-4 taken literally produces exactly the wrong
determination pitfall 18 warns about. Please confirm which is authoritative.

---

## Q4. An excess split always violates ❷-3 by construction

C-2 ❷-3 requires `Σ reqAmt ≤ getDivisionTotalMax(row)`. §6.2.8 requires the two rows to **sum to the used
amount**. Outside `CORP` these cannot both hold. Measured:

| Item | Amount | Source |
|---|---|---|
| Used amount (`approvalAmount`) | 60,000 | the receipt |
| Ruled amount, with `bstrPayClassType = FIXED` | 30,000 | the rule lookup |
| **§6.2.8 requires** the two `EXCESS` rows' claim amounts to sum to the original used amount, so `Σ reqAmt` must equal | **60,000** | `06_Validation` §6.2.8, "The sum of the claim amounts = the original used amount" (`:19`) |
| **§6.3.3 ② row 6 caps** a `FIXED` split total at `getDivisionTotalMax = ruledAmount`, so `Σ reqAmt` must not exceed | **30,000** | `06_Validation` §6.3.3 ②, row 6 of the `getDivisionTotalMax` table (`:116-117`) |

Is an excess split **exempt from ❷-3**, so that ❷-3 applies only to `USER` and `BASIC` splits?

**References**: `06_Validation` §6.6 C-2 ❷-3 and ❷-4 (same table, opposite requirements); §6.2.8 (`:19`);
§6.3.3 ② row 6

**Current behavior**: we follow §6.2.8. Your server accepts this shape.

---

## Q5. C-5 — the criteria for the rest of the submission pipeline

Your §6.5.2 pipeline has 20 rows. Three of them are the ruled-amount blockers we implemented (⑪, ⑫, ⑰).
The other **17** are ordinary submission checks, and because the pipeline is fail-fast they fire **before**
ours, so a document can be rejected without ⑪, ⑫ or ⑰ ever running. C-5 points us at the §6.5.2 table for
them, but that table gives only item names and function names. Of the 17, ② is disabled and ⑦ is opt-in, and
we can already handle several. These are the 6 rows we need, quoted from your table:

> | No. | Rule | API called | Gate |
> |---|---|---|---|
> | ④ | Trip-purpose restrictions | `fetchPurposeRestrict` | Always |
> | ⑤ | Duplicate per-diem dates | `fetchPreDailyCostDates` | Always |
> | ⑥ | Usage-date range (adjusted for pre-trip allowed days; lodging excluded) | `fetchAllowBeyondDays` | Always |
> | ⑧ | Claim amount of 0 | `fetchRequestedAmountSetting` | `requestedAmountUsed = true` ∧ `hasAdvancePayment !== true` |
> | ⑧-1 | Posting date required (`required=true` but blank) | — | Always |
> | ⑨ | Receipt date > posting date | `fetchBudatBeforeBldatDays` | Always |
> | ⑯ | Cancellation reason required on cancelled receipts | `fetchCancelReceiptReasonPolicy` | When the setting is ON |

Each row names a function but gives no path, no request shape and no pass/fail criterion. For each of the 6,
please give us the endpoint and what counts as a failure. ⑤ is the same question as Q2.

**References**: `06_Validation` §6.5.2, the full table; §6.6 C-5

---

**In short**: Q1 and Q2 unblock two features. Q3 and Q4 resolve two contradictions inside your own package.
Q5 is needed for a complete self-check.

Thank you.
