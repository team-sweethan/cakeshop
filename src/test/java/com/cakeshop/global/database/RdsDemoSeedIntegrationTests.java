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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@MariaDbIntegrationTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RdsDemoSeedIntegrationTests {

    private static final String RDS_DEMO_SEED = "db/seed/seed-rds-demo.sql";
    private static final String TEST_ADMIN_HASH =
            new BCryptPasswordEncoder().encode("rds-demo-test-only-password");

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

        assertThat(count(
                """
                SELECT COUNT(*)
                FROM product_option_groups pog
                INNER JOIN products p ON p.id = pog.product_id
                INNER JOIN categories c ON c.id = p.category_id
                WHERE c.code = 'OTHER'
                """
        )).isOne();
        assertThat(count(
                """
                SELECT COUNT(*)
                FROM product_options po
                INNER JOIN product_option_groups pog ON pog.id = po.option_group_id
                INNER JOIN products p ON p.id = pog.product_id
                INNER JOIN categories c ON c.id = p.category_id
                WHERE c.code = 'OTHER'
                """
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
        jdbcTemplate.update(
                """
                INSERT INTO product_option_groups (
                    product_id, name, required, selection_type, sort_order
                )
                SELECT p.id, '케이크 크기', 1, 'SINGLE', 1
                FROM products p
                INNER JOIN categories c ON c.id = p.category_id
                WHERE c.code = 'OTHER'
                  AND p.name = '레터링 생크림 케이크'
                """);
    }

    private long count(String sql) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class);
        return result == null ? 0 : result;
    }
}
