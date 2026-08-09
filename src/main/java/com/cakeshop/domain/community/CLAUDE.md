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

1. `docs/community/PLAN.md`의 작업 방식과 조각 표에서 현재 작업 위치를 확인한다.
2. `docs/community/DOMAIN.md` 목차에서 요청과 연결된 기능 절만 읽는다.
3. 아래 조건에 해당할 때만 추가 문서를 읽는다.

| 조건 | 추가 컨텍스트 |
|---|---|
| 화면·문구·Thymeleaf 변경 | `docs/community/DOMAIN.md`의 해당 기능 절과 `docs/frontend-template-format.md` |
| 회원 정보·탈퇴 회원·도메인 경계 | `docs/community/DOMAIN.md` 8절, `docs/conventions.md` 12절 |
| 조회수·잠금·동시성 | `docs/community/DOMAIN.md` 6.2절과 `PLAN.md`의 관련 하네스·위험 |
| 인기글 | `docs/community/DOMAIN.md` 6.9절과 현재 관련 조각 |
| 테스트 추가·변경 | `docs/testing.md` 관련 절 |
| 결정 배경이 실제로 필요한 경우 | `PLAN.md`의 해당 위험·결정 로그만 |

`DOMAIN.md`, `PLAN.md`, 화면 명세 전체를 관성적으로 모두 읽지 않는다. 구현 범위가 넓어질 때 표에 따라
컨텍스트를 추가한다.

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
4. 화면 변경은 해당 화면 명세와 렌더링 검증을 함께 갱신한다.
5. 변경 파일, 실행한 검증, 자동 검사가 보증하지 못한 경계와 남은 위험을 보고한다.

규칙이 부족하거나 코드와 충돌하면 코드로 우회하지 말고 정본과 충돌 지점을 먼저 알린다.
