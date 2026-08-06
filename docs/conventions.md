# cakeshop 코드 컨벤션

- **상태**: 정본 (2026-07-28 확정, v2 제안안 승격)
- **범위**: Java · Spring MVC · MyBatis · 패키지 · DB 스키마 · Flyway·seed · 공통 코드 기준
- **제외**: 테스트 작성 규칙, CI, PR — 각각 별도 문서에서 확정했다. [22절](#22-이-문서-밖에서-다루는-항목)에 정본 위치와 미정 항목을 정리했다
- **관련 문서**: 화면 규격 [frontend-template-format.md](frontend-template-format.md), 상태 설계 [status-design.md](status-design.md)

---

## 1. 문서 목적과 적용 원칙

이 문서는 도메인별 구현 방식이 달라지는 것을 막고 코드 리뷰의 공통 기준을 정하기 위한 **정본**이다. 상태값 설계의 상세 기준은 [status-design.md](status-design.md)를 정본으로 한다.

- 규칙 문서를 코드보다 우선하는 정본으로 삼는다. 특정 도메인의 현재 구현(store 포함)을 무조건 복사하지 않는다.
- 문서와 기존 코드가 어긋나면 **문서를 기준으로 코드를 고치는 것이 원칙**이되, 수정 시점은 [20절](#20-기존-코드-적용-방식)을 따른다. 문서 쪽이 틀렸다고 판단되면 코드가 아니라 문서 수정 PR을 먼저 올린다.
- 새 코드와 수정하는 코드부터 적용한다. 기존 코드를 컨벤션에 맞추기 위한 대규모 이동은 기능 PR과 분리한다.
- 예외가 필요하면 이유와 적용 범위를 PR에 기록하고 팀의 승인을 받는다.

### 규칙 강도 표기

| 라벨 | 의미 |
|---|---|
| **[필수]** | 예외 승인 없이 지켜야 한다. **이 문서에서 별도 표기가 없는 규칙은 전부 [필수]다.** |
| **[권장]** | 특별한 이유가 없으면 따른다. 어길 경우 PR에 이유를 한 줄 남긴다. |
| **[허용]** | 상황에 따라 선택할 수 있다. |
| **[금지]** | 사용하지 않는다. ([18. 금지 패턴](#18-금지-패턴)에 취합) |
| 🔶 **합의 필요** | 기존 코드와 충돌하거나 팀 결정이 남은 항목. 합의 전까지 강제하지 않는다. |

## 2. 기본 기술 기준

- Java 21 · Spring Boot 4 🔶 *채택 전 build.gradle과 대조해 실제 버전으로 확정한다. 문서의 버전 표기는 항상 build.gradle을 따라간다.*
- Spring MVC + Thymeleaf (서버 사이드 렌더링, 관리자/고객 화면 분리)
- Spring Security 세션 인증
- MyBatis XML Mapper + MariaDB
- 생성자 주입, Bean Validation
- JPA와 MyBatis를 혼용하지 않는다. DB 접근 방식의 정본은 MyBatis다.

## 3. 패키지 구조

도메인은 `com.cakeshop.domain.<도메인>` 아래에 수직 슬라이스로 구성한다.

```
domain/<도메인>/
  controller/      화면 요청 처리
  service/         업무 로직·트랜잭션
  mapper/          @Mapper 인터페이스 (SQL은 XML)
  entity/          DB 한 행을 표현하는 POJO
  dto/
    form/          화면 입력 + 검증 전용
    view/          화면 출력 전용 (record)
  error/           도메인별 ErrorCode enum
```

MyBatis XML은 `src/main/resources/mapper/<도메인>/XxxMapper.xml`에 둔다.

### 관리자와 고객 코드

- 관리자와 고객 화면은 **URL과 클래스명**으로 구분한다.
  - 고객: `ProductController`, URL `/products`
  - 관리자: `ProductAdminController`, URL `/admin/products`
- 같은 업무 규칙을 관리자·고객에 각각 구현하지 않는다. Service·Mapper·Entity는 원칙적으로 공유한다.
- 패키지를 `admin`/`customer`로 추가 분리하려면 도메인 전체가 같은 구조를 쓰도록 별도 구조 변경 PR에서 처리한다.

### 계층 의존 방향

```
Controller → Service → Mapper → DB
```

- Controller에서 Mapper를 직접 호출하지 않는다. 역방향 호출·계층 건너뛰기 금지.
- Service가 Thymeleaf, Model, RedirectAttributes 등 웹 타입에 의존하지 않는다.
- 다른 도메인의 Mapper·Entity를 직접 사용하지 않는다. 도메인 간 호출은 [15절](#15-도메인-간-연동)의 공개 Service 인터페이스를 사용한다.

## 4. 클래스와 메서드 네이밍

| 대상 | 규칙 | 예시 |
|---|---|---|
| 고객 Controller | `<Domain>Controller` | `ProductController` |
| 관리자 Controller | `<Domain>AdminController` | `ProductAdminController` |
| Service | `<Domain>Service` | `MemberService` |
| 조회 전용 Service | `<Domain>QueryService` | `ProductQueryService` |
| Mapper | `<Domain>Mapper` | `MemberMapper` |
| 입력 Form | 🔶 아래 참고 | — |
| 화면 View | `<Domain><Purpose>View` | `ProductDetailView` |
| 오류 코드 | `<Domain>ErrorCode` | `MemberErrorCode` |

**도메인 간 연동 계약은 이 표가 아니라 [15.2](#152-명명)를 따른다** — 이름 앞에 데이터 소유 도메인이 한 번 더 붙는다(`OrderCouponQueryService`).

### 🔶 Form 네이밍

| 안       | 형식 | 예시 | 비고 |
|---------|---|---|---|
| **확정안** | `<Domain><Action>Form` | `ProductCreateForm`, `StoreUpdateForm` | **기존 store 코드와 일치.** 리네임 불필요, View 네이밍(`ProductDetailView`)과도 도메인-선행으로 일관됨 |


### 메서드 네이밍

- boolean 메서드는 `is`, `has`, `can`으로 시작한다.
- 조회 메서드 접두어는 의미로 구분한다:
  - `find`: 값이 없을 수 있다 → `Optional<T>` 반환
  - `get`: 값이 반드시 있어야 한다 → 없으면 `BusinessException`
  - `exists` / `count`: 존재 여부 / 개수
- `Manager`, `Helper`, `Util`, `Common`처럼 범위가 불명확한 이름은 피한다. **[권장]**

## 5. Java 작성 스타일

- 들여쓰기 공백 4칸, 탭 금지. 중괄호는 K&R.
- 한 줄 120자 상한. **[권장]**
- 와일드카드 import 금지.
- 필드는 `private` 기본. 변경 불필요한 의존성·지역 변수는 `final`. **[권장]**
- 생성자 주입을 사용한다. 필드 주입 `@Autowired`는 [금지]. 주입용 생성자는 `@RequiredArgsConstructor` [허용].
- `@Data`는 Entity·DTO에 사용하지 않는다. 필요한 `@Getter`, `@Setter`만 사용한다.
- 주석은 구현을 그대로 읽는 대신 선택 이유·제약·부작용을 설명한다. **[권장]**
- 소스 파일은 UTF-8로 저장한다. `build.gradle`이 `JavaCompile`의 `options.encoding`을 `UTF-8`로 고정하고 있다. JVM 기본 인코딩에 맡기면 Windows(MS949)에서 한글 주석과 문자열이 컴파일하는 PC마다 다르게 깨진다.
- 콘솔에 그대로 찍히는 문자열(빌드 스크립트 출력, 기동 실패 안내 등)에서 **실행에 필요한 명령·경로는 ASCII로 적는다.** 콘솔 코드페이지에 따라 한글이 깨져도 조치는 읽을 수 있어야 한다.

### 🔶 합의 필요: import 순서

| 안                 | 순서 | 비고 |
|-------------------|---|---|
| 확정안 (기존 store 코드) | 표준 라이브러리 → (빈 줄) → 서드파티(lombok 등) | 현재 코드 전체가 이 순서 |
> 어느 안이든 **formatter 설정 파일과 함께 확정**한다([22절](#22-이-문서-밖에서-다루는-항목)). formatter 없이 문서로만 정하면 도메인마다 다시 어긋난다. 확정 전까지는 기존 파일의 순서를 건드리지 않는다.

## 6. 데이터베이스 규약

> v1에서 복원한 절이다. DDL을 작성하는 전 담당자에게 적용된다.

- **PK**: `BIGINT AUTO_INCREMENT`, 컬럼명 `id`, 자바 타입 `Long`.
- **네이밍**: 테이블·컬럼은 `snake_case`, 테이블명은 복수형(`members`, `products`). 자바 필드는 `camelCase`, 매핑은 `map-underscore-to-camel-case: true`가 처리한다.
  - 예외: 단일 설정 도메인인 `store` · `store_business_hour` · `store_holiday`는 기존 코드 호환을 위해 단수형을 유지한다.
- **시간 컬럼**: `created_at`, `updated_at`을 `DATETIME(6)`으로 둔다.
  - 생성 시각은 DDL의 `DEFAULT CURRENT_TIMESTAMP(6)`, 수정 시각은 `ON UPDATE CURRENT_TIMESTAMP(6)`에 위임한다.
  - `UPDATE` 문과 자바 서비스 코드에서 시간 값을 직접 세팅하지 않는다.
- **enum 컬럼**: `VARCHAR`로 저장한다. MyBatis 기본 핸들러가 enum `name()` 문자열로 저장·조회한다. `INT`(ordinal) 저장은 [금지].
- **status 컬럼 DDL** (상세 규칙은 [14절](#14-상태값-규칙)):
  - 타입 `VARCHAR(20) NOT NULL` (더 긴 값이 필요하면 그 컬럼만 늘리고 이유를 주석으로 남긴다)
  - 제약 `CONSTRAINT chk_<table>_status CHECK (status IN (...))`
  - 신규 행의 시작 상태를 `DEFAULT`로 지정 (예: `members` → `ACTIVE`)
- **소프트삭제**: 공통 규약으로 강제하지 않는다. 이력 보존이 필요한 테이블만 담당자가 판단해 도입한다. **[허용]**

### 6-1. Flyway migration 규약

- **파일명을 직접 짓지 않는다. [금지]** 여러 사람이 동시에 브랜치를 나눠 작업하면 같은 버전 번호가 나오고, Git은 파일명이 다르면 조용히 둘 다 머지한다. 충돌은 머지 뒤 앱을 띄울 때야 드러난다.
- **생성은 항상 아래 명령으로 한다.**

  ```powershell
  .\gradlew.bat newMigration -Pdesc=add_coupon_table
  ```

  `src/main/resources/db/migration/V<yyyyMMdd>_<HHmmss>__<snake_case>.sql`이 만들어진다. `-Pdesc`는 소문자 `snake_case`만 받는다. 같은 초에 만들어진 파일이 있으면 자동으로 1초 밀어서 생성한다.
- **분 단위 버전(`V20260729_1015__x.sql`)은 [금지].** Flyway는 버전 조각을 숫자로 비교하므로 초 단위와 섞이면 `1015 < 101542`가 되어 나중에 만든 파일이 먼저 실행된다. `MigrationNamingTests`가 CI에서 잡는다.
- **머지된 migration은 수정하지 않는다. [금지]** checksum이 바뀌면 팀원 전원이 로컬 DB를 다시 만들어야 한다. 변경이 필요하면 새 migration을 만든다.
- **서로 의존하는 DDL은 한 파일·한 PR에 담는다.** 타임스탬프는 "만든 시각"이라 머지 순서와 다를 수 있어 `out-of-order: true`를 켜 두었다. 파일이 나뉘면 머신마다 적용 순서가 달라질 수 있다.
- **Flyway 자동 실행 범위는 `local`, `test`로 제한한다.** `rds` 프로필에서는 Flyway를 비활성화하고, 검토·승인된 별도 반영 절차 없이 애플리케이션이 공용 DB 스키마를 변경하지 못하게 한다.
- **모든 환경의 실행에 필요한 기준 데이터는 versioned migration으로 관리한다.** 애플리케이션이
  특정 PK나 행의 존재를 전제로 한다면 로컬 seed에만 두지 않는다. `rds`에는 애플리케이션이
  자동 실행하지 않으며, 검토·승인된 별도 반영 절차에서 해당 migration을 적용한다.
- **로컬 샘플 데이터는 migration에 넣지 않는다. [금지]** `src/main/resources/db/seed/seed-local.sql`에 둔다. 이 디렉터리는 Flyway가 스캔하지 않으므로 내용을 고쳐도 DB를 다시 만들 필요가 없다. 시드는 맨 앞에서 기존 로컬 샘플 데이터를 지우고 다시 넣어 몇 번을 실행해도 결과가 같아야 한다.
- 레거시 `V0`, `V1`, `V3`은 Flyway 도입 이전에 손으로 지은 이름이라 생성기 형식과 다르다. 이후 타임스탬프 버전이 항상 더 크므로(`3 < 20260729.003452`) 순서에 문제가 없어 그대로 둔다. `MigrationNamingTests`가 이 셋만 예외로 허용한다.
- **적용된 migration의 파일명을 바꾸지 않는다. [금지]** checksum은 파일 **내용**으로 계산하므로 이름만 바꿔서는 checksum이 변하지 않는다. 대신 이력에 기록된 버전·설명과 어긋나 실패한다. 버전은 그대로 두고 설명만 바꾸면 `DESCRIPTION_MISMATCH`, 버전까지 바꾸면 이력의 기존 버전이 미해결이 되고 새 버전은 미적용으로 잡힌다.
- **Flyway 실패 중 조치가 정해진 것은 한국어 안내로 바꿔 던진다.** `global/config/FlywayConfig.java`가 `FlywayMigrationStrategy`로 `migrate()`를 감싸, 이력 테이블 부재·checksum 불일치·버전 중복을 각각의 조치와 함께 출력한다. 원인을 특정할 수 없는 오류는 원본 예외를 그대로 남긴다. 새로운 실패 유형에 조치가 정해지면 이 클래스에 error code를 추가한다.

## 7. Entity 규칙

Entity는 DB 한 행을 표현하는 MyBatis용 POJO다.

- JPA 애너테이션을 붙이지 않는다.
- DB 컬럼에 대응하는 값만 가진다. 화면 표시용 문자열·UI 상태·Bean Validation을 넣지 않는다.
- Entity를 Controller의 입력 객체로 쓰거나 Model에 담아 Thymeleaf에 직접 전달하지 않는다.
- 비밀번호 해시 등 민감한 필드를 가진 Entity는 웹 계층에 노출하지 않는다.
- 공통 BaseEntity 상속은 사용하지 않는다. 시간 필드는 필요한 Entity에 `LocalDateTime`으로 직접 선언한다.
- 접근자를 손으로 작성하지 않는다. Lombok `@Getter` / `@Setter`로 생성한다.
- **쓰지 않는 setter를 열어 두지 않는다. [권장]** 아무도 호출하지 않는 setter는 "이 객체는 언제든 바뀔 수 있다"고 말하면서 그 말을 지키는 코드가 없는 상태다.

### 7-1. setter를 어디까지 열 것인가

**MyBatis는 setter가 없어도 조회 결과를 채운다.** 실제 MariaDB로 확인한 결과다.

| POJO 형태 | 매핑 | 어떻게 |
|---|---|---|
| setter 없음, 기본 생성자 있음 | 된다 | 세터가 없는 프로퍼티는 **필드에 직접** 주입한다 (`SetFieldInvoker`) |
| 필드 전부 `final`, 전 인자 생성자만 | 된다 | **생성자 매핑**으로 채운다. 다만 인자 순서·타입에 의존하므로, 컬럼이 늘거나 순서가 바뀌면 조용히 어긋난다. 이 형태를 쓰려면 `resultMap`의 `<constructor>`로 컬럼을 명시한다 |

그래서 "조회 Entity라서 setter가 필요하다"는 말은 **성립하지 않는다.** 기준은 이렇게 잡는다.

- **쓰기 전용 파라미터 Entity**(INSERT·UPDATE에만 쓴다): 필드를 `final`로 잠근다. 생성 키를 받는 `id`에만 `@Setter`를 둔다 — `useGeneratedKeys`가 넣을 자리가 필요하다.
- **조회 결과로 매핑되는 Entity**: 클래스 단위 `@Setter`가 **기본이되 필수는 아니다.** 불변으로 만들고 싶으면 `resultMap`의 `<constructor>`로 컬럼을 명시해 매핑한다. 자동 매핑에 기대어 생성자 순서에 의존하지 않는다.

```java
// 쓰기 전용 Entity — id에만 setter, 나머지는 final
@Getter
public class Post {

    @Setter                     // useGeneratedKeys가 INSERT 후 채우는 자리
    private Long id;

    private final Long memberId;
    private final String title;

    private Post(Long id, Long memberId, String title) { ... }

    public static Post create(Long memberId, String title) {
        return new Post(null, memberId, title);
    }

    public static Post edit(Long id, Long memberId, String title) {
        return new Post(id, memberId, title);
    }
}
```

**정적 팩터리를 쓰는 이유는 용도를 이름으로 드러내기 위해서다.** 위 예에서 작성과 수정은 인자 개수가 달라 생성자 하나로는 표현되지 않고, 오버로딩하면 호출부만 봐서는 어느 쪽인지 알 수 없다. 다만 **팩터리도 위치 인자라 같은 타입끼리 뒤바꾼 호출은 여전히 컴파일된다** — `Post.edit(memberId, postId, ...)`를 막아 주지 않는다. 그것까지 막으려면 식별자를 서로 다른 값 타입으로 감싸야 하는데, 이 프로젝트는 아직 그렇게 하지 않는다. **팩터리는 의도 표현이지 타입 안전장치가 아니다.**

- `@Data`는 여전히 [금지]다. 필요한 `@Getter`, `@Setter`만 쓴다.
- **이 규칙 때문에 기존 Entity를 일괄 수정하지 않는다** ([20절](#20-기존-코드-적용-방식)). 해당 도메인을 손볼 때 함께 정리한다.

## 8. DTO 규칙

### Form DTO — `dto/form`

- HTTP 요청 입력과 입력 검증을 담당한다. 변경 가능한 일반 class로 작성한다.
- `@NotBlank`, `@Size`, `@Email` 등 Bean Validation은 **Form에만** 붙인다.
- Controller에서 `@Valid` + `BindingResult`를 함께 사용한다.
- 여러 필드에 걸친 검증(비밀번호 확인 등)은 `@AssertTrue` 또는 커스텀 검증으로 처리한다.
- 업무 상태 확인·DB 조회가 필요한 검증은 Service에서 처리한다.

### View DTO — `dto/view`

- 화면에 필요한 출력 데이터만 제공한다. 불변 `record`를 기본으로 한다.
- Entity 전체를 필드로 포함하지 않는다. 템플릿이 DB 구조를 직접 알지 않게 한다.
- 한글 라벨·파생값은 View DTO 또는 enum의 메서드가 만든다.
- 여러 테이블 조회 결과는 Service에서 하나의 View로 조합한다.

## 9. Controller 규칙

Controller의 책임은 다음으로 한정한다: 요청 값 바인딩, 입력 형식 검증, 인증 사용자 확인, Service 호출, View/redirect 선택.

- 업무 계산과 상태 전이를 Controller에 작성하지 않는다.
- 성공한 POST는 PRG(Post-Redirect-Get). 검증 실패 시에는 redirect하지 않고 입력한 form을 그대로 재렌더한다.
- 성공 메시지는 `successMessage`, 실패 메시지는 `errorMessage` Flash Attribute로 전달한다. 이 두 키는 `fragments/common/alert.html`이 읽는다.
- 화면에서 바로 수정 가능한 입력 오류만 `bindingResult.rejectValue(...)`로 해당 필드에 연결한다. 나머지 업무 예외는 GlobalExceptionHandler가 처리한다.
- `catch (Exception)`으로 모든 예외를 잡지 않는다.
- URL 식별자는 명시적인 `@PathVariable("name")`을 사용한다.
- 반환하는 View 이름은 실제 템플릿 경로와 일치시킨다.

## 10. Service와 트랜잭션 규칙

Service가 업무 규칙과 트랜잭션 경계를 소유한다.

- 조회 메서드는 `@Transactional(readOnly = true)`, 쓰기 메서드는 `@Transactional`.
- 여러 Mapper 호출이 하나의 업무 작업이면 반드시 하나의 트랜잭션으로 묶는다. 일부만 반영되는 상태를 만들지 않는다.
- 트랜잭션은 public Service 메서드에서 시작한다. Controller·Mapper에 `@Transactional`을 붙이지 않는다.
- 상태 전이는 현재 상태 → 목표 상태를 Service에서 검증한다.
- 업무 규칙 위반은 `BusinessException` + 도메인별 ErrorCode로 표현한다. `IllegalArgumentException`·일반 `RuntimeException`을 업무 오류 전달 수단으로 쓰지 않는다.
- Service가 `Model`, `HttpServletRequest`, `HttpSession`에 의존하지 않는다.

## 11. MyBatis Mapper 규칙

- Mapper 인터페이스에 `@Mapper`, SQL은 XML에 작성한다. 메서드명과 XML id를 동일하게 유지한다.
- `SELECT *`를 사용하지 않는다. 컬럼을 명시한다.
- 사용자 입력은 반드시 `#{}`로 바인딩한다. `${}`는 정렬 컬럼을 포함해 [금지].
  - 동적 정렬은 허용된 enum 값을 SQL의 `<choose>`로 매핑한다.
- 단건 조회는 `Optional<T>`를 반환한다.
- 생성 키는 `useGeneratedKeys="true" keyProperty="id"`.
- 컬럼-필드명이 다르거나 enum 변환이 불명확하면 `resultMap`을 작성한다.
- `created_at`, `updated_at`은 DB 기본값이 관리하므로 SQL에서 세팅하지 않는다. ([6절](#6-데이터베이스-규약))
- **다른 도메인의 테이블은 JOIN하지 않는다.** 연동 Mapper의 소유와 명명은 [15절](#15-도메인-간-연동), 조회 전용 예외는 [15.9](#159-통계대시보드-전용-readmodel).

## 12. 오류 처리 규칙

- 도메인 오류 코드는 각 도메인의 `error` 패키지가 소유하며 `ErrorCode` 인터페이스를 구현한다. 도메인별 오류 코드를 global에 모으지 않는다.
- 오류 코드는 `<DOMAIN>_<3자리 번호>` 형식 (`STORE_001`, `MEMBER_001` …). 각 항목은 코드·메시지·HTTP 상태를 가진다.
- 사용자에게 노출할 메시지와 내부 로그 메시지를 구분한다.
- 예외 메시지에 비밀번호, 개인정보, SQL, 내부 경로를 포함하지 않는다.
- `BusinessException`, `ErrorCode`, `GlobalExceptionHandler` 등 공통 기반만 `global.error`에 둔다.

## 13. 인증·인가 규칙

- Spring Security 세션 인증을 사용한다. 현재 사용자는 `@AuthenticationPrincipal MemberDetails`로 받는다.
- Controller에서 세션 키를 직접 읽거나 커스텀 애너테이션을 만들지 않는다.
- DB `role` 값은 접두어 없이 `USER` / `ADMIN`으로 저장한다. `ROLE_` 접두어는 `MemberDetailsService`가 권한 객체 생성 시에만 붙인다. role 컬럼에 `ROLE_`을 직접 넣지 않는다.
- 인증 실패 메시지로 이메일 존재 여부를 구분해 노출하지 않는다.
- 정지·탈퇴 회원의 로그인 허용 여부는 `MemberStatus` 업무 규칙으로 검사한다.
- 관리자 인가는 URL 숨김·버튼 비활성화가 아니라 **Security 설정에서 강제**한다.

## 14. 상태값 규칙

한 상태 컬럼은 3가지 표현을 가지며, 정본은 저장값이다.

| 표현 | 소유 | 규칙 |
|---|---|---|
| 저장값 = enum 이름 (UPPER_SNAKE) | DB `VARCHAR` + Java enum | **정본.** MyBatis가 이름으로 자동 매핑 |
| 한글 라벨 | enum `label()` / View DTO | **DB에 저장하지 않는다** |
| 전이 규칙 | enum `canTransitionTo()` + Service | DB `CHECK`는 값 집합만 검증 |

- ordinal 숫자 저장 [금지]. DDL 규칙은 [6절](#6-데이터베이스-규약) 참고.
- 재고 수량(파생값), 읽음 여부(boolean), 글 종류(type/category), 다른 도메인의 status는 내 status enum으로 만들지 않는다.
- `OrderStatus`(7개 + 전이)·`PaymentStatus`(6개)가 확정된 레퍼런스 구현이다.

> 도메인별 상태값 인벤토리, 담당자별 미확정 ☐ 항목(product_options·payment_cancellations·coupons·reviews·chat_rooms·NotificationType), 함정 분류표는 [status-design.md](status-design.md)를 정본으로 한다. 담당자 ☐ 항목의 확정·갱신도 그 문서에서 계속한다.

## 15. 도메인 간 연동

도메인 간 연동은 **데이터 소유권을 지키면서 공개 Service 계약으로만 수행한다.**

### 15.1 기본 원칙

- 데이터와 업무 규칙은 그 데이터를 **소유한 도메인**이 관리한다.
- 다른 도메인의 테이블·Entity·Mapper를 직접 참조하거나 JOIN하지 않는다.
- 도메인 간에는 공개 Service와 최소 범위의 DTO만 오간다.
- **연동 코드의 패키지와 SQL은 데이터 소유 도메인에 둔다.** 쓰는 쪽이 아니라 가진 쪽이다.

### 15.2 명명

| 종류 | 형식 |
|---|---|
| 조회 | `<소유 도메인><참조 도메인>QueryService` |
| 상태 변경 | `<소유 도메인><참조 도메인>CommandService` |
| Mapper | `<소유 도메인><참조 도메인>Mapper` |
| XML | `<소유 도메인><참조 도메인>Mapper.xml` |

**이름의 첫 도메인이 데이터와 SQL의 소유자다.** `OrderCouponQueryService`는 쿠폰이 쓰지만 주문이 소유한다. 반대로 읽으면 SQL을 엉뚱한 도메인에 두게 된다.

### 15.3 Service 책임

**QueryService** — 조회만 공개한다. `@Transactional(readOnly = true)`. 등록·수정·삭제와 상태 변경을 하지 않는다.

**CommandService** — 등록·수정·삭제와 상태 변경을 맡는다.

- 공개 메서드는 SQL 동작이 아니라 **업무 행위**를 표현한다.
- 상태 검증과 잠금에 필요한 조회는 해도 된다.
- 상태 전이·유효성 검사·예외 처리를 책임진다.

### 15.4 Mapper와 XML

- 한 연동 Mapper와 XML을 QueryService와 CommandService가 함께 써도 된다. 조회 SQL과 변경 SQL이 같은 파일에 있어도 된다.
- Mapper는 **데이터 소유 도메인 내부 Service에서만** 사용한다.
- 다른 도메인의 테이블은 조회도 변경도 하지 않는다. **예외는 15.9 하나뿐이다.**
- 파일이 지나치게 커지거나 동시 수정 충돌이 반복될 때만 담당자 합의 후 나눈다. 미리 나누지 않는다.

### 15.5 일반 기능과 관리자 기능

- **관리자 기능이라는 이유만으로 연동 Service·Mapper·XML을 새로 만들지 않는다.**
- 같은 데이터와 업무 규칙을 쓰면 같은 연동 Service를 재사용한다.
- 권한은 관리자 Controller·관리자 Service와 Spring Security에서 검증한다.
- 관리자 전용 데이터, 조회 범위, 상태 변경 규칙이 **실제로 다를 때만** 별도 계약을 합의한다.
- 일반·관리자 × Query·Command 네 조합을 미리 만들지 않는다.

### 15.6 DTO와 Entity 경계

- 다른 도메인에 Entity를 반환하거나 입력으로 받지 않는다.
- 조회 결과와 명령 입력은 필요한 최소 필드만 담는다.
- 비밀번호·인증 정보·불필요한 개인정보를 담지 않는다.
- 사용자 입력으로 넘어온 권한·상태·금액을 그대로 믿지 않는다.

### 15.7 트랜잭션

- 여러 도메인의 변경이 하나의 업무라면 **요청을 시작한 공개 Service가 트랜잭션을 소유**한다.
- 하위 CommandService는 같은 트랜잭션에 참여한다.
- 한 단계라도 실패하면 관련 변경 전체가 rollback되어야 한다.
- 상태 검증, **잠금 순서**, 중복 실행 방지 방식은 구현 전에 합의한다. 잠금 순서는 어긋나도 단일 요청에서는 결과가 같아 테스트를 통과한다.

### 15.8 적용 범위

- 필요한 계약만 만든다. "쓸 수도 있으니" 미리 만들지 않는다.
- **기존 코드를 일괄로 개명하거나 옮기지 않는다.** 신규 연동 코드부터 적용한다.
- 공개 계약과 담당 외 도메인 변경은 **데이터 소유 도메인 담당자의 확인을 받는다.**
- **구현이 아직 없으면 시그니처만 먼저 합의하고, 쓰는 쪽은 stub으로 개발을 진행한다.** 상대 도메인의 구현을 기다리며 멈추지 않는다.

### 15.9 통계·대시보드 전용 ReadModel

**통계와 대시보드**처럼 여러 도메인의 데이터를 **집계·요약**해야 하는 조회는 ReadModel로 만들 수 있다. 직접 참조·JOIN 금지의 **조회 전용 예외**다.

**여기까지다. 화면이 관리자용이라는 것은 근거가 아니다.**

| | ReadModel |
|---|---|
| 일자별 매출, 상품별 판매량, 회원 유입 추이 | **대상** |
| 관리자 후기 목록·검색, 관리자 주문 목록·검색 | **아니다** |

- 갈리는 것은 **집계·요약이냐, 한 도메인의 목록이냐**다. 관리자 후기 검색은 작성자명·상품명으로 거르더라도 결국 **후기 목록**이고, 그 목록은 리뷰가 소유한다. 조건은 각 도메인의 계약에 넘겨 ID 목록이나 페이지 결과를 받는다.
- 이 선을 "관리자 화면이면 열린다"로 넓히면 **관리자 화면 전체가 예외가 되어** 15.1이 사실상 고객 화면에만 걸린다. 관리자 쪽이 오히려 상태 전이를 많이 다루는 곳이라 방향이 거꾸로다.

- 여러 도메인의 테이블을 직접 조회하거나 JOIN해도 된다.
- **등록·수정·삭제·상태 변경 SQL을 쓰지 않는다.**
- 결과는 Entity가 아니라 그 화면·통계 전용 DTO로 돌려준다.
- 상태 전이·유효성 검증·권한 판단·업무 규칙을 여기서 처리하지 않는다.
- **ReadModel 결과를 데이터 변경 가능 여부의 근거로 쓰지 않는다.** 상태 확인이나 변경 전 검증이 필요하면 소유 도메인의 QueryService·CommandService를 쓴다.
- `@Transactional(readOnly = true)`.
- Service·Mapper·DTO·XML은 개별 소유 도메인이 아니라 **그 조회 기능을 소유한 도메인**(대개 `domain/statistics`) 아래에 둔다.
- 명명은 `<업무 목적>ReadModelQueryService`, `<업무 목적>ReadModelMapper`.
- 조회 대상 테이블의 구조 변경에 영향을 받으므로, **최초 작성과 주요 변경 때 관련 소유 도메인 담당자의 확인을 받는다.**
- 같은 조회 목적의 ReadModel을 일반용·관리자용으로 중복해 만들지 않는다.

```text
domain/statistics/
├── service/OrderStatisticsReadModelQueryService.java
├── mapper/OrderStatisticsReadModelMapper.java
└── dto/view/OrderStatisticsView.java

resources/mapper/statistics/OrderStatisticsReadModelMapper.xml
```

```sql
SELECT DATE(o.created_at)        AS orderDate,
       COUNT(DISTINCT o.id)      AS orderCount,
       SUM(p.amount)             AS paymentAmount,
       COUNT(DISTINCT o.member_id) AS purchaserCount
FROM orders o
JOIN payments p ON p.order_id = o.id
WHERE o.created_at >= #{startDate}
  AND o.created_at <  #{endDate}
  AND p.status = 'COMPLETED'
GROUP BY DATE(o.created_at)
ORDER BY orderDate
```

주문과 결제를 함께 읽지만 **읽기 위해서만** 쓴다. 주문 취소 가능 여부, 결제 상태 전이, 환불 처리 같은 업무 판단과 상태 변경은 각 소유 도메인의 공개 Service로 한다.

**JOIN을 열어도 결합은 남는다.** 원본 테이블의 어휘가 바뀌면 ReadModel이 조용히 어긋나고 컴파일도 테스트도 통과한다. 이건 규칙이 아니라 **테스트로 막는다** — 제외 대상(예: 숨김 상태, 취소된 주문)이 실제로 빠지는지를 단언에 넣는다.

## 16. global 편입 기준

다음 조건을 **모두** 만족할 때만 global에 둔다.

1. 두 개 이상의 도메인에서 **실제로** 사용한다. (향후 가능성만으로는 불충분)
2. 특정 도메인의 업무 의미를 포함하지 않는다.
3. 변경 시 영향 범위와 관리 담당자가 명확하다.

| global에 둘 수 있는 것 | global에 두지 않는 것 |
|---|---|
| Spring·MyBatis·Web 설정 | 도메인 Entity, Form, View |
| Security 기반 설정과 어댑터 | 도메인 상태 enum·업무 오류 코드 |
| 공통 예외 처리 기반 | 한 도메인만 쓰는 유틸리티 |
| 여러 도메인이 공유하는 페이징 값 객체 | 할인·재고·주문 전이 같은 업무 규칙 |
| 파일 저장소 등 외부 시스템 공통 인터페이스 | 향후 가능성만 보고 만든 추상화 |

global에서 도메인 Mapper를 직접 호출하지 않는다. 필요하면 도메인이 공개한 Service/QueryService를 사용한다.

## 17. Thymeleaf와 화면 모델 규칙

- 공통 header, footer, alert는 fragment를 재사용한다. 화면 규격 상세는 [frontend-template-format.md](frontend-template-format.md)를 따른다.
- 템플릿에서 Entity를 직접 탐색하거나 복잡한 업무 조건을 계산하지 않는다. Controller가 View DTO와 필요한 enum 목록을 Model에 제공한다.
- URL은 `th:href`, `th:action`을 사용한다. POST 폼은 Spring Security CSRF 정책을 따른다.
- 관리자·고객 템플릿은 각각 `templates/admin`, `templates/customer` 아래에 둔다.
- 템플릿 전용 JavaScript에서 API·URL 문자열을 중복 정의하지 않는다. **[권장]**

## 18. 금지 패턴

리뷰에서 이 절 번호로 바로 지적한다.

1. Controller → Mapper 직접 호출
2. 다른 도메인의 Mapper·Entity 직접 사용 ([15.9](#159-통계대시보드-전용-readmodel) ReadModel은 조회 전용 예외)
3. Entity를 요청 Form이나 화면 View로 재사용
4. Service에서 웹 객체(Model, HttpSession 등) 사용
5. 일반 `Exception`으로 업무 흐름 제어
6. `SELECT *`
7. MyBatis `${}` 사용자 입력 치환
8. 비밀번호·개인정보 로그 출력
9. 한글 상태값 DB 저장
10. ordinal enum 저장
11. 필드 주입 `@Autowired`
12. `UPDATE` 문·서비스 코드에서 `updated_at` 직접 세팅
13. 근거 없는 global 이동
14. 기능 PR 안에서 대규모 패키지 정리

## 19. 기계적 검사와 코드 리뷰의 역할

| formatter·정적 검사·CI가 확인 | 사람·AI 리뷰가 확인 |
|---|---|
| 빌드와 컴파일 | 계층 책임과 의존 방향 |
| import와 포맷 | 트랜잭션 경계 |
| 테스트 통과 | 상태 전이 검증 |
| 와일드카드 import | 인증·인가 누락 |
| 정적 분석으로 찾는 단순 위반 | Entity·민감정보 노출, 도메인 간 결합, global 편입 타당성 |

AI 코드 리뷰는 보조 수단이며 테스트, CI, 사람의 승인을 대체하지 않는다.

## 20. 기존 코드 적용 방식

1. 이 문서가 정본이다. 변경이 필요하면 문서 수정 PR을 먼저 올려 합의한다.
2. 신규 코드부터 적용한다. 기존 위반은 해당 도메인 기능을 수정할 때 함께 고친다.
3. 패키지 이동·리네임처럼 충돌 위험이 큰 정리는 별도 PR + 담당자 승인으로 진행한다.
4. 이 문서를 채택해도 기존 담당자 코드를 일괄 수정하지 않는다.

## 21. 확정이 필요한 결정

### 합의만 하면 되는 것 (충돌 없음)

- 문서가 특정 구현체보다 우선한다.
- 도메인 기본 구조는 `controller/service/mapper/entity/dto/error`로 통일한다.
- 관리자·고객은 Controller 클래스명과 URL로 구분한다.
- Entity / Form / View를 분리하고, Service가 업무 규칙과 트랜잭션을 소유한다.
- 도메인 간 연결은 공개 Service/QueryService를 사용한다.
- global 편입은 16절 기준을 만족할 때만 한다.
- 기계적으로 검사 가능한 스타일은 향후 CI에서 강제한다.

### 🔶 합의 필요 (기존 코드와 충돌 — 회의 안건)

| # | 항목 | 선택지 | 위치 |
|---|---|---|---|
| 1 | Form 네이밍 | A: `<Domain><Action>Form` (기존 코드 일치, 추천) / B: `<Action><Domain>Form` | [4절](#4-클래스와-메서드-네이밍) |
| 2 | import 순서 | A: 표준 → 서드파티 (기존 코드) / B: java → 내부 → 외부 — **formatter 설정과 함께 확정** | [5절](#5-java-작성-스타일) |
| 3 | 기술 버전 표기 | build.gradle 실제 버전과 대조 후 확정 | [2절](#2-기본-기술-기준) |

## 22. 이 문서 밖에서 다루는 항목

### 다른 문서에서 확정됨

| 항목 | 정본 |
|---|---|
| 상태값 설계 — 도메인별 status 인벤토리, 함정 분류표 | [status-design.md](status-design.md) |
| 테스트 종류별 작성 규칙과 최소 범위 | [testing.md](testing.md) |
| PR·커밋 규칙, 리뷰 요청과 병합 기준 | [pull-request.md](pull-request.md) |
| CI 필수 검사 | [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) |
| 로컬 DB 준비와 초기화 절차 | [README.md](../README.md) |

### 아직 미정

| 항목 | 내용 |
|---|---|
| 페이징 표준 | 무한스크롤/페이지 번호 택1, 카운트 쿼리·`LIMIT/OFFSET` 규약 |
| formatter·정적 분석 도구 선택 | [5절 「🔶 합의 필요: import 순서」](#-합의-필요-import-순서)와 함께 확정한다. 도구 없이 문서로만 정하면 도메인마다 다시 어긋난다 |
| branch protection·required check 적용 | 현황은 [testing.md 17절](testing.md#17-결정-현황과-알려진-공백)에서 관리한다 |
