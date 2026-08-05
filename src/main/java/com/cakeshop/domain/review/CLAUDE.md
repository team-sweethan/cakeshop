# Review 도메인 작업 규칙

이 디렉터리(`domain/review/`)와 아래 관련 파일을 수정할 때는 **반드시 다음 세 문서를 먼저 읽고 그에 따른다.**

- **`docs/review/SPEC.md`** — 기능 명세의 정본. 기능 목록, 상태 모델, 평점, 권한, 입력 검증, 오류 코드, 화면. 여기 적힌 것과 다르게 구현하지 않는다.
- **`docs/review/FLOW.md`** — 도메인 전체 흐름의 정본. 주문 → 후기 → 상품 → 알림이 어디서 이어지고 어디가 끊겨 있는지.
- **`docs/review/PLAN.md`** — 작업 순서, 진행 상태, 위험, 결정 로그. 지금 어느 조각을 하는지 여기서 확인한다.

**아직 `docs/review/DOMAIN.md`는 없다.** 규칙이 더 굳으면 `docs/community/DOMAIN.md` 형식을 따라 세우고 그쪽이 정본이 된다. 그전까지는 `SPEC.md`가 정본이다.

화면 명세 파일(`docs/community/screens/*.md` 같은 것)도 아직 없다. 커뮤니티처럼 문서와 템플릿을 대조하는 테스트가 없으므로, **화면 문구는 `SPEC.md` 8절과 각 기능 절이 유일한 근거다.**

프로젝트 전체 규칙은 저장소 루트 `AGENTS.md`가 상위 정본이며, 충돌하면 `AGENTS.md`가 우선한다.

## 지금 상태

**동작하는 기능이 하나도 없다.** 있는 것은 스키마(`V0__initial_schema.sql`)와 목업 화면, 그리고 빈 스텁뿐이다.

| 파일 | 상태 |
|---|---|
| `entity/Review.java`, `entity/ReviewReply.java` | 필드 없는 빈 클래스 |
| `service/ReviewService.java` | TODO 주석만 |
| `mapper/ReviewMapper.java`, `mapper/review/ReviewMapper.xml` | 비어 있음 |
| `controller/ReviewController.java` | `GET /reviews/new` 목업 반환만 |
| `controller/ReviewAdminController.java` | `GET /admin/reviews` 목업 반환만 |
| `error/ReviewErrorCode.java` | `REVIEW_001` 하나 |

즉 **기존 구현에 맞출 것이 없다.** 판단 기준은 코드가 아니라 위 세 문서다.

## 이 도메인의 파일 범위

- `src/main/java/com/cakeshop/domain/review/**`
- `src/main/resources/mapper/review/*.xml` (고객 `ReviewMapper.xml`, 관리자가 필요해지면 `ReviewAdminMapper.xml`)
- `src/main/resources/templates/customer/review/**`
- `src/main/resources/templates/admin/review/**`
- `src/test/java/com/cakeshop/domain/review/**`
- 새 Flyway migration (`gradlew newMigration -Pdesc=<snake_case>`로 생성)

### 범위 밖이지만 건드려야 하는 파일

진입점 때문에 남의 도메인 화면을 고쳐야 한다(`SPEC.md` 8절). **링크 한 줄 수정을 넘어서면 손대지 말고 먼저 알린다.**

| 파일 | 무엇 | 담당 |
|---|---|---|
| `customer/member/mypage.html` | A1·B3 링크 2개 | 수민 |
| `customer/order/detail.html` | `후기 작성` 링크에 `orderItemId` 전달 | 주환 |
| `customer/product/detail.html` | 후기 영역 교체 (`"준비 중"` → B1) | 시은 |

`customer/product/detail.html`과 `customer/order/detail.html`은 **PR #74(주환, 열려 있음)가 지금 함께 고치고 있다.** 조각 3 착수 전에 `dev` 기준을 다시 확인한다.

