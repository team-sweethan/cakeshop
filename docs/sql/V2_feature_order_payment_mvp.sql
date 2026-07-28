-- feature/order-payment-mvp 주문·결제 로컬 MVP 마이그레이션
-- Target DBMS: MariaDB 11.4
--
-- 실행 기준
--   1. 깨끗한 로컬 DB에 V1_first_MVC_table.sql을 적용한다.
--   2. 이 파일을 적용한다.
--   3. V1에 없는 주문 옵션·이미지·결제 취소 테이블은 이 파일이 생성한다.
--
-- 주의
--   - V0_ERD.sql은 전체 ERD 초기화 DDL이며 이 파일의 선행 버전으로 사용하지 않는다.
--   - V1_first_MVC_table.sql은 독립 초기화 DDL이므로 기존 데이터가 있는 DB에서 재실행하지 않는다.
--   - 기존 데이터가 있으면 반드시 백업 후 실행한다.
--   - order_type은 order_items.product_type이 모두 GENERAL 또는 CUSTOM이고,
--     한 주문 안에서 단일 유형일 때만 자동 분류된다. 분류할 수 없는 주문이 있으면
--     order_type NOT NULL 변경에서 실패하므로 데이터를 확인한 뒤 재실행한다.
--   - 기존 approved_at, accepted_at, completed_at은 상태·기준 시각 변환 후 삭제한다.
--   - V2_add_product_stock.sql과는 독립적이며 이 파일은 재고 컬럼을 변경하지 않는다.

SET NAMES utf8mb4;

-- =========================================================
-- 주문 유형과 상태 처리 컬럼
-- =========================================================

ALTER TABLE `orders`
    ADD COLUMN IF NOT EXISTS `order_type` VARCHAR(30) NULL
        AFTER `member_id`,
    ADD COLUMN IF NOT EXISTS `under_review_at` DATETIME(6) NULL
        AFTER `reject_reason`,
    ADD COLUMN IF NOT EXISTS `expired_at` DATETIME(6) NULL
        AFTER `picked_up_at`,
    ADD COLUMN IF NOT EXISTS `approved_by` BIGINT NULL
        AFTER `ready_at`,
    ADD COLUMN IF NOT EXISTS `rejected_by` BIGINT NULL
        AFTER `rejected_at`,
    ADD COLUMN IF NOT EXISTS `picked_up_by` BIGINT NULL
        AFTER `picked_up_at`;

-- 주문 항목의 스냅샷 유형이 하나로 일치하는 주문만 자동 분류한다.
UPDATE `orders` AS o
JOIN (
    SELECT
        oi.`order_id`,
        MIN(oi.`product_type`) AS `order_type`
    FROM `order_items` AS oi
    GROUP BY oi.`order_id`
    HAVING COUNT(*) > 0
       AND COUNT(DISTINCT oi.`product_type`) = 1
       AND MIN(oi.`product_type`) IN ('GENERAL', 'CUSTOM')
) AS classified
    ON classified.`order_id` = o.`id`
SET o.`order_type` = classified.`order_type`
WHERE o.`order_type` IS NULL;

-- 결제 만료 시각이 없는 미결제 주문은 생성 후 10분으로 보정한다.
UPDATE `orders`
SET `payment_expires_at` = `created_at` + INTERVAL 10 MINUTE
WHERE `status` IN ('PENDING_PAYMENT', 'WAITING_APPROVAL', 'APPROVED')
  AND `payment_expires_at` IS NULL;

-- 결제 상태의 과거 PAID는 Java PaymentStatus.DONE과 같은 의미이므로 변환한다.
UPDATE `payments`
SET `status` = 'DONE'
WHERE `status` = 'PAID';

-- 과거 주문 상태를 새 7개 상태로 전환한다.
-- PAID는 주문 유형을 확인한 뒤 GENERAL/CUSTOM 흐름으로 분리한다.
UPDATE `orders`
SET `status` = CASE
    WHEN `status` IN ('WAITING_APPROVAL', 'APPROVED')
        THEN 'PENDING_PAYMENT'
    WHEN `status` = 'PAID' AND `order_type` = 'GENERAL'
        THEN 'READY_FOR_PICKUP'
    WHEN `status` = 'PAID' AND `order_type` = 'CUSTOM'
        THEN 'UNDER_REVIEW'
    WHEN `status` IN ('ACCEPTED', 'PREPARING') AND `order_type` = 'GENERAL'
        THEN 'READY_FOR_PICKUP'
    WHEN `status` IN ('ACCEPTED', 'PREPARING') AND `order_type` = 'CUSTOM'
        THEN 'UNDER_REVIEW'
    WHEN `status` = 'READY'
        THEN 'READY_FOR_PICKUP'
    ELSE `status`
