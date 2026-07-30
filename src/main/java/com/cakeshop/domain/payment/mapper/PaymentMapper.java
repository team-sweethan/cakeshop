package com.cakeshop.domain.payment.mapper;

import com.cakeshop.domain.payment.entity.Payment;
import com.cakeshop.domain.payment.entity.PaymentStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PaymentMapper {

    // 결제 시도 생성. XML에서 상태를 READY로 고정한다.
    int insertReadyPayment(Payment payment);

    // 한 주문에서 발생한 모든 결제 시도를 조회한다.
    List<Payment> findPaymentsByOrderId(@Param("orderId") long orderId);

    // DB의 현재 상태가 expectedStatus일 때만 nextStatus로 변경한다.
    int updateStatusIfCurrent(
            @Param("paymentId") long paymentId,
            @Param("expectedStatus") PaymentStatus expectedStatus,
            @Param("nextStatus") PaymentStatus nextStatus
    );
}
