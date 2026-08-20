package com.cakeshop.domain.community.error;

import lombok.RequiredArgsConstructor;

import com.cakeshop.global.error.ErrorCode;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 오류 정의
 * 설명 : CommunityErrorCode 도메인에서 사용하는 오류 코드를 정의한다.
 * ******************************
 */
// 커뮤니티가 거절할 상황을 한 자리에 모아 둔 enum 이다. 상수 하나가 값 셋을 들고 다닌다.
// 이 값들이 흘러가는 곳:
//   Service 가 throw new BusinessException(POST_NOT_FOUND) 로 던진다
//     -> status(404)  : HTTP 응답 코드가 된다
//     -> message(...) : 사용자에게 보일 문장. 오류 화면이나 폼 오류 문구로 뜬다
//     -> code("COMMUNITY_002") : 어떤 거절인지 구분하는 이름표. 로그와 화면 분기가 이걸로 판별한다
// 폼으로 돌려보낼 거절이면 컨트롤러가 code 와 message 를 bindingResult.rejectValue 에 그대로 넘긴다.
//
// implements ErrorCode: 다른 도메인의 오류 enum 들과 같은 규격(code/message/status)을 맞춘 것이다.
// 그래서 전역 예외 처리기는 어느 도메인 오류인지 몰라도 ErrorCode 하나로 응답을 만들 수 있다.
@RequiredArgsConstructor
public enum CommunityErrorCode implements ErrorCode {

    // 상수 뒤 괄호가 (code, message, status) 순서다. 번호는 한 번 쓰면 재사용하지 않는다.
    BLOCKED_POST("COMMUNITY_001", "제재된 게시글입니다.", 403),

    POST_NOT_FOUND("COMMUNITY_002", "게시글을 찾을 수 없습니다.", 404),

    CATEGORY_NOT_FOUND("COMMUNITY_003", "선택할 수 없는 분류입니다.", 400),

    COMMENT_NOT_FOUND("COMMUNITY_004", "댓글을 찾을 수 없습니다.", 404),

    ALREADY_REPORTED("COMMUNITY_005", "이미 신고한 게시글입니다.", 409),

    OWN_POST_REPORT("COMMUNITY_006", "자기 글은 신고할 수 없습니다.", 400),

    INVALID_POST_TRANSITION("COMMUNITY_007", "지금 상태에서 할 수 없는 조치입니다.", 400),

    NOTICE_NOT_FOUND("COMMUNITY_008", "공지사항을 찾을 수 없습니다.", 404),

    INVALID_NOTICE_TRANSITION("COMMUNITY_009", "지금 상태에서 할 수 없는 조치입니다.", 400),

    INVALID_IMAGE_FILE("COMMUNITY_010", "JPG 또는 PNG 이미지만 첨부할 수 있습니다.", 400),

    IMAGE_TOO_LARGE("COMMUNITY_011", "이미지는 한 장에 5MB까지 첨부할 수 있습니다.", 400),

    IMAGE_LIMIT_EXCEEDED("COMMUNITY_012", "이미지는 게시글당 5장까지 첨부할 수 있습니다.", 400),

    IMAGE_NOT_FOUND("COMMUNITY_013", "첨부 이미지를 찾을 수 없습니다.", 404);

    private final String code;
    private final String message;
    private final int status;

    // @RequiredArgsConstructor: final 필드 셋을 순서대로 받는 생성자를 롬복이 만들어 준다.
    // 위 상수들의 괄호가 그 생성자를 부르는 자리라서, 필드 순서를 바꾸면 값이 어긋난다.

    // ErrorCode 규격이 요구하는 세 메서드. getCode 가 아니라 code 인 것에 주의한다.
    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
