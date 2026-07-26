-- =========================================================
-- 상품 도메인 로컬 테스트 데이터
-- 공용 RDS 적용 금지
-- =========================================================

-- 테스트용 케이크 카테고리를 등록한다.
-- 동일한 code가 존재하면 중복 행을 만들지 않고 기존 값을 갱신한다.
INSERT INTO `categories` (
    `code`,
    `name`,
    `sort_order`,
    `is_active`
)
VALUES (
    'CAKE',
    '케이크',
    1,
    1
)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `sort_order` = VALUES(`sort_order`),
    `is_active` = VALUES(`is_active`);


-- =========================================================
-- 다양한 케이크 테스트 상품 등록
--
-- 동일한 카테고리와 상품명이 존재하면 다시 등록하지 않는다.
-- =========================================================

INSERT INTO `products` (
    `category_id`,
    `name`,
    `description`,
    `base_price`,
    `stock_quantity`,
    `product_type`,
    `preparation_days`,
    `cancellation_limit_days`,
    `status`
)
SELECT
    c.`id`,
    sample.`name`,
    sample.`description`,
    sample.`base_price`,
    sample.`stock_quantity`,
    sample.`product_type`,
    sample.`preparation_days`,
    sample.`cancellation_limit_days`,
    sample.`status`
FROM `categories` c
CROSS JOIN (
    -- 딸기 케이크: 일반적인 재고 상품
    SELECT
        '딸기 생크림 케이크' AS `name`,
        '신선한 딸기와 부드러운 생크림으로 만든 케이크입니다.' AS `description`,
        35000 AS `base_price`,
        12 AS `stock_quantity`,
        'GENERAL' AS `product_type`,
        2 AS `preparation_days`,
        1 AS `cancellation_limit_days`,
        'ACTIVE' AS `status`

    UNION ALL

    -- 초코 케이크: 일반적인 재고 상품
    SELECT
        '초코 가나슈 케이크',
        '진한 다크초콜릿과 부드러운 가나슈 크림으로 만든 케이크입니다.',
        42000,
        8,
        'GENERAL',
        2,
        1,
        'ACTIVE'

    UNION ALL

    -- 말차 케이크: 재고가 적은 상품
    SELECT
        '말차 팥 케이크',
        '쌉싸름한 제주 말차 크림과 달콤한 팥을 조합한 케이크입니다.',
        39000,
        3,
        'GENERAL',
        3,
        2,
        'ACTIVE'

    UNION ALL

    -- 얼그레이 케이크: 품절 상태 확인용 상품
    SELECT
        '얼그레이 오렌지 케이크',
        '향긋한 얼그레이 크림과 상큼한 오렌지를 더한 케이크입니다.',
        40000,
        0,
        'GENERAL',
        2,
        1,
        'ACTIVE'

    UNION ALL

    -- 당근 케이크: 당일 픽업 가능 상품
    SELECT
        '호두 당근 케이크',
        '당근과 호두를 넣고 크림치즈 프로스팅을 올린 케이크입니다.',
        32000,
        10,
        'GENERAL',
        0,
        0,
        'ACTIVE'

    UNION ALL

    -- 주문 제작 케이크: 재고 제한 없음
    SELECT
        '레터링 생크림 케이크',
        '원하는 문구와 색상을 선택할 수 있는 주문 제작 케이크입니다.',
        55000,
        NULL,
        'CUSTOM',
        4,
        3,
        'ACTIVE'

    UNION ALL

    -- 판매 중지 상품: 고객 화면에 노출되지 않아야 한다.
    SELECT
        '블루베리 요거트 케이크',
        '블루베리와 요거트 크림을 사용한 상큼한 케이크입니다.',
        37000,
        6,
        'GENERAL',
        2,
        1,
        'INACTIVE'
) sample
WHERE c.`code` = 'CAKE'
  AND NOT EXISTS (
      SELECT 1
      FROM `products` p
      WHERE p.`category_id` = c.`id`
        AND p.`name` = sample.`name`
  );


-- 등록 결과 확인용 조회
SELECT
    `id`,
    `name`,
    `base_price`,
    `stock_quantity`,
    `product_type`,
    `preparation_days`,
    `cancellation_limit_days`,
    `status`
FROM `products`
ORDER BY `id`;
