package com.cakeshop.domain.order.service;

import com.cakeshop.domain.member.service.MemberNotificationQueryService;
import com.cakeshop.domain.notification.dto.form.NotificationRequest;
import com.cakeshop.domain.notification.entity.DeliveryScope;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 주환
 * 작성일 : 2026-08-18
 * 기능 : 주문 및 결제 실시간/문자 알림 전송 서비스
 * 설명 : 주문/결제 라이프사이클 이벤트 발생 시 활성 고객 및 관리자에게 실시간 웹소켓 토스트 및 문자(SMS) 알림을 발송한다.
 * ******************************
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderNotificationSender {

    private final NotificationService notificationService;
    private final MemberNotificationQueryService memberNotificationQueryService;

    public void sendOrderPaid(long orderId, long customerId, String orderType) {
        boolean isCustom = "CUSTOM".equalsIgnoreCase(orderType);
        NotificationType customerType = isCustom ? NotificationType.CUSTOM_ORDER_PAID : NotificationType.ORDER_PAID;
        NotificationType adminType = isCustom ? NotificationType.NEW_CUSTOM_ORDER : NotificationType.NEW_ORDER;

        if (memberNotificationQueryService.isMemberActive(customerId)) {
            notificationService.makeNotification(NotificationRequest.builder()
                    .receiverId(customerId)
                    .actorId(customerId)
                    .orderId(orderId)
                    .type(customerType)
                    .eventKey(customerType.name() + ":" + customerId + ":" + orderId)
                    .deliveryScope(DeliveryScope.WEB_AND_SMS)
                    .args(new Object[0])
                    .build());
        }

        sendToActiveAdmins(orderId, customerId, adminType, adminType.name() + ":ALL_ADMINS:" + orderId, new Object[0]);
    }

    public void sendCustomOrderInProduction(long orderId, long customerId) {
        if (!memberNotificationQueryService.isMemberActive(customerId)) {
            return;
        }

        notificationService.makeNotification(NotificationRequest.builder()
                .receiverId(customerId)
                .orderId(orderId)
                .type(NotificationType.CUSTOM_ORDER_IN_PRODUCTION)
                .eventKey("CUSTOM_ORDER_IN_PRODUCTION:" + customerId + ":" + orderId)
                .deliveryScope(DeliveryScope.WEB_AND_SMS)
                .args(new Object[0])
                .build());
    }

    public void sendCustomOrderRejected(long orderId, long customerId) {
        if (!memberNotificationQueryService.isMemberActive(customerId)) {
            return;
        }

        notificationService.makeNotification(NotificationRequest.builder()
                .receiverId(customerId)
                .orderId(orderId)
                .type(NotificationType.CUSTOM_ORDER_REJECTED)
                .eventKey("CUSTOM_ORDER_REJECTED:" + customerId + ":" + orderId)
                .deliveryScope(DeliveryScope.WEB_AND_SMS)
                .args(new Object[0])
                .build());
    }

    public void sendOrderCanceled(long orderId, long customerId) {
        sendOrderCanceled(orderId, customerId, null);
    }

    public void sendOrderCanceled(long orderId, long customerId, String orderNumber) {
        sendOrderCanceledToCustomer(orderId, customerId);
        sendOrderCanceledToAdmins(orderId, customerId, orderNumber);
    }

    public void sendOrderCanceledToCustomer(long orderId, long customerId) {
        if (!memberNotificationQueryService.isMemberActive(customerId)) {
            return;
        }

        notificationService.makeNotification(NotificationRequest.builder()
                .receiverId(customerId)
                .orderId(orderId)
                .type(NotificationType.ORDER_CANCELED)
                .eventKey("ORDER_CANCELED:" + customerId + ":" + orderId)
                .deliveryScope(DeliveryScope.WEB_AND_SMS)
                .args(new Object[0])
                .build());
    }

    public void sendOrderCanceledToAdmins(long orderId, long customerId, String orderNumber) {
        try {
            String ordNum = (orderNumber != null && !orderNumber.isBlank()) ? orderNumber : String.valueOf(orderId);
            sendToActiveAdmins(orderId, customerId, NotificationType.ORDER_CANCEL_REQUEST, "ORDER_CANCEL_REQUEST:ALL_ADMINS:" + orderId, new Object[]{ordNum});
        } catch (Exception e) {
            log.error("관리자 주문 취소 알림 발송 오류 (orderId={}):", orderId, e);
        }
    }

    public void sendRefundFailed(long orderId, String orderNumber) {
        try {
            String ordNum = (orderNumber != null && !orderNumber.isBlank()) ? orderNumber : String.valueOf(orderId);
            sendToActiveAdmins(orderId, null, NotificationType.REFUND_FAILED, "REFUND_FAILED:ALL_ADMINS:" + orderId, new Object[]{ordNum});
        } catch (Exception e) {
            log.error("환불 실패 관리자 알림 발송 오류 (orderId={}):", orderId, e);
        }
    }

    private void sendToActiveAdmins(long orderId, Long actorId, NotificationType type, String baseEventKey, Object[] args) {
        try {
            List<Long> activeAdminIds = memberNotificationQueryService.findActiveAdminIds();
            if (activeAdminIds == null || activeAdminIds.isEmpty()) return;

            for (Long adminId : activeAdminIds) {
                try {
                    notificationService.makeNotification(NotificationRequest.builder()
                            .receiverId(adminId)
                            .actorId(actorId)
                            .orderId(orderId)
                            .type(type)
                            .eventKey(type.name() + ":" + adminId + ":" + orderId)
                            .deliveryScope(DeliveryScope.WEB_AND_SMS)
                            .args(args)
                            .build());
                } catch (Exception e) {
                    log.error("관리자(id={}) 대상 {} 알림 발송 중 개별 오류 발생 (orderId={}):", adminId, type, orderId, e);
                }
            }
        } catch (Exception e) {
            log.error("관리자 목록 대상 {} 알림 발송 중 오류 발생 (orderId={}):", type, orderId, e);
        }
    }
}
