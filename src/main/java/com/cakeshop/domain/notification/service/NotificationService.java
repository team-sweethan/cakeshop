package com.cakeshop.domain.notification.service;

import java.util.List;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import com.cakeshop.domain.notification.mapper.NotificationMapper;
import com.cakeshop.global.infra.kakao.SolapiKakaoAlimtalkClient;
import com.cakeshop.domain.notification.dto.view.NotificationResponse;
import com.cakeshop.domain.notification.dto.form.NotificationRequest;
import com.cakeshop.domain.notification.entity.DeliveryScope;
import com.cakeshop.domain.notification.entity.Notification;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final SolapiKakaoAlimtalkClient solapiKakaoAlimtalkClient;
    private final NotificationDeliveryService notificationDeliveryService;

    // 알림 생성
    @Transactional
    public void makeNotification(NotificationRequest request) {

        // 중복 알림인지 체크하기
        if (request.getEventKey() != null && notificationMapper.existsByReceiverIdAndEventKey(request.getReceiverId(), request.getEventKey())) {
            return;
        }

        // 알림 제목, 내용 만들기
        String title = request.getType().getDefaultTitle();
        String content = request.getType().formatContent(request.getArgs());

        // 알림 발송 범위 결정
        DeliveryScope scope = request.getDeliveryScope() != null ? request.getDeliveryScope() : DeliveryScope.WEB_ONLY;

        // 이벤트 키 결정 (누락 시 수신자, 타입, 가장 세밀한 연관ID 조합으로 결정론적 고유 키 생성)
        String eventKey = request.getEventKey();
        if (eventKey == null || eventKey.trim().isEmpty()) {
            Object targetId = request.getCommentId() != null ? request.getCommentId()
                    : request.getReviewReplyId() != null ? request.getReviewReplyId()
                    : request.getChatMessageId() != null ? request.getChatMessageId()
                    : request.getChatRoomId() != null ? request.getChatRoomId()
                    : request.getOrderId() != null ? request.getOrderId()
                    : request.getPostId() != null ? request.getPostId()
                    : request.getReviewId() != null ? request.getReviewId()
                    : request.getUserCouponId() != null ? request.getUserCouponId()
                    : java.util.UUID.randomUUID().toString();
            eventKey = request.getType().name() + ":" + request.getReceiverId() + ":" + targetId;
        }

        // 알림 만들기
        Notification notification = Notification.builder()
            .receiverId(request.getReceiverId())
            .actorId(request.getActorId())
            .orderId(request.getOrderId())
            .chatRoomId(request.getChatRoomId())
            .chatMessageId(request.getChatMessageId())
            .postId(request.getPostId())
            .commentId(request.getCommentId())
            .reviewId(request.getReviewId())
            .reviewReplyId(request.getReviewReplyId())
            .userCouponId(request.getUserCouponId())
            .notificationType(request.getType())
            .title(title)
            .content(content)
            .deliveryScope(scope)
            .isRead(false)
            .eventKey(eventKey)
            .createdAt(LocalDateTime.now())
            .build();

        // 알림 DB 저장하기 (동시 요청으로 인한 중복 키 예외 멱등 처리)
        try {
            notificationMapper.save(notification);
        } catch (DuplicateKeyException e) {
            // 동일 eventKey 중복 요청 시 웹 알림은 멱등하게 무시하되, WEB_AND_SMS인 경우 성공(SENT) 이력이 없을 때만 SMS 재발송 시도
            if (scope == DeliveryScope.WEB_AND_SMS && request.getReceiverId() != null) {
                Long existingId = notificationMapper.findIdByReceiverIdAndEventKey(request.getReceiverId(), eventKey);
                if (existingId != null && !notificationMapper.hasSentDelivery(existingId)) {
                    String receiverPhone = notificationMapper.findReceiverPhone(request.getReceiverId());
                    executeSmsSending(existingId, receiverPhone, title, content);
                }
            }
            return;
        }

        // 알림톡 / SMS 외부 발송 연동 (DB 트랜잭션 커밋 완료 후 안전하게 발송)
        if (scope == DeliveryScope.WEB_AND_SMS && request.getReceiverId() != null) {
            String receiverPhone = notificationMapper.findReceiverPhone(request.getReceiverId());
            Long notificationId = notification.getId();
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        executeSmsSending(notificationId, receiverPhone, title, content);
                    }
                });
            } else {
                executeSmsSending(notificationId, receiverPhone, title, content);
            }
        }
    }

    private void executeSmsSending(Long notificationId, String receiverPhone, String title, String content) {
        if (receiverPhone == null || receiverPhone.trim().isEmpty()) {
            com.cakeshop.domain.notification.entity.NotificationDelivery delivery = com.cakeshop.domain.notification.entity.NotificationDelivery.builder()
                    .notificationId(notificationId)
                    .recipient("NO_PHONE")
                    .templateCode("DEFAULT_SMS")
                    .providerMessageId(null)
                    .status("FAILED")
                    .failureReason("No receiver phone number")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            notificationDeliveryService.recordDelivery(delivery);
            return;
        }

        SolapiKakaoAlimtalkClient.SmsResult result = solapiKakaoAlimtalkClient.sendAlimtalk(notificationId, receiverPhone, title, content);
        if (result != null) {
            LocalDateTime sentAt = "SENT".equals(result.getStatus()) ? LocalDateTime.now() : null;
            com.cakeshop.domain.notification.entity.NotificationDelivery delivery = com.cakeshop.domain.notification.entity.NotificationDelivery.builder()
                    .notificationId(notificationId)
                    .recipient(receiverPhone)
                    .templateCode("DEFAULT_SMS")
                    .providerMessageId(result.getProviderMessageId())
                    .status(result.getStatus())
                    .failureReason(result.getFailureReason())
                    .sentAt(sentAt)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            notificationDeliveryService.recordDelivery(delivery);
        }
    }

    // 특정 회원 알림 목록 최신순 페이징 조회
    @Transactional(readOnly = true)
    public List<NotificationResponse> findUserNotificationList(Long receiverId, int size, int offset) {
        return notificationMapper.findUserNotificationList(receiverId, size, offset);
    }

    // 알림 1개 읽음 처리
    @Transactional
    public void markAsRead(Long id, Long receiverId) {
        notificationMapper.markAsRead(id, receiverId);
    }

    // 알림 전체 읽음 처리
    @Transactional
    public void markAllAsRead(Long receiverId) {
        notificationMapper.markAllAsRead(receiverId);
    }

    // 종 모양 옆에 띄울 안 읽은 알림 개수 조회
    @Transactional(readOnly = true)
    public int countUnreadNotifications(Long receiverId) {
        return notificationMapper.countUnreadNotifications(receiverId);
    }
}
