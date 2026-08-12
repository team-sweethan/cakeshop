-- add_custom_production_due_index
-- 생성: 2026-08-11 16:59:15
--
-- 규칙
-- * 이 파일은 공유된 뒤 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 한 migration 은 하나의 배포 가능한 스키마 전환을 담는다. SQL 수만으로 나누거나 합치지 않는다.

-- 수제 주문 자동 제작 완료 후보는 주문 유형·상태를 먼저 한정한 뒤 제작 시작 시각을 기준으로 조회한다.
ALTER TABLE `orders`
    ADD INDEX `idx_orders_custom_production_due` (`order_type`, `status`, `approved_at`);
