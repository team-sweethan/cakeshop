# Review 도메인 전체 흐름 (2026-08-05 초안)

> 이 문서는 Review 도메인의 **전체 흐름**을 잡는다. 흐름 위 각 지점의 기능 명세는 `docs/review/SPEC.md`를 본다.
> 작업 순서·진행 상태·위험은 `docs/review/PLAN.md`를 본다.
> 현재 구현은 스키마(`V0__initial_schema.sql`)와 목업 화면뿐이며 동작하는 기능은 없다.
> 프로젝트 전체 규칙은 `AGENTS.md`가 상위 정본이다.

## 1. 한 문장 정의

Cakeshop 후기는 **케이크를 실제로 받아 간 고객이 그 주문 상품에 대해 남기는 평가**다.

커뮤니티의 `REVIEW`(후기) 카테고리와 다르다. 커뮤니티 후기는 누구나 쓰는 글이고, 이쪽은 **주문 이력이 있어야만 쓸 수 있는 평점 데이터**다.

## 2. 전체 흐름

```
[주문]                    [후기]                      [상품]
  │                         │                           │
  ├─ 결제 완료               │                           │
  ├─ 픽업 완료 ──────────────┤                           │
  │  (PICKED_UP)      작성 자격 발생                     │
  │                         │                           │
  │                    후기 작성 ────────────────────► 평점 집계
  │                    (주문상품당 1개)            average_rating
  │                         │                      review_count
  │                         │                           │
  │                         ▼                           ▼
  │                    상품 상세에 노출 ◄─────────────────┘
  │                         │
  │                         ▼
  │                   [관리자] 목록 확인
  │                         │
  │                    ├─ 답글 (후기당 1개) ──► [알림] 작성자에게
  │                    └─ 숨김 (status)
```

## 3. 단계별 흐름

### 3.1 작성 자격 획득 — 주문에서 온다

후기는 스스로 시작되지 않는다. **주문이 `PICKED_UP`에 도달하는 것이 유일한 입구**다.

- `OrderStatus.PICKED_UP`은 종착 상태다(나가는 전이 없음). 즉 **자격은 한 번 생기면 사라지지 않는다.**
- 자격의 단위는 주문이 아니라 **주문 상품(`order_items`)** 이다. 케이크 세 종류를 한 번에 샀으면 후기도 세 개다.
- 어긋난 경우의 응답은 이미 정해져 있다 — `ReviewErrorCode.NOT_PICKED_UP`("픽업 완료된 주문만 후기를 작성할 수 있습니다.", 400).

### 3.2 작성 — 주문 상품당 한 번

- 진입: `GET /reviews/new` (현재 목업 화면만 존재)
- 입력: 평점 4종 + 본문. **이미지는 1차 범위 밖이고 목업의 입력도 화면에서 걷어낸다**(`SPEC.md` A6) — 동작하지 않는 입력을 남기면 고객이 첨부했다고 믿는다
- 저장: `reviews` 한 행. `uk_reviews_order_item` UNIQUE가 **주문 상품당 1개를 구조적으로 강제**한다.
- 본인 주문인지는 `order_items → orders.member_id`로 확인한다. `order_items`에는 회원 정보가 없다.

### 3.3 집계 반영 — 상품으로 나간다

후기가 쌓이면 상품의 대표값이 바뀐다.

- `products.average_rating`, `products.review_count`를 갱신한다.
- **이 흐름이 지금 끊겨 있다.** 두 컬럼은 `ProductMapper.xml`이 조회할 뿐 아니라 **정렬 기준으로 쓰는데**(`review_count DESC, average_rating DESC`), 값을 쓰는 애플리케이션 코드가 없다. 운영에서는 영구히 0이다.
- 후기 작성·수정·삭제·숨김이 모두 이 갱신을 불러야 흐름이 닫힌다.

### 3.4 노출

- 상품 상세에서 그 상품의 후기를 본다.
- 고객 본인은 자기가 쓴 후기를 확인한다(마이페이지 또는 주문 상세).
- 숨김 처리된 후기는 노출에서 빠진다 — 판단 기준은 `reviews.status` 하나다(커뮤니티 `posts.status` 선례).

### 3.5 관리자 조치

- 진입: `GET /admin/reviews` (현재 목업 화면만 존재)
- **답글**: `review_replies`에 저장. `uk_review_replies_review` UNIQUE가 **후기당 답글 1개**를 강제한다. `admin_id`는 `members(id)` FK이므로 **사람이 다는 것을 전제**한다.
- **숨김**: `reviews.status`를 바꾼다.

### 3.6 알림

- 관리자 답글이 달리면 후기 작성자에게 알린다.
- 알림 쪽은 받을 준비가 되어 있다 — `NotificationRequest`의 연관 PK 후보에 **`reviewId`가 이미 포함**되어 있다(`docs/team-plan.md`).
- 후기에서 알림을 부르는 코드는 아직 없다.

## 4. 흐름이 닿는 도메인

| 도메인 | 방향 | 무엇 |
|---|---|---|
| 주문 | 주문 → 후기 | `PICKED_UP` 도달이 작성 자격. 대상은 `order_items` |
| 상품 | 후기 → 상품 | 평점 집계(`average_rating`, `review_count`). **현재 끊김** |
| 회원 | 회원 → 후기 | 작성자·관리자 식별. 탈퇴해도 행은 남는다 |
| 알림 | 후기 → 알림 | 답글 시 작성자에게. 자리는 이미 확보됨 |

## 5. 흐름 위 각 지점의 결정

