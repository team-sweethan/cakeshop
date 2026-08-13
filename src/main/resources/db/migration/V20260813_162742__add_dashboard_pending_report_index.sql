-- add_dashboard_pending_report_index
-- 생성: 2026-08-13 16:27:42
--
-- 규칙
-- * 이 파일은 공유된 뒤 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 한 migration 은 하나의 배포 가능한 스키마 전환을 담는다. SQL 수만으로 나누거나 합치지 않는다.

-- 대시보드가 미처리 신고 상태를 먼저 한정한 뒤 고유 게시글 수를 계산할 수 있도록 지원한다.
ALTER TABLE `post_reports`
    ADD INDEX `idx_post_reports_status_post_id` (`status`, `post_id`);
