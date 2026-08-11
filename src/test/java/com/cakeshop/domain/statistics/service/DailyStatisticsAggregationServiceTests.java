package com.cakeshop.domain.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.cakeshop.domain.statistics.entity.StatisticsBatchRun;
import com.cakeshop.domain.statistics.mapper.DailyStatisticsAggregationMapper;
import com.cakeshop.domain.statistics.mapper.DailyStatisticsSourceReadModelMapper;
import com.cakeshop.domain.statistics.mapper.StatisticsBatchRunMapper;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@MybatisTest
@Import({
        DailyStatisticsAggregationService.class,
        StatisticsAggregationTransactionService.class
})
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DailyStatisticsAggregationServiceTests {

    private final DailyStatisticsAggregationService service;
    private final StatisticsBatchRunMapper batchRunMapper;
    private final JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private DailyStatisticsAggregationMapper aggregationMapper;

    @MockitoSpyBean
    private DailyStatisticsSourceReadModelMapper sourceReadModelMapper;

    @Autowired
    DailyStatisticsAggregationServiceTests(
            DailyStatisticsAggregationService service,
            StatisticsBatchRunMapper batchRunMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.service = service;
        this.batchRunMapper = batchRunMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        deleteAggregationData();
    }

    @AfterEach
    void tearDown() {
        deleteAggregationData();
    }

    @Test
    void aggregateDailyStatistics_noSuccessfulBackfill_skipsRun() {
        boolean executed = service.aggregateDailyStatistics();

        assertThat(executed).isFalse();
        assertThat(countBatchRuns()).isZero();
    }

    @Test
    void aggregateDailyStatistics_successfulBackfill_aggregatesYesterdayAndCompletesRun() {
        LocalDate yesterday = findYesterday();
        LocalDateTime backfillStartedAt = insertSuccessfulBackfill(yesterday);

        boolean executed = service.aggregateDailyStatistics();

        assertThat(executed).isTrue();
        assertThat(findDailyStatistics(yesterday)).isEqualTo(
                new DailyStatisticsRow(0, 0, 0, BigDecimal.ZERO)
        );
        BatchRunRow dailyRun = findLatestDailyRun();
        assertThat(dailyRun.status()).isEqualTo("SUCCEEDED");
        assertThat(dailyRun.sourceWindowStartedAt()).isEqualTo(backfillStartedAt);
        assertThat(dailyRun.completedAt()).isNotNull();
    }

    @Test
    void aggregateDailyStatistics_missedEmptyDates_fillsEveryDateThroughYesterday() {
        LocalDate yesterday = findYesterday();
        LocalDate latestSuccessfulDate = yesterday.minusDays(3);
        insertSuccessfulBackfill(latestSuccessfulDate);

        boolean executed = service.aggregateDailyStatistics();

        assertThat(executed).isTrue();
        for (LocalDate date = latestSuccessfulDate.plusDays(1);
                !date.isAfter(yesterday);
                date = date.plusDays(1)) {
            assertThat(findDailyStatistics(date)).isEqualTo(
                    new DailyStatisticsRow(0, 0, 0, BigDecimal.ZERO)
            );
        }
    }

    @Test
    void aggregateDailyStatistics_resumedBackfill_usesFirstAttemptAsChangeWindowStart() {
        LocalDate yesterday = findYesterday();
        StatisticsBatchRun failedRun = StatisticsBatchRun.backfill(
                yesterday.minusDays(6),
                yesterday
        );
        batchRunMapper.insertRunningBatch(failedRun);
        batchRunMapper.updateBackfillProgress(failedRun.getId(), yesterday.minusDays(3));
        batchRunMapper.completeFailed(failedRun.getId());
        LocalDateTime firstAttemptStartedAt = jdbcTemplate.queryForObject(
                "SELECT started_at FROM statistics_batch_runs WHERE id = ?",
                LocalDateTime.class,
                failedRun.getId()
        );
        insertSuccessfulBackfill(yesterday);

        boolean executed = service.aggregateDailyStatistics();

        assertThat(executed).isTrue();
        verify(sourceReadModelMapper).findChangedStatisticsDates(
                eq(firstAttemptStartedAt),
                any(LocalDateTime.class),
                eq(yesterday)
        );
    }

    @Test
    void aggregateDailyStatistics_runningRunExists_skipsNewRun() {
        LocalDate yesterday = findYesterday();
        insertSuccessfulBackfill(yesterday);
        StatisticsBatchRun running = StatisticsBatchRun.backfill(yesterday.minusDays(6), yesterday);
        batchRunMapper.insertRunningBatch(running);

        boolean executed = service.aggregateDailyStatistics();

        assertThat(executed).isFalse();
        assertThat(countBatchRunsByStatus("RUNNING")).isOne();
    }

    @Test
    void aggregateDailyStatistics_expiredRunExists_failsOldRunAndExecutesNewRun() {
        LocalDate yesterday = findYesterday();
        LocalDateTime backfillStartedAt = insertSuccessfulBackfill(yesterday);
        StatisticsBatchRun expiredRun = StatisticsBatchRun.daily(
                backfillStartedAt,
                batchRunMapper.findCurrentDateTime(),
                yesterday,
                yesterday
        );
        batchRunMapper.insertRunningBatch(expiredRun);
        jdbcTemplate.update(
                """
                UPDATE statistics_batch_runs
                SET heartbeat_at = CURRENT_TIMESTAMP(6) - INTERVAL 2 HOUR
                WHERE id = ?
                """,
                expiredRun.getId()
        );

        boolean executed = service.aggregateDailyStatistics();

        assertThat(executed).isTrue();
        assertThat(findBatchRunStatus(expiredRun.getId())).isEqualTo("FAILED");
        assertThat(findLatestDailyRun().status()).isEqualTo("SUCCEEDED");
    }

    @Test
    void aggregateDailyStatistics_dateReplacementFails_preservesOldResultAndMarksRunFailed() {
        LocalDate yesterday = findYesterday();
        insertSuccessfulBackfill(yesterday);
        insertDailyStatistics(yesterday);
        doThrow(new IllegalStateException("집계 실패 테스트"))
                .when(aggregationMapper)
                .upsertDailyStatistics(any(), any());

        assertThatThrownBy(service::aggregateDailyStatistics)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("집계 실패 테스트");

        assertThat(findDailyStatistics(yesterday)).isEqualTo(
                new DailyStatisticsRow(99, 88, 7, new BigDecimal("123456"))
        );
        assertThat(findLatestDailyRun().status()).isEqualTo("FAILED");
        assertThat(countBatchRunsByStatus("RUNNING")).isZero();
    }

    private LocalDate findYesterday() {
        return batchRunMapper.findCurrentDateTime().toLocalDate().minusDays(1);
    }

    private LocalDateTime insertSuccessfulBackfill(LocalDate yesterday) {
        StatisticsBatchRun backfill = StatisticsBatchRun.backfill(yesterday.minusDays(6), yesterday);
        batchRunMapper.insertRunningBatch(backfill);
        batchRunMapper.updateBackfillProgress(backfill.getId(), yesterday);
        batchRunMapper.completeSucceeded(backfill.getId());
        return jdbcTemplate.queryForObject(
                "SELECT started_at FROM statistics_batch_runs WHERE id = ?",
                LocalDateTime.class,
                backfill.getId()
        );
    }

    private void insertDailyStatistics(LocalDate statisticsDate) {
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
                VALUES (?, 99, 88, 7, 123456, CURRENT_TIMESTAMP(6))
                """,
                statisticsDate
        );
    }

    private DailyStatisticsRow findDailyStatistics(LocalDate statisticsDate) {
        return jdbcTemplate.queryForObject(
                """
                SELECT
                    total_order_count,
                    completed_order_count,
                    canceled_order_count,
                    total_sales_amount
                FROM daily_statistics
                WHERE statistics_date = ?
                """,
                (resultSet, rowNum) -> new DailyStatisticsRow(
                        resultSet.getLong("total_order_count"),
                        resultSet.getLong("completed_order_count"),
                        resultSet.getLong("canceled_order_count"),
                        resultSet.getBigDecimal("total_sales_amount")
                ),
                statisticsDate
        );
    }

    private BatchRunRow findLatestDailyRun() {
        return jdbcTemplate.queryForObject(
                """
                SELECT status, source_window_started_at, completed_at
                FROM statistics_batch_runs
                WHERE batch_type = 'DAILY'
                ORDER BY id DESC
                LIMIT 1
                """,
                (resultSet, rowNum) -> new BatchRunRow(
                        resultSet.getString("status"),
                        resultSet.getObject("source_window_started_at", LocalDateTime.class),
                        resultSet.getObject("completed_at", LocalDateTime.class)
                )
        );
    }

    private String findBatchRunStatus(long batchRunId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM statistics_batch_runs WHERE id = ?",
                String.class,
                batchRunId
        );
    }

    private int countBatchRuns() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM statistics_batch_runs",
                Integer.class
        );
    }

    private int countBatchRunsByStatus(String status) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM statistics_batch_runs WHERE status = ?",
                Integer.class,
                status
        );
    }

    private void deleteAggregationData() {
        jdbcTemplate.update("DELETE FROM daily_statistics");
        jdbcTemplate.update("DELETE FROM statistics_batch_runs");
    }

    private record DailyStatisticsRow(
            long totalOrderCount,
            long completedOrderCount,
            long canceledOrderCount,
            BigDecimal totalSalesAmount
    ) {
    }

    private record BatchRunRow(
            String status,
            LocalDateTime sourceWindowStartedAt,
            LocalDateTime completedAt
    ) {
    }
}
