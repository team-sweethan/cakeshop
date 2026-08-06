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
        if (orderId != null) return "/orders/" + orderId;
        if (chatRoomId != null) return "/chat";
        if (postId != null && commentId != null) return "/community/" + postId + "#comment-" + commentId;
        if (postId != null) return "/community/" + postId;
        if (reviewId != null) return "/mypage";
        if (userCouponId != null) return "/mypage/coupons";
        return null;
    }
}