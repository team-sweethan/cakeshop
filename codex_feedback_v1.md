# cakeshop 프로젝트 코드 평가 — Codex (v1)

> 작성일: 2026-07-28
> 대상 브랜치: `dev_common_rules_fix` (`3dcc91b`)
> 범위: 전체 소스 정적 리뷰 + `.\gradlew.bat test --no-daemon` 실행
> 참고: `claude_feedback_v1.md`의 지적을 현재 코드와 다시 대조하고 별도로 검토했다.

---

## 0. 결론

공통 규약, 에러 모델, 보안 설정, 파일 저장소 추상화처럼 **프로젝트의 뼈대는 잘 설계되어 있다.**
하지만 현재 브랜치는 테스트 소스가 컴파일되지 않아 품질 게이트가 빨간 상태이고,
회원 도메인에는 계정 상태 미반영, 서버 검증 부재, 프리뷰 보안 규칙과 컨트롤러의 불일치 등
실제 사용 전에 반드시 고쳐야 할 문제가 남아 있다.

특히 `members.status`에 `SUSPENDED`와 `WITHDRAWN`을 정의했지만 인증 과정에서 상태를 전혀 확인하지 않는다.
현재 구조에서는 DB에 존재하고 비밀번호가 맞는 정지·탈퇴 회원도 로그인할 수 있다.

**종합 판단: 구조 설계 B+ / 현재 동작 신뢰도 D+ / 규약 준수도 C+**

---

## 1. 검증 결과

### 1.1 현재 소스 규모

| 항목 | 현재 수치 |
|---|---:|
| `src/main` Java 파일 | 141개, 2,738줄 |
| 15줄 미만 Java 파일 | 87개 |
| 테스트 파일 | 11개 |
| MyBatis XML | 13개, 357줄 |
| 5줄짜리 placeholder 매퍼 XML | 10개 |
| Thymeleaf 템플릿 | 52개, 2,821줄 |
| Git이 추적 중인 `bin/` 파일 | 213개 |
| GitHub Actions workflow | 0개 |

실질 구현은 `product`, `store`에 가장 많이 집중되어 있고 `member`는 일부 실제 로직이 들어간 상태다.
나머지 다수 도메인은 화면 또는 타입 골격 위주라 “구현 완료”보다는 “통합 구조 선행”으로 보는 편이 정확하다.

### 1.2 빌드·테스트

실행 명령:

```powershell
.\gradlew.bat test --no-daemon
```

결과:

```text
> Task :compileJava
> Task :compileTestJava FAILED

MemberDetailsServiceTests.java:45:
cannot find symbol: method getMemberId()
```

운영 소스 `compileJava`는 통과했지만 테스트 소스 컴파일에 실패해 테스트는 한 건도 실행되지 않았다.

---

## 2. 잘 된 점

### 2.1 규약이 구체적이고 기준 구현체가 명확하다

`docs/conventions.md`는 패키지 구조, DTO 역할, MyBatis 작성법, 트랜잭션,
예외 처리, 상태값 저장·표시·전이 규칙을 실제 구현 가능한 수준으로 정리했다.
판단이 어려울 때 `store`를 기준 구현체로 삼도록 한 점도 팀 병렬 개발에 유용하다.

### 2.2 공통 에러 처리 구조가 확장 가능하다

`ErrorCode` → `BusinessException` → `GlobalExceptionHandler` 구조는 도메인 오류를 일관되게 처리할 수 있다.
HTTP 상태에 따라 404, 공통 4xx, 500 화면을 나누고 정적 리소스 404를 debug로 기록하는 구현도 합리적이다.

문제는 구조 자체가 아니라 `member`가 이 구조를 사용하지 않는 데 있다.

### 2.3 보안 설정의 기본 방향이 좋다

- 전체 CSRF 비활성화 대신 Toss webhook POST만 예외 처리
- BCrypt 비밀번호 해시 사용
- `/admin/**`에 `ADMIN` 역할 요구
- 역할별 로그인 성공 경로 분기
- matcher 순서와 공개 프리뷰 의도를 주석으로 설명

보안 의도는 명확하다. 다만 회원 상태와 프리뷰 경로까지 종단 간으로 연결되지는 않았다.

### 2.4 인프라와 설정의 교체 가능성을 고려했다

`FileStorageClient`와 `LocalFileStorageClient`를 분리해 향후 S3 같은 외부 저장소로 교체할 수 있다.
환경별 DB 설정과 비밀값 외부화, 업로드 제한값의 근거 주석도 잘 되어 있다.

### 2.5 `store`와 `product`가 좋은 참고 구현을 제공한다

