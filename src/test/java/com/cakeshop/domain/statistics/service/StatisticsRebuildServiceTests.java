package com.cakeshop.domain.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.cakeshop.domain.statistics.dto.StatisticsRebuildRequest;
import com.cakeshop.domain.statistics.entity.StatisticsBatchRun;
import com.cakeshop.domain.statistics.mapper.DailyStatisticsAggregationMapper;
import com.cakeshop.domain.statistics.mapper.StatisticsBatchRunMapper;
import com.cakeshop.global.config.MariaDbIntegrationTest;
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
        StatisticsRebuildService.class,
        DailyProductStatisticsSourceReadModelQueryService.class,
        StatisticsAggregationTransactionService.class
})
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StatisticsRebuildServiceTests {

    private final StatisticsRebuildService service;
    private final StatisticsBatchRunMapper batchRunMapper;
    private final JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private DailyStatisticsAggregationMapper aggregationMapper;

    @Autowired
    StatisticsRebuildServiceTests(
            StatisticsRebuildService service,
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
    void rebuild_noEndDate_usesYesterdayAndReplacesRequestedRange() {
        LocalDate yesterday = findYesterday();
        insertSuccessfulBackfill(yesterday);

        StatisticsRebuildResult result = service.rebuild(
                new StatisticsRebuildRequest(yesterday.minusDays(2), null)
        );

        assertThat(result).isEqualTo(StatisticsRebuildResult.SUCCEEDED);
        assertThat(countDailyStatisticsBetween(yesterday.minusDays(2), yesterday)).isEqualTo(3);
        assertThat(countCompletedProductStatisticsBetween(yesterday.minusDays(2), yesterday))
                .isEqualTo(3);
        BatchRunRow run = findLatestRebuildRun();
        assertThat(run.status()).isEqualTo("SUCCEEDED");
        assertThat(run.targetStartDate()).isEqualTo(yesterday.minusDays(2));
        assertThat(run.targetEndDate()).isEqualTo(yesterday);
        assertThat(run.lastCompletedDate()).isEqualTo(yesterday);
    }

    @Test
    void rebuild_sameRangeRepeated_keepsOneRowPerDate() {
        LocalDate yesterday = findYesterday();
        insertSuccessfulBackfill(yesterday);
        StatisticsRebuildRequest request = new StatisticsRebuildRequest(yesterday, yesterday);

        assertThat(service.rebuild(request)).isEqualTo(StatisticsRebuildResult.SUCCEEDED);
        assertThat(service.rebuild(request)).isEqualTo(StatisticsRebuildResult.SUCCEEDED);

        assertThat(countDailyStatisticsBetween(yesterday, yesterday)).isOne();
        assertThat(countRebuildRuns()).isEqualTo(2);
    }

    @Test
    void rebuild_noSuccessfulBackfill_returnsRequiredResult() {
        LocalDate yesterday = findYesterday();

        StatisticsRebuildResult result = service.rebuild(
                new StatisticsRebuildRequest(yesterday, yesterday)
        );

        assertThat(result).isEqualTo(StatisticsRebuildResult.INITIAL_BACKFILL_REQUIRED);
        assertThat(countRebuildRuns()).isZero();
    }

    @Test
    void rebuild_anotherAggregationRunning_returnsSkippedResult() {
        LocalDate yesterday = findYesterday();
        insertSuccessfulBackfill(yesterday);
        StatisticsBatchRun running = StatisticsBatchRun.backfill(yesterday, yesterday);
        batchRunMapper.insertRunningBatch(running);

        StatisticsRebuildResult result = service.rebuild(
                new StatisticsRebuildRequest(yesterday, yesterday)
        );

        assertThat(result).isEqualTo(StatisticsRebuildResult.SKIPPED_RUNNING);
        assertThat(countRebuildRuns()).isZero();
    }

    @Test
    void rebuild_dateReplacementFails_preservesExistingDateAndRecordsProgress() {
        LocalDate yesterday = findYesterday();
        LocalDate firstDate = yesterday.minusDays(1);
        insertSuccessfulBackfill(yesterday);
        insertDailyStatistics(yesterday);
        doThrow(new IllegalStateException("재집계 실패 테스트"))
                .when(aggregationMapper)
                .upsertDailyStatistics(eq(yesterday), any());

        assertThatThrownBy(() -> service.rebuild(
                new StatisticsRebuildRequest(firstDate, yesterday)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("재집계 실패 테스트");

        BatchRunRow run = findLatestRebuildRun();
        assertThat(run.status()).isEqualTo("FAILED");
        assertThat(run.lastCompletedDate()).isEqualTo(firstDate);
        assertThat(findTotalOrderCount(yesterday)).isEqualTo(99);
    }

    @Test
    void rebuild_startDateAfterEndDate_rejectsRange() {
        LocalDate yesterday = findYesterday();

        assertThatThrownBy(() -> service.rebuild(
                new StatisticsRebuildRequest(yesterday, yesterday.minusDays(1))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("시작일");
    }

    @Test
    void rebuild_endDateAfterYesterday_rejectsRange() {
        LocalDate yesterday = findYesterday();

        assertThatThrownBy(() -> service.rebuild(
                new StatisticsRebuildRequest(yesterday, yesterday.plusDays(1))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("어제");
    }

    @Test
    void rebuild_moreThan366Days_rejectsRange() {
        LocalDate yesterday = findYesterday();

        assertThatThrownBy(() -> service.rebuild(
                new StatisticsRebuildRequest(yesterday.minusDays(366), yesterday)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("366일");
    }

    private LocalDate findYesterday() {
        return batchRunMapper.findCurrentDateTime().toLocalDate().minusDays(1);
    }

    private void insertSuccessfulBackfill(LocalDate targetEndDate) {
        StatisticsBatchRun run = StatisticsBatchRun.backfill(
                targetEndDate.minusDays(6),
                targetEndDate
        );
        batchRunMapper.insertRunningBatch(run);
        batchRunMapper.updateBackfillProgress(run.getId(), targetEndDate);
        batchRunMapper.completeSucceeded(run.getId());
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

    private long findTotalOrderCount(LocalDate statisticsDate) {
        return jdbcTemplate.queryForObject(
                "SELECT total_order_count FROM daily_statistics WHERE statistics_date = ?",
                Long.class,
                statisticsDate
        );
    }

    private BatchRunRow findLatestRebuildRun() {
        return jdbcTemplate.queryForObject(
                """
                SELECT status, target_start_date, target_end_date, last_completed_date
                FROM statistics_batch_runs
                WHERE batch_type = 'REBUILD'
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

    private int countDailyStatisticsBetween(LocalDate startDate, LocalDate endDate) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM daily_statistics WHERE statistics_date BETWEEN ? AND ?",
                Integer.class,
                startDate,
                endDate
        );
    }

    private int countCompletedProductStatisticsBetween(LocalDate startDate, LocalDate endDate) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM daily_statistics
                WHERE statistics_date BETWEEN ? AND ?
                  AND product_aggregated_at IS NOT NULL
                """,
                Integer.class,
                startDate,
                endDate
        );
    }

    private int countRebuildRuns() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM statistics_batch_runs WHERE batch_type = 'REBUILD'",
                Integer.class
        );
    }

    private void deleteAggregationData() {
        jdbcTemplate.update("DELETE FROM daily_product_statistics");
        jdbcTemplate.update("DELETE FROM daily_statistics");
        jdbcTemplate.update("DELETE FROM statistics_batch_runs");
    }

    private record BatchRunRow(
            String status,
            LocalDate targetStartDate,
            LocalDate targetEndDate,
            LocalDate lastCompletedDate
    ) {
    }
}
