package com.cakeshop.domain.community.dto.view;

import java.time.LocalDate;
import java.util.List;

/**
 * 목록 화면 상단 인기글 영역 전체(docs/community/DOMAIN.md 6.9).
 *
 * <b>날짜가 함께 다니는 것이 이 타입의 요점이다.</b> 인기글은 실시간이 아니라 확정된
 * 스냅샷이고, 배치를 한 번 거르면 화면은 그 전날 것으로 폴백한다. 언제 것인지 적지
 * 않으면 사용자는 낡은 순위를 오늘 것으로 읽는다 — 그리고 그 상태의 화면은 정상일
 * 때와 완전히 똑같이 생겼다.
 *
 * 비어 있으면 화면은 영역을 <b>통째로</b> 그리지 않는다. 제목만 남기고 안을 비우지
 * 않는다 — 첫 배포 직후처럼 보여 줄 것이 아직 없는 상태는 고장이 아니다(H27).
 */
public record PopularSectionView(
        LocalDate rankingDate,
        List<PopularPostView> posts
) {

    /**
     * 그릴 것이 없는 상태. 확정된 실행이 하나도 없을 때와, 확정은 됐지만 노출 가능한
     * 글이 하나도 남지 않았을 때 둘 다 이 값이다.
     *
     * 둘을 화면에서 구분하지 않는 이유는 사용자가 할 수 있는 일이 같기 때문이다.
     * 구분이 필요한 쪽은 운영이고, 그것은 D6의 경고 로그가 맡는다.
     */
    public static PopularSectionView empty() {
        return new PopularSectionView(null, List.of());
    }

    public boolean isEmpty() {
        return posts.isEmpty();
    }
}
