package com.claimsplatform.claims.web;

import com.claimsplatform.claims.domain.Claim;
import com.claimsplatform.claims.domain.ClaimAction;
import com.claimsplatform.claims.domain.ClaimStatus;
import com.claimsplatform.claims.exception.ForbiddenActionException;
import com.claimsplatform.claims.service.ClaimService;
import com.claimsplatform.claims.web.dto.ActionRequest;
import com.claimsplatform.claims.web.dto.ClaimResponse;
import com.claimsplatform.claims.web.dto.CreateClaimRequest;
import com.claimsplatform.claims.web.dto.HistoryResponse;
import com.claimsplatform.claims.web.dto.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/claims")
@Tag(name = "Claims", description = "Claim intake, tracking and lifecycle")
public class ClaimController {

    private final ClaimService service;

    public ClaimController(ClaimService service) {
        this.service = service;
    }

    // ---------------------------------------------------------------- claimant

    @PostMapping
    @Operation(summary = "Report an incident (submit a claim)")
    public ResponseEntity<ClaimResponse> submit(@Valid @RequestBody CreateClaimRequest request,
                                                @AuthenticationPrincipal Jwt jwt) {
        CreateClaimRequest ownedRequest = new CreateClaimRequest(request.claimType(), request.market(), request.currency(),
                request.policyNumber(), jwt.getSubject(), jwt.getClaimAsString("name"), jwt.getClaimAsString("email"),
                request.incidentDate(), request.incidentLocation(), request.assetReference(), request.description(),
                request.claimedAmount());
        Claim c = service.submit(ownedRequest);
        return ResponseEntity.created(URI.create("/api/claims/" + c.getId())).body(ClaimResponse.from(c));
    }

    @GetMapping("/track/{claimNumber}")
    @Operation(summary = "Claimant tracking: look up a claim by number + email")
    public ClaimResponse track(@PathVariable String claimNumber, @RequestParam String email) {
        return ClaimResponse.from(service.track(claimNumber, email));
    }

    @PostMapping("/{id}/provide-info")
    @Operation(summary = "Claimant supplies the information an officer asked for (actorId = claimantId)")
    public ClaimResponse provideInfo(@PathVariable UUID id, @Valid @RequestBody ActionRequest body,
                                     @AuthenticationPrincipal Jwt jwt) {
        return act(id, ClaimAction.PROVIDE_INFO, body, jwt);
    }

    @PostMapping("/{id}/withdraw")
    @Operation(summary = "Claimant withdraws the claim (actorId = claimantId)")
    public ClaimResponse withdraw(@PathVariable UUID id, @Valid @RequestBody ActionRequest body,
                                  @AuthenticationPrincipal Jwt jwt) {
        return act(id, ClaimAction.WITHDRAW, body, jwt);
    }

    // ---------------------------------------------------------------- shared reads

    @GetMapping("/{id}")
    @Operation(summary = "Get a claim (includes the actions currently allowed)")
    public ClaimResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        Claim claim = service.get(id);
        assertVisible(claim, jwt);
        return ClaimResponse.from(claim);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Audit trail: every status change, who did it and the notes")
    public List<HistoryResponse> history(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        assertVisible(service.get(id), jwt);
        return service.historyOf(id).stream().map(HistoryResponse::from).toList();
    }

    @GetMapping
    @Operation(summary = "Search claims. Officer workload = ?assignedOfficerId=X&openOnly=true; intake queue = ?unassigned=true&status=SUBMITTED")
    public PageResult<ClaimResponse> search(
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(required = false) String assignedOfficerId,
            @RequestParam(required = false) String market,
            @RequestParam(required = false) String claimantId,
            @RequestParam(defaultValue = "false") boolean unassigned,
            @RequestParam(defaultValue = "false") boolean openOnly,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        if (hasRole(jwt, "MANAGER")) {
            return PageResult.of(service.search(status, assignedOfficerId, market, claimantId, unassigned, openOnly, pageable),
                    ClaimResponse::from);
        }
        if (hasRole(jwt, "OFFICER")) {
            if (unassigned) {
                return PageResult.of(service.search(ClaimStatus.SUBMITTED, null, market, null, true, false, pageable),
                        ClaimResponse::from);
            }
            return PageResult.of(service.search(status, jwt.getSubject(), market, null, false, openOnly, pageable),
                    ClaimResponse::from);
        }
        return PageResult.of(service.search(status, null, market, jwt.getSubject(), false, false, pageable),
                ClaimResponse::from);
    }

    // ---------------------------------------------------------------- claims staff

    @PostMapping("/queue/pick")
    @Operation(summary = "Pick up the oldest unassigned claim (race-safe; optional market filter)")
    public ClaimResponse pickNext(@RequestParam(required = false) String market, @AuthenticationPrincipal Jwt jwt) {
        return ClaimResponse.from(service.pickNext(jwt.getSubject(), market));
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Officer picks up a specific claim (actorId = officerId)")
    public ClaimResponse assign(@PathVariable UUID id, @Valid @RequestBody ActionRequest body,
                                @AuthenticationPrincipal Jwt jwt) {
        return act(id, ClaimAction.ASSIGN, body, jwt);
    }

    @PostMapping("/{id}/request-info")
    @Operation(summary = "Ask the claimant for more information (note required)")
    public ClaimResponse requestInfo(@PathVariable UUID id, @Valid @RequestBody ActionRequest body,
                                     @AuthenticationPrincipal Jwt jwt) {
        return act(id, ClaimAction.REQUEST_INFO, body, jwt);
    }

    @PostMapping("/{id}/assess")
    @Operation(summary = "Set the assessed (payable) amount; must be <= claimed amount")
    public ClaimResponse assess(@PathVariable UUID id, @Valid @RequestBody ActionRequest body,
                                @AuthenticationPrincipal Jwt jwt) {
        return act(id, ClaimAction.ASSESS, body, jwt);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve an assessed claim")
    public ClaimResponse approve(@PathVariable UUID id, @Valid @RequestBody ActionRequest body,
                                 @AuthenticationPrincipal Jwt jwt) {
        return act(id, ClaimAction.APPROVE, body, jwt);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a claim (reason in note, required)")
    public ClaimResponse reject(@PathVariable UUID id, @Valid @RequestBody ActionRequest body,
                                @AuthenticationPrincipal Jwt jwt) {
        return act(id, ClaimAction.REJECT, body, jwt);
    }

    @PostMapping("/{id}/settle")
    @Operation(summary = "Mark an approved claim as paid")
    public ClaimResponse settle(@PathVariable UUID id, @Valid @RequestBody ActionRequest body,
                                @AuthenticationPrincipal Jwt jwt) {
        return act(id, ClaimAction.SETTLE, body, jwt);
    }

    private ClaimResponse act(UUID id, ClaimAction action, ActionRequest body, Jwt jwt) {
        return ClaimResponse.from(service.perform(id, action,
                new ActionRequest(jwt.getSubject(), body.note(), body.amount())));
    }

    private void assertVisible(Claim claim, Jwt jwt) {
        if (hasRole(jwt, "MANAGER")) return;
        if (hasRole(jwt, "CLAIMANT") && claim.getClaimantId().equals(jwt.getSubject())) return;
        if (hasRole(jwt, "OFFICER") && (claim.getAssignedOfficerId() == null
                || claim.getAssignedOfficerId().equals(jwt.getSubject()))) return;
        throw new ForbiddenActionException("You do not have access to this claim");
    }

    private boolean hasRole(Jwt jwt, String role) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains(role);
    }
}
