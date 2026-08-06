package com.cakeshop.domain.review.error;

import com.cakeshop.global.error.ErrorCode;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-06
 * 기능 : 리뷰 도메인 오류 코드
 * 설명 : 후기 작성에서 쓰는 도메인 오류를 정의한다.
 * ******************************
 *
 * <p><b>소유권 위반 전용 코드는 두지 않는다.</b> 남의 주문 상품에 후기를 쓰려 하면 403 이
 * 아니라 {@link #ORDER_ITEM_NOT_FOUND} 404 다 — 두 응답이 갈리면 주소를 훑어 남의 주문 상품
 * 존재를 알아낼 수 있어 가리려던 것이 그대로 드러난다(SPEC 2.5).
 */
public enum ReviewErrorCode implements ErrorCode {

    NOT_PICKED_UP("REVIEW_001", "픽업 완료된 주문만 후기를 작성할 수 있습니다.", 400),

    /** 없는 주문 상품과 <b>남의</b> 주문 상품 둘 다 이 코드다. */
    ORDER_ITEM_NOT_FOUND("REVIEW_002", "주문 상품을 찾을 수 없습니다.", 404),

    ALREADY_REVIEWED("REVIEW_003", "이미 후기를 작성한 주문 상품입니다.", 409),

    INVALID_RATING("REVIEW_004", "평점은 1점에서 5점 사이여야 합니다.", 400);

    private final String code;
    private final String message;
    private final int status;

    ReviewErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
