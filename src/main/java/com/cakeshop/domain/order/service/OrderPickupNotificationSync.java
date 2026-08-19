package com.cakeshop.domain.order.service;

import com.cakeshop.domain.member.service.MemberOrderNotificationQueryService;
import com.cakeshop.domain.notification.entity.NotificationType;
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
 * 설명 : 서울 시각(Asia/Seoul) 기준 오전 9시~저녁 9시 사이 매시 정각 픽업 하루 전 및 픽업 당일 안내 알림/SMS를 고객과 관리자별로 독립 멱등 전송하며, 오전 9시 다운타임 누락건도 당일 내 자동 복구한다.
 * ******************************
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPickupNotificationSync {

    private final OrderPickupNotificationMapper orderPickupNotificationMapper;
    private final OrderNotificationSender orderNotificationSender;
    private final NotificationOrderQueryService notificationOrderQueryService;
    private final MemberOrderNotificationQueryService memberOrderNotificationQueryService;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    /** 서울 시간대 기준 오전 9시~저녁 9시 사이 매시 정각 픽업 안내 알림 체크 및 복구 발송 */
    @Async
    @Scheduled(cron = "0 0 9-21 * * *", zone = "Asia/Seoul")
    public void syncPickupReminderNotifications() {
        if (!isRunning.compareAndSet(false, true)) {
            log.trace("이전 픽업 안내 배치가 실행 중이므로 스킵합니다.");
            return;
        }

        try {
            List<Long> adminIds = memberOrderNotificationQueryService.findActiveAdminIds();

            // 1. 픽업 하루 전(내일) 알림 처리
            List<OrderChatView> tomorrowOrders = orderPickupNotificationMapper.findOrdersForPickupTomorrow();
            if (tomorrowOrders != null && !tomorrowOrders.isEmpty()) {
                for (OrderChatView order : tomorrowOrders) {
                    if (order.id() == null || order.memberId() == null) continue;
                    long orderId = order.id();
                    long memberId = order.memberId();
                    String orderNumber = order.orderNumber();

                    // 고객 알림 독립 체크 및 전송
                    String customerEventKey = NotificationType.CUSTOMER_PICKUP_REMINDER_TOMORROW.name() + ":" + memberId + ":" + orderId;
                    if (!notificationOrderQueryService.isNotificationFullySent(memberId, customerEventKey)) {
                        orderNotificationSender.sendPickupReminderTomorrowToCustomer(orderId, memberId);
                    }

                    // 관리자들 알림 독립 체크 및 전송 (미완료 관리자가 있으면 관리자 발송 수행)
                    if (adminIds != null && !adminIds.isEmpty()) {
                        boolean hasUnsentAdmin = adminIds.stream().anyMatch(adminId ->
                                !notificationOrderQueryService.isNotificationFullySent(adminId,
                                        NotificationType.ADMIN_PICKUP_REMINDER_TOMORROW.name() + ":" + adminId + ":" + orderId));
                        if (hasUnsentAdmin) {
                            orderNotificationSender.sendPickupReminderTomorrowToAdmins(orderId, memberId, orderNumber);
                        }
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

                    // 고객 알림 독립 체크 및 전송
                    String customerEventKey = NotificationType.CUSTOMER_PICKUP_REMINDER_TODAY.name() + ":" + memberId + ":" + orderId;
                    if (!notificationOrderQueryService.isNotificationFullySent(memberId, customerEventKey)) {
                        orderNotificationSender.sendPickupReminderTodayToCustomer(orderId, memberId);
                    }

                    // 관리자들 알림 독립 체크 및 전송 (미완료 관리자가 있으면 관리자 발송 수행)
                    if (adminIds != null && !adminIds.isEmpty()) {
                        boolean hasUnsentAdmin = adminIds.stream().anyMatch(adminId ->
                                !notificationOrderQueryService.isNotificationFullySent(adminId,
                                        NotificationType.ADMIN_PICKUP_REMINDER_TODAY.name() + ":" + adminId + ":" + orderId));
                        if (hasUnsentAdmin) {
                            orderNotificationSender.sendPickupReminderTodayToAdmins(orderId, memberId, orderNumber);
                        }
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
