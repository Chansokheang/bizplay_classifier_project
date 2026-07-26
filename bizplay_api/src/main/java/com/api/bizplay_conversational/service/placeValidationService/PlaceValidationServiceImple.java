package com.api.bizplay_conversational.service.placeValidationService;

import com.api.bizplay_compliance.service.corpService.LocationGeocodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceValidationServiceImple implements PlaceValidationService {

    /** 시/도 + major Korean cities — instant local hit for the common demo destinations. */
    private static final Set<String> KOREAN_PLACES = Set.of(
            "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "제주",
            "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남",
            "수원", "성남", "고양", "용인", "부천", "안산", "안양", "화성", "평택", "의정부",
            "청주", "천안", "아산", "전주", "익산", "여수", "순천", "목포",
            "포항", "구미", "경주", "안동", "창원", "김해", "진주", "양산", "거제",
            "춘천", "원주", "강릉", "속초", "판교", "일산", "분당");

    private final LocationGeocodeService locationGeocodeService;

    @Override
    public Result validateKorean(String destination) {
        if (destination == null || destination.isBlank()) {
            return new Result(Result.Status.SKIPPED, null);
        }
        String d = destination.trim();
        // Gazetteer: "부산", "부산광역시", "부산 해운대" all match the 부산 entry.
        for (String place : KOREAN_PLACES) {
            if (d.equals(place) || d.startsWith(place) || place.startsWith(d)) {
                return new Result(Result.Status.VALID, place);
            }
        }
        try {
            LocationGeocodeService.GeocodedLocation loc = locationGeocodeService.geocode(d);
            String road = loc.roadAddress() != null ? loc.roadAddress() : loc.jibunAddress();
            String normalized = (road == null || road.isBlank()) ? d : road.split("\\s+")[0];
            return new Result(Result.Status.VALID, normalized);
        } catch (IllegalStateException e) {
            // The geocoder throws this for an EMPTY result — the place genuinely wasn't found.
            log.info("Korean place validation: no geocode result for '{}'", d);
            return new Result(Result.Status.UNKNOWN, null);
        } catch (Exception e) {
            // Network / credential problems: validation is best-effort, never punish the user.
            log.warn("Korean place validation unavailable ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            return new Result(Result.Status.SKIPPED, null);
        }
    }
}
