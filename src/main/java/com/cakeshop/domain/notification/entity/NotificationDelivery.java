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
    private Long id;
    private Long notificationId;
    private String channel;
    private String recipient;
    private String templateCode;
    private String providerMessageId;
    private String status;
    private String failureCode;
    private String failureReason;
    private LocalDateTime requestedAt;
    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime failedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
