# §3. Condition Application Rules (layer ② of the 3 ruled-amount layers)

> **Where this section sits** — the ruled amount has 3 layers.
> ① Raw data lookup: backend `POST /api/v2/bstr/policy/renewal/limit` → `limitAmounts` (per-date **base amount**) + `appliedConditions[]` (conditions) + `dayTypeMap`
> ② **Condition application (this document)** — the front end lays the conditions over the base amount per day to produce the final ruled amount
> ③ Validation: receipt amount ≤ ruled amount
>
> The backend **does not calculate the amount.** The comment at `BstrRenewalLimitService.java:149` is the source of truth —
> "limitAmounts stays as the base amount (the front end applies conditions to derive the final amount)"
> So when an external system assembles the requestBody directly and wants to fill `ruledAmount`, **it must reproduce the calculation in this document on its own side.**

**Source-of-truth files**

| Role | File |
|------|------|
| Application engine (source of truth) | `packages/domains/src/bstr-policy/utils/calc-condition-engine.ts` (472 lines) |
| Per-day application guards | `packages/domains/src/bstr-policy/utils/calc-condition-guards.ts` |
| Date position axis and coverage determination | `packages/domains/src/bstr-policy/utils/calc-day-position.ts` |
| `calcMethod` constants and normalization | `packages/domains/src/bstr-policy/utils/calc-method.ts` |
| Calculation-detail label builder | `packages/domains/src/bstr-policy/utils/calc-condition-labels.ts` |
| Response types, boundary zod, deferral determination | `packages/domains/src/bstr-policy/api/renewal-limit-api.ts` |
| Regression gate (comparison baseline) | `packages/domains/src/bstr-policy/utils/calc-condition-engine.test.ts` (91 cases) |
| The admin screen's value sets (source of truth) | `packages/domains/src/bstr-expense-rule/constants/calc-constants.ts` |

---

## 1. Application order (algorithm)

### 1-0. Four ground rules

| # | Rule | Source |
|---|------|------|
| 1 | **No rounding.** All arithmetic stays in floating point. Rounding, floor, and trunc are the responsibility of the display and receipt stages. | `calc-condition-engine.ts:8` (engine header) |
| 2 | The loop is **dates outside, conditions inside**. | `calc-condition-engine.ts:399` (dates) → `:222` (conditions) |
| 3 | Within a single day, **the next condition is laid on top of the previous condition's result** (accumulation). There is no accumulation across dates — each day starts fresh from `limitAmounts[date]`. | `calc-condition-engine.ts:221` (`result = amount`), `:229`, `:232` (`result` reassigned), `:401` |
| 4 | Traversal order is ascending `sortOrder`. Equal `sortOrder` preserves the response array order (`Array.sort` is a stable sort). | `calc-condition-engine.ts:334` |

Because of rule 3, **condition order changes the result.** The tests pin this explicitly —
`×2` then `fixed 10,000` = **10,000**, while `fixed 10,000` then `×2` = **20,000** (`calc-condition-engine.test.ts:104-131`).

### 1-1. Pseudocode — top level

```text
FUNCTION applyCalcConditions(response, opts = {}) -> response'
  conditions ← response.appliedConditions
  baseMap    ← response.limitAmounts          // { "YYYY-MM-DD": base amount }

  # Guards (pass-through — limitAmounts kept as-is)
  IF conditions is null OR conditions is empty OR baseMap is null:  RETURN response   # :319
  IF response.calcEnabled === false:                                RETURN response   # :320

  # Candidate selection: only matched ones. Ascending sortOrder.
  #   ※ overridden is NOT excluded here — it is determined per day (§1-3).
  candidates ← conditions.filter(c => c.matched === true)
                         .sort(asc by c.sortOrder)                                    # :332-334

  IF candidates is empty:
      RETURN response with calcBreakdown = {                                          # :335-340
          baseAmount : response.limitAmount ?? 0,
          items      : [],
          totalAmount: sum(values(baseMap))
      }
      # limitAmounts is left untouched.

  dates ← sort(keys(baseMap))                 // ISO string sort = chronological      # :354

  # Compute the per-date position axis (pre/core/post, first/last day, coverage denominator) — §3-4, §5
  (positions, isTravelDayAssignable)
      ← resolveDayPositions(dates, opts.preCount ?? 0, opts.postCount ?? 0,
                            response.calcPeriod)                                      # :364

  # Split the conditions three ways into travel-day and core — §5
  IF isTravelDayAssignable:                                                           # :371-379
      preConds  ← candidates where hasItemType(c, opts.predepartType)
      postConds ← candidates where hasItemType(c, opts.nextarriveType)
      coreConds ← candidates where NOT hasItemType(c, predepartType)
                                AND NOT hasItemType(c, nextarriveType)
  ELSE:
      preConds ← []  ;  postConds ← []  ;  coreConds ← candidates

  dayTypeMap ← response.dayTypeMap ?? {}                                              # :394
  finalMap   ← {}
  appliedSet ← ∅                              // for the calculation detail (calcBreakdown)

  FOR index, date IN enumerate(dates):                                                # :399
      amount ← baseMap[date] ?? 0
      pos    ← positions[date]
      IF pos is missing:  finalMap[date] ← amount ; CONTINUE                          # :404-407

      dayNum ← index + 1        // ★ Based on the map index. NOT the absolute date — §3-3  # :410

      IF pos.slot == 'pre':                                                           # :411-415
          finalMap[date] ← applyConditionsForDay(preConds,  date, amount, dayTypeMap,
                                                 isFirst=TRUE,  isLast=FALSE,
                                                 dayNum, pos=NONE, appliedSet)
      ELSE IF pos.slot == 'post':                                                     # :416-420
          finalMap[date] ← applyConditionsForDay(postConds, date, amount, dayTypeMap,
                                                 isFirst=FALSE, isLast=TRUE,
                                                 dayNum, pos=NONE, appliedSet)
      ELSE:  // 'core'                                                                # :421-434
          finalMap[date] ← applyConditionsForDay(coreConds, date, amount, dayTypeMap,
                                                 isFirst=pos.isFirst, isLast=pos.isLast,
                                                 dayNum, pos=pos, appliedSet)

  RETURN response with
      limitAmounts  = finalMap,
      calcBreakdown = buildBreakdown(response.limitAmount ?? 0,
                                     sort(appliedSet, asc by sortOrder),
                                     sum(values(finalMap)))                           # :437-450
```

### 1-2. Pseudocode — one day (`applyConditionsForDay`, `:203-236`)

```text
FUNCTION applyConditionsForDay(conds, date, amount, dayTypeMap,
                               isFirst, isLast, dayNum, pos, appliedSet) -> number

  # Step 1: the set of condition ids "applicable" on this day — the basis for per-day fallback
  applicableIds ← { c.id | c ∈ conds, c.id ≠ null,
                    isApplicableOnDay(c, date, dayTypeMap, dayNum) }                  # :215-220

  # Step 2: lay them on in order
  result ← amount                                                                     # :221
  FOR cond IN conds:                          // already in ascending sortOrder        # :222
      IF NOT isApplicableOnDay(cond, date, dayTypeMap, dayNum):  CONTINUE             # :223
      IF isSuppressedOnDay(cond, applicableIds):                 CONTINUE             # :225
      appliedSet.add(cond)                                                            # :226
      IF pos is present:                      // a core day                           # :227-230
          result ← isDayCovered(cond, pos) ? calculateAmount(cond, result, isFirst, isLast)
                                           : 0                  // ★ zero, not skip
      ELSE:                                   // travel day (pre/post) — no coverage check  # :231-233
          result ← calculateAmount(cond, result, isFirst, isLast)
  RETURN result


FUNCTION isApplicableOnDay(cond, date, dayTypeMap, dayNum) -> boolean                 # :167-178
  RETURN isDayTypeMatched(cond, dayTypeMap[date])      # §3-1
     AND isDateInRange(cond, date)                     # §3-2
     AND isPeriodDayCovered(cond, dayNum)              # §3-3


FUNCTION calculateAmount(cond, amount, isFirst, isLast) -> number                     # :151-161
  IF normalizeMethod(cond.calcMethod) == 'dailyDiff':
      RETURN applyDailyDiff(cond, amount, isFirst, isLast)      # §4 — operator ignored
  RETURN applyOperator(cond, amount)                            # §2
```

