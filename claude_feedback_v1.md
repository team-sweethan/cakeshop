# cakeshop 프로젝트 코드 평가 (v1)

> 작성일: 2026-07-28 · 대상 브랜치: `dev_common_rules_fix`
> 범위: 전체 소스 트리 정적 리뷰 (빌드/테스트 실행은 하지 않음)

---

## 0. 한 줄 요약

기반 설계는 부트캠프 수준 이상으로 잘 잡혀 있다. 다만 **13개 도메인 중 실제 구현은 3개(`product`, `store`, `member`)뿐**이고,
가장 최근 머지된 `member` 도메인이 팀 자체 규약(`docs/conventions.md`)을 여러 지점에서 벗어나 있다.
**계정 잠김을 유발하는 실제 버그**도 하나 있다.

---

## 1. 현재 진척도

| 항목 | 수치 |
|---|---|
| 자바 파일 | 약 150개 (main 3,358줄) |
| 그중 15줄 미만 **스텁** | **81개** |
| 매퍼 XML | 13개 (총 397줄) |
| 그중 빈 껍데기 | **11개** (`product` 194줄, `store` 117줄, `member` 36줄만 내용 있음) |
| 템플릿 | 60여 개 (3,078줄) — 화면은 상당히 진행됨 |
| 테스트 | 11개 파일 (product / store / 화면 렌더링에 집중) |

구현 완료: `product`, `store`, `member`, `home`
골격만 존재: `order`, `payment`, `cart`, `coupon`, `review`, `chat`, `community`, `notification`, `statistics`

---

## 2. 잘 된 점

### 2.1 공통 기반
- `global/error` — `ErrorCode` 인터페이스 + `BusinessException` + `GlobalExceptionHandler` 조합이 깔끔하다.
  상태코드별 뷰 분기(404 / 4xx / 500)와 `NoResourceFoundException`을 debug 레벨로 조용히 처리하는 부분까지 세심하다.
- `global/common/paging` (`PageRequest` / `PageResult`) — 페이징을 도메인마다 재발명하지 않도록 미리 뽑아둔 점이 좋다.
- `global/infra`의 `FileStorageClient` 인터페이스 + `LocalFileStorageClient` 구현 — 나중에 S3로 교체할 여지를 남겼다.

### 2.2 보안 설정 (`SecurityConfig.java`)
부트캠프 프로젝트에서 흔히 보이는 안티패턴을 피했다.
- `csrf.disable()` 전체 비활성화 대신 **webhook 경로만** 예외 처리
- BCrypt 사용
- `RoleBasedAuthenticationSuccessHandler`로 역할별 진입점 분기
- matcher 순서가 곧 우선순위라는 점을 주석으로 명시
- `public-preview` 플래그로 목업 공개 범위를 local 프로필에만 한정, 관리자 화면은 preview에서도 잠금

### 2.3 설정 관리
- `application.yml` 프로필 분리 (`local` / `rds`), `.env` 외부화, `.env_sample` 커밋 — 교과서적으로 맞다.
- `max-part-count`, `max-file-size` 등을 **왜** 올렸는지 주석으로 남긴 점이 특히 좋다.
- Spring Boot 4.0.2 / Java 21 — 최신 스택.

### 2.4 문서
`docs/conventions.md`가 형식적인 문서가 아니라 실제로 판단 기준이 되는 수준으로 작성돼 있다.
"판단이 서지 않으면 store 코드를 그대로 따른다"처럼 **기준 구현체를 지정한 것**이 특히 좋은 결정이다.

### 2.5 기준 도메인 구현
`store`, `product`는 규약을 지킨다 — form/view DTO 분리, view는 불변 `record`, `#{}` 바인딩, `SELECT *` 미사용, 컬럼 명시.

---

## 3. 문제점

### 🔴 P1 — 즉시 수정

#### 3.1 [버그] 비밀번호 공백 시 계정 잠김
`src/main/java/com/cakeshop/domain/member/service/MemberService.java`

