# 브랜치 현황 (2026-07-29 기준)

이 문서는 매일 00시(KST)에 자동으로 다시 만들어진다. 손으로 고쳐도 다음 실행 때 사라지므로, 내용을 바꾸려면 `.github/scripts/branch-status.sh`를 고친다.

## 브랜치별 최신 커밋

| 브랜치 | 최신 커밋일 | 작성자 | 커밋 | 요약 |
| --- | --- | --- | --- | --- |
| `chore/branch-status-workflow` | 2026-07-29 | HyunGyu-Cho | 849d47b | fix: 브랜치 현황 생성기의 표 깨짐과 자기 브랜치 보고 수정 |
| `dev` | 2026-07-29 | HyunGyu-Cho | 237b0d2 | docs: README에 migration 작성 예시 문서 링크 추가 |
| `dev_common_rules_fix` | 2026-07-29 | HyunGyu-Cho | b0353b9 | docs: README 처음 설치 절차를 자립적으로 재구성 |
| `feature/product-detail-update` | 2026-07-28 | 시은 | 5879429 | Merge pull request #20 from team-sweethan/feature/admin-product |
| `feature/coupon` | 2026-07-28 | leejunghoo | 5abb464 | 관리자 쿠폰 목록/등록/수정/상태변경 작업 (회원 관련된 쿠폰 내용은 이후 구현 예정) |
| `feature/order-payment-entity` | 2026-07-28 | juhwanz_w | 69f8cb9 | test: 주문·결제 Mapper 제약 검증 |
| `feature/order-payment-mvp` | 2026-07-28 | juhwanz_w | b0243b0 | fix: MemberDetailSErvice |
| `chore/mac-local-setup` | 2026-07-24 | hxtchhxkxr | 280e9d5 | fix: 매장 관리자 테스트의 FlashMessage 참조 수정 |
| `main` | 2026-07-24 | HyunGyu-Cho | 2ef4477 | skeleton code |

## `dev` 기준 앞/뒤 커밋 수

| 브랜치 | 앞선 커밋 | 뒤처진 커밋 | 상태 |
| --- | ---: | ---: | --- |
| `feature/order-payment-entity` | 11 | 43 | 작업 중 |
| `feature/order-payment-mvp` | 5 | 43 | 작업 중 |
| `chore/branch-status-workflow` | 3 | 0 | 작업 중 |
| `main` | 0 | 62 | 릴리스 브랜치 |
| `chore/mac-local-setup` | 0 | 60 | `dev`에 반영 완료 |
| `feature/coupon` | 0 | 42 | `dev`에 반영 완료 |
| `feature/product-detail-update` | 0 | 28 | `dev`에 반영 완료 |
| `dev_common_rules_fix` | 0 | 5 | `dev`에 반영 완료 |

- "앞선 커밋"이 0이면 그 브랜치의 모든 커밋이 이미 `dev`에 들어가 있다는 뜻이다.
- `main`의 "뒤처진 커밋"은 아직 릴리스되지 않은 `dev`의 작업량이다.
- 위 수치는 기준 커밋 `237b0d2` 시점의 스냅샷이다. 이 문서를 머지하면 그 커밋과 merge commit 이 `dev`에 얹히므로, 머지 직후의 "뒤처진 커밋" 실제 값은 표보다 1~2 크다.
