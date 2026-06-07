package com.api.bizplay_conversational.service.staffService;

import com.api.bizplay_conversational.model.entity.Staff;
import com.api.bizplay_conversational.model.response.StaffLookupResult;
import com.api.bizplay_conversational.repository.StaffRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaffServiceImple implements StaffService {

    private final StaffRepo staffRepo;

    @Override
    @Transactional(readOnly = true)
    public StaffLookupResult lookup(String corpNo, String name) {
        if (corpNo == null || corpNo.isBlank()) {
            throw new IllegalArgumentException("corpNo is required.");
        }
        if (name == null || name.isBlank()) {
            return StaffLookupResult.builder()
                    .matched(false)
                    .corpNo(corpNo)
                    .matchType("NONE")
                    .message("No staff name was provided or extracted.")
                    .build();
        }

        // Normalize foreign/booking name formats (e.g. "CHHUN/ROTANAKKOSAL MR") into a plain name
        // ("CHHUN ROTANAKKOSAL") so the DB lookup is not defeated by titles or slashes.
        String normalizedName = normalizePersonName(name);
        if (normalizedName.isBlank()) {
            return StaffLookupResult.builder()
                    .matched(false)
                    .corpNo(corpNo)
                    .matchType("NONE")
                    .message("No staff name was provided or extracted.")
                    .build();
        }

        List<Staff> exactMatches = staffRepo.findExactByCorpNoAndName(corpNo, normalizedName);
        if (!exactMatches.isEmpty()) {
            return toResult(corpNo, exactMatches.get(0), "EXACT");
        }

        List<Staff> partialMatches = staffRepo.searchByCorpNoAndName(corpNo, normalizedName);
        if (!partialMatches.isEmpty()) {
            return toResult(corpNo, partialMatches.get(0), "PARTIAL");
        }

        // Token fallback: names may be reordered between the document and the DB (e.g. the booking
        // lists LAST/FIRST). Try the longest name token, which is usually the distinctive given name.
        String token = longestToken(normalizedName);
        if (token != null && token.length() >= 2 && !token.equalsIgnoreCase(normalizedName)) {
            List<Staff> tokenMatches = staffRepo.searchByCorpNoAndName(corpNo, token);
            if (!tokenMatches.isEmpty()) {
                return toResult(corpNo, tokenMatches.get(0), "PARTIAL");
            }
        }

        return StaffLookupResult.builder()
                .matched(false)
                .corpNo(corpNo)
                .matchType("NONE")
                .message("No staff found for name: " + normalizedName)
                .build();
    }

    /** Honorifics stripped from extracted names before lookup. */
    private static final Set<String> HONORIFICS = Set.of(
            "mr", "mrs", "ms", "miss", "mister", "dr", "prof", "professor", "sir", "madam");

    /**
     * Clean a name pulled from free text or a document: replace separators ("/", ".") with spaces,
     * drop honorific tokens (Mr/Ms/Dr…), and collapse whitespace. Keeps the original word order.
     */
    private String normalizePersonName(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replace('/', ' ').replace('.', ' ').replace(',', ' ');
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String tok : cleaned.split(" ")) {
            if (HONORIFICS.contains(tok.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(tok);
        }
        // If everything was stripped (e.g. the input was only a title), fall back to the cleaned text.
        return sb.length() == 0 ? cleaned : sb.toString();
    }

    private String longestToken(String name) {
        String best = null;
        for (String tok : name.split(" ")) {
            if (best == null || tok.length() > best.length()) {
                best = tok;
            }
        }
        return best;
    }

    private StaffLookupResult toResult(String corpNo, Staff staff, String matchType) {
        return StaffLookupResult.builder()
                .matched(true)
                .corpNo(corpNo)
                .staffId(staff.getId())
                .staffName(staff.getName())
                .departmentId(staff.getDepartment().getId())
                .departmentName(staff.getDepartment().getName())
                .position(staff.getPosition())
                .matchType(matchType)
                .message("Staff matched.")
                .build();
    }
}
