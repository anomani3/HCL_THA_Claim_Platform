package com.claimsplatform.claims.domain;

import com.claimsplatform.claims.exception.InvalidTransitionException;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static com.claimsplatform.claims.domain.ClaimAction.*;
import static com.claimsplatform.claims.domain.ClaimStatus.*;

/**
 * The single source of truth for which lifecycle transitions are legal.
 * Pure and stateless so it is trivially unit-testable.
 *
 * <pre>
 * SUBMITTED --ASSIGN--> UNDER_REVIEW --REQUEST_INFO--> INFO_REQUESTED --PROVIDE_INFO--> UNDER_REVIEW
 *                           |
 *                         ASSESS --> ASSESSED --APPROVE--> APPROVED --SETTLE--> SETTLED
 *                           |            |
 *                         REJECT       REJECT  (--> REJECTED)
 * WITHDRAW allowed from SUBMITTED, UNDER_REVIEW, INFO_REQUESTED (--> WITHDRAWN)
 * </pre>
 */
public final class ClaimStateMachine {

    private static final Map<ClaimStatus, Map<ClaimAction, ClaimStatus>> TRANSITIONS =
            new EnumMap<>(ClaimStatus.class);

    static {
        define(SUBMITTED, ASSIGN, UNDER_REVIEW);
        define(SUBMITTED, WITHDRAW, WITHDRAWN);

        define(UNDER_REVIEW, REQUEST_INFO, INFO_REQUESTED);
        define(UNDER_REVIEW, ASSESS, ASSESSED);
        define(UNDER_REVIEW, REJECT, REJECTED);
        define(UNDER_REVIEW, WITHDRAW, WITHDRAWN);

        define(INFO_REQUESTED, PROVIDE_INFO, UNDER_REVIEW);
        define(INFO_REQUESTED, WITHDRAW, WITHDRAWN);

        define(ASSESSED, APPROVE, APPROVED);
        define(ASSESSED, REJECT, REJECTED);

        define(APPROVED, SETTLE, SETTLED);
    }

    private ClaimStateMachine() {
    }

    private static void define(ClaimStatus from, ClaimAction action, ClaimStatus to) {
        TRANSITIONS.computeIfAbsent(from, k -> new EnumMap<>(ClaimAction.class)).put(action, to);
    }

    public static ClaimStatus next(ClaimStatus from, ClaimAction action) {
        ClaimStatus to = TRANSITIONS.getOrDefault(from, Map.of()).get(action);
        if (to == null) {
            throw new InvalidTransitionException(from, action);
        }
        return to;
    }

    public static Set<ClaimAction> allowedActions(ClaimStatus from) {
        Map<ClaimAction, ClaimStatus> m = TRANSITIONS.get(from);
        return m == null ? Set.of() : Set.copyOf(m.keySet());
    }
}
