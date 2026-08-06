# Repository Guidelines

## 프로젝트 구조와 모듈 구성

이 프로젝트는 Java 21, Spring Boot 4, Spring MVC, Thymeleaf, Spring Security, MyBatis, Flyway, MariaDB를 사용한다. 애플리케이션 코드는 `src/main/java/com/cakeshop`에 둔다. 기능은 `domain/<도메인>/` 아래에 `controller`, `service`, `mapper`, `entity`, `dto/form`, `dto/view`, `error` 패키지를 두는 수직 슬라이스 구조로 구성한다. 둘 이상의 도메인이 실제로 공유하는 기반 코드만 `global`에 둔다.

MyBatis XML은 `src/main/resources/mapper/<도메인>/`, 화면 템플릿은 `src/main/resources/templates/{admin,customer}`, Flyway migration은 `src/main/resources/db/migration`, 로컬 개발용 시드는 `src/main/resources/db/seed`에 둔다(시드는 Flyway 관리 대상이 아니다). 테스트는 `src/test/java`에서 운영 코드의 패키지 구조를 따르고, 테스트 설정은 `src/test/resources`에 둔다. `bin`, `build`, `out`은 생성 결과물이므로 직접 수정하지 않는다.

## 아키텍처 개요

```mermaid
flowchart LR
    Client[브라우저] --> Security[Spring Security]
    Security --> Controller
    Controller --> Service
    Service --> Mapper[MyBatis Mapper]
    Mapper --> DB[(MariaDB)]
    Controller --> View[Thymeleaf View]
    View --> Client
    Service --> Global[공통 인프라]
    Flyway[Flyway Migration] --> DB
```

계층을 건너뛰거나 역방향으로 참조하지 않는다. 각 도메인의 기본 구성은 다음과 같다.

```text
domain/<도메인>/
├── controller/
├── service/
├── mapper/
├── entity/
├── dto/
│   ├── form/
│   └── view/
└── error/
```

## 도메인 담당과 협업 경계

담당자는 해당 도메인의 최종 오너이자 우선 리뷰 대상이다. 담당 외 도메인도 작업할 수 있지만, 공개 Service 인터페이스나 상태 전이처럼 다른 담당자에게 영향을 주는 변경은 먼저 협의한다.

**담당 외 도메인에 클래스나 공개 Service 메서드를 새로 만들 때는 아래 헤더 주석을 단다.** 담당자가 나중에 "이건 누가 왜 넣었나"를 코드만 보고 알 수 있어야 한다. `작성자`는 만든 사람, `담당자`는 그 도메인의 오너다.

```java
/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 시은
 * 작성일 : 2026-08-06
 * 기능 : 리뷰 평점 집계 계약
 * 설명 : 후기 등록·수정·삭제·숨김 시 products 의 평균 평점과 후기 수를 다시 계산한다.
 * ******************************
 */
```

**새로 만드는 것에만 적용한다.** 저장소에 이미 이 형식을 쓰는 파일이 있지만 전부에 붙어 있지는 않으므로, 기존 파일을 소급해 고치지 않는다.

| 담당 | 도메인 |
|---|---|
| 수민 | `member`, `cart` |
| 주환 | `order`, `payment` |
| 정후 | `coupon` |
| 민정 | `chat`, `notification` |
| 현규 | `community`, `review` |
| 시은 | `product` |
| 공통 협의 | `store`, `global`, `home` |
| 후반 작업 | `statistics` |

결제 흐름은 `cart -> order/payment <- coupon`이 연결되므로 공개 인터페이스, 금액 계산, 상태 전이를 변경할 때 수민·주환·정후가 함께 검토한다. 위 표가 담당의 정본이다. 도메인 간에 무엇을 주고받을지, 재고 차감 시점처럼 아직 정하지 않은 업무 규칙은 `docs/team-plan.md`에 모아 둔다.

## 빌드, 테스트, 로컬 실행

- `.\gradlew.bat bootRun`(Windows) 또는 `./gradlew bootRun`: 애플리케이션을 로컬에서 실행한다.
- `.\gradlew.bat test` 또는 `./gradlew test`: 전체 JUnit Platform 테스트를 실행한다. MariaDB Testcontainers 테스트에는 실행 중인 Docker가 필요하다.
- `.\gradlew.bat build` 또는 `./gradlew build`: 컴파일, 테스트, 패키징을 수행한다.
- `.\gradlew.bat clean test`: 빌드 캐시 문제가 의심될 때만 결과물을 지우고 테스트를 다시 실행한다.

