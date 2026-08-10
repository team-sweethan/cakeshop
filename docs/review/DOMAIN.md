# Review 도메인 정본

> 이 문서는 Review 도메인에서 **변하지 않는 것**의 정본이다 — 정의, 전체 흐름, 공통 규칙, 기능 인벤토리, 화면, 미정.
> 기능 하나하나의 입력·처리·출력·검증은 `specs/`가 소유한다. 1절 기능 목록의 `spec` 열이 라우팅 표다.
> **공통 규칙과 그 근거는 이 문서에만 있다** — 여러 기능에 걸리는 것(2절). 다른 문서는 여기를 가리킬 뿐 다시 적지 않는다.
> **기능 하나에만 걸리는 규칙과 그 근거는 그 기능의 `specs/` 문서가 소유한다** — 답글의 상태 확인·저장 원자성(`specs/review-reply.md` C5), 집계의 잠금 순서(`specs/product-rating.md`)처럼. 이것도 두 곳에 적지 않는다.
> 프로젝트 전체 규칙은 `AGENTS.md`가 상위 정본이며, 충돌 시 `AGENTS.md`가 우선한다.

| 찾는 것 | 문서 |
|---|---|
| 기능 단위 명세 (입력·처리·출력·검증) | `specs/` — 1절 `spec` 열 |
| 상태 모델·평점·권한·검증·오류 코드·표시·도메인 경계 | 이 문서 2절 |
| 화면 인벤토리 | 이 문서 3절 |
| 아직 안 정해진 것 | 이 문서 4절 |
| 조각 순서, 진행 상태, 위험, 결정 로그 | `PLAN.md` |
| 왜 그렇게 정했나 — PLAN 한 줄로 재구성이 안 되는 것 | `decisions/` |
| 머지되었고 더 이상 구속하지 않는 것 | `history/` |
| 도메인 작업 규칙 (구현 시작 전) | `src/main/java/com/cakeshop/domain/review/CLAUDE.md` |

## 한 문장 정의

Cakeshop 후기는 **케이크를 실제로 받아 간 고객이 그 주문 상품에 대해 남기는 평가**다.

커뮤니티의 `REVIEW`(후기) 카테고리와 다르다. 커뮤니티 후기는 누구나 쓰는 글이고, 이쪽은 **주문 이력이 있어야만 쓸 수 있는 평점 데이터**다.

## 전체 흐름

