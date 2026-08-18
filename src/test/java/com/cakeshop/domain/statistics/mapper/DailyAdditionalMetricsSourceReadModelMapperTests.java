package com.cakeshop.domain.statistics.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.statistics.dto.source.DailyAdditionalMetricsSourceView;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DailyAdditionalMetricsSourceReadModelMapperTests {

    private static final LocalDate STATISTICS_DATE = LocalDate.of(2026, 8, 1);
    private static final LocalDateTime START = STATISTICS_DATE.atStartOfDay();
    private static final LocalDateTime END = START.plusDays(1);

    private final DailyStatisticsSourceReadModelMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    private String suffix;
    private long memberId;

    @Autowired
    DailyAdditionalMetricsSourceReadModelMapperTests(
            DailyStatisticsSourceReadModelMapper mapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        suffix = Long.toString(System.nanoTime());
        memberId = insertMember("BASE", "USER", "ACTIVE", START.minusDays(10), null);
    }

    @Test
    void findDailyAdditionalMetrics_sourceDataExists_returnsConfirmedMetrics() {
        insertMember("NEW-ACTIVE", "USER", "ACTIVE", START.plusHours(1), null);
        insertMember(
                "NEW-WITHDRAWN",
                "USER",
                "WITHDRAWN",
                START.plusHours(2),
                START.plusHours(3)
        );
        insertMember("ADMIN", "ADMIN", "ACTIVE", START.plusHours(4), null);
        insertMember("AT-END", "USER", "ACTIVE", END, null);

        insertPost(START.plusHours(5), "PUBLISHED");
        insertPost(START.plusHours(6), "DELETED");
        insertPost(END, "PUBLISHED");

        long couponOrderId = insertOrder("COUPON");
        insertPayment(couponOrderId, "COUPON", "DONE", START.plusHours(7));
        insertUsedMemberCoupon(couponOrderId);

        long paidOrderId = insertOrder("PAID");
        insertPayment(paidOrderId, "PAID", "DONE", START.plusHours(8));
        long canceledOrderId = insertOrder("CANCELED");
        long canceledPaymentId = insertPayment(
                canceledOrderId,
                "CANCELED",
                "CANCELED",
                START.plusHours(9)
        );
        insertCancellation(canceledPaymentId, "DONE", new BigDecimal("12000"), START.plusHours(10));
        insertCancellation(canceledPaymentId, "FAILED", new BigDecimal("3000"), START.plusHours(11));

        DailyAdditionalMetricsSourceView result = mapper.findDailyAdditionalMetrics(START, END);

        assertThat(result).isEqualTo(new DailyAdditionalMetricsSourceView(
                2,
                1,
                2,
                1,
                new BigDecimal("12000"),
                2
        ));
    }

    @Test
    void findDailyAdditionalMetrics_noSourceData_returnsZeroValues() {
        DailyAdditionalMetricsSourceView result = mapper.findDailyAdditionalMetrics(
                START.plusYears(10),
                END.plusYears(10)
        );

        assertThat(result).isEqualTo(new DailyAdditionalMetricsSourceView(
                0,
                0,
                0,
                0,
                BigDecimal.ZERO,
                0
        ));
    }

    @Test
    void findEarliestAdditionalMetricsDate_memberExists_returnsMemberCreatedDate() {
        assertThat(mapper.findEarliestAdditionalMetricsDate())
                .isEqualTo(START.minusDays(10).toLocalDate());
    }

    @Test
    void findEarliestAdditionalMetricsDate_withdrawnMemberExists_returnsWithdrawnDate() {
        LocalDateTime withdrawnAt = START.minusDays(20);
        insertMember(
                "EARLIEST-WITHDRAWN",
                "USER",
                "WITHDRAWN",
                START.minusDays(5),
                withdrawnAt
        );

        assertThat(mapper.findEarliestAdditionalMetricsDate())
                .isEqualTo(START.minusDays(20).toLocalDate());
    }

    @Test
    void findEarliestAdditionalMetricsDate_postExists_returnsPostCreatedDate() {
        insertPost(START.minusDays(30), "DELETED");

        assertThat(mapper.findEarliestAdditionalMetricsDate())
                .isEqualTo(START.minusDays(30).toLocalDate());
    }

    @Test
    void findEarliestAdditionalMetricsDate_completedCancellationExists_returnsCanceledDate() {
        long paymentId = insertPayment(
                insertOrder("EARLIEST-CANCELLATION"),
                "EARLIEST-CANCELLATION",
                "CANCELED",
                START.minusDays(1)
        );
        insertCancellation(
                paymentId,
                "DONE",
                new BigDecimal("10000"),
                START.minusDays(40)
        );

        assertThat(mapper.findEarliestAdditionalMetricsDate())
                .isEqualTo(START.minusDays(40).toLocalDate());
    }

    private long insertMember(
            String label,
            String role,
            String status,
            LocalDateTime createdAt,
            LocalDateTime withdrawnAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO members (
                    email, password, name, nickname, phone, role, status,
                    created_at, updated_at, withdrawn_at
                )
                VALUES (?, NULL, '기타 지표 테스트', ?, '010-0000-0000',
                        ?, ?, ?, ?, ?)
                """,
                "additional-metrics-" + label + "-" + suffix + "@example.com",
                "기타지표-" + label + "-" + suffix,
                role,
                status,
                createdAt,
                createdAt,
                withdrawnAt
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertPost(LocalDateTime createdAt, String status) {
        long categoryId = insertPostCategory();
        jdbcTemplate.update(
                """
                INSERT INTO posts (
                    member_id, category_id, title, content, status, created_at, updated_at
                )
                VALUES (?, ?, '기타 지표 게시글', '본문', ?, ?, ?)
                """,
                memberId,
                categoryId,
                status,
                createdAt,
                createdAt
        );
    }

    private long insertPostCategory() {
        String code = "ADDITIONAL_" + suffix + "_" + System.nanoTime();
        jdbcTemplate.update(
                "INSERT INTO post_categories (code, name) VALUES (?, '기타 지표 카테고리')",
                code
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertOrder(String label) {
        jdbcTemplate.update(
                """
                INSERT INTO orders (
                    order_number, member_id, order_type, orderer_name, orderer_phone,
                    pickup_name, pickup_phone, original_amount, discount_amount, final_amount,
                    status, pickup_at, payment_expires_at, created_at, updated_at
                )
                VALUES (?, ?, 'GENERAL', '주문자', '010-1111-2222',
                        '수령자', '010-3333-4444', 20000, 0, 20000,
                        'PENDING_PAYMENT', ?, ?, ?, ?)
                """,
                "ADDITIONAL-" + label + "-" + suffix,
                memberId,
                END.plusDays(10),
                END.plusDays(9),
                START.minusDays(1),
                START.minusDays(1)
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertPayment(
            long orderId,
            String label,
            String status,
            LocalDateTime approvedAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO payments (
                    order_id, toss_order_id, payment_key, idempotency_key, method,
                    amount, status, approved_at, updated_at
                )
                VALUES (?, ?, ?, ?, 'CARD', 20000, ?, ?, ?)
                """,
                orderId,
                "ADDITIONAL-TOSS-" + label + "-" + suffix,
                "ADDITIONAL-KEY-" + label + "-" + suffix,
                "ADDITIONAL-IDEMPOTENCY-" + label + "-" + suffix,
                status,
                approvedAt,
                approvedAt
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertUsedMemberCoupon(long orderId) {
        long adminId = insertMember("COUPON-ADMIN", "ADMIN", "ACTIVE", START.minusDays(10), null);
        jdbcTemplate.update(
                """
                INSERT INTO coupons (
                    name, discount_type, discount_value, minimum_order_amount,
                    maximum_discount_amount, total_quantity, issued_quantity,
                    starts_at, expires_at, status, target_type, created_by
                )
                VALUES ('기타 지표 쿠폰', 'FIXED_AMOUNT', 1000, 0,
                        NULL, 1, 1, ?, ?, 'ACTIVE', 'SPECIFIC_MEMBERS', ?)
                """,
                START.minusDays(10),
                END.plusDays(10),
                adminId
        );
        long couponId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update(
                """
                INSERT INTO member_coupons (
                    coupon_id, member_id, status, applied_order_id, issued_at, used_at
                )
                VALUES (?, ?, 'USED', ?, ?, ?)
                """,
                couponId,
                memberId,
                orderId,
                START.minusDays(1),
                START.plusHours(7)
        );
    }

    private void insertCancellation(
            long paymentId,
            String status,
            BigDecimal amount,
            LocalDateTime canceledAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO payment_cancellations (
                    payment_id, idempotency_key, cancel_amount, cancel_reason,
                    status, canceled_at, created_at, updated_at
                )
                VALUES (?, ?, ?, '기타 지표 테스트', ?, ?, ?, ?)
                """,
                paymentId,
                "ADDITIONAL-CANCEL-" + status + "-" + suffix,
                amount,
                status,
                canceledAt,
                canceledAt,
                canceledAt
        );
    }
}
