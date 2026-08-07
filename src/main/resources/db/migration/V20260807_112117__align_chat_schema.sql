-- align_chat_schema
-- 생성: 2026-08-07 11:21:17
--
-- 규칙
-- * 이 파일은 머지된 뒤 절대 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다.

-- =========================================================
-- align_chat_schema
-- 생성: 2026-08-07
--
-- 목적
-- * 기존 초기 채팅 스키마를 최종 채팅 정책에 맞게 변경한다.
-- * 기존 채팅 데이터가 없는 것을 전제로 한다.
--
-- 규칙
-- * 이미 병합된 기존 Migration 파일은 수정하지 않는다.
-- * 이후 변경이 필요하면 새로운 Migration 파일을 작성한다.
-- =========================================================


-- =========================================================
-- 1. chat_rooms
-- =========================================================

-- ---------------------------------------------------------
-- 1-1. 기존 관리자 배정 FK 제거
--
-- 관리자 계정 하나가 전체 채팅방을 관리하므로
-- 채팅방에 admin_id를 저장하지 않는다.
-- 실제 메시지를 보낸 관리자는 chat_messages.sender_id로 확인한다.
-- ---------------------------------------------------------

ALTER TABLE `chat_rooms`
    DROP FOREIGN KEY `fk_chat_rooms_admin`;


-- ---------------------------------------------------------
-- 1-2. chat_rooms 최종 구조 반영
-- ---------------------------------------------------------

ALTER TABLE `chat_rooms`

    DROP COLUMN `admin_id`,

    MODIFY COLUMN `status`
        VARCHAR(20) NOT NULL
        DEFAULT 'OPEN'
        COMMENT '관리자 내부 상담 상태: OPEN, CLOSED',

    ADD COLUMN `response_status`
        VARCHAR(30) NOT NULL
        COMMENT '답변 상태: WAITING_ADMIN, WAITING_CUSTOMER, RESOLVED'
        AFTER `status`,

    ADD COLUMN `last_message_id`
        BIGINT NULL
        COMMENT '가장 최근 메시지 ID'
        AFTER `response_status`,

    ADD COLUMN `updated_at`
        DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6)
        COMMENT '채팅방 정보 마지막 수정 시각'
        AFTER `created_at`,

    ADD CONSTRAINT `chk_chat_rooms_status`
        CHECK (
            `status` IN (
                'OPEN',
                'CLOSED'
            )
        ),

    ADD CONSTRAINT `chk_chat_rooms_response_status`
        CHECK (
            `response_status` IN (
                'WAITING_ADMIN',
                'WAITING_CUSTOMER',
                'RESOLVED'
            )
        ),

    ADD CONSTRAINT `chk_chat_rooms_status_pair`
        CHECK (
            (
                `status` = 'OPEN'
                AND `response_status` IN (
                    'WAITING_ADMIN',
                    'WAITING_CUSTOMER'
                )
            )
            OR
            (
                `status` = 'CLOSED'
                AND `response_status` = 'RESOLVED'
            )
        ),

    ADD INDEX `idx_chat_rooms_last_message`
        (`last_message_at`, `id`),

    ADD INDEX `idx_chat_rooms_status_response_last`
        (
            `status`,
            `response_status`,
            `last_message_at`,
            `id`
        );


-- =========================================================
-- 2. chat_messages
-- =========================================================
--
-- 최종 정책
-- * 고객/관리자 메시지를 하나의 테이블에서 관리한다.
-- * sender_id로 발신자를 구분한다.
-- * 시스템 메시지는 사용하지 않는다.
-- * message_type은 사용하지 않는다.
-- * 주문별 메시지 연결을 하지 않으므로 order_id는 추가하지 않는다.
-- * 상품 상세 문의를 위해 product_id는 유지한다.
-- * 이미지는 별도 chat_message_attachments 테이블에서 관리한다.
-- =========================================================

ALTER TABLE `chat_messages`

    DROP COLUMN `message_type`,

    DROP COLUMN `image_url`,

    ADD COLUMN `product_id`
        BIGINT NULL
        COMMENT '상품 상세 페이지에서 문의를 시작한 상품 ID'
        AFTER `sender_id`,

    ADD CONSTRAINT `fk_chat_messages_product`
        FOREIGN KEY (`product_id`)
        REFERENCES `products` (`id`)
        ON DELETE SET NULL,

    ADD INDEX `idx_chat_messages_room_id`
        (`chat_room_id`, `id`),

    ADD INDEX `idx_chat_messages_room_product_id`
        (`chat_room_id`, `product_id`, `id`);


-- =========================================================
-- 3. chat_rooms.last_message_id FK
-- =========================================================
--
-- chat_rooms와 chat_messages가 서로 참조하므로
-- chat_messages 구조 변경 이후 FK를 추가한다.
-- =========================================================

ALTER TABLE `chat_rooms`
    ADD CONSTRAINT `fk_chat_rooms_last_message`
        FOREIGN KEY (`last_message_id`)
        REFERENCES `chat_messages` (`id`)
        ON DELETE SET NULL;


-- =========================================================
-- 4. chat_room_orders
-- =========================================================
--
-- 한 채팅방에 여러 주문을 연결한다.
--
-- conversation_anchor_message_id:
-- * 상품 문의에서 주문으로 이어진 경우
--   → 최초 고객 문의 메시지 ID
--
-- * 문의 없이 바로 주문한 경우
--   → NULL
--
-- 이전 주문 카드를 클릭했을 때 해당 대화 위치로 이동하는 데 사용한다.
-- =========================================================

