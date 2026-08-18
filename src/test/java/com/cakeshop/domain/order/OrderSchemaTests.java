package com.cakeshop.domain.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderSchemaTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void orderSchema_unusedColumnsAndRedundantIndex_areRemoved() {
        List<String> orderColumns = findColumns("orders");
        List<String> orderItemColumns = findColumns("order_items");
        List<String> orderCartItemIndexes = findIndexes("order_cart_items");

        assertThat(orderColumns)
                .doesNotContain("accepted_at", "completed_at", "cancellation_blocked_at")
                .contains("pickup_reminder_sent_at");
        assertThat(orderItemColumns).doesNotContain("cancellation_limit_days");
        assertThat(orderCartItemIndexes).doesNotContain("idx_order_cart_items_order_id");
    }

    private List<String> findColumns(String tableName) {
        return jdbcTemplate.queryForList(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """,
                String.class,
                tableName
        );
    }

    private List<String> findIndexes(String tableName) {
        return jdbcTemplate.queryForList(
                """
                SELECT DISTINCT index_name
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """,
                String.class,
                tableName
        );
    }
}
