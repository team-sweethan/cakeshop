# 쿠폰 발급 정책

## 대상별 기본 발급 방식

| 대상 | 발급 방식 | 발급 주체 |
| --- | --- | --- |
| `ALL_MEMBERS` | 쿠폰 등록 직후 활성 기존 회원에게 일괄 발급 | coupon 등록 Service |
| `NEW_MEMBERS` | 회원가입 완료 직후 발급 | member Service → coupon 공개 Service |
| `FIRST_ORDER` | 쿠폰 등록 직후 주문 이력이 없는 기존 회원에게 일괄 발급 | coupon 등록 Service + order 공개 조회 Service |
| `BIRTHDAY` | 관리자가 매월 등록한 생일 쿠폰을 해당 월 생일 회원에게 스케줄러로 발급 | coupon scheduler + member 공개 조회 Service |
| `SPECIFIC_MEMBERS` | 관리자 상세 화면에서 선택 회원에게 수동 발급 | coupon 관리자 Service |

## 공통 규칙

- 발급 대상은 쿠폰 등록 후 변경하지 않는다.
- 같은 `coupon_id`, `member_id` 조합은 한 번만 발급한다.
- 총 발급 수량을 초과하면 신규 발급을 거부한다.
- 발급·수량 증감·발급 이력 생성은 coupon Service의 단일 트랜잭션에서 처리한다.
- 회원 정보는 member 공개 조회 Service, 주문 이력은 order 공개 조회 Service를 통해서만 가져온다.
- `member_coupons` 생성·취소·발급 상태 판단은 coupon 도메인이 소유한다.

## 현재 구현 범위

- 관리자 쿠폰 등록·목록·상세·수정과 특정 회원 수동 발급 화면을 우선 제공한다.
- `FIRST_ORDER` 주문 이력 조회와 생일 자동 발급은 공개 Service 계약을 통해 구현한다.
- `NEW_MEMBERS`는 쿠폰 도메인의 `issueNewMemberCoupons(memberId)` 진입점까지 제공하며,
  회원가입 완료 흐름에서 호출하는 연결은 후속 작업이다.

## 도메인 연동 계약

- member는 쿠폰 대상 회원 조회와 신규 회원 가입 완료 시점만 제공한다.
- order는 주문 이력이 없는 회원 식별만 제공한다.
- coupon은 대상 ID를 받아 발급 가능 상태·중복·수량을 검증한 뒤 발급 이력을 저장한다.
