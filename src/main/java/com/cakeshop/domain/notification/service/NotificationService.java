package com.cakeshop.domain.notification.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import com.cakeshop.domain.notification.mapper.NotificationMapper;
import com.cakeshop.domain.notification.dto.view.NotificationResponse;
import java.time.LocalDateTime;
import com.cakeshop.domain.notification.dto.form.NotificationRequest;
import com.cakeshop.domain.notification.entity.DeliveryScope;
import com.cakeshop.domain.notification.entity.Notification;

@Service
@RequiredArgsConstructor
public class NotificationService {
    // TODO: 종단 도메인 — 공개 notify(memberId, type, ...) 만 노출, 업무 도메인 역참조 금지

    private final NotificationMapper notificationMapper;

    // 알림 생성
    @Transactional
    public void makeNotification(NotificationRequest request) {

        // 중복 알림인지 체크하기
        if(request.getEventKey() != null && notificationMapper.existsByReceiverIdAndEventKey(request.getReceiverId(), request.getEventKey())) {
            return;
        }

        // 알림 내용 만들기
        String title = request.getType().getDefaultTitle();
        String content = request.getType().formatContent(request.getArgs());

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
            .deliveryScope(request.getDeliveryScope() != null ? request.getDeliveryScope() : DeliveryScope.WEB_ONLY)
            .isRead(false)
            .eventKey(request.getEventKey())
            .createdAt(LocalDateTime.now())
            .build();

        // 알림 저장하기
        notificationMapper.save(notification);
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

    // 카카오 api 연결해서 알림톡 보내기 (공부하고 추후에 추가)
}
