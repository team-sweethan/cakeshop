-- 주문 상태 7개 확정에 따른 orders 상태·시간 컬럼 동기화
-- Target DBMS: MariaDB 10.5+
--
-- 기존 V0/V1 스키마가 적용된 DB에서 한 번 실행한다.
-- 데이터 보존 정책이 필요한 기존 시간 컬럼은 적용 전에 백업한다.

ALTER TABLE `orders`
    MODIFY COLUMN `status` VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT',
    ADD COLUMN `under_review_at` DATETIME(6) NULL AFTER `reject_reason`,
    DROP COLUMN `approved_at`,
    DROP COLUMN `accepted_at`,
    ADD COLUMN `expired_at` DATETIME(6) NULL AFTER `picked_up_at`,
    DROP COLUMN `completed_at`;

-- 기존 11개 상태값이 남아 있으면 7개 상태값으로 먼저 변환한다.
UPDATE `orders`
SET `status` = CASE `status`
    WHEN 'WAITING_APPROVAL' THEN 'PENDING_PAYMENT'
    WHEN 'APPROVED' THEN 'PENDING_PAYMENT'
    WHEN 'PAID' THEN 'READY_FOR_PICKUP'
    WHEN 'ACCEPTED' THEN 'READY_FOR_PICKUP'
    WHEN 'PREPARING' THEN 'READY_FOR_PICKUP'
    WHEN 'READY' THEN 'READY_FOR_PICKUP'
    ELSE `status`
END;

ALTER TABLE `orders`
    ADD CONSTRAINT `chk_orders_status`
        CHECK (`status` IN (
            'PENDING_PAYMENT',
            'UNDER_REVIEW',
            'READY_FOR_PICKUP',
            'PICKED_UP',
            'CANCELED',
            'REJECTED',
            'EXPIRED'
        ));
