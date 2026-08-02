package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** 픽업 가능한 주문의 수령 완료 처리를 담당한다. */
@Service
@RequiredArgsConstructor
public class FulfillmentService {

    private final OrderMapper orderMapper;
    private final Clock clock;

    /** DONE 결제가 유지되는 픽업 준비 주문만 수령 완료로 원자적으로 변경한다. */
    @Transactional
    public void markPickedUp(long orderId, long adminMemberId) {
        if (orderId <= 0 || adminMemberId <= 0) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }
        int affectedRows = orderMapper.markPickedUpIfReady(
                orderId,
                adminMemberId,
                LocalDateTime.now(clock)
        );
        if (affectedRows != 1) {
            throw new BusinessException(OrderErrorCode.INVALID_STATUS_TRANSITION);
        }
    }
}
