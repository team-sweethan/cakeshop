-- 커뮤니티 도메인 로컬 시드 데이터
--
-- ⚠️ 주의: 커뮤니티 데이터(게시글·댓글·좋아요·신고·공지)를 지우고 샘플을 다시 넣는다.
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
--   /community              목록 2페이지, 카테고리 필터, 탈퇴 회원 표시, 상단 인기글
--   /community/33           댓글 25건 — "이전 댓글 더 보기", 오래된 순 정렬, (수정됨) 표시
--   /community/34           본문 HTML 이스케이프와 줄바꿈, 댓글 수(삭제 댓글 제외), 자리 표시
--   /community/35           차단된 글 — 비로그인은 404, 작성자(user@cakeshop.local)는 사유 표시
--   /community/36           삭제된 글 — 작성자에게도 404
--   /admin/community/notices  공지 5건 — 배지 네 종류(예정·노출 중·종료·삭제됨)와
--                             `즉시`/`무기한` 표시
--   /community/notices        위 5건 중 **2건만** 보인다. 나머지 셋(예정·종료·삭제됨)이
--                             빠지는 것이 정상이다
--
-- id 가 1..36 사이에서 띄엄띄엄한 것은 정상이다. InnoDB 는 INSERT ... SELECT 처럼
-- 행 수를 미리 모르는 삽입에서 auto_increment 를 넉넉히 잡아 두어 빈 번호가 생긴다.
-- 매번 같은 값이 나오므로 위 경로는 안정적이다.

-- ---------------------------------------------------------------------------
-- 기준 시각 — 아래 모든 시각은 실행한 날에서 거꾸로 센다.
--
-- 고정 날짜를 박아 두면 인기글이 **시드를 고치지 않는 한 언젠가 반드시 사라진다.**
-- 배치는 대상일 하루만 집계하므로(specs/community-popular.md E3), 박아 둔 날짜가 창을
-- 벗어난 다음 날부터 로컬의 인기글 영역이 통째로 비고 — 그런데 화면은 "활동이 없는
-- 정상 상태"와 똑같이 생겨서(그릴 것이 없으면 영역이 통째로 사라진다) 시드가 낡은
-- 것인지 기능이 깨진 것인지 구분되지 않는다.
--
-- **창이 하루가 된 뒤로는 이 시드가 하루짜리다**(PLAN.md R35). 7일 창일 때는 다음 날
-- 배치가 대상일을 옮겨도 어제치 활동이 창에 엿새 더 걸렸지만, 지금은 하루만 지나면
-- 그날의 순위가 0건으로 확정되어 영역이 사라진다. 로컬에서 인기글을 다시 보려면
-- 시드를 다시 실행한다.
--
-- 활동을 어제에 두는 이유: 배치의 대상일은 언제나 **전날**이다(PLAN.md D3). 활동을
-- 오늘에 두면 오늘 밤 배치가 도는 대상일(오늘)에는 들어가지만, 아래 8절이 미리 확정해
-- 두는 어제치에는 한 건도 안 잡혀 시드 직후 화면이 빈다.
--
-- 글은 활동보다 하루 앞에 둔다. 창이 보는 것은 활동(post_views·post_likes·comments)의
-- created_at 뿐이라 글 날짜는 창과 무관하고, 하루 벌려 두어야 목록의 작성일과 활동일이
-- 눈으로 갈린다.
--
-- 시드를 다시 깔지 않아도 **엿새는 버틴다.** 활동이 어제에 있으므로 이후 배치가 매일
-- 새 대상일로 돌아도 창(대상일 −6일)에 계속 걸린다. 이레째부터는 빈다 — 그때는 활동이
-- 실제로 오래된 것이므로 6.9 대로 안 보이는 것이 맞다.
--
-- 세션 시간대를 고정하는 이유: CURDATE() 는 DB 세션 시간대를 따른다. 앱은 JDBC URL 로
-- +09:00 을 걸지만(application.yml, PLAN.md D10) 이 파일은 mariadb CLI 로 직접 실행되고
-- 그 세션은 서버 기본값(대개 UTC)을 쓴다. 그대로 두면 한국 시각 오전 9시 이전에 시드를
-- 깐 날 '어제' 가 하루 밀려, 배치가 넘기는 날짜와 어긋난다.
-- ---------------------------------------------------------------------------

