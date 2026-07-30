package com.cakeshop.domain.payment.mapper;

import com.cakeshop.domain.payment.entity.Payment;
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
        Payment firstPayment = newPayment("FIRST");
        Payment secondPayment = newPayment("SECOND");

        assertThat(paymentMapper.insertReadyPayment(firstPayment)).isEqualTo(1);
        assertThat(paymentMapper.insertReadyPayment(secondPayment)).isEqualTo(1);
        assertThat(firstPayment.getId()).isNotNull();
        assertThat(secondPayment.getId()).isNotNull();

        List<Payment> payments =
                paymentMapper.findPaymentsByOrderId(orderId);

        assertThat(payments)
                .extracting(Payment::getId)
                .containsExactly(firstPayment.getId(), secondPayment.getId());
        assertThat(payments)
                .extracting(Payment::getStatus)
                .containsOnly(PaymentStatus.READY);
        assertThat(payments)
                .extracting(Payment::getTossOrderId)
                .containsExactly(
                        firstPayment.getTossOrderId(),
                        secondPayment.getTossOrderId()
                );
    }

    // READY 상태일 때만 DONE으로 바뀌는지 확인한다.
    @Test
    void updateStatusIfCurrentChangesReadyToDoneConditionally() {
        Payment payment = insertPayment("READY-TO-DONE");

        assertThat(paymentMapper.updateStatusIfCurrent(
                payment.getId(),
                PaymentStatus.CANCELED,
                PaymentStatus.DONE
        )).isZero();
        assertThat(findPayment(payment.getId()).getStatus())
                .isEqualTo(PaymentStatus.READY);

        assertThat(paymentMapper.updateStatusIfCurrent(
                payment.getId(),
                PaymentStatus.READY,
                PaymentStatus.DONE
        )).isEqualTo(1);
        assertThat(findPayment(payment.getId()).getStatus())
                .isEqualTo(PaymentStatus.DONE);
    }

    // DONE 상태일 때만 CANCELED로 바뀌는지 확인한다.
    @Test
    void updateStatusIfCurrentChangesDoneToCanceledConditionally() {
        Payment payment = insertPayment("DONE-TO-CANCELED");
        paymentMapper.updateStatusIfCurrent(
                payment.getId(),
                PaymentStatus.READY,
                PaymentStatus.DONE
        );

        assertThat(paymentMapper.updateStatusIfCurrent(
                payment.getId(),
                PaymentStatus.READY,
                PaymentStatus.CANCELED
        )).isZero();
        assertThat(findPayment(payment.getId()).getStatus())
                .isEqualTo(PaymentStatus.DONE);

        assertThat(paymentMapper.updateStatusIfCurrent(
                payment.getId(),
                PaymentStatus.DONE,
                PaymentStatus.CANCELED
        )).isEqualTo(1);
        assertThat(findPayment(payment.getId()).getStatus())
                .isEqualTo(PaymentStatus.CANCELED);
    }

    // 한 주문에서 DONE 결제는 UNIQUE 제약에 따라 한 건만 허용되는지 확인한다.
    @Test
    void onlyOneDonePaymentIsAllowedPerOrder() {
        Payment firstPayment = insertPayment("FIRST-DONE");
        Payment secondPayment = insertPayment("SECOND-DONE");

        assertThat(paymentMapper.updateStatusIfCurrent(
                firstPayment.getId(),
                PaymentStatus.READY,
                PaymentStatus.DONE
        )).isEqualTo(1);

        // 두 번째 결제도 DONE이 되면 같은 order_id가 생성 열에 중복되므로 DB가 거부한다.
        assertThatThrownBy(() -> paymentMapper.updateStatusIfCurrent(
                secondPayment.getId(),
                PaymentStatus.READY,
                PaymentStatus.DONE
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
                  AND updated_at IS NOT NULL
                """,
                Integer.class,
                payment.getId(),
                payment.getId()
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
                    status
                )
                VALUES (?, ?, ?, '테스트 환불', ?)
                """,
                paymentId,
                "CANCEL-" + label + "-" + suffix,
                cancelAmount,
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
