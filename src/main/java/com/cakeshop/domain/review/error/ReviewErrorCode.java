package com.cakeshop.domain.review.error;

import lombok.RequiredArgsConstructor;

import com.cakeshop.global.error.ErrorCode;

// 후기 도메인이 던지는 거절 사유 목록. BusinessException 이 이 상수 하나를 들고 올라간다
// implements ErrorCode: 도메인마다 따로 있는 오류 enum 을 전역 예외 처리기가 같은 타입으로 받으려고 맞춘 규격이다
// @RequiredArgsConstructor: 아래 final 필드 셋을 선언 순서대로 받는 생성자를 롬복이 만들어 준다
//     private final String code / String message / int status
//     -> ReviewErrorCode(String, String, int) 가 생기고, 각 상수의 괄호 세 칸이 그 자리에 들어간다
@RequiredArgsConstructor
public enum ReviewErrorCode implements ErrorCode {

    // 괄호 = (code, message, status)
    //     code    : 화면·로그에서 사유를 짚는 문자열. 번호는 한 번 붙으면 재사용하지 않는다
    //     message : 사용자에게 그대로 보여 줄 문장
    //     status  : 이 거절을 HTTP 몇 번으로 내보낼지 (400 잘못된 요청 / 403 권한 없음 / 404 없음 / 409 충돌 / 500 서버 실패)
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

    // ErrorCode 가 요구하는 세 메서드. 이름이 getCode() 가 아니라 code() 인 것도 그 규격을 따른 것이다
    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }

}
