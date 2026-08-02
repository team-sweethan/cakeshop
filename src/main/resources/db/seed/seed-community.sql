-- 커뮤니티 도메인 로컬 시드 데이터
--
-- ⚠️ 주의: 커뮤니티 데이터(게시글·댓글·좋아요·신고)를 지우고 샘플을 다시 넣는다.
--    로컬에서 직접 작성한 글도 함께 사라진다. 운영/공용 DB에서는 절대 실행하지 않는다.
--
-- Flyway 관리 대상이 아니다. 스키마 마이그레이션이 모두 적용된 뒤 직접 실행한다.
--
-- ‼️ 실행 순서: seed-local.sql 을 먼저 실행하고 이 파일을 실행한다.
--    seed-local.sql 이 `members` 를 지우고 다시 넣으므로 순서를 바꾸면 이 시드의 글이
--    전부 사라진다. 또 seed-local.sql 은 `post_categories` 까지 지우는데, 그 카테고리는
--    V20260802_113229__provision_post_categories.sql 이 넣는 참조 데이터이고 없으면
--    posts.category_id(NOT NULL FK) 때문에 글쓰기가 아예 불가능하다
--    (docs/community/DOMAIN.md 6.8). 그래서 1절에서 다시 채운다.
--
--   mariadb --host=localhost --port=3307 --user=root --password cakeshop \
--     < src/main/resources/db/seed/seed-community.sql
--
-- 확인용 경로 (id 는 재실행해도 그대로다)
--   /community              목록 2페이지, 카테고리 필터, 탈퇴 회원 표시
--   /community/34           본문 HTML 이스케이프와 줄바꿈, 댓글 수(삭제 댓글 제외)
--   /community/35           차단된 글 — 비로그인은 404, 작성자(user@cakeshop.local)는 사유 표시
--   /community/36           삭제된 글 — 작성자에게도 404
--
-- id 가 1..36 사이에서 띄엄띄엄한 것은 정상이다. InnoDB 는 INSERT ... SELECT 처럼
-- 행 수를 미리 모르는 삽입에서 auto_increment 를 넉넉히 잡아 두어 빈 번호가 생긴다.
-- 매번 같은 값이 나오므로 위 경로는 안정적이다.

-- ---------------------------------------------------------------------------
-- 0. 초기화 — 커뮤니티 데이터만 자식 → 부모 순으로 지운다.
--
-- post_categories 는 지우지 않는다. 샘플이 아니라 기능 동작에 필요한 참조 데이터다.
-- ---------------------------------------------------------------------------

DELETE FROM `post_reports`;
DELETE FROM `post_likes`;
DELETE FROM `comments`;
DELETE FROM `posts`;

-- 재실행해도 /community/1 같은 경로가 그대로이도록 카운터를 되돌린다.
ALTER TABLE `post_reports` AUTO_INCREMENT = 1;
ALTER TABLE `post_likes` AUTO_INCREMENT = 1;
ALTER TABLE `comments` AUTO_INCREMENT = 1;
ALTER TABLE `posts` AUTO_INCREMENT = 1;

-- ---------------------------------------------------------------------------
-- 1. 카테고리 보충
--
-- seed-local.sql 이 지웠을 수 있어 없는 code 만 채운다. 운영자가 이름이나 노출 여부를
-- 바꿨을 수 있으므로 덮어쓰지 않는다. migration 과 같은 방식이다.
-- ---------------------------------------------------------------------------

INSERT INTO `post_categories` (`code`, `name`, `is_active`, `sort_order`)
SELECT defaults.`code`,
       defaults.`name`,
       defaults.`is_active`,
       defaults.`sort_order`
  FROM (
        SELECT 'QNA'    AS `code`, '질문' AS `name`, 1 AS `is_active`, 1 AS `sort_order`
        UNION ALL
        SELECT 'REVIEW', '후기', 1, 2
        UNION ALL
        SELECT 'FREE',   '자유', 1, 3
       ) defaults
  LEFT JOIN `post_categories` existing
    ON existing.`code` = defaults.`code`
 WHERE existing.`id` IS NULL;

-- 비활성 카테고리는 필터·글쓰기 선택지에서 빠져야 한다(DOMAIN.md 6.8).
-- 그 동작을 로컬에서 눈으로 확인할 수 있도록 하나 넣어 둔다.
INSERT INTO `post_categories` (`code`, `name`, `is_active`, `sort_order`)
SELECT 'RECIPE', '레시피', 0, 4
 WHERE NOT EXISTS (SELECT 1 FROM `post_categories` WHERE `code` = 'RECIPE');

-- ---------------------------------------------------------------------------
-- 2. 탈퇴 회원
--
-- 탈퇴해도 글은 남고 작성자명만 "탈퇴한 회원"으로 보여야 한다(DOMAIN.md 8).
-- seed-local.sql 의 공통 계정에는 탈퇴 회원이 없어 여기서 만든다.
-- ---------------------------------------------------------------------------

