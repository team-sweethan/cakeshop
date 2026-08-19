-- 후기 도메인 로컬 시드 데이터
--
-- ⚠️ 주의: SEED-REVIEW- 주문과 그 주문에 연결된 결제·후기 데이터를 지우고
--    샘플을 다시 넣는다. 해당 주문에 직접 작성한 후기도 함께 사라진다.
--    직접 작성한 후기에 이미지가 있으면 원본 파일을 SQL로 지울 수 없으므로 삭제 전에 중단한다.
--    운영/공용 DB에서는 절대 실행하지 않는다.
--
-- Flyway 관리 대상이 아니다. 스키마 마이그레이션이 모두 적용된 뒤 직접 실행한다.
--
-- ‼️ 실행 순서: seed-local.sql 을 먼저 실행하고 이 파일을 실행한다.
--    이 시드는 seed-local.sql 의 user/admin 계정과 일반·주문 제작 상품·옵션을 참조한다.
--
-- 확인용 계정: user@cakeshop.local / Admin1234!
-- 확인용 경로:
--   /mypage/reviews          이미 작성한 일반·주문 제작 후기 각 1건
--   /mypage/reviews/writable 새로 작성할 일반·주문 제작 후기 각 1건
--
-- 주문·주문 상품·후기 id 는 다른 로컬 데이터와 AUTO_INCREMENT 를 공유하므로 재실행 때
-- 달라질 수 있다. 화면 진입은 위 목록 경로를 사용한다.

-- ---------------------------------------------------------------------------
-- 0. 초기화 — 이 시드가 만든 SEED-REVIEW- 주문의 연결 데이터만 지운다.
--
-- 다른 도메인의 로컬 데이터와 AUTO_INCREMENT 는 건드리지 않는다.
-- ---------------------------------------------------------------------------

-- FileStorageClient를 거치지 않고 review_images 행만 지우면 S3/로컬 원본이 고아 파일로 남는다.
-- 이미지가 하나라도 있으면 아무 데이터도 지우기 전에 CHECK 위반으로 실행을 멈춘다.
-- 원본 파일과 review_images 행을 함께 정리한 뒤 이 시드를 다시 실행한다.
DROP TEMPORARY TABLE IF EXISTS `seed_review_reapply_guard`;

CREATE TEMPORARY TABLE `seed_review_reapply_guard` (
    `attached_image_count` BIGINT UNSIGNED NOT NULL,
    CONSTRAINT `chk_seed_review_without_attached_images`
        CHECK (`attached_image_count` = 0)
);

INSERT INTO `seed_review_reapply_guard` (`attached_image_count`)
SELECT COUNT(*)
FROM `review_images` image
INNER JOIN `reviews` review ON review.`id` = image.`review_id`
INNER JOIN `order_items` oi ON oi.`id` = review.`order_item_id`
INNER JOIN `orders` o ON o.`id` = oi.`order_id`
WHERE o.`order_number` LIKE 'SEED-REVIEW-%';

DROP TEMPORARY TABLE `seed_review_reapply_guard`;

-- 후기나 주문을 사용하며 생긴 알림은 FK 의 ON DELETE SET NULL 로 남기지 않고 함께 정리한다.
DELETE delivery
FROM `notification_deliveries` delivery
INNER JOIN `notifications` notification
        ON notification.`id` = delivery.`notification_id`
WHERE notification.`order_id` IN (
          SELECT o.`id`
          FROM `orders` o
          WHERE o.`order_number` LIKE 'SEED-REVIEW-%'
      )
   OR notification.`review_id` IN (
          SELECT review.`id`
          FROM `reviews` review
          INNER JOIN `order_items` oi ON oi.`id` = review.`order_item_id`
          INNER JOIN `orders` o ON o.`id` = oi.`order_id`
          WHERE o.`order_number` LIKE 'SEED-REVIEW-%'
      )
   OR notification.`review_reply_id` IN (
          SELECT reply.`id`
          FROM `review_replies` reply
          INNER JOIN `reviews` review ON review.`id` = reply.`review_id`
          INNER JOIN `order_items` oi ON oi.`id` = review.`order_item_id`
          INNER JOIN `orders` o ON o.`id` = oi.`order_id`
          WHERE o.`order_number` LIKE 'SEED-REVIEW-%'
      );

