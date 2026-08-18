# 조각 1 — 목록·상세

> **끝난 조각의 기록이다. 지금 구속하지 않는다.**
> 이 조각이 만든 규칙의 정본은 `../specs/community-read.md`다.
> 조각 순서와 진행 상태, 위험과 결정 로그는 `../PLAN.md`.

- `CommunityMapper` + `CommunityMapper.xml`: 목록(스칼라 서브쿼리), 총 개수, 상세, 조회수 증가
- `CommunityService`: `PageRequest`/`PageResult` 사용, 상세 조회 시 `UPDATE → SELECT` 단일 트랜잭션
- 목록/상세 DTO 분리 (`dto/view/`)
- `CommunityController` 뷰 바인딩, 템플릿 2종(`list`, `detail`) 채우기 — `form`은 작성 화면이라 조각 2다

**검증**:
- `./gradlew clean test`
- **H1a** — 목록 SQL이 스칼라 서브쿼리 형태인지 정적 검사 (`GROUP BY`가 없고 `SELECT COUNT(*) FROM comments`를 포함)
- **H1b** — 게시글 건수를 늘려도 실행 쿼리 수가 변하지 않는지 (목록 1 + 총 개수 1)
- 상태별 상세 접근 규칙 (DOMAIN.md 4.3 표의 각 칸)
- 페이징 경계: 마지막 페이지, 범위 밖 페이지, `created_at`이 동일한 글이 중복·누락되지 않는지

**완료 (2026-08-02)**. `CommunityMapper`(+XML) 5개 statement, `CommunityService`, `CommunityController`, `dto/view` 3종, 템플릿 2종을 추가했다. 검증은 `CommunityMapperXmlTests`(5), `CommunityMapperTests`(20), `CommunityQueryCountTests`(2), `CommunityServiceTests`(12), `CommunityControllerTests`(9), `CommunityScreenRenderingTests`(20), `CommunitySeedTests`(4), `CommunityScreenDocTests`(6) 78건으로 고정했다. 하네스 표에 H1a·H1b·H1c·H4·H5·H6·H7을 올렸다.

`bootRun`으로 띄워 목록·상세·페이징·필터·404·조회수·이스케이프를 브라우저에서 확인했다. 그 과정에서 `seed-local.sql`이 카테고리를 지우는 문제를 발견해, 커뮤니티 전용 시드 `db/seed/seed-community.sql`을 새로 만들었다(`../PLAN.md`의 결정 로그).

구현 중 DOMAIN.md에 없던 빈칸 두 개를 채우고 6.2에 반영했다: 노출되지 않는 글은 조회수를 올리지 않는다(UPDATE의 `status` 조건), 조회수 UPDATE는 `updated_at`을 명시적으로 보존한다.

이후 화면 명세 `docs/community/SCREENS.md`와 H7을 추가하면서, 명세를 쓰는 과정에서 렌더링 테스트가 없던 자리 네 곳(쪽 이동 블록, 작성 화면, 작성 화면의 비로그인 차단, `(수정됨)` 표시)을 발견해 함께 고정했다.

H7은 리뷰를 거치며 다섯 번 강해졌다.

| 단계 | 무엇까지 봤나 | 무엇이 여전히 통과했나 |
|---|---|---|
| 1 | 소스에 메서드 이름이 있는가 | `@Test`를 떼거나 `@Disabled`를 붙인 테스트 |
| 2 | JUnit이 실행하는 테스트인가 | **문구와 아무 상관없는** 테스트를 연결한 경우 |
| 3 | 본문에 문구가 등장하는가 | 입력 fixture로 쓰거나 `not(...)`으로 **없다고** 단언한 경우 |
| 4 | `containsString`으로 **있다고 단언**하는가 | 정적 import를 지운 `Matchers.not(...)`, 조건부 비활성화(`@DisabledOnOs`) |
| 5 | 한정한 `not`도 부정으로 세고, `org.junit.jupiter.api.condition`도 거절하는가 | 작은따옴표 `th:text='...'`, 안쪽까지 한정한 `Matchers.not(Matchers.containsString(...))` |
| 6 | 두 표기도 함께 거절하는가 | **표기법은 계속 남는다.** 여기서 멈추고 보증 수준을 문서에 적었다 — R9 |

메서드 경계도 중괄호만 세다가 문자열·주석을 인식하는 스캐너로 바꿨다. 문자열 안의 `"}"` 하나가 본문을 일찍 잘라 **뒤쪽 assertion을 통째로 빠뜨리는데**, 그렇게 비는 것은 실패가 아니라 통과로 나타나서 더 나쁘다.

`build.gradle`에는 `docs/`에 더해 `src/test/java`도 `test` 입력으로 등록했다. 이 검사는 컴파일된 클래스가 아니라 소스 원문을 읽으므로 **바이트코드가 같은 수정이 검사 결과를 바꾼다.** 등록하지 않으면 Gradle이 `UP-TO-DATE`로 건너뛴다.

다섯 번 모두 같은 실수였다 — **"검사가 있다"와 "검사가 문다"는 다르다.** 매번 "이번엔 됐다"고 생각한 자리에서 한 겹이 더 나왔고, 공통점은 빈 곳이 **실패가 아니라 통과의 모습**으로 나타난다는 것이다. 스스로는 못 찾는다. 통과하니까.

여섯 번째에 멈춘 이유는 구멍이 없어져서가 아니다. **이 검사는 성질이 아니라 대리물(소스 텍스트의 모양)을 보므로 표기법의 수만큼 구멍이 남는다.** 한 겹 더 두껍게 만드는 것은 다음 표기 하나를 막을 뿐 종류를 없애지 못한다. 그래서 표기 2건을 막고, 이 하네스가 **어디까지 보증하는지**를 SCREENS.md와 R9에 적는 쪽으로 방향을 바꿨다.

## 결정 로그에서 옮겨 온 리뷰 기록

`../PLAN.md` 결정 로그에는 한 줄만 남고 전문이 여기 있다.

| 날짜 | 내용 |
|---|---|
| 2026-08-02 | PR #75 Codex 리뷰 P2 3건 처리. (1) **페이지 상한은 받아들여 고쳤다** — `PageRequest.getOffset()`의 `(page - 1) * size`가 int 연산이라 `page=2147483647`에서 `-40`이 되고 목록이 500으로 죽는다. 커뮤니티만의 문제가 아니라 `PageRequest`를 함께 쓰는 5개 도메인의 문제이고 공개 경로인 `/products`에도 같은 구멍이 있어, 컨트롤러가 아니라 공용 컴포넌트에서 막고 `PageRequestTests`를 신설했다(그 클래스에는 테스트가 하나도 없었다). (2) 시드 실행 순서는 현 상태 유지 — H6이 불변식을 고정하고 있고 남은 것은 README 한 줄이다(R6). (3) 인덱스는 1차 미적용, 대신 돌아올 트리거를 R8에 숫자로 적었다 |