> **⚠️ The easiest thing to get wrong** — on a core day, when `isDayCovered(cond, pos)` is false, it does **not skip the condition; it sets that day's accumulated amount to 0** (`:228-230`).
> That means the value built up by preceding conditions is wiped out too.
> Example: 1 `departArrive` condition over 3 days → `[100, 0, 100]` (`calc-condition-engine.test.ts:173-176`).

### 1-3. `overridden` / `supersededByIds` — per-day fallback

```text
FUNCTION isSuppressedOnDay(cond, applicableIds) -> boolean                            # :187-191
  superseders ← cond.supersededByIds
  IF superseders == null:                          // older backend (not sent)
      RETURN cond.overridden === true              //   → globally not applied (back-compat)
  RETURN ∃ id ∈ superseders : id ∈ applicableIds   // newer: yields if a superseder applies that day
```

Meaning:

- `supersededByIds` = the list of **higher-priority condition ids** that strict-superset (are more specific than) this condition and take precedence (`renewal-limit-api.ts:243-246`).
- If any of those higher-priority conditions is applicable **that day**, this condition yields (is not applied). If all of them are inapplicable that day, **this condition takes over (fallback)**.
- `overridden` is only a summary flag meaning "an overriding higher-priority condition exists," not "globally not applied" (`renewal-limit-api.ts:240-242`). A condition with `overridden=true` can still be applied.

Chain example (`calc-condition-engine.test.ts:906-936`, A→B→C):

| Date | Day type | A (id3, weekday + limited to 1/1, 10,000) | B (id2, weekday, 8,000) | C (id1, all, 5,000) | Result |
|------|---------|------------------------------|-------------------|-------------------|------|
| 2026-01-01 | Weekday | Applied | Yields to A | Yields to A and B | **10,000** |
| 2026-01-02 | Weekday | Outside dateRange → not applied | Applied (takes over) | Yields to B | **8,000** |
| 2026-01-03 | Weekend | dayType mismatch → not applied | dayType mismatch → not applied | Applied (takes over) | **5,000** |

> **An external system may end up reproducing only the older contract.** If you ignore `supersededByIds` and filter with `matched && !overridden`, rows 1/2 and 1/3 above become "no conditions" (the base amount of 23,000) and diverge.
> If `supersededByIds` is present in the response, you **must** implement the per-day determination.

### 1-4. The idempotency guard — applying twice throws off the amount

The engine itself has **no** idempotency guard. `applyCalcConditions` calculates as many times as you call it.
The guard lives in the **consuming path**.

```text
isCalcAlreadyApplied ← (rule.calcBreakdown exists) AND (rule.calcDeferred is not truthy)
IF isCalcAlreadyApplied:  use limitAmounts as-is (do not call the engine again)
ELSE:                     apply once via applyCalcConditionsKRW(rule, {...limitAmounts}, opts)
```

| Location | Lines |
|------|----|
| `cloud-expense-report/utils/daily-cost-amount-utils.ts` | `:240-243`, `:278-281` |
| `cloud-expense-report/utils/daily-cost-breakdown.ts` | `:132-135` (rationale comment at `:99-102`) |

In other words, **`calcBreakdown` present and not `calcDeferred` = already applied.**
`calcDeferred=true` means "the base amount is raw; only the calculation detail has been built ahead of time" (§5-3, §6).

---

## 2. The 8 `operator` arithmetic rules

### 2-1. Value set

The `OPERATOR` constant (`calc-condition-engine.ts:25-34`) matches the admin screen's `CALC_OPERATORS` (`bstr-expense-rule/constants/calc-constants.ts:238-248`) **1:1 across all 8**.

| operator | Admin label | Formula (`amount` = the accumulated amount so far that day) | Description | Source line |
|----------|------------|--------------------------------------|------|---------|
| `none` | Not applied | `amount` | No change to the amount | `:121-122` |
| `unpaid` | Unpaid | `0` | Not paid that day | `:123-124` |
| `fixed` | Fixed | `cond.fixedAmount != null ? cond.fixedAmount : amount` | **Replaces** the accumulated amount (not an adjustment) | `:125-126` |
| `silbiLimit` | Actual cost (capped) | `cond.fixedAmount != null ? cond.fixedAmount : amount` | Actual cost up to the limit. **Not a `min` — the same replacement as `fixed`** | `:127-130` |
| `x` | × | `isPercent ? amount × (v / 100) : amount × v` | `isPercent` per §2-3 | `:131-136` |
| `+` | + | `amount + v` | | `:137-138` |
| `-` | − | `max(0, amount − v)` | **Clamped at a lower bound of 0** | `:139-140` |
| `/` | ÷ | `v !== 0 ? amount / v : amount` | Division by zero keeps the amount | `:141-144` |
| (Any other string) | — | `amount` | An unknown operator leaves the amount unchanged | `:145-146` |

`v` = `parseValue(cond.operatorValue)` (§2-4).

Three things to watch:

1. **`silbiLimit` is not a `min`.** It replaces even when the limit is larger than the base amount —
   base amount 10,000 + `silbiLimit` 16,500 → **16,500** (`calc-condition-engine.test.ts:1020-1022`).
   Non-payment of the excess is handled by the **downstream excess determination** using this limit as the basis (comment at `:128-130`).
2. **Only `-` has a clamp.** `+`, `x`, and `/` have no lower bound.
3. If `fixedAmount` is `null` for `fixed` or `silbiLimit`, **the amount is kept** (not 0) —
   `calc-condition-engine.test.ts:148` (fixed), `:1024-1026` (silbiLimit).

### 2-2. `normalizeOperator` — uppercase backend enum aliases

`calc-condition-engine.ts:39-51`.

| Input (backend) | Normalized form |
|-------------|--------|
| `NONE` | `none` |
| `FIXED` | `fixed` |
| `UNPAID` | `unpaid` |
| `ADD` | `+` |
| `SUBTRACT` | `-` |
| `MULTIPLY` | `x` |
| `DIVIDE` | `/` |
| `null`, `undefined`, `''` (falsy) | `none` (`:40`) |
| Any other value not in the map | **Returned verbatim** (`:50`) → the `default` branch of `applyOperator` → the amount is unchanged |

> **⚠️ There is no `SILBI_LIMIT` alias.** The mapping table has no UPPER_SNAKE entry corresponding to `silbiLimit`, so if the backend sends `SILBI_LIMIT`, it passes through verbatim, falls to `default`, and **the amount silently becomes unchanged.**
> Since the admin-saved value is the lowercase `silbiLimit` (§2-1), this is not a problem on the current path.
> An external system should feed **the string carried in the response** into the table above as-is — do not uppercase it on your side.

### 2-3. `PERCENT_UNITS` — percentage interpretation of `operatorUnit`

`PERCENT_UNITS = ['PERCENT', '%']` (`calc-condition-engine.ts:36`).

```text
isPercent ← (cond.operatorUnit != null) AND (cond.operatorUnit ∈ ['PERCENT', '%'])    # :134
```

| Condition | Interpretation |
|------|------|
| `operator='x'`, `operatorUnit='PERCENT'` (or `'%'`) | `operatorValue` is a **percentage** → `amount × (v/100)` |
| `operator='x'`, `operatorUnit='AMOUNT'` | `operatorValue` is a **multiplier** → `amount × v` |
| `operator='x'`, `operatorUnit=null` | Treated as **not** a percentage → multiplier (comment at `:133`) |
| `operator` is not `x` | **`operatorUnit` is not read at all** |

The value set for `operatorUnit` is `PERCENT` and `AMOUNT` (`renewal-limit-api.ts:230`; backend `CalcConditionSaveRequest.java:58` "operand unit: PERCENT, AMOUNT").

Verification (`calc-condition-engine.test.ts:152-159`; `operatorUnit=null` at `:462-475`):

| Input | Result (base 50,000) |
|------|------------------|
| `x` / `2` / `AMOUNT` | 100,000 |
| `x` / `70` / `PERCENT` | 35,000 |
| `x` / `2` / `null` | 100,000 |
| `+` / `10000` / `PERCENT` | **60,000** — `+` ignores `operatorUnit` |

### 2-4. `parseValue` — string to float

