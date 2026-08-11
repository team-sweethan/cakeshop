package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.mapper.OrderChatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 주환
 * 작성일 : 2026-08-11
 * 기능 : 채팅방 연동 주문 조회 계약
 * 설명 : 채팅방과 연동된 주문 상세 정보를 연동 전용 Mapper로 안전하게 조회한다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
public class OrderChatQueryService {

    private final OrderChatMapper orderChatMapper;

    public Order findOrder(Long orderId) {
        if (orderId == null || orderId <= 0) {
            return null;
        }
        return orderChatMapper.findOrderById(orderId);
    }

    public String getRepresentativeProductName(Long orderId) {
        if (orderId == null || orderId <= 0) {
            return "연동 주문 상품";
        }
        try {
            String name = orderChatMapper.findRepresentativeProductName(orderId);
            return (name != null && !name.isBlank()) ? name : "연동 주문 상품";
        } catch (Exception e) {
            return "연동 주문 상품";
        }
    }
}
