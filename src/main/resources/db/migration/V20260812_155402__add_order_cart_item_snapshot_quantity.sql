-- add_order_cart_item_snapshot_quantity
-- 생성: 2026-08-12 15:54:02
--
-- 규칙
-- * 이 파일은 공유된 뒤 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 한 migration 은 하나의 배포 가능한 스키마 전환을 담는다. SQL 수만으로 나누거나 합치지 않는다.

-- 결제 전 장바구니 수량이 바뀐 항목을 삭제하지 않도록 주문 생성 시점의 수량을 보존한다.
-- 기존 연결 행은 과거 주문과 호환되도록 기본 수량 1을 사용한다.
ALTER TABLE order_cart_items
    ADD COLUMN snapshot_quantity INT UNSIGNED NOT NULL DEFAULT 1
        COMMENT '주문 생성 시점 장바구니 항목 수량'
        AFTER cart_item_id;
