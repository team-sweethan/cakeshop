-- add_notification_tables
-- 생성: 2026-08-04 11:16:20
--
-- 규칙
-- * 이 파일은 머지된 뒤 절대 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다.

-- 1. notifications 테이블: 컬럼 보충, 미사용 FK 삭제 및 최종 FK 정책 적용
ALTER TABLE `notifications`
    ADD COLUMN `actor_id`          BIGINT NULL COMMENT '발생 회원/관리자 ID (members.id)' AFTER `receiver_id`,
    ADD COLUMN `chat_room_id`      BIGINT NULL COMMENT '관련 채팅방 ID' AFTER `order_id`,
    ADD COLUMN `post_id`           BIGINT NULL COMMENT '관련 게시글 ID' AFTER `chat_message_id`,
    ADD COLUMN `comment_id`        BIGINT NULL COMMENT '관련 댓글/대댓글 ID' AFTER `post_id`,
    ADD COLUMN `review_id`         BIGINT NULL COMMENT '관련 리뷰 ID' AFTER `comment_id`,
    ADD COLUMN `review_reply_id`   BIGINT NULL COMMENT '관련 사장님 리뷰 답글 ID' AFTER `review_id`,
    ADD COLUMN `user_coupon_id`    BIGINT NULL COMMENT '발급된 회원 쿠폰 ID' AFTER `review_reply_id`,
    ADD COLUMN `delivery_scope`    VARCHAR(30) NOT NULL DEFAULT 'WEB_ONLY' COMMENT '발송 범위 (WEB_ONLY, WEB_AND_SMS)' AFTER `content`,
    ADD COLUMN `event_key`         VARCHAR(100) NULL COMMENT '동일 이벤트 중복 알림 방지 키' AFTER `read_at`,
    DROP COLUMN `target_url`,
    DROP FOREIGN KEY `fk_notifications_receiver`,
    DROP FOREIGN KEY `fk_notifications_order`,
    DROP FOREIGN KEY `fk_notifications_chat_message`,
    ADD CONSTRAINT `fk_notifications_receiver` FOREIGN KEY (`receiver_id`) REFERENCES `members` (`id`) ON DELETE CASCADE,
    ADD CONSTRAINT `fk_notifications_actor`    FOREIGN KEY (`actor_id`)    REFERENCES `members` (`id`) ON DELETE SET NULL,
    ADD CONSTRAINT `fk_notifications_order`    FOREIGN KEY (`order_id`)    REFERENCES `orders` (`id`)  ON DELETE SET NULL;

-- 기존 알림 데이터의 event_key 고유값 안전 보정
UPDATE `notifications`
SET `event_key` = CONCAT('LEGACY_NOTIFICATION:', `id`)
WHERE `event_key` IS NULL;

-- event_key NOT NULL 변경 및 유니크 제약조건/인덱스 생성
ALTER TABLE `notifications`
    MODIFY COLUMN `event_key` VARCHAR(100) NOT NULL COMMENT '동일 이벤트 중복 알림 방지 키',
    ADD CONSTRAINT `uk_notifications_receiver_event` UNIQUE (`receiver_id`, `event_key`);

CREATE INDEX `idx_notifications_receiver_created` ON `notifications` (`receiver_id`, `created_at` DESC);
CREATE INDEX `idx_notifications_receiver_read`    ON `notifications` (`receiver_id`, `is_read`, `created_at` DESC);

-- 2. notification_deliveries 레거시 데이터 안전 보정
UPDATE `notification_deliveries`
SET `status` = 'PENDING'
WHERE `status` = 'REQUESTED';

-- 기존 NULL 템플릿 코드에 레거시 식별값 입력
UPDATE `notification_deliveries`
SET `template_code` = 'NOTI_DEFAULT'
WHERE `template_code` IS NULL;

-- 3. notification_deliveries 테이블: FK ON DELETE CASCADE, 타입 및 제약조건/인덱스 반영
ALTER TABLE `notification_deliveries`
    ADD COLUMN `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '이력 수정 시간' AFTER `created_at`,
    MODIFY COLUMN `recipient` VARCHAR(30) NOT NULL COMMENT '수신 전화번호',
    MODIFY COLUMN `template_code` VARCHAR(50) NOT NULL COMMENT '알림 문자 템플릿 코드',
    MODIFY COLUMN `provider_message_id` VARCHAR(100) NULL COMMENT '발송 중계사 메시지 ID',
    MODIFY COLUMN `status` VARCHAR(30) NOT NULL DEFAULT 'PENDING' COMMENT '발송 상태',
    MODIFY COLUMN `failure_reason` TEXT NULL COMMENT '발송 실패 사유',
    DROP COLUMN `channel`,
    DROP COLUMN `failure_code`,
    DROP COLUMN `requested_at`,
    DROP COLUMN `sent_at`,
    DROP COLUMN `clicked_at`,
    DROP FOREIGN KEY `fk_notification_deliveries_notification`,
    ADD CONSTRAINT `fk_notification_deliveries_notification` FOREIGN KEY (`notification_id`) REFERENCES `notifications` (`id`) ON DELETE CASCADE,
    ADD CONSTRAINT `uk_deliveries_notification` UNIQUE (`notification_id`),
    ADD CONSTRAINT `uk_deliveries_provider_msg_id` UNIQUE (`provider_message_id`);

CREATE INDEX `idx_deliveries_status_created` ON `notification_deliveries` (`status`, `created_at` DESC);