초안 시점에 열려 있던 항목은 `SPEC.md`에서 전부 닫혔다. **규칙의 정본은 `SPEC.md`이고 아래는 어디를 보면 되는지의 색인이다.**

| 지점 | 결정 | 정본 |
|---|---|---|
| 3.2 작성 | 평점은 스키마를 그대로 쓴다(`overall`/`taste`/`design`/`service`). 목업의 `포장`은 `응대`로 흡수하고 화면에 `전체` 입력을 추가한다 | SPEC 2.2 |
| 3.2 작성 | 수정·삭제 **둘 다 연다. 기간 제한은 없다.** 다만 삭제하면 그 주문 상품에 재작성이 막힌다 | SPEC A4·A5 |
| 3.3 집계 | 대표값은 고객이 직접 매기는 `overall_rating`. **컬럼 갱신·재계산**이고 상품 도메인이 소유한다(`ProductRatingService`) | SPEC D1 |
| 3.4 노출 | **비로그인에게 연다.** 경로는 `/products/{id}/reviews`. 단 판매 중지 상품의 후기는 가린다 | SPEC 2.3·B1 |
| 3.5 관리자 | **숨김·해제만.** 삭제는 관리자에게 열지 않는다 | SPEC C1·C4 |
| 3.6 알림 | 등록 시 관리자에게, 답글 시 작성자에게. 답글 **수정에는 보내지 않는다** | SPEC D2 |

아직 열려 있는 것은 `SPEC.md` 9절에 모아 두었다. **대부분이 도메인 간 계약이고 조각별 선행 조건으로 붙어 있다**(2.7) — 주문 계약(조각 1), member 계약과 상품 상세 연결 방식(조각 3), D1 시그니처(조각 2, #33), 관리자 검색 계약(조각 5), `event_key`와 `NEW_REVIEW` 수신자(조각 7). 그 밖에 관리자 조치 이력 기록과 이미지 첨부가 남아 있다.

## 6. 팀 구현에 맞출 지점

새로 만들지 않고 이미 있는 것에 맞춘다.

### 6.1 알림 — 리뷰 자리가 이미 파여 있다

`feature/notification-sms-test`(PR #107, 미머지)에 리뷰 규격이 이미 들어가 있다. 리뷰는 `NotificationRequest`를 채워 호출만 하면 된다.

| 이미 있는 것 | 내용 |
|---|---|
| `NotificationType.NEW_REVIEW` | "새로운 리뷰가 등록되었습니다." → 관리자 |
| `NotificationType.CUSTOMER_REVIEW` | "사장님이 회원님의 리뷰에 답글을 남겼습니다." → 고객 |
| `NotificationType.CUSTOMER_ORDER_PICKED_UP` | "픽업이 완료되었습니다. **소중한 리뷰를 남겨 주세요**" → 리뷰 진입 동선 |
| `notifications.review_id` / `review_reply_id` | 연결 컬럼 |
| `NotificationRequest.reviewId` / `reviewReplyId` | DTO 필드 |

함께 맞출 규격: `event_key`(중복 알림 방지, `uk_notifications_receiver_event` UNIQUE), `DeliveryScope`(`WEB_ONLY` / `WEB_AND_SMS`).

> 아직 `dev`에 머지되지 않았고 해당 migration이 V0의 `notifications`를 `DROP` 후 재생성한다. 머지 전에는 규격이 바뀔 수 있다.

### 6.2 상태값 — `PostStatus`와 같은 모양으로

`reviews.status`의 스키마 기본값 `'VISIBLE'`은 **코드베이스 어디에도 없는 어휘**다. 팀이 쓰는 패턴은 둘이다.

| 패턴 | 쓰는 곳 | 성격 |
|---|---|---|
| `ACTIVE` / `INACTIVE` | `ProductStatus`, `ProductOptionStatus`, `CouponStatus` | 관리자가 노출을 켜고 끈다 |
| `PUBLISHED` / `DELETED` / `BLOCKED` | `PostStatus` | 작성자가 쓰고 관리자가 차단한다 |

리뷰는 후자와 같은 구조다 — 고객이 쓰고 관리자가 숨긴다. 목업의 `숨김`이 `BLOCKED`에 해당한다.

### 6.3 enum 규약 — 4개 도메인이 이미 동일

- 위치는 `domain/*/entity/`, **enum 이름을 DB 문자열로 그대로 저장**한다.
- 전이 규칙은 enum 안 `canTransitionTo(next)`에 둔다. `MemberStatus`·`PostStatus`·`CommentStatus`는 `null` 방어까지 같다.
- 종착 판정이 필요하면 `isFinal()`(`OrderStatus` 선례), 표시명이 필요하면 `@Getter` + `displayName`(`ProductOptionStatus` 선례).
- migration으로 `CHECK (status IN (...))`를 건다(선례 6건).

### 6.4 그대로 쓰는 것

- `OrderStatus.PICKED_UP` — 이미 종착 상태이고 `isFinal()`에 포함된다. 새로 만들 것이 없다.
- `ReviewErrorCode`의 `REVIEW_001` — 팀 규약(`<도메인>_NNN`)에 이미 맞다. 12개 도메인 전부 같다.
- 평점 집계 갱신 방식 — 커뮤니티 `like_count`의 **재계산** 선례를 따른다(`docs/community/DOMAIN.md` 6.5).

---

각 지점의 규칙이 정해지면 `docs/community/DOMAIN.md` 형식을 따라 `docs/review/DOMAIN.md`로 세운다.
