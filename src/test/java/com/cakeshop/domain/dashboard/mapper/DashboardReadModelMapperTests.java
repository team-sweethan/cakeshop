package com.cakeshop.domain.dashboard.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.dashboard.dto.view.LowStockProductView;
import com.cakeshop.domain.dashboard.dto.view.PickupUrgency;
import com.cakeshop.domain.dashboard.dto.view.RecentOrderView;
import com.cakeshop.domain.dashboard.dto.view.TodayPickupScheduleView;
import com.cakeshop.global.config.MariaDbIntegrationTest;
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
class DashboardReadModelMapperTests {

    private static final LocalDateTime START =
            LocalDateTime.of(2026, 8, 6, 0, 0);
    private static final LocalDateTime END = START.plusDays(1);
    private static final LocalDateTime PICKUP_QUERY_REFERENCE_AT = START.plusHours(10);

    private final DashboardReadModelMapper dashboardReadModelMapper;
    private final JdbcTemplate jdbcTemplate;

    private String suffix;
    private long categoryId;
    private long postCategoryId;
    private long memberId;
    private long productId;

    @Autowired
    DashboardReadModelMapperTests(
            DashboardReadModelMapper dashboardReadModelMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.dashboardReadModelMapper = dashboardReadModelMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        suffix = Long.toString(System.nanoTime());
        memberId = insertMember();
        productId = insertProduct();
        postCategoryId = insertPostCategory();
    }

    @Test
    void countTodayOrders_doneWithinRange_countsOnlyDoneOrders() {
        insertPayment("DONE-FIRST", "DONE", START.plusHours(1));
        insertPayment("DONE-SECOND", "DONE", START.plusHours(2));
        insertPayment("READY", "READY", null);
        insertPayment("CANCELED", "CANCELED", START.plusHours(3));
        insertPayment("PARTIAL", "PARTIAL_CANCELED", START.plusHours(4));
        insertPayment("ABORTED", "ABORTED", START.plusHours(5));
        insertPayment("EXPIRED", "EXPIRED", START.plusHours(6));

        long count = dashboardReadModelMapper.countTodayOrders(START, END);

        assertThat(count).isEqualTo(2);
    }

