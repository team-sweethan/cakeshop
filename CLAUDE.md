# Cakeshop

Java 21 · Spring Boot 4 · Spring MVC · Thymeleaf · Spring Security · MyBatis · Flyway · MariaDB

**규칙의 정본은 `AGENTS.md`다.** 아래는 매번 필요한 것만 추린 요약이고, **어긋나면 `AGENTS.md`가 맞다.**

## 먼저 읽을 것

| 하려는 일 | 읽을 것 |
|---|---|
| `domain/community/` 작업 | `src/main/java/com/cakeshop/domain/community/CLAUDE.md` |
| `domain/review/` 작업 | `src/main/java/com/cakeshop/domain/review/CLAUDE.md` |
| 그 밖의 도메인·공통 작업 | `AGENTS.md` |

위 두 폴더에는 작업 단위별로 읽을 문서를 지정하는 표가 있다. 그 폴더를 작업할 때는 **표가 가리키는 것만** 읽는다.

## 구조와 의존 방향

`domain/<도메인>/`에 `controller` · `service` · `mapper` · `entity` · `dto/form` · `dto/view` · `error`를 두는 수직 슬라이스. 둘 이상이 **실제로** 공유하는 것만 `global`.

MyBatis XML은 `src/main/resources/mapper/<도메인>/`, 템플릿은 `templates/{admin,customer}`, migration은 `db/migration`, 테스트는 운영 코드의 패키지 구조를 따른다.

의존 방향은 `Controller → Service → Mapper → DB`. 건너뛰거나 역방향으로 참조하지 않는다.

## 도메인 담당

| 수민 | 주환 | 정후 | 민정 | 현규 | 시은 | 공통 협의 |
|---|---|---|---|---|---|---|
| `member` `cart` | `order` `payment` | `coupon` | `chat` `notification` | `community` `review` | `product` `statistics` | `store` `global` `home` |

## 하지 않는 것

- **다른 도메인의 테이블·Mapper·Entity를 직접 참조하거나 JOIN하지 않는다.** 연동 계약의 코드와 SQL은 쓰는 쪽이 아니라 **데이터를 소유한 도메인**에 둔다 (`docs/conventions.md` 15절)
- 사용자 입력에 `${}`를 쓰지 않는다. MyBatis 값은 `#{}`로 바인딩한다
- **머지된 Flyway migration은 고치지 않는다.** 새 versioned migration을 `gradlew newMigration -Pdesc=<snake_case>`로 만든다
- 권한을 화면 숨김으로 처리하지 않는다. Spring Security에서 `ADMIN`을 강제한다
- 회원 소유 자원은 요청으로 온 회원 ID를 믿지 않고 Service에서 인증 사용자 기준으로 검증한다

## 빌드와 커밋

- `.\gradlew.bat test` · `bootRun` · `build` (Testcontainers 테스트에는 Docker 필요)
- 커밋 제목은 `<type>: 한글 요약`. type은 `feat` `fix` `refactor` `test` `docs` `ci` `chore`
- PR 하나에는 목적 하나. 상세는 `docs/pull-request.md`, 테스트 규약은 `docs/testing.md`

## 여기 적지 않는 것

**협의 시점·승인 조건·branch protection 설정은 사본을 두지 않는다.** 최근에 바뀌었고 또 바뀐다 — 사본이 있으면 그게 다음 드리프트다. 그때 `AGENTS.md`와 `docs/pull-request.md` 4절을 읽는다.

담당 외 도메인에 파일을 만들 때 다는 **헤더 주석 형식**도 `AGENTS.md`에 있다.

## 문서를 넓게 읽지 않는다

**한 번 읽은 문서는 세션이 끝날 때까지 컨텍스트에 남는다.** 내려놓을 방법이 없으므로 절약은 "나중에 비우기"가 아니라 **"애초에 안 읽기"로만 된다.**

- 지정되지 않은 문서는 열지 않는다. 필요해지면 그때 연다.
- 여러 문서를 훑어 결론만 필요한 조사는 **서브에이전트에 맡긴다** — 자기 컨텍스트에서 읽고 결론만 돌려주므로 원문이 이쪽에 쌓이지 않는다. 다만 **그 문서를 근거로 코드를 써야 하면 결국 다시 읽게 되므로 구현에는 쓰지 않는다.**
