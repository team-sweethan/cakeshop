package com.cakeshop.domain.order.service;

import com.cakeshop.domain.notification.service.NotificationOrderQueryService;
import com.cakeshop.domain.order.dto.view.OrderChatView;
import com.cakeshop.domain.order.mapper.OrderPickupNotificationMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * 기능 : 픽업 안내 알림(하루 전/당일) 자동 동기화 스케줄러
 * 설명 : 매일 아침 9시 pickup_at 날짜를 조회하여 픽업 하루 전 및 픽업 당일 안내 알림/SMS를 멱등하게 전송한다.
 * ******************************
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPickupNotificationSync {

    private final OrderPickupNotificationMapper orderPickupNotificationMapper;
    private final OrderNotificationSender orderNotificationSender;
    private final NotificationOrderQueryService notificationOrderQueryService;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    /** 매일 아침 9시 픽업 안내 알림 발송 */
    @Async
    @Scheduled(cron = "0 0 9 * * *")
    public void syncPickupReminderNotifications() {
        if (!isRunning.compareAndSet(false, true)) {
            log.trace("이전 픽업 안내 배치가 실행 중이므로 스킵합니다.");
            return;
        }

        try {
            // 1. 픽업 하루 전(내일) 알림 처리
            List<OrderChatView> tomorrowOrders = orderPickupNotificationMapper.findOrdersForPickupTomorrow();
            if (tomorrowOrders != null && !tomorrowOrders.isEmpty()) {
                for (OrderChatView order : tomorrowOrders) {
                    if (order.id() == null || order.memberId() == null) continue;
                    long orderId = order.id();
                    long memberId = order.memberId();
                    String orderNumber = order.orderNumber();

                    String customerEventKey = "CUSTOMER_PICKUP_TOMORROW:" + memberId + ":" + orderId;
                    if (!notificationOrderQueryService.isNotificationFullySent(memberId, customerEventKey)) {
                        orderNotificationSender.sendPickupReminderTomorrow(orderId, memberId, orderNumber);
                    }
                }
            }

            // 2. 픽업 당일(오늘) 알림 처리
            List<OrderChatView> todayOrders = orderPickupNotificationMapper.findOrdersForPickupToday();
            if (todayOrders != null && !todayOrders.isEmpty()) {
                for (OrderChatView order : todayOrders) {
                    if (order.id() == null || order.memberId() == null) continue;
                    long orderId = order.id();
                    long memberId = order.memberId();
                    String orderNumber = order.orderNumber();

                    String customerEventKey = "CUSTOMER_PICKUP_TODAY:" + memberId + ":" + orderId;
                    if (!notificationOrderQueryService.isNotificationFullySent(memberId, customerEventKey)) {
                        orderNotificationSender.sendPickupReminderToday(orderId, memberId, orderNumber);
                    }
                }
            }
        } catch (Exception e) {
            log.error("픽업 안내 알림 동기화 스케줄러 실행 오류:", e);
        } finally {
            isRunning.set(false);
        }
    }
}