## 작업 절차

1. `docs/review/SPEC.md`와 `docs/review/PLAN.md`를 읽는다.
2. `PLAN.md`의 조각 순서를 확인하고, **현재 조각의 범위를 벗어나는 구현을 하지 않는다.**
3. 구현 후 `./gradlew clean test`를 실행한다.
4. 해당 조각에 명시된 검증 항목을 확인한다.
5. 변경 파일, 실행한 검증, 남은 위험을 보고한다.

## 규칙을 벗어나야 할 때

SPEC.md의 결정이 잘못됐거나 부족하다고 판단되면 **코드로 우회하지 말고 먼저 알린다.** 규칙과 코드가 어긋나면 다음 작업자(사람이든 AI든)가 어느 쪽을 믿어야 할지 알 수 없게 된다. 결정을 바꾸면 SPEC.md를 고치고 PLAN.md 결정 로그에 한 줄 남긴다.

## 혼자 정하면 안 되는 것

리뷰 안에서 닫히지 않는 항목이다. **임의로 정하지 말고 확인을 받는다.**

- **조각 2(평점 집계, D1)는 이슈 #33(시은 담당)과 같은 일이다.** 집계 책임 도메인, 실시간 조회인지 컬럼 갱신인지, 재계산인지 증분인지가 전부 미합의다. **리뷰 단독으로 착수하지 않는다.** (`PLAN.md` R9, `SPEC.md` D1)
- **조각 7(알림, D2)은 PR #107(민정, 미머지)의 규격에 의존한다.** `NotificationType.NEW_REVIEW`·`CUSTOMER_REVIEW`, `notifications.review_id`, `NotificationRequest.reviewId`가 전부 `dev`에 없다. **머지 전에는 이 이름들을 코드에 등장시키지 않는다.** (`PLAN.md` R1·R7)
- `SPEC.md` 9절의 **미정 항목**(관리자 숨김 사유 기록, 이미지 첨부)도 마찬가지다.

## 특히 놓치기 쉬운 것

아래는 결과가 겉보기에 정상이라 리뷰에서 놓치기 쉬운 항목이다. 근거는 `SPEC.md`·`PLAN.md`의 해당 절에 있다.

### 자격과 소유권

- **`reviews.product_id`는 요청값을 믿지 않고 `order_items.product_id`에서 파생시킨다.** 같은 값에 이르는 경로가 둘이라 요청값을 그대로 쓰면 **남의 상품에 후기를 붙일 수 있다.** 화면으로는 정상으로 보인다. (`SPEC.md` A3, `PLAN.md` R4)
- **`reviews.member_id`도 요청으로 받지 않는다.** 인증 사용자에서 가져온다.
- **작성 폼(A2)의 검증 4가지를 등록(A3)에서 그대로 다시 수행한다.** 폼을 열고 제출하기까지 사이가 벌어질 수 있고, 무엇보다 **폼 화면을 거치지 않은 직접 호출**을 막아야 한다. (`SPEC.md` A2·A3)
- **소유권 위반은 403이 아니라 404(`REVIEW_NOT_FOUND`)다.** "권한이 없습니다"는 그 후기가 존재한다는 사실을 알려 준다. 남의 주문 상품 id(A2 검증 2번)도 같은 이유로 404다. **구현은 `UPDATE ... WHERE id = ? AND member_id = ?`로 쏘고 `affectedRows == 0`이면 그때 원인을 가려 던진다**(커뮤니티 선례). (`SPEC.md` 2.5)
- **다만 `BLOCKED` 후기의 수정·삭제 거부는 404가 아니라 403(`BLOCKED_REVIEW`)이다.** 상대가 작성자라 글의 존재를 이미 알기 때문이다. 커뮤니티와 같은 자리다. (`SPEC.md` A4, 2.1)
- **회원 상태(`ACTIVE`/`SUSPENDED`/`WITHDRAWN`)를 재검증하지 않는다.** 인증을 통과한 사용자는 정의상 `ACTIVE`이고 그 전제는 member 도메인 테스트가 고정한다. (`SPEC.md` 2.3)

