package com.cakeshop.domain.statistics.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.statistics.dto.view.AdditionalMetricsView;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AdditionalMetricsPeriodStatisticsReadModelMapperTests {

    private static final LocalDate START_DATE = LocalDate.of(2026, 8, 1);
    private static final LocalDate END_DATE = START_DATE.plusDays(2);

    private final PeriodStatisticsReadModelMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AdditionalMetricsPeriodStatisticsReadModelMapperTests(
            PeriodStatisticsReadModelMapper mapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void countIncompleteAdditionalMetricsDates_nullAndMissingDate_countsBoth() {
        insertDailyStatistics(START_DATE, true, 0, 0, 0, 0, "0", "0", 0);
        insertDailyStatistics(START_DATE.plusDays(1), false, 0, 0, 0, 0, "0", "0", 0);

        int incompleteDates = mapper.countIncompleteAdditionalMetricsDates(
                START_DATE,
                END_DATE
        );

        assertThat(incompleteDates).isEqualTo(2);
    }

    @Test
    void findAdditionalMetrics_completedRange_returnsSumsAndRoundedAverageOrderAmount() {
        insertDailyStatistics(START_DATE, true, 2, 1, 3, 4, "5000", "101", 1);
        insertDailyStatistics(START_DATE.plusDays(1), true, 5, 2, 7, 6, "7000", "100", 1);
        insertDailyStatistics(
                END_DATE.plusDays(1),
                true,
                99,
                99,
                99,
                99,
                "99000",
                "99000",
                1
        );

        AdditionalMetricsView result = mapper.findAdditionalMetrics(START_DATE, END_DATE);

        assertThat(result).isEqualTo(new AdditionalMetricsView(
                7,
                3,
                10,
                10,
                new BigDecimal("12000"),
                new BigDecimal("101")
        ));
    }

    @Test
    void findAdditionalMetrics_noValidPaymentOrder_returnsZeroAverageOrderAmount() {
        insertDailyStatistics(START_DATE, true, 0, 0, 0, 0, "0", "0", 0);

        AdditionalMetricsView result = mapper.findAdditionalMetrics(START_DATE, START_DATE);

        assertThat(result.averageOrderAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private void insertDailyStatistics(
            LocalDate date,
            boolean additionalMetricsCompleted,
            long newMemberCount,
            long withdrawnMemberCount,
            long newPostCount,
            long couponUsageCount,
            String refundAmount,
            String totalSalesAmount,
            long validPaymentOrderCount
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO daily_statistics (
                    statistics_date,
                    total_order_count,
                    completed_order_count,
                    canceled_order_count,
                    total_sales_amount,
                    new_member_count,
                    withdrawn_member_count,
                    new_post_count,
                    coupon_usage_count,
                    refund_amount,
                    valid_payment_order_count,
                    aggregated_at,
                    additional_metrics_aggregated_at
                )
                VALUES (?, 0, 0, 0, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6),
                        CASE WHEN ? THEN CURRENT_TIMESTAMP(6) ELSE NULL END)
                """,
                date,
                new BigDecimal(totalSalesAmount),
                newMemberCount,
                withdrawnMemberCount,
                newPostCount,
                couponUsageCount,
                new BigDecimal(refundAmount),
                validPaymentOrderCount,
                additionalMetricsCompleted
        );
    }
}
