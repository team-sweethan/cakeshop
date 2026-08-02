# Community 도메인 규칙 (1차 MVP)

> 이 문서는 Community 도메인의 **결정된 규칙의 정본**이다.
> 진행 상태와 조각 순서는 `docs/community/PLAN.md`를 본다.
> 프로젝트 전체 규칙은 `AGENTS.md`가 상위 정본이며, 충돌 시 `AGENTS.md`가 우선한다.

## 1. 한 문장 정의

Cakeshop 커뮤니티는 고객이 케이크 관련 질문과 후기를 공유하는 공간이다.

## 2. MVP 범위

**포함**: 게시글 목록·상세, 게시글 CRUD, 댓글(1단계) 작성·삭제, 좋아요, 신고, 관리자 차단, 페이지 번호 페이징

**제외**: 이미지 첨부(`post_images` 미사용), 알림, 실시간 댓글, 대댓글, 댓글 수정, 검색, 인기글·정렬 옵션, 무한 스크롤, 카테고리 관리 화면

## 3. 기존 자산

스키마는 `V0__initial_schema.sql`에 **이미 전부 존재**한다. 공유된 migration이므로 수정하지 않고, 필요하면 새 versioned migration을 추가한다(`gradlew newMigration -Pdesc=<snake_case>`).

- `post_categories`, `posts`, `comments`, `post_likes`, `post_images`, `post_reports`
- `post_likes(post_id, member_id)` UNIQUE 존재
- `post_reports(post_id, reporter_id)` UNIQUE 존재
- `posts`에 `status`, `blocked_at`, `blocked_reason`, `blocked_by`, `view_count`, `like_count` 존재
- `posts`에 `comment_count` 컬럼은 **없다**

코드 뼈대: `domain/community/`에 controller 3개(뷰 이름만 반환), 빈 `CommunityService`, 빈 `CommunityMapper`, 빈 entity 3개, `CommunityErrorCode`. 테스트 0개.

템플릿: `templates/customer/community/{list,detail,form}.html`, `templates/admin/community/{list,detail}.html`

기존 공용 유틸: `global/common/paging/PageRequest`(1-based, offset, 기본 20, 최대 100), `PageResult`(totalElements, totalPages)

## 4. 상태 모델

### 4.1 노출 기준

**`posts.status`가 게시글 노출 여부의 유일한 기준이다.** `blocked_at`/`blocked_reason`/`blocked_by`는 부가 기록일 뿐 노출 판단에 쓰지 않는다.

- 이유: 조건이 두 개면 새 쿼리를 추가할 때 하나를 빠뜨려 차단된 글이 노출된다. `WHERE status = 'PUBLISHED'` 하나로 끝내면 빠뜨릴 것이 없다.
- `PostStatus { PUBLISHED, DELETED, BLOCKED }` enum으로 정의하고, `MemberStatus.canTransitionTo` 선례를 따라 전이 규칙을 enum에 둔다.
- 새 migration으로 `CHECK (status IN ('PUBLISHED','DELETED','BLOCKED'))`를 추가한다 (선례: `V20260729_123306__add_member_status_constraint.sql`).

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

게시글을 soft delete해도 `comments`, `post_likes`, `post_reports` 행은 **그대로 둔다.**

- 게시글이 `DELETED`면 상세가 404이므로 댓글이 노출될 경로가 없다. 자식까지 일괄 `DELETED`로 바꾸는 것은 중복이고, "원래 삭제돼 있던 댓글"과 "게시글 때문에 삭제된 댓글"을 구분할 수 없게 만든다.
- **대신 댓글·좋아요·신고 Service는 대상 게시글이 `PUBLISHED`인지 반드시 검증해야 한다.** 이 검증이 없으면 삭제된 글에 API로 직접 댓글을 달 수 있다. 테스트로 고정할 항목이다.

## 5. 권한

`SecurityConfig`에 이미 반영되어 있다.

- `GET /community`, `GET /community/{id}` → `permitAll` (비로그인 조회 허용)
- 그 외 커뮤니티 경로 → `anyRequest().authenticated()`
- `/admin/**` → `hasRole("ADMIN")`

**커뮤니티에서 회원 상태(`ACTIVE`/`SUSPENDED`/`WITHDRAWN`)를 재검증하지 않는다.**

- 근거: `MemberAuthenticationService`가 `loginAllowed = (status == ACTIVE)`로 만들고, `MemberDetailsService`가 false면 인증을 거부한다. 관리자가 정지시키면 `MemberAdminController`가 `expireSessionsByEmail`로 기존 세션도 즉시 만료시킨다. 즉 커뮤니티 코드에 도달하는 인증 사용자는 정의상 `ACTIVE`다.
- **이것은 member 도메인 구현에 대한 의존이지만, 그 전제는 테스트로 고정되어 있다** — `MemberAuthenticationServiceTests`(`SUSPENDED`/`WITHDRAWN`/status 누락 → `loginAllowed == false`), `MemberDetailsServiceTests`(`loginAllowed == false` → 인증 거부), `MemberAdminControllerTests`(정지 시 기존 세션 만료). 커뮤니티에 중복 검증을 넣지 않는다.