SET time_zone = '+09:00';

SET @today        := CURDATE();
-- 활동(조회·좋아요·댓글)이 일어난 날이자 8절이 확정하는 인기글의 대상일이다.
SET @activity_day := @today - INTERVAL 1 DAY;
-- 글이 올라온 날.
SET @posted_day   := @today - INTERVAL 2 DAY;

-- ---------------------------------------------------------------------------
-- 0. 초기화 — 커뮤니티 데이터만 자식 → 부모 순으로 지운다.
--
-- post_categories 는 지우지 않는다. 샘플이 아니라 기능 동작에 필요한 참조 데이터다.
-- ---------------------------------------------------------------------------

DELETE FROM `post_reports`;
DELETE FROM `post_likes`;
DELETE FROM `post_views`;
-- daily_popular_posts 는 posts 를 FK 로 참조한다(조각 7b). 배치가 한 번이라도 돈
-- 뒤에는 이 줄이 없으면 아래 DELETE FROM posts 가 제약에 걸려 시드가 통째로 실패한다.
DELETE FROM `daily_popular_posts`;
-- 실행 기록에는 FK 가 없지만 함께 지운다. 남겨 두면 글을 새로 깔아도 배치가
-- "이미 확정한 날짜" 로 판단해 건너뛰어서(D4) 인기글이 채워지지 않는다.
-- 지운 자리는 8절이 어제치로 다시 채운다.
DELETE FROM `popular_post_batch_runs`;
-- 답글이 뿌리를 참조하므로(fk_comments_parent, ON DELETE 없음) 답글을 먼저 지운다.
-- 한 문장으로 전부 지우면 삭제 순서에 따라 FK 위반이 난다.
DELETE FROM `comments` WHERE `parent_comment_id` IS NOT NULL;
DELETE FROM `comments`;
DELETE FROM `posts`;
-- 공지는 posts 와 아무 관계가 없다(별도 표, 자식 표 없음). 순서에 걸리는 것이 없어
-- 마지막에 둔다.
DELETE FROM `community_notices`;

-- 재실행해도 /community/1 같은 경로가 그대로이도록 카운터를 되돌린다.
ALTER TABLE `post_reports` AUTO_INCREMENT = 1;
ALTER TABLE `post_likes` AUTO_INCREMENT = 1;
ALTER TABLE `post_views` AUTO_INCREMENT = 1;
ALTER TABLE `comments` AUTO_INCREMENT = 1;
ALTER TABLE `posts` AUTO_INCREMENT = 1;
ALTER TABLE `community_notices` AUTO_INCREMENT = 1;

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
       'USER', 'WITHDRAWN', TIMESTAMP(@posted_day - INTERVAL 5 DAY, '09:00:00')
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
       DATE_ADD(TIMESTAMP(@posted_day, '09:00:00'), INTERVAL seq MINUTE),
       DATE_ADD(TIMESTAMP(@posted_day, '09:00:00'), INTERVAL seq MINUTE)
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
        TIMESTAMP(@posted_day, '09:30:00'), TIMESTAMP(@posted_day, '09:30:00'));

-- 수정된 글: updated_at 이 created_at 보다 뒤면 화면에 "(수정됨)"이 붙는다(DOMAIN.md 6.3).
-- 조회수 증가로는 이 값이 바뀌지 않아야 한다.
INSERT INTO `posts` (`member_id`, `category_id`, `title`, `content`, `status`,
                     `view_count`, `like_count`, `created_at`, `updated_at`)
