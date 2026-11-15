package com.claimsplatform.reporting;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports", description = "Manager and team-lead views. Eventually consistent with claims-service (Kafka).")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/workload")
    @Operation(summary = "Open claims per officer (and the unassigned intake queue), by status, with exposure")
    public List<ReportService.OfficerWorkload> workload() {
        return service.workload();
    }

    @GetMapping("/exposure")
    @Operation(summary = "Outstanding liability exposure by market / claim type / currency")
    public ReportService.ExposureReport exposure(@RequestParam(required = false) String market,
                                                 @RequestParam(required = false) String claimType) {
        return service.exposure(market, claimType);
    }

    @GetMapping("/performance")
    @Operation(summary = "Officer performance: settled / rejected / open counts, average resolution hours, total paid")
    public List<ReportService.PerformanceRow> performance() {

        return service.performance();
    }

    @GetMapping("/summary")
    @Operation(summary = "Claim counts by market and status")
    public List<ReportService.StatusCount> summary() {
        return service.summary();

    }


}
