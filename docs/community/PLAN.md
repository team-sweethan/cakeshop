# Community 진행 계획

> 기능 단위 명세의 정본은 `docs/community/specs/`이고, 여러 기능에 걸리는 공통 규칙의 정본은
> `docs/community/DOMAIN.md`다. 어느 spec인지는 `DOMAIN.md` 0.1절 기능 목록이 가리킨다.
> 이 문서는 **작업 순서, 진행 상태, 하네스 인덱스, 결정 로그, 위험**만 다룬다.
> 규칙이 바뀌면 정본 문서를 고치고, 여기에는 "언제 왜 바꿨는지"만 한 줄 남긴다.

## 작업 방식

한 번에 **수직 조각 하나**만 한다. 조각 하나는 DB → Mapper → Service → Controller → 화면 → 테스트까지 닿아야 하고, 끝나면 브라우저에서 동작하고 `gradlew test`가 통과하고 CI가 초록불이어야 한다.

조각마다 AI에게 주는 지시는 이 형태를 따른다:

```
docs/community/DOMAIN.md 0.1절에서 <이 조각의 기능 ID>가 어느 spec인지 찾고,
그 spec과 DOMAIN.md의 관련 공통 절, 기존 코드를 먼저 읽고 구현 계획을 보고해.
<조각 내용>을 구현해.
구현 후 ./gradlew clean test를 실행하고, <이 조각의 검증 항목>을 확인해.
끝나면 변경 파일, 실행한 검증, 남은 위험을 보고하고,
바뀐 업무 결정이 있으면 그 spec을, 조각 상태는 PLAN.md를 함께 갱신해.
```

**규칙이 바뀌면 고칠 곳은 그 기능의 `specs/*.md`다.** 여러 기능에 걸리는 것만 `DOMAIN.md`이고,
`DOMAIN.md` 6절은 옛 절 번호를 지금 자리로 보내는 매핑 표일 뿐이라 거기에 규칙을 적지 않는다.
갱신 대상의 전체 목록은 `domain/community/CLAUDE.md`의 조각 완료 시 문서 동기화 절에 있다.

**AI가 실수하면 그 자리에서 고치고 끝내지 않고 하네스로 승격시킨다.** 같은 실수가 두 번 일어날 수 없게 테스트·제약·규칙 문서 중 하나로 고정한다.

## 조각 순서

| # | 조각 | 상태 | 내용 |
|---|---|---|---|
| 0 | 준비 | 완료 | `PostStatus` enum, `CHECK` 제약 migration, 카테고리 3종 주입 migration |
| 1 | 목록·상세 | 완료 | 카테고리 필터, 페이징, 상세 조회, 조회수 |
| 2 | 작성·수정·삭제 | 완료 | 게시글 CRUD, 소유권 검증, soft delete |
| 3 | 댓글 | 완료 | 1단계 댓글 작성·삭제, 자리 표시 |
| 6 | 조회수 중복 방지 | 완료 | `post_views` migration, 10분 창(2026-08-04에 날짜 창에서 변경), `view_count`를 파생 값으로 |
| 4 | 좋아요 | 완료 | 추가/취소 경로 분리, 멱등, `like_count` 재계산 |
| 5 | 신고·차단 | 완료 | 회원 신고, 관리자 차단·해제 화면 |
| 7 | 조회수 정렬·인기글 | 완료 (7a·7b·7c) | 목록 `?sort=views`, 인기글 영역, 정렬 인덱스 |
| 10 | 회원 연동 계약 분리 | 완료 (10a~10d) | `members` JOIN 8곳을 `MemberCommunityQueryService` 경유로 바꿨다 |
| 13 | 메인 인기글 노출 | 완료 | B5·D2. `CommunityHomeQueryService`로 메인에 5건. 목록 10건과 공유하는 읽기를 `PopularPostReader`로 모았다 |
| 14 | 공지사항 | 완료 (14a·14b·14c) | B8·B9·B10·C5·C6·C7·D3·E4. 별도 `community_notices` 표. spec은 `specs/community-notice.md` |
| 8 | (2차) 대댓글 5 depth | **착수 전** | A7. `parent_comment_id` 사용. **`community-comment.md` B4의 자르기 규칙과 부딪히는 자리를 먼저 닫는다** |
| 9 | (2차) 무한 스크롤 | **착수 전** | B7. B1의 쪽 번호 페이징을 대체. R28의 오프셋 한계가 여기서 정면으로 걸린다 |
| 11 | (2차) 검색 | **착수 전** | B6. 구현 방식과 인프라 도입 여부를 먼저 합의한다(`DOMAIN.md` 2절) |
| 12 | (2차) 이미지 첨부 | **착수 전** | A4. `post_images` 사용 |

> **2차 넷을 전부 한다 (2026-08-10).** 8·9는 원래 `범위 밖`으로 적혀 있었고 검색·이미지 첨부는 표에
> 없었다. 넷 다 하기로 정해져 줄을 세웠다. **순서는 아직 정하지 않았다** — 다만 8은 자르기 규칙,
> 9는 커서 페이징이라는 **선행 결정**을 각각 갖고 있고, 그 결정 없이 착수하면 되돌아온다.
> 각 기능의 미정 항목은 해당 spec의 2차 절이 정본이다.

> **순서를 바꿨다 (2026-08-04).** 원래는 3 → 4 → 5 → 6 → 7이었고, 조각 6을 **4보다 먼저** 했다. 번호는 붙인 순서라 그대로 두고 표의 줄만 실제 진행 순서로 옮겼다.
>
> **왜 앞당겼나**: 조회수 정책을 바꾼 순간부터 `view_count`는 **순위에 쓸 수 없는 값**이 됐다(R13). 새로고침도 댓글 `더 보기` 클릭도 그대로 +1이라, 조각 4·5를 하는 동안 계속 쌓이는 값은 어차피 나중에 버려야 한다. 버릴 값을 더 쌓는 것보다 세는 법을 먼저 고치는 편이 싸다.
>
> **6 → 7 순서는 여전히 지켜야 한다.** 7은 조회수를 화면의 순위로 내보내는 일이고, 6이 없으면 **새로고침만으로 순위가 오르는 화면을 공개하는 것**이 된다. 지금은 6이 끝났으므로 7은 언제 해도 된다.

### 조각 0 — 준비

**왜 먼저인가**: 조각 1의 모든 쿼리가 `status='PUBLISHED'` 조건에 의존하고, 카테고리가 없으면 게시글을 하나도 만들 수 없다. 순서를 바꾸면 조각 1에서 되돌아와야 한다.

- `PostStatus { PUBLISHED, DELETED, BLOCKED }` + `canTransitionTo` (`MemberStatus` 선례를 따름)
- 새 migration: `posts.status` CHECK 제약
- 새 migration: `post_categories`에 `QNA/REVIEW/FREE` 주입
- migration 파일명은 직접 짓지 않고 `gradlew newMigration -Pdesc=<snake_case>`로 생성

**검증**: 전이 규칙 단위 테스트(허용 3 / 금지 3), MariaDB Testcontainers로 CHECK 제약이 잘못된 값을 거부하는지, 카테고리 3건이 주입되는지.

**완료 (2026-08-02, `4c9cdb7`)**. `PostStatus`(전이 규칙 포함), `CommentStatus`, `V20260802_113219__add_post_status_constraint.sql`(`posts`·`comments` 두 컬럼), `V20260802_113229__provision_post_categories.sql`을 추가했다. 검증은 `PostStatusTests`와 `CommunitySchemaTests`로 고정했고 하네스 표에 H0a·H0b로 올렸다. `comments.status`도 함께 제약을 건 것은 계획보다 넓지만, 댓글 자리 표시 정책(DOMAIN.md 4.4)이 상태값에 의존하므로 같은 migration에 담았다.

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

### 조각 2 — 작성·수정·삭제

- 소유권 검증은 인증 사용자 기준으로 Service에서
- 삭제 = `PUBLISHED → DELETED` 전이
- 입력 검증 (DOMAIN.md 7)

**검증**: 남의 글 수정·삭제 시도 거부, `BLOCKED` 글 수정·삭제 시도 거부, 검증 실패 케이스, 공백만 입력 거부.

**완료 (2026-08-03)**. `CommunityMapper` +4 statement(`insertPost`·`updatePost`·`deletePost`·`existsActiveCategory`), `Post` 엔티티(쓰기 경로 필드만), `dto/form/PostForm`, `CommunityService` 3개 메서드 + `getEditablePost`, `CommunityController` 5개 핸들러, `form.html` 전면 교체(작성·수정 공용), `detail.html` 수정·삭제 버튼. migration은 없다 — `posts`에 필요한 컬럼이 V0에 전부 있다.

검증은 `CommunityMapperTests`(+11), `CommunityServiceTests`(+15), `CommunityControllerTests`(+7), `CommunityScreenRenderingTests`(+5)로 고정했다. 하네스 표에 H2a·H2b·H2d를 올렸다.

목업이던 `form.html`의 거짓 다섯 개를 걷어냈다(`screens/new.md`의 "이 화면에 없는 것"). 분류 선택지는 `categories` 모델로, `data-mock-form`과 목업 안내는 삭제, 사진 첨부 입력 삭제, 본문 `maxlength=5000` 추가. `SecurityConfig`의 `publicPreview` 목록에서 `/community/new`도 뺐다 — 저장 경로가 생긴 화면을 비로그인에게 열어 두면 폼을 다 채우고 등록에서야 튕긴다.

`screens/edit.md`의 보류 3건은 아래 결정 로그에 남겼다.

### 조각 3 — 댓글

- 대상 게시글이 `PUBLISHED`인지 검증 (DOMAIN.md 4.5)
- 삭제 시 자리 표시, 개수 집계에서 제외
- `parent_comment_id`를 코드에 등장시키지 않는다

**검증**: 삭제된 게시글에 댓글 작성 시도 거부, 삭제된 댓글이 개수에 안 세이는지, 남의 댓글 삭제 거부.

**완료 (2026-08-03)**. `CommunityMapper` +5 statement(`findRecentComments`·`countComments`·`findCommentById`·`insertComment`·`deleteComment`), `Comment` 엔티티(쓰기 경로 필드만), `dto/form/CommentForm`, `dto/view` 3종(`CommentView`·`CommentCountView`·`CommentSectionView`), `CommunityService` 5개 메서드, `CommunityController` 2개 핸들러 + 상세 확장, `detail.html`의 "댓글 기능은 준비 중입니다" 자리를 실제 댓글로 교체. migration은 없다 — `comments`에 필요한 컬럼이 V0에 전부 있다. `SecurityConfig`도 그대로다. 새 경로는 `anyRequest().authenticated()`에 걸린다.

검증은 `CommunityMapperTests`(+16), `CommunityMapperXmlTests`(+4), `CommunityServiceTests`(+19), `CommunityControllerTests`(+9), `CommunityScreenRenderingTests`(+10), `CommunityQueryCountTests`(+1), 새 `CommunityCommentScopeTests`(2)로 고정했다. 하네스 표에 H8·H9·H10·H11을 올렸다.

보류 항목이던 **댓글 페이징을 "최신 20건 + `이전 댓글 더 보기`"로 결정**했다(아래 결정 로그). DOMAIN.md 6.4에 정렬·분량·경로·상한을 함께 적었고 9절의 보류 줄을 지웠다.

구현 중 DOMAIN.md에 없던 빈칸 셋을 채우고 6.4에 반영했다: 댓글을 지울 수 있는 사람은 작성자 본인뿐이라는 것(게시글 작성자·관리자에게 권한이 없다), 댓글 작성뿐 아니라 **삭제에도** 게시글이 `PUBLISHED`여야 한다는 것, 삭제된 댓글의 본문을 조회 단계에서 `NULL`로 지운다는 것.

`getPostDetail`을 둘로 갈랐다. 댓글 검증이 실패해 상세를 다시 그릴 때 조회수를 올리면, **빈 댓글을 여러 번 보내는 것만으로 조회수가 오른다.** 화면에는 숫자가 커질 뿐이라 원인을 찾을 수 없다. 조회수를 올리는 `getPostDetail`과 올리지 않는 `getVisiblePost`로 나누고, 노출 판단은 `requireVisiblePost` 하나가 맡는다.

### 조각 4 — 좋아요

- POST/DELETE 분리, 둘 다 멱등
- `like_count` 재계산

**검증**: 같은 요청 반복 시 카운트 불변, **동시 요청 후 `like_count == post_likes 실제 개수`**, 삭제된 게시글에 좋아요 시도 거부.

**완료 (2026-08-04)**. `CommunityMapper` +5 statement(`lockPost`·`insertLike`·`deleteLike`·`recalculateLikeCount`·`existsLike`), `dto/view/PostLockView`, `CommunityService` 3개 메서드 + `requireLikeablePost`, `CommunityController` 2개 핸들러 + 상세 모델 확장(`canLike`·`likedByViewer`), `detail.html`의 숫자만 있던 `좋아요 N` 자리를 버튼과 함께 교체. migration은 없다 — `post_likes`(`UNIQUE(post_id, member_id)` + FK 2개)와 `posts.like_count`가 V0에 전부 있다. `SecurityConfig`도 그대로다. 공개 규칙이 `GET` 한정이라 새 POST 경로는 `anyRequest().authenticated()`에 걸린다.

검증은 `CommunityMapperTests`(+7), `CommunityMapperXmlTests`(+3), `CommunityServiceTests`(+9), `CommunityControllerTests`(+8), `CommunityScreenRenderingTests`(+5), 새 `CommunityLikeConcurrencyTests`(3)로 고정했다. 하네스 표에 H2c(예정이던 것)와 H15를 올렸다.

**교착이 조각 6과 같은 자리에서 다시 나왔고, 이번에는 미리 막았다.** `post_likes` INSERT가 FK 확인으로 부모 `posts` 행에 공유 잠금을 걸고 뒤따르는 재계산이 배타 잠금을 기다리는, H13과 똑같은 모양이다. **다만 해법은 쓸 수 없었다** — 조회수는 순서를 뒤집어 풀었는데, 여기서는 재계산이 INSERT 이후여야 새 행을 세므로 뒤집을 데가 없다. 그래서 `SELECT ... FOR UPDATE`로 배타 잠금을 앞에서 잡아 요청들이 한 줄로 서게 했다. **같은 종류의 함정이 두 번째로 나왔다는 것이 이 조각에서 배운 것이다** — `posts` 행에 쓰는 경로가 늘 때마다 잠금 순서를 따져야 한다.

**하네스가 무는지 직접 확인했다.** `FOR UPDATE`를 떼고 돌려 보니 `CommunityLikeConcurrencyTests` 세 개가 전부 `DeadlockLoserDataAccessException`으로 실패했고, 형태 검사도 함께 물었다. 교착은 추측이 아니라 **실제로 나는 것**이었다. 조각 1에서 배운 대로 — "검사가 있다"와 "검사가 문다"는 다르다.

잠금 조회를 `findPostById`로 재사용하지 않은 이유가 하나 더 있다. **`FOR UPDATE`는 조인한 테이블의 행까지 잠근다.** 상세 조회는 `post_categories`·`members`를 조인하므로 그대로 썼다면 좋아요 한 번에 카테고리 행이 잠기고 **같은 분류의 모든 글이 서로 줄을 선다.** 교착처럼 터지지 않고 조용히 느려지기만 해서 더 찾기 어려운 종류다. 그래서 `posts`만 읽는 `lockPost`와 `PostLockView`를 따로 뒀다.

**이 잠금 덕분에 좋아요 경로에는 R14의 경합 창이 없다.** 댓글에서 같은 잠금을 마다한 근거가 "막는 값이 싸지 않다"였는데, 좋아요는 `like_count` 때문에 어차피 같은 행에 배타 잠금을 잡아야 해서 값이 0이다. R14는 댓글에 대해서만 유효하다.

### 조각 5 — 신고·차단

- 중복 신고 에러 응답, 취소 불가
- 관리자 차단·해제, `blocked_*` 기록 및 해제 후 보존
- 보류 항목 결정: `post_reports.status` 전이, 관리자 목록 필터·정렬

**검증**: 중복 신고 거부, 비관리자의 차단 API 접근 거부(화면 숨김이 아니라 Security), 차단 해제 후 `blocked_*`가 남아 있는지.

**관리자 목업 두 화면은 도메인 규칙보다 먼저 그려졌다.** 규칙에 없는 기능이 버튼으로 존재한다 — 특히 `게시글 영구 삭제`와 댓글 `삭제`는 **DOMAIN.md에 없는 권한**이고, 관리자 조치는 차단뿐이며 `BLOCKED → DELETED`는 금지다(4.2, 6.7). 상태 어휘도 화면은 `정상`/`제재`, 문서는 `차단`으로 갈려 있다. 조각 5는 `screens/admin-detail.md`의 "조각 5에서 정하거나 고쳐야 할 것" 표를 정리하는 일부터 시작한다.

**완료 (2026-08-04)**. 보류 2건과 목업 정리를 먼저 확정하고(아래 결정 로그) 구현했다.

- **신고**: `CommunityMapper` +5 statement(`insertReport`·`existsReport`·`findReportsByPost`·~~`countPendingReports`~~(2026-08-18 삭제, 아래 결정 로그)·`closePendingReports`), `dto/form/ReportForm`, `dto/view/ReportView`, `CommunityService` 3개 메서드 + `requireReportablePost`, `CommunityController.report` + 상세 모델 확장(`canReport`·`alreadyReported`), `detail.html`에 접힌 신고 폼.
- **차단·해제·기각**: `ReportStatus`(전이 규칙 포함), `V20260804_074043__add_post_report_status_constraint.sql`, `CommunityMapper` +5 statement(`findPostsForAdmin`·`countPostsForAdmin`·`findPostByIdForAdmin`·`blockPost`·`unblockPost`), `dto/view` 3종(`AdminPostListView`·`AdminPostDetailView`·`AdminPostSort`), `dto/form/BlockForm`, 새 `CommunityAdminService`, `CommunityAdminController` 5개 핸들러, 관리자 템플릿 2종 전면 교체.
- migration은 CHECK 제약 하나뿐이다 — `post_reports`와 `posts.blocked_*`가 V0에 전부 있다. `SecurityConfig`도 그대로다. 새 경로는 `/admin/**` → `hasRole("ADMIN")`과 `anyRequest().authenticated()`에 걸린다.

검증은 `CommunitySchemaTests`, `CommunityMapperXmlTests`, `CommunityMapperTests`, `CommunityServiceTests`, `CommunityAdminServiceTests`, `CommunityControllerTests`, `CommunityAdminControllerTests`, `CommunityScreenRenderingTests`로 고정했다. 하네스 표에 H16·H17·H18을 올렸다.

**관리자 서비스를 따로 뒀다.** 고객 경로는 "노출 중인 글만"이 기본이고 그 판단이 거의 모든 메서드에 붙어 있는데, 관리자 경로는 **모든 상태를 보는 것이 기본**이다(4.3). 한 클래스에 섞으면 노출 판단을 빠뜨린 메서드가 고객 경로에서 호출되는 날이 오고, 그때 새는 것은 차단된 글의 본문이다.

**`posts` 행에 쓰는 세 번째 경로였고, 이번에는 처음부터 잠그고 시작했다.** 조회수(H13)·좋아요(H15)와 같은 자리다. 차단은 `posts`와 `post_reports`를 함께 바꾸므로 잠금 없이 하면 좋아요·조회수 경로와 순서가 엇갈린다. `lockPost`를 그대로 재사용했다 — 조인이 없는 것이 이 자리에서도 그대로 필요했다.

**전이 판단을 `PostStatus.canTransitionTo`에 맡겼다.** 관리자 경로에 조건을 새로 적으면 전이 규칙이 두 벌이 되고, enum만 고치는 날 이 경로만 옛 규칙으로 남는다. `BLOCKED → BLOCKED`가 거짓인 덕분에 "이미 차단된 글 다시 차단"이 자동으로 막혔다 — 막지 않으면 원래 조치의 시각과 사유가 덮이는데 화면에는 성공으로 보인다.

**신고는 좋아요와 정반대라 코드에서 그 대비를 두 번 적었다.** 좋아요는 중복이 멱등 성공, 신고는 중복이 에러다(6.6). 그래서 `insertReport`에는 `IGNORE`도 `ON DUPLICATE KEY`도 없고, XML 형태 검사가 그 부재를 고정한다 — 나중에 "여기도 좋아요처럼" 하고 붙이면 중복 신고가 조용히 성공한다.

구현 중 걸린 것 하나: **Thymeleaf가 HTML 주석을 응답에 그대로 내보낸다.** 관리자 상세 주석에 "이 버튼은 없앴다"고 적으면서 없앤 문구를 그대로 썼더니, 그 문구가 화면에 없다고 단언하는 테스트가 주석 때문에 실패했다. 고객 상세에서 이미 겪고 적어 둔 함정인데 같은 자리에서 다시 밟았다.

### 조각 6 — 조회수 중복 방지

**왜 이것이 먼저인가**: 조각 7의 순위가 전부 이 값에 기댄다. 순서를 바꾸면 **조작이 되는 순위 화면**을 먼저 공개하게 되고, 그 사이 쌓인 `view_count`는 나중에 신뢰할 수 없어 어차피 다시 세야 한다.

- 새 migration: `post_views(post_id, viewer_key, viewed_on)` + `UNIQUE (post_id, viewer_key, viewed_on)` + `posts(id)` FK — **당시 계획이다. 2026-08-04에 10분 창으로 바뀌면서 `viewed_on`과 `UNIQUE`가 없어졌다**(DOMAIN.md 6.2가 정본)
- `viewer_key`는 회원이면 `M:{memberId}`, 비로그인이면 `S:{sessionId}` (DOMAIN.md 6.2)
- 상세 조회 흐름을 `INSERT 시도 → 삽입 1행일 때만 view_count +1 → SELECT 상세`로 바꾼다
- migration 파일명은 직접 짓지 않고 `gradlew newMigration -Pdesc=<snake_case>`로 생성

**검증**:
- 같은 회원이 같은 날 같은 글을 여러 번 열어도 `view_count`가 1만 오르는지
- 날짜가 바뀌면 다시 오르는지
- **동시 요청 후 `view_count == post_views 실제 개수`** (H2c의 좋아요판과 같은 형태)
- 노출되지 않는 글은 `post_views` 행도 남기지 않는지 — 지금은 `view_count`만 안 오른다
- 비로그인 세션이 유지되는 동안 재조회가 안 세이는지
- 조각 3의 `더 보기`를 눌러도 조회수가 안 오르는지 (지금은 클릭마다 오른다 — R13)

**함께 손봐야 하는 것**: `CommunityQueryCountTests.getPostDetail_queryCount_isFixed`가 상세 SELECT 1회를 단언한다. `INSERT`는 update 경로라 세지 않지만, 중복 여부를 SELECT로 확인하는 구현으로 가면 이 수가 바뀐다 — **DB가 판단하게 두면 안 바뀐다.**

**완료 (2026-08-04)**. `V20260803_235726__add_post_views.sql`, `CommunityMapper.recordView` 및 `increaseViewCount` 재작성, `CommunityService.getPostDetail`에 `viewerKey` 추가, `CommunityController.viewerKeyOf`, `seed-community.sql`에 조회 이력 절 추가. 하네스 표에 H12·H13·H14를 올렸다.

검증은 `CommunityMapperTests`(+8), `CommunityMapperXmlTests`(+2), `CommunityServiceTests`(+3), `CommunityControllerTests`(+2), 새 `CommunityViewCountTests`(6)·`CommunityViewCountConcurrencyTests`(2)로 고정했다.

**구현 중 교착 상태를 발견해 설계를 바꿨다.** 처음에는 "이력을 먼저 넣고, 새로 들어갔으면 숫자를 올린다"로 만들었는데, `post_views` INSERT가 FK 확인 때문에 부모인 `posts` 행에 **공유 잠금(S)** 을 걸고 그 다음 조회수 UPDATE가 같은 행의 **배타 잠금(X)** 을 기다린다. 같은 글을 동시에 연 요청 둘이 서로 S를 쥔 채 상대의 X를 기다리면 그대로 교착이다. 인기 있는 글일수록 더 잘 터지는데, **단일 스레드 테스트로는 절대 드러나지 않는다.** `CommunityViewCountConcurrencyTests`를 만들고 나서야 잡혔다.

그래서 순서를 뒤집어 조회수 UPDATE가 X를 먼저 잡고 `NOT EXISTS`로 중복까지 판단하게 했다. 이력 INSERT는 이미 X를 쥔 상태에서 하므로 잠금이 뒤집히지 않는다. 이 순서를 H13이 고정한다.

**`ON DUPLICATE KEY UPDATE`의 갱신 행 수를 믿을 수 없다는 것도 여기서 드러났다.** MariaDB JDBC 드라이버가 `CLIENT_FOUND_ROWS`를 켜서 갱신 행 수가 '바뀐 행'이 아니라 '찾은 행'을 뜻한다. "값이 그대로면 0"에 기대는 방식은 여기서 언제나 1을 돌려주고, **제약은 멀쩡히 도는데 숫자만 부푼다.** 이력과 숫자를 비교해 보기 전에는 드러나지 않는다.

시드에서 **기존 버그도 하나 고쳤다.** `like_count` 재계산 UPDATE가 `updated_at`을 보존하지 않아, 좋아요를 받은 글마다 화면에 `(수정됨)`이 붙어 있었다. 6.2·6.3이 경고하는 바로 그 유형이고 같은 파일이라 함께 고쳤다.

### 조각 7 — 조회수 정렬·인기글

> **한 문장**: 최근 7일 활동을 **매일 새벽 배치가 한 번** 집계해 그날의 인기글을 확정하고 날짜별 스냅샷으로 고정한다. 화면은 확정된 결과만 읽는다.

