package com.cakeshop.global.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.sql.PreparedStatement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@MariaDbIntegrationTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RdsDemoSeedIntegrationTests {

    private static final String RDS_DEMO_SEED = "db/seed/seed-rds-demo.sql";
    private static final String TEST_ADMIN_HASH = "$2a$10$" + "x".repeat(53);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seed_usesInjectedAdminHashAndExcludesSameNamedProductInAnotherCategory() {
        runSeed();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT password FROM members WHERE email = 'admin@cakeshop.local'",
                String.class
        )).isEqualTo(TEST_ADMIN_HASH);

        insertSameNamedProductInAnotherCategory();
        runSeed();

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM product_option_groups pog
                INNER JOIN products p ON p.id = pog.product_id
                INNER JOIN categories c ON c.id = p.category_id
                WHERE c.code = 'OTHER'
                """,
                Long.class
        )).isZero();
    }

    private void runSeed() {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SET @rds_demo_admin_password_hash = ?")) {
                statement.setString(1, TEST_ADMIN_HASH);
                statement.executeUpdate();
            }
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(RDS_DEMO_SEED));
            return null;
        });
    }

    private void insertSameNamedProductInAnotherCategory() {
        jdbcTemplate.update(
                """
                INSERT INTO categories (code, name, sort_order, is_active)
                VALUES ('OTHER', '다른 카테고리', 99, 1)
                """);
        jdbcTemplate.update(
                """
                INSERT INTO products (
                    category_id, name, description, base_price, stock_quantity,
                    product_type, preparation_days, status
                )
                SELECT
                    other_category.id, p.name, p.description, p.base_price,
                    p.stock_quantity, p.product_type, p.preparation_days, p.status
                FROM products p
                INNER JOIN categories cake_category
                        ON cake_category.id = p.category_id
                       AND cake_category.code = 'CAKE'
                INNER JOIN categories other_category
                        ON other_category.code = 'OTHER'
                WHERE p.name = '레터링 생크림 케이크'
                """);
    }
}
