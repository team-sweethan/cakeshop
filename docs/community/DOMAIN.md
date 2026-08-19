# Community 도메인 규칙 (1차 MVP)

> 이 문서는 Community 도메인의 **결정된 규칙의 정본**이다.
> 진행 상태와 조각 순서는 `docs/community/PLAN.md`를 본다.
> 프로젝트 전체 규칙은 `AGENTS.md`가 상위 정본이며, 충돌 시 `AGENTS.md`가 우선한다.

| 찾는 것 | 문서 |
|---|---|
| 기능 단위 명세 (입력·처리·출력·검증) | `specs/` — 아래 `spec` 열 |
| 상태 모델·권한·입력 검증·탈퇴 회원·카테고리 | 이 문서 4·5·7·8·10절 |
| 아직 안 정해진 것 | 이 문서 9절, 그리고 해당 spec의 2차 절 |
| 조각 순서, 진행 상태, 하네스 인덱스, 위험, 결정 로그 | `PLAN.md` |
| 기능이 어떻게 생겼는지 (비개발자용) | `README.md` — 규칙의 근거로 쓰지 않는다 |
| 도메인 작업 규칙 (구현 시작 전) | `src/main/java/com/cakeshop/domain/community/CLAUDE.md` |

## 0. 읽는 법

기능 ID는 액터와 성격으로 나눈다. **ID는 전역 고유하므로 파일이 바뀌어도 ID는 그대로다.**

| 접두 | 묶음 |
|---|---|
| `A` | 고객 — 쓰기 |
| `B` | 누구나 — 읽기 |
| `C` | 관리자 |
| `D` | 다른 도메인과의 연동 |
| `E` | 기반 (화면 없음) |

`현재` 열은 **지금 코드에 있는 것**이다. `없음`은 코드·화면 모두 없다는 뜻이고, `완료`는 머지되어 동작한다는 뜻이다.

## 0.1 기능 목록

`spec` 열이 그 기능의 명세가 어느 파일에 있는지 가리킨다.