명시적 SQL 컬럼, `#{}` 바인딩, form/view 분리, `record` 기반 출력 DTO 등
공통 규약이 요구하는 주요 패턴을 실제 코드에서 확인할 수 있다.

---

## 3. 발견된 문제

### 🔴 P1 — 머지·실사용 전에 수정

#### 3.1 테스트 소스 컴파일 실패

`src/test/java/com/cakeshop/global/security/MemberDetailsServiceTests.java`

```java
assertThat(details.getMemberId()).isEqualTo(1L);
```

현재 `MemberDetails`에는 `getMemberId()`가 없고 `getMember()`만 있다.
이 때문에 전체 테스트가 실행되기 전에 `compileTestJava`에서 중단된다.

테스트를 `details.getMember().getId()`에 맞출지, 인증 객체에 `getMemberId()` 계약을 추가할지 결정해야 한다.
컨트롤러가 전체 `Member` 엔티티에 의존하는 문제까지 고려하면 장기적으로는 인증 객체가 최소 식별 정보만 제공하는 편이 안전하다.

#### 3.2 정지·탈퇴 회원도 로그인 가능

관련 파일:

- `global/security/MemberDetailsService.java`
- `global/security/MemberDetails.java`
- `resources/mapper/member/MemberMapper.xml`

`members.status`는 `ACTIVE / SUSPENDED / WITHDRAWN`을 저장하도록 설계되어 있지만,
로그인 조회는 이메일만 조건으로 사용하고 `MemberDetails`도 모든 계정을 enabled 상태로 만든다.
조회 SQL은 `status` 자체도 선택하지 않는다.

현재 흐름:

```text
이메일 조회 성공
→ 비밀번호 일치
→ status와 관계없이 ROLE_USER 또는 ROLE_ADMIN 부여
→ 로그인 성공
```

해결 방법은 둘 중 하나로 명확히 정해야 한다.

1. 매퍼에서 로그인 가능한 상태만 조회한다: `WHERE email = #{email} AND status = 'ACTIVE'`
2. 상태를 조회한 뒤 `UserDetails`의 `enabled` 또는 `accountNonLocked`에 반영한다.

두 번째 방식은 “존재하지 않는 계정”과 “정지 계정”을 내부적으로 구분할 수 있어 운영 정책 확장에 더 적합하다.

#### 3.3 local 공개 프리뷰와 `/mypage` 컨트롤러가 충돌한다

local 프로필은 `app.mockup.public-preview: true`이고 `SecurityConfig`는 GET `/mypage/**`를 익명 허용한다.
하지만 `MyPageController`는 인증 객체가 항상 있다고 가정한다.

```java
String email = memberDetails.getUsername();
```

익명 GET `/mypage`에서는 `memberDetails`가 `null`이므로 `NullPointerException`이 발생하고 공통 500 화면으로 이어진다.
`/mypage/profile`도 같은 문제를 가진다.

`ScreenRenderingTests`에는 이 두 경로가 포함되어 있어 원래라면 발견되어야 하지만,
현재는 3.1의 테스트 컴파일 오류 때문에 해당 테스트까지 도달하지 못한다.

프리뷰용 가짜 모델을 공급하거나, 실제 데이터·인증이 필요한 `/mypage/**`를 공개 프리뷰 대상에서 제외해야 한다.

#### 3.4 프로필 수정의 비밀번호 처리에 데이터 무결성 위험이 있다

현재 화면은 이름이나 전화번호만 바꾸려 해도 현재 비밀번호와 새 비밀번호를 모두 `required`로 요구한다.
즉, 정상 UI에서는 **프로필 수정이 곧 비밀번호 변경**이다.

반대로 서버에는 Bean Validation이 없기 때문에 직접 POST 요청으로 새 비밀번호 두 필드에 빈 문자열을 보내면:

```java
"".equals("")                                  // 검증 통과
passwordEncoder.encode("")                     // 빈 문자열의 BCrypt 해시 생성
member.setPassword(passwordEncoder.encode(...));
```

결과적으로 원래 비밀번호로 로그인할 수 없게 된다.

`StringUtils.hasText(newPassword)`로 비밀번호 변경 여부를 분리하고,
변경하지 않을 때는 기존 비밀번호 컬럼을 건드리지 않아야 한다.
화면도 “기본 정보 수정”과 “비밀번호 변경”을 분리하거나 새 비밀번호를 선택 입력으로 바꾸는 것이 자연스럽다.

---

### 🟠 P2 — 규약·보안 경계 정렬

#### 3.5 회원가입의 “비밀번호 확인” 입력은 아무 기능이 없다