DELETE notification
FROM `notifications` notification
WHERE notification.`order_id` IN (
          SELECT o.`id`
          FROM `orders` o
          WHERE o.`order_number` LIKE 'SEED-REVIEW-%'
      )
   OR notification.`review_id` IN (
          SELECT review.`id`
          FROM `reviews` review
          INNER JOIN `order_items` oi ON oi.`id` = review.`order_item_id`
          INNER JOIN `orders` o ON o.`id` = oi.`order_id`
          WHERE o.`order_number` LIKE 'SEED-REVIEW-%'
      )
   OR notification.`review_reply_id` IN (
          SELECT reply.`id`
          FROM `review_replies` reply
          INNER JOIN `reviews` review ON review.`id` = reply.`review_id`
          INNER JOIN `order_items` oi ON oi.`id` = review.`order_item_id`
          INNER JOIN `orders` o ON o.`id` = oi.`order_id`
          WHERE o.`order_number` LIKE 'SEED-REVIEW-%'
      );

DELETE reply
FROM `review_replies` reply
INNER JOIN `reviews` review ON review.`id` = reply.`review_id`
INNER JOIN `order_items` oi ON oi.`id` = review.`order_item_id`
INNER JOIN `orders` o ON o.`id` = oi.`order_id`
WHERE o.`order_number` LIKE 'SEED-REVIEW-%';

DELETE review
FROM `reviews` review
INNER JOIN `order_items` oi ON oi.`id` = review.`order_item_id`
INNER JOIN `orders` o ON o.`id` = oi.`order_id`
WHERE o.`order_number` LIKE 'SEED-REVIEW-%';

DELETE cancellation
FROM `payment_cancellations` cancellation
INNER JOIN `payments` payment ON payment.`id` = cancellation.`payment_id`
INNER JOIN `orders` o ON o.`id` = payment.`order_id`
WHERE o.`order_number` LIKE 'SEED-REVIEW-%';

DELETE payment
FROM `payments` payment
INNER JOIN `orders` o ON o.`id` = payment.`order_id`
WHERE o.`order_number` LIKE 'SEED-REVIEW-%';

DELETE room_order
FROM `chat_room_orders` room_order
INNER JOIN `orders` o ON o.`id` = room_order.`order_id`
WHERE o.`order_number` LIKE 'SEED-REVIEW-%';

DELETE cart_order
FROM `order_cart_items` cart_order
INNER JOIN `orders` o ON o.`id` = cart_order.`order_id`
WHERE o.`order_number` LIKE 'SEED-REVIEW-%';

DELETE image
FROM `order_item_images` image
INNER JOIN `order_items` oi ON oi.`id` = image.`order_item_id`
INNER JOIN `orders` o ON o.`id` = oi.`order_id`
WHERE o.`order_number` LIKE 'SEED-REVIEW-%';

DELETE item_option
FROM `order_item_options` item_option
INNER JOIN `order_items` oi ON oi.`id` = item_option.`order_item_id`
INNER JOIN `orders` o ON o.`id` = oi.`order_id`
WHERE o.`order_number` LIKE 'SEED-REVIEW-%';

DELETE oi
FROM `order_items` oi
INNER JOIN `orders` o ON o.`id` = oi.`order_id`
WHERE o.`order_number` LIKE 'SEED-REVIEW-%';