| ID | 기능 | 액터 | 경로 | 현재 | 조각 | spec |
|---|---|---|---|---|---|---|
| **A1** | 게시글 작성 | 고객 | `GET /community/new` · `POST /community` | **완료** | 2 | `community-post.md` |
| **A2** | 게시글 수정 | 작성자 | `GET·POST /community/{id}/edit` | **완료** | 2 | `community-post.md` |
| **A3** | 게시글 삭제 | 작성자 | `POST /community/{id}/delete` | **완료** | 2 | `community-post.md` |
| **A4** | 이미지 첨부 | 고객 | (A1·A2에 포함) | **완료** | 12 | `community-post.md` |
| **A5** | 댓글 작성 | 고객 | `POST /community/{postId}/comments` | **완료** | 3 | `community-comment.md` |
| **A6** | 댓글 삭제 | 댓글 작성자 | `POST /community/{postId}/comments/{commentId}/delete` | **완료** | 3 | `community-comment.md` |
| **A7** | 답글 (2단계) | 고객 | (A5 경로의 `replyTo`) | **완료** | 8 | `community-comment.md` |
| **A8** | 좋아요 추가·취소 | 고객 | `POST /community/{id}/likes` · `/likes/delete` | **완료** | 4 | `community-reaction.md` |
| **A9** | 신고 | 고객 | `POST /community/{id}/reports` | **완료** | 5 | `community-reaction.md` |
| **B1** | 게시글 목록 | 누구나 | `GET /community` | **완료** | 1 · 7a · 16 | `community-read.md` |
| **B2** | 게시글 상세 | 누구나 | `GET /community/{id}` | **완료** | 1 | `community-read.md` |
| **B3** | 조회수와 중복 방지 | — | (B2에 포함) | **완료** | 6 | `community-read.md` |
| **B4** | 댓글 정렬·분량 (`더 보기`) | 누구나 | `GET /community/{id}?comments=N` | **완료** | 3 · 6 | `community-comment.md` |
| **B5** | 인기글 영역 | 누구나 | (B1에 포함 — 메인은 15가 제거) | **완료** | 7c · 13 · 15 | `community-popular.md` |
| **B6** | 검색 | 누구나 | (B1의 파라미터) | **없음** | 2차 | `community-read.md` |
| **B7** | 무한 스크롤 | 누구나 | (댓글 구역 부분 로드로 재정의 — 게시글 목록은 쪽 번호 유지) | **없음** | 2차 (조각 9) | `community-read.md` |
| **B8** | 메인 공지 상단 영역·롤링 | 누구나 | (메인 `GET /`에 포함) | **완료** | 14c · 18 · 19 | `community-notice.md` |
| **B9** | 공지 전체보기·GNB 진입점 | 누구나 | `GET /community/notices` | **완료** | 14b · 18 | `community-notice.md` |
| **B10** | 공지 상세 | 누구나 | `GET /community/notices/{id}` | **완료** | 14b | `community-notice.md` |
| **C1** | 관리자 목록 | 관리자 | `GET /admin/community` | **완료** | 5 | `community-admin.md` |
| **C2** | 관리자 상세 | 관리자 | `GET /admin/community/{id}` | **완료** | 5 | `community-admin.md` |
| **C3** | 차단·해제 | 관리자 | `POST /admin/community/{id}/block` · `/unblock` | **완료** | 5 | `community-admin.md` |
| **C4** | 신고 기각 | 관리자 | `POST /admin/community/{id}/reports/reject` | **완료** | 5 | `community-admin.md` |
| **C5** | 관리자 공지 목록 | 관리자 | `GET /admin/community/notices` | **완료** | 14a | `community-notice.md` |
| **C6** | 공지 작성·수정 | 관리자 | `GET·POST /admin/community/notices/new` · `/{id}/edit` | **완료** | 14a | `community-notice.md` |
| **C7** | 공지 삭제 | 관리자 | `POST /admin/community/notices/{id}/delete` | **완료** | 14a | `community-notice.md` |
| **D1** | 작성자 표시명 | — | (Service 계약) | **완료** | 10 | 이 문서 8절 |
| **D2** | 메인 인기글 계약 | — | (Service 계약) | **제거** — 조각 15가 메인 노출을 뺐다. 계약 클래스는 공지(D3)용으로 남는다 | 13 · 15 | `community-popular.md` |
| **D3** | 메인 공지 계약 | — | (D2와 같은 계약에 추가) | **완료** | 14c | `community-notice.md` |
| **D4** | 댓글·답글 알림 | — | 알림 발송 + `GET /community/{postId}/comments/{commentId}` | **완료** | 17 | `community-comment.md` |
| **E1** | 상태 enum + `CHECK` | — | — | **완료** | 0 | 이 문서 4절 |
| **E2** | 카테고리 주입 | — | — | **완료** | 0 | 이 문서 10절 |
| **E3** | 인기글 집계 배치 | — | (스케줄러) | **완료** | 7b · 20 | `community-popular.md` |
| **E4** | 공지 표 + 상태·기간 `CHECK` | — | — | **완료** | 14a | `community-notice.md` |

## 1. 한 문장 정의

Cakeshop 커뮤니티는 고객이 케이크 관련 질문과 후기를 공유하는 공간이다.

## 2. MVP 범위

**포함**: 게시글 목록·상세, 게시글 CRUD, 댓글 작성·삭제(2단계 답글 포함 — 조각 8), 좋아요, 신고, 관리자 차단, 페이지 번호 페이징, **조회수 정렬 옵션·인기글**, **관리자 공지사항**

### 2차로 미룬 것

1차에서 만들지 않지만 **나중에 만든다.** 요청이 들어오면 여기를 먼저 본다.

