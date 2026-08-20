package com.cakeshop.domain.community.entity;

import lombok.Getter;
import lombok.Setter;

// @Getter 가 getId(), getPostId(), getImageUrl(), getSortOrder() 를 대신 만들어 준다
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

    // @Setter 가 여기에만 붙어서 setId() 하나만 생긴다
    @Setter
    private Long id;

    private final Long postId;
    private final String imageUrl;

    // int 는 기본형이라 null 이 될 수 없다 -> 값이 없는 상태 자체를 만들 수 없다
    // 위의 Long postId 는 참조형이라 null 이 들어갈 수 있는 것과 다르다
    private final int sortOrder;

    // Post 와 같은 모양: 생성자를 private 으로 닫고 create() 만 열어 둔다
    private PostImage(Long id, Long postId, String imageUrl, int sortOrder) {
        this.id = id;
        this.postId = postId;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
    }

    // 예: PostImage.create(37L, "/uploads/community/a.png", 0)
    // 매개변수에 id 가 없으니 id 자리에는 항상 null 이 들어간다
    public static PostImage create(Long postId, String imageUrl, int sortOrder) {
        return new PostImage(null, postId, imageUrl, sortOrder);
    }

}
