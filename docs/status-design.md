# 상태 설계 문서 (상태값 공통 규칙)

- **상태**: 정본 (2026-07-28 기준, 코드 컨벤션에서 분리)
- **범위**: 전 도메인 `status` 컬럼 설계 · 저장값 · 전이 · 화면 표시명
- **관련 문서**: [코드 컨벤션](conventions.md)

---

> 목적: 13개 도메인에 흩어진 모든 `status`(및 상태처럼 보이는 값)를 **한 방식으로 통일**한다.
> 상태값 관련 결정은 이 절을 정본으로 삼는다.

## status 하나를 다루는 표준 방식 (전 도메인 공통)

한 상태 컬럼은 **3가지 표현**을 가진다. 어디를 정본으로 두는지가 핵심이다.

| 표현 | 어디에 | 규칙 |
|---|---|---|
| **저장값 = enum 이름** (영문 UPPER_SNAKE) | DB `VARCHAR` + `CHECK IN(...)`, Java `enum` | **이게 정본.** MyBatis가 enum ↔ 문자열을 이름으로 자동 매핑한다. |
| **한글 라벨** (판매 중, 승인 대기 …) | 화면 | **절대 저장하지 않는다.** enum의 `label()` 또는 view 헬퍼로 매핑한다. 목업에 하드코딩된 한글 배지는 전부 enum-driven으로 교체한다. |
| **전이 규칙** | Java `enum`의 `canTransitionTo()` | DB `CHECK`는 "허용 값 집합"만 지킨다. **전이는 service에서 검증**한다. SQL에 전이를 넣지 않는다. |

### 핵심 원칙

- **저장값은 영문 enum 이름 하나로 통일.** 한글·숫자·코드값으로 저장하지 않는다.
- **한글은 화면에서만.** 저장값과 라벨을 섞으면 세 표현이 서로 어긋난다(drift). 라벨 매핑은 enum이나 view가 소유한다.
- **전이는 service에서.** DB는 값 집합만, 상태 머신은 Java가 소유한다.
- `OrderStatus`(7개 + 전이)·`PaymentStatus`(6개)가 **이미 이 형태의 모범답안**이다. 나머지 담당자는 이 두 enum을 그대로 복제해서 자기 도메인에 적용한다.

### DB 컬럼 작성 규칙 (전원 합의)

- 타입: **`VARCHAR(20) NOT NULL`** (긴 값이 필요하면 그 컬럼만 예외적으로 늘리고 이유를 주석으로 남긴다)
- 제약: **`CONSTRAINT chk_<table>_status CHECK (status IN ('A','B', ...))`** — `store` DDL의 `chk_...` 네이밍 관례 준수
- 기본값: 신규 행이 시작하는 상태를 `DEFAULT`로 지정 (예: `members` → `ACTIVE`, `member_coupons` → `ISSUED`)
- 컬럼명: 상태는 `status`, 종류는 `type` / `category` (아래 분류에 따름)

## status 자리 전수 인벤토리 (코드 + 목업 통합)

