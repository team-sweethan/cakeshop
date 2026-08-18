package com.cakeshop.domain.notification.dto.view;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
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
public class NotificationResponse {
    private Long id;
    private NotificationType type;
    private String title;
    private String content;

    @JsonProperty("isRead")
    private boolean isRead;
    private LocalDateTime createdAt;
    private Long orderId;
    private Long chatRoomId;
    private Long commentId;
    private Long postId;
    private Long reviewId;
    private Long userCouponId;
    private String targetUrl;
    private LocalDateTime lastEventAt;

    public String getTargetUrl() {
        if (targetUrl != null) return targetUrl;
        
        // 관리자 알림인 경우 /admin/... URL 반환
        if (type != null && isAdminType(type)) {
            if (chatRoomId != null) return "/admin/chat?roomId=" + chatRoomId;
            if (type == NotificationType.ADMIN_CHAT) return "/admin/chat";
            if (orderId != null) return "/admin/orders/" + orderId;
            if (postId != null) return "/admin/community/" + postId;
            if (reviewId != null) return "/admin/reviews";
            return "/admin";
        }

        // 고객 알림인 경우 /... URL 반환
        if (type == NotificationType.CUSTOM_ORDER_REJECTED) return "/chat";
        if (orderId != null) return "/orders/" + orderId;
        if (chatRoomId != null) return "/chat";
        if (postId != null && commentId != null) return "/community/" + postId + "#comment-" + commentId;
        if (postId != null) return "/community/" + postId;
        if (reviewId != null) return "/mypage";
        if (userCouponId != null) return "/mypage/coupons";
        return null;
    }

    private boolean isAdminType(NotificationType type) {
        return type.name().startsWith("ADMIN_")
                || type == NotificationType.NEW_ORDER
                || type == NotificationType.NEW_CUSTOM_ORDER
                || type == NotificationType.ORDER_CANCEL_REQUEST
                || type == NotificationType.NEW_REVIEW
                || type == NotificationType.REFUND_FAILED;
    }
}