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
        sb.append("Rules: bare/\"this\" weekday = soonest occurrence; \"next <weekday>\" = that ")
                .append("weekday in NEXT week; \"in N days/weeks\" counts from today. Resolve every ")
                .append("relative date to YYYY-MM-DD from this calendar — never guess, never output relative words.");
        return sb.toString();
    }

    private String weekday(LocalDate d) {
        DayOfWeek dow = d.getDayOfWeek();
        return dow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }
}
