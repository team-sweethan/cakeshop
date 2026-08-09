# Review 후기 기능 개발 라우터

이 파일은 `review` 후기 기능 작업에 필요한 컨텍스트를 선택하는 얇은 진입점이다. 상세 업무 규칙은
`docs/review/`의 정본에 한 번만 둔다. 프로젝트 공통 규칙은 저장소 루트 `AGENTS.md`가 우선한다.

## 범위

- `src/main/java/com/cakeshop/domain/review/**`
- `src/main/resources/mapper/review/**`
- `src/main/resources/templates/{customer,admin}/review/**`
- `src/test/java/com/cakeshop/domain/review/**`
- 새 Flyway migration

상품·주문·회원·알림 도메인 파일이 필요하면 `docs/conventions.md` 12절과 담당 경계를 먼저 확인한다.
담당자의 기존 파일이나 공개 계약을 바꾸는 작업은 합의 없이 확장하지 않는다.

## 선택적 문서 로딩

1. `docs/review/PLAN.md`의 작업 방식과 조각 표에서 현재 조각을 확인한다.
2. `docs/review/DOMAIN.md` 0~1절에서 기능 ID와 연결된 `spec`을 찾는다.
3. 해당 `docs/review/specs/*.md` 한 파일과 `DOMAIN.md`의 관련 공통 규칙만 읽는다.
4. 아래 조건에 해당할 때만 추가 문서를 읽는다.

| 조건 | 추가 컨텍스트 |
|---|---|
| 도메인 간 계약·표시명·상품 집계 | `DOMAIN.md` 2.6~2.7절, 4절과 `docs/conventions.md` 12절 |
| 상태·권한·오류 처리 | `DOMAIN.md` 2.1~2.5절의 필요한 절 |
| 화면 변경 | `DOMAIN.md` 3절과 해당 기능 spec |
| 테스트·하네스 추가 또는 실패 분석 | `docs/review/HARNESS.md`와 `docs/testing.md` 관련 절 |
| 결정의 이유가 구현 선택을 바꾸는 경우 | 관련 `docs/review/decisions/*.md` 한 파일 |
| 완료된 조각의 회귀를 추적해야 하는 경우 | 관련 `docs/review/history/*`만 |

`DOMAIN.md`, 모든 spec, decisions, history를 한꺼번에 읽지 않는다. 현재 기능과 연결된 참조만 따라간다.

## 항상 지킬 경계

- 리뷰 Mapper는 `reviews`, `review_replies`, `review_images` 외 도메인 테이블을 JOIN하지 않는다.
- 다른 도메인의 값과 쓰기는 그 도메인의 공개 Query/Command Service 계약을 통한다.
- `DOMAIN.md` 4절의 미정 항목은 정해진 것으로 가정하지 않는다.
- 공유되거나 머지된 Flyway migration은 수정하지 않고 `newMigration`으로 새 파일을 만든다.
- 업무 결정이 바뀌면 `DOMAIN.md` 또는 해당 spec과 `PLAN.md` 결정 로그를 코드와 함께 갱신한다.

## 개발 루프와 완료 조건

1. 현재 조각, 선행 계약, 수용 조건과 금지 범위를 확인한다.
2. 조각 범위 안의 가장 작은 구현과 위험을 직접 검증할 테스트를 함께 만든다.
3. 관련 테스트를 먼저 실행하고, 트랜잭션·동시성·DB 위험에 맞춰 검증을 확장한다.
4. 완료 전 전체 테스트를 실행한다.
5. 추가한 하네스는 `docs/review/HARNESS.md`에 등록하고 `PLAN.md` 조각 상태를 갱신한다.
6. 변경 파일, 실행한 검증, 자동 검사가 보증하지 못한 경계와 남은 위험을 보고한다.

정본의 결정이 부족하거나 코드와 충돌하면 코드로 우회하지 말고 정본과 충돌 지점을 먼저 알린다.
