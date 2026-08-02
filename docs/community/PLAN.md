# Community 진행 계획

> 도메인 규칙의 정본은 `docs/community/DOMAIN.md`다. 이 문서는 **작업 순서, 진행 상태, 결정 로그, 위험**만 다룬다.
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
| 0 | 준비 | 대기 | `PostStatus` enum, `CHECK` 제약 migration, 카테고리 3종 주입 migration |
| 1 | 목록·상세 | 대기 | 카테고리 필터, 페이징, 상세 조회, 조회수 |
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

### 조각 1 — 목록·상세

- `CommunityMapper` + `CommunityMapper.xml`: 목록(스칼라 서브쿼리), 총 개수, 상세, 조회수 증가
- `CommunityService`: `PageRequest`/`PageResult` 사용, 상세 조회 시 `UPDATE → SELECT` 단일 트랜잭션
- 목록/상세 DTO 분리 (`dto/view/`)
- `CommunityController` 뷰 바인딩, 템플릿 3종 채우기

**검증**:
- `./gradlew clean test`
- **H1a** — 목록 SQL이 스칼라 서브쿼리 형태인지 정적 검사 (`GROUP BY`가 없고 `SELECT COUNT(*) FROM comments`를 포함)
- **H1b** — 게시글 건수를 늘려도 실행 쿼리 수가 변하지 않는지 (목록 1 + 총 개수 1)
- 상태별 상세 접근 규칙 (DOMAIN.md 4.3 표의 각 칸)
- 페이징 경계: 마지막 페이지, 범위 밖 페이지, `created_at`이 동일한 글이 중복·누락되지 않는지

### 조각 2 — 작성·수정·삭제

- 소유권 검증은 인증 사용자 기준으로 Service에서
- 삭제 = `PUBLISHED → DELETED` 전이
- 입력 검증 (DOMAIN.md 7)

**검증**: 남의 글 수정·삭제 시도 거부, `BLOCKED` 글 수정·삭제 시도 거부, 검증 실패 케이스, 공백만 입력 거부.

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

## 하네스 (누적)

조각을 진행하며 여기에 쌓는다. 빈 칸은 아직 필요가 발생하지 않은 것이다.

| # | 무엇을 고정하나 | 형태 | 추가 시점 |
|---|---|---|---|
| H1a | 목록 SQL이 스칼라 서브쿼리 형태를 유지함 | `XMLMapperBuilder`로 XML 파싱 후 SQL 문자열 검사 (선례: `OrderMapperXmlTests`) | 조각 1 (예정) |
| H1b | 목록 조회 시 실행 쿼리 수가 게시글 수와 무관 | MyBatis `Interceptor`로 실행 statement 수 카운트 | 조각 1 (예정) |
| H2 | `like_count`와 실제 좋아요 수 일치 | 동시 요청 테스트 | 조각 4 (예정) |
| H3 | Controller가 Mapper를 직접 호출하지 않음 | ArchUnit | 위반 발생 시 |
| H4 | 템플릿에서 `th:utext` 미사용 | 정적 검사 또는 테스트 | 위반 발생 시 |

## 위험

| # | 위험 | 상태 |
|---|---|---|
| R1 | ~~`SUSPENDED` 회원 로그인 차단에 의존~~ — **해소.** 전제가 3중으로 테스트되어 있음을 확인했다: `MemberAuthenticationServiceTests:51`(`@EnumSource({SUSPENDED, WITHDRAWN})` → `loginAllowed == false`, status 누락 시에도 차단), `MemberDetailsServiceTests:65`(`loginAllowed == false` → `UsernameNotFoundException`), `MemberAdminControllerTests:151`(정지 시 기존 세션 만료). 커뮤니티에 중복 검증을 넣지 않는다 | 해소 (2026-08-02) |
| R2 | 목록 쿼리를 `LEFT JOIN ... GROUP BY`로 바꿔도 결과가 같아 눈으로는 안 잡힌다. **실행 쿼리 수 측정으로는 잡히지 않는다** — GROUP BY로 바꿔도 쿼리는 여전히 1번이다. 형태 검사(H1a)가 있어야 잡힌다 | H1a로 방어 예정 (조각 1) |
| R3 | soft delete 도입이 이 프로젝트의 첫 사례다. 다른 도메인에 선례가 없어 팀 컨벤션과 어긋날 수 있다 | 조각 1 리뷰에서 확인 |
| R4 | `comment_count` 비정규화 컬럼이 없어 집계로 처리한다. 트래픽이 늘면 컬럼 추가로 전환 필요 | 1차에선 수용 |

## 결정 로그

| 날짜 | 내용 |
|---|---|
| 2026-08-02 | 초기 grill 완료. 결정 17건을 DOMAIN.md에 반영, 보류 4건. 상세 근거는 DOMAIN.md 각 절에 있다 |
| 2026-08-02 | 댓글 삭제 정책을 "목록에서 완전 제외"에서 **"자리 표시 유지"로 변경**. 2차에 대댓글을 확실히 구현하기로 하면서, 그때 정책을 뒤집는 비용(테스트·화면 재작업)보다 지금부터 맞추는 편이 싸다고 판단 |
| 2026-08-02 | `post_categories`에 데이터를 넣는 코드가 어디에도 없다는 사실 발견. 조각 0에 카테고리 주입 migration 추가 |
| 2026-08-02 | R1 확인 후 해소. member 도메인 테스트 3건이 전제를 고정하고 있어 커뮤니티에 중복 검증을 넣지 않기로 확정 |
| 2026-08-02 | H1을 H1a(형태 고정)/H1b(실행 횟수)로 분리. 실행 쿼리 수 측정만으로는 `GROUP BY` 재작성을 못 잡는다는 점을 반영 |
