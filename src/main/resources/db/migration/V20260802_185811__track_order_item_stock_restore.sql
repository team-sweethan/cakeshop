-- track_order_item_stock_restore
-- 생성: 2026-08-02 18:58:11
--
-- 규칙
-- * 이 파일은 머지된 뒤 절대 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다.
-- 일반 상품 결제 성공 때 실제로 차감한 재고만 취소 시 한 번 복구하기 위한 이력이다.
ALTER TABLE `order_items`
    ADD COLUMN `stock_deducted_at` DATETIME(6) NULL
        AFTER `cancellation_limit_days`,
    ADD COLUMN `stock_restored_at` DATETIME(6) NULL
        AFTER `stock_deducted_at`,
    ADD CONSTRAINT `chk_order_items_stock_restore_order`
        CHECK (`stock_restored_at` IS NULL OR `stock_deducted_at` IS NOT NULL);
