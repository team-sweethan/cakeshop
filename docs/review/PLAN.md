# Review 진행 계획

> 규칙의 정본은 `DOMAIN.md`, 기능 단위 명세의 정본은 `specs/`다. 결정의 배경 중 한 줄로 재구성이 안 되는 것은 `decisions/`에 있다.
> 이 문서는 **작업 순서, 진행 상태, 위험, 결정 로그**만 다룬다. 자동 검증 체계는 `HARNESS.md`가 정본이고 여기서는 번호만 부른다.
> 규칙이 바뀌면 정본 문서를 고치고, 여기에는 "언제 왜 바꿨는지"만 한 줄 남긴다.

## 작업 방식

커뮤니티와 같다. 한 번에 **수직 조각 하나**만 하고, 조각 하나는 DB → Mapper → Service → Controller → 화면 → 테스트까지 닿아야 한다. 끝나면 브라우저에서 동작하고 `gradlew test`가 통과하고 CI가 초록불이어야 한다.

**예외 하나: 조각 1과 2는 한 배포 단위다.** PR은 따로 열되 **조각 2 없이 조각 1만 `dev`에 머지하지 않는다.**

- 조각 1이 `POST /reviews`를 여는데 집계는 조각 2에서 생긴다. 그 사이에 쌓인 후기는 `average_rating`·`review_count`에 반영되지 않고, `ProductMapper.xml`이 그 두 컬럼으로 정렬하므로 **후기가 쌓이는데 정렬은 계속 0을 본다**(R2).
- `specs/product-rating.md`의 "후기 쓰기와 집계는 같은 트랜잭션"도 조각 1 단독으로는 지킬 수가 없다 — 부를 대상이 없다.
- 조각 0·3~7은 이 제약이 없다.

**AI가 실수하면 그 자리에서 고치고 끝내지 않고 하네스로 승격시킨다** — 올리는 곳은 `HARNESS.md`다.

## 조각 순서

| # | 조각 | 이슈 | 상태 | 내용 |
|---|---|---|---|---|
| 0 | 준비 | #108 | **완료** (PR #125) | `ReviewStatus` enum, `CHECK` 제약 migration, 평점 범위 제약, `status` 기본값 정리 → `history/2026-08-slice-0-schema.md` |
| 1 | 작성 | #109 | 대기 | 자격 검증(`PICKED_UP` + 본인 주문), `reviews` INSERT, 주문상품당 1건, **작성할 후기 목록 화면 신규**(A1) |
| 2 | 평점 집계 | **#33** | 대기 | 집계 연동. **1 직후에 붙인다.** 시은 담당 이슈와 같은 일이라 선행 합의가 필요하다 |
| 3 | 조회·노출 | #110 | 대기 | 상품 상세 후기 목록, 페이징, 내 후기 조회 |
| 4 | 수정·삭제 | #111 | 대기 | soft delete, 집계 재호출 |
| 5 | 관리자 숨김 | #112 | 대기 | 관리자 목록·검색·필터, `BLOCKED` **전이와 해제 양쪽**, 집계 재호출, **관리자 상세 화면 신규**(C3) |
| 6 | 관리자 답글 | #113 | 대기 | `review_replies`, 후기당 1건, 작성·수정, **고객 화면 답글 노출**(B4) |
| 7 | 알림 연동 | #114 | 대기 | `NEW_REVIEW`, `CUSTOMER_REVIEW` |
| 8 | (2차) 이미지 첨부 | — | 범위 밖 | `review_images` |

**`history/`로 옮기는 기준: 머지되었고 지금도 구속하지 않는 것만.** 조각의 착수 항목과 검증 목록이 그렇다. 위험과 결정 로그는 과거 기록처럼 보여도 **현재 구속**이므로 대상이 아니다(`HARNESS.md`도 마찬가지다).

### 왜 이 순서인가

**0이 먼저인 이유**는 커뮤니티 조각 0과 같다. 이후 모든 쿼리가 `status` 조건에 의존하고, 어휘를 나중에 바꾸면 전부 되돌아와야 한다. 당시 `reviews`에 데이터가 한 건도 없어 **어휘 교체 비용이 가장 싼 시점**이었다.

