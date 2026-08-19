package com.cakeshop.domain.community.dto.query;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 화면 데이터 전달
 * 설명 : 댓글 구역에 필요한 뿌리 행 수와 노출 중 행 수를 전달한다.
 * ******************************
 *
 * <p>둘의 기준이 다른 것이 의도다 — 더 펼칠 게 남았는지는 뿌리(자리 표시 포함)로 세고,
 * 화면의 `댓글 N`은 답글까지 포함해 노출 중인 것만 센다(specs/community-comment.md B4).</p>
 */
public record CommentCountRow(
        long rootRowCount,
        long publishedCount
) {
}