DELETE FROM `orders`
WHERE `order_number` LIKE 'SEED-REVIEW-%';
-- =========================================================
-- 후기 기능 로컬 테스트 데이터
--
-- user@cakeshop.local 에 일반·주문 제작 픽업 완료 주문을 각각 두 건씩 둔다.
-- 각 유형은 한 건만 미리 후기를 작성해, 로그인 직후 다음 흐름을 모두 확인할 수 있다.
--   * /mypage/reviews          : 이미 작성한 후기 2건
--   * /mypage/reviews/writable : 새로 작성할 수 있는 후기 2건
-- =========================================================

INSERT INTO `orders` (
    `order_number`,
    `member_id`,
    `request_key`,
    `order_type`,
    `orderer_name`,
    `orderer_phone`,
    `pickup_name`,
    `pickup_phone`,
    `original_amount`,
    `discount_amount`,
    `final_amount`,
    `status`,
    `pickup_at`,
    `payment_expires_at`,
    `request_message`,
    `under_review_at`,
    `approved_at`,
    `ready_at`,
    `picked_up_at`,
    `approved_by`,
    `picked_up_by`,
    `created_at`,
    `updated_at`
)
SELECT
    sample.`order_number`,
    customer.`id`,
    sample.`request_key`,
    sample.`order_type`,
    customer.`name`,
    customer.`phone`,
    customer.`name`,
    customer.`phone`,
    sample.`amount`,
    0,
    sample.`amount`,
    'PICKED_UP',
    sample.`pickup_at`,
    sample.`created_at` + INTERVAL 10 MINUTE,
    sample.`request_message`,
    sample.`under_review_at`,
    sample.`approved_at`,
    sample.`ready_at`,
    sample.`picked_up_at`,
    CASE WHEN sample.`order_type` = 'CUSTOM' THEN admin.`id` END,
    admin.`id`,
    sample.`created_at`,
    CURRENT_TIMESTAMP(6)
FROM `members` customer
CROSS JOIN `members` admin
CROSS JOIN (
    SELECT
        'SEED-REVIEW-GENERAL-REVIEWED' AS `order_number`,
        '00000000-0000-0000-0000-000000000101' AS `request_key`,
        'GENERAL' AS `order_type`,
        35000 AS `amount`,
        TIMESTAMP('2026-08-12 13:00:00') AS `pickup_at`,
        NULL AS `request_message`,
        NULL AS `under_review_at`,
        NULL AS `approved_at`,
        TIMESTAMP('2026-08-12 12:30:00') AS `ready_at`,
        TIMESTAMP('2026-08-12 14:00:00') AS `picked_up_at`,
        TIMESTAMP('2026-08-10 10:00:00') AS `created_at`

    UNION ALL

    SELECT
        'SEED-REVIEW-CUSTOM-REVIEWED',
        '00000000-0000-0000-0000-000000000102',
        'CUSTOM',
        55000,
        TIMESTAMP('2026-08-14 15:00:00'),
        '기념일 분위기에 맞게 깔끔하게 제작해 주세요.',
        TIMESTAMP('2026-08-07 10:05:00'),
        TIMESTAMP('2026-08-07 11:00:00'),
        TIMESTAMP('2026-08-14 13:00:00'),
        TIMESTAMP('2026-08-14 16:00:00'),
        TIMESTAMP('2026-08-07 10:00:00')

    UNION ALL

    SELECT
        'SEED-REVIEW-GENERAL-WRITABLE',
        '00000000-0000-0000-0000-000000000103',
        'GENERAL',
        35000,
        TIMESTAMP('2026-08-16 13:00:00'),
        NULL,
        NULL,
        NULL,
        TIMESTAMP('2026-08-16 12:30:00'),
        TIMESTAMP('2026-08-16 14:00:00'),
        TIMESTAMP('2026-08-15 10:00:00')

    UNION ALL

    SELECT
        'SEED-REVIEW-CUSTOM-WRITABLE',
        '00000000-0000-0000-0000-000000000104',
        'CUSTOM',
        70000,
        TIMESTAMP('2026-08-18 15:00:00'),
        '핑크색 크림으로 밝고 귀엽게 제작해 주세요.',
        TIMESTAMP('2026-08-11 10:05:00'),
        TIMESTAMP('2026-08-11 11:00:00'),
        TIMESTAMP('2026-08-18 13:00:00'),
        TIMESTAMP('2026-08-18 16:00:00'),
        TIMESTAMP('2026-08-11 10:00:00')
) sample
WHERE customer.`email` = 'user@cakeshop.local'
  AND admin.`email` = 'admin@cakeshop.local';

