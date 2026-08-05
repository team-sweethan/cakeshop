# Review 진행 계획

> 흐름의 정본은 `docs/review/FLOW.md`, 기능 단위 명세의 정본은 `docs/review/SPEC.md`다. 규칙이 더 굳으면 `docs/review/DOMAIN.md`를 세우고 그쪽이 정본이 된다.
> 이 문서는 **작업 순서, 진행 상태, 결정 로그, 위험**만 다룬다.
> 규칙이 바뀌면 정본 문서를 고치고, 여기에는 "언제 왜 바꿨는지"만 한 줄 남긴다.

## 작업 방식

커뮤니티와 같다. 한 번에 **수직 조각 하나**만 하고, 조각 하나는 DB → Mapper → Service → Controller → 화면 → 테스트까지 닿아야 한다. 끝나면 브라우저에서 동작하고 `gradlew test`가 통과하고 CI가 초록불이어야 한다.

**AI가 실수하면 그 자리에서 고치고 끝내지 않고 하네스로 승격시킨다.**

## 조각 순서

| # | 조각 | 이슈 | 상태 | 내용 |
|---|---|---|---|---|
| 0 | 준비 | #108 | 대기 | `ReviewStatus` enum, `CHECK` 제약 migration, 평점 범위 제약, `status` 기본값 정리 |
| 1 | 작성 | #109 | 대기 | 자격 검증(`PICKED_UP` + 본인 주문), `reviews` INSERT, 주문상품당 1건, **작성할 후기 목록 화면 신규**(SPEC A1) |
| 2 | 평점 집계 | **#33** | 대기 | 집계 연동. **1 직후에 붙인다.** 시은 담당 이슈와 같은 일이라 선행 합의가 필요하다 |
| 3 | 조회·노출 | #110 | 대기 | 상품 상세 후기 목록, 페이징, 내 후기 조회 |
| 4 | 수정·삭제 | #111 | 대기 | soft delete, 집계 재호출 |
| 5 | 관리자 숨김 | #112 | 대기 | 관리자 목록·검색·필터, `BLOCKED` 전이, 집계 재호출, **관리자 상세 화면 신규**(SPEC C3) |
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
- migration 파일명은 직접 짓지 않고 `gradlew newMigration -Pdesc=<snake_case>`로 생성
- **`docs/status-design.md`의 `reviews.status` 행을 확정으로 갱신한다.** 그 문서가 상태값 인벤토리의 정본이고 지금 `VISIBLE / HIDDEN ?` · `☐ 열림`으로 남아 있다. 154절이 "☐ 항목을 확정하면 인벤토리 행을 갱신하고 **enum + DDL을 함께 커밋한다**"고 못 박고 있다

**검증**: 전이 규칙 단위 테스트(허용/금지 각 케이스), Testcontainers로 `CHECK`가 잘못된 상태값·평점을 거부하는지, 기본값이 `PUBLISHED`인지.

### 조각 1 — 작성 (#109)

- 자격 검증: `order_items → orders`로 본인 주문인지, `orders.status == PICKED_UP`인지
- `uk_reviews_order_item` UNIQUE로 주문상품당 1건. 중복은 `DuplicateKeyException`을 잡아 도메인 에러로 바꾼다(커뮤니티 신고 선례)
- `reviews.product_id`는 요청값을 믿지 않고 `order_items.product_id`에서 파생시킨다 (R4)
- `ReviewErrorCode`에 `REVIEW_NOT_FOUND`·`ORDER_ITEM_NOT_FOUND`·`ALREADY_REVIEWED` 추가(SPEC 2.5의 번호를 그대로 쓴다). **소유권 전용 코드는 만들지 않는다** — 남의 주문 상품·후기를 건드리면 404다. 403을 두면 id를 훑어 존재 여부를 알아낼 수 있다
- `SecurityConfig`의 local preview 목록에서 `/reviews/**`를 뺀다(SPEC 2.3). 남겨 두면 익명 사용자가 작성 폼까지 들어온다. `/community/new` 선례가 같은 자리에 있다
- `POST /reviews` 핸들러와 `form.html` 실동작 전환

