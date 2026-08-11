# 통계 집계 운영 가이드

초기 통계 백필과 기간별 수동 재집계의 실행·확인 절차를 정리한다. 집계 기준과 업무 규칙은
[`statistics-spec.md`](statistics-spec.md)가 정본이다.

## 실행 전 확인

- 실행 대상 DB에 필요한 Flyway migration이 적용되어 있어야 한다.
- `local` 프로필은 애플리케이션 시작 시 Flyway migration을 자동 적용한다.
- `rds` 프로필은 Flyway가 비활성화되어 있으므로 승인된 별도 스키마 반영 절차로 migration을 먼저
  적용한다. REBUILD 실행 전에는 `V20260811_101818__add_statistics_rebuild_batch_type.sql` 적용 여부를
  확인한다.
- 초기 백필과 수동 재집계는 동시에 활성화하지 않는다.
- 정기 일별 집계, 초기 백필과 수동 재집계는 하나의 실행 잠금을 공유한다.
- 오늘 통계는 집계하지 않으며 모든 날짜는 `Asia/Seoul` 기준이다.
- 로컬 `bootRun`의 `--args` 값은 줄바꿈 없이 한 줄로 전달한다.

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
