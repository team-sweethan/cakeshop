package com.cakeshop.domain.order.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cakeshop.domain.order.mapper.OrderCouponMapper;

/**
 * ******************************
 * 작성자 : 이정후
 * 담당자 : 주환
 * 작성일 : 2026-08-07
 * 기능 : 쿠폰 연동용 주문 이력 조회 계약
 * 설명 : 쿠폰 도메인이 orders 테이블을 직접 조회하지 않고 첫 주문 쿠폰 대상 여부를 판단하도록 제공한다.
 * ******************************
 */
@Service
public class OrderCouponQueryService {

    private final OrderCouponMapper orderCouponMapper;

    public OrderCouponQueryService(OrderCouponMapper orderCouponMapper) {
        this.orderCouponMapper = orderCouponMapper;
    }

    /**
     * ******************************
     * 기능 : 주문 이력 보유 회원 조회
     * 설명 : 첫 주문 쿠폰 등록 시 후보 중 주문 이력이 있는 회원 ID를 제외하도록 제공한다.
     * ******************************
     */
    @Transactional(readOnly = true)
    public List<Long> getMemberIdsWithOrderHistory(List<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return List.of();
        }
        return orderCouponMapper.findMemberIdsWithOrderHistory(memberIds);
    }

    /**
     * ******************************
     * 기능 : 회원 주문 이력 재확인
     * 설명 : 첫 주문 쿠폰 INSERT 직전에 최신 주문 이력을 확인해 대상 조건을 재검증한다.
     * ******************************
     */
    @Transactional(readOnly = true)
    public boolean hasOrderHistory(Long memberId) {
        return orderCouponMapper.existsOrderHistory(memberId);
    }
}
