# cakeshop

Spring Boot 4.0.2 · Java 21 · Gradle · Thymeleaf · MyBatis · MariaDB

## 개발 환경

각 개발자가 PC에 MariaDB를 직접 설치하고 Spring Boot를 실행한다. 기본적으로 각자의 로컬 MariaDB를 사용하고, 필요할 때만 `rds` 프로필로 공용 AWS RDS에 접속한다. 애플리케이션의 로컬 실행에는 Docker가 필요하지 않지만, MariaDB Testcontainers 기반 DB 통합 테스트에는 Docker가 필요하다.

## 처음 설치하기

빈 DB에서 화면이 뜨기까지의 전체 절차다. 이미 로컬 DB가 있고 기동이 실패한다면 아래 「기존 로컬 DB 완전 초기화」로 간다.

### 0. 필요한 것

| | 비고 |
|---|---|
| **JDK 21** | Gradle toolchain이 자동으로 받아오지만, 미리 설치돼 있으면 첫 빌드가 빠르다 |
| **MariaDB 11.4** | 각 PC에 직접 설치한다 |
| **Docker** | 애플리케이션 실행에는 필요 없다. `gradlew test`의 Testcontainers 통합 테스트에만 필요하다 |

```powershell
git clone https://github.com/team-sweethan/cakeshop.git
cd cakeshop
```

### 1. `.env` 만들기

`.env_sample`을 `.env`로 복사하고 `LOCAL_DB_HOST`, `LOCAL_DB_PORT`, `LOCAL_DB_DATABASE`, `LOCAL_DB_USERNAME`, `LOCAL_DB_PASSWORD`를 자신의 환경에 맞게 고친다. `.env_sample`의 값은 예시이며 **포트가 `3307`로 되어 있으니** MariaDB 기본 포트(`3306`)를 쓴다면 반드시 바꾼다. `.env`는 커밋하지 않는다.

### 2. 빈 데이터베이스 만들기

`root` 또는 데이터베이스 생성 권한이 있는 계정으로 실행한다.

```sql
CREATE DATABASE `cakeshop`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
```

애플리케이션이 사용하는 계정에는 최소한 `cakeshop` 데이터베이스에서 테이블 생성·변경과 데이터 읽기·쓰기 권한이 있어야 한다.

### 3. 애플리케이션 실행 — Flyway가 스키마를 만든다

`docs/sql`의 파일을 직접 실행하지 않는다. 스키마는 Flyway가 적용한다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

macOS·Linux에서는 `./gradlew bootRun --args="--spring.profiles.active=local"`을 사용한다.

### 4. 샘플 데이터 넣기

시드는 Flyway 관리 대상이 아니라서 **애플리케이션 실행만으로는 들어가지 않는다.** 스키마가 올라온 뒤 MariaDB 클라이언트로 직접 실행한다. 호스트·포트·사용자는 자신의 `.env`에 맞게 바꾼다.

```powershell
mariadb --host=localhost --port=3307 --user=root --password cakeshop `
  < src\main\resources\db\seed\seed-local.sql
```

### 5. 확인

```sql
USE `cakeshop`;

SELECT `installed_rank`, `version`, `description`, `success`
FROM `flyway_schema_history`
ORDER BY `installed_rank`;
```

버전 `0`, `1`, `3`, `20260729.003452`가 모두 `success = 1`이어야 한다. 시드는 Flyway 관리 대상이 아니므로 이 목록에 나타나지 않는다.

`http://localhost:8080/`에서 고객 화면이 뜨고, `admin@cakeshop.local / Admin1234!`로 관리자 로그인이 되면 완료다.

## 기존 로컬 DB 완전 초기화

이 절차는 과거에 `docs/sql`의 DDL을 수동 적용했거나 Flyway 이력이 꼬인 **개인 로컬 DB만** 대상으로 한다. 데이터베이스 전체와 그 안의 모든 테이블, 데이터, `flyway_schema_history`가 삭제된다. 공용 RDS나 보존해야 할 데이터베이스에는 절대 실행하지 않는다.

**Flyway 도입 브랜치가 `dev`에 머지된 뒤 최초 실행**에서 애플리케이션이 다음 오류로 기동하지 못하면 이 절차가 필요한 경우다.

