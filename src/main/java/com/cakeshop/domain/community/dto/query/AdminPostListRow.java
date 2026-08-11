package com.cakeshop.domain.community.dto.query;

import com.cakeshop.domain.community.entity.PostStatus;

import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-07
 * 기능 : 관리자 게시글 목록 조회 결과
 * 설명 : posts 만 읽은 목록 한 줄이다. 작성자는 회원 도메인에서 받아 Service 가 채운다.
 * ******************************
 *
 * <p>작성자 자리를 두지 않는 이유는 {@link PostListRow}와 같다.</p>
 */
public record AdminPostListRow(
        Long id,
        Long memberId,
        String categoryName,
        String title,
        PostStatus status,
        long pendingReportCount,
        LocalDateTime createdAt
) {
}