VALUES (@member_id, @qna_id,
        '한 번 수정한 글입니다',
        '수정 표시를 확인하는 글이다.',
        'PUBLISHED', 7, 0,
        TIMESTAMP(@posted_day, '09:35:00'), TIMESTAMP(@activity_day, '11:00:00'));

-- 본문은 순수 텍스트다. HTML 이 실행되지 않고 줄바꿈이 유지되어야 한다(DOMAIN.md 7).
INSERT INTO `posts` (`member_id`, `category_id`, `title`, `content`, `status`,
                     `view_count`, `like_count`, `created_at`, `updated_at`)
VALUES (@member_id, @free_id,
        'HTML 이스케이프 확인용 글',
        '아래 줄은 스크립트 태그다.\n<script>alert("xss")</script>\n\n빈 줄도 유지되어야 한다.',
        'PUBLISHED', 5, 0,
        TIMESTAMP(@posted_day, '09:40:00'), TIMESTAMP(@posted_day, '09:40:00'));

-- 차단된 글: 남에게는 404, 작성자에게는 본문과 사유가 보여야 한다(DOMAIN.md 4.3).
INSERT INTO `posts` (`member_id`, `category_id`, `title`, `content`, `status`,
                     `view_count`, `like_count`,
                     `blocked_at`, `blocked_reason`, `blocked_by`,
                     `created_at`, `updated_at`)
VALUES (@member_id, @free_id,
        '차단된 글',
        '관리자가 차단한 글의 본문이다.',
        'BLOCKED', 3, 0,
        TIMESTAMP(@activity_day, '10:00:00'), '광고성 게시물로 판단되어 차단되었습니다.', @admin_id,
        TIMESTAMP(@posted_day, '09:45:00'), TIMESTAMP(@posted_day, '09:45:00'));

-- 삭제된 글: 작성자에게도 404다. 목록에도 나오지 않는다.
INSERT INTO `posts` (`member_id`, `category_id`, `title`, `content`, `status`,
                     `view_count`, `like_count`, `created_at`, `updated_at`)
VALUES (@member_id, @free_id,
        '삭제된 글',
        '작성자가 삭제한 글의 본문이다.',
        'DELETED', 2, 0,
        TIMESTAMP(@posted_day, '09:50:00'), TIMESTAMP(@posted_day, '09:50:00'));

-- ---------------------------------------------------------------------------
-- 4. 댓글
--
-- 삭제된 댓글은 자리 표시로 남기되 개수 집계에서는 제외한다(DOMAIN.md 4.4).
-- 목록의 "댓글 N"이 삭제된 것을 빼고 세는지 확인할 수 있다.
-- 답글(2단계)은 조각 8부터 있다 — 아래 6절이 첫 댓글 밑에 심는다.
-- ---------------------------------------------------------------------------

SET @escaped_post_id := (SELECT `id` FROM `posts` WHERE `title` = 'HTML 이스케이프 확인용 글');
SET @withdrawn_post_id := (SELECT `id` FROM `posts` WHERE `title` = '탈퇴한 회원이 남긴 후기입니다');

INSERT INTO `comments` (`post_id`, `member_id`, `content`, `status`, `created_at`, `updated_at`)
VALUES (@escaped_post_id, @member_id, '첫 번째 댓글입니다.', 'PUBLISHED',
        TIMESTAMP(@activity_day, '10:00:00'), TIMESTAMP(@activity_day, '10:00:00')),
       (@escaped_post_id, @admin_id, '두 번째 댓글입니다.', 'PUBLISHED',
        TIMESTAMP(@activity_day, '10:01:00'), TIMESTAMP(@activity_day, '10:01:00')),
       (@escaped_post_id, @member_id, '지워진 댓글의 본문입니다.', 'DELETED',
        TIMESTAMP(@activity_day, '10:02:00'), TIMESTAMP(@activity_day, '10:02:00')),
       (@withdrawn_post_id, @admin_id, '탈퇴 회원 글에 달린 댓글입니다.', 'PUBLISHED',
        TIMESTAMP(@activity_day, '10:03:00'), TIMESTAMP(@activity_day, '10:03:00'));

