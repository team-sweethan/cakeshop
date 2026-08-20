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
// 상세 화면 댓글 영역 전체. threads 는 CommentThreadView(뿌리 + 답글) 의 목록이다.
// 세는 단위가 전부 "뿌리 스레드" 라는 점이 요점이다 — limit, rootRowCount, hasMore 모두 뿌리 수 기준이고,
// 답글은 묶음째 따라오므로 화면에 실제로 그려지는 줄 수와는 다르다.
public record CommentSectionView(
        List<CommentThreadView> threads,
        long publishedCount,
        long rootRowCount,
        int limit
) {

    // 처음 20개 -> 더 보기 한 번에 +20 -> 아무리 눌러도 200개에서 멈춘다.
    public static final int DEFAULT_LIMIT = 20;
    public static final int STEP = 20;
    public static final int MAX_LIMIT = 200;

    // 주소로 들어온 comments 값을 20~200 사이로 눌러 담는다.
    // 예시 요청: GET /community/37?comments=60 -> 60 / comments=9999 -> 200 / 없거나 20 미만 -> 20
    public static int clampLimit(Integer requested) {
        if (requested == null || requested < DEFAULT_LIMIT) {
            return DEFAULT_LIMIT;
        }

        return Math.min(requested, MAX_LIMIT);
    }

    // 더 보기 링크에 실을 값. 기본 분량이면 null 을 줘서 주소에 ?comments= 가 아예 안 붙는다.
    public Integer limitParam() {
        return limit == DEFAULT_LIMIT ? null : limit;
    }

    // rootRowCount = DB 에 있는 뿌리 댓글 전체 수, threads.size() = 이번에 실어 보낸 수.
    public boolean hasMore() {
        return rootRowCount > threads.size();
    }

    public long hiddenCount() {
        return rootRowCount - threads.size();
    }

    public int nextLimit() {
        return Math.min(limit + STEP, MAX_LIMIT);
    }

    // 아래 둘은 "남은 댓글이 있다" 를 두 갈래로 가른다. 화면 문구가 달라지기 때문이다.
    // canLoadMore  : 더 보기 버튼을 그린다 (더 실을 여유가 남았다)
    // cappedByLimit: 버튼 대신 상한 안내를 그린다 (남았지만 200 에 닿아 더는 못 늘린다)
    public boolean canLoadMore() {
        return hasMore() && nextLimit() > limit;
    }

    public boolean cappedByLimit() {
        return hasMore() && nextLimit() <= limit;
    }
}
