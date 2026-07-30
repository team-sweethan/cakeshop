-- align_order_payment_schema
-- 생성: 2026-07-29 18:43:56
--
-- 규칙
-- * 이 파일은 머지된 뒤 절대 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다.

-- 기존 데이터에 자동 변환할 수 없는 값이 있으면 영구 DDL 적용 전에 중단한다.
CREATE TEMPORARY TABLE `_order_payment_migration_guard` (
    `check_name` VARCHAR(100) NOT NULL,
    `is_valid`  TINYINT NOT NULL,
    CONSTRAINT `chk_order_payment_migration_guard`
        CHECK (`is_valid` = 1)
);

-- 기존 주문은 한 가지 상품 유형으로 분류할 수 있어야 한다.
INSERT INTO `_order_payment_migration_guard` (`check_name`, `is_valid`)
SELECT
    'orders_are_classifiable',
    CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM (
    SELECT o.`id`
    FROM `orders` AS o
    LEFT JOIN `order_items` AS oi
        ON oi.`order_id` = o.`id`
    GROUP BY o.`id`
    HAVING COUNT(oi.`id`) = 0
        OR COUNT(DISTINCT oi.`product_type`) <> 1
        OR MIN(oi.`product_type`) NOT IN ('GENERAL', 'CUSTOM')
) AS invalid_orders;

-- PAID와 READY만 현재 7개 상태 모델로 의미가 명확하게 자동 변환한다.
INSERT INTO `_order_payment_migration_guard` (`check_name`, `is_valid`)
SELECT
    'orders_have_supported_status',
    CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM `orders`
WHERE `status` NOT IN (
    'PENDING_PAYMENT',
    'UNDER_REVIEW',
    'READY_FOR_PICKUP',
    'PICKED_UP',
    'CANCELED',
    'REJECTED',
    'EXPIRED',
    'PAID',
    'READY'
);

INSERT INTO `_order_payment_migration_guard` (`check_name`, `is_valid`)
SELECT
    'orders_have_valid_amounts_and_reject_reason',
    CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM `orders`
WHERE `original_amount` < 0
   OR `discount_amount` < 0
   OR `final_amount` < 0
   OR (`status` = 'REJECTED' AND `reject_reason` IS NULL);

INSERT INTO `_order_payment_migration_guard` (`check_name`, `is_valid`)
SELECT
    'order_items_are_valid',
    CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM `order_items`
WHERE `product_type` NOT IN ('GENERAL', 'CUSTOM')
   OR `quantity` <= 0
   OR `base_price` < 0
   OR `option_amount` < 0
   OR `total_amount` < 0;

INSERT INTO `_order_payment_migration_guard` (`check_name`, `is_valid`)
SELECT
    'order_item_options_are_unique',
    CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM (
    SELECT
        `order_item_id`,
        `product_option_id`
    FROM `order_item_options`
    GROUP BY `order_item_id`, `product_option_id`
    HAVING COUNT(*) > 1
) AS duplicate_options;

INSERT INTO `_order_payment_migration_guard` (`check_name`, `is_valid`)
SELECT
    'order_item_option_prices_are_valid',
    CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM `order_item_options`
WHERE `additional_price` < 0;

INSERT INTO `_order_payment_migration_guard` (`check_name`, `is_valid`)
SELECT
    'order_item_image_sort_orders_are_valid',
    CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM `order_item_images`
WHERE `sort_order` < 0;

INSERT INTO `_order_payment_migration_guard` (`check_name`, `is_valid`)
SELECT
    'payments_are_valid',
    CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM `payments`
WHERE `status` NOT IN (
    'READY',
    'DONE',
    'PAID',
    'CANCELED',
    'PARTIAL_CANCELED',
    'ABORTED',
    'EXPIRED'
)
   OR `amount` < 0;

-- PAID와 DONE을 합쳤을 때에도 주문당 성공 결제는 한 건이어야 한다.
INSERT INTO `_order_payment_migration_guard` (`check_name`, `is_valid`)
SELECT
    'one_successful_payment_per_order',
    CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM (
    SELECT `order_id`
    FROM `payments`
    WHERE `status` IN ('PAID', 'DONE')
    GROUP BY `order_id`
    HAVING COUNT(*) > 1
) AS duplicate_successful_payments;

INSERT INTO `_order_payment_migration_guard` (`check_name`, `is_valid`)
SELECT
    'payment_cancellations_are_valid',
    CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM `payment_cancellations`
WHERE `status` NOT IN ('REQUESTED', 'DONE', 'FAILED')
   OR `cancel_amount` <= 0;

INSERT INTO `_order_payment_migration_guard` (`check_name`, `is_valid`)
SELECT
    'one_requested_cancellation_per_payment',
    CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM (
    SELECT `payment_id`
    FROM `payment_cancellations`
    WHERE `status` = 'REQUESTED'
    GROUP BY `payment_id`
    HAVING COUNT(*) > 1
) AS duplicate_requested_cancellations;

DROP TEMPORARY TABLE `_order_payment_migration_guard`;

-- 주문 유형과 상태 처리 정보를 7개 상태 모델에 맞춘다.
ALTER TABLE `orders`
    ADD COLUMN `order_type` VARCHAR(30) NULL,
    ADD COLUMN `under_review_at` DATETIME(6) NULL,
    ADD COLUMN `expired_at` DATETIME(6) NULL,
    ADD COLUMN `approved_by` BIGINT NULL,
    ADD COLUMN `rejected_by` BIGINT NULL,
    ADD COLUMN `picked_up_by` BIGINT NULL;