-- "이전 댓글 더 보기"는 댓글이 한 화면 분량(20건)을 넘어야 나타난다(DOMAIN.md 6.4).
-- 넘는 글이 하나도 없으면 그 블록을 로컬에서 볼 방법이 없다. 25건을 넣어 두면
-- 처음 화면에 최신 20건이 오래된 순으로 보이고, 더 보기를 눌러 과거로 갈 수 있다.
--
-- 가장 오래된 '더보기 확인용 댓글 01' 은 처음에는 보이지 않아야 한다. 보인다면
-- 자르는 방향이 뒤집힌 것이다.
SET @many_comment_post_id := (SELECT `id` FROM `posts`
                               WHERE `title` = '한 번 수정한 글입니다');

INSERT INTO `comments` (`post_id`, `member_id`, `content`, `status`, `created_at`, `updated_at`)
SELECT @many_comment_post_id,
       CASE WHEN seq % 2 = 0 THEN @admin_id ELSE @member_id END,
       CONCAT('더보기 확인용 댓글 ', LPAD(seq, 2, '0')),
       'PUBLISHED',
       DATE_ADD(TIMESTAMP(@activity_day, '13:00:00'), INTERVAL seq MINUTE),
       DATE_ADD(TIMESTAMP(@activity_day, '13:00:00'), INTERVAL seq MINUTE)
  FROM (
        SELECT 1 AS seq UNION ALL SELECT 2  UNION ALL SELECT 3  UNION ALL SELECT 4
        UNION ALL SELECT 5  UNION ALL SELECT 6  UNION ALL SELECT 7  UNION ALL SELECT 8
        UNION ALL SELECT 9  UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12
        UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15 UNION ALL SELECT 16
        UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20
        UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24
        UNION ALL SELECT 25
       ) comment_seqs;

-- ---------------------------------------------------------------------------
-- 4-1. 답글 (조각 8)
--
-- 접힌 "답글 n개 보기"와 펼친 화면, 삭제된 답글의 자리 표시를 로컬에서 볼 수 있게
-- 첫 번째 댓글 밑에 셋을 심는다. 삭제된 뿌리("지워진 댓글") 밑에는 심지 않는다 —
-- 그 뿌리에는 새 답글도 달 수 없다(specs/community-comment.md A7).
-- ---------------------------------------------------------------------------

SET @reply_root_id := (SELECT `id` FROM `comments`
                        WHERE `post_id` = @escaped_post_id
                          AND `content` = '첫 번째 댓글입니다.');

INSERT INTO `comments`
       (`post_id`, `member_id`, `parent_comment_id`, `content`, `status`, `created_at`, `updated_at`)
VALUES (@escaped_post_id, @admin_id, @reply_root_id, '첫 번째 답글입니다.', 'PUBLISHED',
        TIMESTAMP(@activity_day, '10:05:00'), TIMESTAMP(@activity_day, '10:05:00')),
       (@escaped_post_id, @member_id, @reply_root_id, '지워진 답글의 본문입니다.', 'DELETED',
        TIMESTAMP(@activity_day, '10:06:00'), TIMESTAMP(@activity_day, '10:06:00')),
       (@escaped_post_id, @member_id, @reply_root_id, '두 번째 답글입니다.', 'PUBLISHED',
        TIMESTAMP(@activity_day, '10:07:00'), TIMESTAMP(@activity_day, '10:07:00'));

-- ---------------------------------------------------------------------------
-- 5. 좋아요
--
-- 회원 한 명이 글 하나에 한 번만 누를 수 있다(uk_post_likes_post_member).
-- 최근 글 몇 개에만 눌러 둔다.
-- ---------------------------------------------------------------------------

INSERT INTO `post_likes` (`post_id`, `member_id`, `created_at`)
SELECT p.`id`, m.`id`, TIMESTAMP(@activity_day, '12:00:00')
  FROM `posts` p
  CROSS JOIN `members` m
 WHERE p.`status` = 'PUBLISHED'
   AND p.`id` % 3 = 0
   AND m.`email` IN ('user@cakeshop.local', 'admin@cakeshop.local');