**2를 1 바로 뒤에 두는 것이 이 계획의 제약이다.** `products.average_rating`·`review_count`는 **이미 `ProductMapper.xml`이 정렬 기준으로 쓰고 있는데**(`review_count DESC, average_rating DESC`) 갱신하는 코드가 없다. 조각 1만 하고 2를 미루면 후기가 쌓이는 동안 상품 정렬은 계속 0을 기준으로 돈다 — 커뮤니티에서 조회수 중복 방지를 인기글보다 먼저 둔 것과 같은 자리다(`docs/community/PLAN.md` R13). 다만 방향은 반대다. 커뮤니티는 **믿을 수 없는 값이 쌓이는** 문제였고, 여기는 **값이 아예 안 움직이는** 문제다.

**2를 먼저 세우면 4·5가 싸진다.** 수정·삭제·숨김은 전부 집계를 다시 불러야 하는데, 재계산 메서드가 이미 있으면 호출 한 줄이다. 나중에 만들면 세 조각을 되돌아와야 한다.

**7이 마지막인 이유는 이제 의존이 아니라 편의다.** 알림 규격은 2026-08-07에 `dev`에 들어왔다(R1). 1~6이 알림 없이 완결되므로 순서를 바꿀 이유가 없을 뿐, 기다리는 것은 없다.

### 조각 1 — 작성 (#109)

**주문 데이터는 `domain/order/`에 두는 연동 계약으로 받는다.** 자격 검증과 A1 목록이 `order_items`·`orders`를 직접 JOIN하는 모양이라 `docs/conventions.md` 15절과 어긋난다. 상세는 `specs/review-write.md` A1.

**합의를 기다리지 않는다.** 15.2가 `<소유 도메인><참조 도메인>` 이름을 지시하고, 15.8은 확인을 **사전 허락이 아니라 담당자 검토**로 정의한다. 그래서 순서가 "물어보고 만든다"가 아니라 **"만들고 PR에서 확인받는다"** 이다.

| | |
|---|---|
| 만드는 것 | `domain/order/`에 `OrderReviewQueryService`·`OrderReviewMapper`·`mapper/order/OrderReviewMapper.xml` |
| 지키는 조건 | **주환님 기존 파일을 수정하지 않는다.** `OrderMapper`에 메서드를 얹지 않고 새 파일만 만든다 |
| 확인 방법 | AGENTS.md의 담당 외 도메인 헤더 주석 + PR 리뷰어로 주환님 지정 (15.8) |
| 선례 | `OrderCouponQueryService`(정후님이 주문 폴더에 만든 것), `MemberCommunityQueryService` |

**`CommandService`는 만들지 않는다** — 리뷰가 주문에 하는 것은 조회뿐이다. 쓸 수도 있으니 미리 만들지 않는다(15.8), 네 조합을 미리 만들지 않는다(15.5).

- 자격 검증: 본인 주문인지, `orders.status == PICKED_UP`인지 — **주문 도메인 공개 계약으로 받는다**
- `uk_reviews_order_item` UNIQUE로 주문상품당 1건. 중복은 `DuplicateKeyException`을 잡아 도메인 에러로 바꾼다(커뮤니티 신고 선례)
- `reviews.product_id`는 요청값을 믿지 않고 `order_items.product_id`에서 파생시킨다 (R4)
- `ReviewErrorCode`에 `REVIEW_NOT_FOUND`·`ORDER_ITEM_NOT_FOUND`·`ALREADY_REVIEWED` 추가(`DOMAIN.md` 2.5의 번호를 그대로). **소유권 전용 코드는 만들지 않는다** — 404다
- `SecurityConfig`의 local preview 목록에서 `/reviews/**`를 뺀다(`DOMAIN.md` 2.3)
- `POST /reviews` 핸들러와 `form.html` 실동작 전환

**검증**: 남의 주문에 작성 거부, `PICKED_UP`이 아닌 주문 거부, 같은 주문상품에 두 번 작성 거부, 평점 범위 밖 거부.

### 조각 2 — 평점 집계 (#33)

