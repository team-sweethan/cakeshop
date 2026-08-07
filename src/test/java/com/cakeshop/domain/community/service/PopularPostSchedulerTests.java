package com.cakeshop.domain.community.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/** 인기글 스케줄러의 대상 날짜를 검증한다. */
class PopularPostSchedulerTests {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** 서울 기준 전날을 배치에 전달한다. */
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