END;

-- 변환된 상태의 기준 시각을 기존 이력에서 보수적으로 채운다.
UPDATE `orders`
SET `under_review_at` = COALESCE(
        `under_review_at`,
        `updated_at`,
        `created_at`
    )
WHERE `status` = 'UNDER_REVIEW'
  AND `under_review_at` IS NULL;

UPDATE `orders`
SET `ready_at` = COALESCE(
        `ready_at`,
        `updated_at`,
        `created_at`
    )
WHERE `status` = 'READY_FOR_PICKUP'
  AND `ready_at` IS NULL;

UPDATE `orders`
SET `expired_at` = COALESCE(`expired_at`, `updated_at`, `created_at`)
WHERE `status` = 'EXPIRED'
  AND `expired_at` IS NULL;

-- 일반 주문 취소 마감은 결제 승인일의 20:00(저장 기준 Asia/Seoul)이다.
UPDATE `orders` AS o
JOIN (
    SELECT
        p.`order_id`,
        MAX(p.`approved_at`) AS `approved_at`
    FROM `payments` AS p
    WHERE p.`status` = 'DONE'
      AND p.`approved_at` IS NOT NULL
    GROUP BY p.`order_id`
) AS approved_payment
    ON approved_payment.`order_id` = o.`id`
SET o.`cancellation_blocked_at`
        = TIMESTAMP(DATE(approved_payment.`approved_at`), '20:00:00')
WHERE o.`order_type` = 'GENERAL'
  AND o.`cancellation_blocked_at` IS NULL;

-- 새 상태 모델에서 사용하지 않는 과거 처리 시각 컬럼을 제거한다.
-- 재실행할 때는 IF EXISTS에 의해 안전하게 건너뛴다.
ALTER TABLE `orders`
    DROP COLUMN IF EXISTS `approved_at`,
    DROP COLUMN IF EXISTS `accepted_at`,
    DROP COLUMN IF EXISTS `completed_at`;

-- 분류할 수 없는 주문이 있으면 여기서 실패한다.
-- 혼합 주문이나 알 수 없는 product_type을 먼저 정리한 뒤 파일을 재실행한다.
ALTER TABLE `orders`
    MODIFY COLUMN `order_type` VARCHAR(30) NOT NULL
        AFTER `member_id`,
    MODIFY COLUMN `status` VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT';

-- 기존 CHECK가 있어도 같은 이름으로 최종 정의를 다시 적용할 수 있게 한다.
ALTER TABLE `orders`
    DROP CONSTRAINT IF EXISTS `chk_orders_order_type`,
    DROP CONSTRAINT IF EXISTS `chk_orders_status`,
    DROP CONSTRAINT IF EXISTS `chk_orders_amounts`,
    DROP CONSTRAINT IF EXISTS `chk_orders_reject_reason`;

ALTER TABLE `orders`
    ADD CONSTRAINT `chk_orders_order_type`
        CHECK (`order_type` IN ('GENERAL', 'CUSTOM')),
    ADD CONSTRAINT `chk_orders_status`
        CHECK (`status` IN (
            'PENDING_PAYMENT',
            'UNDER_REVIEW',
            'READY_FOR_PICKUP',
            'PICKED_UP',
            'CANCELED',
            'REJECTED',
            'EXPIRED'
        )),
    ADD CONSTRAINT `chk_orders_amounts`
        CHECK (
            `original_amount` >= 0
            AND `discount_amount` >= 0
            AND `final_amount` >= 0
        ),
    ADD CONSTRAINT `chk_orders_reject_reason`
        CHECK (`status` <> 'REJECTED' OR `reject_reason` IS NOT NULL);

-- 처리 관리자 FK는 nullable이며 기존 데이터를 강제로 추정하지 않는다.
ALTER TABLE `orders`
    DROP FOREIGN KEY IF EXISTS `fk_orders_approved_by`,
    DROP FOREIGN KEY IF EXISTS `fk_orders_rejected_by`,
    DROP FOREIGN KEY IF EXISTS `fk_orders_picked_up_by`;

