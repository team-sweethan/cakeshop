# 새 Flyway migration 작성 예시

실제 파일을 만들기 전에 포맷을 눈으로 익히기 위한 예시 모음이다. 절차 전체는
[`README.md`의 「새 migration 만들기」](../README.md#새-migration-만들기)를 따르고,
Flyway와 seed의 역할 분리는 [`conventions.md`](conventions.md)를 본다.

## 1. 생성 직후 받는 초안

```powershell
.\gradlew.bat newMigration -Pdesc=add_coupon_table
```

```
created: docs/sql/draft/add_coupon_table.sql
next:    gradlew promoteMigration -Pdesc=add_coupon_table
```

초안은 `db/migration`이 **아니라** `docs/sql/draft/`에 생긴다. 클래스패스 밖이라 Flyway가
보지 못하고, 따라서 SQL을 쓰는 도중에 앱이 떠도 적용될 일이 없다. 파일명에 버전도 아직 없다.

초안은 다음 내용만 담고 있다 (템플릿 정의는 `build.gradle`의 `newMigration` 태스크).

```sql
-- add_coupon_table (초안)
--
-- 규칙
-- * 로컬 샘플 데이터는 여기 넣지 않는다. db/seed/seed-local.sql 을 쓴다.
-- * 서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다.
-- * SQL 위에 '왜 필요한지'를 주석으로 단다. '무엇을 하는지'는 SQL 이 말한다.
--
-- 이 파일은 아직 Flyway 가 보지 않는다. 다 쓰면 아래 명령으로 승격한다.
--     gradlew promoteMigration -Pdesc=add_coupon_table
-- >>> SQL >>>

```

`-- >>> SQL >>>` **아래에** SQL을 채운다. 위쪽 머리말은 승격할 때 정식 머리말로 교체되므로
그대로 둔다. 마커 줄을 지우면 승격이 거부된다.

- 파일명은 직접 짓지 않는다. 버전은 **승격 시각**(`yyyyMMdd_HHmmss`)으로 자동으로 찍히며,
  같은 초의 버전이 이미 있으면 1초 밀어서 만든다.
- 직접 정하는 값은 `-Pdesc` 하나뿐이며 **소문자 snake_case**(`[a-z0-9]+(_[a-z0-9]+)*`)만 받는다.
  대문자·하이픈·한글은 태스크가 거부한다.

## 2. 기준 데이터 보충 예시 (저장소 실물)

`V20260729_003452__provision_default_store.sql`은 모든 환경에 필요한 대표 매장을 보장한다.
승격된 머리말 아래에 다음이 이어진다.

```sql
-- StoreService.DEFAULT_STORE_ID가 참조하는 대표 매장은 모든 환경의 실행 필수 데이터다.
-- 이미 운영자가 설정한 값이 있으면 덮어쓰지 않고, 없는 행만 기본값으로 보충한다.
INSERT INTO `store`
    (`id`, `name`, `description`, `address`, `phone`,
    `pickup_place`, `pickup_start_time`, `pickup_end_time`, `pickup_interval_minutes`)
SELECT
    1, '케이크 공방', '수제 케이크 전문 매장입니다.', '서울특별시 강남구 테헤란로 1', '02-000-0000',
    '매장 1층 픽업 데스크', '10:00:00', '20:00:00', 30
WHERE NOT EXISTS (
    SELECT 1 FROM `store` WHERE `id` = 1
);
```

같은 파일의 두 번째 쿼리는 7개 요일 중 **누락된 요일만** 채우기 위해
`LEFT JOIN ... WHERE existing.id IS NULL` 패턴을 쓴다.

### 여기서 읽어낼 관례

| 관례 | 이유 |
| --- | --- |
| SQL 위에 **왜 필요한지**를 주석으로 단다 | "무엇을 하는지"는 SQL이 말한다. 어느 코드가 이 데이터를 전제하는지(`StoreService.DEFAULT_STORE_ID`)를 적는다 |
| 테이블·컬럼 식별자를 백틱으로 감싼다 | 저장소 전체가 이 표기를 쓴다 |
| 데이터 삽입은 **멱등**하게 쓴다 | `WHERE NOT EXISTS` / `LEFT JOIN ... IS NULL`로 없는 것만 보충한다. 운영자가 바꿔 놓은 값을 덮어쓰지 않는다 |

## 3. 순수 DDL 예시

```sql
-- 쿠폰 발급 이력을 회원별로 조회하는 화면이 추가되어 신규 테이블이 필요하다.
CREATE TABLE `coupons` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT,
    `code`       VARCHAR(50)  NOT NULL,
    `name`       VARCHAR(100) NOT NULL,
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_coupons_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

컬럼 정렬과 제약 명명은 `V0__initial_schema.sql`의 스타일을 따른다.

## 4. 무엇을 어디에 넣는가

| 성격 | 위치 |
| --- | --- |
| 스키마 변경(DDL) | `db/migration` — 새 migration 파일 |
| 모든 환경에 필요한 기준 데이터 | `db/migration` — 앱이 특정 PK나 행의 존재를 전제한다면 seed에 두면 안 된다 |
| 로컬 샘플 데이터 | `db/seed/seed-local.sql` **[migration에 넣는 것 금지]** |

서로 의존하는 DDL(테이블 생성 → 그 테이블에 컬럼 추가)은 파일을 나누지 말고 한 파일·한 PR에 담는다.
`out-of-order: true` 설정이라 나누면 머신마다 적용 순서가 달라질 수 있다.

## 5. 승격과 확인

작성이 끝나면 승격한다. 이 시점에 버전이 찍히고 파일이 `db/migration`으로 옮겨진다.

```powershell
.\gradlew.bat promoteMigration -Pdesc=add_coupon_table
```

```
promoted: src/main/resources/db/migration/V20260729_101542__add_coupon_table.sql
```

승격이 막히는 경우와 대처는 다음과 같다.

| 메시지 | 뜻 |
| --- | --- |
| `draft has no SQL yet` | 마커 아래가 비었거나 주석뿐이다. 주석만 있는 파일도 Flyway는 적용해 checksum을 박으므로 승격시키지 않는다 |
| `draft lost its marker line` | `-- >>> SQL >>>` 줄을 지웠다. 되살리고 그 아래에 SQL을 둔다 |
| `draft not found` | 이미 승격했거나 `-Pdesc`가 초안 파일명과 다르다 |
| `warn: INSERT without an idempotent guard` | 경고일 뿐 승격은 된다. 3절의 멱등 패턴을 의도적으로 뺀 것인지 확인한다 |

이어서 로컬에 적용해 본다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

```sql
USE `cakeshop`;

SELECT `installed_rank`, `version`, `description`, `success`
FROM `flyway_schema_history`
ORDER BY `installed_rank`;
```

기존 `0`, `1`, `3`, `20260729.003452` 뒤에 새 타임스탬프 버전이 `success = 1`로 붙어야 한다.
이어서 `.\gradlew.bat test`로 `MigrationNamingTests`와 Testcontainers 통합 테스트를 돌린다
(**Docker 실행 필요**).

## 6. 머지된 뒤에는 손대지 않는다

머지된 migration을 고치면 checksum이 바뀌어 **팀원 전원이 로컬 DB를 다시 만들어야 한다.**

머지 전 자기 브랜치라면 고쳐도 되지만, **이미 로컬에 적용된 뒤라면 내 DB에는 checksum이
남아 있다.** 그대로 고치면 다음 기동에서 `CHECKSUM_MISMATCH`가 난다. 해당 버전의 이력 행을
지우고 다시 띄운다.

```sql
USE `cakeshop`;
DELETE FROM `flyway_schema_history` WHERE `version` = '20260729.101542';
```

이 방법은 **아직 아무에게도 공유되지 않은** migration에만 쓴다. 이미 적용된 DDL이 있다면
그 변경을 되돌리는 것은 별도로 해야 한다.

- **내용 수정 [금지]** — checksum 불일치. 변경이 필요하면 새 migration을 만든다.
- **파일명 변경 [금지]** — checksum은 파일 내용으로 계산하므로 이름만 바꿔도 이력의 버전·설명과
  어긋나 실패한다(설명만 바꾸면 `DESCRIPTION_MISMATCH`).