`calc-condition-engine.ts:53-57`.

```text
FUNCTION parseValue(value) -> number
  IF value == null:  RETURN 0            // null, undefined
  num ← parseFloat(value)                // parses only the leading number (JS Number.parseFloat semantics)
  RETURN isNaN(num) ? 0 : num            // parse failure → 0
```

- `''` → `parseFloat('')` = NaN → **0**
- As a consequence, when `operatorValue` is empty, `+` and `-` change nothing, `x` becomes `×0`, and `/` is a division by zero and changes nothing.
- **`parseValue` is not used for `dailyDiff` rates** (§4-2 — that path treats NaN as "keep the base," not 0).

---

## 3. The `dayType` filter and applicable-day coverage

`isApplicableOnDay` is the AND of three guards (`calc-condition-engine.ts:167-178`).
If any one is false, **that condition is skipped for that day** (the amount keeps its previous state — it does not become 0).
That is what distinguishes it from `isDayCovered` in §1-2.

### 3-1. `isDayTypeMatched` — day type

`calc-condition-guards.ts:29-41`.

```text
FUNCTION isDayTypeMatched(cond, dayType) -> boolean       // dayType = response.dayTypeMap[date]
  FOR item IN (cond.items ?? []):                                                     # :34
      IF item.itemType NOT IN ['DAY_TYPE', 'dayType']:  CONTINUE                      # :20-22, :35
      value ← item.itemValue
      IF value is falsy OR value == '평일/공휴일':       CONTINUE   // no constraint    # :37
      IF dayType !== value:  RETURN FALSE                // includes missing (undefined)  # :38
  RETURN TRUE                                            // always true if there is no dayType item
```

**Value sets**

| Axis | Values | Source |
|----|----|------|
| Response `dayTypeMap[date]` (determined by the backend) | `평일` (weekday), `주말` (weekend), `공휴일` (holiday) | `BstrRenewalLimitService.java:216-231` (`buildDayTypeMap`) |
| The condition's `itemValue` (admin selection) | `평일/공휴일` (weekday/holiday), `평일`, `공휴일`, `주말` | `bstr-expense-rule/constants/calc-constants.ts:18` |
| Wildcard (no constraint) | `평일/공휴일`, empty string | `calc-condition-guards.ts:17` (`DAY_TYPE_ALWAYS`), `:37` |

- Matching is **string equality**. It is not substring matching or set membership.
- `itemType` accepts both `DAY_TYPE` (uppercase) and `dayType` (camelCase) (`:20-22`).
- If the date is absent from `dayTypeMap` (`undefined`), a concrete-value condition is treated as **not matching** (comment at `:38`).
- The backend assigns exactly one type in the order **holiday > weekend > weekday** (`buildDayTypeMap`). So a day that is both a weekend and a holiday is `공휴일`, and a `주말` condition does not apply to it.

Verification (`calc-condition-engine.test.ts:342-345`): weekday condition + weekday = applied; weekday condition + holiday = skipped; weekend condition + weekend = applied; `평일/공휴일` condition + weekend = applied.

### 3-2. `isDateInRange` — the date option (`details.dateRange`)

`calc-condition-guards.ts:52-60`.

```text
FUNCTION isDateInRange(cond, date) -> boolean
  range ← cond.details?.dateRange
  IF range is null/undefined:  RETURN TRUE          // no constraint                    # :54
  from ← range.from || null   ;  to ← range.to || null    // '' is absorbed into null    # :55-56
  IF from AND date < from:  RETURN FALSE                                              # :57
  IF to   AND date > to:    RETURN FALSE                                              # :58
  RETURN TRUE
```

- `date`, `from`, and `to` are all `"YYYY-MM-DD"`, so **string comparison is safe** (timezone-independent, comment at `:48`).
- If only one bound is present, only that bound applies.
- A date outside the range skips only that condition and **keeps the base amount**.

Verification (`calc-condition-engine.test.ts:367-370`): condition `7/1–7/31` with `+10,000`, trip `7/30–8/1`
→ `[50000, 50000, 40000]`.

### 3-3. `isPeriodDayCovered` — the period option (`details.periodRange`)

`calc-condition-guards.ts:71-82`.

```text
FUNCTION isPeriodDayCovered(cond, dayNum) -> boolean     // dayNum: 1-based day number
  pr ← cond.details?.periodRange
  IF pr is null/undefined OR pr.value is falsy:  RETURN TRUE                          # :73
  value ← Number(pr.value)
  IF NOT isFinite(value):  RETURN TRUE                                                # :75
  SWITCH pr.op:
      'gt'  -> RETURN dayNum >  value                                                  # :77
      'lte' -> RETURN dayNum <= value                                                  # :78
      'lt'  -> RETURN dayNum <  value                                                  # :79
      default (= 'gte' and unspecified) -> RETURN dayNum >= value                      # :80
```

**The axis of `dayNum` is the trap** — `dayNum = index + 1` is based on **the sorted index of the `limitAmounts` map**, **not** on the absolute date within the request period (`calcPeriod`) (`calc-condition-engine.ts:408-410`).
The reason: the backend first removes dates from the map via the payment option (`applyPayOptionDays`), and the backend's own `tripDays` (= `limitAmounts.size()`, `BstrRenewalLimitService.java:135`) is also based on that trimmed map.
If the front end alone switched to an absolute axis, front-end and back-end matching would diverge (`calc-day-position.ts:16-20`).

#### ★ The asymmetry between the backend's `matched` and front-end application (must be reproduced)

The backend determines `matched` solely as **"is there at least one day number on which the condition would apply?"**, and **the front end decides which days it actually applies to.**

The comment at `BstrRenewalLimitService.java:384-386` is the source of truth —
"Switched to per-day application: the actual per-day-number application is performed by the front-end engine (isPeriodDayCovered), and the backend only determines whether there is at least one day number on which the condition would apply (=matched). Example: 31 or fewer (lte 31) + a 40-day trip → day 1 is ≤ 31, so matched → the front end applies it only to days 1–31."

The backend's determination formulas (`BstrRenewalLimitService.java:387-393`):

| `op` | Backend `matched` condition | Day numbers the front end actually applies to |
|------|----------------------|--------------------------|
| `gte` (default) | `tripDays >= threshold` | `dayNum >= threshold` |
| `gt` | `tripDays > threshold` | `dayNum > threshold` |
| `lte` | `threshold >= 1` (true if day 1 satisfies the condition) | `dayNum <= threshold` |
| `lt` | `threshold > 1` | `dayNum < threshold` |

> **If an external system reads `matched=true` as "apply to every day," a 40-day trip gets the condition applied through days 32–40 too and the amount balloons.**
> `matched` is only "candidate eligibility"; day assignment must be determined again on the front end using `periodRange`, `dateRange`, and `dayType`.

### 3-4. `calcMethod` — amount coverage determination (`isDayCovered`)

`isDayCovered` differs in nature from §3-1 through §3-3. It is not "is the condition applicable" but **amount coverage according to the payment period**, and when false it **sets that day's amount to 0** (comment at `calc-condition-guards.ts:11-12`, `calc-condition-engine.ts:228-230`).

The `CALC_METHOD` constant (`calc-method.ts:16-29`) and normalization (`calc-method.ts:52-55`):

| `calcMethod` (normalized) | UPPER_SNAKE alias | Admin label | Coverage determination (`calc-day-position.ts`) | Source line |
|----------------------|-----------------|-----------|-----------------------------------|---------|
| `daily` | `DAILY` | Per-day calculation | Always `true` | `:153` |
| `dailyDiff` | `DAILY_DIFF` | Per-day calculation (tiered) | Always `true` (the amount is decided by §4) | `:153` |
| `departArrive` | `DEPART_ARRIVE` | Paid on departure and end days | `pos.isFirst \|\| pos.isLast` | `:155-156` |
| `excludeDepart` | `EXCLUDE_DEPART` | Trip period −1 (departure day) | `!pos.isFirst` | `:157-158` |
| `excludeArrive` | `EXCLUDE_ARRIVE` | Trip period −1 (arrival day) | `!pos.isLast` | `:159-160` |
| `excludeN` | `EXCLUDE_N` | Trip period −N days | `excludeDays >= pos.coreTotalDays ? false : pos.dayFromEnd >= excludeDays` | `:161-165` |
| (A value not in the map) | — | — | `true` (the default branch) | `:166-167` |

