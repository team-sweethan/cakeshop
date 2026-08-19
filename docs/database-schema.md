# 데이터베이스 스키마 명세

이 문서는 현재까지 적용된 Flyway migration을 도메인별로 찾아보기 위한 진입점이다. 실제 스키마의 정본은
`src/main/resources/db/migration`의 versioned migration이며, 아래 문서는 migration을 버전 순서대로 모두 적용한
**최종 상태**를 설명하는 읽기 전용 명세다.

## 문서 기준

- 기준 migration: `V0__initial_schema.sql`부터 `V20260819_115236__add_order_pending_payment_lookup_index.sql`까지 46개
- 최종 물리 테이블: 46개
- migration의 중간 상태, 데이터 보정용 임시 테이블과 이미 삭제된 컬럼·인덱스는 기록하지 않는다.
- 담당자 이름은 현재 리뷰 경계를 나타낸다. 파일명과 경로는 담당 변경과 무관하게 도메인명을 유지한다.
- 통계처럼 여러 도메인의 테이블을 읽는 ReadModel은 자체 테이블이 없더라도 별도 문서로 경계를 남긴다.

## 표기 규약

| 표기 | 의미 |
|---|---|
| PK | 기본 키(Primary Key) |
| FK | 외래 키(Foreign Key). 제약조건 목록에 참조 대상을 적는다. |
| UK | 유니크 키(Unique Key) |
| INDEX | migration에서 명시적으로 선언한 일반 인덱스 |
| GEN | 다른 컬럼으로 계산되는 generated column |
| Null `X` | `NOT NULL` |
| Null `O` | `NULL` 허용 |
| 기본값 `없음` | INSERT 시 값을 명시해야 하며 DB 기본값이 없음 |

복합 키와 복합 인덱스는 관련된 모든 컬럼에 같은 표기를 붙이고, 실제 컬럼 순서는 제약조건·인덱스 목록에서
확인한다. PK와 UK도 DB에서는 인덱스로 동작하지만 각각 `PK`, `UK`로만 표기한다. FK 지원을 위해 MariaDB가
자동 생성하는 암묵 인덱스는 migration에 직접 선언된 인덱스가 아니므로 별도 `INDEX`로 추정해 기록하지 않는다.

## 담당자별 도메인

| 담당 | 도메인 | 테이블 수 | 문서 |
|---|---|---:|---|
| 수민 | 회원 | 4 | [member](schema/member.md) |
| 수민 | 장바구니 | 4 | [cart](schema/cart.md) |
| 주환 | 주문 | 4 | [order](schema/order.md) |
| 주환 | 결제 | 2 | [payment](schema/payment.md) |
| 정후 | 쿠폰 | 2 | [coupon](schema/coupon.md) |
| 민정 | 채팅 | 4 | [chat](schema/chat.md) |
| 민정 | 알림 | 2 | [notification](schema/notification.md) |
| 현규 | 커뮤니티 | 10 | [community](schema/community.md) |
| 현규 | 리뷰 | 3 | [review](schema/review.md) |
| 시은 | 상품 | 5 | [product](schema/product.md) |
| 시은 | 통계 | 3 | [statistics](schema/statistics.md) |
| 공통 협의 | 매장 | 3 | [store](schema/store.md) |

## 갱신 규칙

1. 공유된 migration은 수정하지 않고 새 versioned migration을 추가한다.
2. 스키마가 바뀌는 PR에서는 해당 도메인 문서와 이 문서의 기준 migration·테이블 수를 함께 갱신한다.
3. 문서는 migration 실행 결과를 따라가며, 문서와 SQL이 다르면 migration을 정본으로 판단한다.
4. 컬럼을 추가·변경·삭제할 때 컬럼 표뿐 아니라 FK, UK, CHECK, INDEX와 관련 migration 목록도 확인한다.
5. 여러 도메인을 참조하는 FK는 테이블 소유 도메인 문서에 기록하고, 계약 변경은 해당 데이터 담당자와 검토한다.
