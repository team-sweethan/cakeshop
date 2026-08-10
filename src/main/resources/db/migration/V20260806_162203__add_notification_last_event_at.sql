-- add_notification_last_event_at
-- 생성: 2026-08-06 16:22:03
--
-- 규칙
-- * 이 파일은 머지된 뒤 절대 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다.

-- add_notification_last_event_at
--
-- 목적
-- 1. 알림이 최초 생성된 시간과 최근 이벤트가 반영된 시간을 분리한다.
-- 2. 묶음 채팅 알림에 새 메시지가 추가되면 최근 알림으로 다시 노출한다.
-- 3. 전체 알림과 안 읽은 알림을 최신 이벤트순으로 조회한다.
-- 4. 채팅 알림 정책을 지키기 위해 새 속성을 추가합니다.
--
-- 주의
-- 이미 적용된 기존 Flyway 파일은 수정하지 않는다.

-- =========================================================
-- 1. last_event_at 컬럼 추가
-- =========================================================

-- 기존 데이터 백필을 위해 우선 NULL 허용
ALTER TABLE `notifications`
    ADD COLUMN `last_event_at`
        DATETIME(6) NULL
        COMMENT '이 알림에 반영된 가장 최근 이벤트 발생 시각'
        AFTER `created_at`;


-- =========================================================
-- 2. 기존 데이터 백필
-- =========================================================

-- 기존 알림에는 별도의 최근 이벤트 시각이 없으므로
-- 최초 생성 시간을 최근 이벤트 시각으로 사용
UPDATE `notifications`
SET `last_event_at` = `created_at`
WHERE `last_event_at` IS NULL;


-- =========================================================
-- 3. 필수 컬럼 및 기본값 설정
-- =========================================================

ALTER TABLE `notifications`
    MODIFY COLUMN `last_event_at`
        DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6)
        COMMENT '이 알림에 반영된 가장 최근 이벤트 발생 시각';


-- =========================================================
-- 4. 기존 created_at 기준 인덱스 제거
-- =========================================================

DROP INDEX `idx_notifications_receiver_created`
    ON `notifications`;

DROP INDEX `idx_notifications_receiver_read`
    ON `notifications`;


-- =========================================================
-- 5. last_event_at 기준 인덱스 추가
-- =========================================================

-- 전체 알림 목록 조회
-- WHERE receiver_id = ?
-- ORDER BY last_event_at DESC, id DESC
CREATE INDEX `idx_notifications_receiver_last_event`
    ON `notifications`
       (`receiver_id`, `last_event_at`, `id`);

-- 안 읽은 알림 목록 조회 및 미읽음 개수 조회
-- WHERE receiver_id = ? AND is_read = 0
-- ORDER BY last_event_at DESC, id DESC
CREATE INDEX `idx_notifications_receiver_read_last_event`
    ON `notifications`
       (`receiver_id`, `is_read`, `last_event_at`, `id`);