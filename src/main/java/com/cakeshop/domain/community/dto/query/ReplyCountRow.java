package com.cakeshop.domain.community.dto.query;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-19
 * 기능 : 커뮤니티 댓글 조회 결과
 * 설명 : 뿌리 댓글 하나에 달린 답글 행 수다. 자리 표시를 포함해 센다.
 * ******************************
 */
public record ReplyCountRow(
        Long parentId,
        long replyCount
) {
}
