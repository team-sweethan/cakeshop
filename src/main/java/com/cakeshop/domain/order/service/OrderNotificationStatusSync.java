package com.cakeshop.domain.order.service;

import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.domain.order.dto.view.OrderChatView;
import com.cakeshop.domain.order.mapper.OrderChatMapper;
import java.time.LocalDateTime;
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
 * 설명 : 타 도메인 코드를 수정하지 않고 시간 커서(since) 및 NotificationService 멱등성 검사를 통해 제작 승인, 반려, 취소 알림을 발송한다.
 * ******************************
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationStatusSync {

    private final OrderChatMapper orderChatMapper;
    private final OrderNotificationSender orderNotificationSender;
    private final NotificationService notificationService;

    private LocalDateTime lastSyncTime = LocalDateTime.now().minusMinutes(5);

    @Scheduled(fixedDelay = 3000)
    public void syncOrderNotifications() {
        try {
            LocalDateTime nextSyncTime = LocalDateTime.now();
            List<OrderChatView> recentOrders = orderChatMapper.findRecentStatusChangedOrders(lastSyncTime);
            lastSyncTime = nextSyncTime.minusSeconds(2); // 네트워크 시차 대비 2초 랩핑

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
                    if (!notificationService.existsByReceiverIdAndEventKey(memberId, eventKey)) {
                        orderNotificationSender.sendCustomOrderInProduction(orderId, memberId);
                    }
                } else if ("REJECTED".equalsIgnoreCase(statusStr)) {
                    String eventKey = "CUSTOM_ORDER_REJECTED:" + memberId + ":" + orderId;
                    if (!notificationService.existsByReceiverIdAndEventKey(memberId, eventKey)) {
                        orderNotificationSender.sendCustomOrderRejected(orderId, memberId);
                    }
                } else if ("CANCELED".equalsIgnoreCase(statusStr)) {
                    String eventKey = "ORDER_CANCELED:" + memberId + ":" + orderId;
                    if (!notificationService.existsByReceiverIdAndEventKey(memberId, eventKey)) {
                        orderNotificationSender.sendOrderCanceled(orderId, memberId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("주문 상태 변경 동기화 알림 처리 실패:", e);
        }
    }
}
