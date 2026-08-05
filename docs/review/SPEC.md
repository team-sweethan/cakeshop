# Review 기능 명세서 (1차)

> 이 문서는 Review 도메인에서 **무엇을 만드는지**의 정본이다. 기능 단위로 쪼개고 각 단위의 입력·처리·출력·오류를 적는다.
> 도메인 전체 흐름은 `docs/review/FLOW.md`, 조각 순서·진행 상태·위험은 `docs/review/PLAN.md`를 본다.
> 규칙이 더 굳으면 `docs/community/DOMAIN.md` 형식을 따라 `docs/review/DOMAIN.md`로 옮기고 그쪽이 정본이 된다.
> 프로젝트 전체 규칙은 `AGENTS.md`가 상위 정본이며, 충돌 시 `AGENTS.md`가 우선한다.

## 0. 읽는 법

기능 ID는 액터와 성격으로 나눈다.

| 접두 | 묶음 |
|---|---|
| `A` | 고객 — 작성 |
| `B` | 고객 — 조회 |
| `C` | 관리자 |
| `D` | 다른 도메인과의 연동 |
| `E` | 기반 정리 (화면 없음) |

`현재` 열은 **지금 코드에 있는 것**이다.

| 표기 | 뜻 |
|---|---|
| 없음 | 코드·화면 모두 없다 |
| 목업 | 화면은 있으나 정적 HTML이다 |
| 링크만 | 다른 화면에 진입 링크만 있고 대상이 없다 |
| 화면 완료 | 화면은 이미 실데이터를 바인딩하고 있다 |

## 1. 기능 목록

