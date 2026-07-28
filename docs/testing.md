# cakeshop 테스트 작성 규칙

- **상태**: 입문자 팀 기준 운영본
- **범위**: Java · Spring MVC · Spring Security · MyBatis · MariaDB 테스트와 CI 실행 기준
- **관련 문서**: [코드 컨벤션](conventions.md) · [PR 규칙](pull-request.md)

---

## 0. 지금 지킬 것 (필수 5개)

**이 5개가 [필수]의 전부다.** 나머지 절은 전부 [권장] 또는 참고용이며, 지키지 못했다고 PR이 막히지 않는다.

| # | 규칙 | 이유 |
|---|---|---|
| 1 | 새로 만들거나 고친 기능에 **테스트를 최소 1개** 함께 넣는다. 정상 경로 하나여도 된다. | 테스트 습관의 시작점 |
| 2 | PR을 올리기 전에 **전체 테스트를 통과**시킨다 (`.\gradlew.bat test` / `./gradlew test`) | CI가 이미 같은 명령을 돌린다 |
| 3 | 실패하는 테스트를 `@Disabled`·삭제·assertion 약화로 넘기지 않는다 | 실패를 숨기는 것이 가장 위험하다 |
| 4 | 운영 DB·개인 DB·실제 외부 API에 붙지 않는다. DB가 필요하면 Testcontainers를 쓴다 | 사고 방지 |
| 5 | assertion은 **AssertJ `assertThat`**을 쓴다 | 이미 저장소 전체가 이렇게 되어 있다 |

3·4·5번은 지금도 이미 지켜지고 있다. **실질적으로 새로 요구하는 것은 1번과 2번뿐이다.**

나머지 규칙은 "테스트를 어떻게 잘 쓸까"에 대한 참고서다. 처음부터 다 지키려 하지 말고,
막힐 때 해당 절을 찾아보면 된다. 팀이 익숙해지면 항목을 하나씩 [필수]로 올린다.

---

## 1. 목적과 적용 원칙

이 문서는 테스트의 개수보다 **변경한 기능의 동작과 위험을 재현 가능하게 검증하는 것**을 목적으로 한다.

- 새 기능과 수정한 기능에는 해당 변경을 검증하는 테스트를 함께 작성한다. **[필수 — 0절 1번]**
- 테스트는 로컬과 CI에서 같은 결과가 나와야 한다.
- 테스트 하나는 독립적으로 실행할 수 있어야 하며 실행 순서에 의존하지 않는다.
- 운영 RDS, 개발자 개인 DB, 외부 API에 의존하지 않는다. **[필수 — 0절 4번]**
- 실제 시각과 네트워크 상태에 의존하지 않는다.
- 구현의 내부 모양보다 사용자가 관찰할 수 있는 결과와 업무 규칙을 검증한다.
- 버그 수정 PR에는 수정 전 실패하고 수정 후 통과하는 회귀 테스트를 먼저 추가한다.
- 테스트를 통과시키기 위해 실제 요구사항을 약화하거나 의미 없는 assertion을 작성하지 않는다. **[필수 — 0절 3번]**

### 규칙 강도

| 라벨 | 의미 |
|---|---|
| **[필수]** | 반드시 지킨다. **0절의 5개(와 12절의 금지 4개)가 전부다.** |
| **[권장]** | 특별한 이유가 없으면 지킨다. **못 지켰다고 PR이 막히지는 않는다.** |
| **[허용]** | 상황에 따라 선택할 수 있다. |
| **[금지]** | 사용하지 않는다. |
| 🔶 **합의 필요** | 도구 도입이나 팀 결정이 필요한 항목. 합의 전에는 강제하지 않는다. |

> **이 문서에서 라벨이 없는 문장은 모두 [권장]이다.** [필수]는 명시된 것만이다.

## 2. 테스트 종류와 선택 기준 **[권장]**

가장 작은 범위로 필요한 동작을 증명할 수 있는 테스트를 선택한다. 넓은 범위의 테스트 하나로 모든 경우를 대신하지 않는다.

