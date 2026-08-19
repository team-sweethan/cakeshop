# Community 기능 개발 라우터

이 파일은 `community` 기능 작업에 필요한 컨텍스트를 선택하는 얇은 진입점이다. 기능 규칙을 복제하지 않고
정본의 필요한 절만 읽도록 안내한다. 프로젝트 공통 규칙은 저장소 루트 `AGENTS.md`가 우선한다.

## 범위

- `src/main/java/com/cakeshop/domain/community/**`
- `src/main/resources/mapper/community/**`
- `src/main/resources/templates/{customer,admin}/community/**`
- `src/test/java/com/cakeshop/domain/community/**`
- `src/main/resources/db/seed/seed-community.sql`
- `src/main/resources/static/css/community.css` (커뮤니티 상세만 참조)
- `src/main/resources/static/js/home-notice-rotation.js` (조각 19가 만들었다. **부르는 곳은 아래의 공유 화면 하나뿐이다**)

**공유 파일이라 범위가 아니지만 커뮤니티 조각이 실제로 고치는 자리**: 메인 화면(`templates/home/**`)의 공지
영역과 고객 GNB(`templates/fragments/customer/gnb.html`)의 `공지사항` 진입점. 조각 14c·18·19가 여기를 건드렸고
H45·H51·H58이 그 결과를 고정한다. `home`은 공통 협의 도메인이라(`docs/community/specs/community-notice.md`)
**`HomeService`·`HomeController`·`main.html` 변경은 PR에서 확인을 받는다.**

범위 밖 파일을 수정해야 하면 도메인 경계와 담당을 먼저 확인한다. 공유 Flyway migration은 수정하지 않는다.

## 선택적 문서 로딩

문서는 **파이프라인**으로 나뉘어 있다. `PLAN.md`의 `문서 파이프라인` 절이 정본이고 요약은 이렇다 —
문제 발견은 `reviews/`, 해결 방향은 `decisions/`, 현재 규칙은 `specs/`·`DOMAIN.md`, 완료 이력은 `history/`.

1. `docs/community/PLAN.md`의 작업 방식과 조각 표에서 현재 조각을 확인한다.
2. `docs/community/DOMAIN.md` 0.1절 기능 목록에서 요청과 연결된 기능 ID와 그 `spec`을 찾는다.
3. 해당 `docs/community/specs/*.md` 한 파일과 `DOMAIN.md`의 관련 공통 절만 읽는다.
4. 아래 조건에 해당할 때만 추가 문서를 읽는다.

| 조건 | 추가 컨텍스트 |
|---|---|
| Entity·Form·Query·Command·View 구조 변경 | `docs/community/community_conventions.md` |
| 상태·전이·권한·입력 검증 | `DOMAIN.md` 4·5·7절의 필요한 절 |
| 회원 정보·탈퇴 회원·도메인 경계 | `DOMAIN.md` 8·11절, `docs/conventions.md` 12절 |
| 화면·문구·Thymeleaf 변경 | 해당 spec과 `docs/frontend-template-format.md` |
| 어떤 검사가 무엇을 지키나 | 해당 spec의 `검증` 절. 번호만 찾을 때는 `PLAN.md` 하네스 인덱스 |
| 테스트 추가·변경 | `docs/testing.md` 관련 절 |
| 아직 살아 있는 위험의 본문 (`R#`) | `docs/community/reviews/risks.md`의 해당 행 |
| 같은 지적이 다시 올라왔을 때 | `docs/community/reviews/findings.md`의 해당 행 |
| 결정 배경이 실제로 필요한 경우 | `docs/community/decisions/decision-log-1st.md`(1차)·`-2nd.md`(2차)의 해당 날짜 행 |
| 그 한 줄로 재구성이 안 되는 결정 | `docs/community/decisions/`의 해당 ADR 하나 |
| 끝난 조각이 **왜** 그렇게 됐는지 | `docs/community/history/`의 해당 조각 파일 하나 |
| 성능·부하·계측을 다루는 작업 | `docs/community/MONITORING.md` |

`DOMAIN.md` 전체, 모든 spec, `PLAN.md` 전체를 한꺼번에 읽지 않는다. 현재 기능과 연결된 참조만 따라간다.
**`reviews/`·`decisions/`·`history/`는 기본으로 읽지 않는다** — 발견된 문제, 결정 배경, 끝난 조각의
기록이라 지금 하는 일을 구속하지 않는다. 위 표의 조건에 걸릴 때만 해당 파일 **하나**를 연다.
`PLAN.md`에는 그 셋으로 가는 **번호와 한 줄**만 있다.

