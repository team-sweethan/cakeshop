# Coupon 도메인 정책과 흐름

> 이 문서는 쿠폰 도메인의 현재 업무 규칙과 구현 범위를 정리한다.
> 프로젝트 공통 규칙은 `AGENTS.md`, 코드·패키지 규칙은 `docs/conventions.md`를 따른다.

## 1. 도메인 책임

쿠폰 도메인은 관리자 쿠폰 관리, 발급 대상 정책, `member_coupons` 발급 이력, 발급 수량, 쿠폰 사용 가능 상태를 책임진다.

- 관리자 화면: 등록, 목록, 상세, 수정, 발급 중지·재개, 특정 회원 수동 발급·취소
- 고객 쿠폰함: 현재는 `/mypage/coupons` 목업 화면만 제공한다.
- 주문·결제에서의 쿠폰 적용, 고객 보유 쿠폰 조회·사용 확정은 후속 기능이다.

의존 방향은 `Controller -> Service -> Mapper -> DB`다. 쿠폰 도메인은 회원·주문 Mapper 또는 Entity를 직접 사용하지 않고, 각 도메인의 공개 조회 Service를 통해 필요한 데이터만 받는다.

## 2. 상태 모델

### 2.1 쿠폰 DB 상태

`coupons.status`에는 관리자가 제어하는 상태만 저장한다.

| 값 | 의미 |
| --- | --- |
| `ACTIVE` | 관리자가 발급을 허용함 |
| `INACTIVE` | 관리자가 발급을 중지함 |

### 2.2 관리자 화면 표시 상태

`CouponDisplayStatus`는 DB에 저장하지 않고, 관리 상태·기간·수량으로 계산한다.

| 값 | 계산 기준 |
| --- | --- |
| `SCHEDULED` | `starts_at` 이전이며 발급 허용 상태 |
| `ACTIVE` | 발급 기간 중이고 발급 허용 상태 |
| `INACTIVE` | 기간과 관계없이 관리자가 발급 중지 |
| `EXHAUSTED` | 특정 회원 쿠폰의 발급 수량이 모두 소진됨 |
| `ENDED` | `expires_at`이 현재 시각과 같거나 이전 |

자동 발급 대상은 `total_quantity`가 `NULL`이므로 `EXHAUSTED`가 될 수 없다.

### 2.3 회원 쿠폰 상태

`member_coupons.status`는 고객이 보유한 개별 쿠폰의 상태다.

| 값 | 화면 표시 |
| --- | --- |
| `AVAILABLE` | 사용 가능 |
| `USED` | 사용 완료 |
| `ENDED` | 만료 |

## 3. 발급 대상과 수량 정책

| 대상 | `target_type` | 발급 수량 | 기본 발급 방식 |
| --- | --- | --- | --- |
| 전체 회원 | `ALL_MEMBERS` | `NULL` (제한 없음) | 등록 직후 기존 활성 회원에게 일괄 발급 |
| 신규 회원 | `NEW_MEMBERS` | `NULL` (제한 없음) | 유효기간 중 회원가입 완료 시 발급 |
| 첫 주문 회원 | `FIRST_ORDER` | `NULL` (제한 없음) | 현재는 등록 시점의 미주문 기존 회원에게 일괄 발급 |
| 생일 회원 | `BIRTHDAY` | `NULL` (제한 없음) | 매시 정각 스케줄러가 해당 월 생일 회원에게 발급 |
| 특정 회원 | `SPECIFIC_MEMBERS` | 양의 정수 필수 | 상세 화면에서 관리자가 선택해 수동 발급 |

### 3.1 수량 제약

- `SPECIFIC_MEMBERS`만 `total_quantity`를 입력한다.
- 나머지 대상은 `total_quantity = NULL`이며, 발급 수는 `issued_quantity`로 누적만 한다.
- DB 제약 `chk_coupons_target_quantity`가 특정 회원의 수량 누락 및 자동 대상의 수량 입력을 방지한다.
- 특정 회원 수동 발급은 발급 수량이 총 수량에 도달하면 거부한다.
- 화면의 `발급 수량`은 자동 대상에서 `발급 수 / 제한 없음`으로 표시한다.

### 3.2 중복 발급과 동시성

- 같은 `(coupon_id, member_id)` 조합은 이미 발급된 경우 다시 생성하지 않는다.
- 발급 이력 생성, 발급 수량 증가, 수량 초과 검증은 하나의 공개 Service 트랜잭션에서 처리한다.
- 발급 처리 시 쿠폰 행을 잠그고 현재 관리 상태·만료 여부·수량을 다시 확인한다.

## 4. 관리자 흐름

### 4.1 등록