| 대상 | 기본 테스트 | 실제로 사용하는 것 | 검증 목적 |
|---|---|---|---|
| 순수 업무 규칙, enum, 값 계산 | JUnit 단위 테스트 | 실제 대상 객체 | 경곗값, 계산, 상태 전이 |
| Service | Mockito 단위 테스트 | 실제 Service + mock 의존성 | 업무 흐름, 예외, 협력 객체 호출 |
| Controller | `standaloneSetup` (기본) | 실제 Controller + mock Service | 바인딩, 검증, View·redirect, 소유권 분기 |
| Mapper XML | `@MybatisTest` 통합 테스트 | 실제 Mapper + MariaDB Testcontainers | SQL, 매핑, 정렬, 필터, 페이징 |
| Security 설정 | Spring Security 통합 테스트 | 실제 보안 설정 | URL 패턴별 인증·역할 접근, CSRF |
| 전체 애플리케이션 흐름 | `@SpringBootTest` | 필요한 실제 Bean | 여러 계층을 건너는 핵심 시나리오 |
| 파일 저장소 등 인프라 | 단위 또는 통합 테스트 | 임시 디렉터리·대역 서버 | 경로, 실패 처리, 자원 정리 |

- `@SpringBootTest`는 전체 Context가 필요한 이유가 있을 때만 사용한다. (느리다)
- Service 업무 규칙은 Controller 테스트에서 간접 검증하지 않고 Service 테스트로 분리한다.
- Mapper SQL은 mock으로 검증하지 않고 MariaDB Testcontainers 기반 통합 테스트로 검증한다.
- 동일한 세부 동작을 여러 계층에서 중복 검증하지 않는다. 각 계층의 책임과 연결 계약만 검증한다.

### Controller 테스트 방식

**기본은 `MockMvcBuilders.standaloneSetup`이다.** 준비가 간단하고 빠르며, 현재 저장소의 Controller 테스트도
모두 이 방식이다. 바인딩, Bean Validation, View·redirect 선택은 이것으로 충분하다.

역할별 401/403, CSRF, `@AuthenticationPrincipal` 주입을 **직접 검증해야 할 때만** `@WebMvcTest` +
Security 설정 import를 쓴다. **[권장]** — 독립형은 Security 필터를 타지 않으므로 이 항목들은 검증 자체가
되지 않는다(통과해도 의미 없음). 즉 "안 쓰면 안 된다"가 아니라, **쓰지 않았다면 인가 검증을 한 것이
아니라는 사실을 알고 있으면 된다.**

### 인증·인가 검증의 소유 계층

| 검증 항목 | 소유 |
|---|---|
| URL 패턴 단위 접근 제어 (`/admin/**`은 ADMIN만 등), 로그인·로그아웃, CSRF 전역 정책 | Security 통합 테스트 |
| 같은 URL 안에서의 소유권 분기 (내 주문 / 남의 주문), 회원 상태별 업무 거부 | Controller·Service 테스트 |

같은 검증을 두 계층에서 반복하지 않기 위한 구분이다.

> ⚠️ **현재 저장소에는 Security 통합 테스트가 없다.** 즉 위 표의 첫 줄은 아직 아무도 검증하지 않고 있다.
> 17절 "알려진 공백" 참고.

## 3. 위치와 이름 **[권장]**

- 테스트 파일은 대상 코드와 같은 패키지 구조를 `src/test/java` 아래에 따른다.
- 테스트 클래스명은 `<대상클래스명>Tests`로 작성한다. (현재 100% 지켜지고 있다)
- 테스트 메서드명은 `대상_조건이면_결과()` 흐름이 읽히는 lowerCamel + 밑줄 구분을 권장한다.
    - 예: `updateStore_rejectsDuplicateHoliday()`, `suspendedMember_cannotLogIn()`
- `test1`, `successTest`, `normalCase`처럼 의도를 알 수 없는 이름은 쓰지 않는다.
- `@DisplayName`은 메서드명만으로 의도가 충분하지 않을 때 사용할 수 있다. **[허용]**

> 기존 테스트는 이 명명 규칙이 아직 정착되지 않았다. 일괄 변경하지 않고 새로 쓰는 테스트부터 적용한다.

## 4. 테스트 구조 **[권장]**

테스트 본문은 Given-When-Then 구조를 기본으로 한다.

