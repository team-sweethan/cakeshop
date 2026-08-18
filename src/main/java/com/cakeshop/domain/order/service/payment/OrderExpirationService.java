package com.cakeshop.domain.order.service.payment;

import com.cakeshop.domain.coupon.service.CouponOrderCommandService;
import com.cakeshop.domain.order.mapper.OrderMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 결제 기한이 지난 주문과 READY 결제를 조건부 상태 전이로 만료 처리하고 예약 쿠폰을 해제한다. */
@Service
@RequiredArgsConstructor
public class OrderExpirationService {

    private final OrderMapper orderMapper;
    private final Clock clock;
    // 쿠폰 담당자의 공개 계약으로, 결제 만료 주문에 묶인 RESERVED 쿠폰만 해제한다.
    private final CouponOrderCommandService couponOrderCommandService;

    @Value("${app.order.expiration.batch-size:100}")
    private int batchSize;

    /**
     * 한 번에 제한된 수만 처리한다. 각 UPDATE가 현재 상태와 결제 상태를 다시 확인하므로
     * Toss 승인과 경합해 이미 DONE이 된 결제는 EXPIRED로 덮어쓰지 않는다.
     */
    @Transactional
    public int expireOverdueOrders() {
        LocalDateTime now = LocalDateTime.now(clock);
        int safeBatchSize = Math.max(1, batchSize);
        return orderMapper.findOverduePendingOrderIds(now, safeBatchSize)
                .stream()
                .mapToInt(orderId -> expireOrderAndReleaseCoupon(orderId, now))
                .sum();
    }

    private int expireOrderAndReleaseCoupon(long orderId, LocalDateTime now) {
        if (orderMapper.expireIfPendingPayment(orderId, now) <= 0) {
            return 0;
        }
        // 주문 만료 UPDATE가 성공한 경우에만 예약 쿠폰을 해제한다.
        couponOrderCommandService.releaseReservedCouponForExpiredOrder(orderId);
        return 1;
    }
}
