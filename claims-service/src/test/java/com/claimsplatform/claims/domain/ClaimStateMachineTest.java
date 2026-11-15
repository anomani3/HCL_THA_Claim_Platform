package com.claimsplatform.claims.domain;

import com.claimsplatform.claims.exception.InvalidTransitionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimStateMachineTest {

    @Test
    void happyPathToSettlement() {
        ClaimStatus s = ClaimStatus.SUBMITTED;
        s = ClaimStateMachine.next(s, ClaimAction.ASSIGN);
        assertEquals(ClaimStatus.UNDER_REVIEW, s);
        s = ClaimStateMachine.next(s, ClaimAction.REQUEST_INFO);
        assertEquals(ClaimStatus.INFO_REQUESTED, s);
        s = ClaimStateMachine.next(s, ClaimAction.PROVIDE_INFO);
        assertEquals(ClaimStatus.UNDER_REVIEW, s);
        s = ClaimStateMachine.next(s, ClaimAction.ASSESS);
        s = ClaimStateMachine.next(s, ClaimAction.APPROVE);
        s = ClaimStateMachine.next(s, ClaimAction.SETTLE);
        assertEquals(ClaimStatus.SETTLED, s);
    }

    @Test
    void cannotSkipAssessmentOrSettleEarly() {
        assertThrows(InvalidTransitionException.class,
                () -> ClaimStateMachine.next(ClaimStatus.UNDER_REVIEW, ClaimAction.APPROVE));
        assertThrows(InvalidTransitionException.class,
                () -> ClaimStateMachine.next(ClaimStatus.ASSESSED, ClaimAction.SETTLE));
        assertThrows(InvalidTransitionException.class,
                () -> ClaimStateMachine.next(ClaimStatus.SUBMITTED, ClaimAction.ASSESS));
    }

    @Test
    void terminalStatesAcceptNothing() {
        for (ClaimStatus s : ClaimStatus.values()) {
            if (s.isTerminal()) {
                assertTrue(ClaimStateMachine.allowedActions(s).isEmpty(), s + " should be terminal");
            }
        }
    }

    @Test
    void rejectAllowedBeforeAndAfterAssessment() {
        assertEquals(ClaimStatus.REJECTED, ClaimStateMachine.next(ClaimStatus.UNDER_REVIEW, ClaimAction.REJECT));
        assertEquals(ClaimStatus.REJECTED, ClaimStateMachine.next(ClaimStatus.ASSESSED, ClaimAction.REJECT));
        assertThrows(InvalidTransitionException.class,
                () -> ClaimStateMachine.next(ClaimStatus.APPROVED, ClaimAction.REJECT));
    }

    @Test
    void exposureUsesAssessedAmountWhenKnownAndDropsToZeroWhenClosed() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Claim c = new Claim(UUID.randomUUID(), "CLM-SG-2026-1", ClaimType.MOTOR, "SG", "SGD", "POL-1",
                "C1", "Jane", "jane@example.com", LocalDate.of(2025, 12, 31), null, null, "dent",
                new BigDecimal("5000.00"), now);
        assertEquals(new BigDecimal("5000.00"), c.outstandingExposure());

        c.assess(new BigDecimal("4200.00"));
        c.moveTo(ClaimStatus.ASSESSED, now);
        assertEquals(new BigDecimal("4200.00"), c.outstandingExposure());

        c.settle();
        c.moveTo(ClaimStatus.SETTLED, now);
        assertEquals(BigDecimal.ZERO, c.outstandingExposure());
        assertEquals(new BigDecimal("4200.00"), c.getPaidAmount());
        assertNotNull(c.getClosedAt());
    }
}
