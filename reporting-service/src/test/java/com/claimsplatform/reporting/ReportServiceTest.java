package com.claimsplatform.reporting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    private JdbcTemplate jdbc;
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        reportService = new ReportService(jdbc);
    }

    @Test
    void performanceMapsAggregatedClaimSummaryRows() throws SQLException {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<ReportService.PerformanceRow> mapper = invocation.getArgument(1);
            return List.of(
                    mapper.mapRow(row("officer", "officer-1", "currency", "USD", "settled", 2L,
                            "rejected", 1L, "open_cnt", 3L, "avg_hours", 12.345, "paid", "1500.00"), 0),
                    mapper.mapRow(row("officer", "officer-2", "currency", "EUR", "settled", 0L,
                            "rejected", 0L, "open_cnt", 1L, "avg_hours", null, "paid", "0.00"), 1)
            );
        }).when(jdbc).query(anyString(), any(RowMapper.class));

        List<ReportService.PerformanceRow> rows = reportService.performance();

        assertEquals(2, rows.size());
        assertEquals(new ReportService.PerformanceRow("officer-1", "USD", 2, 1, 3,
                12.35, new BigDecimal("1500.00")), rows.get(0));
        assertEquals("officer-2", rows.get(1).officerId());
        assertEquals("EUR", rows.get(1).currency());
        assertEquals(1L, rows.get(1).open());
        assertNull(rows.get(1).avgResolutionHours());
        assertEquals(new BigDecimal("0.00"), rows.get(1).totalPaid());
    }

    @Test
    void summaryMapsClaimCountsByMarketAndStatus() throws SQLException {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<ReportService.StatusCount> mapper = invocation.getArgument(1);
            return List.of(
                    mapper.mapRow(row("market", "US", "status", "APPROVED", "cnt", 4L), 0),
                    mapper.mapRow(row("market", "US", "status", "SETTLED", "cnt", 2L), 1),
                    mapper.mapRow(row("market", "UK", "status", "REJECTED", "cnt", 1L), 2)
            );
        }).when(jdbc).query(anyString(), any(RowMapper.class));

        List<ReportService.StatusCount> rows = reportService.summary();

        assertEquals(List.of(
                new ReportService.StatusCount("US", "APPROVED", 4),
                new ReportService.StatusCount("US", "SETTLED", 2),
                new ReportService.StatusCount("UK", "REJECTED", 1)
        ), rows);
    }

    private ResultSet row(Object... values) throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        for (int i = 0; i < values.length; i += 2) {
            String column = (String) values[i];
            Object value = values[i + 1];
            switch (column) {
                case "avg_hours" -> {
                    when(resultSet.getDouble(column)).thenReturn(value == null ? 0.0 : ((Number) value).doubleValue());
                    when(resultSet.wasNull()).thenReturn(value == null);
                }
                case "settled", "rejected", "open_cnt", "cnt" ->
                        when(resultSet.getLong(column)).thenReturn(((Number) value).longValue());
                case "paid" -> when(resultSet.getBigDecimal(column)).thenReturn(new BigDecimal((String) value));
                default -> when(resultSet.getString(column)).thenReturn((String) value);
            }
        }
        return resultSet;
    }
}