```java
@Test
void addHoliday_rejectsDuplicateDate() {
    // given
    // 테스트 입력과 협력 객체 동작 준비

    // when
    // 검증할 동작 한 번 실행

    // then
    // 반환값, 상태 변화, 예외 또는 필요한 협력 호출 검증
}
```

- 한 테스트는 하나의 시나리오와 하나의 실패 원인을 갖도록 작성한다.
- 준비 코드가 길면 의미 있는 fixture 메서드나 Object Mother로 분리할 수 있다. **[허용]**
- fixture 메서드는 기본값이 무엇인지 알 수 있게 작성하고, 테스트에 중요한 값은 호출부에 드러낸다.
- `@BeforeEach`에는 모든 테스트가 공통으로 필요로 하는 최소 설정만 둔다.
- 실행 흐름을 감추는 과도한 공통화는 하지 않는다.

## 5. 변경 유형별 최소 테스트 **[권장]**

기계적으로 채우는 체크리스트가 아니다. **변경으로 생긴 위험에 해당하는 항목을 골라서** 쓴다.
처음이라면 각 분류에서 1~2개만 골라도 충분하다.

### 조회 기능

- 결과가 있는 경우와 없는 경우
- 공개 여부, 회원 소유권, 상태값 등 조회 조건
- 정렬 순서, 필터 조합과 경곗값
- 페이징 첫 페이지, 마지막 페이지, 범위를 벗어난 페이지

### 생성·수정·삭제 기능

- 정상 처리 결과
- 필수 입력 누락과 형식 오류
- 존재하지 않는 대상, 중복 데이터
- 권한이 없는 사용자와 다른 사용자의 자원
- 처리할 수 없는 현재 상태
- 여러 저장 작업 중 실패했을 때 전체 rollback

### 상태 전이

- 허용된 전이와 금지된 전이
- 최종 상태에서의 추가 변경 거부
- 전이에 따른 부수 효과가 있다면 그 결과

### 금액·수량·날짜 계산

- 0, 최소값, 최대 허용값과 경계 바로 전후
- 음수와 overflow 가능성, 반올림 단위
- 날짜 경계, 마감 시각, 휴무일
- 고정된 시각과 timezone

### 인증·인가

- 비로그인 사용자 / 일반 회원 / 관리자
- 정지·탈퇴 등 로그인할 수 없는 회원 상태
- 본인 자원과 타인 자원
- 상태를 변경하는 요청의 CSRF 검증