소유권 검증은 요청으로 전달된 회원 ID를 믿지 말고 **인증 사용자 기준으로 Service에서** 한다(`AGENTS.md`).

## 6. 기능별 규칙

### 6.1 목록 (`GET /community`)

- 오프셋 페이지 번호 페이징. `PageRequest`/`PageResult` 재사용.
- 페이지 크기 **20 고정** (사용자가 바꿀 수 없음).
- 정렬 **`created_at DESC, id DESC` 고정**. 정렬 옵션 없음. `id` tiebreaker가 없으면 동일 시각 글이 두 페이지에 중복되거나 누락된다.
- `?categoryId=` 선택 필터. 없으면 전체.
- 총 개수 `COUNT(*)` 조회 (PageResult가 요구).
- 표시 항목: 제목, 작성자명, 카테고리명, 작성일, 조회수, 좋아요 수, 댓글 수.
- **본문 미리보기 없음.** `content`는 TEXT이며 목록에서 SELECT하지 않는다.

**댓글 수는 반드시 스칼라 서브쿼리로 집계한다:**

```sql
SELECT p.id, p.title, ...,
       (SELECT COUNT(*) FROM comments c
         WHERE c.post_id = p.id AND c.status = 'PUBLISHED') AS comment_count
FROM posts p
JOIN post_categories pc ON pc.id = p.category_id
JOIN members m ON m.id = p.member_id
WHERE p.status = 'PUBLISHED'
ORDER BY p.created_at DESC, p.id DESC
LIMIT #{size} OFFSET #{offset}
```

**`LEFT JOIN comments ... GROUP BY p.id`로 바꾸지 말 것.** `LIMIT`은 `GROUP BY` 이후에 적용되므로 전체 게시글 × 전체 댓글을 조인하고 전부 집계한 뒤 20개를 잘라낸다. 결과는 동일하지만 전체 스캔이 된다. 스칼라 서브쿼리는 최종 결과 20행에 대해서만 평가되며 `fk_comments_post` 인덱스를 탄다.

### 6.2 상세 (`GET /community/{id}`)

- 조회수는 **무조건 +1**. 중복 방지 없음(세션·이력 테이블 모두 사용하지 않음). 작성자가 새로고침해도 오르는 것을 감수한다.
- 근거: 조회수는 참고용 표시일 뿐 정렬·순위에 쓰이지 않는다. 정확성을 위해 세션 크기나 새 테이블 쓰기 부하를 감수할 가치가 없다.
- Service 메서드 하나 안에서 `UPDATE view_count` → `SELECT 상세` 순으로 처리하고 통째로 `@Transactional`을 건다.
- **노출되지 않는 글(`DELETED`/`BLOCKED`)의 조회수는 올리지 않는다.** UPDATE에 `status = 'PUBLISHED'` 조건을 두어 처리한다. 상세가 404인 글의 조회수를 올릴 이유가 없고, 조건을 UPDATE 쪽에 두면 노출 판단 때문에 SELECT를 두 번 하지 않아도 된다. 차단된 글을 작성자가 열어보는 경우도 마찬가지로 올리지 않는다 — 조회수는 공개 지표인데 그 글은 아무에게도 노출되지 않는다.
- **조회수 UPDATE는 `updated_at`을 명시적으로 보존한다**(`SET view_count = view_count + 1, updated_at = updated_at`). `posts.updated_at`은 `ON UPDATE CURRENT_TIMESTAMP(6)`이므로 그냥 두면 조회만으로 값이 바뀌고, 6.3의 "수정됨" 표시가 켜진다. 화면에는 조용히 "(수정됨)"이 붙을 뿐이라 원인을 찾기 어렵다.

### 6.3 작성·수정·삭제

- 수정 가능 항목: **제목, 본문, 카테고리** 전부.
- 수정 이력 테이블 없음. `updated_at != created_at`이면 화면에 "수정됨" 표시.
- 수정 가능 시간 제한 없음.
- 삭제는 `status = 'DELETED'` 전이 (4.2).

### 6.4 댓글

- 1단계만. **`parent_comment_id`는 엔티티·DTO·SQL 어디에도 등장시키지 않는다.** 컬럼은 V0에 존재하지만 값을 넣는 코드가 없으면 값이 들어갈 수 없다. 2차에 정식으로 대댓글을 구현할 예정이므로 `CHECK` 제약은 걸지 않는다(곧 떼야 할 제약이다).
- 작성·삭제만. **수정 없음.**
- 삭제는 soft delete + 자리 표시 (4.4).

### 6.5 좋아요

