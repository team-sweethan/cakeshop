# cakeshop 코드 컨벤션

이 문서는 cakeshop의 구현과 코드 리뷰에서 사용하는 코드·아키텍처 규칙의 정본이다.
별도로 **권장**이라고 표시하지 않은 규칙은 필수다.

프로젝트 소개, 기술 스택, 실행 방법과 전체 구조는 [README.md](../README.md)를 먼저 본다.
이 문서는 README의 내용을 반복하지 않고 구현할 때 필요한 세부 기준만 다룬다.

## 목차

1. [문서 범위와 적용 원칙](#1-문서-범위와-적용-원칙)
2. [이름과 Java 작성 규칙](#2-이름과-java-작성-규칙)
3. [계층 책임과 패키지 배치](#3-계층-책임과-패키지-배치)
4. [Entity와 DTO](#4-entity와-dto)
5. [Controller](#5-controller)
6. [Service와 트랜잭션](#6-service와-트랜잭션)
7. [MyBatis와 데이터베이스](#7-mybatis와-데이터베이스)
8. [Flyway와 seed](#8-flyway와-seed)
9. [오류 처리](#9-오류-처리)
10. [인증과 인가](#10-인증과-인가)
11. [상태값](#11-상태값)
12. [도메인 경계와 연동](#12-도메인-경계와-연동)
13. [global과 화면 코드](#13-global과-화면-코드)

## 1. 문서 범위와 적용 원칙

이 문서는 다음 항목을 다룬다.

- Java 코드의 이름과 작성 형식
- Controller, Service, Mapper의 책임과 의존 방향
- Entity, Form, View의 경계
- MyBatis, DB 스키마, Flyway와 seed 작성 기준
- 오류 처리, 인증·인가, 상태값, 도메인 연동 기준

다음 항목은 각 문서를 정본으로 삼는다.

| 항목 | 정본 |
|---|---|
| 테스트 범위·작성법·실행 환경 | [testing.md](testing.md) |
| 브랜치·커밋·PR·리뷰·병합 절차 | [pull-request.md](pull-request.md) |
| 미결정 업무 규칙과 도메인 협업 안건 | [team-plan.md](team-plan.md) |
| Thymeleaf 화면 구조·프래그먼트·정적 자원 | [frontend-template-format.md](frontend-template-format.md) |

새 코드와 수정하는 코드에는 현재 규칙을 적용한다. 규칙 정리만을 위한 대규모 이동이나 이름 변경은
기능 변경과 분리한다. 규칙과 구현이 충돌하고 규칙이 현실에 맞지 않는다면 우회 코드를 추가하지 말고
이 문서부터 고친다.

## 2. 이름과 Java 작성 규칙

### 이름

| 대상 | 형식 | 예시 |
|---|---|---|
| 고객 Controller | `<Domain>Controller` | `ProductController` |
| 관리자 Controller | `<Domain>AdminController` | `ProductAdminController` |
| Service | `<Domain><Purpose>Service` | `ProductAdminService` |
| 조회 전용 Service | `<Domain><Purpose>QueryService` | `ProductQueryService` |
| Mapper | `<Domain><Purpose>Mapper` | `MemberMapper` |
| 요청 Form | `<Domain><Action>Form` | `ProductCreateForm` |
| 화면 View | `<Domain><Purpose>View` | `ProductDetailView` |
| 오류 코드 | `<Domain>ErrorCode` | `MemberErrorCode` |

- 이름에는 역할과 업무 목적을 드러낸다. `Manager`, `Helper`, `Util`, `Common`처럼 범위가 불분명한
  이름은 사용하지 않는 것을 권장한다.
- boolean 메서드는 `is`, `has`, `can`으로 시작한다.
- 값이 없을 수 있는 조회는 `find`와 `Optional<T>`, 반드시 있어야 하는 조회는 `get`과 도메인 예외를
  사용한다. 존재 여부와 개수는 `exists`, `count`를 사용한다.

### Java 작성 형식

- 공백 4칸으로 들여쓰고 탭을 사용하지 않는다. 중괄호는 K&R 형식을 사용한다.
- 한 줄은 120자 이내를 권장한다.
- 와일드카드 import를 사용하지 않는다.
- 의존성은 생성자로 주입한다. 필드 주입 `@Autowired`를 사용하지 않는다.
- 필드는 기본적으로 `private`로 선언하고, 변경하지 않는 의존성은 `final`로 선언한다.
- Entity와 DTO에 Lombok `@Data`를 사용하지 않는다. 필요한 기능만 선택해 사용한다.
- 주석은 코드가 이미 보여 주는 동작보다 선택 이유, 제약과 부작용을 설명한다.
- 소스 파일은 UTF-8로 저장한다.

import 순서는 자동 formatter를 도입할 때 도구 설정으로 확정한다. 그전에는 기존 파일의 순서만
바꾸는 수정은 하지 않는다.

## 3. 계층 책임과 패키지 배치

기본 의존 방향은 다음과 같다.

```text
Controller -> Service -> Mapper -> DB
```

- 상위 계층은 바로 아래 계층을 통해 작업한다. Controller가 Mapper를 직접 호출하지 않는다.
- 하위 계층이 상위 계층에 의존하지 않는다. Service와 Mapper는 Spring MVC·Thymeleaf 타입을 알지 않는다.
- 업무 코드는 `com.cakeshop.domain.<domain>` 아래에서 해당 도메인이 소유한다.
- Java Mapper 인터페이스와 MyBatis XML은 각각 `domain/<domain>/mapper`와
  `resources/mapper/<domain>`에 둔다.
- 관리자와 고객은 Controller 이름, URL과 템플릿 경로로 구분한다. 동일한 업무 규칙을 각각
  구현하지 않는다.
- 역할별 하위 패키지는 실제로 파일이 많아 탐색이 어려울 때 추가한다. 예상만으로 패키지를 만들지 않는다.

## 4. Entity와 DTO

### Entity

Entity는 DB 한 행을 표현하는 MyBatis용 객체다.

- DB 컬럼에 대응하는 값만 둔다. 화면 문구, UI 상태와 Bean Validation을 넣지 않는다.
- JPA 애너테이션을 사용하지 않는다.
- HTTP 요청 객체나 화면 출력 객체로 재사용하지 않는다.
- 비밀번호 해시와 개인정보를 가진 Entity를 웹 계층에 노출하지 않는다.
- 공통 필드를 묶기 위한 상속은 실제 중복과 변경 이점이 확인될 때만 도입한다.
- 필요한 접근자만 연다. MyBatis 매핑을 이유로 모든 필드의 setter를 열 필요는 없다.

Entity 생성 방식은 생성자, 정적 팩터리 등 한 가지 형식을 전역 규칙으로 강제하지 않는다. 객체가
유효한 상태로 만들어지고 호출 의도가 분명한 가장 단순한 방식을 선택한다.

### Form DTO

- `dto/form`에 두고 HTTP 요청 바인딩과 입력 형식 검증에 사용한다.
- 변경 가능한 일반 class로 작성한다.
- `@NotBlank`, `@Size`, `@Email` 같은 Bean Validation은 Form에 둔다.
- DB 조회, 소유권, 현재 상태처럼 업무 정보가 필요한 검증은 Service에서 수행한다.

### View DTO

- `dto/view`에 두고 화면에 필요한 값만 제공한다.
- 불변 `record`를 기본으로 하되 프레임워크 제약이 있으면 일반 class를 사용할 수 있다.
- Entity 전체를 필드로 포함하지 않는다.
- 표시용 라벨과 파생값은 View DTO 또는 값을 소유한 enum에서 만든다.

## 5. Controller

Controller는 요청을 웹 계층의 입력으로 바꾸고 Service 결과를 응답으로 만드는 역할만 맡는다.

- 요청 바인딩, 입력 형식 검증, 인증 사용자 확인, Service 호출, View·redirect 선택만 수행한다.
- 업무 계산, 소유권 판단과 상태 전이를 구현하지 않는다.
- Form은 `@Valid`와 `BindingResult`를 함께 사용한다.
- 성공한 POST는 PRG(Post-Redirect-Get)를 적용한다. 입력 검증 실패는 redirect하지 않고 Form을
  그대로 다시 렌더링한다.
- 사용자가 화면에서 고칠 수 있는 입력 오류만 `BindingResult`에 연결한다. 권한·소유권·상태 오류를
  입력 오류로 바꾸어 숨기지 않는다.
- `catch (Exception)`으로 모든 오류를 한꺼번에 처리하지 않는다.
- URL 식별자는 명시적인 `@PathVariable("name")`을 사용한다.

공통 알림 fragment를 사용하는 화면은 성공 메시지를 `successMessage`, 실패 메시지를
`errorMessage` Flash Attribute로 전달한다.

## 6. Service와 트랜잭션

Service는 업무 규칙과 트랜잭션 경계를 소유한다.

- 조회 작업에는 `@Transactional(readOnly = true)`, 쓰기 작업에는 `@Transactional`을 사용한다.
- 하나의 업무 작업에 여러 Mapper 호출이 필요하면 공개 Service 메서드의 단일 트랜잭션으로 묶는다.
- Controller와 Mapper에 `@Transactional`을 붙이지 않는다.
- 상태 변경 전에 현재 상태, 요청 주체와 목표 상태를 검증한다.
- 요청으로 받은 회원 ID, 권한, 상태, 금액을 신뢰하지 않는다. 인증 사용자와 저장된 값을 기준으로
  다시 검증한다.
- 조건부 UPDATE·DELETE의 갱신 행 수가 0이면 성공으로 처리하지 않는다.
- 재시도나 웹훅이 가능한 작업은 중복 실행돼도 같은 변경이 다시 반영되지 않도록 설계한다.
- Service는 `Model`, `RedirectAttributes`, `HttpServletRequest`, `HttpSession`에 의존하지 않는다.

여러 도메인의 쓰기가 하나의 업무를 이루면 요청을 시작한 Service가 전체 트랜잭션을 소유한다.
잠금 순서, 실패 시 rollback 범위와 중복 실행 방식을 구현 전에 정한다.

## 7. MyBatis와 데이터베이스

### MyBatis

- Mapper 인터페이스에는 `@Mapper`를 붙이고 SQL은 XML에 작성한다.
- Mapper 메서드명과 XML statement id를 일치시킨다.
- `SELECT *`를 사용하지 않고 필요한 컬럼을 명시한다.
- 사용자 입력은 `#{}`로 바인딩한다. `${}` 치환은 사용하지 않는다.
- 동적 정렬은 허용한 값만 enum 등으로 제한하고 XML `<choose>`로 SQL 컬럼에 매핑한다.
- 단건 조회에서 값이 없을 수 있으면 `Optional<T>`를 반환한다.
- 생성 키가 필요하면 `useGeneratedKeys="true" keyProperty="id"`를 사용한다.
- 자동 매핑으로 의미가 불분명하거나 생성자 매핑이 필요하면 `resultMap`을 명시한다.

### 데이터베이스

- PK는 `BIGINT AUTO_INCREMENT`, 컬럼명은 `id`, Java 타입은 `Long`을 기본으로 한다.
- 테이블과 컬럼은 `snake_case`, Java 필드는 `camelCase`를 사용한다.
- 새 테이블 이름은 복수형을 사용한다. 기존 단수형 테이블은 이름 통일만을 위해 변경하지 않는다.
- 생성·수정 시각은 `created_at`, `updated_at`과 `DATETIME(6)`을 사용한다.
- 생성 시각은 `DEFAULT CURRENT_TIMESTAMP(6)`, 수정 시각은
  `ON UPDATE CURRENT_TIMESTAMP(6)`로 DB가 관리한다.
- enum은 이름을 `VARCHAR`로 저장한다. ordinal 숫자와 한글 라벨을 저장하지 않는다.
- 소프트 삭제는 이력 보존이나 복구 요구가 있는 데이터에만 도입한다.
- 제약조건 이름은 목적과 대상을 알 수 있게 작성한다. 상태 컬럼은 11절의 규칙을 따른다.

## 8. Flyway와 seed

- migration 파일은 직접 만들지 않고 Gradle의 `newMigration` 태스크로 생성한다. 운영체제별 명령은
  [Flyway migration 작성 가이드의 파일 생성](flyway_make_sample.md#2-파일-생성)을 따른다.
- 생성된 `V<yyyyMMdd>_<HHmmss>__<snake_case>.sql` 이름을 임의로 바꾸지 않는다.
- 공유 브랜치에 반영된 versioned migration은 수정하지 않는다. 변경은 새 migration으로 추가한다.
- 한 migration은 하나의 배포 가능한 스키마 전환을 표현한다. SQL 문장 수만으로 파일을 합치거나
  나누지 않는다.
- 서로 의존하는 여러 migration은 같은 PR에서 version 순서를 확인한다. 단계별로 나누는 경우 각 단계만
  반영된 상태에서도 애플리케이션이 정상 동작해야 한다.
- 모든 환경에서 필요한 기준 데이터는 versioned migration으로 관리한다.
- 로컬 확인용 샘플 데이터는 `src/main/resources/db/seed`에 두고 migration에 넣지 않는다.
- seed는 반복 실행해도 같은 결과가 되도록 작성하며 Flyway 이력 테이블을 변경하지 않는다.
- 애플리케이션의 Flyway 자동 실행은 `local`, `test`에서만 허용한다. 공용 DB는 검토된 별도 절차로
  반영한다.

migration의 변경 단위, 기존 데이터 처리, MariaDB DDL의 부분 실패·복구와 검증 방법은
[Flyway migration 작성 가이드](flyway_make_sample.md)를 따른다. 스키마 변경 PR은 기존 데이터 영향과
실패 시 복구 방법을 설명한다.

## 9. 오류 처리

- 업무 규칙 위반은 `BusinessException`과 도메인별 `ErrorCode`로 표현한다.
- 도메인 오류 코드는 해당 도메인의 `error` 패키지에 둔다.
- 오류 코드는 `<DOMAIN>_<3자리 번호>` 형식을 사용하고 메시지와 HTTP 상태를 함께 가진다.
- 사용자 메시지와 내부 진단 정보를 구분한다.
- 예외 메시지와 로그에 비밀번호, 인증 정보, 개인정보, SQL과 내부 경로를 포함하지 않는다.
- `BusinessException`, `ErrorCode`, `GlobalExceptionHandler` 같은 공통 기반만 `global.error`에 둔다.

## 10. 인증과 인가

- 현재 사용자는 Spring Security의 `@AuthenticationPrincipal MemberDetails`로 받는다.
- Controller가 세션 키를 직접 읽어 인증 상태를 판단하지 않는다.
- DB role은 `USER`, `ADMIN`으로 저장하고 `ROLE_` 접두어는 권한 객체를 만들 때만 붙인다.
- 로그인 실패 응답으로 이메일이나 계정의 존재 여부를 노출하지 않는다.
- 정지·탈퇴 등 로그인 가능 여부는 회원 도메인의 상태 규칙으로 판단한다.
- 관리자 URL은 Spring Security에서 `ADMIN` 권한을 강제한다. 화면 요소를 숨기는 것만으로 인가를
  대신하지 않는다.
- 회원 소유 자원은 Service에서 인증 사용자와 자원 소유자를 비교한다.

## 11. 상태값

상태의 저장값과 전이 정책은 그 데이터를 소유한 도메인이 관리한다. 저장 가능한 값은 Java enum과
Flyway migration, 실제 전이는 Service와 테스트를 함께 바꿔 일치시킨다. 상태 변경의 업무 의미와
부수효과를 사람이 읽을 설명으로 남겨야 할 때는 해당 도메인의 `DOMAIN.md`에 둔다.

- DB에는 Java enum 이름과 같은 `UPPER_SNAKE_CASE` 값을 `VARCHAR`로 저장한다. ordinal 숫자와
  사용자용 한글 라벨은 저장하지 않는다.
- `VARCHAR` 길이는 실제 enum 이름을 수용하도록 정한다. 저장소 전체에 하나의 고정 길이를 강제하지 않는다.
- 시작 상태가 하나로 명확하면 DB `DEFAULT`를 지정한다.
- DB에는 `chk_<table>_status` 이름의 `CHECK`로 허용 값 집합을 제한한다. enum 값을 추가·삭제할 때는
  새 Flyway migration과 DB 테스트를 함께 추가한다.
- 사용자용 라벨은 enum 또는 View DTO가 만든다. 화면 템플릿에 상태별 한글 문구를 중복 하드코딩하지 않는다.
- Service는 요청 주체, 현재 상태, 목표 상태와 상태 변경에 따른 쓰기·외부 호출을 검증한다. 상태 머신이
  필요한 도메인은 enum의 `canTransitionTo()` 같은 메서드로 순수 전이 규칙을 표현할 수 있다.
- Mapper의 조건부 `UPDATE ... WHERE status = <expected>`는 Service 판단 뒤 발생할 수 있는 동시 변경을
  막는 최종 방어다. SQL이 업무 정책의 유일한 구현이 되어서는 안 된다.
- 수량에서 계산되는 품절, 읽음 여부 같은 boolean, `type`·`category`와 다른 도메인의 상태를 편의상
  새 status enum이나 중복 컬럼으로 만들지 않는다.
- 다른 도메인의 상태가 필요하면 저장값을 복제하지 않고 소유 도메인의 공개 Service 계약으로 조회한다.
- 기존 상태값, 허용 전이, 변경 주체와 상태 변경의 부수효과를 바꿀 때는 영향을 받는 담당자와 먼저 협의한다.

## 12. 도메인 경계와 연동

데이터와 업무 규칙은 해당 데이터를 가진 도메인이 소유한다.

- 다른 도메인의 Entity와 Mapper를 직접 참조하지 않는다.
- 다른 도메인의 테이블을 직접 조회·변경하거나 JOIN하지 않는다.
- 도메인 간 호출은 소유 도메인이 제공하는 공개 Service와 필요한 최소 DTO를 사용한다.
- 연동 Service, DTO, Mapper와 XML은 데이터를 소유한 도메인에 둔다.
- 공개 메서드는 SQL 동작이 아니라 조회나 상태 변경 같은 업무 행위를 표현한다.
- 조회 계약은 `QueryService`로 분리하고 `@Transactional(readOnly = true)`를 사용한다.
- 다른 도메인의 데이터를 변경하는 계약은 `CommandService`로 의도를 드러낸다.
- Entity, 비밀번호, 인증 정보와 불필요한 개인정보를 계약 DTO에 담지 않는다.
- 필요한 계약만 만든다. 관리자·고객, Query·Command 조합을 예상만으로 미리 생성하지 않는다.

### 연동 계약 작성 책임

연동 계약은 해당 데이터를 필요로 하는 사용 도메인 담당자가 작성한다.

- 사용 도메인 담당자는 실제 연동에 필요한 Service, 최소 DTO와 전용 Mapper·XML을 데이터 소유 도메인
  아래에 새 파일로 추가한다.
- 데이터 소유 담당자가 작성한 기존 Service·Mapper·Entity는 임의로 수정하지 않는다.
- 기존 파일이나 공개 계약을 변경해야 하면 데이터 소유 담당자와 먼저 협의한다.
- 작성한 연동 계약은 데이터 소유 담당자를 리뷰어로 지정해 데이터 접근 범위와 업무 규칙을 확인받는다.
- 예상되는 연동을 미리 만들지 않고 현재 필요한 계약만 작성한다.

연동 계약 이름은 `<데이터 소유 도메인><사용 도메인><역할>` 형식을 사용한다.

- 데이터를 조회만 하는 계약은 `QueryService`로 작성하고 `@Transactional(readOnly = true)`를 사용한다.
- 데이터를 등록·수정·삭제하거나 상태를 변경하는 계약은 `CommandService`로 작성한다.
- Query와 Command가 모두 필요하면 각각 분리한다.
- 전용 Mapper와 XML은 `<데이터 소유 도메인><사용 도메인>Mapper`로 이름을 맞춘다.

예를 들어 Order가 Cart 데이터를 필요로 하면 주문 담당자가 `domain/cart` 아래에
`CartOrderQueryService`, `CartOrderCommandService`와 필요한 전용 DTO·Mapper를 작성한다.

- 계약 작성자: 주문 담당자
- 데이터 소유 담당자 및 리뷰어: 장바구니 담당자

Member가 Order 데이터를 필요로 하면 회원 담당자가 `domain/order` 아래에
`OrderMemberQueryService`와 필요한 전용 DTO·Mapper를 작성한다.

- 계약 작성자: 회원 담당자
- 데이터 소유 담당자 및 리뷰어: 주문 담당자

### 연동 계약의 협업 기준

**갈리는 기준은 파일이 새 것이냐가 아니라 담당자의 호출부가 따라 움직이느냐다.**

- 담당 외 도메인에 연동 계약을 추가할 때, 그 담당자가 작성한 코드를 한 줄도 고치지 않는다면 사전
  협의 없이 만들 수 있다. 해당 담당자를 PR 리뷰어로 지정해 확인받는다.
  - **내가 만든 연동 계약 파일에 메서드를 더하는 것도 여기에 해당한다.** 파일이 담당자의 도메인
    폴더에 있어도 그가 쓴 코드가 아니고, 메서드가 하나 느는 것만으로는 깨질 호출부가 없다.
  - 계약이 상대 도메인에 **쓰기**를 하더라도 기준은 같다. 읽기냐 쓰기냐로 갈리지 않는다.
- 기존 공개 Service 인터페이스나 상태 전이를 **변경**할 때는 영향을 받는 담당자와 먼저 협의한다.
  시그니처·반환 타입 변경과 삭제처럼 **남의 호출부가 따라 움직이는 것**이 여기다.
- 여러 도메인을 JOIN하는 ReadModel은 최초 작성과 주요 변경 때 읽는 테이블 담당자의 확인을 받는다.
- PR에서의 합의 기록과 리뷰어 지정 절차는 [pull-request.md](pull-request.md) 1절을 따른다.

### 집계 ReadModel 예외

ReadModel은 관리자 대시보드(`/admin`), 관리자 통계(`/admin/statistics`)와 관리자 통계 데이터를 만드는
집계 배치에서만 예외적으로 사용한다. 그 밖의 관리자 목록·검색, 고객 화면과 홈 화면에는 적용하지 않고
일반 도메인 연동 계약을 사용한다.

- 여러 도메인 테이블의 조회와 JOIN만 허용하며 쓰기 SQL을 두지 않는다.
- 결과는 해당 조회 전용 DTO로 반환한다.
- 권한, 상태 전이, 변경 가능 여부와 업무 규칙을 판단하지 않는다.
- 결과를 원본 도메인의 데이터 변경 근거로 사용하지 않는다.
- 원본으로부터 재생성 가능한 통계 도메인의 파생 집계 데이터를 생성·교체하는 조회 근거로 사용할 수 있다.
- 파생 집계 데이터 쓰기는 별도의 통계 도메인 Mapper에서 수행하며, ReadModel Mapper에는 쓰기 SQL을
  두지 않는다.
- `statistics` 도메인에 두고 `<Purpose>ReadModelQueryService`,
  `<Purpose>ReadModelMapper`로 이름을 짓는다.
- 최초 작성과 주요 변경 때 JOIN 대상 데이터의 소유 담당자를 리뷰어로 지정한다.
- 조회 대상 테이블의 구조와 제외 조건을 실제 MariaDB 통합 테스트로 검증한다.

## 13. global과 화면 코드

### global

다음 조건을 모두 만족하는 기반 코드만 `global`에 둔다.

1. 둘 이상의 도메인에서 실제로 사용한다.
2. 특정 도메인의 업무 의미를 포함하지 않는다.
3. 변경 영향과 관리 책임이 명확하다.

Spring·MyBatis·Web·Security 설정, 공통 오류 처리 기반과 외부 시스템 공통 어댑터는 `global`에 둘
수 있다. 도메인 Entity·DTO·상태·오류 코드와 한 도메인만 사용하는 유틸리티는 해당 도메인에 둔다.
향후 재사용 가능성만으로 공통 추상화를 만들지 않는다.

### 화면 코드

- Controller는 Entity 대신 View DTO와 화면에 필요한 선택 목록을 Model에 제공한다.
- Thymeleaf에서 DB 구조를 탐색하거나 업무 규칙을 계산하지 않는다.
- URL은 `th:href`, `th:action`으로 만들고 POST 폼에는 Spring Security의 CSRF 정책을 적용한다.
- 관리자와 고객 기능 화면은 각각 `templates/admin`, `templates/customer`에 둔다. 로그인·홈·오류 화면은
  `templates/auth`, `templates/home`, `templates/error`에 둔다.
- header, footer, alert 같은 공통 UI는 `templates/fragments`의 프래그먼트를 재사용한다.

Thymeleaf 화면 구조, 프래그먼트 계약, 정적 자원과 렌더링 검증 기준은
[frontend-template-format.md](frontend-template-format.md)를 따른다.
