-- 공용 개발 RDS 시연용 seed
--
-- 적용 대상: Flyway migration을 모두 적용한 빈 공용 개발·시연 RDS
-- 운영 DB에는 실행하지 않는다.
-- Flyway migration을 모두 적용한 뒤 MariaDB 클라이언트의
-- --abort-source-on-error 옵션과 함께 수동 실행한다.
--
-- 기존 업무 데이터를 삭제하거나 AUTO_INCREMENT를 초기화하지 않는다.
-- 같은 관리자 이메일과 상품·옵션 데이터가 이미 있으면 중복 생성하지 않는다.
-- 커뮤니티 시드는 일반 회원 의존성이 있으므로 이 파일에 포함하지 않는다.

SET time_zone = '+09:00';

START TRANSACTION;

-- 관리자 로그인용 계정
-- 이메일 인증 이력은 일반 회원가입 절차용이므로 생성하지 않는다.
-- 로그인: admin@cakeshop.local / Admin1234!
INSERT INTO `members`
    (`email`, `password`, `name`, `nickname`, `phone`, `role`, `status`)
SELECT
    'admin@cakeshop.local',
    '$2a$10$wRIE78x8sm..uLtbp9LHde7l6wUWQD3NjPvThQaXvZ3PpXfW6wwX.',
    '관리자',
    '관리자',
    '010-0000-0001',
    'ADMIN',
    'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
      FROM `members`
     WHERE `email` = 'admin@cakeshop.local'
);

-- =========================================================
-- 상품 도메인 공용 개발 RDS 시연 데이터
-- 운영 DB에는 적용하지 않는다
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
    sample.`status`
