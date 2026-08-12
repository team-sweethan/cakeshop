-- add_email_verifications
-- 생성: 2026-08-11 17:43:39
--
-- 규칙
-- * 이 파일은 공유된 뒤 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 한 migration 은 하나의 배포 가능한 스키마 전환을 담는다. SQL 수만으로 나누거나 합치지 않는다.

-- 회원 생성 전에도 인증 요청을 저장해야 하므로 members FK 없이 이메일을 인증 대상으로 보관한다.
CREATE TABLE `email_verifications` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `email` VARCHAR(255) NOT NULL,
    `purpose` VARCHAR(30) NOT NULL,
    `code_hash` VARCHAR(100) NOT NULL,
    `expires_at` DATETIME(6) NOT NULL,
    `attempt_count` INT UNSIGNED NOT NULL DEFAULT 0,
    `verified_at` DATETIME(6) NULL,
    `consumed_at` DATETIME(6) NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    CONSTRAINT `chk_email_verifications_purpose`
        CHECK (`purpose` IN ('SIGNUP', 'PASSWORD_RESET')),
    CONSTRAINT `chk_email_verifications_attempt_count`
        CHECK (`attempt_count` <= 5),
    INDEX `idx_email_verifications_email_purpose_created`
        (`email`, `purpose`, `created_at` DESC, `id` DESC),
    INDEX `idx_email_verifications_expires_at` (`expires_at`)
);
