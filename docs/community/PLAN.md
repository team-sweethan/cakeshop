# Community 진행 계획

> 도메인 규칙의 정본은 `docs/community/DOMAIN.md`, 화면 구성의 정본은 `docs/community/SCREENS.md`다.
> 이 문서는 **작업 순서, 진행 상태, 결정 로그, 위험**만 다룬다.
> 규칙이 바뀌면 DOMAIN.md를 고치고, 여기에는 "언제 왜 바꿨는지"만 한 줄 남긴다.

## 작업 방식

한 번에 **수직 조각 하나**만 한다. 조각 하나는 DB → Mapper → Service → Controller → 화면 → 테스트까지 닿아야 하고, 끝나면 브라우저에서 동작하고 `gradlew test`가 통과하고 CI가 초록불이어야 한다.

조각마다 AI에게 주는 지시는 이 형태를 따른다:

```
docs/community/DOMAIN.md와 기존 코드를 먼저 읽고 구현 계획을 보고해.
<조각 내용>을 구현해.
구현 후 ./gradlew clean test를 실행하고, <이 조각의 검증 항목>을 확인해.
끝나면 변경 파일, 실행한 검증, 남은 위험을 보고해.
```

**AI가 실수하면 그 자리에서 고치고 끝내지 않고 하네스로 승격시킨다.** 같은 실수가 두 번 일어날 수 없게 테스트·제약·규칙 문서 중 하나로 고정한다.

## 조각 순서

| # | 조각 | 상태 | 내용 |
|---|---|---|---|
| 0 | 준비 | 완료 | `PostStatus` enum, `CHECK` 제약 migration, 카테고리 3종 주입 migration |
| 1 | 목록·상세 | 완료 | 카테고리 필터, 페이징, 상세 조회, 조회수 |
| 2 | 작성·수정·삭제 | 대기 | 게시글 CRUD, 소유권 검증, soft delete |
| 3 | 댓글 | 대기 | 1단계 댓글 작성·삭제, 자리 표시 |
| 4 | 좋아요 | 대기 | POST/DELETE 분리, 멱등, `like_count` 재계산 |
| 5 | 신고·차단 | 대기 | 회원 신고, 관리자 차단·해제 화면 |
| 6 | (2차) 대댓글 | 범위 밖 | |
| 7 | (2차) 무한 스크롤 | 범위 밖 | |

### 조각 0 — 준비

**왜 먼저인가**: 조각 1의 모든 쿼리가 `status='PUBLISHED'` 조건에 의존하고, 카테고리가 없으면 게시글을 하나도 만들 수 없다. 순서를 바꾸면 조각 1에서 되돌아와야 한다.

- `PostStatus { PUBLISHED, DELETED, BLOCKED }` + `canTransitionTo` (`MemberStatus` 선례를 따름)
- 새 migration: `posts.status` CHECK 제약
- 새 migration: `post_categories`에 `QNA/REVIEW/FREE` 주입
- migration 파일명은 직접 짓지 않고 `gradlew newMigration -Pdesc=<snake_case>`로 생성

**검증**: 전이 규칙 단위 테스트(허용 3 / 금지 3), MariaDB Testcontainers로 CHECK 제약이 잘못된 값을 거부하는지, 카테고리 3건이 주입되는지.

**완료 (2026-08-02, `4c9cdb7`)**. `PostStatus`(전이 규칙 포함), `CommentStatus`, `V20260802_113219__add_post_status_constraint.sql`(`posts`·`comments` 두 컬럼), `V20260802_113229__provision_post_categories.sql`을 추가했다. 검증은 `PostStatusTests`(6), `CommentStatusTests`(3), `CommunitySchemaTests`(6)로 고정했고 하네스 표에 H0a·H0b로 올렸다. `comments.status`도 함께 제약을 건 것은 계획보다 넓지만, 댓글 자리 표시 정책(DOMAIN.md 4.4)이 상태값에 의존하므로 같은 migration에 담았다.

