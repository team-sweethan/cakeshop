# Review 진행 계획

> 흐름의 정본은 `docs/review/FLOW.md`, 기능 단위 명세의 정본은 `docs/review/SPEC.md`다. 규칙이 더 굳으면 `docs/review/DOMAIN.md`를 세우고 그쪽이 정본이 된다.
> 이 문서는 **작업 순서, 진행 상태, 결정 로그, 위험**만 다룬다.
> 규칙이 바뀌면 정본 문서를 고치고, 여기에는 "언제 왜 바꿨는지"만 한 줄 남긴다.

## 작업 방식

커뮤니티와 같다. 한 번에 **수직 조각 하나**만 하고, 조각 하나는 DB → Mapper → Service → Controller → 화면 → 테스트까지 닿아야 한다. 끝나면 브라우저에서 동작하고 `gradlew test`가 통과하고 CI가 초록불이어야 한다.

**예외 하나: 조각 1과 2는 한 배포 단위다. PR도 하나로 묶는다**(2026-08-06 변경, 원래는 "따로 열되 함께 머지").

- 묶은 이유는 **조각 1 단독 머지를 절차가 아니라 구조로 막기 위해서**다. PR을 따로 두면 "조각 2 없이 머지하지 않는다"를 사람이 기억해야 하는데, 하나면 그럴 일이 없다.
- 부수 효과로 **두 도메인 계약(주문·상품)이 실제 호출부와 함께 리뷰된다.** 인터페이스만 떼어 놓고 보면 왜 그 인자인지가 안 보인다.

- 조각 1이 `POST /reviews`를 여는데 집계는 조각 2에서 생긴다. 그 사이에 쌓인 후기는 `average_rating`·`review_count`에 반영되지 않고, `ProductMapper.xml`이 그 두 컬럼으로 정렬하므로 **후기가 쌓이는데 정렬은 계속 0을 본다**(R2).
- `SPEC.md` D1의 "후기 쓰기와 집계는 같은 트랜잭션"도 조각 1 단독으로는 지킬 수가 없다 — 부를 대상이 없다.
- 조각 0·3~7은 이 제약이 없다.

**AI가 실수하면 그 자리에서 고치고 끝내지 않고 하네스로 승격시킨다.**

## 조각 순서

| # | 조각 | 이슈 | 상태 | 내용 |
|---|---|---|---|---|
| 0 | 준비 | #108 | **완료** | `ReviewStatus` enum, `CHECK` 제약 migration, 평점 범위 제약, `status` 기본값 정리 |
| 1 | 작성 | #109 | 구현 완료·확인 대기 | 자격 검증(`PICKED_UP` + 본인 주문), `reviews` INSERT, 주문상품당 1건, **작성할 후기 목록 화면 신규**(SPEC A1) |
| 2 | 평점 집계 | **#33** | 구현 완료·확인 대기 | 집계 연동. **조각 1과 한 PR이다.** 시은 담당 이슈와 같은 일이라 담당자 확인이 필요하다 |
| 3 | 조회·노출 | #110 | 대기 | 상품 상세 후기 목록, 페이징, 내 후기 조회 |
| 4 | 수정·삭제 | #111 | 대기 | soft delete, 집계 재호출 |
| 5 | 관리자 숨김 | #112 | 대기 | 관리자 목록·검색·필터, `BLOCKED` **전이와 해제 양쪽**, 집계 재호출, **관리자 상세 화면 신규**(SPEC C3) |
| 6 | 관리자 답글 | #113 | 대기 | `review_replies`, 후기당 1건, 작성·수정. **답글 UI 신규**(SPEC C5·C6) |
| 7 | 알림 연동 | #114 | 대기 | `NEW_REVIEW`, `CUSTOMER_REVIEW`. **PR #107 머지 이후** |
| 8 | (2차) 이미지 첨부 | — | 범위 밖 | `review_images` |

### 왜 이 순서인가

**0이 먼저인 이유**는 커뮤니티 조각 0과 같다. 이후 모든 쿼리가 `status` 조건에 의존하고, 어휘를 나중에 바꾸면 전부 되돌아와야 한다. 지금은 `reviews`에 데이터가 한 건도 없어 **어휘 교체 비용이 가장 싼 시점**이다.