| ID | 기능 | 액터 | 경로 | 현재 | 조각 |
|---|---|---|---|---|---|
| **A1** | 작성할 후기 목록 | 고객 | `GET /mypage/reviews/writable` | 링크만 | 1 |
| **A2** | 후기 작성 폼 | 고객 | `GET /reviews/new` | 목업 | 1 |
| **A3** | 후기 등록 | 고객 | `POST /reviews` | 없음 | 1 |
| **A4** | 후기 수정 | 고객 | `GET·POST /reviews/{id}/edit` | 목업 버튼만 | 4 |
| **A5** | 후기 삭제 | 고객 | `POST /reviews/{id}/delete` | 목업 버튼만 | 4 |
| **A6** | 이미지 첨부 | 고객 | (A2·A3에 포함) | 목업 입력만 | 8 (2차) |
| **B1** | 상품 후기 목록 | 누구나 | `GET /products/{id}/reviews` | "준비 중" | 3 |
| **B2** | 상품 평균 평점·후기 수 | 누구나 | (상품 상세에 포함) | 화면 완료 | 2 |
| **B3** | 내가 쓴 후기 목록 | 고객 | `GET /mypage/reviews` | 없음 | 3 |
| **B4** | 후기에 달린 답글 노출 | 누구나 | (B1·B3에 포함) | 없음 | 6 |
| **C1** | 관리자 후기 목록 | 관리자 | `GET /admin/reviews` | 목업 | 5 |
| **C2** | 관리자 검색·필터 | 관리자 | (C1의 파라미터) | 목업 폼만 | 5 |
| **C3** | 관리자 후기 상세 | 관리자 | `GET /admin/reviews/{id}` | **없음** | 5 |
| **C4** | 후기 숨김 | 관리자 | `POST /admin/reviews/{id}/block` | 목업 버튼만 | 5 |
| **C5** | 답글 작성 | 관리자 | `POST /admin/reviews/{id}/replies` | **없음** | 6 |
| **C6** | 답글 수정 | 관리자 | `POST /admin/reviews/{id}/replies/edit` | **없음** | 6 |
| **D1** | 상품 평점 집계 | — | (Service 계약) | 없음 | 2 (#33) |
| **D2** | 알림 발송 | — | (Service 계약) | 없음 | 7 |
| **E1** | `ReviewStatus` enum + `CHECK` | — | — | 없음 | 0 |
| **E2** | 평점 범위 `CHECK` | — | — | 없음 | 0 |
| **E3** | `Review`·`ReviewReply` 엔티티 | — | — | 빈 클래스 | 0 |

**신규 화면 3개**: A1, C3, C5·C6의 답글 영역. 목업에 대응하는 화면이 없어 새로 만든다.

## 2. 공통 규칙

### 2.1 상태 모델

`ReviewStatus { PUBLISHED, DELETED, BLOCKED }`. `PostStatus` 선례를 그대로 따른다.

**`reviews.status`가 노출 여부의 유일한 기준이다.** 조건이 두 개면 새 쿼리를 추가할 때 하나를 빠뜨린다.

| 전이 | 허용 | 주체 |
|---|---|---|
| `PUBLISHED → DELETED` | 허용 | 작성자 (A5) |
| `PUBLISHED → BLOCKED` | 허용 | 관리자 (C4) |
| `BLOCKED → PUBLISHED` | 허용 | 관리자 (숨김 해제) |
| `BLOCKED → DELETED` | **금지** | — |
| `DELETED → PUBLISHED` | **금지** | — |
| `DELETED → BLOCKED` | **금지** | — |

- `BLOCKED → DELETED` 금지: 숨김은 관리자가 걸어 둔 상태다. 작성자가 지워서 그 상태를 없앨 수 없어야 한다. 결과적으로 **숨겨진 후기에 대해 작성자가 할 수 있는 일은 없다**(수정·삭제 모두 불가).
- `DELETED`는 종착 상태다. 복구는 1차 범위 밖이다.
- 전이 규칙은 enum 안 `canTransitionTo(next)`에 두고 `null` 방어까지 `PostStatus`와 같게 한다.
- **전이 판단과 쓰기를 갈라 놓지 않는다.** `canTransitionTo`로 확인한 뒤 조건 없는 `UPDATE`를 날리면 그 사이에 상태가 바뀔 수 있다. 작성자 삭제(A5)와 관리자 숨김(C4)이 같은 `PUBLISHED`를 읽으면 **둘 다 통과하고 나중 쓰기가 앞의 조치를 덮는다** — 위 표가 금지한 전이가 결과적으로 성립한다.
  - **기대 상태를 쓰기 조건에 넣는다.** `UPDATE reviews SET status = ? WHERE id = ? AND status = ?`로 쏘고 `affectedRows == 0`이면 그때 원인을 가려 던진다. 2.5의 소유권 판정과 같은 모양이고, 조건은 한 문장에 함께 붙는다.
  - **동시 요청 테스트로 고정한다.** 순서대로 부르면 결과가 똑같아 단일 스레드 테스트로는 드러나지 않는다.
- 스키마 기본값 `'VISIBLE'`은 **코드베이스 어디에도 없는 어휘**다. 조각 0에서 `'PUBLISHED'`로 바꾸고 `CHECK (status IN ('PUBLISHED','DELETED','BLOCKED'))`를 건다.

### 2.2 평점

**스키마를 그대로 쓴다. 컬럼을 바꾸지 않는다.**

| 화면 라벨 | 컬럼 | 필수 |
|---|---|---|
| 전체 | `overall_rating` | 필수 |
| 맛 | `taste_rating` | 필수 |
| 디자인 | `design_rating` | 필수 |
| 응대 | `service_rating` | 필수 |

- 네 값 모두 **1~5 정수**. `TINYINT UNSIGNED`라 DB만으로는 0과 255가 들어가므로 조각 0에서 `CHECK (... BETWEEN 1 AND 5)`를 건다(E2).
- **대표값은 `overall_rating`이다.** 고객이 직접 매기며, 나머지 3종의 평균으로 계산하지 않는다.
  - 근거: 계산값으로 두면 "맛은 5인데 전체는 3" 같은 실제 감상을 담을 자리가 없어진다. 관리자 목록의 `★★★★★ 5.0`과 평점 필터도 단일 값을 전제한다.
- `products.average_rating` = `AVG(overall_rating)`. 세부 3종은 집계에 쓰지 않는다.
- 목업의 `포장`은 `응대`로 흡수한다. 대응 컬럼이 없고, 컬럼을 새로 만들 만큼 구분 실익이 크지 않다.
- **목업에 없는 `전체` 입력을 화면에 추가해야 한다.** 목업은 4종 중 `전체`가 빠지고 `포장`이 들어가 있다.

### 2.3 권한과 경로

`SecurityConfig`는 이미 `/reviews/**` 인증 필요, `/products/**` 공개, `/admin/**` `hasRole("ADMIN")`이다. **경로를 아래처럼 두면 기본 규칙을 고칠 필요가 없다.** 다만 local 프로필의 목업 preview 목록은 예외라 조각 1에서 손대야 한다(아래).

| 경로 | 접근 | 기능 |
|---|---|---|
| `GET /products/{id}/reviews` | 공개 | B1 |
| `GET·POST /reviews/**` | 인증 | A2, A3, A4, A5 |
| `GET /mypage/reviews/**` | 인증 | A1, B3 |
| `/admin/reviews/**` | 관리자 | C1~C6 |

- **후기 목록을 공개하는 이유**: 상품 상세의 평균 평점·후기 수는 이미 비로그인에게 보인다. 근거가 되는 후기만 가리면 숫자만 있고 이유는 없는 화면이 된다. 구매 전 고객이 후기를 읽는 것이 후기의 목적이다.
- **local 프로필에서는 `GET /reviews/**`가 이미 비로그인에 열려 있다.** `SecurityConfig`가 `app.mockup.public-preview=true`일 때 `/orders/**`·`/notifications`·`/reviews/**`·`/chat`을 앞에서 `permitAll`로 매칭한다. 목업을 로그인 없이 보려고 둔 것이라, A2가 실기능이 되는 **조각 1에서 `/reviews/**`를 이 목록에서 뺀다.**
  - 남겨 두면 익명 사용자가 작성 폼까지 들어와 주문 소유권 검증에서 튕긴다. 폼을 다 채우고 나서 튕기는 화면이다.
  - **선례가 바로 위 주석에 있다** — `/community/new`도 목업이 아니게 되면서 같은 이유로 이 목록에서 뺐다.
  - `rds`에서는 `public-preview: false`라 이 경로가 원래 닫혀 있다. 즉 **로컬에서만 뚫려 있어 로컬 확인으로는 드러나지 않는다.**
- 소유권 검증은 요청으로 전달된 회원 ID를 믿지 말고 **인증 사용자 기준으로 Service에서** 한다(`AGENTS.md`).
- 회원 상태(`ACTIVE`/`SUSPENDED`/`WITHDRAWN`)는 재검증하지 않는다. 커뮤니티와 같은 근거다 — 인증을 통과한 사용자는 정의상 `ACTIVE`이고, 그 전제는 member 도메인 테스트로 고정되어 있다.
- 경로 변수는 `{id:\d+}` 형태로 숫자를 강제한다(커뮤니티 선례).

### 2.4 입력 검증

| 항목 | 규칙 | DB 컬럼 |
|---|---|---|
| 평점 4종 | 필수, 1~5 정수 | `TINYINT UNSIGNED` |
| 후기 본문 | 필수, 10~2000자 | `TEXT` |
| 답글 본문 | 필수, 1~1000자 | `TEXT` |

- 본문 하한 10자는 목업(`minlength="10"`)을 따른다. 상한 2000자는 새로 정한다 — `TEXT`를 그대로 쓰면 정책을 정한 게 아니라 안 정한 것이다.
- **trim 후 검증**한다. 공백만 입력은 거부. 저장 시 앞뒤만 trim하고 중간 줄바꿈은 보존한다.
- **본문에 HTML을 허용하지 않는다. 순수 텍스트만.** 템플릿에서 `th:utext`를 쓰지 않는다. 줄바꿈은 CSS `white-space: pre-wrap`으로 처리한다.
- `reviews.content`는 스키마상 `NULL` 허용이지만 **화면에서는 필수**로 받는다. 평점만 있는 후기는 관리자 목록의 `내용 미리보기`를 빈칸으로 만든다.

### 2.5 오류 코드

`REVIEW_001`만 존재한다. 나머지는 조각 진행에 맞춰 추가한다. 번호는 `<도메인>_NNN` 팀 규약을 따른다.

| 코드 | 이름 | 메시지 | HTTP | 조각 |
|---|---|---|---|---|
| `REVIEW_001` | `NOT_PICKED_UP` | 픽업 완료된 주문만 후기를 작성할 수 있습니다. | 400 | (기존) |
| `REVIEW_002` | `REVIEW_NOT_FOUND` | 후기를 찾을 수 없습니다. | 404 | 1 |
| `REVIEW_003` | `ORDER_ITEM_NOT_FOUND` | 주문 상품을 찾을 수 없습니다. | 404 | 1 |
| `REVIEW_004` | `ALREADY_REVIEWED` | 이미 후기를 작성한 주문 상품입니다. | 409 | 1 |
| `REVIEW_005` | `BLOCKED_REVIEW` | 숨김 처리된 후기입니다. | 403 | 4 |
| `REVIEW_006` | `INVALID_REVIEW_TRANSITION` | 지금 상태에서 할 수 없는 조치입니다. | 400 | 4 |
| `REVIEW_007` | `ALREADY_REPLIED` | 이미 답글이 달린 후기입니다. | 409 | 6 |
| `REVIEW_008` | `REPLY_NOT_FOUND` | 답글을 찾을 수 없습니다. | 404 | 6 |

**소유권 위반에 별도 코드를 두지 않는다.** 남의 후기를 수정·삭제하려 하면 `REVIEW_NOT_FOUND`(404)다.

- 근거: 커뮤니티가 같다(`requireOwnComment` → `COMMENT_NOT_FOUND`). "권한이 없습니다"는 **그 후기가 존재한다는 사실을 알려 준다.** 남의 후기 id를 훑어 존재 여부를 확인할 수 있게 된다.
- 구현도 커뮤니티를 따른다 — `UPDATE ... WHERE id = ? AND member_id = ?`로 쏘고 `affectedRows == 0`이면 그때 원인을 가려 던진다.

### 2.6 표시 규칙

- **탈퇴 회원의 후기는 유지하고 작성자명만 `탈퇴한 회원`으로 표시**한다. 커뮤니티와 같다.
  - `reviews.member_id`는 NOT NULL FK이므로 탈퇴해도 회원 행은 남는다.
  - 평점은 상품 평가의 근거다. 작성자가 떠났다고 지우면 다른 고객의 판단 근거가 사라진다. 표시명만 바꾸면 데이터를 건드리지 않으므로 되돌릴 수 있다.
- 작성자명은 `members.nickname`을 쓴다.
- 목록에서 상품명은 **`order_items.product_name` 스냅샷**을 쓴다(주문 이력 성격의 화면 — A1, B3). 상품 상세의 후기 목록(B1)은 그 상품 화면 안이므로 상품명을 따로 표시하지 않는다.
- 새로 만드는 Java 파일의 헤더 주석은 커뮤니티 파일 형식(`작성자`/`담당자`/`작성일`/`기능`/`설명`)을 따른다.

---

## 3. A — 고객 작성

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

- 진입점 3곳이 모두 파라미터 없는 `/reviews/new`로 가고 있다. 셋 다 고쳐야 한다.

| 진입점 | 현재 | 바꿀 것 |
|---|---|---|
| 주문 상세 `후기 작성` | `/reviews/new` | `/reviews/new?orderItemId={N}` |
| 마이페이지 `작성할 후기` | `/reviews/new` | `/mypage/reviews/writable` (A1) |
| 상품 상세 `후기 작성` | `/reviews/new` | `/mypage/reviews/writable` (A1) |

- **상품 상세에서 곧바로 폼으로 보내지 않는 이유**: 그 상품을 픽업한 주문이 여러 건일 수 있어 어느 주문 상품인지 화면이 정할 수 없다. 고르는 중간 화면을 새로 만드느니 A1이 대신한다.

**검증 순서** (Service에서, 실패 시 즉시 중단)

| # | 검증 | 실패 |
|---|---|---|
| 1 | `order_items` 존재 | `ORDER_ITEM_NOT_FOUND` 404 |
| 2 | `orders.member_id` = 인증 회원 | `ORDER_ITEM_NOT_FOUND` 404 |
| 3 | `orders.status = 'PICKED_UP'` | `NOT_PICKED_UP` 400 |
| 4 | 같은 `order_item_id`의 후기 없음 | `ALREADY_REVIEWED` 409 |

- 2번이 403이 아니라 404인 이유는 2.5와 같다 — 남의 주문 상품 id의 존재를 알려 주지 않는다.

**출력**: 주문 상품 정보(상품명·주문번호·픽업 완료 표시), 평점 4종 입력(2.2), 본문 입력

**목업에서 걷어낼 것**: 같은 폼 안에 있는 `수정하기`·`삭제하기` 버튼. 등록 화면과 수정 화면(A4)은 갈라진다.

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
- 저장 후 **D1(평점 집계)을 호출**한다. 같은 트랜잭션 안이다.

**성공 후**: `/mypage/reviews/writable`(A1)로 redirect. 방금 쓴 항목이 목록에서 빠진 것으로 완료를 확인한다.

> B3(내 후기 목록)가 생기는 조각 3부터는 `/mypage/reviews`로 옮긴다. 조각 1 시점에는 쓴 후기를 볼 화면이 아직 없다.

### A4. 후기 수정

| | |
|---|---|
| 액터 | 작성자 |
| 경로 | `GET /reviews/{id:\d+}/edit`, `POST /reviews/{id:\d+}/edit` |
| 화면 | **신규** (A2 폼을 재사용하되 주문 상품 선택 영역 없음) |

**처리**

- `PUBLISHED` 상태일 때만 수정할 수 있다. `BLOCKED`는 `BLOCKED_REVIEW` 403, `DELETED`는 `REVIEW_NOT_FOUND` 404.
- **기간 제한을 두지 않는다.** 제한을 두면 기준 시각과 시간대 판단이 따라붙고, 화면에도 남은 기간 표시가 필요해진다. 얻는 것에 비해 비싸다.
- 수정할 수 있는 것은 **평점 4종과 본문**이다. `order_item_id`·`product_id`·`member_id`는 바뀌지 않는다.
- `overall_rating`이 바뀌면 집계가 달라지므로 **D1을 호출**한다.

### A5. 후기 삭제

| | |
|---|---|
| 액터 | 작성자 |
| 경로 | `POST /reviews/{id:\d+}/delete` |

**처리**

- `PUBLISHED → DELETED` 전이. **soft delete다.** 행을 지우지 않는다.
- `BLOCKED` 후기는 삭제할 수 없다(2.1).
- 삭제 후 **D1을 호출**한다. 집계에서 빠져야 한다.

**삭제하면 그 주문 상품에는 다시 후기를 쓸 수 없다.**

- `uk_reviews_order_item`이 `order_item_id`에 UNIQUE라 `DELETED` 행이 남아 재작성 INSERT가 막힌다.
- **삭제 확인창에 이 사실을 적는다** — `삭제하면 이 주문 상품에는 다시 후기를 작성할 수 없습니다. 삭제하시겠습니까?`
- 수정이 열려 있으므로 실질적 손해는 작다. 고치고 싶으면 수정하면 된다.
- `DELETED → PUBLISHED` 재활성을 열지 않는 이유: 그러면 "삭제"가 실제로는 숨김이 되어 버튼 이름과 동작이 어긋난다.

### A6. 이미지 첨부 — 2차

`review_images` 테이블은 있고 목업에도 입력(`최대 3장`)이 있으나 **1차 범위 밖이다.**

- 1차를 이미지 없이 끝내도 후기의 목적(평점·글)은 완결된다.
- 업로드는 상품 이미지 쪽에 이미 선례가 있으므로 나중에 붙이는 비용이 크지 않다.
- **목업의 이미지 입력은 1차에서 화면에서 걷어낸다.** 동작하지 않는 입력을 남겨 두면 고객이 첨부했다고 믿는다.

---

## 4. B — 고객 조회

### B1. 상품 후기 목록

| | |
|---|---|
| 액터 | **누구나** (비로그인 포함) |
| 경로 | `GET /products/{id:\d+}/reviews` |
| 화면 | `customer/product/detail.html`의 후기 영역 (현재 `"후기 목록은 준비 중입니다"`) |

**처리**

- `WHERE r.product_id = ? AND r.status = 'PUBLISHED'`. 노출 판단은 `status` 하나다(2.1).
- 정렬은 `created_at DESC, id DESC`. **`id` tiebreaker는 반드시 붙인다** — 없으면 동일 시각 후기가 두 페이지에 중복되거나 누락된다.
- 페이징은 `PageRequest`/`PageResult` 재사용.

**출력**: 작성자명(2.6), 평점 4종, 본문, 작성일, 답글(B4)

**빈 목록**: `아직 등록된 후기가 없습니다.` 현재의 `준비 중` 문구를 대체한다.

> 상품별 후기 조회를 받쳐 줄 인덱스가 `fk_reviews_product`뿐이라 정렬에 filesort가 붙는다. 1차 데이터로는 문제가 없다. 되돌아올 계기는 `PLAN.md` R8에 있다.

### B2. 상품 평균 평점·후기 수

**화면은 이미 완성되어 있다.** `product.averageRating`·`product.reviewCount`가 상품 상세에 바인딩되어 있고 `ProductMapper.xml`이 조회한다.

**문제는 값이 영원히 0이라는 것이다.** 이 기능에 필요한 것은 화면이 아니라 D1(집계 갱신)이다. 조각 2가 끝나면 이 항목은 저절로 완료된다.

### B3. 내가 쓴 후기 목록

| | |
|---|---|
| 액터 | 로그인 고객 |
| 경로 | `GET /mypage/reviews` |
| 화면 | **신규** |

**처리**

- `WHERE r.member_id = 인증회원 AND r.status <> 'DELETED'`.
- **`BLOCKED` 후기도 본인에게는 보인다.** 숨겨진 사실을 본인이 알아야 한다. 다만 수정·삭제 버튼은 뜨지 않는다(2.1).
- 정렬 `created_at DESC, id DESC`.

**출력**: 상품명(`order_items.product_name`), 평점, 본문, 작성일, 상태 표시, 답글(B4), 수정·삭제 버튼(`PUBLISHED`일 때만)

**A1과 다른 목록이다.** A1은 *아직 안 쓴* 것, B3는 *이미 쓴* 것이다. 마이페이지에 링크 두 개가 나란히 있어야 한다.

### B4. 답글 노출

- 후기당 답글은 최대 1개다(`uk_review_replies_review`).
- B1·B3의 각 후기 아래에 접어 붙인다. 별도 화면을 만들지 않는다.
- 표시: `사장님 답글` 라벨, 답글 본문, 작성일. **답글 작성자(관리자)의 실명은 표시하지 않는다.**
- 후기가 `BLOCKED`면 답글도 함께 가려진다. 판단 기준은 후기의 `status` 하나다.

---

## 5. C — 관리자

### C1. 후기 목록

| | |
|---|---|
| 액터 | 관리자 |
| 경로 | `GET /admin/reviews` |
| 화면 | `admin/review/list.html` (목업 → 실동작 전환) |

**처리**

- **모든 상태를 보여 준다.** 숨긴 후기가 목록에 없으면 해제할 방법이 없고, 삭제된 후기는 그 주문 상품에 재작성이 막혀 있는 이유를 설명하는 자리다(A5).
- 정렬 `created_at DESC, id DESC`.

**이 화면은 조치 이력이 아니라 현재 상태를 보여 준다.**

- `reviews`에는 `blocked_at`·`blocked_by`가 없다(C4). 그래서 **숨김을 해제하면 그 후기가 과거에 숨겨졌다는 사실은 남지 않는다.** 언제 누가 조치했는지도 마찬가지다.
- 1차에서 이 상태를 받아들인다. 이력이 필요해지면 새 migration으로 메타데이터를 붙인다(9절). **그전까지 "이력을 볼 수 있다"를 다른 규칙의 근거로 쓰지 않는다** — 근거로 쓰면 실제로는 없는 것을 있다고 전제한 규칙이 쌓인다.

**출력**: 작성자, 상품명, 평점(`overall_rating`), 내용 미리보기, 작성일, 상태, 관리 버튼

**목업에서 걷어낼 것**: `삭제` 버튼. **관리자 조치는 숨김뿐이다**(커뮤니티 선례). 노출을 막는 목적은 숨김으로 이미 달성되고, 관리자가 물리 삭제까지 하면 작성자는 자기 후기가 어떻게 됐는지 알 방법이 없어진다 — `BLOCKED`는 B3에서 본인에게 보이지만 지워진 행은 아무 데도 없다.

### C2. 검색·필터

C1의 요청 파라미터다. 목업의 검색 폼을 그대로 산다.

| 파라미터 | 목업 | 처리 |
|---|---|---|
| `writer` | 작성자 검색 | `members.nickname` 부분 일치 |
| `product` | 상품명 검색 | `order_items.product_name` 부분 일치 |
| `rating` | 평점 전체 / 5점 / 4점 / 3점 이하 | `overall_rating` 기준 |

- **`rating`은 허용값을 `<choose>`로 매핑한다.** 사용자가 주소로 넣는 값을 `${}`로 이어 붙이지 않는다(`AGENTS.md` SQL 안전성).
- 모르는 값은 오류가 아니라 **전체(필터 없음)로 떨어뜨린다.**
- 부분 일치 검색은 `LIKE`이므로 `%`·`_`를 이스케이프한다. 하지 않으면 `%` 한 글자로 전체가 조회된다.
- 상태 필터는 목업에 없지만 **추가한다.** 숨긴 후기만 모아 보는 것이 관리자 화면의 실제 쓰임이다.

### C3. 후기 상세

**목록에 `상세보기` 버튼이 있는데 대응 템플릿이 없다.** 새로 만든다.

| | |
|---|---|
| 액터 | 관리자 |
| 경로 | `GET /admin/reviews/{id:\d+}` |
| 화면 | **신규** `admin/review/detail.html` |

**출력**: 작성자, 상품명, 주문번호, 평점 4종 전부, 본문 전문, 작성일·수정일, 상태, 답글(있으면), 조치 버튼(C4·C5·C6)

목록은 `내용 미리보기`만 보여 주므로 전문을 읽을 화면이 필요하다. 답글 작성(C5)도 이 화면에서 한다.

### C4. 후기 숨김

| | |
|---|---|
| 액터 | 관리자 |
| 경로 | `POST /admin/reviews/{id:\d+}/block`, `POST /admin/reviews/{id:\d+}/unblock` |

**처리**

- `PUBLISHED → BLOCKED`, `BLOCKED → PUBLISHED`. 전이 규칙은 enum이 판단하고, 어긋나면 `INVALID_REVIEW_TRANSITION` 400.
- 숨김·해제 모두 **D1을 호출**한다. 숨겨진 후기는 집계에서 빠져야 한다.
- 접근 제어는 **화면에서 버튼을 감추는 것이 아니라 Security로** 막는다.

> `reviews`에는 `blocked_at`·`blocked_reason` 같은 컬럼이 없다(`posts`에는 있다). 1차에서는 사유를 남기지 않는다. 필요해지면 새 migration으로 추가한다.

### C5. 답글 작성

**목업에 답글 UI가 전혀 없다.** 테이블·알림 타입은 준비되어 있는데 화면만 없어 새로 만든다.

| | |
|---|---|
| 액터 | 관리자 |
| 경로 | `POST /admin/reviews/{id:\d+}/replies` |
| 화면 | C3 상세 화면 안의 입력 영역 (**신규**) |
| 입력 | `content` (1~1000자) |

**처리**

- **`PUBLISHED` 후기에만 답글을 단다.** `DELETED`는 `REVIEW_NOT_FOUND` 404, `BLOCKED`는 `BLOCKED_REVIEW` 403.
  - 관리자 목록·상세는 모든 상태를 보여 주므로(C1) 검증이 없으면 **숨긴 후기에도 답글이 저장된다.** 그리고 그 답글은 아무에게도 보이지 않는다 — `BLOCKED` 후기의 답글은 B4에서 함께 가려지고, `DELETED` 후기는 B3에도 나오지 않는다.
  - 그 상태에서 D2 알림은 정상 발송된다. **고객은 알림을 받고 들어왔는데 볼 것이 없다.**
- `review_replies`에 INSERT. `admin_id`는 **인증 관리자**에서 가져온다. `members(id)` FK이므로 사람이 다는 것을 전제한다.
- `uk_review_replies_review`가 후기당 1건을 강제한다. `DuplicateKeyException`을 잡아 `ALREADY_REPLIED` 409로 바꾼다.
- 저장 후 **D2(알림)를 호출** — `CUSTOMER_REVIEW`가 후기 작성자에게 간다. 조각 7에서 붙인다.

### C6. 답글 수정

| | |
|---|---|
| 액터 | 관리자 |
| 경로 | `POST /admin/reviews/{id:\d+}/replies/edit` |
| 입력 | `content` |

**처리**

- `review_replies.content`만 바꾼다.
- **수정에는 알림을 보내지 않는다.** 알림은 답글이 처음 달릴 때 한 번이다.

**답글 삭제는 열지 않는다.**

- 근거: `uk_review_replies_review`가 후기당 1건을 강제하므로 삭제 후 재작성은 수정과 결과가 같다. 삭제를 열면 "재작성 시 알림을 또 보내는가"를 판단해야 하고, `event_key` 설계(D2)가 그만큼 복잡해진다.
- 답글을 물리고 싶으면 내용을 고친다.

---

## 6. D — 도메인 연동

### D1. 상품 평점 집계

> **이 기능은 이슈 #33 `feat(product): 리뷰 평점 및 후기 수 연동`(시은 담당)과 같은 일이다. 리뷰 단독으로 정할 수 없다.**
> 별도 이슈를 만들지 않고 #33에서 진행한다. 상세는 `PLAN.md` 조각 2와 R9.

**리뷰 쪽에서 이미 정해진 것**

- 호출 지점은 5곳이다 — A3(등록), A4(수정), A5(삭제), C4(숨김), C4(숨김 해제).
- **다섯 곳 모두 후기 쓰기와 같은 트랜잭션이다.** 후기 쪽이 먼저 커밋되고 집계가 실패하면 `average_rating`·`review_count`가 실제 후기와 **영구히 어긋난다** — 그 상품에 다음 쓰기가 올 때까지 아무도 모르는 채 잘못된 평점과 정렬이 나간다. A3만이 아니라 A4·A5·C4에도 같은 보장이 필요하다. rollback 테스트로 고정한다.
- 집계 대상은 `overall_rating`이다(2.2).
- 집계에 포함되는 후기는 `status = 'PUBLISHED'`뿐이다.
- **공개 후기가 하나도 없으면 평균은 0이다.** `AVG(overall_rating)`은 대상이 없으면 `NULL`을 돌려주는데 `products.average_rating`은 `NOT NULL DEFAULT 0.00`이다. 계약을 `COALESCE(AVG(overall_rating), 0)`과 `COUNT(*)`로 적는다.
  - 이 지점은 등록이 아니라 **마지막 한 건을 삭제하거나 숨길 때** 온다. 후기가 쌓이는 동안에는 절대 드러나지 않아서, 검증에 "마지막 공개 후기를 지운다"를 따로 넣지 않으면 통과한다.
- **리뷰에서 `UPDATE products ...`를 직접 날리지 않는다.** #33 완료 조건에 "review 도메인이 Product Mapper를 직접 사용하지 않는다"가 못 박혀 있다. 도메인 간 공개 Service 계약을 거친다.

**#33에서 합의해야 하는 것**

- 집계 책임을 product와 review 중 어느 도메인이 갖는가
- 실시간 집계 조회인가, `products` 컬럼 갱신인가
- 컬럼 갱신이면 재계산인가 증분인가

**컬럼 갱신으로 정해질 경우 가져올 커뮤니티 선례**

- **재계산 방식.** 후기는 좋아요보다 훨씬 적게 쌓여 증분의 이점이 없고, 호출 지점이 5곳이나 되어 증분은 "여기서도 조정해야 하나"를 매번 판단해야 한다.
- **`updated_at = updated_at` 보존.** 커뮤니티에서 같은 자리를 두 번 빠뜨려 화면에 `(수정됨)`이 붙는 버그가 실제로 났다.
- **잠금 순서.** `reviews` INSERT가 FK로 `products` 행에 공유 잠금을 걸고 재계산이 배타 잠금을 요구한다. 커뮤니티 좋아요와 같은 모양이므로 `SELECT ... FOR UPDATE` 선행이 후보다.

### D2. 알림

> **PR #107(`feature/notification-sms-test`)이 `dev`에 머지된 뒤에 시작한다.** 규격이 아직 `dev`에 없다(`PLAN.md` R1).

| 시점 | 타입 | 받는 사람 |
|---|---|---|
| A3 후기 등록 | `NotificationType.NEW_REVIEW` | 관리자 |
| C5 답글 작성 | `NotificationType.CUSTOMER_REVIEW` | 후기 작성자 |

- `NotificationRequest`의 `reviewId`·`reviewReplyId`를 채운다.
- **`NEW_REVIEW`를 받을 관리자가 누구인지 아직 정해지지 않았다.** `NotificationRequest`는 `receiverId`를 필수로 받는데, 관리자 계정이 둘 이상이면 누구의 ID를 넣을지 정해야 구현할 수 있다. 활성 관리자 전원에게 각각인지 대표 계정 하나인지, 그 조회를 review·member·notification 중 어디가 맡는지가 함께 걸린다. 조각 7에서 민정님과 합의한다(9절).
  - `CUSTOMER_REVIEW`는 이 문제가 없다. 받는 사람이 후기 작성자 하나로 정해져 있다.
- `event_key`를 정한다. `uk_notifications_receiver_event` UNIQUE가 중복 알림을 막는다.
- `DeliveryScope`는 명시하지 않으면 서버가 `WEB_ONLY`로 채운다.
- **알림 실패가 후기 저장을 되돌리면 안 된다.** 후기는 저장됐는데 알림만 못 간 상태가 그 반대보다 낫다.
- A4·A5·C4에는 알림을 보내지 않는다. C6(답글 수정)도 보내지 않는다(5.C6).

---

## 7. E — 기반 정리

화면이 없는 작업이다. 전부 조각 0이다.

### E1. `ReviewStatus` enum + `CHECK`

- `ReviewStatus { PUBLISHED, DELETED, BLOCKED }`를 `domain/review/entity/`에 둔다. **enum 이름을 DB 문자열로 그대로 저장**한다(4개 도메인이 이미 동일).
- 전이 규칙은 `canTransitionTo(next)`에. `null` 방어까지 `PostStatus`와 같게.
- 새 migration으로 `reviews.status` 기본값 `'VISIBLE'` → `'PUBLISHED'`, `CHECK (status IN ('PUBLISHED','DELETED','BLOCKED'))`.

### E2. 평점 범위 `CHECK`

- 평점 4종에 `CHECK (... BETWEEN 1 AND 5)`.
- 화면 검증만으로는 API 직접 호출을 막지 못한다. `TINYINT UNSIGNED`라 지금은 0과 255가 들어간다(`PLAN.md` R5).

### E3. 엔티티

- `Review`·`ReviewReply`는 **필드가 하나도 없는 빈 클래스**다. 스키마에 맞춰 채운다.
- migration 파일명은 직접 짓지 않고 `gradlew newMigration -Pdesc=<snake_case>`로 생성한다.

---

## 8. 화면 정리

| 화면 | 파일 | 상태 |
|---|---|---|
| 작성할 후기 목록 (A1) | `customer/review/writable.html` | **신규** |
| 후기 작성 폼 (A2) | `customer/review/form.html` | 목업 → 전환 (`전체` 평점 추가, 이미지·수정·삭제 버튼 제거) |
| 후기 수정 폼 (A4) | `customer/review/edit.html` | **신규** |
| 내 후기 목록 (B3) | `customer/review/my.html` | **신규** |
| 상품 후기 영역 (B1·B2·B4) | `customer/product/detail.html` | 후기 영역 교체 |
| 관리자 목록 (C1·C2) | `admin/review/list.html` | 목업 → 전환 (`삭제` 제거, 상태 필터 추가) |
| 관리자 상세 (C3·C5·C6) | `admin/review/detail.html` | **신규** |

진입점 수정: `customer/member/mypage.html`(A1·B3 링크 2개), `customer/order/detail.html`(`orderItemId` 전달), `customer/product/detail.html`(작성 버튼 → A1)

## 9. 미정

| 항목 | 결정 시점 |
|---|---|
| D1 집계 책임 도메인·갱신 방식 | 조각 2 착수 전, 이슈 #33에서 합의 |
| D2 `event_key` 규격 | 조각 7, PR #107 머지 후 |
| D2 `NEW_REVIEW` 수신 관리자 | 조각 7, 민정님과 합의 |
| 관리자 숨김 사유·조치 이력 기록 여부 | 필요해지면 새 migration (C1) |
| A6 이미지 첨부 | 2차 |

## 10. 결정 로그

**2026-08-05 — 평점은 스키마에 맞춘다.** 스키마(`overall`/`taste`/`design`/`service`)와 목업(`맛`/`디자인`/`포장`/`응대`)이 어긋나 있었다. 스키마를 택했다 — 대표값 `overall_rating`을 고객이 직접 매기면 집계(D1)가 `AVG(overall_rating)` 한 줄로 끝나고, 관리자 목록의 `★★★★★ 5.0`과 평점 필터가 전제하는 단일 값도 그대로 성립한다. 목업에 맞추면 컬럼을 바꾸는 migration이 필요하고 `overall_rating`을 계산값으로 만들어야 한다. `포장`은 `응대`로 흡수하고, 화면에 `전체` 입력을 추가한다.

**2026-08-05 — 작성 진입에 `orderItemId`를 필수로 둔다.** 진입점 3곳(주문 상세·마이페이지·상품 상세)이 모두 파라미터 없는 `/reviews/new`로 가고 있어 어느 주문 상품에 대한 후기인지 지정할 방법이 없었다. 상품 상세에서 오는 경우는 그 상품을 픽업한 주문이 여러 건일 수 있어 화면이 정할 수 없으므로, 주문 선택 화면을 새로 만드는 대신 A1(작성할 후기 목록)이 그 자리를 대신한다.

**2026-08-05 — 수정·삭제를 둘 다 열고 기간 제한을 두지 않는다.** 목업에 버튼이 둘 다 있다. 기간 제한은 기준 시각·시간대 판단과 화면의 잔여 기간 표시를 함께 데려오는데 얻는 것이 크지 않다. 대신 `uk_reviews_order_item` 때문에 **삭제하면 재작성이 막힌다**는 것을 확인했고, 삭제 확인창에 명시하기로 했다(`PLAN.md` R10).

**2026-08-05 — 후기 목록을 비로그인에게 공개한다.** 상품 상세의 평균 평점·후기 수는 이미 공개다. 근거가 되는 후기만 가리면 숫자만 있고 이유는 없는 화면이 된다. 경로를 `/products/{id}/reviews`로 두면 `SecurityConfig`를 고칠 필요도 없다.

**2026-08-06 — 관리자 목록에서 "조치 이력" 근거를 걷어냈다(PR #121 Codex P2).** C1이 "모든 상태를 보여 조치 이력을 확인한다"고 적혀 있었는데, `reviews`에는 `blocked_at`·`blocked_by`가 없어 **숨김을 해제하는 순간 그 사실이 사라진다.** 없는 것을 근거로 삼은 문장이었다. 메타데이터를 추가하는 길도 있었지만 1차 범위를 넓히지 않고 문구를 고치는 쪽을 택했다 — 모든 상태를 보여 주는 이유는 이력이 아니라 **숨긴 것을 해제하려면 목록에 있어야 하기 때문**이다. 이력 기록 여부는 9절 미정으로 남긴다.

**2026-08-06 — 상태 전이의 판단과 쓰기를 한 문장에 묶는다(PR #121 Codex P1).** enum으로 확인한 뒤 조건 없는 `UPDATE`를 날리면 A5(작성자 삭제)와 C4(관리자 숨김)가 같은 `PUBLISHED`를 읽어 둘 다 통과한다. 나중 쓰기가 앞의 조치를 덮으므로 2.1이 금지한 전이가 결과적으로 성립한다. `WHERE ... AND status = ?`로 기대 상태를 조건에 넣고 `affectedRows`로 판정한다. 2.5의 소유권 판정과 같은 모양이라 규칙이 하나 늘지 않는다.

**2026-08-06 — 집계는 다섯 호출 지점 모두 후기 쓰기와 같은 트랜잭션이고, 평균은 `COALESCE`로 0을 채운다(PR #121 Codex P1).** 트랜잭션 보장이 A3에만 적혀 있었다. `AVG`는 대상이 없으면 `NULL`인데 `products.average_rating`은 `NOT NULL`이라 **마지막 공개 후기를 지우는 순간** 걸린다. 둘 다 #33 합의 대상이 아니라 어느 방식을 택하든 성립해야 하는 조건이므로 리뷰 쪽 전제로 적어 #33에 넘긴다.

**2026-08-06 — local preview에서 `/reviews/**`를 조각 1에 뺀다(PR #121 Codex P2).** `app.mockup.public-preview=true`인 local 프로필이 `GET /reviews/**`를 `permitAll`로 먼저 매칭하고 있어 2.3의 "설정을 고칠 필요가 없다"가 성립하지 않았다. `rds`에서는 `false`라 **로컬에서만 뚫려 있다.** `/community/new`를 같은 이유로 뺀 선례가 바로 위 주석에 있다.

**2026-08-06 — 답글은 `PUBLISHED` 후기에만 단다(PR #121 Codex P2).** 관리자 화면이 모든 상태를 보여 주는데 C5에 상태 검증이 없어, 숨긴 후기에 답글을 달면 저장과 알림은 되고 고객 화면에서는 답글이 가려졌다. 알림을 받고 들어와도 볼 것이 없는 상태다.

**2026-08-05 — 답글은 작성·수정만 열고 삭제는 닫는다.** `uk_review_replies_review`가 후기당 1건을 강제하므로 삭제 후 재작성은 수정과 결과가 같다. 삭제를 열면 재작성 시 알림을 다시 보낼지 판단해야 하고 `event_key` 설계가 복잡해진다. 닫아 두면 **알림 발송 시점이 답글 최초 작성 한 곳으로 고정된다.**
