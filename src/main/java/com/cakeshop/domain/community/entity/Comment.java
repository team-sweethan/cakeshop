package com.cakeshop.domain.community.entity;

import lombok.Getter;
import lombok.Setter;

// @Getter 가 getId(), getPostId(), getMemberId(), getParentCommentId(), getContent() 를 만들어 준다
@Getter
/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 도메인 모델
 * 설명 : Comment 도메인의 상태와 값을 정의한다.
 * ******************************
 */
public class Comment {

    // @Setter 가 여기에만 붙어서 setId() 하나만 생긴다
    @Setter
    private Long id;

    private final Long postId;
    private final Long memberId;

    // 댓글이면 null, 답글이면 부모 댓글의 id 가 들어간다
    private final Long parentCommentId;
    private final String content;

    // 생성자는 하나뿐이고 private 이다
    // 댓글이냐 답글이냐는 아래 두 정적 팩토리가 이 생성자에 무엇을 넘기느냐로만 갈린다
    private Comment(
            Long id, Long postId, Long memberId, Long parentCommentId, String content) {
        this.id = id;
        this.postId = postId;
        this.memberId = memberId;
        this.parentCommentId = parentCommentId;
        this.content = content;
    }

    // 예: Comment.create(37L, 5L, "잘 봤습니다")
    // parentCommentId 자리에 null 을 박아 넣는다 -> 부모 없는 댓글
    public static Comment create(Long postId, Long memberId, String content) {
        return new Comment(null, postId, memberId, null, content);
    }

    // 예: Comment.createReply(37L, 12L, 5L, "저도요")
    // 같은 private 생성자를 부르되 parentCommentId 에 12L 이 들어간다 -> 답글
    // 이름을 create 로 겹쳐 쓰지 않고 createReply 로 나눠 두면
    // 매개변수 개수·순서를 잘못 맞춰 다른 쪽이 불리는 일이 없다
    public static Comment createReply(
            Long postId, Long parentCommentId, Long memberId, String content) {
        return new Comment(null, postId, memberId, parentCommentId, content);
    }
}