**검증**: 남의 주문에 작성 거부, `PICKED_UP`이 아닌 주문 거부, 같은 주문상품에 두 번 작성 거부, 평점 범위 밖 거부.

### 조각 2 — 평점 집계 (#33)

> **이 조각은 이슈 #33 `feat(product): 리뷰 평점 및 후기 수 연동`(시은 담당)과 같은 일이다. 리뷰 단독으로 정할 수 없다.**
> 별도 이슈를 만들지 않고 #33에서 진행한다. 착수 전에 아래 선행 합의가 필요하다.

**#33이 이미 못 박은 것** — 완료 조건에 **"review 도메인이 Product Mapper를 직접 사용하지 않는다"** 가 있다. 리뷰 쪽에서 `UPDATE products ...`를 직접 날리는 방식은 쓸 수 없고, **도메인 간 공개 Service 계약**을 거쳐야 한다.

**#33이 미합의로 남긴 것**

- 집계 책임을 product와 review 중 어느 도메인이 갖는가
- 실시간 집계 조회인가, `products` 컬럼 갱신인가
- 리뷰 숨김·삭제 시 반영 정책

**합의가 컬럼 갱신 쪽으로 정해질 경우** 커뮤니티 선례를 가져올 자리가 둘 있다.

- **재계산 방식**(`docs/community/DOMAIN.md` 6.5). 후기는 좋아요보다 훨씬 적게 쌓여 증분의 이점이 없고, 갱신 경로가 늘 때마다(수정·삭제·숨김) "여기서도 조정해야 하나"를 판단하지 않아도 된다.
- **`updated_at = updated_at` 보존.** 커뮤니티에서 같은 자리를 두 번 빠뜨려 화면에 `(수정됨)`이 붙는 버그가 실제로 났다.
- 잠금 순서도 따져야 한다 — `reviews` INSERT가 FK로 `products` 행에 공유 잠금을 걸고 재계산이 배타 잠금을 요구한다. 커뮤니티 좋아요와 같은 모양이므로 같은 해법(`SELECT ... FOR UPDATE` 선행)이 후보다.

**실시간 집계 쪽으로 정해지면** `products.average_rating`·`review_count` 컬럼은 쓰지 않게 되고, **`ProductMapper.xml`의 정렬(`review_count DESC, average_rating DESC`)을 함께 고쳐야 한다.** 이 경우 R2는 해소가 아니라 소멸이다.

**검증**(컬럼 갱신 방식일 때): 작성 후 집계 일치, **동시 요청 후 `review_count == reviews 실제 개수`**, 집계 갱신이 `products.updated_at`을 건드리지 않는지.

### 조각 3 — 조회·노출 (#110)

- 상품 상세에 후기 목록. `PageRequest`/`PageResult` 재사용
- 내 후기 조회(마이페이지 또는 주문 상세)
- 노출 판단은 `status = 'PUBLISHED'` 하나로 (`docs/community/DOMAIN.md` 4.1 선례)

**검증**: `BLOCKED`·`DELETED` 후기가 목록에 안 나오는지, 페이징 경계, 탈퇴 회원 표시명.

### 조각 4 — 수정·삭제 (#111)

- 소유권 검증은 인증 사용자 기준으로 Service에서
- 삭제 = `PUBLISHED → DELETED` 전이, 집계 재호출

**검증**: 남의 후기 수정·삭제 거부, `BLOCKED` 후기 수정·삭제 거부, 삭제 후 집계 반영.

### 조각 5 — 관리자 숨김 (#112)

