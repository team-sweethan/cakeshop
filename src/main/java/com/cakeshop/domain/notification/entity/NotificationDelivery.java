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
public class NotificationDelivery {
    private Long id; // 발송 이력 ID
    private Long notificationId; // 원본 웹 알림 ID
    // private String channel; // 발송 채널
    private String recipient; // 실제 발송한 전화번호
    private String templateCode; // 카카오 알림톡 템플릿 코드
    private String providerMessageId; // 카카오 또는 발송 업체 메시지 ID
    private String status; // 발송 상태
    // private String failureCode; // 발송 실패 코드
    private String failureReason; // 발송 실패 사유
    // private LocalDateTime requestedAt; // 카카오 API 발송 요청 시간
    // private LocalDateTime sentAt; // 카카오 API 요청 성공 시간
    private LocalDateTime deliveredAt; // 수신자에게 실제 전달 완료된 시간
    private LocalDateTime createdAt; // 발송 이력 생성 시간
    private LocalDateTime updatedAt; // 발송 이력 수정 시간
}
