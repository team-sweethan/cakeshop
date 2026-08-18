package com.cakeshop.domain.order.service;

import com.cakeshop.domain.notification.service.NotificationOrderQueryService;
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
 * 설명 : 타 도메인 코드를 직접 수정하지 않고, 시간 커서 페이징(since) 및 NotificationOrderQueryService 멱등성 검사를 통해 제작 승인, 반려, 취소 알림을 발송한다.
 * ******************************
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationStatusSync {

    private final OrderChatMapper orderChatMapper;
    private final OrderNotificationSender orderNotificationSender;
    private final NotificationOrderQueryService notificationOrderQueryService;

    private LocalDateTime lastSyncTime = LocalDateTime.now().minusMinutes(10);

    @Scheduled(fixedDelay = 3000)
    public void syncOrderNotifications() {
        try {
            List<OrderChatView> recentOrders = orderChatMapper.findRecentStatusChangedOrders(lastSyncTime);
            if (recentOrders == null || recentOrders.isEmpty()) {
                return;
            }

            LocalDateTime maxProcessedTime = lastSyncTime;

            for (OrderChatView order : recentOrders) {
                if (order.id() == null || order.memberId() == null || order.status() == null) {
                    continue;
                }

                String statusStr = order.status();
                long orderId = order.id();
                long memberId = order.memberId();

                if ("IN_PRODUCTION".equalsIgnoreCase(statusStr)) {
                    String eventKey = "CUSTOM_ORDER_IN_PRODUCTION:" + memberId + ":" + orderId;
                    if (!notificationOrderQueryService.existsByReceiverIdAndEventKey(memberId, eventKey)) {
                        orderNotificationSender.sendCustomOrderInProduction(orderId, memberId);
                    }
                } else if ("REJECTED".equalsIgnoreCase(statusStr)) {
                    String eventKey = "CUSTOM_ORDER_REJECTED:" + memberId + ":" + orderId;
                    if (!notificationOrderQueryService.existsByReceiverIdAndEventKey(memberId, eventKey)) {
                        orderNotificationSender.sendCustomOrderRejected(orderId, memberId);
                    }
                } else if ("CANCELED".equalsIgnoreCase(statusStr)) {
                    String eventKey = "ORDER_CANCELED:" + memberId + ":" + orderId;
                    if (!notificationOrderQueryService.existsByReceiverIdAndEventKey(memberId, eventKey)) {
                        orderNotificationSender.sendOrderCanceled(orderId, memberId);
                    }
                }

                if (order.orderCreatedAt() != null && order.orderCreatedAt().isAfter(maxProcessedTime)) {
                    maxProcessedTime = order.orderCreatedAt();
                }
            }

            // 페이지 소비 완료 후 커서를 마지막으로 처리된 시각으로 전진
            if (maxProcessedTime.isAfter(lastSyncTime)) {
                lastSyncTime = maxProcessedTime;
            }
        } catch (Exception e) {
            log.error("주문 상태 변경 동기화 알림 처리 실패:", e);
        }
    }
}