ALTER TABLE `chat_room_orders`

    ADD COLUMN `conversation_anchor_message_id`
        BIGINT NULL
        COMMENT '주문 관련 대화 시작 메시지 ID'
        AFTER `order_id`,

    ADD CONSTRAINT `fk_chat_room_orders_anchor_message`
        FOREIGN KEY (`conversation_anchor_message_id`)
        REFERENCES `chat_messages` (`id`)
        ON DELETE SET NULL,

    ADD INDEX `idx_chat_room_orders_room_created`
        (`chat_room_id`, `created_at`, `id`);


-- =========================================================
-- 5. chat_message_attachments
-- =========================================================
--
-- 실제 이미지 파일은 S3에 저장한다.
-- DB에는 S3 전체 URL이 아닌 Object Key와 파일 메타데이터를 저장한다.
-- =========================================================

CREATE TABLE `chat_message_attachments` (
    `id`
        BIGINT NOT NULL AUTO_INCREMENT,

    `chat_message_id`
        BIGINT NOT NULL
        COMMENT '첨부파일이 속한 채팅 메시지 ID',

    `object_key`
        VARCHAR(500) NOT NULL
        COMMENT 'S3 객체 키',

    `original_filename`
        VARCHAR(255) NOT NULL
        COMMENT '사용자가 업로드한 원본 파일명',

    `content_type`
        VARCHAR(100) NOT NULL
        COMMENT '파일 MIME 타입',

    `file_size`
        BIGINT NOT NULL
        COMMENT '파일 크기(byte)',

    `display_order`
        INT NOT NULL DEFAULT 0
        COMMENT '한 메시지 안에서 이미지 표시 순서',

    `created_at`
        DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6)
        COMMENT '첨부파일 생성 시각',

    PRIMARY KEY (`id`),

    CONSTRAINT `fk_chat_message_attachments_message`
        FOREIGN KEY (`chat_message_id`)
        REFERENCES `chat_messages` (`id`)
        ON DELETE CASCADE,

    CONSTRAINT `uk_chat_message_attachments_order`
        UNIQUE (
            `chat_message_id`,
            `display_order`
        ),

    CONSTRAINT `uk_chat_message_attachments_object_key`
        UNIQUE (`object_key`),

    CONSTRAINT `chk_chat_message_attachments_file_size`
        CHECK (`file_size` > 0),

    CONSTRAINT `chk_chat_message_attachments_display_order`
        CHECK (`display_order` >= 0)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- 6. chat_room_read_cursors
-- =========================================================
--
-- 기존 chat_message_reads처럼 메시지별 읽음 행을 저장하지 않는다.
--
-- 채팅방마다:
-- * CUSTOMER
-- * ADMIN
--
-- 두 개의 읽음 위치만 관리한다.
-- =========================================================

CREATE TABLE `chat_room_read_cursors` (
    `id`
        BIGINT NOT NULL AUTO_INCREMENT,

    `chat_room_id`
        BIGINT NOT NULL
        COMMENT '채팅방 ID',

    `reader_side`
        VARCHAR(20) NOT NULL
        COMMENT '읽은 주체: CUSTOMER, ADMIN',

    `last_read_message_id`
        BIGINT NULL
        COMMENT '해당 주체가 마지막으로 읽은 메시지 ID',

    `last_read_at`
        DATETIME(6) NULL
        COMMENT '마지막 읽음 처리 시각',

    `created_at`
        DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6)
        COMMENT '읽음 커서 생성 시각',

    `updated_at`
        DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6)
        COMMENT '읽음 커서 수정 시각',

    PRIMARY KEY (`id`),

    CONSTRAINT `uk_chat_room_read_cursor`
        UNIQUE (
            `chat_room_id`,
            `reader_side`
        ),

    CONSTRAINT `fk_chat_room_read_cursors_room`
        FOREIGN KEY (`chat_room_id`)
        REFERENCES `chat_rooms` (`id`)
        ON DELETE CASCADE,

    CONSTRAINT `fk_chat_room_read_cursors_last_message`
        FOREIGN KEY (`last_read_message_id`)
        REFERENCES `chat_messages` (`id`)
        ON DELETE SET NULL,

    CONSTRAINT `chk_chat_room_read_cursors_side`
        CHECK (
            `reader_side` IN (
                'CUSTOMER',
                'ADMIN'
            )
        )

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- 7. customer_admin_notes
-- =========================================================
--
-- 관리자 채팅 화면에서 고객별 특이사항 메모를 관리한다.
-- 고객 한 명당 메모 하나를 사용한다.
-- =========================================================

CREATE TABLE `customer_admin_notes` (
    `id`
        BIGINT NOT NULL AUTO_INCREMENT,

    `customer_id`
        BIGINT NOT NULL
        COMMENT '메모 대상 고객 ID',

    `content`
        TEXT NOT NULL
        COMMENT '관리자 메모 내용',

    `updated_by`
        BIGINT NOT NULL
        COMMENT '마지막으로 메모를 수정한 관리자 ID',

    `created_at`
        DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6)
        COMMENT '메모 생성 시각',

    `updated_at`
        DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6)
        COMMENT '메모 마지막 수정 시각',

    PRIMARY KEY (`id`),

    CONSTRAINT `uk_customer_admin_notes_customer`
        UNIQUE (`customer_id`),

    CONSTRAINT `fk_customer_admin_notes_customer`
        FOREIGN KEY (`customer_id`)
        REFERENCES `members` (`id`),

    CONSTRAINT `fk_customer_admin_notes_updated_by`
        FOREIGN KEY (`updated_by`)
        REFERENCES `members` (`id`)

) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- 8. 기존 메시지별 읽음 테이블 제거
-- =========================================================
--
-- 기존 데이터가 없음을 확인했으므로 데이터 이전 없이 제거한다.
-- 읽음 상태는 chat_room_read_cursors로 관리한다.
-- =========================================================

DROP TABLE `chat_message_reads`;