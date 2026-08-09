# 후기 작성 — A1·A2·A3·A6

> 공통 규칙(상태·평점·권한·검증·오류 코드·표시·도메인 경계)은 `../DOMAIN.md` 2절이 정본이다.
> 조각 순서와 진행 상태는 `../PLAN.md`. 결정의 배경은 `../decisions/`.
> 조각: 1 (A6만 2차)

### A1. 작성할 후기 목록

마이페이지 `작성할 후기` 링크가 가리키는 화면. **지금은 링크만 있고 대상 화면이 없다.**

| | |
|---|---|
| 액터 | 로그인 고객 |
| 경로 | `GET /mypage/reviews/writable` |
| 화면 | **신규** |
| 입력 | `page`(선택, 기본 1) |

**처리**

- 인증 회원의 주문 중 `orders.status = 'PICKED_UP'`인 것의 `order_items`를 모은다.
- 그중 `reviews`에 아직 행이 없는 것만 남긴다.

```sql
SELECT oi.id, oi.product_name, o.order_number, o.picked_up_at
FROM order_items oi
JOIN orders o ON o.id = oi.order_id
WHERE o.member_id = #{memberId}
  AND o.status = 'PICKED_UP'
  AND NOT EXISTS (SELECT 1 FROM reviews r WHERE r.order_item_id = oi.id)
ORDER BY o.picked_up_at DESC, oi.id DESC
LIMIT #{size} OFFSET #{offset}
```

> **이 SQL은 리뷰 매퍼에 두지 않는다. 주문 도메인에 두는 연동 계약이 돌려주는 형태다.** 계약을 어디에 만들고 확인을 어떻게 받는지는 `../PLAN.md` 조각 1이 정본이다 — 여기에는 그 계약이 **무엇을 보장해야 하는지**만 적는다.
>
> - `docs/conventions.md` 15절은 다른 도메인의 테이블을 직접 JOIN·조회하지 않고 공개 Service나 QueryService를 거치도록 정한다. 위 문장은 review 쪽에서 `order_items`와 `orders`를 직접 JOIN하고, **A2·A3의 검증 1~3번도 같은 접근을 전제한다.** D1(`product-rating.md`)에서 상품 쪽에 적용한 기준을 주문 쪽에는 적용하지 않은 자리다.
> - 그대로 두면 리뷰가 주문 스키마와 `OrderStatus` 표현에 직접 묶인다. 주문 쪽이 상태 어휘나 픽업 시각 컬럼을 바꾸는 날 **리뷰가 조용히 어긋난다** — 컴파일도 테스트도 통과하고 목록만 비어 보인다.
> - 계약은 두 가지를 보장해야 한다. **(1)** 후기 작성 자격이 있는 주문 상품 목록(위 SELECT의 출력이 그 반환 형태다), **(2)** A2·A3의 검증 1~3번이 쓸 단건 조회(`orderItemId` → 소유 회원·주문 상태·`product_id`).
> - **(1)은 제외 조건과 페이징을 함께 처리해야 한다.** 계약이 픽업 완료 주문 상품을 먼저 `LIMIT 20`으로 잘라 주고 리뷰가 뒤에서 `NOT EXISTS`로 거르면, **최신 20건이 전부 작성 완료일 때 첫 페이지가 통째로 빈다.** 그 항목은 다음 페이지로 밀리고 전체 건수도 틀어진다. 목록이 비는 것은 정상 상태라 오류로도 드러나지 않는다.
>   - **계약이 제외할 `orderItemId` 목록을 인자로 받아 페이징까지 책임지는 모양을 제안한다** — `findWritableOrderItems(memberId, excludedOrderItemIds, PageRequest)`. 주문이 리뷰를 아는 것이 아니라 **불투명한 ID 목록**을 받을 뿐이라 의존 방향이 뒤집히지 않는다.
>   - 제외 목록의 크기는 **그 회원이 쓴 후기 수**로 묶인다. 한 사람이 주문한 상품 수를 넘지 않으므로 1차 규모에서 `NOT IN`으로 충분하다. 커지면 계약 안에서 조인 방식으로 바꾸면 되고 리뷰 쪽은 그대로다.
>   - `DOMAIN 2.7`이 검색 조건에 대해 정한 것과 같은 규칙이다 — **리뷰가 뒤에서 거르면 건수가 틀어진다.**
> - 위 SQL은 **계약의 형태를 정하기 위한 것**이지 리뷰 매퍼에 넣을 문장이 아니다. 리뷰 쪽에 남는 것은 **제외 목록을 만들기 위한 `reviews` 조회**뿐이다.

- **`NOT EXISTS`는 상태를 보지 않는다.** `DELETED` 후기도 행이 남으므로 목록에 다시 뜨지 않는다. 이것이 의도다 — 재작성은 `uk_reviews_order_item`이 막으므로, 목록에 띄워 놓고 저장에서 거절하면 안 된다(`PLAN.md` R10).
- `order_items.quantity`가 2 이상이어도 후기는 1개다. 후기 단위는 수량이 아니라 주문 상품 행이다.
- 페이징은 `PageRequest`/`PageResult` 재사용. 크기 20 고정.
- 정렬 키 `picked_up_at`은 변하지 않으므로 오프셋 페이징으로 충분하다.

**출력**: 상품명, 주문번호, 픽업 일시, `후기 쓰기` 버튼(→ `/reviews/new?orderItemId={oi.id}`)

**빈 목록**: `작성할 후기가 없습니다.` 안내. 목록이 비는 것은 정상이므로 오류로 다루지 않는다.

### A2. 후기 작성 폼

