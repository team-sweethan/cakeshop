package com.cakeshop.domain.statistics.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.statistics.dto.view.ProductStatisticsView;
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
class ProductPeriodStatisticsReadModelMapperTests {

    private static final LocalDate START_DATE = LocalDate.of(2026, 8, 1);
    private static final LocalDate END_DATE = START_DATE.plusDays(2);

    private final PeriodStatisticsReadModelMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ProductPeriodStatisticsReadModelMapperTests(
            PeriodStatisticsReadModelMapper mapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void countIncompleteProductStatisticsDates_nullAndMissingDate_countsBoth() {
        insertDailyStatistics(START_DATE, true);
        insertDailyStatistics(START_DATE.plusDays(1), false);

        int incompleteDates = mapper.countIncompleteProductStatisticsDates(
                START_DATE,
                END_DATE
        );

        assertThat(incompleteDates).isEqualTo(2);
    }

    @Test
    void findProductStatistics_completedRange_aggregatesAndRanksAllProducts() {
        insertDailyStatistics(START_DATE, true);
        insertDailyStatistics(START_DATE.plusDays(1), true);
        insertProductStatistics(START_DATE, 30L, "매출 상품 이전명", 1, 1, "120");
        insertProductStatistics(START_DATE.plusDays(1), 30L, "매출 상품 최신명", 1, 1, "80");
        insertProductStatistics(START_DATE, 40L, "수량 상품", 1, 6, "100");
        insertProductStatistics(START_DATE, 5L, "동률 상품 A", 3, 5, "100");
        insertProductStatistics(START_DATE, 20L, "동률 상품 B", 3, 5, "100");
        insertProductStatistics(START_DATE, 10L, "주문 상품", 2, 5, "100");
        insertProductStatistics(END_DATE.plusDays(1), 99L, "기간 외 상품", 99, 99, "999");

        List<ProductStatisticsView> result = mapper.findProductStatistics(
                START_DATE,
                START_DATE.plusDays(1)
        );

        assertThat(result).containsExactly(
                view(1, 30, "매출 상품 최신명", 2, 2, "200"),
                view(2, 40, "수량 상품", 1, 6, "100"),
                view(3, 5, "동률 상품 A", 3, 5, "100"),
                view(4, 20, "동률 상품 B", 3, 5, "100"),
                view(5, 10, "주문 상품", 2, 5, "100")
        );
    }

    @Test
    void findProductStatistics_noProductActivity_returnsEmptyList() {
        insertDailyStatistics(START_DATE, true);

        assertThat(mapper.findProductStatistics(START_DATE, START_DATE)).isEmpty();
    }

    private ProductStatisticsView view(
            long ranking,
            long productId,
            String productName,
            long orderCount,
            long salesQuantity,
            String salesAmount
    ) {
        return new ProductStatisticsView(
                ranking,
                productId,
                productName,
                orderCount,
                salesQuantity,
                new BigDecimal(salesAmount)
        );
    }

    private void insertDailyStatistics(LocalDate date, boolean productAggregationCompleted) {
        jdbcTemplate.update(
                """
                INSERT INTO daily_statistics (
                    statistics_date,
                    total_order_count,
                    completed_order_count,
                    canceled_order_count,
                    total_sales_amount,
                    aggregated_at,
                    product_aggregated_at
                )
                VALUES (?, 0, 0, 0, 0, CURRENT_TIMESTAMP(6),
                        CASE WHEN ? THEN CURRENT_TIMESTAMP(6) ELSE NULL END)
                """,
                date,
                productAggregationCompleted
        );
    }

    private void insertProductStatistics(
            LocalDate date,
            long productId,
            String productName,
            long orderCount,
            long salesQuantity,
            String salesAmount
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
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                date,
                productId,
                productName,
                orderCount,
                salesQuantity,
                new BigDecimal(salesAmount)
        );
    }
}
