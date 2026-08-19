package com.cakeshop.domain.community.dto.view;

import java.util.List;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-19
 * 기능 : 커뮤니티 화면 데이터 전달
 * 설명 : 뿌리 댓글 하나와 접힌 답글 수, 펼쳤을 때의 답글 목록을 전달한다.
 * ******************************
 *
 * <p>답글 수는 자리 표시를 포함해 센다 — 펼치면 정확히 그 수만큼 줄이 나온다는 뜻이고,
 * 삭제된 답글만 남은 묶음도 펼치는 길이 사라지지 않는다(specs/community-comment.md B4).</p>
 */
public record CommentThreadView(
        CommentView root,
        long replyCount,
        boolean expanded,
        List<CommentView> replies
) {

    public static CommentThreadView collapsed(CommentView root, long replyCount) {
        return new CommentThreadView(root, replyCount, false, List.of());
    }

    public static CommentThreadView expanded(
            CommentView root, long replyCount, List<CommentView> replies) {
        return new CommentThreadView(root, replyCount, true, replies);
    }

    /** 상한에 막혀 못 실은 답글이 있는지. 감추지 않고 화면에 적는다(B4와 같은 규칙). */
    public boolean cappedReplies() {
        return expanded && replyCount > replies.size();
    }

    public long hiddenReplyCount() {
        return replyCount - replies.size();
    }
}
