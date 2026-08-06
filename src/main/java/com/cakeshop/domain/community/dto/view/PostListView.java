package com.cakeshop.domain.community.dto.view;

import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 화면 데이터 전달
 * 설명 : PostListView 화면에 전달할 데이터를 정의한다.
 * ******************************
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

    public String authorName() {
        return authorWithdrawn ? WITHDRAWN_AUTHOR_NAME : authorNickname;
    }

    static final String WITHDRAWN_AUTHOR_NAME = "탈퇴한 회원";
}