### 조각 1 — 목록·상세

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

`bootRun`으로 띄워 목록·상세·페이징·필터·404·조회수·이스케이프를 브라우저에서 확인했다. 그 과정에서 `seed-local.sql`이 카테고리를 지우는 문제를 발견해, 커뮤니티 전용 시드 `db/seed/seed-community.sql`을 새로 만들었다(아래 결정 로그).

구현 중 DOMAIN.md에 없던 빈칸 두 개를 채우고 6.2에 반영했다: 노출되지 않는 글은 조회수를 올리지 않는다(UPDATE의 `status` 조건), 조회수 UPDATE는 `updated_at`을 명시적으로 보존한다.

이후 화면 명세 `docs/community/SCREENS.md`와 H7을 추가하면서, 명세를 쓰는 과정에서 렌더링 테스트가 없던 자리 네 곳(쪽 이동 블록, 작성 화면, 작성 화면의 비로그인 차단, `(수정됨)` 표시)을 발견해 함께 고정했다.

H7은 리뷰를 거치며 세 번 강해졌다. 매번 "이번엔 됐다"고 생각한 자리에서 한 겹이 더 나왔다.

| 단계 | 무엇까지 봤나 | 무엇이 여전히 통과했나 |
|---|---|---|
| 1 | 소스에 메서드 이름이 있는가 | `@Test`를 떼거나 `@Disabled`를 붙인 테스트 |
| 2 | JUnit이 실행하는 테스트인가 | **문구와 아무 상관없는** 테스트를 연결한 경우 |
| 3 | 본문에 문구가 등장하는가 | 입력 fixture로 쓰거나 `not(...)`으로 **없다고** 단언한 경우 |
| 4 | `containsString`으로 **있다고 단언**하는가 | — |

메서드 경계도 중괄호만 세다가 문자열·주석을 인식하는 스캐너로 바꿨다. 문자열 안의 `"}"` 하나가 본문을 일찍 잘라 **뒤쪽 assertion을 통째로 빠뜨리는데**, 그렇게 비는 것은 실패가 아니라 통과로 나타나서 더 나쁘다.

네 번 모두 같은 실수였다 — **"검사가 있다"와 "검사가 문다"는 다르다.**

### 조각 2 — 작성·수정·삭제

- 소유권 검증은 인증 사용자 기준으로 Service에서
- 삭제 = `PUBLISHED → DELETED` 전이
- 입력 검증 (DOMAIN.md 7)

**검증**: 남의 글 수정·삭제 시도 거부, `BLOCKED` 글 수정·삭제 시도 거부, 검증 실패 케이스, 공백만 입력 거부.

**작성 화면(`form.html`)은 아직 목업이며 화면에 거짓이 세 개 있다.** 무엇을 고쳐야 하는지는 `SCREENS.md`의 "조각 2에서 반드시 고쳐야 할 거짓" 표에 적어 두었다. 특히 분류 선택지가 `후기/질문/자유/레시피`로 하드코딩되어 있어, DB의 활성 카테고리(`QNA/REVIEW/FREE`)와 어긋나고 **비활성인 `레시피`를 고를 수 있다.**

### 조각 3 — 댓글

- 대상 게시글이 `PUBLISHED`인지 검증 (DOMAIN.md 4.5)
- 삭제 시 자리 표시, 개수 집계에서 제외
- `parent_comment_id`를 코드에 등장시키지 않는다

**검증**: 삭제된 게시글에 댓글 작성 시도 거부, 삭제된 댓글이 개수에 안 세이는지, 남의 댓글 삭제 거부.

### 조각 4 — 좋아요

- POST/DELETE 분리, 둘 다 멱등
- `like_count` 재계산

**검증**: 같은 요청 반복 시 카운트 불변, **동시 요청 후 `like_count == post_likes 실제 개수`**, 삭제된 게시글에 좋아요 시도 거부.

### 조각 5 — 신고·차단

