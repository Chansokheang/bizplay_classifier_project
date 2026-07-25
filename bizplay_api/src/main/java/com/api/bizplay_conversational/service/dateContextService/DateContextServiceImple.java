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
        sb.append(". Rules: bare/\"this\" weekday = soonest occurrence; \"next <weekday>\" = that ")
                .append("weekday in NEXT week; \"in N days/weeks\" counts from today. Resolve every ")
                .append("relative date to YYYY-MM-DD from this calendar — never guess, never output relative words.");
        return sb.toString();
    }

    private String weekday(LocalDate d) {
        DayOfWeek dow = d.getDayOfWeek();
        return dow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }
}
