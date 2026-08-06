-- align_notification_schema
-- 생성: 2026-08-04 15:00:10
--
-- 규칙
-- * 이 파일은 머지된 뒤 절대 수정하지 않는다.
-- * 변경이 필요하면 새로운 버전의 migration 파일을 만든다.
-- * 로컬 샘플 데이터는 이 파일에 넣지 않는다.
-- * 로컬 샘플 데이터는 db/seed/seed-local.sql을 사용한다.
-- * 서로 의존하는 DDL은 동일한 migration 파일에 함께 작성한다.

-- =========================================================
-- 1. notifications
-- =========================================================

-- ---------------------------------------------------------
-- 1-1. V0에서 생성된 기존 FK 제거
-- ---------------------------------------------------------
ALTER TABLE `notifications`
    DROP FOREIGN KEY `fk_notifications_receiver`,
    DROP FOREIGN KEY `fk_notifications_order`,
    DROP FOREIGN KEY `fk_notifications_chat_message`;

-- ---------------------------------------------------------
-- 1-2. 신규 컬럼 추가, 미사용 컬럼 삭제, FK 정책 변경
--
-- event_key는 기존 데이터 백필을 위해 우선 NULL 허용으로 추가한다.
-- ---------------------------------------------------------
ALTER TABLE `notifications`
    ADD COLUMN `actor_id`
        BIGINT NULL
        COMMENT '발생 회원/관리자 ID (members.id)'
        AFTER `receiver_id`,

    ADD COLUMN `chat_room_id`
        BIGINT NULL
        COMMENT '관련 채팅방 ID'
        AFTER `order_id`,

    ADD COLUMN `post_id`
        BIGINT NULL
        COMMENT '관련 게시글 ID'
        AFTER `chat_message_id`,

    ADD COLUMN `comment_id`
        BIGINT NULL
        COMMENT '관련 댓글/대댓글 ID'
        AFTER `post_id`,

    ADD COLUMN `review_id`
        BIGINT NULL
        COMMENT '관련 리뷰 ID'
        AFTER `comment_id`,

    ADD COLUMN `review_reply_id`
        BIGINT NULL
        COMMENT '관련 사장님 리뷰 답글 ID'
        AFTER `review_id`,

    ADD COLUMN `user_coupon_id`
        BIGINT NULL
        COMMENT '발급된 회원 쿠폰 ID'
        AFTER `review_reply_id`,

    ADD COLUMN `delivery_scope`
        VARCHAR(30) NOT NULL DEFAULT 'WEB_ONLY'
        COMMENT '발송 범위 (WEB_ONLY, WEB_AND_SMS)'
        AFTER `content`,

    ADD COLUMN `event_key`
        VARCHAR(100) NULL
        COMMENT '동일 이벤트 중복 알림 방지 키'
        AFTER `read_at`;

-- 레거시 target_url URL 문자열에서 실제 존재하는 주문/게시글/리뷰/쿠폰 ID 안전 이관 (REGEXP & JOIN 검증)
UPDATE `notifications` n
JOIN `orders` o
    ON o.`id` = CAST(SUBSTRING_INDEX(n.`target_url`, '/orders/', -1) AS UNSIGNED)
SET n.`order_id` = o.`id`
WHERE n.`order_id` IS NULL
  AND n.`target_url` REGEXP '^/orders/[0-9]+$';

UPDATE `notifications` n
JOIN `posts` p
    ON p.`id` = CAST(SUBSTRING_INDEX(n.`target_url`, '/community/', -1) AS UNSIGNED)
SET n.`post_id` = p.`id`
WHERE n.`post_id` IS NULL
  AND n.`target_url` REGEXP '^/community/[0-9]+$';

UPDATE `notifications` n
JOIN `reviews` r
    ON r.`id` = CAST(SUBSTRING_INDEX(n.`target_url`, '/reviews/', -1) AS UNSIGNED)
SET n.`review_id` = r.`id`
WHERE n.`review_id` IS NULL
  AND n.`target_url` REGEXP '^/reviews/[0-9]+$';