```text
Found non-empty schema(s) `cakeshop` but no schema history table.
```

Flyway 도입 이전에 만든 로컬 DB에는 `flyway_schema_history`가 없어서 발생하며 정상이다. 각자 로컬 DB를 **1회 재생성**하면 되고, 이번 한 번으로 끝난다. 이후 샘플 데이터가 바뀌어도 `db/seed/seed-local.sql`만 다시 실행하면 되며 DB를 다시 만들 필요가 없다.

1. 실행 중인 애플리케이션을 `Ctrl+C`로 종료한다.
2. 보존할 데이터가 있으면 먼저 백업한다.
3. 접속하려는 호스트·포트·데이터베이스 이름이 `.env`의 `LOCAL_DB_*` 값과 일치하는지 다시 확인한다.
4. `cakeshop` 데이터베이스 전체를 삭제하고 같은 이름으로 다시 생성한다.
5. 애플리케이션을 `local` 프로필로 실행해 Flyway가 빈 DB를 처음부터 구성하도록 한다.
6. `db/seed/seed-local.sql`을 실행해 샘플 데이터를 넣는다.
7. `flyway_schema_history`와 샘플 계정을 확인한다.

Windows에서 MariaDB CLI로 백업하는 예시는 다음과 같다. 호스트, 포트, 사용자, 백업 경로는 자신의 `.env`에 맞게 바꾼다. `--password`는 명령행에 비밀번호를 노출하지 않고 입력 프롬프트를 표시한다.

```powershell
mariadb-dump `
  --host=localhost `
  --port=3307 `
  --user=root `
  --password `
  --result-file="C:\backup\cakeshop-before-reset.sql" `
  cakeshop
```

백업이 필요 없거나 백업이 완료되면 MariaDB에 접속한다.

```powershell
mariadb --host=localhost --port=3307 --user=root --password
```

접속 후 다음 SQL을 실행한다. 이 명령은 복구할 수 없는 삭제 작업이므로 현재 접속 대상이 로컬 MariaDB인지 반드시 확인한다.

```sql
SELECT
    @@hostname AS `server_host`,
    @@port AS `server_port`;

DROP DATABASE IF EXISTS `cakeshop`;

CREATE DATABASE `cakeshop`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
```

`docs/sql/V0_ERD.sql`의 `DROP TABLE` 구문으로 일부 테이블만 삭제하면 안 된다. 그 방법은 `flyway_schema_history` 또는 이후 추가된 테이블을 남길 수 있고, Flyway가 이미 적용된 마이그레이션이라고 오판하게 만든다. `flyway_schema_history`만 따로 삭제하거나 임의로 수정하는 것도 금지한다.

DB를 다시 만든 뒤 프로젝트 루트에서 실행한다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

현재 `local` 프로필에서는 다음 순서로 적용된다.

1. `V0__initial_schema.sql`: 전체 공통 스키마 생성
2. `V1__add_product_stock.sql`: 상품 재고 컬럼 추가
3. `V3__add_member_name.sql`: 회원 이름 컬럼 추가 및 기존 로컬 계정 값 보정
4. `V20260729_003452__provision_default_store.sql`: 모든 환경에 필요한 대표 매장과 7개 요일 영업시간 보장

스키마가 올라오면 샘플 데이터를 넣는다. MariaDB 클라이언트에서 `src/main/resources/db/seed/seed-local.sql`을 실행한다.

```powershell
mariadb --host=localhost --port=3307 --user=root --password cakeshop `
  < src\main\resources\db\seed\seed-local.sql
```

이 스크립트는 공통 필수 데이터인 대표 매장과 영업시간은 유지하고, 나머지 로컬 샘플 데이터를
지운 뒤 다시 넣는다. 로컬에서 만든 주문·리뷰·게시글도 함께 사라지므로 로컬 DB에서만 실행한다.
대신 몇 번을 실행해도 결과가 같으므로, 시드 내용이 바뀌었을 때 이 단계만 다시 실행하면 된다.
DB를 다시 만들 필요가 없다.

적용 결과는 MariaDB에서 다음 SQL로 확인한다.

```sql
USE `cakeshop`;

