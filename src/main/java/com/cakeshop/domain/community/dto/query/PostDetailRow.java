package com.cakeshop.domain.community.dto.query;

import com.cakeshop.domain.community.entity.PostStatus;

import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-07
 * 기능 : 커뮤니티 상세 조회 결과
 * 설명 : posts 만 읽은 상세 한 줄이다. 작성자는 회원 도메인에서 받아 Service 가 채운다.
 * ******************************
 *
 * <p>노출·소유권 판단은 작성자 표기가 없어도 되므로 이 타입만으로 끝난다. 근거는
 * {@link PostListRow}와 같다.</p>
 */
public record PostDetailRow(
        Long id,
        Long memberId,
        Long categoryId,
        String categoryName,
        String title,
        String content,
        PostStatus status,
        String blockedReason,
        long viewCount,
        long likeCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 화면의 {@code (수정됨)} 표기와 같은 판단이다.
     *
     * <p>화면의 수정 여부와 같은 규칙을 여기에도 둔 것은, 차단·해제와
     * 조회수·좋아요가 {@code updated_at}을 보존하는지를 SQL 층에서 바로 확인하기
     * 위해서다. 작성자를 채우지 않고도 볼 수 있어야 하는 검사다.</p>
     */
    public boolean isEdited() {
        return createdAt != null && updatedAt != null && updatedAt.isAfter(createdAt);
    }
}