(각 항목의 소유 계층은 [2절](#2-테스트-종류와-선택-기준-권장)의 소유 표를 따른다.)

## 6. 계층별 작성 규칙 **[권장]**

### Controller

변경과 관련된 항목을 검증한다.

- 요청 URL과 HTTP method, 파라미터·Form 바인딩
- Bean Validation 실패 시 같은 화면과 필드 오류 반환
- 정상 처리 시 View 이름 또는 PRG redirect 경로
- Model과 Flash Attribute의 공개 계약 (`successMessage` / `errorMessage`)
- 소유권·회원 상태에 따른 분기
- 검증 실패 시 Service를 호출하지 않는 것

Controller의 private 구현, 지역 변수, 내부 호출 순서는 검증하지 않는다.
View 이름을 검증할 때는 그 문자열이 실제 존재하는 템플릿 경로인지까지 확인하면 좋다
(존재하지 않는 View 이름으로 통과하는 테스트를 막는다).

### Service

Service 테스트는 업무 규칙을 가장 상세하게 검증한다. **테스트를 하나만 쓸 여유가 있다면 여기에 쓴다.**

- 정상 업무 흐름과 반환값
- 입력 및 조회 결과의 경곗값
- 상태 전이와 권한·소유권 규칙
- `BusinessException`의 구체적인 ErrorCode
- 쓰기가 필요한 협력 객체 호출과 실패 시 중단 여부
- 조회 결과가 없을 때의 동작

mock 호출 검증은 업무 결과를 증명하는 데 필요한 호출에만 사용한다. 단순 getter, 호출 순서,
모든 인자를 무조건 `verify`하는 과잉 검증은 하지 않는다.

트랜잭션 rollback 자체는 mock 단위 테스트로 증명할 수 없다. 여러 쓰기를 하나로 묶는 기능은
별도의 Spring 통합 테스트로 rollback을 검증한다. **[권장]** (현재 이런 테스트는 아직 없다 — 17절 참고)

### Mapper

Mapper 테스트는 실제 Mapper XML과 MariaDB Testcontainers에서 SQL을 실행한다.
([9절](#9-데이터베이스-테스트-환경) 참고, `ProductMapperTests`가 참고 예제다.)

- 컬럼과 Java 필드 매핑, enum·날짜·금액 타입 매핑
- insert 후 생성된 PK, update·delete 영향 행 수
- 동적 조건 조합, 공개 상태 필터와 소유권 조건
- 정렬과 페이징, JOIN으로 인한 중복 또는 누락

Mapper 테스트 데이터는 테스트가 직접 준비한다. 고정 ID 가정, 로컬 seed 데이터,
다른 테스트가 만든 데이터에 의존하지 않는다.

### Entity·enum·순수 객체

- 계산이나 상태 전이 메서드가 있으면 순수 단위 테스트를 작성한다.
- getter/setter, Lombok 생성 결과, 단순 생성자 자체는 테스트하지 않는다.
- 상태 enum은 허용 전이와 금지 전이를 함께 검증한다. 경우의 수가 반복되면 `@ParameterizedTest`를 쓸 수 있다. **[허용]**

### 예외 처리

`GlobalExceptionHandler`는 예외 종류별 HTTP 상태, View 또는 응답 형태, 사용자 공개 메시지를 검증한다.
내부 예외 메시지, SQL, 개인정보가 응답에 노출되지 않는지도 보안상 중요한 경로에서 확인한다.

## 7. 테스트 대역 사용 규칙 **[권장]**

- mock은 테스트 대상의 외부 협력 객체에만 사용한다. 테스트 대상 자체나 private 메서드를 mocking하지 않는다.
- 값 객체와 DTO는 실제 객체를 사용한다.
- `lenient()`와 광범위한 `any()`는 필요한 이유가 있을 때만 사용한다. 사용하지 않는 stubbing은 제거한다.
- 구현 순서가 업무 계약이 아닌 경우 `InOrder`로 고정하지 않는다.
- 반환값·상태 검증만으로 충분하면 불필요한 `verify`를 추가하지 않는다.
- 외부 API는 실제 호출하지 않고 client 경계에서 stub, fake 또는 대역 서버로 격리한다. **[필수 — 0절 4번]**

## 8. 테스트 데이터 규칙 **[권장]**

- 각 테스트는 자신이 필요한 데이터를 직접 생성한다.
- 핵심 조건이 아닌 값은 fixture 기본값으로 둘 수 있다. 테스트 의미에 영향을 주는 값은 메서드 안에 명시한다.
- 운영 데이터와 동일한 개인정보를 복사하지 않는다.
- 이메일, 전화번호, 파일명 등 unique 값은 테스트 안에서 충돌하지 않게 생성한다.
- 자동 생성 PK 값을 예상하지 말고 insert 결과로 받은 값을 사용한다.
- 현재 시각이 필요한 업무 로직은 `Clock`처럼 주입 가능한 시간 경계를 사용하고 테스트에서는 고정한다.
  `Thread.sleep()`으로 시간을 맞추지 않는다.
- 무작위 값이 꼭 필요하면 seed를 고정하고 실패 로그에서 재현할 수 있게 한다.

## 9. 데이터베이스 테스트 환경

**"DB에 붙는 테스트는 Testcontainers로 한다"는 [필수]**(0절 4번)이고, 아래 세부 사항은 [권장]이다.
공통 설정이 이미 있으므로 대부분은 **애노테이션만 붙이면 된다.**

```java
@MybatisTest
@MariaDbIntegrationTest                              // 컨테이너 + test profile
@AutoConfigureTestDatabase(replace = Replace.NONE)
class XxxMapperTests { ... }
```

- `com.cakeshop.global.config.MariaDbIntegrationTest` — `@ActiveProfiles("test")` + 컨테이너 설정 import를
  묶은 메타 애노테이션. 통합 테스트는 이것만 붙인다.
- `com.cakeshop.global.config.MariaDbTestContainerConfig` — `@ServiceConnection`으로 datasource가 자동 연결된다.
  **접속 정보를 직접 쓸 일이 없다.**

### 규칙

- Mapper·트랜잭션 rollback·Flyway migration 통합 테스트는 MariaDB Testcontainers를 사용한다.
- 테스트는 운영 RDS나 개발자 개인 DB에 연결하지 않는다. **[필수]**
- `local`, `rds` profile을 DB 테스트에 사용하지 않는다. Testcontainers용 `test` profile을 사용한다.
- datasource URL·사용자·비밀번호·포트는 container가 제공한 값을 사용한다. 고정 접속 정보를 저장소에 쓰지 않는다.
- MariaDB image는 명시적인 버전으로 고정한다(현재 `mariadb:11.4.10`). `latest` tag는 **[금지]**.
- container 선언과 datasource 연결은 위 공통 설정 한 곳에서만 관리한다. 테스트 클래스마다 따로 만들지 않는다.
- container는 테스트 메서드마다 새로 시작하지 않고 Spring Context 단위로 공유한다.
- 테스트 간 데이터 격리는 다음 중 하나로 보장한다:
  1. 트랜잭션 rollback (`@MybatisTest`는 기본 rollback)
  2. 명시적 정리 (`@AfterEach` 삭제)
  3. **고유 접미사로 논리적 분리** — 예: `"name-" + System.nanoTime()`.
     `ProductMapperTests`가 쓰는 방식이며 허용된다. **[허용]**
- schema는 애플리케이션이 사용하는 Flyway migration으로 생성한다. seed(`db/seed/seed-local.sql`)에 의존하지 않는다.
  - 시드가 꼭 필요한 소수의 테스트는 `@Sql(scripts = "classpath:db/seed/seed-local.sql", executionPhase = BEFORE_TEST_CLASS)`로 직접 넣는다 (`ScreenRenderingTests`). `spring.flyway.locations`를 덮어쓰지 않는다. **[금지]**
- DB 통합 테스트는 당분간 병렬 실행하지 않는다.

### 적용 범위

- Mapper XML의 SQL·매핑·필터·정렬·페이징 검증
- 여러 쓰기 작업의 트랜잭션 rollback 검증
- 빈 DB에서 Flyway migration 전체 적용 검증 (`FlywayMigrationTests`)
- migration 파일명 규약·버전 중복 검증 (`MigrationNamingTests` — 리소스만 훑으므로 Docker 불필요)
- DB 제약조건, enum·날짜·금액 타입처럼 MariaDB 동작이 중요한 검증
- DB까지 포함해야 의미가 있는 소수의 핵심 애플리케이션 흐름

Controller·Service·Entity·enum처럼 DB가 없어도 검증할 수 있는 대상에는 Testcontainers를 쓰지 않는다(느려진다).
MariaDB 고유 문법과 실제 DB 제약을 검증해야 하므로 H2 호환 모드는 사용하지 않는다. **[금지]**

### 실행 환경 / 문제 해결

Testcontainers 테스트를 돌리려면 **로컬에 Docker가 실행 중**이어야 한다. CI도 같은 조건이다
(`.github/workflows/ci.yml`의 `docker info` 단계에서 먼저 확인한다).

| 증상 | 확인 |
|---|---|
| `Could not find a valid Docker environment` | Docker Desktop이 실행 중인지 확인 후 `docker info` |
| 첫 실행이 매우 느림 | MariaDB image 최초 다운로드. 이후에는 캐시되어 빨라진다 |
| container는 뜨는데 연결 실패 | `@MariaDbIntegrationTest`를 붙였는지, `@AutoConfigureTestDatabase(replace = NONE)`가 있는지 확인 |

Flyway와 seed 데이터의 역할 분리는 [`conventions.md`](conventions.md)의 「6-1. Flyway migration
규약」에서 확정했다. 요약하면 `db/migration`은 Flyway가 관리하는 스키마 변경과 모든 환경에
필요한 기준 데이터, `db/seed`는 Flyway 밖에서 직접 실행하는 로컬 샘플 데이터다.
개발 환경 준비 절차는 `CONTRIBUTING.md`에 기록한다(**작성 예정**).

## 10. 파일·네트워크·외부 시스템 **[권장]**

- 파일 테스트는 JUnit `@TempDir` 아래에서 실행한다. 프로젝트 디렉터리, 사용자 홈,
  공유 업로드 디렉터리에 테스트 파일을 만들지 않는다. (`LocalFileStorageClientTests` 참고)
- 테스트 종료 후 파일 핸들, 서버, container 등 자원을 정리한다.
- 실제 이메일, 결제, 알림, 클라우드 저장소를 호출하지 않는다. **[필수 — 0절 4번]**
- 외부 시스템의 timeout·오류·중복 응답처럼 업무에 영향을 주는 실패 경로를 대역으로 검증한다.
- 단위·Controller·Service 테스트는 네트워크 없이 실행되어야 한다.

## 11. Assertion 규칙

- **AssertJ `assertThat`을 사용한다.** **[필수 — 0절 5번]**
  `assertThat(actual).isEqualTo(expected)` 형태라 기대값·실제값 방향 혼동이 없다.
  (spring-boot-starter-test에 포함되어 있어 추가 의존성이 없다.)
- JUnit `assertEquals` 계열은 신규 코드에서 사용하지 않는다. **[금지]** (현재 저장소에 0건)

아래는 [권장]이다.

- 컬렉션은 크기만 확인하지 말고 필요한 원소와 순서도 검증한다. (`containsExactly` 등)
- 예외는 타입만 확인하지 말고 업무 예외라면 ErrorCode도 검증한다.
- `assertThat(x).isNotNull()` 하나로 업무 결과 검증을 끝내지 않는다.
- 여러 독립 결과를 함께 보여야 하면 `assertAll` 또는 AssertJ soft assertion을 쓸 수 있다. **[허용]**
- 소수점 금액은 double 단순 동등 비교를 피하고 프로젝트의 금액 타입과 반올림 규칙에 맞게 검증한다.
- 전체 HTML 문자열·snapshot 비교는 사소한 마크업 변경에 취약하므로 핵심 요소와 공개 계약만 검증한다.

## 12. 안정성과 실행 시간

### 절대 하지 않는 것 **[필수]**

1. `@Disabled`로 실패 은폐
2. 실패 테스트를 삭제하거나 assertion을 약화해 CI 통과
3. 운영·공용 DB 접근
4. 실제 외부 API 호출

### 피해야 하는 것 **[권장]**

- 테스트 실행 순서 의존
- `Thread.sleep()`을 이용한 동기화
- 고정 포트 사용
- 로컬 절대 경로 사용
- 실행 날짜·timezone·locale에 따라 결과가 달라지는 테스트
- 같은 원인의 flaky test를 재실행 횟수로만 숨기는 처리

가끔 실패하는(flaky) 테스트를 발견하면 혼자 붙잡고 있지 말고 팀에 공유한다.
원인은 대개 데이터 격리, 비동기 대기, 시각 의존 셋 중 하나다.

## 13. Coverage 기준 **[권장]**

- 변경한 업무 분기에는 정상 경로와 주요 실패 경로 테스트가 있으면 좋다.
- 인증, 결제, 주문 상태 전이, 금액 계산, 개인정보 처리처럼 위험이 큰 코드는
  coverage 수치보다 시나리오 누락 여부를 먼저 본다.
- DTO getter, 설정 클래스, 생성 코드의 수치를 채우기 위한 무의미한 테스트를 만들지 않는다.

🔶 **합의 필요 — JaCoCo**: 아직 도입하지 않았다. 임의의 coverage 백분율을 CI 필수 조건으로 두지 않는다.

## 14. 로컬 실행과 CI

PR을 올리기 전에 저장소 루트에서 전체 테스트를 실행한다. **[필수 — 0절 2번]**

```bash
# Windows
.\gradlew.bat test

# macOS / Linux / CI
./gradlew test
```

- `clean`은 매번 붙이지 않는다. 캐시 이상이 의심될 때만 `clean test`를 실행한다.
- 실패하면 `build/reports/tests/test/index.html`을 열어 원인을 본다.

### CI

`.github/workflows/ci.yml`이 `dev`·`main`으로의 PR과 push에서 자동 실행된다.
Java 21 + Gradle Wrapper, Docker 확인 후 `./gradlew test`, 실패 시 test report를 artifact로 올린다.

- 전체 테스트가 통과해야 merge한다.
- Mapper·통합 테스트도 CI에서 함께 실행된다. 별도 DB secret이나 상시 실행 DB가 필요 없다.
- 테스트를 생략하는 Gradle option(`-x test` 등)으로 CI를 우회하지 않는다. **[금지] [필수]**
- required check 지정과 branch protection은 [PR 규칙 문서](pull-request.md)에서 정한다.

기존 실패 테스트는 그 테스트나 관련 코드의 소유자가 수정한다.
다른 팀원의 코드·테스트를 건드려야 하면 먼저 해당 소유자와 이야기한다.

## 15. PR 작성자와 리뷰어

### PR 작성자

- 변경한 동작과 어떤 테스트를 썼는지 PR 본문에 적는다.
- 새 기능, 버그 수정, 업무 규칙 변경에 맞는 테스트를 같은 PR에 포함한다.
- 실패하거나 제외한 테스트가 있다면 숨기지 않고 원인을 적는다.
- 자신의 변경으로 기존 테스트가 실패하면 원인을 확인하고 담당자와 수정 범위를 이야기한다.

### 리뷰어

리뷰어도 처음이다. **지적이 아니라 제안**으로 남긴다.

- 테스트가 아예 없으면 "이 부분 테스트 하나 있으면 좋겠다"고 제안한다.
- 정상 경로만 있으면 떠오르는 실패 케이스를 하나만 제안한다.
- 테스트가 요구사항이 아니라 현재 구현을 그대로 베낀 것은 아닌지 본다.
- mock이 과도해 실제 wiring·SQL·보안 설정 오류를 놓치지 않는지 본다.
- AI 코드 리뷰의 지적은 참고만 하며, 테스트 통과와 사람의 승인을 대신하지 않는다.

## 16. 기존 코드에 적용하는 방법

- 이 문서 이후 새로 작성하거나 수정하는 테스트부터 적용한다.
- 기존 테스트 전체를 한 번에 이름 변경하거나 구조 변경하지 않는다.
- 기능 PR에서 관련 없는 다른 도메인의 테스트를 정리하지 않는다.
- 기존 테스트 개선은 별도 정비 PR로 진행한다.

## 17. 결정 현황과 알려진 공백

### 결정 완료

| 항목 | 결정 |
|---|---|
| MariaDB image | `mariadb:11.4.10` (`MariaDbTestContainerConfig`) |
| 공통 테스트 설정 | `@MariaDbIntegrationTest` 메타 애노테이션 |
| 테스트 profile | `test` (`src/test/resources/application-test.yml`) |
| 테스트 schema 생성 | Flyway migration (`classpath:db/migration`), seed 미적용 |
| `@MybatisTest` | 사용 가능 확인됨 (Spring Boot 4.0.2 + mybatis-spring-boot-starter-test 4.0.1) |
| Assertion | AssertJ |
| CI | `.github/workflows/ci.yml` (Java 21, `./gradlew test`) |

### 아직 안 한 것 (급하지 않음)

- JaCoCo 도입과 초기 기준
- 통합 테스트의 Gradle task 분리
- flaky test 담당자 지정과 임시 격리 절차
- required check·branch protection 적용 ([PR 규칙 문서](pull-request.md))
- `CONTRIBUTING.md`, `database.md` 작성

### 알려진 공백 (담당 미정, 지금 강제하지 않음)

- **Security 통합 테스트가 없다.** URL 패턴 접근 제어·CSRF·로그인 흐름이 검증되지 않고 있다.
  `@WebMvcTest` + Security 설정 import 예제를 하나 만들어 두면 이후 복사해 쓸 수 있다.
- **다중 쓰기 트랜잭션 rollback 테스트가 없다.** 여러 저장을 묶는 기능이 생기면 함께 추가한다.
- **메서드 명명 규칙(3절)이 아직 정착되지 않았다.** 새 테스트부터 적용한다.

위 항목이 자리를 잡으면 [권장] → [필수]로 올린다.
