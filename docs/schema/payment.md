# 결제 스키마

- 담당: 주환
- 테이블: `payments`, `payment_cancellations`
- 정본: Flyway migration 적용 결과

## `payments`

주문의 결제 요청·승인 결과와 결제사 응답을 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 결제 식별자 |
| `order_id` | BIGINT | FK | X | 없음 | 주문 식별자 |
| `toss_order_id` | VARCHAR(100) | UK | X | 없음 | Toss 주문 식별자 |
| `payment_key` | VARCHAR(200) | UK | O | NULL | 결제사 결제 키 |
| `idempotency_key` | VARCHAR(100) | UK | X | 없음 | 결제 요청 멱등 키 |
| `method` | VARCHAR(30) |  | O | NULL | 결제 수단 |
| `amount` | DECIMAL(12, 0) |  | X | 없음 | 결제 금액 |
| `status` | VARCHAR(30) | INDEX | X | `'READY'` | 결제 상태 |
| `provider_status` | VARCHAR(50) |  | O | NULL | 결제사 상태 |
| `active_payment_order_id` | BIGINT | GEN, UK | O | 계산값 | `READY` 또는 `DONE`이면 `order_id`, 아니면 NULL |
| `failure_code` | VARCHAR(100) |  | O | NULL | 실패 코드 |
| `failure_message` | VARCHAR(500) |  | O | NULL | 실패 메시지 |
| `requested_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 요청 시각 |
| `approved_at` | DATETIME(6) |  | O | NULL | 승인 시각 |
| `canceled_at` | DATETIME(6) |  | O | NULL | 취소 시각 |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |
| `updated_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 수정 시각, 수정 시 자동 갱신 |

- UK: `toss_order_id`, `payment_key`, `idempotency_key` 각각 개별 유니크
- UK: `uk_payments_active_order` (`active_payment_order_id`) — 주문별 진행/완료 결제 1건 제한
- FK: `fk_payments_order` — `order_id` → `orders.id`
- CHECK: `status IN ('READY', 'DONE', 'CANCELED', 'PARTIAL_CANCELED', 'ABORTED', 'EXPIRED')`
- CHECK: `amount >= 0`
- INDEX: `idx_payments_status` (`status`)

## `payment_cancellations`

결제 취소 요청과 처리 결과를 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 결제 취소 식별자 |
| `payment_id` | BIGINT | FK | X | 없음 | 결제 식별자 |
| `idempotency_key` | VARCHAR(100) | UK | X | 없음 | 취소 요청 멱등 키 |
| `cancel_amount` | DECIMAL(12, 0) |  | X | 없음 | 취소 금액 |
| `cancel_reason` | VARCHAR(500) |  | X | 없음 | 취소 사유 |
| `request_type` | VARCHAR(30) |  | O | NULL | 취소 요청 유형 |
| `requested_by` | BIGINT | FK, INDEX | O | NULL | 요청 회원 식별자 |
| `status` | VARCHAR(30) | INDEX | X | `'REQUESTED'` | 취소 상태 |
| `transaction_key` | VARCHAR(200) | UK | O | NULL | 취소 거래 키 |
| `failure_code` | VARCHAR(100) |  | O | NULL | 실패 코드 |
| `failure_message` | VARCHAR(500) |  | O | NULL | 실패 메시지 |
| `requested_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 요청 시각 |
| `canceled_at` | DATETIME(6) |  | O | NULL | 취소 완료 시각 |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |
| `active_requested_payment_id` | BIGINT | GEN, UK | O | 계산값 | `REQUESTED`이면 `payment_id`, 아니면 NULL |
| `updated_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 수정 시각, 수정 시 자동 갱신 |

- UK: `idempotency_key`, `transaction_key` 각각 개별 유니크
- UK: `uk_payment_cancellations_active_requested` (`active_requested_payment_id`)
- FK: `payment_id` → `payments.id`, `requested_by` → `members.id`
- CHECK: `status IN ('REQUESTED', 'DONE', 'FAILED')`
- CHECK: `cancel_amount > 0`
- INDEX: `idx_payment_cancellations_status` (`status`)
- INDEX: `idx_payment_cancellations_requested_by` (`requested_by`)

## 관련 migration

- `V0__initial_schema.sql`
- `V20260729_184356__align_order_payment_schema.sql`
- `V20260730_170822__add_payment_request_guards.sql`
- `V20260731_091629__unify_active_payment_guard.sql`
