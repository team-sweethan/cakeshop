package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.entity.OrderStatus;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 안내 화면에서 새 주문 작성 의도를 발급하기 전에 기존 결제 대기 주문을 검증한다. */
@Service
@RequiredArgsConstructor
public class PendingPaymentOrderGuideService {

    private final OrderMapper orderMapper;
    private final Clock clock;

    /** 인증 회원이 여전히 결제할 수 있는 자신의 주문에 대해서만 새 주문 진행 의도를 허용한다. */
    @Transactional(readOnly = true)
    public void verifyNewOrderIntentTarget(long memberId, long orderId) {
        Order order = orderMapper.findOrderById(orderId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.FORBIDDEN));
        if (!Long.valueOf(memberId).equals(order.getMemberId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                || order.getPaymentExpiresAt() == null
                || !order.getPaymentExpiresAt().isAfter(now)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
