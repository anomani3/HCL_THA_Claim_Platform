package com.claimsplatform.claims.web;

import com.claimsplatform.claims.domain.Claim;
import com.claimsplatform.claims.domain.ClaimAction;
import com.claimsplatform.claims.domain.ClaimStatus;
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
    public ResponseEntity<ClaimResponse> submit(@Valid @RequestBody CreateClaimRequest request) {
        Claim c = service.submit(request);
        return ResponseEntity.created(URI.create("/api/claims/" + c.getId())).body(ClaimResponse.from(c));
    }

    @GetMapping("/track/{claimNumber}")
    @Operation(summary = "Claimant tracking: look up a claim by number + email")
    public ClaimResponse track(@PathVariable String claimNumber, @RequestParam String email) {
        return ClaimResponse.from(service.track(claimNumber, email));
    }

    @PostMapping("/{id}/provide-info")
    @Operation(summary = "Claimant supplies the information an officer asked for (actorId = claimantId)")
    public ClaimResponse provideInfo(@PathVariable UUID id, @Valid @RequestBody ActionRequest body) {
        return act(id, ClaimAction.PROVIDE_INFO, body);
    }

    @PostMapping("/{id}/withdraw")
    @Operation(summary = "Claimant withdraws the claim (actorId = claimantId)")
    public ClaimResponse withdraw(@PathVariable UUID id, @Valid @RequestBody ActionRequest body) {
        return act(id, ClaimAction.WITHDRAW, body);
    }

    // ---------------------------------------------------------------- shared reads

    @GetMapping("/{id}")
    @Operation(summary = "Get a claim (includes the actions currently allowed)")
    public ClaimResponse get(@PathVariable UUID id) {
        return ClaimResponse.from(service.get(id));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Audit trail: every status change, who did it and the notes")
    public List<HistoryResponse> history(@PathVariable UUID id) {
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
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResult.of(
                service.search(status, assignedOfficerId, market, claimantId, unassigned, openOnly, pageable),
                ClaimResponse::from);
    }

    // ---------------------------------------------------------------- claims staff

    @PostMapping("/queue/pick")
    @Operation(summary = "Pick up the oldest unassigned claim (race-safe; optional market filter)")
    public ClaimResponse pickNext(@RequestParam String officerId, @RequestParam(required = false) String market) {
        return ClaimResponse.from(service.pickNext(officerId, market));
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Officer picks up a specific claim (actorId = officerId)")
    public ClaimResponse assign(@PathVariable UUID id, @Valid @RequestBody ActionRequest body) {
        return act(id, ClaimAction.ASSIGN, body);
    }

    @PostMapping("/{id}/request-info")
    @Operation(summary = "Ask the claimant for more information (note required)")
    public ClaimResponse requestInfo(@PathVariable UUID id, @Valid @RequestBody ActionRequest body) {
        return act(id, ClaimAction.REQUEST_INFO, body);
    }

    @PostMapping("/{id}/assess")
    @Operation(summary = "Set the assessed (payable) amount; must be <= claimed amount")
    public ClaimResponse assess(@PathVariable UUID id, @Valid @RequestBody ActionRequest body) {
        return act(id, ClaimAction.ASSESS, body);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve an assessed claim")
    public ClaimResponse approve(@PathVariable UUID id, @Valid @RequestBody ActionRequest body) {
        return act(id, ClaimAction.APPROVE, body);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a claim (reason in note, required)")
    public ClaimResponse reject(@PathVariable UUID id, @Valid @RequestBody ActionRequest body) {
        return act(id, ClaimAction.REJECT, body);
    }

    @PostMapping("/{id}/settle")
    @Operation(summary = "Mark an approved claim as paid")
    public ClaimResponse settle(@PathVariable UUID id, @Valid @RequestBody ActionRequest body) {
        return act(id, ClaimAction.SETTLE, body);
    }

    private ClaimResponse act(UUID id, ClaimAction action, ActionRequest body) {
        return ClaimResponse.from(service.perform(id, action, body));
    }
}
