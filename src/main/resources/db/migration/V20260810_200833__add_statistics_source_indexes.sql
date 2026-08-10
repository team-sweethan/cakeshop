-- add_statistics_source_indexes
-- 생성: 2026-08-10 20:08:33
--
-- 규칙
-- * 이 파일은 공유된 뒤 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 한 migration 은 하나의 배포 가능한 스키마 전환을 담는다. SQL 수만으로 나누거나 합치지 않는다.

-- 날짜별 주문 집계와 변경 날짜 탐색이 원본 전체를 반복 스캔하지 않도록 한다.
ALTER TABLE `orders`
    ADD INDEX `idx_orders_created_at_status` (`created_at`, `status`),
    ADD INDEX `idx_orders_updated_at_created_at` (`updated_at`, `created_at`);

-- 승인 결제 매출 집계와 변경 날짜 탐색에 사용하는 범위 조건을 지원한다.
ALTER TABLE `payments`
    ADD INDEX `idx_payments_status_approved_at` (`status`, `approved_at`),
    ADD INDEX `idx_payments_updated_at_approved_at` (`updated_at`, `approved_at`);
