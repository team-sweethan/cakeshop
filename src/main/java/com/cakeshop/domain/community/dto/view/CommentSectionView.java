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
 *
 * <p>자르기의 단위는 댓글 행이 아니라 <b>뿌리 스레드</b>다(조각 8). limit·hasMore·hiddenCount
 * 전부 뿌리 수 기준이고, 답글은 묶음 단위로 접혀 있다가 펼칠 때 통째로 실린다 — 평면 목록의
 * "최신 N행"을 트리에 얹으면 부모 없는 자식이 남기 때문이다.</p>
 */
public record CommentSectionView(
        List<CommentThreadView> threads,
        long publishedCount,
        long rootRowCount,
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

    /** 링크에 실을 값. 기본 분량이면 null 을 주어 파라미터가 생략되게 한다(redirect 규칙과 같다). */
    public Integer limitParam() {
        return limit == DEFAULT_LIMIT ? null : limit;
    }

    public boolean hasMore() {
        return rootRowCount > threads.size();
    }

    public long hiddenCount() {
        return rootRowCount - threads.size();
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
