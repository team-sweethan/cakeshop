package com.cakeshop.domain.notification.service;

import java.util.List;
import java.time.Duration;
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
import com.cakeshop.domain.notification.entity.NotificationType;

import org.springframework.messaging.simp.SimpMessagingTemplate;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.cakeshop.domain.member.service.MemberNotificationQueryService;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final SolapiKakaoAlimtalkClient solapiKakaoAlimtalkClient;
    private final NotificationDeliveryService notificationDeliveryService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MemberNotificationQueryService memberNotificationQueryService;

    // 알림 생성
    @Transactional
    public void makeNotification(NotificationRequest request) {

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
                    : request.getOrderId() != null ? request.getOrderId()
                    : request.getPostId() != null ? request.getPostId()
                    : request.getReviewId() != null ? request.getReviewId()
                    : request.getUserCouponId() != null ? request.getUserCouponId()
                    : request.getChatRoomId() != null ? "ROOM_" + request.getChatRoomId()
                    : java.util.UUID.randomUUID().toString();
            eventKey = request.getType().name() + ":" + request.getReceiverId() + ":" + targetId;
        }

        // 방 단위 묶음 알림(ROOM_...) 여부 확인 (메시지 ID 단위 단건 알림 키는 멱등 처리)
        boolean isBundleNotification = request.getChatRoomId() != null
            || (eventKey != null && eventKey.contains("ROOM_"));

        // 중복 eventKey가 존재하는 경우 처리
        if (notificationMapper.existsByReceiverIdAndEventKey(request.getReceiverId(), eventKey)) {
            if (isBundleNotification) {
                // 비관적 잠금(FOR UPDATE) 획득 후 최신 시각(now) 측정하여 0.001초 동시성 덮어쓰기 방지
                Notification existing = notificationMapper.findNotificationByReceiverAndEventKeyForUpdate(request.getReceiverId(), eventKey);
                LocalDateTime now = LocalDateTime.now();
                notificationMapper.updateLastEventAtAndUnread(request.getReceiverId(), eventKey, title, content, now);

                // 30분 쿨타임 체크: 직전 메시지 시각(last_event_at) 대비 30분 이상 경과했으면 새 묶음으로 간주해 SMS 재발송
                long minutesGap = (existing != null && existing.getLastEventAt() != null)
                    ? Duration.between(existing.getLastEventAt(), now).toMinutes() : 999;

                if (minutesGap >= 30 && scope == DeliveryScope.WEB_AND_SMS && request.getReceiverId() != null) {
                    Long existingId = existing != null ? existing.getId() : notificationMapper.findIdByReceiverIdAndEventKey(request.getReceiverId(), eventKey);
                    String receiverPhone = notificationMapper.findReceiverPhone(request.getReceiverId(), request.getOrderId());
                    registerSmsSending(existingId, receiverPhone, title, content);
                }

                Long bundleId = existing != null ? existing.getId() : notificationMapper.findIdByReceiverIdAndEventKey(request.getReceiverId(), eventKey);
                NotificationResponse bundleResponse = NotificationResponse.builder()
                        .id(bundleId)
                        .type(request.getType())
                        .title(title)
                        .content(content)
                        .isRead(false)
                        .createdAt(existing != null ? existing.getCreatedAt() : now)
                        .orderId(request.getOrderId())
                        .chatRoomId(request.getChatRoomId())
                        .commentId(request.getCommentId())
                        .postId(request.getPostId())
                        .reviewId(request.getReviewId())
                        .userCouponId(request.getUserCouponId())
                        .lastEventAt(now)
                        .build();
                registerWebSocketSending(request.getReceiverId(), bundleResponse);
            } else {
                // 일반 알림(주문 등) 중복 시 기존 SMS 전송이 완료(SENT)되지 않았고 최대 시도(2회) 미만인 경우에만 SMS를 재발송한다.
                if (scope == DeliveryScope.WEB_AND_SMS && request.getReceiverId() != null) {
                    Long existingId = notificationMapper.findIdByReceiverIdAndEventKey(request.getReceiverId(), eventKey);
                    if (existingId != null && !notificationMapper.hasSentDelivery(existingId)
                            && notificationMapper.countDeliveryAttempts(existingId) < 2) {
                        String receiverPhone = notificationMapper.findReceiverPhone(request.getReceiverId(), request.getOrderId());
                        registerSmsSending(existingId, receiverPhone, title, content);
                    }
                }
            }
            return;
        }

        LocalDateTime createNow = LocalDateTime.now();

        // 신규 알림 생성
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
            .createdAt(createNow)
            .lastEventAt(createNow)
            .build();

        // 알림 DB 저장하기 (동시 요청으로 인한 중복 키 예외 멱등 처리)
        try {
            notificationMapper.save(notification);
        } catch (DuplicateKeyException e) {
            if (isBundleNotification) {
                Notification existing = notificationMapper.findNotificationByReceiverAndEventKeyForUpdate(request.getReceiverId(), eventKey);
                LocalDateTime updateNow = LocalDateTime.now();
                notificationMapper.updateLastEventAtAndUnread(request.getReceiverId(), eventKey, title, content, updateNow);

                long minutesGap = (existing != null && existing.getLastEventAt() != null)
                    ? Duration.between(existing.getLastEventAt(), updateNow).toMinutes() : 999;

                if (minutesGap >= 30 && scope == DeliveryScope.WEB_AND_SMS && request.getReceiverId() != null) {
                    Long existingId = existing != null ? existing.getId() : notificationMapper.findIdByReceiverIdAndEventKey(request.getReceiverId(), eventKey);
                    String receiverPhone = notificationMapper.findReceiverPhone(request.getReceiverId(), request.getOrderId());
                    registerSmsSending(existingId, receiverPhone, title, content);
                }

                Long bundleId = existing != null ? existing.getId() : notificationMapper.findIdByReceiverIdAndEventKey(request.getReceiverId(), eventKey);
                NotificationResponse bundleResponse = NotificationResponse.builder()
                        .id(bundleId)
                        .type(request.getType())
                        .title(title)
                        .content(content)
                        .isRead(false)
                        .createdAt(existing != null ? existing.getCreatedAt() : updateNow)
                        .orderId(request.getOrderId())
                        .chatRoomId(request.getChatRoomId())
                        .commentId(request.getCommentId())
                        .postId(request.getPostId())
                        .reviewId(request.getReviewId())
                        .userCouponId(request.getUserCouponId())
                        .lastEventAt(updateNow)
                        .build();
                registerWebSocketSending(request.getReceiverId(), bundleResponse);
            }
            return;
        }

        // 알림톡 / SMS 외부 발송 연동 (DB 트랜잭션 커밋 완료 후 안전하게 발송)
        if (scope == DeliveryScope.WEB_AND_SMS && request.getReceiverId() != null) {
            String receiverPhone = notificationMapper.findReceiverPhone(request.getReceiverId(), request.getOrderId());
            registerSmsSending(notification.getId(), receiverPhone, title, content);
        }

        // 알림 실시간 웹소켓(STOMP) 전파 (DB 트랜잭션 커밋 완료 후 안전하게 방송)
        NotificationResponse responseDTO = NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getNotificationType())
                .title(title)
                .content(content)
                .isRead(false)
                .createdAt(notification.getCreatedAt())
                .orderId(notification.getOrderId())
                .chatRoomId(notification.getChatRoomId())
                .commentId(notification.getCommentId())
                .postId(notification.getPostId())
                .reviewId(notification.getReviewId())
                .userCouponId(notification.getUserCouponId())
                .lastEventAt(notification.getLastEventAt())
                .build();
        registerWebSocketSending(request.getReceiverId(), responseDTO);
    }

    private void registerSmsSending(Long notificationId, String receiverPhone, String title, String content) {
        if (notificationId == null) return;
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

    private void registerWebSocketSending(Long receiverId, NotificationResponse responseDTO) {
        if (responseDTO == null) return;
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executeWebSocketSending(receiverId, responseDTO);
                }
            });
        } else {
            executeWebSocketSending(receiverId, responseDTO);
        }
    }

    private void executeWebSocketSending(Long receiverId, NotificationResponse responseDTO) {
        try {
            if (receiverId != null && memberNotificationQueryService.isMemberActive(receiverId)) {
                messagingTemplate.convertAndSend("/topic/notifications/" + receiverId, responseDTO);
            }
        } catch (Exception e) {
            // 웹소켓 전파 예외는 메인 발송 로직에 영향을 주지 않는다.
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