1. 관리자가 할인 정책, 사용 기간, 발급 대상을 입력한다.
2. 비율 할인에는 최대 할인 금액이 필수이며, 할인율은 100 이하로 제한한다.
3. 특정 회원을 선택한 경우에만 총 발급 수량을 입력한다.
4. 등록 성공 뒤 `ALL_MEMBERS`, `FIRST_ORDER`는 시작 시각과 관계없이 기존 대상에게 발급 이력을 즉시 생성한다. 실제 쿠폰 사용 가능 여부는 `startsAt` 이후인지로 판단한다.
5. `NEW_MEMBERS`, `BIRTHDAY`, `SPECIFIC_MEMBERS`는 등록 시점에 일괄 발급하지 않는다.

### 4.2 상세와 수동 발급

- 상세 화면은 종료 쿠폰도 조회할 수 있다.
- 발급 회원 목록은 회원 ID, 이름, 이메일, 마스킹한 휴대폰 번호, 생일, 사용 상태, 사용 일시를 보여준다.
- 특정 회원 쿠폰만 회원 검색·발급·발급 취소를 제공한다.
- 특정 회원 수동 발급은 쿠폰 시작 시각 이후, 발급 허용 상태이며 만료 전인 경우에만 가능하다.
- 발급 취소는 종료 전이며 아직 `AVAILABLE`인 회원 쿠폰만 가능하다.
- 발급·취소 대상이 조회 이후 변경되어 INSERT 또는 DELETE가 0건이면 성공 메시지를 보여 주지 않고 최신 상태 오류를 반환한다.
- 회원 검색·발급 회원 목록 응답은 이메일과 휴대폰 번호를 서버에서 마스킹하고, 생일은 월·일만 제공한다.

### 4.3 수정

- 종료된 쿠폰은 수정 화면 진입과 수정 요청을 모두 거부한다.
- 시작 전에는 등록 Form의 값을 수정할 수 있다. 단, 발급 대상은 등록 이후 변경하지 않는다.
  수정 요청 Form에는 `targetType`을 포함하지 않고, 기존 정책은 읽기 전용으로만 표시한다.
- 시작 후에는 쿠폰명, 종료 일시, 총 발급 수량만 수정할 수 있다.
- 시작 후 종료 일시는 기존 값보다 과거로 변경할 수 없고, 동일 시각 또는 연장만 허용한다.
- 특정 회원 쿠폰의 총 발급 수량은 이미 발급된 수량보다 작게 변경할 수 없다.

### 4.4 발급 중지·재개

- 발급 중지는 발급 허용 상태(`ACTIVE`)이고 만료되지 않은 쿠폰에서만 가능하다.
- 발급 재개는 발급 중지 상태(`INACTIVE`)이고 만료되지 않은 쿠폰에서만 가능하다.
- 만료 여부는 스케줄러 실행 여부와 무관하게 현재 시각으로 판단한다.

## 5. 자동 발급 흐름

```text
ALL_MEMBERS / FIRST_ORDER
관리자 등록 -> CouponAdminService -> CouponIssueService -> member_coupons

NEW_MEMBERS
회원가입 완료 -> member 도메인 -> CouponIssueService.issueNewMemberCoupons(memberId)

BIRTHDAY
매시 정각 -> CouponIssueScheduler -> CouponIssueService.issueBirthdayCoupons()
           -> member 도메인의 해당 월 생일 회원 조회 -> member_coupons

SPECIFIC_MEMBERS
관리자 상세 화면 -> CouponAdminService.issueSpecificMember() -> member_coupons
```

### 5.1 도메인 간 공개 조회 계약

| 제공 도메인 | 공개 Service | 쿠폰 도메인 사용 목적 |
| --- | --- | --- |
| member | `MemberCouponQueryService` | 활성 회원 검색, 전체 회원·생일 회원 조회 |
| order | `OrderCouponQueryService` | 주문 이력이 있는 회원 식별 |

신규 회원 발급은 member 도메인이 회원가입 완료 트랜잭션의 적절한 시점에 `CouponIssueService.issueNewMemberCoupons(memberId)`를 호출해 연결한다.

## 6. 데이터와 migration

- `coupons.target_type`: 발급 대상 정책
- `coupons.total_quantity`: 특정 회원 쿠폰만 사용하는 발급 한도, 자동 대상은 `NULL`
- `coupons.issued_quantity`: 실제 발급된 회원 쿠폰 수
- `member_coupons`: 회원별 발급·사용 이력

현재 대상 정책 컬럼과 수량 제약은 `V20260804_094624__add_coupon_target_type.sql`에 정의되어 있다. 이 migration은 아직 공유되지 않은 로컬 작업이므로 정책 변경에 맞춰 수정했다. 공유·적용된 뒤에는 기존 migration을 수정하지 않고 새 versioned migration을 추가한다.

## 7. 후속 작업

- 고객 쿠폰함의 실제 보유 쿠폰 조회와 만료 처리
- 주문·결제에서 쿠폰 선택, 할인 금액 계산, 사용 확정·실패 복구
- 첫 주문 이벤트 시점 발급으로 정책을 전환할지 여부 협의
- 신규 회원 가입 Service와 `issueNewMemberCoupons` 실제 연결
- MariaDB Testcontainers로 대상별 수량 제약·중복 발급·동시 발급 통합 테스트 추가