| 항목 | 미룬 이유와 방향 |
|---|---|
| 검색 | 2차에서 다룬다. 단순한 제목·본문 `LIKE` 검색은 데이터가 늘면 성능 부담이 될 수 있으므로, 실제 요구와 데이터 규모를 확인한 뒤 구현 방식과 인프라 도입 여부를 별도로 합의한다 |
| 무한 스크롤 | 게시글 목록은 1차에서 쪽 번호 페이징으로 간다. **댓글의 `이전 댓글 더 보기`는 이것이 아니다** — 스크롤이 아니라 사용자가 누를 때만 늘어나고, 주소가 바뀌는 링크라 JS 없이 동작한다 (`specs/community-comment.md` B4) |
| ~~이미지 첨부~~ | **끝났다 (조각 12).** 규칙은 `specs/community-post.md` A4가 정본이다 |
| ~~대댓글~~ | **끝났다 (조각 8).** 2단계 답글로 확정하고 V0의 `parent_comment_id`를 그대로 쓴다. 규칙은 `specs/community-comment.md` A7이 정본이다 |

### 범위 밖

만들지 않는다.

| 항목 | 왜 안 만드나 |
|---|---|
| 카테고리 관리 화면 | 카테고리는 migration으로 주입한다 (10절) |
| 알림·실시간 댓글 | 커뮤니티 범위 밖이다. 알림은 알림 도메인이 소유한다 |
| 댓글 수정 | 등록한 댓글은 수정할 수 없다 (`specs/community-comment.md` A5·A6) |

> **범위 변경 (2026-08-03).** `인기글·정렬 옵션`은 원래 제외였다. 조회수를 정렬·순위에 쓰기로 하면서 포함으로 옮겼다.
>
> 이 변경은 **조회수 규칙(`specs/community-read.md` B3)을 함께 뒤집어야만 성립한다.** 그 규칙이 조회수 중복 방지를 뺀 근거가 바로 "조회수는 정렬·순위에 쓰이지 않는다"였다. 그 전제를 없애 놓고 중복 방지를 그대로 두면 **새로고침만으로 순위가 오른다.** 순서가 있다 — 중복 방지가 먼저고 순위 화면이 나중이다(`PLAN.md` 조각 6 → 7).

## 3. 기존 자산

스키마는 `V0__initial_schema.sql`에 **이미 전부 존재**한다. 공유된 migration이므로 수정하지 않고, 필요하면 새 versioned migration을 추가한다(`gradlew newMigration -Pdesc=<snake_case>`).

- `post_categories`, `posts`, `comments`, `post_likes`, `post_images`, `post_reports`
- `post_likes(post_id, member_id)` UNIQUE 존재
- `post_reports(post_id, reporter_id)` UNIQUE 존재
- `posts`에 `status`, `blocked_at`, `blocked_reason`, `blocked_by`, `view_count`, `like_count` 존재
- `posts`에 `comment_count` 컬럼은 **없다**
- **`post_views` 테이블은 없다.** 조회수 중복 방지(`specs/community-read.md` B3)에 필요하므로 새 migration으로 만든다 — V0에 기댈 수 있는 유일한 예외다

코드 뼈대: `domain/community/`에 controller 3개(뷰 이름만 반환), 빈 `CommunityService`, 빈 `CommunityMapper`, 빈 entity 3개, `CommunityErrorCode`. 테스트 0개.

템플릿: `templates/customer/community/{list,detail,form}.html`, `templates/admin/community/{list,detail}.html`

기존 공용 유틸: `global/common/paging/PageRequest`(1-based, offset, 기본 20, 최대 100), `PageResult`(totalElements, totalPages)

## 4. 상태 모델

### 4.1 노출 기준

**`posts.status`가 게시글 노출 여부의 유일한 기준이다.** `blocked_at`/`blocked_reason`/`blocked_by`는 부가 기록일 뿐 노출 판단에 쓰지 않는다.

- 이유: 조건이 두 개면 새 쿼리를 추가할 때 하나를 빠뜨려 차단된 글이 노출된다. `WHERE status = 'PUBLISHED'` 하나로 끝내면 빠뜨릴 것이 없다.
- `PostStatus { PUBLISHED, DELETED, BLOCKED }` enum으로 정의하고, `MemberStatus.canTransitionTo` 선례를 따라 전이 규칙을 enum에 둔다.
- 새 migration으로 `CHECK (status IN ('PUBLISHED','DELETED','BLOCKED'))`를 추가한다 (선례: `V20260729_123306__add_member_status_constraint.sql`).

