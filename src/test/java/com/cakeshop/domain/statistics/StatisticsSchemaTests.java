package com.cakeshop.domain.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** 통계 집계 결과와 실행 기록의 스키마 계약을 검증한다. */
@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StatisticsSchemaTests {

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 9);
    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 8, 10, 0, 10);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void dailyStatistics_sameDate_replacesOneRow() {
        insertDailyStatistics(10, new BigDecimal("100000"));

        jdbcTemplate.update(
                """
                INSERT INTO daily_statistics (
                    statistics_date,
                    total_order_count,
                    completed_order_count,
                    canceled_order_count,
                    total_sales_amount,
                    aggregated_at
                )
                VALUES (?, 12, 8, 2, 120000, ?)
                ON DUPLICATE KEY UPDATE
                    total_order_count = VALUES(total_order_count),
                    completed_order_count = VALUES(completed_order_count),
                    canceled_order_count = VALUES(canceled_order_count),
                    total_sales_amount = VALUES(total_sales_amount),
                    aggregated_at = VALUES(aggregated_at)
                """,
                TARGET_DATE,
                STARTED_AT.plusMinutes(1)
        );

        DailyStatisticsRow row = jdbcTemplate.queryForObject(
                """
                SELECT total_order_count, total_sales_amount
                FROM daily_statistics
                WHERE statistics_date = ?
                """,
                (resultSet, rowNum) -> new DailyStatisticsRow(
                        resultSet.getLong("total_order_count"),
                        resultSet.getBigDecimal("total_sales_amount")
                ),
                TARGET_DATE
        );

        assertThat(row).isEqualTo(new DailyStatisticsRow(12, new BigDecimal("120000")));
    }

    @Test
    void statisticsBatchRuns_runningExists_rejectsAnotherRunningRun() {
        insertRunningBatch("DAILY", TARGET_DATE);

        assertThatThrownBy(() -> insertRunningBatch("BACKFILL", TARGET_DATE.minusDays(6)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void statisticsBatchRuns_previousRunCompleted_allowsNextRunningRun() {
        long firstRunId = insertRunningBatch("DAILY", TARGET_DATE);
        jdbcTemplate.update(
                """
                UPDATE statistics_batch_runs
                SET status = 'SUCCEEDED', completed_at = ?
                WHERE id = ?
                """,
                STARTED_AT.plusMinutes(5),
                firstRunId
        );

        long secondRunId = insertRunningBatch("BACKFILL", TARGET_DATE.minusDays(6));

        assertThat(secondRunId).isPositive();
        Integer runningCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM statistics_batch_runs WHERE status = 'RUNNING'",
                Integer.class
        );
        assertThat(runningCount).isOne();
    }

    @Test
    void statisticsBatchRuns_runningInserted_recordsHeartbeat() {
        long runId = insertRunningBatch("DAILY", TARGET_DATE);

        LocalDateTime heartbeatAt = jdbcTemplate.queryForObject(
                "SELECT heartbeat_at FROM statistics_batch_runs WHERE id = ?",
                LocalDateTime.class,
                runId
        );

        assertThat(heartbeatAt).isNotNull();
    }

    @Test
    void statisticsBatchRuns_invalidStatus_rejectsRun() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO statistics_batch_runs (
                    batch_type, status, target_start_date, target_end_date, started_at
                )
                VALUES ('DAILY', 'PENDING', ?, ?, ?)
                """,
                TARGET_DATE,
                TARGET_DATE,
                STARTED_AT
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertDailyStatistics(long totalOrderCount, BigDecimal totalSalesAmount) {
        jdbcTemplate.update(
                """
                INSERT INTO daily_statistics (
                    statistics_date,
                    total_order_count,
                    completed_order_count,
                    canceled_order_count,
                    total_sales_amount,
                    aggregated_at
                )
                VALUES (?, ?, 7, 2, ?, ?)
                """,
                TARGET_DATE,
                totalOrderCount,
                totalSalesAmount,
                STARTED_AT
        );
    }

    private long insertRunningBatch(String batchType, LocalDate targetStartDate) {
        jdbcTemplate.update(
                """
                INSERT INTO statistics_batch_runs (
                    batch_type,
                    status,
                    target_start_date,
                    target_end_date,
                    started_at
                )
                VALUES (?, 'RUNNING', ?, ?, ?)
                """,
                batchType,
                targetStartDate,
                TARGET_DATE,
                STARTED_AT
        );

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private record DailyStatisticsRow(long totalOrderCount, BigDecimal totalSalesAmount) {
    }
}
