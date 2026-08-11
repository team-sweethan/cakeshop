package com.cakeshop.domain.community.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import lombok.RequiredArgsConstructor;

import com.cakeshop.domain.community.dto.view.PopularPostView;
import com.cakeshop.domain.community.dto.view.PopularSectionView;
import com.cakeshop.domain.community.mapper.CommunityMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/*
 * 확정된 인기글을 읽는 한 곳(specs/community-popular.md B5).
 *
 * <p>인기글이 실리는 자리는 목록 화면과 메인 둘인데, <b>건수만 다르고 규칙은 같다</b> — 최신
 * 확정일로 폴백하고, 그릴 것이 없으면 빈 영역을 주고, 확정일이 밀리면 경고한다. 화면이 늘 때
 * 이 셋이 함께 늘면 두 벌이 되고, 두 벌이 되는 순간 갈린다.
 *
 * <p><b>자리별 노출 조건은 여기 없다.</b> 목록의 "1쪽 + 필터 없음"은 그 화면의 규칙이라
 * {@code CommunityService}가, 메인은 {@code CommunityHomeQueryService}가 각자 갖는다.
 */
@Component
@RequiredArgsConstructor
class PopularPostReader {

    private static final Logger log = LoggerFactory.getLogger(PopularPostReader.class);

    private static final LocalTime STALE_WARNING_GRACE_UNTIL = LocalTime.of(1, 0);

    private final CommunityMapper communityMapper;
    private final Clock clock;

    PopularSectionView read(int limit) {
        LocalDate rankingDate = communityMapper.findLatestRankingDate();

        if (rankingDate == null) {
            return PopularSectionView.empty();
        }

        warnIfRankingIsStale(rankingDate);

        List<PopularPostView> popularPosts = communityMapper.findPopularPosts(rankingDate, limit);

        if (popularPosts.isEmpty()) {
            return PopularSectionView.empty();
        }

        return new PopularSectionView(rankingDate, popularPosts);
    }

    private void warnIfRankingIsStale(LocalDate rankingDate) {
        LocalDateTime now = LocalDateTime.now(clock);

        if (!rankingDate.isBefore(now.toLocalDate().minusDays(1))) {
            return;
        }

        if (now.toLocalTime().isBefore(STALE_WARNING_GRACE_UNTIL)) {
            return;
        }

        log.warn(
                "인기글 확정 날짜가 어제보다 오래됐습니다. 배치가 돌지 않았을 수 있습니다."
                        + " latestRankingDate={}, now={}",
                rankingDate,
                now
        );
    }
}