INSERT INTO `members` (`email`, `password`, `name`, `nickname`, `phone`,
                       `role`, `status`, `withdrawn_at`)
SELECT 'withdrawn@cakeshop.local',
       '$2a$10$wRIE78x8sm..uLtbp9LHde7l6wUWQD3NjPvThQaXvZ3PpXfW6wwX.',
       '탈퇴회원', '떠난회원', '010-0000-0003',
       'USER', 'WITHDRAWN', '2026-07-20 09:00:00'
 WHERE NOT EXISTS (
        SELECT 1 FROM `members` WHERE `email` = 'withdrawn@cakeshop.local'
       );

SET @member_id    := (SELECT `id` FROM `members` WHERE `email` = 'user@cakeshop.local');
SET @admin_id     := (SELECT `id` FROM `members` WHERE `email` = 'admin@cakeshop.local');
SET @withdrawn_id := (SELECT `id` FROM `members` WHERE `email` = 'withdrawn@cakeshop.local');

SET @qna_id    := (SELECT `id` FROM `post_categories` WHERE `code` = 'QNA');
SET @review_id := (SELECT `id` FROM `post_categories` WHERE `code` = 'REVIEW');
SET @free_id   := (SELECT `id` FROM `post_categories` WHERE `code` = 'FREE');

-- ---------------------------------------------------------------------------
-- 3. 게시글
--
-- 페이지 크기는 20 고정이므로(DOMAIN.md 6.1) 2페이지가 나오도록 25건을 넣는다.
-- created_at 을 1분씩 벌려 최신순 정렬을 눈으로 확인할 수 있게 한다.
-- ---------------------------------------------------------------------------

INSERT INTO `posts` (`member_id`, `category_id`, `title`, `content`, `status`,
                     `view_count`, `like_count`, `created_at`, `updated_at`)
SELECT @member_id,
       CASE seq % 3 WHEN 0 THEN @qna_id WHEN 1 THEN @review_id ELSE @free_id END,
       CONCAT('샘플 게시글 ', LPAD(seq, 2, '0'), ' — 케이크 이야기'),
       CONCAT('로컬 확인용 본문입니다. ', seq, '번째 글.'),
       'PUBLISHED',
       seq * 3,
       0,
       DATE_ADD('2026-07-25 09:00:00', INTERVAL seq MINUTE),
       DATE_ADD('2026-07-25 09:00:00', INTERVAL seq MINUTE)
  FROM (
        SELECT 1 AS seq UNION ALL SELECT 2  UNION ALL SELECT 3  UNION ALL SELECT 4
        UNION ALL SELECT 5  UNION ALL SELECT 6  UNION ALL SELECT 7  UNION ALL SELECT 8
        UNION ALL SELECT 9  UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12
        UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15 UNION ALL SELECT 16
        UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20
        UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24
        UNION ALL SELECT 25
       ) seqs;

-- 탈퇴 회원의 글: 목록·상세에서 "탈퇴한 회원"으로 보여야 한다.
INSERT INTO `posts` (`member_id`, `category_id`, `title`, `content`, `status`,
                     `view_count`, `like_count`, `created_at`, `updated_at`)
VALUES (@withdrawn_id, @review_id,
        '탈퇴한 회원이 남긴 후기입니다',
        '작성자는 탈퇴했지만 글은 남는다. 표시명만 가린다.',
        'PUBLISHED', 12, 0,
        '2026-07-25 09:30:00', '2026-07-25 09:30:00');

-- 수정된 글: updated_at 이 created_at 보다 뒤면 화면에 "(수정됨)"이 붙는다(DOMAIN.md 6.3).
-- 조회수 증가로는 이 값이 바뀌지 않아야 한다.
INSERT INTO `posts` (`member_id`, `category_id`, `title`, `content`, `status`,
                     `view_count`, `like_count`, `created_at`, `updated_at`)
VALUES (@member_id, @qna_id,
        '한 번 수정한 글입니다',
        '수정 표시를 확인하는 글이다.',
        'PUBLISHED', 7, 0,
        '2026-07-25 09:35:00', '2026-07-26 11:00:00');

-- 본문은 순수 텍스트다. HTML 이 실행되지 않고 줄바꿈이 유지되어야 한다(DOMAIN.md 7).
INSERT INTO `posts` (`member_id`, `category_id`, `title`, `content`, `status`,
                     `view_count`, `like_count`, `created_at`, `updated_at`)
VALUES (@member_id, @free_id,
        'HTML 이스케이프 확인용 글',
        '아래 줄은 스크립트 태그다.\n<script>alert("xss")</script>\n\n빈 줄도 유지되어야 한다.',
        'PUBLISHED', 5, 0,
        '2026-07-25 09:40:00', '2026-07-25 09:40:00');

