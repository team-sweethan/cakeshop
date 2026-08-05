package com.cakeshop.domain.community.dto.view;

import java.util.List;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 화면 데이터 전달
 * 설명 : CommentSectionView 화면에 전달할 데이터를 정의한다.
 * ******************************
 */
public record CommentSectionView(
        List<CommentView> comments,
        long publishedCount,
        long rowCount,
        int limit
) {

    public static final int DEFAULT_LIMIT = 20;

    public static final int STEP = 20;

    public static final int MAX_LIMIT = 200;

    public static int clampLimit(Integer requested) {
        if (requested == null || requested < DEFAULT_LIMIT) {
            return DEFAULT_LIMIT;
        }

        return Math.min(requested, MAX_LIMIT);
    }

    public boolean hasMore() {
        return rowCount > comments.size();
    }

    public long hiddenCount() {
        return rowCount - comments.size();
    }

    public int nextLimit() {
        return Math.min(limit + STEP, MAX_LIMIT);
    }

    public boolean canLoadMore() {
        return hasMore() && nextLimit() > limit;
    }

    public boolean cappedByLimit() {
        return hasMore() && nextLimit() <= limit;
    }
}
