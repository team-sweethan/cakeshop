package com.cakeshop.domain.statistics.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cakeshop.domain.statistics.dto.source.DailyProductStatisticsSourceView;
import com.cakeshop.domain.statistics.service.aggregation.DailyProductStatisticsSourceReadModelQueryService;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DailyProductStatisticsSourceReadModelQueryService.class)
class DailyProductStatisticsSourceReadModelMapperTests {

    private static final LocalDate STATISTICS_DATE = LocalDate.of(2026, 8, 1);
    private static final LocalDateTime START = STATISTICS_DATE.atStartOfDay();
    private static final LocalDateTime END = START.plusDays(1);

    private final DailyStatisticsSourceReadModelMapper mapper;
    private final DailyProductStatisticsSourceReadModelQueryService queryService;
    private final JdbcTemplate jdbcTemplate;

    private String suffix;
    private long memberId;
    private long categoryId;

    @Autowired
    DailyProductStatisticsSourceReadModelMapperTests(
            DailyStatisticsSourceReadModelMapper mapper,
            DailyProductStatisticsSourceReadModelQueryService queryService,
            JdbcTemplate jdbcTemplate
    ) {
        this.mapper = mapper;
        this.queryService = queryService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        suffix = Long.toString(System.nanoTime());
        memberId = insertMember();
        categoryId = insertCategory();
    }

    @Test
    void findDailyProductStatistics_ordersAndPayments_returnsProductAggregates() {
        long productAId = insertProduct("A", "상품 A");
        long productBId = insertProduct("B", "상품 B");
        long excludedProductId = insertProduct("EXCLUDED", "제외 상품");

        long paidOrderAId = insertOrder(
                "A-PAID",
                START.plusHours(1),
                new BigDecimal("20000"),
                new BigDecimal("18000")
        );
        insertOrderItem(paidOrderAId, productAId, "상품 A 이전명", 2);
        insertPayment(
                paidOrderAId,
                "A-DONE",
                "DONE",
                new BigDecimal("18000"),
                START.plusHours(5)
        );

        long latestOrderAId = insertOrder("A-LATEST", START.plusHours(3));
        insertOrderItem(latestOrderAId, productAId, "상품 A 최신명", 1);

        long paidOrderBId = insertOrder(
                "B-PAID",
                START.minusDays(1),
                new BigDecimal("30000"),
                new BigDecimal("25000")
        );
        insertOrderItem(paidOrderBId, productBId, "상품 B", 3);
        insertPayment(
                paidOrderBId,
                "B-DONE",
                "DONE",
                new BigDecimal("25000"),
                START.plusHours(6)
        );

        long canceledOrderId = insertOrder("CANCELED", START.minusDays(1));
        insertOrderItem(canceledOrderId, excludedProductId, "제외 상품", 4);
        insertPayment(
                canceledOrderId,
                "CANCELED",
                "CANCELED",
                new BigDecimal("30000"),
                START.plusHours(7)
        );

        long endBoundaryOrderId = insertOrder("AT-END", END);
        insertOrderItem(endBoundaryOrderId, excludedProductId, "제외 상품", 5);
        long endBoundaryPaymentOrderId = insertOrder("PAYMENT-AT-END", START.minusDays(1));
        insertOrderItem(endBoundaryPaymentOrderId, excludedProductId, "제외 상품", 6);
        insertPayment(
                endBoundaryPaymentOrderId,
                "AT-END",
                "DONE",
                new BigDecimal("40000"),
                END
        );

        List<DailyProductStatisticsSourceView> result =
                queryService.getDailyProductStatistics(START, END);

        assertThat(result).containsExactly(
                new DailyProductStatisticsSourceView(
                        productAId,
                        "상품 A 최신명",
                        2,
                        2,
                        new BigDecimal("18000")
                ),
                new DailyProductStatisticsSourceView(
                        productBId,
                        "상품 B",
                        0,
                        3,
                        new BigDecimal("25000")
                )
        );
    }

    @Test
    void findDailyProductStatistics_sameCreatedAt_usesHigherOrderItemIdName() {
        long productId = insertProduct("LATEST-NAME", "상품명");
        long firstOrderId = insertOrder("FIRST-NAME", START.plusHours(1));
        long secondOrderId = insertOrder("SECOND-NAME", START.plusHours(1));
        insertOrderItem(firstOrderId, productId, "이전 상품명", 1);
        insertOrderItem(secondOrderId, productId, "최신 상품명", 1);

        List<DailyProductStatisticsSourceView> result =
                mapper.findDailyProductStatistics(START, END);

        assertThat(result).containsExactly(
                new DailyProductStatisticsSourceView(
                        productId,
                        "최신 상품명",
                        2,
                        0,
                        BigDecimal.ZERO
                )
        );
    }

