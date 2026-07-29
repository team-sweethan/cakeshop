# 브랜치 현황 (2026-07-29 기준)

`git fetch --all --prune` 후 원격(`origin`) 브랜치 8개를 모두 로컬 추적 브랜치로 생성한 시점의 스냅샷입니다.

## 브랜치별 최신 커밋

| 브랜치 | 최신 커밋일 | 작성자 | 커밋 | 요약 |
| --- | --- | --- | --- | --- |
| `dev` | 2026-07-29 | HyunGyu-Cho | 0345bf8 | docs: README 재배치 및 migration 생성 절차 보강 |
| `dev_common_rules_fix` | 2026-07-29 | HyunGyu-Cho | b0353b9 | docs: README 처음 설치 절차를 자립적으로 재구성 |
| `feature/product-detail-update` | 2026-07-28 | 시은 | 5879429 | Merge pull request #20 from team-sweethan/feature/admin-product |
| `feature/coupon` | 2026-07-28 | leejunghoo | 5abb464 | 관리자 쿠폰 목록/등록/수정/상태변경 작업 (회원 관련 쿠폰은 이후 구현 예정) |
| `feature/order-payment-entity` | 2026-07-28 | juhwanz_w | 69f8cb9 | test: 주문·결제 Mapper 제약 검증 |
| `feature/order-payment-mvp` | 2026-07-28 | juhwanz_w | b0243b0 | fix: MemberDetailSErvice |
| `chore/mac-local-setup` | 2026-07-24 | hxtchhxkxr | 280e9d5 | fix: 매장 관리자 테스트의 FlashMessage 참조 수정 |
| `main` | 2026-07-24 | HyunGyu-Cho | 2ef4477 | skeleton code |

## `dev` 기준 앞/뒤 커밋 수

| 브랜치 | 앞선 커밋 | 뒤처진 커밋 | 상태 |
| --- | ---: | ---: | --- |
| `feature/order-payment-entity` | 11 | 40 | 작업 중 |
| `feature/order-payment-mvp` | 5 | 40 | 작업 중 |
| `feature/product-detail-update` | 0 | 25 | dev에 반영 완료 |
| `feature/coupon` | 0 | 39 | dev에 반영 완료 |
| `dev_common_rules_fix` | 0 | 2 | dev에 반영 완료 |
| `chore/mac-local-setup` | 0 | 57 | dev에 반영 완료 |
| `main` | 0 | 59 | 스켈레톤 시점, 릴리스 없음 |

- "앞선 커밋"이 0이면 해당 브랜치의 모든 커밋이 이미 `dev`에 들어가 있다는 뜻입니다.

## 특이 사항

- `feature/order-payment-entity`와 `feature/order-payment-mvp`는 같은 작성자의 주문·결제 작업이 두 갈래로 나뉜 상태이며, 둘 다 `dev`보다 40커밋 뒤처져 있습니다. 병합 시 충돌 가능성이 있어 사전에 `dev` 기준 rebase 또는 merge가 필요합니다.
- `feature/coupon`은 커밋 메시지상 "회원 관련 쿠폰은 이후 구현 예정"이지만 앞선 커밋이 0이므로, 현재 분량은 `dev`에 병합 완료되었고 후속 작업은 별도 브랜치에서 진행될 것으로 보입니다.
- `main`은 초기 스켈레톤(2ef4477) 이후 갱신되지 않았습니다.

## 이 시점에 수행한 정리 작업

- `origin/feature/admin-product`, `origin/test/codex-auto-review-20260728` — 원격에서 삭제되어 prune으로 제거
- 로컬 `test/codex-auto-review-20260728` (8921dfc) — 원격이 사라져(`gone`) 삭제
- 로컬 `dev`, `main` — 누락된 upstream을 각각 `origin/dev`, `origin/main`으로 설정
- 로컬 `dev_common_rules_fix` — 5d4c730 → b0353b9로 fast-forward
