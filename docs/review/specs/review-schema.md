# 기반 정리 — E1·E2·E3

> 공통 규칙(상태·평점·권한·검증·오류 코드·표시·도메인 경계)은 `../DOMAIN.md` 2절이 정본이다.
> 조각 순서와 진행 상태는 `../PLAN.md`. 결정의 배경은 `../decisions/`.
> 조각: 0 — **머지 완료(#125).** 조각 기록은 `history/2026-08-slice-0-schema.md`.

화면이 없는 작업이다. 전부 조각 0이다.

### E1. `ReviewStatus` enum + `CHECK`

- `ReviewStatus { PUBLISHED, DELETED, BLOCKED }`를 `domain/review/entity/`에 둔다. **enum 이름을 DB 문자열로 그대로 저장**한다(4개 도메인이 이미 동일).
- 전이 규칙은 `canTransitionTo(next)`에. `null` 방어까지 `PostStatus`와 같게.
- 새 migration으로 `reviews.status` 기본값 `'VISIBLE'` → `'PUBLISHED'`, `CHECK (status IN ('PUBLISHED','DELETED','BLOCKED'))`.

### E2. 평점 범위 `CHECK`

- 평점 4종에 `CHECK (... BETWEEN 1 AND 5)`.
- 화면 검증만으로는 API 직접 호출을 막지 못한다. `TINYINT UNSIGNED`라 제약 이전에는 0과 255가 들어갔다(`PLAN.md` R5).

### E3. 엔티티

- `Review`·`ReviewReply`는 **필드가 하나도 없는 빈 클래스**였다. 스키마에 맞춰 채웠다.
- migration 파일명은 직접 짓지 않고 `gradlew newMigration -Pdesc=<snake_case>`로 생성한다.
