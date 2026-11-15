package com.claimsplatform.claims.exception;

import com.claimsplatform.claims.domain.ClaimAction;
import com.claimsplatform.claims.domain.ClaimStatus;

public class InvalidTransitionException extends RuntimeException {
    public InvalidTransitionException(ClaimStatus from, ClaimAction action) {
        super("Action " + action + " is not allowed when claim is " + from);
    }
}