- **`POST /community/{id}/likes`(추가) / `DELETE /community/{id}/likes`(취소) 분리.** 토글로 만들지 않는다. 토글은 재전송·더블클릭 시 두 번 실행되어 원래 상태로 되돌아가고, 사용자는 눌렀는데 안 눌린 상태가 된다.
- 두 메서드 모두 **멱등**하게 만든다. 이미 눌린 상태에서 POST를 다시 받아도 성공으로 응답한다(UNIQUE 위반을 에러로 노출하지 않는다).
- **`posts.like_count`는 매번 재계산한다**: `UPDATE posts SET like_count = (SELECT COUNT(*) FROM post_likes WHERE post_id = ?) WHERE id = ?`
- 증분(`like_count + 1`) 방식을 쓰지 않는 이유: 한 번 틀어지면 스스로 복구되지 않고 아무도 모른다. 앞으로 `like_count`를 건드릴 경로(삭제·차단·탈퇴)가 늘어날 때마다 "여기서도 조정해야 하나"를 판단해야 하는데, 재계산은 그 판단 자체가 없다. `uk_post_likes_post_member`의 선두 컬럼이 `post_id`이므로 인덱스 범위 스캔으로 끝난다.
- INSERT/DELETE와 카운트 갱신은 **하나의 Service 트랜잭션**에서 처리한다.

### 6.6 신고

- 중복 신고는 **에러로 응답**한다("이미 신고한 게시글입니다"). 좋아요와 달리 멱등 성공 처리를 하지 않는 이유: 재신고는 "내 신고가 처리되지 않았다"는 인식의 표현이다. 조용히 성공을 돌려주면 접수됐다고 오해하지만 실제로는 아무 일도 일어나지 않는다.
- **신고 취소 불가.** 신고는 개인 선호 표시가 아니라 관리자에게 전달된 보고다. 관리자가 이미 조치했을 수 있고, 취소를 허용하면 "신고 → 취소 → 신고"로 UNIQUE를 우회한 반복 신고가 가능해진다.
- 댓글 신고는 1차 범위 밖이다 (`post_reports`는 게시글 전용).

### 6.7 관리자 차단

- **관리자 수동 차단만.** 신고 누적 자동 차단·자동 숨김 없음.
- 근거: `blocked_by`가 `members.id` FK다. 자동 차단은 넣을 값이 없어 시스템 계정이나 암묵적 NULL 규칙이 필요하다. 스키마가 사람이 차단하는 것을 전제한다. 또한 임계값 방식은 담합 어뷰징 방어(신고자 신뢰도 등)를 불러오며 MVP를 벗어난다.
- 차단 시 `status='BLOCKED'`, `blocked_at`, `blocked_reason`, `blocked_by`를 함께 기록한다.

### 6.8 카테고리

- `QNA`(질문) / `REVIEW`(후기) / `FREE`(자유) 3종을 **새 Flyway migration으로 주입**한다.
- `seed-local.sql`이 아닌 migration인 이유: `posts.category_id`가 NOT NULL FK이므로 카테고리가 없으면 운영에서 글쓰기가 아예 불가능하다. 샘플 데이터가 아니라 기능 동작에 필요한 참조 데이터다. 선례: `V20260729_003452__provision_default_store.sql`.
- 카테고리 목록(필터 드롭다운, 글쓰기 선택지)에는 `is_active = 1`만 노출한다. 비활성 카테고리에 이미 달린 글은 그대로 보인다.
- 카테고리 관리 화면은 만들지 않는다.

## 7. 입력 검증

| 항목 | 규칙 | DB 컬럼 |
|---|---|---|
| 게시글 제목 | 필수, 1~100자 | `VARCHAR(200)` |
| 게시글 본문 | 필수, 1~5000자 | `TEXT` |
| 댓글 내용 | 필수, 1~500자 | `TEXT` |
| 신고 사유 | 필수, 1~500자 | `VARCHAR(500)` |

- 화면 허용 길이를 DB 컬럼 크기보다 작게 잡는다. 컬럼 크기를 그대로 쓰면 정책을 정한 게 아니라 안 정한 것이다.
- **trim 후 검증**한다. 공백만 입력은 거부. 저장 시 제목은 trim, 본문은 앞뒤만 trim(중간 줄바꿈 보존).
- **본문에 HTML을 허용하지 않는다. 순수 텍스트만.** 템플릿에서 `th:utext`를 절대 쓰지 않는다(`th:text`가 기본 이스케이프한다). 줄바꿈은 CSS `white-space: pre-wrap`으로 처리한다.

## 8. 탈퇴 회원

- `posts.member_id`는 NOT NULL FK이므로 탈퇴해도 회원 행은 남는다.
- **탈퇴 회원의 글·댓글은 유지하고, 작성자명만 "탈퇴한 회원"으로 표시**한다.
- 글을 함께 삭제하지 않는 이유: 질문글이 사라지면 거기 달린 답변들이 맥락을 잃는다. 커뮤니티는 개인 소유물이 아니라 공유 자산이다. 실명을 그대로 두지 않는 이유는 개인정보다. 표시명만 바꾸면 데이터를 건드리지 않으므로 되돌릴 수 있다.

## 9. 보류 (조각 진행 중 결정)

| 항목 | 결정 시점 |
|---|---|
| 목록·상세에 "내가 좋아요 눌렀는지" 표시 | 좋아요 조각 |
| `post_reports.status` 전이 (`PENDING` → ?) | 신고·차단 조각 |
| 관리자 목록 화면의 필터·정렬 | 관리자 조각 |
| 댓글 페이징 (1차는 전체 로드 가정) | 댓글 조각 |
