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
// 알림 생성 요청 DTO
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
    private NotificationType type; // 아직 알림 글이 완성되기 전이라서 type, args만 넘김, 넘기면 서버가 문구를 조립함.
    private DeliveryScope deliveryScope;
    private String eventKey;
    private String targetUrl;
    private Object[] args; // 동적 알림 문구 치환 인자 (예: "홍길동", "ORD-001")
    // createdAt은 알림이 db에 저장될 때 서버에서 자동으로 값을 채워서 필요가 없음.
}