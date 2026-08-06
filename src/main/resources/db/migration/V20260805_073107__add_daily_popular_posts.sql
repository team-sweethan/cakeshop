-- add_daily_popular_posts
-- 생성: 2026-08-05 07:31:07
--
-- 규칙
-- * 이 파일은 머지된 뒤 절대 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다.

-- 인기글 배치의 저장소 (docs/community/PLAN.md 조각 7b).
--
-- ▸ 이 파일이 다섯 문장인 이유, 그리고 전부 IF NOT EXISTS 인 이유
--
-- 두 테이블과 세 인덱스는 **함께 있어야 배치가 동작하는 한 벌**이다. 파일을 다섯으로
-- 쪼개면 "인덱스 없이 테이블만 있는" 중간 버전이 정상 상태로 기록되고, 그 시점에
-- 배치가 돌면 원본 세 테이블을 통째로 스캔한다.
--
-- 그런데 MariaDB 의 DDL 은 트랜잭션이 아니다. 다섯 문장을 한 파일에 담으면 세 번째가
-- 잠금 시간 초과로 실패했을 때 **앞의 둘만 남고 Flyway 버전은 기록되지 않는다.**
-- 그 상태로 재시도하면 매번 'Table already exists' 로 죽어 손으로 지우기 전에는
-- 복구되지 않는다.
--
-- IF NOT EXISTS 가 그 문제를 푼다 — 부분 적용이 남아도 재실행이 그 자리를 그냥
-- 지나간다. V20260804_102934 가 세 ALTER 를 한 문장으로 묶어 푼 것과 목적은 같고,
-- 대상 테이블이 여럿이라 수단만 다르다.

-- 날짜별 인기글 순위 스냅샷.
--
-- 화면은 이 표만 읽는다. 원본(post_views, post_likes, comments)을 화면에서 집계하지
-- 않는 이유는 목록 요청마다 7일치를 훑게 되기 때문이고, 스냅샷이라 **원본이 나중에
-- 변해도 그날의 순위는 그대로 남는다.**
CREATE TABLE IF NOT EXISTS `daily_popular_posts` (
    -- 어느 날짜의 순위인가. 배치가 넘긴 '대상일'이며 서울 기준이다(PLAN.md D10).
    `ranking_date`     DATE   NOT NULL,

    -- 1 부터 20 까지. 배치는 TOP 20 을 저장하고 화면은 10 건만 쓴다(D5).
    -- 20-10 의 여유는 비노출 글의 몫이 아니라 **선정 이후의 상태 변화**를 흡수하는
    -- 몫이다. 선정 시점에도 PUBLISHED 만 담기 때문이다.
    `ranking`          INT    NOT NULL,

    `post_id`          BIGINT NOT NULL,

    -- 조회수*1 + 좋아요*25 + 댓글 쓴 서로 다른 회원 수*15 (D1).
    `popularity_score` BIGINT NOT NULL,

    -- 선정 당시의 근거 수치. 순위가 **왜 그랬는지가 사후에 설명되어야** 하기 때문에
    -- 함께 담는다. 원본이 변한 뒤에도 그날의 계산을 재현할 수 있다.
    -- comment_count 는 댓글 건수가 아니라 **댓글을 쓴 서로 다른 회원 수**다(D1).
    `view_count`       BIGINT NOT NULL,
    `like_count`       BIGINT NOT NULL,
    `comment_count`    BIGINT NOT NULL,

    `created_at`       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    -- 화면이 (날짜, 순위) 로 읽으므로 그대로 PK 다. 같은 날짜에 같은 순위가 둘일 수
    -- 없다는 것도 함께 강제된다.
    PRIMARY KEY (`ranking_date`, `ranking`),

    -- 한 날짜에 같은 글이 두 번 오르지 않는다. 집계 SQL 의 GROUP BY 가 깨지면
    -- 여기서 먼저 걸린다 — 순위만 보면 그럴듯해 보여서 결과 검증으로는 안 잡힌다.
    CONSTRAINT `uk_daily_popular_post` UNIQUE (`ranking_date`, `post_id`),

    CONSTRAINT `fk_daily_popular_posts_post`
        FOREIGN KEY (`post_id`) REFERENCES `posts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 배치 실행 기록 (PLAN.md D11).
--
-- ▸ 순위 행과 '실행했다는 사실'은 다른 것이다
--
-- daily_popular_posts 만 두면 **"안 돈 날"과 "돌았는데 0건인 날"이 둘 다 행 없음**
-- 이라 구분되지 않는다. 구분이 안 되면 세 가지가 한꺼번에 무너진다.
--
--   1. 화면의 MAX(ranking_date) 가 옛 날짜로 계속 폴백해 **7일 창 밖의 오래된 글이
--      무기한 노출된다.** 그런데 화면은 멀쩡해 보인다 — 순위가 안 바뀌는 것은 활동이
--      뜸한 날과 구분되지 않는다.
--   2. "이미 돌았나" 판단(D4)이 매일 거짓이 되어 재실행이 계속 집계한다.
--   3. 배치 실패 경고가 정상 상태에서 울린다.
--
-- 이 표에는 0건인 날도 post_count = 0 으로 행이 남으므로 셋이 전부 갈린다.
--
-- ▸ FK 를 걸지 않는다
--
-- 이 표가 가리키는 것은 게시글이 아니라 **실행**이고, 순위가 0건인 날에는 참조할
-- 대상 자체가 없다.
CREATE TABLE IF NOT EXISTS `popular_post_batch_runs` (
    `ranking_date` DATE   NOT NULL,

    -- 그날 확정한 순위 행 수. 0 이 정상값이며, 그것이 이 표의 존재 이유다.
    `post_count`   INT    NOT NULL,

    `executed_at`  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (`ranking_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 집계용 인덱스 셋.
--
-- 배치는 세 원본을 created_at 범위로 **먼저 자른 뒤** UNION ALL 로 합친다. 이 인덱스가
-- 없으면 자르는 단계에서 세 테이블을 통째로 스캔해 창을 둔 이득이 통째로 사라진다.
--
-- (created_at, post_id) 순서가 규칙이다. 선두가 범위 조건인 created_at 이어야 창으로
-- 좁힐 수 있고, post_id 를 두 번째에 두면 그 뒤의 GROUP BY 까지 인덱스만 읽고 끝난다
-- (커버링). 뒤집으면 창으로 못 좁힌다.
--
-- post_views 에 이미 있는 ix_post_views_post_viewer_created 로는 이 일을 못 한다 —
-- 선두가 post_id 라 created_at 범위로는 탈 수가 없다. 같은 컬럼이 들어 있다고 해서
-- 쓸 수 있는 것이 아니다.
--
-- 되돌리기: 세 인덱스를 DROP INDEX 로 되돌릴 수 있다. 데이터를 바꾸지 않으므로
-- 백업이 필요 없다. 두 테이블은 DROP TABLE 로 되돌린다 — 배치가 다시 채운다.
ALTER TABLE `post_views`
    ADD INDEX IF NOT EXISTS `ix_post_views_created` (`created_at`, `post_id`);

ALTER TABLE `post_likes`
    ADD INDEX IF NOT EXISTS `ix_post_likes_created` (`created_at`, `post_id`);

ALTER TABLE `comments`
    ADD INDEX IF NOT EXISTS `ix_comments_created` (`created_at`, `post_id`);
