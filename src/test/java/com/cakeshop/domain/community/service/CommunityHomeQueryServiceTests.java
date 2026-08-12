package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import com.cakeshop.domain.community.dto.query.NoticeListRow;
import com.cakeshop.domain.community.dto.view.PopularPostView;
import com.cakeshop.domain.community.dto.view.PopularSectionView;
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.domain.community.mapper.CommunityNoticeMapper;

import org.junit.jupiter.api.Test;

/**
 * 메인 인기글 계약이 소유하는 것은 <b>건수</b> 하나다.
 *
 * <p>폴백·빈 영역·낡음 경고는 {@code PopularPostReader}가 갖고 {@code CommunityServiceTests}가
 * 이미 본다. 여기서 다시 보면 같은 결함을 두 번 잡는다(docs/testing.md 4절).
 *
 * <p>목록 화면과 같은 10건을 요청하면 메인 절반이 커뮤니티가 되는데, <b>화면은 그래도 멀쩡해
 * 보인다</b> — 인기글이 많은 것과 구분되지 않는다. 공지도 같다.
 */
class CommunityHomeQueryServiceTests {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static final LocalDate RANKING_DATE = LocalDate.of(2026, 3, 9);

    private static final int MAIN_LIMIT = 5;

    private static final int MAIN_NOTICE_LIMIT = 3;

    private static final LocalDateTime NOW = RANKING_DATE.plusDays(1).atTime(10, 0);

    private final CommunityMapper communityMapper = mock(CommunityMapper.class);

    private final CommunityNoticeMapper communityNoticeMapper = mock(CommunityNoticeMapper.class);

    private final CommunityHomeQueryService communityHomeQueryService =
            new CommunityHomeQueryService(
                    new PopularPostReader(communityMapper, fixedClock()),
                    new CommunityNoticeService(communityNoticeMapper, fixedClock()));

    @Test
    void getPopularSection_readsLimitOwnedByMain() {
        when(communityMapper.findLatestRankingDate()).thenReturn(RANKING_DATE);
        when(communityMapper.findPopularPosts(RANKING_DATE, MAIN_LIMIT))
                .thenReturn(popularPosts(MAIN_LIMIT));

        PopularSectionView section = communityHomeQueryService.getPopularSection();

        assertThat(section.posts()).hasSize(MAIN_LIMIT);
        assertThat(section.rankingDate()).isEqualTo(RANKING_DATE);
    }

    @Test
    void getNoticeSection_readsLimitOwnedByMain() {
        when(communityNoticeMapper.selectVisibleNotices(NOW, MAIN_NOTICE_LIMIT, 0))
                .thenReturn(notices(MAIN_NOTICE_LIMIT));

        assertThat(communityHomeQueryService.getNoticeSection().notices())
                .hasSize(MAIN_NOTICE_LIMIT);
    }

    @Test
    void getNoticeSection_withoutVisibleNotices_isEmpty() {
        when(communityNoticeMapper.selectVisibleNotices(NOW, MAIN_NOTICE_LIMIT, 0))
                .thenReturn(List.of());

        assertThat(communityHomeQueryService.getNoticeSection().isEmpty()).isTrue();
    }

    private static List<NoticeListRow> notices(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(index -> new NoticeListRow(
                        (long) index, "공지 " + index, null, NOW.minusDays(index)))
                .toList();
    }

    private static Clock fixedClock() {
        return Clock.fixed(RANKING_DATE.plusDays(1).atTime(10, 0).atZone(SEOUL).toInstant(), SEOUL);
    }

    private static List<PopularPostView> popularPosts(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(rank -> new PopularPostView(rank, (long) rank, "질문", "제목 " + rank))
                .toList();
    }
}
