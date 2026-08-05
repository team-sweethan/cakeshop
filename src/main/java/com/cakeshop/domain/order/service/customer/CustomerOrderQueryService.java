package com.cakeshop.domain.order.service.customer;

import com.cakeshop.domain.order.dto.view.OrderDetailView;
import com.cakeshop.domain.order.dto.view.OrderListView;
import com.cakeshop.domain.order.entity.Order;
import com.cakeshop.domain.order.service.OrderViewAssembler;
import com.cakeshop.domain.order.error.OrderErrorCode;
import com.cakeshop.domain.order.mapper.OrderMapper;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 고객 소유권을 적용한 주문 목록·상세 조회를 담당한다. */
@Service
@RequiredArgsConstructor
public class CustomerOrderQueryService {

    private final OrderViewAssembler orderViewAssembler;

    @Transactional(readOnly = true)
    public List<OrderListView> getMemberOrders(long memberId) {
        validateMemberId(memberId);
        return orderViewAssembler.findOrdersByMemberId(memberId).stream()
                .map(orderViewAssembler::toListView)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDetailView getMemberOrder(long memberId, long orderId) {
        validateMemberId(memberId);
        Order order = orderViewAssembler.findOrder(orderId);
        // 다른 회원에게 주문의 존재 여부도 노출하지 않는다.
        if (!Long.valueOf(memberId).equals(order.getMemberId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return orderViewAssembler.toDetailView(order);
    }

    /** 실존하는 회원인지 검증**/
    private void validateMemberId(long memberId) {
        if (memberId <= 0) {
            throw new BusinessException(OrderErrorCode.MEMBER_NOT_AVAILABLE);
        }
    }
}
