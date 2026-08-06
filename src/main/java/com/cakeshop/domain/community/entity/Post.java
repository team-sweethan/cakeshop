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
 * 설명 : Post 도메인의 상태와 값을 정의한다.
 * ******************************
 */
public class Post {

    @Setter
    private Long id;

    private final Long memberId;
    private final Long categoryId;
    private final String title;
    private final String content;

    private Post(Long id, Long memberId, Long categoryId, String title, String content) {
        this.id = id;
        this.memberId = memberId;
        this.categoryId = categoryId;
        this.title = title;
        this.content = content;
    }

    public static Post create(Long memberId, Long categoryId, String title, String content) {
        return new Post(null, memberId, categoryId, title, content);
    }

    public static Post edit(
            Long id, Long memberId, Long categoryId, String title, String content) {
        return new Post(id, memberId, categoryId, title, content);
    }
}