> **이 절은 `posts`에만 적용된다.** 공지는 별도 표이고 노출 기간을 갖기 때문에 조건이 셋이다. 그 예외와 예외를 감당하는 방법은 `specs/community-notice.md` E4가 정본이다.

### 4.2 게시글 전이 규칙

| 전이 | 허용 | 주체 |
|---|---|---|
| `PUBLISHED → DELETED` | 허용 | 작성자 |
| `PUBLISHED → BLOCKED` | 허용 | 관리자 |
| `BLOCKED → PUBLISHED` | 허용 | 관리자 (차단 해제) |
| `BLOCKED → DELETED` | **금지** | — |
| `DELETED → PUBLISHED` | **금지** | — |
| `DELETED → BLOCKED` | **금지** | — |

- `BLOCKED → DELETED` 금지: 차단된 글은 신고·조치의 증거다. 작성자가 지워서 없앨 수 없어야 한다. 결과적으로 **차단된 글에 대해 작성자가 할 수 있는 일은 없다**(수정·삭제 모두 불가).
- `DELETED`는 종착 상태다. 복구 기능(휴지통)은 MVP 밖이다.
- 차단 해제 시 `blocked_at`/`blocked_reason`/`blocked_by`를 **NULL로 되돌리지 않는다.** "과거에 차단된 적이 있다"는 관리자에게 유용한 이력이고, 노출은 `status`가 결정하므로 값이 남아도 영향이 없다.

### 4.3 상세 접근 규칙 (`GET /community/{id}`)

| 접근자 | `PUBLISHED` | `DELETED` | `BLOCKED` |
|---|---|---|---|
| 비로그인 / 다른 회원 | 정상 | **404** | **404** |
| 작성자 본인 | 정상 | **404** | **본문 + 차단 사유 표시** |
| 관리자 (고객 경로) | 정상 | 404 | 404 |
| 관리자 (`/admin/community/{id}`) | 정상 | 정상 | 정상 |

- 남에게 403이 아니라 404를 주는 이유: 403은 "그 자리에 글이 존재한다"는 사실을 흘린다.
- 작성자에게 `BLOCKED` 본문과 사유를 보여주는 이유: 4.2에 따라 작성자는 차단된 글에 아무 조치도 할 수 없다. 404까지 주면 글이 왜 사라졌는지 영영 알 수 없다. 차단이 처벌이 아니라 교정으로 작동하려면 사유가 전달되어야 한다.

### 4.4 댓글 상태

- 댓글 삭제도 soft delete (`comments.status = 'DELETED'`).
- **삭제된 댓글은 목록에서 지우지 않고 "삭제된 댓글입니다" 자리 표시로 남긴다.** 1차에는 자식 댓글이 없어 기능적으로 불필요하지만, 2차 대댓글 도입 시 부모 댓글이 사라지면 자식이 고아가 되므로 그때 정책을 뒤집는 비용(테스트·화면 재작업)을 피하기 위해 처음부터 이렇게 간다.
- **삭제된 댓글은 댓글 개수 집계에서 제외한다** (`WHERE status = 'PUBLISHED'`).

### 4.5 게시글 삭제와 자식 데이터

게시글을 soft delete해도 `comments`, `post_likes`, `post_reports`, `post_images` 행은 **그대로 둔다.**

