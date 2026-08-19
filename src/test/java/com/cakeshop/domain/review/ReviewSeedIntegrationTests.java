package com.cakeshop.domain.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.sql.DataSource;

import com.cakeshop.domain.review.dto.form.ReviewWriteForm;
import com.cakeshop.domain.review.service.ReviewService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.config.MariaDbIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.transaction.annotation.Transactional;

/** 공통 시드 뒤에 적용한 후기 시드가 제공하는 대표 시나리오와 재실행 계약을 검증한다. */
@SpringBootTest
@MariaDbIntegrationTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(
        scripts = {
                "classpath:db/seed/seed-local.sql",
                "classpath:db/seed/seed-review.sql"
        },
        config = @SqlConfig(encoding = "UTF-8"),
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS
)
class ReviewSeedIntegrationTests {

    private static final String REVIEW_SEED = "db/seed/seed-review.sql";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReviewService reviewService;

    @Test
    void reviewSeed_generalAndCustomOrdersEachHaveReviewedAndWritableItem() {
        assertThat(reviewScenarios()).containsExactly(
                new SeedReviewScenario("CUSTOM", 1, 1),
                new SeedReviewScenario("GENERAL", 1, 1)
        );

        long memberId = memberId();
        assertThat(reviewService.getWritableOrderItems(memberId, new PageRequest(1, null))
                .getContent())
                .extracting(item -> item.orderNumber())
                .containsExactly(
                        "SEED-REVIEW-CUSTOM-WRITABLE",
                        "SEED-REVIEW-GENERAL-WRITABLE"
                );
        assertThat(reviewService.getMyReviews(memberId, new PageRequest(1, null))
                .getContent())
                .extracting(review -> review.orderNumber())
                .containsExactly(
                        "SEED-REVIEW-CUSTOM-REVIEWED",
                        "SEED-REVIEW-GENERAL-REVIEWED"
                );

        assertThat(count(
                """
                SELECT COUNT(*)
                FROM payments payment
                JOIN orders o ON o.id = payment.order_id
                JOIN members m ON m.id = o.member_id
                WHERE m.email = 'user@cakeshop.local'
                  AND o.order_number LIKE 'SEED-REVIEW-%'
                  AND payment.status = 'DONE'
                """
        )).isEqualTo(4);
    }

    @Test
    @Transactional
    void reviewSeed_writableItemCanBeReviewedThroughRealService() {
        long memberId = memberId();
        Long orderItemId = jdbcTemplate.queryForObject(
                """
                SELECT oi.id
                FROM order_items oi
                JOIN orders o ON o.id = oi.order_id
                WHERE o.order_number = 'SEED-REVIEW-GENERAL-WRITABLE'
                """,
                Long.class
        );

        ReviewWriteForm form = new ReviewWriteForm();
        form.setOrderItemId(orderItemId);
        form.setOverallRating(3);
        form.setTasteRating(4);
        form.setDesignRating(3);
        form.setServiceRating(4);
        form.setContent("직접 작성 흐름을 확인하기 위한 로컬 시드 후기입니다.");

        reviewService.write(form, memberId);

        assertThat(reviewService.getWritableOrderItems(memberId, new PageRequest(1, null))
                .getTotalElements()).isEqualTo(1);
        assertThat(reviewService.getMyReviews(memberId, new PageRequest(1, null))
                .getTotalElements()).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT p.average_rating
                FROM products p
                JOIN order_items oi ON oi.product_id = p.id
                WHERE oi.id = ?
                """,
                BigDecimal.class,
                orderItemId
        )).isEqualByComparingTo("4.00");
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT p.review_count
                FROM products p
                JOIN order_items oi ON oi.product_id = p.id
                WHERE oi.id = ?
                """,
                Integer.class,
                orderItemId
        )).isEqualTo(2);
    }

    @Test
    void reviewSeed_canBeReappliedWithTheSameReviewScenario() {
        SeedSnapshot expected = snapshot();
        assertThat(expected.aggregateMismatchCount()).isZero();

        runReviewSeed();

        assertThat(snapshot()).isEqualTo(expected);
    }

    private List<SeedReviewScenario> reviewScenarios() {
        return jdbcTemplate.query(
                """
                SELECT o.order_type,
                       SUM(review.id IS NOT NULL) AS reviewed_count,
                       SUM(review.id IS NULL) AS writable_count
                FROM orders o
                JOIN members m ON m.id = o.member_id
                JOIN order_items oi ON oi.order_id = o.id
                LEFT JOIN reviews review ON review.order_item_id = oi.id
                WHERE m.email = 'user@cakeshop.local'
                  AND o.status = 'PICKED_UP'
                  AND o.order_number LIKE 'SEED-REVIEW-%'
                GROUP BY o.order_type
                ORDER BY o.order_type
                """,
                (resultSet, rowNumber) -> new SeedReviewScenario(
                        resultSet.getString("order_type"),
                        resultSet.getInt("reviewed_count"),
                        resultSet.getInt("writable_count")
                )
        );
    }

    private SeedSnapshot snapshot() {
        return new SeedSnapshot(
                count("SELECT COUNT(*) FROM orders WHERE order_number LIKE 'SEED-REVIEW-%'"),
                count("""
                        SELECT COUNT(*)
                        FROM payments payment
                        JOIN orders o ON o.id = payment.order_id
                        WHERE o.order_number LIKE 'SEED-REVIEW-%'
                        """),
                count("""
                        SELECT COUNT(*)
                        FROM reviews review
                        JOIN order_items oi ON oi.id = review.order_item_id
                        JOIN orders o ON o.id = oi.order_id
                        WHERE o.order_number LIKE 'SEED-REVIEW-%'
                        """),
                count("""
                        SELECT COUNT(*)
                        FROM order_item_options oio
                        JOIN order_items oi ON oi.id = oio.order_item_id
                        JOIN orders o ON o.id = oi.order_id
                        WHERE o.order_number LIKE 'SEED-REVIEW-%'
                        """),
                count("""
                        SELECT COUNT(*)
                        FROM products p
                        LEFT JOIN (
                            SELECT product_id,
                                   AVG(overall_rating) AS average_rating,
                                   COUNT(*) AS review_count
                            FROM reviews
                            WHERE status = 'PUBLISHED'
                            GROUP BY product_id
                        ) review_aggregate
                            ON review_aggregate.product_id = p.id
                        WHERE p.name IN ('딸기 생크림 케이크 1호', '레터링 생크림 케이크')
                          AND (
                              p.average_rating
                                  <> COALESCE(review_aggregate.average_rating, 0.00)
                              OR p.review_count
                                  <> COALESCE(review_aggregate.review_count, 0)
                          )
                        """),
                reviewScenarios()
        );
    }

    private long memberId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = 'user@cakeshop.local'",
                Long.class
        );
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private void runReviewSeed() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.addScript(new ClassPathResource(REVIEW_SEED));
        DatabasePopulatorUtils.execute(populator, dataSource);
    }

    private record SeedReviewScenario(
            String orderType,
            int reviewedCount,
            int writableCount
    ) {
    }

    private record SeedSnapshot(
            int orderCount,
            int paymentCount,
            int reviewCount,
            int optionCount,
            int aggregateMismatchCount,
            List<SeedReviewScenario> scenarios
    ) {
    }
}
