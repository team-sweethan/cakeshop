# 쿠폰 스키마

- 담당: 정후
- 테이블: `coupons`, `member_coupons`
- 정본: Flyway migration 적용 결과

## `coupons`

쿠폰 정책, 수량과 발급 대상을 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 쿠폰 식별자 |
| `name` | VARCHAR(100) |  | X | 없음 | 쿠폰명 |
| `discount_type` | VARCHAR(30) |  | X | 없음 | 할인 유형 |
| `discount_value` | DECIMAL(12, 2) |  | X | 없음 | 할인 값 |
| `minimum_order_amount` | DECIMAL(12, 0) |  | X | `0` | 최소 주문 금액 |
| `maximum_discount_amount` | DECIMAL(12, 0) |  | O | NULL | 최대 할인 금액 |
| `total_quantity` | INT UNSIGNED |  | O | NULL | 총 발급 가능 수량 |
| `issued_quantity` | INT UNSIGNED |  | X | `0` | 발급 수량 |
| `starts_at` | DATETIME(6) |  | X | 없음 | 사용 시작 시각 |
| `expires_at` | DATETIME(6) |  | X | 없음 | 만료 시각 |
| `status` | VARCHAR(30) |  | X | `'ACTIVE'` | 쿠폰 상태 |
| `target_type` | VARCHAR(30) |  | X | `'SPECIFIC_MEMBERS'` | 발급 대상 유형 |
| `created_by` | BIGINT | FK | X | 없음 | 생성 회원 식별자 |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |
| `updated_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 수정 시각, 수정 시 자동 갱신 |

- FK: `fk_coupons_creator` — `created_by` → `members.id`
- CHECK: `target_type IN ('ALL_MEMBERS', 'NEW_MEMBERS', 'FIRST_ORDER', 'BIRTHDAY', 'SPECIFIC_MEMBERS')`
- CHECK: 총 수량이 있으면 `issued_quantity <= total_quantity`
- CHECK: 특정 회원 대상은 총 수량 필수, 그 외 대상은 총 수량 NULL

## `member_coupons`

회원에게 발급된 쿠폰과 주문 적용 상태를 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 회원 쿠폰 식별자 |
| `coupon_id` | BIGINT | FK, UK | X | 없음 | 쿠폰 식별자 |
| `member_id` | BIGINT | FK, UK | X | 없음 | 회원 식별자 |
| `status` | VARCHAR(30) |  | X | `'AVAILABLE'` | 회원 쿠폰 상태 |
| `applied_order_id` | BIGINT | FK, UK | O | NULL | 적용 주문 식별자 |
| `issued_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 발급 시각 |
| `used_at` | DATETIME(6) |  | O | NULL | 사용 시각 |

- UK: `uk_member_coupons_coupon_member` (`coupon_id`, `member_id`)
- UK: `uk_member_coupons_applied_order` (`applied_order_id`)
- FK: `coupon_id` → `coupons.id`, `member_id` → `members.id`, `applied_order_id` → `orders.id`

## 관련 migration

- `V0__initial_schema.sql`
- `V20260804_094624__add_coupon_target_type.sql`
