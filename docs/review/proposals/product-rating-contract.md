## 조각 2 — 상품 도메인에 평점 집계 계약을 추가하려 합니다

`products.average_rating`·`review_count`가 이미 상품 정렬에 쓰이는데 갱신하는 코드가 없어 운영에서는 계속 0입니다. 후기 작성(조각 1)과 한 배포 단위로 묶어 채웁니다.

### 생기는 파일 (전부 신규, 기존 파일 수정 0건)

```
domain/product/service/ProductRatingService.java   ← 공개 쓰기 계약
domain/product/mapper/ProductReviewMapper.java
resources/mapper/product/ProductReviewMapper.xml
```

`ProductMapper.xml`이 723줄이라 나눕니다. 잠금은 기존 `ProductMapper.findSalesInfoByIdForUpdate`를 **그대로 재사용**하고 복제하지 않습니다 — `ProductRatingService`가 두 매퍼를 함께 주입받습니다.

### 시그니처

```java
public interface ProductRatingService {

    /** 집계 전에 상품 행을 배타 잠금한다. 후기 INSERT보다 먼저 부른다. */
    void lockForRating(long productId);

    /** 그 상품의 공개 후기로 평균 평점과 후기 수를 다시 계산해 반영한다. */
    void recalculate(long productId);
}
```

`ProductQueryService`가 아니라 새 서비스인 건 그쪽이 읽기 전용 계약이고 집계는 `products`에 쓰기 때문입니다. 형판은 `ProductStockService`입니다.

### 규칙을 하나 바꿨습니다 — 봐 주셔야 합니다

`ProductReviewMapper`가 **`reviews`를 직접 읽습니다.** 원래 `conventions.md` 15절이 다른 도메인 테이블 조회를 예외 없이 금지해서 걸리는 자리였고, 이렇게 다듬어 열었습니다.

> 다른 도메인 테이블은 읽지 않는다. 단 **자기 도메인이 소유한 파생 컬럼을 유지하기 위한 집계 읽기**는 전용 매퍼에서 허용한다. 표시·검색·업무 규칙 판단은 예외 없이 금지한다.

리뷰가 평균·건수를 계산해서 넘기는 안(`applyRating(productId, average, count)`)은 버렸습니다. **상품 입장에서 자기 컬럼인데도 받은 값을 검증할 수 없어서**입니다.

문서는 PR #129에 올라가 있습니다.

### 잠금 순서가 재고 경로와 어긋나지 않는지 봐 주세요

**잠금 → `reviews` INSERT → 집계** 순서로 갑니다. `ProductStockService.decreaseStock`이 이미 쓰는 순서와 같게 맞춘 것입니다. 반대로 가면 `reviews` INSERT의 FK 확인이 `products`에 공유 잠금을 걸고 집계가 배타로 승격하려 해서, **누가 그 케이크를 주문하는 동안 다른 사람이 후기를 쓰면 주문 흐름과 교착합니다.**

집계 SELECT에는 `FOR UPDATE`를 붙입니다(`REPEATABLE READ` 스냅샷 때문에 평범한 SELECT면 건수가 어긋납니다). 둘 다 동시 요청 테스트로 고정하겠습니다.

### 그 밖에 리뷰 쪽에서 정해 둔 것

호출 지점 5곳(등록·수정·삭제·숨김·숨김 해제) 전부 후기 쓰기와 같은 트랜잭션 / 집계 대상은 `overall_rating`, `PUBLISHED`만 / 평균은 `COALESCE(AVG(...), 0)` / `UPDATE`에 `updated_at = updated_at` 보존.

새 파일에는 `작성자 : HyunGyu-Cho` / `담당자 : 시은`으로 헤더 주석을 답니다(`AGENTS.md`).

### 여쭐 것 셋

1. **상품 도메인에 이 파일들을 만들어도 될까요?** 파일이 상품 쪽에 사는 이상 앞으로 같이 보셔야 해서 여쭙습니다.
2. **`ProductReviewMapper`가 `reviews`를 직접 읽는 것에 동의하시나요?** 위 규칙 변경입니다.
3. **잠금 순서가 재고 경로와 부딪히지 않을까요?** 이건 재고 쪽을 아시는 분 확인이 필요합니다.

나머지(이름·시그니처 구성)는 편하신 대로 맞추겠습니다.