- 중복 신고 에러 응답, 취소 불가
- 관리자 차단·해제, `blocked_*` 기록 및 해제 후 보존
- 보류 항목 결정: `post_reports.status` 전이, 관리자 목록 필터·정렬

**검증**: 중복 신고 거부, 비관리자의 차단 API 접근 거부(화면 숨김이 아니라 Security), 차단 해제 후 `blocked_*`가 남아 있는지.

**관리자 목업 두 화면은 도메인 규칙보다 먼저 그려졌다.** 규칙에 없는 기능이 버튼으로 존재한다 — 특히 `게시글 영구 삭제`와 댓글 `삭제`는 **DOMAIN.md에 없는 권한**이고, 관리자 조치는 차단뿐이며 `BLOCKED → DELETED`는 금지다(4.2, 6.7). 상태 어휘도 화면은 `정상`/`제재`, 문서는 `차단`으로 갈려 있다. 조각 5는 `SCREENS.md`의 "조각 5에서 정하거나 고쳐야 할 것" 표를 정리하는 일부터 시작한다.

## 하네스 (누적)

조각을 진행하며 여기에 쌓는다. 빈 칸은 아직 필요가 발생하지 않은 것이다.

| # | 무엇을 고정하나 | 형태 | 상태 |
|---|---|---|---|
| H0a | 게시글·댓글의 상태 전이 규칙(DOMAIN.md 4.2, 4.4). 특히 `BLOCKED -> DELETED` 금지와 `DELETED` 종착 | `PostStatusTests`(6), `CommentStatusTests`(3) | **적용** (조각 0) |
| H0b | `posts.status`·`comments.status`에 미정의 값이 저장되지 않음, 카테고리 3종이 활성 상태로 존재함 | `CommunitySchemaTests`(6), MariaDB Testcontainers로 CHECK 제약 검증 | **적용** (조각 0) |
| H1a | 목록 SQL이 스칼라 서브쿼리 형태를 유지함. 정렬의 `id` tiebreaker, 노출 조건이 `status` 하나인 것도 함께 고정 | `CommunityMapperXmlTests` — `XMLMapperBuilder`로 XML 파싱 후 SQL 문자열 검사 (선례: `OrderMapperXmlTests`) | **적용** (조각 1) |
| H1b | 목록 조회 시 실행 쿼리 수가 게시글 수와 무관 | `CommunityQueryCountTests` — MyBatis `Interceptor`로 실행 statement 수 카운트 | **적용** (조각 1) |
| H1c | 조회수 증가가 게시글을 "수정됨"으로 만들지 않음 | `CommunityMapperTests.increaseViewCount_doesNotMarkPostAsEdited` + H1a의 SQL 형태 검사 | **적용** (조각 1) |
| H2 | `like_count`와 실제 좋아요 수 일치 | 동시 요청 테스트 | 조각 4 (예정) |
| H3 | Controller가 Mapper를 직접 호출하지 않음 | ArchUnit | 위반 발생 시 |
| H4 | 본문·제목의 HTML이 이스케이프됨 (`th:utext` 미사용의 실제 결과) | `CommunityScreenRenderingTests` — 본문에 `<script>`를 넣고 렌더링 결과를 확인 | **적용** (조각 1) |
| H5 | 커뮤니티 화면이 실제로 렌더링됨. 작성자에게만 열리는 차단 안내 화면 포함 | `CommunityScreenRenderingTests` — Thymeleaf를 실제로 돌린다. Controller 단위 테스트는 뷰 이름만 보므로 템플릿이 깨져도 통과한다 | **적용** (조각 1) |
| H6 | 로컬 시드를 순서대로 실행하면 카테고리 참조 데이터가 남음. 커뮤니티 시드가 `parent_comment_id`를 쓰지 않는 것도 함께 | `CommunitySeedTests` — 시드 파일 내용을 직접 검사 | **적용** (조각 1) |
| H7 | 화면 명세 `SCREENS.md`가 실제 화면과 어긋나지 않음. 고객·관리자 템플릿 6종 전부. 문서가 "이 테스트가 지킨다"고 적은 테스트가 **실행되며 그 문구를 실제로 확인하는지**, `계획` 화면이 아직 안 만들어졌는지도 함께 | `CommunityScreenDocTests`(6) — 문서를 파싱해 템플릿 파일·문구와 대조하고, 참조된 테스트는 리플렉션으로 실행 여부를, 소스 본문의 `containsString` 인자로 그 문구를 있다고 단언하는지를 본다. `build.gradle`에서 `docs/`를 `test` 입력으로 등록해야 문서만 고쳐도 다시 돈다 | **적용** (조각 1) |