FROM `categories` c
CROSS JOIN (
    -- 일반 상품은 크기·색상 조합별로 독립적인 재고를 가진다.
    SELECT
        '딸기 생크림 케이크 1호' AS `name`,
        '신선한 딸기와 부드러운 생크림으로 만든 1호 케이크입니다.' AS `description`,
        35000 AS `base_price`,
        7 AS `stock_quantity`,
        'GENERAL' AS `product_type`,
        0 AS `preparation_days`,
        'ACTIVE' AS `status`

    UNION ALL

    SELECT
        '딸기 생크림 케이크 2호',
        '신선한 딸기와 부드러운 생크림으로 만든 2호 케이크입니다.',
        45000,
        5,
        'GENERAL',
        0,
        'ACTIVE'

    UNION ALL

    SELECT
        '딸기 생크림 케이크 3호',
        '신선한 딸기와 부드러운 생크림으로 만든 3호 케이크입니다.',
        55000,
        2,
        'GENERAL',
        0,
        'ACTIVE'

    UNION ALL

    SELECT
        '초코 가나슈 케이크 1호 다크초코',
        '진한 다크초콜릿 가나슈로 만든 1호 케이크입니다.',
        42000,
        6,
        'GENERAL',
        0,
        'ACTIVE'

    UNION ALL

    SELECT
        '초코 가나슈 케이크 1호 화이트',
        '부드러운 화이트초콜릿 가나슈로 만든 1호 케이크입니다.',
        42000,
        4,
        'GENERAL',
        0,
        'ACTIVE'

    UNION ALL

    SELECT
        '초코 가나슈 케이크 2호 다크초코',
        '진한 다크초콜릿 가나슈로 만든 2호 케이크입니다.',
        54000,
        3,
        'GENERAL',
        0,
        'ACTIVE'

    UNION ALL

    SELECT
        '초코 가나슈 케이크 2호 화이트',
        '부드러운 화이트초콜릿 가나슈로 만든 2호 케이크입니다.',
        54000,
        2,
        'GENERAL',
        0,
        'ACTIVE'

    UNION ALL

    -- 말차 케이크: 재고가 적은 상품
    SELECT
        '말차 팥 케이크',
        '쌉싸름한 제주 말차 크림과 달콤한 팥을 조합한 케이크입니다.',
        39000,
        3,
        'GENERAL',
        0,
        'ACTIVE'

    UNION ALL

    -- 얼그레이 케이크: 품절 상태 확인용 상품
    SELECT
        '얼그레이 오렌지 케이크',
        '향긋한 얼그레이 크림과 상큼한 오렌지를 더한 케이크입니다.',
        40000,
        0,
        'GENERAL',
        0,
        'ACTIVE'

    UNION ALL

    -- 당근 케이크: 일반 재고 상품
    SELECT
        '호두 당근 케이크',
        '당근과 호두를 넣고 크림치즈 프로스팅을 올린 케이크입니다.',
        32000,
        10,
        'GENERAL',
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
        'ACTIVE'

    UNION ALL

    -- 바닐라 케이크: 일반 재고와 낮은 가격대 확인용
    SELECT
        '바닐라 빈 케이크',
        '마다가스카르산 바닐라 빈을 넣은 부드러운 생크림 케이크입니다.',
        30000,
        20,
        'GENERAL',
        0,
        'ACTIVE'

    UNION ALL

    -- 망고 케이크: 일반 상품이 품절된 경우 확인용
    SELECT
        '애플망고 요거트 케이크',
        '애플망고와 산뜻한 요거트 크림을 올린 케이크입니다.',
        45000,
        0,
        'GENERAL',
        0,
        'ACTIVE'

    UNION ALL

    -- 티라미수: 중간 가격대와 일반 재고 확인용
    SELECT
        '마스카포네 티라미수 케이크',
        '에스프레소 시트와 마스카포네 크림을 겹겹이 쌓은 케이크입니다.',
        46000,
        5,
        'GENERAL',
        0,
        'ACTIVE'

    UNION ALL

    -- 레드벨벳: 재고 1개 경계값 확인용
    SELECT
        '레드벨벳 크림치즈 케이크',
        '촉촉한 레드벨벳 시트와 진한 크림치즈 프로스팅을 사용했습니다.',
        43000,
        1,
        'GENERAL',
        0,
        'ACTIVE'

    UNION ALL

    -- 레몬 치즈케이크: 재고가 넉넉한 일반 상품
    SELECT
        '레몬 바스크 치즈케이크',
        '상큼한 레몬 향을 더해 구운 진한 바스크 치즈케이크입니다.',
        36000,
        15,
        'GENERAL',
        0,
        'ACTIVE'

    UNION ALL

    -- 흑임자 케이크: 재고 부족 상태 확인용
    SELECT
        '흑임자 인절미 케이크',
        '고소한 흑임자 크림과 쫄깃한 인절미를 조합한 케이크입니다.',
        41000,
        2,
        'GENERAL',
        0,
        'ACTIVE'

    UNION ALL

    -- 복숭아 케이크: 상품명 검색과 일반 재고 확인용
    SELECT
        '복숭아 우유 생크림 케이크',
        '달콤한 복숭아와 담백한 우유 생크림을 사용한 케이크입니다.',
        44000,
        7,
        'GENERAL',
        0,
        'ACTIVE'

    UNION ALL

    -- 밤 케이크: 일반 상품 품절 필터 확인용
    SELECT
        '몽블랑 밤 케이크',
        '국산 밤 페이스트와 부드러운 생크림을 층층이 올린 케이크입니다.',
        48000,
        0,
        'GENERAL',
        0,
        'ACTIVE'

    UNION ALL

    -- 미니 케이크: 최저 가격대 확인용
    SELECT
        '미니 도시락 케이크',
        '한두 명이 가볍게 즐기기 좋은 작은 크기의 케이크입니다.',
        18000,
        25,
        'GENERAL',
        0,
        'ACTIVE'

    UNION ALL

    -- 10만원 상품: 기본 최대 가격 경계값 포함 확인용
    SELECT
        '프리미엄 3단 기념일 케이크',
        '특별한 기념일을 위한 화려한 3단 케이크입니다.',
        100000,
        1,
        'GENERAL',
        0,
        'ACTIVE'

    UNION ALL

    -- 10만원 초과 상품: 기본 최대 가격 필터 제외 확인용
    SELECT
        '럭셔리 웨딩 케이크',
        '웨딩 행사를 위한 대형 수제 장식 케이크입니다.',
        120000,
        NULL,
        'CUSTOM',
        14,
        'ACTIVE'

    UNION ALL

    -- 포토 케이크: 주문 제작과 무제한 재고 확인용
    SELECT
        '포토 이미지 주문 제작 케이크',
        '고객이 전달한 사진을 식용 이미지로 제작하는 맞춤 케이크입니다.',
        62000,
        NULL,
        'CUSTOM',
        4,
        'ACTIVE'

    UNION ALL

    -- 캐릭터 케이크: 주문 제작 검색 및 인기순 확인용
    SELECT
        '캐릭터 입체 주문 제작 케이크',
        '원하는 캐릭터를 입체 장식으로 표현하는 주문 제작 케이크입니다.',
        75000,
        NULL,
        'CUSTOM',
        5,
        'ACTIVE'

    UNION ALL

    -- 2단 주문 제작: 높은 가격대의 주문 제작 상품
    SELECT
        '2단 파티 주문 제작 케이크',
        '여러 명이 함께 즐길 수 있는 2단 구성의 파티용 맞춤 케이크입니다.',
        90000,
        NULL,
        'CUSTOM',
        7,
        'ACTIVE'

    UNION ALL

    -- 돌잔치 케이크: 긴 준비 기간 확인용
    SELECT
        '돌잔치 한복 주문 제작 케이크',
        '한복과 전통 문양을 표현한 돌잔치용 주문 제작 케이크입니다.',
        85000,
        NULL,
        'CUSTOM',
        6,
        'ACTIVE'

    UNION ALL

    -- 반려동물 케이크: 키워드 검색 확인용
    SELECT
        '반려동물 초상화 주문 제작 케이크',
        '반려동물 사진을 바탕으로 얼굴을 그려 넣는 맞춤 케이크입니다.',
        68000,
        NULL,
        'CUSTOM',
        4,
        'ACTIVE'

    UNION ALL

    -- 기업 행사 케이크: 주문 제작 상품의 다양한 가격 확인용
    SELECT
        '기업 로고 주문 제작 케이크',
        '기업 로고와 행사 문구를 반영하는 단체 행사 주문 제작 케이크입니다.',
        78000,
        NULL,
        'CUSTOM',
        3,
        'ACTIVE'

    UNION ALL

    -- 주문 제작 품절: CUSTOM 유형에도 재고가 지정된 예외 상황 확인용
    SELECT
        '플라워 데코 주문 제작 케이크',
        '수제 플라워 장식을 올리는 기간 한정 주문 제작 케이크입니다.',
        72000,
        0,
        'CUSTOM',
        5,
        'ACTIVE'

    UNION ALL

    -- 쌀 케이크: 일반 상품 검색 확인용
    SELECT
        '백설기 생화 케이크',
        '담백한 백설기 위에 생화 느낌의 장식을 올린 케이크입니다.',
        52000,
        6,
        'GENERAL',
        0,
        'ACTIVE'

    UNION ALL

    -- 판매 중지 상품: 고객 화면에 노출되지 않아야 한다.
    SELECT
        '블루베리 요거트 케이크',
        '블루베리와 요거트 크림을 사용한 상큼한 케이크입니다.',
        37000,
        6,
        'GENERAL',
        0,
        'INACTIVE'

    UNION ALL

    -- 두 번째 판매 중지 상품: 필터와 고객 비노출 확인용
    SELECT
        '단종 체리 초콜릿 케이크',
        '판매가 종료되어 고객 상품 목록에 노출되지 않는 케이크입니다.',
        47000,
        4,
        'GENERAL',
        0,
        'INACTIVE'
) sample
WHERE c.`code` = 'CAKE'
  AND NOT EXISTS (
      SELECT 1
      FROM `products` p
      WHERE p.`category_id` = c.`id`
        AND p.`name` = sample.`name`
  );

