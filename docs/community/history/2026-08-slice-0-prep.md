# 조각 0 — 준비

> **끝난 조각의 기록이다. 지금 구속하지 않는다.**
> 이 조각이 만든 규칙의 정본은 `../DOMAIN.md` 4절다.
> 조각 순서와 진행 상태는 `../PLAN.md`, 발견된 문제는 `../reviews/`,
> 방향을 고른 판단은 `../decisions/`에 있다.

**왜 먼저인가**: 조각 1의 모든 쿼리가 `status='PUBLISHED'` 조건에 의존하고, 카테고리가 없으면 게시글을 하나도 만들 수 없다. 순서를 바꾸면 조각 1에서 되돌아와야 한다.

- `PostStatus { PUBLISHED, DELETED, BLOCKED }` + `canTransitionTo` (`MemberStatus` 선례를 따름)
- 새 migration: `posts.status` CHECK 제약
- 새 migration: `post_categories`에 `QNA/REVIEW/FREE` 주입
- migration 파일명은 직접 짓지 않고 `gradlew newMigration -Pdesc=<snake_case>`로 생성

**검증**: 전이 규칙 단위 테스트(허용 3 / 금지 3), MariaDB Testcontainers로 CHECK 제약이 잘못된 값을 거부하는지, 카테고리 3건이 주입되는지.

**완료 (2026-08-02, `4c9cdb7`)**. `PostStatus`(전이 규칙 포함), `CommentStatus`, `V20260802_113219__add_post_status_constraint.sql`(`posts`·`comments` 두 컬럼), `V20260802_113229__provision_post_categories.sql`을 추가했다. 검증은 `PostStatusTests`와 `CommunitySchemaTests`로 고정했고 하네스 표에 H0a·H0b로 올렸다. `comments.status`도 함께 제약을 건 것은 계획보다 넓지만, 댓글 자리 표시 정책(DOMAIN.md 4.4)이 상태값에 의존하므로 같은 migration에 담았다.
