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
import com.cakeshop.domain.community.mapper.CommunityNoticeMapper;

import org.junit.jupiter.api.Test;

/**
 * 메인 공지 계약이 소유하는 것은 <b>건수</b> 하나다.
 *
 * <p>노출 조건·정렬은 {@code CommunityNoticeService}와 매퍼가 갖고 각자의 테스트가 이미 본다.
 * 여기서 다시 보면 같은 결함을 두 번 잡는다(docs/testing.md 4절).
 *
 * <p>목록과 같은 10건을 요청해도 <b>화면은 멀쩡해 보인다</b> — 공지가 많은 날과 구분되지 않는다.
 */
class CommunityHomeQueryServiceTests {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static final LocalDate RANKING_DATE = LocalDate.of(2026, 3, 9);

    private static final int MAIN_NOTICE_LIMIT = 3;

    private static final LocalDateTime NOW = RANKING_DATE.plusDays(1).atTime(10, 0);

    private final CommunityNoticeMapper communityNoticeMapper = mock(CommunityNoticeMapper.class);

    private final CommunityHomeQueryService communityHomeQueryService =
            new CommunityHomeQueryService(
                    new CommunityNoticeService(communityNoticeMapper, fixedClock()));

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
}
