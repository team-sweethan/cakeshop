package com.cakeshop.domain.statistics.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.statistics.dto.source.DailyProductStatisticsSourceView;
import com.cakeshop.domain.statistics.dto.source.DailyStatisticsSourceView;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DailyProductStatisticsAggregationMapperTests {

    private static final LocalDate STATISTICS_DATE = LocalDate.of(2026, 8, 1);

    private final DailyStatisticsAggregationMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    DailyProductStatisticsAggregationMapperTests(
            DailyStatisticsAggregationMapper mapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void insertDailyProductStatistics_sources_writesRowsAndCompletesAggregation() {
        insertDailyStatistics();
        List<DailyProductStatisticsSourceView> sources = List.of(
                source(1L, "상품 A", 3, 5, "50000"),
                source(2L, "상품 B", 2, 2, "30000")
        );

        int insertedRows = mapper.insertDailyProductStatistics(STATISTICS_DATE, sources);
        int updatedRows = mapper.updateProductAggregatedAt(STATISTICS_DATE);

        assertThat(insertedRows).isEqualTo(2);
        assertThat(updatedRows).isOne();
        assertThat(findProductStatistics()).containsExactly(
                new ProductStatisticsRow(1L, "상품 A", 3, 5, new BigDecimal("50000")),
                new ProductStatisticsRow(2L, "상품 B", 2, 2, new BigDecimal("30000"))
        );
        assertThat(findProductAggregatedAt()).isNotNull();
    }

    @Test
    void replaceDailyProductStatistics_sourceRemoved_deletesStaleRows() {
        insertDailyStatistics();
        mapper.insertDailyProductStatistics(
                STATISTICS_DATE,
                List.of(
                        source(1L, "상품 A", 3, 5, "50000"),
                        source(2L, "상품 B", 2, 2, "30000")
                )
        );

        int deletedRows = mapper.deleteDailyProductStatistics(STATISTICS_DATE);
        int insertedRows = mapper.insertDailyProductStatistics(
                STATISTICS_DATE,
                List.of(source(1L, "상품 A 변경", 1, 1, "10000"))
        );
        mapper.updateProductAggregatedAt(STATISTICS_DATE);

        assertThat(deletedRows).isEqualTo(2);
        assertThat(insertedRows).isOne();
        assertThat(findProductStatistics()).containsExactly(
                new ProductStatisticsRow(1L, "상품 A 변경", 1, 1, new BigDecimal("10000"))
        );
    }

    @Test
    void updateProductAggregatedAt_noProductSource_completesWithoutProductRows() {
        insertDailyStatistics();

        int deletedRows = mapper.deleteDailyProductStatistics(STATISTICS_DATE);
        int updatedRows = mapper.updateProductAggregatedAt(STATISTICS_DATE);

        assertThat(deletedRows).isZero();
        assertThat(updatedRows).isOne();
        assertThat(findProductStatistics()).isEmpty();
        assertThat(findProductAggregatedAt()).isNotNull();
    }

    @Test
    void updateProductAggregatedAt_dailyStatisticsMissing_updatesNothing() {
        assertThat(mapper.updateProductAggregatedAt(STATISTICS_DATE)).isZero();
    }

    private void insertDailyStatistics() {
        mapper.upsertDailyStatistics(
                STATISTICS_DATE,
                new DailyStatisticsSourceView(0, 0, 0, BigDecimal.ZERO)
        );
    }

    private DailyProductStatisticsSourceView source(
            long productId,
            String productName,
            long orderCount,
            long salesQuantity,
            String salesAmount
    ) {
        return new DailyProductStatisticsSourceView(
                productId,
                productName,
                orderCount,
                salesQuantity,
                new BigDecimal(salesAmount)
        );
    }

    private List<ProductStatisticsRow> findProductStatistics() {
        return jdbcTemplate.query(
                """
                SELECT product_id, product_name, order_count, sales_quantity, sales_amount
                FROM daily_product_statistics
                WHERE statistics_date = ?
                ORDER BY product_id
                """,
                (resultSet, rowNum) -> new ProductStatisticsRow(
                        resultSet.getLong("product_id"),
                        resultSet.getString("product_name"),
                        resultSet.getLong("order_count"),
                        resultSet.getLong("sales_quantity"),
                        resultSet.getBigDecimal("sales_amount")
                ),
                STATISTICS_DATE
        );
    }

    private LocalDateTime findProductAggregatedAt() {
        return jdbcTemplate.queryForObject(
                """
                SELECT product_aggregated_at
                FROM daily_statistics
                WHERE statistics_date = ?
                """,
                LocalDateTime.class,
                STATISTICS_DATE
        );
    }

    private record ProductStatisticsRow(
            long productId,
            String productName,
            long orderCount,
            long salesQuantity,
            BigDecimal salesAmount
    ) {
    }
}
