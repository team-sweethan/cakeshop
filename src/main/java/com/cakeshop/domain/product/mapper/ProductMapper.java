package com.cakeshop.domain.product.mapper;

import java.util.List;

import com.cakeshop.domain.product.admin.dto.form.ProductAdminSearchCondition;
import com.cakeshop.domain.product.admin.dto.form.ProductForm;
import com.cakeshop.domain.product.admin.dto.view.ProductAdminListView;
import com.cakeshop.domain.product.admin.dto.view.ProductCategoryOptionView;
import com.cakeshop.domain.product.admin.dto.view.ProductOptionAdminRow;
import com.cakeshop.domain.product.customer.dto.form.ProductSearchCondition;
import com.cakeshop.domain.product.customer.dto.view.ProductDetailView;
import com.cakeshop.domain.product.customer.dto.view.ProductListView;
import com.cakeshop.domain.product.customer.dto.view.ProductOptionRow;

import com.cakeshop.domain.product.entity.Product;
import com.cakeshop.domain.product.entity.ProductOption;
import com.cakeshop.domain.product.entity.ProductOptionGroup;
import com.cakeshop.domain.product.entity.ProductStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductMapper {

    /**
     * 고객 상품 목록에 표시할 판매 중인 상품을 조회한다.
     *
     * @param condition 상품 검색·필터·정렬 조건
     * @param size 한 페이지에 표시할 상품 수
     * @param offset 조회를 시작할 행 위치
     * @return 조건에 맞는 고객용 상품 목록
     */
    List<ProductListView> findPublicProducts(
            @Param("condition") ProductSearchCondition condition,
            @Param("size") int size,
            @Param("offset") int offset
    );

    /**
     * 고객 상품 검색 조건에 맞는 전체 상품 개수를 조회한다.
     *
     * @param condition 상품 검색·필터 조건
     * @return 조건에 맞는 전체 상품 개수
     */
    long countPublicProducts(
            @Param("condition") ProductSearchCondition condition
    );

    /**
     * 판매 중인 상품의 고객용 상세 정보를 조회한다.
     *
     * @param productId 조회할 상품 식별자
     * @return 판매 중인 상품 정보, 조건에 맞는 상품이 없으면 {@code null}
     */
    ProductDetailView findPublicDetailById(
            @Param("productId") long productId
    );

    /**
     * 다른 도메인에 제공할 상품 판매 정보를 조회한다.
     *
     * <p>상품 존재 여부와 판매 상태를 Service에서 구분할 수 있도록
     * 상품명, 상품 유형, 준비 기간, 기본 가격, 재고 수량,
     * 판매 상태를 함께 조회한다.</p>
     *
     * @param productId 조회할 상품 식별자
     * @return 상품 판매 정보, 존재하지 않으면 {@code null}
     */
    Product findSalesInfoById(
            @Param("productId")
            long productId
    );

    /**
     * 판매 중인 일반 상품의 유한 재고를 요청 수량만큼 차감한다.
     *
     * <p>현재 재고가 요청 수량 이상인 경우에만 갱신하므로
     * 동시에 여러 주문이 요청돼도 재고가 음수가 되지 않는다.</p>
     *
     * @param productId 재고를 차감할 상품 식별자
     * @param quantity 차감할 수량
     * @return 재고가 차감된 상품 행 개수
     */
    int decreaseStockIfAvailable(
            @Param("productId")
            long productId,

            @Param("quantity")
            int quantity
    );

    /**
     * 일반 상품의 유한 재고를 요청 수량만큼 복구한다.
     *
     * <p>복구의 중복 실행 방지는 호출하는 주문·결제 도메인이
     * 주문 상태 전이와 함께 보장해야 한다.</p>
     *
     * @param productId 재고를 복구할 상품 식별자
     * @param quantity 복구할 수량
     * @return 재고가 복구된 상품 행 개수
     */
    int restoreLimitedStock(
            @Param("productId")
            long productId,

            @Param("quantity")
            int quantity
    );

    /**
     * 판매 중인 상품의 활성 옵션을 그룹 순서와 옵션 순서로 조회한다.
     *
     * @param productId 조회할 상품 식별자
     * @return 옵션 그룹 정보가 포함된 평탄화된 옵션 행 목록
     */
    List<ProductOptionRow> findPublicOptionRowsByProductId(
            @Param("productId") long productId
    );

    /**
     * 관리자 옵션 관리 화면에 표시할 그룹과 옵션을 조회한다.
     *
     * @param productId 조회할 상품 식별자
     * @return 비활성 항목을 포함한 옵션 조회 행
     */
    List<ProductOptionAdminRow> findAdminOptionRowsByProductId(
            @Param("productId")
            long productId
    );

    /**
     * 옵션 그룹이 지정한 상품에 속하는지 확인한다.
     */
    boolean existsOptionGroupById(
            @Param("productId")
            long productId,

            @Param("optionGroupId")
            long optionGroupId
    );

    /**
     * 개별 옵션이 지정한 상품과 옵션 그룹에 속하는지 확인한다.
     */
    boolean existsProductOptionById(
            @Param("productId")
            long productId,

            @Param("optionGroupId")
            long optionGroupId,

            @Param("optionId")
            long optionId
    );

    /** 새로운 옵션 그룹을 등록한다. */
    int insertOptionGroup(ProductOptionGroup optionGroup);

    /** 지정한 상품의 옵션 그룹을 수정한다. */
    int updateOptionGroup(
            @Param("productId")
            long productId,

            @Param("optionGroup")
            ProductOptionGroup optionGroup
    );

    /** 지정한 상품에 속한 옵션 그룹의 표시 순서를 변경한다. */
    int updateOptionGroupSortOrder(
            @Param("productId")
            long productId,

            @Param("optionGroupId")
            long optionGroupId,

            @Param("sortOrder")
            int sortOrder
    );

    /** 옵션 그룹에 새로운 개별 옵션을 등록한다. */
    int insertProductOption(ProductOption productOption);

    /** 지정한 상품과 그룹의 개별 옵션을 수정한다. */
    int updateProductOption(
            @Param("productId")
            long productId,

            @Param("optionGroupId")
            long optionGroupId,

            @Param("option")
            ProductOption option
    );

    /** 지정한 상품과 그룹에 속한 개별 옵션의 표시 순서를 변경한다. */
    int updateProductOptionSortOrder(
            @Param("productId")
            long productId,

            @Param("optionGroupId")
            long optionGroupId,

            @Param("optionId")
            long optionId,

            @Param("sortOrder")
            int sortOrder
    );

    /**
     * 검색 조건에 맞는 관리자 상품 목록을 조회한다.
     *
     * @param condition 상품 검색 및 필터 조건
     * @param size 한 페이지에 표시할 상품 수
     * @param offset 조회를 시작할 행 위치
     * @return 검색 조건에 맞는 관리자용 상품 목록
     */
    List<ProductAdminListView> findAdminProducts(
            @Param("condition")
            ProductAdminSearchCondition condition,

            @Param("size")
            int size,

            @Param("offset")
            int offset
    );

    /**
     * 관리자 상품 검색 조건에 맞는 전체 상품 개수를 조회한다.
     *
     * @param condition 상품 검색 및 필터 조건
     * @return 검색 조건에 맞는 전체 상품 개수
     */
    long countAdminProducts(
            @Param("condition")
            ProductAdminSearchCondition condition
    );

    /**
     * 상품의 판매 상태를 변경한다.
     *
     * @param productId 상태를 변경할 상품 식별자
     * @param status 변경할 판매 상태
     * @return 상태가 변경된 상품 행 개수
     */
    int updateProductStatus(
            @Param("productId")
            long productId,

            @Param("status")
            ProductStatus status
    );

    /**
     * 상품 등록 화면에서 선택할 활성 카테고리를 조회한다.
     *
     * @return 정렬 순서에 따른 활성 카테고리 목록
     */
    List<ProductCategoryOptionView> findActiveCategories();

    /**
     * 카테고리가 존재하고 활성 상태인지 확인한다.
     *
     * @param categoryId 확인할 카테고리 식별자
     * @return 활성 카테고리가 존재하면 {@code true}
     */
    boolean existsActiveCategoryById(
            @Param("categoryId")
            long categoryId
    );

    /**
     * 새로운 상품의 기본 정보를 등록한다.
     *
     * <p>등록 후 생성된 상품 ID는
     * {@code product.id}에 저장된다.</p>
     *
     * @param product 등록할 상품 정보
     * @return 등록된 상품 행 개수
     */
    int insertProduct(Product product);

    /**
     * 관리자 상품 수정 화면에 표시할 기존 상품 정보를 조회한다.
     *
     * @param productId 조회할 상품 식별자
     * @return 상품 수정 폼, 존재하지 않으면 {@code null}
     */
    ProductForm findAdminProductFormById(
            @Param("productId")
            long productId
    );

    /**
     * 상품의 기본 정보를 수정한다.
     *
     * <p>판매 상태, 평점, 리뷰 수와 상품 옵션은 변경하지 않는다.</p>
     *
     * @param product 수정할 상품 정보
     * @return 수정된 상품 행 개수
     */
    int updateProduct(Product product);
}
