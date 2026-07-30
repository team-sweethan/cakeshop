package com.cakeshop.domain.notification.dto.view;

import java.time.LocalDateTime;
import com.cakeshop.domain.notification.entity.NotificationType;
import lombok.Data;

@Data
public class NotificationResponse {
    private Long id;
    private NotificationType type;
    private String title;
    private String content;
    private boolean isRead;
    private LocalDateTime createdAt;
    private Long orderId;
    private Long chatRoomId;
    private Long postId;
    private Long reviewId;
    private Long userCouponId;
    private String targetUrl;

    public String getTargetUrl() {
        if (targetUrl != null) return targetUrl;
        if (orderId != null) return "/orders/" + orderId;
        if (chatRoomId != null) return "/chat";
        if (postId != null) return "/community/" + postId;
        if (reviewId != null) return "/reviews";
        if (userCouponId != null) return "/mypage/coupons";
        return null;
    }
}