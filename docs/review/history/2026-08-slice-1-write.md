# 조각 1 — 작성 (#109, PR #177 머지 완료)

> **끝난 조각의 기록이다. 지금 구속하지 않는다.**
> 이 조각이 만든 규칙의 정본은 `../specs/review-write.md`.
> 조각 순서와 진행 상태는 `../PLAN.md`, 발견된 문제는 `../reviews/`,
> 방향을 고른 판단은 `../decisions/`에 있다.

**주문 데이터는 `domain/order/`에 둔 연동 계약으로 받았다.** 자격 검증과 A1 목록이 `order_items`·`orders`를 직접 JOIN하는 모양이라 `docs/conventions.md` 12절과 어긋났다. 상세는 `../specs/review-write.md` A1이 정본이다.

만든 것은 `domain/order/`의 `OrderReviewQueryService`·`OrderReviewMapper`·`mapper/order/OrderReviewMapper.xml` 셋이고, **주환님 기존 파일은 한 줄도 고치지 않았다** — `OrderMapper`에 메서드를 얹지 않고 새 파일만 만들었다. 선례는 `OrderCouponQueryService`(정후님이 주문 폴더에 만든 것)와 `MemberCommunityQueryService`다.

**`CommandService`는 만들지 않았다** — 리뷰가 주문에 하는 것은 조회뿐이다. 필요한 계약만 만들고 Query·Command 조합을 예상만으로 미리 만들지 않는다(12절).

- `SecurityConfig`의 local preview 목록에서 `/reviews/**`를 뺐다(`../DOMAIN.md` 2.3)

**검증**: 남의 주문에 작성 거부, `PICKED_UP`이 아닌 주문 거부, 같은 주문상품에 두 번 작성 거부, 평점 범위 밖 거부.
