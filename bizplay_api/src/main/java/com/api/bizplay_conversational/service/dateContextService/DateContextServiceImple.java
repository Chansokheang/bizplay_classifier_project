package com.api.bizplay_conversational.service.dateContextService;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

@Service
public class DateContextServiceImple implements DateContextService {

    /** How many days of explicit calendar to list (3 weeks covers "next <weekday>" and "in 2 weeks"). */
    private static final int CALENDAR_DAYS = 21;

    @Override
    public String buildContext() {
        LocalDate today = LocalDate.now(); // app timezone is forced to Asia/Seoul
        StringBuilder sb = new StringBuilder();
        sb.append("Date context: today is ").append(weekday(today)).append(' ').append(today)
                .append(". Calendar: ");
        for (int i = 0; i < CALENDAR_DAYS; i++) {
            LocalDate d = today.plusDays(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(weekday(d)).append('=').append(d);
        }
        // Named anchors, spelled out. The calendar above technically contains them, but leaving the
        // model to work out that "tomorrow" is today+1 made it intermittent: the same sentence
        // ("a trip to Gwangju tomorrow") sometimes came back with no dates at all, and the flow
        // then asked for a period the user had already given.
        sb.append("Anchors: today/오늘=").append(today)
                .append(", tomorrow/내일=").append(today.plusDays(1))
                .append(", the day after tomorrow/모레=").append(today.plusDays(2))
                .append(", yesterday/어제=").append(today.minusDays(1))
                .append(", next week/다음주=").append(today.plusWeeks(1))
                .append(". A single day mentioned alone is BOTH the start and the end date. ");
        // NEXT week's weekdays spelled out one by one: combining "다음 주" with a weekday from
        // the rolling calendar is exactly the two-step lookup the model kept fumbling (다음 주
        // 수요일 came back as THIS week's Wednesday). A direct anchor removes the step.
        LocalDate nextMon = today.with(DayOfWeek.MONDAY).plusWeeks(1);
        sb.append("Next week (다음 주) weekday by weekday: ");
        for (int i = 0; i < 7; i++) {
            LocalDate d = nextMon.plusDays(i);
            sb.append(weekday(d)).append('=').append(d).append(i < 6 ? ", " : ". ");
        }
        sb.append("Rules: bare/\"this\" weekday = soonest occurrence; \"next <weekday>\" = that ")
                .append("weekday in NEXT week; \"in N days/weeks\" counts from today. Resolve every ")
                .append("relative date to YYYY-MM-DD from this calendar — never guess, never output relative words.");
        return sb.toString();
    }

    private String weekday(LocalDate d) {
        DayOfWeek dow = d.getDayOfWeek();
        // Both scripts on every entry: the 14B model mismapped Korean weekday names against an
        // English-only calendar (다음 주 수요일 was answered with next week's TUESDAY).
        String ko = switch (dow) {
            case MONDAY -> "월"; case TUESDAY -> "화"; case WEDNESDAY -> "수";
            case THURSDAY -> "목"; case FRIDAY -> "금"; case SATURDAY -> "토"; case SUNDAY -> "일";
        };
        return dow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + "/" + ko + "요일";
    }
}
