package com.cakeshop.domain.community.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-18
 * 기능 : 커뮤니티 도메인 모델
 * 설명 : PostImage 도메인의 상태와 값을 정의한다.
 * ******************************
 */
public class PostImage {

    @Setter
    private Long id;

    private final Long postId;
    private final String imageUrl;
    private final int sortOrder;

    private PostImage(Long id, Long postId, String imageUrl, int sortOrder) {
        this.id = id;
        this.postId = postId;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
    }

    public static PostImage create(Long postId, String imageUrl, int sortOrder) {
        return new PostImage(null, postId, imageUrl, sortOrder);
    }

}
