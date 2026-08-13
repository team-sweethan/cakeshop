package com.cakeshop.domain.statistics.service.rebuild;

/** 운영자 수동 재집계 명령의 실행 결과다. */
public enum StatisticsRebuildResult {
    SUCCEEDED,
    INITIAL_BACKFILL_REQUIRED,
    SKIPPED_RUNNING
}
