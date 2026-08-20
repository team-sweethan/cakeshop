-- 후기 측정 데이터. 01-parents.sql 다음에 실행한다.
--
-- 후기 5,510건을 상품 셋에 몰아서 심는다.
--
--   상품 1  후기    10건  ← 지금 상태의 기준선
--   상품 2  후기   500건  ← risks.md R8 이 말한 계기("한 상품의 후기가 수백 건") 근처
--   상품 3  후기 5,000건  ← 계기를 넘겼을 때
--
-- ▸ 왜 한 상품에 몰아 넣나
--
-- 이 도메인의 비용은 후기 총량이 아니라 한 상품이 가진 후기 수가 정한다.
-- 후기를 하나 쓸 때마다 aggregateForUpdate 가 그 상품의 공개 후기를 전부 읽고,
-- 상품 상세의 목록·건수도 같은 조건으로 훑는다(M1·M3).
-- 그래서 세 상품을 나란히 두면 같은 쿼리에 입력 크기만 다른 A/B 가 된다.
--
-- ▸ 이 후기들은 앱을 거치지 않는다
--
-- 시드와 같은 이유로 NEW_REVIEW 알림이 없다. M4 대조에는 안 걸리는데,
-- created_at 이 2026-01-01 근처라 M4 의 측정 창(최근 7일) 밖이기 때문이다.
-- 창을 늘려서 보게 되면 이 행들이 통째로 뜬다 — 정상이다.

SET @base = TIMESTAMP'2026-01-01 00:00:00';

SET foreign_key_checks = 0;
SET unique_checks = 0;
SET autocommit = 0;

-- ── 후기 ───────────────────────────────────────────────────────────────
-- 어느 주문 상품이 어느 상품에 붙는지는 01-parents.sql 이 이미 갈라 놓았다.
-- 평점은 1~5 를 골고루 흩어 AVG 가 한쪽으로 쏠리지 않게 한다.
INSERT INTO reviews (order_item_id, product_id, member_id,
                     overall_rating, taste_rating, design_rating, service_rating,
                     content, status, created_at, updated_at)
SELECT oi.id,
       oi.product_id,
       o.member_id,
       1 + (oi.id % 5),
       1 + ((oi.id + 1) % 5),
       1 + ((oi.id + 2) % 5),
       1 + ((oi.id + 3) % 5),
       CONCAT('측정 후기 ', oi.id, ' 맛있게 잘 먹었습니다. 다음에 또 주문할게요.'),
       'PUBLISHED',
       @base + INTERVAL oi.id MINUTE,
       @base + INTERVAL oi.id MINUTE
FROM order_items oi
JOIN orders o ON o.id = oi.order_id
WHERE oi.product_id IN (1, 2, 3);

COMMIT;

SET autocommit = 1;
SET unique_checks = 1;
SET foreign_key_checks = 1;

-- ── 상품에 저장된 평점을 실제와 맞춘다 ─────────────────────────────────
-- 앱을 거치지 않고 넣었으므로 재계산이 돌지 않았다. 여기서 맞추지 않으면
-- M5(저장된 평점 대 실제 후기) 대조가 처음부터 어긋난 채로 시작한다.
UPDATE products p
LEFT JOIN (
    SELECT product_id,
           AVG(overall_rating) AS avg_rating,
           COUNT(*)            AS cnt
    FROM reviews
    WHERE status = 'PUBLISHED'
    GROUP BY product_id
) agg ON agg.product_id = p.id
SET p.average_rating = ROUND(COALESCE(agg.avg_rating, 0), 2),
    p.review_count   = COALESCE(agg.cnt, 0);

-- ── 확인 ───────────────────────────────────────────────────────────────
SELECT p.id AS product_id, p.name,
       p.review_count AS stored_count,
       p.average_rating AS stored_rating,
       COUNT(r.id) AS actual_count
FROM products p
LEFT JOIN reviews r ON r.product_id = p.id AND r.status = 'PUBLISHED'
WHERE p.id IN (1, 2, 3)
GROUP BY p.id, p.name, p.review_count, p.average_rating
ORDER BY p.id;

-- M5 대조: 어긋난 상품이 없어야 한다(0행).
SELECT p.id AS mismatched_product
FROM products p
LEFT JOIN reviews r ON r.product_id = p.id AND r.status = 'PUBLISHED'
GROUP BY p.id, p.review_count, p.average_rating
HAVING p.review_count <> COUNT(r.id)
    OR p.average_rating <> ROUND(COALESCE(AVG(r.overall_rating), 0), 2);