    @Test
    void findDailyProductStatistics_sameProductItemsInOneOrder_countsOrderOnce() {
        long productId = insertProduct("DISTINCT", "중복 주문 상품");
        long orderId = insertOrder("DISTINCT", START.plusHours(1));
        insertOrderItem(orderId, productId, "중복 주문 상품", 1);
        insertOrderItem(orderId, productId, "중복 주문 상품", 2);

        List<DailyProductStatisticsSourceView> result =
                mapper.findDailyProductStatistics(START, END);

        assertThat(result).singleElement().satisfies(statistics -> {
            assertThat(statistics.productId()).isEqualTo(productId);
            assertThat(statistics.orderCount()).isOne();
        });
    }

    @Test
    void findDailyProductStatistics_multiProductPayment_allocatesDiscountedAmountByItemRatio() {
        long productAId = insertProduct("ALLOCATE-A", "배분 상품 A");
        long productBId = insertProduct("ALLOCATE-B", "배분 상품 B");
        long orderId = insertOrder(
                "MULTI-PRODUCT",
                START.plusHours(1),
                new BigDecimal("30000"),
                new BigDecimal("27000")
        );
        insertOrderItem(orderId, productAId, "배분 상품 A", 1, new BigDecimal("10000"));
        insertOrderItem(orderId, productBId, "배분 상품 B", 2, new BigDecimal("20000"));
        insertPayment(
                orderId,
                "MULTI-PRODUCT",
                "DONE",
                new BigDecimal("27000"),
                START.plusHours(2)
        );

        List<DailyProductStatisticsSourceView> result =
                mapper.findDailyProductStatistics(START, END);

        assertThat(result).containsExactly(
                new DailyProductStatisticsSourceView(
                        productAId,
                        "배분 상품 A",
                        1,
                        1,
                        new BigDecimal("9000")
                ),
                new DailyProductStatisticsSourceView(
                        productBId,
                        "배분 상품 B",
                        1,
                        2,
                        new BigDecimal("18000")
                )
        );
        BigDecimal totalSalesAmount = result.stream()
                .map(DailyProductStatisticsSourceView::salesAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalSalesAmount).isEqualByComparingTo("27000");
    }

    @Test
    void findDailyProductStatistics_equalRemainders_allocatesToLowerOrderItemId() {
        long productAId = insertProduct("REMAINDER-A", "나머지 상품 A");
        long productBId = insertProduct("REMAINDER-B", "나머지 상품 B");
        long orderId = insertOrder(
                "REMAINDER",
                START.plusHours(1),
                new BigDecimal("2"),
                BigDecimal.ONE
        );
        insertOrderItem(orderId, productAId, "나머지 상품 A", 1, BigDecimal.ONE);
        insertOrderItem(orderId, productBId, "나머지 상품 B", 1, BigDecimal.ONE);
        insertPayment(
                orderId,
                "REMAINDER",
                "DONE",
                BigDecimal.ONE,
                START.plusHours(2)
        );

        List<DailyProductStatisticsSourceView> result =
                mapper.findDailyProductStatistics(START, END);

        assertThat(result)
                .extracting(DailyProductStatisticsSourceView::salesAmount)
                .containsExactly(BigDecimal.ONE, BigDecimal.ZERO);
    }

    @Test
    void findDailyProductStatistics_differentRemainders_allocatesToLargerFraction() {
        long productAId = insertProduct("FRACTION-A", "소수 상품 A");
        long productBId = insertProduct("FRACTION-B", "소수 상품 B");
        long orderId = insertOrder(
                "FRACTION",
                START.plusHours(1),
                new BigDecimal("3"),
                new BigDecimal("2")
        );
        insertOrderItem(orderId, productAId, "소수 상품 A", 1, BigDecimal.ONE);
        insertOrderItem(orderId, productBId, "소수 상품 B", 1, new BigDecimal("2"));
        insertPayment(
                orderId,
                "FRACTION",
                "DONE",
                new BigDecimal("2"),
                START.plusHours(2)
        );

        List<DailyProductStatisticsSourceView> result =
                queryService.getDailyProductStatistics(START, END);

        assertThat(result)
                .extracting(DailyProductStatisticsSourceView::salesAmount)
                .containsExactly(BigDecimal.ONE, BigDecimal.ONE);
    }

