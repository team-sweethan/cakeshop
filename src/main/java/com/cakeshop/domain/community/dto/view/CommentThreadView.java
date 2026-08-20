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
 */
// 상세 화면 댓글 목록의 한 덩어리다. 뿌리 댓글 하나와 거기 매달린 답글이 같이 다닌다.
// expanded = false 면 replies 는 빈 목록이고 화면은 replyCount 로 "답글 3개" 만 그린다.
// replyCount 는 삭제된 자리 표시까지 세므로, 펼치면 적힌 수만큼 줄이 나온다.
public record CommentThreadView(
        CommentView root,
        long replyCount,
        boolean expanded,
        List<CommentView> replies
) {

    // 같은 record 를 접힌 모습/펼친 모습 둘로 만드는 정적 팩토리 한 쌍이다.
    // 생성자를 직접 부르지 않으면 expanded 와 replies 가 어긋날 일이 없다
    // (접혔는데 답글이 실려 있거나, 펼쳤는데 목록이 비어 있는 상태).
    public static CommentThreadView collapsed(CommentView root, long replyCount) {
        return new CommentThreadView(root, replyCount, false, List.of());
    }

    public static CommentThreadView expanded(
            CommentView root, long replyCount, List<CommentView> replies) {
        return new CommentThreadView(root, replyCount, true, replies);
    }

    // 펼쳤는데 상한에 막혀 못 실은 답글이 있는지. 전체 수(replyCount) > 실제로 실은 수(replies.size()).
    public boolean cappedReplies() {
        return expanded && replyCount > replies.size();
    }

    // 화면에 "답글 2개 더 있음" 처럼 적을 수. cappedReplies() 가 true 일 때만 의미가 있다.
    public long hiddenReplyCount() {
        return replyCount - replies.size();
    }
}