-- 런타임의 like_count 는 조건부 원자 UPDATE 증분이다(specs/community-reaction.md A8, 2026-08-18 개정).
-- 시드는 행을 한꺼번에 깔아 증분할 사건이 없으므로 여기서만 재계산으로 값을 맞춘다.
-- 값이 어긋난 채로 시작하면 증분이 그 어긋남을 영영 실어 나른다(PLAN.md R34).
--
-- updated_at 을 자기 값으로 다시 지정하는 것이 핵심이다. posts.updated_at 은
-- ON UPDATE CURRENT_TIMESTAMP(6) 이라 그냥 두면 like_count 가 바뀐 글마다 값이 갱신되고,
-- 화면에 '(수정됨)' 이 붙는다(DOMAIN.md 6.3). 좋아요를 받은 것과 글을 고친 것은 다르다.
UPDATE `posts` p
   SET p.`like_count` = (
        SELECT COUNT(*) FROM `post_likes` pl WHERE pl.`post_id` = p.`id`
       ),
       p.`updated_at` = p.`updated_at`;

-- ---------------------------------------------------------------------------
-- 6. 조회 이력
--
-- posts.view_count 는 post_views 에서 파생된 캐시다(DOMAIN.md 6.2). 이력 없이 숫자만
-- 넣어 두면 두 값이 어긋난 채로 시작하고, 그 상태에서는 중복 방지가 도는지 눈으로
-- 확인할 수가 없다 — 이미 큰 수라 1 이 오르든 말든 티가 안 난다.
--
-- 그래서 위에서 넣은 view_count 만큼 가짜 조회자를 만들고, 숫자는 이력에서 다시 센다.
-- 글마다 조회수가 다르므로 조각 7 의 조회수 정렬도 이 데이터로 확인할 수 있다.
--
-- viewer_key 접두사를 'S:seed-' 로 두어 실제 세션 키('S:{sessionId}')와 겹치지 않게 한다.
--
-- created_at 을 기본값(지금) 에 맡기지 않고 과거로 박아 둔다. 중복 방지 창이 이제 이
-- 컬럼을 보기 때문에(DOMAIN.md 6.2), 기본값으로 두면 시드 직후 10분 동안 'S:seed-*' 로
-- 들어온 조회가 창에 걸린다 — 로컬에서 조회수를 확인하려는 그 시간대다.
-- ---------------------------------------------------------------------------

INSERT INTO `post_views` (`post_id`, `viewer_key`, `created_at`)
SELECT p.`id`,
       CONCAT('S:seed-', nums.`n`),
       TIMESTAMP(@activity_day, '12:00:00')
  FROM `posts` p
  JOIN (
        SELECT (tens.`n` - 1) * 10 + ones.`n` AS `n`
          FROM (
                SELECT 1 AS `n` UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
                UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8
                UNION ALL SELECT 9 UNION ALL SELECT 10
               ) tens
          CROSS JOIN (
                SELECT 1 AS `n` UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
                UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8
                UNION ALL SELECT 9 UNION ALL SELECT 10
               ) ones
       ) nums
    ON nums.`n` <= p.`view_count`;

-- 이력에서 다시 센다. 여기서도 updated_at 을 보존한다.
UPDATE `posts` p
   SET p.`view_count` = (
        SELECT COUNT(*) FROM `post_views` pv WHERE pv.`post_id` = p.`id`
       ),
       p.`updated_at` = p.`updated_at`;

