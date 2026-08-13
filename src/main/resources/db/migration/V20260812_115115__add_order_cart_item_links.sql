-- add_order_cart_item_links
-- 생성: 2026-08-12 11:51:15
--
-- 규칙
-- * 이 파일은 공유된 뒤 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 한 migration 은 하나의 배포 가능한 스키마 전환을 담는다. SQL 수만으로 나누거나 합치지 않는다.
-- 장바구니 선택 주문이 결제 완료된 뒤 원본 장바구니 항목을 삭제할 수 있도록 주문과 항목 ID를 연결한다.
-- cart_items FK는 사용자가 결제 전 항목을 직접 삭제하는 정상 흐름을 막지 않기 위해 두지 않는다.
CREATE TABLE order_cart_items (
    order_id BIGINT NOT NULL,
    cart_item_id BIGINT NOT NULL,
    PRIMARY KEY (order_id, cart_item_id),
    CONSTRAINT fk_order_cart_items_order
        FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_order_cart_items_order_id ON order_cart_items (order_id);
