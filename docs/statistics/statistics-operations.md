# 통계 집계 운영 가이드

초기 통계 백필과 기간별 수동 재집계의 실행·확인 절차를 정리한다. 집계 기준과 업무 규칙은
[`statistics-spec.md`](statistics-spec.md)가 정본이다.

## 실행 전 확인

- 실행 대상 DB에 필요한 Flyway migration이 적용되어 있어야 한다.
- `local` 프로필은 애플리케이션 시작 시 Flyway migration을 자동 적용한다.
- `rds` 프로필은 Flyway가 비활성화되어 있으므로 승인된 별도 스키마 반영 절차로 migration을 먼저
  적용한다. REBUILD 실행 전에는 `V20260811_101818__add_statistics_rebuild_batch_type.sql` 적용 여부를
  확인하고, 상품별 통계 재집계 전에는
  `V20260811_163316__add_daily_product_statistics.sql` 적용 여부도 확인한다. 활동·금액 지표 재집계
  전에는 기타 지표 컬럼과 원본 조회 인덱스를 추가하는 migration 적용 여부도 확인한다.
- 초기 백필과 수동 재집계는 동시에 활성화하지 않는다.
- 정기 일별 집계, 초기 백필과 수동 재집계는 하나의 실행 잠금을 공유한다.
- 오늘 통계는 집계하지 않으며 모든 날짜는 `Asia/Seoul` 기준이다.
- 로컬 `bootRun`의 `--args` 값은 줄바꿈 없이 한 줄로 전달한다.

## 상품별 통계 도입 시 필수 재집계

상품별 통계 migration은 기존 `daily_statistics.product_aggregated_at`을 `NULL`로 유지한다. 실제 상품별
집계 없이 완료 시각만 기록하지 않으며, migration 실행 중 원본 전체 기간을 조회하지 않는다.

기존 집계일은 애플리케이션 시작 시 자동 따라잡기 대상이 아니므로 다음 배포 절차로 직접 재집계한다.

1. 실행 대상 DB에 `V20260811_163316__add_daily_product_statistics.sql`을 적용한다.
2. 상품별 통계 집계 기능이 포함된 애플리케이션 버전을 배포한다.
3. 아래 SQL로 재집계할 최초일과 최종일을 확인한다.
4. 해당 전체 기간을 366일 이하의 겹치지 않는 연속 구간으로 나누어 이 문서의 REBUILD 명령을 실행한다.
5. 모든 구간이 성공한 뒤 아래 완료 확인 SQL의 결과가 `0`인지 확인한다.

```sql
SELECT
    MIN(statistics_date) AS rebuild_start_date,
    MAX(statistics_date) AS rebuild_end_date,
    COUNT(*) AS incomplete_date_count
FROM daily_statistics
WHERE product_aggregated_at IS NULL;
```

```sql
SELECT COUNT(*) AS incomplete_date_count
FROM daily_statistics
WHERE product_aggregated_at IS NULL;
```

재집계가 완료되기 전에도 기존 주문·매출 통계는 조회할 수 있지만, `product_aggregated_at IS NULL`인 날짜가
조회 기간에 포함되면 상품별 통계 영역은 미집계 상태로 표시된다.

## 활동·금액 지표 도입 시 필수 재집계

기타 지표 migration은 기존 `daily_statistics.additional_metrics_aggregated_at`을 `NULL`로 유지한다.
실제 활동·금액 지표 집계 없이 완료 시각만 기록하지 않으며, migration 실행 중 원본 전체 기간을
조회하지 않는다.

기존 집계일은 애플리케이션 시작 시 자동 따라잡기 대상이 아니므로 다음 배포 절차로 직접 재집계한다.

1. 실행 대상 DB에 기타 지표 컬럼과 원본 조회 인덱스를 추가하는 migration을 적용한다.
2. 기타 지표 집계 기능이 포함된 애플리케이션 버전을 배포한다.
3. 아래 SQL로 재집계할 최초일과 최종일을 확인한다.
4. 해당 전체 기간을 366일 이하의 겹치지 않는 연속 구간으로 나누어 이 문서의 REBUILD 명령을 실행한다.
5. 모든 구간이 성공한 뒤 아래 완료 확인 SQL에서 누락 및 두 미집계 날짜 수가 모두 `0`인지 확인한다.

