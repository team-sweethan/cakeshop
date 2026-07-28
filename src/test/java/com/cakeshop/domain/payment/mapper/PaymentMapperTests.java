package com.cakeshop.domain.payment.mapper;

import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PaymentMapper가 실제 DB에서 결제를 저장·조회·조건부 변경하는지 확인한다.
 * JdbcTemplate은 결제의 FK 부모인 회원과 주문 준비에만 사용한다.
 */
@MybatisTest
@ActiveProfiles("local")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentMapperTests {

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String suffix;
    private long orderId;

    @BeforeEach
    void setUp() {
        suffix = Long.toString(System.nanoTime());
        orderId = insertOrder();
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

    private Payment findPayment(long paymentId) {
        return paymentMapper.findPaymentsByOrderId(orderId).stream()
                .filter(payment -> payment.getId() == paymentId)
                .findFirst()
                .orElseThrow();
    }

    // payments.order_id FK를 만족시키는 회원과 주문을 준비한다.
    private long insertOrder() {
        String email = "payment-mapper-" + suffix + "@example.com";
        jdbcTemplate.update(
                """
                INSERT INTO members (
                    email,
                    password,
                    nickname,
                    phone,
                    role,
                    status
                )
                VALUES (?, NULL, ?, ?, 'USER', 'ACTIVE')
                """,
                email,
                "결제테스트",
                "010-0000-0000"
        );

        long memberId = jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?",
                Long.class,
                email
        );

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
}
