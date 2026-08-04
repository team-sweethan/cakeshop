package com.cakeshop.domain.community.dto.view;

import com.cakeshop.domain.community.entity.CommentStatus;

import java.time.LocalDateTime;

/**
 * 상세 화면의 댓글 한 줄.
 *
 * 삭제된 댓글도 목록에 남는다. 지우지 않고 "삭제된 댓글입니다" 자리 표시로 보여주기
 * 때문이다(docs/community/DOMAIN.md 4.4). 그래서 status가 함께 담긴다.
 *
 * 다만 삭제된 댓글의 content는 SQL이 NULL로 지운다. 자리 표시에는 쓰지 않는 값이라
 * 화면까지 내려보낼 이유가 없고, 내려보내지 않으면 템플릿을 잘못 고쳐도 지워진 댓글이
 * 되살아나지 않는다. 지운 사람이 지우기를 원한 것이 바로 그 본문이다.
 *
 * 컴포넌트 순서는 CommunityMapper.xml의 SELECT 컬럼 순서와 맞춰 둔다.
 */
public record CommentView(
        Long id,
        Long postId,            // 주소의 게시글에 달린 댓글이 맞는지 확인하는 데 쓴다
        Long memberId,          // 작성자. 소유권 판단에 쓴다
        String authorNickname,
        boolean authorWithdrawn,
        String content,         // 삭제된 댓글이면 null이다
        CommentStatus status,
        LocalDateTime createdAt
) {

    /**
     * 화면에 표시할 작성자명. 표시 문구는 게시글과 같아야 하므로 PostListView의 상수를
     * 함께 쓴다(DOMAIN.md 8). 탈퇴 회원의 댓글도 지우지 않고 이름만 가린다.
     */
    public String authorName() {
        return authorWithdrawn ? PostListView.WITHDRAWN_AUTHOR_NAME : authorNickname;
    }

    /** 삭제되어 자리 표시로만 남은 댓글인지. */
    public boolean isDeleted() {
        return status == CommentStatus.DELETED;
    }
}