UPDATE `notifications` n
JOIN `member_coupons` mc
    ON mc.`id` = CAST(SUBSTRING_INDEX(n.`target_url`, '/coupons/', -1) AS UNSIGNED)
SET n.`user_coupon_id` = mc.`id`
WHERE n.`user_coupon_id` IS NULL
  AND n.`target_url` REGEXP '^/coupons/[0-9]+$';

UPDATE `notifications` n
JOIN `chat_messages` cm ON cm.`id` = n.`chat_message_id`
SET n.`chat_room_id` = cm.`chat_room_id`
WHERE n.`chat_room_id` IS NULL AND n.`chat_message_id` IS NOT NULL;

ALTER TABLE `notifications`
    DROP COLUMN `target_url`,

    ADD CONSTRAINT `fk_notifications_receiver`
        FOREIGN KEY (`receiver_id`)
        REFERENCES `members` (`id`)
        ON DELETE CASCADE,

    ADD CONSTRAINT `fk_notifications_actor`
        FOREIGN KEY (`actor_id`)
        REFERENCES `members` (`id`)
        ON DELETE SET NULL,

    ADD CONSTRAINT `fk_notifications_order`
        FOREIGN KEY (`order_id`)
        REFERENCES `orders` (`id`)
        ON DELETE SET NULL,

    ADD CONSTRAINT `fk_notifications_chat_message`
        FOREIGN KEY (`chat_message_id`)
        REFERENCES `chat_messages` (`id`)
        ON DELETE SET NULL,

    ADD CONSTRAINT `fk_notifications_chat_room`
        FOREIGN KEY (`chat_room_id`)
        REFERENCES `chat_rooms` (`id`)
        ON DELETE SET NULL,

    ADD CONSTRAINT `fk_notifications_post`
        FOREIGN KEY (`post_id`)
        REFERENCES `posts` (`id`)
        ON DELETE SET NULL,

    ADD CONSTRAINT `fk_notifications_comment`
        FOREIGN KEY (`comment_id`)
        REFERENCES `comments` (`id`)
        ON DELETE SET NULL,

    ADD CONSTRAINT `fk_notifications_review`
        FOREIGN KEY (`review_id`)
        REFERENCES `reviews` (`id`)
        ON DELETE SET NULL,

    ADD CONSTRAINT `fk_notifications_review_reply`
        FOREIGN KEY (`review_reply_id`)
        REFERENCES `review_replies` (`id`)
        ON DELETE SET NULL,

    ADD CONSTRAINT `fk_notifications_user_coupon`
        FOREIGN KEY (`user_coupon_id`)
        REFERENCES `member_coupons` (`id`)
        ON DELETE SET NULL;

-- ---------------------------------------------------------
-- 1-3. 기존 알림 데이터의 event_key 및 notification_type 백필 정규화
-- ---------------------------------------------------------
UPDATE `notifications`
SET `event_key` = CONCAT('LEGACY_NOTIFICATION:', `id`)
WHERE `event_key` IS NULL;

UPDATE `notifications`
SET `notification_type` = CASE
    -- 레거시 주문/결제
    WHEN `notification_type` IN ('ORDER', 'PAYMENT', '주문완료', '결제완료') THEN 'ORDER_PAID'
    WHEN `notification_type` IN ('CUSTOM_ORDER', '주문제작') THEN 'CUSTOM_ORDER_PAID'
    WHEN `notification_type` IN ('CANCEL', '주문취소') THEN 'ORDER_CANCELED'

    -- 레거시 픽업
    WHEN `notification_type` IN ('PICKUP', '픽업대기', '픽업안내') THEN 'CUSTOMER_PICKUP_REMINDER_TODAY'
    WHEN `notification_type` IN ('PICKED_UP', '픽업완료') THEN 'CUSTOMER_ORDER_PICKED_UP'

    -- 레거시 댓글/답글/리뷰
    WHEN `notification_type` IN ('NEW_COMMENT', 'COMMENT', '댓글', '댓글 알림') THEN 'CUSTOMER_COMMENT'
    WHEN `notification_type` IN ('COMMENT_REPLY', 'REPLY', '답글', '답글 알림') THEN 'CUSTOMER_COMMENT_REPLY'
    WHEN `notification_type` IN ('REVIEW', '리뷰', '리뷰 답글 알림') THEN 'CUSTOMER_REVIEW'

    -- 레거시 쿠폰/채팅
    WHEN `notification_type` IN ('COUPON_ISSUED', '쿠폰', '쿠폰 발급') THEN 'COUPON'
    WHEN `notification_type` IN ('CHAT', '채팅', '채팅 답변') THEN 'CUSTOMER_CHAT'

    ELSE `notification_type`