- 게시글이 `DELETED`면 상세가 404이므로 댓글이 노출될 경로가 없다. 자식까지 일괄 `DELETED`로 바꾸는 것은 중복이고, "원래 삭제돼 있던 댓글"과 "게시글 때문에 삭제된 댓글"을 구분할 수 없게 만든다.
- **대신 댓글·좋아요·신고 Service는 대상 게시글이 `PUBLISHED`인지 반드시 검증해야 한다.** 이 검증이 없으면 삭제된 글에 API로 직접 댓글을 달 수 있다. 테스트로 고정할 항목이다.
- **첨부 이미지는 행뿐 아니라 저장된 파일도 남고, 그 파일의 URL은 계속 열려 있다.** 정적 경로(`/uploads/**`)가 인증 없이 열려 있어서다. 알고 받아들인 구멍이고 근거와 되돌아올 계기는 `specs/community-post.md` A4와 `PLAN.md` R33에 있다.

**이 검증은 불변식이 아니라 권한 판단이다.** 확인하는 SELECT와 뒤따르는 쓰기 사이에 게시글이 차단·삭제되면 그 쓰기는 그대로 통과한다. **부모 행을 잠가서 막지 않는다** — 위 첫 항목이 말하듯 "비노출 글에는 자식 행이 없다"는 불변식 자체가 없으므로 지킬 것이 없고, 댓글 쓰기마다 게시글 행을 잠그면 `specs/community-read.md` B3의 조회수 UPDATE와 같은 행을 두고 경합한다. 남는 창과 되돌아올 계기는 `PLAN.md` R14에 있다.

### 화면은 어디를 보나

커뮤니티 화면은 고객 3종(`templates/customer/community/{list,detail,form}.html`)과 관리자 2종
(`templates/admin/community/{list,detail}.html`)이다. 작성과 수정은 같은 `form.html`을 쓴다.

**무엇이 어떤 조건에서 보이는지는 이 문서의 4.3과 6절이 정본이고, 실제로 무엇이 렌더링되는지는
템플릿과 `CommunityScreenRenderingTests`가 보여 준다.** 화면 문구 목록을 따로 문서로 유지하지 않는다 —
2026-08-09에 `SCREENS.md`와 `screens/*.md`를 걷어냈다. 문구를 문서에 복사해 두면 템플릿과 갈라지는데,
그 어긋남을 잡아 주던 검사(H7)도 같은 날 폐기했기 때문이다(`PLAN.md` 결정 로그).

## 5. 권한

`SecurityConfig`에 이미 반영되어 있다.

- `GET /community`, `GET /community/{id}` → `permitAll` (비로그인 조회 허용)
- **댓글 작성·자기 댓글 삭제 → `hasAnyRole("USER", "ADMIN")`** (2026-08-18). 관리자가 커뮤니티에
  참여하는 유일한 경로다. 왜 댓글만인지는 `specs/community-comment.md`가 정본이다.
- 그 외 커뮤니티 쓰기 경로 → `anyRequest().hasRole("USER")`. 2026-08-11의 관리자·고객 분리
  (`SecurityConfig`, member 담당) 이후 `authenticated()`가 아니라 **일반 회원 전용**이다 — 관리자는
  좋아요·신고·글쓰기를 할 수 없고, 화면 숨김이 아니라 여기서 강제된다.
- `/admin/**` → `hasRole("ADMIN")`

**커뮤니티에서 회원 상태(`ACTIVE`/`SUSPENDED`/`WITHDRAWN`)를 재검증하지 않는다.**

- 근거: `MemberAuthenticationService`가 `loginAllowed = (status == ACTIVE)`로 만들고, `MemberDetailsService`가 false면 인증을 거부한다. 관리자가 정지시키면 `MemberAdminController`가 `expireSessionsByEmail`로 기존 세션도 즉시 만료시킨다. 즉 커뮤니티 코드에 도달하는 인증 사용자는 정의상 `ACTIVE`다.
- **이것은 member 도메인 구현에 대한 의존이지만, 그 전제는 테스트로 고정되어 있다** — `MemberAuthenticationServiceTests`(`SUSPENDED`/`WITHDRAWN`/status 누락 → `loginAllowed == false`), `MemberDetailsServiceTests`(`loginAllowed == false` → 인증 거부), `MemberAdminControllerTests`(정지 시 기존 세션 만료). 커뮤니티에 중복 검증을 넣지 않는다.

