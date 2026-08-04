-- switch_post_view_window_to_10_minutes
-- 생성: 2026-08-04 10:29:34
--
-- 규칙
-- * 이 파일은 머지된 뒤 절대 수정하지 않는다. 변경이 필요하면 새 migration 을 만든다.
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다.

-- 조회수 중복 방지의 시간 창을 '날짜 칸' 에서 '굴러가는 10분' 으로 바꾼다
-- (docs/community/DOMAIN.md 6.2).
--
-- ▸ 왜 바꾸는가
--
-- 날짜 칸은 같은 사람이 같은 글을 하루에 한 번만 세게 한다. 방어력은 세지만 숫자가
-- 하루 단위로만 움직여서, 글을 다시 열어 본 사람에게는 조회수가 멈춘 값으로 보인다.
-- 10분 창은 반복 조회(새로고침·뒤로가기·댓글 '더 보기') 를 여전히 막으면서 숫자를
-- 살아 있게 한다.
--
-- 상한만 보면 느슨해지는 변경이다 — 뷰어 하나가 하루에 올릴 수 있는 조회수가
-- 1 에서 144 가 된다. 그럼에도 실질 방어력 차이가 크지 않은 것은 비로그인 키가
-- 세션 id 라서 **작정한 조작에는 두 창 모두 무력하기 때문이다**(6.2, PLAN.md R12).
-- 두 설계가 실제로 막는 것은 사람의 반복 조회이고, 그건 10분으로도 막힌다.
--
-- ▸ 왜 UNIQUE 를 버리는가
--
-- 굴러가는 창은 UNIQUE 로 표현할 수 없다. 그런데 애초에 중복을 실제로 막고 있던 것은
-- 이 제약이 아니라 increaseViewCount 가 posts 행에 먼저 거는 배타 잠금이다 — 같은 글의
-- 조회 요청들은 그 UPDATE 에서 줄을 서고, 잠금을 쥔 뒤에 NOT EXISTS 를 보므로 앞 요청이
-- 남긴 이력을 반드시 본다. 제약은 그 잠금 순서가 깨졌을 때 울리는 경보였다.
--
-- 경보를 잃는 것은 실제 손실이며, 대신 CommunityViewCountConcurrencyTests 가 그 자리를
-- 맡는다(H14). 잠금 순서를 되돌리면 교착이 나므로 동시성 테스트가 더 직접적으로 잡는다.
--
-- ▸ 왜 viewed_at 컬럼을 새로 만들지 않는가
--
-- created_at 이 이미 그 값이다. post_views 는 append-only 조회 이력이라 '행이 생긴 시각'
-- 과 '조회한 시각' 이 같다. 같은 값을 담는 컬럼을 하나 더 두면 언젠가 둘이 어긋난다.
-- DB 의 CURRENT_TIMESTAMP(6) 로 잡히는 것도 그대로다 — 서버가 여러 대여도 시계는 하나다.

-- ▸ 세 문장의 순서가 규칙이다
--
-- (1) 새 인덱스를 **먼저** 만든다. fk_post_views_post 가 post_id 로 시작하는 인덱스를
--     요구하는데, 지금 그것을 제공하는 유일한 인덱스가 지우려는 UNIQUE 다. 순서를
--     뒤집으면 "Cannot drop index: needed in a foreign key constraint" 로 migration 이
--     통째로 실패한다. 새 인덱스도 선두가 post_id 라 그 역할을 그대로 물려받는다.
--
-- (2) UNIQUE 를 **명시적으로** 지운다. 이 문장을 빼고 viewed_on 만 DROP 하면 MariaDB 는
--     인덱스를 함께 지우는 것이 아니라 그 컬럼만 빼고 UNIQUE (post_id, viewer_key) 를
--     남긴다. 그러면 한 사람이 한 글을 **영원히 한 번만** 볼 수 있게 되고, 두 번째
--     조회부터 recordView 가 예외를 던져 상세 페이지가 500 이 된다. 조용히 망가지는
--     쪽이라 (1) 보다 위험하다 — (1) 은 적어도 migration 이 실패하며 알려 준다.
--
-- (3) 컬럼을 지운다.

-- (1) 창 판단(NOT EXISTS) 이 탈 인덱스. 선두가 post_id 라 한 게시글의 이력 조회도 탄다.
ALTER TABLE `post_views`
    ADD INDEX `ix_post_views_post_viewer_created` (`post_id`, `viewer_key`, `created_at`);

-- (2)
ALTER TABLE `post_views`
    DROP INDEX `uk_post_views_post_viewer_date`;

-- (3)
ALTER TABLE `post_views`
    DROP COLUMN `viewed_on`;

-- view_count 는 손대지 않는다.
--
-- 창이 바뀌어도 지금까지의 이력은 그대로 유효하다 — 행 하나가 조회 한 번이라는 뜻은
-- 변하지 않으므로 view_count == COUNT(post_views) 불변식(H14) 이 계속 성립한다.
-- V20260803_235726 이 0 으로 되돌려야 했던 것과는 상황이 다르다. 그때는 이력 자체가
-- 없어서 재계산할 근거가 없었다.
