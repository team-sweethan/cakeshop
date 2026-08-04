-- add_notification_tables
-- 생성: 2026-08-04 15:00:10
--
-- 규칙
-- * 이 파일은 머지된 뒤 절대 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다.

-- 주의: 기존 V0 레거시 notifications 및 notification_deliveries 데이터가 삭제되고 정밀 스키마로 재구축됩니다.
-- 개발 초기 스키마 정돈용 migration입니다.
DROP TABLE IF EXISTS `notification_deliveries`;
DROP TABLE IF EXISTS `notifications`;

-- =========================================================
-- 1. notifications (웹 알림 원본 테이블)
-- =========================================================
CREATE TABLE `notifications` (
    `id`                BIGINT NOT NULL AUTO_INCREMENT COMMENT '알림 PK',
    `receiver_id`       BIGINT NOT NULL COMMENT '수신 회원 ID (members.id)',
    `actor_id`          BIGINT NULL COMMENT '발생 회원/관리자 ID (members.id)',
    `order_id`          BIGINT NULL COMMENT '관련 주문 ID',
    `chat_room_id`      BIGINT NULL COMMENT '관련 채팅방 ID',
    `chat_message_id`   BIGINT NULL COMMENT '관련 채팅 메시지 ID',
    `post_id`           BIGINT NULL COMMENT '관련 게시글 ID',
    `comment_id`        BIGINT NULL COMMENT '관련 댓글/대댓글 ID',
    `review_id`         BIGINT NULL COMMENT '관련 리뷰 ID',
    `review_reply_id`   BIGINT NULL COMMENT '관련 사장님 리뷰 답글 ID',
    `user_coupon_id`    BIGINT NULL COMMENT '발급된 회원 쿠폰 ID',
    `notification_type` VARCHAR(50) NOT NULL COMMENT '알림 유형 (Java Enum)',
    `title`             VARCHAR(200) NOT NULL COMMENT '알림 제목',
    `content`           TEXT NOT NULL COMMENT '알림 내용',
    `delivery_scope`    VARCHAR(30) NOT NULL DEFAULT 'WEB_ONLY' COMMENT '발송 범위 (WEB_ONLY, WEB_AND_SMS)',
    `is_read`           TINYINT(1) NOT NULL DEFAULT 0 COMMENT '웹 알림 읽음 여부 (0:미읽음, 1:읽음)',
    `read_at`           DATETIME(6) NULL COMMENT '웹 알림 읽은 시간',
    `event_key`         VARCHAR(100) NOT NULL COMMENT '동일 이벤트 중복 알림 방지 키',
    `created_at`        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '알림 생성 시간',
    PRIMARY KEY (`id`),
    
    -- 동일 수신자 중복 알림 방지 제약조건
    CONSTRAINT `uk_notifications_receiver_event` UNIQUE (`receiver_id`, `event_key`),
    
    -- 부모 테이블(members, orders) FK 연결
    CONSTRAINT `fk_notifications_receiver` FOREIGN KEY (`receiver_id`) REFERENCES `members` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_notifications_actor`    FOREIGN KEY (`actor_id`)    REFERENCES `members` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_notifications_order`    FOREIGN KEY (`order_id`)    REFERENCES `orders` (`id`)  ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='웹 알림 원본';

-- [인덱스] 알림함 목록 최적화
CREATE INDEX `idx_notifications_receiver_created` ON `notifications` (`receiver_id`, `created_at` DESC);
CREATE INDEX `idx_notifications_receiver_read`    ON `notifications` (`receiver_id`, `is_read`, `created_at` DESC);


-- =========================================================
-- 2. notification_deliveries (카카오 알림톡/SMS 발송 이력 테이블)
-- =========================================================
CREATE TABLE `notification_deliveries` (
    `id`                  BIGINT NOT NULL AUTO_INCREMENT COMMENT '발송 이력 PK',
    `notification_id`     BIGINT NOT NULL COMMENT '원본 웹 알림 ID (notifications.id)',
    `recipient`           VARCHAR(30) NOT NULL COMMENT '수신 전화번호',
    `template_code`       VARCHAR(50) NOT NULL COMMENT '알림톡/SMS 템플릿 코드',
    `provider_message_id` VARCHAR(100) NULL COMMENT '발송 중계사 메시지 ID',
    `status`              VARCHAR(30) NOT NULL DEFAULT 'PENDING'
                          COMMENT '발송 상태 (PENDING, SENT, DELIVERED, FAILED)',
    `failure_reason`      TEXT NULL COMMENT '발송 실패 사유',
    `delivered_at`        DATETIME(6) NULL COMMENT '수신자 실제 전달 완료 시간',
    `created_at`          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                          COMMENT '이력 생성 시간',
    `updated_at`          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                          ON UPDATE CURRENT_TIMESTAMP(6)
                          COMMENT '이력 수정 시간',

    PRIMARY KEY (`id`),

    CONSTRAINT `uk_deliveries_notification`
        UNIQUE (`notification_id`),

    CONSTRAINT `uk_deliveries_provider_msg_id`
        UNIQUE (`provider_message_id`),

    CONSTRAINT `fk_deliveries_notification`
        FOREIGN KEY (`notification_id`)
        REFERENCES `notifications` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='카카오 알림톡/SMS 발송 이력';

-- [인덱스] 발송 상태별 이력 조회 최적화
CREATE INDEX `idx_deliveries_status_created` ON `notification_deliveries` (`status`, `created_at` DESC);
