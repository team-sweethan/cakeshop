package com.cakeshop.domain.community.dto.query;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 화면 데이터 전달
 * 설명 : 댓글 조회에 필요한 전체 행 수와 노출 행 수를 전달한다.
 * ******************************
 */
public record CommentCountRow(
        long rowCount,
        long publishedCount
) {
}
