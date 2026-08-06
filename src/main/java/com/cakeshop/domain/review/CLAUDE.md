# Review 도메인 작업 규칙

이 디렉터리(`domain/review/`)와 아래 관련 파일을 수정할 때는 **반드시 다음 세 문서를 먼저 읽고 그에 따른다.**

- **`docs/review/SPEC.md`** — 기능 명세의 정본. 기능 목록, 상태 모델, 평점, 권한, 입력 검증, 오류 코드, 화면. 여기 적힌 것과 다르게 구현하지 않는다.
- **`docs/review/FLOW.md`** — 도메인 전체 흐름. 주문 → 후기 → 상품 → 알림이 어디서 이어지고 어디가 끊겨 있는지.
- **`docs/review/PLAN.md`** — 작업 순서, 진행 상태, 위험, 결정 로그. 지금 어느 조각을 하는지 여기서 확인한다.

**이 문서는 규칙을 다시 적지 않는다.** 규칙과 그 근거는 `SPEC.md`에 한 번만 있다. 여기 있는 것은 **파일 범위, 담당자 경계, 작업 절차, 그리고 SPEC에 자리가 없는 저장소 경험칙**뿐이다. 사본을 만들면 한쪽이 낡고, 다음 작업자가 어느 쪽을 믿어야 할지 알 수 없게 된다.

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

`customer/product/detail.html`과 `customer/order/detail.html`은 **PR #74(주환)가 함께 고쳤고 지금은 머지됐다.** 조각 3 착수 전에 `dev` 기준을 다시 확인한다.

### 남의 영역에 클래스·메서드를 만들 때는 주석으로 흔적을 남긴다

조각 2가 `domain/product/`에 `ProductRatingService`·`ProductReviewMapper`를 만들고, 조각 3이 시은님 `ProductController`에 모델 주입을 넣는다. **남의 담당 구역에 새로 만드는 클래스와 공용 서비스 메서드에는 아래 헤더를 반드시 단다.** 담당자가 나중에 "이건 누가 왜 넣었나"를 코드만 보고 알 수 있어야 한다.

```java
/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-06
 * 기능 : 리뷰 평점 집계 계약
 * 설명 : 후기 등록·수정·삭제 시 products 의 평균 평점과 후기 수를 다시 계산한다.
 * ******************************
 */
```

저장소에 이미 32개 파일이 쓰는 형식이다. 전부에 붙어 있지는 않으므로 **기존 파일을 소급해 고치지는 않는다** — 새로 만드는 것에만 적용한다.

`domain/product/`에 파일을 만드는 조각 2(D1)는 **시은님 담당 구역이다.** 아래 "혼자 정하면 안 되는 것"을 본다.

## 작업 절차

1. `docs/review/SPEC.md`와 `docs/review/PLAN.md`를 읽는다.
2. `PLAN.md`의 조각 순서를 확인하고, **현재 조각의 범위를 벗어나는 구현을 하지 않는다.**
3. 그 조각에 **선행 합의**가 붙어 있으면 합의 전에는 착수하지 않는다(아래).
4. 구현 후 `./gradlew clean test`를 실행한다.
5. 해당 조각에 명시된 검증 항목을 확인한다.
6. 변경 파일, 실행한 검증, 남은 위험을 보고한다.

## 규칙을 벗어나야 할 때

SPEC.md의 결정이 잘못됐거나 부족하다고 판단되면 **코드로 우회하지 말고 먼저 알린다.** 규칙과 코드가 어긋나면 다음 작업자(사람이든 AI든)가 어느 쪽을 믿어야 할지 알 수 없게 된다. 결정을 바꾸면 SPEC.md를 고치고 PLAN.md 결정 로그에 한 줄 남긴다.

## 혼자 정하면 안 되는 것

리뷰 안에서 닫히지 않는 항목이다. **임의로 정하지 말고 확인을 받는다. 시그니처가 정해지기 전에는 그 조각을 열지 않는다.** 근거와 상세는 `SPEC.md` 9절에 있다.

