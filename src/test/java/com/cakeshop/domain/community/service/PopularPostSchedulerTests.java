package com.cakeshop.domain.community.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/**
 * 스케줄러가 <b>어느 날짜를 넘기는지만</b> 고정한다(docs/community/PLAN.md 조각 7b).
 *
 * <p>크론 표현식 자체는 검사하지 않는다. 시간을 기다리는 테스트가 되고, 값이 틀려도
 * 실패까지 하루가 걸린다. 대신 시계를 고정해 날짜 계산을 직접 본다 — 스케줄러가 하는
 * 일이 그것뿐이라 이 검사가 곧 이 클래스 전부다.
 */
class PopularPostSchedulerTests {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /**
     * 대상은 전날이고, 기준은 <b>시스템 시간대가 아니라 서울</b>이다.
     *
     * <p>고른 순간이 중요하다 — 서울로는 2026-08-05 00:05이지만 UTC로는 아직 2026-08-04
     * 15:05다. 시간대를 잃고 시스템 기본값으로 계산하는 구현은 '어제'를 08-03으로 넘겨
     * 여기서 걸린다. CI가 우연히 UTC라 통과하고 운영에서만 틀어지는 자리라, 두 날짜가
     * 갈리는 순간을 일부러 고른다.
     */
    @Test
    void createYesterdayRanking_passesYesterdayInSeoul() {
        Clock fixedAtSeoulMidnightPastFive =
                Clock.fixed(Instant.parse("2026-08-04T15:05:00Z"), SEOUL);

        PopularPostBatchService batchService = mock(PopularPostBatchService.class);

        new PopularPostScheduler(batchService, fixedAtSeoulMidnightPastFive)
                .createYesterdayRanking();

        verify(batchService).createDailyRanking(LocalDate.of(2026, 8, 4));
    }
}
