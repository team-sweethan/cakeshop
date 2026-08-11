-- add_community_notices
-- 생성: 2026-08-12 06:56:39
--
-- 규칙
-- * 이 파일은 공유된 뒤 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 한 migration 은 하나의 배포 가능한 스키마 전환을 담는다. SQL 수만으로 나누거나 합치지 않는다.

-- 관리자 공지사항 (docs/community/specs/community-notice.md E4, PLAN.md 조각 14a).
--
-- posts 의 플래그가 아니라 별도 표인 이유, 조회수 컬럼과 자식 표가 없는 이유, BLOCKED 가
-- 없는 이유는 전부 위 spec 에 있다. 여기서는 그 결정의 결과만 만든다.
--
-- 되돌리기: DROP TABLE 하나. 다른 표를 건드리지 않으므로 기존 데이터에 영향이 없다.
CREATE TABLE IF NOT EXISTS `community_notices` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,

    `title`      VARCHAR(200) NOT NULL,
    `content`    TEXT         NOT NULL,

    `status`     VARCHAR(20)  NOT NULL,

    -- 노출 기간. NULL 이 값이다 — 시작 NULL = 즉시 노출, 종료 NULL = 무기한.
    -- 끝은 ends_at '미만' 이라 경계가 열려 있다.
    `starts_at`  DATETIME(6)  NULL,
    `ends_at`    DATETIME(6)  NULL,

    -- 감사용. 화면에 쓰지 않는다.
    `created_by` BIGINT       NOT NULL,

    `created_at` DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                  ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (`id`),

    CONSTRAINT `ck_community_notices_status`
        CHECK (`status` IN ('PUBLISHED', 'DELETED')),

    -- 둘 다 있을 때만 순서를 따진다. 한쪽이 NULL 이면 기간이 열려 있다는 뜻이라 비교 대상이
    -- 없다. 화면 입력은 NoticeForm 이 먼저 거르지만, 뒤집힌 기간은 공지를 영영 안 보이게
    -- 만들면서 등록은 성공으로 끝나므로 표에서도 막는다.
    CONSTRAINT `ck_community_notices_period`
        CHECK (`starts_at` IS NULL OR `ends_at` IS NULL OR `starts_at` < `ends_at`),

    CONSTRAINT `fk_community_notices_member`
        FOREIGN KEY (`created_by`) REFERENCES `members` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
