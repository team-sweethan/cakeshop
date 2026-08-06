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
    // 요청하는 시점에는 아직 ID가 존재하지 않음.
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
    private NotificationType type; // 아직 알림 글이 완성되기 전이라서 type, args만 넘김, 넘기면 서버가 문구를 조립함. type은 채워져서 전달.
    private DeliveryScope deliveryScope; // 직접 WEB_AND_SMS라고 적어주면 그 값으로 가고, 안 정하면(null) 서버가 알아서 WEB_ONLY로 채움
    private String eventKey; 
    private String targetUrl;
    private Object[] args; // 동적 알림 문구 치환 인자 (예: "홍길동")
    // createdAt은 알림이 db에 저장될 때 서버에서 자동으로 값을 채워서 필요가 없음.
}