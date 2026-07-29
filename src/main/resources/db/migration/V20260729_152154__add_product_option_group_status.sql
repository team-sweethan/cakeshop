-- add_product_option_group_status
-- 생성: 2026-07-29 15:21:54
--
-- 규칙
-- * 이 파일은 머지된 뒤 절대 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다.

ALTER TABLE `product_option_groups`
    ADD COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        AFTER `selection_type`,
    ADD CONSTRAINT `chk_product_option_groups_status`
        CHECK (`status` IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE `product_options`
    ADD CONSTRAINT `chk_product_options_status`
        CHECK (`status` IN ('ACTIVE', 'INACTIVE'));
