package com.cakeshop.domain.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.cakeshop.global.config.MariaDbIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

@SpringBootTest
@MariaDbIntegrationTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(
        scripts = "classpath:db/seed/seed-local.sql",
        config = @SqlConfig(encoding = "UTF-8"),
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS
)
class ProductSeedIntegrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void generalProductVariants_haveIndependentPricesAndStocks() {
        List<SeedProductVariant> variants = jdbcTemplate.query(
                """
                SELECT name, base_price, stock_quantity
                FROM products
                WHERE name LIKE '딸기 생크림 케이크%'
                   OR name LIKE '초코 가나슈 케이크%'
                ORDER BY name
                """,
                (resultSet, rowNumber) ->
                        new SeedProductVariant(
                                resultSet.getString("name"),
                                resultSet.getLong("base_price"),
                                resultSet.getInt("stock_quantity")
                        )
        );

        assertThat(variants).containsExactly(
                new SeedProductVariant(
                        "딸기 생크림 케이크 1호",
                        35_000,
                        7
                ),
                new SeedProductVariant(
                        "딸기 생크림 케이크 2호",
                        45_000,
                        5
                ),
                new SeedProductVariant(
                        "딸기 생크림 케이크 3호",
                        55_000,
                        2
                ),
                new SeedProductVariant(
                        "초코 가나슈 케이크 1호 다크초코",
                        42_000,
                        6
                ),
                new SeedProductVariant(
                        "초코 가나슈 케이크 1호 화이트",
                        42_000,
                        4
                ),
                new SeedProductVariant(
                        "초코 가나슈 케이크 2호 다크초코",
                        54_000,
                        3
                ),
                new SeedProductVariant(
                        "초코 가나슈 케이크 2호 화이트",
                        54_000,
                        2
                )
        );
    }

    @Test
    void optionGroups_onlyCustomProductsHaveRequiredGroups() {
        assertThat(count(
                """
                SELECT COUNT(*)
                FROM product_option_groups pog
                JOIN products p ON p.id = pog.product_id
                WHERE p.product_type = 'GENERAL'
                """
        )).isZero();

        assertThat(count(
                """
                SELECT COUNT(*)
                FROM product_option_groups pog
                JOIN products p ON p.id = pog.product_id
                WHERE p.product_type = 'CUSTOM'
                  AND pog.required = 1
                """
        )).isPositive();
    }

    @Test
    void products_withoutReviews_startWithZeroReviewAggregate() {
        assertThat(count("SELECT COUNT(*) FROM reviews"))
                .isZero();
        assertThat(count(
                """
                SELECT COUNT(*)
                FROM products
                WHERE average_rating <> 0.00
                   OR review_count <> 0
                """
        )).isZero();
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private record SeedProductVariant(
            String name,
            long basePrice,
            int stockQuantity
    ) {
    }
}