INSERT INTO `order_items` (
    `order_id`,
    `product_id`,
    `product_name`,
    `product_type`,
    `quantity`,
    `base_price`,
    `option_amount`,
    `total_amount`,
    `requirements`,
    `preparation_days`,
    `stock_deducted_at`,
    `stock_restored_at`
)
SELECT
    o.`id`,
    p.`id`,
    p.`name`,
    p.`product_type`,
    1,
    p.`base_price`,
    sample.`option_amount`,
    p.`base_price` + sample.`option_amount`,
    sample.`requirements`,
    p.`preparation_days`,
    sample.`stock_deducted_at`,
    NULL
FROM `orders` o
INNER JOIN (
    SELECT
        'SEED-REVIEW-GENERAL-REVIEWED' AS `order_number`,
        '딸기 생크림 케이크 1호' AS `product_name`,
        0 AS `option_amount`,
        NULL AS `requirements`,
        TIMESTAMP('2026-08-10 10:05:00') AS `stock_deducted_at`

    UNION ALL

    SELECT
        'SEED-REVIEW-CUSTOM-REVIEWED',
        '레터링 생크림 케이크',
        0,
        '레터링 문구: 우리의 특별한 날',
        NULL

    UNION ALL

    SELECT
        'SEED-REVIEW-GENERAL-WRITABLE',
        '딸기 생크림 케이크 1호',
        0,
        NULL,
        TIMESTAMP('2026-08-15 10:05:00')

    UNION ALL

    SELECT
        'SEED-REVIEW-CUSTOM-WRITABLE',
        '레터링 생크림 케이크',
        15000,
        '레터링 문구: 오늘도 행복하자',
        NULL
) sample
        ON sample.`order_number` = o.`order_number`
INNER JOIN `products` p
        ON p.`name` = sample.`product_name`;

-- 주문 제작 주문은 실제 주문 화면과 같은 옵션 스냅샷을 갖는다.
INSERT INTO `order_item_options` (
    `order_item_id`,
    `product_option_id`,
    `option_group_name`,
    `option_name`,
    `additional_price`
)
SELECT
    oi.`id`,
    po.`id`,
    pog.`name`,
    po.`name`,
    po.`additional_price`
FROM `orders` o
INNER JOIN `order_items` oi
        ON oi.`order_id` = o.`id`
INNER JOIN (
    SELECT
        'SEED-REVIEW-CUSTOM-REVIEWED' AS `order_number`,
        '케이크 크기' AS `group_name`,
        '1호' AS `option_name`

    UNION ALL

    SELECT
        'SEED-REVIEW-CUSTOM-REVIEWED',
        '크림 색상',
        '화이트'

    UNION ALL

    SELECT
        'SEED-REVIEW-CUSTOM-WRITABLE',
        '케이크 크기',
        '2호'

    UNION ALL

    SELECT
        'SEED-REVIEW-CUSTOM-WRITABLE',
        '크림 색상',
        '핑크'
) sample
        ON sample.`order_number` = o.`order_number`
INNER JOIN `product_option_groups` pog
        ON pog.`product_id` = oi.`product_id`
       AND pog.`name` = sample.`group_name`
INNER JOIN `product_options` po
        ON po.`option_group_id` = pog.`id`
       AND po.`name` = sample.`option_name`;

