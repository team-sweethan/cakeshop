# 조각 0 — 준비 (#108, PR #125 머지 완료)

> **끝난 조각의 기록이다. 지금 구속하지 않는다.**
> 이 조각이 만든 규칙 자체는 `../DOMAIN.md` 2.1·2.2가 정본이다. (조각 0 전용 spec이었던
> `../specs/review-schema.md`는 2026-08-09에 이 파일로 흡수했다 — 머지되어 더 이상 구속하지 않는 것은
> `history/`에 둔다는 기준 그대로다.)
> 머지: PR #125, 커밋 `5f6f7e7`. migration `V20260806_075114__add_review_status_and_rating_constraints.sql`.

착수 시점에 적어 두었던 항목은 아래와 같다(기능 ID: E1 enum+CHECK, E2 평점 CHECK, E3 엔티티).

- `ReviewStatus { PUBLISHED, DELETED, BLOCKED }` + `canTransitionTo` (`PostStatus` 선례를 그대로 따름, `null` 방어 포함)
- `Review`·`ReviewReply` 엔티티는 필드가 하나도 없는 빈 클래스였고, 스키마에 맞춰 채웠다
- 새 migration: `reviews.status` 기본값을 `'VISIBLE'` → `'PUBLISHED'`로 바꾸고 `CHECK (status IN ('PUBLISHED','DELETED','BLOCKED'))` 추가
  - **`CHECK`를 걸기 전에 기존 `'VISIBLE'` 행을 `'PUBLISHED'`로 변환한다.** 남아 있으면 제약 추가가 배포 중 실패한다. 당시 `reviews`는 비어 있고 INSERT 경로도 없었지만, 한 줄로 막을 수 있는 것을 환경 상태에 맡기지 않았다. 선례: `V20260730_123931__apply_product_preparation_policy.sql`(보정 UPDATE 후 CHECK)
- 새 migration: 평점 4종에 `CHECK (rating BETWEEN 1 AND 5)`
  - **`status`와 달리 사전 보정 UPDATE를 두지 않았다.** 근거는 `../DOMAIN.md` 2.2
- migration 파일명은 직접 짓지 않고 `gradlew newMigration -Pdesc=<snake_case>`로 생성
- **당시 중앙 상태 인벤토리의 `reviews.status` 행도 함께 갱신한다.** 당시에는 `VISIBLE / HIDDEN ?` · `☐ 열림`으로 남아 있었고, 상태를 확정하면 **enum + DDL을 함께 커밋한다**는 기준을 사용했다. 중앙 인벤토리는 이후 폐지했으며 현재 공통 규칙은 `docs/conventions.md` 11절, 리뷰 정책은 `docs/review/DOMAIN.md` 2.1절이 정본이다

**검증**: 전이 규칙 단위 테스트(허용/금지 각 케이스), Testcontainers로 `CHECK`가 잘못된 상태값·평점을 거부하는지, 기본값이 `PUBLISHED`인지.

## 여기서 배운 것

**머지하면서 인벤토리를 함께 안 고쳤다.** `../DOMAIN.md` 1절 기능 목록의 E1·E2·E3 `현재` 열이 조각 0 머지 뒤에도 `없음`·`빈 클래스`로 남아 있었고, 문서 재배치 때 실제 코드와 대조하고서야 드러났다. 커뮤니티에서도 같은 자리가 있었다(`docs/community/PLAN.md` R32).

**규칙으로는 안 되는 자리다.** "조각마다 문서를 함께 고친다"는 규칙은 이미 있었고 지켜지지 않았다.

**이 자리를 기계로 막는 검사는 없다.** 한때 문서 형태 하네스(H1~H6)를 세웠지만 인벤토리의 `현재` 열 같은 내용 드리프트는 원리상 잡지 못했고, 2026-08-09에 하네스 전체를 걷어냈다(`../decisions/decision-log.md` 08-09 행). **없는 안전망을 있다고 적는 것이 없는 것보다 나쁘다** — 다음 사람이 그것을 믿고 안 본다. 문서와 코드의 일치는 조각 완료 시 문서 동기화(`domain/review/CLAUDE.md`)와 사람 리뷰가 맡는다.
