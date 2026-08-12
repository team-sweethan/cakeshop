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
@RequiredArgsConstructor
public enum CommunityErrorCode implements ErrorCode {

    BLOCKED_POST("COMMUNITY_001", "제재된 게시글입니다.", 403),

    POST_NOT_FOUND("COMMUNITY_002", "게시글을 찾을 수 없습니다.", 404),

    CATEGORY_NOT_FOUND("COMMUNITY_003", "선택할 수 없는 분류입니다.", 400),

    COMMENT_NOT_FOUND("COMMUNITY_004", "댓글을 찾을 수 없습니다.", 404),

    ALREADY_REPORTED("COMMUNITY_005", "이미 신고한 게시글입니다.", 409),

    OWN_POST_REPORT("COMMUNITY_006", "자기 글은 신고할 수 없습니다.", 400),

    INVALID_POST_TRANSITION("COMMUNITY_007", "지금 상태에서 할 수 없는 조치입니다.", 400),

    NOTICE_NOT_FOUND("COMMUNITY_008", "공지사항을 찾을 수 없습니다.", 404),

    INVALID_NOTICE_TRANSITION("COMMUNITY_009", "지금 상태에서 할 수 없는 조치입니다.", 400);

    private final String code;
    private final String message;
    private final int status;

    @Override public String code() { return code; }
    @Override public String message() { return message; }
    @Override public int status() { return status; }
}
