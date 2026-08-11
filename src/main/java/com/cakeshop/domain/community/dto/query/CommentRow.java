package com.cakeshop.domain.community.dto.query;

import com.cakeshop.domain.community.entity.CommentStatus;

import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-07
 * 기능 : 커뮤니티 댓글 조회 결과
 * 설명 : comments 만 읽은 댓글 한 줄이다. 작성자는 회원 도메인에서 받아 Service 가 채운다.
 * ******************************
 *
 * <p>댓글 소유권 판단은 작성자 표기가 없어도 되므로 이 타입만으로 끝난다. 근거는
 * {@link PostListRow}와 같다.</p>
 */
public record CommentRow(
        Long id,
        Long postId,
        Long memberId,
        String content,
        CommentStatus status,
        LocalDateTime createdAt
) {

    public boolean isDeleted() {
        return status == CommentStatus.DELETED;
    }
}