SELECT
    `installed_rank`,
    `version`,
    `description`,
    `type`,
    `success`
FROM `flyway_schema_history`
ORDER BY `installed_rank`;
```

`local` 프로필에서는 위 목록의 버전 `0`, `1`, `3`, `20260729.003452`가 모두 성공(`success = 1`)이어야 한다. 시드는 Flyway 관리 대상이 아니므로 이 목록에 나타나지 않는다. 이후 마이그레이션이 추가되면 `20260729.101542` 형태의 타임스탬프 버전이 함께 표시된다. 초기화가 완료되면 `admin@cakeshop.local / Admin1234!` 계정으로 관리자 화면 로그인을 확인한다.

자주 발생하는 오류는 다음과 같이 처리한다.

| 증상 | 원인 | 조치 |
|---|---|---|
| `NON_EMPTY_SCHEMA_WITHOUT_SCHEMA_HISTORY_TABLE` | 기존 수동 스키마가 남아 있음 | 로컬 DB가 맞는지 확인한 뒤 데이터베이스 전체를 다시 생성한다. 임의로 baseline을 켜지 않는다. |
| `Table ... already exists` | 테이블만 일부 삭제했거나 다른 DB에 접속함 | `.env` 접속 정보를 확인하고 데이터베이스 전체를 다시 생성한다. |
| 샘플 이메일·매장 PK 중복 | 기존 샘플 데이터가 남아 있음 | 테이블 단위 삭제 대신 로컬 데이터베이스 전체를 초기화한다. |
| `Access denied` | 애플리케이션 계정의 권한 또는 비밀번호가 잘못됨 | `.env` 값과 MariaDB 계정 권한을 확인한다. |
| `Unknown database 'cakeshop'` | 삭제 후 데이터베이스를 다시 만들지 않음 | `CREATE DATABASE`를 실행하고 다시 시작한다. |

위 오류 중 조치 방법이 정해진 것은 애플리케이션이 기동에 실패할 때 한국어 안내와 함께 로그에 출력한다(`global/config/FlywayConfig.java`). 원인을 특정할 수 없는 오류는 Flyway의 원본 메시지를 그대로 남긴다.

Flyway의 `clean`은 `clean-disabled: true`로 차단되어 있다. 초기화 목적으로 이 보호 설정을 해제하지 않는다. 기존 데이터 보존이 필요하면 전체 삭제를 진행하지 말고 팀과 별도의 전환 마이그레이션 및 baseline 절차를 먼저 합의한다.

로컬 DB 전환과 함께 테스트 실행 조건도 바뀐다. `.\gradlew.bat test`는 MariaDB Testcontainers 기반 DB 통합 테스트를 포함하므로 **실행 중인 Docker가 필요**하다. Docker 없이 실행하면 해당 테스트만 실패한다.

## 실행 프로필 선택

애플리케이션을 시작할 때 `local`과 `rds` 중 하나를 선택한다. 기본 프로필은 `local`이지만, 오접속을 막고 실행 의도를 분명히 하기 위해 항상 프로필을 명시하는 것을 권장한다. 실행 중에는 프로필을 바꿀 수 없으므로 전환하기 전에 실행 중인 서버를 `Ctrl+C`로 종료해야 한다. 종료하지 않고 다시 실행하면 `Port 8080 was already in use` 오류가 발생한다.

### 로컬 DB로 실행

개인 개발과 화면 확인에는 `local` 프로필을 사용한다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

기본 프로필도 `local`이지만 실행 환경을 로그와 명령 기록에서 명확하게 구분할 수 있도록 `--spring.profiles.active=local`을 명시한다.

정상 실행 로그에는 `The following 1 profile is active: "local"`과 `Tomcat started on port 8080`이 표시된다. 실행 후 `http://localhost:8080/`에서 고객 화면을 확인한다.

전체 화면 경로는 `http://localhost:8080/screens`에서 확인한다. `local` 프로필에서는 화면 선이관 기간 동안 고객 목업 흐름만 로그인 없이 열 수 있다. 모든 관리자 화면(`/admin/**`)은 관리자 로그인이 필요하며, 로컬에서는 `db/seed/seed-local.sql`로 생성된 `admin@cakeshop.local / Admin1234!` 계정으로 로그인해 확인한다. `rds` 프로필에서는 고객 목업 공개 조회도 비활성화된다.