```
[주문]                    [후기]                      [상품]
  │                         │                           │
  ├─ 결제 완료               │                           │
  ├─ 픽업 완료 ──────────────┤                           │
  │  (PICKED_UP)      작성 자격 발생                     │
  │                         │                           │
  │                    후기 작성 ───────────────────► 평점 집계
  │                    (주문상품당 1개)              average_rating
  │                         │                       review_count
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

**입구는 하나다.** 후기는 스스로 시작되지 않는다. 주문이 `PICKED_UP`에 도달하는 것이 유일한 입구이고, `OrderStatus.PICKED_UP`은 종착 상태(나가는 전이 없음)라 **자격은 한 번 생기면 사라지지 않는다.**

**자격의 단위는 주문이 아니라 주문 상품(`order_items`)이다.** 케이크 세 종류를 한 번에 샀으면 후기도 세 개다.

집계로 나가는 흐름은 조각 1·2를 함께 머지하면서 닫혔다. 왜 그 둘을 한 배포 단위로 묶었는지는 `PLAN.md` R2가 소유한다 — 여기에 다시 적지 않는다.

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
| 완료 | 머지되어 동작한다 |

## 1. 기능 목록

`spec` 열이 그 기능의 명세가 어느 파일에 있는지 가리킨다. **ID는 전역 고유하므로 파일이 바뀌어도 ID는 그대로다.**

| ID | 기능 | 액터 | 경로 | 현재 | 조각 | spec |
|---|---|---|---|---|---|---|
| **A1** | 작성할 후기 목록 | 고객 | `GET /mypage/reviews/writable` | **완료** | 1 | `review-write.md` |
| **A2** | 후기 작성 폼 | 고객 | `GET /reviews/new?orderItemId={N}` | **완료** | 1 | `review-write.md` |
| **A3** | 후기 등록 | 고객 | `POST /reviews` | **완료** | 1 | `review-write.md` |
| **A4** | 후기 수정 | 고객 | `GET·POST /reviews/{id}/edit` | **완료** | 4 | `review-edit-delete.md` |
| **A5** | 후기 삭제 | 고객 | `POST /reviews/{id}/delete` | **완료** | 4 | `review-edit-delete.md` |
| **A6** | 이미지 첨부 | 고객 | (A2·A3에 포함) | 목업 입력만 | 8 (2차) | `review-write.md` |
| **B1** | 상품 후기 목록 | 누구나 | 미리보기 3개(상품 상세 안) · 전체 `GET /products/{id}/reviews` | **완료** | 3 | `review-read.md` |
| **B2** | 상품 평균 평점·후기 수 | 누구나 | (상품 상세에 포함) | **완료** | 2 | `review-read.md` |
| **B3** | 내가 쓴 후기 목록 | 고객 | `GET /mypage/reviews` | **완료** | 3 | `review-read.md` |
| **B4** | 후기에 달린 답글 노출 | 누구나 | (B1·B3에 포함) | **완료** | 6 | `review-reply.md` |
| **C1** | 관리자 후기 목록 | 관리자 | `GET /admin/reviews` | **완료** | 5 | `review-admin.md` |
| **C2** | 관리자 검색·필터 | 관리자 | (C1의 파라미터) | **완료** | 5 | `review-admin.md` |
| **C3** | 관리자 후기 상세 | 관리자 | `GET /admin/reviews/{id}` | **완료** | 5 | `review-admin.md` |
| **C4** | 후기 숨김·해제 | 관리자 | `POST /admin/reviews/{id}/block` · `/unblock` | **완료** | 5 | `review-admin.md` |
| **C5** | 답글 작성 | 관리자 | `POST /admin/reviews/{id}/replies` | **완료** | 6 | `review-reply.md` |
| **C6** | 답글 수정 | 관리자 | `POST /admin/reviews/{id}/replies/edit` | **완료** | 6 | `review-reply.md` |
| **D1** | 상품 평점 집계 | — | (Service 계약) | **완료** | 2 (#33) | `product-rating.md` |
| **D2** | 알림 발송 | — | (Service 계약) | **완료** | 7 | `review-notification.md` |
| **E1** | `ReviewStatus` enum + `CHECK` | — | — | **완료** (#125) | 0 | `history/2026-08-slice-0-schema.md` |
| **E2** | 평점 범위 `CHECK` | — | — | **완료** (#125) | 0 | `history/2026-08-slice-0-schema.md` |
| **E3** | `Review`·`ReviewReply` 엔티티 | — | — | **완료** (#125) | 0 | `history/2026-08-slice-0-schema.md` |

**신규 화면 5개가 전부 났다** — A1(`customer/review/writable.html`, 조각 1), **B1 전체 목록**(`customer/review/product.html`, 조각 3), B3(`customer/review/my.html`, 조각 3), A4(`customer/review/edit.html`, 조각 4), C3(`admin/review/detail.html`, 조각 5). C5·C6의 답글 영역은 조각 6에서 C3 화면 안에 들어갔다. 화면 인벤토리의 정본은 3절이다.

## 2. 공통 규칙

### 2.1 상태 모델

`ReviewStatus { PUBLISHED, DELETED, BLOCKED }`. `PostStatus` 선례를 그대로 따른다.

**`reviews.status`가 노출 여부의 유일한 기준이다.** 조건이 두 개면 새 쿼리를 추가할 때 하나를 빠뜨린다.

**선행 관문은 이 원칙과 다르다.** B1은 후기를 읽기 전에 상품이 공개되는지 먼저 본다(`specs/review-read.md` B1). 그것은 후기의 노출 조건이 아니라 **그 목록에 들어올 수 있는지**를 가르는 문이라 위 원칙을 깨지 않는다. 후기 SQL의 `WHERE`에 조건을 하나 더 얹는 것과는 다른 자리다.

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
- 스키마 기본값 `'VISIBLE'`은 **코드베이스 어디에도 없는 어휘**였다. 조각 0에서 `'PUBLISHED'`로 바꾸고 `CHECK (status IN ('PUBLISHED','DELETED','BLOCKED'))`를 걸었다(E1, `history/2026-08-slice-0-schema.md`).

### 2.2 평점

**스키마를 그대로 쓴다. 컬럼을 바꾸지 않는다.**

| 화면 라벨 | 컬럼 | 필수 |
|---|---|---|
| 전체 | `overall_rating` | 필수 |
| 맛 | `taste_rating` | 필수 |
| 디자인 | `design_rating` | 필수 |
| 응대 | `service_rating` | 필수 |

- 네 값 모두 **1~5 정수**. `TINYINT UNSIGNED`라 DB만으로는 0과 255가 들어간다.

**범위 밖 값은 세 겹으로 막고, 그중 화면이 첫 겹이다.**

| 겹 | 무엇 | 막는 것 |
|---|---|---|
| 화면 | 별 5개만 렌더링한다. 0이나 6에 해당하는 입력을 **애초에 만들지 않는다** | 평범한 사용자 |
| 서버 | 요청 DTO에 `@NotNull` + `@Min(1)` `@Max(5)`(2.4) | 폼을 거치지 않은 직접 호출 |
| DB | 조각 0의 `CHECK (... BETWEEN 1 AND 5)`(E2, `history/2026-08-slice-0-schema.md`) | 위 둘을 지나온 것 — 최후의 그물 |

- **기본값으로 별 하나를 찍어 두지 않는다.** 미선택 상태를 두고 서버 필수 검증으로 막는다. 기본 선택을 두면 고객이 손대지 않은 값이 그대로 저장되고, `overall_rating`은 상품 평균으로 나가는 대표값이라 **손대지 않은 별 1개가 집계에 섞인다.** 빈 채로 제출하면 "평점을 선택해 주세요"로 돌려보내는 편이 낫다.
- **이 세 겹이 있으므로 기존 데이터 보정 절차는 두지 않는다.** 조각 0에서 `CHECK`를 걸 당시 `reviews`에 INSERT하는 코드가 저장소에 없었고(작성 경로는 조각 1에서 생겼다), 평점 컬럼에는 `status`의 `DEFAULT 'VISIBLE'` 같은 **체계적 발생원도 없다.** 게다가 6을 5로 깎는 "보정"은 고객 평가를 지어내는 것이다. `CHECK`가 배포에서 막고 멈추는 것이 올바른 동작이다.
- **대표값은 `overall_rating`이다.** 고객이 직접 매기며, 나머지 3종의 평균으로 계산하지 않는다.
  - 근거: 계산값으로 두면 "맛은 5인데 전체는 3" 같은 실제 감상을 담을 자리가 없어진다. 관리자 목록의 `★★★★★ 5.0`과 평점 필터도 단일 값을 전제한다.
- `products.average_rating` = `AVG(overall_rating)`. 세부 3종은 집계에 쓰지 않는다.
- 목업의 `포장`은 `응대`로 흡수한다. 대응 컬럼이 없고, 컬럼을 새로 만들 만큼 구분 실익이 크지 않다.
- **목업에 없던 `전체` 입력을 화면에 추가했다**(조각 1). 목업은 4종 중 `전체`가 빠지고 `포장`이 들어가 있었다.

### 2.3 권한과 경로

`SecurityConfig`는 이미 `/reviews/**` 인증 필요, `/products/**` 공개, `/admin/**` `hasRole("ADMIN")`이다. **경로를 아래처럼 둔 덕에 기본 규칙은 그대로 뒀다.** 예외였던 local 프로필의 목업 preview 목록만 조각 1에서 손댔다(아래).

| 경로 | 접근 | 기능 |
|---|---|---|
| `GET /products/{id}/reviews` | 공개 | B1 |
| `GET·POST /reviews/**` | 인증 | A2, A3, A4, A5 |
| `GET /mypage/reviews/**` | 인증 | A1, B3 |
| `/admin/reviews/**` | 관리자 | C1~C6 |

- **후기 목록을 공개하는 이유**: 상품 상세의 평균 평점·후기 수는 이미 비로그인에게 보인다. 근거가 되는 후기만 가리면 숫자만 있고 이유는 없는 화면이 된다. 구매 전 고객이 후기를 읽는 것이 후기의 목적이다.
- **local 프로필에서는 `GET /reviews/**`가 비로그인에 열려 있었다.** `SecurityConfig`가 `app.mockup.public-preview=true`일 때 `/orders/**`·`/notifications`·`/chat` 등을 앞에서 `permitAll`로 매칭한다. 목업을 로그인 없이 보려고 둔 것이라, A2가 실기능이 되는 **조각 1에서 `/reviews/**`를 이 목록에서 뺐다.**
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

`REVIEW_001`~`REVIEW_006`이 코드에 있다(`ReviewErrorCode`). 나머지는 조각 진행에 맞춰 추가한다. 번호는 `<도메인>_NNN` 팀 규약을 따르며, **`조각` 열이 그 코드가 들어온(또는 들어올) 조각이다.**

| 코드 | 이름 | 메시지 | HTTP | 조각 |
|---|---|---|---|---|
| `REVIEW_001` | `NOT_PICKED_UP` | 픽업 완료된 주문만 후기를 작성할 수 있습니다. | 400 | (기존) |
| `REVIEW_002` | `REVIEW_NOT_FOUND` | 후기를 찾을 수 없습니다. | 404 | 3 |
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
- 작성자명은 `members.nickname`이지만 **`members`를 JOIN해서 가져오지 않는다.** member 도메인 계약이 DTO로 돌려준다(2.7). 탈퇴 여부 판단도 그 DTO가 들고 온다 — 리뷰가 `members.status`를 읽고 판단하지 않는다.
- 목록에서 상품명은 **`order_items.product_name` 스냅샷**을 쓴다(주문 이력 성격의 화면 — A1, B3). 상품 상세의 후기 목록(B1)은 그 상품 화면 안이므로 상품명을 따로 표시하지 않는다.

### 2.7 도메인 경계 — 다른 도메인 테이블을 읽지 않는다

**리뷰 매퍼는 `reviews`·`review_replies`·`review_images` 외의 테이블을 JOIN하지 않는다.** 표시용이든 검색용이든 같다.

**규칙은 양방향이다.** 다른 도메인이 `reviews`를 읽는 것도 같은 선을 받는다.

필요한 값은 **그 도메인의 전용 QueryService가 DTO로 돌려준다.** 없으면 만든다.

**만드는 순서는 "물어보고 만든다"가 아니라 "만들고 PR에서 확인받는다"이다**(`docs/conventions.md` 12절). 12절이 사전 협의를 요구하는 것은 **기존** 공개 Service 인터페이스나 상태 전이를 **변경**할 때다. 담당자가 쓴 코드를 한 줄도 고치지 않는다면 깨질 남의 호출부가 없으므로 사전 협의 없이 만들고, 담당자를 PR 리뷰어로 지정해 확인받는다.

**내가 만든 계약 파일에 메서드를 더하는 것도 여기에 든다.** `MemberReviewQueryService`·`OrderReviewQueryService`는 담당자의 도메인 폴더에 있지만 내가 쓴 파일이고, 메서드가 하나 느는 것만으로는 따라 움직일 호출부가 없다. 조각 3의 `findOrderItemSnapshots`와 조각 5의 검색 계약 둘이 그 자리다. 계약이 상대 도메인에 **쓰기**를 하더라도 이 기준은 같다 — 갈리는 지점은 읽기·쓰기도, 파일이 새 것이냐도 아니라 **남의 호출부가 따라 움직이느냐**다.

| 필요한 것 | 쓰는 곳 | 도메인 | 담당 | 계약 |
|---|---|---|---|---|
| 후기 쓸 수 있는 주문 상품 목록 + 단건 자격 검증 | A1·A2·A3 | `order` | 주환 | `OrderReviewQueryService.findWritableOrderItems` · `findReviewTarget` (PR #158) |
| 이미 쓴 후기의 상품명·주문번호 스냅샷 | B3·C1·C3 | `order` | 주환 | `OrderReviewQueryService.findOrderItemSnapshots(Collection<Long>)` → `List<OrderReviewSnapshotView>` |
| 작성자 표시명(`nickname`, 탈퇴 여부) | B1·C1·C3 | `member` | 수민 | `MemberReviewQueryService.getMembersByIds(Collection<Long>)` → `List<MemberReviewView>` |
| 관리자 검색의 작성자명 조건 | C2 | `member` | 수민 | `MemberReviewQueryService.findMemberIdsByNickname(String)` → `List<Long>` |
| 관리자 검색의 상품명 조건 | C2 | `order` | 주환 | `OrderReviewQueryService.findOrderItemIdsByProductName(String)` → `List<Long>` |
| 신규 후기 알림을 받을 관리자 | D2 | `member` | 수민 | `MemberReviewQueryService.findActiveAdminIds()` → `List<Long>` |
| 알림 발송 | D2(`specs/review-notification.md`) | `notification` | 민정 | `NotificationService.makeNotification` (기존) |
| 상품 판매 여부 | B1 | `product` | 시은 | `ProductQueryService.getSalesInfo` (기존) |
| 평점 갱신 | D1(`specs/product-rating.md`) | `product` | 시은 | `ProductReviewCommandService` |

**반대 방향이 하나 있다.** 상품 상세가 후기를 붙이는 자리다 — 리뷰가 소유하는
`ReviewProductQueryService.getPreview(long)` → `List<ProductReviewView>` 를 `ProductController.detail`이
부른다. 상품이 `reviews` 를 읽지 않게 하려는 것이라 근거는 위와 같고, 방향만 반대다.

**B3 는 작성자 계약을 부르지 않는다.** 보는 사람이 곧 작성자라 표시명이 필요 없다.

**연동 계약의 이름과 위치는 `docs/conventions.md` 12절을 따른다** — 데이터를 소유한 도메인이 이름 앞에 온다.

**목록 화면은 건별 조회가 아니라 ID 묶음 조회다.** 후기 20건에 회원 조회를 20번 하면 N+1이다. 계약은 `Collection<Long>`을 받아 DTO 목록을 돌려주는 모양이어야 한다. **이것도 계약 설계에 포함한다.**

**리뷰에는 예외가 하나도 없다** (2026-08-06 개정)

**규칙 문장과 그 근거는 `docs/conventions.md` 12절이 정본이다.** 팀 전체에 걸리는 규칙이라 여기 다시 적지 않는다. 리뷰에서 어디가 갈리는지만 남긴다.

| 경우 | 판정 |
|---|---|
| 리뷰 매퍼가 `members`를 JOIN해 닉네임을 표시 | 금지 |
| 리뷰 매퍼가 `orders`를 읽어 작성 자격을 판단 | 금지 |
| 리뷰 매퍼가 `order_items`를 JOIN해 관리자 검색을 건다 | 금지 |
| 상품 매퍼가 `reviews`를 집계해 `products` 컬럼을 유지 | **금지** — 값은 리뷰가 계산해 넘긴다(D1) |

**리뷰에는 ReadModel이 없다.** 12절의 ReadModel 예외는 여러 도메인을 **집계·요약**하는 통계·대시보드에만 열린다. C1·C2 관리자 목록·검색은 조건이 다른 도메인에서 오더라도 결국 **후기 목록**이라 대상이 아니다 — 리뷰가 소유하고 조건은 계약으로 받는다.

- **쓰기 근거가 되는 읽기에도 예외가 없다.** 평점 집계가 여기 걸린다 — `reviews`를 세어 `products`를 갱신하는 것이라 조회 전용이 아니다.
- **한때 열었던 "파생 컬럼 유지를 위한 집계 읽기" 예외는 걷었다.** 그 예외로 허용하던 D1의 방향이 뒤집혔다(`decisions/ADR-001-rating-aggregation-ownership.md`).
- 결합을 막는 테스트는 D1 검증에 있다 — **"`BLOCKED` 후기는 평균에서 빠진다"**. `reviews.status` 어휘가 바뀌면 그 테스트가 빨간불이 된다.

**검색 조건은 리뷰가 뒤에서 거르지 않는다.** 후기를 먼저 페이지하고 이름을 나중에 맞추면 **일치 행과 전체 건수가 둘 다 틀어진다**(C2, A1이 같은 모양이다). 조건을 계약에 넘겨 ID 목록이나 페이지 결과를 받는다.

**ID 목록을 받는 계약에서는 `null`과 빈 목록의 뜻이 다르다.** `null`은 그 조건을 걸지 않는 것이고, 빈 목록은 계약이 "일치하는 것이 없다"고 답한 것이라 결과가 0건이어야 한다. 둘을 같게 다루면 **검색어에 아무도 안 걸렸을 때 전체 목록이 나온다.** `IN ()`은 문법 오류라 빈 목록은 `1 = 0`으로 받는다(`ReviewAdminMapper.xml`).

**부분 일치 검색어는 계약을 가진 도메인이 이스케이프한다.** `%`·`_`를 그대로 넘기면 `%` 한 글자로 전체가 조회된다. 이스케이프 문자는 `!`이고 SQL이 `ESCAPE '!'`로 받는다 — 기본값인 역슬래시는 문자열 리터럴 단계에서도 특수 문자라 이스케이프가 두 겹이 되고, 어느 겹이 빠졌는지 SQL만 보고는 드러나지 않는다.

> 선을 하나로 두는 이유: 표시용 조인까지 허용하면 어디까지가 표시용인지를 매번 판단해야 하고, 그 판단이 한 번 느슨해지는 순간 자격 검증 같은 업무 규칙도 같은 문으로 들어온다 — 실제로 A1·A2·A3가 그렇게 들어와 있었다. ReadModel 예외는 이 판단을 되살리지 않는다. 갈리는 지점이 **"표시용이냐"가 아니라 "읽기 전용이냐"**라서, 쓰기 SQL을 담을 수 없는 ReadModel로는 업무 규칙이 같은 문으로 들어올 수가 없다.

## 3. 화면

| 화면 | 파일 | 상태 |
|---|---|---|
| 작성할 후기 목록 (A1) | `customer/review/writable.html` | 완료 (조각 1) |
| 후기 작성 폼 (A2) | `customer/review/form.html` | 완료 (조각 1) |
| 후기 수정 폼 (A4) | `customer/review/edit.html` | 완료 (조각 4) |
| 내 후기 목록 (B3) | `customer/review/my.html` | 완료 (조각 3. 조각 4에서 수정·삭제 버튼) |
| 상품 후기 미리보기 3개 (B1·B2·B4) | `customer/product/detail.html` | 후기 영역 교체 + `전체 리뷰 확인` 버튼 완료 (조각 3. 답글은 조각 6) |
| 상품 후기 전체 목록 (B1·B4) | `customer/review/product.html` | 완료 (조각 3. 답글은 조각 6) |
| 관리자 목록 (C1·C2) | `admin/review/list.html` | 완료 (조각 5. `삭제` 제거, 상태 필터 추가) |
| 관리자 상세 (C3) | `admin/review/detail.html` | 완료 (조각 5. C5·C6 답글 영역은 조각 6) |

진입점 수정: `customer/member/mypage.html`(A1·B3 링크 2개 — 완료), `customer/product/detail.html`(작성 버튼 → A1, `전체 리뷰 확인` → B1 — 완료), `home/screens.html`(화면 카탈로그 C16·C22·C23·C24와 관리자 A11·A15 — 완료), `customer/order/detail.html`(`orderItemId` 전달 — **아직 안 함**. 지금 작성 진입은 A1 목록 하나뿐이다)

**화면 카탈로그(`home/screens.html`)도 진입점이다.** 새 화면을 내고 여기를 빠뜨리면 팀이 그 화면의 존재를 모른다. `specs/review-write.md`가 조각 1에서 같은 자리를 한 번 놓쳤다.

상품 후기 한 건의 표시는 미리보기와 전체 목록이 `fragments/customer/product-review.html`을 함께 쓴다.
두 자리의 모양이 갈리면 상세에서 본 후기가 전체 목록에서 달라 보인다. **B4 답글은 B3(`customer/review/my.html`)까지
같은 파일의 `reply` 프래그먼트를 쓴다** — 세 자리에 붙는 같은 덩어리다.

**화면 문구를 문서로 따로 관리하지 않는다.** 커뮤니티가 그 방식(`screens/*.md` + 문구 대조 하네스)을 먼저 세웠다가 2026-08-09에 걷어냈다 — 문구를 문서에 복사하면 템플릿과 갈라지고, 그것을 잡던 검사는 `docs/testing.md` 4절의 삭제 대상이었다. 이 표가 화면 인벤토리의 정본이고, 무엇이 언제 보이는지는 2절과 각 spec이, 실제 출력은 렌더링 테스트가 맡는다.

## 4. 미정

| 항목 | 결정 시점 |
|---|---|
| 관리자 숨김 사유·조치 이력 기록 여부 | 필요해지면 새 migration (C1) |
| A6 이미지 첨부 | 2차 |

**조각 7에서 해소된 것**: D2 `event_key` 규격과 `NEW_REVIEW` 수신 관리자. 둘 다 `specs/review-notification.md` D2가 정본이고, 정한 날짜는 `PLAN.md` 결정 로그에 있다.

---

**결정 로그는 `PLAN.md`에 있다.** 무엇을 언제 왜 정했는지는 그쪽 한 곳에만 쌓는다. 이 문서에는 **지금 유효한 규칙과 그 근거**만 두고, 규칙을 바꾸면 해당 절을 고치고 PLAN 결정 로그에 한 줄 남긴다. 한 줄로 재구성이 안 되는 결정은 `decisions/`로 승격한다.
