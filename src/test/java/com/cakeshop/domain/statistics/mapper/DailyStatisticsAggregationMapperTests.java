package com.cakeshop.domain.statistics.mapper;

import static org.assertj.core.api.Assertions.assertThat;

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
class DailyStatisticsAggregationMapperTests {

    private static final LocalDate STATISTICS_DATE = LocalDate.of(2026, 8, 1);
    private static final LocalDateTime START = STATISTICS_DATE.atStartOfDay();
    private static final LocalDateTime END = START.plusDays(1);

    private final DailyStatisticsAggregationMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    private String suffix;
    private long memberId;

    @Autowired
    DailyStatisticsAggregationMapperTests(
            DailyStatisticsAggregationMapper mapper,
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
    void replaceDailyStatistics_ordersAndPayments_writesExpectedAggregate() {
        insertOrder("PENDING", "PENDING_PAYMENT", START.plusHours(1), START.plusHours(1));
        insertOrder("COMPLETED", "PICKED_UP", START.plusHours(2), START.plusHours(2));
        insertOrder("CANCELED", "CANCELED", START.plusHours(3), START.plusHours(3));
        insertOrder("REJECTED", "REJECTED", START.plusHours(4), START.plusHours(4));
        insertOrder("AT-END", "PICKED_UP", END, END);
        insertPayment("DONE", "DONE", new BigDecimal("10000"), START.plusHours(5), START.plusHours(5));
        insertPayment(
                "CANCELED",
                "CANCELED",
                new BigDecimal("20000"),
                START.plusHours(6),
                START.plusHours(6)
        );
        insertPayment("AT-END", "DONE", new BigDecimal("30000"), END, END);

        mapper.replaceDailyStatistics(STATISTICS_DATE, START, END);

        assertThat(findDailyStatistics()).isEqualTo(
                new DailyStatisticsRow(4, 1, 2, new BigDecimal("10000"))
        );
    }

    @Test
    void replaceDailyStatistics_noSourceData_writesZeroRow() {
        mapper.replaceDailyStatistics(STATISTICS_DATE, START, END);

        assertThat(findDailyStatistics()).isEqualTo(
                new DailyStatisticsRow(0, 0, 0, BigDecimal.ZERO)
        );
    }

    @Test
    void replaceDailyStatistics_repeatedAfterSourceChange_replacesExistingValues() {
        long orderId = insertOrder(
                "CHANGE",
                "PENDING_PAYMENT",
                START.plusHours(1),
                START.plusHours(1)
        );
        long paymentId = insertPayment(
                "CHANGE",
                "DONE",
                new BigDecimal("10000"),
                START.plusHours(2),
                START.plusHours(2)
        );
        mapper.replaceDailyStatistics(STATISTICS_DATE, START, END);

        jdbcTemplate.update(
                "UPDATE orders SET status = 'PICKED_UP' WHERE id = ?",
                orderId
        );
        jdbcTemplate.update(
                "UPDATE payments SET amount = 20000 WHERE id = ?",
                paymentId
        );
        mapper.replaceDailyStatistics(STATISTICS_DATE, START, END);

        assertThat(findDailyStatistics()).isEqualTo(
                new DailyStatisticsRow(1, 1, 0, new BigDecimal("20000"))
        );
        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM daily_statistics WHERE statistics_date = ?",
                Integer.class,
                STATISTICS_DATE
        );
        assertThat(rowCount).isOne();
    }