-- =========================================================
-- 상품 상세 화면 옵션 그룹
--
-- 상품명과 옵션 그룹명이 같은 데이터가 있으면 중복 등록하지 않는다.
-- =========================================================

INSERT INTO `product_option_groups` (
    `product_id`,
    `name`,
    `required`,
    `selection_type`,
    `sort_order`
)
SELECT
    p.`id`,
    sample.`group_name`,
    sample.`required`,
    sample.`selection_type`,
    sample.`sort_order`
FROM `products` p
INNER JOIN (
    SELECT
        '레터링 생크림 케이크' AS `product_name`,
        '케이크 크기' AS `group_name`,
        1 AS `required`,
        'SINGLE' AS `selection_type`,
        1 AS `sort_order`

    UNION ALL

    SELECT
        '레터링 생크림 케이크',
        '크림 색상',
        1,
        'SINGLE',
        2

    UNION ALL

    SELECT
        '캐릭터 입체 주문 제작 케이크',
        '케이크 크기',
        1,
        'SINGLE',
        1

    UNION ALL

    SELECT
        '캐릭터 입체 주문 제작 케이크',
        '추가 장식',
        0,
        'MULTIPLE',
        2
) sample
        ON sample.`product_name` = p.`name`
WHERE NOT EXISTS (
    SELECT 1
    FROM `product_option_groups` pog
    WHERE pog.`product_id` = p.`id`
      AND pog.`name` = sample.`group_name`
);