| 조각 | 합의할 것 | 상대 |
|---|---|---|
| 1 | 주문 도메인 계약 (A1 목록 + A2·A3 단건 검증, 제외 목록과 페이징 포함) | 주환 |
| 2 | `ProductRatingService` 시그니처, 전용 매퍼 신설 동의 | 시은 (#33) |
| 3 | 작성자 표시명 계약 (ID 묶음 조회) / 상품 상세에 후기를 붙이는 방식 / **B3·C1·C3용 주문 스냅샷 계약** | 수민 / 시은 / 주환 |
| 5 | 관리자 검색 계약 (`writer`·`product`) | 수민·주환 |
| 7 | `event_key` 규격, `NEW_REVIEW` 수신 관리자 | 민정 (PR #107 머지 후) |

- **조각 1과 2는 한 배포 단위다.** PR은 따로 열되 **조각 2 없이 조각 1만 `dev`에 머지하지 않는다.** (`PLAN.md` 작업 방식, R2)
- **조각 7 전에는 PR #107의 이름들을 코드에 등장시키지 않는다** — `NotificationType.NEW_REVIEW`·`CUSTOMER_REVIEW`, `notifications.review_id`, `NotificationRequest.reviewId`가 전부 `dev`에 없다. (`PLAN.md` R1·R7)

## 이 도메인에서 절대 하지 않는 것

**리뷰 매퍼는 `reviews`·`review_replies`·`review_images` 외의 테이블을 JOIN하지 않는다. 예외 없다.** 표시용이든 검색용이든 같다. 필요한 값은 그 도메인의 전용 QueryService가 DTO로 돌려받는다. **커뮤니티가 `members`를 8곳에서 직접 JOIN한다고 해서 따라 하지 않는다** — 리뷰는 더 엄격한 규칙을 쓰기로 정했고, 근거는 `SPEC.md` 2.7에 있다.

2.7이 연 예외는 **반대 방향**이다 — 상품이 자기 파생 컬럼을 유지하려고 `reviews`를 세는 것(D1). **리뷰 매퍼 쪽은 여전히 예외가 없다.**

계약이 없으면 **만들지 말고 합의부터 한다.**

## 놓치기 쉬운 것 — 점검 목록

아래는 **결과가 겉보기에 정상이라 리뷰에서 놓치기 쉬운** 항목이다. 규칙 본문과 근거는 괄호 안 절에 있다. 여기서는 빠뜨렸는지만 확인한다.

| 확인 | 정본 |
|---|---|
| `reviews.product_id`는 요청값이 아니라 `order_items.product_id`에서 파생 | SPEC A3 / R4 |
| A2의 검증 4가지를 A3에서 그대로 다시 수행 | SPEC A2·A3 |
| 소유권 위반은 403이 아니라 404. 단 `BLOCKED`는 403 | SPEC 2.5 · A4 |
| 상태 확인과 쓰기를 갈라 놓지 않음 — 전이(2.1)·수정(A4)·답글(C5) 세 자리 전부 | SPEC 2.1 |
| `DuplicateKeyException` → `ALREADY_REVIEWED` / `ALREADY_REPLIED` | SPEC A3 · C5 |
| A1의 `NOT EXISTS`에 `status` 조건을 붙이지 않음 | SPEC A1 / R10 |
| 노출 판단은 `status` 하나. B3만 `<> 'DELETED'` | SPEC 2.1 · B3 |
| B1은 상품 공개 여부를 먼저 확인하고 `NOT_ON_SALE`을 404로 바꿈 | SPEC B1 |
| 집계 호출 5곳 — 등록·수정·삭제·숨김·**숨김 해제** | SPEC D1 |
| 다섯 곳 모두 후기 쓰기와 같은 트랜잭션 (rollback 테스트) | SPEC D1 |
| 평균은 `COALESCE(AVG(...), 0)` — 마지막 공개 후기 삭제로만 드러남 | SPEC D1 |
| 집계에 `updated_at = updated_at` 보존 | SPEC D1 |
| 순서는 잠금 → `reviews` 쓰기 → 집계. 집계 SELECT는 `FOR UPDATE` | SPEC A3 · D1 |
| 조각 1에서 `SecurityConfig` local preview 목록에서 `/reviews/**` 제거 | SPEC 2.3 |
| `rating` 필터는 `<choose>` 매핑, `LIKE`는 `%`·`_` 이스케이프 | SPEC C2 |
| 정렬에 `id` tiebreaker — 어느 분기에도 | SPEC B1 |
| `V0__initial_schema.sql`을 고치지 않고 `newMigration`으로 생성 | SPEC E3 |

**동시 요청으로만 드러나는 것이 넷 있다** — 전이 경합(2.1), 수정 경합(A4), 답글·숨김 경합(C5), 집계 순서·스냅샷(D1). 순서대로 부르면 결과가 똑같아 **단일 스레드 테스트로는 전부 통과한다.** 동시 요청 테스트로 고정한다.

### SPEC에 자리가 없는 저장소 경험칙

- **Thymeleaf는 HTML 주석을 응답에 그대로 내보낸다.** 화면에 없어야 하는 문구를 주석에 적으면 "그 문구가 없다"를 단언하는 테스트가 주석 때문에 깨진다. 커뮤니티에서 두 번 밟았다.
- **조건부로만 그려지는 블록은 렌더링 테스트로 고정한다.** 빈 목록, 숨김 상태 표시, 쪽 이동처럼 평소 화면에 없는 것은 표현식이 깨져도 아무도 모른 채 지나간다. 커뮤니티 선례.
