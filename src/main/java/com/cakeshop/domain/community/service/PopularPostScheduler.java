package com.cakeshop.domain.community.service;

import java.time.Clock;
import java.time.LocalDate;

import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 새벽 전날의 인기글을 확정한다(docs/community/PLAN.md D3).
 *
 * <b>날짜 계산 말고는 아무것도 하지 않는다.</b> 집계는 PopularPostBatchService가 한다 —
 * 갈라 둬야 테스트가 임의의 날짜로 서비스를 직접 부를 수 있고, 트랜잭션 경계도
 * 서비스의 공개 메서드에 그대로 남는다. 여기서 집계까지 하면 @Transactional이
 * 스케줄러 호출에 걸려 프록시 경계를 따지는 자리가 하나 더 생긴다.
 *
 * 크론 표현식 자체는 테스트하지 않는다 — 시간을 기다리는 테스트가 되고, 값이 틀려도
 * 실패까지 하루가 걸린다. 대신 고정한 시계로 '어제'를 넘기는지만 본다.
 */
@Component
@RequiredArgsConstructor
public class PopularPostScheduler {

    /**
     * 00시 정각이 아니라 00:05인 것은 자정 경계의 쓰기가 커밋될 여유를 두기 위해서다.
     *
     * <b>이 시각은 화면 경고의 유예(01:00)와 한 벌이다</b>(D6). 한쪽을 바꾸면 다른
     * 쪽도 함께 봐야 한다 — 크론을 늦추면 그 사이 목록 요청이 정상 상태에서 경고를
     * 찍는다.
     *
     * zone을 적는 것만으로는 부족하다. 그건 배치가 깨어나는 시각일 뿐이고, 넘긴
     * 날짜가 DB의 created_at과 같은 기준인지는 JDBC 세션 시간대가 맡는다(D10).
     */
    private static final String DAILY_AT_00_05 = "0 5 0 * * *";

    private final PopularPostBatchService popularPostBatchService;
    private final Clock clock;

    /**
     * 대상은 <b>전날</b>이다. 오늘을 집계하면 아직 끝나지 않은 하루를 확정하게 되고,
     * 그 결과는 자정까지 계속 낡아 간다.
     */
    @Scheduled(cron = DAILY_AT_00_05, zone = "Asia/Seoul")
    public void createYesterdayRanking() {
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);

        popularPostBatchService.createDailyRanking(yesterday);
    }
}