> ✅ **정본은 이제 `DOMAIN.md` 6.9다 (2026-08-05, 7b와 함께 신설).** 아래 D1~D11은 **그 결정에 이르기까지의 근거와 대안**이고, "무엇이 규칙인가"를 물으면 6.9를 본다. 둘이 어긋나면 6.9가 옳다.
>
> 화면 쪽(D6·D7)도 **7c에서 6.9의 "화면" 절로 옮겼다 (2026-08-05).** 여기에 남은 것은 근거와 대안뿐이다.

DOMAIN.md 9의 보류 항목 넷이 **전부 닫혔다.**

| 보류 항목 | 상태 |
|---|---|
| `sort=likes` 추가 여부 | **닫음 (조각 7a)**. 넣지 않는다 — DOMAIN.md 6.1에 근거와 함께 반영 완료 |
| 인기글의 기간 | **닫음 (조각 7b)**. 기간별 최근 7일 — DOMAIN.md 6.9에 근거와 함께 반영 완료 |
| 인기글을 어디에 두나 | **닫음 (조각 7c)**. 목록 화면 상단의 영역 — DOMAIN.md 6.9와 9절, `SCREENS.md`에 반영 완료 |
| `post_views` 보관 기간 | **닫음 (조각 7b)**. 1차에서는 정리하지 않는다 — 6.9와 R25에 반영 완료 |

#### 결정

