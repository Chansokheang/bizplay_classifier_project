package com.api.bizplay_conversational.service.corpProvisioningService;

/**
 * Every conversational session hangs off a {@code corp} row (the table's foreign key), and BizPlay
 * sends whatever business number the logged-in user belongs to. Demo data only ever held one corp,
 * so any real 사업자번호 blew up as an unhandled FK violation — a 500 with nothing to act on.
 *
 * <p>This is the one gate in front of that: it says whether a corpNo can be used at all, registers
 * it on first sight (BizPlay is the source of truth for who the corp is — no one should have to
 * pre-create it by hand), and refuses the rest with a reason a caller can read.
 */
public interface CorpProvisioningService {

    /**
     * Normalise, register-if-new, and return the corpNo to use for this conversation.
     *
     * @param corpNo       what the caller sent — "1078836128", "107-88-36128", or something unusable
     * @param bizplayToken the caller's token, used to name a newly registered corp after the
     *                     corporation BizPlay says the user is connected to; may be null
     * @return the corpNo as it is stored (digits only)
     * @throws IllegalArgumentException — answered as HTTP 400 with the reason — when the value is
     *                                    missing or is not a business number we can interpret
     */
    String requireUsableCorpNo(String corpNo, String bizplayToken);
}
