-- add_member_status_histories
-- 생성: 2026-08-03 09:17:27
--
-- 규칙
-- * 이 파일은 머지된 뒤 절대 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다.
CREATE TABLE member_status_histories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    before_status VARCHAR(20) NOT NULL,
    after_status VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    processed_by BIGINT NULL,
    processed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_member_status_histories_action
        CHECK (action IN ('SUSPEND', 'ACTIVATE')),
    CONSTRAINT chk_member_status_histories_before_status
        CHECK (before_status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT chk_member_status_histories_after_status
        CHECK (after_status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT fk_member_status_histories_member
        FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_member_status_histories_processor
        FOREIGN KEY (processed_by) REFERENCES members (id),
    INDEX idx_member_status_histories_member_processed
        (member_id, processed_at DESC, id DESC)
);

INSERT INTO member_status_histories (
    member_id,
    action,
    before_status,
    after_status,
    reason,
    processed_by,
    processed_at
)
SELECT id,
       'SUSPEND',
       'ACTIVE',
       'SUSPENDED',
       COALESCE(NULLIF(TRIM(suspended_reason), ''), '기존 이용정지 데이터'),
       NULL,
       COALESCE(suspended_at, updated_at, created_at, CURRENT_TIMESTAMP(6))
  FROM members
 WHERE status = 'SUSPENDED';
