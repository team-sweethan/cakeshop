package com.cakeshop.domain.home.service;

import java.util.List;

import com.cakeshop.domain.product.customer.dto.form.ProductSearchCondition;
import com.cakeshop.domain.product.customer.dto.form.ProductSort;
import com.cakeshop.domain.product.customer.dto.view.ProductListView;
import com.cakeshop.domain.product.customer.service.ProductService;
import com.cakeshop.domain.store.dto.view.StorePublicView;
import com.cakeshop.domain.store.service.StoreService;
import com.cakeshop.global.common.paging.PageRequest;

import org.springframework.stereotype.Service;

@Service
public class HomeService {

    private final StoreService storeService;
    private final ProductService productService;

    public HomeService(
            StoreService storeService,
            ProductService productService
    ) {
        this.storeService = storeService;
        this.productService = productService;
    }

    // Home은 전용 Mapper를 만들지 않고 각 도메인의 공개 조회 결과만 단방향으로 조합한다.
    public StorePublicView getStore() {
        return storeService.getPublicStore();
    }

    /**
     * 홈 화면에 노출할 인기 상품을 최대 4개 조회한다.
     *
     * @return 판매 중인 인기 상품 목록
     */
    public List<ProductListView> getRecommendedProducts() {
        ProductSearchCondition condition =
                new ProductSearchCondition();

        condition.setSort(ProductSort.POPULAR);

        return productService.getPublicProducts(
                condition,
                new PageRequest(1, 4)
        ).getContent();
    }
}
