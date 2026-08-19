package com.cakeshop.domain.order.service.payment;

import com.cakeshop.domain.order.dto.view.OrderPaymentAdminView;
import com.cakeshop.domain.order.mapper.OrderMapper;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : 주환
 * 담당자 : 주환
 * 작성일 : 2026-08-11
 * 기능 : 결제 관리자 연동용 주문 조회 계약
 * 설명 : 결제 도메인이 orders 테이블을 직접 조회하지 않고 관리자 결제 목록에 필요한 주문 정보를 조회하도록 제공한다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
public class OrderPaymentAdminQueryService {

    private final OrderMapper orderMapper;

    /**
     * ******************************
     * 작성자 : 주환
     * 담당자 : 주환
     * 작성일 : 2026-08-11
     * 기능 : 관리자 결제 목록용 주문 조회
     * 설명 : 결제 목록에 포함된 주문 ID의 주문번호·주문자·주문 유형만 제공한다.
     * ******************************
     */
    @Transactional(readOnly = true)
    public List<OrderPaymentAdminView> getPaymentAdminOrders(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return orderMapper.findPaymentAdminOrders(orderIds);
    }
}