| 도메인.컬럼 | 담당 | 목업 표기 | 저장값(enum) | 상태 |
|---|---|---|---|---|
| `members.status` | 수민 | 정상 / 이용 제한 | `ACTIVE / SUSPENDED / WITHDRAWN` | 거의 확정 |
| `products.status` | 시은 | 판매 중 / 판매 중지 | `ACTIVE / INACTIVE` | 거의 확정 |
| `product_option_groups.status` | 시은 | 활성 / 비활성 | `ACTIVE / INACTIVE` | ✅ 정책 확정 |
| `product_options.status` | 시은 | 활성 / 비활성 | `ACTIVE / INACTIVE` | ✅ 정책 확정 |
| `orders.status` | 주환 | 결제대기/검토중/픽업대기/픽업완료/취소/반려/만료 | **`OrderStatus` 7개 (확정)** | ✅ 코드 확정 |
| `payments.status` | 주환 | 결제 완료 / 결제 대기 | **`PaymentStatus` 6개 (확정)** | ✅ 코드 확정 |
| `payment_cancellations.status` | 주환 | 취소 요청 | `REQUESTED / DONE / REJECTED` ? | ☐ 열림 |
| `coupons.status` | 정후 | 발급 중 | `ACTIVE / INACTIVE / ENDED` ? | ☐ 열림 |
| `member_coupons.status` | 정후 | 사용 가능 / 사용 완료 | `ISSUED / USED / EXPIRED` | 거의 확정 |
| `posts.status` | 현규 | 정상 / 제재 | **`PostStatus` `PUBLISHED / DELETED / BLOCKED` (확정)** | ✅ 코드 확정 |
| `comments.status` | 현규 | (표기 없음) | **`CommentStatus` `PUBLISHED / DELETED` (확정)** | ✅ 코드 확정 |
| `post_reports.status` | 현규 | (신고 처리) | `PENDING / ACCEPTED / REJECTED` | 거의 확정 |
| `reviews.status` | 현규 | 숨김 | `VISIBLE / HIDDEN` ? | ☐ 열림 |
| `chat_rooms.status` | 민정 | 상담가능 / 상담중 / 미답변 | `OPEN / CLOSED` ? (아래 함정 참고) | ☐ 열림 |

### 상품 옵션 상태 정책

- `product_option_groups.status`와 `product_options.status`는
  `ACTIVE / INACTIVE`를 사용한다.
- 관리자 삭제 요청은 물리 삭제하지 않고 `INACTIVE`로 변경한다.
- 비활성 그룹과 옵션은 관리자 화면에 남겨 재활성화할 수 있다.
- 고객 화면에는 그룹과 옵션이 모두 `ACTIVE`인 경우만 노출한다.
- 장바구니·주문이 참조할 수 있는 옵션 행의 식별자는 삭제하지 않는다.
- `GENERAL`과 `CUSTOM` 상품 모두 필수 옵션 그룹을 사용할 수 있다.
- 옵션별 재고는 관리하지 않는다. `GENERAL` 상품의 주문 가능 수량과
  차감·복구는 `products.stock_quantity`만을 기준으로 처리한다.
- 옵션별로 독립적인 재고가 필요한 품목은 옵션이 아니라 별도 상품으로
  등록한다.
- 판매 중지 상품은 옵션을 준비할 수 있도록 필수 옵션 그룹의 활성 옵션이
  없어도 허용한다.
- 판매 시작 시 활성 상태인 필수 옵션 그룹마다 활성 옵션이 하나 이상인지
  최종 검증한다.
- 판매 중인 상품의 활성 필수 옵션 그룹에서는 마지막 활성 옵션을
  비활성화할 수 없고, 필수 옵션 그룹 자체도 비활성화할 수 없다.
- 선택 옵션 그룹은 활성 옵션이 없어도 허용하며, 고객 화면에는 노출하지
  않는다.

### 이미 확정된 두 enum

**`OrderStatus` (주문 상태 7개 + 전이 규칙 — 코드에 확정됨)**

```text
일반 상품:
PENDING_PAYMENT → READY_FOR_PICKUP → PICKED_UP

주문 제작 상품:
PENDING_PAYMENT → UNDER_REVIEW → READY_FOR_PICKUP → PICKED_UP

예외 흐름:
PENDING_PAYMENT  → EXPIRED
UNDER_REVIEW     → REJECTED
UNDER_REVIEW     → CANCELED
READY_FOR_PICKUP → CANCELED

최종 상태:
PICKED_UP / CANCELED / REJECTED / EXPIRED
```

결제 완료 여부는 `orders.status`에 저장하지 않고 `payments.status = DONE`으로 관리한다.
전이 규칙은 `OrderStatus.canTransitionTo()`가 소유하며, SQL의 `CHECK`는 7개 값 집합만 나열한다.

