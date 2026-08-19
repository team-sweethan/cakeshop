# Review 후기 기능 개발 라우터

이 파일은 `review` 후기 기능 작업에 필요한 컨텍스트를 선택하는 얇은 진입점이다. 상세 업무 규칙은
`docs/review/`의 정본에 한 번만 두고, 프로젝트 공통 규칙과 작업·검증 루프는 저장소 루트
`AGENTS.md`를 따른다. 여기에는 review 고유의 경계와 문서 동기화 의무만 둔다.

## 범위

- `src/main/java/com/cakeshop/domain/review/**`
- `src/main/resources/mapper/review/**`
- `src/main/resources/templates/{customer,admin}/review/**`
- `src/test/java/com/cakeshop/domain/review/**`
- `src/main/resources/db/seed/seed-review.sql`
- `src/main/resources/templates/fragments/customer/product-review.html`, `src/main/resources/static/css/review.css`
- 새 Flyway migration

상품·주문·회원·알림 도메인의 파일이 필요하면 `docs/conventions.md` 12절과 담당 경계를 먼저 확인한다.
**담당자가 쓴 코드를 고쳐야 하는 작업만 합의가 선행한다** — 아래 `review 고유 경계`가 갈리는 자리를
적어 둔다.

## 선택적 문서 로딩

Entity·Form·Query·Command·View의 로컬 패키지 경계는 `docs/review/review_conventions.md`를 먼저 따른다.
이 규칙은 review 도메인에만 적용하며 공통 정본을 바꾸지 않는다.

문서는 **파이프라인**으로 나뉘어 있다. `PLAN.md`의 `문서 파이프라인` 절이 정본이고 요약은 이렇다 —
문제 발견은 `reviews/`, 해결 방향은 `decisions/`, 현재 규칙은 `specs/`·`DOMAIN.md`, 완료 이력은 `history/`.

1. `docs/review/PLAN.md`의 작업 방식과 조각 표에서 현재 조각을 확인한다.
2. `docs/review/DOMAIN.md` 0~1절에서 기능 ID와 연결된 `spec`을 찾는다.
3. 해당 `docs/review/specs/*.md` 한 파일과 `DOMAIN.md`의 관련 공통 규칙만 읽는다.
4. 아래 조건에 해당할 때만 추가 문서를 읽는다.

| 조건 | 추가 컨텍스트 |
|---|---|
| 도메인 간 계약·표시명·상품 집계 | `DOMAIN.md` 2.6~2.7절, 4절과 `docs/conventions.md` 12절 |
| 상태·권한·오류 처리 | `DOMAIN.md` 2.1~2.5절의 필요한 절 |
| 화면 변경 | `DOMAIN.md` 3절과 해당 기능 spec |
| 테스트 추가 또는 실패 분석 | `docs/testing.md` 관련 절 |
| 아직 살아 있는 위험의 본문 (`R#`) | `docs/review/reviews/risks.md`의 해당 행 |
| 언제 무엇을 정했는지 | `docs/review/decisions/decision-log.md`의 해당 날짜 행 |
| 결정의 이유가 구현 선택을 바꾸는 경우 | 관련 `docs/review/decisions/ADR-*.md` 한 파일 |
| 완료된 조각의 회귀를 추적해야 하는 경우 | 관련 `docs/review/history/*` 한 파일만 |
| 성능·부하·계측을 다루는 작업 | `docs/review/MONITORING.md` |

`DOMAIN.md`, 모든 spec, reviews, decisions, history를 한꺼번에 읽지 않는다. 현재 기능과 연결된 참조만
따라간다. **`reviews/`·`decisions/`·`history/`는 기본으로 읽지 않는다** — `PLAN.md`에는 그 셋으로 가는
**번호와 한 줄**만 있다.

## review 고유 경계

- 리뷰 Mapper는 `reviews`, `review_replies`, `review_images` 외 도메인 테이블을 JOIN하지 않는다.
  다른 도메인의 값이나 쓰기가 필요하면 그 도메인의 공개 Service 계약을 통한다.
- `DOMAIN.md` 4절의 미정 항목은 정해진 것으로 가정하고 구현하지 않는다.
- 담당 외 도메인의 연동 계약은 **착수 전 합의를 기다리지 않는다.** 담당자가 쓴 코드를 한 줄도
  고치지 않는다면 만들고 PR 리뷰어 지정으로 확인받는다(`DOMAIN.md` 2.7). **내가 만든 계약 파일에
  메서드를 더하는 것도 여기에 든다.** 담당자가 쓴 코드를 고쳐야 하는 경우만 합의가 선행한다.

## 조각 완료 시 문서 동기화

완료 보고 전에 코드와 같은 변경에서 다음을 함께 갱신한다. 대상은 내가 고친 문장이 아니라 **이 세션에서
읽은 문서 전부**다. 내 변경이 낡게 만든 남의 문장이 실제로 빠지는 자리다.

**어디에 적을지는 파이프라인 자리가 정한다.** 같은 내용을 두 자리에 적지 않는다.

- **문제가 나왔으면 `reviews/risks.md`**에 새 `R#` 행으로 적는다. 아직 닫히지 않았거나 알고
  감수하기로 한 것만이다. **리뷰 지적의 반영은 문서로 남기지 않는다** — 규칙은 정본에, 언제 왜
  고쳤는지는 커밋 메시지에 있다(`decisions/decision-log.md` 머리말이 이 선택의 근거를 갖는다).
- **방향을 골랐으면 `decisions/decision-log.md`**에 날짜 한 줄과 `정본` 포인터를 적고, 한 줄로
  재구성이 안 되면 같은 폴더에 ADR을 세워 그 행에서 가리킨다.
- **규칙이 바뀌었으면 `DOMAIN.md` 또는 해당 spec.** 여기가 정본이다.
- **조각이 머지되면 착수 항목·구현 기록·검증 목록을 `docs/review/history/`로 옮긴다.**
  `PLAN.md`에는 상태 한 줄과 기록 파일 링크만 남는다.
- **`PLAN.md`에는 조각 상태와 새 번호만.** 예외는 **진행 중인 조각 하나**다 — 아직 머지되지 않아
  `history/`의 "끝난 조각의 기록이다"가 거짓이 되는 동안만 PLAN에 본문을 둔다. 머지되면 옮긴다.
- **`DOMAIN.md` 1절 기능 목록의 `현재` 열과 3절 화면 표를 실제 코드와 대조한다.** 두 번 빠뜨린
  자리다(`docs/review/history/2026-08-slice-0-schema.md`).
- 끝난 조각의 문서에 남은 미래형 문장을 현재 사실로 고친다. "앞으로 만든다"가 남으면 다음 사람이
  아직 없는 줄 안다.
