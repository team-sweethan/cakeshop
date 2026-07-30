package com.cakeshop.domain.notification.dto.form;

import java.time.LocalDateTime;
import com.cakeshop.domain.notification.entity.DeliveryScope;
import com.cakeshop.domain.notification.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {
    private Long receiverId;
    private Long actorId;
    private Long orderId;
    private Long chatRoomId;
    private Long chatMessageId;
    private Long postId;
    private Long commentId;
    private Long reviewId;
    private Long reviewReplyId;
    private Long userCouponId;
    private NotificationType type;
    private DeliveryScope deliveryScope;
    private String eventKey;
    private String targetUrl;
    private Object[] args; // 동적 알림 문구 치환 인자 (예: "홍길동", "ORD-001")
}