    @Test
    void getDailyProductStatistics_originalAmountMismatch_rejectsAllocation() {
        long productId = insertProduct("INVALID", "불일치 상품");
        long orderId = insertOrder(
                "INVALID",
                START.plusHours(1),
                new BigDecimal("30000"),
                new BigDecimal("27000")
        );
        insertOrderItem(orderId, productId, "불일치 상품", 1, new BigDecimal("20000"));
        insertPayment(
                orderId,
                "INVALID",
                "DONE",
                new BigDecimal("27000"),
                START.plusHours(2)
        );

        assertThatThrownBy(() -> queryService.getDailyProductStatistics(START, END))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("상품별 매출을 배분할 수 없는 주문 데이터입니다.");
    }

    @Test
    void findDailyProductStatistics_noSourceData_returnsEmptyList() {
        assertThat(mapper.findDailyProductStatistics(START, END)).isEmpty();
    }

    private long insertMember() {
        jdbcTemplate.update(
                """
                INSERT INTO members (
                    email, password, name, nickname, phone, role, status
                )
                VALUES (?, NULL, '상품 통계 테스트', '상품 통계',
                        '010-0000-0000', 'USER', 'ACTIVE')
                """,
                "daily-product-statistics-" + suffix + "@example.com"
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertCategory() {
        jdbcTemplate.update(
                """
                INSERT INTO categories (code, name)
                VALUES (?, '상품 통계 카테고리')
                """,
                "DAILY_PRODUCT_" + suffix
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertProduct(String label, String name) {
        jdbcTemplate.update(
                """
                INSERT INTO products (
                    category_id,
                    name,
                    base_price,
                    product_type,
                    preparation_days,
                    status
                )
                VALUES (?, ?, 10000, 'GENERAL', 0, 'ACTIVE')
                """,
                categoryId,
                name + "-" + label
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertOrder(String label, LocalDateTime createdAt) {
        return insertOrder(
                label,
                createdAt,
                new BigDecimal("40000"),
                new BigDecimal("40000")
        );
    }

    private long insertOrder(
            String label,
            LocalDateTime createdAt,
            BigDecimal originalAmount,
            BigDecimal finalAmount
    ) {
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
                    created_at,
                    updated_at
                )
                VALUES (?, ?, 'GENERAL', '주문자', '010-1111-2222',
                        '수령자', '010-3333-4444', ?, ?, ?,
                        'READY_FOR_PICKUP', ?, ?, ?, ?)
                """,
                "DAILY-PRODUCT-" + label + "-" + suffix,
                memberId,
                originalAmount,
                originalAmount.subtract(finalAmount),
                finalAmount,
                END.plusDays(10),
                END.plusDays(9),
                createdAt,
                createdAt
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertOrderItem(
            long orderId,
            long productId,
            String productName,
            int quantity
    ) {
        insertOrderItem(
                orderId,
                productId,
                productName,
                quantity,
                BigDecimal.valueOf(10000L * quantity)
        );
    }

    private void insertOrderItem(
            long orderId,
            long productId,
            String productName,
            int quantity,
            BigDecimal totalAmount
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO order_items (
                    order_id,
                    product_id,
                    product_name,
                    product_type,
                    quantity,
                    base_price,
                    option_amount,
                    total_amount,
                    preparation_days,
                    cancellation_limit_days
                )
                VALUES (?, ?, ?, 'GENERAL', ?, ?, 0, ?, 0, 0)
                """,
                orderId,
                productId,
                productName,
                quantity,
                totalAmount.divide(BigDecimal.valueOf(quantity)),
                totalAmount
        );
    }

    private void insertPayment(
            long orderId,
            String label,
            String status,
            BigDecimal amount,
            LocalDateTime approvedAt
    ) {
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
                    approved_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, 'CARD', ?, ?, ?, ?)
                """,
                orderId,
                "DAILY-PRODUCT-TOSS-" + label + "-" + suffix,
                "DAILY-PRODUCT-KEY-" + label + "-" + suffix,
                "DAILY-PRODUCT-IDEMPOTENCY-" + label + "-" + suffix,
                amount,
                status,
                approvedAt,
                approvedAt
        );
    }
}
