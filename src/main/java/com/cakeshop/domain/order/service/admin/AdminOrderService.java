package com.cakeshop.domain.order.service.admin;

import com.cakeshop.domain.order.dto.view.OrderDetailView;
import com.cakeshop.domain.order.dto.view.OrderListView;
import com.cakeshop.domain.order.service.OrderViewAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

/** 관리자 전체 주문 목록·상세 조회를 담당한다. */
@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private final OrderViewAssembler orderViewAssembler;

    @Transactional(readOnly = true)
    public List<OrderListView> getOrders() {
        return orderViewAssembler.findAllOrders().stream()
                .map(orderViewAssembler::toListView)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDetailView getOrder(long orderId) {
        return orderViewAssembler.toDetailView(orderViewAssembler.findOrder(orderId));
    }
}
