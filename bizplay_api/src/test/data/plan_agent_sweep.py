import json, sys, urllib.request, urllib.error

API = "http://localhost:8080/api/v1/agent-conversations/bizplay"
RESULTS = []


class P:
    def __init__(self):
        self.sid = None
        self.last = {}

    def turn(self, msg):
        body = {"corpNo": "1234567890", "corpUserId": 30447, "message": msg}
        if self.sid:
            body["sessionId"] = self.sid
        req = urllib.request.Request(API + "/agents/plan", data=json.dumps(body).encode(),
                                     headers={"Content-Type": "application/json"}, method="POST")
        d = json.load(urllib.request.urlopen(req, timeout=180))
        p = d.get("data") or d.get("payload") or {}
        self.sid = p.get("sessionId") or self.sid
        self.last = p
        return p

    def doc(self):
        draft = (self.last.get("draftJson") or [{}])
        return draft[0] if isinstance(draft, list) else draft

    def create(self, lines=None):
        body = {"approvalLines": lines or [{"corporationUserId": 30192, "approvalKindType": "APPROVAL"}]}
        req = urllib.request.Request(API + f"/agents/plan/{self.sid}/create?corpNo=1234567890",
                                     data=json.dumps(body).encode(),
                                     headers={"Content-Type": "application/json"}, method="POST")
        d = json.load(urllib.request.urlopen(req, timeout=180))
        return d.get("data") or d.get("payload") or {}


def check(name, cond, detail=""):
    RESULTS.append((name, bool(cond), detail))
    print(("PASS " if cond else "FAIL "), name, ("| " + str(detail)[:120] if detail and not cond else ""))


which = sys.argv[1] if len(sys.argv) > 1 else "all"

if which in ("all", "dest"):
    # 1. full KO one-shot: everything given, only optional detail should be asked
    p = P()
    r = p.turn("인천에서 오사카로 9월 20일부터 22일까지 해외출장 장기 다녀올게. 출장자는 김충북. 비행기로 갈게.")
    check("oneshot-ko: destination resolved", r.get("destination") == "오사카", r.get("destination"))
    check("oneshot-ko: only optional-detail ask left",
          "상세" in (r.get("reply") or "") or "detail" in (r.get("reply") or "").lower(), r.get("reply"))
    r = p.turn("없어")
    check("oneshot-ko: ready after decline", r.get("status") == "READY_FOR_REVIEW", r.get("status"))

    # 2. no destination -> early region ask; typed country -> city ask; typed city binds
    p = P()
    r = p.turn("9월 20일부터 22일까지 해외출장 장기 다녀올게. 출장자는 김충북. 비행기로 갈게.")
    check("no-dest: region ask first", r.get("intent") == "DESTINATION_ASK", r.get("intent"))
    r = p.turn("스위스")
    check("typed-country: city ask, not complaint",
          r.get("intent") == "DESTINATION_ASK" and "도시" in (r.get("reply") or ""), r.get("reply"))
    r = p.turn("로잔")
    check("typed-city: binds", r.get("destination") == "로잔", r.get("destination"))

    # 3. destination change + negation
    r = p.turn("아 목적지를 베른으로 바꿔줘")
    check("change-to: applied", r.get("destination") == "베른", r.get("destination"))
    r = p.turn("취리히는 아니고 그냥 베른 유지할게")
    check("negation: destination stays", r.get("destination") == "베른", r.get("destination"))

    # 4. copula + 나고야 safety (policy form -> region ask for unlisted is fine; check the VALUE)
    p = P()
    p.turn("인천에서 오사카로 9월 20일부터 22일까지 해외출장 장기 다녀올게. 출장자는 김충북. 비행기로 갈게.")
    r = p.turn("아니다, 목적지는 나고야")
    check("copula-name: 나고야 intact", r.get("destination") == "나고야", r.get("destination"))

    # 5. decline phrases must not clobber destination
    p = P()
    p.turn("인천에서 오사카로 9월 20일부터 22일까지 해외출장 장기 다녀올게. 출장자는 김충북. 비행기로 갈게.")
    r = p.turn("세부 목적지는 없어")
    check("decline-ko: destination stays 오사카", r.get("destination") == "오사카", r.get("destination"))
    check("decline-ko: ready", r.get("status") == "READY_FOR_REVIEW", r.get("status"))

    # 6. late detail edit (EN + KO)
    r = p.turn('add destination detail of "floor 2"')
    check("late-detail-en: captured", (r.get("destinationDetail") or "") == "floor 2", r.get("destinationDetail"))
    check("late-detail-en: destination intact", r.get("destination") == "오사카", r.get("destination"))

