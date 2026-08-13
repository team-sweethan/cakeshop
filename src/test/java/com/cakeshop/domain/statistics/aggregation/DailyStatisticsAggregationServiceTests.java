package com.cakeshop.domain.statistics.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.cakeshop.domain.statistics.dto.view.DailyProductStatisticsSourceView;
import com.cakeshop.domain.statistics.entity.StatisticsBatchRun;
import com.cakeshop.domain.statistics.mapper.DailyStatisticsAggregationMapper;
import com.cakeshop.domain.statistics.mapper.DailyStatisticsSourceReadModelMapper;
import com.cakeshop.domain.statistics.mapper.StatisticsBatchRunMapper;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
        DailyProductStatisticsSourceReadModelQueryService.class,
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

    @MockitoSpyBean
    private DailyProductStatisticsSourceReadModelQueryService productSourceQueryService;

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
        assertThat(findProductAggregatedAt(yesterday)).isNotNull();
        assertThat(findAdditionalMetricsAggregatedAt(yesterday)).isNotNull();
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
    void catchUpMissingDailyStatistics_anotherInstanceCompletedAfterPolicy_skipsNewDailyRun() {
        LocalDate yesterday = findYesterday();
        insertSuccessfulBackfill(yesterday.minusDays(1));
        assertThat(service.aggregateDailyStatistics()).isTrue();
        int batchRunCountBeforeCatchup = countBatchRuns();

        boolean executed = service.catchUpMissingDailyStatistics();

        assertThat(executed).isFalse();
        assertThat(countBatchRuns()).isEqualTo(batchRunCountBeforeCatchup);
    }

    @Test
    void catchUpMissingDailyStatistics_latestDateBeforeYesterday_executesDailyRun() {
        LocalDate yesterday = findYesterday();
        insertSuccessfulBackfill(yesterday.minusDays(2));

        boolean executed = service.catchUpMissingDailyStatistics();

        assertThat(executed).isTrue();
        assertThat(findLatestDailyRun().status()).isEqualTo("SUCCEEDED");
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

    @Test
    void aggregateDailyStatistics_productInsertFails_preservesBothExistingResults() {
        LocalDate yesterday = findYesterday();
        insertSuccessfulBackfill(yesterday);
        insertDailyStatistics(yesterday);
        LocalDateTime previousProductAggregatedAt = LocalDateTime.of(2026, 8, 1, 0, 10);
        jdbcTemplate.update(
                "UPDATE daily_statistics SET product_aggregated_at = ? WHERE statistics_date = ?",
                previousProductAggregatedAt,
                yesterday
        );
        insertDailyProductStatistics(yesterday, 1L, "기존 상품", 99L);
        doReturn(List.of(new DailyProductStatisticsSourceView(
                2L,
                "새 상품",
                1,
                1,
                new BigDecimal("10000")
        ))).when(productSourceQueryService).getDailyProductStatistics(any(), any());
        doThrow(new IllegalStateException("상품 집계 실패 테스트"))
                .when(aggregationMapper)
                .insertDailyProductStatistics(any(), any());

        assertThatThrownBy(service::aggregateDailyStatistics)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("상품 집계 실패 테스트");

        assertThat(findDailyStatistics(yesterday)).isEqualTo(
                new DailyStatisticsRow(99, 88, 7, new BigDecimal("123456"))
        );
        assertThat(findProductStatistics(yesterday)).containsExactly(
                new ProductStatisticsRow(1L, "기존 상품", 99L)
        );
        assertThat(findProductAggregatedAt(yesterday)).isEqualTo(previousProductAggregatedAt);
        assertThat(findLatestDailyRun().status()).isEqualTo("FAILED");
    }

    @Test
    void aggregateDailyStatistics_additionalMetricsUpdateFails_preservesExistingResult() {
        LocalDate yesterday = findYesterday();
        insertSuccessfulBackfill(yesterday);
        insertDailyStatistics(yesterday);
        LocalDateTime previousAggregatedAt = LocalDateTime.of(2026, 8, 1, 0, 10);
        jdbcTemplate.update(
                """
                UPDATE daily_statistics
                SET new_member_count = 9,
                    refund_amount = 45000,
                    additional_metrics_aggregated_at = ?
                WHERE statistics_date = ?
                """,
                previousAggregatedAt,
                yesterday
        );
        doThrow(new IllegalStateException("기타 지표 집계 실패 테스트"))
                .when(aggregationMapper)
                .updateDailyAdditionalMetrics(eq(yesterday), any());

        assertThatThrownBy(service::aggregateDailyStatistics)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("기타 지표 집계 실패 테스트");

        assertThat(findDailyStatistics(yesterday)).isEqualTo(
                new DailyStatisticsRow(99, 88, 7, new BigDecimal("123456"))
        );
        assertThat(findAdditionalMetrics(yesterday)).isEqualTo(
                new AdditionalMetricsRow(9, new BigDecimal("45000"), previousAggregatedAt)
        );
        assertThat(findLatestDailyRun().status()).isEqualTo("FAILED");
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

    private LocalDateTime findAdditionalMetricsAggregatedAt(LocalDate statisticsDate) {
        return jdbcTemplate.queryForObject(
                """
                SELECT additional_metrics_aggregated_at
                FROM daily_statistics
                WHERE statistics_date = ?
                """,
                LocalDateTime.class,
                statisticsDate
        );
    }

    private AdditionalMetricsRow findAdditionalMetrics(LocalDate statisticsDate) {
        return jdbcTemplate.queryForObject(
                """
                SELECT
                    new_member_count,
                    refund_amount,
                    additional_metrics_aggregated_at
                FROM daily_statistics
                WHERE statistics_date = ?
                """,
                (resultSet, rowNum) -> new AdditionalMetricsRow(
                        resultSet.getLong("new_member_count"),
                        resultSet.getBigDecimal("refund_amount"),
                        resultSet.getObject(
                                "additional_metrics_aggregated_at",
                                LocalDateTime.class
                        )
                ),
                statisticsDate
        );
    }

    private void insertDailyProductStatistics(
            LocalDate statisticsDate,
            long productId,
            String productName,
            long orderCount
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO daily_product_statistics (
                    statistics_date,
                    product_id,
                    product_name,
                    order_count,
                    sales_quantity,
                    sales_amount
                )
                VALUES (?, ?, ?, ?, 0, 0)
                """,
                statisticsDate,
                productId,
                productName,
                orderCount
        );
    }

    private List<ProductStatisticsRow> findProductStatistics(LocalDate statisticsDate) {
        return jdbcTemplate.query(
                """
                SELECT product_id, product_name, order_count
                FROM daily_product_statistics
                WHERE statistics_date = ?
                ORDER BY product_id
                """,
                (resultSet, rowNum) -> new ProductStatisticsRow(
                        resultSet.getLong("product_id"),
                        resultSet.getString("product_name"),
                        resultSet.getLong("order_count")
                ),
                statisticsDate
        );
    }

    private LocalDateTime findProductAggregatedAt(LocalDate statisticsDate) {
        return jdbcTemplate.queryForObject(
                "SELECT product_aggregated_at FROM daily_statistics WHERE statistics_date = ?",
                LocalDateTime.class,
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
        jdbcTemplate.update("DELETE FROM daily_product_statistics");
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

    private record ProductStatisticsRow(long productId, String productName, long orderCount) {
    }

    private record AdditionalMetricsRow(
            long newMemberCount,
            BigDecimal refundAmount,
            LocalDateTime aggregatedAt
    ) {
    }

    private record BatchRunRow(
            String status,
            LocalDateTime sourceWindowStartedAt,
            LocalDateTime completedAt
    ) {
    }
}
