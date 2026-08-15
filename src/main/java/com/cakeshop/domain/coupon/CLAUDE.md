# Coupon 기능 개발 라우터

이 파일은 `coupon` 기능 작업에 필요한 컨텍스트를 고르는 진입점이다. 상세 규칙을 복제하지 않고 필요한
정본만 읽도록 안내한다. 프로젝트 공통 규칙은 저장소 루트 `AGENTS.md`가 우선한다.

## 범위

- `src/main/java/com/cakeshop/domain/coupon/**`
- `src/main/resources/mapper/coupon/**`
- `src/main/resources/templates/{admin,customer}/coupon/**`
- `src/main/resources/static/js/coupon-*.js`
- `src/test/java/com/cakeshop/domain/coupon/**`

다른 도메인 파일을 수정해야 하면 데이터 소유 도메인과 공개 Service 계약을 먼저 확인한다. 공유된
versioned Flyway migration은 수정하지 않는다.

## 선택적 문서 로딩

1. 쿠폰 기능을 수정하거나 정책을 판단할 때 `docs/coupon/coupon_conventions.md`에서 요청과 연결된 절만 읽는다.
2. 화면의 기능 범위·현재 동작을 빠르게 확인할 때만 `docs/coupon/README.md`를 읽는다.
3. 아래 조건에 해당할 때만 추가 정본을 읽는다.

| 조건 | 추가 컨텍스트 |
| --- | --- |
| 회원·주문·결제 도메인과 새 계약을 설계 | `docs/conventions.md` 12절 |
| 화면·Thymeleaf·정적 JavaScript 변경 | `docs/frontend-template-format.md` |
| 테스트 추가·변경 | `docs/testing.md` |
| DB·Flyway·seed 변경 | `docs/conventions.md` 7~8절, `docs/flyway_make_sample.md` |
| 아직 합의되지 않은 연동 정책 | `docs/team-plan.md` |

`coupon_conventions.md` 전체나 관계없는 공통 문서를 한꺼번에 읽지 않는다. 현재 변경과 연결된 절만 읽는다.

## 항상 지킬 경계

- 쿠폰 도메인은 다른 도메인의 Mapper·Entity·테이블을 직접 사용하지 않는다.
- 회원·주문·결제 데이터는 소유 도메인의 공개 Service와 최소 DTO를 통해 받는다.
- 상태 전이와 발급·예약·사용 확정은 현재 상태를 검증하는 공개 Service의 단일 트랜잭션에서 처리한다.
- 정책이 코드와 충돌하거나 문서에 없는 결정이 필요하면 구현으로 추정하지 않고 충돌 지점을 먼저 알린다.
