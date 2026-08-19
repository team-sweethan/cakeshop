package com.cakeshop.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.utility.DockerImageName;

// 조각 0 migration 의 두 변경을 다시 다른 ALTER 로 쪼개면 이 테스트가 깨진다. DDL 은 문장 단위로
// 암시적 커밋되어, 절반만 적용된 스키마가 남으면 데이터를 고쳐도 재실행이 제약 중복으로 막힌다.
@Tag("mariadb")
class ReviewMigrationRetryTests {

    private static final DockerImageName MARIA_DB_IMAGE = DockerImageName.parse("mariadb:11.4.10");

    /** 조각 0 migration 바로 앞 버전. 여기까지만 적용해 제약이 없는 상태를 만든다. */
    private static final String VERSION_BEFORE_REVIEW_CONSTRAINTS = "20260805.073107";

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 10, 0);

    private static MariaDBContainer mariaDb;

    private static SingleConnectionDataSource dataSource;

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void startContainer() {
        mariaDb = new MariaDBContainer(MARIA_DB_IMAGE);
        mariaDb.start();

        // 커넥션을 하나로 고정한다. LAST_INSERT_ID() 는 커넥션 단위라 문장마다 새로 열면 0 이 나온다.
        dataSource = new SingleConnectionDataSource(
                mariaDb.getJdbcUrl(), mariaDb.getUsername(), mariaDb.getPassword(), true);
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @AfterAll
    static void stopContainer() {
        if (dataSource != null) {
            dataSource.destroy();
        }
        if (mariaDb != null) {
            mariaDb.stop();
        }
    }

    @Test
    void migrate_outOfRangeRatingExists_leavesNoPartialSchemaChange() {
        migrateTo(VERSION_BEFORE_REVIEW_CONSTRAINTS);
        insertReviewWithRating(0);

        assertThatThrownBy(() -> migrateAll())
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("chk_reviews_overall_rating");

        // 앞쪽 절이 먼저 적용돼 남아 있으면 재실행이 제약 중복으로 막힌다.
        assertThat(checkConstraintExists("chk_reviews_status")).isFalse();
        assertThat(statusColumnDefault()).contains("VISIBLE");

        // 데이터를 고치고 실패 기록을 지우면 그대로 다시 올라가야 한다.
        jdbcTemplate.update("UPDATE reviews SET overall_rating = 5 WHERE overall_rating = 0");
        repair();

        assertThatCode(() -> migrateAll()).doesNotThrowAnyException();

        assertThat(checkConstraintExists("chk_reviews_status")).isTrue();
        assertThat(checkConstraintExists("chk_reviews_overall_rating")).isTrue();
        assertThat(checkConstraintExists("chk_reviews_taste_rating")).isTrue();
        assertThat(checkConstraintExists("chk_reviews_design_rating")).isTrue();
        assertThat(checkConstraintExists("chk_reviews_service_rating")).isTrue();
        assertThat(statusColumnDefault()).contains("PUBLISHED");
    }

    private static void migrateTo(String version) {
        flyway(version).migrate();
    }

    private static void migrateAll() {
        flyway(null).migrate();
    }

    private static void repair() {
        flyway(null).repair();
    }

    /** application.yml 의 루트 flyway 설정과 같은 조건으로 맞춘다. */
    private static Flyway flyway(String target) {
        var configuration = Flyway.configure()
                .dataSource(mariaDb.getJdbcUrl(), mariaDb.getUsername(), mariaDb.getPassword())
                .locations("classpath:db/migration")
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .outOfOrder(true);
        if (target != null) {
            configuration = configuration.target(target);
        }
        return configuration.load();
    }

    private static boolean checkConstraintExists(String name) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.CHECK_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'reviews'
                  AND CONSTRAINT_NAME = ?
                """,
                Integer.class, name);
        return count != null && count > 0;
    }

    private static String statusColumnDefault() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COLUMN_DEFAULT
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'reviews'
                  AND COLUMN_NAME = 'status'
                """,
                String.class);
    }

    private static void insertReviewWithRating(int overallRating) {
        jdbcTemplate.update(
                """
                INSERT INTO members (
                    email, password, nickname, phone, role, status, name, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "retry@cakeshop.local", "encoded-password", "retry-test", "010-0000-0000",
                "USER", "ACTIVE", "재시도 테스트", CREATED_AT, CREATED_AT);
        Long memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbcTemplate.update(
                """
                INSERT INTO categories (code, name, sort_order, created_at, updated_at)
                VALUES (?, ?, 0, ?, ?)
                """,
                "CAT-retry", "테스트 분류", CREATED_AT, CREATED_AT);
        Long categoryId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbcTemplate.update(
                """
                INSERT INTO products (
                    category_id, name, base_price, product_type, status, created_at, updated_at
                )
                VALUES (?, ?, ?, 'GENERAL', 'ACTIVE', ?, ?)
                """,
                categoryId, "딸기 생크림 케이크", new BigDecimal("30000"), CREATED_AT, CREATED_AT);
        Long productId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbcTemplate.update(
                """
                INSERT INTO orders (
                    order_number, member_id, order_type, orderer_name, orderer_phone,
                    pickup_name, pickup_phone, original_amount, final_amount,
                    status, pickup_at, picked_up_at, created_at, updated_at
                )
                VALUES (?, ?, 'GENERAL', ?, ?, ?, ?, ?, ?, 'PICKED_UP', ?, ?, ?, ?)
                """,
                "ORD-retry", memberId, "주문자", "010-0000-0000",
                "수령인", "010-0000-0000", new BigDecimal("30000"), new BigDecimal("30000"),
                CREATED_AT, CREATED_AT, CREATED_AT, CREATED_AT);
        Long orderId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbcTemplate.update(
                """
                INSERT INTO order_items (
                    order_id, product_id, product_name, product_type,
                    quantity, base_price, total_amount
                )
                VALUES (?, ?, ?, 'GENERAL', 1, ?, ?)
                """,
                orderId, productId, "딸기 생크림 케이크",
                new BigDecimal("30000"), new BigDecimal("30000"));
        Long orderItemId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbcTemplate.update(
                """
                INSERT INTO reviews (
                    order_item_id, product_id, member_id,
                    overall_rating, taste_rating, design_rating, service_rating, content
                )
                VALUES (?, ?, ?, ?, 5, 5, 5, ?)
                """,
                orderItemId, productId, memberId, overallRating, "범위 밖 평점");
    }
}
