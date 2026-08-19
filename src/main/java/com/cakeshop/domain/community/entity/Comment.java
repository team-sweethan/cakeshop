package com.cakeshop.domain.community.entity;

import lombok.Getter;
import lombok.Setter;

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

    @Setter
    private Long id;

    private final Long postId;
    private final Long memberId;
    private final Long parentCommentId;
    private final String content;

    private Comment(
            Long id, Long postId, Long memberId, Long parentCommentId, String content) {
        this.id = id;
        this.postId = postId;
        this.memberId = memberId;
        this.parentCommentId = parentCommentId;
        this.content = content;
    }

    public static Comment create(Long postId, Long memberId, String content) {
        return new Comment(null, postId, memberId, null, content);
    }

    /*
     * 답글의 postId 는 저장에 쓰이지 않는다. insertReply 가 부모 행에서 가져오고 이 값은 부모를
     * 고르는 조건으로만 간다 — 답글이 부모와 다른 글에 붙을 수 없어야 해서다.
     */
    public static Comment createReply(
            Long postId, Long parentCommentId, Long memberId, String content) {
        return new Comment(null, postId, memberId, parentCommentId, content);
    }
}
