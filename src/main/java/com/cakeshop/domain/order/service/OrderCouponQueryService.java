package com.cakeshop.domain.order.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cakeshop.domain.order.mapper.OrderCouponQueryMapper;

/**
 * 첫 주문 쿠폰 선별에 필요한 주문 이력을 제공하는 읽기 전용 Service다.
 *
 * <p>작성자: 이정후, 주문 담당자 협의 - 쿠폰 도메인의 미주문 회원 선별에 사용한다.</p>
 */
@Service
public class OrderCouponQueryService {

    private final OrderCouponQueryMapper orderCouponQueryMapper;

    public OrderCouponQueryService(OrderCouponQueryMapper orderCouponQueryMapper) {
        this.orderCouponQueryMapper = orderCouponQueryMapper;
    }

    /** 전달받은 회원 중 주문 이력이 하나라도 있는 회원 식별자만 반환한다. */
    @Transactional(readOnly = true)
    public List<Long> getMemberIdsWithOrderHistory(List<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return List.of();
        }
        return orderCouponQueryMapper.findMemberIdsWithOrderHistory(memberIds);
    }
}
