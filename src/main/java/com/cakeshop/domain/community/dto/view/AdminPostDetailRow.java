package com.cakeshop.domain.community.dto.view;

import com.cakeshop.domain.community.entity.PostStatus;

import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-07
 * 기능 : 관리자 게시글 상세 조회 결과
 * 설명 : posts 만 읽은 상세 한 줄이다. 작성자와 차단 관리자는 회원 도메인에서 받아 채운다.
 * ******************************
 *
 * <p>차단 관리자를 닉네임이 아니라 {@code blockedBy} ID 로 들고 있다. 차단 기록이 없으면
 * null 이고, 그 자리는 {@link AdminPostDetailView#of}가 처리한다. 원래 SQL 도 LEFT JOIN
 * 이었으므로 null 이 정상인 것은 그대로다.</p>
 */
public record AdminPostDetailRow(
        Long id,
        Long memberId,
        String categoryName,
        String title,
        String content,
        PostStatus status,
        String blockedReason,
        LocalDateTime blockedAt,
        Long blockedBy,
        long viewCount,
        long likeCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
