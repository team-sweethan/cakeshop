# 장바구니 스키마

- 담당: 수민
- 테이블: `carts`, `cart_items`, `cart_item_options`, `cart_item_images`
- 정본: Flyway migration 적용 결과

## `carts`

회원별 장바구니를 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 장바구니 식별자 |
| `member_id` | BIGINT | FK, UK | X | 없음 | 회원 식별자 |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |
| `updated_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 수정 시각, 수정 시 자동 갱신 |

- UK: `uk_carts_member` (`member_id`)
- FK: `fk_carts_member` — `member_id` → `members.id`

## `cart_items`

장바구니에 담긴 상품과 수량·요청사항을 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 장바구니 항목 식별자 |
| `cart_id` | BIGINT | FK | X | 없음 | 장바구니 식별자 |
| `product_id` | BIGINT | FK | X | 없음 | 상품 식별자 |
| `quantity` | INT UNSIGNED |  | X | `1` | 수량 |
| `requirements` | TEXT |  | O | NULL | 요청사항 |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |
| `updated_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 수정 시각, 수정 시 자동 갱신 |

- FK: `fk_cart_items_cart` — `cart_id` → `carts.id`
- FK: `fk_cart_items_product` — `product_id` → `products.id`

## `cart_item_options`

장바구니 항목에 선택된 상품 옵션과 가격 스냅샷을 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 선택 옵션 식별자 |
| `cart_item_id` | BIGINT | FK, UK | X | 없음 | 장바구니 항목 식별자 |
| `product_option_id` | BIGINT | FK, UK | X | 없음 | 상품 옵션 식별자 |
| `option_name` | VARCHAR(100) |  | X | 없음 | 옵션명 스냅샷 |
| `additional_price` | DECIMAL(12, 0) |  | X | `0` | 추가 금액 스냅샷 |

- UK: `uk_cart_item_options_item_option` (`cart_item_id`, `product_option_id`)
- FK: `fk_cart_item_options_item` — `cart_item_id` → `cart_items.id`
- FK: `fk_cart_item_options_product_option` — `product_option_id` → `product_options.id`

## `cart_item_images`

장바구니 항목에 첨부된 이미지를 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 이미지 식별자 |
| `cart_item_id` | BIGINT | FK | X | 없음 | 장바구니 항목 식별자 |
| `image_url` | VARCHAR(500) |  | X | 없음 | 이미지 URL |
| `sort_order` | INT |  | X | `0` | 노출 순서 |

- FK: `fk_cart_item_images_item` — `cart_item_id` → `cart_items.id`

## 관련 migration

- `V0__initial_schema.sql`
