# Review 도메인 작업 규칙

이 디렉터리(`domain/review/`)와 아래 관련 파일을 수정할 때는 **반드시 다음 문서를 먼저 읽고 그에 따른다.**

- **`docs/review/DOMAIN.md`** — 규칙의 정본. 정의, 전체 흐름, 공통 규칙(상태·평점·권한·검증·오류 코드·표시·도메인 경계), 기능 인벤토리, 화면, 미정. 여기 적힌 것과 다르게 구현하지 않는다.
- **`docs/review/specs/`** — 기능 단위 명세. 어느 파일인지는 `DOMAIN.md` 1절 기능 표의 `spec` 열이 가리킨다.
- **`docs/review/PLAN.md`** — 작업 순서, 진행 상태, 위험, 결정 로그, 하네스. 지금 어느 조각을 하는지 여기서 확인한다.

배경이 필요하면 `docs/review/decisions/`(한 줄로 재구성이 안 되는 결정)와 `docs/review/history/`(끝난 조각)를 본다.

**이 문서는 규칙을 다시 적지 않는다.** 규칙과 그 근거는 정본 문서에 한 번만 있다. 여기 있는 것은 **파일 범위, 담당자 경계, 작업 절차, 그리고 정본에 자리가 없는 저장소 경험칙**뿐이다. 사본을 만들면 한쪽이 낡고, 다음 작업자가 어느 쪽을 믿어야 할지 알 수 없게 된다.

화면 명세 파일(`docs/community/screens/*.md` 같은 것)은 아직 없다. 커뮤니티처럼 문서와 템플릿을 대조하는 테스트가 없으므로, **화면 문구는 `DOMAIN.md` 3절과 각 기능 절이 유일한 근거다.** 실제 화면이 처음 생기는 조각 3에서 `screens/`로 나누고 대조 하네스를 함께 세운다.

프로젝트 전체 규칙은 저장소 루트 `AGENTS.md`가 상위 정본이며, 충돌하면 `AGENTS.md`가 우선한다.

## 지금 상태