### 공용 RDS로 실행

`rds` 프로필에서는 Flyway를 비활성화한다. 애플리케이션 기동은 RDS 스키마를 생성하거나 변경하지 않으므로, 필요한 스키마가 별도 검토·승인 절차로 먼저 반영됐는지 확인한다. 로컬 DB 초기화 절차의 `DROP DATABASE`를 RDS에 실행해서는 안 된다.

1. `.env_sample`을 `.env`로 복사한다.
2. `.env`의 `RDS_ENDPOINT`, `RDS_PORT`, `RDS_DATABASE`, `RDS_USERNAME`, `RDS_PASSWORD`를 실제 접속 정보로 변경한다.
3. 다음 명령을 실행한다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=rds"
```

정상 실행 로그에는 `The following 1 profile is active: "rds"`가 표시되고 Flyway migration은 실행되지 않는다. `.env`는 Git에 커밋하지 않으며 저장소에는 실제 값이 없는 `.env_sample`만 유지한다.

RDS 프로필은 `require_secure_transport=ON` 환경에 맞춰 MariaDB Connector/J의 `sslMode=trust`로 TLS 연결을 사용한다. 이 설정은 통신을 암호화하지만 서버 인증서와 호스트명은 검증하지 않으므로 팀 공용 개발 RDS 용도에만 사용한다. 운영 환경에서는 AWS RDS CA 인증서를 등록하고 `sslMode=verify-full`을 사용해야 한다.

TLS 설정이 빠진 JDBC URL을 사용하면 다음 오류가 발생한다.

```text
Connections using insecure transport are prohibited while --require_secure_transport=ON
```

### PowerShell 환경변수로 전환한 경우

다음처럼 환경변수를 사용하면 해당 PowerShell 창에서 이후 실행에도 같은 프로필이 유지된다.

```powershell
$env:SPRING_PROFILES_ACTIVE="rds"
```

다시 `local` 프로필로 돌아가려면 실행 중인 서버를 종료한 후 환경변수를 제거하고 프로필을 명시해 실행한다.

```powershell
Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

이미 실행된 Java 프로세스의 프로필은 환경변수를 제거해도 바뀌지 않는다. 반드시 기존 서버를 먼저 종료하고 다시 실행한다.

## 프로필 요약

- `local` (기본): `.env`의 `LOCAL_DB_HOST`, `LOCAL_DB_PORT`, `LOCAL_DB_DATABASE`, `LOCAL_DB_USERNAME`, `LOCAL_DB_PASSWORD`를 사용하고 공통 Flyway migration을 적용. 샘플 데이터는 `db/seed/seed-local.sql`을 직접 실행해 넣는다
- `rds`: `.env`의 `RDS_ENDPOINT`, `RDS_PORT`, `RDS_DATABASE`, `RDS_USERNAME`, `RDS_PASSWORD`를 사용하며 Flyway는 비활성화. 스키마는 별도 승인 절차로 반영한다

## DB 스키마 관리

로컬과 테스트 DB 스키마는 Flyway가 관리한다. `rds` 프로필의 Flyway는 비활성화하며 애플리케이션 기동으로 공용 DB를 변경하지 않는다. 이미 공유된 versioned migration은 수정하지 않고 새로운 버전 파일을 추가한다.

- `src/main/resources/db/migration`: `local`, `test`에서 자동 적용하고 RDS에는 별도 검토·승인 절차로 반영하는 스키마 변경
- `src/main/resources/db/seed`: 로컬 개발용 샘플 데이터. **Flyway 관리 대상이 아니다.** 필요할 때 직접 실행한다
- `docs/sql`: 과거 수동 적용 SQL과 설계 참고 자료. 신규 DB에 직접 실행하지 않는다.

### 새 migration 만들기

파일명은 직접 짓지 않는다. 여러 사람이 동시에 브랜치를 나눠 작업하면 같은 버전 번호가 나오고, Git은 파일명이 다르면 조용히 둘 다 머지하기 때문이다. 아래 명령으로 만든다.

