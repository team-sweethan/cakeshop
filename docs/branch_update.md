# 브랜치 현황 (2026-07-29 기준)

이 문서는 매일 00시(KST)에 자동으로 다시 만들어진다. 손으로 고쳐도 다음 실행 때 사라지므로, 내용을 바꾸려면 `.github/scripts/branch-status.sh`를 고친다.

같은 내용을 그림으로 본 것이 [branch_status.html](branch_status.html) 이다. 브라우저로 열면 된다.

## 브랜치별 최신 커밋

| 브랜치 | 최신 커밋일 | 작성자 | 커밋 | 요약 |
| --- | --- | --- | --- | --- |
| `feature/order-payment-entity` | 2026-07-29 | juhwanz | 83c6c8c | test: 주문·결제 통합 테스트를 최신 DB 설정에 맞춤 |
| `dev` | 2026-07-29 | 시은 | 7a733df | Merge pull request #38 from team-sweethan/feature/product-sales-query |
| `feature/product-option-management` | 2026-07-29 | 시은 | 7a733df | Merge pull request #38 from team-sweethan/feature/product-sales-query |
| `feature/member-account-management` | 2026-07-29 | sm | 1d5bfa6 | fix: 회원 계정 관리 리뷰 사항 반영 |
| `chore/branch-status-workflow` | 2026-07-29 | HyunGyu-Cho | 90a0de2 | fix: 필드 구분자를 NUL 로 바꾸고 게시 후 오차 설명을 정정 |
| `feature/order-payment-mvp` | 2026-07-29 | juhwanz | 7616fa0 | merge: dev 브랜치 최신화 반영 |
| `dev_common_rules_fix` | 2026-07-29 | HyunGyu-Cho | b0353b9 | docs: README 처음 설치 절차를 자립적으로 재구성 |
| `feature/coupon` | 2026-07-28 | leejunghoo | 5abb464 | 관리자 쿠폰 목록/등록/수정/상태변경 작업 (회원 관련된 쿠폰 내용은 이후 구현 예정) |
| `chore/mac-local-setup` | 2026-07-24 | hxtchhxkxr | 280e9d5 | fix: 매장 관리자 테스트의 FlashMessage 참조 수정 |
| `main` | 2026-07-24 | HyunGyu-Cho | 2ef4477 | skeleton code |

## `dev` 기준 앞/뒤 커밋 수

| 브랜치 | 앞선 커밋 | 뒤처진 커밋 | 상태 |
| --- | ---: | ---: | --- |
| `feature/order-payment-entity` | 15 | 4 | 작업 중 |
| `feature/order-payment-mvp` | 6 | 4 | 작업 중 |
| `chore/branch-status-workflow` | 5 | 4 | 작업 중 |
| `feature/member-account-management` | 3 | 2 | 작업 중 |
| `main` | 0 | 66 | 릴리스 브랜치 |
| `chore/mac-local-setup` | 0 | 64 | `dev`에 반영 완료 |
| `feature/coupon` | 0 | 46 | `dev`에 반영 완료 |
| `dev_common_rules_fix` | 0 | 9 | `dev`에 반영 완료 |
| `feature/product-option-management` | 0 | 0 | `dev`와 동일 |

- "앞선 커밋"이 0이면 그 브랜치의 모든 커밋이 이미 `dev`에 들어가 있다는 뜻이다.
- `main`의 "뒤처진 커밋"은 아직 릴리스되지 않은 `dev`의 작업량이다.
- 위 수치는 기준 커밋 `7a733df` 시점의 스냅샷이다. 실제 값은 이 문서가 머지되는 순간 이미 더 커져 있다. 이 문서 자신의 커밋과 merge commit 이 `dev`에 얹히고(1~2), 그 사이 다른 PR 이 먼저 머지됐다면 그 커밋 수만큼 더 더해진다. 정확한 값은 `git rev-list --left-right --count origin/dev...origin/<브랜치>`로 확인한다.
