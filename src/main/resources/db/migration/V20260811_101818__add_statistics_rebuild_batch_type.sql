-- add_statistics_rebuild_batch_type
-- 생성: 2026-08-11 10:18:18
--
-- 규칙
-- * 이 파일은 공유된 뒤 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 한 migration 은 하나의 배포 가능한 스키마 전환을 담는다. SQL 수만으로 나누거나 합치지 않는다.

-- 기존 DAILY·BACKFILL 행은 그대로 유지하면서 운영자 수동 재집계 실행 유형을 허용한다.
-- 유형과 유형별 필드 제약은 함께 교체하여 중간 상태가 남지 않도록 한다.
ALTER TABLE `statistics_batch_runs`
    DROP CONSTRAINT `chk_statistics_batch_runs_type`,
    DROP CONSTRAINT `chk_statistics_batch_runs_type_fields`,
    ADD CONSTRAINT `chk_statistics_batch_runs_type`
        CHECK (`batch_type` IN ('DAILY', 'BACKFILL', 'REBUILD')),
    ADD CONSTRAINT `chk_statistics_batch_runs_type_fields`
        CHECK (
            (`batch_type` = 'DAILY' AND `last_completed_date` IS NULL)
            OR
            (
                `batch_type` IN ('BACKFILL', 'REBUILD')
                AND `source_window_started_at` IS NULL
                AND `source_window_ended_at` IS NULL
            )
        );
