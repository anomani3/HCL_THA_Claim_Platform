package com.claimsplatform.reporting;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read side. Plain SQL over the projection table: aggregates are what this service is for,
 * and amounts are never summed across currencies.
 */
@Service
public class ReportService {

    /** "Open" = still carries liability or needs work. */
    private static final String OPEN = "('SUBMITTED','UNDER_REVIEW','INFO_REQUESTED','ASSESSED','APPROVED')";

    private final JdbcTemplate jdbc;

    public ReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ DTOs

    public record OfficerWorkload(String officerId, long openClaims, Map<String, Long> byStatus,
                                  Map<String, BigDecimal> exposureByCurrency) {
    }

    public record ExposureRow(String market, String claimType, String currency, long openClaims,
                              BigDecimal outstandingExposure) {
    }

    public record ExposureReport(List<ExposureRow> rows, Map<String, BigDecimal> totalsByCurrency) {
    }

    public record PerformanceRow(String officerId, String currency, long settled, long rejected, long open,
                                 Double avgResolutionHours, BigDecimal totalPaid) {
    }

    public record StatusCount(String market, String status, long count) {
    }

    // ------------------------------------------------------------------ reports

    /** Team workload: open claims per officer (UNASSIGNED = intake queue), by status, with exposure. */
    public List<OfficerWorkload> workload() {
        String sql = "select coalesce(assigned_officer_id,'UNASSIGNED') as officer, status, currency, "
                + "count(*) as cnt, coalesce(sum(outstanding_exposure),0) as exposure "
                + "from claim_summary where status in " + OPEN
                + " group by 1,2,3 order by 1,2,3";

        Map<String, Map<String, Long>> statusByOfficer = new LinkedHashMap<>();
        Map<String, Map<String, BigDecimal>> exposureByOfficer = new LinkedHashMap<>();

        jdbc.query(sql, rs -> {
            String officer = rs.getString("officer");
            statusByOfficer.computeIfAbsent(officer, k -> new LinkedHashMap<>())
                    .merge(rs.getString("status"), rs.getLong("cnt"), Long::sum);
            exposureByOfficer.computeIfAbsent(officer, k -> new LinkedHashMap<>())
                    .merge(rs.getString("currency"), rs.getBigDecimal("exposure"), BigDecimal::add);
        });

        List<OfficerWorkload> result = new ArrayList<>();
        statusByOfficer.forEach((officer, byStatus) -> {
            long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
            result.add(new OfficerWorkload(officer, total, byStatus, exposureByOfficer.get(officer)));
        });
        return result;
    }

    /** Outstanding liability by market / claim type / currency, optionally filtered. */
    public ExposureReport exposure(String market, String claimType) {
        StringBuilder sql = new StringBuilder(
                "select market, claim_type, currency, count(*) as cnt, "
                        + "coalesce(sum(outstanding_exposure),0) as exposure "
                        + "from claim_summary where status in " + OPEN);
        List<Object> args = new ArrayList<>();
        if (market != null && !market.isBlank()) {
            sql.append(" and market = ?");
            args.add(market);
        }
        if (claimType != null && !claimType.isBlank()) {
            sql.append(" and claim_type = ?");
            args.add(claimType);
        }
        sql.append(" group by market, claim_type, currency order by market, claim_type, currency");

        List<ExposureRow> rows = jdbc.query(sql.toString(),
                (rs, i) -> new ExposureRow(rs.getString("market"), rs.getString("claim_type"),
                        rs.getString("currency"), rs.getLong("cnt"), rs.getBigDecimal("exposure")),
                args.toArray());

        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        rows.forEach(r -> totals.merge(r.currency(), r.outstandingExposure(), BigDecimal::add));
        return new ExposureReport(rows, totals);
    }

    /** Throughput and speed per officer. Resolution time = submitted -> closed (settled/rejected). */
    public List<PerformanceRow> performance() {
        String sql = "select assigned_officer_id as officer, currency, "
                + "count(*) filter (where status = 'SETTLED') as settled, "
                + "count(*) filter (where status = 'REJECTED') as rejected, "
                + "count(*) filter (where status in " + OPEN + ") as open_cnt, "
                + "avg(extract(epoch from (closed_at - submitted_at)) / 3600.0) "
                + "    filter (where status in ('SETTLED','REJECTED')) as avg_hours, "
                + "coalesce(sum(paid_amount),0) as paid "
                + "from claim_summary where assigned_officer_id is not null "
                + "group by assigned_officer_id, currency order by assigned_officer_id, currency";
        return jdbc.query(sql, (rs, i) -> {
            double avg = rs.getDouble("avg_hours");
            Double avgHours = rs.wasNull() ? null : Math.round(avg * 100.0) / 100.0;
            return new PerformanceRow(rs.getString("officer"), rs.getString("currency"),
                    rs.getLong("settled"), rs.getLong("rejected"), rs.getLong("open_cnt"),
                    avgHours, rs.getBigDecimal("paid"));
        });
    }

    /** Claim counts by market and status (manager overview). */
    public List<StatusCount> summary() {
        return jdbc.query("select market, status, count(*) as cnt from claim_summary "
                        + "group by market, status order by market, status",
                (rs, i) -> new StatusCount(rs.getString("market"), rs.getString("status"), rs.getLong("cnt")));
    }
}