-- 차단된 글: 남에게는 404, 작성자에게는 본문과 사유가 보여야 한다(DOMAIN.md 4.3).
INSERT INTO `posts` (`member_id`, `category_id`, `title`, `content`, `status`,
                     `view_count`, `like_count`,
                     `blocked_at`, `blocked_reason`, `blocked_by`,
                     `created_at`, `updated_at`)
VALUES (@member_id, @free_id,
        '차단된 글',
        '관리자가 차단한 글의 본문이다.',
        'BLOCKED', 3, 0,
        '2026-07-26 10:00:00', '광고성 게시물로 판단되어 차단되었습니다.', @admin_id,
        '2026-07-25 09:45:00', '2026-07-25 09:45:00');

-- 삭제된 글: 작성자에게도 404다. 목록에도 나오지 않는다.
INSERT INTO `posts` (`member_id`, `category_id`, `title`, `content`, `status`,
                     `view_count`, `like_count`, `created_at`, `updated_at`)
VALUES (@member_id, @free_id,
        '삭제된 글',
        '작성자가 삭제한 글의 본문이다.',
        'DELETED', 2, 0,
        '2026-07-25 09:50:00', '2026-07-25 09:50:00');

-- ---------------------------------------------------------------------------
-- 4. 댓글
--
-- 삭제된 댓글은 자리 표시로 남기되 개수 집계에서는 제외한다(DOMAIN.md 4.4).
-- 목록의 "댓글 N"이 삭제된 것을 빼고 세는지 확인할 수 있다.
-- 1차에는 대댓글이 없으므로 parent_comment_id 를 넣지 않는다(6.4).
-- ---------------------------------------------------------------------------

SET @escaped_post_id := (SELECT `id` FROM `posts` WHERE `title` = 'HTML 이스케이프 확인용 글');
SET @withdrawn_post_id := (SELECT `id` FROM `posts` WHERE `title` = '탈퇴한 회원이 남긴 후기입니다');

INSERT INTO `comments` (`post_id`, `member_id`, `content`, `status`, `created_at`, `updated_at`)
VALUES (@escaped_post_id, @member_id, '첫 번째 댓글입니다.', 'PUBLISHED',
        '2026-07-26 10:00:00', '2026-07-26 10:00:00'),
       (@escaped_post_id, @admin_id, '두 번째 댓글입니다.', 'PUBLISHED',
        '2026-07-26 10:01:00', '2026-07-26 10:01:00'),
       (@escaped_post_id, @member_id, '삭제된 댓글입니다.', 'DELETED',
        '2026-07-26 10:02:00', '2026-07-26 10:02:00'),
       (@withdrawn_post_id, @admin_id, '탈퇴 회원 글에 달린 댓글입니다.', 'PUBLISHED',
        '2026-07-26 10:03:00', '2026-07-26 10:03:00');

-- ---------------------------------------------------------------------------
-- 5. 좋아요
--
-- 회원 한 명이 글 하나에 한 번만 누를 수 있다(uk_post_likes_post_member).
-- 최근 글 몇 개에만 눌러 둔다.
-- ---------------------------------------------------------------------------

INSERT INTO `post_likes` (`post_id`, `member_id`, `created_at`)
SELECT p.`id`, m.`id`, '2026-07-26 12:00:00'
  FROM `posts` p
  CROSS JOIN `members` m
 WHERE p.`status` = 'PUBLISHED'
   AND p.`id` % 3 = 0
   AND m.`email` IN ('user@cakeshop.local', 'admin@cakeshop.local');

-- like_count 는 증분하지 않고 매번 재계산한다(DOMAIN.md 6.5).
-- 시드도 같은 방식으로 맞춰 둔다. 값이 어긋난 채로 시작하면 조각 4에서 원인을 찾기 어렵다.
UPDATE `posts` p
   SET p.`like_count` = (
        SELECT COUNT(*) FROM `post_likes` pl WHERE pl.`post_id` = p.`id`
       );

-- ---------------------------------------------------------------------------
-- 6. 확인
-- ---------------------------------------------------------------------------

SELECT (SELECT COUNT(*) FROM `post_categories` WHERE `is_active` = 1) AS `활성카테고리`,
       (SELECT COUNT(*) FROM `posts` WHERE `status` = 'PUBLISHED')    AS `노출게시글`,
       (SELECT COUNT(*) FROM `posts` WHERE `status` <> 'PUBLISHED')   AS `숨김게시글`,
       (SELECT COUNT(*) FROM `comments` WHERE `status` = 'PUBLISHED') AS `노출댓글`,
       (SELECT COUNT(*) FROM `post_likes`)                            AS `좋아요`;
