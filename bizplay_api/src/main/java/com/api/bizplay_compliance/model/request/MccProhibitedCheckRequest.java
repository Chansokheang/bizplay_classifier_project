package com.api.bizplay_compliance.model.request;

import java.util.List;

public record MccProhibitedCheckRequest(
        String mccCode,
        List<String> blockedMccCodes
) {
}
