-- add_post_views
-- 생성: 2026-08-03 23:57:26
--
-- 규칙
-- * 이 파일은 머지된 뒤 절대 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다.

-- 조회 이력. 조회수 중복 방지의 정본이다(docs/community/DOMAIN.md 6.2).
--
-- 2026-08-03 에 조회수를 정렬·순위에 쓰기로 하면서 생겼다. 그 전까지 조회수는
-- 참고용 표시였고 중복 방지가 없었는데, 순위를 정하는 값이 되면 새로고침 한 번이
-- 곧 순위 조작이 된다.
CREATE TABLE `post_views` (
    `id`         BIGINT NOT NULL AUTO_INCREMENT,
    `post_id`    BIGINT NOT NULL,

    -- 조회자 식별자. 회원이면 'M:{memberId}', 비로그인이면 'S:{sessionId}' 다.
    -- 회원 번호를 컬럼으로 따로 두지 않는 것은 비로그인 조회도 같은 규칙으로 세기
    -- 위해서다. FK 가 걸리지 않는 대신 두 종류를 한 UNIQUE 로 다룰 수 있다.
    --
    -- IP 를 쓰지 않는다. 공유 NAT 에서 남의 조회가 합쳐지고, 개인정보를 새로
    -- 저장하게 된다(AGENTS.md 보안). 세션 id 는 우리가 발급한 값이라 그 문제가 없다.
    -- 길이는 세션 id(32자 내외)와 접두사를 넉넉히 담도록 잡았다.
    `viewer_key` VARCHAR(100) NOT NULL,

    -- 조회한 날짜. 중복 방지의 시간 창 단위다.
    --
    -- 굴러가는 '최근 24시간' 이 아니라 날짜 칸인 이유는 UNIQUE 로 강제할 수 있는
    -- 것이 칸뿐이기 때문이다. 창으로 하면 결국 애플리케이션이 "봤는지" 를 판단하게
    -- 되고, 동시 요청 두 개가 그 판단을 함께 통과하는 순간을 코드로는 막을 수 없다.
    -- 대가는 자정 직후 재조회가 다시 세어지는 것이고, 이건 감수한다.
    `viewed_on`  DATE NOT NULL,

    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (`id`),

    -- 중복 방지를 애플리케이션 판단이 아니라 DB 제약이 하게 한다.
    -- 선두 컬럼이 post_id 라 한 게시글의 이력 조회도 이 인덱스를 탄다.
    CONSTRAINT `uk_post_views_post_viewer_date`
        UNIQUE (`post_id`, `viewer_key`, `viewed_on`),

    CONSTRAINT `fk_post_views_post`
        FOREIGN KEY (`post_id`) REFERENCES `posts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 기존 view_count 를 0 으로 되돌린다.
--
-- posts.view_count 는 이제 post_views 에서 파생된 캐시다(DOMAIN.md 6.2). 그런데
-- 이력은 지금 비어 있으므로 과거 값을 재계산할 근거가 없다 — 누가 봤는지를 남기기
-- 시작하는 것이 이 migration 이다.
--
-- 남겨 두면 view_count == COUNT(post_views) 불변식이 첫날부터 거짓이 되고, 그러면
-- 그 불변식을 하네스로 고정할 수도 없다. 게다가 기존 값은 새로고침과 댓글 '더 보기'
-- 클릭이 섞인 수라 순위의 근거로 쓸 수 없다. 근거 없는 큰 수보다 근거 있는 0 이 낫다.
--
-- updated_at 을 자기 값으로 다시 지정하는 것은 실수가 아니다. posts.updated_at 은
-- ON UPDATE CURRENT_TIMESTAMP(6) 이라 그냥 두면 모든 글이 이 migration 때문에
-- '수정됨' 으로 표시된다(DOMAIN.md 6.3).
--
-- 되돌리기: 이 UPDATE 는 되돌릴 수 없다. 보존이 필요한 환경이라면 적용 전에
-- posts 의 id, view_count 를 따로 백업한다. rds 프로필은 Flyway 를 실행하지 않으므로
-- 운영 반영은 별도 검토·승인 절차를 따른다.
UPDATE `posts`
   SET `view_count` = 0,
       `updated_at` = `updated_at`;
