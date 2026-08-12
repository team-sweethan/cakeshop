package com.cakeshop.domain.community.dto.view;

import java.util.List;

/**
 * 다른 화면 안에 실리는 공지 영역 전체(docs/community/specs/community-notice.md B8).
 *
 * <p>인기글 영역과 같은 모양이다({@link PopularSectionView}). 비어 있으면 화면은 영역을
 * <b>통째로</b> 그리지 않는다 — 제목만 남기고 안을 비우지 않는다.
 *
 * <p>영역에 싣는 건수에는 제한이 있지만 등록 개수에는 없다. 그래서 <b>전체보기 링크가 반드시
 * 함께 있어야 한다</b> — 링크가 없으면 오래된 공지는 접근 경로가 통째로 사라진다.
 */
public record NoticeSectionView(
        List<NoticeView> notices
) {

    public static NoticeSectionView empty() {
        return new NoticeSectionView(List.of());
    }

    public boolean isEmpty() {
        return notices.isEmpty();
    }
}
