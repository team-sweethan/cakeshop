-- 측정용 부모 데이터. cakeshop_perf 에만 실행한다.
--
-- Flyway 가 만드는 것은 스키마와 기준 데이터(매장 1건, 게시글 카테고리 3종)까지다.
-- 회원·상품·주문은 시드가 넣던 것이라 측정 DB 에는 없다. 게시글은 회원이,
-- 후기는 주문 상품이 있어야 FK 가 걸리므로 여기서 먼저 만든다.
--
-- 순서: members -> categories -> products -> orders -> order_items
-- 되돌리기: DROP DATABASE cakeshop_perf (teardown.sql 참고)

SET @base = TIMESTAMP'2026-01-01 00:00:00';

SET foreign_key_checks = 0;
SET unique_checks = 0;

-- ── 회원 ───────────────────────────────────────────────────────────────
-- 관리자 1명. M4(후기 알림) 대조가 활성 관리자 수를 기준으로 하므로 수를 안다.
INSERT INTO members (email, password, name, nickname, phone, role, status, created_at, updated_at)
VALUES ('perf-admin@perf.local', '{noop}perf', '측정관리자', '측정관리자',
        '010-0000-0000', 'ADMIN', 'ACTIVE', @base, @base);

-- 일반 회원 1,000명. 좋아요·조회수 중복 판정이 회원 단위라 충분히 많아야 한다.
INSERT INTO members (email, password, name, nickname, phone, role, status, created_at, updated_at)
SELECT CONCAT('perf', LPAD(seq, 6, '0'), '@perf.local'),
       '{noop}perf',
       CONCAT('측정회원', seq),
       CONCAT('측정회원', seq),
       '010-0000-0000',
       'USER', 'ACTIVE', @base, @base
FROM seq_1_to_1000;

-- ── 상품 카테고리 ──────────────────────────────────────────────────────
INSERT INTO categories (code, name, sort_order, is_active, created_at, updated_at)
VALUES ('PERF', '측정용', 1, 1, @base, @base);

SET @category_id = LAST_INSERT_ID();

-- ── 상품 ───────────────────────────────────────────────────────────────
-- 앞의 셋이 후기 측정 대상이다(각각 10 / 500 / 5,000건).
-- 나머지는 목록 화면이 비지 않게 채우는 용도다.
INSERT INTO products (category_id, name, description, base_price, stock_quantity,
                      product_type, preparation_days, status, average_rating, review_count,
                      created_at, updated_at)
SELECT @category_id,
       CASE seq
           WHEN 1 THEN '측정상품A-후기10'
           WHEN 2 THEN '측정상품B-후기500'
           WHEN 3 THEN '측정상품C-후기5000'
           ELSE CONCAT('측정상품', seq)
       END,
       '측정용 상품이다. 화면 확인용이 아니다.',
       20000, 1000000,
       'GENERAL', 0, 'ACTIVE', 0.00, 0,
       @base, @base
FROM seq_1_to_50;

-- ── 주문 ───────────────────────────────────────────────────────────────
-- 후기 하나에 주문 상품 하나가 필요하다(uk_reviews_order_item).
-- 10 + 500 + 5,000 = 5,510 건에 여유를 더해 6,000 건을 만든다.
-- 주문 하나에 상품 하나로 두어 order_id 와 order_item id 가 1:1 로 따라간다.
INSERT INTO orders (order_number, member_id, orderer_name, orderer_phone,
                    pickup_name, pickup_phone, original_amount, discount_amount, final_amount,
                    status, order_type, pickup_at, picked_up_at, created_at, updated_at)
SELECT CONCAT('PERF-', LPAD(seq, 7, '0')),
       -- 회원 id 는 관리자 다음부터다. 1,000 명을 돌려 쓴다.
       2 + (seq % 1000),
       '측정주문자', '010-0000-0000', '측정수령자', '010-0000-0000',
       20000, 0, 20000,
       'PICKED_UP', 'GENERAL',
       @base + INTERVAL seq MINUTE,
       @base + INTERVAL seq MINUTE,
       @base, @base
FROM seq_1_to_6000;

-- ── 주문 상품 ──────────────────────────────────────────────────────────
-- 후기가 붙을 상품을 여기서 가른다.
--   1     ~ 10    -> 상품 A (후기 10건)
--   11    ~ 510   -> 상품 B (후기 500건)
--   511   ~ 5510  -> 상품 C (후기 5,000건)
--   5511  ~ 6000  -> 여분
INSERT INTO order_items (order_id, product_id, product_name, product_type,
                         quantity, base_price, option_amount, total_amount,
                         preparation_days, stock_deducted_at)
SELECT o.id,
       CASE
           WHEN seq <= 10   THEN 1
           WHEN seq <= 510  THEN 2
           WHEN seq <= 5510 THEN 3
           ELSE 4
       END,
       '측정상품', 'GENERAL',
       1, 20000, 0, 20000, 0, @base
FROM seq_1_to_6000 s
JOIN orders o ON o.order_number = CONCAT('PERF-', LPAD(s.seq, 7, '0'));

SET unique_checks = 1;
SET foreign_key_checks = 1;

SELECT (SELECT COUNT(*) FROM members)     AS members,
       (SELECT COUNT(*) FROM products)    AS products,
       (SELECT COUNT(*) FROM orders)      AS orders,
       (SELECT COUNT(*) FROM order_items) AS order_items;
