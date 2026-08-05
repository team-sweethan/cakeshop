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

-- 레거시 target_url URL 문자열에서 실제 존재하는 주문/게시글 ID 안전 이관 (REGEXP & JOIN 검증)
UPDATE `notifications` n
JOIN `orders` o
    ON o.`id` = CAST(SUBSTRING_INDEX(n.`target_url`, '/orders/', -1) AS UNSIGNED)
SET n.`order_id` = o.`id`
WHERE n.`order_id` IS NULL
  AND n.`target_url` REGEXP '^/orders/[0-9]+$';

UPDATE `notifications`
SET `post_id` = CAST(SUBSTRING_INDEX(`target_url`, '/community/', -1) AS UNSIGNED)
WHERE `post_id` IS NULL
  AND `target_url` REGEXP '^/community/[0-9]+$';

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
        ON DELETE SET NULL;

-- ---------------------------------------------------------
-- 1-3. 기존 알림 데이터의 event_key 백필
-- ---------------------------------------------------------
UPDATE `notifications`
SET `event_key` = CONCAT('LEGACY_NOTIFICATION:', `id`)
WHERE `event_key` IS NULL;

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
-- 2-2. 기존 데이터 보정 및 notification_id 중복 정리 (최신 1건만 보존)
-- ---------------------------------------------------------
DELETE d1 FROM `notification_deliveries` d1
INNER JOIN `notification_deliveries` d2
    ON d1.`notification_id` = d2.`notification_id`
   AND d1.`id` < d2.`id`;

UPDATE `notification_deliveries`
SET `status` = 'PENDING'
WHERE `status` = 'REQUESTED';

UPDATE `notification_deliveries`
SET `template_code` = 'NOTI_DEFAULT'
WHERE `template_code` IS NULL;

-- ---------------------------------------------------------
-- 2-3. 컬럼 정리, FK 및 유니크 제약조건 추가
-- ---------------------------------------------------------
ALTER TABLE `notification_deliveries`
    ADD COLUMN `updated_at`
        DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6)
        COMMENT '이력 수정 시간'
        AFTER `created_at`,

    MODIFY COLUMN `recipient`
        VARCHAR(30) NOT NULL
        COMMENT '수신 전화번호',

    MODIFY COLUMN `template_code`
        VARCHAR(50) NOT NULL
        COMMENT '알림 문자 템플릿 코드',

    MODIFY COLUMN `provider_message_id`
        VARCHAR(200) NULL
        COMMENT '발송 중계사 메시지 ID',

    MODIFY COLUMN `status`
        VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        COMMENT '발송 상태 (PENDING, SENT, DELIVERED, FAILED)',

    MODIFY COLUMN `failure_reason`
        TEXT NULL
        COMMENT '발송 실패 사유',

    DROP COLUMN `channel`,
    DROP COLUMN `failure_code`,
    DROP COLUMN `requested_at`,
    DROP COLUMN `sent_at`,
    DROP COLUMN `clicked_at`,

    ADD CONSTRAINT `fk_deliveries_notification`
        FOREIGN KEY (`notification_id`)
        REFERENCES `notifications` (`id`)
        ON DELETE CASCADE,

    ADD CONSTRAINT `uk_deliveries_notification`
        UNIQUE (`notification_id`),

    ADD CONSTRAINT `uk_deliveries_provider_msg_id`
        UNIQUE (`provider_message_id`);

-- ---------------------------------------------------------
-- 2-4. 발송 상태별 조회 인덱스
-- ---------------------------------------------------------
CREATE INDEX `idx_deliveries_status_created`
    ON `notification_deliveries` (`status`, `created_at` DESC);