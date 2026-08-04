package com.cakeshop.domain.product.dto.view;

import java.math.BigDecimal;
import java.util.List;

import com.cakeshop.domain.product.entity.ProductType;

import lombok.Getter;
import lombok.Setter;

/**
 * 고객 상품 상세 화면에 출력할 상품 정보를 담는다.
 *
 * <p>상품 Entity 전체를 화면에 전달하지 않고,
 * 고객에게 공개할 정보만 조회하여 저장한다.</p>
 */
@Getter
@Setter
public class ProductDetailView {

    /** 상품 식별자. */
    private Long id;

    /** 상품이 속한 카테고리의 식별자. */
    private Long categoryId;

    /** 고객 화면에 표시할 카테고리 이름. */
    private String categoryName;

    /** 상품명. */
    private String name;

    /** 상품 상세 설명. */
    private String description;

    /** 옵션 추가 금액을 제외한 상품의 기본 판매 가격. */
    private BigDecimal basePrice;

    /**
     * 현재 주문 가능한 재고 수량.
     *
     * <ul>
     *     <li>{@code null}: 재고 제한 없음</li>
     *     <li>{@code 0}: 품절</li>
     *     <li>{@code 1 이상}: 주문 가능한 재고 수량</li>
     * </ul>
     */
    private Integer stockQuantity;

    /** 일반 상품, 주문 제작 상품 등을 구분하는 상품 유형. */
    private ProductType productType;

    /** 주문 제작 상품이 관리자 승인 후 준비되기까지 필요한 최소 일수. */
    private Integer preparationDays;

    /** 상품 후기의 평균 평점. */
    private BigDecimal averageRating;

    /** 상품에 작성된 후기의 총개수. */
    private Integer reviewCount;

    /** 표시 순서대로 정렬된 상품 이미지 목록. */
    private List<ProductImageView> images = List.of();

    /**
     * 재고 수량 제한 없이 주문할 수 있는 상품인지 확인한다.
     *
     * @return 재고 수량이 {@code null}이면 {@code true}
     */
    public boolean isUnlimitedStock() {
        return stockQuantity == null;
    }

    /**
     * 상품이 품절 상태인지 확인한다.
     *
     * @return 재고 수량이 0이면 {@code true}
     */
    public boolean isOutOfStock() {
        return stockQuantity != null && stockQuantity == 0;
    }

    /**
     * 현재 상품을 주문할 수 있는 재고가 있는지 확인한다.
     *
     * <p>재고 제한이 없거나 재고 수량이 1개 이상이면 주문할 수 있다.
     * 상품의 판매 상태는 Mapper 조회 조건에서 별도로 검사한다.</p>
     *
     * @return 주문 가능한 재고가 있으면 {@code true}
     */
    public boolean isAvailable() {
        return stockQuantity == null || stockQuantity > 0;
    }
}
