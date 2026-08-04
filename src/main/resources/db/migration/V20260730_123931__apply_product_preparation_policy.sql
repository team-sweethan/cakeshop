-- apply_product_preparation_policy
-- 생성: 2026-07-30 12:39:31
--
-- 규칙
-- * 이 파일은 머지된 뒤 절대 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다.

-- 일반 상품은 미리 준비된 재고 상품이므로 준비 기간을 0일로 통일한다.
UPDATE `products`
SET `preparation_days` = 0
WHERE `product_type` = 'GENERAL'
  AND `preparation_days` <> 0;

-- 기존 주문 제작 상품 중 새 정책을 만족하지 않는 값은 최소 준비 기간인 1일로 보정한다.
UPDATE `products`
SET `preparation_days` = 1
WHERE `product_type` = 'CUSTOM'
  AND `preparation_days` < 1;

ALTER TABLE `products`
    DROP COLUMN `cancellation_limit_days`,
    ADD CONSTRAINT `chk_products_preparation_days_by_type`
        CHECK (
            (`product_type` = 'GENERAL' AND `preparation_days` = 0)
            OR (`product_type` = 'CUSTOM' AND `preparation_days` >= 1)
        );
