package com.claimsplatform.claims.domain;

import java.util.EnumSet;
import java.util.Set;

public enum ClaimStatus {
    SUBMITTED,       // reported by claimant, waiting in the intake queue
    UNDER_REVIEW,    // picked up by a claims officer
    INFO_REQUESTED,  // ball is in the claimant's court
    ASSESSED,        // officer has set the assessed (payable) amount
    APPROVED,        // decision made, payment outstanding
    SETTLED,         // paid - terminal
    REJECTED,        // declined - terminal
    WITHDRAWN;       // claimant withdrew - terminal

    private static final Set<ClaimStatus> TERMINAL = EnumSet.of(SETTLED, REJECTED, WITHDRAWN);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