### 중복과 삭제

- **`uk_reviews_order_item` 검증과 INSERT 사이의 동시 요청은 검증만으로 막히지 않는다.** `DuplicateKeyException`을 잡아 `ALREADY_REVIEWED` 409로 바꾼다(커뮤니티 신고 선례). (`SPEC.md` A3)
- **A1(작성할 후기 목록)의 `NOT EXISTS`에 `status` 조건을 붙이지 않는다.** 붙이면 `DELETED` 후기의 주문 상품이 목록에 다시 뜨는데, `uk_reviews_order_item`은 그대로라 **눌러서 저장하면 거절된다.** 목록에 띄워 놓고 저장에서 거절하는 화면이 된다. 조건 없는 `NOT EXISTS`가 의도다. (`SPEC.md` A1, `PLAN.md` R10)
- **삭제하면 그 주문 상품에는 다시 후기를 쓸 수 없다.** soft delete라 행이 남고 UNIQUE가 재작성을 막는다. **삭제 확인창에 이 사실을 적는다** — `삭제하면 이 주문 상품에는 다시 후기를 작성할 수 없습니다.` (`SPEC.md` A5)
- **`DELETED → PUBLISHED` 재활성을 열지 않는다.** 열면 "삭제"가 실제로는 숨김이 되어 버튼 이름과 동작이 어긋난다.
- **`order_items.quantity`가 2 이상이어도 후기는 1개다.** 후기 단위는 수량이 아니라 주문 상품 행이다.

### 상태와 노출

- **노출 여부는 `reviews.status` 하나로 판단한다.** 조건이 두 개면 새 쿼리를 추가할 때 하나를 빠뜨린다. (`SPEC.md` 2.1, 커뮤니티 4.1 선례)
- **스키마 기본값 `'VISIBLE'`을 코드에 등장시키지 않는다.** 코드베이스 어디에도 없는 어휘다. 조각 0에서 `'PUBLISHED'`로 바꾸고 `CHECK`를 건다. (`PLAN.md` R3)
- **B3(내 후기 목록)만 조건이 다르다** — `status <> 'DELETED'`. `BLOCKED` 후기도 본인에게는 보여야 한다(숨겨진 사실을 본인이 알아야 한다). **대신 수정·삭제 버튼은 뜨지 않는다.** 버튼을 남기면 눌러도 403만 나오는 죽은 버튼이 된다. (`SPEC.md` B3)
- **C1(관리자 목록)은 모든 상태를 보여 준다.** 조치 이력을 확인해야 하기 때문이다.
- **후기가 `BLOCKED`면 답글도 함께 가려진다.** 판단 기준은 후기의 `status` 하나다. (`SPEC.md` B4)

### 평점과 집계

