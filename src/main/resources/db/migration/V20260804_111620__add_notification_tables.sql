-- add_notification_tables
-- 생성: 2026-08-04 11:16:20
--
-- 규칙
-- * 이 파일은 머지된 뒤 절대 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다.

-- 1. notifications 테이블 신규 컬럼, 외래키, 유니크 제약조건 보충
ALTER TABLE `notifications`
    ADD COLUMN `actor_id`          BIGINT NULL COMMENT '발생 회원/관리자 ID (members.id)' AFTER `receiver_id`,
    ADD COLUMN `chat_room_id`      BIGINT NULL COMMENT '관련 채팅방 ID' AFTER `order_id`,
    ADD COLUMN `post_id`           BIGINT NULL COMMENT '관련 게시글 ID' AFTER `chat_message_id`,
    ADD COLUMN `comment_id`        BIGINT NULL COMMENT '관련 댓글/대댓글 ID' AFTER `post_id`,
    ADD COLUMN `review_id`         BIGINT NULL COMMENT '관련 리뷰 ID' AFTER `comment_id`,
    ADD COLUMN `review_reply_id`   BIGINT NULL COMMENT '관련 사장님 리뷰 답글 ID' AFTER `review_id`,
    ADD COLUMN `user_coupon_id`    BIGINT NULL COMMENT '발급된 회원 쿠폰 ID' AFTER `review_reply_id`,
    ADD COLUMN `delivery_scope`    VARCHAR(30) NOT NULL DEFAULT 'WEB_ONLY' COMMENT '발송 범위 (WEB_ONLY, WEB_AND_SMS)' AFTER `content`,
    ADD COLUMN `event_key`         VARCHAR(100) NOT NULL DEFAULT '' COMMENT '동일 이벤트 중복 알림 방지 키' AFTER `target_url`,
    ADD CONSTRAINT `fk_notifications_actor` FOREIGN KEY (`actor_id`) REFERENCES `members` (`id`) ON DELETE SET NULL,
    ADD CONSTRAINT `uk_notifications_receiver_event` UNIQUE (`receiver_id`, `event_key`);

-- notifications 인덱스 추가
CREATE INDEX `idx_notifications_receiver_created` ON `notifications` (`receiver_id`, `created_at` DESC);
CREATE INDEX `idx_notifications_receiver_read`    ON `notifications` (`receiver_id`, `is_read`, `created_at` DESC);

-- 2. notification_deliveries 테이블 신규 컬럼, 제약조건, 인덱스 보충
ALTER TABLE `notification_deliveries`
    ADD COLUMN `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '이력 수정 시간' AFTER `created_at`,
    ADD CONSTRAINT `uk_deliveries_notification` UNIQUE (`notification_id`),
    ADD CONSTRAINT `uk_deliveries_provider_msg_id` UNIQUE (`provider_message_id`);

-- notification_deliveries 인덱스 추가
CREATE INDEX `idx_deliveries_status_created` ON `notification_deliveries` (`status`, `created_at` DESC);
