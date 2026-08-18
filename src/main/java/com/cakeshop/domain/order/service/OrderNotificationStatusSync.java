package com.cakeshop.domain.order.service;

import com.cakeshop.domain.order.dto.view.OrderChatView;
import com.cakeshop.domain.order.mapper.OrderChatMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 주환
 * 작성일 : 2026-08-18
 * 기능 : 주문 상태 변경 독립 감지 및 알림 동기화 스케줄러
 * 설명 : 타 도메인 코드를 직접 수정하지 않고, 제작 승인(IN_PRODUCTION), 반려(REJECTED), 취소(CANCELED) 상태 전이를 안전하게 감지하여 실시간 알림을 발송한다.
 * ******************************
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationStatusSync {

    private final OrderChatMapper orderChatMapper;
    private final OrderNotificationSender orderNotificationSender;

    @Scheduled(fixedDelay = 3000)
    public void syncOrderNotifications() {
        try {
            List<OrderChatView> recentOrders = orderChatMapper.findRecentStatusChangedOrders();
            if (recentOrders == null || recentOrders.isEmpty()) {
                return;
            }

            for (OrderChatView order : recentOrders) {
                if (order.id() == null || order.memberId() == null || order.status() == null) {
                    continue;
                }

                String statusStr = order.status();
                long orderId = order.id();
                long memberId = order.memberId();

                if ("IN_PRODUCTION".equalsIgnoreCase(statusStr)) {
                    String eventKey = "CUSTOM_ORDER_IN_PRODUCTION:" + memberId + ":" + orderId;
                    if (orderChatMapper.countNotificationByEventKey(eventKey) == 0) {
                        orderNotificationSender.sendCustomOrderInProduction(orderId, memberId);
                    }
                } else if ("REJECTED".equalsIgnoreCase(statusStr)) {
                    String eventKey = "CUSTOM_ORDER_REJECTED:" + memberId + ":" + orderId;
                    if (orderChatMapper.countNotificationByEventKey(eventKey) == 0) {
                        orderNotificationSender.sendCustomOrderRejected(orderId, memberId);
                    }
                } else if ("CANCELED".equalsIgnoreCase(statusStr)) {
                    String eventKey = "ORDER_CANCELED:" + memberId + ":" + orderId;
                    if (orderChatMapper.countNotificationByEventKey(eventKey) == 0) {
                        orderNotificationSender.sendOrderCanceled(orderId, memberId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("주문 상태 변경 동동기화 알림 처리 실패:", e);
        }
    }
}
