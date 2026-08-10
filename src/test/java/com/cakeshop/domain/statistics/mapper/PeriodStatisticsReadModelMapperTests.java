package com.cakeshop.domain.statistics.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.statistics.dto.view.DailyOrderStatisticsView;
import com.cakeshop.domain.statistics.dto.view.DailySalesStatisticsView;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PeriodStatisticsReadModelMapperTests {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 1, 0, 0);
    private static final LocalDateTime END = START.plusDays(2);

    private final PeriodStatisticsReadModelMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    private String suffix;
    private long memberId;

    @Autowired
    PeriodStatisticsReadModelMapperTests(
            PeriodStatisticsReadModelMapper mapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        suffix = Long.toString(System.nanoTime());
        memberId = insertMember();
    }

    @Test
    void findDailyOrderStatistics_statusAndDateRange_returnsDailyCountsInOrder() {
        insertOrder("BEFORE", "PENDING_PAYMENT", START.minusNanos(1_000));
        insertOrder("PENDING", "PENDING_PAYMENT", START);
        insertOrder("REVIEW", "UNDER_REVIEW", START.plusHours(1));
        insertOrder("READY", "READY_FOR_PICKUP", START.plusHours(2));
        insertOrder("PICKED", "PICKED_UP", START.plusHours(3));
        insertOrder("CANCELED", "CANCELED", START.plusHours(4));
        insertOrder("REJECTED", "REJECTED", START.plusHours(5));
        insertOrder("EXPIRED", "EXPIRED", START.plusHours(6));
        insertOrder("SECOND-DAY", "PICKED_UP", START.plusDays(1).plusHours(1));
        insertOrder("AT-END", "PICKED_UP", END);

        List<DailyOrderStatisticsView> statistics =
                mapper.findDailyOrderStatistics(START, END);

        assertThat(statistics).containsExactly(
                new DailyOrderStatisticsView(LocalDate.of(2026, 8, 1), 7, 1, 2),
                new DailyOrderStatisticsView(LocalDate.of(2026, 8, 2), 1, 1, 0)
        );
    }

    @Test
    void findDailySalesStatistics_statusAndDateRange_returnsDoneSalesInOrder() {
        insertPayment("BEFORE", "DONE", new BigDecimal("1000"), START.minusNanos(1_000));
        insertPayment("START-FIRST", "DONE", new BigDecimal("10000"), START);
        insertPayment("START-SECOND", "DONE", new BigDecimal("20000"), START.plusHours(1));
        insertPayment("CANCELED", "CANCELED", new BigDecimal("30000"), START.plusHours(2));
        insertPayment(
                "PARTIAL",
                "PARTIAL_CANCELED",
                new BigDecimal("40000"),
                START.plusHours(3)
        );
        insertPayment("ABORTED", "ABORTED", new BigDecimal("50000"), START.plusHours(4));
        insertPayment("EXPIRED", "EXPIRED", new BigDecimal("60000"), START.plusHours(5));
        insertPayment(
                "SECOND-DAY",
                "DONE",
                new BigDecimal("70000"),
                START.plusDays(1).plusHours(1)
        );
        insertPayment("AT-END", "DONE", new BigDecimal("80000"), END);

        List<DailySalesStatisticsView> statistics =
                mapper.findDailySalesStatistics(START, END);

        assertThat(statistics).containsExactly(
                new DailySalesStatisticsView(
                        LocalDate.of(2026, 8, 1),
                        new BigDecimal("30000")
                ),
                new DailySalesStatisticsView(
                        LocalDate.of(2026, 8, 2),
                        new BigDecimal("70000")
                )
        );
    }

    private long insertMember() {
        String email = "period-statistics-" + suffix + "@example.com";
        jdbcTemplate.update(
                """
                INSERT INTO members (
                    email,
                    password,
                    name,
                    nickname,
                    phone,
                    role,
                    status
                )
                VALUES (?, NULL, '기간별 통계 테스트', '기간별 통계',
                        '010-0000-0000', 'USER', 'ACTIVE')
                """,
                email
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?",
                Long.class,
                email
        );
    }

    private long insertOrder(String label, String status, LocalDateTime createdAt) {
        String orderNumber = "PS-" + label + "-" + suffix;
        String rejectReason = "REJECTED".equals(status) ? "기간별 통계 테스트 반려" : null;
        jdbcTemplate.update(
                """
                INSERT INTO orders (
                    order_number,
                    member_id,
                    order_type,
                    orderer_name,
                    orderer_phone,
                    pickup_name,
                    pickup_phone,
                    original_amount,
                    discount_amount,
                    final_amount,
                    status,
                    pickup_at,
                    payment_expires_at,
                    reject_reason,
                    created_at
                )
                VALUES (?, ?, 'GENERAL', '주문자', '010-1111-2222',
                        '수령자', '010-3333-4444', 40000, 0, 40000,
                        ?, ?, ?, ?, ?)
                """,
                orderNumber,
                memberId,
                status,
                END.plusDays(1),
                END,
                rejectReason,
                createdAt
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM orders WHERE order_number = ?",
                Long.class,
                orderNumber
        );
    }

    private void insertPayment(
            String label,
            String status,
            BigDecimal amount,
            LocalDateTime approvedAt
    ) {
        long orderId = insertOrder("PAYMENT-" + label, "PENDING_PAYMENT", START);
        String tossOrderId = "PERIOD-TOSS-" + label + "-" + suffix;
        jdbcTemplate.update(
                """
                INSERT INTO payments (
                    order_id,
                    toss_order_id,
                    payment_key,
                    idempotency_key,
                    method,
                    amount,
                    status,
                    provider_status,
                    approved_at
                )
                VALUES (?, ?, ?, ?, 'CARD', ?, ?, ?, ?)
                """,
                orderId,
                tossOrderId,
                "PERIOD-PAYMENT-KEY-" + label + "-" + suffix,
                "PERIOD-IDEMPOTENCY-" + label + "-" + suffix,
                amount,
                status,
                status,
                approvedAt
        );
    }
}
