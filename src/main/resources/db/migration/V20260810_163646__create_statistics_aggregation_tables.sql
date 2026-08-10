-- create_statistics_aggregation_tables
-- 생성: 2026-08-10 16:36:46
--
-- 규칙
-- * 이 파일은 공유된 뒤 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 한 migration 은 하나의 배포 가능한 스키마 전환을 담는다. SQL 수만으로 나누거나 합치지 않는다.

-- 관리자 기간별 통계 화면이 읽는 날짜별 확정 통계다.
-- 업무 데이터가 없는 날짜도 0인 행을 저장하며, 행이 없으면 미집계 날짜로 판단한다.
CREATE TABLE IF NOT EXISTS `daily_statistics` (
    `statistics_date`       DATE           NOT NULL,
    `total_order_count`     BIGINT         NOT NULL DEFAULT 0,
    `completed_order_count` BIGINT         NOT NULL DEFAULT 0,
    `canceled_order_count`  BIGINT         NOT NULL DEFAULT 0,
    `total_sales_amount`    DECIMAL(18, 0) NOT NULL DEFAULT 0,
    `aggregated_at`         DATETIME(6)    NOT NULL,

    PRIMARY KEY (`statistics_date`),

    CONSTRAINT `chk_daily_statistics_counts`
        CHECK (
            `total_order_count` >= 0
            AND `completed_order_count` >= 0
            AND `canceled_order_count` >= 0
        ),
    CONSTRAINT `chk_daily_statistics_sales_amount`
        CHECK (`total_sales_amount` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 일별 집계와 초기 백필의 실행 이력 및 변경 탐색 기준을 저장한다.
-- RUNNING인 행만 running_lock이 1이 되므로 UNIQUE 제약이 두 작업의 동시 실행을 막는다.
CREATE TABLE IF NOT EXISTS `statistics_batch_runs` (
    `id`                       BIGINT      NOT NULL AUTO_INCREMENT,
    `batch_type`               VARCHAR(20) NOT NULL,
    `status`                   VARCHAR(20) NOT NULL,
    `source_window_started_at` DATETIME(6) NULL,
    `source_window_ended_at`   DATETIME(6) NULL,
    `target_start_date`        DATE        NOT NULL,
    `target_end_date`          DATE        NOT NULL,
    `last_completed_date`      DATE        NULL,
    `started_at`               DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `heartbeat_at`             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `completed_at`             DATETIME(6) NULL,
    `running_lock`             TINYINT
        GENERATED ALWAYS AS (
            CASE WHEN `status` = 'RUNNING' THEN 1 ELSE NULL END
        ) STORED,

    PRIMARY KEY (`id`),
    CONSTRAINT `uk_statistics_batch_runs_running`
        UNIQUE (`running_lock`),
    CONSTRAINT `chk_statistics_batch_runs_type`
        CHECK (`batch_type` IN ('DAILY', 'BACKFILL')),
    CONSTRAINT `chk_statistics_batch_runs_status`
        CHECK (`status` IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT `chk_statistics_batch_runs_target_range`
        CHECK (`target_start_date` <= `target_end_date`),
    CONSTRAINT `chk_statistics_batch_runs_source_window`
        CHECK (
            (
                `source_window_started_at` IS NULL
                AND `source_window_ended_at` IS NULL
            )
            OR
            (
                `source_window_started_at` IS NOT NULL
                AND `source_window_ended_at` IS NOT NULL
                AND `source_window_started_at` <= `source_window_ended_at`
            )
        ),
    CONSTRAINT `chk_statistics_batch_runs_type_fields`
        CHECK (
            (`batch_type` = 'DAILY' AND `last_completed_date` IS NULL)
            OR
            (
                `batch_type` = 'BACKFILL'
                AND `source_window_started_at` IS NULL
                AND `source_window_ended_at` IS NULL
            )
        ),
    CONSTRAINT `chk_statistics_batch_runs_progress`
        CHECK (
            `last_completed_date` IS NULL
            OR `last_completed_date` BETWEEN `target_start_date` AND `target_end_date`
        ),
    CONSTRAINT `chk_statistics_batch_runs_completion`
        CHECK (
            (`status` = 'RUNNING' AND `completed_at` IS NULL)
            OR
            (`status` IN ('SUCCEEDED', 'FAILED') AND `completed_at` IS NOT NULL)
        ),

    INDEX `idx_statistics_batch_runs_daily_watermark`
        (`batch_type`, `status`, `source_window_ended_at`),
    INDEX `idx_statistics_batch_runs_backfill_history`
        (`batch_type`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
