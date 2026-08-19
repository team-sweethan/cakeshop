-- remove_unused_order_schema
-- 생성: 2026-08-18 10:47:41
--
-- 규칙
-- * 이 파일은 공유된 뒤 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 한 migration 은 하나의 배포 가능한 스키마 전환을 담는다. SQL 수만으로 나누거나 합치지 않는다.

ALTER TABLE `orders`
    DROP COLUMN `accepted_at`,
    DROP COLUMN `completed_at`,
    DROP COLUMN `cancellation_blocked_at`;

ALTER TABLE `order_items`
    DROP COLUMN `cancellation_limit_days`;

DROP INDEX `idx_order_cart_items_order_id` ON `order_cart_items`;