> **이 조각은 이슈 #33 `feat(product): 리뷰 평점 및 후기 수 연동`(시은 담당)과 같은 일이다.**
> 별도 이슈를 만들지 않고 #33에서 진행한다.
> **`domain/product/`는 시은님 담당이므로 착수 전에 #33에 계약 시그니처를 올려 확인받는다**(`AGENTS.md` — 공개 Service 인터페이스는 먼저 협의).

**방식은 2026-08-06에 정했다.** 상세는 `specs/product-rating.md`가 정본이고, 방향이 네 번 뒤집힌 경로는 `decisions/ADR-001-rating-aggregation-ownership.md`에 있다.

- `products` **컬럼 갱신**, **재계산**. 집계는 리뷰가 계산하고 쓰기만 상품이 맡는다
- 계약 `ProductReviewCommandService`, SQL은 신규 `ProductReviewMapper` + `mapper/product/ProductReviewMapper.xml`
- 형판은 `ProductStockService`(주문·결제에 재고 변경을 공개하는 기존 쓰기 계약)
- **잠금 문장은 새 매퍼에 복제하지 않는다.** 기존 `ProductMapper.findSalesInfoByIdForUpdate`를 재사용하고 Service가 두 매퍼를 함께 주입받는다
- 호출 순서는 **잠금 → `reviews` 쓰기 → 집계**. 뒤집으면 FK 공유 잠금이 배타 잠금으로 승격되어 교착이다

**만드는 것**

| 위치 | 무엇 |
|---|---|
| `domain/product/service/ProductReviewCommandService.java` | 공개 계약 (**신규**) |
| `domain/product/mapper/ProductReviewMapper.java` | 매퍼 인터페이스 (**신규**) |
| `mapper/product/ProductReviewMapper.xml` | `UPDATE products` (**신규**) |
| `mapper/review/ReviewMapper.xml` | 집계 SELECT (`FOR UPDATE`) |
| 리뷰 Service | A3의 호출 5곳 중 등록 자리 |

**검증**: 작성 후 집계 일치, **동시 요청 후 `review_count == reviews 실제 개수`**, 집계 갱신이 `products.updated_at`을 건드리지 않는지, **마지막 공개 후기를 지웠을 때 평균이 0**(`COALESCE`), 후기 저장과 집계가 한 트랜잭션인지(rollback).

### 조각 3 — 조회·노출 (#110)

**선행 2건.** 시그니처가 정해지기 전에는 착수하지 않는다(`DOMAIN.md` 2.7·4).

- **수민님** — 작성자 표시명 계약(ID 묶음 조회). `members`를 JOIN하지 않는다
- **주환님** — B3·C1·C3용 주문 스냅샷 계약. **조각 1 계약은 *미작성* 항목만 돌려주므로 이미 쓴 후기의 상품명·주문번호를 받을 자리가 없다**(`DOMAIN.md` 4)
- ~~**시은님** — 상품 상세에 후기를 붙이는 방식~~ → 2026-08-06 합의 완료. 미리보기 3개는 모델 주입, 전체는 별도 화면(`specs/review-read.md` B1)

- 상품 상세 후기 미리보기 — 최신 **고정 3개**. **페이징하지 않는다**
- 후기 전체 목록 화면(신규) — **여기만** `PageRequest`/`PageResult` 재사용
- 내 후기 조회(마이페이지 또는 주문 상세)
- 노출 판단은 `status = 'PUBLISHED'` 하나로 (`docs/community/DOMAIN.md` 4.1 선례)

**이 조각이 세우는 하네스: H7 · H8 · H9** (`HARNESS.md`). **별도 작업으로 미루지 않는다** — 이 저장소에는 미룬 하네스가 안 온 기록이 있다(커뮤니티 H1b를 10c에서 미뤘다가 10d에서야 했다).

