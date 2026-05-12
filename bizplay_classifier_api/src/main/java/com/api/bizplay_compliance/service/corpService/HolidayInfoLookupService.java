package com.api.bizplay_compliance.service.corpService;

import java.time.LocalDate;
import java.util.Optional;

public interface HolidayInfoLookupService {

    Optional<HolidayInfo> findHoliday(LocalDate date);

    record HolidayInfo(
            String dateKind,
            String dateName,
            String isHoliday,
            long locdate,
            int seq
    ) {
    }
}