- 관리자 목록(`GET /admin/reviews`), `PUBLISHED → BLOCKED` 전이, 집계 재호출
- 목업의 `삭제` 버튼은 걷어낸다 — 관리자 조치는 숨김뿐이다(`docs/community/DOMAIN.md` 6.7 선례)

**검증**: 비관리자의 숨김 접근 거부(화면 숨김이 아니라 Security), 숨김 후 집계 반영.

### 조각 6 — 관리자 답글 (#113)

- `review_replies` INSERT. `uk_review_replies_review`가 후기당 1건을 강제
- `admin_id`는 `members(id)` FK — 사람이 다는 것을 전제한다

**검증**: 같은 후기에 두 번째 답글 거부, 비관리자 접근 거부.

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
| R2 | **`products.average_rating`·`review_count`는 이미 상품 정렬에 쓰이는데 갱신되지 않는다.** 값을 쓰는 곳은 `seed-local.sql`(776~785행)뿐이라 운영에서는 영구히 0이다. 후기가 없는 지금은 드러나지 않지만, 조각 1만 하고 2를 미루면 **후기가 쌓이는데 정렬은 안 바뀌는** 상태가 된다 | 조각 2를 1 직후에 배치 |
| R3 | **`reviews.status` 기본값 `'VISIBLE'`은 코드베이스 어디에도 없는 어휘다.** 지금 바꾸지 않으면 이후 모든 쿼리가 이 값을 전제하게 되고, 그때는 데이터까지 있어 교체 비용이 오른다 | 조각 0에서 해소 |
| R4 | **`reviews.product_id`가 `order_items.product_id`와 어긋날 수 있다.** 같은 값에 이르는 경로가 둘이라 요청값을 그대로 믿으면 남의 상품에 후기를 붙일 수 있다 | 조각 1에서 `order_items`로부터 파생. 테스트로 고정 |
| R5 | **평점 범위 제약이 DB에 없다.** `TINYINT UNSIGNED`라 0과 255가 들어간다. 화면 검증만으로는 API 직접 호출을 막지 못한다 | 조각 0에서 `CHECK` 추가 |
| R6 | **목업의 평점 4종이 스키마와 어긋난다.** 목업은 `맛`/`디자인`/`포장`/`응대`, 스키마는 `overall`/`taste`/`design`/`service`다. `포장`에 대응하는 컬럼이 없고 `overall_rating`에 대응하는 입력이 없다. 관리자 목업의 `★★★★★ 5.0`과 평점 필터는 **단일 평점을 전제**하므로 대표값도 함께 정해야 한다 | 조각 0 착수 전 결정 필요 |
| R7 | **PR #107의 migration이 V0의 `notifications`·`notification_deliveries`를 `DROP` 후 재생성한다.** 리뷰와 직접 관련은 없지만, 조각 7이 그 migration 이후 스키마를 전제하므로 머지 순서가 어긋나면 로컬에서 재현되지 않는 실패가 난다 | 조각 7 착수 시 `dev` 기준으로 확인 |
| R8 | **상품별 후기 조회를 받쳐 줄 인덱스가 `fk_reviews_product`뿐이다.** `product_id`로 좁힌 뒤 정렬하면 filesort가 붙는다. 1차 데이터로는 문제가 없고 온라인 DDL로 나중에 붙일 수 있다. **되돌아올 계기**: 한 상품의 후기가 수백 건을 넘거나 상품 상세가 눈에 띄게 느려지면 `(product_id, status, created_at, id)` 복합 인덱스를 새 migration으로 추가한다 | 1차에선 수용 |
| R9 | **조각 2는 리뷰 단독으로 결정할 수 없다.** 이슈 #33(시은 담당)이 같은 일이고, 완료 조건에 "review 도메인이 Product Mapper를 직접 사용하지 않는다"가 이미 못 박혀 있다. 집계 책임 도메인과 갱신 방식은 미합의다. **합의 없이 조각 1을 끝내면** 후기는 쌓이는데 반영 경로가 없는 상태로 멈춘다 — R2가 실제로 드러나는 지점이 바로 여기다. **되돌아올 계기가 아니라 선행 조건이다**: 조각 1(#109) 착수와 동시에 #33에서 합의를 시작한다 | 조각 1과 병행해 #33에서 합의 (2026-08-05) |
| R10 | **후기를 삭제하면 그 주문 상품에는 다시 쓸 수 없다.** `uk_reviews_order_item`이 `order_item_id`에 UNIQUE라, soft delete로 `DELETED`가 되어도 행이 남아 재작성 INSERT가 막힌다. 수정을 열어 두었으므로 실질적 손해는 작지만, 모르고 지우면 되돌릴 방법이 없다 | 삭제 확인창에 명시. 작성할 후기 목록(A1)도 `NOT EXISTS`로 걸러, 목록에 띄워 놓고 저장에서 거절하는 일을 막는다 (2026-08-05) |