소유권 검증은 요청으로 전달된 회원 ID를 믿지 말고 **인증 사용자 기준으로 Service에서** 한다(`AGENTS.md`).

## 6. 기능별 규칙 — `specs/`로 옮겼다

**이 번호는 비워 두고 다시 쓰지 않는다.** 2026-08-10에 기능별 규칙을 `specs/`로 나누면서 6.1~6.9의
내용이 전부 옮겨 갔는데, **공유 Flyway migration과 seed의 주석이 `DOMAIN.md 6.2`·`6.8` 같은 절 번호를
그대로 들고 있다.** 그 파일들은 수정하지 않는 것이 프로젝트 규칙이라 참조를 고칠 수 없다. 번호를 다른
내용에 재사용하면 그 주석들이 **틀린 곳을 가리키게 된다** — 깨진 참조는 눈에 띄지만 옮겨 붙은 참조는
읽는 사람이 그대로 믿는다.

| 옛 절 | 지금 있는 곳 |
|---|---|
| 6.1 목록 | `specs/community-read.md` B1 |
| 6.2 상세·조회수 | `specs/community-read.md` B2·B3 |
| 6.3 작성·수정·삭제 | `specs/community-post.md` A1·A2·A3 |
| 6.4 댓글 | `specs/community-comment.md` A5·A6·B4 |
| 6.5 좋아요 | `specs/community-reaction.md` A8 |
| 6.6 신고 | `specs/community-reaction.md` A9 |
| 6.7 관리자 차단 | `specs/community-admin.md` C1~C4 |
| 6.8 카테고리 | 이 문서 10절 |
| 6.9 인기글 | `specs/community-popular.md` B5·E3 |

같은 이유로 `PLAN.md`의 위험·결정 로그에 남은 `6.x` 표기도 고치지 않았다. 과거에 그렇게 적힌 기록이고,
이 표가 그것을 지금 자리로 옮겨 준다.

## 7. 입력 검증

| 항목 | 규칙 | DB 컬럼 |
|---|---|---|
| 게시글 제목 | 필수, 1~100자 | `VARCHAR(200)` |
| 게시글 본문 | 필수, 1~5000자 | `TEXT` |
| 댓글 내용 | 필수, 1~500자 | `TEXT` |
| 신고 사유 | 필수, 1~500자 | `VARCHAR(500)` |
| 차단 사유 | 필수, 1~500자 | `VARCHAR(500)` (`posts.blocked_reason`) |
| 공지 제목 | 필수, 1~100자 | `VARCHAR(200)` (`community_notices.title`) |
| 공지 본문 | 필수, 1~5000자 | `TEXT` (`community_notices.content`) |
| 공지 노출 기간 | 선택. 둘 다 있으면 시작 < 종료 | `DATETIME(6)` NULL 둘 (`starts_at`·`ends_at`). 표에도 `CHECK`가 있다 |

- 화면 허용 길이를 DB 컬럼 크기보다 작게 잡는다. 컬럼 크기를 그대로 쓰면 정책을 정한 게 아니라 안 정한 것이다.
- **trim 후 검증**한다. 공백만 입력은 거부. 저장 시 제목은 trim, 본문은 앞뒤만 trim(중간 줄바꿈 보존).
- **본문에 HTML을 허용하지 않는다. 순수 텍스트만.** 템플릿에서 `th:utext`를 절대 쓰지 않는다(`th:text`가 기본 이스케이프한다). 줄바꿈은 CSS `white-space: pre-wrap`으로 처리한다.

## 8. 탈퇴 회원