## 코딩 스타일과 명명 규칙

세부 기준은 `docs/conventions.md`를 정본으로 따른다. 공백 4칸, 탭 금지, K&R 중괄호, 명시적 import, 생성자 주입을 사용하며 한 줄은 120자 이내를 권장한다. 의존 방향은 `Controller -> Service -> Mapper -> DB`를 지킨다. 클래스 역할이 드러나도록 `ProductAdminController`, `ProductService`, `ProductMapper`, `ProductCreateForm`, `ProductDetailView`처럼 이름을 짓는다. MyBatis 값은 `#{}`로 바인딩하고 사용자 입력에 `${}`를 사용하지 않는다.

## 테스트 지침

`docs/testing.md`를 따른다. 테스트 클래스는 `*Tests`, 메서드는 `method_condition_expectedResult` 형식으로 작성한다. JUnit 5, AssertJ, Mockito, MockMvc, Spring Security Test를 사용한다. DB 동작은 `@MybatisTest`, `@MariaDbIntegrationTest`와 MariaDB Testcontainers로 검증한다. H2나 공용 DB로 대체하지 않는다. 정상 흐름뿐 아니라 유효성 검사, 권한, rollback, 금지된 상태 전이를 테스트한다.

## 커밋과 Pull Request

최근 이력과 `docs/pull-request.md`에 따라 커밋 제목은 `<type>: 한글 요약` 형식으로 작성한다. 예: `fix: 회원 이름 컬럼 마이그레이션 추가`. type은 `feat`, `fix`, `refactor`, `test`, `docs`, `ci`, `chore`를 사용한다.

PR 하나에는 하나의 목적만 담는다. 본문에 변경 목적, 주요 변경, 테스트 결과, DB/Flyway 영향, 집중 리뷰 사항, 관련 이슈를 작성하고 화면 변경에는 스크린샷을 첨부한다. 공유된 Flyway migration은 수정하지 말고 새 versioned migration을 추가한다. 파일명은 직접 짓지 않고 `gradlew newMigration -Pdesc=<snake_case>`로 만든다(자세한 규약은 `docs/conventions.md` 6-1절). CI 통과, 미해결 리뷰 정리, 최소 1명 승인을 병합 조건으로 한다.

## 보안과 설정

`.env`의 비밀 값을 커밋하지 않는다. 인증 정보, 개인정보, SQL, 내부 경로를 응답이나 로그에 노출하지 않는다. 회원 소유권은 Service에서 인증 사용자 기준으로 검증하고, 관리자 기능은 화면 숨김이 아니라 Spring Security에서 `ADMIN` 권한을 강제한다.

## Code Review Rules

포맷, 컴파일, 테스트처럼 결정적인 검사는 CI에 맡기고 코드 리뷰는 다음 중대한 위험에 집중한다.

### 리뷰 언어

- 코드 리뷰의 제목과 본문은 반드시 한국어로 작성한다.
- 코드, 식별자, 로그와 오류 메시지는 정확성이 필요한 경우 원문을 유지한다.

### 인증·인가와 정보 노출

- 회원 소유 자원은 요청으로 전달된 회원 ID를 신뢰하지 말고 인증된 사용자와 자원 소유권을 Service에서 검증한다.
- 관리자 기능은 화면 요소를 숨기는 데 의존하지 말고 Spring Security에서 `ADMIN` 권한을 강제한다.
- 비밀번호, 인증 정보, 개인정보, SQL, 내부 경로를 응답이나 로그에 노출하지 않는다.

### 트랜잭션과 상태 무결성

- 주문·결제·재고·쿠폰처럼 여러 쓰기와 상태 전이가 연결된 작업은 공개 Service 메서드의 단일 트랜잭션에서 현재 상태와 목표 상태를 검증한다.
- 실패 시 일부 변경만 남거나 재시도·웹훅으로 중복 반영되지 않아야 한다.
- rollback, 중복 실행, 금지된 상태 전이를 테스트한다.

### SQL과 스키마 안전성

- MyBatis에서 사용자 제어 값은 `#{}`로 바인딩하고 `${}` 치환을 사용하지 않는다.
- 정렬처럼 동적 SQL이 필요하면 허용된 enum 값을 `<choose>`로 매핑한다.
- 공유된 Flyway migration은 수정하지 않고 새 versioned migration을 추가한다.
- 스키마 변경은 기존 데이터 영향과 복구 방법을 설명하고 MariaDB Testcontainers 테스트로 검증한다.