```sql
SELECT
    MIN(statistics_source.source_date) AS rebuild_start_date,
    DATE(UTC_TIMESTAMP(6) + INTERVAL 9 HOUR) - INTERVAL 1 DAY AS rebuild_end_date
FROM (
    SELECT statistics_date AS source_date
    FROM daily_statistics

    UNION ALL

    SELECT DATE(created_at)
    FROM orders

    UNION ALL

    SELECT DATE(approved_at)
    FROM payments
    WHERE approved_at IS NOT NULL

    UNION ALL

    SELECT DATE(created_at)
    FROM members

    UNION ALL

    SELECT DATE(withdrawn_at)
    FROM members
    WHERE withdrawn_at IS NOT NULL

    UNION ALL

    SELECT DATE(created_at)
    FROM posts

    UNION ALL

    SELECT DATE(canceled_at)
    FROM payment_cancellations
    WHERE canceled_at IS NOT NULL
) AS statistics_source;
```

```sql
WITH rebuild_bounds AS (
    SELECT
        MIN(statistics_source.source_date) AS start_date,
        DATE(UTC_TIMESTAMP(6) + INTERVAL 9 HOUR) - INTERVAL 1 DAY AS end_date
    FROM (
        SELECT statistics_date AS source_date
        FROM daily_statistics

        UNION ALL

        SELECT DATE(created_at)
        FROM orders

        UNION ALL

        SELECT DATE(approved_at)
        FROM payments
        WHERE approved_at IS NOT NULL

        UNION ALL

        SELECT DATE(created_at)
        FROM members

        UNION ALL

        SELECT DATE(withdrawn_at)
        FROM members
        WHERE withdrawn_at IS NOT NULL

        UNION ALL

        SELECT DATE(created_at)
        FROM posts

        UNION ALL

        SELECT DATE(canceled_at)
        FROM payment_cancellations
        WHERE canceled_at IS NOT NULL
    ) AS statistics_source
)
SELECT
    CASE
        WHEN bounds.start_date IS NULL OR bounds.end_date < bounds.start_date THEN 0
        ELSE GREATEST(
            DATEDIFF(bounds.end_date, bounds.start_date) + 1
                - COUNT(statistics.statistics_date),
            0
        )
    END AS missing_date_count,
    COALESCE(SUM(
        statistics.statistics_date IS NOT NULL
        AND statistics.product_aggregated_at IS NULL
    ), 0) AS incomplete_product_date_count,
    COALESCE(SUM(
        statistics.statistics_date IS NOT NULL
        AND statistics.additional_metrics_aggregated_at IS NULL
    ), 0) AS incomplete_additional_metrics_date_count
FROM rebuild_bounds AS bounds
LEFT JOIN daily_statistics AS statistics
    ON statistics.statistics_date BETWEEN bounds.start_date AND bounds.end_date
GROUP BY bounds.start_date, bounds.end_date;
```

재집계가 완료되기 전에도 기존 주문·매출 통계는 조회할 수 있지만,
`additional_metrics_aggregated_at IS NULL`인 날짜가 조회 기간에 포함되면 활동·금액 지표 영역은
미집계 상태로 표시된다. 상품별 통계와 활동·금액 지표를 함께 도입하면 누락 날짜 수와 두 미집계 날짜
수가 모두 `0`인 것을 확인한 뒤 사용한다. 시작일이 기존 `daily_statistics`의 최초일보다 이르면
REBUILD가 앞쪽 날짜의 `daily_statistics` 행도 새로 생성한다.

## 애플리케이션 시작 시 자동 따라잡기

일반 애플리케이션은 시작 준비가 완료되면 누락된 일별 통계를 한 번 확인한다.

- DB 기준 00:10 이전에 시작하면 자동 따라잡기를 실행하지 않고 당일 00:10 정기 배치에 맡긴다.
- DB 기준 00:10 이상이고 마지막 성공 `DAILY`·`BACKFILL`의 대상 종료일이 어제보다 이전이면 기존
  일별 집계 로직으로 어제까지의 누락 날짜와 원본 변경이 발견된 과거 날짜를 집계한다.
- 마지막 성공 집계의 대상 종료일이 어제 이상이면 다시 시작해도 새 `DAILY` 실행을 만들지 않는다.
- 여러 인스턴스가 동시에 시작해도 실행 잠금 획득 후 누락 여부를 다시 확인하여 먼저 완료된 집계를
  반복하지 않는다.
