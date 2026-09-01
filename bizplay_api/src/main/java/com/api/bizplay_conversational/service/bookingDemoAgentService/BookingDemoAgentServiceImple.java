package com.api.bizplay_conversational.service.bookingDemoAgentService;

import com.api.bizplay_conversational.model.request.BizplayPlanAgentRequest;
import com.api.bizplay_conversational.model.response.BizplayPlanAgentResponse;
import com.api.bizplay_conversational.model.response.TripPlanAgentResponse;
import com.api.bizplay_conversational.service.slotFillerAgentService.SlotFillerAgentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DEMO ONLY - a booking agent over dummy inventory. See {@link BookingDemoAgentService}.
 *
 * <p>Three domains, each with its own intent so the UI knows which card to draw: RAIL (KTX/SRT),
 * FLIGHT and ACCOMMODATION. The conversation is the three beats every real booking has - work out
 * what is being booked, offer what is available, confirm before spending - and the response is the
 * same shape the plan and settlement agents already return, so the existing chat UI renders it
 * with no new code.
 *
 * <p>State lives in memory on purpose: a demo that writes to the database leaves rows to clean up,
 * and this whole package is meant to be deleted. Sessions do not survive a restart.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingDemoAgentServiceImple implements BookingDemoAgentService {

    private final ObjectMapper objectMapper;
    private final SlotFillerAgentService slotFillerAgentService;

    /** sessionId -> the booking being assembled. Demo-only; lost on restart, by design. */
    private final Map<String, ObjectNode> sessions = new ConcurrentHashMap<>();

    /** One line of dummy inventory. */
    private record Offer(String id, String operator, String detail, String from, String to, long amount) { }

    private static final List<Offer> RAIL = List.of(
            new Offer("KTX101-0600", "코레일", "KTX 101 · 일반실", "06:00", "08:42", 59800),
            new Offer("KTX105-0730", "코레일", "KTX 105 · 일반실", "07:30", "10:12", 59800),
            new Offer("SRT301-0710", "SR", "SRT 301 · 일반실", "07:10", "09:38", 52600));

    private static final List<Offer> FLIGHT = List.of(
            new Offer("KE721-0905", "대한항공", "KE721 · 일반석", "09:05", "10:55", 287000),
            new Offer("OZ112-1420", "아시아나", "OZ112 · 일반석", "14:20", "16:05", 264000),
            new Offer("LJ203-1810", "진에어", "LJ203 · 일반석", "18:10", "19:55", 198000));

    private static final List<Offer> ROOM = List.of(
            new Offer("OSKBAY-STDTWIN", "Osaka Bay Hotel", "Standard Twin", null, null, 147744),
            new Offer("GRANVIA-SUPKING", "Hotel Granvia", "Superior King", null, null, 206000),
            new Offer("TOYOKO-SGL", "Toyoko Inn", "Single", null, null, 89000));

    private static final Pattern ISO_DATE =
            Pattern.compile("\\b(\\d{4})[-./\\s](\\d{1,2})[-./\\s](\\d{1,2})\\b");
    private static final Pattern KO_DATE =
            Pattern.compile("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");
    /** "서울에서 부산", "인천에서 오사카까지" - the two place names of a leg. */
    private static final Pattern ROUTE =
            Pattern.compile("([\\p{L}]{2,20})\\s*에서\\s*([\\p{L}]{2,20})");
    /** The same leg written in English: "from Incheon to Osaka". */
    private static final Pattern ROUTE_EN =
            Pattern.compile("(?i)from\\s+([\\p{L}]{2,20})\\s+to\\s+([\\p{L}]{2,20})");
    /** Half a leg, which is how people actually answer: "from CheongJu", "청주에서". */
    private static final Pattern FROM_ONLY =
            Pattern.compile("(?i)(?:from\\s+([\\p{L}]{2,20})|([\\p{L}]{2,20})\\s*에서)");
    private static final Pattern TO_ONLY =
            Pattern.compile("(?i)(?:to\\s+([\\p{L}]{2,20})|([\\p{L}]{2,20})\\s*(?:까지|으로|로))");
    /** "오사카 호텔" - a stay is booked in a city, not along a route. Korean puts the city first. */
    private static final Pattern CITY_STAY =
            Pattern.compile("([\\p{L}]{2,20})\\s*(?:호텔|숙소)");
    /** English puts it after: "a hotel in Osaka", "accommodation at Busan". */
    private static final Pattern CITY_STAY_EN =
            Pattern.compile("(?i)(?:hotel|accommodation|stay)\\s+(?:in|at)\\s+([\\p{L}]{2,20})");

    private static final List<String[]> RAIL_WORDS = List.of(
            new String[]{"ktx", "KTX"}, new String[]{"srt", "SRT"},
            new String[]{"기차", "KTX"}, new String[]{"열차", "KTX"}, new String[]{"train", "KTX"});
    private static final List<String> FLIGHT_WORDS = List.of("비행기", "항공", "flight", "air");
    private static final List<String> ROOM_WORDS =
            List.of("호텔", "숙소", "숙박", "hotel", "accommodation", "room");

    @Override
    public BizplayPlanAgentResponse chat(BizplayPlanAgentRequest request) {
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        String sessionId = request.getSessionId() == null || request.getSessionId().isBlank()
                ? UUID.randomUUID().toString()
                : request.getSessionId();
        ObjectNode draft = sessions.computeIfAbsent(sessionId, k -> newDraft(request));
        // A chip token ("offer:KTX101-0600", "booking-confirm") is generated by our own UI, not
        // written by the user, so it must not decide the language: reading it as English flipped a
        // Korean conversation the moment the user tapped an offer. Only real text sets the language.
        boolean machineToken = message.toLowerCase(Locale.ROOT).startsWith("offer:")
                || message.matches("(?i)\\s*booking-(confirm|cancel).*");
        boolean ko = machineToken ? draft.path("ko").asBoolean(false) : hasHangul(message);
        draft.put("ko", ko);   // remembered here, never re-derived from a reply
        if (request.getBstrPlanApprovalId() != null) {
            draft.put("bstrPlanApprovalId", request.getBstrPlanApprovalId());
        }

        // Picking an offer is a machine token, exactly like the settlement agent chips: a tap and a
        // typed sentence travel the same path, so selecting one needs no separate endpoint.
        if (message.toLowerCase(Locale.ROOT).startsWith("offer:")) {
            return pickOffer(sessionId, draft, message.substring("offer:".length()).trim(), ko);
        }
        if (message.matches("(?i)\\s*booking-cancel.*")) {
            sessions.remove(sessionId);
            return turn(sessionId, draft, "BOOKING_CANCELLED",
                    t(ko, "Dropped that booking - nothing was reserved.",
                        "예약 요청을 취소했어요 - 예약된 내용은 없습니다."), null);
        }

        // An offer is selected and awaiting confirmation. That question is answerable in WORDS,
        // not only by the chip - "book it" here used to fall through and re-list the offers, which
        // read as the assistant failing. The judgement is the LLM's, not a phrase list; anything it
        // does not read as confirm/cancel falls through, so "날짜를 모레로" still edits the draft.
        if (draft.hasNonNull("offerId")
                && !"CONFIRMED".equals(draft.path("bookingStatus").asText(""))
                // Invariant, before any judgement: a message carrying a concrete NEW value - a
                // date, a place - is an EDIT of the draft, never a confirmation or a cancel.
                // "날짜를 모레로 바꿔줘" was judged "cancel" and silently dropped the booking; a
                // turn like that never reaches the classifier at all now.
                && !carriesConcreteValue(message)) {
            String decision = confirmDecision(message, ko);
            if ("confirm".equals(decision)) {
                return confirm(sessionId);
            }
            if ("cancel".equals(decision)) {
                sessions.remove(sessionId);
                return turn(sessionId, draft, "BOOKING_CANCELLED",
                        t(ko, "Dropped that booking - nothing was reserved.",
                            "예약 요청을 취소했어요 - 예약된 내용은 없습니다."), null);
            }
        }

        // The offers are on the table and the user answered in WORDS ("SRT로 할게", "the cheapest
        // one", "두 번째"). A tap and a sentence must travel the same path: resolve the words
        // against the listed offers — the offers' own tokens first, the LLM judging the row on a
        // miss — and continue into the exact same pickOffer the chip uses. A message carrying a
        // concrete new value (a date, a place) is an EDIT, never a pick — same invariant as the
        // confirm gate above.
        String typeSoFar = draft.path("bookingType").asText("");
        if (!typeSoFar.isBlank() && !draft.hasNonNull("offerId")
                && missingSlots(draft, typeSoFar, ko).isEmpty()
                && !machineToken && !carriesConcreteValue(message)) {
            String offerPick = resolveOfferByWords(draft, typeSoFar, message, ko);
            if (offerPick != null) {
                return pickOffer(sessionId, draft, offerPick, ko);
            }
        }

        readInto(draft, message);
        String type = draft.path("bookingType").asText("");
        if (type.isBlank()) {
            return turn(sessionId, draft, "BOOKING_SLOT_FILLING",
                    t(ko, "What would you like to book - rail, a flight, or a hotel?",
                        "무엇을 예약해 드릴까요? 기차, 항공, 숙소 중에서 말씀해 주세요."), null);
        }
        List<String> missing = missingSlots(draft, type, ko);
        if (!missing.isEmpty()) {
            // The deterministic pass covers the phrasings we anticipated; people use the ones we
            // did not. "i will leave tmr" carries a date and "from CheongJu" carries an origin,
            // and neither matched a pattern - so the same question repeated forever. Fall back to
            // the slot-filler sub-agent, which reads the sentence rather than matching it.
            llmFill(draft, message, type, ko);
            missing = missingSlots(draft, type, ko);
        }
        if (!missing.isEmpty()) {
            return turn(sessionId, draft, prefix(type) + "_SLOT_FILLING",
                    t(ko, "I still need the " + String.join(" and ", missing) + ".",
                        String.join(", ", missing) + "을(를) 알려주시겠어요?"), null);
        }
        return offerTurn(sessionId, draft, type, ko);
    }

    @Override
    public BizplayPlanAgentResponse confirm(String sessionId) {
        ObjectNode draft = sessions.get(sessionId);
        if (draft == null) {
            throw new IllegalArgumentException("No booking in progress for this session.");
        }
        if (!draft.hasNonNull("offerId")) {
            throw new IllegalArgumentException("Pick an offer before confirming the booking.");
        }
        boolean ko = draft.path("ko").asBoolean(true);
        String ref = String.format("%06d", Math.abs(draft.path("offerId").asText("").hashCode()) % 1000000);
        String bookingId = "BZ-DEMO-" + draft.path("usedStartDate").asText("").replace("-", "") + "-" + ref;
        draft.put("bookingId", bookingId);
        draft.put("bookingReference", ref);
        draft.put("bookingStatus", "CONFIRMED");
        draft.put("paymentStatus", "PAID");
        // In the real integration these come from the provider, and they are the point of the whole
        // chain: the booking lands on the settlement as ISSUED evidence with nothing retyped.
        ArrayNode receipts = draft.putArray("receiptIds");
        receipts.add(900000 + Math.abs(bookingId.hashCode()) % 99999);
        log.info("DEMO booking confirmed: {} ({}) for plan {}", bookingId,
                draft.path("bookingType").asText("-"), draft.path("bstrPlanApprovalId").asText("-"));
        return turn(sessionId, draft, "BOOKING_CONFIRMED",
                t(ko, "Booked - reference " + ref
                        + ". It will appear on the settlement as evidence, already issued.",
                     "예약이 완료되었어요 - 예약번호 " + ref
                        + ". 정산서에는 발급된 증빙으로 첨부됩니다."), null);
    }

    private BizplayPlanAgentResponse offerTurn(String sessionId, ObjectNode draft, String type, boolean ko) {
        List<Offer> inventory = inventoryFor(type);
        List<TripPlanAgentResponse.Option> options = new ArrayList<>();
        for (Offer o : inventory) {
            options.add(TripPlanAgentResponse.Option.builder()
                    .label(offerLabel(o, type))
                    .sendText("offer:" + o.id())
                    .meta(Map.of("offerId", o.id(), "amount", String.valueOf(o.amount())))
                    .build());
        }
        TripPlanAgentResponse.PendingChoice chips = TripPlanAgentResponse.PendingChoice.builder()
                .kind(prefix(type) + "_OFFER")
                .name(t(ko, "offer", "예약 후보"))
                .options(options)
                .build();
        String reply;
        if ("ACCOMMODATION".equals(type)) {
            reply = t(ko,
                    draft.path("arrival").asText("") + ", " + nights(draft) + " night(s) - "
                            + inventory.size() + " options. Pick one.",
                    draft.path("arrival").asText("") + " " + nights(draft) + "박 숙소 "
                            + inventory.size() + "건을 찾았어요. 선택해 주세요.");
        } else {
            String where = draft.path("depart").asText("") + " → " + draft.path("arrival").asText("");
            reply = t(ko,
                    where + " on " + draft.path("usedStartDate").asText("") + " - "
                            + inventory.size() + " options. Pick one.",
                    where + ", " + draft.path("usedStartDate").asText("") + " 출발 "
                            + inventory.size() + "건을 찾았어요. 선택해 주세요.");
        }
        return turn(sessionId, draft, prefix(type) + "_OFFER_SELECTION", reply, List.of(chips));
    }

    /**
     * The offer a sentence refers to, or null. Deterministic first: an offer's own tokens (train
     * code, brand, hotel-name words, operator) found in the message — exactly one offer mentioned
     * wins, shared words ("KTX" with two KTX rows) fall through. Then the LLM judges the row for
     * meaning-picks ("the cheapest", "두 번째", "the 7 o'clock one") — no wording is predefined.
     */
    private String resolveOfferByWords(ObjectNode draft, String type, String message, boolean ko) {
        if (message == null || message.isBlank()) {
            return null;
        }
        List<Offer> inventory = inventoryFor(type);
        String norm = message.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        java.util.Set<Offer> mentioned = new java.util.LinkedHashSet<>();
        for (Offer o : inventory) {
            List<String> tokens = new ArrayList<>();
            tokens.add(o.id());
            tokens.add(o.operator());
            String[] dw = o.detail().split("[\\s·,]+");
            for (String w : dw) {
                if (w.length() >= 2) {
                    tokens.add(w);
                }
            }
            if (dw.length >= 2) {
                tokens.add(dw[0] + dw[1]);   // "KTX 101" typed without the space
            }
            for (String tk : tokens) {
                String t2 = tk.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
                if (t2.length() >= 2 && norm.contains(t2)) {
                    mentioned.add(o);
                    break;
                }
            }
        }
        if (mentioned.size() == 1) {
            return mentioned.iterator().next().id();
        }
        StringBuilder rows = new StringBuilder();
        int i = 1;
        for (Offer o : inventory) {
            rows.append(i++).append(") ").append(offerLabel(o, type)).append("; ");
        }
        try {
            String picked = slotFillerAgentService.extract(message, Map.of(
                    "offerRow", "Situation: the assistant listed these travel offers and asked the "
                            + "user to pick one: " + rows + "Judge THIS message: EXACTLY the single "
                            + "row number it chooses (match by name, time, seat class or price). "
                            + "Omit the field when it does not choose one"), ko)
                    .path("offerRow").asText("").trim();
            log.info("Offer pick judge on '{}': '{}'", message, picked.isEmpty() ? "none" : picked);
            if (picked.matches("\\d+")) {
                int idx = Integer.parseInt(picked);
                if (idx >= 1 && idx <= inventory.size()) {
                    return inventory.get(idx - 1).id();
                }
            }
        } catch (Exception e) {
            log.warn("Offer pick judge unavailable: {}", e.getMessage());
        }
        return null;
    }

    private BizplayPlanAgentResponse pickOffer(String sessionId, ObjectNode draft, String offerId, boolean ko) {
        String type = draft.path("bookingType").asText("");
        Offer picked = null;
        for (Offer o : inventoryFor(type)) {
            if (o.id().equalsIgnoreCase(offerId)) {
                picked = o;
                break;
            }
        }
        if (picked == null) {
            return turn(sessionId, draft, "BOOKING_OFFER_UNKNOWN",
                    t(ko, "That option is no longer available - pick another.",
                        "해당 상품은 선택할 수 없어요 - 다른 상품을 골라 주세요."), null);
        }
        draft.put("offerId", picked.id());
        draft.put("operator", picked.operator());
        draft.put("amount", picked.amount());
        draft.put("currencyCode", "KRW");
        return turn(sessionId, draft, prefix(type) + "_BOOKING_CONFIRMATION",
                t(ko, "Confirm this booking: " + offerLabel(picked, type)
                        + ". Nothing is reserved until you confirm.",
                     "예약 내용을 확인해 주세요: " + offerLabel(picked, type)
                        + ". 확정하기 전에는 예약되지 않아요."),
                List.of(TripPlanAgentResponse.PendingChoice.builder()
                        .kind("BOOKING_CONFIRM")
                        .name(t(ko, "confirm this booking?", "예약할까요?"))
                        .options(List.of(
                                TripPlanAgentResponse.Option.builder()
                                        .label(t(ko, "Confirm booking", "예약 확정"))
                                        .sendText("booking-confirm").build(),
                                TripPlanAgentResponse.Option.builder()
                                        .label(t(ko, "Cancel", "취소"))
                                        .sendText("booking-cancel").build()))
                        .build()));
    }

    /**
     * What does this message mean, standing in front of an unconfirmed selected offer? Decided by
     * the slot-filler LLM - "book it", "예약해줘", "확정", "그걸로 해" are all the same answer and
     * no list would hold them. Fails closed: unsure or unavailable reads as neither, and the turn
     * falls through to the normal draft-editing path.
     */
    private String confirmDecision(String message, boolean ko) {
        if (message == null || message.isBlank() || message.length() > 80) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode got = slotFillerAgentService.extract(message,
                    // The situation, not example wordings: any phrasing in any language that means
                    // "go ahead" must classify the same, and a list of sample phrases would only
                    // anchor the model to them.
                    Map.of("decision", "Situation: the assistant showed the user a selected travel "
                            + "offer and asked whether to finalize the booking. Judge what THIS "
                            + "message does with that question: EXACTLY \"confirm\" if it agrees to "
                            + "proceed with the booking; EXACTLY \"cancel\" if it abandons the "
                            + "booking altogether. Requests to change any detail of the trip are "
                            + "neither - omit the field for those and for anything else"),
                    ko);
            String v = got.path("decision").asText("").trim().toLowerCase(Locale.ROOT);
            return ("confirm".equals(v) || "cancel".equals(v)) ? v : null;
        } catch (Exception e) {
            log.warn("Booking confirm-intent check failed: {}", e.getMessage());
            return null;
        }
    }

    private ObjectNode newDraft(BizplayPlanAgentRequest request) {
        ObjectNode d = objectMapper.createObjectNode();
        d.put("corpUserId", request.getCorpUserId());
        d.put("bookingStatus", "DRAFT");
        return d;
    }

    /** Read whatever this message states into the draft. Deterministic - a demo must not stall. */
    private void readInto(ObjectNode draft, String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        // What KIND of travel does this message name? Checked on every turn, not only the first:
        // "아니 비행기로 할래" mid-flow is the user changing their mind, and the old fill-if-empty
        // guard left them looking at train times while the agent said nothing was wrong.
        String namedType = null;
        String namedVehicle = null;
        for (String w : ROOM_WORDS) {
            if (lower.contains(w)) {
                namedType = "ACCOMMODATION";
                break;
            }
        }
        if (namedType == null) {
            for (String w : FLIGHT_WORDS) {
                if (lower.contains(w)) {
                    namedType = "FLIGHT";
                    namedVehicle = "AIR";
                    break;
                }
            }
        }
        if (namedType == null) {
            for (String[] w : RAIL_WORDS) {
                if (lower.contains(w[0])) {
                    namedType = "RAIL";
                    namedVehicle = w[1];
                    break;
                }
            }
        }
        if (namedType != null) {
            boolean changed = !namedType.equals(draft.path("bookingType").asText(""));
            draft.put("bookingType", namedType);
            if (namedVehicle != null) {
                draft.put("vehicleType", namedVehicle);
            }
            if (changed) {
                // A different kind of travel means the old choice is meaningless - a KTX seat is
                // not a flight - so the selection goes, while route and date stay: they are still
                // what the traveller wants.
                for (String stale : new String[]{"offerId", "operator", "amount", "bookingReference"}) {
                    draft.remove(stale);
                }
                log.info("Booking type switched to {} - previous selection dropped.", namedType);
            }
        }
        Matcher r = ROUTE.matcher(message);
        if (!r.find()) {
            r = ROUTE_EN.matcher(message);
            if (!r.find()) {
                r = null;
            }
        }
        if (r != null) {
            // Through the same place guard as every other writer: "대전에서 출발할래" fits the
            // "X에서 Y" shape too, and the raw put made 출발할래 - a verb - the arrival. Each end
            // is judged on its own, so a valid origin still lands when the "arrival" was junk.
            String routeFrom = placeOrNull(r.group(1));
            String routeTo = placeOrNull(r.group(2));
            if (routeFrom != null) {
                draft.put("depart", routeFrom);
            }
            if (routeTo != null && !routeTo.equals(draft.path("depart").asText(""))) {
                draft.put("arrival", routeTo);
            }
        }
        // Half a leg is a perfectly normal answer to "where from?" - take whichever end is given.
        // These OVERWRITE rather than fill-if-empty: "청주에서" states the origin outright, and a
        // value guessed on an earlier turn must not outrank it. Without this, one bad guess stuck
        // for the rest of the conversation and every later correction was silently ignored.
        String from = placeOrNull(firstGroup(FROM_ONLY.matcher(message)));
        if (from != null) {
            draft.put("depart", from);
        }
        String to = placeOrNull(firstGroup(TO_ONLY.matcher(message)));
        if (to != null) {
            draft.put("arrival", to);
        }
        List<String> dates = datesIn(message);
        if (!dates.isEmpty()) {
            draft.put("usedStartDate", dates.get(0));
            draft.put("usedEndDate", dates.size() > 1 ? dates.get(1) : dates.get(0));
        }
        if ("ACCOMMODATION".equals(draft.path("bookingType").asText("")) && !draft.hasNonNull("arrival")) {
            Matcher city = CITY_STAY.matcher(message);
            if (!city.find()) {
                city = CITY_STAY_EN.matcher(message);
                if (!city.find()) {
                    city = null;
                }
            }
            if (city != null) {
                draft.put("arrival", city.group(1));
            }
        }
    }

    /**
     * Ask the slot-filler sub-agent for whatever is still missing. Only the empty slots are
     * requested, so a value already given deterministically is never second-guessed, and a failure
     * is swallowed - the flow simply asks again rather than breaking.
     */
    private void llmFill(ObjectNode draft, String message, String type, boolean ko) {
        if (message == null || message.isBlank()) {
            return;
        }
        java.util.LinkedHashMap<String, String> wanted = new java.util.LinkedHashMap<>();
        boolean stay = "ACCOMMODATION".equals(type);
        if (!draft.hasNonNull("arrival")) {
            wanted.put("arrival", stay
                    ? "the city or place the user wants to stay in"
                    : "the destination place the user is travelling TO");
        }
        if (!stay && !draft.hasNonNull("depart")) {
            wanted.put("depart", "the place the user is travelling FROM");
        }
        if (!draft.hasNonNull("usedStartDate")) {
            wanted.put("usedStartDate", "the travel or check-in date as ISO yyyy-MM-dd, resolving "
                    + "relative words such as tomorrow / tmr / 내일 / 모레 against today");
        }
        if (stay && !draft.hasNonNull("usedEndDate")) {
            wanted.put("usedEndDate", "the check-out date as ISO yyyy-MM-dd, if the user gave one");
        }
        if (wanted.isEmpty()) {
            return;
        }
        int filled = merge(draft, message, wanted, ko);
        // Asking for several slots at once is all-or-nothing: "i will leave tmr" carries a date and
        // nothing else, and with three fields requested the extractor returned an empty object
        // rather than the one value it could see. Retry per slot - a few extra calls, but only on
        // the turn that would otherwise have repeated the same question.
        if (filled == 0 && wanted.size() > 1) {
            for (Map.Entry<String, String> e : wanted.entrySet()) {
                merge(draft, message, Map.of(e.getKey(), e.getValue()), ko);
            }
        }
        if (draft.hasNonNull("usedStartDate") && !draft.hasNonNull("usedEndDate")) {
            draft.put("usedEndDate", draft.path("usedStartDate").asText());
        }
    }

    /** One extraction pass; returns how many slots it actually filled. Failure-safe. */
    private int merge(ObjectNode draft, String message, Map<String, String> wanted, boolean ko) {
        int filled = 0;
        try {
            com.fasterxml.jackson.databind.JsonNode got =
                    slotFillerAgentService.extract(message, wanted, ko);
            for (String key : wanted.keySet()) {
                String v = got.path(key).asText("").trim();
                // The extractor offered "내일" as a departure place; a date word is not a place.
                if (("depart".equals(key) || "arrival".equals(key)) && placeOrNull(v) == null) {
                    continue;
                }
                // A leg has two DIFFERENT ends. Asked for the arrival while only "청주에서" had
                // been said, the extractor answered 청주 - the origin - and the draft read
                // 청주 → 청주 until the next turn overwrote it.
                if (("depart".equals(key) || "arrival".equals(key)) && placeOrNull(v) == null) {
                    continue;   // verb fragments and date words are not places, whoever offers them
                }
                if ("arrival".equals(key) && v.equals(draft.path("depart").asText(""))) {
                    continue;
                }
                if ("depart".equals(key) && v.equals(draft.path("arrival").asText(""))) {
                    continue;
                }
                if (!v.isEmpty() && !draft.hasNonNull(key)) {
                    draft.put(key, v);
                    log.info("Booking slot {} filled by the slot-filler: {}", key, v);
                    filled++;
                }
            }
        } catch (Exception e) {
            log.warn("Booking slot-filler pass failed: {}", e.getMessage());
        }
        return filled;
    }

    private List<String> datesIn(String message) {
        List<String> out = new ArrayList<>();
        Matcher m = ISO_DATE.matcher(message);
        while (m.find()) {
            add(out, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        }
        if (out.isEmpty()) {
            Matcher k = KO_DATE.matcher(message);
            int year = LocalDate.now().getYear();
            while (k.find()) {
                add(out, year, Integer.parseInt(k.group(1)), Integer.parseInt(k.group(2)));
            }
        }
        // Relative calendar words, deterministically. These used to reach only the LLM fallback,
        // which runs while slots are MISSING - so "날짜를 모레로 바꿔줘" after the offers were
        // already on screen changed nothing at all.
        if (out.isEmpty()) {
            String lower = message.toLowerCase(Locale.ROOT);
            // If-chain, not a nested ternary: mixing int literals with null in a conditional
            // auto-unboxes and NPEs on the null branch - which took the whole endpoint down.
            Integer plus = null;
            if (lower.contains("글피")) {
                plus = 3;
            } else if (lower.contains("모레")) {
                plus = 2;
            } else if (lower.contains("내일") || lower.contains("tomorrow")
                    || lower.matches(".*\btmr\b.*")) {
                plus = 1;
            } else if (lower.contains("오늘") || lower.contains("today")) {
                plus = 0;
            }
            if (plus != null) {
                out.add(LocalDate.now().plusDays(plus).toString());
            }
        }
        return out;
    }

    /** Words that are dates, not destinations. "내일 출발" put 내일 into depart. */
    /** Vehicle and stay words. "비행기로" matched the "X로 = to X" pattern and became a destination. */
    private static final java.util.Set<String> NOT_A_PLACE_VEHICLES = java.util.Set.of(
            "비행기", "항공", "기차", "열차", "ktx", "srt", "버스", "택시",
            "호텔", "숙소", "숙박", "flight", "train", "bus", "taxi", "hotel", "air");

    private static final java.util.Set<String> NOT_A_PLACE = java.util.Set.of(
            "내일", "오늘", "모레", "어제", "지금", "담주", "이번주", "다음주",
            "tomorrow", "tmr", "today", "tonight", "yesterday", "now", "next", "week",
            // Demonstrative pronouns: "그걸로 진행해" made 그걸 the arrival.
            "그걸", "이걸", "저걸", "그거", "이거", "저거", "그것", "이것", "그쪽", "이쪽", "여기", "거기");

    /**
     * Does this message state a concrete NEW value - a date or a real place? Judged on the
     * GUARDED extractions, not the raw patterns: "그걸로 진행해" matches the "X로" shape, but 그걸
     * is a pronoun, not a place - the raw-pattern gate treated it as an edit and the proceed
     * intent never reached the classifier.
     */
    private boolean carriesConcreteValue(String message) {
        if (!datesIn(message).isEmpty()) {
            return true;
        }
        Matcher r = ROUTE.matcher(message);
        if (r.find() && (placeOrNull(r.group(1)) != null || placeOrNull(r.group(2)) != null)) {
            return true;
        }
        return placeOrNull(firstGroup(FROM_ONLY.matcher(message))) != null
                || placeOrNull(firstGroup(TO_ONLY.matcher(message))) != null;
    }

    /** Verb morphology that ends up captured next to a particle: "출발할래" is not a town. */
    private static final Pattern VERBISH = Pattern.compile(
            ".*(출발|도착|바꿔|바꿀|변경|예약|할래|할게|해줘|주세요|싶어).*");

    /** The place, or null when the text is really a date word, a vehicle, or a verb fragment. */
    private String placeOrNull(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        String c = candidate.trim().toLowerCase(Locale.ROOT);
        if (NOT_A_PLACE.contains(c) || NOT_A_PLACE_VEHICLES.contains(c) || VERBISH.matcher(c).matches()) {
            return null;
        }
        return candidate.trim();
    }

    /** The first non-null capture of a match, or null when nothing matched. */
    private String firstGroup(Matcher m) {
        if (!m.find()) {
            return null;
        }
        for (int i = 1; i <= m.groupCount(); i++) {
            if (m.group(i) != null && !m.group(i).isBlank()) {
                return m.group(i);
            }
        }
        return null;
    }

    private void add(List<String> out, int y, int m, int d) {
        try {
            out.add(LocalDate.of(y, m, d).toString());
        } catch (RuntimeException ignored) {
            // text that looks like a date but is not one
        }
    }

    private List<String> missingSlots(ObjectNode draft, String type, boolean ko) {
        List<String> missing = new ArrayList<>();
        if ("ACCOMMODATION".equals(type)) {
            if (!draft.hasNonNull("arrival")) {
                missing.add(t(ko, "city", "도시"));
            }
            if (!draft.hasNonNull("usedStartDate")) {
                missing.add(t(ko, "dates", "숙박 기간"));
            }
        } else {
            if (!draft.hasNonNull("depart")) {
                missing.add(t(ko, "departure place", "출발지"));
            }
            if (!draft.hasNonNull("arrival")) {
                missing.add(t(ko, "arrival place", "도착지"));
            }
            if (!draft.hasNonNull("usedStartDate")) {
                missing.add(t(ko, "date", "이용일"));
            }
        }
        return missing;
    }

    private List<Offer> inventoryFor(String type) {
        if ("FLIGHT".equals(type)) {
            return FLIGHT;
        }
        if ("ACCOMMODATION".equals(type)) {
            return ROOM;
        }
        return RAIL;
    }

    private String prefix(String type) {
        if ("FLIGHT".equals(type)) {
            return "FLIGHT";
        }
        if ("ACCOMMODATION".equals(type)) {
            return "ROOM";
        }
        return "RAIL";
    }

    private String offerLabel(Offer o, String type) {
        String money = "₩" + String.format("%,d", o.amount());
        if ("ACCOMMODATION".equals(type)) {
            return o.operator() + "  " + o.detail() + "  " + money;
        }
        return o.detail() + "  " + o.from() + " → " + o.to() + "  " + money + "  (" + o.operator() + ")";
    }

    private long nights(ObjectNode draft) {
        try {
            long n = java.time.temporal.ChronoUnit.DAYS.between(
                    LocalDate.parse(draft.path("usedStartDate").asText()),
                    LocalDate.parse(draft.path("usedEndDate").asText()));
            return n <= 0 ? 1 : n;
        } catch (RuntimeException e) {
            return 1;
        }
    }

    private BizplayPlanAgentResponse turn(String sessionId, ObjectNode draft, String intent, String reply,
                                          List<TripPlanAgentResponse.PendingChoice> chips) {
        // NOT derived from the reply: offer labels contain Korean text (일반석) even in an English
        // conversation, which silently flipped the session language at confirm time.
        ArrayNode wrapper = objectMapper.createArrayNode();
        wrapper.add(draft.deepCopy());
        return BizplayPlanAgentResponse.builder()
                .sessionId(sessionId)
                .status("IN_PROGRESS")
                .intent(intent)
                .subAgents(List.of("BOOKING_DEMO_AGENT"))
                .reply(reply)
                .pendingChoices(chips)
                .draftJson(wrapper)
                .build();
    }

    private boolean hasHangul(String s) {
        return s != null && s.codePoints().anyMatch(c -> c >= 0xAC00 && c <= 0xD7A3);
    }

    private String t(boolean ko, String en, String korean) {
        return ko ? korean : en;
    }
}