    @Test
    void countTodayOrders_dateBoundary_includesStartAndExcludesEnd() {
        insertPayment("AT-START", "DONE", START);
        insertPayment("BEFORE-START", "DONE", START.minusNanos(1_000));
        insertPayment("AT-END", "DONE", END);

        long count = dashboardReadModelMapper.countTodayOrders(START, END);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void countTodayOrders_noMatchingPayments_returnsZero() {
        long count = dashboardReadModelMapper.countTodayOrders(START, END);

        assertThat(count).isZero();
    }

    @Test
    void sumTodaySales_doneWithinRange_sumsOnlyDonePayments() {
        insertPayment("SALES-DONE-FIRST", "DONE", START.plusHours(1));
        insertPayment("SALES-DONE-SECOND", "DONE", START.plusHours(2));
        insertPayment("SALES-READY", "READY", null);
        insertPayment("SALES-CANCELED", "CANCELED", START.plusHours(3));
        insertPayment("SALES-PARTIAL", "PARTIAL_CANCELED", START.plusHours(4));
        insertPayment("SALES-ABORTED", "ABORTED", START.plusHours(5));
        insertPayment("SALES-EXPIRED", "EXPIRED", START.plusHours(6));

        var sales = dashboardReadModelMapper.sumTodaySales(START, END);

        assertThat(sales).isEqualByComparingTo("80000");
    }

    @Test
    void sumTodaySales_dateBoundary_includesStartAndExcludesEnd() {
        insertPayment("SALES-AT-START", "DONE", START);
        insertPayment("SALES-BEFORE-START", "DONE", START.minusNanos(1_000));
        insertPayment("SALES-AT-END", "DONE", END);

        var sales = dashboardReadModelMapper.sumTodaySales(START, END);

        assertThat(sales).isEqualByComparingTo("40000");
    }

    @Test
    void sumTodaySales_noMatchingPayments_returnsZero() {
        var sales = dashboardReadModelMapper.sumTodaySales(START, END);

        assertThat(sales).isZero();
    }

    @Test
    void countApprovalPendingCustomOrders_typeAndStatus_countsOnlyCustomUnderReview() {
        insertOrder("APPROVAL-FIRST", "CUSTOM", "UNDER_REVIEW", END.plusDays(1));
        insertOrder("APPROVAL-SECOND", "CUSTOM", "UNDER_REVIEW", END.plusDays(2));
        insertOrder("GENERAL-REVIEW", "GENERAL", "UNDER_REVIEW", END.plusDays(3));
        insertOrder("CUSTOM-PRODUCTION", "CUSTOM", "IN_PRODUCTION", END.plusDays(4));

        long count = dashboardReadModelMapper.countApprovalPendingCustomOrders();

        assertThat(count).isEqualTo(2);
    }

    @Test
    void countApprovalPendingCustomOrders_noMatchingOrders_returnsZero() {
        assertThat(dashboardReadModelMapper.countApprovalPendingCustomOrders()).isZero();
    }

    @Test
    void countApprovalPendingCustomOrders_requestedCancellation_excludesOrder() {
        insertOrder("APPROVAL-ACTIONABLE", "CUSTOM", "UNDER_REVIEW", END.plusDays(1));
        long requestedOrderId =
                insertOrder("APPROVAL-REFUND", "CUSTOM", "UNDER_REVIEW", END.plusDays(2));
        long requestedPaymentId = insertPaymentForOrder(
                requestedOrderId,
                "APPROVAL-REFUND",
                "DONE",
                START.plusHours(1)
        );
        insertCancellation(
                requestedPaymentId,
                "APPROVAL-REFUND",
                "CUSTOMER",
                "REQUESTED",
                null
        );

        assertThat(dashboardReadModelMapper.countApprovalPendingCustomOrders()).isEqualTo(1L);
    }

    @Test
    void countPaymentsRequiringAttention_statusOrCancellation_countsDistinctPayments() {
        insertPayment("ATTENTION-ABORTED", "ABORTED", null);
        insertPayment("ATTENTION-EXPIRED", "EXPIRED", null);
        long checkedExpiredPaymentId =
                insertPayment("CHECKED-EXPIRED", "EXPIRED", null);
        jdbcTemplate.update(
                "UPDATE payments SET expiration_checked_at = CURRENT_TIMESTAMP(6) WHERE id = ?",
                checkedExpiredPaymentId
        );

        long requestedPaymentId =
                insertPayment("ATTENTION-REQUESTED", "DONE", START.plusHours(1));
        insertCancellation(
                requestedPaymentId,
                "ATTENTION-REQUESTED",
                "ADMIN",
                "REQUESTED",
                null
        );

        long failedPaymentId =
                insertPayment("ATTENTION-FAILED", "DONE", START.plusHours(2));
        insertCancellation(
                failedPaymentId,
                "ATTENTION-FAILED",
                "ADMIN",
                "FAILED",
                "PROVIDER_ERROR"
        );

        long duplicatePaymentId =
                insertPayment("ATTENTION-DUPLICATE", "DONE", START.plusHours(3));
        insertCancellation(
                duplicatePaymentId,
                "ATTENTION-DUPLICATE-FAILED",
                "ADMIN",
                "FAILED",
                "PROVIDER_ERROR"
        );
        insertCancellation(
                duplicatePaymentId,
                "ATTENTION-DUPLICATE-REQUESTED",
                "ADMIN",
                "REQUESTED",
                null
        );

        insertPayment("NO-ATTENTION-DONE", "DONE", START.plusHours(4));
        long completedCancellationPaymentId =
                insertPayment("NO-ATTENTION-CANCELED", "DONE", START.plusHours(5));
        insertCancellation(
                completedCancellationPaymentId,
                "NO-ATTENTION-CANCELED",
                "ADMIN",
                "DONE",
                null
        );

        long count = dashboardReadModelMapper.countPaymentsRequiringAttention();

        assertThat(count).isEqualTo(5L);
    }

    @Test
    void countPaymentsRequiringAttention_completedCompensationFailure_excludesRecord() {
        long excludedPaymentId =
                insertPayment("COMP-EXCLUDED", "DONE", START.plusHours(1));
        insertCancellation(
                excludedPaymentId,
                "COMP-EXCLUDED",
                "SYSTEM_COMPENSATION",
                "FAILED",
                "PAYMENT_COMPLETED"
        );

        long includedPaymentId =
                insertPayment("COMP-INCLUDED", "DONE", START.plusHours(2));
        insertCancellation(
                includedPaymentId,
                "COMP-INCLUDED",
                "SYSTEM_COMPENSATION",
                "FAILED",
                "PAYMENT_NOT_APPROVED"
        );

        long count = dashboardReadModelMapper.countPaymentsRequiringAttention();

        assertThat(count).isEqualTo(1L);
    }

    @Test
    void countPaymentsRequiringAttention_noMatchingPayments_returnsZero() {
        long count = dashboardReadModelMapper.countPaymentsRequiringAttention();

        assertThat(count).isZero();
    }

    @Test
    void countCustomOrdersInProduction_typeAndStatus_countsOnlyCustomInProduction() {
        insertOrder("PRODUCTION-FIRST", "CUSTOM", "IN_PRODUCTION", END.plusDays(1));
        insertOrder("PRODUCTION-SECOND", "CUSTOM", "IN_PRODUCTION", END.plusDays(2));
        insertOrder("GENERAL-PRODUCTION", "GENERAL", "IN_PRODUCTION", END.plusDays(3));
        insertOrder("CUSTOM-READY", "CUSTOM", "READY_FOR_PICKUP", END.plusDays(4));

        long count = dashboardReadModelMapper.countCustomOrdersInProduction();

        assertThat(count).isEqualTo(2);
    }

    @Test
    void countCustomOrdersInProduction_noMatchingOrders_returnsZero() {
        assertThat(dashboardReadModelMapper.countCustomOrdersInProduction()).isZero();
    }

    @Test
    void countCustomOrdersInProduction_requestedCancellation_excludesOrder() {
        insertOrder("PRODUCTION-ACTIONABLE", "CUSTOM", "IN_PRODUCTION", END.plusDays(1));
        long requestedOrderId =
                insertOrder("PRODUCTION-REFUND", "CUSTOM", "IN_PRODUCTION", END.plusDays(2));
        long requestedPaymentId = insertPaymentForOrder(
                requestedOrderId,
                "PRODUCTION-REFUND",
                "DONE",
                START.plusHours(1)
        );
        insertCancellation(
                requestedPaymentId,
                "PRODUCTION-REFUND",
                "ADMIN",
                "REQUESTED",
                null
        );

        assertThat(dashboardReadModelMapper.countCustomOrdersInProduction()).isEqualTo(1L);
    }

    @Test
    void countTodayPickups_dateAndStatus_countsOnlyReadyForPickupOrders() {
        insertPickupOrder("PICKUP-START", "GENERAL", "READY_FOR_PICKUP", START, "시작 상품");
        insertPickupOrder(
                "PICKUP-CUSTOM",
                "CUSTOM",
                "READY_FOR_PICKUP",
                START.plusHours(1),
                "주문제작 상품"
        );
        insertPickupOrder(
                "PICKUP-BEFORE",
                "GENERAL",
                "READY_FOR_PICKUP",
                START.minusNanos(1_000),
                "이전 상품"
        );
        insertPickupOrder("PICKUP-END", "GENERAL", "READY_FOR_PICKUP", END, "종료 상품");
        insertPickupOrder(
                "PICKUP-REVIEW",
                "CUSTOM",
                "UNDER_REVIEW",
                START.plusHours(2),
                "검토 상품"
        );
        insertPickupOrder(
                "PICKUP-DONE",
                "GENERAL",
                "PICKED_UP",
                START.plusHours(3),
                "완료 상품"
        );
        insertPickupOrder(
                "PICKUP-CANCELED",
                "GENERAL",
                "CANCELED",
                START.plusHours(4),
                "취소 상품"
        );

        long count = dashboardReadModelMapper.countTodayPickups(START, END);

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void findTodayPickupSchedules_readyOrders_returnsSortedLimitedSummaries() {
        long eleven = insertPickupOrder(
                "SCHEDULE-11",
                "GENERAL",
                "READY_FOR_PICKUP",
                PICKUP_QUERY_REFERENCE_AT.plusHours(1),
                "열한시 상품"
        );
        long nine = insertPickupOrder(
                "SCHEDULE-09",
                "GENERAL",
                "READY_FOR_PICKUP",
                PICKUP_QUERY_REFERENCE_AT.minusNanos(1_000),
                "딸기 케이크",
                "초코 케이크"
        );
        long tenFirst = insertPickupOrder(
                "SCHEDULE-10-A",
                "GENERAL",
                "READY_FOR_PICKUP",
                PICKUP_QUERY_REFERENCE_AT,
                "열시 첫 상품"
        );
        long tenSecond = insertPickupOrder(
                "SCHEDULE-10-B",
                "CUSTOM",
                "READY_FOR_PICKUP",
                PICKUP_QUERY_REFERENCE_AT,
                "열시 두 번째 상품"
        );
        long twelve = insertPickupOrder(
                "SCHEDULE-12",
                "GENERAL",
                "READY_FOR_PICKUP",
                PICKUP_QUERY_REFERENCE_AT.plusHours(1).plusNanos(1_000),
                "열두시 상품"
        );
        insertPickupOrder(
                "SCHEDULE-13",
                "GENERAL",
                "READY_FOR_PICKUP",
                START.plusHours(13),
                "열세시 상품"
        );

        List<TodayPickupScheduleView> schedules =
                dashboardReadModelMapper.findTodayPickupSchedules(
                        START,
                        END,
                        PICKUP_QUERY_REFERENCE_AT,
                        5
                );

        assertThat(schedules)
                .extracting(TodayPickupScheduleView::orderId)
                .containsExactly(nine, tenFirst, tenSecond, eleven, twelve);
        assertThat(schedules.getFirst().productName()).isEqualTo("딸기 케이크 외 1개");
        assertThat(schedules)
                .extracting(TodayPickupScheduleView::urgency)
                .containsExactly(
                        PickupUrgency.OVERDUE,
                        PickupUrgency.IMMINENT,
                        PickupUrgency.IMMINENT,
                        PickupUrgency.IMMINENT,
                        PickupUrgency.SCHEDULED
                );
        assertThat(schedules.getFirst().urgency().label()).isEqualTo("지연");
    }

    @Test
    void findTodayPickupSchedules_noMatchingOrders_returnsEmptyResults() {
        insertPickupOrder(
                "NO-SCHEDULE",
                "CUSTOM",
                "UNDER_REVIEW",
                START.plusHours(1),
                "대상 아님"
        );

        long count = dashboardReadModelMapper.countTodayPickups(START, END);
        List<TodayPickupScheduleView> schedules =
                dashboardReadModelMapper.findTodayPickupSchedules(
                        START,
                        END,
                        PICKUP_QUERY_REFERENCE_AT,
                        5
                );

        assertThat(count).isZero();
        assertThat(schedules).isEmpty();
    }

    @Test
    void findRecentOrders_allStatuses_returnsLatestFiveInOrder() {
        insertRecentOrder(
                "RECENT-PENDING",
                "PENDING_PAYMENT",
                START.plusHours(1),
                "결제 대기 상품"
        );
        insertRecentOrder(
                "RECENT-REVIEW",
                "UNDER_REVIEW",
                START.plusHours(2),
                "승인 대기 상품"
        );
        long ready = insertRecentOrder(
                "RECENT-READY",
                "READY_FOR_PICKUP",
                START.plusHours(3),
                "픽업 준비 상품"
        );
        long pickedUp = insertRecentOrder(
                "RECENT-PICKED",
                "PICKED_UP",
                START.plusHours(4),
                "픽업 완료 상품"
        );
        long rejected = insertRecentOrder(
                "RECENT-REJECTED",
                "REJECTED",
                START.plusHours(5),
                "반려 상품"
        );
        long canceled = insertRecentOrder(
                "RECENT-CANCELED",
                "CANCELED",
                START.plusHours(6),
                "취소 상품"
        );
        long expired = insertRecentOrder(
                "RECENT-EXPIRED",
                "EXPIRED",
                START.plusHours(6),
                "만료 상품",
                "추가 상품"
        );

        List<RecentOrderView> allStatuses = dashboardReadModelMapper.findRecentOrders(10);
        List<RecentOrderView> latestFive = dashboardReadModelMapper.findRecentOrders(5);

        assertThat(allStatuses)
                .extracting(RecentOrderView::status)
                .containsExactly(
                        OrderStatus.EXPIRED,
                        OrderStatus.CANCELED,
                        OrderStatus.REJECTED,
                        OrderStatus.PICKED_UP,
                        OrderStatus.READY_FOR_PICKUP,
                        OrderStatus.UNDER_REVIEW,
                        OrderStatus.PENDING_PAYMENT
                );
        assertThat(latestFive)
                .extracting(RecentOrderView::orderId)
                .containsExactly(expired, canceled, rejected, pickedUp, ready);
        assertThat(latestFive.getFirst().productName()).isEqualTo("만료 상품 외 1개");
        assertThat(latestFive.getFirst().finalAmount()).isEqualByComparingTo("40000");
        assertThat(latestFive.getFirst().statusLabel()).isEqualTo("결제 만료");
    }

    @Test
    void findRecentOrders_noOrders_returnsEmptyResult() {
        List<RecentOrderView> recentOrders = dashboardReadModelMapper.findRecentOrders(5);

        assertThat(recentOrders).isEmpty();
    }

    @Test
    void countLowStockProducts_conditions_countsOnlyActiveGeneralFiniteStockUpToTwo() {
        insertInventoryProduct("OUT-OF-STOCK", "GENERAL", "ACTIVE", 0);
        insertInventoryProduct("ONE-LEFT", "GENERAL", "ACTIVE", 1);
        insertInventoryProduct("TWO-LEFT", "GENERAL", "ACTIVE", 2);
        insertInventoryProduct("ENOUGH-STOCK", "GENERAL", "ACTIVE", 3);
        insertInventoryProduct("UNLIMITED", "GENERAL", "ACTIVE", null);
        insertInventoryProduct("CUSTOM", "CUSTOM", "ACTIVE", 1);
        insertInventoryProduct("INACTIVE", "GENERAL", "INACTIVE", 1);

        long count = dashboardReadModelMapper.countLowStockProducts();

        assertThat(count).isEqualTo(3L);
    }

    @Test
    void findLowStockProducts_matchingProducts_returnsSortedLimitedResults() {
        long firstTwo = insertInventoryProduct("TWO-FIRST", "GENERAL", "ACTIVE", 2);
        long firstZero = insertInventoryProduct("ZERO-FIRST", "GENERAL", "ACTIVE", 0);
        long firstOne = insertInventoryProduct("ONE-FIRST", "GENERAL", "ACTIVE", 1);
        long secondZero = insertInventoryProduct("ZERO-SECOND", "GENERAL", "ACTIVE", 0);
        insertInventoryProduct("TWO-SECOND", "GENERAL", "ACTIVE", 2);
        long secondOne = insertInventoryProduct("ONE-SECOND", "GENERAL", "ACTIVE", 1);

        List<LowStockProductView> products =
                dashboardReadModelMapper.findLowStockProducts(5);

        assertThat(products)
                .extracting(LowStockProductView::productId)
                .containsExactly(firstZero, secondZero, firstOne, secondOne, firstTwo);
        assertThat(products.getFirst().productName()).contains("ZERO-FIRST");
        assertThat(products.getFirst().stockQuantity()).isZero();
    }

    @Test
    void findLowStockProducts_noMatchingProducts_returnsZeroAndEmptyResult() {
        long count = dashboardReadModelMapper.countLowStockProducts();
        List<LowStockProductView> products =
                dashboardReadModelMapper.findLowStockProducts(5);

        assertThat(count).isZero();
        assertThat(products).isEmpty();
    }

    @Test
    void countPendingReportedPosts_actionablePendingReports_countsDistinctNonDeletedPosts() {
        long firstPostId = insertPost("FIRST");
        long secondPostId = insertPost("SECOND");
        long resolvedPostId = insertPost("RESOLVED");
        long deletedPostId = insertPost("DELETED", "DELETED");
        long secondReporterId = insertReporter("SECOND");

        insertPostReport(firstPostId, memberId, "FIRST-A", "PENDING");
        insertPostReport(firstPostId, secondReporterId, "FIRST-B", "PENDING");
        insertPostReport(secondPostId, memberId, "SECOND", "PENDING");
        insertPostReport(resolvedPostId, memberId, "RESOLVED", "RESOLVED");
        insertPostReport(deletedPostId, memberId, "DELETED", "PENDING");

        long count = dashboardReadModelMapper.countPendingReportedPosts();

        assertThat(count).isEqualTo(2);
    }

    @Test
    void countPendingReportedPosts_noPendingReports_returnsZero() {
        long postId = insertPost("REJECTED");
        insertPostReport(postId, memberId, "REJECTED", "REJECTED");

        assertThat(dashboardReadModelMapper.countPendingReportedPosts()).isZero();
    }

    private long insertMember() {
        String email = "dashboard-read-model-" + suffix + "@example.com";
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
                VALUES (?, NULL, '대시보드 테스트', '대시보드', '010-0000-0000',
                        'USER', 'ACTIVE')
                """,
                email
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?",
                Long.class,
                email
        );
    }

    private long insertProduct() {
        String categoryCode = "DASHBOARD_" + suffix;
        jdbcTemplate.update(
                "INSERT INTO categories (code, name, is_active) VALUES (?, '대시보드', 1)",
                categoryCode
        );
        categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE code = ?",
                Long.class,
                categoryCode
        );
        String productName = "대시보드 상품 " + suffix;
        jdbcTemplate.update(
                """
                INSERT INTO products (
                    category_id,
                    name,
                    description,
                    base_price,
                    product_type,
                    preparation_days,
                    status
                )
                VALUES (?, ?, '', 40000, 'GENERAL', 0, 'ACTIVE')
                """,
                categoryId,
                productName
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE name = ?",
                Long.class,
                productName
        );
    }

    private long insertPostCategory() {
        String code = "DASHBOARD_POST_" + suffix;
        jdbcTemplate.update(
                "INSERT INTO post_categories (code, name) VALUES (?, '대시보드 게시글')",
                code
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertReporter(String label) {
        String email = "dashboard-reporter-" + label + "-" + suffix + "@example.com";
        jdbcTemplate.update(
                """
                INSERT INTO members (
                    email, password, name, nickname, phone, role, status
                )
                VALUES (?, NULL, '대시보드 신고자', ?, '010-9999-9999', 'USER', 'ACTIVE')
                """,
                email,
                "신고자-" + label + "-" + suffix
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertPost(String label) {
        return insertPost(label, "PUBLISHED");
    }

    private long insertPost(String label, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO posts (
                    member_id, category_id, title, content, status
                )
                VALUES (?, ?, ?, '대시보드 신고 집계 테스트', ?)
                """,
                memberId,
                postCategoryId,
                "대시보드 게시글 " + label + " " + suffix,
                status
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertPostReport(
            long postId,
            long reporterId,
            String label,
            String status
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO post_reports (post_id, reporter_id, reason, status)
                VALUES (?, ?, ?, ?)
                """,
                postId,
                reporterId,
                "대시보드 신고 " + label,
                status
        );
    }

    private long insertInventoryProduct(
            String label,
            String productType,
            String status,
            Integer stockQuantity
    ) {
        String productName = "재고 상품 " + label + " " + suffix;
        int preparationDays = "CUSTOM".equals(productType) ? 1 : 0;
        jdbcTemplate.update(
                """
                INSERT INTO products (
                    category_id,
                    name,
                    description,
                    base_price,
                    stock_quantity,
                    product_type,
                    preparation_days,
                    status
                )
                VALUES (?, ?, '', 40000, ?, ?, ?, ?)
                """,
                categoryId,
                productName,
                stockQuantity,
                productType,
                preparationDays,
                status
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM products WHERE name = ?",
                Long.class,
                productName
        );
    }

    private long insertOrder(String label) {
        return insertOrder(
                label,
                "GENERAL",
                "PENDING_PAYMENT",
                END.plusDays(1),
                START
        );
    }

    private long insertOrder(
            String label,
            String orderType,
            String status,
            LocalDateTime pickupAt
    ) {
        return insertOrder(label, orderType, status, pickupAt, START);
    }

    private long insertOrder(
            String label,
            String orderType,
            String status,
            LocalDateTime pickupAt,
            LocalDateTime createdAt
    ) {
        String orderNumber = "DASHBOARD-" + label + "-" + suffix;
        String rejectReason = "REJECTED".equals(status) ? "대시보드 테스트 반려" : null;
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
                VALUES (?, ?, ?, '주문자', '010-1111-2222',
                        '수령자', '010-3333-4444', 40000, 0, 40000,
                        ?, ?, ?, ?, ?)
                """,
                orderNumber,
                memberId,
                orderType,
                status,
                pickupAt,
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

    private long insertPickupOrder(
            String label,
            String orderType,
            String status,
            LocalDateTime pickupAt,
            String... productNames
    ) {
        long orderId = insertOrder(label, orderType, status, pickupAt);
        for (String productName : productNames) {
            insertOrderItem(orderId, orderType, productName);
        }
        return orderId;
    }

    private long insertRecentOrder(
            String label,
            String status,
            LocalDateTime createdAt,
            String... productNames
    ) {
        long orderId = insertOrder(
                label,
                "GENERAL",
                status,
                END.plusDays(1),
                createdAt
        );
        for (String productName : productNames) {
            insertOrderItem(orderId, "GENERAL", productName);
        }
        return orderId;
    }

    private void insertOrderItem(
            long orderId,
            String productType,
            String productName
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
                    preparation_days
                )
                VALUES (?, ?, ?, ?, 1, 40000, 0, 40000, 0)
                """,
                orderId,
                productId,
                productName,
                productType
        );
    }

    private long insertPayment(
            String label,
            String status,
            LocalDateTime approvedAt
    ) {
        long orderId = insertOrder(label);
        return insertPaymentForOrder(orderId, label, status, approvedAt);
    }

    private long insertPaymentForOrder(
            long orderId,
            String label,
            String status,
            LocalDateTime approvedAt
    ) {
        String tossOrderId = "TOSS-" + label + "-" + suffix;
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
                    approved_at
                )
                VALUES (?, ?, ?, ?, 'CARD', 40000, ?, ?)
                """,
                orderId,
                tossOrderId,
                "PAYMENT-KEY-" + label + "-" + suffix,
                "IDEMPOTENCY-" + label + "-" + suffix,
                status,
                approvedAt
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM payments WHERE toss_order_id = ?",
                Long.class,
                tossOrderId
        );
    }

    private void insertCancellation(
            long paymentId,
            String label,
            String requestType,
            String status,
            String failureCode
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO payment_cancellations (
                    payment_id,
                    idempotency_key,
                    cancel_amount,
                    cancel_reason,
                    request_type,
                    status,
                    failure_code
                )
                VALUES (?, ?, 40000, '대시보드 테스트', ?, ?, ?)
                """,
                paymentId,
                "CANCEL-" + label + "-" + suffix,
                requestType,
                status,
                failureCode
        );
    }
}