- `posts.member_id`는 NOT NULL FK이므로 탈퇴해도 회원 행은 남는다.
- **탈퇴 회원의 글·댓글은 유지하고, 작성자명만 "탈퇴한 회원"으로 표시**한다.
- **탈퇴 여부는 회원 도메인이 판정한다.** 커뮤니티는 `MemberCommunityQueryService`에서 `withdrawn`을 받아 표시명만 고른다. 커뮤니티 SQL이 `members`를 JOIN해 직접 판정하지 않는다(조각 10b, `conventions.md` 15.1).
- **회원 행을 찾지 못해도 글은 목록에 남고 작성자만 가려진다.** JOIN하던 때는 그런 글이 목록에서 통째로 사라졌는데, 그건 위 규칙과 어긋난다.
- 글을 함께 삭제하지 않는 이유: 질문글이 사라지면 거기 달린 답변들이 맥락을 잃는다. 커뮤니티는 개인 소유물이 아니라 공유 자산이다. 실명을 그대로 두지 않는 이유는 개인정보다. 표시명만 바꾸면 데이터를 건드리지 않으므로 되돌릴 수 있다.

## 9. 보류 (조각 진행 중 결정)

| 항목 | 결정 시점 |
|---|---|
| ~~`post_reports.status` 전이 (`PENDING` → ?)~~ | **결정됨 (조각 5)**. `PENDING → RESOLVED`(차단) / `PENDING → REJECTED`(기각), 둘 다 종착. 게시글 단위로 바꾼다. 근거는 6.6 |
| ~~관리자 목록 화면의 필터·정렬~~ | **결정됨 (조각 5)**. 상태 필터 + 최신순/미처리 신고 많은 순. 작성자·제목 검색은 넣지 않는다. 근거는 6.7 |
| ~~좋아요 기준 정렬(`sort=likes`) 추가 여부~~ | **결정됨 (조각 7a)**. 넣지 않는다. 근거는 6.1 — 분기를 하나 더 여는 값이 크지 않고, 좋아요로 줄을 세우는 것은 인기글이 대신한다 |
| ~~**인기글의 기간** — 누적(`posts.view_count`)인가 기간별(`post_views`를 `created_at`으로 집계)인가~~ | **결정됨 (조각 7b)**. 기간별. 매일 새벽 배치가 확정하고 화면은 스냅샷만 읽는다. **폭은 최근 7일이었다가 조각 20(2026-08-19)이 대상일 하루로 좁혔다.** 근거와 점수식은 6.9 |
| ~~**인기글을 어디에 두나** — 목록 화면 상단 영역인가 별도 화면인가~~ | **결정됨 (조각 7c)**. 목록 화면 상단의 영역이고 별도 화면을 만들지 않는다. 근거와 노출 조건은 6.9 |
| ~~**`post_views` 보관 기간** — 무한히 쌓인다~~ | **결정됨 (조각 7b)**. 1차에서는 정리하지 않는다. 지우면 `view_count == COUNT(post_views)`(H14)가 깨진다. 스냅샷이 과거 순위를 보존하므로 나중에 정리로 넘어갈 근거는 생겼다 — 되돌아올 계기는 `PLAN.md` R25 |
| ~~댓글 페이징~~ | **결정됨 (조각 3)**. 전체 로드가 아니라 최신 20건 + `이전 댓글 더 보기`. 근거는 6.4 |
| ~~조회수를 정렬·순위에 쓰는지~~ | **결정됨 (2026-08-03)**. 쓴다. 그래서 중복 방지가 생겼다 — 6.2, 그리고 2절의 범위 변경 |

## 10. 카테고리

옛 6.8이다. 여러 기능(B1 목록 필터, A1·A2 작성·수정 폼)이 함께 쓰는 참조 데이터라 spec이 아니라
여기에 둔다.

- `QNA`(질문) / `REVIEW`(후기) / `FREE`(자유) 3종을 **새 Flyway migration으로 주입**한다.
- `seed-local.sql`이 아닌 migration인 이유: `posts.category_id`가 NOT NULL FK이므로 카테고리가 없으면 운영에서 글쓰기가 아예 불가능하다. 샘플 데이터가 아니라 기능 동작에 필요한 참조 데이터다. 선례: `V20260729_003452__provision_default_store.sql`.
- 카테고리 목록(필터 드롭다운, 글쓰기 선택지)에는 `is_active = 1`만 노출한다. 비활성 카테고리에 이미 달린 글은 그대로 보인다.
- 카테고리 관리 화면은 만들지 않는다.