| # | 결정 | 근거 |
|---|---|---|
| D1 | 점수 = **최근 7일** 창의 `조회수*1 + 좋아요*25 + 댓글 쓴 서로 다른 회원 수*15` | 조회수는 비로그인 키가 세션 id라 조작 가능하고(R12) 10분 창에서 뷰어 하나가 하루 144까지 올린다. 원안의 `조회수 + 좋아요*5 + 댓글*3`은 **가장 못 믿을 신호에 가장 큰 볼륨**을 준다 — 원안 예시조차 773점 중 조회수가 530이다. 좋아요는 `UNIQUE(post_id, member_id)`라 구조적 상한이 있다. 창을 두는 이유는 기간이 없으면 초기에 쌓인 글이 인기글 영역을 영구 점유하기 때문이고, 하루가 아니라 7일인 이유는 활동이 적은 날에 좋아요 두 개짜리 글이 1위가 되어 순위가 잡음이 되기 때문이다. **댓글을 건수가 아니라 사람 수로 세는 이유는 아래에 따로 적는다** |
| D10 | 배치가 넘기는 날짜 경계를 **DB 시계 기준으로 맞춘다.** 집계 SQL이 `targetDate`를 그대로 `created_at`과 비교하지 않고, JDBC 연결의 세션 시간대를 `Asia/Seoul`로 고정한다 | 아래 "시계가 두 벌이 되는 첫 경로" |
| D2 | 집계 원본은 `post_views`·`post_likes`·`comments`의 `created_at`. **일일 카운터 테이블(`post_daily_metrics`)을 두지 않는다** | 아래 "카운터 테이블을 두지 않는 이유" |
| D3 | 배치는 `@Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")`, 대상은 **전날** | 선례 `OrderExpireScheduler`, `SchedulingConfig`에 `@EnableScheduling`이 이미 있다. 00시 정각이 아니라 00:05인 것은 자정 경계의 쓰기가 커밋될 여유를 두기 위해서다. **`zone`을 적는 것만으로는 부족하다** — 그건 배치가 깨어나는 시각일 뿐이고, 넘긴 날짜가 DB의 `created_at`과 같은 기준인지는 D10이 맡는다 |
| D4 | 멱등성 = **한 트랜잭션에서 `DELETE by date` → `INSERT` → 실행 기록**. 단 **이미 기록된 날짜는 아무것도 하지 않고 끝낸다** | 스케줄러는 실패하고 다시 돌 수 있다. 같은 입력이면 같은 결과여야 하므로 동점 tiebreaker까지 SQL에 박는다. **그런데 원본이 변하면 입력이 같지 않다** — 좋아요 취소·댓글 삭제 뒤에 같은 날짜를 다시 돌리면 확정된 순위와 근거 수치가 조용히 바뀌고, 그러면 "원본이 변해도 그날 기록은 남는다"는 스냅샷의 목적이 무너진다(PR #103 Codex 리뷰). 그래서 재실행은 **실패한 날짜에만** 의미가 있다. 성공한 날짜를 건너뛰어도 잃는 것이 없는 이유는 H31이 "실패하면 아예 손대지 않음"을 보증하기 때문이다 — **기록이 있다는 것이 곧 성공했다는 뜻**이라 판단이 성립한다. **다만 이 건너뛰기가 R22를 해소하지는 않는다.** 확인(SELECT)과 기록(INSERT)이 트랜잭션의 양 끝에 있어 **날짜를 원자적으로 선점하지 않는다** — 여러 인스턴스가 같은 시각에 깨면 전부 "기록 없음"을 보고 전부 집계한 뒤, 마지막 `INSERT`에서 PK 충돌로 한쪽만 남고 나머지는 통째로 rollback된다. 즉 줄어드는 것은 **시차를 두고 도는 재시도**뿐이고, 동시 실행의 낭비와 로그에 남는 중복키 예외는 그대로다(PR #103 Codex 리뷰 4라운드). 원자적 선점은 잠금 행이나 실행 상태 컬럼을 요구하는데, 단일 서버 전제를 R22에서 이미 수용했으므로 여기서 도입하지 않는다 — **이 행이 주는 보증은 "재실행이 확정된 날짜를 덮어쓰지 않는다"까지이고, 동시 실행 조율은 R22의 몫이다** |
| D11 | **실행 기록 테이블 `popular_post_batch_runs`를 따로 둔다.** 순위가 0건인 날도 행이 남는다 | 순위 행만으로는 **"안 돈 날"과 "돌았는데 0건인 날"이 구분되지 않는다** — 둘 다 행이 없다. 구분이 안 되면 (1) D6의 `MAX(ranking_date)`가 옛 날짜로 계속 폴백해 **7일 창 밖의 오래된 글이 무기한 노출되고**, (2) D4의 "이미 기록됐나" 판단이 매일 거짓이 되며, (3) 실패 경고가 정상 상태에서 울린다. 세 지적이 전부 같은 빈자리에서 나왔다(PR #103 Codex 리뷰 3라운드). **저장하는 것이 결과뿐이고 실행 사실이 아니었다**는 것이 원래의 누락이다. 테이블 하나가 느는 대가는 받는다 — 순위 행에 sentinel을 섞는 방법(0위 행 등)은 `PRIMARY KEY(ranking_date, ranking)`·FK·화면 쿼리를 전부 오염시킨다 |
| D5 | 배치는 **TOP 20 저장**, 화면은 **10건 노출**. **선정 SQL도 그 시점의 `PUBLISHED`만 대상으로 삼고**, 화면이 노출 시 현재 `status`를 다시 확인한다 | 노출 판단의 유일 기준은 언제나 현재 `status`다(4.1). **두 곳 모두 걸러야 한다.** 선정에서 안 거르면 이미 지워진 글이 스냅샷의 20칸을 먹는다 — 4.5가 "게시글을 지워도 자식 행은 그대로 둔다"라서 지워진 글도 창 안의 조회·좋아요·댓글을 그대로 갖고 있고, 삭제 직전에 인기였던 글일수록 상위를 차지한다. 비노출 글이 11건을 넘으면 화면이 10건보다 적게 나오거나 통째로 빈다(PR #103 Codex 리뷰). **20−10의 여유는 그 몫이 아니라 선정 이후의 상태 변화를 흡수하는 몫이다** — 둘을 헷갈리면 여유분을 아무리 늘려도 모자란다 |
| D6 | 화면은 **실행 기록의 최신 날짜**(`MAX(ranking_date) FROM popular_post_batch_runs`)를 읽는다. 그날의 순위가 0건이거나 확정 실행이 하나도 없으면 **인기글 영역 자체를 그리지 않는다.** 폴백은 **사용자 경험을 위한 의도된 설계**이고, 그 대가로 **최신 확정일이 어제보다 오래됐으면 경고 로그를 남긴다 — 단 서울 기준 01:00 이후에만 본다** | 첫 배포 후 첫 배치 전에는 보여 줄 것이 없고, 배치를 한 번 거른 날에도 없다. 최신 확정일로 폴백하면 거른 밤이 "빈 화면"이 아니라 "어제 목록 유지"로 degrade 된다 — 인기글은 하루 낡아도 읽을 만하지만(R24가 이미 최대 24시간 낡음을 설계로 받아들였다) 갑자기 비면 화면이 고장 난 것처럼 보인다. **대가는 폴백이 자기 일을 잘한다는 것 그 자체다** — 사용자에게 매끄러운 만큼 운영자에게도 아무 일 없어 보이고, 그래서 순위가 조용히 낡아 간다. H31이 막는 것은 애초에 폴백이 발동할 상황(재집계 중 스냅샷 소실)이고, 로그는 **그럼에도 발동했을 때 남는 유일한 흔적**이다. 둘은 경쟁하지 않는다 — 사용자에게는 매끄럽게, 운영자에게는 투명하게가 이 행의 목표다. **경고에 01:00 유예를 두는 이유**: 배치가 00:05에 도므로 00:00~00:04에는 정상 상태에서도 최신 확정일이 그제다. 유예가 없으면 매일 새벽 목록 요청마다 경고가 찍혀 **정상 운영이 장애로 오인된다**(PR #103 Codex 리뷰). 울지 않아야 할 때 우는 경고는 아무도 안 보게 되므로, 이건 로그를 붙인 목적 자체를 무너뜨리는 자리다. **기준을 00:05가 아니라 01:00으로 두는 이유는 00:05가 배치가 끝나는 시각이 아니라 시작하는 시각이기 때문이다**(4라운드). 집계가 도는 중에 들어온 요청은 아직 그제 날짜를 보므로, 크론 시각을 그대로 유예 종료로 쓰면 **실행 시간이 길어질수록 오경보 창이 도로 넓어진다.** 55분은 지금 데이터에 근거한 값이 아니라 **집계가 그보다 오래 걸리면 경고보다 먼저 다른 문제가 있다**는 판단이다 — 실행 시간을 재는 수단이 아직 없으므로 근거는 나중에 생긴다. 이 유예는 D3의 크론 시각과 한 벌이라 **한쪽을 바꾸면 다른 쪽도 바꿔야 한다.** **되돌아올 계기**: 배치 실행 시간이 실제로 수십 분대에 들어서면 유예를 늘리는 대신 **실행 중 상태를 기록해**(실행 기록에 시작 행을 먼저 남기는 식) 시각 기반 추정을 버린다 |
| D7 | 인기글은 **목록 화면 상단 영역**. 1쪽이고 카테고리 필터가 없을 때만 | 별도 화면이면 `SCREENS.md` 인덱스와 `screens/` 파일이 함께 생기는데 얻는 것은 주소 하나다. 필터를 건 화면에 전체 인기글이 뜨면 필터가 안 먹은 것처럼 보인다 |
| D8 | 정렬 옵션은 `latest`(기본) / `views` 둘뿐. **`sort=likes`는 넣지 않는다** | 분기마다 tiebreaker·인덱스·형태 검사가 따라붙는데, 좋아요 순으로 보고 싶은 것을 인기글 점수가 이미 대신한다(D1이 좋아요에 가장 큰 계수를 준다) |
| D9 | `post_views`는 1차에서 **정리하지 않는다** | 지우면 `view_count == COUNT(post_views)`(H14)가 깨진다. 스냅샷이 과거 순위를 보존하므로 나중에 정리로 넘어갈 근거는 생겼다 — R25에 계기와 함께 남긴다 |

#### 카운터 테이블을 두지 않는 이유 (D2)

원안은 `post_daily_metrics(metric_date, post_id, view_count, like_count, comment_count)`를 두고 조회·좋아요·댓글마다 실시간으로 올리는 구조였다. 두지 않기로 한 근거는 셋이다.

1. **요청 경로에 잠금이 하나 더 붙는다.** 조회수와 좋아요는 이미 `posts` 행 잠금 순서로 교착을 세 번 맞은 경로다(H13·H15·H17). 같은 트랜잭션에 `(metric_date, post_id)` 행 잠금이 추가되면 **잠금 순서가 두 벌**이 되고, 조각 6과 4에서 겪은 모양이 그대로 재현될 자리가 생긴다. 원안 12절이 이것을 Hot Row(성능)로 다루지만 실제 위험은 성능이 아니라 교착이다.
2. **좋아요 취소와 댓글 삭제가 저절로 맞는다.** `deleteLike`는 실제로 행을 지우므로(`CommunityMapper.xml`) 원본을 세면 취소가 그대로 반영된다. 증가만 하는 카운터였다면 취소–재좋아요 반복으로 점수를 무한히 올릴 수 있었다. 댓글도 `status`로 그냥 빠진다 — 원안 4.3이 "삭제된 댓글은 배치 재계산 시 제외"라고 적었지만, 증가 카운터는 재계산할 근거를 갖고 있지 않다.
3. **아끼는 값이 하룻밤에 한 번이다.** 카운터는 배치 한 번을 위해 요청 수만큼의 쓰기를 미리 하는 거래다. 배치는 인덱스로 창을 잘라 읽으면 되고, 그 인덱스는 어차피 만든다.

**학습 목표는 깎이지 않는다** — 스케줄러·크론·멱등성·스냅샷·트랜잭션 경계는 전부 그대로다. 카운터 테이블은 배치가 아니라 실시간 집계 쪽 주제다.

#### 댓글을 건수가 아니라 사람 수로 세는 이유 (D1)

처음 점수식은 `댓글*15`였다. **그런데 `comments`에는 `UNIQUE(post_id, member_id)`가 없다** — FK 셋뿐이고, 한 사람이 같은 글에 댓글을 몇 개든 달 수 있다. 계정 하나가 댓글 20개를 달면 300점이고, 이건 좋아요 12명분이다.

이 배점의 근거가 정확히 **"좋아요는 `UNIQUE(post_id, member_id)`라 구조적 상한이 있다"**였다. 그 상한이 없는 신호에 두 번째로 큰 계수를 준 것은 자기 근거와 어긋난다 — 조회수 계수를 1로 낮춘 논리를 댓글에는 적용하지 않았다(PR #103 Codex 리뷰).

그래서 **댓글 기여를 `COUNT(DISTINCT member_id)`로 센다.** 그러면 댓글도 좋아요와 같은 구조적 상한(회원 하나당 최대 1)을 갖고, 계수 15의 근거가 비로소 성립한다. 집계 SQL의 `UNION ALL` 형태는 그대로다 — 댓글 쪽 갈래만 `GROUP BY post_id, member_id`로 한 번 접고 세면 된다.

**바뀌는 것은 집계 SQL 한 곳뿐이다.** `comments` 스키마에 `UNIQUE`를 걸지 않는다 — "한 사람이 댓글 한 번만"이 아니라 **"한 사람이 몇 개를 달든 점수는 15점까지"**다.

| | 지금 | 바뀐 뒤 |
|---|---|---|
| `comments` 스키마 | FK 3개, UNIQUE 없음 | **그대로.** migration 없음 |
| 댓글 쓰기(`insertComment`) | 한 글에 몇 번이든 | **그대로** |
| 화면의 `댓글 N` | 건수 (4.4) | **그대로** |
| 인기 점수의 댓글 항 | `COUNT(*) * 15` | `COUNT(DISTINCT member_id) * 15` |

한 글에 여러 번 답하는 것은 정상적인 대화이고, 막으면 커뮤니티의 기능이 줄어든다. 고칠 자리는 쓰기 규칙이 아니라 **점수가 무엇을 신뢰하는가**다. 점수와 화면이 다른 것을 세게 되지만 둘은 목적이 다르다 — 표시는 "얼마나 이야기가 오갔나"이고 점수는 "몇 사람이 반응했나"다. **그 차이가 곧 이 선택의 대가이고, R27에 계기와 함께 적었다.**

#### 시계가 두 벌이 되는 첫 경로 (D10)

스케줄러는 `LocalDate.now(ZoneId.of("Asia/Seoul"))`로 날짜를 정하는데 `created_at`은 DB의 `CURRENT_TIMESTAMP(6)`로 박힌다. **`application.yml`의 local·rds JDBC URL 어디에도 세션 시간대 설정이 없다** — DB가 UTC면 서울 기준 `targetDate 00:00`이 실제로는 전날 15:00을 가리켜 창이 9시간 어긋난다(PR #103 Codex 리뷰).

지금까지 이 문제가 없었던 것은 **조회수 10분 창이 시각을 애플리케이션에서 받지 않기 때문**이다 — `NOW(6) - INTERVAL 10 MINUTE`은 DB 시계 하나로 끝나고, 그래서 "서버가 여러 대여도 시계는 하나다"라고 migration 주석에 적을 수 있었다(H13). **배치는 시계가 두 벌이 되는 첫 경로다.**

고르는 방법은 셋이다.

1. **JDBC 세션 시간대를 고정한다** (`connectionTimeZone=+09:00&forceConnectionTimeZoneToSession=true`). 한 줄이고 전역이라, 앞으로 시각을 다루는 모든 경로가 같은 기준을 갖는다.
2. 서울 날짜 경계를 애플리케이션에서 `Instant`로 바꿔 넘긴다. 배치만 고쳐지고 다음 경로는 다시 따져야 한다.
3. 집계 SQL 안에서 변환한다(`CONVERT_TZ`). MariaDB의 시간대 테이블이 채워져 있어야 하고, 로컬·CI·운영이 서로 다를 수 있다.

**1번으로 간다.** 문제의 성격이 "이 배치가 날짜를 잘못 넘긴다"가 아니라 **"애플리케이션과 DB가 서로 다른 시간대를 쓴다"**이므로, 배치 안에서 고치면 같은 함정이 다음 경로에서 또 나온다. 다만 커뮤니티 밖 전역 설정이라 **7b에서 이 변경만 따로 확인한다** — 기존 시각 데이터의 해석이 바뀌는지, 다른 도메인 테스트가 영향을 받는지. 영향이 있으면 2번으로 내려간다.

**확인했다 — 영향 없어 1번을 유지한다 (2026-08-05).** 두 프로필의 URL에 파라미터를 건 뒤 `./gradlew test` 전체 84개 스위트 899건이 통과했고, 커뮤니티 밖 도메인에서 깨진 것이 없다. **기존 시각 데이터의 해석은 바뀌지 않는다** — 지금까지 시각을 애플리케이션으로 실어 나른 경로가 없기 때문이다. `created_at`은 전부 DB가 `CURRENT_TIMESTAMP(6)`로 박았고 조회수 창도 `NOW(6)` 하나로 끝나므로, 저장된 값은 세션 시간대와 무관하게 그대로다. 바뀌는 것은 **앞으로 JDBC를 건너는 `LocalDateTime`의 기준**뿐이고, 그것이 정확히 이 설정으로 맞추려던 것이다. 이 확인은 되풀이할 필요가 없다 — 다음에 다시 물을 자리는 "전역으로 걸어도 되나"가 아니라 **새로 생기는 시각 경로가 이 기준을 쓰는가**다.

**위 확인의 범위를 좁힌다 — "값이 안 움직인다"와 "해석이 안 바뀐다"는 다르다 (2026-08-05, PR #115 Codex 리뷰).** 앞 문단은 저장된 값이 세션 시간대와 무관하게 그대로라는 것에서 **해석도 그대로**라는 결론으로 갔는데, 그 두 걸음 사이가 비어 있었다. 우리 시각 컬럼은 `DATETIME` 73개에 `TIMESTAMP` 0개이고, **`DATETIME`은 시간대가 안 붙은 벽시계 숫자라 값이 안 움직이는 것이 맞다.** 그런데 바로 그 성질 때문에 **기존 행만 옛 기준에 남고 `NOW(6)`는 새 기준으로 간다.** `TIMESTAMP`였다면 읽을 때 자동 변환되어 함께 따라왔을 자리다 — 안 움직이는 것이 여기서는 방어가 아니라 구멍이다. 그래서 UTC 세션으로 데이터가 쌓인 DB에서는 조회수 10분 창이 기존 이력을 못 보고 인기글 7일 창의 경계가 9시간 밀린다. **결론(1번 유지)은 그대로다** — 해당하는 DB가 지금 없기 때문이고, 없는 이유와 되돌아올 계기는 R31에 적었다. 앞 문단이 틀린 것이 아니라 **적용 조건을 안 적어서 일반 진술처럼 읽혔다.**

**시간대를 이름으로 적으면 연결이 아예 안 된다 (2026-08-05, 구현 중 확인).** 계획은 3번의 단점으로만 "MariaDB의 시간대 테이블이 채워져 있어야 한다"를 적었는데, **1번도 값이 이름이면 똑같이 걸린다.** `forceConnectionTimeZoneToSession=true`가 세션에 `SET time_zone`을 거는 순간 `mysql.time_zone_name`을 읽기 때문이다. 기본 설치에는 비어 있어서 `connectionTimeZone=Asia/Seoul`로 `bootRun`을 하면 Flyway가 첫 연결에서 `Unknown or incorrect time zone: 'Asia/Seoul'`로 죽는다 — **앱이 뜨지 않는다.**

그래서 **값을 `+09:00` 고정 오프셋으로 적는다.** 한국 표준시는 1988년 이후 서머타임이 없어 오프셋과 이름이 언제나 같은 값이므로 잃는 것이 없고, 대신 **팀원마다 로컬 DB에 tzinfo를 적재해야 하는 설치 단계가 사라진다.** 이름을 고집하면 README의 설치 절차가 한 단계 늘고, 그 단계를 건너뛴 사람은 커뮤니티와 무관한 화면까지 못 띄운다. 선택의 성격이 "정확도 대 편의"가 아니라 **"이름이 주는 이득이 없는데 전제만 늘어난다"**였다.

**경계 테스트는 시간대까지 본다**(H23). DB를 UTC로 띄운 컨테이너에서도 서울 기준 창이 나오는지 확인하지 않으면, 로컬이 우연히 서울이라 통과하고 운영에서만 어긋난다 — H19가 "로컬에서는 절대 안 보인다"고 적어 둔 것과 같은 종류다. **그래서 테스트 컨테이너는 UTC로 두고 연결 파라미터만 건다**(`MariaDbTestContainerConfig`). 컨테이너까지 서울로 띄우면 세션 설정이 빠져도 전부 통과해 이 하네스가 무력해진다.

#### 7a — 정렬 옵션

인기글과 독립이고 제일 작다. 먼저 해서 목록 쿼리의 정렬 분기를 만들어 둔다.

- 목록 `?sort=` (`latest` 기본 / `views`). **허용값은 `<choose>`로 매핑하고 `${}`로 잇지 않는다**(`AGENTS.md`). 조각 5의 `AdminPostSort`가 선례다
- 모르는 값은 오류가 아니라 기본 정렬로 떨어뜨린다 — 목록은 공개 화면이고 주소로 들어오는 값이다
- **분기마다 `, p.id DESC` tiebreaker를 유지한다.** 조회수는 0이 흔해 최신순보다 동점이 잦고, 동점 정렬이 흔들리면 페이지 경계에서 글이 중복·누락된다
- 새 migration: `(status, view_count, id)` 인덱스 (R8과 같은 자리)
- `screens/list.md`에 정렬 선택지 문자열 추가

**완료 (2026-08-04)**. `PostSort` enum(`AdminPostSort` 선례), `CommunityMapper.findPublishedPosts`에 `sort` 파라미터와 XML `<choose>` 분기, `CommunityService.getPosts`·`CommunityController.list` 통과, `list.html` 정렬 링크 2개, `V20260804_130038__add_post_view_count_sort_index.sql`을 추가했다. 검증은 `CommunityMapperXmlTests`(+2), `CommunityMapperTests`(+3), `CommunityServiceTests`(+1), `CommunityControllerTests`(+3), `CommunityScreenRenderingTests`(+2), `CommunitySchemaTests`(+1)로 고정하고 하네스 표에 H28·H29를 올렸다. `./gradlew clean test` 875건 통과.

구현하며 계획에 없던 자리 하나를 채웠다 — **필터 링크와 정렬 링크가 서로의 현재 값을 함께 실어야 한다.** 안 실으면 분류를 고른 뒤 조회수순을 누르는 순간 분류가 조용히 풀리는데, 목록은 멀쩡히 그려지고 글만 늘어나서 사용자에게는 "정렬이 이상하다"로 보인다. 관리자 목록이 이미 같은 방식이었고(조각 5), 쪽 이동 링크도 셋을 다 싣도록 함께 고쳤다. 렌더링 검사는 **한 링크 안에** 둘 다 있는지를 본다 — 따로 찾으면 상단 필터 링크가 `categoryId`를 갖고 있어서 정렬이 그것을 잃어도 통과한다(조각 1에서 쪽 이동 링크로 배운 그대로다). **양쪽 방향을 다 본다**: 한쪽만 보면 한 방향만 값을 싣는 구현이 통과한다.

인덱스는 컬럼 순서까지 스키마 검사로 고정했다(H29). `(view_count, status, id)`로 뒤집혀도 화면 결과는 똑같고 스캔량만 안 준다 — 조각 4의 `lockPost` 조인과 같은 종류로, **터지지 않고 조용히 느려지기만 하는** 자리라 형태를 직접 적어 두는 것 말고는 잡을 방법이 없다. 카테고리 필터가 붙으면 이 인덱스를 온전히 쓰지 못하는 것은 migration 주석에 남겼고, 트리거는 R8과 공유한다.

#### 7b — 배치

**새 migration 하나** (`gradlew newMigration -Pdesc=add_daily_popular_posts`)

```sql
CREATE TABLE IF NOT EXISTS daily_popular_posts (
    ranking_date DATE   NOT NULL,
    ranking      INT    NOT NULL,
    post_id      BIGINT NOT NULL,
    popularity_score BIGINT NOT NULL,
    view_count BIGINT NOT NULL, like_count BIGINT NOT NULL, comment_count BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (ranking_date, ranking),
    CONSTRAINT uk_daily_popular_post UNIQUE (ranking_date, post_id),
    CONSTRAINT fk_daily_popular_posts_post FOREIGN KEY (post_id) REFERENCES posts(id)
);
```

선정 당시의 조회수·좋아요·댓글 수를 함께 담는 이유는 **순위가 왜 그랬는지가 사후에 설명되어야** 하기 때문이다. 원본이 나중에 변해도 그날의 기록은 그대로 남는다.

같은 파일에 **실행 기록 테이블**을 함께 만든다 (D11).

```sql
CREATE TABLE IF NOT EXISTS popular_post_batch_runs (
    ranking_date DATE   NOT NULL,
    post_count   INT    NOT NULL,
    executed_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (ranking_date)
);
```

**순위 행과 실행 사실은 다른 것이다.** `daily_popular_posts`만 두면 "그날 배치가 돌았는가"에 답할 수 없다 — 활동이 없어 `INSERT`가 0행인 날과 배치가 아예 안 돈 날이 **둘 다 "행 없음"으로 똑같이 보인다.** 이 표에는 0건인 날도 `post_count = 0`으로 행이 남으므로 둘이 갈린다. FK는 걸지 않는다: 순위가 0건인 날에는 참조할 게 없고, 이 표가 가리키는 것은 게시글이 아니라 **실행**이다.

같은 파일에 **집계용 인덱스 셋**을 함께 만든다. 없으면 배치가 세 테이블을 통째로 스캔한다 — 지금 `post_views`의 유일한 인덱스는 선두가 `post_id`(`ix_post_views_post_viewer_created`)라 `created_at` 범위로는 못 탄다.

```sql
ALTER TABLE post_views ADD INDEX IF NOT EXISTS ix_post_views_created (created_at, post_id);
ALTER TABLE post_likes ADD INDEX IF NOT EXISTS ix_post_likes_created (created_at, post_id);
ALTER TABLE comments   ADD INDEX IF NOT EXISTS ix_comments_created   (created_at, post_id);
```

**`IF NOT EXISTS`가 이 파일의 규칙이다.** 두 `CREATE TABLE`에도 붙인다. 이 migration은 서로 다른 다섯 테이블을 건드리므로 **한 문장으로 묶을 수가 없고**, MariaDB의 DDL은 트랜잭션이 아니라서 세 번째 문장이 잠금 시간 초과로 실패하면 앞의 테이블과 인덱스 둘만 남은 채 버전은 기록되지 않는다. 그러면 재시도가 매번 `Table already exists`로 죽는다 — 손으로 지우기 전에는 복구되지 않는다(PR #103 Codex 리뷰).

**파일을 다섯으로 쪼개는 방법은 쓰지 않는다.** migration 템플릿의 규칙이 "서로 의존하는 DDL 은 파일을 나누지 말고 이 파일에 함께 담는다"이고, 이 다섯은 **함께 있어야 배치가 동작하는 한 벌**이다. 나누면 인덱스 없이 테이블만 있는 중간 버전이 정상 상태로 기록되어, 그 시점에 배치가 돌면 세 테이블을 통째로 스캔한다.

`IF NOT EXISTS`는 문장 단위 재시도를 안전하게 만들어 같은 문제를 푼다 — **부분 적용이 남아도 재실행이 그 자리를 그냥 지나간다.** V20260804_102934가 세 `ALTER`를 한 문장으로 묶어 푼 것과 목적은 같고, 대상 테이블이 여럿이라 수단만 다르다.

**집계 SQL의 형태**: 세 원본을 창으로 **먼저 자른 뒤** `UNION ALL` + `GROUP BY post_id`. 게시글마다 도는 스칼라 서브쿼리로 쓰면 대상이 **전체 게시글**이 되어 창의 이득이 사라진다. 조각 1의 H1a와 반대 방향의 판단인데, 이유는 "무엇에 비례하는가"가 다르기 때문이다 — 목록은 한 쪽 20건에 비례하지만 배치는 창 안의 이벤트 수에 비례한다.

- 창: `created_at >= targetDate - 6일 00:00:00` **이상**, `targetDate + 1일 00:00:00` **미만** (대상일 포함 7칸). 시간대 기준은 D10
- 댓글은 `status = 'PUBLISHED'`만 세고, **건수가 아니라 `COUNT(DISTINCT member_id)`다**(D1). 댓글 갈래만 `GROUP BY post_id, member_id`로 한 번 접는다
- 점수 0인 글은 제외한다 — 활동 없는 글로 20칸을 채우지 않는다
- 순위는 `ROW_NUMBER() OVER (ORDER BY score DESC, post_id DESC)`. 테스트 컨테이너가 `mariadb:11.4.10`이라 윈도 함수를 쓸 수 있다
- **동점 tiebreaker를 SQL에 박는다.** 없으면 같은 날짜를 두 번 돌렸을 때 순위가 흔들려 D4의 멱등성이 거짓이 된다

**Java**

- `PopularPostBatchService.createDailyRanking(LocalDate)` — `@Transactional`. **실행 기록을 먼저 확인하고 있으면 즉시 끝낸다**(D4) → `DELETE` → `INSERT ... SELECT` → **실행 기록 `INSERT`**. 넷이 한 트랜잭션인 것이 중요하다: 순위만 들어가고 기록이 없으면 다음 실행이 그 날짜를 다시 계산하고, 기록만 들어가고 순위가 없으면 0건인 날과 구분되지 않는다. **확인과 기록이 트랜잭션의 양 끝이라 이 순서는 날짜를 선점하지 않는다** — 동시에 깬 인스턴스는 전부 집계하고 마지막에 한쪽만 남는다(D4 말미, R22)
- `PopularPostScheduler` — 크론으로 깨어나 **전날을 계산해 서비스에 넘기는 일만** 한다. 날짜 계산과 집계를 갈라 둬야 테스트가 서비스를 직접 부를 수 있다. 시계는 `Clock` 빈으로 주입해 고정한다

**완료 (2026-08-05).** `V20260805_073107__add_daily_popular_posts.sql`(테이블 둘 + 집계 인덱스 셋), `CommunityMapper`에 배치 문장 넷(`existsBatchRun`·`deleteDailyRanking`·`insertDailyRanking`·`insertBatchRun`), `PopularPostBatchService`, `PopularPostScheduler`, `ClockConfig`, 시드 두 곳을 추가했다. 정본은 `DOMAIN.md` 6.9로 옮겼고 9절의 보류 둘을 닫았다. 검증은 `PopularPostBatchTests`(11), `CommunityMapperXmlTests`(+5), `CommunitySchemaTests`(+1), `CommunitySeedTests`(+2), `PopularPostSchedulerTests`(1)로 고정했다. `./gradlew test` 899건 통과.

**배치 문장을 별도 매퍼로 빼지 않았다.** 매퍼는 고객·관리자로만 가른다는 규칙(CLAUDE.md)을 따랐고, 7c의 화면 조회도 같은 파일로 들어온다. 인기글 SQL이 두 파일로 흩어지면 **선정과 노출이 각각 `PUBLISHED`를 봐야 한다는 D5**를 한자리에서 볼 수 없다. 규칙이 든 근거(같은 `posts` 행의 잠금 순서)는 배치에 해당하지 않지만, 규칙을 좁게 해석해 예외를 만드는 것보다 한 파일에 두는 편이 이 자리에서는 더 얻는 것이 많았다.

**계획에 없던 자리 하나를 채웠다 — 시간대를 이름으로 적으면 앱이 아예 안 뜬다.** D10의 서술을 그대로 옮겨 `connectionTimeZone=Asia/Seoul`로 적었더니 Flyway가 첫 연결에서 죽었다. 근거와 대안은 D10 아래에 적었고, 값은 `+09:00`이다. **계획서에 적힌 설정 문자열이 검증된 값이 아니라는 것**이 이 조각에서 배운 것이다 — 3번 방식의 단점으로만 적어 둔 전제가 1번 방식에도 그대로 걸려 있었다.

**H22의 시나리오를 D4에 맞춰 고쳐 썼다.** 원래 문장은 "같은 날짜로 두 번 돌려도 결과가 같다"인데, 3라운드에서 D4가 확정된 날짜를 건너뛰게 되면서 **두 번째 호출이 집계 SQL에 닿지도 않는다** — 그대로 구현하면 tiebreaker가 없어도 통과하는 검사가 된다. H31에서 한 번 밟은 것과 같은 종류의 자기충돌이라, 실행 기록만 지우고 부르도록 바꿨다(실패한 날의 재실행이 실제로 밟는 경로이기도 하다).

#### 7c — 화면

- `CommunityMapper`: `findLatestRankingDate()` — **`popular_post_batch_runs`에서 읽는다**(D11), `daily_popular_posts`가 아니다 — + `findPopularPosts(rankingDate, limit)`. 후자는 `JOIN posts p ... AND p.status = 'PUBLISHED' ORDER BY ranking LIMIT 10`
- `CommunityService.getList`가 인기글을 함께 싣는다. **1쪽 + 카테고리 필터 없음**일 때만 (D7)
- `list.html` 상단 영역, `screens/list.md` 갱신 — 문자열 표, 확정 날짜 표기, **비었을 때 영역이 통째로 사라진다는 사실**

**시드 두 곳을 함께 고친다.** `daily_popular_posts.post_id`가 `posts`를 참조하므로, 배치가 한 번이라도 돈 뒤에는 `seed-local.sql`·`seed-community.sql`의 `DELETE FROM posts`가 FK 위반으로 죽는다 — **시드 재실행이 통째로 실패한다.** `post_views`를 넣을 때 똑같이 겪은 자리이고 그때 남긴 주석 형식이 두 파일에 이미 있다(`e275bc7`). `DELETE FROM daily_popular_posts;`를 `DELETE FROM posts;`보다 위에 넣고, 실행 기록(`popular_post_batch_runs`)도 함께 지운다 — FK는 없지만 남겨 두면 **시드로 글을 새로 깔아도 배치가 "이미 돌았다"고 판단해 건너뛴다**(D4). **테이블을 만드는 커밋에서 함께 한다** — 먼저 넣으면 없는 테이블을 지우게 되어 지금 멀쩡한 시드가 깨진다. H6을 함께 넓힌다(PR #103 Codex 리뷰).

**검증**: H21~H28, H31~H33. 스케줄러의 크론 표현식 자체는 테스트하지 않는다 — 시간을 기다리는 테스트가 되고, 값이 틀려도 실패까지 하루가 걸린다. 대신 스케줄러가 `LocalDate.now(서울) - 1일`을 넘기는지만 고정한 시계로 본다.

**H1a는 넓혀야 한다** — 지금 H1a는 목록 SQL이 `ORDER BY p.created_at DESC, p.id DESC`인지 단언한다. 정렬 분기가 생기면 이 단언은 그대로는 깨지고, `<choose>`의 **분기마다** 형태를 고정하도록 넓혀야 한다. 넓히지 않고 지우면 tiebreaker가 사라져도 아무도 모른다.

**완료 (2026-08-05).** `PopularPostView`·`PopularSectionView`, `CommunityMapper`에 조회 문장 둘(`findLatestRankingDate`·`findPopularPosts`), `CommunityService.getPopularSection`, `CommunityController.list`의 모델, `list.html` 상단 영역을 추가했다. 정본은 `DOMAIN.md` 6.9의 "화면" 절로 옮겼고 9절의 마지막 보류(인기글을 어디에 두나)를 닫았다. `SCREENS.md` 17행의 유보 문단도 함께 정리했다 — 인기글은 새 화면이 아니라 목록 화면이 넓어진 것이라 `screens/list.md`가 받는다. 검증은 `CommunityServiceTests`(+8), `CommunityMapperXmlTests`(+2), `CommunityMapperTests`(+6), `CommunityScreenRenderingTests`(+2)로 고정하고 H25와 H27을 **적용**으로 올렸다. `./gradlew test` 918건 통과.

**슬라이스 테스트 둘이 함께 깨졌다.** `CommunityService`가 `Clock`을 주입받게 되면서 `@Import(CommunityService.class)`만 있던 `CommunityQueryCountTests`·`CommunityViewCountTests`가 컨텍스트를 못 띄웠다. `ClockConfig`를 함께 올려 고쳤다 — **슬라이스는 필요한 빈을 스스로 다 적어야 한다**는 성질이 드러난 자리이고, 생성자에 협력자를 하나 더 붙일 때마다 이 목록을 본다.

**계획에 없던 자리 셋을 채웠다.**

1. **인기글 줄에 숫자를 싣지 않는다.** 계획은 "무엇을 그리는지"를 비워 두었고, 스냅샷이 조회·좋아요·댓글을 함께 보존하니 그리는 것이 자연스러워 보인다. 그런데 **같은 글이 아래 목록에도 나오고 그쪽은 현재 수치다** — 한 화면에 같은 글의 숫자가 둘이면 사용자에게는 어느 쪽도 못 믿을 값이 된다. 스냅샷의 근거 수치는 사후 설명용이지 화면용이 아니다(D5가 "왜 그랬는지가 설명되어야 한다"고 적은 대상은 운영자다).
2. **D7의 조건(1쪽 + 필터 없음)을 Controller가 아니라 Service에 뒀다.** 계획서 문장이 "`getList`가 인기글을 함께 싣는다"여서 어느 층의 일인지가 열려 있었다. 규칙은 화면이 늘면 한 벌씩 늘고 **두 벌이 되는 순간 갈린다** — 조각 3·5에서 두 번 밟은 자리다. 검사는 결과가 비었는지가 아니라 **매퍼를 아예 안 부르는지**를 본다: 조회해 놓고 버리는 구현도 화면은 똑같고, 필터를 건 모든 공개 요청이 쿼리를 두 번 더 돌린다.
3. **확정된 실행이 하나도 없을 때는 경고를 남기지 않는다.** D6은 "최신 확정일이 어제보다 오래되면 경고"라고만 적었는데, 확정 실행이 아예 없는 상태는 그 비교에 닿지 못한다. 첫 배포 직후에는 그것이 정상이고 배치가 몇 주째 안 돈 상태와 구분할 수단이 지금은 없다 — 조용한 쪽으로 틀렸고, 그 빈자리를 R30에 적었다.

### 조각 14 — 공지사항

규칙의 정본은 `specs/community-notice.md`다. 여기에는 **무엇을 어떤 순서로 만드는지**만 둔다.

셋으로 나눈 기준은 "그 조각만으로 브라우저에서 확인이 되는가"다. 표만 만들고 끝나는 조각은 두지
않는다 — 확인할 화면이 없으면 다음 조각에서 되돌아온다.

#### 14a — 표와 관리자 CRUD (E4·C5·C6·C7) — **완료**

**왜 먼저였나**: 표가 없으면 아무것도 못 하고, 관리자 CRUD가 없으면 고객 화면에 보여 줄 공지를
만들 방법이 시드밖에 없다. 관리자 화면까지 닿아야 14b를 손으로 확인할 수 있다.

- migration `V20260812_065639__add_community_notices.sql` — 표 + 상태 `CHECK` + **기간 `CHECK`**
- `Notice` entity, `NoticeStatus` enum(`PUBLISHED → DELETED`만 허용, `MemberStatus`·`PostStatus` 선례)
- `CommunityNoticeMapper` + XML **하나**. 게시글처럼 고객·관리자로 나누지 않는다 — 14b의
  `<sql id="visibleNotice">`를 고객 조회 셋이 `<include>`로만 써야 하는데, 네임스페이스가 갈리면
  건너 참조하거나 복사하게 되고 spec E4가 막으려던 자리가 그대로 열린다. 대신 노출 조건을 **걸지
  않는** 관리자 조회는 `selectAdmin*` 이름으로 뗐다(모든 상태·모든 기간)
- dto: `NoticeForm`, `NoticeUpdateCommand`, `AdminNoticeListRow`·`AdminNoticeDetailRow`·`NoticeLockRow`,
  `AdminNoticeListView`·`AdminNoticeDetailView`, `NoticeDisplayStatus`(`예정`/`노출 중`/`종료`/`삭제됨`)
- `CommunityNoticeAdminService` — `Clock` 주입, 잠금 → 전이 확인 → 조건부 UPDATE → 0행 거절
  (`CommunityAdminService`와 같은 순서)
- `CommunityNoticeAdminController` (`/admin/community/notices/**`)
- 화면: `templates/admin/community/notice/{list,form}.html`. 진입점은 **관리자 커뮤니티 목록의
  버튼**이다 — 사이드바(`fragments/admin/**`)는 공통 협의 파일이라 건드리지 않았다
- `seed-community.sql` 9절에 네 상태 샘플. **시각은 실행일 기준 상대값**(2026-08-11 결정 로그)

**검증**: H39·H40·H41(근거는 spec `검증` 절). 그 밖에 상태 전이 enum 표, 재삭제 0행, `DELETED`
공지 수정 거절, 폼 기간 검증, 관리자 목록 렌더링을 각 계층 테스트가 본다.

#### 14b — 고객 노출 (B8 상단 영역 · B9 전체보기 · B10 상세) — **완료**

- `<sql id="visibleNotice">` 하나와 그것을 `<include>`하는 조회 3종: 상단·전체보기가 함께 쓰는
  목록, 개수, 상세 1건. 정렬도 `<sql id="visibleNoticeOrder">` 하나다
- `CommunityNoticeService` — 자리별 건수 상수(목록 상단 10), 페이지 크기 20, `now`를 `Clock`으로
  만들어 Mapper에 넘김. **상단 영역의 "1쪽 + 필터 없음"은 Controller가 아니라 여기 있다**
  (인기글 D7과 같은 자리)
- `CommunityNoticeController` (`GET /community/notices`, `/community/notices/{id}`)
- `CommunityController` 목록 모델에 상단 영역 추가
- **`SecurityConfig`에 고객 경로 둘을 따로 적었다** — 기존 `/community/{id:\d+}`가 숫자만 받아
  `"notices"`가 걸리지 않는다. 안 적으면 비로그인이 로그인 화면으로 튕긴다
- 화면: `customer/community/notice/{list,detail}.html` 신설, `list.html` 최상단에 영역(**인기글보다 위**)과 전체보기 링크
- `detail.html`을 재사용하지 않는다. **폴더를 나누는 것이 그 결정을 드러내는 자리다** — 같은
  폴더에 `detail.html`과 나란히 두면 다음 사람이 재사용을 먼저 떠올린다

**검증**: H42·H43·H44(근거는 spec `검증` 절). 그 밖에 정렬 키와 화면 날짜가 같은 값인지, 404 규칙,
페이징, 본문 이스케이프를 각 계층 테스트가 본다.

#### 14c — 메인 노출 (D3) — **완료**

- `CommunityHomeQueryService.getNoticeSection()`(3건). **새 계약 클래스를 만들지 않았다**
- `HomeService`·`HomeController`·`main.html`
- `home`은 공통 협의 도메인이라 PR에서 확인을 받는다(조각 13과 같다)

**자리는 서비스 안내와 카테고리 사이로 정했다**(2026-08-12, 사용자 결정 변경).

**검증**: H45(근거는 spec `검증` 절) — 건수 3, 빈 영역, 그리고 **서비스 안내 < 공지 < 카테고리 순서인지**.

### 조각 10 — 회원 연동 계약 분리

**왜 지금인가**: 커뮤니티 SQL 8곳이 `members`를 직접 JOIN한다 — `conventions.md` 15.1 위반이다. 15.9의 ReadModel 예외는 **집계·요약**에만 열려 있고 게시글 목록은 한 도메인의 목록이라 해당이 없다(문서가 "관리자 후기 목록·검색"을 명시적으로 제외했고, 관리자 화면이라는 이유로 넓히지 말라고 따로 못박았다). 다만 15.8이 기존 코드를 일괄로 옮기는 것을 말리므로 한 번에 걷어내지 않고 조각으로 쪼갠다.

**걷어낼 수 있다고 판단한 근거**: JOIN 8곳이 쓰는 것은 `m.nickname`과 `(m.status = 'WITHDRAWN')` **두 값뿐**이고, `WHERE`·`ORDER BY` 어디에도 members 컬럼이 없다. 필터는 `p.status`·`p.category_id`, 정렬은 `p.created_at`·`p.view_count`·`pending_report_count`다. 그래서 "ID 목록 → 회원 요약 조립"으로 형태를 바꿔도 결과가 같다. **조각 5에서 관리자 목록에 작성자 검색을 넣지 않기로 한 결정이 여기서 값을 했다** — 넣었으면 members가 `WHERE`에 들어가 조립으로 대체할 수 없었다.

| | 자리 | 상태 |
|---|---|---|
| 10a | 회원 쪽 계약 신설 (`member` 폴더) | **완료** |
| 10b | `CommunityMapper.xml` 4곳 (목록·상세·댓글 2) | **완료** |
| 10c | `CommunityAdminMapper.xml` 4곳 (신고·관리자 목록·상세 작성자·차단 관리자) | **완료** |
| 10d | 경계 회귀 테스트 | **완료** |

**10a에서 만든 것** — 전부 새 파일이고 회원 담당자의 기존 코드는 고치지 않았다(팀 합의: `member` 폴더에 `MemberCommunity*`를 허락 없이 만들 수 있되 담당자 코드는 수정하지 않는다).

```text
member/dto/view/MemberCommunityView.java        (id, nickname, withdrawn)
member/mapper/MemberCommunityMapper.java
member/service/MemberCommunityQueryService.java  getMembersByIds(List<Long>)
resources/mapper/member/MemberCommunityMapper.xml
```

**계약을 짜며 내린 결정 셋.**

1. **`MemberStatus`를 노출하지 않는다.** enum을 돌려주면 커뮤니티가 `member.entity`를 import하게 되고, 그건 10d가 잡아야 할 바로 그 냄새다. 지금 SQL이 하던 `(status = 'WITHDRAWN')` 계산을 회원 쪽 XML에 그대로 남겨 탈퇴 판정이라는 업무 규칙을 소유 도메인에 둔다(15.1). 커뮤니티는 `withdrawn` boolean만 받는다.
2. **단건 조회 메서드를 같이 만들지 않았다**(15.8 "필요한 계약만"). 상세 화면도 원소 1개짜리 리스트로 부르면 된다. 필요해지면 그때 추가한다.
3. **없는 ID는 예외가 아니라 누락으로 다룬다.** 목록 조립이 회원 한 명 때문에 실패하면 게시글 목록 전체가 안 보인다. 수민님 PR #119의 `MemberQueryService.findByMemberId`가 `NOT_FOUND`를 던져 목록에 쓸 수 없었던 자리가 이것이다.

**#119와의 관계**: 건드리지 않는다. 머지되면 `MemberQueryService`(범용)와 `MemberCommunityQueryService`(커뮤니티 전용)가 공존한다. 15.2 표가 `<소유 도메인><참조 도메인>QueryService`를 지시하므로 커뮤니티가 쓸 계약은 후자다. `MemberCouponQueryService`가 같은 형태의 선례다.

**10b·10c에서 나올 동작 변화 둘 — 미리 적어 둔다.**

- 지금은 `INNER JOIN`이라 **회원 행이 없으면 게시글이 목록에서 조용히 사라진다.** 조립으로 바꾸면 게시글은 남고 작성자만 "탈퇴한 회원"이 된다. 8절의 규칙에 오히려 맞는 방향이지만 명백한 동작 변화라 10d의 단언으로 못박는다.
- 쿼리가 2방으로 갈리면서 **게시글과 작성자를 읽는 사이의 원자성이 없어진다.** 닉네임이 그 사이 바뀔 수 있다. 표시용이라 실질 영향은 없고, 상태 변경의 근거로 쓰는 값이 아니다(15.9의 같은 취지).

**10d가 볼 것**: 커뮤니티 XML에 `members` 참조가 남아 있지 않은지, 커뮤니티 코드가 `member.entity`를 import하지 않는지, 회원 행이 없는 게시글이 목록에서 **빠지지 않는지**. 앞의 둘은 형태 검사라 H1a·H28과 같은 종류다 — 지우면 조용히 통과하므로 형태를 직접 적어 두는 것 말고는 잡을 방법이 없다.

**10b에서 계획에 없던 자리 하나를 채웠다 — 컬럼만 빼는 것으로는 JOIN을 걷어낼 수 없다.** MyBatis 생성자 자동 매핑은 결과 컬럼 수가 record 생성자의 인자 수와 정확히 같아야 해서, `m.nickname`과 `(m.status = 'WITHDRAWN')`을 SELECT에서 지우자 `PostDetailView`가 "14개를 받아야 하는데 12개"라며 전부 터졌다. 그래서 **매퍼 반환 타입을 작성자 없는 Row로 분리했다** — `PostListRow`·`PostDetailRow`·`CommentRow`.

결과적으로 이게 더 나은 모양이다. 작성자 자리를 비워 둔 View를 들고 다니는 형태였다면 **빈 채로 화면까지 새어 나가도 컴파일과 테스트가 통과한다.** Row에는 그 자리가 아예 없고, `PostListView.of(row, author)`를 거쳐야만 화면용 DTO가 되므로 빠뜨릴 수가 없다. 노출·소유권 판단은 작성자 없이 끝나므로 Row만으로 하고, 상세가 실제로 화면에 나가는 자리에서만 회원을 조회한다. **댓글 소유권 검사(`requireOwnComment`)에는 회원 조회가 붙지 않는다** — 표기가 필요 없는 경로다.

**H1b(쿼리 수)의 기대값이 한 회씩 늘었다**: 목록 2→3, 댓글 구역 2→3, 상세 1→2. 늘어난 것은 게시글·댓글 수와 무관한 **고정 1회**이고, "적은 글과 많은 글의 횟수가 같은지"를 함께 단언하므로 회원을 행마다 조회하는 형태로 바뀌면 여전히 깨진다.

**매퍼 테스트에서 작성자 검사 3건을 덜어내고 Service 쪽으로 옮겼다.** 작성자 판정이 더 이상 커뮤니티 SQL의 책임이 아니기 때문이다. 지운 것이 아니라 자리를 옮긴 것이고, SQL 층의 탈퇴 판정은 `MemberCommunityMapperTests`가 맡는다.

**10c는 10b의 형태를 그대로 따랐다** — `AdminPostListRow`·`AdminPostDetailRow`·`ReportRow`를 만들고 `CommunityAdminService`가 조립한다. **이제 커뮤니티 XML 두 벌 모두 `members` 참조가 0건이다.**

한 자리만 모양이 달랐다. **차단 관리자는 `LEFT JOIN`이었다.** 상세는 작성자와 차단 관리자 **둘**을 봐야 하므로 두 ID를 한 번의 배치 조회로 함께 받는다. `blocked_by`가 null이면 조회 대상에서 빠지고 화면의 차단 관리자 자리도 그대로 빈다 — LEFT JOIN이던 때와 결과가 같다. `AdminPostDetailRow`가 닉네임이 아니라 `blockedBy` ID를 들고 있는 것이 그 때문이고, 매퍼 테스트의 차단 기록 검사도 닉네임이 아니라 ID를 보도록 바꿨다.

**관리자 쪽에는 목록 조회의 쿼리 수 하네스가 없었다.** H1b는 고객 목록만 봤다. 관리자 목록에도 같은 N+1 위험이 생겼으므로 **10d에서 H1b를 관리자 목록까지 넓혔다.**

**10d — 되돌아오는 것을 막는다.** 조각 10이 한 일은 JOIN을 지운 것이지만, 지운 상태를 유지하는 것은 다른 문제다. **JOIN 하나를 다시 넣으면 화면 결과가 똑같고 기존 테스트도 전부 통과한다.** 빠르고 편하기까지 해서 되돌아올 이유는 늘 있다. 그래서 세 갈래로 막았다.

- **형태 검사**(`CommunityDomainBoundaryTests`) — 커뮤니티 SQL이 `members`를 건드리지 않는지, 커뮤니티 코드가 회원 도메인에서 **합의된 계약 둘만 정확히** 쓰는지 확인한다. 허용 계약의 누락과 미합의 참조를 한 검사에서 함께 잡는다.
- **실제 DB 조립 검사**(`CommunityMemberContractTests`) — 탈퇴 회원의 글·댓글·신고가 목록에 남고 표시명만 가려지는지를 고객·관리자 양쪽에서 본다. 15.9의 마지막 문단이 가리키는 자리다: `members.status`에 값이 하나 늘거나 `nickname`이 옮겨 가면 화면의 작성자가 전부 "탈퇴한 회원"이 되는데 커뮤니티 쪽 검사는 전부 초록불이다.
- **쿼리 수**(H1b) — 관리자 목록까지 넓혔다.

하네스 표에는 H34·H35로 올렸다.

**하네스가 무는지 직접 확인했다.** `findPostById`에 `JOIN members`를 되돌리고 `CommunityService`에 `MemberStatus` import를 심으니 형태 검사 3개가 전부 빨간불이 됐고, 관리자 목록 조립을 행마다 조회하는 형태로 바꾸니 H1b가 물었다. 조각 4에서 배운 대로 — **"검사가 있다"와 "검사가 문다"는 다르다.**

**그런데 무는 폭이 좁았다 (PR #144 Codex 리뷰).** 위에서 심은 것은 **행마다** 조회하는 형태였는데, 되돌아올 가능성이 더 큰 쪽은 **작성자마다**(`distinct()` 후 단건 조회) 조회하는 형태다. 그런데 두 검사 모두 게시글을 전부 같은 회원으로 만들고 있어서, 작성자가 하나뿐이라 그 회귀는 조회가 한 번으로 끝나 그대로 통과했다. 실제 목록은 작성자가 제각각이라 그 구현이 곧 N+1인데 **데이터가 그것을 구분하지 못했다.** 같은 종류가 하나 더 있었다 — 관리자 상세 검사가 작성자와 차단 관리자를 같은 회원으로 두어 **두 자리를 뒤바꿔도 통과**했다.

셋 다 이 문서가 여러 번 적어 온 모양이다 — 조각 7a의 H28("한쪽만 보면 정렬을 통째로 지운 구현이 통과한다"), 조각 6의 H19("한쪽만 보면 창이 1분이든 하루든 통과한다")와 같은 자리다. **검사가 무는 것을 확인했더라도 어떤 회귀를 심었는지가 그만큼 중요하다.** 고친 뒤 Codex가 말한 형태를 그대로 심어 두 검사가 무는 것을 다시 확인했다.

**2차 리뷰에서 네 건이 더 나왔고 넷 다 같은 종류였다** — 검사가 보는 범위가 실제 규칙보다 좁았다.

- **테이블 하나만 봤다.** `members`만 탐지해서 `social_accounts`·`member_status_histories`를 직접 붙이면 그대로 통과했다. 회원 도메인 소유 테이블 전부로 넓혔다. `member_coupons`는 이름과 달리 쿠폰 도메인 소유라 넣지 않았고, 그 이유를 상수 옆에 적어 뒀다.
- **SQL 주석을 안 걷어냈다.** XML 주석만 걷어내서, "여기서는 members 대신 회원 계약을 쓴다"고 `--`로 적어 두기만 해도 CI가 빨간불이 됐다. **거짓 실패는 검사보다 나쁘다** — 다음 사람이 지우는 것은 JOIN이 아니라 설명이다. H1a·H28에서 주석을 걷어내는 것과 같은 이유인데 XML 쪽만 빠져 있었다.
- **차단 관리자 fixture가 `USER`였다.** 운영에서 `blocked_by`에 남는 회원은 관리자다. 전부 `USER`로 두면 회원 계약에 `role = 'USER'` 조건이 붙어도 통과하는데, 실제 관리자 상세에서는 차단 관리자 자리가 빈다. **같은 폴더의 `MemberCouponQueryMapper`가 실제로 역할을 걸러 조회하므로** 그 형태를 따라가는 변경은 충분히 있을 법하다.
- **실명과 닉네임을 같은 값으로 넣었다.** 매퍼가 `nickname` 대신 `name`을 조회하도록 바뀌어도 모든 표시명 단언이 통과한다. **화면에 실명이 뜨는 회귀라 개인정보 문제**고, 넷 중 결과가 가장 무겁다. 10a의 `MemberCommunityMapperTests`도 같은 자리여서 함께 고쳤다.

두 회귀(`nickname`→`name`, `role = 'USER'` 필터)를 실제로 심어 무는 것을 확인했다.

`build.gradle`에도 `src/main/java`를 `test` 입력으로 등록했다. H34는 컴파일된 클래스가 아니라 원문을 읽는데, 쓰지 않는 import를 되살리는 변경은 검사 결과를 바꾸지만 바이트코드는 그대로여서 Gradle이 `test`를 건너뛴다.

**10d에서 하지 않은 것 하나**: "회원 행이 없는 게시글이 목록에서 빠지지 않는지"를 실제 DB로 확인할 수 없다. `posts.member_id`가 members를 참조하는 NOT NULL FK라 그런 행을 만들 수가 없다. 탈퇴는 행을 지우는 것이 아니라 `WITHDRAWN` 상태로 두는 것이므로 실제로 도달하는 경계는 탈퇴 쪽이고, FK가 없는 상황을 가정한 조립 규칙은 Service 테스트가 mock으로 본다. **막지 못한 것이 아니라 DB가 이미 막고 있는 자리**라 테스트 클래스 주석에 근거를 적어 뒀다.

## 하네스 인덱스

**이 표는 번호와 소유처만 갖는다.** "이 검사가 없으면 무엇이 조용히 틀어지나"라는 판단 근거는
2026-08-10에 각 하네스를 소유하는 문서 본문으로 옮겼다 — 기능별은 `docs/community/specs/*.md`의
`검증` 절, 도메인 공통은 `DOMAIN.md` 11절이다. **근거가 기능과 함께 로드되지 않으면 그 기능을 고치는
사람이 읽지 않기 때문이고**, 옮기기 전에는 같은 근거가 `DOMAIN.md`와 이 표에 두 번 적혀 있었다.

| # | 무엇을 고정하나 | 근거를 가진 곳 | 상태 |
|---|---|---|---|
| H0a | 게시글·댓글 상태 전이 규칙과 상태값 제약 | `DOMAIN.md` 11 | **적용** (조각 0) |
| H0b | 미정의 상태값 저장 불가, 카테고리 3종 활성 | `DOMAIN.md` 11 | **적용** (조각 0) |
| H1a | 목록 SQL이 스칼라 서브쿼리 형태를 유지함 | `specs/community-read.md` | **적용** (조각 1, 2026-08-04 분리) |
| H1b | 목록·댓글·상세·관리자 목록의 실행 쿼리 수가 행 수와 무관 | `DOMAIN.md` 11 (네 화면 공통) | **적용** (조각 1, 10b·10d 갱신) |
| H1c | 조회수 증가가 게시글을 "수정됨"으로 만들지 않음 | `specs/community-read.md` | **적용** (조각 1) |
| H2a | 게시글 소유권·상태 조건이 SQL에도 있음 | `specs/community-post.md` | **적용** (조각 2) |
| H2b | 차단된 글에 작성자가 아무 조치도 못 함 | `specs/community-post.md` | **적용** (조각 2) |
| H2c | `like_count`와 실제 좋아요 수 일치. 동시 요청 뒤에도 | `specs/community-reaction.md` | **적용** (조각 4) |
| H2d | 조건부 UPDATE·DELETE가 0행이면 성공으로 넘어가지 않음 | `specs/community-post.md` | **적용** (조각 2) |
| H3 | Controller가 Mapper를 직접 호출하지 않음 | `DOMAIN.md` 11 | 위반 발생 시 |
| H4 | 본문·제목의 HTML이 이스케이프됨 | `DOMAIN.md` 11 | **적용** (조각 1) |
| H5 | 커뮤니티 화면이 실제로 렌더링됨 | `DOMAIN.md` 11 | **적용** (조각 1) |
| H6 | ~~로컬 시드의 삭제 순서와 `parent_comment_id` 부재~~ | — | **폐기** (2026-08-09, `docs/testing.md` 4절) |
| H7 | ~~화면 명세 `screens/*.md`가 실제 화면·테스트와 어긋나지 않음~~ | — | **폐기** (2026-08-09, `docs/testing.md` 4절) |
| H8 | ~~`parent_comment_id`가 소스·XML·템플릿에 없음~~ | — | **폐기** (2026-08-09, `docs/testing.md` 4절) |
| H9 | 댓글 목록이 최신 쪽부터 남고 자리 표시가 사라지지 않음 | `specs/community-comment.md` | **적용** (조각 3) |
| H10 | 댓글 구역 조회의 실행 쿼리 수가 댓글 수와 무관 | `specs/community-comment.md` | **적용** (조각 3) |
| H11 | 댓글 삭제의 소유권·게시글·상태 조건이 SQL에도 있음 | `specs/community-comment.md` | **적용** (조각 3) |
| H12 | 같은 사람의 창 안 재조회가 조회수를 올리지 않음 | `specs/community-read.md` | **적용** (조각 6, 2026-08-04 갱신) |
| H13 | 조회수 UPDATE가 게시글 행을 먼저 잠그고 중복까지 판단함 | `specs/community-read.md` | **적용** (조각 6, 2026-08-04 갱신) |
| H14 | `view_count`가 `post_views` 이력과 일치함. 동시 요청 뒤에도 | `specs/community-read.md` | **적용** (조각 6, 2026-08-04 강화) |
| H15 | 좋아요가 게시글 행을 먼저 잠그고 시작함 | `specs/community-reaction.md` | **적용** (조각 4, 롤백은 2026-08-07) |
| H16 | 신고가 중복을 삼키지 않음 (좋아요의 반대편) | `specs/community-reaction.md` | **적용** (조각 5) |
| H17 | 관리자 조치가 전이 규칙을 지킴 | `specs/community-admin.md` | **적용** (조각 5, 롤백은 2026-08-07) |
| H18 | 관리자 목록이 상태로 거르지 않고 미처리 신고만 셈 | `specs/community-admin.md` | **적용** (조각 5) |
| H19 | 조회 창의 폭이 실제로 10분임. 양쪽 경계를 다 봄 | `specs/community-read.md` | **적용** (2026-08-04) |
| H20 | 댓글 `더 보기`가 창과 무관하게 조회수를 올리지 않음 | `specs/community-read.md` | **적용** (2026-08-04, PR #98 Codex 리뷰) |
| H21 | 집계 SQL이 창으로 먼저 자르는 형태임 | `specs/community-popular.md` | **적용** (조각 7b) |
| H22 | 같은 날짜로 두 번 돌려도 결과가 완전히 같음 | `specs/community-popular.md` | **적용** (조각 7b) |
| H23 | 집계 창의 양쪽 경계를 다 봄 | `specs/community-popular.md` | **적용** (조각 7b) |
| H24 | 가중치가 실제로 25/15/1임 | `specs/community-popular.md` | **적용** (조각 7b) |
| H25 | 상태 필터가 선정·노출 두 곳 모두에 있음 | `specs/community-popular.md` | **적용** — 선정은 7b, 노출은 7c |
| H26 | 좋아요 취소와 삭제된 댓글이 점수에서 빠짐 | `specs/community-popular.md` | **적용** (조각 7b) |
| H27 | 확정 결과가 없을 때 인기글 영역 없이 그려짐 | `specs/community-popular.md` | **적용** (조각 7c) |
| H28 | 목록 정렬 분기마다 `id` tiebreaker가 있음 | `specs/community-read.md` | **적용** (조각 7a) |
| H29 | `(status, view_count, id)` 인덱스가 그 컬럼 순서로 존재함 | `specs/community-read.md` | **적용** (조각 7a) |
| H30 | 댓글 점수가 건수가 아니라 사람 수임 | `specs/community-popular.md` | **적용** (조각 7b) |
| H31 | 재집계가 실패하면 이전 스냅샷이 그대로 남음 | `specs/community-popular.md` | **적용** (조각 7b) |
| H32 | 활동이 0인 날도 확정으로 기록됨 | `specs/community-popular.md` | **적용** (조각 7b) |
| H33 | 성공한 날짜를 다시 돌려도 확정된 순위가 바뀌지 않음 | `specs/community-popular.md` | **적용** (조각 7b) |
| H34 | 커뮤니티 SQL이 `members`를 건드리지 않음 | `DOMAIN.md` 11 | **적용** (조각 10d) |
| H35 | 탈퇴 회원의 글이 남고 표시명만 가려짐 | `DOMAIN.md` 11 | **적용** (조각 10d) |
| H36 | ~~이 표가 실제 테스트와 어긋나지 않음(양방향)~~ | — | **폐기** (2026-08-09, `docs/testing.md` 4절) |
| H37 | 스케줄러가 서울 기준 전날을 배치에 넘김 | `specs/community-popular.md` | **적용** (조각 7b, 표에는 2026-08-07) |
| H38 | 메인이 자기 건수로 읽고, 그릴 것이 없으면 영역이 통째로 빠짐 | `specs/community-popular.md` | **적용** (조각 13) |
| H39 | 공지 표가 미정의 상태값과 뒤집힌 기간을 거부함 | `specs/community-notice.md` | **적용** (조각 14a) |
| H40 | 공지 노출 상태 판정이 기간 경계 양쪽에서 옳음 | `specs/community-notice.md` | **적용** (조각 14a) |
| H41 | 관리자 공지 쓰기가 Security 뒤에 있고 거절 뒤 표가 그대로임 | `specs/community-notice.md` | **적용** (조각 14a) |
| H42 | 공지 노출 조건이 한 곳에만 있고 고객 조회가 전부 그것을 `<include>`함 | `specs/community-notice.md` | **적용** (조각 14b) |
| H43 | 삭제·기간 밖 공지가 고객 경로에서 빠짐. 반대쪽과 경계 양쪽을 함께 봄 | `specs/community-notice.md` | **적용** (조각 14b) |
| H44 | 목록 상단이 1쪽·필터 없을 때만 자기 건수로 읽음 | `specs/community-notice.md` | **적용** (조각 14b) |
| H45 | 메인이 자기 건수로 읽고, 빈 영역은 빠지며, 서비스 안내와 카테고리 사이에 있음 | `specs/community-notice.md` | **적용** (조각 14c) |

이 표는 새 테스트를 빠짐없이 등록하는 목록이 아니다. 조용히 틀어지는 고유 위험과 그 위험을 소유하는
검증의 선택 근거를 다음 작업자에게 남겨야 할 때만 갱신한다. 실제 테스트의 존재와 이름은 테스트 코드가
정본이며, 이 표와 소스 목록을 대조하던 H36은 2026-08-09에 폐기했다.

검사를 걷어낼 때는 행을 지우지 말고 `폐기`로 바꾼다 — 비워 두면 다음 사람이 빠진 자리부터 찾는다.

> 번호는 **붙인 순서**다. H0·H1·H2는 조각 번호와 맞지만 H3부터는 아니다 — H3–H7이 조각 1에서 한꺼번에 올라오면서 어긋났고, 그래서 조각 3의 하네스도 `H3x`가 아니라 H8부터다. **어느 조각의 것인지는 `상태` 칸이 정본이다.**

## 위험

| # | 위험 | 상태 |
|---|---|---|
| R1 | ~~`SUSPENDED` 회원 로그인 차단에 의존~~ — **해소.** 전제가 3중으로 테스트되어 있음을 확인했다: `MemberAuthenticationServiceTests:51`(`@EnumSource({SUSPENDED, WITHDRAWN})` → `loginAllowed == false`, status 누락 시에도 차단), `MemberDetailsServiceTests:65`(`loginAllowed == false` → `UsernameNotFoundException`), `MemberAdminControllerTests:151`(정지 시 기존 세션 만료). 커뮤니티에 중복 검증을 넣지 않는다 | 해소 (2026-08-02) |
| R2 | 목록 쿼리를 `LEFT JOIN ... GROUP BY`로 바꿔도 결과가 같아 눈으로는 안 잡힌다. **실행 쿼리 수 측정으로는 잡히지 않는다** — GROUP BY로 바꿔도 쿼리는 여전히 1번이다. 형태 검사(H1a)가 있어야 잡힌다 | H1a로 방어 예정 (조각 1) |
| R3 | soft delete 도입이 이 프로젝트의 첫 사례다. 다른 도메인에 선례가 없어 팀 컨벤션과 어긋날 수 있다 | 조각 1 리뷰에서 확인 |
| R4 | `comment_count` 비정규화 컬럼이 없어 집계로 처리한다. 트래픽이 늘면 컬럼 추가로 전환 필요 | 1차에선 수용 |
| R5 | `ScreenRenderingTests.productOptionAdminScreenRendersWithSeededAdmin`이 **로컬(Windows)에서만** 실패한다. 조각 1 이전(`4c9cdb7`)에서도 동일하게 재현되며, PR #75의 CI(ubuntu)에서는 통과했다. 커뮤니티와 무관한 상품 도메인 화면 테스트이고 병합을 막지 않는다. 다만 로컬 `gradlew test`가 빨간불이라 커뮤니티 조각의 "전체 초록불" 확인은 CI로 대신해야 한다 | 환경 차이로 확인됨. 상품 담당에게 공유 (2026-08-02) |
| R6 | `seed-local.sql`만 실행하면 `post_categories`가 비어 커뮤니티 글쓰기가 불가능하다. `seed-community.sql`을 이어서 실행해야 한다는 안내가 README에는 아직 없다 | 내가 README 반영 여부를 직접 확인 (2026-08-02) |
| R7 | ~~`SCREENS.md` 검사는 문서 → 코드 한 방향뿐이다~~ | **해소 (2026-08-09).** 화면 문구 명세와 그 검사를 함께 걷어냈다. 조건부 블록이 실제로 그려지는지는 `CommunityScreenRenderingTests`가 본다 |
| R8 | 목록 정렬 `created_at DESC, id DESC`를 받쳐 줄 인덱스가 없다. `posts`에는 PK와 FK 3개(`member_id`, `category_id`, `blocked_by`)뿐이라 **카테고리 필터 없는 목록은 전체를 훑고 filesort**한다. 1차에서는 데이터가 적어 수용하고, 인덱스는 나중에 온라인 DDL로 붙일 수 있어 되돌리기 쉬운 결정이다. **다만 "모니터링 후"라고만 두면 돌아올 계기가 없다** — `posts`가 1만 건을 넘거나 목록 응답이 눈에 띄게 느려지면 `(status, created_at, id)` 복합 인덱스를 새 migration으로 추가하고 MariaDB 실행 계획으로 확인한다. **2026-08-03 갱신: 조회수 정렬이 범위에 들어오면서 `(status, view_count, id)`도 같은 자리에 필요해졌다.** 정렬 옵션은 트리거를 기다릴 것 없이 조각 7에서 인덱스와 함께 넣는다 — 없는 인덱스로 정렬을 새로 여는 것은 "수용"이 아니라 알면서 느리게 만드는 것이다 | 최신순은 1차에서 수용. 조회수 정렬은 조각 7에서 인덱스와 함께 (2026-08-02, 2026-08-03 갱신) |
| R10 | 댓글 `이전 댓글 더 보기`에는 **상한 200이 있고, 그 너머의 댓글에는 닿을 수 없다.** 상한 자체는 필요하다 — 주소로 들어오는 값이라 막지 않으면 `?comments=99999999` 하나로 한 게시글의 댓글을 전부 메모리에 올린다. 문제는 200을 넘긴 글에서 오래된 댓글이 **읽을 방법 없이 남는다**는 것이다. 화면이 그 사실을 말하도록 해 두었으므로(`오래된 댓글 일부는 표시하지 않습니다.`) 조용히 사라지지는 않지만, 사실을 알려 주는 것과 닿게 해 주는 것은 다르다. 되돌아올 계기를 R8과 같은 방식으로 적어 둔다 — **한 게시글의 댓글이 200건을 넘는 일이 실제로 생기면** 댓글 전용 쪽 번호나 커서 페이징으로 바꾼다. 1차 데이터로는 발생하지 않는다 | 1차에선 수용. 트리거 도달 시 재검토 (2026-08-03) |
| R11 | `comments`에는 `fk_comments_post`(`post_id`) 인덱스만 있다. 댓글 목록은 `post_id`로 좁힌 뒤 `created_at DESC, id DESC`로 정렬하므로 **한 게시글 안에서 filesort**가 일어난다. 상한이 200이라 정렬 대상이 게시글 하나의 댓글 수를 넘지 않고, R8과 달리 전체 스캔이 아니다. R10과 같은 트리거를 공유한다 — 댓글이 수백 건인 글이 생기면 `(post_id, created_at, id)` 복합 인덱스를 새 migration으로 추가한다 | 1차에선 수용 (2026-08-03) |
| R12 | **비로그인 조회수 중복 방지는 사람의 반복 조회만 막는다.** 키가 세션 id라, 쿠키를 지우거나 애초에 쿠키를 받지 않는 클라이언트로 요청하면 매 요청이 새 조회자가 된다 — 스크립트 한 줄로 뚫린다(DOMAIN.md 6.2). 조회수가 순위를 정하는 이상 이건 **순위 조작이 여전히 가능하다**는 뜻이다. 그래도 이 방식을 택한 이유는 실제로 자주 일어나는 것이 조작이 아니라 새로고침·뒤로가기·댓글 `더 보기` 같은 반복 조회이고, 그건 막히기 때문이다. **진짜 위험은 남은 구멍이 아니라 막았다고 믿는 것**이므로 보증 수준을 6.2에 적어 두었다. 되돌아올 계기: **순위가 실제로 조작된 정황이 보이면** 회원 조회만 집계하도록 좁히거나(계정 생성이 비용이 된다) 순위 기준을 좋아요로 옮긴다 — `post_likes`는 `UNIQUE(post_id, member_id)`라 구조적으로 중복이 없다 | 수용하고 한계를 명시. 트리거 도달 시 재검토 (2026-08-03) |
| R13 | ~~**조각 6이 들어오기 전까지 `view_count`는 순위에 쓸 수 없는 값이다.**~~ — **해소 (2026-08-04, 조각 6).** 중복 방지가 들어갔고, 신뢰할 수 없던 기존 값은 migration에서 0으로 되돌렸다(재계산할 이력이 없었다). 새로고침도 댓글 `더 보기`도 더는 숫자를 올리지 않는다. 원문: **조각 6이 들어오기 전까지 `view_count`는 순위에 쓸 수 없는 값이다.** 지금은 새로고침도, 조각 3의 댓글 `더 보기` 클릭도 그대로 +1이다. 즉 댓글이 많은 글일수록 조회수가 저절로 부푼다 — 순위 신호로 삼으면 자가 인플레다. 조각 6이 이 둘을 함께 해소하지만, **그 전에 조각 7을 먼저 하면 안 된다**는 것이 조각 표의 순서 제약이다. 이미 쌓인 값도 신뢰할 수 없으므로 조각 6에서 `post_views` 기준으로 재계산할지 0으로 되돌릴지 정한다 | 조각 6에서 해소 (2026-08-03) |
| R14 | **댓글 작성·삭제의 `PUBLISHED` 검증에는 경합 창이 남는다.** 확인하는 SELECT와 뒤따르는 INSERT/UPDATE 사이에 관리자가 글을 차단하면 그 쓰기는 통과한다(PR #91 Codex 리뷰 P2 2건). 부모 행을 잠그거나 쓰기 조건에 게시글 상태를 넣으면 막을 수 있지만 **넣지 않기로 했다.** 첫째, 이건 불변식이 아니라 권한 판단이다 — DOMAIN.md 4.5가 "게시글을 지워도 자식 행은 그대로 둔다"이므로 **"비노출 글에는 댓글이 없다"는 불변식 자체가 없고**, 경합으로 한 건 더 생겨도 새로운 종류의 상태가 아니다. 둘째, 두 경우 모두 손해가 없는 쪽으로 틀린다 — 작성은 아무에게도 안 보이는 댓글 한 건이 남는 것이고, 삭제는 **사용자가 자기 댓글을 자기 뜻대로 지운 것**이 된다(삭제에 `PUBLISHED`를 요구하는 이유는 안전이 아니라 조건을 한 벌로 두려는 것이다, DOMAIN.md 6.4). 셋째, 막는 값이 싸지 않다 — 댓글 쓰기마다 게시글 행에 잠금이 걸리고, 조각 6에서 조회수가 같은 행을 쓰게 되면서 그 행은 이미 뜨겁다. **우리는 바로 그 잠금 순서 때문에 교착을 한 번 맞았다(R13·H13).** 되돌아올 계기: **차단·삭제된 글에 댓글이 새로 달린 사례가 실제로 관측되면**, 또는 관리자 차단이 자동화되어 빈도가 오르면 `insertComment`를 `INSERT ... SELECT ... FROM posts WHERE status = 'PUBLISHED'`로 바꾸고 갱신 행 수를 확인한다. 그때는 H13의 잠금 순서를 함께 다시 따져야 한다 | 수용하고 근거를 명시. 트리거 도달 시 재검토 (2026-08-04, PR #91 Codex 리뷰) |
| R15 | **미처리 신고 많은 순 정렬을 받쳐 줄 인덱스가 없다.** `post_reports`에는 PK와 `UNIQUE(post_id, reporter_id)`, FK 인덱스뿐이라 게시글마다 도는 스칼라 서브쿼리는 `post_id` 인덱스를 타지만, 그 값으로 정렬하는 순간 filesort가 붙고 대상은 **필터를 통과한 게시글 전부**다. R8과 같은 자리이고 같은 이유로 1차에서는 수용한다 — 관리자 화면이라 동시 사용자가 사실상 한 자리 수이고, 인덱스는 나중에 온라인 DDL로 붙일 수 있다. **되돌아올 계기**: `posts`가 1만 건을 넘거나 관리자 목록이 눈에 띄게 느려지면 `post_reports(post_id, status)` 복합 인덱스를 새 migration으로 추가하고 실행 계획으로 확인한다. R8의 `(status, created_at, id)`와 함께 붙일 자리다 | 1차에선 수용. 트리거 도달 시 재검토 (2026-08-04) |
| R16 | **신고 접수에는 R14와 같은 경합 창이 남는다.** 노출 확인과 INSERT 사이에 관리자가 차단하면 그 신고는 들어간다. 같은 근거로 막지 않았다 — 게시글 행을 잠그면 조회수·좋아요와 같은 뜨거운 행에 신고 한 건마다 배타 잠금이 붙는데, 얻는 것은 **이미 조치된 글에 신고 한 건이 더 붙지 않는 것**뿐이다. 손해가 없는 쪽으로 틀린다: 관리자 화면에서 그 글은 이미 차단됨으로 보이고, 새 신고는 다음 조치 때 함께 닫힌다. 중복 신고 쪽 경합은 다르다 — 그쪽은 UNIQUE 제약이 실제로 막고 Service가 `DuplicateKeyException`을 같은 응답으로 바꾼다. **되돌아올 계기는 R14와 공유한다** | 수용하고 근거를 명시 (2026-08-04) |
| R17 | **신고자는 자기 신고가 어떻게 처리됐는지 알 수 없다.** 접수 시 `신고를 접수했습니다.` 한 줄이 전부이고, 이후 차단됐는지 기각됐는지 볼 화면이 없다. 신고 내역은 관리자 화면에만 있다(6.6). 1차에서 수용하는 이유는 알림 도메인과 엮이기 때문이다 — 결과를 알리려면 신고자에게 보낼 경로가 필요하고, 그건 커뮤니티 밖이다. 다만 **차단된 글은 목록에서 사라지므로 결과가 간접적으로는 보인다.** 되돌아올 계기: 같은 글을 두고 "신고했는데 왜 그대로냐"는 문의가 실제로 생기면, 알림 도메인이 준비된 뒤 `RESOLVED`/`REJECTED` 전이에 통지를 붙인다 | 1차에선 수용 (2026-08-04) |
| R18 | **관리자 상세의 신고 내역에 상한이 없다**(PR #96 Codex 리뷰 P2). 한 글의 신고를 전부 읽고 사유는 최대 500자이며, 화면도 한 번에 렌더링한다. 하필 신고 많은 순 정렬이 그 글을 맨 위에 올리므로 **관리자가 가장 먼저 여는 화면이 가장 무겁다.** 1차에서 수용하는 근거는 댓글(R10)과 다르다 — 댓글은 상한이 없으면 한 사람이 무한히 늘릴 수 있지만, 신고는 `UNIQUE(post_id, reporter_id)`라 **회원 수가 곧 구조적 상한**이고 한 글에 수백 건이 쌓이려면 실제 회원 수백 명이 같은 글을 신고해야 한다. **되돌아올 계기**: 한 게시글의 신고가 100건을 넘는 일이 실제로 생기면 신고 내역을 최신 N건으로 자르고 누적·미처리 개수는 별도 집계로 뽑는다(댓글에서 쓴 방식 그대로). 그때 "일부만 보여 준다"는 사실을 화면에 적는 것까지 R10과 같다 | 1차에선 수용. 트리거 도달 시 재검토 (2026-08-04, PR #96 Codex 리뷰) |
| R19 | **작성자가 지운 글의 미처리 신고는 닫을 방법이 없다.** 기각을 막았기 때문이다(6.6) — `REJECTED`는 "관리자가 보고 문제없다고 판단했다"는 기록인데 판단할 글이 사라진 뒤에 남기면 거짓이 된다. 자동으로 닫지도 않는다: 게시글을 지워도 자식 행은 건드리지 않는 것이 4.5이고, 작성자의 삭제가 신고를 정리해 주면 "지우면 없던 일이 된다"가 되어 6.6의 성격과 어긋난다. 그래서 그 신고는 `PENDING`으로 남고 **신고 많은 순 목록의 상위에 계속 나타난다.** 지금은 상태 필터로 `DELETED`를 걸러 볼 수 있어 수용한다 — 그리고 그 글은 고객 화면에서 이미 사라졌으므로 조치가 늦어도 손해가 없다. **되돌아올 계기**: 지워진 글의 미처리 신고가 목록 상단을 실제로 가리기 시작하면, 관리자 목록의 기본 정렬에서 `DELETED`를 빼거나 `CLOSED_BY_DELETION` 같은 상태를 새로 두는 것을 검토한다(상태를 늘리는 쪽은 전이 규칙·CHECK 제약·하네스가 함께 늘어난다) | 수용하고 근거를 명시 (2026-08-04, PR #96 Codex 리뷰) |
| R20 | **`post_views.viewed_on`이 아직 남아 있다.** 10분 창 migration은 expand-contract의 **확장 단계만** 했다 — 컬럼을 지우지 않고 `NULL` 허용으로만 바꿨고, 신버전은 채우지 않는다. 컬럼 삭제(contract)는 **별도 migration으로 나중에** 한다. 그때 `DROP COLUMN`만 적으면 안 되고 UNIQUE가 이미 지워졌는지 확인해야 한다 — 컬럼만 빼면 MariaDB가 `UNIQUE (post_id, viewer_key)`를 남겨 같은 조회자의 두 번째 조회부터 상세가 500이 된다(그래서 확장 단계에서 인덱스를 명시적으로 지웠다). **되돌아올 계기**: 구버전이 도는 환경이 없다고 확신할 수 있을 때. 지금은 각자 로컬 DB라 사실상 즉시지만, 값이 큰 일이 아니라 미룬다 — 남아 있어도 `NULL`이 쌓일 뿐이다. `CommunitySchemaTests.postViews_withoutViewedOn_isAccepted`는 **컬럼을 지운 뒤에도 그대로 통과**하도록 썼으므로 그 단계에서 고칠 테스트는 없다 | 확장 단계 완료, contract 대기 (2026-08-04, PR #98 Codex 리뷰) |
| R22 | **`@Scheduled`는 조율되지 않는다. 인스턴스가 늘면 그 수만큼 배치가 동시에 돈다.** 크론은 JVM 하나 안의 타이머일 뿐이고 다른 인스턴스가 있는지 알 방법이 없다 — 같은 jar를 3대에 띄우면 00:05에 세 대가 각자 `createDailyRanking(어제)`를 부르고 셋이 보는 DB는 하나다. **다만 최종 데이터는 깨지지 않는다**: 셋 다 `DELETE by date`로 같은 행을 노려 행 잠금에서 줄을 서고, 같은 입력이라 결과가 같으며(D4), `PRIMARY KEY(ranking_date, ranking)`가 겹쳐 쓰는 것도 막는다. 그래서 실제 대가는 (1) 같은 일을 n번 하는 낭비, (2) 타이밍에 따라 로그에 남는 중복키 예외, 그리고 (3) **결과가 맞는 이유가 설계가 아니라 제약과 잠금의 부수효과**라는 것이다 — 멱등성을 안 넣었다면 그대로 깨졌다. **D4의 건너뛰기가 이 위험을 줄여 주지도 않는다** — 확인과 기록이 트랜잭션의 양 끝이라 동시에 깬 인스턴스는 전부 기록 부재를 보고 전부 집계한다(D4 말미, PR #103 Codex 리뷰 4라운드). 건너뛰기가 막는 것은 시차를 둔 재실행이지 동시 실행이 아니다. 단일 서버인 지금은 수용한다. **되돌아올 계기**: 인스턴스를 늘리기로 결정하는 순간. 그때 ShedLock(DB에 잠금 행을 두고 먼저 잡은 쪽만 실행), 배치 전용 인스턴스 1대, 또는 스케줄을 앱 밖(쿠버네티스 CronJob 등)으로 빼는 것 중에 고른다 | 단일 서버 전제로 수용. 트리거 도달 시 재검토 (2026-08-04) |
| R23 | **배치가 실패한 날을 손으로 다시 돌릴 방법이 없다.** 수동 실행 경로(관리자 화면·HTTP 엔드포인트)를 1차에 넣지 않았다 — 인증·권한·화면 명세가 함께 붙는데 아직 한 번도 실패한 적이 없다. D6의 폴백 덕에 **화면은 최신 확정일 목록으로 버티므로 손해가 사용자에게는 드러나지 않는다** — 드러나는 자리는 D6가 남기는 경고 로그뿐이고, **그래서 이 위험은 로그를 실제로 보는 사람이 있을 때만 관리된다.** 대신 그날의 순위는 영원히 생기지 않고, 다음 날 배치는 자기 날짜만 만든다. **되돌아올 계기**: 실제로 거르는 일이 생기면 관리자 수동 실행을 넣는다. 그때 임의 날짜를 받게 되므로 미래 날짜·너무 오래된 날짜를 막는 판단이 함께 필요하다. **그리고 D4가 성공한 날짜를 건너뛰므로, 수동 실행은 두 종류로 갈린다** — 거른 날을 채우는 것(그냥 부르면 된다)과 **이미 확정된 날을 고치는 것**(건너뛰기를 넘어서는 별도 의사 표시가 필요하고, 그 순간 H33이 지키는 성질을 의도적으로 깨는 것이다). 지금 후자를 만들지 않는 이유는 쓸 데가 없어서가 아니라 **무엇을 근거로 고칠 것인지가 정해져 있지 않아서**다 | 1차에선 수용 (2026-08-04) |
| R24 | **인기글은 최대 24시간 낡았다.** 오늘 올라와 폭발한 글은 내일 새벽까지 인기글에 못 들어간다. 이것은 10분 창으로 조회수를 살아 있게 만든 결정(2026-08-04)과 방향이 반대다 — 그쪽은 "숫자가 멈춰 보이는 것"이 문제라고 판단했는데, 순위는 하루 단위로 고정한다. **알고 받아들인다**: 낡음은 배치 설계의 부작용이 아니라 정의이고, 그 대가로 얻는 것이 D4·D5(같은 날의 순위가 흔들리지 않고, 선정 당시 숫자가 보존된다)다. 실시간이 필요해지는 순간이 이 설계를 버리는 순간이다. **되돌아올 계기**: 하루 안에 순위가 바뀌어야 한다는 요구가 실제로 나오면 요청 시점 집계나 Redis Sorted Set으로 옮긴다. **그때 무엇으로 옮길지는 R29에 적었다** — 실시간 스트림은 첫 후보가 아니다 | 설계상 수용 (2026-08-04) |
| R25 | **`post_views`는 계속 쌓이고 1차에서 정리하지 않는다.** 지우면 `view_count == COUNT(post_views)`(H14)가 깨진다 — 그 불변식이 조각 6의 유일한 방어선이라 가볍게 버릴 수 없다. 다만 스냅샷(`daily_popular_posts`)이 과거 순위를 보존하므로 **정리로 넘어갈 근거는 이 조각에서 처음 생겼다**: 순위의 역사는 이제 원본 이력이 아니라 스냅샷이 갖는다. **되돌아올 계기**: 행 수가 눈에 띄게 부담이 되면 H14를 "보관 기간 안에서만 성립"으로 좁히고(그러면 `view_count`는 재계산할 수 없는 누적값이 된다) 창보다 넉넉한 기간 밖을 정리한다. 좁히는 쪽이 지우는 쪽보다 먼저다 — 불변식을 말없이 어기는 것과 범위를 줄여 다시 적는 것은 다르다 | 1차에선 수용. 트리거 도달 시 재검토 (2026-08-04) |
| R26 | **배치는 조회수 조작을 막아 주지 않는다**(R12). 하루에 한 번 세는 것뿐이고, 세션 id를 갈아 끼우며 넣은 조회는 창 안에 그대로 들어간다. D1이 조회수 계수를 1로 낮춘 것이 완화이지 해결이 아니다. **오히려 배치가 위험을 한 겹 가린다** — 순위가 하루 한 번만 바뀌므로 조작이 일어난 시점과 순위가 튀는 시점이 떨어져 있고, 그 사이에 원인을 짚기 어렵다. **되돌아올 계기는 R12와 공유한다**: 조작 정황이 보이면 회원 조회만 집계하도록 좁힌다 | 수용하고 한계를 명시 (2026-08-04) |
| R27 | **댓글을 사람 수로 세면 대화가 활발한 글을 과소평가한다.** 3명이 댓글 10개씩 주고받은 글(30개)과 3명이 하나씩 달고 끝난 글(3개)이 **똑같이 45점**이다. 인기를 "몇 사람이 반응했나"로 보면 맞고 "얼마나 이야기가 오갔나"로 보면 틀리다. 중간 지점이 있다 — 회원당 상한을 1이 아니라 3으로 두면(`LEAST(회원별 댓글 수, 3)`) 두 글이 135점 대 45점으로 갈리면서 계정 하나의 최대 기여는 45점으로 묶인다. **1차에서 상한제를 안 쓰는 이유는 3이라는 숫자에 근거가 없기 때문이다** — 지금 데이터로는 정할 수 없고, 근거 없는 숫자를 정본에 박는 것보다 "사람 하나당 한 번"이라는 명확한 규칙이 낫다. SQL도 한 줄로 끝난다. **되돌아올 계기**: 운영 데이터에서 **댓글이 활발한 글이 인기글에 올라오지 않는 것이 실제로 관측되면** 회원당 상한제로 옮긴다. 그때 상한값은 관측된 분포에서 정한다 — 그게 지금 없는 근거다 | 수용하고 한계를 명시. 트리거 도달 시 재검토 (2026-08-04) |
| R28 | **조회수순 목록은 `id` tiebreaker로도 여러 쪽에 걸친 중복·누락을 막지 못한다**(PR #103 Codex 리뷰 P2). tiebreaker가 보증하는 것은 **한 쿼리 안에서 순서가 결정된다**까지이고, 오프셋 페이징의 쪽 사이 안정성은 **정렬 키가 변하지 않을 때만** 따라온다. `view_count`는 상세를 열 때마다 오르므로, 1쪽을 읽고 2쪽을 누르는 사이에 아래쪽 글이 앞으로 올라오면 `OFFSET`이 밀려 **이미 본 글이 다시 나오고 다른 글은 건너뛰어진다.** 최신순에는 없는 문제다 — `created_at`은 박히면 변하지 않는다. **위험한 것은 구멍 자체보다 H28을 통과했다는 사실이 주는 안심이다**: H28은 한 쿼리의 순서만 보므로 이 자리는 하네스로는 절대 드러나지 않고, 사용자에게는 "가끔 같은 글이 두 번 보인다" 정도로만 나타나 버그로 신고되지도 않는다. 1차에서 수용하는 이유는 값에 비해 대가가 크기 때문이다 — 제대로 막으려면 커서 페이징(정렬 키를 커서로 실어야 하는데 그 키가 변하는 값이라 조회수순에서는 커서 자체가 흔들린다)이나 순위 스냅샷이 필요하고, 후자는 조각 7b가 만드는 것과 같은 종류의 배치를 목록 정렬에도 하나 더 세우는 일이다. 그리고 실제 노출도 좁다: 조회수순은 기본 정렬이 아니고, 상위 몇 쪽에서는 조회수 차이가 커서 순서가 잘 뒤집히지 않으며, 뒤쪽 쪽수일수록 동점이 많지만 거기까지 넘기는 사용자가 드물다. **되돌아올 계기**: 조회수순으로 여러 쪽을 넘기는 탐색이 실제로 쓰이는 것이 확인되거나 중복이 눈에 띈다는 이야기가 나오면, `daily_popular_posts`처럼 **고정된 순위 스냅샷**을 목록 정렬에도 두고 그 위에서 페이징한다(그때 인기글 배치와 원본·주기를 공유할 수 있는지 먼저 따진다). 보증 수준은 `DOMAIN.md` 6.1에 적어 두었다 | 수용하고 한계를 명시. 트리거 도달 시 재검토 (2026-08-04, PR #103 Codex 리뷰) |
| R29 | **인기글을 실시간 이벤트 스트림으로 만들지 않는다 — 낡음(R24)을 감수한다는 판단과 별개로, 스트림은 이 명세에 맞는 도구가 아니다.** 네 가지가 걸린다. (1) **산출물이 스냅샷이다.** `daily_popular_posts`는 `(ranking_date, ranking)`이 키이고 선정 당시 숫자를 함께 보존한다(D5). 스트림은 *지금 값*을 갱신하는 물건이라, 날짜별 확정 기록을 만들려면 하루 끝에 상태를 떠내는 단계가 결국 필요하다 — **배치가 없어지는 게 아니라 스트림이 얹힌다.** (2) **D4 멱등성이 비싸진다.** 지금은 원본이 진실이라 언제 다시 돌려도 같은 답이 나오고, 그것을 `DELETE` → `INSERT ... SELECT` → 실행 기록 한 트랜잭션으로 산다. 스트림은 진실이 상태 저장소로 옮겨가 재계산이 이벤트 재생이 되고, 중복 전달·순서 뒤바뀜·오프셋을 전부 다뤄야 H22와 같은 보장에 닿는다. (3) **D1의 `COUNT(DISTINCT member_id)`가 증분 집계에 특히 적대적이다.** 단순 카운터는 +1로 끝나지만 distinct는 아니다 — 글마다 7일 창 안에 댓글 단 **회원 집합**을 들고 있어야 하고, 창이 하루씩 밀릴 때 만료된 회원을 빼려면 회원별 마지막 시각까지 필요하다. 배치에서 `GROUP BY post_id, member_id` 한 줄인 것이 상태 관리 문제로 승격된다. (4) **철회가 조용히 틀린다.** 댓글 삭제·글 차단은 점수를 내려야 하는데 배치는 매번 `status = 'PUBLISHED'`만 세니 저절로 맞고, 스트림은 상태 변경 이벤트를 하나만 놓쳐도 **터지지 않고 숫자만 서서히 어긋난다** — H21이 "형태가 바뀌어도 결과는 같아서 결과 검증으로는 안 잡힌다"고 적어 둔 것과 같은 종류다. 여기에 Kafka급 인프라가 스택에 새로 들어오는 대가가 붙는다. **되돌아올 계기는 성능이 아니라 요구의 변화다**(R24와 공유) — "어제의 인기글"이 "지금 뜨는 글"이 되는 순간. 그때도 **순서는 정해져 있다: 먼저 배치 주기를 줄인다**(시간마다 돌리기. `ranking_date`를 시각까지 넓히는 것 말고는 구조가 그대로라 D4·D5·철회 정확성을 전부 유지한다). 스트림은 **분 단위 신선도가 실제로 필요할 때만** 마지막 후보다 | 설계상 수용. R24와 트리거 공유 (2026-08-04) |
| R30 | **확정된 실행이 하나도 없으면 D6의 경고가 영영 울리지 않는다.** 경고는 "최신 확정일이 어제보다 오래됐는가"를 보는데, 실행 기록이 비어 있으면 비교할 날짜 자체가 없어 그 판단에 닿지 못한다. 즉 **배치가 처음부터 한 번도 안 돈 상태가 가장 조용하다** — 가장 시끄러워야 할 상태인데. 그렇다고 "기록이 없으면 경고"로 뒤집을 수도 없다: **첫 배포 직후와 몇 주째 안 돈 상태를 구분할 수단이 지금 없다.** 배포 시각을 모르고, 앱은 자기가 언제 처음 떴는지 기록하지 않는다. 뒤집으면 모든 새 환경이 뜨자마자 경고를 뱉고, 그것은 D6이 유예를 둔 이유(울지 않아야 할 때 우는 경고는 곧 아무도 안 보게 된다)를 그대로 재현한다. 조용한 쪽으로 틀린 이유는 **실제 노출이 좁기 때문이다** — 이 상태는 첫 배포 직후 최대 하루뿐이고, 그 뒤로는 한 번이라도 성공한 기록이 남아 정상 경로에 올라탄다. 첫날 배치가 실패하면 이틀째 새벽까지 아무 신호가 없는 것이 대가다. **되돌아올 계기**: 배치의 실행 자체를 감시하는 수단이 생기면(스케줄러 실행 로그를 보는 헬스체크, 또는 실행 기록에 시작 행을 먼저 남기는 D6의 "되돌아올 계기") 이 빈자리는 그쪽이 덮는다 — **날짜 비교로 실행 실패를 추정하는 것 자체가 임시 수단이고, 그래서 임시 수단의 사각이다** | 설계상 수용. D6·H27과 한 벌 (2026-08-05) |
| R31 | **세션 시간대를 `+09:00`으로 바꾸는 것은, 이미 다른 기준으로 데이터가 쌓인 DB에서는 보정 단계를 요구한다**(PR #115 Codex 리뷰). 우리 시각 컬럼은 `DATETIME` 73개에 `TIMESTAMP` 0개인데, **`DATETIME`은 시간대가 안 붙은 벽시계 숫자라 세션 시간대를 바꿔도 기존 행이 따라오지 않는다.** `TIMESTAMP`였다면 읽을 때 자동 변환되어 저절로 맞았을 자리다 — **값이 안 움직인다는 성질이 여기서는 방어가 아니라 구멍이다.** UTC 세션으로 쌓인 DB에 이 설정을 붙이면 그 순간부터 `NOW(6)`는 KST인데 기존 `created_at`은 UTC 벽시계 그대로라, 조회수 10분 창이 기존 이력을 못 봐 중복 차단이 잠시 뚫리고(6.2) 인기글 7일 창의 경계가 9시간 밀린다(6.9). **지금은 해당하는 DB가 없다**: `rds`는 Flyway가 꺼져 있어(`conventions.md` 6-1) 테이블 자체가 만들어지지 않았고, 로컬은 MariaDB 기본값이 `time_zone = SYSTEM`이라 윈도우에서 원래 KST였다. 해당하는 것은 **Docker MariaDB(기본 UTC)로 로컬을 띄운 경우뿐이고, 개발 데이터라 DB를 다시 만들면 끝난다** — 이 저장소는 이미 그것을 전제한다(checksum이 바뀌면 전원이 로컬 DB를 다시 만든다). **위험한 것은 이 구멍 자체보다 "지금 해당 없음"을 "문제 없음"으로 기억하는 것이다** — 실데이터가 생기는 순간 조용히 되살아나고, 그때는 배포 직후에야 드러난다. **되돌아올 계기**: RDS 스키마 반영 절차가 실제로 서는 순간(R21과 공유). 그때 절차에 **기존 `DATETIME`을 KST로 보정하는 단계**를 넣거나, 그게 부담이면 배치·창 경계만 변환하는 전환 단계를 둔다. 근거는 `application.yml`의 `rds` 프로필 주석에도 적어 두었다 | 수용하고 한계를 명시. R21과 트리거 공유 (2026-08-05, PR #115 Codex 리뷰) |
| R21 | **이 저장소에는 무중단 배포 절차가 없다.** 공용 RDS는 도입 여부부터 미결정이고(`team-plan.md` 0절) `rds` 프로필은 Flyway를 실행하지 않는다(`conventions.md` 6-1). 그래서 지금까지의 migration은 구버전과 신버전이 같은 DB를 동시에 보는 상황을 가정하지 않았다 — 10분 창 migration만 expand-contract로 나눴고 나머지는 그렇지 않다. **되돌아올 계기**: 공용 RDS 도입이 결정되는 순간. 그때 `team-plan.md` 0절의 "적용 이력 기록 방식과 롤백 절차"에 **스키마 변경은 expand-contract를 기본으로 한다**를 넣고, `conventions.md`로 옮긴다. 지금 규칙을 미리 세우지 않는 것은 적용 주체·순서가 안 정해진 상태에서 형식만 정하면 지켜지는지 확인할 방법이 없기 때문이다 | 트리거 도달 시 팀 결정 (2026-08-04, PR #98 Codex 리뷰) |
| R9 | ~~**해소 (2026-08-09).** 검사와 문서를 함께 걷어냈다. 아래는 그 결론에 이르기까지의 기록이다.~~ `SCREENS.md` 검사 2·4번은 화면을 렌더링하지 않고 **소스 텍스트의 모양**을 본다. 지키려는 성질의 대리물이라 **같은 뜻을 쓰는 표기의 수만큼 구멍이 남는다** — 리뷰에서 `th:text='...'`, `Matchers.not(...)`, `Matchers.not(Matchers.containsString(...))`이 각각 한 겹씩 나왔고, 막을 때마다 다음 표기가 나왔다. 4번은 한 겹 더 약하다. "문구가 화면에 있는가"가 아니라 "**다른 테스트가 그걸 단언하는가**"를 묻는 메타 검사여서, 텍스트 매칭으로는 결정할 수 없다. 정교함이 아니라 종류의 문제다 | 수용하고 **보증 수준을 문서에 명시**했다. 4번은 "연결이 명백한 거짓인지"만 본다고 SCREENS.md에 적었다. 구멍을 종류째 없애려면 문서 행마다 실제 렌더링과 대조해야 하는데(그러면 표기법이 무의미해진다) 화면 상태별 데이터를 재구성해야 해서 조각 1 범위를 넘는다. **진짜 위험은 남은 구멍이 아니라 하네스를 실제보다 강하다고 믿는 것**이므로 한계를 적는 쪽을 먼저 했다. 조각 2 이후 재검토 (2026-08-03, PR #76 Codex 리뷰 5라운드) |
| R32 | **도메인 진입점(`domain/community/CLAUDE.md`)이 머지된 코드보다 뒤처진다.** 마지막 갱신이 PR #98(조각 6)이었고, 그 뒤 조각 7의 세 PR과 조각 10의 네 PR이 반영되지 않은 채였다 — **인기글 규칙(6.9)이 통째로 없었고 회원 연동 계약(8절)은 한 줄도 없었다.** `DOMAIN.md` 6.1의 목록 SQL 예제는 조각 10b가 걷어낸 `JOIN members`를 그대로 들고 있어 **정본이 8절과 정면으로 어긋난 상태**였다. 위험한 것은 낡음 자체가 아니라 **가장 먼저 읽는 문서가 낡는다는 것**이다 — 다음 작업자(사람이든 AI든)가 이미 만든 계약을 모른 채 JOIN을 되돌리는 경로가 정확히 여기서 열린다. 지금 그것을 막고 있는 것은 문서가 아니라 H34·H35다 — **코드 쪽 회귀는 하네스가 잡는다.** 인기글도 마찬가지로 SQL 형태는 H21·H23이, 계수와 사람 수 집계는 H24·H30이 문다(PR #146 Codex 2차에서 이 문단이 "인기글에는 형태 검사가 없다"고 잘못 적고 있던 것을 고쳤다). **비어 있는 것은 문서 쪽이다** — 문서를 읽는 검사가 없어서, 규칙이 코드와 갈라져도 아무 데서도 울리지 않는다. `DOMAIN.md` 6.1의 SQL 예제가 그 증거다: 매퍼를 그대로 둔 채 예제에만 JOIN을 되살리면 H34는 통과한다. "조각마다 문서를 함께 고친다"는 규칙은 이미 있었고 지켜지지 않았다 | 밀린 것은 2026-08-07에 메웠다. **검사는 두지 않는다 (2026-08-09).** 한때 H7처럼 "안 고치면 빌드가 깨지게" 만드는 쪽을 처방으로 적었지만, 그 방식은 문서를 정규식으로 파싱해야 하고 잡는 것은 형태뿐이라 `docs/testing.md` 4절이 삭제 대상으로 정했다. 문서와 코드의 일치는 조각 완료 시 함께 고치는 것과 사람 리뷰가 맡는다 |

## 결정 로그

| 날짜 | 내용 |
|---|---|
| 2026-08-18 | **커뮤니티를 `Post`/`Comment`/`Notice`/`PopularPost` 네 패키지로 가르는 안을 기각하고, 패키지는 그대로 둔 채 클래스만 관심사별로 쪼개기로 했다.** 기각 근거 셋. (1) **이미 버린 안이다** — 2026-08-04 항목이 "기능 동사로 자르는 안은 버렸다"고 적었고 그 근거(같은 `posts` 행을 만지는 문장들의 잠금 순서를 한자리에서 따진다, H13·H15·H17)는 지금도 그대로다. `lockPost` 하나를 좋아요·신고 기각·차단 셋이 공유하므로 매퍼를 관심사로 가르면 주인을 정할 수 없다. (2) **네 이름이 코드를 못 덮는다** — Admin·Report·Like·Category·ViewCount 다섯이 갈 곳이 없다. 특히 `blockPost`는 `posts` 잠금과 `post_reports` 마감을 한 트랜잭션에 묶어 Post에도 Report에도 못 들어가는데 네 이름에는 Admin 자리가 아예 없다. 넷으로 갈라도 `CommunityService` 400줄이 300줄로만 준다. (3) **관심사 우선 패키지는 정본 위반이다** — `AGENTS.md`의 수직 슬라이스 문장과 `docs/conventions.md`의 매퍼·XML 경로 지시를 고쳐야 하는데 둘 다 전 팀 공유 문서다. 저장소 14개 도메인에 선례도 없다. **채택 근거는 크기가 아니라 `docs/testing.md`의 "테스트 30개 또는 600줄을 넘으면 운영 클래스 책임 재검토 신호"다** — `CommunityServiceTests`(32/889)·`CommunityMapperTests`(36/879)·`CommunityControllerTests`(38/722)·`CommunityScreenRenderingTests`(32/683) 넷이 동시에 넘겼다. 형제 대비로는 이상치가 아니다(`ProductOptionAdminService` 613줄이 더 크다). **조각 표에는 올리지 않는다** — 2026-08-04 매퍼 분리 항목이 세운 전례대로 기능이 아니라 리팩터링이다 |
| 2026-08-18 | **죽은 코드 둘을 지우고 구조 하네스 둘을 고쳤다**(위 결정의 선행 정리). 삭제: `CommunityApiController`(`@RestController`인데 매핑 0개, 본문이 `// TODO` 한 줄, 저장소 전체 참조 0건)와 `CommunityAdminMapper.countPendingReports`(운영 호출부 없음. 관리자 상세의 미처리 건수는 `reports.stream().filter(ReportView::isPending).count()`가 센다). 유일한 참조였던 `CommunityMapperTests`의 단언도 함께 뗐다 — **바로 다음 줄의 `findReportsByPost` 단언이 `containsExactlyInAnyOrder(REJECTED, RESOLVED)`로 "PENDING이 하나도 없다"를 이미 더 강하게 고정한다.** 하네스: `CommunityDtoBoundaryTests.javaFiles`에 `isNotEmpty`를 넣고 양쪽 `Files.list`를 `Files.walk`로 바꿨다. **고치기 전에는 dto 밑에 폴더가 하나만 생겨도 목록이 비고 `noneMatch`·`doesNotContain`이 빈 리스트 위에서 공허하게 통과했다** — 실패가 아니라 무검사이고, 이 문서가 두 번 적은 "빈 곳은 실패가 아니라 통과의 모습으로 나타난다"와 같은 모양이다. 존재하지만 `.java`가 없는 디렉터리를 물려 새 단언이 실제로 무는 것을 확인했다. **매퍼 XML은 `resources/mapper/community` 1단 깊이를 유지해야 한다** — `CommunityDomainBoundaryTests`가 목록이 비면 실패하므로 하위 폴더로 내리면 깨진다 |
| 2026-08-12 | **메인 공지 위치를 `서비스 안내` 다음, `카테고리` 앞으로 변경했다**(사용자 결정). 서비스의 첫인상을 먼저 보여 주고 상품 탐색 전에 공지를 확인하는 순서다. H45도 `서비스 안내 < 공지 < 카테고리`를 고정하도록 바꿨다. |
| 2026-08-12 | **조각 14c를 끝냈고 조각 14가 닫혔다.** 착수 전 미정이던 **메인의 자리를 최상단으로 정했다**(사용자 결정). `서비스 안내`보다도 앞이다 — 배송 중단이나 연휴 일정처럼 **스크롤해야 보이는 자리에 두면 싣는 의미가 없는** 안내라서다. 커뮤니티 목록에서 공지가 인기글 위인 것과 같은 근거를 메인 전체에 적용한 결과이고, 노출 중인 공지가 없으면 영역이 통째로 빠지므로 평소 메인의 첫 화면은 그대로다. 계약은 **`CommunityHomeQueryService`에 메서드 하나를 더했다** — 소비 도메인이 같으면 계약도 하나다. 건수 3은 커뮤니티가 갖고, 목록의 "1쪽 + 필터 없음"은 가져오지 않았다(메인에는 쪽도 필터도 없어 조건이 성립하지 않는다 — 인기글 때와 같은 판단이다). **자리 자체를 H45로 고정했다**: 섹션 순서가 밀려도 화면은 정상으로 보이지만 그 순간 이 결정의 근거가 사라지기 때문에, 낱말이 아니라 `id`로 다른 섹션들보다 앞인지를 본다. `home`은 공통 협의 도메인이라 `HomeService`·`HomeController`·`main.html` 변경은 PR에서 확인을 받아야 한다 |
| 2026-08-12 | **조각 14b를 끝냈다.** spec이 열어 둔 자리 셋을 닫았다. (1) **정렬 키를 화면 날짜와 같은 값으로 묶었다** — 목록은 `COALESCE(starts_at, created_at)`으로 정렬하는데 화면이 등록일을 보여 주면 **목록이 뒤죽박죽으로 보이고, 그 화면은 정렬이 깨진 것과 구분되지 않는다.** `NoticeView.displayedAt` 하나로 두 값을 같게 했다. (2) **`SecurityConfig`에 고객 경로 둘을 따로 적어야 했다** — 기존 규칙이 `/community/{id:\d+}`라 숫자만 받고 `"notices"`는 안 걸린다. 안 적으면 `anyRequest().hasRole("USER")`로 떨어져 비로그인이 로그인으로 튕기고, **공지를 공개로 두려던 결정이 조용히 뒤집힌다.** (3) **상단 영역의 "1쪽 + 필터 없음"을 `CommunityNoticeService`에 뒀다** — 인기글 D7과 같은 자리이고, 검사도 같은 방식으로 결과가 비었는지가 아니라 **매퍼를 아예 안 부르는지**를 본다. 그 김에 **`<sql id="visibleNotice">`가 실제로 한 번만 쓰였는지를 XML에서 세는 검사(H42)를 세웠다** — 조건을 복사해 넣은 새 조회는 처음에는 맞게 동작해서 행동 검사로는 절대 안 잡히고, 나중에 조건이 바뀔 때 한쪽만 바뀐다. E4 전체가 이 하나에 걸려 있다 |
| 2026-08-12 | **조각 14a를 끝냈다.** spec을 쓸 때 열려 있던 자리 넷을 구현하면서 닫았다. (1) **기간 순서(`starts_at < ends_at`)를 표의 `CHECK`로도 막는다.** 원래는 Service 검증으로 적었는데, 이 spec의 방식 자체가 "조건문이 아니라 스키마가 정책을 지킨다"였고 뒤집힌 기간은 **등록이 성공으로 끝나면서 공지만 영영 안 보이게** 만든다. 폼 검증은 관리자가 필드 오류로 고칠 수 있게 남기고, 표는 그 경로를 거치지 않는 쓰기까지 막는다. 길이·필수값은 폼에만 두는 것과 다르게 잡은 이유가 이것이다. (2) **Mapper를 고객·관리자로 나누지 않는다.** 게시글은 둘인데 공지는 하나다 — 14b의 `<sql id="visibleNotice">`를 고객 조회 셋이 `<include>`로만 써야 하고, 네임스페이스가 갈리면 건너 참조하거나 복사하게 되어 E4가 막으려던 자리가 그대로 열린다. (3) **템플릿을 `community/notice/` 하위로 판다.** B10이 "`detail.html`을 재사용하지 않는다"고 못 박은 것을 **폴더가 드러내게** 했다 — 같은 폴더에 나란히 두면 다음 사람이 재사용을 먼저 떠올린다. (4) **사이드바를 건드리지 않았다.** `fragments/admin/**`는 공통 협의 파일이라 진입점을 관리자 커뮤니티 목록의 버튼으로 뒀다. 관리자 목록에 상태 필터를 두지 않은 것도 여기서 정했다 — 배지가 네 상태를 한 화면에서 구분해 주고 공지는 수십 건 규모다 |
| 2026-08-11 | **공지 spec을 썼다**(`specs/community-notice.md`). 확정된 정책 일곱 위에서 **결정이 필요했던 자리가 셋** 나왔고 spec에 근거와 함께 적었다. (1) **"최신순"이 무엇의 최신인지** — 노출 기간을 함께 넣기로 하면서 갈렸다. 고객은 `COALESCE(starts_at, created_at) DESC`, 관리자는 `created_at DESC`다. 고객 쪽을 등록 시각으로 하면 예약 공지가 뜨는 날 목록 한가운데에 나타나 상단에 두는 이유가 무너진다. (2) **노출 조건이 셋이 되어 `DOMAIN.md` 4.1("노출 기준은 status 하나")에서 벗어난다** — 4.1의 목적(빠뜨릴 자리를 없앤다)은 `<sql id="visibleNotice">` 하나를 모든 고객 조회가 `<include>`하는 것으로 지킨다. 복사하면 그 자리가 그대로 열린다. (3) **과거 종료일을 거부하지 않는다** — "지금부터 감추기"가 정당한 사용이라서다. 대신 실수가 조용히 묻히지 않게 관리자 목록에 `예정`/`노출 중`/`종료` 배지를 둔다. 정렬 인덱스는 두지 않는다(수십 건 규모, 정렬 키가 함수식) |
| 2026-08-11 | **Entity·Form·View 경계를 community에서 먼저 검증한다.** 공통 `conventions.md`는 바꾸지 않고 `community_conventions.md`에 로컬 실험 범위와 종료 기준을 뒀다. Mapper 조회 행·집계·잠금 결과는 `dto/query`, 부분 UPDATE 값은 `dto/command`, 실제 화면 모델만 `dto/view`에 둔다. 2026-08-03의 쓰기용 `Post` 결정은 신규 저장 Entity를 작게 유지한다는 뜻으로 보존하되, 수정 매개변수까지 Entity가 맡게 하지는 않는다. 다른 도메인 적용과 공통 규칙 승격은 community 검증 후 별도로 결정한다. |
| 2026-08-11 | **인기글을 메인에도 싣기로 했다**(팀 피드백). `추천·인기 상품`과 `매장 정보` 사이에 **5건**이고, 목록 상단은 10건 그대로다. **B5의 "목록 화면 상단의 영역이다"가 이 결정과 정면으로 부딪혀 spec을 먼저 고쳤다** — 자리가 둘이 됐을 뿐 "별도 화면이 아니다"라는 근거는 그대로다. 건수를 부르는 쪽(`home`)이 아니라 커뮤니티가 갖는 이유는 화면이 늘 때마다 값이 흩어지기 때문이고, **10건이 실려도 화면은 멀쩡해 보인다**(인기글이 많은 날과 구분되지 않는다). 목록의 "1쪽 + 필터 없음"은 **가져오지 않았다** — 메인에는 쪽도 필터도 없어 조건이 성립하지 않고, `PageRequest(1, ...)`을 지어내면 목록의 페이지 크기가 바뀔 때 메인이 함께 흔들린다. 폴백·확정일·빈 영역·경고는 자리와 무관하게 같아서 `PopularPostReader` 하나로 모았다(그 김에 `CommunityService`에서 `Clock` 의존이 빠졌다). `home`은 공통 협의 도메인이라 **`HomeService`·`HomeController`·`main.html` 변경은 PR에서 확인을 받아야 한다** |
| 2026-08-11 | **공지사항 정책을 확정했다**(조각 14, 아직 spec 없음). 관리자만 작성·수정·삭제, **soft delete**, 등록 개수 제한 없음, 화면에는 최신순 최대 10건(메인은 3건), **노출 시작일·종료일 있음**, 일반 게시글과 공지 사이 전환 불가, 댓글·좋아요 미제공, 조회수·인기글 집계 제외. **`posts`의 플래그가 아니라 별도 `community_notices` 테이블로 간다** — 전환 불가·반응 미제공·집계 제외가 전부 "없어야 한다"는 부정형이라 플래그로 두면 `posts`를 읽는 모든 쿼리에 배제 조건을 빠짐없이 붙여야 지켜지고, 인기글 배치만 해도 원본 세 표에 조건이 번진다. 별도 테이블이면 **배치 SQL을 한 글자도 안 건드리고** 집계 제외가 성립하고 신고 불가도 따라온다. **노출 기간은 넣기로 했다** — 넣는 이상 `NULL`의 뜻(시작 `NULL`=즉시, 종료 `NULL`=무기한)을 spec에 함께 적어야 나중에 정하는 일이 없다 |
| 2026-08-11 | **로컬 시드의 시각을 고정 날짜에서 실행일 기준 상대 날짜로 바꾸고, 어제치 인기글을 시드가 직접 확정하게 했다.** 박아 둔 `2026-07-25/26`이 7일 창을 벗어난 뒤로 로컬에서 인기글 영역이 계속 비어 있었는데, **그 화면은 활동이 없는 정상 상태와 똑같이 생겨서**(B5) 시드가 낡은 것인지 기능이 깨진 것인지 구분되지 않았다 — 고정 날짜는 언젠가 반드시 이 상태가 된다. 활동을 **어제**에 두는 이유는 배치의 대상일이 전날이기 때문이고(D3), 그 덕에 이후 배치가 매일 새 대상일로 돌아도 창에 엿새 더 걸린다. 확정 스냅샷까지 시드가 만드는 것은 배치가 다음 날 00:05에야 처음 돌아 그전까지 화면이 비기 때문이다. **그 집계식은 `CommunityMapper.xml`의 `insertDailyRanking`을 옮긴 것이라 정본이 바뀌면 시드도 함께 고쳐야 한다** — 갈라져도 시드는 조용히 성공하고 로컬 순위만 운영과 달라진다. 세션 시간대를 `+09:00`으로 고정한 것은 CLI 세션의 `CURDATE()`가 배치가 넘기는 날짜와 같은 기준이어야 하기 때문이다(D10) |
| 2026-08-11 | **대댓글 깊이를 5로 확정했다**(담당자 확인 완료). 확정된 것은 **깊이 숫자 하나뿐**이고, 그 깊이를 어디서 강제하는지·트리를 어떻게 조회하는지·자르는 단위가 무엇인지는 전부 미정이다 — `specs/community-comment.md` A7이 정본이다. 특히 **B4의 "최신 20건 자르기"는 1단계 댓글을 전제로 쓰인 규칙이라 그대로 얹으면 부모 없는 자식이 남는다.** 깊이만 정해 두는 이유는 그 값이 스키마·조회 방식·화면 들여쓰기를 동시에 좌우해서 나중에 바꾸면 셋을 다 되돌려야 하기 때문이다 |
| 2026-08-11 | **2차 넷을 전부 하기로 정했다** — 검색(B6)·무한 스크롤(B7)·이미지 첨부(A4)·대댓글(A7). 조각 표에 8·9·11·12로 세웠고 **순서는 정하지 않았다.** 8·9는 각각 자르기 단위·커서 페이징이라는 선행 결정을 갖고 있어 그것 없이 착수하면 되돌아온다 |
| 2026-08-11 | **기능별 규칙을 `specs/` 여섯 개로 갈랐다.** 2차가 정해지며 기능 하나를 고치는 작업이 `DOMAIN.md` 413줄을 짊어지던 비용이 회수될 자리가 생겼다. 하네스 표의 근거 문단은 각 spec의 `검증` 절로 옮기고 `PLAN.md`에는 인덱스만 남겼다 — 같은 근거가 `DOMAIN.md`와 표에 두 번 적혀 있던 것을 없앤 것이 절반의 이유다. **절 번호 6은 비석으로 남기고 재사용하지 않는다**(공유 migration 주석이 그 번호를 박고 있다). 근거는 `DOMAIN.md` 6절 |
| 2026-08-05 | 조각 7c 완료. **인기글 줄에 숫자(조회·좋아요·댓글)를 싣지 않기로 했다** — 스냅샷이 근거 수치를 갖고 있어 그리는 것이 자연스러워 보이지만, **같은 글이 아래 목록에도 나오고 그쪽은 현재 수치다.** 한 화면에 같은 글의 숫자가 둘이면 사용자에게는 어느 쪽도 못 믿을 값이 되고, 어느 쪽이 스냅샷인지 설명할 자리도 없다. D5가 "왜 그랬는지가 사후에 설명되어야 한다"고 적은 대상은 운영자이지 목록을 훑는 사용자가 아니다. **D7의 조건(1쪽 + 필터 없음)은 Controller가 아니라 Service에 뒀다** — 규칙이라 화면이 늘면 한 벌씩 늘고, 두 벌이 되는 순간 갈린다(조각 3·5에서 두 번 밟았다). 검사도 결과가 비었는지가 아니라 **매퍼를 아예 안 부르는지**를 본다: 조회해 놓고 버리는 구현은 화면이 똑같은데 필터를 건 모든 공개 요청이 쿼리를 두 번 더 돌린다. 9절의 마지막 보류가 닫히면서 **`DOMAIN.md`의 보류 표가 전부 소진됐다** |
| 2026-08-04 | PR #103 Codex 리뷰 4라운드 4건 반영. `daily_popular_posts`에 빠져 있던 `IF NOT EXISTS`를 채우고, **H31의 시나리오를 다시 짰다** — 3라운드에서 D4에 건너뛰기를 넣은 것이 H31을 무력화하고 있었다(실행 기록이 있으면 두 번째 호출이 실패 지점에 닿지 않는다). 경고 유예를 00:05에서 01:00으로 옮겼다: 크론 시각은 배치가 **시작**하는 시각이라 집계가 도는 동안 오경보가 난다. D4가 "먼저 기록한 하나만 일한다"고 적은 부분은 사실이 아니라 **확인과 기록이 원자적이지 않다**는 쪽으로 고쳐 R22와 문구를 맞췄다 |
| 2026-08-04 | 인기글을 실시간 스트림으로 대체하는 안을 검토하고 **배치를 유지하기로 확정**. 근거를 R29에 적었다 — 산출물이 날짜 스냅샷이라 배치가 안 없어지고, D4 멱등성과 D1의 distinct 계수, 철회 처리가 전부 비싸진다. R24의 "실시간이 필요해지는 순간"이 가리키는 다음 수단을 **배치 주기 단축 → (그래도 부족하면) 스트림** 순으로 못박았다 |
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
| 2026-08-02 | ~~인기글은 **범위 밖으로 유지**~~ — **2026-08-03에 뒤집힘(아래 참조).** 기록은 지우지 않는다. 당시 판단의 근거가 무엇이었는지가 남아야 뒤집은 대가(R12)도 읽힌다. 원문: DOMAIN.md 2에서 이미 제외한 항목인 데다, 조회수 기준으로 만들 수 없다 — 6.2가 "조회수는 정렬·순위에 쓰이지 않는다"를 근거로 중복 방지를 빼서 새로고침만으로 순위 조작이 된다. 넣으려면 범위 변경 + 기준을 좋아요로 고정 + 조각 4 이후가 세트라는 것을 `SCREENS.md`에 적었다 |
| 2026-08-02 | 관리자 목업이 도메인 규칙보다 먼저 그려져 **규칙에 없는 기능이 버튼으로 존재**한다는 것을 화면 지도를 그리다 발견(관리자의 게시글·댓글 삭제, `정상`/`제재` 용어, IP 표시, 첨부 이미지). 조각 5 항목으로 옮겼다 |
| 2026-08-02 | `build.gradle`의 `test`에 `docs/`를 입력으로 등록. 등록 전에는 문서만 고쳤을 때 Gradle이 `test`를 UP-TO-DATE로 건너뛰어, 문서가 어긋나도 로컬에서 초록불이 떴다. 하네스를 만들고 나서 **그 하네스가 정말 무는지 문서를 일부러 틀리게 고쳐 확인하다가** 발견했다 |
| 2026-08-03 | PR #87 Codex 리뷰 P2 3건 전부 수용. (1) **조건부 UPDATE·DELETE의 갱신 행 수를 버리고 있었다.** SQL의 소유권·상태 조건은 검증과 UPDATE 사이의 변화를 막으라고 둔 것인데, 결과를 안 보면 **그 조건이 걸러 낸 순간이 성공으로 보인다** — 관리자가 그 찰나에 차단하면 아무것도 안 바뀌었는데 화면은 "삭제했습니다"라고 말한다. 0행이면 지금 상태를 다시 읽어 403/404를 낸다. (2) **수정 POST에서 검증 실패가 권한 확인보다 먼저 실행됐다.** 남의 글 번호로 빈 본문을 보내면 소유권도 상태도 안 보고 수정 화면이 200으로 열렸다. (3) **비활성 카테고리 오류가 공통 4xx 화면으로 튀어 쓰던 글이 사라졌다.** 분류는 화면에서 다시 고르면 되는 입력 오류라 `BindingResult`에 붙여 폼으로 되돌린다(conventions.md 9). 소유권·상태 오류는 삼키지 않고 그대로 올린다 |
| 2026-08-03 | `screens/edit.md`의 보류 3건 결정. (1) **수정 화면은 작성 화면과 같은 템플릿을 쓴다** — 수정 항목이 작성 항목과 같고(6.3) 선례도 공용이다(`ProductAdminController`, `CouponAdminController`). 갈리는 것은 `editingPostId` 모델 하나뿐이다. (2) 차단된 글의 수정·삭제는 Service에서 막는다. (3) 카테고리 변경은 허용하되 활성 카테고리로만 |
| 2026-08-03 | **차단된 글에 대한 작성자의 수정·삭제만 404가 아니라 403(`BLOCKED_POST`)으로 응답한다.** 4.3의 404 규칙은 "글의 존재를 흘리지 않기 위한" 것인데, 이 상대는 상세에서 이미 본문과 차단 사유까지 본 작성자다. 숨길 것이 없고, 404를 주면 왜 막혔는지 알 수 없다. 남의 글·`DELETED`는 그대로 404다 |
| 2026-08-03 | `SecurityConfig`의 `publicPreview` 목록에서 `/community/new`를 뺐다. 커뮤니티 밖 공용 설정이지만, 저장 경로가 생긴 화면이 목업 예외에 남아 있으면 비로그인이 폼을 다 채우고 등록에서야 로그인으로 튕긴다 |
| 2026-08-03 | 쓰기 경로용 `Post` 엔티티에 **조회수·좋아요 수·차단 기록 필드를 넣지 않았다.** 읽기 경로는 `dto/view`의 record를 쓰므로 필요가 없고, 있으면 누군가 UPDATE에 끼워 넣을 수 있다. 없는 필드는 잊은 것이 아니라는 것을 클래스 주석에 적었다 |
| 2026-08-04 | **조각 순서를 바꿔 6(조회수 중복 방지)을 4보다 먼저 했다.** 정책을 바꾼 순간부터 `view_count`는 순위에 쓸 수 없는 값이었고(R13), 조각 4·5를 하는 동안 쌓이는 값은 어차피 버려야 했다. 버릴 값을 더 쌓는 것보다 세는 법을 먼저 고치는 편이 싸다. 조각 표의 줄을 실제 진행 순서로 옮기고 번호는 붙인 순서 그대로 두었다 |
| 2026-08-04 | **조회 기록과 조회수 증가의 순서를 뒤집었다 — 교착 때문이다.** 자연스러운 순서("이력을 먼저 넣고 새로 들어갔으면 숫자를 올린다")로 만들었더니 `post_views` INSERT가 FK 확인으로 `posts` 행에 공유 잠금을 걸고, 이어지는 조회수 UPDATE가 같은 행의 배타 잠금을 기다리면서 **같은 글을 동시에 연 요청끼리 교착**에 빠졌다. 조회수 UPDATE가 먼저 잠그고 `NOT EXISTS`로 중복까지 판단하도록 바꿨다. **단일 스레드 테스트로는 절대 드러나지 않아** 동시성 테스트를 만들고 나서야 잡혔고, 인기 있는 글일수록 더 잘 터지는 종류다 |
| 2026-08-04 | 중복 판단을 `ON DUPLICATE KEY UPDATE`의 갱신 행 수로 하려다 실패. **MariaDB JDBC 드라이버가 `CLIENT_FOUND_ROWS`를 켜서 갱신 행 수가 '바뀐 행'이 아니라 '찾은 행'을 뜻한다.** "값이 그대로면 0"에 기대는 방식은 여기서 언제나 1을 돌려주고, 제약은 멀쩡히 도는데 숫자만 부푼다. 조건부 UPDATE는 아예 행을 못 찾으므로 0이 정확하다 |
| 2026-08-04 | 기존 `view_count`를 **migration에서 0으로 되돌렸다.** `post_views`가 비어 있어 과거를 재계산할 근거가 없고, 남겨 두면 `view_count == COUNT(post_views)` 불변식이 첫날부터 거짓이라 하네스로 고정할 수도 없다. 기존 값은 새로고침과 `더 보기` 클릭이 섞인 수라 순위의 근거가 못 된다 — 근거 없는 큰 수보다 근거 있는 0이 낫다. 되돌릴 수 없는 UPDATE라 백업 안내를 migration 주석에 적었다 |
| 2026-08-04 | 시드의 기존 버그를 함께 고침. `like_count` 재계산 UPDATE가 `updated_at`을 보존하지 않아 **좋아요를 받은 글마다 `(수정됨)`이 붙어 있었다.** 6.2·6.3이 경고하는 바로 그 유형("화면에는 조용히 (수정됨)이 붙을 뿐이라 원인을 찾기 어렵다")이고 같은 파일이라 함께 고쳤다. 시드에 조회 이력 절도 추가해 `view_count`가 이력과 맞게 시작하도록 했다 — 숫자만 넣어 두면 중복 방지가 도는지 눈으로 확인할 수 없다 |
| 2026-08-03 | **조회수를 정렬·순위에 쓰기로 결정. 6.2를 뒤집고 2절의 범위를 바꿨다.** `인기글·정렬 옵션`이 제외에서 포함으로 옮겨졌다. 이 결정은 **6.2와 세트로만 성립한다** — 6.2가 중복 방지를 뺀 근거 문장이 정확히 "조회수는 정렬·순위에 쓰이지 않는다"였다. 용도만 바꾸고 규칙을 두면 새로고침 한 번이 순위 조작이 된다. 그래서 조각을 둘로 나누고(6 중복 방지 → 7 정렬·인기글) 순서 제약을 조각 표에 명시했다. 2026-08-02에 "인기글은 범위 밖으로 유지"로 적었던 결정을 뒤집는 것이며, 그때 적어 둔 조건 셋 중 "기준을 좋아요로 고정"은 채우지 않고 조회수로 간다 — 그 대가가 R12다 |
| 2026-08-03 | ~~중복 방지를 **이력 테이블 + 날짜 창**으로 정함~~ — **2026-08-04에 굴러가는 10분 창으로 뒤집혔다(이 표의 맨 아래 항목).** 아래 논거 중 `UNIQUE`에 관한 부분이 틀렸다. 기록은 지우지 않는다 — 왜 그렇게 판단했는지가 남아야 무엇을 잘못 봤는지도 읽힌다. 원문: 중복 방지를 **이력 테이블 + 날짜 창**으로 정함(`post_views`, `UNIQUE(post_id, viewer_key, viewed_on)`). 세션만으로는 순위 방어가 안 되고, 굴러가는 "최근 24시간"은 **`UNIQUE`로 표현할 수 없어** 결국 코드가 판단하게 된다 — 그러면 동시 요청 두 개가 "아직 안 봤다"를 함께 읽는 구멍이 되돌아온다. 날짜 칸으로 잡으면 DB 제약이 판단하고, 대가는 자정 직후 재조회가 다시 세어지는 것뿐이다. `viewer_key`를 IP로 잡지 않은 것은 공유 NAT에서 남의 조회가 합쳐지는 데다 **개인정보를 새로 저장하게 되기** 때문이다 |
| 2026-08-03 | `posts.view_count`를 **`post_views`에서 파생된 캐시**로 규정하고, 갱신은 재계산이 아니라 **증분**으로 두기로 함. 좋아요(6.5)와 다른 선택이라 이유를 6.2에 적었다 — `post_views`는 `post_likes`의 수십 배로 쌓여 조회마다 `COUNT(*)`를 도는 비용이 다르고, 6.5의 "여러 경로가 건드린다" 논거도 여기엔 약하다(`view_count`를 건드리는 경로는 상세 조회 하나뿐). 대신 **두 값의 일치를 하네스로 고정**한다 — 증분을 택하면서 "틀어져도 아무도 모른다"까지 받아들이지는 않는다 |
| 2026-08-03 | 정렬 옵션을 `?sort=`로 두되 **허용값을 `<choose>`로 매핑**하기로 함(`AGENTS.md` SQL 안전성). 모르는 값은 오류가 아니라 기본 정렬로 떨어뜨린다 — 목록은 비로그인도 여는 공개 화면이라 주소가 망가졌다고 오류 페이지를 주지 않는다(카테고리·페이지 파라미터와 같은 처리). **`id` tiebreaker는 어느 정렬에도 붙인다.** 조회수는 0이 흔해서 동점이 최신순보다 잦고, tiebreaker가 없으면 페이지 경계에서 글이 사라진다 |
| 2026-08-03 | **보류 항목이던 댓글 페이징을 "최신 20건 + `이전 댓글 더 보기`"로 결정**(DOMAIN.md 9 → 6.4). 쪽 번호를 쓰지 않은 것은 상세 화면에 쪽 번호가 두 종류(게시글 목록·댓글) 생기기 때문이다 — "지금 몇 쪽인가"가 두 개가 되고 댓글을 쓰고 나서 어느 쪽으로 돌아갈지도 정해야 한다. 전체 로드도 쓰지 않았다. 2절이 범위 밖으로 둔 **무한 스크롤과는 다르다** — 스크롤이 아니라 누를 때만 늘어나고, 주소가 바뀌는 링크라 JS 없이 동작한다. 이 구분을 SCREENS.md "만들지 않는 화면"에도 적었다 |
| 2026-08-03 | 댓글을 **최신 N건 가져와 뒤집어** 보여주기로 함. 오래된 순으로 앞에서 자르면 댓글이 20건을 넘는 글에서 **방금 쓴 댓글이 화면 밖에 남고**, 사용자에게는 등록이 안 된 것과 구분되지 않는다. 읽는 순서는 오래된 순이어야 대화가 이어지므로 자르는 방향과 읽는 방향이 반대다. 댓글이 한두 건인 개발 화면에서는 어느 쪽이든 똑같아 보여서 H9(SQL 형태)와 렌더링 테스트로 함께 고정했다 |
| 2026-08-03 | 댓글 `limit`에 **상한 200**을 둠. 주소로 들어오는 값이라 막지 않으면 요청 하나로 한 게시글의 댓글을 전부 메모리에 올릴 수 있다. 다만 상한에 막힌 상태에서 **링크만 조용히 감추지 않기로** 했다 — 감추면 "댓글이 여기까지"로 보이는데 그것은 거짓이고, 그 화면은 200건을 넘어야 나오므로 사람 눈으로는 영원히 발견되지 않는다. 화면이 사실을 말하게 하고 닿지 못하는 구간은 R10에 트리거와 함께 적었다 |
| 2026-08-03 | 댓글 수를 **두 가지로 따로 세기로** 함. 화면의 `댓글 N`은 노출 중인 것만(4.4), "더 펼칠 게 남았는지"는 자리 표시까지. 하나로 합치면 개수가 부풀거나, **삭제된 댓글만 남은 구간에서 더 보기가 사라져 그 아래 댓글에 닿을 수 없다.** 쿼리를 늘리지 않으려고 한 번의 `SELECT`에서 조건부 집계로 둘 다 받는다 |
| 2026-08-03 | **삭제된 댓글의 본문을 조회 단계에서 `NULL`로 지우기로** 함. 자리 표시에는 쓰지 않는 값이라 화면까지 내려보낼 이유가 없다. 템플릿에서 감추는 것만으로는 응답 본문에 그대로 남아 있고, 자리 표시 마크업을 잘못 고치면 지워진 글이 되살아난다. 지운 사람이 지우기를 원한 것이 정확히 그 본문이다. 작성자명도 화면에 내지 않는다 |
| 2026-08-03 | `getPostDetail`을 **조회수를 올리는 것과 올리지 않는 것으로 분리**. 댓글 검증이 실패해 상세를 다시 그릴 때 조회수가 오르면, 빈 댓글을 여러 번 보내는 것만으로 숫자가 오른다. 화면에는 숫자가 커질 뿐이라 원인을 찾을 수 없다. 노출 판단은 `requireVisiblePost` 하나가 맡아 두 경로가 갈리지 않게 했다 |
| 2026-08-03 | 댓글 삭제에 **성공 메시지를 남기지 않기로** 함. 지운 자리에 `삭제된 댓글입니다.`가 그대로 보이므로 결과가 이미 화면에 있다. 목록에서 흔적 없이 사라지는 게시글 삭제와 다르다 — 그쪽은 알리지 않으면 지워졌는지 알 수 없어서 flash를 쓴다 |
| 2026-08-03 | DOMAIN.md 6.4의 빈칸 셋을 채움. (1) 댓글을 지울 수 있는 사람은 **작성자 본인뿐**이다 — 게시글 작성자에게도 관리자에게도 권한이 없다(관리자의 조치는 게시글 차단뿐, 6.7). (2) 작성뿐 아니라 **삭제에도** 게시글이 `PUBLISHED`여야 한다. 노출되지 않는 글의 댓글은 아무에게도 보이지 않아 지울 이유가 없고, 한쪽에만 조건을 걸면 "댓글에 손대는 조건"이 두 벌이 되어 한쪽을 고칠 때 다른 쪽을 빠뜨린다. (3) 대댓글 금지와 댓글 수정 금지를 `CommunityCommentScopeTests`로 고정한다 — DB 제약을 안 거는 것은 2차에 곧 떼야 하기 때문이고, 그 빈자리를 검사가 채운다 |
| 2026-08-04 | PR #93 Codex 리뷰 P2 3건 처리. (1) 게시글 상태 검증의 경합은 **R14와 같은 지적**이라 같은 근거로 수용하지 않았다. (2) **댓글 삭제 후 펼친 상태가 접히는 것은 고쳤다** — `screens/detail.md`가 "작성·삭제 후에는 `?comments=` 없이 돌아간다. 새 댓글은 언제나 최신 20건 안에 있으므로"라고 적고 있었는데, **그 근거는 작성에만 참이다.** 삭제에는 성공 메시지가 없어서 자리 표시가 결과를 보여 주는 유일한 신호인데, 접으면 최신 20건 밖의 자리 표시는 화면 밖으로 나가 삭제가 안 된 것과 구분되지 않는다. 삭제 폼이 지금 값을 실어 보내고 리다이렉트가 되돌린다. 검증 실패로 다시 그릴 때도 유지한다(제자리이므로). 받은 값은 정수로 다시 써서 주소에 넣는다. (3) **차단된 글의 댓글 삭제 버튼을 숨겼다** — 조건이 소유권만 보고 있어 작성자에게 눌러도 403만 나오는 죽은 버튼이 남았다. 댓글 폼과 같은 `canComment` 조건으로 묶었다. 둘 다 조건이 **두 벌로 갈라져 있던 자리**이고, DOMAIN.md 6.4가 삭제에 `PUBLISHED`를 요구한 이유가 바로 그것이었다 |
| 2026-08-04 | PR #91 Codex 리뷰 P2 2건을 **받아들이지 않기로** 결정하고 R14에 근거를 적었다. 지적 자체는 맞다 — 댓글 작성·삭제의 `PUBLISHED` 확인과 뒤따르는 쓰기 사이에 경합 창이 있다. 안 막는 이유는 셋이다. (1) **이건 불변식이 아니라 권한 판단이다.** 4.5가 "게시글을 지워도 자식 행은 그대로 둔다"이므로 "비노출 글에는 댓글이 없다"는 불변식이 애초에 없다. (2) **두 경우 다 손해 없는 쪽으로 틀린다** — 작성은 안 보이는 댓글 한 건, 삭제는 사용자가 자기 댓글을 뜻대로 지운 것이다. (3) **막는 값이 싸지 않다** — 댓글 쓰기마다 게시글 행에 잠금이 걸리는데 조각 6이 같은 행을 뜨겁게 만들었고, 우리는 바로 그 순서로 교착을 한 번 맞았다. 되돌아올 계기를 R8·R12와 같은 형식으로 적어 두었다. 리뷰를 보다 DOMAIN.md 6.4의 `더 보기` 조회수 항목이 조각 6 이후에도 미래형으로 남아 있는 것을 함께 발견해 고쳤다 |
| 2026-08-04 | **좋아요 취소를 `DELETE`가 아니라 `POST /community/{id}/likes/delete`로 정했다.** DOMAIN.md 6.5가 `DELETE`로 적고 있었는데 **HTML 폼은 DELETE를 보낼 수 없다.** 보내려면 `spring.mvc.hiddenmethod.filter.enabled`를 켜야 하는데 커뮤니티 밖 전역 설정이라 5개 도메인에 영향이 가고, 이 프로젝트에는 `@DeleteMapping`이 한 곳도 없다(게시글·댓글 삭제도 `POST .../delete`다). **6.5가 지키려던 것은 메서드가 아니라 "토글 금지 + 양쪽 멱등"이고 그건 그대로다.** 6.5의 해당 줄을 근거와 함께 고쳤다 |
| 2026-08-04 | **보류 항목이던 "내가 좋아요 눌렀는지" 표시를 상세에만 넣기로 결정**(DOMAIN.md 9 → 6.5). 상세에는 필요하다 — 버튼이 `좋아요`인지 `좋아요 취소`인지를 갈라야 한다. 목록에는 넣지 않는다: 20행마다 붙는 값이라 목록 SQL에 `memberId`를 넘기고 H1a를 함께 넓혀야 하는데, 얻는 것은 표시 하나뿐이고 비로그인 분기까지 생긴다. **비로그인에게는 묻지도 않는다** — 버튼이 없으므로 물어볼 것이 없고, 물으면 공개 화면인 상세마다 쿼리가 하나 는다 |
| 2026-08-04 | **좋아요에서 교착을 미리 막았다. 조각 6과 같은 자리인데 같은 해법을 쓸 수 없었다.** `post_likes` INSERT가 FK 확인으로 `posts` 행에 공유 잠금을 걸고 재계산이 배타 잠금을 기다리는, H13과 똑같은 모양이다. 조회수는 순서를 뒤집어 풀었지만 **재계산은 INSERT 이후여야 새 행을 세므로** 뒤집을 데가 없다. 그래서 `SELECT ... FOR UPDATE`로 배타 잠금을 앞에서 잡았다. 같은 종류의 함정이 두 번째다 — `posts` 행에 쓰는 경로가 늘 때마다 잠금 순서를 따져야 한다 |
| 2026-08-04 | 잠금 조회를 `findPostById`로 재사용하지 않고 `lockPost`·`PostLockView`를 따로 뒀다. **`FOR UPDATE`가 조인한 테이블의 행까지 잠그기 때문**이다. 상세 조회는 `post_categories`·`members`를 조인하므로 그대로 썼다면 좋아요 한 번에 **카테고리 행이 잠겨 같은 분류의 모든 글이 서로 줄을 선다.** 교착처럼 터지지 않고 조용히 느려지기만 해서 더 찾기 어렵다 |
| 2026-08-04 | 좋아요 추가를 `INSERT ... ON DUPLICATE KEY UPDATE`로 받고 **`INSERT IGNORE`는 쓰지 않기로** 했다. IGNORE 는 UNIQUE 위반만이 아니라 **FK 위반과 값 잘림까지** 경고로 낮춰서, 없는 게시글·없는 회원으로 들어온 요청이 조용히 성공한다. 화면에는 좋아요가 눌린 것처럼 보이고 숫자만 안 오른다. 갱신 행 수는 어느 쪽이든 보지 않는다 — `CLIENT_FOUND_ROWS`(6.2에서 겪었다) 때문에 믿을 수 없고, 뒤따르는 재계산이 실제 행 수를 다시 세므로 볼 이유도 없다 |
| 2026-08-04 | 좋아요 요청도 **`?comments=`를 실어 보내기로** 했다. 좋아요는 댓글 구역 위에 있어서, 댓글을 펼쳐 놓고 좋아요를 눌렀다가 20건으로 접혀 돌아오면 읽던 자리를 잃는다. 댓글 삭제에서 내린 판단(PR #93)이 같은 이유로 여기에도 적용된다 |
| 2026-08-02 | 조회수 규칙의 빈칸을 DOMAIN.md 6.2에 채움. (1) 노출되지 않는 글은 조회수를 올리지 않는다 — UPDATE의 `status` 조건이 담당한다. (2) 조회수 UPDATE는 `updated_at`을 명시적으로 보존한다 — `ON UPDATE CURRENT_TIMESTAMP` 때문에 조회만으로 "수정됨"이 켜지는 것을 구현 중 발견했고, H1c로 고정했다 |
| 2026-08-04 | **보류 항목이던 `post_reports.status` 전이를 정했다**(DOMAIN.md 9 → 6.6). `PENDING → RESOLVED`(차단)·`PENDING → REJECTED`(기각)이고 둘 다 종착이다. **상태를 바꾸는 단위는 신고 한 건이 아니라 게시글이다** — 조치의 단위가 게시글이라, 같은 글에 대한 신고 다섯 건 중 셋만 처리된 상태는 관리자에게 아무 뜻도 없다. 차단을 해제해도 `RESOLVED`는 되돌리지 않는다. 4.2가 `blocked_*`를 보존하는 것과 같은 이유로, 상태는 "그때 조치했다"는 기록이지 현재 노출 여부가 아니다 — 되돌리면 처리한 신고가 목록에 다시 나타나고 같은 글을 몇 번 조치했는지 알 수 없게 된다. `기각`을 둔 이유는 **"차단하지 않기로 했다"도 조치**이기 때문이다. 닫을 길이 없으면 문제없는 글이 신고 목록 맨 위에 영원히 남아 진짜 처리할 글을 가린다 |
| 2026-08-04 | **보류 항목이던 관리자 목록의 필터·정렬을 정했다**(DOMAIN.md 9 → 6.7). 상태 필터(전체/`PUBLISHED`/`BLOCKED`/`DELETED`)와 정렬(최신순/미처리 신고 많은 순) 둘뿐이고, **목업에 있던 작성자·제목 검색은 넣지 않았다.** 관리자가 하는 일은 "신고된 글을 찾아 조치하는 것"이고 그 동선은 정렬이 해결한다. 검색은 LIKE 4종과 인덱스 판단을 함께 불러와 조각 5가 신고·차단보다 커진다 — SCREENS.md가 고객 검색을 범위 밖으로 둔 근거(인덱스를 못 탄다)가 관리자 화면에도 그대로 적용된다. 정렬 허용값은 `AdminPostSort`로 좁히고 SQL은 `<choose>`로 갈랐다. 모르는 값은 오류가 아니라 기본값이다(고객 목록과 같은 처리) |
| 2026-08-04 | **관리자 목업의 규칙 밖 기능을 되살리지 않고 걷어냈다**(`screens/admin-detail.md`의 표). `게시글 영구 삭제`·댓글별 `삭제`·`IP`·`첨부 이미지`·검색·`레시피` 분류가 대상이다. 규칙을 고쳐 권한을 새로 만드는 선택지도 있었지만, `BLOCKED → DELETED` 금지는 **차단된 글이 신고·조치의 증거**라는 근거 위에 서 있고(4.2) 그것을 뒤집으면 H0a·전이 규칙·조각 0의 migration까지 함께 흔들린다. 얻는 것은 관리자 편의 하나이고 잃는 것은 조치 이력이라 화면을 규칙에 맞췄다. 상태 어휘도 `정상`/`제재`에서 `노출 중`/`차단됨`/`삭제됨`으로 통일했다 — 화면·문서·코드가 다른 말을 쓰면 같은 상태를 가리키고 있는지조차 확인할 수 없다 |
| 2026-08-04 | 중복 신고를 **확인과 UNIQUE 제약 둘로** 막기로 했다. 확인만 두면 같은 사람이 두 번 눌렀을 때 둘 다 "없다"를 읽고 하나가 500으로 죽는데, 사용자에게는 신고가 접수됐는지조차 알 수 없는 화면이 된다. 제약만 두면 정상 흐름의 흔한 실수까지 예외 처리에 기대게 된다. `DuplicateKeyException`을 `ALREADY_REPORTED`로 바꿔 둘을 같은 응답으로 모은다. 좋아요가 `ON DUPLICATE KEY UPDATE`로 중복을 삼키는 것과 정반대이고, 그 대비를 XML 형태 검사가 고정한다(H16) |
| 2026-08-04 | 신고 폼을 **접힌 `<details>`로 두되 검증 실패 시 스스로 펼치게** 했다. 취소가 없는 조치라 실수로 눌리지 않게 접어 두는 편이 맞지만, 접힌 채로 돌아오면 오류 문구가 화면 밖에 남아 사용자에게는 아무 일도 일어나지 않은 것으로 보인다. 이미 신고한 사람에게는 폼 대신 안내를 보여 준다 — 폼을 남겨 두면 눌렀을 때 409 화면으로 튀는데, 그건 사용자가 잘못한 것이 아니라 이미 접수된 것이다 |
| 2026-08-04 | 관리자 조치를 `CommunityService`가 아니라 **새 `CommunityAdminService`에 뒀다.** 두 경로의 기본 규칙이 정반대라서다 — 고객 경로는 "노출 중인 글만"이 기본이고 그 판단이 거의 모든 메서드에 붙어 있는데, 관리자 경로는 모든 상태를 보는 것이 기본이다(4.3). 한 클래스에 섞으면 노출 판단을 빠뜨린 메서드가 고객 경로에서 호출되는 날이 오고, 그때 새는 것은 차단된 글의 본문이다. 댓글 조회만 `CommunityService.getComments`를 그대로 쓴다 — 자리 표시·개수 규칙(4.4)이 두 벌이 되면 한쪽만 고치는 날이 온다 |
| 2026-08-04 | 브라우저 확인 중 **시드를 두 번째 실행하면 통째로 실패하는 기존 버그**를 발견해 고쳤다. 조각 6이 만든 `post_views`가 `posts`를 FK로 참조하는데 `seed-local.sql`의 삭제 목록에 없어서, `DELETE FROM posts`가 제약에 걸린다. **처음 실행에서는 `post_views`가 비어 있어 드러나지 않고**, 시드를 다시 돌리는 순간부터 매번 실패한다. 조각 6에서 공용 시드를 건드리지 않는 쪽을 택했지만(2026-08-02의 판단) 이 줄만은 그 파일에 있어야 한다 — 지우는 순서를 정하는 곳이 거기다. 커뮤니티 신고 시드도 함께 고쳤다: 신고자를 회원 하나로 고정했더니 시드 글이 대부분 같은 회원 작성이라 "자기 글은 신고할 수 없다"(6.6)에 전부 걸려 한 건도 안 들어갔다. 글마다 작성자가 아닌 쪽을 고르게 했다 |
| 2026-08-04 | PR #96 Codex 리뷰 P2 2건 처리. (1) **삭제된 글의 신고 기각을 막았다** — 지적이 맞다. `rejectReports`가 게시글의 존재만 확인해서, 노출 중일 때 접수된 신고가 작성자 삭제 후에도 `PENDING`으로 남는데(4.5) 그것을 `REJECTED`로 닫을 수 있었다. **화면에도 그대로 드러났다**: `작성자가 삭제한 게시글이라 조치할 수 없습니다.` 안내와 `신고 기각` 버튼이 나란히 보였다. 안내는 `post.deleted`, 버튼은 미처리 신고 수만 보고 있어서 **조건이 두 벌로 갈라진 자리**였고, 조각 3에서 차단된 글의 댓글 삭제 버튼으로 이미 겪은 유형이다(PR #93). 다만 리뷰가 덧붙인 "삭제 시 미처리 신고를 어떻게 종결할지"는 받아들이지 않았다 — 자동으로 닫으면 작성자의 삭제가 신고를 정리해 주는 셈이라 6.6의 성격과 어긋난다. 남는 대가는 R19다. (2) **신고 내역 상한은 넣지 않고 위험으로 남겼다**(R18). 지적 자체는 맞지만 신고는 `UNIQUE(post_id, reporter_id)`라 회원 수가 곧 구조적 상한이고, 한 글에 수백 건이 쌓이려면 실제 회원 수백 명이 같은 글을 신고해야 한다. 트리거를 R10과 같은 형식으로 적어 두었다 |
| 2026-08-04 | `CommunityMapper`를 **고객/관리자 둘로 갈랐다**(`CommunityMapper` + 새 `CommunityAdminMapper`, XML도 각각). 한 파일에 인터페이스 300줄·XML 718줄이 쌓여 있었고, 무엇보다 `publishedPostConditions`(노출 중만)와 `adminPostConditions`(거르지 않음)라는 **정반대의 조각 둘이 같은 파일에** 있어서 새 쿼리가 어느 쪽을 include해야 하는지가 흐렸다. 경계는 소비자와 맞췄다 — `CommunityService`/`CommunityAdminService`가 이미 같은 이유로 갈려 있다(위 2026-08-04 항목). **기능 동사(CRUD·조회수·좋아요·신고)로 자르는 안은 버렸다**: 그 문장들은 전부 같은 `posts` 행을 만지고, 잠금 순서를 한자리에서 따질 수 없게 되면 그 대가가 교착이다(H13·H15·H17). 같은 이유로 **`lockPost`는 복제하지 않고 고객 매퍼에 하나만 두고**, `CommunityAdminService`가 두 매퍼를 함께 주입받는다. 하네스는 셋을 손봤다 — `CommunityMapperXmlTests`는 두 XML을 **한 Configuration에** 파싱해 검사를 갈라 두지 않고(조회수·좋아요·차단의 `updated_at` 보존처럼 두 파일에 걸친 규칙이 대부분이다) 인터페이스↔XML 대조만 매퍼마다 돌린다, `CommunityAdminServiceTests`는 `inOrder`에 **두 목을 함께** 넣는다(한쪽만 넣으면 잠그기 전에 쓰는 구현이 순서 판단에서 빠져 통과한다), `CommunityCommentScopeTests`는 파일 이름 대신 **매퍼 디렉터리**를 훑는다(이름을 박아 두면 새 파일만 검사에서 조용히 빠진다). 조각 표에는 올리지 않는다 — 기능이 아니라 리팩터링이고 SQL은 한 글자도 바뀌지 않았다 |
| 2026-08-04 | **조회수 중복 방지의 시간 창을 날짜 칸에서 굴러가는 10분으로 바꿨다.** 바꾼 이유는 방어가 아니라 **표시**다 — 하루 단위로만 움직이는 숫자는 글을 다시 연 사람에게 멈춘 값으로 보인다. 상한만 보면 느슨해지는 변경이고(뷰어 하나가 하루에 1 → 144), 그것을 알고 받아들였다. 실질 차이가 작은 근거는 R12가 이미 적어 둔 것이다: 비로그인 키가 세션 id라 **작정한 조작에는 어느 창도 무력하고**, 두 설계가 실제로 막는 것은 사람의 반복 조회인데 그건 10분으로도 막힌다. **원래 날짜 칸을 고른 논거가 틀렸다는 것이 이 작업에서 배운 것이다** — "굴러가는 창은 `UNIQUE`로 표현할 수 없어 결국 코드가 판단하게 된다"고 적었는데, 정작 직렬화를 하고 있던 것은 제약이 아니라 조건부 `UPDATE`가 게시글 행에 먼저 거는 배타 잠금이었다(H13). 창 판단은 그 잠금을 쥔 채 `UPDATE` 안에서 평가되므로 코드로 새어 나가지 않는다. **대신 제약이 하던 경보를 잃었다**: 잠금 순서가 깨져도 이제 DB가 아무 말도 하지 않는다. 그 자리는 `CommunityViewCountConcurrencyTests`가 맡고(H14), 창이 열린 직후의 동시 요청을 보는 테스트를 하나 더 붙였다 — 앞의 두 테스트는 창을 한 번도 넘지 않아 창 조건을 통째로 지워도 통과한다. 창 폭은 양쪽 경계를 다 고정한다(H19): 한쪽만 보면 창이 1분이든 하루든 통과한다. 조회 시각은 컬럼을 새로 만들지 않고 `created_at`을 쓴다 — `post_views`는 append-only라 행이 생긴 시각이 곧 조회한 시각이고, 같은 값을 담는 컬럼이 둘이면 언젠가 어긋난다. migration에서 **`UNIQUE`를 명시적으로 먼저 지우는 것이 규칙이다**: 컬럼만 지우면 MariaDB가 `UNIQUE (post_id, viewer_key)`를 남겨 한 사람이 한 글을 영영 한 번만 볼 수 있게 되고, 두 번째 조회부터 상세가 500이 된다. 시드도 `created_at`을 과거로 박았다 — 기본값에 맡기면 시드 직후 10분 동안 `S:seed-*` 조회가 창에 걸린다 |
| 2026-08-04 | PR #98 Codex 리뷰 3건 전부 처리. (1) **댓글 `더 보기`를 조회수 경로에서 뺐다(P2, 셋 중 제일 무겁다)** — 지적이 맞다. `?comments=`가 붙은 요청도 조회수를 올리는 `getPostDetail`로 가고 있어서, 상세를 **10분 넘게 읽다가** 누르면 창이 이미 닫혀 그대로 +1이었다. 날짜 칸일 때는 하루 한 번으로 눌려 있어 드러나지 않던 것이 창을 좁히면서 되살아났다. **문제는 구멍보다 문서와 하네스가 그걸 아니라고 말하고 있었다는 것이다** — H12가 "댓글 `더 보기` 전부"라고 적고 있었는데, 그 행의 테스트들은 창을 한 번도 넘지 않아 이 경계를 통째로 놓쳤다. 조각 1에서 배운 그 성질이 또 나왔다: 빈 곳은 실패가 아니라 통과의 모습으로 나타난다. 조각 3이 갈라 둔 `getVisiblePost`를 그대로 쓰고, H12에서 이 자리를 떼어 H20으로 따로 세웠다 — 창이 지키는 것과 경로가 지키는 것이 섞여 있으면 어느 쪽이 무는지 알 수 없다. (2) **세 `ALTER`를 한 문장으로 묶었다(P2)** — MariaDB DDL은 트랜잭션이 아니라 Flyway가 통째로 롤백해 주지 않는다. 첫 문장이 커밋된 뒤 둘째가 잠금 시간 초과로 실패하면 인덱스만 남고 버전은 기록되지 않아, 재시도가 매번 `Duplicate key name`으로 죽는다 — 손으로 지우기 전에는 복구되지 않는다. **migration 주석이 "통째로 실패한다"고 적고 있던 것이 틀린 자리였다.** (3) **expand-contract로 나눴다(P1)** — 지적의 전제(공용 RDS 무중단 롤링)는 지금 성립하지 않지만(R21), 나누는 값이 컸다. `viewed_on`을 지우지 않고 `NULL` 허용으로만 바꾸면 구버전(날짜를 넣는다)과 신버전(넣지 않는다)이 **같은 스키마에서 함께 돈다.** 컬럼 삭제는 R20에 계기와 함께 남겼다. 스키마 검사 3건을 새로 넣었고, 그중 "`viewed_on` 없이 INSERT된다"는 **컬럼을 지운 뒤에도 그대로 참**이라 다음 단계에서 고칠 테스트가 없다 |
| 2026-08-04 | **조각 7의 인기글을 요청 시점 집계로 정했다가 같은 날 배치로 뒤집었다.** 처음 판단은 "배치를 두지 않는다"였고 근거는 셋이었다 — 배치 실패가 곧 그날 인기글 없음이 되고, 배치의 하루 단위 시계가 조회수의 10분 창과 두 벌이 되며, 지금 데이터 규모에서 인덱스 탄 7일 집계는 아낄 값이 아니다. **뒤집은 이유는 기술적 우열이 아니라 목적이 바뀐 것이다**: 스케줄러·배치를 실제로 만들어 보는 것이 이 조각의 학습 목표가 됐다. 그 셋은 사라지지 않고 R23·R24로 옮겼다 — 기각 사유를 위험으로 바꿔 적는 것이 뒤집기의 대가를 남기는 방법이다(2026-08-02의 인기글 범위 뒤집기와 같은 형식). **점수식(D1)은 뒤집지 않았다.** 원안의 `조회수 + 좋아요*5 + 댓글*3`은 배치와 무관하게 틀렸다 — 가장 못 믿을 신호(R12)에 가장 큰 볼륨을 준다 |
| 2026-08-04 | **일일 카운터 테이블(`post_daily_metrics`)을 두지 않기로 했다.** 원안은 조회·좋아요·댓글마다 `(metric_date, post_id)` 행을 실시간으로 올리고 배치가 그것을 읽는 구조였다. 안 두는 이유 셋 중 **첫째가 결정적이다 — 요청 경로에 잠금이 한 벌 더 붙는다.** 조회수·좋아요는 이미 `posts` 행 잠금 순서로 교착을 세 번 맞은 경로이고(H13·H15·H17), 같은 트랜잭션에 다른 테이블의 행 잠금이 들어오면 순서를 다시 처음부터 따져야 한다. 원안 12절은 이것을 Hot Row(성능)로만 다뤘는데 **실제 위험은 성능이 아니라 교착이다.** 둘째, 원본을 세면 좋아요 취소(`DELETE FROM post_likes`)와 댓글 삭제가 저절로 맞는다 — 증가만 하는 카운터였다면 취소–재좋아요 반복으로 점수를 무한히 올릴 수 있었고, 원안 4.3이 "삭제된 댓글은 배치 재계산 시 제외"라고 적었지만 증가 카운터는 재계산할 근거를 갖고 있지 않다(문서가 스스로 모순된 자리다). 셋째, 카운터는 **하룻밤에 한 번 도는 집계**를 위해 요청 수만큼의 쓰기를 미리 하는 거래다. 학습 목표는 깎이지 않는다 — 스케줄러·크론·멱등성·스냅샷은 그대로이고 카운터는 배치가 아니라 실시간 집계 쪽 주제다. 이 판단을 H26이 문다 |
| 2026-08-04 | **`@Scheduled`가 다중 인스턴스에서 조율되지 않는다는 것을 R22에 명시했다.** 크론은 JVM 하나 안의 타이머라 다른 인스턴스의 존재를 모른다 — "서버단에서 알아서 한 번만 돈다"고 읽히기 쉬운 자리라 적어 둔다. 다만 **우리 설계에서 데이터가 깨지지는 않는다**: `DELETE by date` → `INSERT`가 행 잠금에서 직렬화되고, 같은 입력이라 결과가 같으며, `PRIMARY KEY(ranking_date, ranking)`가 겹쳐 쓰기를 막는다. 그래서 남는 대가는 낭비·로그의 중복키 예외, 그리고 **맞는 이유가 설계가 아니라 제약과 잠금의 부수효과**라는 것이다 — 멱등성(D4)을 안 넣었다면 그대로 깨졌다. 1차는 단일 서버 전제로 수용하고 ShedLock·전용 인스턴스·앱 밖 스케줄을 계기와 함께 R22에 적었다 |
| 2026-08-04 | **DOMAIN.md 9의 보류 항목 넷을 조각 7 계획에서 한꺼번에 닫았다.** 기간은 최근 7일(D1), 위치는 목록 화면 상단(D7), `post_views` 보관 기간은 정리하지 않고 R25로(D9), `sort=likes`는 넣지 않는다(D8). 보관 기간이 **이 조각에서 처음 닫을 수 있게 된 이유**는 스냅샷이 생겼기 때문이다 — 순위의 역사를 원본 이력이 아니라 `daily_popular_posts`가 갖게 되면서, 나중에 `post_views`를 정리할 근거가 처음 생겼다. 그래도 1차에서는 안 지운다: 지우는 순간 `view_count == COUNT(post_views)`(H14)가 깨지는데 그 불변식이 조각 6의 유일한 방어선이다. **불변식을 말없이 어기는 것과 범위를 줄여 다시 적는 것은 다르고**, 순서는 후자가 먼저다 |
| 2026-08-04 | 인기글 화면이 **특정 날짜가 아니라 `MAX(ranking_date)`를 읽게** 했다(D6). 원안 8절은 `WHERE ranking_date = :rankingDate`로 날짜를 받는 형태였는데, **화면에 무엇을 넘길지가 비어 있었다.** 오늘 날짜를 넘기면 00:05 배치 전까지 매일 빈 화면이고, 첫 배포 후 첫 배치 전에는 아예 아무것도 없다. 최신 확정일을 고르면 그 둘이 함께 사라지고, **배치를 한 번 거른 밤도 "빈 화면"이 아니라 "어제 목록 유지"로 degrade 된다**(R23의 손해가 겉으로 드러나지 않는 이유이기도 하다). 확정 결과가 정말 하나도 없을 때만 영역을 통째로 감추고, 그 상태를 H27이 문다 — 첫 배포 직후에만 나타나는 화면이라 사람 눈으로는 한 번 지나가면 다시 볼 수 없다 |
| 2026-08-04 | PR #103 Codex 리뷰 P2 4건 전부 처리. 넷 다 **아직 코드가 없는 계획(7b)을 겨눈 지적**이고, 그래서 고치는 값이 특히 컸다 — 구현 전에 계획이 틀려 있으면 그 틀린 계획대로 만들어진다. (1) **점수식에서 댓글을 건수가 아니라 사람 수로 바꿨다(제일 무겁다)** — `comments`에는 `UNIQUE(post_id, member_id)`가 없어서 계정 하나가 댓글 20개로 300점(좋아요 12명분)을 만들 수 있었다. **문제는 구멍보다 이 배점의 근거가 "좋아요는 UNIQUE라 구조적 상한이 있다"였다는 것이다** — 상한이 없는 신호에 두 번째로 큰 계수를 주면서, 조회수를 1로 낮춘 그 논리를 댓글에는 적용하지 않았다. `COUNT(DISTINCT member_id)`로 세면 댓글도 같은 상한을 갖는다. 댓글 작성 자체는 제한하지 않는다 — 한 글에 여러 번 답하는 것은 정상적인 대화이고, 고칠 자리는 쓰기 규칙이 아니라 점수가 무엇을 신뢰하는가다. H30을 세웠고, **정상 데이터로는 두 구현이 구분되지 않으므로**(사람마다 하나씩 다는 흔한 경우에 두 값이 같다) 같은 회원이 여러 번 다는 데이터를 일부러 만든다. (2) **날짜 경계의 시간대를 D10으로 세웠다** — 스케줄러는 서울 기준으로 날짜를 정하는데 `created_at`은 DB 시계로 박히고, `application.yml`에는 세션 시간대 설정이 없다. DB가 UTC면 창이 9시간 어긋난다. 지금까지 안 터진 것은 10분 창이 `NOW(6)` 하나로 끝나기 때문이고, **배치는 시계가 두 벌이 되는 첫 경로다.** 배치 안에서 고치지 않고 JDBC 세션 시간대를 고정하는 쪽으로 정했다 — 문제의 성격이 "이 배치가 틀렸다"가 아니라 "애플리케이션과 DB가 다른 시간대를 쓴다"라서, 배치만 고치면 다음 경로에서 또 나온다. 전역 설정이라 7b에서 이 변경만 따로 확인한다. (3) **H23의 경계를 날짜가 아니라 시각으로 다시 적었다** — 집계 명세는 `targetDate - 6일 00:00` 이상(7칸)인데 H23은 "7일 전은 들어간다"라고 적어 8칸을 요구하고 있었다. **하네스가 명세와 다른 것을 요구하는 상태**라, 그대로 두면 올바른 SQL이 테스트에 떨어지거나 테스트에 맞춘 구현이 하루치를 더 센다. 날짜로 쓰면 대상일 포함 여부가 읽는 사람마다 갈리므로 앞으로 경계는 시각으로 적는다. (4) **"보류 넷이 이 조각에서 전부 닫힌다"는 선언을 걷어냈다** — 실제로 닫힌 것은 `sort=likes` 하나이고 `DOMAIN.md`에 6.9는 존재하지도 않는데, 계획이 이미 옮겨진 것처럼 적혀 있었다. 7b를 이어받는 사람이 정확히 여기서 어느 문서를 믿을지 헷갈린다. 항목별 상태 표로 바꾸고 **`DOMAIN.md` 9절의 세 행에서도 PLAN.md를 되짚도록** 양방향으로 연결했다. 6.9 신설을 구현 시점으로 미루는 판단 자체는 유지한다 — 구현 전에 정본으로 옮기면 고쳐질 값을 정본에 적게 된다 |
| 2026-08-04 | PR #103 Codex 리뷰 **2라운드** P2 4건 전부 처리. 1라운드와 마찬가지로 넷 다 계획(7b)을 겨눈 지적이다. (1) **선정 SQL에도 상태 필터를 넣었다(D5 개정)** — 화면에서만 `PUBLISHED`를 보고 있었는데, 4.5가 "게시글을 지워도 자식 행은 그대로 둔다"라서 **지워진 글도 창 안의 조회·좋아요·댓글을 그대로 갖고 있다.** 삭제 직전에 인기였던 글일수록 상위를 차지하므로, 비노출 글이 11건을 넘으면 화면이 10건보다 적게 나오거나 통째로 빈다. **더 중요한 것은 20−10 여유분의 뜻을 헷갈리고 있었다는 점이다** — 그 여유는 비노출 글의 몫이 아니라 선정 이후의 상태 변화를 흡수하는 몫이고, 둘을 섞으면 여유분을 아무리 늘려도 모자란다. H25를 양쪽 필터를 다 보도록 넓혔다. 조건이 두 벌로 갈라진 자리이지만 **여기서는 두 벌인 것이 맞다** — 막는 것이 서로 다르다. (2) **7b migration에 `IF NOT EXISTS`를 규칙으로 세웠다** — 서로 다른 네 테이블을 건드려 한 문장으로 묶을 수가 없는데 MariaDB DDL은 트랜잭션이 아니라, 세 번째 `ALTER`가 실패하면 앞의 둘만 남고 버전은 기록되지 않아 재시도가 매번 `Table already exists`로 죽는다. **파일을 넷으로 쪼개는 쪽은 쓰지 않았다** — 이 넷은 함께 있어야 배치가 도는 한 벌이라, 나누면 인덱스 없이 테이블만 있는 중간 버전이 정상 상태로 기록되고 그때 배치가 돌면 세 테이블을 통째로 스캔한다. V20260804_102934가 세 `ALTER`를 한 문장으로 묶어 푼 것과 목적은 같고 수단만 다르다. (3) **H31을 세웠다 — 재집계 실패 시 이전 스냅샷이 보존되는지.** D4의 `DELETE`→`INSERT`는 **이미 성공했던 날짜를 다시 돌리다 죽으면 있던 것을 잃는 구조**다(새로 못 만드는 것과 다르다). H22는 성공 실행 둘을 비교할 뿐이라 `@Transactional`이 빠져도 통과한다. **그리고 이 자리를 D6 폴백이 가린다** — 화면은 전날 목록으로 멀쩡히 보여서 아무도 눈치채지 못한다. 그래서 D6에 **폴백은 의도된 설계임을 명시하고 읽은 날짜가 어제가 아니면 경고 로그를 남긴다**를 함께 붙였다. 폴백을 약하게 만드는 대신 **사용자에게는 매끄럽게, 운영자에게는 투명하게**로 나눈 것이고, 그래도 남는 한계(로그를 보는 사람이 있어야 관리된다)는 R23에 적었다. (4) **조회수순 오프셋 페이징의 보증 수준을 R28로 적었다** — `id` tiebreaker가 보증하는 것은 한 쿼리 안의 순서까지이고, 쪽 사이 안정성은 **정렬 키가 불변일 때만** 따라온다. `view_count`는 상세를 열 때마다 바뀌므로 쪽을 넘기는 사이 `OFFSET`이 밀려 중복·누락이 난다(최신순에는 없다). 1차에서는 수용한다 — 커서 페이징은 그 커서가 변하는 값이고 순위 스냅샷은 배치를 하나 더 세우는 일인데, 조회수순은 기본 정렬이 아니다. **위험한 것은 구멍보다 H28을 통과했다는 안심이다** — H28은 한 쿼리의 순서만 보므로 이 자리는 하네스로 절대 드러나지 않는다 |
| 2026-08-04 | PR #103 Codex 리뷰 **3라운드** P2 4건 처리. **셋이 같은 빈자리에서 나왔다** — 저장하는 것이 순위(결과)뿐이고 **실행했다는 사실**이 아니었다. `daily_popular_posts`만으로는 "안 돈 날"과 "돌았는데 0건인 날"이 **둘 다 행 없음**이라 구분되지 않고, 거기서 (1) D6의 폴백이 옛 날짜에 머물러 7일 창 밖의 글이 무기한 노출되고 (2) "이미 만든 날짜인가" 판단이 성립하지 않으며 (3) 실패 경고가 정상 상태에서 운다. 그래서 **실행 기록 테이블 `popular_post_batch_runs`를 D11로 세웠다** — 0건인 날도 행이 남는다. 순위 행에 sentinel을 섞는 쪽은 `PRIMARY KEY(ranking_date, ranking)`·FK·화면 쿼리를 전부 오염시켜 쓰지 않았고, 테이블 하나가 느는 대가는 받는다. D6는 이제 실행 기록의 최신 날짜를 읽고, H32가 이 자리를 문다. **D4도 함께 뒤집었다 — 성공한 날짜는 재집계하지 않고 건너뛴다.** 원본이 변하면 입력이 같지 않으므로 재실행이 확정된 순위를 조용히 바꾸는데, 그러면 "원본이 변해도 그날 기록은 남는다"는 스냅샷의 목적이 무너진다. 처음에는 "정책만 문서에 적자"로 판단했다가 바꿨다 — 막아도 잃는 것이 없기 때문이다. **H31이 "실패하면 아예 손대지 않음"을 보증하므로 기록이 있다는 것이 곧 성공했다는 뜻**이고, 그래서 실패한 날 재실행은 그대로 되며 R22의 다중 인스턴스 낭비까지 함께 준다. H33을 세웠고 **H22로는 절대 안 잡힌다** — 그쪽은 원본을 건드리지 않고 두 번 부르므로 덮어쓰는 구현도 같은 결과를 낸다. 어제 붙인 D6 경고에는 **00:05 유예**를 넣었다: 배치가 00:05에 도니 00:00~00:04에는 정상 상태에서도 최신 확정일이 그제이고, 유예가 없으면 매일 새벽 5분 동안 목록 요청마다 경고가 찍혀 **울지 않아야 할 때 우는 경고**가 된다 — 그러면 아무도 안 보게 되어 로그를 붙인 목적 자체가 무너진다. 마지막으로 **시드 두 곳의 삭제 순서**를 7b 작업에 넣었다(H6 확장). `post_views` 때 똑같이 겪은 자리인데(`e275bc7`) 계획에 빠져 있었다. **테이블을 만드는 커밋에서 함께 한다** — 지금 넣으면 없는 테이블을 지우게 되어 멀쩡한 시드가 깨진다. 실행 기록도 함께 지운다: FK는 없지만 남으면 시드로 글을 새로 깔아도 배치가 "이미 돌았다"고 건너뛴다 |
