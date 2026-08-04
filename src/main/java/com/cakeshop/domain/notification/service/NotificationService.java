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

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final SolapiKakaoAlimtalkClient solapiKakaoAlimtalkClient;

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

        // 이벤트 키 결정
        String eventKey = request.getEventKey() != null ? request.getEventKey() : "EVENT_" + java.util.UUID.randomUUID().toString().substring(0, 8);

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

        // 알림 DB 저장하기
        notificationMapper.save(notification);

        // 알림톡 / SMS 외부 발송 연동 (DeliveryScope가 WEB_AND_SMS인 경우)
        if (scope == DeliveryScope.WEB_AND_SMS) {
            if (request.getReceiverId() != null) {
                String receiverPhone = notificationMapper.findReceiverPhone(request.getReceiverId());
                if (receiverPhone != null && !receiverPhone.trim().isEmpty()) { // 공백을 제외한 문자열이 빈 값이 아닌지 확인
                    solapiKakaoAlimtalkClient.sendAlimtalk(
                        notification.getId(),
                        receiverPhone,
                        title,
                        content
                    );
                }
            }
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
