-- add_payment_request_guards
-- 생성: 2026-07-30 17:08:22
--
-- 규칙
-- * 이 파일은 머지된 뒤 절대 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다.

-- UNIQUE 인덱스를 추가하기 전에 주문당 READY 결제가 둘 이상인 기존 데이터가 없는지 확인한다.
CREATE TEMPORARY TABLE `_payment_request_guard` (
    `check_name` VARCHAR(100) NOT NULL,
    `is_valid`  TINYINT NOT NULL,
    CONSTRAINT `chk_payment_request_guard`
        CHECK (`is_valid` = 1)
);

INSERT INTO `_payment_request_guard` (`check_name`, `is_valid`)
SELECT
    'one_ready_payment_per_order',
    CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM (
    SELECT `order_id`
    FROM `payments`
    WHERE `status` = 'READY'
    GROUP BY `order_id`
    HAVING COUNT(*) > 1
) AS duplicate_ready_payments;

DROP TEMPORARY TABLE `_payment_request_guard`;

-- 결제 요청을 재시도하더라도 주문당 진행 중인 READY 결제는 한 건만 유지한다.
ALTER TABLE `payments`
    ADD COLUMN `active_ready_order_id` BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN `status` = 'READY' THEN `order_id` ELSE NULL END
        ) STORED
        AFTER `active_paid_order_id`,
    ADD UNIQUE INDEX `uk_payments_active_ready_order`
        (`active_ready_order_id`);

-- 요청 주체와 요청 유형은 이후 환불 처리에서 인증 사용자와 업무 흐름을 기록한다.
-- 기존 환불 행의 요청 주체·유형은 현재 스키마만으로 확정할 수 없어 NULL을 허용한다.
ALTER TABLE `payment_cancellations`
    ADD COLUMN `request_type` VARCHAR(30) NULL
        AFTER `cancel_reason`,
    ADD COLUMN `requested_by` BIGINT NULL
        AFTER `request_type`,
    ADD CONSTRAINT `fk_payment_cancellations_requested_by`
        FOREIGN KEY (`requested_by`) REFERENCES `members` (`id`),
    ADD INDEX `idx_payment_cancellations_requested_by`
        (`requested_by`);