    @Test
    void findChangedStatisticsDates_windowBoundaries_returnsStartExclusiveEndInclusiveDates() {
        LocalDateTime sourceStart = LocalDateTime.of(2026, 8, 10, 0, 0);
        LocalDateTime sourceEnd = sourceStart.plusHours(1);
        LocalDate latestStatisticsDate = LocalDate.of(2026, 8, 9);

        insertOrder("AT-START", "PICKED_UP", START, sourceStart);
        insertOrder("AFTER-START", "PICKED_UP", START.plusDays(1), sourceStart.plusNanos(1_000));
        insertPayment(
                "AT-END",
                "DONE",
                new BigDecimal("10000"),
                START.plusDays(2),
                sourceEnd
        );
        insertOrder("AFTER-END", "PICKED_UP", START.plusDays(3), sourceEnd.plusNanos(1_000));
        insertOrder(
                "TODAY",
                "PICKED_UP",
                latestStatisticsDate.plusDays(1).atStartOfDay(),
                sourceStart.plusMinutes(30)
        );

        List<LocalDate> dates = mapper.findChangedStatisticsDates(
                sourceStart,
                sourceEnd,
                latestStatisticsDate
        );

        assertThat(dates).containsExactly(STATISTICS_DATE.plusDays(1), STATISTICS_DATE.plusDays(2));
    }

    @Test
    void findEarliestSourceDates_sourceDataExists_returnsEachEarliestDate() {
        insertOrder(
                "EARLIEST-ORDER",
                "PICKED_UP",
                LocalDate.of(2026, 7, 1).atStartOfDay(),
                START
        );
        insertPayment(
                "EARLIEST-PAYMENT",
                "DONE",
                new BigDecimal("10000"),
                LocalDate.of(2026, 6, 1).atStartOfDay(),
                START
        );

        assertThat(mapper.findEarliestOrderDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(mapper.findEarliestApprovedPaymentDate()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    private DailyStatisticsRow findDailyStatistics() {
        return jdbcTemplate.queryForObject(
                """
                SELECT
                    total_order_count,
                    completed_order_count,
                    canceled_order_count,
                    total_sales_amount
                FROM daily_statistics
                WHERE statistics_date = ?
                """,
                (resultSet, rowNum) -> new DailyStatisticsRow(
                        resultSet.getLong("total_order_count"),
                        resultSet.getLong("completed_order_count"),
                        resultSet.getLong("canceled_order_count"),
                        resultSet.getBigDecimal("total_sales_amount")
                ),
                STATISTICS_DATE
        );
    }

    private long insertMember() {
        jdbcTemplate.update(
                """
                INSERT INTO members (
                    email, password, name, nickname, phone, role, status
                )
                VALUES (?, NULL, '일별 통계 테스트', '일별 통계',
                        '010-0000-0000', 'USER', 'ACTIVE')
                """,
                "daily-statistics-" + suffix + "@example.com"
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertOrder(
            String label,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        String orderNumber = "DAILY-" + label + "-" + suffix;
        String rejectReason = "REJECTED".equals(status) ? "일별 통계 테스트 반려" : null;
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
                    created_at,
                    updated_at
                )
                VALUES (?, ?, 'GENERAL', '주문자', '010-1111-2222',
                        '수령자', '010-3333-4444', 40000, 0, 40000,
                        ?, ?, ?, ?, ?, ?)
                """,
                orderNumber,
                memberId,
                status,
                END.plusDays(10),
                END.plusDays(9),
                rejectReason,
                createdAt,
                updatedAt
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertPayment(
            String label,
            String status,
            BigDecimal amount,
            LocalDateTime approvedAt,
            LocalDateTime updatedAt
    ) {
        long orderId = insertOrder(
                "PAYMENT-" + label,
                "PENDING_PAYMENT",
                START.minusDays(1),
                START.minusDays(2)
        );
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
                    approved_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, 'CARD', ?, ?, ?, ?, ?)
                """,
                orderId,
                "DAILY-TOSS-" + label + "-" + suffix,
                "DAILY-PAYMENT-KEY-" + label + "-" + suffix,
                "DAILY-IDEMPOTENCY-" + label + "-" + suffix,
                amount,
                status,
                status,
                approvedAt,
                updatedAt
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private record DailyStatisticsRow(
            long totalOrderCount,
            long completedOrderCount,
            long canceledOrderCount,
            BigDecimal totalSalesAmount
    ) {
    }
}
