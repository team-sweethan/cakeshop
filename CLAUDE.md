# Cakeshop

Java 21 · Spring Boot 4 · Spring MVC · Thymeleaf · Spring Security · MyBatis · Flyway · MariaDB

**규칙의 정본은 `AGENTS.md`다.** 아래는 매번 필요한 것만 추린 요약이고, **어긋나면 `AGENTS.md`가 맞다.**

## 먼저 읽을 것

| 하려는 일 | 읽을 것 |
|---|---|
| **community** 도메인 파일 전부 | `src/main/java/com/cakeshop/domain/community/CLAUDE.md` |
| **review** 도메인 파일 전부 | `src/main/java/com/cakeshop/domain/review/CLAUDE.md` |
| 그 밖의 도메인·공통 작업 | `AGENTS.md` |

**`domain/` 아래 java 파일만이 아니다.** mapper XML·시드·템플릿·테스트, 그 도메인이 추가하는 새 Flyway migration도 그 도메인의 작업이다. **정확한 목록은 각 문서의 `이 도메인의 파일 범위` 절이 정본이다** — 여기 옮겨 적지 않는다. 그다음은 그 문서가 지정하는 것만 읽는다.

## 구조와 의존 방향

`domain/<도메인>/`에 `controller` · `service` · `mapper` · `entity` · `dto/form` · `dto/view` · `error`를 두는 수직 슬라이스. 둘 이상이 **실제로** 공유하는 것만 `global`.

MyBatis XML은 `src/main/resources/mapper/<도메인>/`, 템플릿은 `templates/{admin,customer}`, migration은 `db/migration`, 테스트는 운영 코드의 패키지 구조를 따른다.

의존 방향은 `Controller → Service → Mapper → DB`. 건너뛰거나 역방향으로 참조하지 않는다.

## 도메인 담당

| 수민 | 주환 | 정후 | 민정 | 현규 | 시은 | 공통 협의 |
|---|---|---|---|---|---|---|
| `member` `cart` | `order` `payment` | `coupon` | `chat` `notification` | `community` `review` | `product` `statistics` | `store` `global` `home` |

## 하지 않는 것

- **다른 도메인의 테이블·Mapper·Entity를 직접 참조하거나 JOIN하지 않는다.** 연동 계약의 코드와 SQL은 쓰는 쪽이 아니라 **데이터를 소유한 도메인**에 둔다 (`docs/conventions.md` 15절). **예외는 여러 도메인을 집계·요약하는 통계·대시보드 ReadModel 하나**다(15.9) — 관리자 화면이라는 것은 근거가 아니다
- 사용자 입력에 `${}`를 쓰지 않는다. MyBatis 값은 `#{}`로 바인딩한다
- **이미 공유된 Flyway migration은 고치지 않는다.** 머지 전이라도 팀원이 받아 적용했으면 checksum이 어긋나 그쪽 기동이 깨진다. 새 versioned migration을 `gradlew newMigration -Pdesc=<snake_case>`로 만든다
- 권한을 화면 숨김으로 처리하지 않는다. Spring Security에서 `ADMIN`을 강제한다
- 회원 소유 자원은 요청으로 온 회원 ID를 믿지 않고 Service에서 인증 사용자 기준으로 검증한다

## 빌드와 커밋

- `.\gradlew.bat test`(Windows) 또는 `./gradlew test`(mac·Linux). `bootRun` · `build`도 같다. Testcontainers 테스트에는 Docker 필요
- 커밋 제목은 `<type>: 한글 요약`. type은 `feat` `fix` `refactor` `test` `docs` `ci` `chore`
- PR 하나에는 목적 하나. 상세는 `docs/pull-request.md`, 테스트 규약은 `docs/testing.md`

## 여기 적지 않는 것

**협의 시점·승인 조건·branch protection 설정은 사본을 두지 않는다.** 최근에 바뀌었고 또 바뀐다 — 사본이 있으면 그게 다음 드리프트다. 그때 `AGENTS.md`와 `docs/pull-request.md` 4절을 읽는다.

담당 외 도메인에 **클래스나 공개 Service 메서드를 새로 만들 때** 다는 헤더 주석 형식도 `AGENTS.md`에 있다.

## 문서를 넓게 읽지 않는다

**한 번 읽은 문서는 세션이 끝날 때까지 컨텍스트에 남는다.** 내려놓을 방법이 없으므로 절약은 "나중에 비우기"가 아니라 **"애초에 안 읽기"로만 된다.**

- 지정되지 않은 문서는 열지 않는다. 필요해지면 그때 연다.
- 여러 문서를 훑어 결론만 필요한 조사는 **서브에이전트에 맡긴다** — 자기 컨텍스트에서 읽고 결론만 돌려주므로 원문이 이쪽에 쌓이지 않는다. 다만 **그 문서를 근거로 코드를 써야 하면 결국 다시 읽게 되므로 구현에는 쓰지 않는다.**
