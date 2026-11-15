package com.claimsplatform.claims.domain;

public enum ClaimAction {
    SUBMIT,        // recorded in history only; creates the claim
    ASSIGN,        // officer picks up the claim
    REQUEST_INFO,  // officer asks the claimant for more information
    PROVIDE_INFO,  // claimant responds
    ASSESS,        // officer sets assessed amount
    APPROVE,
    REJECT,
    SETTLE,
    WITHDRAW       // claimant withdraws
}