- **대표값은 `overall_rating`이고 고객이 직접 매긴다. 나머지 3종의 평균으로 계산하지 않는다.** 계산값으로 두면 "맛은 5인데 전체는 3" 같은 실제 감상을 담을 자리가 없어진다. `products.average_rating = AVG(overall_rating)`이고 **세부 3종은 집계에 쓰지 않는다.** (`SPEC.md` 2.2)
- **목업의 `포장`은 `응대`(`service_rating`)로 흡수하고, 목업에 없는 `전체` 입력을 화면에 추가한다.** 목업 4종과 스키마 4종이 어긋나 있다. (`SPEC.md` 2.2)
- **리뷰에서 `UPDATE products ...`를 직접 날리지 않는다.** 이슈 #33 완료 조건에 "review 도메인이 Product Mapper를 직접 사용하지 않는다"가 못 박혀 있다. 도메인 간 공개 Service 계약을 거친다. (`SPEC.md` D1)
- **집계 호출 지점은 5곳이다** — A3(등록), A4(수정), A5(삭제), C4(숨김), C4(숨김 해제). **숨김 해제를 빠뜨리기 쉽다.**
- **컬럼 갱신 방식으로 정해지면 `updated_at = updated_at`을 반드시 넣는다.** 안 넣으면 집계가 바뀔 때마다 상품에 수정 흔적이 남는다. **커뮤니티에서 같은 자리를 두 번 빠뜨려 화면에 `(수정됨)`이 붙는 버그가 실제로 났다.** (`SPEC.md` D1)
- **잠금 순서를 따진다.** `reviews` INSERT가 FK로 `products` 행에 공유 잠금을 걸고 재계산이 배타 잠금을 요구한다. 커뮤니티 좋아요와 같은 모양이라 교착이 난다. 동시 요청이 없으면 결과가 똑같아 **단일 스레드 테스트로는 드러나지 않는다.** (`SPEC.md` D1)
- **`average_rating`·`review_count`는 이미 `ProductMapper.xml`의 정렬 기준이다**(`review_count DESC, average_rating DESC`). 갱신 없이 후기만 쌓이면 정렬이 계속 0을 본다. 조각 1을 끝내고 조각 2를 미루면 이 상태가 된다. (`PLAN.md` R2)

### SQL

- **`rating` 필터의 허용값을 `<choose>`로 매핑한다. `${}`로 잇지 않는다.** 모르는 값은 오류가 아니라 **전체(필터 없음)로 떨어뜨린다.** (`SPEC.md` C2, `AGENTS.md`)
- **부분 일치 검색(`writer`·`product`)은 `LIKE`이므로 `%`·`_`를 이스케이프한다.** 하지 않으면 `%` 한 글자로 전체가 조회된다. (`SPEC.md` C2)
- **정렬에 `id` tiebreaker를 반드시 붙인다.** 없으면 동일 시각 후기가 두 페이지에 중복되거나 누락된다. 어느 정렬 분기에도 붙인다. (`SPEC.md` B1)
- **페이징은 `PageRequest`/`PageResult`를 재사용한다.** 새로 만들지 않는다.
- **상품별 후기 조회를 받쳐 줄 인덱스가 `fk_reviews_product`뿐이라 정렬에 filesort가 붙는다.** 1차에서는 수용한 결정이다. 되돌아올 계기(`(product_id, status, created_at, id)` 복합 인덱스)는 `PLAN.md` R8에 있다. **미리 붙이지 않는다.**
- **`V0__initial_schema.sql`을 비롯한 공유 migration을 수정하지 않는다.** 새 versioned migration을 `gradlew newMigration -Pdesc=<snake_case>`로 만든다. 파일명을 직접 짓지 않는다.

### 관리자와 답글

- **관리자가 할 수 있는 일은 숨김·해제뿐이다.** 목업의 `삭제` 버튼은 걷어낸다 — 관리자가 지울 수 있으면 조치 이력이 사라진다. (`SPEC.md` C1, 커뮤니티 6.7 선례)
- **접근 제어는 화면에서 버튼을 감추는 것이 아니라 Spring Security로 막는다.** (`AGENTS.md`)
- **답글 삭제는 열지 않는다.** `uk_review_replies_review`가 후기당 1건을 강제하므로 삭제 후 재작성은 수정과 결과가 같다. 삭제를 열면 재작성 시 알림을 또 보낼지 판단해야 하고 `event_key` 설계가 복잡해진다. (`SPEC.md` C6)
- **답글 수정에는 알림을 보내지 않는다.** 알림은 답글이 처음 달릴 때 한 번이다.
- **답글 작성자(관리자)의 실명은 고객 화면에 표시하지 않는다.** 라벨은 `사장님 답글`이다. (`SPEC.md` B4)
- **`review_replies.admin_id`는 `members(id)` FK다.** 사람이 다는 것을 전제하며 인증 관리자에서 가져온다.