```powershell
.\gradlew.bat newMigration -Pdesc=add_coupon_table
```
```
created: src/main/resources/db/migration/V20260729_101542__add_coupon_table.sql
```

버전은 생성 시각(`yyyyMMdd_HHmmss`)으로 찍힌다. 같은 초에 만들어진 파일이 이미 있으면 자동으로 1초 밀어서 생성한다. 규약 위반은 `MigrationNamingTests`가 CI에서 잡는다.

타임스탬프는 "만든 시각"이라 머지 순서와 다를 수 있으므로 `out-of-order: true`를 켜 두었다. 서로 의존하는 DDL(테이블 생성 → 그 테이블에 컬럼 추가)은 파일을 나누지 말고 한 파일·한 PR에 담는다.

### 샘플 데이터

`db/seed/seed-local.sql`은 Flyway가 스캔하지 않는다. 그래서 checksum 검증에 걸리지 않고, 내용을
고쳐도 팀원들이 DB를 다시 만들 필요가 없다. 스크립트 맨 앞에서 로컬 샘플 데이터를 지운 뒤
다시 넣으므로 몇 번을 실행해도 결과가 같다. 모든 환경에 필요한 대표 매장과 영업시간은
versioned migration으로 관리하며 로컬 seed가 삭제하거나 덮어쓰지 않는다.

상태값(`status`) 컬럼은 도메인마다 흩어지지 않도록 `docs/status-design.md`의 상태값 공통 규칙(영문 enum 이름 저장·한글 라벨 미저장·전이는 service)을 따른다.

빈 로컬 DB에서는 Flyway가 자동으로 전체 이력을 적용한다. 기존 수동 DB는 스키마 상태가 사람마다 다를 수 있으므로 `baseline-on-migrate`를 임의로 활성화하지 않는다. `rds` 프로필은 Flyway를 실행하지 않으며, 백업과 스키마 비교를 거친 팀 승인 절차 없이 RDS를 초기화하거나 migration을 반영하지 않는다.

## 구조

- `global`: 공통 기반(config·security·error·web·paging·infra)
- `domain/{13개}`: controller·service·mapper·dto(form/view)·entity·error

## Store 수직 슬라이스 구현 예시

새 설정형 도메인은 `domain/store`의 흐름을 기준으로 구현한다. 단, store는 단일 매장 설정이므로 목록·페이징을 포함한 전체 CRUD 예시는 product 도메인에서 별도로 제공한다.

### 적용 순서

1. 빈 로컬 DB를 생성하고 애플리케이션을 `local` 프로필로 실행해 Flyway가 테이블과 필수 대표
   매장·7개 요일 영업시간을 만들게 한 뒤, `db/seed/seed-local.sql`을 실행해 공통 샘플 계정
   `admin@cakeshop.local`·`user@cakeshop.local` 등 로컬 샘플 데이터를 넣는다.
2. `admin@cakeshop.local / Admin1234!`로 로그인한다.
3. `GET /admin/store`에서 매장 정보를 조회한다.
4. 폼 저장은 `StoreUpdateForm` 검증 → `StoreService` 트랜잭션 → `StoreMapper.xml`의 `#{}` 바인딩 순서로 처리된다.
5. 검증 실패는 같은 화면을 재렌더하고, 성공은 `/admin/store`로 redirect한 뒤 공통 FlashMessage를 표시한다.
6. 저장 결과는 `HomeService`가 `StorePublicView`로 받아 고객 메인과 공통 Footer에 전달한다.

`db/seed/seed-local.sql`의 샘플 계정은 화면 확인용이며 `rds` 프로필에는 적용되지 않는다. Spring Security는 이메일로 회원을 조회하고 DB의 `ADMIN` 역할(권한 문자열 `ROLE_ADMIN`)을 확인한 뒤, 로그인 전에 요청했던 `/admin/store`로 돌려보낸다.

### 역할 분리 기준

