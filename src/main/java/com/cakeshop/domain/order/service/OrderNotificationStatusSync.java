package com.cakeshop.domain.order.service;

import com.cakeshop.domain.member.service.MemberOrderNotificationQueryService;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationOrderQueryService;
import com.cakeshop.domain.order.dto.view.OrderChatView;
import com.cakeshop.domain.order.mapper.OrderChatMapper;
import java.time.Clock;
import java.time.LocalDateTime;
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
 * 기능 : 주문 상태 변경 독립 감지 및 알림 동기화 스케줄러
 * 설명 : 타 도메인 코드를 직접 수정하지 않고, 서울 시각 Clock, 과거 1일 복구 탐색, AtomicBoolean 직렬화, 비동기 스레드 풀(@Async), 시간 커서 페이징(since) 및 NotificationOrderQueryService 멱등성 검사를 통해 제작 승인, 반려, 취소, 픽업 완료 알림을 발송한다.
 * ******************************
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationStatusSync {

    private final OrderChatMapper orderChatMapper;
    private final OrderNotificationSender orderNotificationSender;
    private final NotificationOrderQueryService notificationOrderQueryService;
    private final MemberOrderNotificationQueryService memberOrderNotificationQueryService;
    private final Clock clock;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private LocalDateTime lastSyncTime;

    @Async
    @Scheduled(fixedDelay = 3000)
    public void syncOrderNotifications() {
        if (!isRunning.compareAndSet(false, true)) {
            log.trace("이전 동기화 배치가 여전히 실행 중이므로 이번 스케줄 조회를 직렬화(스킵)합니다.");
            return;
        }

        try {
            if (lastSyncTime == null) {
                // 재시작 시 다운타임(10분 초과) 동안의 미처리 알림 복구를 위해 최근 1일 전부터 탐색
                lastSyncTime = LocalDateTime.now(clock).minusDays(1);
            }

            List<OrderChatView> recentOrders = orderChatMapper.findRecentStatusChangedOrders(lastSyncTime);
            if (recentOrders == null || recentOrders.isEmpty()) {
                return;
            }

            List<Long> adminIds = memberOrderNotificationQueryService.findActiveAdminIds();
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
                    boolean orderFullyHandled = true;

                    if ("IN_PRODUCTION".equalsIgnoreCase(statusStr)) {
                        String eventKey = "CUSTOM_ORDER_IN_PRODUCTION:" + memberId + ":" + orderId;
                        if (!notificationOrderQueryService.isNotificationFullySent(memberId, eventKey)) {
                            orderNotificationSender.sendCustomOrderInProduction(orderId, memberId);
                        }
                        orderFullyHandled = notificationOrderQueryService.isNotificationFullySent(memberId, eventKey);
                    } else if ("REJECTED".equalsIgnoreCase(statusStr)) {
                        String eventKey = "CUSTOM_ORDER_REJECTED:" + memberId + ":" + orderId;
                        if (!notificationOrderQueryService.isNotificationFullySent(memberId, eventKey)) {
                            orderNotificationSender.sendCustomOrderRejected(orderId, memberId);
                        }
                        orderFullyHandled = notificationOrderQueryService.isNotificationFullySent(memberId, eventKey);
                    } else if ("CANCELED".equalsIgnoreCase(statusStr)) {
                        // 고객 취소 알림 체크 및 전송
                        String customerEventKey = "ORDER_CANCELED:" + memberId + ":" + orderId;
                        if (!notificationOrderQueryService.isNotificationFullySent(memberId, customerEventKey)) {
                            orderNotificationSender.sendOrderCanceledToCustomer(orderId, memberId);
                        }
                        // 관리자 취소 알림 체크 및 전송
                        if (adminIds != null && !adminIds.isEmpty()) {
                            boolean hasUnsentAdmin = adminIds.stream().anyMatch(adminId ->
                                    !notificationOrderQueryService.isNotificationFullySent(adminId,
                                            NotificationType.ORDER_CANCEL_REQUEST.name() + ":" + adminId + ":" + orderId));
                            if (hasUnsentAdmin) {
                                orderNotificationSender.sendOrderCanceledToAdmins(orderId, memberId, orderNumber);
                            }
                        }
                        boolean customerSent = notificationOrderQueryService.isNotificationFullySent(memberId, customerEventKey);
                        boolean adminSent = adminIds == null || adminIds.stream().allMatch(adminId ->
                                notificationOrderQueryService.isNotificationFullySent(adminId,
                                        NotificationType.ORDER_CANCEL_REQUEST.name() + ":" + adminId + ":" + orderId));
                        orderFullyHandled = customerSent && adminSent;
                    } else if ("PICKED_UP".equalsIgnoreCase(statusStr)) {
                        // 픽업 완료 고객 알림 독립 체크 및 전송
                        String customerEventKey = NotificationType.CUSTOMER_ORDER_PICKED_UP.name() + ":" + memberId + ":" + orderId;
                        if (!notificationOrderQueryService.isNotificationFullySent(memberId, customerEventKey)) {
                            orderNotificationSender.sendOrderPickedUpToCustomer(orderId, memberId);
                        }
                        // 픽업 완료 관리자 알림 독립 체크 및 전송
                        if (adminIds != null && !adminIds.isEmpty()) {
                            boolean hasUnsentAdmin = adminIds.stream().anyMatch(adminId ->
                                    !notificationOrderQueryService.isNotificationFullySent(adminId,
                                            NotificationType.ADMIN_PICKEDUP.name() + ":" + adminId + ":" + orderId));
                            if (hasUnsentAdmin) {
                                orderNotificationSender.sendOrderPickedUpToAdmins(orderId, memberId, orderNumber);
                            }
                        }
                        boolean customerSent = notificationOrderQueryService.isNotificationFullySent(memberId, customerEventKey);
                        boolean adminSent = adminIds == null || adminIds.stream().allMatch(adminId ->
                                notificationOrderQueryService.isNotificationFullySent(adminId,
                                        NotificationType.ADMIN_PICKEDUP.name() + ":" + adminId + ":" + orderId));
                        orderFullyHandled = customerSent && adminSent;
                    }

                    // 실패 건이 남아있으면 커서를 전진시키지 않고 다음 스케줄에서 재시도하도록 중단
                    if (!orderFullyHandled) {
                        log.debug("주문(orderId={})의 알림 발송이 미완료 상태이므로 커서 전진을 보류합니다.", orderId);
                        break;
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
        } finally {
            isRunning.set(false);
        }
    }
}
