# 통계 스키마

- 담당: 시은
- 물리 테이블: 3개
- 성격: 여러 소유 도메인의 원본을 읽어 재생성 가능한 파생 집계 데이터를 관리한다.

## `daily_statistics`

날짜별 주문·매출 집계 결과를 저장한다. `statistics_date`당 한 행이며 원본 데이터가 없는 집계 완료
날짜도 지표가 `0`인 행을 저장한다.

| 컬럼 | 타입 | Null | 기본값 | 키·속성 |
|---|---|---|---|---|
| `statistics_date` | `DATE` | X | 없음 | PK |
| `total_order_count` | `BIGINT` | X | `0` | CHECK |
| `completed_order_count` | `BIGINT` | X | `0` | CHECK |
| `canceled_order_count` | `BIGINT` | X | `0` | CHECK |
| `total_sales_amount` | `DECIMAL(18, 0)` | X | `0` | CHECK |
| `aggregated_at` | `DATETIME(6)` | X | 없음 |  |
| `product_aggregated_at` | `DATETIME(6)` | O | `NULL` | 상품별 집계 완료 시각 |

### 제약조건

- `chk_daily_statistics_counts`: 주문 건수 지표는 모두 `0` 이상이다.
- `chk_daily_statistics_sales_amount`: 총매출은 `0` 이상이다.

## `daily_product_statistics`

날짜와 상품별 주문·판매·매출 집계 결과를 저장한다. 상품 활동이 없는 날짜에는 행을 생성하지 않으며,
해당 날짜의 상품별 집계 완료 여부는 `daily_statistics.product_aggregated_at`으로 판단한다.

| 컬럼 | 타입 | Null | 기본값 | 키·속성 |
|---|---|---|---|---|
| `statistics_date` | `DATE` | X | 없음 | PK |
| `product_id` | `BIGINT` | X | 없음 | PK |
| `product_name` | `VARCHAR(150)` | X | 없음 | 상품명 스냅샷 |
| `order_count` | `BIGINT` | X | `0` | CHECK |
| `sales_quantity` | `BIGINT` | X | `0` | CHECK |
| `sales_amount` | `DECIMAL(18, 0)` | X | `0` | CHECK |

### 제약조건

- 기본 키: (`statistics_date`, `product_id`)
- `chk_daily_product_statistics_counts`: 주문 건수와 판매 수량은 `0` 이상이다.
- `chk_daily_product_statistics_sales_amount`: 상품별 매출액은 `0` 이상이다.
- `product_id`는 파생 집계의 원본 식별값이며 상품 원본의 수명 주기와 결합하는 FK는 두지 않는다.

## `statistics_batch_runs`

정규 일별 집계, 초기 백필과 운영자 수동 재집계의 실행 상태·범위·진행 상황을 기록한다.

| 컬럼 | 타입 | Null | 기본값 | 키·속성 |
|---|---|---|---|---|
| `id` | `BIGINT` | X | 자동 증가 | PK |
| `batch_type` | `VARCHAR(20)` | X | 없음 | CHECK |
| `status` | `VARCHAR(20)` | X | 없음 | CHECK |
| `source_window_started_at` | `DATETIME(6)` | O | `NULL` | CHECK |
| `source_window_ended_at` | `DATETIME(6)` | O | `NULL` | CHECK |
| `target_start_date` | `DATE` | X | 없음 | CHECK |
| `target_end_date` | `DATE` | X | 없음 | CHECK |
| `last_completed_date` | `DATE` | O | `NULL` | CHECK |
| `started_at` | `DATETIME(6)` | X | `CURRENT_TIMESTAMP(6)` |  |
| `heartbeat_at` | `DATETIME(6)` | X | `CURRENT_TIMESTAMP(6)` |  |
| `completed_at` | `DATETIME(6)` | O | `NULL` | CHECK |
| `running_lock` | `TINYINT` | O | 생성값 | GEN, UK |

### 제약조건

- `uk_statistics_batch_runs_running`: `RUNNING` 실행을 하나만 허용한다.
- `chk_statistics_batch_runs_type`: `DAILY`, `BACKFILL`, `REBUILD`만 허용한다.
- `chk_statistics_batch_runs_status`: `RUNNING`, `SUCCEEDED`, `FAILED`만 허용한다.
- `chk_statistics_batch_runs_target_range`: 대상 시작일은 종료일보다 늦을 수 없다.
- `chk_statistics_batch_runs_source_window`: 원본 변경 탐색 시각은 둘 다 없거나 유효한 범위여야 한다.
- `chk_statistics_batch_runs_type_fields`: `DAILY`는 진행일을 사용하지 않고 `BACKFILL`, `REBUILD`는
  원본 변경 탐색 시각을 사용하지 않는다.
- `chk_statistics_batch_runs_progress`: 진행일은 대상 기간 안에 있어야 한다.
- `chk_statistics_batch_runs_completion`: 실행 상태와 완료 시각의 존재 여부가 일치해야 한다.

### 인덱스

- `idx_statistics_batch_runs_daily_watermark`
  (`batch_type`, `status`, `source_window_ended_at`)
- `idx_statistics_batch_runs_backfill_history` (`batch_type`, `id`)

## 변경 원칙

- 집계 결과는 원본 도메인의 데이터를 변경하는 근거로 사용하지 않는다.
- ReadModel 원본 조회와 통계 집계 테이블 쓰기는 별도 Mapper로 분리한다.
- ReadModel이 읽는 테이블이나 집계 기준을 주요하게 변경할 때 해당 테이블 담당자의 확인을 받는다.
- 원본 테이블의 컬럼·제약조건은 이 문서에 복제하지 않고 각 도메인 문서를 참조한다.

## 관련 migration

- `V20260810_163646__create_statistics_aggregation_tables.sql`
- `V20260811_101818__add_statistics_rebuild_batch_type.sql`
- `V20260811_163316__add_daily_product_statistics.sql`

## 관련 문서

- [회원](member.md)
- [주문](order.md)
- [결제](payment.md)
- [상품](product.md)
- [쿠폰](coupon.md)
- [커뮤니티](community.md)
- [리뷰](review.md)
