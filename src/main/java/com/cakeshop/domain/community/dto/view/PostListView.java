package com.cakeshop.domain.community.dto.view;

import java.time.LocalDateTime;

/**
 * 커뮤니티 목록 한 줄에 출력할 게시글 정보를 담는다.
 *
 * <p>본문(content)은 담지 않는다. TEXT 컬럼이라 목록에서 SELECT하면 20건마다 불필요한
 * 전송이 생긴다(docs/community/DOMAIN.md 6.1).
 *
 * <p>컴포넌트 순서는 {@code CommunityMapper.xml}의 SELECT 컬럼 순서와 맞춰 둔다.
 *
 * @param id 게시글 식별자
 * @param categoryName 카테고리 이름
 * @param title 제목
 * @param authorNickname 작성자 닉네임
 * @param authorWithdrawn 작성자가 탈퇴한 회원인지 여부
 * @param viewCount 조회수
 * @param likeCount 좋아요 수
 * @param commentCount 노출 중인 댓글 수
 * @param createdAt 작성 시각
 */
public record PostListView(
        Long id,
        String categoryName,
        String title,
        String authorNickname,
        boolean authorWithdrawn,
        long viewCount,
        long likeCount,
        long commentCount,
        LocalDateTime createdAt
) {

    /**
     * 화면에 표시할 작성자명을 반환한다.
     *
     * <p>탈퇴 회원의 글은 지우지 않고 표시명만 가린다(DOMAIN.md 8).
     *
     * @return 탈퇴 회원이면 "탈퇴한 회원", 아니면 작성자 닉네임
     */
    public String authorName() {
        return authorWithdrawn ? WITHDRAWN_AUTHOR_NAME : authorNickname;
    }

    /** 탈퇴 회원의 글에 표시할 이름. 상세 화면과 같은 값을 쓴다. */
    static final String WITHDRAWN_AUTHOR_NAME = "탈퇴한 회원";
}