-- 결제 완료 이력까지 넣어 주문·결제 화면에서도 구매 완료 주문으로 일관되게 보이게 한다.
INSERT INTO `payments` (
    `order_id`,
    `toss_order_id`,
    `payment_key`,
    `idempotency_key`,
    `method`,
    `amount`,
    `status`,
    `requested_at`,
    `approved_at`,
    `created_at`,
    `updated_at`
)
SELECT
    o.`id`,
    CONCAT('TOSS-', o.`order_number`),
    CONCAT('PAYMENT-KEY-', o.`order_number`),
    CONCAT('PAYMENT-IDEMPOTENCY-', o.`order_number`),
    'CARD',
    o.`final_amount`,
    'DONE',
    o.`created_at`,
    o.`created_at` + INTERVAL 5 MINUTE,
    o.`created_at`,
    CURRENT_TIMESTAMP(6)
FROM `orders` o
WHERE o.`order_number` IN (
    'SEED-REVIEW-GENERAL-REVIEWED',
    'SEED-REVIEW-CUSTOM-REVIEWED',
    'SEED-REVIEW-GENERAL-WRITABLE',
    'SEED-REVIEW-CUSTOM-WRITABLE'
);

INSERT INTO `reviews` (
    `order_item_id`,
    `product_id`,
    `member_id`,
    `overall_rating`,
    `taste_rating`,
    `design_rating`,
    `service_rating`,
    `content`,
    `status`,
    `created_at`,
    `updated_at`
)
SELECT
    oi.`id`,
    oi.`product_id`,
    o.`member_id`,
    sample.`overall_rating`,
    sample.`taste_rating`,
    sample.`design_rating`,
    sample.`service_rating`,
    sample.`content`,
    'PUBLISHED',
    o.`picked_up_at` + INTERVAL 1 HOUR,
    o.`picked_up_at` + INTERVAL 1 HOUR
FROM `orders` o
INNER JOIN `order_items` oi
        ON oi.`order_id` = o.`id`
INNER JOIN (
    SELECT
        'SEED-REVIEW-GENERAL-REVIEWED' AS `order_number`,
        5 AS `overall_rating`,
        5 AS `taste_rating`,
        5 AS `design_rating`,
        5 AS `service_rating`,
        '딸기가 신선하고 생크림이 부드러워서 가족 모두 맛있게 먹었어요.' AS `content`

    UNION ALL

    SELECT
        'SEED-REVIEW-CUSTOM-REVIEWED',
        4,
        4,
        5,
        5,
        '요청한 레터링과 색상이 그대로 나와서 기념일에 정말 만족스러웠어요.'
) sample
        ON sample.`order_number` = o.`order_number`;

-- 직접 INSERT 한 후기와 상품 화면의 평균·개수가 어긋나지 않도록 공개 후기 기준으로 맞춘다.
UPDATE `products` p
LEFT JOIN (
    SELECT
        r.`product_id`,
        AVG(r.`overall_rating`) AS `average_rating`,
        COUNT(*) AS `review_count`
    FROM `reviews` r
    WHERE r.`status` = 'PUBLISHED'
    GROUP BY r.`product_id`
) review_aggregate
        ON review_aggregate.`product_id` = p.`id`
SET p.`average_rating` = COALESCE(review_aggregate.`average_rating`, 0.00),
    p.`review_count` = COALESCE(review_aggregate.`review_count`, 0),
    p.`updated_at` = p.`updated_at`
WHERE p.`name` IN ('딸기 생크림 케이크 1호', '레터링 생크림 케이크');

-- 실행 직후 기대값: 유형별 작성 완료 1건·작성 가능 1건, 결제 완료 합계 4건.
SELECT o.`order_type` AS `주문유형`,
       SUM(review.`id` IS NOT NULL) AS `작성완료`,
       SUM(review.`id` IS NULL) AS `작성가능`
FROM `orders` o
INNER JOIN `order_items` oi ON oi.`order_id` = o.`id`
LEFT JOIN `reviews` review ON review.`order_item_id` = oi.`id`
WHERE o.`order_number` LIKE 'SEED-REVIEW-%'
GROUP BY o.`order_type`
ORDER BY o.`order_type`;
