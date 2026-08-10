# 리뷰 스키마

- 담당: 현규
- 테이블: `reviews`, `review_images`, `review_replies`
- 정본: Flyway migration 적용 결과

## `reviews`

주문 항목별 상품 리뷰와 세부 평점을 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 리뷰 식별자 |
| `order_item_id` | BIGINT | FK, UK | X | 없음 | 주문 항목 식별자 |
| `product_id` | BIGINT | FK | X | 없음 | 상품 식별자 |
| `member_id` | BIGINT | FK | X | 없음 | 작성 회원 식별자 |
| `overall_rating` | TINYINT UNSIGNED |  | X | 없음 | 종합 평점 |
| `taste_rating` | TINYINT UNSIGNED |  | X | 없음 | 맛 평점 |
| `design_rating` | TINYINT UNSIGNED |  | X | 없음 | 디자인 평점 |
| `service_rating` | TINYINT UNSIGNED |  | X | 없음 | 서비스 평점 |
| `content` | TEXT |  | O | NULL | 리뷰 내용 |
| `status` | VARCHAR(30) |  | X | `'PUBLISHED'` | 리뷰 상태 |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |
| `updated_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 수정 시각, 수정 시 자동 갱신 |

- UK: `uk_reviews_order_item` (`order_item_id`)
- FK: `order_item_id` → `order_items.id`, `product_id` → `products.id`, `member_id` → `members.id`
- CHECK: `status IN ('PUBLISHED', 'DELETED', 'BLOCKED')`
- CHECK: 네 평점은 각각 `BETWEEN 1 AND 5`

## `review_images`

리뷰 첨부 이미지를 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 이미지 식별자 |
| `review_id` | BIGINT | FK | X | 없음 | 리뷰 식별자 |
| `image_url` | VARCHAR(500) |  | X | 없음 | 이미지 URL |
| `sort_order` | INT |  | X | `0` | 노출 순서 |

- FK: `fk_review_images_review` — `review_id` → `reviews.id`

## `review_replies`

리뷰에 대한 관리자 답글을 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 답글 식별자 |
| `review_id` | BIGINT | FK, UK | X | 없음 | 리뷰 식별자 |
| `admin_id` | BIGINT | FK | X | 없음 | 작성 관리자 회원 식별자 |
| `content` | TEXT |  | X | 없음 | 답글 내용 |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |
| `updated_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 수정 시각, 수정 시 자동 갱신 |

- UK: `uk_review_replies_review` (`review_id`)
- FK: `review_id` → `reviews.id`, `admin_id` → `members.id`

## 관련 migration

- `V0__initial_schema.sql`
- `V20260806_075114__add_review_status_and_rating_constraints.sql`
