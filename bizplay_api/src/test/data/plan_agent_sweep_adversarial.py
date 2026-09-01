import json, urllib.request

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


def check(name, cond, detail=""):
    RESULTS.append((name, bool(cond), detail))
    print(("PASS " if cond else "FAIL "), name, ("| " + str(detail)[:130] if not cond else ""))


# A1. person name containing a region (김도하 vs Doha) as TRAVELER
p = P()
r = p.turn("9월 20일부터 22일까지 오사카로 해외출장 장기 다녀올게. 출장자는 김도하야. 비행기로 갈게.")
check("A1 김도하 traveler ≠ Doha destination", r.get("destination") == "오사카",
      f"dest={r.get('destination')} trav={r.get('travelers')}")

# A2. date edit WHILE the destination ask is pending — date applies, ask survives
p = P()
p.turn("9월 20일부터 22일까지 해외출장 장기 다녀올게. 출장자는 김충북. 비행기로 갈게.")
r = p.turn("출발일을 9월 25일로 바꿔줘")
d0 = p.doc()
check("A2 date edit during region ask: date moved", str(d0.get("bstrStartDate"))[:10] == "2026-09-25",
      d0.get("bstrStartDate"))
r2 = p.turn("로잔으로 갈게")
check("A2 region ask still answerable after", r2.get("destination") == "로잔", r2.get("destination"))

# A3. off-topic question at region ask: nothing binds, the ask survives
p = P()
r0 = p.turn("9월 20일부터 22일까지 해외출장 장기 다녀올게. 출장자는 김충북. 비행기로 갈게.")
r = p.turn("이 계획은 승인까지 얼마나 걸려?")
check("A3 off-topic at ask: destination unchanged & ask pending",
      r.get("destination") == r0.get("destination") and r.get("intent") == "DESTINATION_ASK",
      f"{r0.get('destination')}->{r.get('destination')} intent={r.get('intent')}")

# A4. indefinite date -> clarify, not invented dates
p = P()
r = p.turn("다음 주쯤 뉴샤텔로 해외출장 장기 갈 것 같아. 출장자는 김충북.")
d0 = p.doc()
start = str(d0.get("bstrStartDate"))
check("A4 indefinite: no invented start", start in ("None", "null", "") or "?" in (r.get("reply") or ""),
      f"start={start} reply={(r.get('reply') or '')[:60]}")

# A5. mixed language one-shot
p = P()
r = p.turn("Trip to 오사카 from Sep 20 to 22, 해외출장 장기, traveler 김충북, by plane from Incheon")
d0 = p.doc()
check("A5 mixed-language: dest", r.get("destination") == "오사카", r.get("destination"))
check("A5 mixed-language: period", str(d0.get("bstrStartDate"))[:10] == "2026-09-20", d0.get("bstrStartDate"))

# A6. two travelers
p = P()
r = p.turn("9월 20일부터 22일까지 오사카로 해외출장 장기 다녀올게. 출장자는 김충북이랑 김철수야. 비행기로 갈게.")
trav = r.get("travelers") or []
check("A6 two travelers resolved", "김충북" in trav and "김철수" in trav, trav)

# A7. reversed period
p = P()
r = p.turn("뉴샤텔로 해외출장 장기 다녀올게. 출장자는 김충북. 비행기로 갈게.")
r = p.turn("9월 22일부터 20일까지")
d0 = p.doc()
s, e = str(d0.get("bstrStartDate"))[:10], str(d0.get("bstrEndDate"))[:10]
ok = (s, e) == ("2026-09-20", "2026-09-22") or (s in ("None", "null") and "?" in (r.get("reply") or ""))
check("A7 reversed period: swapped or clarified", ok, f"{s}→{e} reply={(r.get('reply') or '')[:60]}")

print()
fails = [(n, d) for n, ok, d in RESULTS if not ok]
print(f"TOTAL {len(RESULTS)} | PASS {len(RESULTS) - len(fails)} | FAIL {len(fails)}")
for n, d in fails:
    print("  FAIL:", n, "|", str(d)[:160])
