# Demo test script — plan to settlement, exercising the 2026-09 updates

Type the **bold** lines into the chat. Everything else is what you should see back.
App at `http://localhost:8080/web/`, tenant `cloud-dev`, corp `1234567890`, user 김충북 (30447).

What this script is checking, in order of how new it is:

| # | Update | Covered in |
|---|---|---|
| 1 | Meal receipts carry the meal classification and the usage date | A4 |
| 2 | Meal limits sum the drafter and the plan's companions | Part B |
| 3 | Foreign ruled amount is blank once a condition applied | A3 |
| 4 | Per-date truncation on the converted limit | A3 |
| 5 | Excess split, announce then confirm, two rows | A5 |
| 6 | Plan pick falls back to a name search when the plan is not in the staged list | A2, B1 |

---

## Part A — full cycle on a new plan

### A1. Create the plan

Tab **Request Plan**.

> **10월 20일부터 22일까지 오사카 협력사 공장 방문 출장 신청할게요**

The agent will ask for whatever it still needs. Answer as it asks:

> **해외출장이고 일반 출장입니다**

> **출발지는 인천공항, 도착지는 오사카입니다**

When it shows the approval line (김도하):

> **이대로 제출해줘**

Expect a document number, `2026-출장계획서-NNNN`. **Write it down**, you need it in A2 and A3.

### A2. Approve it

The plan is DRAFTED. Only APPROVED plans can be settled.

1. Top right, switch the role from **Traveler** to **Admin**.
2. Open the **Approve Plan** tab.
3. Find your document number and press **Approve**.
4. Switch back to **Traveler**.

### A3. Start the settlement and register lodging

Tab **Expense Report**, then **Settle in Chat**.

The chat opens with a list of recent plans. Your new plan may or may not be in it. Either way, type the
document number, which also tests the plan-pick fallback:

> **2026-출장계획서-NNNN**

Expect: "I found this trip — settle it?" with one row. Click the row.

Then:

> **숙박비**

> **기타증빙으로 조회해줘**

No existing receipt will match, so press **Add manual expense** and type:

> **10월 20일부터 22일까지 오사카 호텔 400달러 2박, 스탠다드 룸**

Answer the slots it asks for (currency USD, check-in 2026-10-20, check-out 2026-10-22, room 스탠다드,
star rating, merchant). Confirm with **Register**, and attach any PDF or image when it asks.

**What to check on the reply and the card**

| Field | Expect |
|---|---|
| Policy line in the reply | `한도 USD 100/일` and `적용 조건: 평일 +10,000원` |
| Policy amount | About ₩282,958 for 2 nights, not ₩287,580 |
| Foreign amount column | **Blank.** This is update 3. A condition was applied, so the foreign figure is deliberately not filled |
| Claim amount | Capped at the policy amount, not the spend |

The ₩282,958 figure is the point of update 4. Each day is converted, truncated to whole won, then summed.
The old code rounded once after summing.

### A4. The excess split

The reply ends by announcing the excess and asking. This is update 5.

> **네, 분할해 주세요**

**What to check**

| Field | Expect |
|---|---|
| Registered expenses | Now **two** rows for the same receipt |
| Row 1 | Split shows `policy row`, claim equals the policy amount |
| Row 2 | Split shows `own expense`, claim equals the excess |
| Total | Counted **once**, the original spend, with "2 item(s)" |

To see the refusal path instead, answer **아니요** and then try to submit. It should refuse and ask again.

### A5. Register a meal

> **식비**

> **기타증빙으로 조회해줘**

Press **Add manual expense**:

> **10월 21일 점심 식사 스시집 20000원**

This is update 1. The meal classification is taken from the sentence, so the preview should already say
`which meal: LUNCH` without asking. The card row **Meal** should read `LUNCH`.

To see the chips instead, phrase it without naming the meal:

> **10월 21일 스시집 20000원**

Now it should ask 식사 구분 with chips 아침 / 점심 / 저녁 / 간식 / 야식 / 식사 / 기타.

> Why it matters: before this change the receipt had no meal classification, and BizPlay answered the
> rule lookup with an arbitrary meal row. We measured the same receipt returning a fixed ₩64,522 per day
> without it and the real USD 10 per day limit with it.

### A6. File it

> **이대로 BizPlay에 제출해줘**

> **이대로 제출해줘**

Expect a settlement document number. The Expense Report list should show it as DRAFTED.

---

## Part B — meal companions

Our plan agent cannot add a companion, so use an existing plan that already has one.
**2026-출장계획서-1629** carries 김도하 as a companion.

### B1. Open it

**Settle in Chat**, then:

> **2026-출장계획서-1629**

This plan already has settlements filed against it, so it is hidden from the opening list. It should still
be found, which is update 6.

### B2. Register a meal and read the limit

> **식비**

> **기타증빙으로 조회해줘**

**Add manual expense**:

> **8월 28일 점심 식사 국밥집 20000원**

**What to check**

| Field | Expect |
|---|---|
| Policy line | `(동행 1명 포함, 2인 합산)` |
| Policy amount | About ₩27,206, which is two people at USD 10 per day |
| Excess | **None.** ₩20,000 is under the two-person limit |

This is the whole point of update 2. The same ₩20,000 receipt on a plan **without** a companion has a limit
of about ₩13,147 and would be over, forcing a split. With the companion counted it passes cleanly.

---

## If something looks wrong

- The policy line says nothing about conditions: the rule lookup returned no matched conditions. Check the
  trip dates fall inside the plan period.
- The foreign column shows a number on a receipt that had a condition: update 3 has regressed.
- The meal card row is empty: the meal classification did not reach the receipt, update 1 has regressed.
- The split produces one row, or the total double counts: update 5 has regressed.

The API equivalents of these paths live in `scratchpad/`, notably `draft_by_type.py`,
`excess_split_e2e.py` and `food_companions_e2e.py`.