`normalizeMethod` rules (`calc-method.ts:52-55`):
- Falsy (`null`, `undefined`, `''`) → **`daily`** (no payment-period constraint = every day)
- UPPER_SNAKE → camelCase
- A value not in the map → **verbatim** (it is not flattened to `daily`, to prevent a new payment period from being miscalculated as "all days")

Verification (`calc-condition-engine.test.ts:173-190`, 3 days, base 100):

| `calcMethod` | Result |
|-------------|------|
| `departArrive` | `[100, 0, 100]` |
| `excludeDepart` | Day 1 is `0` |
| `excludeArrive` | Day 3 is `0` |
| `excludeN` (`excludeDays=1`) | `[100, 100, 0]` |
| `excludeN` (`excludeDays=3` ≥ total) | `[0, 0, 0]` |

### 3-5. `DayPosition` and `resolveDayPositions` — the raw material for day-number coverage

`DayPosition` (`calc-day-position.ts:33-43`):

| Field | Type | Meaning |
|------|------|------|
| `slot` | `'pre' \| 'core' \| 'post'` | Travel-day / core assignment |
| `isFirst` | boolean | First core day |
| `isLast` | boolean | Last core day |
| `dayFromEnd` | number | Days elapsed from the core end date (0 = the end date) |
| `coreTotalDays` | number | Total core days (the denominator for `excludeN`) |

`resolveDayPositions(dates, preCount, postCount, period)` computes on **one of two axes** (`:52-62`).

**(A) Absolute axis** — when `period` (= `response.calcPeriod`) is present and `isAbsoluteAxisApplicable` is true (`:82-115`)

```text
Precondition (:77-80): isValidYmd(period.start) AND isValidYmd(period.end)
              AND every date is a valid YMD AND period.start <= date <= period.end   // map ⊆ period
rawCoreStart ← period.start + preCount days                                           # :89
rawCoreEnd   ← period.end   − postCount days                                          # :90
isTravelDayAssignable ← (preCount>0 OR postCount>0) AND diffDays(rawCoreStart, rawCoreEnd) >= 0   # :91
coreStart ← isTravelDayAssignable ? rawCoreStart : period.start                       # :93
coreEnd   ← isTravelDayAssignable ? rawCoreEnd   : period.end                         # :94
coreTotalDays ← diffDays(coreStart, coreEnd) + 1                                      # :95
FOR date:
    slot    ← date < coreStart ? 'pre' : date > coreEnd ? 'post' : 'core'             # :100-102
    isFirst ← slot=='pre' ? TRUE : slot=='post' ? FALSE : (date == coreStart)         # :104
    isLast  ← slot=='post' ? TRUE : slot=='pre' ? FALSE : (date == coreEnd)           # :105
    dayFromEnd ← diffDays(date, coreEnd)                                              # :110
```

**(B) Relative axis (fallback)** — when `period` is absent or the precondition fails (`:117-148`)

```text
lastIndex ← len(dates) − 1
isTravelDayAssignable ← (preCount>0 OR postCount>0) AND (preCount + postCount <= lastIndex)   # :124
coreStart ← isTravelDayAssignable ? preCount           : 0                            # :125
coreEnd   ← isTravelDayAssignable ? lastIndex−postCount: lastIndex                    # :126
coreTotalDays ← coreEnd − coreStart + 1                                               # :127
FOR index, date:
    slot ← index < coreStart ? 'pre' : index > coreEnd ? 'post' : 'core'              # :132-134
    isFirst / isLast are based on the core sub-range index                            # :137-138
    dayFromEnd ← coreEnd − index                                                      # :143
```

**Why the absolute axis is needed** (`calc-day-position.ts:4-9`): the backend's `applyPayOptionDays` **removes dates from `limitAmounts` first**, according to the payment option (`EXCEPT_END` and so on).
If the engine mistakes "the end of the remaining map" for the arrival day, it applies the same rule (such as `excludeArrive`) one more time and the number of paid days shrinks.

Golden case (`calc-condition-engine.test.ts:1032-1050`, TXR 2026-trip-report-887):
period `08-05` to `08-12`, map `08-05..08-11` (08-12 removed by EXCEPT_END), condition `excludeArrive`

| `calcPeriod` | Result |
|-------------|------|
| Present | All 7 days at 70,000 → **total 490,000** (08-11 is not the arrival day on the absolute axis) |
| Absent (fallback) | 08-11 is mistaken for the arrival day → `0` → **total 420,000** |

**Equivalence**: for a response where the map equals the period, the two axes give identical results (`calc-condition-engine.test.ts:1170-1189`).

> An external system does not receive `calcPeriod` in the response — this field is a **front-end-only field that the front end stamps** from the request's `bstrDate` to `bstrEndDate` (`renewal-limit-api.ts:368-374`, `:480`). So an external system must **use its own request's trip start and end dates as the absolute axis.** Omitting this and falling back to the map index reproduces exactly the discrepancy in the table above (490,000 vs 420,000) on any response where a payment option was applied.

---

## 4. `dailyDiff` tiered application (first / middle / last day)

A condition whose `calcMethod` is `dailyDiff` **does not go through `applyOperator`** — `calculateAmount` branches to `applyDailyDiff` (`calc-condition-engine.ts:157-159`).
**`operator` is ignored** (`:60` "dailyDiff: tiered application by first/middle/last day (operator ignored)").
A test pins this: a `dailyDiff` condition with `operator:'x'` is calculated by rate, not by `×` (`calc-condition-engine.test.ts:194-209`).

### 4-1. Mode branching and field names

`details.diffType` decides the mode (`calc-condition-engine.ts:85`).
Value set: `pct` (rate %) and `amt` (amount) — `bstr-expense-rule/constants/calc-constants.ts:253-256`.

| Mode | `diffType` | First day | Middle days | Last day | Base amount | Field location |
|------|-----------|------|--------|---------|---------|----------|
| Amount | `'amt'` | `details.diffFirstAmt` | `details.diffMidAmt` | `details.diffLastAmt` | `details.diffBaseAmt` | `details` JSON (number, ± allowed) |
| Rate | `'pct'`, unset, or anything else | `cond.diffFirst` | `cond.diffMid` | `cond.diffLast` | `details.diffBaseAmt` | Rates are at the condition's **top level** (string); only the base amount is in `details` |

Type evidence: `renewal-limit-api.ts:179-184` (`diffType`, `diffBaseAmt`, `diffCurrency`, `diffFirstAmt`, `diffMidAmt`, `diffLastAmt`) and `:233-235` (`diffFirst`, `diffMid`, `diffLast` — top-level strings).

> **The rate values live at the condition's top level, not in `details`, while the amount values live inside `details`.** The field names differ across the two axes (`diffFirst` vs `diffFirstAmt`).

### 4-2. `amt` (amount) mode

`calc-condition-engine.ts:90-104`.

```text
diffAmt ← isFirst ? details.diffFirstAmt
                  : isLast ? details.diffLastAmt
                           : details.diffMidAmt                                       # :91-95
base    ← details.diffBaseAmt ?? amount   // the admin fixed value wins; else that day's base  # :97
IF diffAmt == null OR NOT isFinite(diffAmt):
    RETURN max(0, base)                        // adjustment 0 — base is kept          # :102
RETURN max(0, base + diffAmt)                                                         # :103
```

**Base-amount priority**: `details.diffBaseAmt` (the exception base amount entered by the admin), or that day's base amount when absent (`null`/`undefined`).
`diffBaseAmt` of `0` is a valid setting (`??` lets `0` through).

**`max(0, …)` truncation**: negative results are clamped to 0.

**The unset-tier rule** (comment at `:98-101`): if there is no adjustment for that day, it returns `max(0, base)`.
If you entered a `diffBaseAmt`, that base amount comes out; if you did not, that day's rule base is kept.
Without this rule you would get the inconsistency "I entered a base amount but the middle days still show the rule amount."

Verification (`calc-condition-engine.test.ts`):

