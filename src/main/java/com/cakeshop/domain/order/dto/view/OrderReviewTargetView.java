package com.cakeshop.domain.order.dto.view;

import java.time.LocalDateTime;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 주환
 * 작성일 : 2026-08-08
 * 기능    : 후기 작성 자격 검증용 주문 상품 단건
 * 설명 : 후기 작성 폼과 등록이 자격을 검증할 때 쓰는 주문 상품 정보다. 소유 회원 판정은 조회에서 끝난다.
 * ******************************
 *
 * <p><b>{@code OrderStatus} 를 노출하지 않고 {@code pickedUp} 으로 계산해 넘긴다.</b> 주문이 상태
 * 어휘를 바꿔도 리뷰가 깨지지 않는다. 회원 도메인이 {@code MemberStatus} 대신
 * {@code withdrawn} 을 넘기는 것과 같은 판단이다({@code MemberCommunityView}).</p>
 *
 * <p><b>소유 회원은 담지 않는다.</b> 조회가 {@code memberId} 를 받아 걸러 내므로, 남의 주문 상품은
 * 애초에 결과가 비어서 돌아온다. 리뷰가 소유자를 받아 비교하면 그 값을 다루는 자리가 하나 늘어날
 * 뿐이고, 없는 것과 남의 것은 어차피 같은 404 다({@code specs/review-write.md} A2 검증 1·2).</p>
 *
 * <p>{@code pickedUpAt} 은 픽업 전이면 {@code null} 이다. 폼 화면이 픽업 일시를 보여 주지만
 * 픽업 전 주문 상품은 400 으로 막히므로 화면까지 가지 않는다.</p>
 */
public record OrderReviewTargetView(
        Long orderItemId,
        Long productId,
        String productName,
        String orderNumber,
        boolean pickedUp,
        LocalDateTime pickedUpAt
) {
}