### 화면

- **템플릿에서 `th:utext`를 쓰지 않는다.** 본문은 순수 텍스트이며 줄바꿈은 CSS `white-space: pre-wrap`으로 처리한다. (`SPEC.md` 2.4)
- **Thymeleaf는 HTML 주석을 응답에 그대로 내보낸다.** 화면에 없어야 하는 문구를 주석에 적으면 "그 문구가 없다"를 단언하는 테스트가 주석 때문에 깨진다. 커뮤니티에서 두 번 밟았다.
- **목업의 이미지 입력(`최대 3장`)은 1차에서 걷어낸다.** 동작하지 않는 입력을 남겨 두면 고객이 첨부했다고 믿는다. (`SPEC.md` A6)
- **A2 폼에 있는 `수정하기`·`삭제하기` 버튼도 걷어낸다.** 등록 화면과 수정 화면(A4)은 갈라진다.
- **`/reviews/new`는 `orderItemId`가 필수다.** 없거나 숫자가 아니면 400이다. 진입점 3곳이 모두 파라미터 없이 가고 있으므로 셋 다 고친다. (`SPEC.md` A2)
- **상품 상세에서 곧바로 작성 폼으로 보내지 않는다.** 그 상품을 픽업한 주문이 여러 건일 수 있어 화면이 어느 주문 상품인지 정할 수 없다. A1(작성할 후기 목록)이 그 자리를 대신한다.
- **경로 변수는 `{id:\d+}` 형태로 숫자를 강제한다.** (커뮤니티 선례)
- **탈퇴 회원의 후기는 유지하고 작성자명만 `탈퇴한 회원`으로 표시한다.** 평점은 상품 평가의 근거라 작성자가 떠났다고 지우면 다른 고객의 판단 근거가 사라진다. (`SPEC.md` 2.6)
- **A1·B3의 상품명은 `order_items.product_name` 스냅샷을 쓴다.** 주문 이력 성격의 화면이다. B1은 그 상품 화면 안이므로 상품명을 따로 표시하지 않는다.
- **빈 목록은 오류가 아니다.** A1은 `작성할 후기가 없습니다.`, B1은 `아직 등록된 후기가 없습니다.`(현재의 `준비 중` 문구를 대체).
- **조건부로만 그려지는 블록은 렌더링 테스트로 고정한다.** 빈 목록, 숨김 상태 표시, 쪽 이동처럼 평소 화면에 없는 것은 표현식이 깨져도 아무도 모른 채 지나간다. (커뮤니티 선례)

### 검증과 코드 규약

- **본문은 trim 후 검증한다. 공백만 입력은 거부.** 저장 시 앞뒤만 trim하고 중간 줄바꿈은 보존한다. (`SPEC.md` 2.4)
- **`reviews.content`는 스키마상 `NULL` 허용이지만 화면에서는 필수로 받는다.** 평점만 있는 후기는 관리자 목록의 `내용 미리보기`를 빈칸으로 만든다.
- **평점 범위는 화면 검증만으로 막히지 않는다.** `TINYINT UNSIGNED`라 API 직접 호출로 0과 255가 들어간다. 조각 0에서 `CHECK (... BETWEEN 1 AND 5)`를 건다. (`PLAN.md` R5)
- **enum 이름을 DB 문자열로 그대로 저장한다.** 전이 규칙은 enum 안 `canTransitionTo(next)`에 두고 **`null` 방어까지 `PostStatus`와 같게 한다.** (4개 도메인이 이미 동일)
- **오류 코드 번호는 `<도메인>_NNN` 규약을 따르고 `SPEC.md` 2.5의 번호를 그대로 쓴다.** 임의로 새 번호를 매기지 않는다.
- **새로 만드는 Java 파일의 헤더 주석은 커뮤니티 파일 형식**(`작성자`/`담당자`/`작성일`/`기능`/`설명`)을 따른다.