ALTER TABLE `orders`
    ADD CONSTRAINT `fk_orders_approved_by`
        FOREIGN KEY (`approved_by`) REFERENCES `members` (`id`),
    ADD CONSTRAINT `fk_orders_rejected_by`
        FOREIGN KEY (`rejected_by`) REFERENCES `members` (`id`),
    ADD CONSTRAINT `fk_orders_picked_up_by`
        FOREIGN KEY (`picked_up_by`) REFERENCES `members` (`id`);

ALTER TABLE `orders`
    ADD INDEX IF NOT EXISTS `idx_orders_member_id` (`member_id`),
    ADD INDEX IF NOT EXISTS `idx_orders_status` (`status`),
    ADD INDEX IF NOT EXISTS `idx_orders_pickup_at` (`pickup_at`),
    ADD INDEX IF NOT EXISTS `idx_orders_payment_expires_at` (`payment_expires_at`);

-- =========================================================
-- 주문 항목 및 선택 옵션·요구 이미지
-- =========================================================

ALTER TABLE `products`
    DROP CONSTRAINT IF EXISTS `chk_products_product_type`;

ALTER TABLE `products`
    ADD CONSTRAINT `chk_products_product_type`
        CHECK (`product_type` IN ('GENERAL', 'CUSTOM'));

ALTER TABLE `order_items`
    DROP CONSTRAINT IF EXISTS `chk_order_items_product_type`,
    DROP CONSTRAINT IF EXISTS `chk_order_items_amounts`;

ALTER TABLE `order_items`
    ADD CONSTRAINT `chk_order_items_product_type`
        CHECK (`product_type` IN ('GENERAL', 'CUSTOM')),
    ADD CONSTRAINT `chk_order_items_amounts`
        CHECK (
            `quantity` > 0
            AND `base_price` >= 0
            AND `option_amount` >= 0
            AND `total_amount` >= 0
        );

