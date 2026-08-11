package com.cakeshop.domain.community.service;

import java.time.LocalDate;

import lombok.RequiredArgsConstructor;

import com.cakeshop.domain.community.mapper.CommunityMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 하루치 인기글 순위를 확정한다(docs/community/DOMAIN.md 6.9, PLAN.md 조각 7b).
 *
 * 날짜를 정하는 일은 여기 없다. 스케줄러가 '어제'를 계산해 넘기고 이 클래스는 받은
 * 날짜만 집계한다 — 갈라 둬야 테스트가 임의의 날짜로 직접 부를 수 있다.
 */
@Service
@RequiredArgsConstructor
public class PopularPostBatchService {

    private static final Logger log = LoggerFactory.getLogger(PopularPostBatchService.class);

    /**
     * 스냅샷에 담는 상위 건수. 화면은 이 중 10건만 쓴다(D5).
     *
     * 20-10의 여유는 비노출 글의 몫이 아니다 — 선정 SQL도 그 시점의 PUBLISHED만
     * 담으므로, 이 여유가 흡수하는 것은 **선정 이후에 지워지거나 차단된 글**이다.
     * 둘을 헷갈리면 여유분을 아무리 늘려도 모자란다.
     */
    private static final int SNAPSHOT_SIZE = 20;

    private final CommunityMapper communityMapper;

    /**
     * 이 날짜의 인기글을 확정한다. 이미 확정된 날짜면 아무것도 하지 않는다.
     *
     * <b>넷이 한 트랜잭션이어야 한다.</b> 순위만 들어가고 실행 기록이 없으면 다음
     * 실행이 그 날짜를 다시 계산하고, 기록만 들어가고 순위가 없으면 활동이 0인 날과
     * 구분되지 않는다. 특히 DELETE만 커밋되고 INSERT가 죽으면 그날 순위가 통째로
     * 사라지는데, 화면은 전날로 폴백해 멀쩡히 보이므로 아무도 눈치채지 못한다(H31).
     *
     * <b>확정된 날짜를 다시 돌려도 덮어쓰지 않는다</b>(D4). 좋아요 취소·댓글 삭제 뒤에
     * 재실행하면 확정된 순위와 근거 수치가 조용히 바뀌는데, 그러면 "원본이 변해도
     * 그날 기록은 남는다"는 스냅샷의 목적이 무너진다. 건너뛰어도 잃는 것이 없는
     * 이유는 실패한 실행이 아무것도 남기지 않기 때문이다 — 기록이 있다는 것이 곧
     * 성공했다는 뜻이다(H31이 그 전제를 지킨다).
     *
     * <b>날짜를 원자적으로 선점하지는 않는다.</b> 확인(SELECT)과 기록(INSERT)이
     * 트랜잭션의 양 끝이라, 여러 인스턴스가 같은 시각에 깨면 전부 "기록 없음"을 보고
     * 전부 집계한 뒤 마지막 INSERT에서 한쪽만 남는다. 줄어드는 것은 시차를 둔
     * 재시도뿐이고, 동시 실행 조율은 단일 서버 전제에 맡긴다(R22).
     */
    @Transactional
    public void createDailyRanking(LocalDate rankingDate) {
        if (communityMapper.existsBatchRun(rankingDate)) {
            log.info("인기글 배치를 건너뜁니다. 이미 확정된 날짜입니다. rankingDate={}", rankingDate);
            return;
        }

        communityMapper.deleteDailyRanking(rankingDate);

        int rankedCount = communityMapper.insertDailyRanking(rankingDate, SNAPSHOT_SIZE);

        // 0건이어도 기록한다. 이 줄이 빠지면 활동 없는 날이 "안 돈 날"과 같아진다(D11).
        communityMapper.insertBatchRun(rankingDate, rankedCount);

        log.info("인기글 배치를 확정했습니다. rankingDate={}, postCount={}", rankingDate, rankedCount);
    }
}
