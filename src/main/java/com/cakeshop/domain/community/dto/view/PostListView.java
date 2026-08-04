package com.cakeshop.domain.community.dto.view;

import java.time.LocalDateTime;

/**
 * 커뮤니티 목록 한 줄에 출력할 게시글 정보를 담는다. 본문(content)은 담지 않는다.
 * TEXT 컬럼이라 목록에서 SELECT하면 20건마다 불필요한 전송이 생긴다(docs/community/DOMAIN.md 6.1).
 *
 * 컴포넌트 순서는 CommunityMapper.xml의 SELECT 컬럼 순서와 맞춰 둔다.
 */
public record PostListView(
        Long id,
        String categoryName,
        String title,
        String authorNickname,
        boolean authorWithdrawn,
        long viewCount,
        long likeCount,
        long commentCount,      // 노출 중인 댓글만 센다
        LocalDateTime createdAt
) {

    /**
     * 화면에 표시할 작성자명. 탈퇴 회원이면 닉네임 대신 "탈퇴한 회원"이 나온다.
     * 탈퇴 회원의 글은 지우지 않고 표시명만 가린다(DOMAIN.md 8).
     */
    public String authorName() {
        return authorWithdrawn ? WITHDRAWN_AUTHOR_NAME : authorNickname;
    }

    /** 탈퇴 회원의 글에 표시할 이름. 상세 화면과 같은 값을 쓴다. */
    static final String WITHDRAWN_AUTHOR_NAME = "탈퇴한 회원";
}
