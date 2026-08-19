package com.cakeshop.domain.order.service;

import com.cakeshop.domain.member.service.MemberNotificationQueryService;
import com.cakeshop.domain.member.service.MemberOrderNotificationQueryService;
import com.cakeshop.domain.notification.dto.form.NotificationRequest;
import com.cakeshop.domain.notification.entity.DeliveryScope;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationService;
import com.cakeshop.domain.order.dto.view.OrderChatView;
import com.cakeshop.domain.order.mapper.OrderChatMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 주환
 * 작성일 : 2026-08-19
 * 기능 : 픽업 리마인더 알림 전송 전용 독립 서비스
 * 설명 : 결제 완료 후 afterCommit 시점 또는 스케줄러에서 프록시 경계를 보장하며 독립 트랜잭션(REQUIRES_NEW)으로 D-1 및 당일 픽업 리마인더를 발송한다.
 * ******************************
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPickupNotificationSender {

    private final NotificationService notificationService;
    private final MemberNotificationQueryService memberNotificationQueryService;
    private final MemberOrderNotificationQueryService memberOrderNotificationQueryService;
    private final OrderChatMapper orderChatMapper;
    private final Clock clock;

    /**
     * 주간 시간대(09시~21시) 익일 픽업 주문 건에 대해 결제 커밋 직후 독립 트랜잭션에서 D-1 리마인더 즉시 발송
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendInstantPickupReminderTomorrowIfEligible(long orderId, long customerId) {
        try {
            OrderChatView orderView = orderChatMapper.findOrderById(orderId);
            if (orderView != null && orderView.pickupAt() != null) {
                LocalDate today = LocalDate.now(clock);
                LocalTime nowTime = LocalTime.now(clock);
                LocalDate pickupDate = orderView.pickupAt().toLocalDate();
                boolean isDaytime = nowTime.getHour() >= 9 && nowTime.getHour() < 21;
                if (isDaytime && pickupDate.isEqual(today.plusDays(1))) {
                    sendPickupReminderTomorrowToCustomer(orderId, customerId);
                    sendPickupReminderTomorrowToAdmins(orderId, customerId, orderView.orderNumber());
                }
            }
        } catch (Exception e) {
            log.warn("결제 완료 직후 픽업 D-1 리마인더 독립 발송 오류 (orderId={}):", orderId, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendPickupReminderTomorrowToCustomer(long orderId, long customerId) {
        try {
            if (memberNotificationQueryService.isMemberActive(customerId)) {
                notificationService.makeNotification(NotificationRequest.builder()
                        .receiverId(customerId)
                        .orderId(orderId)
                        .type(NotificationType.CUSTOMER_PICKUP_REMINDER_TOMORROW)
                        .eventKey(NotificationType.CUSTOMER_PICKUP_REMINDER_TOMORROW.name() + ":" + customerId + ":" + orderId)
                        .deliveryScope(DeliveryScope.WEB_AND_SMS)
                        .args(new Object[0])
                        .build());
            }
        } catch (Exception e) {
            log.error("고객 픽업 하루 전 알림 발송 오류 (orderId={}):", orderId, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendPickupReminderTomorrowToAdmins(long orderId, long customerId, String orderNumber) {
        try {
            String ordNum = (orderNumber != null && !orderNumber.isBlank()) ? orderNumber : String.valueOf(orderId);
            sendToActiveAdmins(orderId, customerId, NotificationType.ADMIN_PICKUP_REMINDER_TOMORROW,
                    NotificationType.ADMIN_PICKUP_REMINDER_TOMORROW.name() + ":ALL_ADMINS:" + orderId, new Object[]{ordNum});
        } catch (Exception e) {
            log.error("관리자 픽업 하루 전 알림 발송 오류 (orderId={}):", orderId, e);
        }
    }

    private void sendToActiveAdmins(long orderId, long customerId, NotificationType type, String eventKey, Object[] args) {
        try {
            List<Long> activeAdminIds = memberOrderNotificationQueryService.findActiveAdminIds();
            if (activeAdminIds == null || activeAdminIds.isEmpty()) {
                return;
            }

            for (Long adminId : activeAdminIds) {
                try {
                    notificationService.makeNotification(NotificationRequest.builder()
                            .receiverId(adminId)
                            .actorId(customerId)
                            .orderId(orderId)
                            .type(type)
                            .eventKey(type.name() + ":" + adminId + ":" + orderId)
                            .deliveryScope(DeliveryScope.WEB_AND_SMS)
                            .args(args)
                            .build());
                } catch (Exception e) {
                    log.error("관리자(id={}) 대상 {} 알림 발송 중 개별 오류 (orderId={}):", adminId, type, orderId, e);
                }
            }
        } catch (Exception e) {
            log.error("관리자 목록 대상 {} 알림 발송 중 오류 발생 (orderId={}):", type, orderId, e);
        }
    }
}
