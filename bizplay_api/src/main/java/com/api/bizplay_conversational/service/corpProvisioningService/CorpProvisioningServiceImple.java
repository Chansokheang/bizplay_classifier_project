package com.api.bizplay_conversational.service.corpProvisioningService;

import com.api.bizplay_classifier_api.model.dto.CorpGroupDTO;
import com.api.bizplay_classifier_api.model.request.CorpRequest;
import com.api.bizplay_classifier_api.repository.CorpRepo;
import com.api.bizplay_conversational.service.bizplayGatewayService.BizplayGatewayService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CorpProvisioningServiceImple implements CorpProvisioningService {

    /** A Korean 사업자등록번호 is ten digits; people write it 107-88-36128 as often as 1078836128. */
    private static final Pattern BUSINESS_NUMBER = Pattern.compile("\\d{10}");

    /** Fallback group for a corp we register ourselves and cannot name from the user's profile. */
    private static final String DEFAULT_GROUP_CODE = "BIZPLAY";

    private final CorpRepo corpRepo;
    private final BizplayGatewayService bizplayGatewayService;

    @Override
    public String requireUsableCorpNo(String corpNo, String bizplayToken) {
        if (corpNo == null || corpNo.isBlank()) {
            throw new IllegalArgumentException("corpNo is required.");
        }
        String given = corpNo.trim();
        // A corp already registered under exactly this value keeps working, whatever shape it has —
        // the demo corp and anything set up by hand through POST /corps included.
        if (corpRepo.existsBycorpNo(given)) {
            return given;
        }
        String digits = given.replaceAll("\\D", "");
        if (!given.equals(digits) && corpRepo.existsBycorpNo(digits)) {
            return digits;   // sent as 107-88-36128, stored as 1078836128
        }
        if (!BUSINESS_NUMBER.matcher(digits).matches()) {
            throw new IllegalArgumentException(
                    "corpNo must be a 10-digit business registration number (사업자등록번호), "
                            + "with or without dashes — e.g. 1078836128 or 107-88-36128. Received: \""
                            + given + "\".");
        }
        register(digits, bizplayToken);
        return digits;
    }

    /**
     * Register the corp the way BizPlay describes it. The name and group come from the caller's own
     * profile ("DemoCorp01" / "DemoGroup"), because that is who the corpNo belongs to; when the
     * profile is unavailable the corp is still registered — a nameless row beats a 500 mid-chat.
     */
    private void register(String corpNo, String bizplayToken) {
        String corpName = corpNo;
        String groupCode = DEFAULT_GROUP_CODE;
        try {
            JsonNode profile = bizplayGatewayService.getUserProfile(bizplayToken);
            JsonNode connected = null;
            for (JsonNode c : profile.path("corporations")) {
                if (c.path("connect").asBoolean(false)) {
                    connected = c;
                    break;
                }
            }
            String name = connected != null
                    ? connected.path("corporationName").asText("")
                    : profile.path("connectedCorporationName").asText("");
            if (!name.isBlank()) {
                corpName = name;
            }
            String group = connected == null ? "" : connected.path("corporationGroupName").asText("");
            if (!group.isBlank()) {
                groupCode = group.replaceAll("[^A-Za-z0-9가-힣_-]", "").toUpperCase(java.util.Locale.ROOT);
            }
        } catch (RuntimeException e) {
            log.info("Could not read the BizPlay profile while registering corp {} ({}) — "
                    + "registering it under its number.", corpNo, e.getMessage());
        }
        if (groupCode.isBlank()) {
            groupCode = DEFAULT_GROUP_CODE;
        }
        if (!corpRepo.existsCorpGroupByCode(groupCode)) {
            CorpGroupDTO created = corpRepo.createCorpGroup(groupCode);
            log.info("Registered corp group {} (id={}).", groupCode,
                    created == null ? null : created.getCorpGroupId());
        }
        try {
            corpRepo.createCorp(CorpRequest.builder()
                    .corpNo(corpNo)
                    .corpName(corpName)
                    .corpGroupCode(groupCode)
                    .build(), corpNo);
            log.info("Registered corp {} as \"{}\" in group {} on first use.", corpNo, corpName, groupCode);
        } catch (RuntimeException e) {
            // Two first turns at once: the loser of the race finds the row already there, which is
            // the state it wanted anyway. Anything else is a real failure and must surface.
            if (!corpRepo.existsBycorpNo(corpNo)) {
                throw e;
            }
            log.info("Corp {} was registered concurrently — continuing.", corpNo);
        }
    }
}