## 결정 로그

**2026-08-06 — PR #121 Codex 리뷰 10건을 반영했다.** P1 5건·P2 5건 전부 실재하는 지적이었다. 상태값 정본(`docs/status-design.md`)이 아직 `VISIBLE / HIDDEN ?`으로 열려 있다는 것과, local preview가 `GET /reviews/**`를 이미 공개하고 있다는 것은 **코드를 열어 확인했다.** 아홉 건은 SPEC·PLAN을 고쳐 닫았고, 관리자 조치 이력만 결정이 필요해 근거 문구를 걷어내는 쪽으로 정했다(SPEC 10절, C1). `NEW_REVIEW` 수신 관리자는 조각 7 차례라 SPEC 9절 미정으로 넘겼다.

**2026-08-05 — 상태값을 `PostStatus`와 같은 어휘로 간다.** 스키마 기본값이 `'VISIBLE'`인데 이 어휘를 쓰는 곳이 코드베이스에 없다. 팀이 실제로 쓰는 패턴은 둘이고(`ACTIVE`/`INACTIVE` — 상품·옵션·쿠폰, `PUBLISHED`/`DELETED`/`BLOCKED` — 게시글), 리뷰는 **고객이 쓰고 관리자가 숨기는** 구조라 후자와 같다. 목업의 `숨김`이 `BLOCKED`에 대응한다. `ACTIVE`/`INACTIVE`는 관리자가 노출을 켜고 끄는 것이라 작성자 삭제를 담을 자리가 없다.

**2026-08-05 — 알림 연동을 마지막 조각으로 뺐다.** 규격은 이미 팀원이 만들어 두었고 리뷰가 채울 자리(`reviewId`, `reviewReplyId`, 타입 2종)까지 있다. 문제는 위치다 — `dev`가 아니라 PR #107에 있다. 조각 1~6이 알림 없이 완결되므로 순서로 푼다.

**2026-08-05 — 조각 2를 이슈 #33으로 넘긴다.** 새 이슈를 만들지 않는다. 같은 일을 다루는 이슈가 이미 있고 담당자가 시은님이다. 리뷰 쪽에서 별도 이슈를 세우면 같은 작업이 두 곳에서 추적되고, 무엇보다 **#33이 이미 정해 둔 제약**(review가 Product Mapper를 직접 쓰지 않는다)을 모르는 채로 구현이 진행될 수 있다. PLAN의 조각 2는 그 이슈를 가리키는 자리로만 남긴다.

**2026-08-05 — 평점 집계를 조각 2로 앞당겼다.** 원래 조회·노출 뒤에 두려 했으나, `average_rating`·`review_count`가 **이미 상품 정렬에 쓰이고 있다**는 것을 확인했다. 갱신 없이 후기만 쌓이면 정렬이 계속 0을 본다. 커뮤니티 R13과 같은 자리이고, 뒤이을 조각 4·5·6이 전부 이 재계산을 호출하므로 먼저 세우는 편이 싸다.
