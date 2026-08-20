package com.cakeshop.domain.community.dto.view;

import java.time.LocalDate;
import java.util.List;

// 목록 화면 상단 인기글 영역 전체. 순위 목록과 "언제 것인지"(rankingDate) 를 한 상자에 묶어 다닌다.
// 인기글은 실시간 집계가 아니라 하루 단위로 확정된 스냅샷이라, 화면에 전날 순위가 걸려 있을 수 있다.
public record PopularSectionView(
        LocalDate rankingDate,
        List<PopularPostView> posts
) {

    // 그릴 것이 없는 상태를 나타내는 값 하나. rankingDate = null, posts = 빈 목록이다.
    // 호출부가 null 을 돌려주는 대신 이걸 주므로, 화면은 isEmpty() 만 보면 되고 NPE 를 걱정하지 않는다.
    public static PopularSectionView empty() {
        return new PopularSectionView(null, List.of());
    }

    // true 면 화면은 제목만 남기지 않고 영역을 통째로 안 그린다.
    public boolean isEmpty() {
        return posts.isEmpty();
    }
}