- `entity`: DB 조회 결과와 영속 상태 (MyBatis POJO — JPA `@Entity`가 아니며 더티체킹·지연로딩 없음, 저장은 mapper 호출로만)
- `dto/form`: 관리자 입력 및 Jakarta Validation 규칙
- `dto/view`: 관리자·고객 화면에 필요한 읽기 데이터
- `service`: 여러 테이블 변경의 트랜잭션과 `BusinessException + StoreErrorCode`
- `mapper/XML`: SQL과 `#{}` 바인딩, camelCase 매핑
- `controller`: Model+View, BindingResult 재렌더, RedirectAttributes FlashMessage

구현 과정과 선택 이유는 각 계층의 핵심 지점에 주석으로 남겨 두었다. 새 도메인은 자명한 문법 주석까지 복사하지 말고, 트랜잭션 경계·검증 실패 처리·도메인 조합처럼 구조상 중요한 주석만 유지한다.

## 관리자 화면 경로

로그인 성공 시 저장된 요청이 없으면 `/admin`으로 이동한다. 매장 관리만 실제 DB에 연결되어 있고, 아래의 다른 화면은 기존 관리자 샘플을 Thymeleaf MVC 경로로 옮긴 목업 상태다. 목업 화면의 변경 버튼은 백엔드가 연결될 때까지 비활성화한다.

| 기능 | 경로 | 현재 상태 |
|---|---|---|
| 대시보드 | `/admin` | 목업 |
| 매장 | `/admin/store` | 실제 조회·수정·휴무일 관리 |
| 상품 | `/admin/products`, `/admin/products/new` | 목업 |
| 주문 | `/admin/orders`, `/admin/orders/{id}` | 목업 |
| 제작·픽업 | `/admin/fulfillment` | 목업 |
| 결제·환불 | `/admin/payments` | 목업 |
| 쿠폰 | `/admin/coupons` | 목업 |
| 회원 | `/admin/members` | 목업 |
| 후기 | `/admin/reviews` | 목업 |
| 커뮤니티 | `/admin/community`, `/admin/community/{id}` | 목업 |
| 알림 | `/admin/notifications` | 목업 |
| 통계 | `/admin/statistics` | 목업 |

각 목업 화면은 해당 `domain/*/controller/*AdminController`가 소유한다. 백엔드를 구현할 때 URL과 템플릿은 유지하고 Controller의 Model 데이터와 비활성화된 명령 버튼만 실제 기능으로 교체한다.

## 고객 화면 선이관

프론트 원본 18개 화면 중 메인과 로그인은 각각 매장 조회와 Spring Security 연동을 유지한다. 나머지 16개 화면은 아래 import 스크립트로 도메인별 Thymeleaf 템플릿과 GET 경로에 먼저 연결했다. 커뮤니티 3개 화면은 프론트 원본에 없어 별도로 추가했으며, 이로써 `/screens` 기준 고객 화면은 총 21개다. 현재 목업 화면의 폼과 장바구니 동작은 브라우저 안에서만 실행되며 DB를 변경하지 않는다.

| 기능 | 경로 | 현재 상태 |
|---|---|---|
| 전체 화면 목록 | `/screens` | 고객·관리자 35개 경로 안내 |
| 회원가입 | `/signup` | 목업 |
| 상품 목록·상세 | `/products`, `/products/{id}` | 목업 |
| 장바구니 | `/cart` | 목업 (브라우저 `localStorage`) |
| 픽업 설정 | `/orders/pickup` | 목업 |
| 주문 제작 | `/orders/custom/options`, `/orders/custom/request` | 목업 |
| 주문서·완료·상세 | `/orders/checkout`, `/orders/complete`, `/orders/{id}` | 목업 |
| 결제 | `/orders/{id}/payment` | 목업 |
| 마이페이지·프로필·쿠폰 | `/mypage`, `/mypage/profile`, `/mypage/coupons` | 목업 |
| 알림·후기 | `/notifications`, `/reviews/new` | 목업 |
| 커뮤니티 목록·상세·글쓰기 | `/community`, `/community/{id}`, `/community/new` | 목업 (별도 추가) |

프론트 저장소가 갱신되면 다음 명령으로 프론트 원본 기반 16개 목업 템플릿과 전용 CSS·JavaScript를 다시 가져온다. 메인·로그인과 별도로 추가한 커뮤니티 화면은 이 명령이 덮어쓰지 않는다.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\import-customer-mockups.ps1
```