**옛 `DOMAIN.md` 6.x 절 번호를 만나면** — 공유 migration·seed 주석과 `PLAN.md`의 과거 기록에 남아 있다 —
`DOMAIN.md` 6절의 매핑 표가 지금 자리를 가리킨다. 그 번호는 재사용하지 않는다.

## 항상 지킬 경계

- 커뮤니티 SQL에서 `members`를 조회하거나 JOIN하지 않는다. 회원 정보는 `MemberCommunityQueryService`의
  배치 계약으로 받고 Service에서 조립한다.
- 다른 도메인의 Mapper·Entity를 직접 사용하지 않는다.
- 정본의 보류 항목이나 검사로 보증되지 않는 경계를 초록 테스트만으로 임의 확정하지 않는다.
- 정본을 바꾸는 결정이면 관련 정본 문서와 `docs/community/decisions/`의 결정 기록을 코드와 함께 갱신한다.

## 개발 루프와 완료 조건

1. 현재 조각과 수용 조건을 확인한다.
2. 조각 범위 안에서 가장 작은 변경과 위험을 직접 검증할 테스트를 함께 만든다.
3. 관련 테스트를 먼저 실행하고, 완료 전 전체 테스트를 실행한다.
4. 변경 파일, 실행한 검증, 자동 검사가 보증하지 못한 경계와 남은 위험을 보고한다.

## 조각 완료 시 문서 동기화

완료 보고 전에 코드와 같은 변경에서 다음을 함께 갱신한다. 대상은 내가 고친 문장이 아니라 **이 세션에서
읽은 문서 전부**다. 내 변경이 낡게 만든 남의 문장이 실제로 빠지는 자리다.

**어디에 적을지는 파이프라인 자리가 정한다.** 같은 내용을 두 자리에 적지 않는다.

- **문제가 나왔으면 `reviews/`.** 아직 닫히지 않았거나 알고 감수하기로 한 것은 `reviews/risks.md`에
  새 `R#` 행으로, PR 리뷰·자체 점검의 지적과 그 처리는 `reviews/findings.md`에 날짜 행으로 적는다.
  **받아들이지 않은 지적일수록 근거를 적는다** — 같은 지적이 다시 올라온다.
- **방향을 골랐으면 `decisions/`.** 날짜순 한 줄은 `decisions/decision-log-2nd.md`에, 한 줄로
  재구성이 안 되는 것은 같은 폴더에 ADR을 세우고 그 행에서 가리킨다.
- **규칙이 바뀌었으면 `specs/*.md` 또는 `DOMAIN.md`의 공통 절.** 여기가 정본이다.
- **조각이 머지되면 착수 항목·구현 기록·검증 목록을 `docs/community/history/`로 옮긴다.**
  `PLAN.md`에는 상태 한 줄과 기록 파일 링크만 남는다. 옮기지 않으면 `PLAN.md`가 다시 부푼다 —
  2026-08-18에 764줄에서, 2026-08-20에 173KB에서 잘라낸 자리다.
  **위험·결정·하네스 번호는 옮기지 않는다**(현재 구속이고, 코드가 번호로 가리킨다).
  **그 블록들을 애초에 만들지 않은 조각은 옮길 것이 없다** — 그때는 조각 순서 줄 하나가 이미
  "상태 한 줄"이고 기록의 전부이므로, 빈 `history/` 파일을 만들지 않는다. 조각 8·12·13·15~21이
  그렇고 `PLAN.md`의 조각 기록 절이 그 사실을 적고 있다. **"완료면 `history/` 파일이 있어야 한다"로
  읽지 않는다** — 2026-08-20에 PR #341 Codex 리뷰가 실제로 그렇게 읽었다.
- **`PLAN.md`에는 조각 상태와 새 번호만.** 본문은 여기 쌓지 않는다.
- **`DOMAIN.md` 0.1절 기능 목록의 `현재` 열을 실제 코드와 대조한다.** review 도메인에서 두 번 빠뜨린
  자리다(`docs/review/history/2026-08-slice-0-schema.md`).
- 새 하네스를 세웠으면 근거는 해당 spec의 `검증` 절에, 번호는 `PLAN.md` 인덱스에 넣는다. **두 곳에
  같은 근거를 적지 않는다.**
- 끝난 조각의 문서에 남은 미래형 문장을 현재 사실로 고친다. "앞으로 만든다"가 남으면 다음 사람이
  아직 없는 줄 안다.

규칙이 부족하거나 코드와 충돌하면 코드로 우회하지 말고 정본과 충돌 지점을 먼저 알린다.
