# 상품 스키마

- 담당: 시은
- 테이블: `categories`, `products`, `product_option_groups`, `product_options`, `product_images`
- 정본: Flyway migration 적용 결과

## `categories`

상품 카테고리를 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 카테고리 식별자 |
| `code` | VARCHAR(50) | UK | X | 없음 | 카테고리 코드 |
| `name` | VARCHAR(100) |  | X | 없음 | 카테고리명 |
| `sort_order` | INT |  | X | `0` | 노출 순서 |
| `is_active` | TINYINT(1) |  | X | `1` | 활성 여부 |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |
| `updated_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 수정 시각, 수정 시 자동 갱신 |

- UK: `uk_categories_code` (`code`)

## `products`

상품 기본 정보, 가격, 재고와 리뷰 집계값을 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 상품 식별자 |
| `category_id` | BIGINT | FK | X | 없음 | 카테고리 식별자 |
| `name` | VARCHAR(150) |  | X | 없음 | 상품명 |
| `description` | TEXT |  | O | NULL | 상품 설명 |
| `base_price` | DECIMAL(12, 0) |  | X | 없음 | 기본 가격 |
| `stock_quantity` | INT UNSIGNED |  | O | NULL | 재고 수량, NULL은 재고 제한 없음 |
| `product_type` | VARCHAR(30) |  | X | 없음 | 상품 유형 |
| `preparation_days` | SMALLINT UNSIGNED |  | X | `0` | 준비 기간(일) |
| `status` | VARCHAR(30) |  | X | `'ACTIVE'` | 상품 상태 |
| `average_rating` | DECIMAL(3, 2) |  | X | `0.00` | 평균 리뷰 평점 |
| `review_count` | INT UNSIGNED |  | X | `0` | 리뷰 수 |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |
| `updated_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 수정 시각, 수정 시 자동 갱신 |

- FK: `fk_products_category` — `category_id` → `categories.id`
- CHECK: `chk_products_preparation_days_by_type` — 일반 상품은 준비 기간 0일, 주문 제작 상품은 1일 이상

## `product_option_groups`

상품별 옵션 그룹과 선택 정책을 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 옵션 그룹 식별자 |
| `product_id` | BIGINT | FK | X | 없음 | 상품 식별자 |
| `name` | VARCHAR(100) |  | X | 없음 | 옵션 그룹명 |
| `required` | TINYINT(1) |  | X | `0` | 필수 선택 여부 |
| `selection_type` | VARCHAR(30) |  | X | 없음 | 선택 방식 |
| `status` | VARCHAR(20) |  | X | `'ACTIVE'` | 옵션 그룹 상태 |
| `sort_order` | INT |  | X | `0` | 노출 순서 |

- FK: `fk_product_option_groups_product` — `product_id` → `products.id`
- CHECK: `chk_product_option_groups_status` — `status IN ('ACTIVE', 'INACTIVE')`

## `product_options`

옵션 그룹의 선택 항목과 추가 금액을 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 상품 옵션 식별자 |
| `option_group_id` | BIGINT | FK | X | 없음 | 옵션 그룹 식별자 |
| `name` | VARCHAR(100) |  | X | 없음 | 옵션명 |
| `additional_price` | DECIMAL(12, 0) |  | X | `0` | 추가 금액 |
| `status` | VARCHAR(30) |  | X | `'ACTIVE'` | 옵션 상태 |
| `sort_order` | INT |  | X | `0` | 노출 순서 |

- FK: `fk_product_options_group` — `option_group_id` → `product_option_groups.id`
- CHECK: `chk_product_options_status` — `status IN ('ACTIVE', 'INACTIVE')`

## `product_images`

상품 이미지를 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 이미지 식별자 |
| `product_id` | BIGINT | FK | X | 없음 | 상품 식별자 |
| `image_url` | VARCHAR(500) |  | X | 없음 | 이미지 URL |
| `sort_order` | INT |  | X | `0` | 노출 순서 |

- FK: `fk_product_images_product` — `product_id` → `products.id`

## 관련 migration

- `V0__initial_schema.sql`
- `V1__add_product_stock.sql`
- `V20260729_152154__add_product_option_group_status.sql`
- `V20260730_123931__apply_product_preparation_policy.sql`