END
WHERE `notification_type` IS NOT NULL;

-- ---------------------------------------------------------
-- 1-4. event_key 필수 처리 및 중복 방지 제약조건 추가
-- ---------------------------------------------------------
ALTER TABLE `notifications`
    MODIFY COLUMN `event_key`
        VARCHAR(100) NOT NULL
        COMMENT '동일 이벤트 중복 알림 방지 키',

    ADD CONSTRAINT `uk_notifications_receiver_event`
        UNIQUE (`receiver_id`, `event_key`);

-- ---------------------------------------------------------
-- 1-5. 알림함 조회 인덱스
-- ---------------------------------------------------------
CREATE INDEX `idx_notifications_receiver_created`
    ON `notifications` (`receiver_id`, `created_at` DESC);

CREATE INDEX `idx_notifications_receiver_read`
    ON `notifications` (`receiver_id`, `is_read`, `created_at` DESC);


-- =========================================================
-- 2. notification_deliveries
-- =========================================================

-- ---------------------------------------------------------
-- 2-1. V0에서 생성된 기존 FK 제거
-- ---------------------------------------------------------
ALTER TABLE `notification_deliveries`
    DROP FOREIGN KEY `fk_notification_deliveries_notification`;

-- ---------------------------------------------------------
-- 2-2. 기존 데이터 보정 및 발송 시각 이력 보존
-- ---------------------------------------------------------
UPDATE `notification_deliveries`
SET `status` = 'PENDING'
WHERE `status` = 'REQUESTED';

UPDATE `notification_deliveries`
SET `template_code` = 'NOTI_DEFAULT'
WHERE `template_code` IS NULL;

UPDATE `notification_deliveries`
SET `sent_at` = COALESCE(`sent_at`, `requested_at`, `created_at`)
WHERE `status` IN ('SENT', 'DELIVERED') AND `sent_at` IS NULL;

-- ---------------------------------------------------------
-- 2-3. 컬럼 정리 및 FK 추가
-- ---------------------------------------------------------
ALTER TABLE `notification_deliveries`
    ADD COLUMN `updated_at`
        DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6)
        COMMENT '이력 수정 시간'
        AFTER `created_at`,

    MODIFY COLUMN `recipient`
        VARCHAR(500) NOT NULL
        COMMENT '수신 전화번호',

    MODIFY COLUMN `template_code`
        VARCHAR(100) NOT NULL
        COMMENT '알림 문자 템플릿 코드',

    MODIFY COLUMN `provider_message_id`
        VARCHAR(200) NULL
        COMMENT '발송 중계사 메시지 ID',

    MODIFY COLUMN `status`
        VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        COMMENT '발송 상태 (PENDING, SENT, DELIVERED, FAILED)',

    MODIFY COLUMN `sent_at`
        DATETIME NULL
        COMMENT '실제 발송 완료 시각',

    MODIFY COLUMN `failure_reason`
        TEXT NULL
        COMMENT '발송 실패 사유',

    DROP COLUMN `channel`,
    DROP COLUMN `failure_code`,
    DROP COLUMN `requested_at`,
    DROP COLUMN `clicked_at`,

    ADD CONSTRAINT `fk_deliveries_notification`
        FOREIGN KEY (`notification_id`)
        REFERENCES `notifications` (`id`)
        ON DELETE CASCADE;

-- ---------------------------------------------------------
-- 2-4. 조회 인덱스
-- ---------------------------------------------------------
CREATE INDEX `idx_deliveries_provider_msg_id`
    ON `notification_deliveries` (`provider_message_id`);

CREATE INDEX `idx_deliveries_status_created`
    ON `notification_deliveries` (`status`, `created_at` DESC);