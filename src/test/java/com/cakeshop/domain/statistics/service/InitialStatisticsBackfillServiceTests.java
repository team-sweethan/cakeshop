package com.cakeshop.domain.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import com.cakeshop.domain.statistics.entity.StatisticsBatchRun;
import com.cakeshop.domain.statistics.mapper.DailyStatisticsAggregationMapper;
import com.cakeshop.domain.statistics.mapper.StatisticsBatchRunMapper;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
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
        InitialStatisticsBackfillService.class,
        StatisticsAggregationTransactionService.class
})
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InitialStatisticsBackfillServiceTests {

    private final InitialStatisticsBackfillService service;
    private final StatisticsBatchRunMapper batchRunMapper;
    private final JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private DailyStatisticsAggregationMapper aggregationMapper;

    @Autowired
    InitialStatisticsBackfillServiceTests(
            InitialStatisticsBackfillService service,
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
    void backfillInitialStatistics_noSourceData_createsRecentSevenZeroRows() {
        LocalDate yesterday = findYesterday();

        StatisticsBackfillResult result = service.backfillInitialStatistics();

        assertThat(result).isEqualTo(StatisticsBackfillResult.SUCCEEDED);
        assertThat(countDailyStatistics()).isEqualTo(7);
        assertThat(countDailyStatisticsBetween(yesterday.minusDays(6), yesterday)).isEqualTo(7);
        BatchRunRow run = findLatestBackfillRun();
        assertThat(run.status()).isEqualTo("SUCCEEDED");
        assertThat(run.targetStartDate()).isEqualTo(yesterday.minusDays(6));
        assertThat(run.targetEndDate()).isEqualTo(yesterday);
        assertThat(run.lastCompletedDate()).isEqualTo(yesterday);
    }

    @Test
    void backfillInitialStatistics_sourceDataOlderThanSevenDays_startsAtEarliestSourceDate() {
        LocalDate yesterday = findYesterday();
        LocalDate earliestOrderDate = yesterday.minusDays(9);
        doReturn(earliestOrderDate).when(aggregationMapper).findEarliestOrderDate();
        doReturn(yesterday.minusDays(3))
                .when(aggregationMapper)
                .findEarliestApprovedPaymentDate();

        StatisticsBackfillResult result = service.backfillInitialStatistics();

        assertThat(result).isEqualTo(StatisticsBackfillResult.SUCCEEDED);
        BatchRunRow run = findLatestBackfillRun();
        assertThat(run.targetStartDate()).isEqualTo(earliestOrderDate);
        assertThat(run.targetEndDate()).isEqualTo(yesterday);
        assertThat(countDailyStatistics()).isEqualTo(10);
    }

    @Test
    void backfillInitialStatistics_successfulRunExists_skipsSecondRun() {
        LocalDate yesterday = findYesterday();
        insertCompletedBackfill(yesterday, "SUCCEEDED");

        StatisticsBackfillResult result = service.backfillInitialStatistics();

        assertThat(result).isEqualTo(StatisticsBackfillResult.ALREADY_COMPLETED);
        assertThat(countBatchRuns()).isOne();
    }

    @Test
    void backfillInitialStatistics_failedRunWithProgress_resumesFromNextDate() {
        LocalDate yesterday = findYesterday();
        LocalDate lastCompletedDate = yesterday.minusDays(3);
        insertCompletedBackfill(lastCompletedDate, "FAILED");

        StatisticsBackfillResult result = service.backfillInitialStatistics();

        assertThat(result).isEqualTo(StatisticsBackfillResult.SUCCEEDED);
        BatchRunRow run = findLatestBackfillRun();
        assertThat(run.status()).isEqualTo("SUCCEEDED");
        assertThat(run.targetStartDate()).isEqualTo(lastCompletedDate.plusDays(1));
        assertThat(run.targetEndDate()).isEqualTo(yesterday);
        assertThat(run.lastCompletedDate()).isEqualTo(yesterday);
        assertThat(countDailyStatistics()).isEqualTo(3);
    }

    @Test
    void backfillInitialStatistics_runningRunExists_skipsNewRun() {
        LocalDate yesterday = findYesterday();
        StatisticsBatchRun running = StatisticsBatchRun.backfill(
                yesterday.minusDays(6),
                yesterday
        );
        batchRunMapper.insertRunningBatch(running);

        StatisticsBackfillResult result = service.backfillInitialStatistics();

        assertThat(result).isEqualTo(StatisticsBackfillResult.SKIPPED_RUNNING);
        assertThat(countBatchRuns()).isOne();
        assertThat(findLatestBackfillRun().status()).isEqualTo("RUNNING");
    }

    @Test
    void backfillInitialStatistics_dateReplacementFails_preservesDateAndProgress() {
        LocalDate yesterday = findYesterday();
        LocalDate firstDate = yesterday.minusDays(6);
        LocalDate failedDate = firstDate.plusDays(1);
        insertDailyStatistics(failedDate);
        doThrow(new IllegalStateException("백필 실패 테스트"))
                .when(aggregationMapper)
                .replaceDailyStatistics(eq(failedDate), any(), any());

        assertThatThrownBy(service::backfillInitialStatistics)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("백필 실패 테스트");

        BatchRunRow run = findLatestBackfillRun();
        assertThat(run.status()).isEqualTo("FAILED");
        assertThat(run.lastCompletedDate()).isEqualTo(firstDate);
        assertThat(findDailyStatistics(failedDate)).isEqualTo(
                new DailyStatisticsRow(99, 88, 7, new BigDecimal("123456"))
        );
    }

    private LocalDate findYesterday() {
        return batchRunMapper.findCurrentDateTime().toLocalDate().minusDays(1);
    }

    private void insertCompletedBackfill(LocalDate lastCompletedDate, String status) {
        LocalDate yesterday = findYesterday();
        StatisticsBatchRun run = StatisticsBatchRun.backfill(yesterday.minusDays(6), yesterday);
        batchRunMapper.insertRunningBatch(run);
        batchRunMapper.updateBackfillProgress(run.getId(), lastCompletedDate);
        if ("SUCCEEDED".equals(status)) {
            batchRunMapper.completeSucceeded(run.getId());
        } else {
            batchRunMapper.completeFailed(run.getId());
        }
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

    private BatchRunRow findLatestBackfillRun() {
        return jdbcTemplate.queryForObject(
                """
                SELECT
                    status,
                    target_start_date,
                    target_end_date,
                    last_completed_date
                FROM statistics_batch_runs
                WHERE batch_type = 'BACKFILL'
                ORDER BY id DESC
                LIMIT 1
                """,
                (resultSet, rowNum) -> new BatchRunRow(
                        resultSet.getString("status"),
                        resultSet.getObject("target_start_date", LocalDate.class),
                        resultSet.getObject("target_end_date", LocalDate.class),
                        resultSet.getObject("last_completed_date", LocalDate.class)
                )
        );
    }

    private int countDailyStatistics() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM daily_statistics",
                Integer.class
        );
    }

    private int countDailyStatisticsBetween(LocalDate startDate, LocalDate endDate) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM daily_statistics
                WHERE statistics_date BETWEEN ? AND ?
                """,
                Integer.class,
                startDate,
                endDate
        );
    }

    private int countBatchRuns() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM statistics_batch_runs",
                Integer.class
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
            LocalDate targetStartDate,
            LocalDate targetEndDate,
            LocalDate lastCompletedDate
    ) {
    }
}
