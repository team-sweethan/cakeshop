package com.cakeshop.domain.statistics.service.backfill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** 운영자가 명시적으로 시작한 일회성 초기 통계 백필을 실행한다. */
@Component
@ConditionalOnProperty(
        prefix = "app.statistics.backfill",
        name = "enabled",
        havingValue = "true"
)
public class StatisticsBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StatisticsBackfillRunner.class);
    private static final String REBUILD_ENABLED = "app.statistics.rebuild.enabled";

    private final InitialStatisticsBackfillService backfillService;
    private final Environment environment;

    public StatisticsBackfillRunner(
            InitialStatisticsBackfillService backfillService,
            Environment environment
    ) {
        this.backfillService = backfillService;
        this.environment = environment;
    }

    /** 백필을 한 번 실행하고 운영자가 확인할 결과를 남긴다. */
    @Override
    public void run(ApplicationArguments args) {
        if (environment.getProperty(REBUILD_ENABLED, Boolean.class, false)) {
            throw new IllegalStateException("초기 백필과 수동 재집계를 동시에 실행할 수 없습니다.");
        }

        StatisticsBackfillResult result = backfillService.backfillInitialStatistics();
        if (result == StatisticsBackfillResult.SKIPPED_RUNNING) {
            throw new IllegalStateException(
                    "다른 통계 집계가 실행 중이므로 초기 백필을 재시도해야 합니다."
            );
        }

        log.info("운영자 초기 통계 백필 명령을 완료했습니다. result={}", result);
    }
}
