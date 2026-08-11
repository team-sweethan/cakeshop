package com.cakeshop.domain.statistics.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.statistics.dto.view.DailyStatisticsRow;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PeriodStatisticsReadModelMapperTests {

    private static final LocalDate START_DATE = LocalDate.of(2026, 8, 1);
    private static final LocalDate END_DATE = LocalDate.of(2026, 8, 5);

    private final PeriodStatisticsReadModelMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    PeriodStatisticsReadModelMapperTests(
            PeriodStatisticsReadModelMapper mapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void findDailyStatistics_completedRows_returnsAggregatesInDateOrder() {
        insertDailyStatistics(START_DATE.plusDays(1), 3, 2, 1, "30000");
        insertDailyStatistics(START_DATE, 5, 4, 0, "50000");
        insertDailyStatistics(END_DATE.plusDays(1), 99, 99, 0, "990000");

        List<DailyStatisticsRow> rows = mapper.findDailyStatistics(START_DATE, END_DATE);

        assertThat(rows).containsExactly(
                new DailyStatisticsRow(START_DATE, 5, 4, 0, new BigDecimal("50000")),
                new DailyStatisticsRow(START_DATE.plusDays(1), 3, 2, 1, new BigDecimal("30000"))
        );
    }

    @Test
    void findLatestContinuousStatisticsDate_noGap_returnsLatestAllowedDate() {
        insertBackfillRun(START_DATE, END_DATE);
        for (LocalDate date = START_DATE; !date.isAfter(END_DATE); date = date.plusDays(1)) {
            insertDailyStatistics(date, 0, 0, 0, "0");
        }

        LocalDate latestDate = mapper.findLatestContinuousStatisticsDate(END_DATE.minusDays(1));

        assertThat(latestDate).isEqualTo(END_DATE.minusDays(1));
    }

    @Test
    void findLatestContinuousStatisticsDate_gapExists_returnsDateBeforeFirstGap() {
        insertBackfillRun(START_DATE, END_DATE);
        insertDailyStatistics(START_DATE, 0, 0, 0, "0");
        insertDailyStatistics(START_DATE.plusDays(1), 0, 0, 0, "0");
        insertDailyStatistics(START_DATE.plusDays(3), 0, 0, 0, "0");
        insertDailyStatistics(START_DATE.plusDays(4), 0, 0, 0, "0");

        LocalDate latestDate = mapper.findLatestContinuousStatisticsDate(END_DATE);

        assertThat(latestDate).isEqualTo(START_DATE.plusDays(1));
    }

    private void insertBackfillRun(LocalDate targetStartDate, LocalDate targetEndDate) {
        jdbcTemplate.update(
                """
                INSERT INTO statistics_batch_runs (
                    batch_type,
                    status,
                    target_start_date,
                    target_end_date,
                    last_completed_date,
                    started_at,
                    heartbeat_at,
                    completed_at
                )
                VALUES (
                    'BACKFILL',
                    'SUCCEEDED',
                    ?,
                    ?,
                    ?,
                    CURRENT_TIMESTAMP(6),
                    CURRENT_TIMESTAMP(6),
                    CURRENT_TIMESTAMP(6)
                )
                """,
                targetStartDate,
                targetEndDate,
                targetEndDate
        );
    }

    private void insertDailyStatistics(
            LocalDate date,
            long totalOrderCount,
            long completedOrderCount,
            long canceledOrderCount,
            String totalSalesAmount
    ) {
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
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))
                """,
                date,
                totalOrderCount,
                completedOrderCount,
                canceledOrderCount,
                new BigDecimal(totalSalesAmount)
        );
    }
}
