# Community 기능 개발 라우터

이 파일은 `community` 기능 작업에 필요한 컨텍스트를 선택하는 얇은 진입점이다. 기능 규칙을 복제하지 않고
정본의 필요한 절만 읽도록 안내한다. 프로젝트 공통 규칙은 저장소 루트 `AGENTS.md`가 우선한다.

## 범위

- `src/main/java/com/cakeshop/domain/community/**`
- `src/main/resources/mapper/community/**`
- `src/main/resources/templates/{customer,admin}/community/**`
- `src/test/java/com/cakeshop/domain/community/**`
- `src/main/resources/db/seed/seed-community.sql`

범위 밖 파일을 수정해야 하면 도메인 경계와 담당을 먼저 확인한다. 공유 Flyway migration은 수정하지 않는다.

## 선택적 문서 로딩

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
| 결정 배경이 실제로 필요한 경우 | `PLAN.md`의 해당 위험·결정 로그만 |

`DOMAIN.md` 전체, 모든 spec, `PLAN.md` 전체를 한꺼번에 읽지 않는다. 현재 기능과 연결된 참조만 따라간다.

**옛 `DOMAIN.md` 6.x 절 번호를 만나면** — 공유 migration·seed 주석과 `PLAN.md`의 과거 기록에 남아 있다 —
`DOMAIN.md` 6절의 매핑 표가 지금 자리를 가리킨다. 그 번호는 재사용하지 않는다.

## 항상 지킬 경계

- 커뮤니티 SQL에서 `members`를 조회하거나 JOIN하지 않는다. 회원 정보는 `MemberCommunityQueryService`의
  배치 계약으로 받고 Service에서 조립한다.
- 다른 도메인의 Mapper·Entity를 직접 사용하지 않는다.
- 정본의 보류 항목이나 검사로 보증되지 않는 경계를 초록 테스트만으로 임의 확정하지 않는다.
- 정본을 바꾸는 결정이면 관련 문서와 `PLAN.md` 결정 로그를 코드와 함께 갱신한다.

## 개발 루프와 완료 조건

1. 현재 조각과 수용 조건을 확인한다.
2. 조각 범위 안에서 가장 작은 변경과 위험을 직접 검증할 테스트를 함께 만든다.
3. 관련 테스트를 먼저 실행하고, 완료 전 전체 테스트를 실행한다.
4. 변경 파일, 실행한 검증, 자동 검사가 보증하지 못한 경계와 남은 위험을 보고한다.

## 조각 완료 시 문서 동기화

완료 보고 전에 코드와 같은 변경에서 다음을 함께 갱신한다. 대상은 내가 고친 문장이 아니라 **이 세션에서
읽은 문서 전부**다. 내 변경이 낡게 만든 남의 문장이 실제로 빠지는 자리다.

- 업무 결정이 바뀌었으면 해당 `specs/*.md` 또는 `DOMAIN.md`의 공통 절을 갱신한다.
- `PLAN.md`의 조각 상태와, 결정을 내렸다면 결정 로그를 갱신한다.
- **`DOMAIN.md` 0.1절 기능 목록의 `현재` 열을 실제 코드와 대조한다.** review 도메인에서 두 번 빠뜨린
  자리다(`docs/review/history/2026-08-slice-0-schema.md`).
- 새 하네스를 세웠으면 근거는 해당 spec의 `검증` 절에, 번호는 `PLAN.md` 인덱스에 넣는다. **두 곳에
  같은 근거를 적지 않는다.**
- 끝난 조각의 문서에 남은 미래형 문장을 현재 사실로 고친다. "앞으로 만든다"가 남으면 다음 사람이
  아직 없는 줄 안다.

규칙이 부족하거나 코드와 충돌하면 코드로 우회하지 말고 정본과 충돌 지점을 먼저 알린다.