**조각 0(#125)만 머지됐다.** 스키마와 상태 어휘는 굳었고, 그 위에서 동작하는 기능은 아직 없다.

| 파일 | 상태 |
|---|---|
| `entity/ReviewStatus.java` | **완료** — `PUBLISHED`/`DELETED`/`BLOCKED` + `canTransitionTo` |
| `entity/Review.java`, `entity/ReviewReply.java` | **완료** — 스키마에 맞춰 필드가 채워져 있다 |
| migration | **완료** — `V20260806_075114__add_review_status_and_rating_constraints.sql` (`status` 기본값·`CHECK`, 평점 범위 `CHECK`) |
| `service/ReviewService.java` | TODO 주석만 |
| `mapper/ReviewMapper.java`, `mapper/review/ReviewMapper.xml` | 비어 있음 |
| `controller/ReviewController.java` | `GET /reviews/new` 목업 반환만 |
| `controller/ReviewAdminController.java` | `GET /admin/reviews` 목업 반환만 |
| `error/ReviewErrorCode.java` | `REVIEW_001` 하나 |

즉 **조각 1부터는 기존 구현에 맞출 것이 거의 없다.** 판단 기준은 코드가 아니라 위 문서들이다.

> `rds` 프로파일은 `flyway.enabled: false`라 **migration이 자동 적용되지 않는다.** 조각 1은 `status` 기본값과 `CHECK`를 전제하므로 착수 전에 반영 여부를 확인한다(`PLAN.md` R11).

## 이 도메인의 파일 범위

- `src/main/java/com/cakeshop/domain/review/**`
- `src/main/resources/mapper/review/*.xml` (고객 `ReviewMapper.xml`, 관리자가 필요해지면 `ReviewAdminMapper.xml`)
- `src/main/resources/templates/customer/review/**`
- `src/main/resources/templates/admin/review/**`
- `src/test/java/com/cakeshop/domain/review/**`
- 새 Flyway migration (`gradlew newMigration -Pdesc=<snake_case>`로 생성)

### 범위 밖이지만 건드려야 하는 파일

진입점 때문에 남의 도메인 화면을 고쳐야 한다(`DOMAIN.md` 3절). **링크 한 줄 수정을 넘어서면 손대지 말고 먼저 알린다.**

| 파일 | 무엇 | 담당 |
|---|---|---|
| `customer/member/mypage.html` | A1·B3 링크 2개 | 수민 |
| `customer/order/detail.html` | `후기 작성` 링크에 `orderItemId` 전달 | 주환 |
| `customer/product/detail.html` | 후기 영역 교체 (`"준비 중"` → B1) | 시은 |

`customer/product/detail.html`과 `customer/order/detail.html`은 **PR #74(주환)가 함께 고쳤고 지금은 머지됐다.** 조각 3 착수 전에 `dev` 기준을 다시 확인한다.

### 남의 영역에 클래스·메서드를 만들 때는 주석으로 흔적을 남긴다

**형식과 규칙은 `AGENTS.md`의 "도메인 담당과 협업 경계"가 정본이다.** 여기 다시 적지 않는다.

리뷰에서 이 규칙이 걸리는 자리는 **조각 1과 2**다 — `domain/order/`에 `OrderReviewQueryService`(담당자 **주환**), `domain/product/`에 `ProductReviewCommandService`·`ProductReviewMapper`(담당자 **시은**)를 새로 만든다. 이름 형식은 `docs/conventions.md` 15.2를 따른다.

**조각 3의 모델 주입은 대상이 아니다.** 기존 `ProductController.detail`을 고치는 것이고, 정본 규칙은 **새로 만드는** 클래스·공개 Service 메서드로 범위를 한정한다.

`domain/product/`에 파일을 만드는 조각 2(D1)는 **시은님 담당 구역이다.** 아래 "혼자 정하면 안 되는 것"을 본다.

## 작업 절차

1. `docs/review/DOMAIN.md`와 `docs/review/PLAN.md`를 읽고, 그 조각의 `specs/` 파일을 읽는다.
2. `PLAN.md`의 조각 순서를 확인하고, **현재 조각의 범위를 벗어나는 구현을 하지 않는다.**
3. 그 조각에 **선행 합의**가 붙어 있으면 합의 전에는 착수하지 않는다(아래).
4. 구현 후 `./gradlew clean test`를 실행한다.
5. 해당 조각에 명시된 검증 항목을 확인한다.
6. **그 조각이 추가한 하네스를 `PLAN.md` 하네스 표에 올리고 조각 표의 상태를 바꾼다.** 문서를 함께 고치지 않으면 `ReviewDocTests`가 빨간불이 된다.
7. 변경 파일, 실행한 검증, 남은 위험을 보고한다.

## 규칙을 벗어나야 할 때

정본의 결정이 잘못됐거나 부족하다고 판단되면 **코드로 우회하지 말고 먼저 알린다.** 규칙과 코드가 어긋나면 다음 작업자(사람이든 AI든)가 어느 쪽을 믿어야 할지 알 수 없게 된다. 결정을 바꾸면 정본 문서(`DOMAIN.md` 또는 해당 `specs/` 파일)를 고치고 `PLAN.md` 결정 로그에 한 줄 남긴다.

## 혼자 정하면 안 되는 것

리뷰 안에서 닫히지 않는 항목이다. **임의로 정하지 말고 확인을 받는다. 시그니처가 정해지기 전에는 그 조각을 열지 않는다.** 근거와 상세는 `DOMAIN.md` 4절에 있다.

| 조각 | 합의할 것 | 상대 |
|---|---|---|
| 1 | 주문 도메인 계약 (A1 목록 + A2·A3 단건 검증, 제외 목록과 페이징 포함) | 주환 |
| 2 | `ProductReviewCommandService` 시그니처, 전용 매퍼 신설 동의, **리뷰가 계산한 값을 그대로 쓰는 것에 대한 동의** | 시은 (#33) |
| 3 | 작성자 표시명 계약 (ID 묶음 조회) / **B3·C1·C3용 주문 스냅샷 계약** | 수민 / 주환 |
| 5 | 관리자 검색 계약 (`writer`·`product`) | 수민·주환 |
| 7 | `event_key` 규격, `NEW_REVIEW` 수신 관리자 | 민정 |

- **조각 1과 2는 한 배포 단위다.** PR은 따로 열되 **조각 2 없이 조각 1만 `dev`에 머지하지 않는다.** (`PLAN.md` 작업 방식, R2)
- **알림 규격은 이제 `dev`에 있다.** PR #107이 #128로 머지되어 `NotificationType.NEW_REVIEW`·`CUSTOMER_REVIEW`, `notifications.review_id`·`review_reply_id`, `NotificationRequest`가 전부 들어왔다. 조각 7이 마지막인 것은 순서상의 편의일 뿐 기다리는 의존이 아니다. (`PLAN.md` R1)

## 이 도메인에서 절대 하지 않는 것

**리뷰 매퍼는 `reviews`·`review_replies`·`review_images` 외의 테이블을 JOIN하지 않는다.** 표시용이든 검색용이든 같다. 필요한 값은 그 도메인의 전용 QueryService가 DTO로 돌려받는다. 근거는 `DOMAIN.md` 2.7에 있다.

> 커뮤니티도 조각 10(PR #142·#143)에서 `members` JOIN 8곳을 걷어냈고 `CommunityDomainBoundaryTests`가 되돌아오는 것을 막는다. **리뷰가 먼저 그은 선을 커뮤니티가 뒤따라온 것이다** — 예전에 여기 적혀 있던 "커뮤니티는 JOIN하지만 따라 하지 않는다"는 더 이상 사실이 아니다.

**리뷰에는 예외가 없다.** 팀 규칙이 연 ReadModel 예외(`docs/conventions.md` 15.9)는 여러 도메인을 **집계·요약**하는 통계·대시보드용이다. **C1·C2 관리자 목록·검색은 관리자 화면이지만 대상이 아니다** — 조건이 남의 도메인에서 올 뿐 결국 후기 목록이라 리뷰가 소유하고, 조건은 계약으로 받는다.

계약이 없으면 **만들지 말고 합의부터 한다.**

## 놓치기 쉬운 것 — 점검 목록

아래는 **결과가 겉보기에 정상이라 리뷰에서 놓치기 쉬운** 항목이다. 규칙 본문과 근거는 오른쪽 정본에 있다. 여기서는 빠뜨렸는지만 확인한다.

| 확인 | 정본 |
|---|---|
| `reviews.product_id`는 요청값이 아니라 `order_items.product_id`에서 파생 | `specs/review-write.md` A3 / R4 |
| A2의 검증 4가지를 A3에서 그대로 다시 수행 | `specs/review-write.md` A2·A3 |
| 소유권 위반은 403이 아니라 404. 단 `BLOCKED`는 403 | `DOMAIN.md` 2.5 · `specs/review-edit-delete.md` A4 |
| 상태 확인과 쓰기를 갈라 놓지 않음 — 전이(`DOMAIN.md` 2.1)·수정(A4)·답글(C5) 세 자리 전부 | `DOMAIN.md` 2.1 |
| `DuplicateKeyException` → `ALREADY_REVIEWED` / `ALREADY_REPLIED` | `specs/review-write.md` A3 · `specs/review-reply.md` C5 |
| A1의 `NOT EXISTS`에 `status` 조건을 붙이지 않음 | `specs/review-write.md` A1 / R10 |
| 노출 판단은 `status` 하나. B3만 `<> 'DELETED'` | `DOMAIN.md` 2.1 · `specs/review-read.md` B3 |
| B1은 상품 공개 여부를 먼저 확인하고 `NOT_ON_SALE`을 404로 바꿈 | `specs/review-read.md` B1 |
| 집계 호출 5곳 — 등록·수정·삭제·숨김·**숨김 해제** | `specs/product-rating.md` D1 |
| 다섯 곳 모두 후기 쓰기와 같은 트랜잭션 (rollback 테스트) | `specs/product-rating.md` D1 |
| 평균은 `COALESCE(AVG(...), 0)` — 마지막 공개 후기 삭제로만 드러남 | `specs/product-rating.md` D1 |
| 집계에 `updated_at = updated_at` 보존 | `specs/product-rating.md` D1 |
| 순서는 잠금 → `reviews` 쓰기 → 집계. 집계 SELECT는 `FOR UPDATE` | `specs/review-write.md` A3 · `specs/product-rating.md` D1 |
| 조각 1에서 `SecurityConfig` local preview 목록에서 `/reviews/**` 제거 | `DOMAIN.md` 2.3 |
| `rating` 필터는 `<choose>` 매핑, `LIKE`는 `%`·`_` 이스케이프 | `specs/review-admin.md` C2 |
| 정렬에 `id` tiebreaker — 어느 분기에도 | `specs/review-read.md` B1 |
| `V0__initial_schema.sql`을 고치지 않고 `newMigration`으로 생성 | `specs/review-schema.md` E3 |

**동시 요청으로만 드러나는 것이 넷 있다** — 전이 경합(`DOMAIN.md` 2.1), 수정 경합(A4), 답글·숨김 경합(C5), 집계 순서·스냅샷(D1). 순서대로 부르면 결과가 똑같아 **단일 스레드 테스트로는 전부 통과한다.** 동시 요청 테스트로 고정한다.

### 정본에 자리가 없는 저장소 경험칙

- **Thymeleaf는 HTML 주석을 응답에 그대로 내보낸다.** 화면에 없어야 하는 문구를 주석에 적으면 "그 문구가 없다"를 단언하는 테스트가 주석 때문에 깨진다. 커뮤니티에서 두 번 밟았다.
- **조건부로만 그려지는 블록은 렌더링 테스트로 고정한다.** 빈 목록, 숨김 상태 표시, 쪽 이동처럼 평소 화면에 없는 것은 표현식이 깨져도 아무도 모른 채 지나간다. 커뮤니티 선례.
- **머지된 Flyway migration은 주석 한 글자도 고치지 않는다.** 체크섬이 바뀌어 이미 반영된 DB에서 검증이 실패한다. `V20260806_075114`의 주석이 지금은 없어진 `docs/review/SPEC.md`를 가리키는데, 그래서 그대로 둔다(`PLAN.md` 하네스 절).
