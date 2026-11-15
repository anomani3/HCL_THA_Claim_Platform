package com.claimsplatform.claims.service;

import com.claimsplatform.claims.domain.Claim;
import com.claimsplatform.claims.domain.ClaimAction;
import com.claimsplatform.claims.domain.ClaimHistory;
import com.claimsplatform.claims.domain.ClaimStateMachine;
import com.claimsplatform.claims.domain.ClaimStatus;
import com.claimsplatform.claims.event.ClaimEvent;
import com.claimsplatform.claims.exception.BusinessRuleException;
import com.claimsplatform.claims.exception.ForbiddenActionException;
import com.claimsplatform.claims.exception.NotFoundException;
import com.claimsplatform.claims.repo.ClaimHistoryRepository;
import com.claimsplatform.claims.repo.ClaimRepository;
import com.claimsplatform.claims.web.dto.ActionRequest;
import com.claimsplatform.claims.web.dto.CreateClaimRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ClaimService {

    private final ClaimRepository claims;
    private final ClaimHistoryRepository history;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ClaimService(ClaimRepository claims, ClaimHistoryRepository history,
                        ApplicationEventPublisher events, Clock clock) {
        this.claims = claims;
        this.history = history;
        this.events = events;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public Claim submit(CreateClaimRequest r) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        if (r.incidentDate().isAfter(today)) {
            throw new BusinessRuleException("Incident date cannot be in the future");
        }
        String number = "CLM-%s-%d-%d".formatted(r.market(), today.getYear(), claims.nextClaimSequence());
        Claim claim = new Claim(UUID.randomUUID(), number, r.claimType(), r.market(), r.currency(),
                r.policyNumber(), r.claimantId(), r.claimantName(), r.claimantEmail(),
                r.incidentDate(), r.incidentLocation(), r.assetReference(), r.description(),
                r.claimedAmount(), now);
        claims.saveAndFlush(claim);
        history.save(new ClaimHistory(claim.getId(), ClaimAction.SUBMIT, null, ClaimStatus.SUBMITTED,
                r.claimantId(), "Claim submitted", now));
        events.publishEvent(ClaimEvent.from(claim, ClaimAction.SUBMIT, now));
        return claim;
    }

    @Transactional
    public Claim perform(UUID id, ClaimAction action, ActionRequest req) {
        return apply(find(id), action, req);
    }

    /** Officer "pick up next": atomically claims the oldest unassigned claim (optionally per market). */
    @Transactional
    public Claim pickNext(String officerId, String market) {
        Claim next = (market == null || market.isBlank()
                ? claims.lockNextSubmitted()
                : claims.lockNextSubmittedInMarket(market))
                .orElseThrow(() -> new NotFoundException("No unassigned claims in the queue"));
        return apply(next, ClaimAction.ASSIGN, new ActionRequest(officerId, "Picked up from queue", null));
    }

    private Claim apply(Claim claim, ClaimAction action, ActionRequest req) {
        ClaimStatus from = claim.getStatus();
        ClaimStatus to = ClaimStateMachine.next(from, action);   // throws if the transition is illegal
        authorise(claim, action, req.actorId());
        Instant now = clock.instant();

        switch (action) {
            case ASSIGN -> claim.assignTo(req.actorId());
            case REQUEST_INFO, PROVIDE_INFO, REJECT -> requireNote(action, req);
            case ASSESS -> {
                BigDecimal amount = req.amount();
                if (amount == null || amount.signum() <= 0) {
                    throw new BusinessRuleException("ASSESS requires a positive amount");
                }
                if (amount.compareTo(claim.getClaimedAmount()) > 0) {
                    throw new BusinessRuleException("Assessed amount cannot exceed the claimed amount of "
                            + claim.getClaimedAmount());
                }
                claim.assess(amount);
            }
            case SETTLE -> claim.settle();
            default -> { /* APPROVE, WITHDRAW: no extra payload */ }
        }

        claim.moveTo(to, now);
        claims.saveAndFlush(claim);   // flush now so an optimistic-lock clash surfaces here and version is current
        history.save(new ClaimHistory(claim.getId(), action, from, to, req.actorId(), req.note(), now));
        events.publishEvent(ClaimEvent.from(claim, action, now));
        return claim;
    }

    /** Officers may only act on claims assigned to them; claimants only on their own claims. */
    private void authorise(Claim claim, ClaimAction action, String actorId) {
        switch (action) {
            case ASSIGN -> { /* any officer may pick up a SUBMITTED claim */ }
            case PROVIDE_INFO, WITHDRAW -> {
                if (!claim.getClaimantId().equals(actorId)) {
                    throw new ForbiddenActionException("Only the claimant can perform " + action);
                }
            }
            default -> {
                if (!actorId.equals(claim.getAssignedOfficerId())) {
                    throw new ForbiddenActionException("Claim is assigned to "
                            + claim.getAssignedOfficerId() + "; only the assigned officer can perform " + action);
                }
            }
        }
    }

    private void requireNote(ClaimAction action, ActionRequest req) {
        if (req.note() == null || req.note().isBlank()) {
            throw new BusinessRuleException(action + " requires a note");
        }
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public Claim get(UUID id) {
        return find(id);
    }

    @Transactional(readOnly = true)
    public Claim track(String claimNumber, String email) {
        Claim c = claims.findByClaimNumber(claimNumber)
                .orElseThrow(() -> new NotFoundException("Claim " + claimNumber + " not found"));
        if (!c.getClaimantEmail().equalsIgnoreCase(email)) {
            // same message as not-found so claim numbers cannot be probed
            throw new NotFoundException("Claim " + claimNumber + " not found");
        }
        return c;
    }

    @Transactional(readOnly = true)
    public List<ClaimHistory> historyOf(UUID id) {
        find(id);
        return history.findByClaimIdOrderByOccurredAtAsc(id);
    }

    @Transactional(readOnly = true)
    public Page<Claim> search(ClaimStatus status, String officerId, String market, String claimantId,
                              boolean unassignedOnly, boolean openOnly, Pageable pageable) {
        Specification<Claim> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            if (officerId != null) ps.add(cb.equal(root.get("assignedOfficerId"), officerId));
            if (market != null) ps.add(cb.equal(root.get("market"), market));
            if (claimantId != null) ps.add(cb.equal(root.get("claimantId"), claimantId));
            if (unassignedOnly) ps.add(cb.isNull(root.get("assignedOfficerId")));
            if (openOnly) ps.add(cb.isNull(root.get("closedAt")));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return claims.findAll(spec, pageable);
    }

    private Claim find(UUID id) {
        return claims.findById(id).orElseThrow(() -> new NotFoundException("Claim " + id + " not found"));
    }
}
