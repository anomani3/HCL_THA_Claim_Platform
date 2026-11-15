package com.claimsplatform.reporting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClaimProjector {

    private static final Logger log = LoggerFactory.getLogger(ClaimProjector.class);

    private final ClaimSummaryRepository repo;

    public ClaimProjector(ClaimSummaryRepository repo) {
        this.repo = repo;
    }

    /**
     * Idempotent upsert. Events are full snapshots with a per-claim version, so a duplicate or
     * out-of-order delivery (version <= what we hold) is simply ignored.
     */
    @Transactional
    public void apply(ClaimEventDto event) {
        ClaimSummary summary = repo.findById(event.claimId()).orElse(null);
        if (summary == null) {
            summary = new ClaimSummary(event.claimId());
        } else if (event.version() <= summary.getVersion()) {
            log.debug("Ignoring stale/duplicate {} for {} (event v{}, stored v{})",
                    event.eventType(), event.claimNumber(), event.version(), summary.getVersion());
            return;
        }
        summary.applyEvent(event);
        repo.save(summary);
        log.info("Projected {} for {} -> {} (v{})", event.eventType(), event.claimNumber(),
                event.status(), event.version());
    }
}