-- =========================================================
-- 상품 상세 화면 개별 옵션
--
-- 같은 옵션 그룹 안에 같은 옵션명이 있으면 중복 등록하지 않는다.
-- =========================================================

INSERT INTO `product_options` (
    `option_group_id`,
    `name`,
    `additional_price`,
    `status`,
    `sort_order`
)
SELECT
    pog.`id`,
    sample.`option_name`,
    sample.`additional_price`,
    sample.`status`,
    sample.`sort_order`
FROM `products` p
INNER JOIN `product_option_groups` pog
        ON pog.`product_id` = p.`id`
INNER JOIN (
    SELECT
        '레터링 생크림 케이크' AS `product_name`,
        '케이크 크기' AS `group_name`,
        '1호' AS `option_name`,
        0 AS `additional_price`,
        'ACTIVE' AS `status`,
        1 AS `sort_order`

    UNION ALL

    SELECT
        '레터링 생크림 케이크',
        '케이크 크기',
        '2호',
        15000,
        'ACTIVE',
        2

    UNION ALL

    SELECT
        '레터링 생크림 케이크',
        '크림 색상',
        '화이트',
        0,
        'ACTIVE',
        1

    UNION ALL

    SELECT
        '레터링 생크림 케이크',
        '크림 색상',
        '핑크',
        0,
        'ACTIVE',
        2

    UNION ALL

    SELECT
        '레터링 생크림 케이크',
        '크림 색상',
        '하늘색',
        0,
        'ACTIVE',
        3

    UNION ALL

    SELECT
        '캐릭터 입체 주문 제작 케이크',
        '케이크 크기',
        '2호',
        0,
        'ACTIVE',
        1

    UNION ALL

    SELECT
        '캐릭터 입체 주문 제작 케이크',
        '케이크 크기',
        '3호',
        18000,
        'ACTIVE',
        2

    UNION ALL

    SELECT
        '캐릭터 입체 주문 제작 케이크',
        '추가 장식',
        '별 장식',
        3000,
        'ACTIVE',
        1

    UNION ALL

    SELECT
        '캐릭터 입체 주문 제작 케이크',
        '추가 장식',
        '하트 장식',
        3000,
        'ACTIVE',
        2

    UNION ALL

    -- 비활성 옵션이 고객 상세에 노출되지 않는지 확인한다.
    SELECT
        '캐릭터 입체 주문 제작 케이크',
        '추가 장식',
        '단종 왕관 장식',
        5000,
        'INACTIVE',
        3
) sample
        ON sample.`product_name` = p.`name`
       AND sample.`group_name` = pog.`name`
WHERE NOT EXISTS (
    SELECT 1
    FROM `product_options` po
    WHERE po.`option_group_id` = pog.`id`
      AND po.`name` = sample.`option_name`
);

COMMIT;
