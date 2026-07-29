-- 상품 재고 수량 추가
-- NULL: 재고 제한 없음
-- 0: 품절
-- 1 이상: 주문 가능 재고

-- 주문 생성 시 재고 차감
-- 주문 거절·결제 만료·취소 시 재고 복구
-- NULL 재고는 차감·복구하지 않음

ALTER TABLE `products`
    ADD COLUMN `stock_quantity` INT UNSIGNED NULL
    AFTER `base_price`;
