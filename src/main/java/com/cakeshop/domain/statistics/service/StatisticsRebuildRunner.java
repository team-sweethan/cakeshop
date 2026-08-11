package com.cakeshop.domain.statistics.service;

import com.cakeshop.domain.statistics.dto.StatisticsRebuildRequest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** 운영자가 명시적으로 시작한 기간별 통계 재집계를 실행한다. */
@Component
@ConditionalOnProperty(
        prefix = "app.statistics.rebuild",
        name = "enabled",
        havingValue = "true"
)
public class StatisticsRebuildRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StatisticsRebuildRunner.class);
    private static final String BACKFILL_ENABLED = "app.statistics.backfill.enabled";
    private static final String START_DATE = "app.statistics.rebuild.start-date";
    private static final String END_DATE = "app.statistics.rebuild.end-date";

    private final StatisticsRebuildService rebuildService;
    private final Environment environment;

    public StatisticsRebuildRunner(
            StatisticsRebuildService rebuildService,
            Environment environment
    ) {
        this.rebuildService = rebuildService;
        this.environment = environment;
    }

    /** CLI 날짜 옵션을 검증하고 수동 재집계를 한 번 실행한다. */
    @Override
    public void run(ApplicationArguments args) {
        if (environment.getProperty(BACKFILL_ENABLED, Boolean.class, false)) {
            throw new IllegalStateException("초기 백필과 수동 재집계를 동시에 실행할 수 없습니다.");
        }

        StatisticsRebuildRequest request = new StatisticsRebuildRequest(
                readRequiredDate(START_DATE),
                readOptionalDate(END_DATE)
        );
        StatisticsRebuildResult result = rebuildService.rebuild(request);
        if (result == StatisticsRebuildResult.INITIAL_BACKFILL_REQUIRED) {
            throw new IllegalStateException("수동 재집계 전에 초기 통계 백필을 완료해야 합니다.");
        }
        if (result == StatisticsRebuildResult.SKIPPED_RUNNING) {
            throw new IllegalStateException(
                    "다른 통계 집계가 실행 중이므로 수동 재집계를 재시도해야 합니다."
            );
        }

        log.info("운영자 통계 재집계 명령을 완료했습니다. result={}", result);
    }

    private LocalDate readRequiredDate(String propertyName) {
        String value = environment.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "재집계 시작일 옵션이 필요합니다: --" + propertyName + "=yyyy-MM-dd"
            );
        }
        return parseDate(propertyName, value);
    }

    private LocalDate readOptionalDate(String propertyName) {
        String value = environment.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseDate(propertyName, value);
    }

    private LocalDate parseDate(String propertyName, String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "날짜 옵션은 yyyy-MM-dd 형식이어야 합니다: --" + propertyName,
                    exception
            );
        }
    }
}