이전 개발 단계에서 사용한 11개 주문 상태 데이터는 새 상태로 자동 변환하지 않는다.
보존해야 하는 운영 주문 데이터가 없으므로, 이전 상태가 저장된 개발 DB는 초기화한 뒤
Flyway migration을 다시 적용한다.

**`PaymentStatus` (토스 결제 상태, 6개 — 코드에 확정됨. 주문 enum과 절대 섞지 않는다)**

```
READY / DONE / CANCELED / PARTIAL_CANCELED / ABORTED / EXPIRED
```

## ⚠️ status처럼 보이지만 status가 아닌 것 (분류 필수)

목업 배지를 그대로 컬럼으로 만들지 않는다. 4종류로 갈린다.

### 불리언 → enum 만들지 않는다

| 목업 표기 | 실제 | 담당 |
|---|---|---|
| 알림 `읽음 / 미읽음` | `notifications.is_read BOOLEAN` | 민정 |
| 영업시간 `휴무` | `store_business_hour.is_closed`(이미 존재) | — |

### 파생값 → 저장하지 않고 계산한다

| 목업 표기 | 실제 | 담당 |
|---|---|---|
| 상품 `품절 / 재고 부족` | `stock_quantity`에서 계산(0이면 품절). **`products.status`(판매 on/off)와 별개** | 시은 |
| 채팅 `미답변` | 마지막 메시지가 관리자 답이 아님 → 메시지에서 파생. 방 자체 `status`와 다름 | 민정 |

> 시은 주의: `products.status`(ACTIVE/INACTIVE = 판매 스위치)와 재고(품절/재고부족)는 **다른 축**이다. 하나의 컬럼으로 합치지 않는다.

### 종류(type/category) → status가 아니다 (단, 저장 방식은 동일 패턴)

| 목업 표기 | 실제 | 담당 |
|---|---|---|
| 커뮤니티 글 `후기 / 질문 / 레시피 / 자유` | `posts.category` (`REVIEW/QUESTION/RECIPE/FREE`) — status와 별도 컬럼 | 현규 |
| 알림 `주문 승인 / 결제 완료 …` | `NotificationType` enum (**현재 TODO, 채워야 함**) | 민정 |

`type`/`category`도 저장값·라벨 규칙은 status와 동일하게 적용한다(영문 enum 이름 저장, 한글 라벨 미저장).

### 다른 도메인의 status를 빌려 표시하는 것 → 자기 컬럼이 아니다

| 목업 표기 | 실제 |
|---|---|
| 채팅 목록의 `승인 대기 / 제작 중` 배지 | 연결된 **주문의 `orders.status`**를 표시한 것. `chat_rooms`에 저장하지 않는다 |

## 지금 못 박을 것 vs 담당자가 채울 것

### 지금 전원 합의로 확정 (미루면 서로 깨진다)

1. **표준 방식** — enum 이름 저장 / 한글 라벨 미저장 / 전이는 service. `OrderStatus`·`PaymentStatus`가 레퍼런스.
2. **DB 컬럼 규칙** — `VARCHAR(20) NOT NULL` + `chk_<table>_status CHECK IN(...)` + 시작 상태 `DEFAULT`.
3. **함정 분류** — "재고·읽음·글종류·남의 status는 내 status 컬럼이 아니다"를 합의.

### 담당자가 자기 DDL 짤 때 채우는 ☐

| 담당 | 채울 것 |
|---|---|
| 주환 | `payment_cancellations.status` 값 확정 |
| 정후 | `coupons.status`(캠페인 상태) 값 확정 |
| 현규 | `reviews.status`(숨김) 값 확정 |
| 민정 | `chat_rooms.status` 정의 + **`NotificationType` enum 값 채우기**(현재 TODO) |

> ☐ 항목을 확정하면 인벤토리의 해당 행을 "확정"으로 갱신하고, enum + DDL을 함께 커밋한다.

## 한 줄 요약

**저장값은 영문 enum 이름 하나로 통일, 한글은 화면에서만, 전이는 service에서. 그리고 재고·읽음·글종류·남의 도메인 status는 내 status 컬럼이 아니다.**