- **H7** — `DOMAIN.md` 2.5 오류 코드 표 ↔ `ReviewErrorCode`. 코드·메시지·HTTP 상태. **코드에 있는데 표에 없어도 실패**
- **H8** — `DOMAIN.md` 2.3 경로 표 ↔ 컨트롤러 매핑. 매핑이 있는데 1절 기능 표 `현재` 열이 `없음`이면 실패
- **H9** — `DOMAIN.md` 3절 화면 표 ↔ `templates/{customer,admin}/review/**` 실존. 화면 명세를 `screens/`로 나누고 커뮤니티 `CommunityScreenDocTests`에 해당하는 문구 대조까지 여기 붙인다
- **기존 검사가 무는 것을 스크립트가 아니라 테스트로 박는다**(번호를 새로 받지 않는다 — 기존 행을 지키는 일이다). 검사 뿌리를 픽스처 디렉터리로 받게 바꾸고, PR #154에서 나온 변형 10건을 회귀 테스트로 남긴다. 근거는 `HARNESS.md` 끝

**검증**: `BLOCKED`·`DELETED` 후기가 목록에 안 나오는지, 페이징 경계(전체 목록·B3), 탈퇴 회원 표시명, **상품 상세 화면 자체가 후기 3개를 렌더링하는지**(`/products/{id}/reviews` 응답만 보면 구멍이 통과한다).

### 조각 4 — 수정·삭제 (#111)

- 소유권 검증은 인증 사용자 기준으로 Service에서
- 삭제 = `PUBLISHED → DELETED` 전이, 집계 재호출

**검증**: 남의 후기 수정·삭제 거부, `BLOCKED` 후기 수정·삭제 거부, 삭제 후 집계 반영.

### 조각 5 — 관리자 숨김 (#112)

**선행: 검색 계약(`writer`·`product`)을 수민·주환님과 합의한다.** `members`·`order_items`를 JOIN하지 않는다(`DOMAIN.md` 2.7, `specs/review-admin.md` C2). 후기를 먼저 페이지한 뒤 이름으로 거르면 **화면의 건수와 실제 건수가 갈린다.**

**관리자 화면이지만 ReadModel이 아니다.** 15.9는 집계·요약하는 통계·대시보드에만 열린다.

- 관리자 목록(`GET /admin/reviews`)과 검색·필터, 관리자 상세 화면(C3)
- **숨김과 해제를 함께 만든다** — `POST .../block`(`PUBLISHED → BLOCKED`), `POST .../unblock`(`BLOCKED → PUBLISHED`). **집계 재호출도 양쪽 다.**
  - 해제를 빼면 잘못 숨긴 후기를 되돌릴 방법이 없다. C1이 모든 상태를 보여 주는 이유가 바로 해제 대상을 찾기 위해서다
- 목업의 `삭제` 버튼은 걷어낸다 — 관리자 조치는 숨김뿐이다(`docs/community/DOMAIN.md` 6.7 선례)

**H7~H9가 여기서 실제로 무는지 확인한다** — 조각 5가 추가하는 오류 코드·경로·화면이 그 세 검사에 그대로 걸린다. 새 번호를 받지 않고 조각 3에서 세운 것을 시험하는 자리다.

**검증**: 비관리자의 숨김·해제 접근 거부(화면 숨김이 아니라 Security), 숨김 후 집계에서 빠지는지, **해제 후 집계에 돌아오는지**, 금지된 전이(`DELETED → BLOCKED`) 거부.

### 조각 6 — 관리자 답글 (#113)

- `review_replies` INSERT. `uk_review_replies_review`가 후기당 1건을 강제
- `admin_id`는 `members(id)` FK — 사람이 다는 것을 전제한다
- **고객 화면의 답글 노출도 이 조각이다**(B4) — B1(상품 후기 목록)과 B3(내 후기)의 각 후기 아래에 붙인다. 빼면 관리자는 답글을 쓰고 알림까지 나가는데 **고객이 들어올 화면에 답글이 없다**
- `PUBLISHED` 후기에만 답글을 달고, 확인과 INSERT를 한 문장에 묶는다(`specs/review-reply.md` C5)

**검증**: 같은 후기에 두 번째 답글 거부, 비관리자 접근 거부, **B1·B3에 답글이 보이는지**, `BLOCKED` 후기의 답글이 함께 가려지는지, 답글 작성과 숨김의 동시 실행.

### 조각 7 — 알림 연동 (#114)

