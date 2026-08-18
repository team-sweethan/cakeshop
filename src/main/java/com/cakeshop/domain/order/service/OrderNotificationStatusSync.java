package com.cakeshop.domain.order.service;

import com.cakeshop.domain.notification.service.NotificationOrderQueryService;
import com.cakeshop.domain.order.dto.view.OrderChatView;
import com.cakeshop.domain.order.mapper.OrderChatMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 주환
 * 작성일 : 2026-08-18
 * 기능 : 주문 상태 변경 독립 감지 및 알림 동기화 스케줄러
 * 설명 : 타 도메인 코드를 직접 수정하지 않고, 서울 시각 Clock, 과거 1일 복구 탐색, 비동기 스레드 풀(@Async), 시간 커서 페이징(since) 및 NotificationOrderQueryService 멱등성 검사를 통해 제작 승인, 반려, 취소 알림을 발송한다.
 * ******************************
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationStatusSync {

    private final OrderChatMapper orderChatMapper;
    private final OrderNotificationSender orderNotificationSender;
    private final NotificationOrderQueryService notificationOrderQueryService;
    private final Clock clock;

    private LocalDateTime lastSyncTime;

    @Async
    @Scheduled(fixedDelay = 3000)
    public void syncOrderNotifications() {
        try {
            if (lastSyncTime == null) {
                // 재시작 시 다운타임(10분 초과) 동안의 미처리 알림 복구를 위해 최근 1일 전부터 탐색
                lastSyncTime = LocalDateTime.now(clock).minusDays(1);
            }

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
                String orderNumber = order.orderNumber();

                try {
                    if ("IN_PRODUCTION".equalsIgnoreCase(statusStr)) {
                        String eventKey = "CUSTOM_ORDER_IN_PRODUCTION:" + memberId + ":" + orderId;
                        if (!notificationOrderQueryService.isNotificationFullySent(memberId, eventKey)) {
                            orderNotificationSender.sendCustomOrderInProduction(orderId, memberId);
                        }
                    } else if ("REJECTED".equalsIgnoreCase(statusStr)) {
                        String eventKey = "CUSTOM_ORDER_REJECTED:" + memberId + ":" + orderId;
                        if (!notificationOrderQueryService.isNotificationFullySent(memberId, eventKey)) {
                            orderNotificationSender.sendCustomOrderRejected(orderId, memberId);
                        }
                    } else if ("CANCELED".equalsIgnoreCase(statusStr)) {
                        // 고객 취소 알림 체크 및 전송
                        String customerEventKey = "ORDER_CANCELED:" + memberId + ":" + orderId;
                        if (!notificationOrderQueryService.isNotificationFullySent(memberId, customerEventKey)) {
                            orderNotificationSender.sendOrderCanceledToCustomer(orderId, memberId);
                        }
                        // 관리자 취소 알림 전송 (주문번호 포함)
                        orderNotificationSender.sendOrderCanceledToAdmins(orderId, memberId, orderNumber);
                    }

                    if (order.orderUpdatedAt() != null && order.orderUpdatedAt().isAfter(maxProcessedTime)) {
                        maxProcessedTime = order.orderUpdatedAt();
                    }
                } catch (Exception itemException) {
                    log.error("주문(orderId={}) 상태 동기화 알림 발송 중 오류 발생. 해당 시점까지만 커서 유지:", orderId, itemException);
                    break;
                }
            }

            if (maxProcessedTime.isAfter(lastSyncTime)) {
                lastSyncTime = maxProcessedTime;
            }
        } catch (Exception e) {
            log.error("주문 상태 변경 동기화 알림 처리 전체 실패:", e);
        }
    }
}