-- ---------------------------------------------------------------------------
-- 7. 신고
--
-- 관리자 화면은 신고가 하나도 없으면 전부 빈 상태로만 보인다 — '신고 많은 순' 정렬도,
-- '미처리' 배지도, '신고 기각' 버튼도 나타나지 않아서 로컬에서 확인할 방법이 없다.
--
-- 세 가지 상태를 모두 깔아 둔다. 미처리 신고가 있는 글, 차단하면서 처리한 신고,
-- 기각한 신고. 상태가 하나뿐이면 목록 정렬이 '미처리만 세는지'를 눈으로 볼 수 없다.
--
-- 자기 글은 신고할 수 없으므로(DOMAIN.md 6.6) 신고자는 언제나 작성자가 아닌 회원이다.
-- 시드 게시글은 대부분 user@ 가 쓴 것이라, 신고자를 회원 하나로 고정하면 조건에 걸려
-- 한 건도 안 들어간다. 그래서 글마다 작성자가 아닌 쪽을 골라 넣는다.
-- ---------------------------------------------------------------------------

SET @pending_report_post_id := (SELECT `id` FROM `posts`
                                 WHERE `title` = '한 번 수정한 글입니다');
SET @rejected_report_post_id := (SELECT MIN(`id`) FROM `posts`
                                  WHERE `status` = 'PUBLISHED'
                                    AND `id` <> @pending_report_post_id);
SET @blocked_post_id := (SELECT MIN(`id`) FROM `posts` WHERE `status` = 'BLOCKED');

INSERT INTO `post_reports` (`post_id`, `reporter_id`, `reason`, `status`, `created_at`)
SELECT r.`post_id`,
       IF(p.`member_id` = @member_id, @admin_id, @member_id),
       r.`reason`,
       r.`status`,
       TIMESTAMP(@activity_day, '15:00:00')
  FROM (
        SELECT @pending_report_post_id  AS `post_id`,
               '광고성 링크가 반복해서 올라옵니다.' AS `reason`, 'PENDING'  AS `status`
        UNION ALL
        SELECT @rejected_report_post_id,
               '내용이 마음에 들지 않습니다.',        'REJECTED'
        UNION ALL
        SELECT @blocked_post_id,
               '욕설이 포함되어 있습니다.',           'RESOLVED'
       ) r
  JOIN `posts` p ON p.`id` = r.`post_id`;

-- ---------------------------------------------------------------------------
-- 8. 인기글 확정 스냅샷
--
-- 배치가 어제치를 이미 돌린 상태를 만든다. 이 절이 없으면 시드 직후 인기글 영역이
-- **비어 있는 것이 정상**이다 — 화면은 확정된 스냅샷만 읽고(DOMAIN.md 6.9), 배치는
-- 다음 날 00:05 에야 처음 돈다(PLAN.md D3). 로컬에서 하루를 기다릴 수는 없다.
--
-- ▸ 집계식의 정본은 여기가 아니다
--
-- 아래 SELECT 는 CommunityMapper.xml 의 insertDailyRanking 을 그대로 옮긴 것이고,
-- 가중치(1·25·15)와 창 경계와 PUBLISHED 조건이 전부 그쪽 정본을 따른다. **정본이
-- 바뀌면 이 절도 함께 고쳐야 한다** — 갈라져도 시드는 조용히 성공하고 로컬 순위만
-- 운영과 달라진다. 재현하지 않고 손으로 고른 순위를 넣지 않는 이유가 그것이다.
-- 순위가 왜 그 순서인지 로컬에서 설명되지 않으면 확인용 데이터가 아니다.
--
-- ▸ 배치가 이 날짜를 다시 돌리지 않는다
--
-- 실행 기록을 함께 남기므로 D4(확정된 날짜는 재집계하지 않는다)에 따라 오늘 밤
-- 배치는 어제를 건너뛴다. 그것이 맞다 — 여기서 넣은 것이 곧 그날의 확정 결과다.
-- 0절이 두 표를 함께 지우는 것과 짝이다.
-- ---------------------------------------------------------------------------

INSERT INTO `daily_popular_posts`
        (`ranking_date`, `ranking`, `post_id`, `popularity_score`,
         `view_count`, `like_count`, `comment_count`)