**2를 1 바로 뒤에 두는 것이 이 계획의 제약이다.** `products.average_rating`·`review_count`는 **이미 `ProductMapper.xml`이 정렬 기준으로 쓰고 있는데**(`review_count DESC, average_rating DESC`) 갱신하는 코드가 없다. 조각 1만 하고 2를 미루면 후기가 쌓이는 동안 상품 정렬은 계속 0을 기준으로 돈다 — 커뮤니티에서 조회수 중복 방지를 인기글보다 먼저 둔 것과 같은 자리다(`docs/community/PLAN.md` R13). 다만 방향은 반대다. 커뮤니티는 **믿을 수 없는 값이 쌓이는** 문제였고, 여기는 **값이 아예 안 움직이는** 문제다.

**2를 먼저 세우면 4·5가 싸진다.** 수정·삭제·숨김은 전부 집계를 다시 불러야 하는데, 재계산 메서드가 이미 있으면 호출 한 줄이다. 나중에 만들면 세 조각을 되돌아와야 한다.

**7이 마지막인 이유**는 의존이 팀 밖에 있기 때문이다. 알림 규격은 `feature/notification-sms-test`(PR #107)에 있고 아직 `dev`에 없다. 1~6은 알림 없이 완결되므로, 머지를 기다리며 멈출 이유가 없다.

### 조각 0 — 준비 (#108)

- `ReviewStatus { PUBLISHED, DELETED, BLOCKED }` + `canTransitionTo` (`PostStatus` 선례를 그대로 따름, `null` 방어 포함)
- 새 migration: `reviews.status` 기본값을 `'VISIBLE'` → `'PUBLISHED'`로 바꾸고 `CHECK (status IN ('PUBLISHED','DELETED','BLOCKED'))` 추가
  - **`CHECK`를 걸기 전에 기존 `'VISIBLE'` 행을 `'PUBLISHED'`로 변환한다.** 남아 있으면 제약 추가가 배포 중 실패한다. 지금 `reviews`는 비어 있고 INSERT 경로도 없지만, 한 줄로 막을 수 있는 것을 환경 상태에 맡기지 않는다. 선례: `V20260730_123931__apply_product_preparation_policy.sql`(보정 UPDATE 후 CHECK)
- 새 migration: 평점 4종에 `CHECK (rating BETWEEN 1 AND 5)`
  - **`status`와 달리 사전 보정 UPDATE를 두지 않는다.** 근거는 `SPEC.md` 2.2
- migration 파일명은 직접 짓지 않고 `gradlew newMigration -Pdesc=<snake_case>`로 생성
- **`docs/status-design.md`의 `reviews.status` 행을 확정으로 갱신한다.** 그 문서가 상태값 인벤토리의 정본이고 지금 `VISIBLE / HIDDEN ?` · `☐ 열림`으로 남아 있다. 154절이 "☐ 항목을 확정하면 인벤토리 행을 갱신하고 **enum + DDL을 함께 커밋한다**"고 못 박고 있다

**검증**: 전이 규칙 단위 테스트(허용/금지 각 케이스), Testcontainers로 `CHECK`가 잘못된 상태값·평점을 거부하는지, 기본값이 `PUBLISHED`인지.

### 조각 1 — 작성 (#109)

**선행: 주문 도메인 계약 시그니처를 주환님과 합의한다.** 자격 검증과 A1 목록이 `order_items`·`orders`를 직접 JOIN하는 모양이라 `docs/conventions.md` 15절과 어긋난다. 시그니처가 정해지기 전에는 착수하지 않는다. 구현이 없으면 stub으로 진행한다(15절). 상세는 `SPEC.md` A1.

- 자격 검증: 본인 주문인지, `orders.status == PICKED_UP`인지 — **주문 도메인 공개 계약으로 받는다**
- `uk_reviews_order_item` UNIQUE로 주문상품당 1건. 중복은 `DuplicateKeyException`을 잡아 도메인 에러로 바꾼다(커뮤니티 신고 선례)
- `reviews.product_id`는 요청값을 믿지 않고 `order_items.product_id`에서 파생시킨다 (R4)
- `ReviewErrorCode`에 `REVIEW_NOT_FOUND`·`ORDER_ITEM_NOT_FOUND`·`ALREADY_REVIEWED` 추가(SPEC 2.5의 번호를 그대로). **소유권 전용 코드는 만들지 않는다** — 404다
- `SecurityConfig`의 local preview 목록에서 `/reviews/**`를 뺀다(SPEC 2.3)
- `POST /reviews` 핸들러와 `form.html` 실동작 전환

**검증**: 남의 주문에 작성 거부, `PICKED_UP`이 아닌 주문 거부, 같은 주문상품에 두 번 작성 거부, 평점 범위 밖 거부.

### 조각 2 — 평점 집계 (#33)

> **이 조각은 이슈 #33 `feat(product): 리뷰 평점 및 후기 수 연동`(시은 담당)과 같은 일이다.**
> 별도 이슈를 만들지 않고 #33에서 진행한다.
> **`domain/product/`는 시은님 담당이므로 착수 전에 #33에 계약 시그니처를 올려 확인받는다**(`AGENTS.md` — 공개 Service 인터페이스는 먼저 협의).

**방식은 2026-08-06에 정했다.** 상세는 `SPEC.md` D1이 정본이다.

- `products` **컬럼 갱신**, **재계산**, **상품 도메인 소유**
- 계약 `ProductRatingService`, SQL은 신규 `ProductReviewMapper` + `mapper/product/ProductReviewMapper.xml`
- 형판은 `ProductStockService`(주문·결제에 재고 변경을 공개하는 기존 쓰기 계약)
- **잠금 문장은 새 매퍼에 복제하지 않는다.** 기존 `ProductMapper.findSalesInfoByIdForUpdate`를 재사용하고 Service가 두 매퍼를 함께 주입받는다
- 호출 순서는 **잠금 → `reviews` 쓰기 → 집계**. 뒤집으면 FK 공유 잠금이 배타 잠금으로 승격되어 교착이다

**만드는 것**

| 위치 | 무엇 |
|---|---|
| `domain/product/service/ProductRatingService.java` | 공개 계약 (**신규**) |
| `domain/product/mapper/ProductReviewMapper.java` | 매퍼 인터페이스 (**신규**) |
| `mapper/product/ProductReviewMapper.xml` | 집계 SELECT + `UPDATE products` (**신규**) |
| 리뷰 Service | A3의 호출 5곳 중 등록 자리 |

**검증**: 작성 후 집계 일치, **동시 요청 후 `review_count == reviews 실제 개수`**, 집계 갱신이 `products.updated_at`을 건드리지 않는지, **마지막 공개 후기를 지웠을 때 평균이 0**(`COALESCE`), 후기 저장과 집계가 한 트랜잭션인지(rollback).

### 조각 3 — 조회·노출 (#110)

**선행 2건.** 시그니처가 정해지기 전에는 착수하지 않는다(`SPEC.md` 2.7·9절).

- **수민님** — 작성자 표시명 계약(ID 묶음 조회). `members`를 JOIN하지 않는다
- **주환님** — B3·C1·C3용 주문 스냅샷 계약. **조각 1 계약은 *미작성* 항목만 돌려주므로 이미 쓴 후기의 상품명·주문번호를 받을 자리가 없다**(`SPEC.md` 9절)
- ~~**시은님** — 상품 상세에 후기를 붙이는 방식~~ → 2026-08-06 합의 완료. 미리보기 3개는 모델 주입, 전체는 별도 화면(`SPEC.md` B1)

- 상품 상세 후기 미리보기 — 최신 **고정 3개**. **페이징하지 않는다**
- 후기 전체 목록 화면(신규) — **여기만** `PageRequest`/`PageResult` 재사용
- 내 후기 조회(마이페이지 또는 주문 상세)
- 노출 판단은 `status = 'PUBLISHED'` 하나로 (`docs/community/DOMAIN.md` 4.1 선례)

**검증**: `BLOCKED`·`DELETED` 후기가 목록에 안 나오는지, 페이징 경계(전체 목록·B3), 탈퇴 회원 표시명, **상품 상세 화면 자체가 후기 3개를 렌더링하는지**(`/products/{id}/reviews` 응답만 보면 구멍이 통과한다).

### 조각 4 — 수정·삭제 (#111)

- 소유권 검증은 인증 사용자 기준으로 Service에서
- 삭제 = `PUBLISHED → DELETED` 전이, 집계 재호출

**검증**: 남의 후기 수정·삭제 거부, `BLOCKED` 후기 수정·삭제 거부, 삭제 후 집계 반영.

### 조각 5 — 관리자 숨김 (#112)

**선행: 검색 계약(`writer`·`product`)을 수민·주환님과 합의한다.** `members`·`order_items`를 JOIN하지 않는다(`SPEC.md` 2.7·C2). 후기를 먼저 페이지한 뒤 이름으로 거르면 **화면의 건수와 실제 건수가 갈린다.**

- 관리자 목록(`GET /admin/reviews`)과 검색·필터, 관리자 상세 화면(SPEC C3)
- **숨김과 해제를 함께 만든다** — `POST .../block`(`PUBLISHED → BLOCKED`), `POST .../unblock`(`BLOCKED → PUBLISHED`). **집계 재호출도 양쪽 다.**
  - 해제를 빼면 잘못 숨긴 후기를 되돌릴 방법이 없다. C1이 모든 상태를 보여 주는 이유가 바로 해제 대상을 찾기 위해서다
- 목업의 `삭제` 버튼은 걷어낸다 — 관리자 조치는 숨김뿐이다(`docs/community/DOMAIN.md` 6.7 선례)

**검증**: 비관리자의 숨김·해제 접근 거부(화면 숨김이 아니라 Security), 숨김 후 집계에서 빠지는지, **해제 후 집계에 돌아오는지**, 금지된 전이(`DELETED → BLOCKED`) 거부.

### 조각 6 — 관리자 답글 (#113)

- `review_replies` INSERT. `uk_review_replies_review`가 후기당 1건을 강제
- `admin_id`는 `members(id)` FK — 사람이 다는 것을 전제한다
- **고객 화면의 답글 노출도 이 조각이다**(SPEC B4) — B1(상품 후기 목록)과 B3(내 후기)의 각 후기 아래에 붙인다. 빼면 관리자는 답글을 쓰고 알림까지 나가는데 **고객이 들어올 화면에 답글이 없다**
- `PUBLISHED` 후기에만 답글을 달고, 확인과 INSERT를 한 문장에 묶는다(SPEC C5)

**검증**: 같은 후기에 두 번째 답글 거부, 비관리자 접근 거부, **B1·B3에 답글이 보이는지**, `BLOCKED` 후기의 답글이 함께 가려지는지, 답글 작성과 숨김의 동시 실행.

### 조각 7 — 알림 연동 (#114)

**PR #107이 `dev`에 머지된 뒤에 시작한다.**

- 작성 시 → `NotificationType.NEW_REVIEW` (관리자에게)
- 답글 시 → `NotificationType.CUSTOMER_REVIEW` (작성자에게)
- `NotificationRequest`의 `reviewId`·`reviewReplyId`를 채운다
- `event_key`를 정한다 — `uk_notifications_receiver_event` UNIQUE가 중복 알림을 막는다
- `DeliveryScope`는 명시하지 않으면 서버가 `WEB_ONLY`로 채운다

**검증**: 같은 이벤트로 두 번 호출해도 알림이 하나인지, 알림 실패가 후기 저장을 되돌리지 않는지.

## 위험

| # | 위험 | 상태 |
|---|---|---|
| R1 | **알림 규격이 `dev`에 없다.** `NotificationType.NEW_REVIEW`·`CUSTOMER_REVIEW`, `notifications.review_id`·`review_reply_id`, `NotificationRequest.reviewId`가 전부 PR #107(미머지)에만 있다. 머지 전에 조각 7을 시작하면 컴파일되지 않는 의존이 생기고, 규격이 바뀌면 다시 써야 한다. 그래서 순서로 풀었다 — 1~6은 알림 없이 완결된다 | 조각 7을 마지막에 두어 회피 |
| R2 | **`products.average_rating`·`review_count`는 이미 상품 정렬에 쓰이는데 갱신되지 않는다.** 값을 쓰는 곳은 `seed-local.sql`(776~785행)뿐이라 운영에서는 영구히 0이다. 조각 1만 하고 2를 미루면 **후기가 쌓이는데 정렬은 안 바뀌는** 상태가 된다 | 조각 2를 1 직후에 배치. **조각 1·2를 한 배포 단위로 묶어** 조각 1 단독 머지를 막는다 (2026-08-06 강화) |
| R3 | **`reviews.status` 기본값 `'VISIBLE'`은 코드베이스 어디에도 없는 어휘다.** 지금은 데이터가 없어 교체 비용이 가장 싸다 | 조각 0(PR #125)에서 해소 — 보정 UPDATE 후 `chk_reviews_status`, 기본값도 `'PUBLISHED'` (SPEC 2.1) |
| R4 | **`reviews.product_id`가 `order_items.product_id`와 어긋날 수 있다.** 요청값을 믿으면 남의 상품에 후기를 붙일 수 있다 | 조각 1에서 `order_items`로부터 파생. 테스트로 고정 (SPEC A3) |
| R5 | **평점 범위 제약이 DB에 없다.** `TINYINT UNSIGNED`라 0과 255가 들어간다 | 조각 0(PR #125)에서 해소 — 평점 4종에 제약을 하나씩. 화면·서버를 합쳐 세 겹 (SPEC 2.2) |
| R6 | **목업의 평점 4종이 스키마와 어긋난다.** 목업 `맛`/`디자인`/`포장`/`응대` vs 스키마 `overall`/`taste`/`design`/`service` | **해소** — 2026-08-05에 스키마를 택하고 `포장`을 `응대`로 흡수, 화면에 `전체` 추가 (SPEC 2.2) |
| R7 | **PR #107의 migration이 V0의 `notifications`·`notification_deliveries`를 `DROP` 후 재생성한다.** 리뷰와 직접 관련은 없지만, 조각 7이 그 migration 이후 스키마를 전제하므로 머지 순서가 어긋나면 로컬에서 재현되지 않는 실패가 난다 | 조각 7 착수 시 `dev` 기준으로 확인 |
| R8 | **상품별 후기 조회를 받쳐 줄 인덱스가 `fk_reviews_product`뿐이라 정렬에 filesort가 붙는다.** 1차 데이터로는 문제가 없고 온라인 DDL로 나중에 붙일 수 있다 | 1차에선 수용. **되돌아올 계기**: 한 상품의 후기가 수백 건을 넘거나 상품 상세가 눈에 띄게 느려지면 `(product_id, status, created_at, id)` 복합 인덱스를 새 migration으로 |
| R9 | **조각 2는 상품 도메인(시은 담당)에 파일을 만든다.** 방식은 2026-08-06에 정했고 남은 것은 **계약 시그니처와 전용 매퍼에 대한 담당자 동의**다. 합의 없이 만들면 시그니처가 뒤집힐 때 리뷰 쪽 호출부까지 따라 움직인다 | 착수 전 #33에 계약 시그니처를 올려 확인 (2026-08-06 갱신) |
| R10 | **후기를 삭제하면 그 주문 상품에는 다시 쓸 수 없다.** soft delete라 행이 남고 `uk_reviews_order_item`이 재작성을 막는다 | 삭제 확인창에 명시. A1도 조건 없는 `NOT EXISTS`로 걸러, 목록에 띄워 놓고 저장에서 거절하는 일을 막는다 (SPEC A5·A1) |

## 결정 로그

**정한 것과 그 근거는 정본 문서 본문에 있다. 여기에는 언제 무엇을 정했는지만 한 줄씩 쌓는다.**

| 날짜 | 정한 것 | 정본 |
|---|---|---|
| 08-05 | 상태값을 `PostStatus` 어휘(`PUBLISHED`/`DELETED`/`BLOCKED`)로. 스키마 기본값 `'VISIBLE'`을 쓰는 곳이 코드베이스에 없다 | SPEC 2.1 |
| 08-05 | 평점은 스키마에 맞춘다. 대표값은 고객이 직접 매기는 `overall_rating`, 목업 `포장`은 `응대`로 흡수, 화면에 `전체` 입력 추가 | SPEC 2.2 |
| 08-05 | 작성 진입에 `orderItemId` 필수. 상품 상세에서 곧바로 폼으로 보내지 않고 A1이 그 자리를 대신한다 | SPEC A2 |
| 08-05 | 수정·삭제 둘 다 열고 기간 제한은 두지 않는다. 대신 삭제하면 재작성이 막히는 것을 확인창에 명시 | SPEC A4·A5 |
| 08-05 | 후기 목록을 비로그인에게 공개한다. 경로는 `/products/{id}/reviews` | SPEC 2.3·B1 |
| 08-05 | 답글은 작성·수정만 열고 삭제는 닫는다. 알림 발송 시점이 최초 작성 한 곳으로 고정된다 | SPEC C6 |
| 08-05 | 알림 연동을 마지막 조각으로 뺐다 — 규격이 `dev`가 아니라 PR #107에 있다. 조각 1~6은 알림 없이 완결된다 | 조각 7·R1 |
| 08-05 | 평점 집계를 조각 2로 앞당겼다. `average_rating`·`review_count`가 이미 상품 정렬에 쓰이고 있다 | 조각 2·R2 |
| 08-05 | 조각 2는 새 이슈를 만들지 않고 #33에서 진행한다. 같은 일을 두 곳에서 추적하지 않는다 | 조각 2·R9 |
| 08-06 | 집계는 **컬럼 갱신·재계산·상품 도메인 소유**. 계약 `ProductRatingService`, SQL은 신규 `ProductReviewMapper` | SPEC D1 |
| 08-06 | 잠금 문장을 새 매퍼에 복제하지 않는다. 기존 `findSalesInfoByIdForUpdate`를 재사용 | SPEC D1 |
| 08-06 | **다른 도메인 테이블 JOIN을 전면 금지한다**(2.7 신설). 표시용 예외를 두지 않는다 — 커뮤니티가 `members`를 8곳에서 직접 JOIN하지만 **리뷰는 따르지 않는다** | SPEC 2.7 |
| 08-06 | 문서 중복을 걷어냈다. **규칙과 근거는 SPEC 본문에 한 번만 둔다** — 다른 문서는 가리킬 뿐 다시 적지 않는다 | SPEC 머리말 |
| 08-06 | 상품 상세에는 후기 **최신 3개만** 모델 주입으로 붙이고, 전체는 별도 화면으로 뺐다. 개수를 고정해 상세 렌더링의 페이징 문제를 없앴다 (시은님 합의) | SPEC B1 |
| 08-06 | **남의 도메인에 만드는 클래스·메서드에는 작성자·담당자 헤더 주석을 반드시 단다** | `domain/review/CLAUDE.md` |
| 08-06 | 도메인 경계에 **예외 하나**를 열었다 — 자기 소유 파생 컬럼을 유지하기 위한 집계 읽기는 전용 매퍼에서 허용. 표시·검색·업무 규칙은 여전히 금지. 기준은 "전용 매퍼냐"가 아니라 "무엇을 위해 읽느냐"다 | SPEC 2.7 |
| 08-06 | 그 결과 **집계 SQL은 상품이 소유**로 확정. 리뷰가 값을 계산해 넘기면 상품이 자기 컬럼인데도 검증할 수 없다 | SPEC D1 |
| 08-06 | 조각 1·2를 **한 PR로 묶었다**. 단독 머지를 사람의 기억이 아니라 구조로 막고, 두 도메인 계약이 실제 호출부와 함께 리뷰되게 했다 | 작업 방식 |
| 08-06 | 계약 제안서를 `docs/review/proposals/`에 임시로 두기로 했다. **합의 후 삭제**하고 결론만 SPEC·PLAN에 남긴다 | `proposals/README.md` |

**리뷰 지적을 반영한 것은 여기에 남기지 않는다.** 규칙은 정본 문서에, 언제 왜 고쳤는지는 커밋 메시지에 이미 있다(`git log -- docs/review/`). 로그에 남길 것은 **여러 안 중에 골랐고 나중에 다시 물을 만한 것**뿐이다.
