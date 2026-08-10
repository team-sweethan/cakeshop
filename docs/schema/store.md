# 매장 스키마

- 담당: 공통 협의
- 테이블: `store`, `store_business_hour`, `store_holiday`
- 정본: Flyway migration 적용 결과

## `store`

픽업 매장의 기본 정보와 공통 픽업 운영 시간을 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 매장 식별자 |
| `name` | VARCHAR(100) |  | X | 없음 | 매장명 |
| `description` | TEXT |  | O | NULL | 매장 설명 |
| `image_url` | VARCHAR(500) |  | O | NULL | 매장 이미지 URL |
| `address` | VARCHAR(500) |  | X | 없음 | 주소 |
| `phone` | VARCHAR(30) |  | X | 없음 | 전화번호 |
| `pickup_place` | VARCHAR(200) |  | X | 없음 | 픽업 장소 안내 |
| `pickup_start_time` | TIME |  | X | 없음 | 픽업 시작 시각 |
| `pickup_end_time` | TIME |  | X | 없음 | 픽업 종료 시각 |
| `pickup_interval_minutes` | SMALLINT UNSIGNED |  | X | 없음 | 픽업 간격(분) |
| `created_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 생성 시각 |
| `updated_at` | DATETIME(6) |  | X | `CURRENT_TIMESTAMP(6)` | 수정 시각, 수정 시 자동 갱신 |

- CHECK: `pickup_start_time < pickup_end_time`
- CHECK: `pickup_interval_minutes BETWEEN 10 AND 180`
- 기준 데이터: ID 1의 기본 매장을 migration에서 없는 경우 생성한다.

## `store_business_hour`

매장의 요일별 영업시간과 휴무 여부를 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 영업시간 식별자 |
| `store_id` | BIGINT | FK, UK | X | 없음 | 매장 식별자 |
| `day_of_week` | VARCHAR(10) | UK | X | 없음 | 요일 |
| `open_time` | TIME |  | O | NULL | 영업 시작 시각 |
| `close_time` | TIME |  | O | NULL | 영업 종료 시각 |
| `is_closed` | TINYINT(1) |  | X | `0` | 휴무 여부 |

- UK: `uk_store_business_hour_day` (`store_id`, `day_of_week`)
- FK: `fk_store_business_hour_store` — `store_id` → `store.id`
- CHECK: 요일은 월요일부터 일요일까지의 영문 상수만 허용한다.
- CHECK: 휴무일은 시작·종료 시각이 NULL이고, 영업일은 두 시각이 존재하며 시작 시각이 더 빨라야 한다.
- 기준 데이터: 기본 매장의 월요일~일요일 영업시간을 migration에서 없는 경우 생성한다.

## `store_holiday`

매장의 날짜별 임시 휴무를 저장한다.

| 컬럼 | 타입 | 키 | Null | 기본값 | 의미 |
|---|---|---|---|---|---|
| `id` | BIGINT | PK | X | AUTO_INCREMENT | 휴무 식별자 |
| `store_id` | BIGINT | FK, UK | X | 없음 | 매장 식별자 |
| `holiday_date` | DATE | UK | X | 없음 | 휴무 날짜 |
| `reason` | VARCHAR(255) |  | O | NULL | 휴무 사유 |

- UK: `uk_store_holiday_date` (`store_id`, `holiday_date`)
- FK: `fk_store_holiday_store` — `store_id` → `store.id`

## 관련 migration

- `V0__initial_schema.sql`
- `V20260729_003452__provision_default_store.sql`