```java
// 3. 새 비밀번호 확인 일치 여부
if (!form.getNewPassword().equals(form.getNewPasswordConfirm())) {
    throw new IllegalArgumentException("새 비밀번호가 일치하지 않습니다.");
}
...
member.setPassword(passwordEncoder.encode(form.getNewPassword()));
```

사용자가 **비밀번호 칸을 비워둔 채 이름/전화번호만 수정**하면:
1. `"" .equals("")` → 검증 통과
2. `encode("")` 결과가 DB에 저장
3. 이후 기존 비밀번호로 **로그인 불가**

프로필 수정 화면에서 가장 흔한 사용 시나리오(비밀번호는 그대로 두고 이름만 변경)가 그대로 계정을 망가뜨린다.
→ `StringUtils.hasText(form.getNewPassword())`로 분기해 비어 있으면 비밀번호를 건드리지 않아야 한다.

#### 3.2 [버그 위험] `@Transactional` 누락
같은 파일 `updateMemberInfo()`에 트랜잭션 애너테이션이 없다. `join()`과 `checkEmailDuplicate()`에는 붙어 있어
누락이 의도가 아니라 실수로 보인다.

---

### 🟠 P2 — 규약 위반 / 보안

#### 3.3 엔티티를 화면에 직접 노출
`MyPageController.java`

```java
model.addAttribute("member", freshMember);  // Member 엔티티 그대로
```

`Member` 엔티티에는 **BCrypt 비밀번호 해시**가 들어 있고, 이게 그대로 템플릿 모델에 실린다.
템플릿이 실수로 출력하면 해시가 HTML에 노출된다.
규약(`conventions.md` DTO 절)은 "entity를 컨트롤러/화면에 직접 노출하지 않는다"고 명시한다.
→ `MemberView` record를 만들어 필요한 필드만 전달.

#### 3.4 Bean Validation이 하나도 없음
`SignupForm.java`, `ProfileUpdateForm.java` — 필드만 있고 검증 애너테이션이 **전무**하다.

```java
public class SignupForm {
    private String email;     // @Email 없음
    private String password;  // @Size 없음
    private String name;      // @NotBlank 없음
    ...
}
```

이메일 형식, 비밀번호 최소 길이, 전화번호 형식이 전부 무검증으로 DB까지 간다.
`spring-boot-starter-validation`은 이미 `build.gradle`에 있고, 규약도 "Bean Validation을 form에만 붙인다"고 적혀 있다.
`store/dto/form/StoreUpdateForm.java`(111줄)가 `@AssertTrue` 교차검증까지 포함한 좋은 예시다.

부수 문제: `ProfileUpdateForm`이 `@Data`를 쓴다. 규약은 `@Getter @Setter`다.

#### 3.5 예외 체계가 둘로 갈라져 있음
`MemberErrorCode`가 정의되어 있지만 **한 번도 사용되지 않는다.** 대신:

```java
throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
throw new RuntimeException("회원 정보 수정에 실패했습니다.");
```

프로젝트 전체에 `IllegalArgumentException` / `RuntimeException`이 10곳 있다.
이 예외들은 `GlobalExceptionHandler`의 `handleUnexpected`로 떨어져 **모든 사용자 실수가 500 에러 페이지**가 된다.
"이미 사용 중인 이메일"은 사용자 입력 오류(4xx)이지 서버 장애가 아니다.
→ `BusinessException(MemberErrorCode.DUPLICATE_EMAIL)` 형태로 통일. `StoreService`의 throw 패턴을 그대로 따르면 된다.

#### 3.6 컨트롤러의 try/catch가 전역 핸들러와 중복
`MyPageController.updateProfile()`이 `IllegalArgumentException`과 `Exception`을 직접 잡는다.
`GlobalExceptionHandler`를 만들어둔 이유가 바로 이 코드를 없애기 위해서다.
→ `@Valid` + `BindingResult`로 검증 오류를 처리하고, 비즈니스 예외는 전역 핸들러에 맡긴다.

#### 3.7 `updated_at` 수동 세팅 (규약 명시적 위반)
`resources/mapper/member/MemberMapper.xml`