| Case | Input | Result | Line |
|--------|------|------|----|
| Negative clamp | base 40,000, `diffMidAmt=-50000` | **0** | `:256-258` |
| Positive addition | base 40,000, `diffFirstAmt=60000` | 100,000 | `:259-261` |
| Tier unset | base 40,000, no adjustment | 40,000 | `:268-270` |
| Non-finite value | `diffMidAmt=NaN` / `Infinity` | 40,000 (base kept) | `:292-297` |
| Base amount wins | base 40,000, `diffBaseAmt=100000`, `diffFirstAmt=+5000` | **105,000** (the rule's 40,000 is ignored) | `:301-303` |
| Base amount + negative adjustment | `diffBaseAmt=100000`, `diffLastAmt=-5000` | 95,000 | `:304-306` |
| `diffBaseAmt=0` + unset tier | rule base 10,000, `diffBaseAmt=0` | **0** (not the rule's 10,000) | `:314-316` |
| Legacy rate value mixed in | `diffType='amt'` + `diffMid='50'` + no `diff*Amt` | 40,000 (the rate is not applied) | `:287-289` |

### 4-3. `pct` (rate) mode

`calc-condition-engine.ts:106-114`.

```text
rateStr ← isFirst ? cond.diffFirst : isLast ? cond.diffLast : cond.diffMid            # :107
IF rateStr is falsy:  RETURN amount            // '' , null, undefined → keep base     # :108
rate ← parseFloat(rateStr)                     // ★ not parseValue                     # :110
IF isNaN(rate):       RETURN amount            // NaN → keep base (not a ×0)           # :111
base ← details.diffBaseAmt ?? amount                                                  # :113
RETURN base × (rate / 100)                                                            # :114
```

- **It does not use `parseValue` (NaN→0).** An empty or NaN rate keeps the base rather than becoming `0` (comment at `:109`).
  The reason: `×0` means "unpaid" while an empty value means "not entered," and conflating the two would make the amount vanish to 0.
- `rateStr = '0'` is a truthy string so it passes → `rate=0` → the result is 0. (`''` and `'0'` are different.)
- **There is no `max(0, …)` clamp** — unlike `amt` mode. A negative rate produces a negative result.
- The result stays in floating point. `75 × 50% = 37.5` comes out exactly (`calc-condition-engine.test.ts:747-778`).

**Why `pct`'s base is `diffBaseAmt` rather than that day's amount** — the source comment (`:66-71`) is the source of truth:

> "Base amount = details.diffBaseAmt (the admin fixed value). When absent, that day's base amount (legacy back-compat).
> The pct + diffBaseAmt combination uses a fixed base amount independent of the accumulated amount.
> **Rule: the reason pct's base is diffBaseAmt rather than that day's amount is that a tiered rate is a rate against a fixed base amount unrelated to accumulation effects (revision specification §4-1).**"

In other words, when `diffBaseAmt` is set, the rate is applied to the fixed base amount, **ignoring the accumulated result of the preceding conditions.**

Verification:

| Case | Input | Result | Line |
|--------|------|------|----|
| `diffBaseAmt` wins | accumulated 40,000, `diffBaseAmt=100000`, `diffMid='50'` | **50,000** (40,000 ignored) | `:253-255` |
| No `diffBaseAmt` | 40,000, `diffFirst='70'` | 28,000 | `:265-267` |
| `diffType` unset (legacy) | 40,000, `diffMid='50'` | 20,000 | `:262-264` |
| Last-day tier | `diffBaseAmt=100000`, `diffLast='70'` | 70,000 | `:282-284` |
| All tiers null | `diffFirst/Mid/Last=null` | Base kept | `:491-503` |

### 4-4. `isFirst` / `isLast` priority and a 1-day trip

The three-way ternary checks `isFirst` first (`:91-95`, `:107`).
So **when `isFirst && isLast` (a 1-day trip = a 1-day core), the first-day tier wins.**
Verification: 1-day trip with `diffFirst='70'`/`diffMid='50'`/`diffLast='30'` → **70** (`calc-condition-engine.test.ts:210-222`).

### 4-5. Interaction between multiple `dailyDiff` conditions and excluded days

Because they are invoked sequentially in `sortOrder` order, a later condition receives the earlier condition's result as its base (comment at `:73-77`).
Even a day zeroed out by an exclusion condition (`excludeDepart`/`excludeArrive`/`excludeN`) **can be refreshed by a subsequent `dailyDiff` condition using `amount=0` as its base** — the later condition in `sortOrder` wins.
The source states this is **intended behavior (confirmed by the user)** (`:74-77`). To control it, place the `sortOrder` below the exclusion condition or split the conditions.
Note, however, that a `dailyDiff` with `diffBaseAmt` set ignores accumulation (§4-3) and escapes this interaction.

---

## 5. Travel-day adjustment (`TravelDayOpts`)

### 5-1. What `TravelDayOpts` is

`calc-condition-engine.ts:288-297`.

| Field | Type | Meaning |
|------|------|------|
| `preCount` | `0 \| 1` (default 0) | Number of prior travel days |
| `postCount` | `0 \| 1` (default 0) | Number of following travel days |
| `predepartType` | `string \| undefined` | The **string representation** of the full-day-departure `item.id`. Conditions whose `itemType` is this value are applied to the prior travel day |
| `nextarriveType` | `string \| undefined` | The string representation of the next-day-arrival `item.id` |

With `preCount = postCount = 0` (or no `opts` passed), the behavior is **100% identical** to the existing logic (`:282-283`).

**Where opts come from** — `detectTravelDays` (`bstr-policy/utils/calc-inputs.ts:239-272`) detects them from the trip input items (`issuedItems`):

```text
FOR issued IN issuedItems:
    IF issued.item.itemType !== 'EXPENSE_BEYOND_BSTR_PERIOD':  CONTINUE               # :252
    IF issued.value !== 'true':                                CONTINUE               # :253
    erpCode ← issued.item.erpCode  (must be a string)                                 # :256-258
    itemId  ← issued.item.id       (must be a number)                                 # :259-260
    IF erpCode == 'PREDEPART':   pre  = 1 ; predepartItemId  = itemId                 # :262-264
    IF erpCode == 'NEXTARRIVE':  post = 1 ; nextarriveItemId = itemId                 # :265-267
```

ERP code constants: `PREDEPART` and `NEXTARRIVE` (`calc-inputs.ts:221-222`).
`TravelDayOpts` conversion: `cloud-expense-report/features/build-travel-day-opts.ts:17-26` (returns `undefined` when there are no travel days).

### 5-2. Where and how it is applied

```text
IF isTravelDayAssignable:                                                             # :371-379
    preConds  ← candidates where ∃ item : String(item.itemType) == predepartType
    postConds ← candidates where ∃ item : String(item.itemType) == nextarriveType
    coreConds ← candidates where neither
ELSE:
    preConds = postConds = [] ; coreConds = candidates
```

`hasItemType` (`:349-352`): if `t` is falsy (`undefined`, `''`), it is **always false**.
The comparison is `String(it.itemType) === t` — **strings on both sides**.
Passing a number as `predepartType` will not match (`calc-condition-engine.test.ts:812-827`).

| Slot | Conditions applied | `isFirst` | `isLast` | `isDayCovered` |
|------|----------|----------|---------|---------------|
| `pre` (prior travel day) | `preConds` | **`true`** | `false` | **Skipped** (`pos` not passed) |
| `post` (following travel day) | `postConds` | `false` | **`true`** | **Skipped** |
| `core` | `coreConds` | `pos.isFirst` | `pos.isLast` | Applied |

The reason `isDayCovered` is skipped: to avoid the index trap where `i - coreStart` goes negative on the relative axis (`:308-309`, `:412`).

Since the travel-day slot fixes `isFirst`/`isLast`, **a travel-day condition that is a `dailyDiff` uses only one of the 3 tiers**:
`pre` → `diffFirst` (or `diffFirstAmt`), `post` → `diffLast` (or `diffLastAmt`) (`:414`, `:419`).
Verification: a next-day-arrival `dailyDiff` of `100/100/50` → last day **37.5** (75×50%), `calc-condition-engine.test.ts:747-778`.
A full-day-departure `dailyDiff` of `50/100/100` → first day **37.5** (`:780-810`).

**The assignment guard (a real INNOTEK incident)** — if the travel-day count eats into the day count and the core range becomes empty, `isTravelDayAssignable = false` and **all days are reverted to core** (`calc-day-position.ts:91-94`, `:124-126`).
Measured: a 2-day map with `pre/post=1` → `coreStart(1) > coreEnd(0)` → both days were misassigned as full-day-departure and next-day-arrival, shrinking the per diem from **211,216 KRW to 30,000 KRW** (`calc-condition-engine.ts:355-360`).
Regression gate: `calc-condition-engine.test.ts:979-986`.
Boundary: assignment still happens if even 1 core day remains (`:987-994`).

### 5-3. ⚠️ Per diem (`DAILY_COST`/`HD_DAILY_COST`) defers application at fetch time — never apply twice

`renewal-limit-api.ts:434-458`.

```text
FUNCTION isDailyCostTranKind(response, requestTranKindType) -> boolean                # :434-440
    tranKindType ← requestTranKindType ?? response.tranKindType
    RETURN tranKindType == 'DAILY_COST' OR tranKindType == 'HD_DAILY_COST'

FUNCTION applyValidatedCalcConditions(response, requestTranKindType):                 # :442-458
    parsed ← RenewalLimitCalcFieldsSchema.safeParse(response)
    IF NOT parsed.success:              RETURN response      // boundary check failed → pass through  # :447
    IF hasDeferredForeignCalc(response): RETURN response     // foreign-currency deferral — §6        # :449
    IF isDailyCostTranKind(...):                                                      # :453-456
        applied ← applyCalcConditions(response)              // once, without opts
        RETURN { ...response,
                 calcBreakdown: applied.calcBreakdown,       // take only the tooltip data
                 calcDeferred : TRUE }                       // limitAmounts stays raw
    RETURN applyCalcConditions(response)                     // non-per-diem: apply at fetch time
```

**Why it defers** (comment at `:426-432`): per diem must have its full-day-departure / next-day-arrival `opts` applied at the **consumption point** (`ruled-amount-calculator` path C) via `applyCalcConditions(res, opts)`.
Pre-modifying `limitAmounts` at fetch time without `opts` makes the idempotency guard (§1-4) block re-application with `opts`, so **travel-day assignment is lost.**

> **⚠️ Applying twice throws off the amount.** In a `calcDeferred=true` response, `limitAmounts` is the **raw base amount** while `calcBreakdown` is **already built.** Judging "already applied" from the presence of `calcBreakdown` alone means per diem never gets its conditions applied at all; conversely, treating it as raw and applying twice puts `×50%` on twice and yields 25%. The determination must be **`calcBreakdown present && !calcDeferred`** (`daily-cost-amount-utils.ts:240`, `:278`, `daily-cost-breakdown.ts:132`).

In summary:

| `tranKindType` | Currency | At fetch time | `limitAmounts` | `calcDeferred` | At consumption time |
|---------------|------|-----------|---------------|---------------|----------|
| Anything but per diem | KRW | Engine applied | Final amount | (absent) | Not re-applied |
| `DAILY_COST`, `HD_DAILY_COST` | KRW | `calcBreakdown` only | **raw** | `true` | Applied once, together with `opts` |
| Anything | Foreign currency (or foreign diff/fixed) | **Deferred** (§6) | **raw** | (absent) | Convert → re-apply |

---

## 6. The foreign-currency path

### 6-1. Deferral determination — `hasDeferredForeignCalc`

`renewal-limit-api.ts:416-423`.

```text
FUNCTION hasDeferredForeignCalc(response) -> boolean
  IF response.calcEnabled === false:  RETURN FALSE                                    # :417
  hasActiveCond ← ∃ cond ∈ (response.appliedConditions ?? []) : cond.matched AND NOT cond.overridden   # :418
  IF isForeignCurrencyCode(response.currencyCode):  RETURN hasActiveCond   // case ②   # :420
  RETURN collectDiffCurrencies(response).length > 0                         // case ①   # :422
```

| Case | Condition | Source |
|--------|------|------|
| **②** Foreign-currency rule | `currencyCode` is not `null`, `''`, or `'KRW'` **AND** `calcEnabled !== false` **AND** at least 1 condition has `matched && !overridden` | `:412`, `:420`, `currency-utils.ts:19-22` |
| **①** KRW rule + a foreign-currency condition value | Even on a KRW rule, at least 1 active condition has a foreign `details.diffCurrency` or a foreign `fixedCurrency` | `:413-414`, `:422`, `diff-currency-converter.ts:34-49` |

`collectDiffCurrencies` (`diff-currency-converter.ts:34-49`) collects only from active (`matched && !overridden`) conditions, and looks at `fixedCurrency` only when `operator ∈ {fixed, silbiLimit}` (`usesFixedAmountField`, `:26-29`).

> "Active" here means `matched && !overridden`. By contrast, **the engine's candidate selection uses `matched` only**, and determines `overridden` per day (§1-1, §1-3). The two criteria differ — do not conflate them.

### 6-2. The consuming path — per-date conversion, then `applyCalcConditionsKRW` re-application

`applyCalcConditionsKRW(response, krwBaseMap, opts, labelOpts)` **replaces** `limitAmounts` with `krwBaseMap` and then runs the same engine (`calc-condition-engine.ts:465-472`).
Conversion (foreign currency → KRW) is **the caller's responsibility** — because the exchange-rate reference date differs per path — and this function **is not currency-aware** (`:461`).

The actual order (`cloud-expense-report/utils/daily-cost-amount-utils.ts:321-418`, comment at `:307-311`):

```text
1. Determine whether an active formula exists — if not, null (scalar fallback)          # :338-355
2. Look up the diffCurrency/fixedCurrency exchange rates → build a rule copy whose
   diff*Amt, diffBaseAmt, and fixedAmount are converted to KRW via applyDiffCurrencyToRule  # :360-374
3. If the rule's currency is foreign, convert limitAmounts per date via exchangeToKRW to
   build krwBaseMap (falls back to null on a rate failure, to prevent currency mixing)   # :375-398
   If KRW, krwBaseMap = { ...limitAmounts }                                              # :399-402
4. applyCalcConditionsKRW(convertedRule, krwBaseMap, opts)  ← the first condition application  # :404
5. Filter to eligibleDates, then Math.trunc per date to drop sub-KRW fractions → sum       # :406-…
```

The 3 axes (`currencyCode`, `details.diffCurrency`, `fixedCurrency`) are **mutually independent** and each is looked up in its own currency, so there is no double conversion (`diff-currency-converter.ts:64-72`).

### 6-3. ★ Why the order must not be reversed (apply conditions → convert)

> **This section applies to the amount-derivation path only.** The on-screen calculation-detail tooltip (`cloud-expense-report/utils/daily-cost-breakdown.ts:101-105`) applies conditions **on the original-currency base without conversion**, even for foreign-currency rules — that is display-only and is a settled specification.
> Do not read that code alone as "conversion is unnecessary."

Three separate factors each break the result independently.

**(1) The engine is not currency-aware — different currencies get mixed into one expression.**
`calc-condition-engine.ts:461`: "Conversion (foreign currency → KRW) is the caller's responsibility (the exchange-rate reference date differs per path). This function is not currency-aware."
The condition operands carry **their own currency, independent of the rule's currency** — `details.diffCurrency` (tiered amounts) and `fixedCurrency` (fixed / actual-cost-capped) (`diff-currency-converter.ts:3-5`, `renewal-limit-api.ts:232`, `:181`).
Replacing a USD base amount with a KRW `fixedAmount` via `fixed`, or adding a KRW `diffFirstAmt`, becomes **unitless number arithmetic** and the value is meaningless. That is why `applyDiffCurrencyToRule` brings the condition's amount fields into KRW **before entering the engine** (`diff-currency-converter.ts:5-6`).

**(2) The `operatorValue` of `+`/`-` has no currency field at all.**
`calc-condition-labels.ts:150-154`: "The condition schema has only `fixedCurrency` and `details.diffCurrency`; there is **no** currency field corresponding to `operatorValue` (the operand of ±)."
So the schema cannot determine whether `+10000` is 10,000 KRW or 10,000 USD, and even for display the engine has to inject the rule's currency (`calc-condition-engine.ts:322-328`). This operation is **defined only on a KRW base.**
Adding on a foreign-currency base first leaves no way to know afterward what currency the value is in.

**(3) Non-linear operations mean conversion does not commute.**
`exchangeToKRW` truncates sub-KRW fractions with `Math.trunc` and divides the 100-unit currencies (JPY, IDR, VND) by 100 (`currency-utils.ts:11`, `:48-57`). On the engine side there are two `max(0, …)` clamps (`-`: `calc-condition-engine.ts:140`; `amt` tiered: `:102-103`).
`trunc` and `max(0,·)` are not linear, so `convert(f(x)) ≠ f(convert(x))`.
Example: an amount pinned to 0 by `-` is still 0 after conversion, but going negative in the foreign currency first and then converting changes when the clamp happens.
`Math.trunc` likewise makes the total diverge depending on which stage discards the fractional remainder (comment after `daily-cost-amount-utils.ts:406`: "the engine does not round → per-date KRW truncation, then sum").

So the order is fixed as **convert → apply conditions → (at the display stage) per-date `Math.trunc`.**

Verification (`calc-condition-engine.test.ts:520-553`):

| Case | Input | Result |
|--------|------|------|
| ADD | USD 100 original, `krwBaseMap=130000`, `+50000` | **180,000** (the foreign original is ignored) |
| MULTIPLY (%) | `krwBaseMap=130000`, `×50%` | 65,000 (rates are currency-neutral) |
| No conditions | `krwBaseMap=130000` | 130,000 (pass-through) |
| Travel-day `opts` passed | USD 75×1300 = 97,500, pre condition `×50%` | First day 48,750 / core 97,500 (`:831-854`) |

---

## 7. `calcBreakdown` — the calculation detail (a structure for checking your work)

This is the calculation detail the front end builds and renders in the on-screen tooltip. An external system can use it as a reference when checking its own calculation.

### 7-1. Structure

`CalcBreakdownSchema` (`renewal-limit-api.ts:287-295`); built by `buildBreakdown` (`calc-condition-engine.ts:264-278`).

| Field | Type | Value |
|------|------|----|
| `baseAmount` | number | `resolveEffectiveBaseAmount(appliedList, response.limitAmount ?? 0)` — §7-2 |
| `items` | `CalcBreakdownItem[]` | The conditions actually applied, in ascending `sortOrder` (`:438-439`) |
| `totalAmount` | number | The sum of the per-date final amounts, `sum(values(finalMap))` (`:437`) — **no rounding** |

`items` holds **only the conditions that survived the per-day fallback determination** (the `appliedConds` set, `:397`, `:226`).
A condition that was a candidate but yielded on every day does not appear.
Example: if the target dates for a post condition are absent from the map, `items` holds only the 1 pre condition (`calc-condition-engine.test.ts:1138-1168`).

`CalcBreakdownItem` — built by `buildBreakdownItem` (`calc-condition-labels.ts:234-249`); schema at `renewal-limit-api.ts:253-280`:

| Field | Required | Built by | Example |
|------|------|------|------|
| `label` | Yes | `buildLabel` (`calc-condition-labels.ts:172-177`) — joins the condition item labels with `·`. `'기본'` (default) when there are no items | `평일` (weekday), `국내출장·렌트카` (domestic trip · rental car) |
| `effect` | Yes | `buildEffect` (`calc-effect-labels.ts:71-151`) — the meaning of the operation in one line | `+10,000원`, `변경없음` (no change), `×50%`, `정액 50,000원` (fixed 50,000), `실비(제한) 16,500원` (actual cost capped at 16,500), `미지급(0원)` (unpaid), `차등 100/50/100%` (tiered), `차등 +5,000원/0원/-5,000원, 기준 100,000원`, `익일도착일 50%` (next-day arrival 50%) |
| `detailLabel` | Optional | `detailLabelItems.join(', ')` | `전일출발 Yes, 합숙여부 No` (full-day departure Yes, group lodging No) |
| `detailLabelItems` | Optional | `buildDetailedLabelItems` (`:214-221`) — the per-item element array | `['출발시각 13:00 이상']` (departure time 13:00 or later) |
| `formula` | Optional | `buildFormula` (`calc-formula-labels.ts`) — **payment period + derived value** | `출발일·종료일 지급. 정액 20,000 KRW`, `차등. 기준금액 30,000 KRW. 첫날 100% 중간일 50% 마지막날 100%` |

Notes:

- `effect` is **only the meaning of the operation, not the final amount.** `-5,000원` denotes a subtraction operation, and the engine actually uses `max(0, amount − 5000)` (`calc-effect-labels.ts:11-12`).
- Do not re-split `detailLabel` on `', '` — transport-mode items join their own values with `', '` (`렌트카, 택시` = rental car, taxi). For vertical layout, use `detailLabelItems` (`renewal-limit-api.ts:266-269`).
- `formula` is **deliberately different notation** from `effect`. It prefixes the payment period and shows all 3 tiers even for a travel-day-only condition (hence it does not pass `travelDaySlot` — `calc-condition-labels.ts:245-247`).
- `effect`, conversely, shows **only the tier actually used** once the travel-day slot is fixed (`차등 100/100/50%` → `익일도착일 50%`, `calc-effect-labels.ts:67-69`, `:87-98`).

### 7-2. `baseAmount` may not be `limitAmount`

`resolveEffectiveBaseAmount` (`calc-condition-engine.ts:253-262`):

```text
effective ← fallback (= response.limitAmount ?? 0)
FOR cond IN active (ascending sortOrder):
    IF normalizeMethod(cond.calcMethod) != 'dailyDiff':  CONTINUE
    IF cond.details?.diffBaseAmt != null:  effective ← cond.details.diffBaseAmt   // including 0
RETURN effective
```

When a tiered condition sets an exception base amount (`details.diffBaseAmt`), the engine ignores the rule base and calculates with that value (§4), so the calculation detail's "base amount" is aligned to **the value actually used in the calculation**. When several tiered conditions each carry a `diffBaseAmt`, the **last one by `sortOrder`** is adopted.

Verification: `diffBaseAmt=100000` → `baseAmount = 100000` (not the rule's 40,000, `calc-condition-engine.test.ts:419-433`); unset → `baseAmount = 40000` (`:434-447`).

### 7-3. When there are 0 candidate conditions

If no condition is `matched`, `limitAmounts` is left untouched and only `calcBreakdown` is built —
`{ baseAmount: response.limitAmount ?? 0, items: [], totalAmount: sum(baseMap) }` (`calc-condition-engine.ts:335-340`).

---

## 8. Reproduction checklist (for external systems)

> **After implementing, compare against the 71 entries in `규정금액_골든벡터.json`.** There are points where an implementation diverges even when this section's rules are understood correctly — particularly the value source for `fixed` and `silbiLimit` (`fixedAmount`), the meaning of a `calcMethod` coverage failure (that day becomes 0), the scope when `dayTypeMap` is missing, and the revert on travel-day assignment failure.
> The vectors are grouped so you can see immediately **which rule diverged.**

1. `calcEnabled === false` or no `appliedConditions` → **the base amount unchanged.**
2. Take only `matched === true` as candidates and sort ascending by `sortOrder`. **Do not pre-filter with `overridden`** (§1-3).
3. Condition loop inside the date loop. Accumulate only within a single day.
4. For each condition, apply the 3 guards `dayType`, `dateRange`, and `periodRange` (with `dayNum` based on the map index) → **skip** if any fails.
5. Determine whether it yields that day via `supersededByIds` → skip if it does.
   **However, for a response without `supersededByIds` (older backend), treat `overridden === true` as globally not applied** (`calc-condition-engine.ts:189`). If you do not split these two cases, an older response will apply conditions that should have yielded.
6. **When travel days (full-day departure / next-day arrival) exist, split the conditions three ways** — `preConds` (conditions carrying the full-day-departure item), `postConds` (next-day arrival), and `coreConds` (the rest) (`calc-condition-engine.ts:371-379`) — and apply only that slot's conditions in the travel-day slots. In a travel-day slot, `isFirst`/`isLast` are fixed and the `isDayCovered` determination is skipped (`:411-434`). **If there is no room to assign, abandon assignment entirely and revert all days to core** — in that case the pre/post conditions are applied as core conditions.
   The same revert happens if you do not pass opts at all (the conditions do not disappear).
7. On a core day, run the `calcMethod` coverage determination → **if not covered, set that day's amount to 0** (not a skip).
   If `excludeN`'s `excludeDays` is `null`, it falls back to `0` and thus **covers every day** (`calc-day-position.ts:162`).
8. For `dailyDiff` use §4; otherwise use the `operator` rules in §2.
9. **Do not round.** Apply per-date `Math.trunc` at the display and validation stage.
10. When foreign currency is involved, keep the §6 order (convert → apply conditions).
11. Calculate **exactly once** (§1-4, §5-3).
12. If a `dayIndex` (day option) condition arrives, **apply it to every day** — it is an unimplemented item, so the backend passes it unconditionally and the front end has no guard (see open item #3). Do not interpret it as a day-number restriction.

---

## Test comparison

Compared against `packages/domains/src/bstr-policy/utils/calc-condition-engine.test.ts` as the source of truth.

- **Run result**: `vitest run …/calc-condition-engine.test.ts` → **all 91 cases pass** (1 file, run 2026-09-08).
- **18 describe blocks compared (91 cases total)**: guards (2), note §5 example 1 (3), order dependency (2), operator (12), calcMethod date coverage (5), dailyDiff (2), dailyDiff rate/amount modes (18), dayType matching (4), dateRange (4), override/breakdown (4), items null guard (5), `applyCalcConditionsKRW` (3), travel-day opts (9), KRW opts passing (1), per-day fallback (3), travel-day assignment guard (3), silbiLimit (3), absolute period axis (8).

### Items corrected — 2

In both cases **the authoring brief's premise differed from the source and tests**, so the document was corrected with the source and tests as the source of truth.

| # | Brief's premise | Actual source and tests | Source-of-truth evidence |
|---|-----------|----------------|----------|
| 1 | "Candidate filter: apply only conditions that are `matched && !overridden`" | The engine filters on **`matched` only** (`calc-condition-engine.ts:332-333`), and `overridden` is determined **per day** by `isSuppressedOnDay` (`:187-191`). When `supersededByIds` is present, even an `overridden=true` condition **is applied** on a day where no superseder applies. `matched && !overridden` is only the "active" criterion for the foreign-currency deferral determination (`hasDeferredForeignCalc`) and `collectDiffCurrencies`, not the engine's candidate criterion. | Test `:873-890` — condition 4 is the only case with `overridden: true` + `supersededByIds: [3]`. Filtering with `matched && !overridden` drops condition 4 and the 2 weekend days diverge from `15,600` to `23,000` |
| 2 | "An idempotency guard — the rule that a response already applied (`calcBreakdown` present) is not re-applied" | The engine has **no** idempotency guard. The guard lives in the consuming path and its formula is `calcBreakdown` present **AND `!calcDeferred`**. Judging by `calcBreakdown` alone lets per diem (`calcDeferred=true`, `limitAmounts` raw) pass through with **no conditions applied** | `daily-cost-amount-utils.ts:240`, `:278`, `daily-cost-breakdown.ts:132`, and tests `daily-cost-breakdown.test.ts:366`, `daily-cost-amount-utils.test.ts:656` |

### Formulas that diverged — 0

The 8 operators in §2, the coverage determinations in §3, and the `amt`/`pct` formulas for `dailyDiff` in §4 all matched the test expectations.
The following values in particular were settled by comparison.

| Statement in this document | Test expectation | Line |
|----------|-------------|----|
| `silbiLimit` is a replacement, not a `min` | base 10,000 + limit 16,500 → **16,500** | `:1020-1022` |
| `+` ignores `operatorUnit` | `+10000` / `PERCENT` → 60,000 | `:158-159` |
| An empty or NaN `pct` keeps the base, not 0 | `diffFirst/Mid/Last=null` → 100 kept | `:491-503` |
| A non-finite `amt` also keeps the base | `NaN`, `Infinity` → 40,000 | `:292-297` |
| `isFirst` takes precedence over `isLast` | 1-day trip, 70/50/30 → **70** | `:210-222` |
| `isDayCovered` false = 0 (not a skip) | `departArrive` over 3 days → `[100, 0, 100]` | `:173-176` |
| No rounding | 75 × 50% → **37.5** | `:747-778`, `:780-810` |
| The presence of `calcPeriod` changes the result | 490,000 vs 420,000 | `:1032-1064` |

---

## Open items

| # | Item | Status |
|---|------|------|
| 1 | The UPPER_SNAKE alias for `silbiLimit` | ⚠️Unconfirmed — the `normalizeOperator` map (`calc-condition-engine.ts:41-49`) has **no** `SILBI_LIMIT` entry. An exhaustive grep of the backend repository found **zero** occurrences of the strings `silbiLimit` or `SILBI`, so it is unconfirmed what notation the backend would send this value in. The admin-saved value is the lowercase `silbiLimit` (`calc-constants.ts:243`), and `operator` round-trips verbatim as a `String` column with no enum conversion (`BstrCalcCondition.java:62-63`, `AppliedConditionDto.java:25`, `:84`) → so it is settled that **there is currently no path by which an uppercase `SILBI_LIMIT` could arrive.** If one did, the amount would be left unchanged |
| 2 | ~~The full `operatorUnit` value set~~ → **Settled** | ⚠️Unconfirmed — **the save and transmit paths use only `PERCENT`, `AMOUNT`, and `null`** — both the admin save (`bstr-expense-rule/views/CalcConditionTab.tsx:520`) and load (`utils/calc-condition-helpers.ts:68`) use only those two values, and an exhaustive backend grep turned up nothing else. The engine's `'%'` is a defensive value with no reachable path → **an external system only needs to handle those two values and `null`** |
| 3 | ~~The `dayIndex` condition~~ → **Settled: unimplemented** | ⚠️Unconfirmed — **it is not yet implemented.** The admin's detail-condition panel renders only a caption instead of an input UI (`bstr-expense-rule/widgets/DetailPanel.tsx:411-415`, "to be implemented later"), the backend passes it unconditionally with `case "DAY_INDEX","dayIndex" -> true` at `BstrRenewalLimitService.java:352` (comment: "the items below are handled during per-day application"), and the front end has no guard. → **The current behavior is that it is saved without `details` and applies to every day.** Do not interpret it as a day-number restriction on your own |
| 4 | The intent behind the overlap of the `dayType` values `주말` (weekend) and `공휴일` (holiday) | ⚠️Unconfirmed — the backend's `buildDayTypeMap` assigns exactly one type with holiday taking precedence (`BstrRenewalLimitService.java:220-226`), so a `주말` condition does not apply to a day that is both a weekend and a holiday. Whether this is the intended specification could not be confirmed against a product-planning document |
| 5 | The intent behind an `overridden` condition being included in `applicableIds` | ⚠️Unconfirmed — `applicableIds` is built looking only at `isApplicableOnDay` (`calc-condition-engine.ts:216-220`), so a condition that itself yields still enters the set. As a result, in an arrangement where A yields to B while B itself yields to C, A can yield simply because "B exists." The chain in the current test (`:906-936`) **actually creates that shape** (on day 1, B, which is C's superseder, yields to A), but C also yields directly to A so the result does not diverge — the test therefore **has no discriminating power** here. Whether this is intended is unconfirmed |
| 6 | ~~`preCount`/`postCount` ≥ 2~~ → **Does not occur on the current path** | ⚠️Unconfirmed — the type is `number` (`calc-condition-engine.ts:290`, `:292`), but **both assembly paths pass through `detectTravelDays`'s `0|1` verbatim** — the expense report at `cloud-expense-report/features/build-travel-day-opts.ts:21-22`, and the plan at `business-plan/utils/bstr-plan-limit-enrich.ts:91-92`. So values of 2 or more do not occur on the current path (only whether such a rule could exist remains unconfirmed) |
| 7 | ~~The `opts` assembly on the plan path~~ → **Settled: identical** | ⚠️Unconfirmed — **it is the same detector** — `business-plan/utils/bstr-plan-limit-enrich.ts:88` calls the identical `detectTravelDays(issuedItems)` and even the `String()` casting at `:90-95` is the same. The differences are that ① the expense-report helper returns `undefined` when pre=post=0 while the plan always builds an object, and ② the plan additionally builds `allowDays:[pre,post]` — **the engine's opts semantics are identical** |
