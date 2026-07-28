package com.cakeshop.domain.product.mapper;

import java.util.List;

import com.cakeshop.domain.product.admin.dto.form.ProductAdminSearchCondition;
import com.cakeshop.domain.product.admin.dto.view.ProductAdminListView;
import com.cakeshop.domain.product.admin.dto.view.ProductCategoryOptionView;
import com.cakeshop.domain.product.customer.dto.form.ProductSearchCondition;
import com.cakeshop.domain.product.customer.dto.view.ProductDetailView;
import com.cakeshop.domain.product.customer.dto.view.ProductListView;
import com.cakeshop.domain.product.customer.dto.view.ProductOptionRow;

import com.cakeshop.domain.product.entity.Product;
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
     * 판매 중인 상품의 활성 옵션을 그룹 순서와 옵션 순서로 조회한다.
     *
     * @param productId 조회할 상품 식별자
     * @return 옵션 그룹 정보가 포함된 평탄화된 옵션 행 목록
     */
    List<ProductOptionRow> findPublicOptionRowsByProductId(
            @Param("productId") long productId
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
}
