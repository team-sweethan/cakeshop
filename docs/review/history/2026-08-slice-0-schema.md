# 조각 0 — 준비 (#108, PR #125 머지 완료)

> **끝난 조각의 기록이다. 지금 구속하지 않는다.**
> 이 조각이 만든 규칙 자체는 `../DOMAIN.md` 2.1·2.2와 `../specs/review-schema.md`가 정본이다.
> 머지: PR #125, 커밋 `5f6f7e7`. migration `V20260806_075114__add_review_status_and_rating_constraints.sql`.

착수 시점에 적어 두었던 항목은 아래와 같다.

- `ReviewStatus { PUBLISHED, DELETED, BLOCKED }` + `canTransitionTo` (`PostStatus` 선례를 그대로 따름, `null` 방어 포함)
- 새 migration: `reviews.status` 기본값을 `'VISIBLE'` → `'PUBLISHED'`로 바꾸고 `CHECK (status IN ('PUBLISHED','DELETED','BLOCKED'))` 추가
  - **`CHECK`를 걸기 전에 기존 `'VISIBLE'` 행을 `'PUBLISHED'`로 변환한다.** 남아 있으면 제약 추가가 배포 중 실패한다. 당시 `reviews`는 비어 있고 INSERT 경로도 없었지만, 한 줄로 막을 수 있는 것을 환경 상태에 맡기지 않았다. 선례: `V20260730_123931__apply_product_preparation_policy.sql`(보정 UPDATE 후 CHECK)
- 새 migration: 평점 4종에 `CHECK (rating BETWEEN 1 AND 5)`
  - **`status`와 달리 사전 보정 UPDATE를 두지 않았다.** 근거는 `../DOMAIN.md` 2.2
- migration 파일명은 직접 짓지 않고 `gradlew newMigration -Pdesc=<snake_case>`로 생성
- **`docs/status-design.md`의 `reviews.status` 행을 확정으로 갱신한다.** 그 문서가 상태값 인벤토리의 정본이고 당시 `VISIBLE / HIDDEN ?` · `☐ 열림`으로 남아 있었다. 154절이 "☐ 항목을 확정하면 인벤토리 행을 갱신하고 **enum + DDL을 함께 커밋한다**"고 못 박고 있다

**검증**: 전이 규칙 단위 테스트(허용/금지 각 케이스), Testcontainers로 `CHECK`가 잘못된 상태값·평점을 거부하는지, 기본값이 `PUBLISHED`인지.

## 여기서 배운 것

**머지하면서 인벤토리를 함께 안 고쳤다.** `DOMAIN.md` 1절 기능 목록의 E1·E2·E3 `현재` 열이 조각 0 머지 뒤에도 `없음`·`빈 클래스`로 남아 있었고, 문서 재배치 때 실제 코드와 대조하고서야 드러났다. 커뮤니티에서도 같은 자리가 있었다(`docs/community/PLAN.md` R32).

**규칙으로는 안 되는 자리다.** "조각마다 문서를 함께 고친다"는 규칙은 이미 있었고 지켜지지 않았다.

**그런데 이 자리를 지금 기계로 막는 것은 없다.** H3(인벤토리 3자 일치)는 기능 ID 집합과 조각 번호만 본다 — `현재` 열에 무엇이 적혀 있든 통과하므로 여기서 벌어진 드리프트를 그대로 다시 허용한다. `ReviewDocTests` H3의 "보증하지 않는 것"에 같은 말이 적혀 있는데도 이 줄이 한때 "H3가 기계로 막는다"고 적고 있었다(PR #154 Codex 리뷰). **없는 안전망을 있다고 적는 것이 없는 것보다 나쁘다** — 다음 사람이 그것을 믿고 안 본다. `현재` 열을 실제 코드와 대조하는 검사는 코드가 생기는 **조각 3**에 붙는다(`../PLAN.md` 조각 3). 그때까지는 사람이 보는 수밖에 없다.
