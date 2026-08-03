package com.cakeshop.domain.community.dto.view;

import java.util.List;

/**
 * 상세 화면의 댓글 구역 전체. 보여줄 댓글과 두 개수, "더 보기"를 그릴지까지 담는다.
 *
 * <h3>왜 "더 보기"인가</h3>
 *
 * 댓글은 쪽 번호로 나누지 않는다(docs/community/DOMAIN.md 6.4). 상세 화면에 게시글 목록과
 * 댓글, 두 종류의 쪽 번호가 생기면 "지금 몇 쪽인가"가 두 개가 되고, 댓글을 쓰고 나서
 * 어느 쪽으로 돌아갈지도 정해야 한다.
 *
 * <h3>왜 최신 N건인가</h3>
 *
 * 앞에서부터 자르지 않고 <b>뒤에서부터</b> 자른다. 방금 쓴 댓글은 언제나 화면에 있어야
 * 하는데, 오래된 순으로 앞에서 자르면 댓글이 20건을 넘는 글에서는 자기가 쓴 댓글이 화면
 * 밖에 남는다. 사용자에게는 등록이 안 된 것과 구분되지 않는다.
 *
 * 그래서 SQL은 최신순으로 limit건을 가져오고, Service가 뒤집어 오래된 순으로 낸다.
 * "더 보기"는 과거로 거슬러 올라간다.
 *
 * <h3>상한</h3>
 *
 * limit에는 상한이 있다. 주소로 들어오는 값이라 막지 않으면 {@code ?comments=99999999}
 * 하나로 한 게시글의 댓글을 전부 메모리에 올릴 수 있다. 상한에 막혀 못 보여주는 댓글이
 * 남으면 링크를 조용히 감추지 않고 화면에 사실을 적는다({@link #cappedByLimit()}) —
 * 감추면 "댓글이 여기까지"로 보이는데, 그것은 거짓이다.
 */
public record CommentSectionView(
        List<CommentView> comments,   // 오래된 순. 새 댓글이 맨 아래에 붙는다
        long publishedCount,          // 화면의 "댓글 N". 자리 표시는 세지 않는다
        long rowCount,                // 자리 표시를 포함한 전체 행 수
        int limit                     // 이번 요청에서 보여주기로 한 최대 건수
) {

    /** 상세를 처음 열 때 보여주는 댓글 수. */
    public static final int DEFAULT_LIMIT = 20;

    /** "더 보기"를 한 번 누를 때 늘어나는 수. */
    public static final int STEP = 20;

    /** 한 화면에 실을 수 있는 상한. */
    public static final int MAX_LIMIT = 200;

    /**
     * 주소로 들어온 값을 실제로 쓸 limit으로 다듬는다.
     *
     * <p>기본값보다 작은 값은 기본값으로 올린다. 줄이는 방향을 허용할 이유가 없고,
     * {@code ?comments=1} 같은 주소가 "댓글이 하나뿐인 글"처럼 보이게 만든다.
     */
    public static int clampLimit(Integer requested) {
        if (requested == null || requested < DEFAULT_LIMIT) {
            return DEFAULT_LIMIT;
        }

        return Math.min(requested, MAX_LIMIT);
    }

    /** 아직 화면에 올리지 않은 댓글이 남았는지. 자리 표시도 남은 것으로 센다. */
    public boolean hasMore() {
        return rowCount > comments.size();
    }

    /** 남은 댓글 수. 화면에 몇 개가 더 있는지 알려줄 때 쓴다. */
    public long hiddenCount() {
        return rowCount - comments.size();
    }

    /** "더 보기"가 요청할 다음 limit. 상한을 넘지 않는다. */
    public int nextLimit() {
        return Math.min(limit + STEP, MAX_LIMIT);
    }

    /** "더 보기" 링크를 그릴 수 있는가. 눌러도 아무것도 늘지 않으면 그리지 않는다. */
    public boolean canLoadMore() {
        return hasMore() && nextLimit() > limit;
    }

    /** 상한에 막혀 더 못 보여주는 상태인가. 이때는 링크 대신 그 사실을 적는다. */
    public boolean cappedByLimit() {
        return hasMore() && nextLimit() <= limit;
    }
}
