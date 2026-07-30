package com.cakeshop.domain.payment.mapper;

import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentCancellation;
import com.cakeshop.domain.payment.entity.PaymentCancellationStatus;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PaymentMapper가 실제 DB에서 결제를 저장·조회·조건부 변경하는지 확인한다.
 * JdbcTemplate은 결제의 FK 부모인 회원과 주문 준비에만 사용한다.
 */
@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// 각 테스트가 끝나면 JdbcTemplate과 Mapper가 저장한 데이터를 함께 롤백한다.
@Transactional
class PaymentMapperTests {

    private final PaymentMapper paymentMapper;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    PaymentMapperTests(PaymentMapper paymentMapper, JdbcTemplate jdbcTemplate) {
        this.paymentMapper = paymentMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    private String suffix;
    private long memberId;
    private long productId;
    private long orderId;

    @BeforeEach
    void setUp() {
        suffix = Long.toString(System.nanoTime());
        memberId = insertMember();
        productId = insertProduct();
        orderId = insertOrder();
        insertOrderItem();
    }

    // READY 결제를 INSERT한 뒤 주문 ID로 다시 조회한다.
    @Test
    void insertReadyPaymentAndFindPaymentsByOrderId() {
        Payment payment = newPayment("READY");

        assertThat(paymentMapper.insertReadyPayment(payment)).isEqualTo(1);
        assertThat(payment.getId()).isNotNull();

        List<Payment> payments =
                paymentMapper.findPaymentsByOrderId(orderId);

        assertThat(payments)
                .extracting(Payment::getId)
                .containsExactly(payment.getId());
        assertThat(payments)
                .extracting(Payment::getStatus)
                .containsOnly(PaymentStatus.READY);
        assertThat(payments)
                .extracting(Payment::getTossOrderId)
                .containsExactly(payment.getTossOrderId());
        assertThat(payments)
                .extracting(Payment::getActiveReadyOrderId)
                .containsExactly(orderId);
    }

    // 한 주문에서 READY 결제는 UNIQUE 제약에 따라 한 건만 허용되는지 확인한다.
    @Test
    void onlyOneReadyPaymentIsAllowedPerOrder() {
        insertPayment("FIRST-READY");

        assertThatThrownBy(() -> insertPayment("SECOND-READY"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void completeIfReady_recordsApprovalDataConditionally() {
        Payment payment = insertPayment("READY-TO-DONE");
        LocalDateTime approvedAt =
                LocalDateTime.of(2026, 8, 1, 12, 1);

        assertThat(paymentMapper.completeIfReady(
                payment.getId(),
                "PAYMENT-KEY-" + suffix,
                "CARD",
                "DONE",
                approvedAt
        )).isEqualTo(1);

        Payment completed = findPayment(payment.getId());
        assertThat(completed.getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(completed.getPaymentKey()).isEqualTo("PAYMENT-KEY-" + suffix);
        assertThat(completed.getMethod()).isEqualTo("CARD");
        assertThat(completed.getProviderStatus()).isEqualTo("DONE");
        assertThat(completed.getApprovedAt()).isEqualTo(approvedAt);
        assertThat(completed.getFailureCode()).isNull();
        assertThat(completed.getFailureMessage()).isNull();

        assertThat(paymentMapper.completeIfReady(
                payment.getId(),
                "OTHER-PAYMENT-KEY-" + suffix,
                "TRANSFER",
                "DONE",
                approvedAt.plusMinutes(1)
        )).isZero();
    }

    @Test
    void cancelIfDone_recordsProviderStatusAndTimeConditionally() {
        Payment payment = insertPayment("DONE-TO-CANCELED");
        LocalDateTime approvedAt =
                LocalDateTime.of(2026, 8, 1, 12, 1);
        LocalDateTime canceledAt =
                LocalDateTime.of(2026, 8, 1, 12, 5);
        paymentMapper.completeIfReady(
                payment.getId(),
                "PAYMENT-KEY-CANCEL-" + suffix,
                "CARD",
                "DONE",
                approvedAt
        );

        assertThat(paymentMapper.cancelIfDone(
                payment.getId(),
                "CANCELED",
                canceledAt
        )).isEqualTo(1);
        Payment canceled = findPayment(payment.getId());
        assertThat(canceled.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(canceled.getProviderStatus()).isEqualTo("CANCELED");
        assertThat(canceled.getCanceledAt()).isEqualTo(canceledAt);
        assertThat(paymentMapper.cancelIfDone(
                payment.getId(),
                "CANCELED",
                canceledAt.plusMinutes(1)
        )).isZero();
    }

    @Test
    void cancelIfDone_doesNotCancelReadyPayment() {
        Payment payment = insertPayment("READY-CANNOT-CANCEL");

        assertThat(paymentMapper.cancelIfDone(
                payment.getId(),
                "CANCELED",
                LocalDateTime.of(2026, 8, 1, 12, 5)
        )).isZero();
        assertThat(findPayment(payment.getId()).getStatus())
                .isEqualTo(PaymentStatus.READY);
    }

    @Test
    void abortIfReady_recordsFailureDataConditionally() {
        Payment payment = insertPayment("READY-TO-ABORTED");

        assertThat(paymentMapper.abortIfReady(
                payment.getId(),
                "ABORTED",
                "PAY_PROCESS_CANCELED",
                "사용자가 결제를 중단했습니다."
        )).isEqualTo(1);

        Payment aborted = findPayment(payment.getId());
        assertThat(aborted.getStatus()).isEqualTo(PaymentStatus.ABORTED);
        assertThat(aborted.getProviderStatus()).isEqualTo("ABORTED");
        assertThat(aborted.getFailureCode()).isEqualTo("PAY_PROCESS_CANCELED");
        assertThat(aborted.getFailureMessage()).isEqualTo("사용자가 결제를 중단했습니다.");
        assertThat(paymentMapper.abortIfReady(
                payment.getId(),
                "ABORTED",
                "OTHER",
                "다시 실패 처리"
        )).isZero();
    }

    @Test
    void expireIfReady_recordsFailureDataConditionally() {
        Payment payment = insertPayment("READY-TO-EXPIRED");

        assertThat(paymentMapper.expireIfReady(
                payment.getId(),
                "EXPIRED",
                "PAYMENT_TIMEOUT",
                "결제 유효 시간이 지났습니다."
        )).isEqualTo(1);

        Payment expired = findPayment(payment.getId());
        assertThat(expired.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(expired.getProviderStatus()).isEqualTo("EXPIRED");
        assertThat(expired.getFailureCode()).isEqualTo("PAYMENT_TIMEOUT");
        assertThat(expired.getFailureMessage()).isEqualTo("결제 유효 시간이 지났습니다.");
    }

    // 한 주문에서 DONE 결제는 UNIQUE 제약에 따라 한 건만 허용되는지 확인한다.
    @Test
    void onlyOneDonePaymentIsAllowedPerOrder() {
        Payment firstPayment = insertPayment("FIRST-DONE");

        assertThat(paymentMapper.completeIfReady(
                firstPayment.getId(),
                "PAYMENT-KEY-FIRST-DONE-" + suffix,
                "CARD",
                "DONE",
                LocalDateTime.of(2026, 8, 1, 12, 1)
        )).isEqualTo(1);

        Payment secondPayment = insertPayment("SECOND-DONE");

        // 두 번째 결제도 DONE이 되면 같은 order_id가 생성 열에 중복되므로 DB가 거부한다.
        assertThatThrownBy(() -> paymentMapper.completeIfReady(
                secondPayment.getId(),
                "PAYMENT-KEY-SECOND-DONE-" + suffix,
                "CARD",
                "DONE",
                LocalDateTime.of(2026, 8, 1, 12, 2)
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(findPayment(firstPayment.getId()).getStatus())
                .isEqualTo(PaymentStatus.DONE);
        assertThat(findPayment(secondPayment.getId()).getStatus())
                .isEqualTo(PaymentStatus.READY);
    }

    // Java enum에 없는 상태값을 직접 저장해도 DB CHECK 제약이 거부하는지 확인한다.
    @Test
    void invalidPaymentStatusCannotBeStored() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO payments (
                    order_id,
                    toss_order_id,
                    idempotency_key,
                    amount,
                    status
                )
                VALUES (?, ?, ?, 40000, 'INVALID_STATUS')
                """,
                orderId,
                "TOSS-INVALID-" + suffix,
                "IDEMPOTENCY-INVALID-" + suffix
        )).isInstanceOf(DataIntegrityViolationException.class);

        Integer savedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE toss_order_id = ?",
                Integer.class,
                "TOSS-INVALID-" + suffix
        );
        assertThat(savedCount).isZero();
    }

    @Test
    void insertPaymentCancellationAndFindPaymentCancellationById() {
        Payment payment = insertPayment("CANCELLATION-MAPPER");
        PaymentCancellation cancellation =
                newPaymentCancellation(payment.getId(), "MAPPER");

        assertThat(paymentMapper.insertPaymentCancellation(cancellation)).isEqualTo(1);
        assertThat(cancellation.getId()).isNotNull();

        PaymentCancellation saved = paymentMapper
                .findPaymentCancellationById(cancellation.getId())
                .orElseThrow();

        assertThat(saved.getPaymentId()).isEqualTo(payment.getId());
        assertThat(saved.getIdempotencyKey()).isEqualTo(cancellation.getIdempotencyKey());
        assertThat(saved.getCancelAmount()).isEqualByComparingTo(cancellation.getCancelAmount());
        assertThat(saved.getCancelReason()).isEqualTo(cancellation.getCancelReason());
        assertThat(saved.getRequestType()).isEqualTo(cancellation.getRequestType());
        assertThat(saved.getRequestedBy()).isEqualTo(memberId);
        assertThat(saved.getStatus()).isEqualTo(PaymentCancellationStatus.REQUESTED);
        assertThat(saved.getActiveRequestedPaymentId()).isEqualTo(payment.getId());
        assertThat(saved.getRequestedAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void completeCancellationIfRequested_recordsTransactionAndTimeConditionally() {
        Payment payment = insertPayment("CANCELLATION-STATUS");
        PaymentCancellation cancellation =
                newPaymentCancellation(payment.getId(), "STATUS");
        paymentMapper.insertPaymentCancellation(cancellation);
        LocalDateTime canceledAt =
                LocalDateTime.of(2026, 8, 1, 12, 5);

        assertThat(paymentMapper.completeCancellationIfRequested(
                cancellation.getId(),
                "CANCEL-TRANSACTION-" + suffix,
                canceledAt
        )).isEqualTo(1);

        PaymentCancellation completed = paymentMapper
                .findPaymentCancellationById(cancellation.getId())
                .orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(PaymentCancellationStatus.DONE);
        assertThat(completed.getActiveRequestedPaymentId()).isNull();
        assertThat(completed.getTransactionKey())
                .isEqualTo("CANCEL-TRANSACTION-" + suffix);
        assertThat(completed.getCanceledAt()).isEqualTo(canceledAt);
        assertThat(completed.getFailureCode()).isNull();
        assertThat(completed.getFailureMessage()).isNull();
        assertThat(paymentMapper.completeCancellationIfRequested(
                cancellation.getId(),
                "OTHER-TRANSACTION-" + suffix,
                canceledAt.plusMinutes(1)
        )).isZero();
    }

    @Test
    void failCancellationIfRequested_recordsFailureDataConditionally() {
        Payment payment = insertPayment("CANCELLATION-FAILURE");
        PaymentCancellation cancellation =
                newPaymentCancellation(payment.getId(), "FAILURE");
        paymentMapper.insertPaymentCancellation(cancellation);

        assertThat(paymentMapper.failCancellationIfRequested(
                cancellation.getId(),
                "ALREADY_CANCELED",
                "이미 취소된 결제입니다."
        )).isEqualTo(1);

        PaymentCancellation failed = paymentMapper
                .findPaymentCancellationById(cancellation.getId())
                .orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(PaymentCancellationStatus.FAILED);
        assertThat(failed.getActiveRequestedPaymentId()).isNull();
        assertThat(failed.getFailureCode()).isEqualTo("ALREADY_CANCELED");
        assertThat(failed.getFailureMessage()).isEqualTo("이미 취소된 결제입니다.");
        assertThat(paymentMapper.failCancellationIfRequested(
                cancellation.getId(),
                "OTHER",
                "다시 실패 처리"
        )).isZero();
    }

    @Test
    void onlyOneRequestedCancellationIsAllowedPerPayment() {
        Payment payment = insertPayment("CANCELLATION");

        assertThat(insertRequestedCancellation(payment.getId(), "FIRST", 40_000, "REQUESTED"))
                .isEqualTo(1);

        Integer generatedColumnAndTimestampCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM payment_cancellations
                WHERE payment_id = ?
                  AND active_requested_payment_id = ?
                  AND request_type = 'CUSTOMER_CANCEL'
                  AND requested_by = ?
                  AND updated_at IS NOT NULL
                """,
                Integer.class,
                payment.getId(),
                payment.getId(),
                memberId
        );
        assertThat(generatedColumnAndTimestampCount).isOne();

        assertThatThrownBy(() ->
                insertRequestedCancellation(payment.getId(), "SECOND", 40_000, "REQUESTED")
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invalidPaymentCancellationStatusCannotBeStored() {
        Payment payment = insertPayment("INVALID-CANCELLATION-STATUS");

        assertThatThrownBy(() ->
                insertRequestedCancellation(payment.getId(), "INVALID-STATUS", 40_000, "INVALID")
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nonPositiveCancellationAmountCannotBeStored() {
        Payment payment = insertPayment("INVALID-CANCELLATION-AMOUNT");

        assertThatThrownBy(() ->
                insertRequestedCancellation(payment.getId(), "ZERO-AMOUNT", 0, "REQUESTED")
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Payment insertPayment(String label) {
        Payment payment = newPayment(label);
        paymentMapper.insertReadyPayment(payment);
        return payment;
    }

    private Payment newPayment(String label) {
        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setTossOrderId("TOSS-" + label + "-" + suffix);
        payment.setIdempotencyKey("IDEMPOTENCY-" + label + "-" + suffix);
        payment.setAmount(BigDecimal.valueOf(40_000));
        return payment;
    }

    private PaymentCancellation newPaymentCancellation(long paymentId, String label) {
        PaymentCancellation cancellation = new PaymentCancellation();
        cancellation.setPaymentId(paymentId);
        cancellation.setIdempotencyKey("CANCEL-MAPPER-" + label + "-" + suffix);
        cancellation.setCancelAmount(BigDecimal.valueOf(40_000));
        cancellation.setCancelReason("테스트 환불");
        cancellation.setRequestType("CUSTOMER_CANCEL");
        cancellation.setRequestedBy(memberId);
        return cancellation;
    }

    private int insertRequestedCancellation(
            long paymentId,
            String label,
            long cancelAmount,
            String status
    ) {
        return jdbcTemplate.update(
                """
                INSERT INTO payment_cancellations (
                    payment_id,
                    idempotency_key,
                    cancel_amount,
                    cancel_reason,
                    request_type,
                    requested_by,
                    status
                )
                VALUES (?, ?, ?, '테스트 환불', 'CUSTOMER_CANCEL', ?, ?)
                """,
                paymentId,
                "CANCEL-" + label + "-" + suffix,
                cancelAmount,
                memberId,
                status
        );
    }

    private Payment findPayment(long paymentId) {
        return paymentMapper.findPaymentsByOrderId(orderId).stream()
                .filter(payment -> payment.getId() == paymentId)
                .findFirst()
                .orElseThrow();
    }

    // 각 테스트가 기존 DB 데이터에 의존하지 않도록 회원을 직접 준비한다.
    private long insertMember() {
        String email = "payment-mapper-" + suffix + "@example.com";
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
                VALUES (?, NULL, ?, ?, ?, 'USER', 'ACTIVE')
                """,
                email,
                "결제 테스트 회원",
                "결제테스트",
                "010-0000-0000"
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?",
                Long.class,
                email
        );
    }

    // 주문 항목 FK에 필요한 카테고리와 상품을 직접 준비한다.
    private long insertProduct() {
        String categoryCode = "PAYMENT_MAPPER_" + suffix;
        jdbcTemplate.update(
                """
                INSERT INTO categories (
                    code,
                    name,
                    sort_order,
                    is_active
                )
                VALUES (?, ?, 999, 1)
                """,
                categoryCode,
                "결제 Mapper 테스트"
        );

        long categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE code = ?",
                Long.class,
                categoryCode
        );

        String productName = "결제 Mapper 상품 " + suffix;
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

    // payments.order_id FK에 필요한 주문을 직접 준비한다.
    private long insertOrder() {
        String orderNumber = "PAYMENT-MAPPER-" + suffix;
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
                    payment_expires_at
                )
                VALUES (?, ?, 'GENERAL', ?, ?, ?, ?, 40000, 0, 40000,
                        'PENDING_PAYMENT',
                        CURRENT_TIMESTAMP(6) + INTERVAL 1 DAY,
                        CURRENT_TIMESTAMP(6) + INTERVAL 10 MINUTE)
                """,
                orderNumber,
                memberId,
                "주문자",
                "010-1111-2222",
                "수령자",
                "010-3333-4444"
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM orders WHERE order_number = ?",
                Long.class,
                orderNumber
        );
    }

    // 결제 대상 주문에 포함된 상품도 매 테스트마다 직접 준비한다.
    private void insertOrderItem() {
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
                VALUES (?, ?, ?, 'GENERAL', 1, 40000, 0, 40000, 0, 0)
                """,
                orderId,
                productId,
                "결제 Mapper 상품 " + suffix
        );
    }
}
