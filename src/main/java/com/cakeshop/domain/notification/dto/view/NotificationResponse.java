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
        // 주문 거절 알림인 경우, orderId 유무와 상관없이 사유 확인을 위해 1:1 채팅 화면(/chat)으로 안내!
        if (type == NotificationType.CUSTOM_ORDER_REJECTED) return "/chat";
        if (orderId != null) return "/orders/" + orderId;
        if (chatRoomId != null) return "/chat";
        if (postId != null && commentId != null) return "/community/" + postId + "#comment-" + commentId;
        if (postId != null) return "/community/" + postId;
        if (reviewId != null) return "/mypage";
        if (userCouponId != null) return "/mypage/coupons";
        return null;
    }
}