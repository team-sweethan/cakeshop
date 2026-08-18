package com.cakeshop.domain.order.service;

import com.cakeshop.domain.member.service.MemberChatQueryService;
import com.cakeshop.domain.notification.entity.NotificationType;
import com.cakeshop.domain.notification.service.NotificationService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * ******************************
 * 작성자 : 김민정
 * 담당자 : 주환
 * 작성일 : 2026-08-18
 * 기능 : 주문 알림 발송 서비스 단위 테스트
 * 설명 : OrderNotificationSender가 결제 완료, 제작 승인, 반려, 취소, 환불 실패 알림을 알맞은 수신자와 타입으로 발송하는지 검증한다.
 * ******************************
 */
@ExtendWith(MockitoExtension.class)
class OrderNotificationSenderTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private MemberChatQueryService memberChatQueryService;

    @InjectMocks
    private OrderNotificationSender orderNotificationSender;

    @Test
    @DisplayName("일반 주문 결제 완료 시 고객에게 ORDER_PAID 및 관리자에게 NEW_ORDER 알림이 전송된다")
    void sendOrderPaid_generalOrder_dispatchesNotifications() {
        long orderId = 100L;
        long customerId = 2L;
        long adminId = 1L;
        given(memberChatQueryService.findActiveAdminIds()).willReturn(List.of(adminId));

        orderNotificationSender.sendOrderPaid(orderId, customerId, "GENERAL");

        verify(notificationService).makeNotification(argThat(req ->
                req.getReceiverId() == customerId && req.getType() == NotificationType.ORDER_PAID
        ));
        verify(notificationService).makeNotification(argThat(req ->
                req.getReceiverId() == adminId && req.getType() == NotificationType.NEW_ORDER
        ));
    }

    @Test
    @DisplayName("주문제작 결제 완료 시 고객에게 CUSTOM_ORDER_PAID 및 관리자에게 NEW_CUSTOM_ORDER 알림이 전송된다")
    void sendOrderPaid_customOrder_dispatchesNotifications() {
        long orderId = 101L;
        long customerId = 2L;
        long adminId = 1L;
        given(memberChatQueryService.findActiveAdminIds()).willReturn(List.of(adminId));

        orderNotificationSender.sendOrderPaid(orderId, customerId, "CUSTOM");

        verify(notificationService).makeNotification(argThat(req ->
                req.getReceiverId() == customerId && req.getType() == NotificationType.CUSTOM_ORDER_PAID
        ));
        verify(notificationService).makeNotification(argThat(req ->
                req.getReceiverId() == adminId && req.getType() == NotificationType.NEW_CUSTOM_ORDER
        ));
    }

    @Test
    @DisplayName("주문제작 제작 승인 시 고객에게 CUSTOM_ORDER_IN_PRODUCTION 알림이 전송된다")
    void sendCustomOrderInProduction_dispatchesNotification() {
        orderNotificationSender.sendCustomOrderInProduction(200L, 2L);

        verify(notificationService).makeNotification(argThat(req ->
                req.getReceiverId() == 2L && req.getType() == NotificationType.CUSTOM_ORDER_IN_PRODUCTION
        ));
    }

    @Test
    @DisplayName("주문제작 반려 시 고객에게 CUSTOM_ORDER_REJECTED 알림이 전송된다")
    void sendCustomOrderRejected_dispatchesNotification() {
        orderNotificationSender.sendCustomOrderRejected(201L, 2L);

        verify(notificationService).makeNotification(argThat(req ->
                req.getReceiverId() == 2L && req.getType() == NotificationType.CUSTOM_ORDER_REJECTED
        ));
    }

    @Test
    @DisplayName("주문 취소 시 고객에게 ORDER_CANCELED 알림이 전송된다")
    void sendOrderCanceled_dispatchesNotification() {
        orderNotificationSender.sendOrderCanceled(202L, 2L);

        verify(notificationService).makeNotification(argThat(req ->
                req.getReceiverId() == 2L && req.getType() == NotificationType.ORDER_CANCELED
        ));
    }

    @Test
    @DisplayName("환불 실패 시 관리자들에게 REFUND_FAILED 알림이 전송된다")
    void sendRefundFailed_dispatchesNotificationToAdmins() {
        given(memberChatQueryService.findActiveAdminIds()).willReturn(List.of(1L));

        orderNotificationSender.sendRefundFailed(300L, "ORD-20260818-0001");

        verify(notificationService).makeNotification(argThat(req ->
                req.getReceiverId() == 1L && req.getType() == NotificationType.REFUND_FAILED
        ));
    }
}