`signup.html`의 확인 입력에는 `name`이나 `th:field`가 없다.

```html
<input id="signup-password-confirm" type="password" required>
```

따라서 값이 서버로 전송되지 않으며 `SignupForm`에도 확인 필드가 없고 서비스의 일치 검증도 없다.
사용자가 서로 다른 두 비밀번호를 입력해도 첫 번째 비밀번호로 가입된다.

확인 필드를 `SignupForm`에 추가하고 `@AssertTrue` 같은 교차 검증을 적용해야 한다.

#### 3.6 회원 form에 서버 측 Bean Validation이 없다

`SignupForm`과 `ProfileUpdateForm`에는 `@NotBlank`, `@Email`, `@Size`, `@Pattern` 등이 전혀 없다.
컨트롤러에도 `@Valid`와 `BindingResult`가 없다.

HTML의 `required`, `minlength`, `type="email"`은 브라우저 편의 기능일 뿐 API 클라이언트나 조작된 요청을 막지 못한다.
현재 서버는 빈 이메일, 매우 짧은 비밀번호, 임의 전화번호 형식을 신뢰한다.

`spring-boot-starter-validation`은 이미 의존성에 있으므로 `store`의 form 검증 패턴을 적용하면 된다.

#### 3.7 `updateMemberInfo()`에 쓰기 트랜잭션이 없다

`join()`과 조회 메서드에는 `@Transactional`이 있지만 회원 정보 수정에는 없다.
규약은 쓰기 서비스 메서드에 `@Transactional`을 요구한다.

현재는 단일 UPDATE라 즉시 부분 커밋 문제가 드러나지 않을 수 있지만,
비밀번호 이력이나 감사 로그가 추가되면 원자성이 깨질 수 있다.

#### 3.8 엔티티가 화면 모델과 인증 객체에 직접 노출된다

`MyPageController`는 비밀번호 해시를 포함한 `Member` 엔티티를 모델에 전달한다.

```java
model.addAttribute("member", freshMember);
model.addAttribute("member", memberDetails.getMember());
```

현재 템플릿이 비밀번호를 출력하지 않아 곧바로 해시가 HTML에 노출되는 것은 아니다.
하지만 템플릿 수정 실수 하나로 민감 필드가 출력될 수 있고, `docs/conventions.md`의 명시적 금지 사항이기도 하다.

`MemberView` record를 두고 화면에 필요한 `name`, `email`, `phone`만 전달해야 한다.

#### 3.9 회원 예외 처리가 공통 체계를 우회한다

`MemberErrorCode.DUPLICATE_EMAIL`은 정의되어 있지만 사용되지 않는다.
서비스는 `IllegalArgumentException`과 `RuntimeException`을 던지고,
컨트롤러는 다시 `try/catch (Exception)`으로 모두 잡는다.

이 구조의 문제:

- 예외 타입과 HTTP 상태의 의미가 사라진다.
- 예상하지 못한 내부 예외 메시지가 회원가입 화면에 그대로 노출될 수 있다.
- `GlobalExceptionHandler`와 도메인 `ErrorCode`가 사실상 무력화된다.
- 회원가입 실패 시 redirect되어 입력값도 잃는다.

입력 검증은 `@Valid` + `BindingResult`, 업무 규칙 위반은 `BusinessException(MemberErrorCode...)`,
예상하지 못한 예외는 전역 처리기로 보내는 구조로 통일해야 한다.

#### 3.10 회원 매퍼가 규약과 엔티티 계약을 부분적으로만 지킨다

`MemberMapper.xml`의 UPDATE는 다음 값을 직접 설정한다.

```xml
updated_at = NOW()
```

DDL에는 이미 `ON UPDATE CURRENT_TIMESTAMP(6)`가 있으므로 중복이며 규약을 위반한다.
INSERT도 기본값이 있는 `created_at`을 `NOW()`로 직접 지정하고 있어 DB 기본값 위임 원칙과 일관되지 않다.

또한 `findByEmail`은 `Member`의 일부 필드만 조회하고 `resultMap`에는 `phone`과 `updatedAt` 등이 빠져 있다.
`phone`은 MyBatis 자동 매핑으로 채워질 수 있지만, 명시적 `resultMap`을 쓰면서 일부만 자동 매핑에 기대는 것은 유지보수성이 낮다.
인증 전용 조회와 회원 상세 조회를 분리하면 필요한 컬럼과 민감 정보의 경계가 더 분명해진다.

---

### 🟡 P3 — 일관성·저장소 위생

#### 3.11 `bin/` 산출물 213개가 Git에 추적되고 있다

