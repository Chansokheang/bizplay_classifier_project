# Demo test script — plan to settlement

Every prompt below was run end to end on 2026-09-14. It produced plan `2026-출장계획서-1696` and
settlement `2026-출장정산서-1698`. The expected values are what actually came back, not estimates.

Type the **bold** lines into the chat box. App at `http://localhost:8080/web/`, user 김충북.

What this exercises:

| # | Update | Step |
|---|---|---|
| 1 | Meal receipts carry the meal classification | B6 |
| 2 | Meal limits sum the drafter and the plan's companions | B6 |
| 3 | Foreign ruled amount is blank once a condition applied | B4 |
| 4 | Per-date truncation on the converted limit | B4 |
| 5 | Excess split, announce then confirm, two rows | B5 |
| 6 | Plan pick falls back to a name search | B2 |
| 7 | An expense-kind name does not end the collection | B6 |

---

## Part A — create and approve the plan

### A1. Create

Tab **Request Plan**, press **Chat**.

> **해외출장 일반으로 출장 계획 만들어줘. 2026-11-10부터 2026-11-12까지 오사카로 가고, 출장자는 김충북이야. 제목은 오사카 협력사 방문, 출발지는 인천공항이야**

It fills the form and asks about companions. **Answer with a companion**, which is what makes the meal
test work later:

> **김도하도 같이 갑니다**

Then it asks for the route, the transport, and an optional destination detail:

> **둘 다 비즈플레이에서 출발해서 티엑스알로보틱스(본사)로 갔다가 비즈플레이로 돌아옵니다**

> **비행기로 이동합니다**

> **없습니다**

Then the approval line:

> **김도하 결재로 제출해줘**

Press **Done ✓ — save now**. You should see "Plan draft saved to BizPlay."

> The route picker only offers domestic locations even for an overseas trip. That is the tenant's own
> location master, not a bug in the flow. Pick any of them.

### A2. Approve

The plan is DRAFTED and cannot be settled yet.

1. Top right, switch **Traveler** to **Admin**.
2. Open the **Approve Plan** tab, find your plan, press **Approve**.
3. Switch back to **Traveler**.

Note the document number. Mine was `2026-출장계획서-1696`.

---

## Part B — settle it

Tab **Expense Report**, then **Settle in Chat**.

### B1. Clear any old session

If the chat opens showing receipts from a previous run, press **Start over with another trip** first.

### B2. Pick the plan by document number

> **2026-출장계획서-1696**

Expect the plan to be found and imported, with the expense-type chips below.
This is update 6. It works even when the plan is not in the list the chat opened with.

### B3. Start a lodging receipt

Press **숙박비**, then **Other receipts (기타증빙)**, then **Add manual expense**.

> ⚠️ A form appears as well as the chat. **Type into the chat box at the bottom**, not into the form.

### B4. Enter the hotel

> **11월 10일부터 12일까지 오사카 호텔 400달러 2박, 스탠다드 룸**

The preview should read: merchant 오사카 호텔, 400 USD, in KRW ₩529,136.
Press **Register**, then **Register without an image**.

**What came back**

| Field | Value |
|---|---|
| Policy line | `limit USD 100 per day. Conditions: 평일 +10,000원` |
| Policy amount | **₩284,568** |
| Foreign amount on the card | 400, the **spend**. The ruled amount has no foreign figure, which is update 3 |
| Claim amount | ₩284,568, capped at the policy |

₩284,568 is update 4. BizPlay returns USD 100 per day for two days. Each day converts to 132,284 at
1,322.84 and is truncated, the weekday condition adds 10,000, giving 142,284 a day.

### B5. Split the excess

The reply ends by announcing the excess and asking. This is update 5.

> **네, 분할해 주세요**

Expect `Split done: 오사카 호텔 policy ₩284,568 / own expense ₩244,568` and **two rows** on the card,
one marked `policy row` and one `own expense`, with the total counted once as ₩529,136 over 2 items.

### B6. Add a meal

> **식비**

This is update 7. It must ask **which card types to search**. If it shows the settlement summary
instead, the finish gate has regressed.

Press **Other receipts (기타증빙)**, then **Add manual expense**:

> **11월 11일 점심 식사 오사카 식당 20000원**

The preview should already say `which meal: LUNCH`, taken from your sentence. That is update 1.
To see the chips instead, leave the meal out of the sentence and it will ask, offering
아침 / 점심 / 저녁 / 간식 / 야식 / 식사 / 기타.

Press **Register**, then **Register without an image**.

**What came back**

| Field | Value |
|---|---|
| Policy line | `fixed allowance ₩129,044 per day **for 2 people (companions included)**` |
| Policy amount | ₩129,044 |
| Excess | None. ₩20,000 is well under it |

That is update 2. Without 김도하 on the plan the limit would be ₩64,522, half of this.

### B7. File

Press **Done — no more expenses**, then **Submit to BizPlay**, then **File it as it is**.

Expect a settlement number. Mine was `2026-출장정산서-1698`.

---

## Verifying on BizPlay's side

The filed document should carry three rows, two for the split hotel and one for the meal.

```bash
curl -s "https://cloud-dev.bizplay.biz/api/v2/approval/bstr/plan/152109/pre-receipts/form?corpUserId=30447" \
  -H "Authorization: Bearer $BIZPLAY_DEV_TOKEN" -H "accept: */*" -H "X-RR-MODE: NONE"
```

Replace `152109` with your plan's approval id. What mine returned:

| Receipt | Type | Split | Approved | Ruled | Claimed |
|---|---|---|---|---|---|
| 343420 | ROOM | EXCESS order 0, excessOver false | 529,136 | 284,568 | 284,568 |
| 343420 | ROOM | EXCESS order 1, excessOver true | 529,136 | 284,568 | 244,568 |
| 343421 | FOOD | none | 20,000 | 129,044 | 20,000 |

The two hotel rows share one receipt id, keep the full spend and the full ruled amount, and divide only
the claim. The claims add back to the original spend.

---

## If something looks wrong

| Symptom | Which update regressed |
|---|---|
| 식비 jumps to the settlement summary | 7, the finish gate |
| The meal card row is empty | 1, the meal classification |
| The policy line says nothing about companions | 2, the companion lookup |
| A foreign figure sits next to a conditioned ruled amount | 3 |
| The policy amount is ₩287,580 rather than ₩284,568 | 4, per-date truncation |
| One row after the split, or the total double counts | 5 |

API equivalents live in `scratchpad/`: `draft_by_type.py`, `excess_split_e2e.py`,
`food_companions_e2e.py`.