if which in ("all", "period"):
    opener = "뉴샤텔로 해외출장 장기 다녀올게. 출장자는 김충북. 비행기로 갈게."

    def period_case(name, answer, exp_start, exp_end):
        p = P()
        p.turn(opener)
        p.turn(answer)
        d0 = p.doc()
        got = (str(d0.get("bstrStartDate"))[:10], str(d0.get("bstrEndDate"))[:10])
        check(name, got == (exp_start, exp_end), f"{got}")

    period_case("period: this month 4-6", "this month from 4 to 6", "2026-09-04", "2026-09-06")
    period_case("period: 다음 주 수-금", "다음 주 수요일부터 금요일까지 갈게", "2026-09-09", "2026-09-11")
    period_case("period: 내일부터 모레", "내일부터 모레까지", "2026-09-02", "2026-09-03")
    period_case("period: single day", "9월 20일 하루만 갈게", "2026-09-20", "2026-09-20")

    # relative edits on an existing period
    p = P()
    p.turn("9월 20일부터 22일까지 뉴샤텔로 해외출장 장기 다녀올게. 출장자는 김충북. 비행기로 갈게.")
    p.turn("시작일을 하루 늦춰줘")
    d0 = p.doc()
    check("date-edit: start +1", str(d0.get("bstrStartDate"))[:10] == "2026-09-21", d0.get("bstrStartDate"))
    p.turn("일정을 이틀 연장해줘")
    d0 = p.doc()
    check("date-edit: end +2", str(d0.get("bstrEndDate"))[:10] == "2026-09-24", d0.get("bstrEndDate"))

    # route-note must not poison the period
    p = P()
    p.turn("9월 11일부터 14일까지 뉴샤텔로 해외출장 장기 다녀올게. 출장자는 김충북. 비행기로 갈게.")
    p.turn("이동경로는 인천-취리히-뉴샤텔이야")
    d0 = p.doc()
    check("route-note: period intact", str(d0.get("bstrStartDate"))[:10] == "2026-09-11", d0.get("bstrStartDate"))

if which in ("all", "misc"):
    # QA during a pending ask (explicit question) + decline phrase with 'detail'
    p = P()
    p.turn("인천에서 오사카로 9월 20일부터 22일까지 해외출장 장기 다녀올게. 출장자는 김충북. 비행기로 갈게.")
    r = p.turn("지금까지 뭐 채워져 있어?")
    check("qa-during-ask: draft answer", r.get("intent") == "DRAFT_QUERY", r.get("intent"))
    r = p.turn("no specific destination detail")
    check("decline-en: not hijacked by QA", r.get("intent") != "DRAFT_QUERY", r.get("intent"))
    check("decline-en: ready", r.get("status") == "READY_FOR_REVIEW", r.get("status"))

    # transport words + origin binding on the route ask
    p = P()
    p.turn("9월 20일부터 22일까지 오사카로 해외출장 장기 다녀올게. 출장자는 김충북.")
    r = p.turn("KTX 타고 서울역에서 출발할 거야")
    reply = (r.get("reply") or "")
    check("route-answer: consumed (no re-ask of transport)", "교통수단" not in reply, reply)

    # session create end-to-end (uses the last ready session)
    p = P()
    p.turn("인천에서 오사카로 9월 20일부터 22일까지 해외출장 장기 다녀올게. 출장자는 김충북. 비행기로 갈게.")
    p.turn("없어")
    c = p.create()
    check("create: POSTED", c.get("status") == "POSTED", c.get("status"))

print()
fails = [(n, d) for n, ok, d in RESULTS if not ok]
print(f"TOTAL {len(RESULTS)} | PASS {len(RESULTS) - len(fails)} | FAIL {len(fails)}")
for n, d in fails:
    print("  FAIL:", n, "|", str(d)[:150])