CREATE TABLE IF NOT EXISTS `order_item_options` (
    `id`                BIGINT NOT NULL AUTO_INCREMENT,
    `order_item_id`     BIGINT NOT NULL,
    `product_option_id` BIGINT NOT NULL,
    `option_group_name` VARCHAR(100) NOT NULL,
    `option_name`       VARCHAR(100) NOT NULL,
    `additional_price`  DECIMAL(12, 0) NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_order_item_options_item_option`
        UNIQUE (`order_item_id`, `product_option_id`),
    CONSTRAINT `fk_order_item_options_item`
        FOREIGN KEY (`order_item_id`) REFERENCES `order_items` (`id`),
    CONSTRAINT `fk_order_item_options_product_option`
        FOREIGN KEY (`product_option_id`) REFERENCES `product_options` (`id`),
    CONSTRAINT `chk_order_item_options_price`
        CHECK (`additional_price` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 재실행 시 기존 테이블에도 중복 옵션 및 음수 금액 방지를 동일하게 반영한다.
ALTER TABLE `order_item_options`
    DROP CONSTRAINT IF EXISTS `chk_order_item_options_price`;

ALTER TABLE `order_item_options`
    ADD UNIQUE INDEX IF NOT EXISTS `uk_order_item_options_item_option`
        (`order_item_id`, `product_option_id`),
    ADD CONSTRAINT `chk_order_item_options_price`
        CHECK (`additional_price` >= 0);

CREATE TABLE IF NOT EXISTS `order_item_images` (
    `id`            BIGINT NOT NULL AUTO_INCREMENT,
    `order_item_id` BIGINT NOT NULL,
    `image_url`     VARCHAR(500) NOT NULL,
    `sort_order`    INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_order_item_images_item`
        FOREIGN KEY (`order_item_id`) REFERENCES `order_items` (`id`),
    CONSTRAINT `chk_order_item_images_sort_order`
        CHECK (`sort_order` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `order_item_images`
    DROP CONSTRAINT IF EXISTS `chk_order_item_images_sort_order`;

ALTER TABLE `order_item_images`
    ADD CONSTRAINT `chk_order_item_images_sort_order`
        CHECK (`sort_order` >= 0);

-- =========================================================
-- 결제
-- =========================================================

-- 기존 컬럼명은 V1 및 공통 DDL 호환을 위해 유지하되,
-- 활성 결제 판정 조건을 PAID가 아닌 DONE으로 바로잡는다.
ALTER TABLE `payments`
    MODIFY COLUMN `active_paid_order_id` BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN `status` = 'DONE' THEN `order_id` ELSE NULL END
        ) STORED;

ALTER TABLE `payments`
    DROP CONSTRAINT IF EXISTS `chk_payments_status`,
    DROP CONSTRAINT IF EXISTS `chk_payments_amount`;

ALTER TABLE `payments`
    ADD CONSTRAINT `chk_payments_status`
        CHECK (`status` IN (
            'READY',
            'DONE',
            'CANCELED',
            'PARTIAL_CANCELED',
            'ABORTED',
            'EXPIRED'
        )),
    ADD CONSTRAINT `chk_payments_amount`
        CHECK (`amount` >= 0);

ALTER TABLE `payments`
    ADD INDEX IF NOT EXISTS `idx_payments_order_id` (`order_id`),
    ADD INDEX IF NOT EXISTS `idx_payments_status` (`status`);

-- =========================================================
-- 결제 취소·환불
-- =========================================================

CREATE TABLE IF NOT EXISTS `payment_cancellations` (
    `id`                          BIGINT NOT NULL AUTO_INCREMENT,
    `payment_id`                  BIGINT NOT NULL,
    `idempotency_key`             VARCHAR(100) NOT NULL,
    `cancel_amount`               DECIMAL(12, 0) NOT NULL,
    `cancel_reason`               VARCHAR(500) NOT NULL,
    `status`                      VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    `active_requested_payment_id` BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN `status` = 'REQUESTED' THEN `payment_id` ELSE NULL END
        ) STORED,
    `transaction_key`             VARCHAR(200) NULL,
    `failure_code`                VARCHAR(100) NULL,
    `failure_message`             VARCHAR(500) NULL,
    `requested_at`                DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `canceled_at`                 DATETIME(6) NULL,
    `created_at`                  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`                  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                                   ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_payment_cancellations_idempotency`
        UNIQUE (`idempotency_key`),
    CONSTRAINT `uk_payment_cancellations_transaction`
        UNIQUE (`transaction_key`),
    CONSTRAINT `uk_payment_cancellations_active_requested`
        UNIQUE (`active_requested_payment_id`),
    CONSTRAINT `fk_payment_cancellations_payment`
        FOREIGN KEY (`payment_id`) REFERENCES `payments` (`id`),
    CONSTRAINT `chk_payment_cancellations_status`
        CHECK (`status` IN ('REQUESTED', 'DONE', 'FAILED')),
    CONSTRAINT `chk_payment_cancellations_amount`
        CHECK (`cancel_amount` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 재실행 시 기존 결제 취소 테이블도 최종 구조로 유지한다.
ALTER TABLE `payment_cancellations`
    ADD COLUMN IF NOT EXISTS `active_requested_payment_id` BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN `status` = 'REQUESTED' THEN `payment_id` ELSE NULL END
        ) STORED
        AFTER `status`,
    ADD COLUMN IF NOT EXISTS `updated_at` DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6)
        AFTER `created_at`;

ALTER TABLE `payment_cancellations`
    DROP CONSTRAINT IF EXISTS `chk_payment_cancellations_status`,
    DROP CONSTRAINT IF EXISTS `chk_payment_cancellations_amount`;

ALTER TABLE `payment_cancellations`
    ADD UNIQUE INDEX IF NOT EXISTS `uk_payment_cancellations_active_requested`
        (`active_requested_payment_id`),
    ADD INDEX IF NOT EXISTS `idx_payment_cancellations_payment_id` (`payment_id`),
    ADD INDEX IF NOT EXISTS `idx_payment_cancellations_status` (`status`),
    ADD CONSTRAINT `chk_payment_cancellations_status`
        CHECK (`status` IN ('REQUESTED', 'DONE', 'FAILED')),
    ADD CONSTRAINT `chk_payment_cancellations_amount`
        CHECK (`cancel_amount` > 0);

-- =========================================================
-- 적용 후 확인
-- =========================================================

SELECT
    o.`order_type`,
    o.`status`,
    COUNT(*) AS `order_count`
FROM `orders` AS o
GROUP BY o.`order_type`, o.`status`
ORDER BY o.`order_type`, o.`status`;

SELECT
    p.`status`,
    COUNT(*) AS `payment_count`
FROM `payments` AS p
GROUP BY p.`status`
ORDER BY p.`status`;

SELECT
    pc.`status`,
    COUNT(*) AS `cancellation_count`
FROM `payment_cancellations` AS pc
GROUP BY pc.`status`
ORDER BY pc.`status`;