UPDATE `orders` AS o
JOIN (
    SELECT
        oi.`order_id`,
        MIN(oi.`product_type`) AS `order_type`
    FROM `order_items` AS oi
    GROUP BY oi.`order_id`
    HAVING COUNT(DISTINCT oi.`product_type`) = 1
       AND MIN(oi.`product_type`) IN ('GENERAL', 'CUSTOM')
) AS classified
    ON classified.`order_id` = o.`id`
SET o.`order_type` = classified.`order_type`;

UPDATE `orders`
SET `payment_expires_at` = `created_at` + INTERVAL 10 MINUTE
WHERE `status` = 'PENDING_PAYMENT'
  AND `payment_expires_at` IS NULL;

UPDATE `orders`
SET `status` = CASE
    WHEN `order_type` = 'GENERAL' THEN 'READY_FOR_PICKUP'
    WHEN `order_type` = 'CUSTOM' THEN 'UNDER_REVIEW'
END
WHERE `status` = 'PAID';

UPDATE `orders`
SET `status` = 'READY_FOR_PICKUP'
WHERE `status` = 'READY';

UPDATE `orders`
SET `under_review_at` = COALESCE(`under_review_at`, `updated_at`, `created_at`)
WHERE `status` = 'UNDER_REVIEW'
  AND `under_review_at` IS NULL;

UPDATE `orders`
SET `ready_at` = COALESCE(`ready_at`, `updated_at`, `created_at`)
WHERE `status` = 'READY_FOR_PICKUP'
  AND `ready_at` IS NULL;

UPDATE `orders`
SET `expired_at` = COALESCE(`expired_at`, `updated_at`, `created_at`)
WHERE `status` = 'EXPIRED'
  AND `expired_at` IS NULL;

-- 일반 주문의 취소 마감은 결제 승인일의 20:00으로 보정한다.
UPDATE `orders` AS o
JOIN (
    SELECT
        p.`order_id`,
        MAX(p.`approved_at`) AS `approved_at`
    FROM `payments` AS p
    WHERE p.`status` IN ('PAID', 'DONE')
      AND p.`approved_at` IS NOT NULL
    GROUP BY p.`order_id`
) AS approved_payment
    ON approved_payment.`order_id` = o.`id`
SET o.`cancellation_blocked_at`
        = TIMESTAMP(DATE(approved_payment.`approved_at`), '20:00:00')
WHERE o.`order_type` = 'GENERAL'
  AND o.`cancellation_blocked_at` IS NULL;

ALTER TABLE `orders`
    MODIFY COLUMN `order_type` VARCHAR(30) NOT NULL,
    MODIFY COLUMN `status` VARCHAR(30) NOT NULL DEFAULT 'PENDING_PAYMENT',
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
        CHECK (`status` <> 'REJECTED' OR `reject_reason` IS NOT NULL),
    ADD CONSTRAINT `fk_orders_approved_by`
        FOREIGN KEY (`approved_by`) REFERENCES `members` (`id`),
    ADD CONSTRAINT `fk_orders_rejected_by`
        FOREIGN KEY (`rejected_by`) REFERENCES `members` (`id`),
    ADD CONSTRAINT `fk_orders_picked_up_by`
        FOREIGN KEY (`picked_up_by`) REFERENCES `members` (`id`),
    ADD INDEX `idx_orders_status` (`status`),
    ADD INDEX `idx_orders_pickup_at` (`pickup_at`),
    ADD INDEX `idx_orders_payment_expires_at` (`payment_expires_at`);

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

ALTER TABLE `order_item_options`
    ADD UNIQUE INDEX `uk_order_item_options_item_option`
        (`order_item_id`, `product_option_id`),
    ADD CONSTRAINT `chk_order_item_options_price`
        CHECK (`additional_price` >= 0);

ALTER TABLE `order_item_images`
    ADD CONSTRAINT `chk_order_item_images_sort_order`
        CHECK (`sort_order` >= 0);

-- 결제 완료 상태를 DONE으로 통일하고 주문당 완료 결제를 한 건으로 제한한다.
UPDATE `payments`
SET `status` = 'DONE'
WHERE `status` = 'PAID';

ALTER TABLE `payments`
    MODIFY COLUMN `active_paid_order_id` BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN `status` = 'DONE' THEN `order_id` ELSE NULL END
        ) STORED,
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
        CHECK (`amount` >= 0),
    ADD INDEX `idx_payments_status` (`status`);

-- 결제당 처리 중인 환불 요청을 한 건으로 제한한다.
ALTER TABLE `payment_cancellations`
    ADD COLUMN `active_requested_payment_id` BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN `status` = 'REQUESTED' THEN `payment_id` ELSE NULL END
        ) STORED,
    ADD COLUMN `updated_at` DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    ADD UNIQUE INDEX `uk_payment_cancellations_active_requested`
        (`active_requested_payment_id`),
    ADD INDEX `idx_payment_cancellations_status` (`status`),
    ADD CONSTRAINT `chk_payment_cancellations_status`
        CHECK (`status` IN ('REQUESTED', 'DONE', 'FAILED')),
    ADD CONSTRAINT `chk_payment_cancellations_amount`
        CHECK (`cancel_amount` > 0);