```xml
updated_at = NOW() <!-- 수정 시간을 현재 시간으로 갱신 -->
```

규약 DB 절: *"수정 시각은 DDL의 `ON UPDATE CURRENT_TIMESTAMP(6)`에 위임한다. `UPDATE` 문에서 `updated_at`을 직접 세팅하지 않는다."*
주석까지 달아둔 걸 보면 규약을 못 본 것으로 보인다.

부수 문제: 같은 파일 `memberResultMap`에 `phone` 매핑이 빠져 있다.
`map-underscore-to-camel-case` 덕에 동작은 하지만, 다른 컬럼은 다 명시했는데 하나만 빠져 일관성이 없다.

---

### 🟡 P3 — 일관성 / 위생

#### 3.8 패키지 구조가 갈라짐
`product`만 `admin/` · `customer/` 하위 패키지로 분리됐고 나머지 도메인은 평면 구조다.

```
product/admin/controller/ProductAdminController.java     ← product만
product/customer/controller/ProductController.java
store/controller/StoreAdminController.java               ← 나머지 전부
```

그런데 `conventions.md`는 평면(`store`) 구조를 표준으로 적고 있다. **문서와 코드가 어긋난 상태**다.
남은 10개 도메인 구현이 시작되기 전에 하나로 정해야 재작업이 없다.

#### 3.9 빌드 산출물 213개가 git에 추적 중
`.gitignore`에 `build/`, `.gradle/`, `.idea/`는 있으나 **Eclipse/VSCode 출력 디렉터리 `bin/`이 빠져 있다.**
`bin/main/**/*.class` 213개 파일이 커밋되어 있다.

영향:
- 코드 한 줄만 고쳐도 diff에 바이너리가 잔뜩 섞여 리뷰가 어렵다
- 팀원끼리 머지 충돌이 계속 난다
- 리포지토리 용량 증가

→ `.gitignore`에 `bin/` 추가 + `git rm -r --cached bin` (팀 전체 공지 후 진행)

#### 3.10 테스트 편중 · CI 없음
- 테스트 11개가 전부 `product` / `store` / 화면 렌더링에 몰려 있다. **`member`의 회원가입·정보수정 경로는 테스트가 0개다** — 3.1의 버그가 잡히지 않은 이유.
- `.github/workflows`가 없어 PR 시 자동 빌드가 돌지 않는다. 컴파일이 깨진 브랜치가 머지될 수 있다.

---

## 4. 권장 조치 순서

| 순서 | 작업 | 이유 |
|---|---|---|
| 1 | `MemberService.updateMemberInfo()` 비밀번호 공백 분기 + `@Transactional` | 실사용 시 계정이 망가짐 |
| 2 | `.gitignore`에 `bin/` 추가 + 추적 해제 | 이후 모든 PR 리뷰 품질에 영향 |
| 3 | `member` 도메인 규약 정렬 (3.3~3.7) | 남은 10개 도메인이 이 코드를 복제하기 전에 |
| 4 | 패키지 구조 확정 + `conventions.md` 갱신 | 구현 시작 전에 정해야 재작업 없음 |
| 5 | `MemberServiceTests` 추가 (특히 비밀번호 공백 회귀 테스트) | 1번 버그 재발 방지 |
| 6 | `.github/workflows/ci.yml` — PR 시 `./gradlew build` | 브랜치 7개가 병렬로 도는 팀 구조상 필수 |

---

## 5. 총평

**구조 설계 A- / 구현 완성도 C / 규약 준수도 B-**

`conventions.md` + 기준 구현체(`store`) 지정은 팀 프로젝트에서 보기 드물게 성숙한 접근이다.
문제는 그 규약이 실제로 지켜지는지 확인하는 장치(CI, 코드리뷰 체크리스트, 테스트)가 없다는 점이다.
`member` 도메인이 규약을 6가지 지점에서 벗어난 채 머지된 것이 그 증거다.

지금은 **10개 도메인 구현이 본격화되기 직전**이라 기준선을 바로잡기에 가장 좋은 타이밍이다.
이 시점을 놓치면 잘못된 패턴이 10배로 복제된다.
