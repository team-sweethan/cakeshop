package com.cakeshop.domain.notification.entity;

import lombok.Getter;

@Getter
public enum NotificationType {

    // === 고객 관련 알림 (13종) ===
    ORDER_PAID("결제 완료", "결제가 완료되었습니다. 주문이 정상적으로 접수되었습니다."),
    CUSTOM_ORDER_PAID("주문제작 결제 완료", "주문제작 결제가 완료되었습니다. 주문 내용을 확인하고 있습니다."),
    CUSTOM_ORDER_IN_PRODUCTION("주문 제작중", "주문제작 요청이 승인되어 제작이 시작되었습니다."),
    ORDER_CANCELED("주문 취소", "주문 취소가 완료되었습니다."),
    CUSTOM_ORDER_REJECTED("주문 거절", "주문제작 요청이 반려되었습니다. 1:1 문의에서 사유를 확인해 주세요."),
    CUSTOMER_PICKUP_REMINDER_TOMORROW("픽업 하루 전 안내", "주문하신 케이크가 내일 픽업 예정입니다. 예약 시간을 확인해 주세요."),
    CUSTOMER_PICKUP_REMINDER_TODAY("픽업 안내", "주문하신 케이크가 금일 픽업 예정입니다. 예약 시간을 확인해 주세요."),
    CUSTOMER_ORDER_PICKED_UP("픽업 완료", "픽업이 완료되었습니다. 소중한 리뷰를 남겨 주세요"),
    CUSTOMER_CHAT("채팅 답변", "새로운 채팅 메시지가 도착했습니다. 확인해주세요."),
    CUSTOMER_COMMENT("댓글 알림", "%s님이 회원님의 게시글에 댓글을 남겼습니다."),
    CUSTOMER_COMMENT_REPLY("답글 알림", "%s님이 회원님의 댓글에 답글을 남겼습니다."),
    CUSTOMER_REVIEW("리뷰 답글 알림", "사장님이 회원님의 리뷰에 답글을 남겼습니다."),
    COUPON("쿠폰 발급", "%s 쿠폰이 발급되었습니다."),

    // === 관리자 관련 알림 (11종) ===
    NEW_ORDER("신규 주문", "새로운 일반 케이크 주문이 접수되었습니다."),
    NEW_CUSTOM_ORDER("신규 주문 제작 주문", "확인이 필요한 주문제작 주문이 접수되었습니다. 확인해주세요."),
    ORDER_CANCEL_REQUEST("주문 취소", "%s 주문이 취소되었습니다."),
    NEW_REVIEW("신규 리뷰", "새로운 리뷰가 등록되었습니다."),
    ADMIN_COMMENT("댓글 알림", "%s님이 회원님의 게시글에 댓글을 남겼습니다."),
    ADMIN_COMMENT_REPLY("답글 알림", "%s님이 회원님의 댓글에 답글을 남겼습니다."),
    ADMIN_PICKUP_REMINDER_TOMORROW("픽업 하루 전", "%s 주문이 내일 픽업 예정입니다."),
    ADMIN_PICKUP_REMINDER_TODAY("픽업 당일", "%s 주문이 오늘 픽업 예정입니다."),
    ADMIN_PICKEDUP("픽업 완료", "%s 주문이 픽업 완료되었습니다."),
    ADMIN_CHAT("채팅 문의", "%s 고객님께서 새로운 채팅 문의를 남기셨습니다. 확인해주세요"),
    REFUND_FAILED("환불 실패", "%s 주문의 결제 취소 처리에 실패했습니다. 확인해주세요.");

    private final String defaultTitle;
    private final String defaultContent;

    NotificationType(String defaultTitle, String defaultContent) {
        this.defaultTitle = defaultTitle;
        this.defaultContent = defaultContent;
    }

    public String formatContent(Object... args) {
        return String.format(this.defaultContent, args); // 알림 내용 만들어서 반환.
    }
}
