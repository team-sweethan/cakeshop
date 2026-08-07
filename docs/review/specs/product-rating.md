# 상품 평점 집계 — D1

> 공통 규칙(상태·평점·권한·검증·오류 코드·표시·도메인 경계)은 `../DOMAIN.md` 2절이 정본이다.
> 조각 순서와 진행 상태는 `../PLAN.md`. 결정의 배경은 `../decisions/`.
> 조각: 2 (#33)

### D1. 상품 평점 집계

> **이 기능은 이슈 #33 `feat(product): 리뷰 평점 및 후기 수 연동`(시은 담당)과 같은 일이다.**
> 별도 이슈를 만들지 않고 #33에서 진행한다. 상세는 `PLAN.md` 조각 2와 R9.
> `domain/product/`는 시은님 담당이므로 **착수 전에 #33에 계약 시그니처를 올려 확인받는다**(`AGENTS.md` — 공개 Service 인터페이스는 먼저 협의).

**방식: `products` 컬럼 갱신. 집계는 리뷰가, 쓰기는 상품이** (2026-08-06 개정)

**이 방식에 이르기까지 하루에 방향이 네 번 뒤집혔다. 그 경로와 대가는 `decisions/ADR-001-rating-aggregation-ownership.md`가 소유한다.**

| | |
|---|---|
| 계약 | `ProductReviewCommandService` (상품 도메인 공개 Service, **신규**) |
| 상품 쪽 SQL | `ProductReviewMapper` + `mapper/product/ProductReviewMapper.xml` — **`products`만 만진다** |
| 리뷰 쪽 SQL | 집계 SELECT는 `ReviewMapper`에 둔다 |
| 형판 | `ProductStockService` — 주문·결제에 재고 변경을 공개하는 기존 쓰기 계약과 같은 모양 |

- **`ProductQueryService`가 아니다.** 그쪽은 `@Transactional(readOnly = true)`인 읽기 전용 계약이고(`docs/conventions.md` 15.3), 평점 갱신은 `products`에 쓴다. 쓰기 계약의 선례는 `ProductStockService`다.
- **`reviews`를 세는 것은 리뷰다.** 평균과 건수를 계산해 `ProductReviewCommandService`에 넘긴다. 상품 매퍼가 `reviews`를 읽는 안은 2026-08-06에 한 번 택했다가 되돌렸다 — 규칙에서 그 예외를 걷었기 때문이다(`DOMAIN 2.7`, `ADR-001`).
  - **대가를 알고 택한다.** 상품은 자기 컬럼인데도 받은 값을 검증할 수 없다. 리뷰가 잘못 세면 상품은 그대로 쓴다. 이건 규칙으로 못 막고 **D1 검증의 집계 테스트가 유일한 방어선**이다.
- **전용 매퍼를 새로 만드는 것은 이 저장소에서 첫 사례다.** `MemberQueryService`(PR #119)는 기존 `MemberMapper.xml`에 문장을 더했다. `ProductMapper.xml`이 700줄을 넘어 나누는 것이지만, PR 본문에 그 이유를 남겨 다음 사람이 판단 기준을 갖게 한다.

**잠금 문장을 새 매퍼에 복제하지 않는다.**

- `ProductReviewMapper.xml`에 `SELECT ... products ... FOR UPDATE`를 따로 선언하지 않는다. 기존 `ProductMapper.findSalesInfoByIdForUpdate`를 재사용하고, `ProductReviewCommandService`가 **두 매퍼를 함께 주입받는다.** 같은 패키지 안이라 도메인 경계를 넘지 않는다.
- 복제해도 당장은 똑같이 동작한다. 갈라지는 것이 문제다 — 한쪽에 `JOIN product_options`가 붙는 날 두 경로의 잠금 순서가 어긋나고, **두 파일을 함께 열어 본 사람이 없어 아무도 눈치채지 못한다.**
- 선례: 커뮤니티는 `lockPost`를 관리자 매퍼에 복제하지 않고 `CommunityAdminService`가 고객 매퍼를 함께 주입받는다(`docs/community/CLAUDE.md`).
- 결과적으로 `ProductReviewMapper.xml`에 들어가는 것은 **`UPDATE products` 하나뿐**이다.

**리뷰 쪽에서 이미 정해진 것**

- 호출 지점은 5곳이다 — A3(등록), A4(수정), A5(삭제), C4(숨김), C4(숨김 해제).
- **다섯 곳 모두 후기 쓰기와 같은 트랜잭션이다.** 후기 쪽이 먼저 커밋되고 집계가 실패하면 `average_rating`·`review_count`가 실제 후기와 **영구히 어긋난다** — 그 상품에 다음 쓰기가 올 때까지 아무도 모르는 채 잘못된 평점과 정렬이 나간다. A3만이 아니라 A4·A5·C4에도 같은 보장이 필요하다. rollback 테스트로 고정한다.
- 집계 대상은 `overall_rating`이다(`DOMAIN 2.2`).
- 집계에 포함되는 후기는 `status = 'PUBLISHED'`뿐이다.
- **공개 후기가 하나도 없으면 평균은 0이다.** `AVG(overall_rating)`은 대상이 없으면 `NULL`을 돌려주는데 `products.average_rating`은 `NOT NULL DEFAULT 0.00`이다. 계약을 `COALESCE(AVG(overall_rating), 0)`과 `COUNT(*)`로 적는다.
  - 이 지점은 등록이 아니라 **마지막 한 건을 삭제하거나 숨길 때** 온다. 후기가 쌓이는 동안에는 절대 드러나지 않아서, 검증에 "마지막 공개 후기를 지운다"를 따로 넣지 않으면 통과한다.
- **리뷰에서 `UPDATE products ...`를 직접 날리지 않는다.** #33 완료 조건에 "review 도메인이 Product Mapper를 직접 사용하지 않는다"가 못 박혀 있다. 도메인 간 공개 Service 계약을 거친다.

**호출 순서: 잠금 → `reviews` 쓰기 → 집계**

`ProductReviewCommandService.lockForRating(productId)`을 **후기 INSERT보다 먼저** 부른다. 저장한 뒤에 잠그면 이미 늦다.

- **이유는 주문 흐름이 이미 같은 순서를 쓰기 때문이다.** `ProductStockService.decreaseStock`이 `findSalesInfoByIdForUpdate`로 `products` 행을 배타 잠금한 뒤 재고를 줄인다.
- 리뷰가 `INSERT → 집계` 순서로 가면 `reviews` INSERT가 FK 확인으로 `products` 행에 **공유 잠금**을 먼저 걸고, 집계가 그것을 **배타 잠금으로 승격**하려 한다. 같은 상품에 후기 두 건이 동시에 들어오면 서로의 공유 잠금을 기다리며 교착이다.
- **리뷰끼리만이 아니다.** 누가 그 케이크를 주문하는 동안 다른 사람이 후기를 쓰면 주문 흐름과도 교착한다.
- 커뮤니티 좋아요와 같은 모양이고 해법도 같다 — 먼저 배타 잠금을 잡으면 승격이 없어 교착도 없다.
- **동시 요청이 없으면 결과가 똑같아 단일 스레드 테스트로는 드러나지 않는다.** 동시 요청 테스트로 고정한다.

**집계 SELECT는 잠금 읽기여야 한다.** 순서만으로는 부족하다.

- **집계 SELECT는 이제 `ReviewMapper`에 있다.** 거기에 `FOR UPDATE`를 붙인다. 평범한 SELECT로 두면 안 된다. 매퍼가 옮겨졌을 뿐 아래 이유는 그대로다 — 잠긴 것은 `products` 행이고 스냅샷이 굳는 것은 트랜잭션 단위라, 집계를 어느 도메인이 하든 같은 문제가 난다.
- MariaDB 기본 격리 수준은 `REPEATABLE READ`이고, **읽기 스냅샷은 트랜잭션의 첫 평범한 SELECT에서 고정된다.** A3는 잠금을 잡기 **전에** A2의 검증 4가지를 평범한 SELECT로 수행하므로 스냅샷이 그때 이미 굳는다.
- 그래서 동시 등록 두 건이 검증을 모두 끝낸 뒤 하나가 잠금을 기다리면, 그 트랜잭션은 잠금을 얻은 뒤에도 **먼저 커밋된 후기를 보지 못한다.** 자기 INSERT만 반영된 값으로 `products`를 마지막에 덮어써 `review_count`가 실제보다 작아진다. 잠금을 먼저 잡아 교착은 사라져도 값은 어긋난다.
- 잠금 읽기는 스냅샷이 아니라 최신 커밋을 읽는다(current read). 이미 상품 행을 배타 잠금한 뒤라 다른 트랜잭션과 경합하지 않는다.
- 이것도 **동시 요청 테스트로만 드러난다.** 위 잠금 순서 테스트에 "두 건 등록 후 `review_count = 2`"를 함께 넣는다.

**재계산 방식으로 간다** (증분 아님)

- 후기는 좋아요보다 훨씬 적게 쌓여 증분의 이점이 없고, 호출 지점이 5곳이나 되어 증분은 "여기서도 조정해야 하나"를 매번 판단해야 한다. 평균의 증분 갱신은 특히 어긋나기 쉽다.
- **`updated_at = updated_at` 보존.** 넣지 않으면 집계가 바뀔 때마다 상품에 수정 흔적이 남는다. 커뮤니티에서 같은 자리를 두 번 빠뜨려 화면에 `(수정됨)`이 붙는 버그가 실제로 났다.

**#33에서 확인받을 것**

- 계약 시그니처(`ProductReviewCommandService`의 메서드 이름과 인자)
- 전용 매퍼(`ProductReviewMapper`)를 두는 것에 대한 상품 담당자 동의
- 위 잠금 순서가 재고 경로와 어긋나지 않는지
- **리뷰가 계산한 평균·건수를 상품이 그대로 쓰는 것**에 대한 동의. 상품 쪽에 검증할 방법이 없다
