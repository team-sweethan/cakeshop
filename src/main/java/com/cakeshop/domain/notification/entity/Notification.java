package com.cakeshop.domain.notification.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

// DB 테이블 속성들을 그대로 작성한 것
public class Notification {
    private Long id; // 알림 ID
    private Long receiverId; // 알림을 받는 회원 ID
    private Long actorId; // 알림을 발생시킨 회원 or 관리자 ID
    private Long orderId; // 관련 주문 ID
    private Long chatRoomId; // 관련 채팅방 ID
    private Long chatMessageId; // 관련 채팅 메시지 ID
    private Long postId; // 관련 게시글 ID
    private Long commentId; // 관련 댓글 or 대댓글 ID
    private Long reviewId; // 관련 리뷰 ID
    private Long reviewReplyId; // 관련 사장님 리뷰 댓글 ID
    private Long userCouponId; // 고객에게 실제 발급된 쿠폰 ID
    private NotificationType notificationType; // 알림 유형
    private String title; // 알림 제목
    private String content; // 알림 내용
    private DeliveryScope deliveryScope; // 웹 or 웹+카카오 여부
    private boolean isRead; // 웹 알림 읽음 여부
    private LocalDateTime readAt; // 웹 알림을 읽은 시간 (필요 없으면 없앨 예정)
    private String eventKey; // 동일 이벤트의 중복 알림 방지 키
    private LocalDateTime createdAt; // 알림 생성 시간
    private LocalDateTime lastEventAt; // 최근 이벤트 시각
}
