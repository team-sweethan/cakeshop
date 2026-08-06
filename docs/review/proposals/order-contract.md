## 조각 1 — 주문 도메인에 조회 계약을 추가하려 합니다

후기는 본인이 픽업 완료한 주문 상품에만 쓸 수 있는데, `conventions.md` 15절이 다른 도메인 테이블 직접 조회를 금지해서 주문 쪽 공개 계약이 필요합니다.

### 생기는 파일 (전부 신규, 기존 파일 수정 0건)

```
domain/order/service/OrderItemQueryService.java   ← 공개 계약
domain/order/mapper/OrderItemQueryMapper.java
domain/order/dto/view/PickedUpOrderItem.java
domain/order/dto/view/OrderItemDetail.java
resources/mapper/order/OrderItemQueryMapper.xml
```

매퍼를 새로 두는 건 `OrderMapper.java`·`.xml`을 안 건드리려는 것입니다. (community·member·payment도 도메인당 매퍼가 여럿입니다.)

### 시그니처

```java
public interface OrderItemQueryService {

    /** 픽업 완료 주문 상품을 제외 목록을 빼고 페이징해 돌려준다. */
    PageResult<PickedUpOrderItem> findPickedUpItems(
            long memberId, Collection<Long> excludedOrderItemIds, PageRequest pageRequest);

    /** 단건. 없으면 empty. 소유·상태 판단은 호출측이 한다. */
    Optional<OrderItemDetail> findOrderItem(long orderItemId);
}

public record PickedUpOrderItem(
        long orderItemId, String productName, String orderNumber, LocalDateTime pickedUpAt) {}

public record OrderItemDetail(
        long orderItemId, long memberId, long productId, boolean pickedUp,
        String productName, String orderNumber, LocalDateTime pickedUpAt) {}
```

메모 두 개만 붙이면:

- **제외 목록을 계약이 받는 이유** — 리뷰가 이미 쓴 항목을 뒤에서 걸러내면 최근 20건이 전부 작성 완료일 때 첫 페이지가 통째로 빕니다. 그래서 거르는 것까지 계약이 맡습니다. 주문이 리뷰를 아는 게 아니라 불투명한 ID 목록을 받을 뿐입니다.
- **단건이 `OrderStatus` 대신 `boolean pickedUp`인 이유** — 없음/남의 것은 404, 픽업 전은 400으로 갈려서 판단은 리뷰가 해야 하고, 주문 쪽 상태 어휘가 바뀌어도 리뷰가 안 깨집니다. **`OrderStatus`가 낫다고 보시면 맞추겠습니다.**

새 파일에는 `작성자 : HyunGyu-Cho` / `담당자 : 주환`으로 헤더 주석을 답니다(`AGENTS.md`).

### 여쭐 것 둘

1. **주문 도메인에 이 파일들을 만들어도 될까요?** 파일이 주문 쪽에 사는 이상 앞으로 주문 스키마를 바꾸실 때 같이 보셔야 해서 여쭙습니다.
2. **구현을 누가 쓸까요?** 15절이 "시그니처만 합의하고 사용하는 쪽은 stub으로 진행"으로 정하고 있어서, 제가 구현까지 만들고 리뷰만 받으셔도 되고 시그니처만 확정해 주시면 제가 stub으로 진행해도 됩니다.

나머지(이름·필드 구성 등)는 편하신 대로 맞추겠습니다.
