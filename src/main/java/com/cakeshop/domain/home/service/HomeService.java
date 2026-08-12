package com.cakeshop.domain.home.service;

import java.util.List;

import com.cakeshop.domain.community.dto.view.NoticeSectionView;
import com.cakeshop.domain.community.dto.view.PopularSectionView;
import com.cakeshop.domain.community.service.CommunityHomeQueryService;
import com.cakeshop.domain.product.dto.form.ProductSearchCondition;
import com.cakeshop.domain.product.dto.form.ProductSort;
import com.cakeshop.domain.product.dto.view.ProductListView;
import com.cakeshop.domain.product.service.ProductService;
import com.cakeshop.domain.store.dto.view.StorePublicView;
import com.cakeshop.domain.store.service.StoreService;
import com.cakeshop.global.common.paging.PageRequest;

import org.springframework.stereotype.Service;

@Service
public class HomeService {

    private final StoreService storeService;
    private final ProductService productService;
    private final CommunityHomeQueryService communityHomeQueryService;

    public HomeService(
            StoreService storeService,
            ProductService productService,
            CommunityHomeQueryService communityHomeQueryService
    ) {
        this.storeService = storeService;
        this.productService = productService;
        this.communityHomeQueryService = communityHomeQueryService;
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

    /*
     * 홈 화면에 노출할 인기글을 확정일과 함께 조회한다.
     *
     * <p>건수는 커뮤니티가 정한다. 여기서 넘기지 않는 이유는 같은 값이 화면마다 흩어지지
     * 않게 하기 위해서다(specs/community-popular.md B5).
     *
     * @return 확정된 인기글 영역. 배치 결과가 없으면 비어 있다
     */
    public PopularSectionView getPopularSection() {
        return communityHomeQueryService.getPopularSection();
    }

    /*
     * 홈 화면의 서비스 안내와 카테고리 사이에 노출할 공지를 조회한다.
     *
     * <p>인기글과 같은 계약을 쓴다. 건수도 커뮤니티가 정한다
     * (specs/community-notice.md B8·D3).
     *
     * @return 노출 중인 공지 영역. 노출 중인 공지가 없으면 비어 있다
     */
    public NoticeSectionView getNoticeSection() {
        return communityHomeQueryService.getNoticeSection();
    }
}
