package com.cakeshop.domain.community.dto.view;

import com.cakeshop.domain.community.entity.PostStatus;

import java.time.LocalDateTime;

/**
 * 커뮤니티 게시글 상세 화면에 출력할 정보를 담는다.
 *
 * <p>Mapper는 상태와 무관하게 게시글을 조회한다. 어떤 상태를 누구에게 보여줄지는
 * {@code CommunityService}가 판단한다(docs/community/DOMAIN.md 4.3). 그래서 이 DTO에는
 * {@code status}와 {@code blockedReason}이 함께 담긴다.
 *
 * <p>컴포넌트 순서는 {@code CommunityMapper.xml}의 SELECT 컬럼 순서와 맞춰 둔다.
 *
 * @param id 게시글 식별자
 * @param memberId 작성자 식별자. 소유권 판단에 쓴다
 * @param categoryId 카테고리 식별자
 * @param categoryName 카테고리 이름
 * @param title 제목
 * @param content 본문. 순수 텍스트이며 HTML을 허용하지 않는다(DOMAIN.md 7)
 * @param authorNickname 작성자 닉네임
 * @param authorWithdrawn 작성자가 탈퇴한 회원인지 여부
 * @param status 게시글 상태
 * @param blockedReason 관리자가 기록한 차단 사유
 * @param viewCount 조회수
 * @param likeCount 좋아요 수
 * @param createdAt 작성 시각
 * @param updatedAt 마지막 수정 시각
 */
public record PostDetailView(
        Long id,
        Long memberId,
        Long categoryId,
        String categoryName,
        String title,
        String content,
        String authorNickname,
        boolean authorWithdrawn,
        PostStatus status,
        String blockedReason,
        long viewCount,
        long likeCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 화면에 표시할 작성자명을 반환한다.
     *
     * <p>표시 문구는 목록과 같아야 하므로 {@link PostListView}의 상수를 함께 쓴다(DOMAIN.md 8).
     *
     * @return 탈퇴 회원이면 "탈퇴한 회원", 아니면 작성자 닉네임
     */
    public String authorName() {
        return authorWithdrawn ? PostListView.WITHDRAWN_AUTHOR_NAME : authorNickname;
    }

    /**
     * 작성 후 수정된 적이 있는지 확인한다.
     *
     * <p>수정 이력 테이블을 두지 않고 두 시각의 차이로만 판단한다(DOMAIN.md 6.3).
     * 조회수 증가가 {@code updated_at}을 건드리면 이 값이 무너지므로,
     * 조회수 UPDATE는 {@code updated_at}을 명시적으로 보존한다(CommunityMapper.xml 참고).
     *
     * @return 수정된 적이 있으면 {@code true}
     */
    public boolean isEdited() {
        return createdAt != null && updatedAt != null && updatedAt.isAfter(createdAt);
    }

    /**
     * 관리자에게 차단된 게시글인지 확인한다.
     *
     * @return 상태가 {@code BLOCKED}이면 {@code true}
     */
    public boolean isBlocked() {
        return status == PostStatus.BLOCKED;
    }
}
