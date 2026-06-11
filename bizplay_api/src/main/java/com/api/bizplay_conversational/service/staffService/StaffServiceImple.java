package com.api.bizplay_conversational.service.staffService;

import com.api.bizplay_conversational.exception.CustomNotFoundException;
import com.api.bizplay_conversational.model.entity.Department;
import com.api.bizplay_conversational.model.entity.Staff;
import com.api.bizplay_conversational.model.request.StaffRequest;
import com.api.bizplay_conversational.model.response.StaffLookupResult;
import com.api.bizplay_conversational.model.response.StaffResponse;
import com.api.bizplay_conversational.repository.DepartmentRepo;
import com.api.bizplay_conversational.repository.StaffRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaffServiceImple implements StaffService {

    private final StaffRepo staffRepo;
    private final DepartmentRepo departmentRepo;

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

    @Override
    @Transactional(readOnly = true)
    public List<StaffLookupResult> findCandidates(String corpNo, String name) {
        if (corpNo == null || corpNo.isBlank() || name == null || name.isBlank()) {
            return List.of();
        }
        String normalizedName = normalizePersonName(name);
        if (normalizedName.isBlank()) {
            return List.of();
        }
        // Best tier wins: all exact matches; else all partial; else all token matches.
        List<Staff> matches = staffRepo.findExactByCorpNoAndName(corpNo, normalizedName);
        String matchType = "EXACT";
        if (matches.isEmpty()) {
            matches = staffRepo.searchByCorpNoAndName(corpNo, normalizedName);
            matchType = "PARTIAL";
        }
        if (matches.isEmpty()) {
            String token = longestToken(normalizedName);
            if (token != null && token.length() >= 2 && !token.equalsIgnoreCase(normalizedName)) {
                matches = staffRepo.searchByCorpNoAndName(corpNo, token);
                matchType = "PARTIAL";
            }
        }
        // Dedup by staff id (a partial pattern can return the same person twice across tiers).
        LinkedHashMap<UUID, StaffLookupResult> byId = new LinkedHashMap<>();
        for (Staff s : matches) {
            StaffLookupResult r = toResult(corpNo, s, matchType);
            byId.putIfAbsent(r.getStaffId(), r);
        }
        return new ArrayList<>(byId.values());
    }

    // --- CRUD management ---------------------------------------------------------

    @Override
    @Transactional
    public StaffResponse create(StaffRequest request) {
        Department department = requireDepartment(request.getDepartmentId());
        String name = blankToNull(request.getName());
        if (name == null) {
            throw new IllegalArgumentException("name is required.");
        }
        Staff staff = new Staff();
        staff.setDepartment(department);
        staff.setName(name);
        staff.setPosition(blankToNull(request.getPosition()));
        staffRepo.save(staff);
        return toResponse(staffRepo.findById(staff.getId().toString()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffResponse> getByCorpNo(String corpNo) {
        List<StaffResponse> result = new ArrayList<>();
        for (Staff s : staffRepo.findByCorpNo(corpNo)) {
            result.add(toResponse(s));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public StaffResponse getById(String id) {
        Staff s = staffRepo.findById(parseId(id).toString());
        if (s == null) {
            throw new CustomNotFoundException("Staff not found: " + id);
        }
        return toResponse(s);
    }

    @Override
    @Transactional
    public StaffResponse update(String id, StaffRequest request) {
        UUID staffId = parseId(id);
        Staff existing = staffRepo.findById(staffId.toString());
        if (existing == null) {
            throw new CustomNotFoundException("Staff not found: " + id);
        }
        // Keep the current department unless a new one is given.
        UUID departmentId = blankToNull(request.getDepartmentId()) != null
                ? requireDepartment(request.getDepartmentId()).getId()
                : existing.getDepartment().getId();
        String name = blankToNull(request.getName());
        if (name == null) {
            throw new IllegalArgumentException("name is required.");
        }
        staffRepo.update(staffId, departmentId, name, blankToNull(request.getPosition()));
        return toResponse(staffRepo.findById(staffId.toString()));
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        UUID staffId = parseId(id);
        if (staffRepo.findById(staffId.toString()) == null) {
            throw new CustomNotFoundException("Staff not found: " + id);
        }
        try {
            staffRepo.deleteById(staffId);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException(
                    "Cannot delete staff " + id + ": they are referenced by a trip traveler. Remove them from trips first.");
        }
    }

    private Department requireDepartment(String departmentId) {
        String did = blankToNull(departmentId);
        if (did == null) {
            throw new IllegalArgumentException("departmentId is required.");
        }
        UUID parsed;
        try {
            parsed = UUID.fromString(did);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("departmentId must be a valid UUID: " + departmentId);
        }
        Department department = staffRepo.findDepartmentById(parsed);
        if (department == null) {
            throw new CustomNotFoundException("Department not found: " + departmentId);
        }
        return department;
    }

    private StaffResponse toResponse(Staff s) {
        Department d = s.getDepartment();
        return StaffResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .position(s.getPosition())
                .departmentId(d != null ? d.getId() : null)
                .departmentName(d != null ? d.getName() : null)
                .corpNo(d != null ? d.getCorpNo() : null)
                .createdAt(s.getCreatedDate())
                .build();
    }

    private UUID parseId(String id) {
        try {
            return UUID.fromString(id == null ? null : id.trim());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CustomNotFoundException("Invalid staff id: " + id);
        }
    }

    private String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
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