## 11. 공통 하네스

기능 하나에 붙지 않고 도메인 전체에 걸리는 검증이다. 기능별 하네스는 해당 `specs/*.md`의 `검증` 절이
소유하고, 번호만 모은 인덱스는 `PLAN.md`에 있다.

**H0a — 게시글·댓글 상태 전이 규칙과 상태값 제약.** `PostStatusTests`, `CommentStatusTests`, `CommunitySchemaTests`. 전이는 enum 표로 고정한다. 댓글에는 차단이 없고 지운 댓글은 되돌아오지 않는다.

**H0b — `posts.status`·`comments.status`에 미정의 값이 저장되지 않음.** 카테고리 3종이 활성 상태로 존재하는 것까지. `CommunitySchemaTests`(6)가 MariaDB Testcontainers로 CHECK 제약을 검증한다.

**H1b — 실행 쿼리 수가 행 수와 무관함.** 고객 목록·댓글 구역·상세와 관리자 목록 **네 화면 전부**. `CommunityQueryCountTests`가 MyBatis `Interceptor`로 실행 statement 수를 센다. 조각 10b에서 작성자 조회가 붙어 기대값이 고정 1회씩 늘었고(목록 2→3, 댓글 2→3, 상세 1→2), 10d에서 관리자 목록까지 넓혔다 — 10c에서 같은 N+1 위험이 생겼는데 배치 조회로 막아 뒀을 뿐 고정하지는 않은 상태였다. **기능 하나가 아니라 네 화면에 걸려 있어 여기 있다** — `community-read.md`·`community-comment.md`·`community-admin.md`가 각각 이 행을 가리킨다. 어느 한 화면의 조회를 고치다 기대값이 움직이면 나머지 셋의 기대값도 같은 파일에 있다.

**H3 — Controller가 Mapper를 직접 호출하지 않음.** ArchUnit. **아직 적용하지 않았다** — 위반이 발생하면 그때 세운다.

**H4 — 본문·제목·댓글의 HTML이 이스케이프됨.** `th:utext`를 쓰지 않은 것의 실제 결과다. `CommunityScreenRenderingTests`가 한 화면에 세 자리를 함께 심고 렌더링 결과를 확인한다. **필드마다 테스트를 늘리지 않는다** — 같은 템플릿 안에서는 `th:text` 하나를 반복 검증하는 셈이다. 다만 공지처럼 템플릿이 다르면 그 템플릿의 검사가 따로 필요하다.

**H5 — 커뮤니티 화면이 실제로 렌더링됨.** 작성자에게만 열리는 차단 안내 화면 포함. `CommunityScreenRenderingTests`가 Thymeleaf를 실제로 돌린다. **Controller 단위 테스트는 뷰 이름만 보므로 템플릿이 깨져도 통과한다.**

**H34 — 커뮤니티 SQL이 `members`를 건드리지 않음.** 커뮤니티 코드가 회원 도메인에서 **합의된 계약 둘**(`MemberCommunityQueryService`, `MemberCommunityView`)만 쓰는 것까지. `CommunityDomainBoundaryTests`(2)가 SQL의 회원 소유 테이블 참조를 막고, 자바 참조가 허용 계약과 정확히 일치하는지 확인한다.

**H35 — 탈퇴 회원의 글·댓글·신고가 목록에 남고 표시명만 가려짐.** 고객·관리자 양쪽. `CommunityMemberContractTests`(5)가 실제 MariaDB로 확인한다. 커뮤니티 조회와 회원 조회 **두 문장이 합쳐진 결과**는 여기서만 드러난다. 양쪽을 각각 보는 검사는 이미 있지만, `members.status`에 값이 늘거나 `nickname`이 옮겨 가면 화면의 작성자가 전부 "탈퇴한 회원"이 되는데도 그 검사들은 전부 통과한다. **회원 행이 아예 없는 경우는 여기에 없다** — `posts.member_id`가 NOT NULL FK라 만들 수 없고, 그쪽은 Service 테스트가 mock으로 본다.
