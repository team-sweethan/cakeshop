-- 커뮤니티 측정 데이터. 01-parents.sql 다음에 실행한다.
--
-- 게시글 100,000 · 댓글 1,000,000.
--
-- ▸ 왜 카테고리로 가르나
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
-- 비교할 두 화면:
--   GET /community?categoryId=1&page=1   (1쪽에 댓글 500짜리 글 20개)
--   GET /community?categoryId=3&page=1   (1쪽에 댓글 0짜리 글 20개)
--
-- ▸ created_at 을 seq 로 늘리는 이유
--
-- 최신순 정렬에서 어느 글이 1쪽에 오는지를 예측할 수 있어야 위 설계가 성립한다.
-- id 가 클수록 새 글이 되도록 심어서, 카테고리별 1쪽 = 그 카테고리의 가장 큰 id 20개가 된다.

SET @base = TIMESTAMP'2026-01-01 00:00:00';

SET foreign_key_checks = 0;
SET unique_checks = 0;
SET autocommit = 0;

-- ── 게시글 100,000 ─────────────────────────────────────────────────────
-- 본문은 400자 안팎으로 둔다. ADR-004 가 검색 비용을 잴 때 쓴 크기와 같게 맞춰
-- 그 측정값과 나란히 놓을 수 있게 한다.
INSERT INTO posts (member_id, category_id, title, content,
                   view_count, like_count, status, created_at, updated_at)
SELECT 2 + (seq % 1000),
       CASE WHEN seq <= 20000 THEN 1
            WHEN seq <= 50000 THEN 2
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
-- 질문 카테고리의 최신 1,000개(id 19001~20000)에 글당 500개.
INSERT INTO comments (post_id, member_id, content, status, created_at)
SELECT 19001 + ((seq - 1) DIV 500),
       2 + (seq % 1000),
       CONCAT('측정 댓글 ', seq, ' 문의 감사합니다.'),
       'PUBLISHED',
       @base + INTERVAL seq SECOND
FROM seq_1_to_500000;

COMMIT;

-- 후기 카테고리의 최신 5,000개(id 45001~50000)에 글당 100개.
INSERT INTO comments (post_id, member_id, content, status, created_at)
SELECT 45001 + ((seq - 1) DIV 100),
       2 + (seq % 1000),
       CONCAT('측정 댓글 ', 500000 + seq, ' 후기 잘 봤습니다.'),
       'PUBLISHED',
       @base + INTERVAL seq SECOND
FROM seq_1_to_500000;

COMMIT;

SET autocommit = 1;
SET unique_checks = 1;
SET foreign_key_checks = 1;

-- ── 확인 ───────────────────────────────────────────────────────────────
SELECT (SELECT COUNT(*) FROM posts)    AS posts,
       (SELECT COUNT(*) FROM comments) AS comments;

-- 카테고리별로 1쪽에 오는 글들의 댓글 수. 설계대로면 500 / 100 / 0 이 나온다.
SELECT p.category_id,
       COUNT(*)                                     AS posts_on_page1,
       MIN(c.cnt)                                   AS min_comments,
       MAX(c.cnt)                                   AS max_comments
FROM (
    SELECT id, category_id,
           ROW_NUMBER() OVER (PARTITION BY category_id ORDER BY created_at DESC, id DESC) AS rn
    FROM posts WHERE status = 'PUBLISHED'
) p
JOIN (SELECT id FROM posts) x ON x.id = p.id
LEFT JOIN (SELECT post_id, COUNT(*) AS cnt FROM comments WHERE status='PUBLISHED' GROUP BY post_id) c
       ON c.post_id = p.id
WHERE p.rn <= 20
GROUP BY p.category_id
ORDER BY p.category_id;