SELECT @activity_day,
       ROW_NUMBER() OVER (ORDER BY s.`popularity_score` DESC, s.`post_id` DESC),
       s.`post_id`,
       s.`popularity_score`,
       s.`view_count`,
       s.`like_count`,
       s.`comment_count`
  FROM (
        SELECT e.`post_id`,
               SUM(e.`view_count`)    AS `view_count`,
               SUM(e.`like_count`)    AS `like_count`,
               SUM(e.`comment_count`) AS `comment_count`,
               SUM(e.`view_count`) * 1
                 + SUM(e.`like_count`) * 25
                 + SUM(e.`comment_count`) * 15 AS `popularity_score`
          FROM (
                SELECT `post_id`, 1 AS `view_count`, 0 AS `like_count`, 0 AS `comment_count`
                  FROM `post_views`
                 WHERE `created_at` >= @activity_day
                   AND `created_at` <  @activity_day + INTERVAL 1 DAY

                UNION ALL

                SELECT `post_id`, 0, 1, 0
                  FROM `post_likes`
                 WHERE `created_at` >= @activity_day
                   AND `created_at` <  @activity_day + INTERVAL 1 DAY

                UNION ALL

                -- 댓글은 건수가 아니라 **쓴 사람 수**다(DOMAIN.md 6.9). 25건짜리 글이
                -- 계수 15 를 25번 받으면 나머지 신호가 통째로 묻힌다.
                SELECT `post_id`, 0, 0, 1
                  FROM (
                        SELECT `post_id`, `member_id`
                          FROM `comments`
                         WHERE `status` = 'PUBLISHED'
                           AND `created_at` >= @activity_day
                           AND `created_at` <  @activity_day + INTERVAL 1 DAY
                         GROUP BY `post_id`, `member_id`
                       ) commenters
               ) e

          -- 선정 단계에서도 노출 중인 글만 본다. 지운 글도 활동 이력은 그대로 남아
          -- 있어서(DOMAIN.md 4.5) 안 거르면 상위 20칸을 먹는다.
          JOIN `posts` p
            ON p.`id` = e.`post_id`
           AND p.`status` = 'PUBLISHED'

         GROUP BY e.`post_id`
         ORDER BY `popularity_score` DESC, e.`post_id` DESC
         LIMIT 20
       ) s;

-- 순위 행 수를 다시 세어 기록한다. ROW_COUNT() 를 쓰지 않는 것은 중간에 문장이 하나만
-- 끼어도 값이 조용히 바뀌기 때문이다.
INSERT INTO `popular_post_batch_runs` (`ranking_date`, `post_count`)
SELECT @activity_day, COUNT(*)
  FROM `daily_popular_posts`
 WHERE `ranking_date` = @activity_day;

-- ---------------------------------------------------------------------------
-- 9. 공지사항 (PLAN.md 조각 14a)
--
-- 네 상태를 다 만든다 — 관리자 목록의 배지가 `예정`/`노출 중`/`종료`/`삭제됨` 넷이고,
-- **판정이 틀려도 화면은 멀쩡해 보이기 때문이다.** 하나만 넣으면 그 하나가 어떤 배지로
-- 나오든 그럴듯하다.
--
-- 시각은 위와 같은 이유로 실행일 기준 상대값이다. 고정 날짜를 박으면 '예정'이 언젠가
-- 반드시 '노출 중'이 되고, 그날부터 로컬에서는 예정 상태를 볼 방법이 사라진다.
--
-- NULL 두 개도 함께 넣는다. 시작 NULL(즉시)과 종료 NULL(무기한)은 **컬럼이 비어 있는
-- 것과 구분되지 않아** 목록에서 `즉시`/`무기한` 문구가 실제로 뜨는지 봐야 한다.
-- ---------------------------------------------------------------------------

INSERT INTO `community_notices`
    (`title`, `content`, `status`, `starts_at`, `ends_at`, `created_by`, `created_at`)
