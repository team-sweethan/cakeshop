# 후기 수정·삭제 — A4·A5

> 공통 규칙(상태·평점·권한·검증·오류 코드·표시·도메인 경계)은 `../DOMAIN.md` 2절이 정본이다.
> 조각 순서와 진행 상태는 `../PLAN.md`. 결정의 배경은 `../decisions/`.
> 조각: 4

### A4. 후기 수정

| | |
|---|---|
| 액터 | 작성자 |
| 경로 | `GET /reviews/{id:\d+}/edit`, `POST /reviews/{id:\d+}/edit` |
| 화면 | **신규** (A2(`review-write.md`) 폼을 재사용하되 주문 상품 선택 영역 없음) |

**처리**

- `PUBLISHED` 상태일 때만 수정할 수 있다. `BLOCKED`는 `BLOCKED_REVIEW` 403, `DELETED`는 `REVIEW_NOT_FOUND` 404.
- **기간 제한을 두지 않는다.** 제한을 두면 기준 시각과 시간대 판단이 따라붙고, 화면에도 남은 기간 표시가 필요해진다. 얻는 것에 비해 비싸다.
- 수정할 수 있는 것은 **평점 4종과 본문**이다. `order_item_id`·`product_id`·`member_id`는 바뀌지 않는다.
- **소유권과 기대 상태를 UPDATE 조건에 함께 넣는다.** `UPDATE reviews SET ... WHERE id = ? AND member_id = ? AND status = 'PUBLISHED'`로 쏘고 `affectedRows == 0`이면 그때 원인을 가려 던진다(`DOMAIN 2.1`·`DOMAIN 2.5`와 같은 모양이라 문장이 늘지 않는다).
  - 앞에서 `PUBLISHED`를 **검증만** 해 두면 관리자 숨김(C4(`review-admin.md`))이 그사이 커밋될 때 **작성자가 숨겨진 후기의 내용을 덮어쓴다.** 해제하는 날 차단했던 것과 다른 글이 공개되어, `DOMAIN 2.1`이 정한 "숨겨진 후기에 대해 작성자가 할 수 있는 일은 없다"와 정면으로 어긋난다.
- `overall_rating`이 바뀌면 집계가 달라지므로 **D1(`product-rating.md`)을 호출**한다. 다만 **"바뀌면"의 판정 기준이 아직 열려 있다** — 폼이 들고 온 옛 값과 비교하면 동시 수정에서 집계가 누락될 수 있다(`DOMAIN 4`).

### A5. 후기 삭제

| | |
|---|---|
| 액터 | 작성자 |
| 경로 | `POST /reviews/{id:\d+}/delete` |

**처리**

- `PUBLISHED → DELETED` 전이. **soft delete다.** 행을 지우지 않는다.
- `BLOCKED` 후기는 삭제할 수 없다(`DOMAIN 2.1`).
- 삭제 후 **D1을 호출**한다. 집계에서 빠져야 한다.

**삭제하면 그 주문 상품에는 다시 후기를 쓸 수 없다.**

- `uk_reviews_order_item`이 `order_item_id`에 UNIQUE라 `DELETED` 행이 남아 재작성 INSERT가 막힌다.
- **삭제 확인창에 이 사실을 적는다** — `삭제하면 이 주문 상품에는 다시 후기를 작성할 수 없습니다. 삭제하시겠습니까?`
- 수정이 열려 있으므로 실질적 손해는 작다. 고치고 싶으면 수정하면 된다.
- `DELETED → PUBLISHED` 재활성을 열지 않는 이유: 그러면 "삭제"가 실제로는 숨김이 되어 버튼 이름과 동작이 어긋난다.
