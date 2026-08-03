-- add_order_request_key
-- 생성: 2026-08-03 18:32:57
--
-- 규칙
-- * 이 파일은 머지된 뒤 절대 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다.

ALTER TABLE `orders`
    ADD COLUMN `request_key` VARCHAR(36) NULL AFTER `member_id`,
    ADD CONSTRAINT `uk_orders_member_request_key`
        UNIQUE (`member_id`, `request_key`);
