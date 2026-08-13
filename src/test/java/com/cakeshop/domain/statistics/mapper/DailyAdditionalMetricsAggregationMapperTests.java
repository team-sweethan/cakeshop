package com.cakeshop.domain.statistics.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.statistics.dto.view.DailyAdditionalMetricsSourceView;
import com.cakeshop.domain.statistics.dto.view.DailyStatisticsSourceView;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DailyAdditionalMetricsAggregationMapperTests {

    private static final LocalDate STATISTICS_DATE = LocalDate.of(2026, 8, 1);

    private final DailyStatisticsAggregationMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    DailyAdditionalMetricsAggregationMapperTests(
            DailyStatisticsAggregationMapper mapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void updateDailyAdditionalMetrics_source_writesMetricsAndCompletesAggregation() {
        insertDailyStatistics();

        int updatedRows = mapper.updateDailyAdditionalMetrics(
                STATISTICS_DATE,
                source(3, 1, 5, 2, "12000", 4)
        );

        assertThat(updatedRows).isOne();
        assertThat(findAdditionalMetrics()).isEqualTo(
                new AdditionalMetricsRow(3, 1, 5, 2, new BigDecimal("12000"), 4)
        );
        assertThat(findAdditionalMetricsAggregatedAt()).isNotNull();
    }

    @Test
    void updateDailyAdditionalMetrics_repeated_replacesExistingValues() {
        insertDailyStatistics();
        mapper.updateDailyAdditionalMetrics(
                STATISTICS_DATE,
                source(3, 1, 5, 2, "12000", 4)
        );

        int updatedRows = mapper.updateDailyAdditionalMetrics(
                STATISTICS_DATE,
                source(1, 0, 2, 0, "3000", 1)
        );

        assertThat(updatedRows).isOne();
        assertThat(findAdditionalMetrics()).isEqualTo(
                new AdditionalMetricsRow(1, 0, 2, 0, new BigDecimal("3000"), 1)
        );
    }

    @Test
    void updateDailyAdditionalMetrics_dailyStatisticsMissing_updatesNothing() {
        int updatedRows = mapper.updateDailyAdditionalMetrics(
                STATISTICS_DATE,
                source(1, 0, 2, 0, "3000", 1)
        );

        assertThat(updatedRows).isZero();
    }

    private void insertDailyStatistics() {
        mapper.upsertDailyStatistics(
                STATISTICS_DATE,
                new DailyStatisticsSourceView(0, 0, 0, BigDecimal.ZERO)
        );
    }

    private DailyAdditionalMetricsSourceView source(
            long newMemberCount,
            long withdrawnMemberCount,
            long newPostCount,
            long couponUsageCount,
            String refundAmount,
            long validPaymentOrderCount
    ) {
        return new DailyAdditionalMetricsSourceView(
                newMemberCount,
                withdrawnMemberCount,
                newPostCount,
                couponUsageCount,
                new BigDecimal(refundAmount),
                validPaymentOrderCount
        );
    }

    private AdditionalMetricsRow findAdditionalMetrics() {
        return jdbcTemplate.queryForObject(
                """
                SELECT
                    new_member_count,
                    withdrawn_member_count,
                    new_post_count,
                    coupon_usage_count,
                    refund_amount,
                    valid_payment_order_count
                FROM daily_statistics
                WHERE statistics_date = ?
                """,
                (resultSet, rowNum) -> new AdditionalMetricsRow(
                        resultSet.getLong("new_member_count"),
                        resultSet.getLong("withdrawn_member_count"),
                        resultSet.getLong("new_post_count"),
                        resultSet.getLong("coupon_usage_count"),
                        resultSet.getBigDecimal("refund_amount"),
                        resultSet.getLong("valid_payment_order_count")
                ),
                STATISTICS_DATE
        );
    }

    private LocalDateTime findAdditionalMetricsAggregatedAt() {
        return jdbcTemplate.queryForObject(
                """
                SELECT additional_metrics_aggregated_at
                FROM daily_statistics
                WHERE statistics_date = ?
                """,
                LocalDateTime.class,
                STATISTICS_DATE
        );
    }

    private record AdditionalMetricsRow(
            long newMemberCount,
            long withdrawnMemberCount,
            long newPostCount,
            long couponUsageCount,
            BigDecimal refundAmount,
            long validPaymentOrderCount
    ) {
    }
}
