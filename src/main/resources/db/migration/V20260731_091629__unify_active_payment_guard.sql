-- unify_active_payment_guard
-- 생성: 2026-07-31 09:16:29
--
-- 규칙
-- * 이 파일은 머지된 뒤 절대 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다.

-- READY와 DONE을 합친 활성 결제는 주문당 한 건이어야 한다.
-- 기존의 상태별 UNIQUE는 READY 한 건과 DONE 한 건이 동시에 존재하는 경우를 막지 못한다.
CREATE TEMPORARY TABLE `_active_payment_guard` (
    `check_name` VARCHAR(100) NOT NULL,
    `is_valid`  TINYINT NOT NULL,
    CONSTRAINT `chk_active_payment_guard`
        CHECK (`is_valid` = 1)
);

INSERT INTO `_active_payment_guard` (`check_name`, `is_valid`)
SELECT
    'one_active_payment_per_order',
    CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM (
    SELECT `order_id`
    FROM `payments`
    WHERE `status` IN ('READY', 'DONE')
    GROUP BY `order_id`
    HAVING COUNT(*) > 1
) AS duplicate_active_payments;

DROP TEMPORARY TABLE `_active_payment_guard`;

-- 결제 요청과 승인 완료 사이의 상태 전이에서도 같은 UNIQUE 키를 유지한다.
ALTER TABLE `payments`
    DROP INDEX `uk_payments_active_paid_order`,
    DROP INDEX `uk_payments_active_ready_order`,
    DROP COLUMN `active_paid_order_id`,
    CHANGE COLUMN `active_ready_order_id` `active_payment_order_id` BIGINT
        GENERATED ALWAYS AS (
            CASE
                WHEN `status` IN ('READY', 'DONE') THEN `order_id`
                ELSE NULL
            END
        ) STORED,
    ADD UNIQUE INDEX `uk_payments_active_order`
        (`active_payment_order_id`);