VALUES
    ('[안내] 커뮤니티 이용 규칙',
     '서로를 존중하는 커뮤니티를 만들어 주세요.\n광고와 비방 글은 예고 없이 차단될 수 있습니다.',
     'PUBLISHED', NULL, NULL, @admin_id, @posted_day),

    ('추석 연휴 배송 일정 안내',
     '연휴 기간에는 픽업만 가능합니다.\n자세한 일정은 매장 공지를 확인해 주세요.',
     'PUBLISHED', @today - INTERVAL 1 DAY, @today + INTERVAL 7 DAY, @admin_id, @posted_day),

    ('[예정] 신메뉴 출시 안내',
     '다음 주에 공개됩니다. 아직 고객 화면에 보이면 안 되는 공지입니다.',
     'PUBLISHED', @today + INTERVAL 3 DAY, @today + INTERVAL 30 DAY, @admin_id, @posted_day),

    ('[종료] 여름 한정 케이크 안내',
     '노출 기간이 지난 공지입니다. 관리자 목록에는 남고 고객 화면에서는 빠집니다.',
     'PUBLISHED', @today - INTERVAL 30 DAY, @today - INTERVAL 1 DAY, @admin_id, @posted_day),

    ('[삭제됨] 잘못 올린 공지',
     '삭제해도 행은 남는다(soft delete). 관리자 목록에만 보인다.',
     'DELETED', NULL, NULL, @admin_id, @posted_day);

-- ---------------------------------------------------------------------------
-- 10. 확인
--
-- 조회수불일치 는 반드시 0 이어야 한다. 0 이 아니면 view_count 와 post_views 가
-- 갈라진 것이고, 그 상태의 조회수는 순위에 쓸 수 없다(DOMAIN.md 6.2).
-- 수정표시글 은 1 이다 — '한 번 수정한 글입니다' 하나뿐이어야 한다.
-- 인기글 은 0 이면 안 된다. 0 이면 화면에서 영역이 통째로 사라진다(DOMAIN.md 6.9).
-- ---------------------------------------------------------------------------

SELECT (SELECT COUNT(*) FROM `post_categories` WHERE `is_active` = 1) AS `활성카테고리`,
       (SELECT COUNT(*) FROM `posts` WHERE `status` = 'PUBLISHED')    AS `노출게시글`,
       (SELECT COUNT(*) FROM `posts` WHERE `status` <> 'PUBLISHED')   AS `숨김게시글`,
       (SELECT COUNT(*) FROM `comments` WHERE `status` = 'PUBLISHED') AS `노출댓글`,
       (SELECT COUNT(*) FROM `comments` WHERE `status` = 'DELETED')   AS `자리표시댓글`,
       (SELECT COUNT(*) FROM `post_likes`)                            AS `좋아요`,
       (SELECT COUNT(*) FROM `post_views`)                            AS `조회이력`,
       (SELECT COUNT(*) FROM `post_reports` WHERE `status` = 'PENDING') AS `미처리신고`,
       (SELECT COUNT(*) FROM `post_reports` WHERE `status` <> 'PENDING') AS `처리된신고`,
       (SELECT COUNT(*) FROM `posts` p
         WHERE p.`view_count` <> (SELECT COUNT(*) FROM `post_views` pv
                                   WHERE pv.`post_id` = p.`id`))      AS `조회수불일치`,
       (SELECT COUNT(*) FROM `posts` WHERE `updated_at` > `created_at`) AS `수정표시글`,
       (SELECT COUNT(*) FROM `daily_popular_posts`)                   AS `인기글`,
       (SELECT MAX(`ranking_date`) FROM `popular_post_batch_runs`)    AS `인기글확정일`,
       (SELECT COUNT(*) FROM `community_notices`)                     AS `공지`,
       (SELECT COUNT(*) FROM `community_notices`
         WHERE `status` = 'PUBLISHED'
           AND (`starts_at` IS NULL OR `starts_at` <= NOW(6))
           AND (`ends_at`   IS NULL OR NOW(6) < `ends_at`))           AS `노출중공지`;