| | |
|---|---|
| 액터 | 로그인 고객 |
| 경로 | `GET /reviews/new?orderItemId={N}` |
| 화면 | `customer/review/form.html` (목업 → 실동작 전환) |
| 입력 | `orderItemId` **필수** |

**`orderItemId`는 필수다.** 없거나 숫자가 아니면 400이다.

- 파라미터 없는 `/reviews/new`로 가는 진입점을 고친다. **`orderItemId` 없이 열면 400이므로 남겨 두면 죽은 링크가 된다.**

| 진입점 | 현재 | 바꿀 것 |
|---|---|---|
| 마이페이지 `작성할 후기` | `/reviews/new` | `/mypage/reviews/writable` (A1) |
| 상품 상세 `후기 작성` | `/reviews/new` | `/mypage/reviews/writable` (A1) |
| 화면 인덱스 `C16 후기 작성` | `/reviews/new` | `/mypage/reviews/writable` (A1) |

> **주문 상세에는 `후기 작성` 링크가 없다.** 2026-08-09에 확인했다 — `customer/order/detail.html`에 `reviews` 문자열이 하나도 없다. 예전에 있었다고 적혀 있었으나 지금 `dev` 기준으로는 사실이 아니다.
>
> 있어야 하는 자리인 것은 맞다. 주문 상세는 그 주문의 `orderItemId`를 이미 알고 있어 A1을 거치지 않고 곧바로 폼으로 보낼 수 있는 **유일한 진입점**이다. 다만 목록 반복 안에 링크를 넣는 일이라 **링크 한 줄 수정을 넘어서므로**(`domain/review/CLAUDE.md`), 주환님께 알리고 별도로 진행한다. 조각 1은 A1을 통한 경로만으로 완결된다.

- **상품 상세에서 곧바로 폼으로 보내지 않는 이유**: 그 상품을 픽업한 주문이 여러 건일 수 있어 어느 주문 상품인지 화면이 정할 수 없다. 고르는 중간 화면을 새로 만드느니 A1이 대신한다.

**검증 순서** (Service에서, 실패 시 즉시 중단)

| # | 검증 | 실패 |
|---|---|---|
| 1 | `order_items` 존재 | `ORDER_ITEM_NOT_FOUND` 404 |
| 2 | `orders.member_id` = 인증 회원 | `ORDER_ITEM_NOT_FOUND` 404 |
| 3 | `orders.status = 'PICKED_UP'` | `NOT_PICKED_UP` 400 |
| 4 | 같은 `order_item_id`의 후기 없음 | `ALREADY_REVIEWED` 409 |

- 2번이 403이 아니라 404인 이유는 `DOMAIN 2.5`와 같다 — 남의 주문 상품 id의 존재를 알려 주지 않는다.

**출력**: 주문 상품 정보(상품명·주문번호·픽업 완료 표시), 평점 4종 입력(`DOMAIN 2.2`), 본문 입력

**목업에서 걷어낼 것**: 같은 폼 안에 있는 `수정하기`·`삭제하기` 버튼. 등록 화면과 수정 화면(A4(`review-edit-delete.md`))은 갈라진다.

### A3. 후기 등록

| | |
|---|---|
| 액터 | 로그인 고객 |
| 경로 | `POST /reviews` |
| 입력 | `orderItemId`, `overallRating`, `tasteRating`, `designRating`, `serviceRating`, `content` |

**처리**

- **A2의 검증 4가지를 그대로 다시 수행한다.** 폼을 연 시점과 제출 시점 사이가 벌어질 수 있고, 무엇보다 폼 화면을 거치지 않은 직접 호출을 막아야 한다.
- `reviews.product_id`는 **요청값을 믿지 않고 `order_items.product_id`에서 파생**시킨다. 같은 값에 이르는 경로가 둘이라 요청값을 그대로 쓰면 남의 상품에 후기를 붙일 수 있다(`PLAN.md` R4).
- `reviews.member_id`는 인증 사용자에서 가져온다. 요청값을 받지 않는다.
- `status`는 `PUBLISHED`로 저장한다.
- INSERT는 `uk_reviews_order_item`에 걸릴 수 있다. **`DuplicateKeyException`을 잡아 `ALREADY_REVIEWED`로 바꾼다** — 4번 검증과 INSERT 사이의 동시 요청은 검증만으로 막히지 않는다(커뮤니티 신고 선례).
- **순서가 정해져 있다: ① 상품 행 잠금(`ProductReviewCommandService.lockForRating`) → ② `reviews` INSERT → ③ D1 집계.** 셋 다 같은 트랜잭션이다. **뒤집으면 교착이고, 순서를 지켜도 집계 SELECT가 잠금 읽기가 아니면 값이 어긋난다** — 둘 다 A3의 검증 4가지가 평범한 SELECT인 데서 나오며, 근거와 계약은 **D1이 정본이다.**

**성공 후**: `/mypage/reviews/writable`(A1)로 redirect. 방금 쓴 항목이 목록에서 빠진 것으로 완료를 확인한다.

> B3(`review-read.md`)가 생기는 조각 3부터는 `/mypage/reviews`로 옮긴다. 조각 1 시점에는 쓴 후기를 볼 화면이 아직 없다.

## 2차

### A6. 이미지 첨부

`review_images` 테이블은 있고 목업에도 입력(`최대 3장`)이 있으나 **1차 범위 밖이다.**

- 1차를 이미지 없이 끝내도 후기의 목적(평점·글)은 완결된다.
- 업로드는 상품 이미지 쪽에 이미 선례가 있으므로 나중에 붙이는 비용이 크지 않다.
- **목업의 이미지 입력은 1차에서 화면에서 걷어낸다.** 동작하지 않는 입력을 남겨 두면 고객이 첨부했다고 믿는다.