- 성공한 초기 백필이 없거나 다른 통계 집계가 실행 중이면 집계하지 않는다.
- 초기 백필 또는 REBUILD CLI 모드에서는 자동 따라잡기를 실행하지 않는다.
- 자동 따라잡기가 실패해도 애플리케이션은 요청 처리를 계속하며 오류 로그를 남긴다.
- `DAILY` 실행 기록을 생성한 뒤 집계가 실패하면 해당 실행을 `FAILED`로 기록한다.

자동 따라잡기는 정기 배치를 실행하지 못한 기간을 복구하고, REBUILD는 운영자가 지정한 과거 기간을
명시적으로 다시 계산할 때 사용한다.

가장 최근 자동 또는 정기 일별 집계는 다음 SQL로 확인한다.

```sql
SELECT
    id,
    status,
    target_start_date,
    target_end_date,
    started_at,
    completed_at
FROM statistics_batch_runs
WHERE batch_type = 'DAILY'
ORDER BY id DESC
LIMIT 1;
```

자동 따라잡기 자체에서 예외가 발생하면 로그에서 다음 메시지를 확인한다.

```text
애플리케이션 시작 시 누락된 일별 통계 따라잡기에 실패했습니다.
```

## 초기 백필

초기 백필은 기존 과거 통계를 최초 한 번 적재할 때만 사용한다. 성공한 백필이 있으면 다시 실행하지
않으며, 이후 과거 통계를 다시 계산할 때는 REBUILD를 사용한다.

### 실행 명령

macOS·Linux:

```bash
./gradlew bootRun --args='--spring.main.web-application-type=none --spring.devtools.restart.enabled=false --app.statistics.backfill.enabled=true'
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun --args="--spring.main.web-application-type=none --spring.devtools.restart.enabled=false --app.statistics.backfill.enabled=true"
```

## 기간별 수동 재집계

성공한 초기 백필이 있을 때만 실행할 수 있다. 시작일과 종료일을 모두 포함하여 최대 366일까지 지정할
수 있고 종료일은 어제보다 늦을 수 없다.

### 실행 명령

```bash
./gradlew bootRun --args='--spring.main.web-application-type=none --spring.devtools.restart.enabled=false --app.statistics.rebuild.enabled=true --app.statistics.rebuild.start-date=2026-08-01 --app.statistics.rebuild.end-date=2026-08-08'
```

종료일을 생략하면 DB 기준 어제까지 재집계한다.

```bash
./gradlew bootRun --args='--spring.main.web-application-type=none --spring.devtools.restart.enabled=false --app.statistics.rebuild.enabled=true --app.statistics.rebuild.start-date=2026-08-01'
```

Windows에서는 `./gradlew`를 `.\gradlew.bat`으로 바꾸고 `--args` 값을 큰따옴표로 감싼다.

## 실행 결과 확인

성공하면 완료 로그를 남기고 종료 코드 `0`을 반환한다. 가장 최근 실행 상태와 진행일은 다음 SQL로
확인한다.

macOS·Linux에서는 `echo $?`, Windows PowerShell에서는 `$LASTEXITCODE`로 직전 종료 코드를 확인할
수 있다.

```sql
SELECT
    id,
    batch_type,
    status,
    target_start_date,
    target_end_date,
    last_completed_date,
    started_at,
    completed_at
FROM statistics_batch_runs
ORDER BY id DESC
LIMIT 1;
```

재집계된 일별 결과는 다음과 같이 확인한다.

```sql
SELECT *
FROM daily_statistics
WHERE statistics_date BETWEEN '2026-08-01' AND '2026-08-08'
ORDER BY statistics_date;
```

## 실패 시 대응

- 초기 백필 완료가 필요하다는 메시지: 초기 백필을 먼저 완료한다.
- 다른 집계가 실행 중이라는 메시지: 해당 실행이 끝난 뒤 같은 명령을 다시 실행한다.
- 날짜 입력 오류: `yyyy-MM-dd` 형식, 날짜 순서, 어제 이하, 최대 366일 조건을 확인한다.
- REBUILD 중간 실패: 해당 실행은 `FAILED`로 기록된다. 원인을 해결한 뒤 같은 전체 기간을 다시 실행한다.
- 프로세스가 강제 종료되어 `RUNNING` 행이 남으면 직접 수정하지 않는다. heartbeat가 1시간 이상 지난
  실행은 다음 집계 시작 시 `FAILED`로 정리되므로 이후 재시도한다.

같은 REBUILD 기간을 반복 실행해도 `daily_statistics`에는 날짜별 한 행만 유지된다.
