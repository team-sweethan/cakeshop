package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.dto.view.OrderDetailView;
import com.cakeshop.domain.order.dto.view.OrderListView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 관리자 주문 조회와 처리 요청을 조정한다. */
@Service
@RequiredArgsConstructor
public class OrderAdminService {

    private final OrderQueryService orderQueryService;

    public List<OrderListView> getOrders() {
        return orderQueryService.getAllOrdersForAdmin();
    }

    public OrderDetailView getOrder(long orderId) {
        return orderQueryService.getOrderForAdmin(orderId);
    }
}