현재 `.gitignore`의 미커밋 변경은 `.bin`을 추가했지만 실제 산출물 디렉터리는 `bin/`이다.
따라서 이 변경만으로는 아무 파일도 제외되지 않는다.

정확한 패턴은 다음과 같다.

```gitignore
bin/
```

그 후 팀에 공지하고 추적만 해제해야 한다.

```powershell
git rm -r --cached bin
```

#### 3.12 도메인 패키지 구조가 기준 문서와 다르다

`docs/conventions.md`는 `store`식 평면 구조를 표준으로 정했지만,
`product`는 `admin`과 `customer` 하위 패키지를 별도로 사용한다.

둘 다 가능한 설계지만 문서와 기준 구현이 둘로 갈리면 앞으로 구현할 도메인이 임의로 선택하게 된다.
남은 도메인의 구현량이 커지기 전에 표준을 하나로 확정해야 한다.

#### 3.13 테스트가 구현 속도를 따라가지 못하고 CI가 없다

테스트 파일은 11개지만 회원가입, 회원정보 수정, 계정 상태별 로그인 회귀 테스트가 없다.
현재 테스트 컴파일 오류도 자동 검증 workflow가 있었다면 머지 전에 차단할 수 있었다.

최소 품질 게이트로 PR마다 다음 명령을 실행하는 GitHub Actions가 필요하다.

```powershell
.\gradlew.bat test --no-daemon
```

---

## 4. `claude_feedback_v1.md`와 대조한 보정 사항

기존 피드백의 큰 방향과 회원 도메인의 규약 위반 지적은 타당하다.
다만 현재 코드 기준으로는 다음처럼 표현을 보정하는 것이 정확하다.

- 일반 브라우저 UI에서는 새 비밀번호 입력이 `required`이므로 “이름만 수정하면서 빈 비밀번호 제출”은 차단된다.
  진짜 문제는 UI가 모든 수정에 비밀번호 변경을 강제하고, 서버는 조작된 빈 문자열 요청을 막지 못한다는 것이다.
- 엔티티를 모델에 넣었다고 해시가 자동으로 HTML에 출력되지는 않는다.
  현재 위험은 규약 위반과 향후 템플릿 실수에 대한 민감 정보 노출 가능성이다.
- `IllegalArgumentException`이 모두 공통 500으로 가는 것은 아니다.
  회원 컨트롤러가 일부를 직접 잡지만, 그 결과 예외 체계가 이중화되고 예상치 못한 메시지 노출 문제가 생긴다.
- 매퍼 XML은 13개 중 10개가 5줄짜리 placeholder다.
- `.gitignore`에는 `bin/`이 필요하며 현재 미커밋된 `.bin` 패턴은 문제를 해결하지 못한다.
- 테스트를 실제 실행하면 테스트 소스 컴파일 실패가 확인된다.

---

## 5. 권장 조치 순서

| 순서 | 작업 | 완료 조건 |
|---:|---|---|
| 1 | 테스트 컴파일 복구 | `compileTestJava` 통과 |
| 2 | 정지·탈퇴 회원 로그인 차단 | 상태별 인증 테스트 통과 |
| 3 | `/mypage/**` 공개 프리뷰 정책 수정 | 익명 프리뷰 렌더링 또는 인증 리다이렉트 테스트 통과 |
| 4 | 프로필 비밀번호 변경 로직 분리 | 빈 새 비밀번호가 기존 해시를 보존하는 회귀 테스트 통과 |
| 5 | 회원가입 확인 비밀번호 + Bean Validation 적용 | 불일치·빈 값·형식 오류 테스트 통과 |
| 6 | 회원 예외·DTO·트랜잭션을 공통 규약에 맞춤 | `MemberErrorCode`, `MemberView`, `@Transactional` 적용 |
| 7 | `bin/` 추적 해제 및 CI 추가 | PR에서 전체 테스트 자동 실행 |
| 8 | 패키지 표준 확정 | 코드 구조와 `conventions.md` 일치 |

---

## 6. 총평

이 프로젝트의 가장 좋은 자산은 이미 작성된 공통 규약과 `store` 기준 구현이다.
현재 문제는 설계 지식이 없는 것이 아니라, 그 설계를 회원 도메인과 자동 검증 과정에 끝까지 연결하지 못한 데 있다.

다음 도메인 구현을 늘리기 전에 **테스트가 실제로 실행되는 상태를 먼저 복구하고,
회원 인증·수정 흐름을 기준 규약에 맞는 두 번째 모범 구현으로 만드는 것**이 가장 효율적이다.
그렇게 하면 이후 도메인이 잘못된 예외 처리와 DTO 패턴을 복제하는 비용을 크게 줄일 수 있다.