조각을 끝낼 때 **그 조각이 추가한 하네스를 여기 올리고 조각 표의 상태를 바꾼다.** 이 문서가 정본이므로, 여기가 현실과 어긋나면 다음 작업자가 끝난 일을 다시 한다.

## 위험

| # | 위험 | 상태 |
|---|---|---|
| R1 | ~~`SUSPENDED` 회원 로그인 차단에 의존~~ — **해소.** 전제가 3중으로 테스트되어 있음을 확인했다: `MemberAuthenticationServiceTests:51`(`@EnumSource({SUSPENDED, WITHDRAWN})` → `loginAllowed == false`, status 누락 시에도 차단), `MemberDetailsServiceTests:65`(`loginAllowed == false` → `UsernameNotFoundException`), `MemberAdminControllerTests:151`(정지 시 기존 세션 만료). 커뮤니티에 중복 검증을 넣지 않는다 | 해소 (2026-08-02) |
| R2 | 목록 쿼리를 `LEFT JOIN ... GROUP BY`로 바꿔도 결과가 같아 눈으로는 안 잡힌다. **실행 쿼리 수 측정으로는 잡히지 않는다** — GROUP BY로 바꿔도 쿼리는 여전히 1번이다. 형태 검사(H1a)가 있어야 잡힌다 | H1a로 방어 예정 (조각 1) |
| R3 | soft delete 도입이 이 프로젝트의 첫 사례다. 다른 도메인에 선례가 없어 팀 컨벤션과 어긋날 수 있다 | 조각 1 리뷰에서 확인 |
| R4 | `comment_count` 비정규화 컬럼이 없어 집계로 처리한다. 트래픽이 늘면 컬럼 추가로 전환 필요 | 1차에선 수용 |
| R5 | `ScreenRenderingTests.productOptionAdminScreenRendersWithSeededAdmin`이 **로컬(Windows)에서만** 실패한다. 조각 1 이전(`4c9cdb7`)에서도 동일하게 재현되며, PR #75의 CI(ubuntu)에서는 통과했다. 커뮤니티와 무관한 상품 도메인 화면 테스트이고 병합을 막지 않는다. 다만 로컬 `gradlew test`가 빨간불이라 커뮤니티 조각의 "전체 초록불" 확인은 CI로 대신해야 한다 | 환경 차이로 확인됨. 상품 담당(시은)에게 공유 (2026-08-02) |
| R6 | `seed-local.sql`만 실행하면 `post_categories`가 비어 커뮤니티 글쓰기가 불가능하다. `seed-community.sql`을 이어서 실행해야 한다는 안내가 README에는 아직 없다 | 현규가 README 반영 여부를 직접 확인 (2026-08-02) |
| R7 | `SCREENS.md` 검사는 **문서 → 코드 한 방향뿐이다.** 템플릿에 조건부 블록을 새로 넣고 문서에 적지 않으면 잡히지 않는다. 그런 블록은 평소 화면에 없어서 리뷰에서도 안 보인다 | 수용. 화면을 만진 조각은 SCREENS.md를 함께 고친다 (2026-08-02) |
| R8 | 목록 정렬 `created_at DESC, id DESC`를 받쳐 줄 인덱스가 없다. `posts`에는 PK와 FK 3개(`member_id`, `category_id`, `blocked_by`)뿐이라 **카테고리 필터 없는 목록은 전체를 훑고 filesort**한다. 1차에서는 데이터가 적어 수용하고, 인덱스는 나중에 온라인 DDL로 붙일 수 있어 되돌리기 쉬운 결정이다. **다만 "모니터링 후"라고만 두면 돌아올 계기가 없다** — `posts`가 1만 건을 넘거나 목록 응답이 눈에 띄게 느려지면 `(status, created_at, id)` 복합 인덱스를 새 migration으로 추가하고 MariaDB 실행 계획으로 확인한다 | 1차에선 수용. 트리거 도달 시 재검토 (2026-08-02, PR #75 Codex 리뷰) |

## 결정 로그

| 날짜 | 내용 |
|---|---|
| 2026-08-02 | 초기 grill 완료. 결정 17건을 DOMAIN.md에 반영, 보류 4건. 상세 근거는 DOMAIN.md 각 절에 있다 |
| 2026-08-02 | 댓글 삭제 정책을 "목록에서 완전 제외"에서 **"자리 표시 유지"로 변경**. 2차에 대댓글을 확실히 구현하기로 하면서, 그때 정책을 뒤집는 비용(테스트·화면 재작업)보다 지금부터 맞추는 편이 싸다고 판단 |
| 2026-08-02 | `post_categories`에 데이터를 넣는 코드가 어디에도 없다는 사실 발견. 조각 0에 카테고리 주입 migration 추가 |
| 2026-08-02 | R1 확인 후 해소. member 도메인 테스트 3건이 전제를 고정하고 있어 커뮤니티에 중복 검증을 넣지 않기로 확정 |
| 2026-08-02 | H1을 H1a(형태 고정)/H1b(실행 횟수)로 분리. 실행 쿼리 수 측정만으로는 `GROUP BY` 재작성을 못 잡는다는 점을 반영 |
| 2026-08-02 | 조각 0 완료를 반영. 조각 표 상태와 하네스 표(H0a·H0b)가 실제 코드와 어긋나 있던 것을 맞추고, "조각 종료 시 하네스 표를 갱신한다"를 명시 |
| 2026-08-02 | 조각 1의 "템플릿 3종 채우기"를 2종(`list`, `detail`)으로 정정. `form`은 작성 화면이라 저장 경로가 생기는 조각 2에 속한다 |
| 2026-08-02 | `seed-local.sql`이 `post_categories`를 지워 로컬에서 카테고리 필터가 비고 글쓰기가 불가능해지는 문제를 발견. 처음에는 공용 시드를 고쳤다가, **공용 파일을 건드리지 않고 `db/seed/seed-community.sql`을 새로 만드는 쪽으로 바꿨다.** 커뮤니티 샘플 데이터가 어차피 필요했고, 카테고리 복구도 같은 파일에서 하면 우리 도메인 안에서 닫힌다. 대신 `seed-local.sql`만 실행한 사람에게는 문제가 그대로 남으므로 실행 순서를 시드 헤더와 `domain/community/CLAUDE.md`에 적었다. H6으로 고정 |
| 2026-08-02 | H4를 "위반 발생 시"에서 조각 1로 앞당김. Controller 단위 테스트가 뷰 이름만 확인한다는 것을 구현 중 확인했고, 그러면 템플릿이 깨지거나 `th:utext`가 들어와도 CI가 초록불이다. 렌더링 테스트(H5)를 만드는 김에 이스케이프까지 함께 고정했다 |
| 2026-08-02 | 화면 명세 `docs/community/SCREENS.md`를 추가. 화면마다 주소·모델·문구·조건부 노출을 적고, **`CommunityScreenDocTests`가 문서를 파싱해 템플릿과 대조**하게 했다(H7). 문서를 그냥 두면 낡고, 낡은 문서는 없는 것보다 나쁘다 — 다음 작업자가 틀린 전제로 작업하기 때문이다. 검사가 문서→코드 한 방향뿐인 한계는 R7에 적었다 |
| 2026-08-02 | 위 명세를 쓰다가 렌더링 테스트가 없던 자리 셋을 발견해 채움: 쪽 이동 블록(글 21건 이상에서만 나타나 개발 중엔 화면에 없다), 작성 화면, 작성 화면의 비로그인 차단. 또 `form.html`의 분류 선택지가 DB 카테고리와 다르다는 것도 이때 드러나 조각 2 항목에 적었다. **"화면에 무엇이 보이는가"를 문장으로 적어 보는 것 자체가 빈 곳을 드러낸다** |
| 2026-08-02 | PR #75 Codex 리뷰 P2 3건 처리. (1) **페이지 상한은 받아들여 고쳤다** — `PageRequest.getOffset()`의 `(page - 1) * size`가 int 연산이라 `page=2147483647`에서 `-40`이 되고 목록이 500으로 죽는다. 커뮤니티만의 문제가 아니라 `PageRequest`를 함께 쓰는 5개 도메인의 문제이고 공개 경로인 `/products`에도 같은 구멍이 있어, 컨트롤러가 아니라 공용 컴포넌트에서 막고 `PageRequestTests`를 신설했다(그 클래스에는 테스트가 하나도 없었다). (2) 시드 실행 순서는 현 상태 유지 — H6이 불변식을 고정하고 있고 남은 것은 README 한 줄이다(R6). (3) 인덱스는 1차 미적용, 대신 돌아올 트리거를 R8에 숫자로 적었다 |
| 2026-08-02 | `SCREENS.md`를 커뮤니티 화면 **전체 지도**로 확장. 고객 3종 + 관리자 2종 + 계획 1종(수정)과 "만들지 않는 화면"까지 담았다. 화면마다 `구현됨`/`목업`/`계획` 상태를 붙이고, 계획 화면은 **템플릿이 아직 없어야** 통과하도록 검사를 걸었다 — 만들면서 상태 표기를 지우지 않으면 빌드가 깨진다. 관리자 화면 2종은 이때까지 어떤 테스트도 열어 본 적이 없어 렌더링 테스트 3건(관리자 목록·상세·비관리자 거부)을 함께 넣었다 |
| 2026-08-02 | 인기글은 **범위 밖으로 유지**. DOMAIN.md 2에서 이미 제외한 항목인 데다, 조회수 기준으로 만들 수 없다 — 6.2가 "조회수는 정렬·순위에 쓰이지 않는다"를 근거로 중복 방지를 빼서 새로고침만으로 순위 조작이 된다. 넣으려면 범위 변경 + 기준을 좋아요로 고정 + 조각 4 이후가 세트라는 것을 `SCREENS.md`에 적었다 |
| 2026-08-02 | 관리자 목업이 도메인 규칙보다 먼저 그려져 **규칙에 없는 기능이 버튼으로 존재**한다는 것을 화면 지도를 그리다 발견(관리자의 게시글·댓글 삭제, `정상`/`제재` 용어, IP 표시, 첨부 이미지). 조각 5 항목으로 옮겼다 |
| 2026-08-02 | `build.gradle`의 `test`에 `docs/`를 입력으로 등록. 등록 전에는 문서만 고쳤을 때 Gradle이 `test`를 UP-TO-DATE로 건너뛰어, 문서가 어긋나도 로컬에서 초록불이 떴다. 하네스를 만들고 나서 **그 하네스가 정말 무는지 문서를 일부러 틀리게 고쳐 확인하다가** 발견했다 |
| 2026-08-02 | 조회수 규칙의 빈칸을 DOMAIN.md 6.2에 채움. (1) 노출되지 않는 글은 조회수를 올리지 않는다 — UPDATE의 `status` 조건이 담당한다. (2) 조회수 UPDATE는 `updated_at`을 명시적으로 보존한다 — `ON UPDATE CURRENT_TIMESTAMP` 때문에 조회만으로 "수정됨"이 켜지는 것을 구현 중 발견했고, H1c로 고정했다 |
