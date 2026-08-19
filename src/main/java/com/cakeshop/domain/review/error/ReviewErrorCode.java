package com.cakeshop.domain.review.error;

import lombok.RequiredArgsConstructor;

import com.cakeshop.global.error.ErrorCode;

@RequiredArgsConstructor
public enum ReviewErrorCode implements ErrorCode {

    NOT_PICKED_UP("REVIEW_001", "픽업 완료된 주문만 후기를 작성할 수 있습니다.", 400),
    REVIEW_NOT_FOUND("REVIEW_002", "후기를 찾을 수 없습니다.", 404),
    ORDER_ITEM_NOT_FOUND("REVIEW_003", "주문 상품을 찾을 수 없습니다.", 404),
    ALREADY_REVIEWED("REVIEW_004", "이미 후기를 작성한 주문 상품입니다.", 409),
    BLOCKED_REVIEW("REVIEW_005", "숨김 처리된 후기입니다.", 403),
    INVALID_REVIEW_TRANSITION("REVIEW_006", "지금 상태에서 할 수 없는 조치입니다.", 400),
    ALREADY_REPLIED("REVIEW_007", "이미 답글이 달린 후기입니다.", 409),
    REPLY_NOT_FOUND("REVIEW_008", "답글을 찾을 수 없습니다.", 404),
    INVALID_IMAGE_FILE("REVIEW_009", "JPG 또는 PNG 이미지만 첨부할 수 있습니다.", 400),
    IMAGE_TOO_LARGE("REVIEW_010", "이미지는 한 장에 5MB까지 첨부할 수 있습니다.", 400),
    IMAGE_LIMIT_EXCEEDED("REVIEW_011", "이미지는 후기당 3장까지 첨부할 수 있습니다.", 400),
    IMAGE_UPLOAD_FAILED("REVIEW_012", "후기 이미지를 저장하지 못했습니다.", 500);

    private final String code;
    private final String message;
    private final int status;

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
