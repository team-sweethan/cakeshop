# Flyway migration 작성 가이드

이 문서는 새 migration을 **어떻게 설계하고 검증할지** 설명한다.
파일명, 공유 후 불변성, Flyway와 seed의 경계 같은 필수 규칙은
[conventions.md](conventions.md#8-flyway와-seed)를 정본으로 따른다.

## 목차

1. [migration이 필요한 변경](#1-migration이-필요한-변경)
2. [파일 생성](#2-파일-생성)
3. [변경 단위와 적용 순서](#3-변경-단위와-적용-순서)
4. [기존 데이터가 있는 스키마 변경](#4-기존-데이터가-있는-스키마-변경)
5. [MariaDB DDL의 부분 실패와 복구](#5-mariadb-ddl의-부분-실패와-복구)
6. [expand·backfill·contract 판단](#6-expandbackfillcontract-판단)
7. [기준 데이터와 로컬 seed](#7-기준-데이터와-로컬-seed)
8. [작성 후 검증](#8-작성-후-검증)
9. [공유 후 불변성과 실패 대응](#9-공유-후-불변성과-실패-대응)

## 1. migration이 필요한 변경

애플리케이션이 사용하는 공통 스키마나 모든 환경에 필요한 기준 데이터가 바뀌면 versioned
migration을 추가한다.

| 변경 | 처리 |
|---|---|
| 테이블·컬럼·인덱스·제약조건 추가 또는 변경 | 새 migration |
| 기존 행의 구조적 backfill·값 변환 | 새 migration |
| 애플리케이션 실행에 반드시 필요한 기준 데이터 | 새 migration |
| 로컬 화면 확인용 회원·상품·게시글 등 | `src/main/resources/db/seed` |
| 이미 성공 적용된 migration의 후속 수정 | 기존 파일 수정이 아니라 새 migration |

코드가 아직 읽거나 쓰지 않는 예상 스키마를 미리 만들지 않는다. 같은 기능을 위한 코드와 migration은
가능하면 한 PR에서 함께 검토한다.

## 2. 파일 생성

파일명을 직접 만들지 않고 프로젝트의 Gradle 태스크를 사용한다. 설명은 변경 목적이 드러나는 소문자
`snake_case`로 작성한다.

**Windows PowerShell**

```powershell
.\gradlew.bat newMigration -Pdesc=add_review_summary
```

**macOS·Linux Bash**

```bash
./gradlew newMigration -Pdesc=add_review_summary
```

생성 결과는 다음 형식이다.

```text
src/main/resources/db/migration/V<yyyyMMdd>_<HHmmss>__<description>.sql
```

태스크는 현재 저장소에 같은 초의 버전이 있으면 다음 초를 사용한다. 다른 브랜치에서 만든 파일까지는
알 수 없으므로, 최신 `dev`를 반영한 뒤 버전 중복 검사를 통과해야 한다.

`V0`, `V1`, `V3` 형식의 파일은 규약 도입 전 migration이다. 새 파일에서 이 이름을 따라 하지 않는다.
생성된 머리말 아래에 SQL과 필요한 설명을 작성하되, 주석은 변경 이유와 데이터 전제를 파일 안에서
이해할 수 있게 적는다. 이름이 바뀔 수 있는 문서 경로나 절 번호를 근거로 남기지 않는다.

## 3. 변경 단위와 적용 순서

**한 migration은 하나의 배포 가능한 스키마 전환**을 표현한다. SQL 문장 수만으로 파일을 합치거나
나누지 않는다.

- 같은 기능에서 함께 적용되어야 의미가 완성되고 한 번에 실패·성공해야 하는 변경은 한 migration을
  우선 검토한다. 단, 한 파일에 넣었다고 여러 MariaDB DDL이 원자적으로 실행되는 것은 아니다.
- backfill, 제약 강화, 호환성 제거처럼 각 단계가 독립적으로 안전해야 하는 변경은 migration을
  나눌 수 있다.
- 서로 의존하는 여러 파일은 같은 PR에 넣고 버전 순서를 확인한다. 별도 PR로 나누려면 먼저 반영되는
  단계만으로도 기존 애플리케이션이 정상 동작해야 한다.
- `out-of-order: true`는 늦게 합쳐진 낮은 버전을 적용하도록 허용할 뿐, 의존 관계나 배포 순서를
  보장하지 않는다.

테이블 생성과 즉시 필요한 인덱스처럼 동일한 전환인 변경을 기계적으로 여러 파일로 쪼개지 않는다.
반대로 대량 backfill과 `NOT NULL` 강제를 단지 같은 기능이라는 이유로 무조건 한 파일에 넣지도 않는다.

## 4. 기존 데이터가 있는 스키마 변경

빈 DB에서 실행되는지만 확인해서는 부족하다. 변경 대상 테이블에 기존 행이 있다고 가정하고 아래를
먼저 확인한다.

1. 새 제약을 위반하는 행이 있는가?
2. 새 컬럼의 기존 행 값은 무엇으로 채울 것인가?
3. 변환 중 읽기·쓰기를 계속하는 코드와 호환되는가?
4. 테이블 잠금과 실행 시간이 허용 가능한가?
5. 실패하면 어느 상태가 남고 어떻게 확인·복구하는가?

### 제약조건 추가

`UNIQUE`, `FOREIGN KEY`, `CHECK`, `NOT NULL`을 추가하기 전 위반 행을 찾는 조회를 먼저 작성한다.
위반 데이터를 migration에서 자동 삭제하거나 임의 값으로 바꾸지 않는다. 업무상 올바른 보정값과
소유 도메인을 확인한 뒤 backfill하거나, 위반 시 migration이 명확히 실패하도록 한다.

### 새 필수 컬럼 추가

기존 행이 있는 테이블에 값 없는 `NOT NULL` 컬럼을 바로 추가하지 않는다. 다음 중 실제 요구에 맞는
방식을 선택한다.

- 모든 기존 행에 같은 올바른 값이 있다면 기본값과 함께 추가하고 필요하면 기본값을 제거한다.
- 행마다 계산해야 한다면 nullable 컬럼 추가 → backfill → 검증 → `NOT NULL` 강제 순서로 진행한다.
- 애플리케이션 배포 사이에 backfill이 필요하면 expand·backfill·contract 단계로 나눈다.

시간 컬럼은 전역 규칙에 따라 `DATETIME(6)`과 `CURRENT_TIMESTAMP(6)`를 사용한다. 기존 테이블의
이름·타입을 단순히 최신 스타일과 다르다는 이유만으로 함께 고치지 않는다.

## 5. MariaDB DDL의 부분 실패와 복구

MariaDB의 DDL은 트랜잭션 rollback만으로 여러 문장을 한꺼번에 되돌릴 수 있다고 가정해서는 안 된다.
앞 문장은 반영되고 뒤 문장이 실패하면 Flyway에는 실패가 기록되면서 스키마 일부가 남을 수 있다.

작성 전에 다음 시나리오를 설명할 수 있어야 한다.

```text
적용 전 상태 → 실행한 문장 → 실패 가능한 지점 → 남는 스키마 → 재실행 또는 복구 방법
```

- 반드시 함께 성공해야 하고 MariaDB가 한 `ALTER TABLE`에서 지원하는 변경은 한 문장으로 묶는 방안을
  검토한다.
- 여러 DDL로 나눠야 하면 각 문장 이후의 상태가 안전한지, 다음 실행이 어디서 시작할지 정한다.
- `IF EXISTS`와 `IF NOT EXISTS`를 실패 은폐용으로 일괄 추가하지 않는다. 이미 존재하는 객체가 의도한
  구조인지 확인하지 못한 채 성공으로 넘어갈 수 있다.
- 데이터 보정 실패를 예상한다면 DDL보다 먼저 검증하거나, DDL이 남지 않는 순서로 구성한다.

부분 적용 위험이 실제로 크고 일반적인 전체 migration 테스트로 증명할 수 없을 때만 전용 재시도 테스트를
추가한다. 모든 migration에 동일한 복구 테스트 클래스를 만드는 것은 요구하지 않는다.

## 6. expand·backfill·contract 판단

기존 코드와 새 코드가 한동안 같은 DB를 사용하거나 데이터 양 때문에 한 번에 전환하기 어렵다면 다음
단계로 나눈다.

1. **Expand**: 새 컬럼·테이블을 추가한다. 기존 코드가 계속 동작하도록 기존 구조는 유지한다.
2. **Backfill**: 기존 데이터를 새 구조로 옮기고 누락·위반 행이 없는지 검증한다.
3. **Contract**: 코드가 새 구조만 사용한 뒤 `NOT NULL` 강제, 옛 컬럼·제약 제거 등을 수행한다.

세 단계를 항상 도입하지는 않는다. 개인 로컬 DB에서만 쓰이고 데이터가 없거나, 하나의 원자적인 DDL로
안전하게 끝나는 변경에 단계적 배포를 강제하면 복잡성만 늘어난다. 다음 중 하나라도 해당할 때 적용을
우선 검토한다.

- 공용 DB에 기존 데이터가 있다.
- 구버전과 신버전 애플리케이션이 동시에 실행될 수 있다.
- backfill이 오래 걸리거나 별도 확인이 필요하다.
- 컬럼 제거·이름 변경처럼 이전 코드와 즉시 호환되지 않는 변경이다.

## 7. 기준 데이터와 로컬 seed

기준은 “팀원이 편한가”가 아니라 “애플리케이션이 모든 환경에서 이 데이터의 존재를 전제하는가”다.

| 데이터 | 위치 | 예시 |
|---|---|---|
| 없으면 기능이 성립하지 않는 코드·분류·대표 행 | versioned migration | 애플리케이션이 고정 ID로 참조하는 대표 매장 |
| 로컬 화면과 수동 테스트를 위한 샘플 | `db/seed` | 개발용 회원·상품·게시글 |
| 테스트 전용 데이터 | 각 테스트의 fixture | 경계값·권한·실패 상황 데이터 |

기준 데이터를 추가할 때는 기존 운영자가 바꾼 값을 덮어쓰지 않는지 확인한다. 고정 PK나 코드를
전제로 한다면 DB 제약과 애플리케이션 상수를 함께 검토하고, 없는 행만 보충하는 경우에는
`INSERT ... SELECT ... WHERE NOT EXISTS` 같은 의도가 드러나는 SQL을 사용할 수 있다.

로컬 seed는 반복 실행 결과가 예측 가능해야 하며 `flyway_schema_history`를 변경하지 않는다.

## 8. 작성 후 검증

변경 위험에 맞춰 아래 순서로 확인한다.

### 1. 정적 확인

- 파일명과 version이 중복되지 않는가?
- SQL이 migration 목적 밖의 테이블이나 데이터를 건드리지 않는가?
- 기존 데이터 영향과 실패 후 상태를 설명할 수 있는가?
- migration 주석만으로 변경 이유와 전제를 이해할 수 있는가?

### 2. 자동 테스트

최소한 파일명 검사와 빈 MariaDB에 전체 migration 적용을 확인한다.

```powershell
.\gradlew.bat test --tests com.cakeshop.global.database.MigrationNamingTests `
    --tests com.cakeshop.global.database.FlywayMigrationTests
```

`FlywayMigrationTests`는 MariaDB Testcontainers를 사용하므로 Docker가 실행 중이어야 한다. 새 제약,
backfill, 부분 실패가 핵심 위험이면 해당 도메인의 MariaDB 통합 테스트를 추가하거나 실행한다.
테스트 선택과 중복 기준은 [testing.md](testing.md#7-db트랜잭션동시성)를 따른다.

### 3. 업그레이드 경로 확인

기존 데이터 영향이 있는 변경은 직전 스키마와 대표 데이터를 준비한 상태에서도 실행한다. 확인 대상은
단순 성공 여부가 아니라 다음과 같다.

- 기존 행이 의도한 값으로 보존·변환됐는가?
- 새 제약이 잘못된 값은 막고 정상 값은 허용하는가?
- 실패 시 예상한 상태만 남고 정한 절차로 복구 가능한가?

로컬 애플리케이션을 실행했다면 버전 값을 하드코딩하지 말고 가장 최근 이력을 확인한다.

```sql
SELECT `installed_rank`, `version`, `description`, `script`, `success`
FROM `flyway_schema_history`
ORDER BY `installed_rank` DESC
LIMIT 10;
```

PR에는 실행한 테스트와 함께 기존 데이터 영향, 부분 실패 시 남는 상태, 공용 DB 반영·복구 시 주의점을
적는다.

## 9. 공유 후 불변성과 실패 대응

`dev`·`main`에 반영됐거나 다른 팀원이 적용한 versioned migration은 파일 내용과 이름을 바꾸거나
삭제하지 않는다. 변경이 필요하면 새 migration을 추가한다. 아직 혼자 사용하는 브랜치이고 누구도
적용하지 않은 파일만 기존 파일을 수정할 수 있다.

| 상황 | 대응 |
|---|---|
| 다른 브랜치와 version이 겹침 | 아직 공유되지 않은 쪽을 `newMigration`으로 다시 만들고 SQL을 옮긴다 |
| 공유 파일의 checksum·description 불일치 | DB를 먼저 지우지 말고 저장소의 원본 파일을 정확히 복원한다 |
| 내용은 맞지만 추가 변경이 필요함 | 더 높은 version의 새 migration을 만든다 |
| 공유 파일이 특정 DB에서 처음 적용되지 않음 | 파일을 고치지 말고 대상 데이터와 실패 지점을 확인한 뒤 팀과 반영·복구 방법을 결정한다 |
| DDL이 일부 적용된 채 실패함 | 재실행·`repair`·조건부 DDL을 먼저 시도하지 말고 남은 스키마와 이력을 확인해 복구 절차를 정한다 |
| 테이블은 있는데 이력 테이블이 없음 | 개인 로컬 DB인지 확인하고 [README의 문제 해결](../README.md#자주-발생하는-문제)을 따른다. 공용 DB를 초기화하거나 임의 baseline하지 않는다 |

### 개인 local DB 초기화

다음 조건을 모두 만족하면 남은 DDL을 수동으로 되돌리기보다 local DB를 새로 만드는 것이 기본
복구 방법이다.

- `local` 프로필에서 자기 PC의 MariaDB만 사용한다.
- 보존할 데이터가 없거나 필요한 데이터를 이미 백업했다.
- 공용 RDS나 다른 팀원이 함께 사용하는 DB가 아니다.

먼저 `.env`의 `LOCAL_DB_HOST`가 `localhost` 또는 `127.0.0.1`이고 포트가 자기 PC의 MariaDB 포트인지
확인한다. 이어서 접속한 서버와 DB를 조회한다. 두 결과를 확인하기 전에는 `DROP DATABASE`를 실행하지
않는다.

```sql
SELECT
    @@GLOBAL.hostname AS `server_host`,
    @@GLOBAL.port AS `server_port`,
    DATABASE() AS `current_database`;
```

개인 local 환경임을 확인했으면 MariaDB에서 DB를 다시 만든다.

```sql
-- 공용 RDS·공유 DB에서 실행 금지
DROP DATABASE IF EXISTS `cakeshop`;

CREATE DATABASE `cakeshop`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
```

애플리케이션을 `local` 프로필로 실행하면 Flyway가 빈 DB에 전체 migration을 적용한다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

```bash
./gradlew bootRun --args="--spring.profiles.active=local"
```

적용이 끝난 뒤 필요한 경우에만 로컬 샘플 데이터를 다시 넣는다.

```sql
SOURCE src/main/resources/db/seed/seed-local.sql;
SOURCE src/main/resources/db/seed/seed-community.sql;
```

아직 공유하지 않은 migration을 작성 중이었다면 SQL을 고친 뒤 이 과정을 반복할 수 있다. 이미 공유된
migration은 local DB를 초기화하더라도 수정하지 않는다.

DB 초기화는 **빈 DB에서 전체 migration이 성공하는지** 확인할 뿐, 기존 데이터가 있는 DB의 업그레이드
안전성을 증명하지 않는다. 기존 행의 backfill·제약 추가가 핵심인 변경은 8절의 업그레이드 경로 검증을
별도로 수행한다.

공용 RDS에서는 애플리케이션 Flyway가 비활성화되어 있다. 이 문서의 명령은 공용 DB 반영 권한이나
절차를 대신하지 않으며, 공용 DB에는 팀이 합의한 별도 반영·복구 절차만 사용한다.