- 작성 시 → `NotificationType.NEW_REVIEW` (관리자에게)
- 답글 시 → `NotificationType.CUSTOMER_REVIEW` (작성자에게)
- `NotificationRequest`의 `reviewId`·`reviewReplyId`를 채운다
- `event_key`를 정한다 — `uk_notifications_receiver_event` UNIQUE가 중복 알림을 막는다
- `DeliveryScope`는 명시하지 않으면 서버가 `WEB_ONLY`로 채운다

**검증**: 같은 이벤트로 두 번 호출해도 알림이 하나인지, 알림 실패가 후기 저장을 되돌리지 않는지.

## 검증

**자동 검증 체계의 정본은 `HARNESS.md`다.** 어떤 검사가 있고 무엇이 적용됐는지는 **그 표만** 말한다 — 여기에 "지금 적용된 것은 …"을 옮겨 적으면 그 사본이 낡는다. 사본을 없애려고 표를 뺀 PR이 같은 자리에 새 사본을 만들고 있었다(PR #156 Codex).

여기에는 표를 다시 적지 않는다 — **이번 조각이 추가·변경하는 번호만** 각 조각 절의 **선언 줄**에 나열한다(형태는 `HARNESS.md` H6 행). 그 번호가 `HARNESS.md`에 실존하는지는 H6이 보고, 표가 다시 이 문서로 새어 들어오는 것은 H4가 막는다.

**AI가 실수하면 그 자리에서 고치고 끝내지 않고 하네스로 승격시킨다** — 올리는 곳은 `HARNESS.md`다.

## 위험

| # | 위험 | 상태 |
|---|---|---|
| R1 | ~~알림 규격이 `dev`에 없다~~ — **해소 (2026-08-07).** PR #107이 #128로 머지되어 `NotificationType.NEW_REVIEW`·`CUSTOMER_REVIEW`, `notifications.review_id`·`review_reply_id`, `event_key` + `uk_notifications_receiver_event`, `NotificationRequest`(`dto/form/`)가 전부 `dev`에 있다. 직접 확인함 | 해소 |
| R2 | **`products.average_rating`·`review_count`는 이미 상품 정렬에 쓰이는데 갱신되지 않는다.** 값을 쓰는 곳은 `seed-local.sql`뿐이라 운영에서는 영구히 0이다. 조각 1만 하고 2를 미루면 **후기가 쌓이는데 정렬은 안 바뀌는** 상태가 된다 | 조각 2를 1 직후에 배치. **조각 1·2를 한 배포 단위로 묶어** 조각 1 단독 머지를 막는다 (2026-08-06 강화) |
| R3 | **`reviews.status` 기본값 `'VISIBLE'`은 코드베이스 어디에도 없는 어휘였다** | **해소** — 조각 0(PR #125). 보정 UPDATE 후 `chk_reviews_status`, 기본값도 `'PUBLISHED'` (`DOMAIN.md` 2.1) |
| R4 | **`reviews.product_id`가 `order_items.product_id`와 어긋날 수 있다.** 요청값을 믿으면 남의 상품에 후기를 붙일 수 있다 | 조각 1에서 `order_items`로부터 파생. 테스트로 고정 (`specs/review-write.md` A3) |
| R5 | **평점 범위 제약이 DB에 없었다.** `TINYINT UNSIGNED`라 0과 255가 들어갔다 | **해소** — 조각 0(PR #125). 평점 4종에 제약을 하나씩. 화면·서버를 합쳐 세 겹 (`DOMAIN.md` 2.2) |
| R6 | **목업의 평점 4종이 스키마와 어긋난다.** 목업 `맛`/`디자인`/`포장`/`응대` vs 스키마 `overall`/`taste`/`design`/`service` | **해소** — 2026-08-05에 스키마를 택하고 `포장`을 `응대`로 흡수, 화면에 `전체` 추가 (`DOMAIN.md` 2.2) |
| R7 | ~~PR #107의 migration이 V0의 `notifications`·`notification_deliveries`를 `DROP` 후 재생성한다~~ — **해소 (2026-08-07).** 실제로 머지된 `V20260804_150010__add_notification_tables.sql`은 테이블을 재생성하지 않고 제약·컬럼을 `ALTER`한다. 이미 `dev`에 있으므로 머지 순서 문제도 없다. 직접 확인함 | 해소 |
| R8 | **상품별 후기 조회를 받쳐 줄 인덱스가 `fk_reviews_product`뿐이라 정렬에 filesort가 붙는다.** 1차 데이터로는 문제가 없고 온라인 DDL로 나중에 붙일 수 있다 | 1차에선 수용. **되돌아올 계기**: 한 상품의 후기가 수백 건을 넘거나 상품 상세가 눈에 띄게 느려지면 `(product_id, status, created_at, id)` 복합 인덱스를 새 migration으로 |
| R9 | **조각 2는 상품 도메인(시은 담당)에 파일을 만든다.** 방식은 2026-08-06에 정했고 남은 것은 **계약 시그니처와 전용 매퍼에 대한 담당자 동의**다. 합의 없이 만들면 시그니처가 뒤집힐 때 리뷰 쪽 호출부까지 따라 움직인다 | 착수 전 #33에 계약 시그니처를 올려 확인 (`decisions/ADR-001-rating-aggregation-ownership.md`) |
| R10 | **후기를 삭제하면 그 주문 상품에는 다시 쓸 수 없다.** soft delete라 행이 남고 `uk_reviews_order_item`이 재작성을 막는다 | 삭제 확인창에 명시. A1도 조건 없는 `NOT EXISTS`로 걸러, 목록에 띄워 놓고 저장에서 거절하는 일을 막는다 (`specs/review-write.md`, `specs/review-edit-delete.md`) |
| R11 | **`RDS`에는 migration이 자동 적용되지 않는다.** `rds` 프로파일이 `flyway.enabled: false`라 승인된 별도 절차로만 반영된다. 조각 1은 `status` 기본값 `PUBLISHED`와 `CHECK`를 전제하므로 착수 전에 반영 여부를 확인해야 한다 | 조각 1 착수 시 확인 (2026-08-07 추가) |

## 결정 로그

**정한 것과 그 근거는 정본 문서 본문에 있다. 여기에는 언제 무엇을 정했는지만 한 줄씩 쌓는다.**

| 날짜 | 정한 것 | 정본 |
|---|---|---|
| 08-05 | 상태값을 `PostStatus` 어휘(`PUBLISHED`/`DELETED`/`BLOCKED`)로. 스키마 기본값 `'VISIBLE'`을 쓰는 곳이 코드베이스에 없다 | `DOMAIN.md` 2.1 |
| 08-05 | 평점은 스키마에 맞춘다. 대표값은 고객이 직접 매기는 `overall_rating`, 목업 `포장`은 `응대`로 흡수, 화면에 `전체` 입력 추가 | `DOMAIN.md` 2.2 |
| 08-05 | 작성 진입에 `orderItemId` 필수. 상품 상세에서 곧바로 폼으로 보내지 않고 A1이 그 자리를 대신한다 | `specs/review-write.md` A2 |
| 08-05 | 수정·삭제 둘 다 열고 기간 제한은 두지 않는다. 대신 삭제하면 재작성이 막히는 것을 확인창에 명시 | `specs/review-edit-delete.md` |
| 08-05 | 후기 목록을 비로그인에게 공개한다. 경로는 `/products/{id}/reviews` | `DOMAIN.md` 2.3, `specs/review-read.md` B1 |
| 08-05 | 답글은 작성·수정만 열고 삭제는 닫는다. 알림 발송 시점이 최초 작성 한 곳으로 고정된다 | `specs/review-reply.md` C6 |
| 08-05 | 알림 연동을 마지막 조각으로 뺐다 — 당시 규격이 `dev`가 아니라 PR #107에 있었다. 조각 1~6은 알림 없이 완결된다 | 조각 7 (전제는 R1에서 해소) |
| 08-05 | 평점 집계를 조각 2로 앞당겼다. `average_rating`·`review_count`가 이미 상품 정렬에 쓰이고 있다 | 조각 2·R2 |
| 08-05 | 조각 2는 새 이슈를 만들지 않고 #33에서 진행한다. 같은 일을 두 곳에서 추적하지 않는다 | 조각 2·R9 |
| 08-06 | 집계는 **컬럼 갱신·재계산·상품 도메인 소유**. 계약 `ProductRatingService`, SQL은 신규 `ProductReviewMapper` | `ADR-001` (**같은 날 뒤집힘**) |
| 08-06 | 잠금 문장을 새 매퍼에 복제하지 않는다. 기존 `findSalesInfoByIdForUpdate`를 재사용 | `specs/product-rating.md` |
| 08-06 | **다른 도메인 테이블 JOIN을 전면 금지한다.** 표시용 예외를 두지 않는다 | `DOMAIN.md` 2.7 |
| 08-06 | 문서 중복을 걷어냈다. **규칙과 근거는 정본 본문에 한 번만 둔다** — 다른 문서는 가리킬 뿐 다시 적지 않는다 | `DOMAIN.md` 머리말 |
| 08-06 | 상품 상세에는 후기 **최신 3개만** 모델 주입으로 붙이고, 전체는 별도 화면으로 뺐다. 개수를 고정해 상세 렌더링의 페이징 문제를 없앴다 (시은님 합의) | `specs/review-read.md` B1 |
| 08-06 | **남의 도메인에 만드는 클래스·메서드에는 작성자·담당자 헤더 주석을 반드시 단다** | `domain/review/CLAUDE.md` |
| 08-06 | 도메인 경계에 **예외 하나**를 열었다 — 자기 소유 파생 컬럼을 유지하기 위한 집계 읽기는 전용 매퍼에서 허용 | `ADR-001` (**같은 날 걷힘**) |
| 08-06 | **위 예외를 걷었다.** 갈리는 기준이 "무엇을 위해 읽느냐"에서 **"읽기 전용이냐"**로 바뀌었다 | `docs/conventions.md` 15 |
| 08-06 | 그 결과 **D1의 방향이 뒤집혔다** — 리뷰가 평균·건수를 계산해 상품에 넘긴다. 08-06 오전에 기각했던 안이다 | `ADR-001` |
| 08-06 | **ReadModel을 열었다** — 여러 도메인을 **집계·요약**하는 통계·대시보드는 JOIN해도 된다. **관리자 화면이라는 것은 근거가 아니다** — 관리자 후기 검색은 조건이 남의 도메인에서 올 뿐 결국 후기 목록이라 대상이 아니고, 조각 5의 선행 합의도 그대로다 | `docs/conventions.md` 15.9 |
| 08-06 | 그 결과 **집계 SQL은 리뷰가 소유**로 확정. 평점 집계는 ReadModel 예외에 해당하지 않는다 — 쓰기의 근거가 되는 읽기이기 때문이다. **대가는 상품이 자기 컬럼인데도 받은 값을 검증할 수 없다는 것** | `ADR-001` |
| 08-07 | 문서를 `DOMAIN`/`specs`/`PLAN`/`decisions`/`history`로 나누고 `FLOW.md`·`SPEC.md`를 없앴다. 동시에 `ReviewDocTests`(H1~H5)를 세웠다 | `HARNESS.md` |
| 08-08 | 하네스 표를 `HARNESS.md`로 뺐다. **표가 다섯 줄일 때 옮긴다** — 44행이 된 뒤에는 이동 diff를 아무도 안 읽는다(커뮤니티 PR #146에서 틀린 문장 여섯 건이 그렇게 통과했다). 경계를 지킬 H4 확장과 H6을 같은 PR에 넣었다 | `HARNESS.md` |

**`decisions/`로 승격하는 기준: 위 한 줄로 재구성이 안 되고, 여러 안을 실제로 비교했고, 되물을 사람이 있는 것.** 나머지는 여기 한 줄로 남긴다 — 시간순 스캔이 이 표에 있어야 한다.

**리뷰 지적을 반영한 것은 여기에 남기지 않는다.** 규칙은 정본 문서에, 언제 왜 고쳤는지는 커밋 메시지에 이미 있다(`git log -- docs/review/`). 로그에 남길 것은 **여러 안 중에 골랐고 나중에 다시 물을 만한 것**뿐이다.
