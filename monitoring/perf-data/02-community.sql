-- 커뮤니티 측정 데이터. 01-parents.sql 다음에 실행한다. 다시 실행해도 된다.
--
-- 게시글 100,000 · 댓글 1,000,000.
--
-- ▸ 왜 카테고리로 댓글 밀도를 가르나
--
-- M1 이 재려는 것은 "목록 한 쪽을 그릴 때 댓글 수가 비용을 정하는가"다.
-- 그런데 최신순 목록에서 댓글 많은 글과 적은 글을 비교하려면 보통 쪽 번호를 바꿔야 하고,
-- 그러면 OFFSET 비용이 함께 변해 무엇이 원인인지 갈리지 않는다.
--
-- 그래서 카테고리마다 댓글 밀도를 다르게 심는다. 그러면 두 조회가
--   - 같은 정렬(최신순), 같은 쪽(1쪽), 같은 OFFSET(0)
--   - 다른 것은 댓글 수 하나
-- 가 되어 A/B 가 성립한다.
--
--   질문(1)  게시글 20,000 — 최신 1,000 개에 글당 댓글 500  ← 최악
--   후기(2)  게시글 30,000 — 최신 5,000 개에 글당 댓글 100  ← 중간
--   자유(3)  게시글 50,000 — 댓글 없음                      ← 대조군
--
-- ▸ 카테고리를 시간에 '섞어야' 한다 — 처음에 여기서 틀렸다
--
-- 첫 판에서는 카테고리를 id 구간으로 뭉쳐 심었다(1~20000 이 질문, 나머지가 차례로).
-- created_at 을 id 순으로 늘렸으므로 카테고리가 곧 시간 구간이 되어 버렸고,
-- 그 결과 질문 카테고리가 통째로 '가장 오래된 글'이 됐다.
--
-- 최신순 목록은 created_at 인덱스를 뒤에서부터 훑는데, 질문 카테고리 20건을 채우려면
-- 그 앞의 자유·후기 80,000 줄을 전부 지나야 했다. ANALYZE 의 r_rows 가 80,020 이었다.
-- 댓글을 한 건도 세지 않아도 48ms 가 나왔고, 이건 코드의 성질이 아니라 데이터 배치가
-- 만든 값이다. 실제 게시판에서 카테고리는 시간에 섞여 있다.
--
-- 그래서 카테고리를 seq 의 나머지로 정해 시간축에 고르게 흩는다. 비율은 그대로 2:3:5 다.

SET @base = TIMESTAMP'2026-01-01 00:00:00';

SET foreign_key_checks = 0;
SET unique_checks = 0;
SET autocommit = 0;

-- 다시 실행할 수 있게 비우고 시작한다. AUTO_INCREMENT 도 되돌아간다.
TRUNCATE TABLE comments;
TRUNCATE TABLE posts;

-- ── 게시글 100,000 ─────────────────────────────────────────────────────
-- 본문은 400자 안팎으로 둔다. ADR-004 가 검색 비용을 잴 때 쓴 크기와 같게 맞춰
-- 그 측정값과 나란히 놓을 수 있게 한다.
INSERT INTO posts (member_id, category_id, title, content,
                   view_count, like_count, status, created_at, updated_at)
SELECT 2 + (seq % 1000),
       -- 시간축에 섞는다. 10개마다 질문 2 · 후기 3 · 자유 5.
       CASE WHEN seq % 10 IN (0, 1)       THEN 1
            WHEN seq % 10 IN (2, 3, 4)    THEN 2
            ELSE 3 END,
       CONCAT('측정 게시글 ', seq, ' 케이크 주문 문의드립니다'),
       CONCAT('측정용 본문 ', seq, ' ', REPEAT('생일 케이크를 주문하려는데 크기와 맛을 골라 주세요. ', 12)),
       -- 조회수순·좋아요순 정렬이 의미를 갖도록 값을 흩는다. 곱수는 소수라 겹침이 적다.
       (seq * 7919) % 10000,
       (seq * 6997) % 500,
       'PUBLISHED',
       @base + INTERVAL seq MINUTE,
       @base + INTERVAL seq MINUTE
FROM seq_1_to_100000;

COMMIT;

-- ── 댓글 1,000,000 ────────────────────────────────────────────────────
-- 대상 글을 id 산술로 고르지 않는다. 카테고리가 시간에 섞이면서 id 와 카테고리의
-- 관계가 끊어졌기 때문이다. 실제 최신순 상위에서 골라 붙인다.

-- 질문 카테고리의 최신 1,000개 × 500 = 500,000
INSERT INTO comments (post_id, member_id, content, status, created_at)
SELECT hot.id,
       2 + (s.seq % 1000),
       CONCAT('측정 댓글 문의 감사합니다. ', hot.id, '-', s.seq),
       'PUBLISHED',
       @base + INTERVAL s.seq SECOND
FROM (SELECT id FROM posts WHERE category_id = 1 AND status = 'PUBLISHED'
       ORDER BY created_at DESC, id DESC LIMIT 1000) hot
JOIN seq_1_to_500 s;

COMMIT;

-- 후기 카테고리의 최신 5,000개 × 100 = 500,000
INSERT INTO comments (post_id, member_id, content, status, created_at)
SELECT warm.id,
       2 + (s.seq % 1000),
       CONCAT('측정 댓글 후기 잘 봤습니다. ', warm.id, '-', s.seq),
       'PUBLISHED',
       @base + INTERVAL s.seq SECOND
FROM (SELECT id FROM posts WHERE category_id = 2 AND status = 'PUBLISHED'
       ORDER BY created_at DESC, id DESC LIMIT 5000) warm
JOIN seq_1_to_100 s;

COMMIT;

SET autocommit = 1;
SET unique_checks = 1;
SET foreign_key_checks = 1;

-- ── 확인 ───────────────────────────────────────────────────────────────
SELECT (SELECT COUNT(*) FROM posts)    AS posts,
       (SELECT COUNT(*) FROM comments) AS comments;

-- 카테고리가 시간에 섞였는지. 세 카테고리의 기간이 겹쳐야 한다.
SELECT category_id, COUNT(*) AS posts, MIN(created_at) AS oldest, MAX(created_at) AS newest
FROM posts GROUP BY category_id ORDER BY category_id;

-- 카테고리별 1쪽에 오는 글들의 댓글 수. 설계대로면 500 / 100 / 0 이 나온다.
SELECT p.category_id,
       COUNT(*)   AS posts_on_page1,
       MIN(c.cnt) AS min_comments,
       MAX(c.cnt) AS max_comments
FROM (
    SELECT id, category_id,
           ROW_NUMBER() OVER (PARTITION BY category_id ORDER BY created_at DESC, id DESC) AS rn
    FROM posts WHERE status = 'PUBLISHED'
) p
LEFT JOIN (SELECT post_id, COUNT(*) AS cnt FROM comments WHERE status='PUBLISHED' GROUP BY post_id) c
       ON c.post_id = p.id
WHERE p.rn <= 20
GROUP BY p.category_id
ORDER BY p.category_id;
