package com.cakeshop.domain.product.service;

import com.cakeshop.domain.product.entity.Product;
import com.cakeshop.domain.product.entity.ProductStatus;
import com.cakeshop.domain.product.entity.ProductType;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.mapper.ProductMapper;
import com.cakeshop.global.error.BusinessException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문·결제 도메인에 원자적인 상품 재고 변경 연산을 제공한다.
 *
 * <p>호출자는 주문 상태 전이와 함께 이 Service를 호출해
 * 동일한 주문의 재고가 중복 차감·복구되지 않도록 해야 한다.</p>
 */
@Service
public class ProductStockService {

    private final ProductMapper productMapper;

    public ProductStockService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    /**
     * 결제에 성공한 일반 상품의 재고를 차감한다.
     *
     * <p>주문 제작 상품과 무제한 재고 상품은 차감하지 않는다.</p>
     *
     * @param productId 재고를 차감할 상품 식별자
     * @param quantity 차감할 수량
     * @throws BusinessException 상품이 없거나 판매 중이 아니거나
     *         재고가 부족한 경우
     */
    @Transactional
    public void decreaseStock(long productId, int quantity) {
        validateQuantity(quantity);

        Product product = getProduct(productId);

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new BusinessException(
                    ProductErrorCode.NOT_ON_SALE
            );
        }

        if (isNotStockManaged(product)) {
            return;
        }

        int updatedRows =
                productMapper.decreaseStockIfAvailable(
                        productId,
                        quantity
                );

        if (updatedRows == 0) {
            throw new BusinessException(
                    ProductErrorCode.INSUFFICIENT_STOCK
            );
        }
    }

    /**
     * 취소된 일반 상품 주문에서 앞서 차감한 재고를 복구한다.
     *
     * <p>판매가 중지된 뒤에도 이미 차감한 재고는 복구한다.
     * 주문 제작 상품과 무제한 재고 상품은 복구하지 않는다.</p>
     *
     * @param productId 재고를 복구할 상품 식별자
     * @param quantity 복구할 수량
     * @throws BusinessException 상품이 없거나 수량이 잘못된 경우
     */
    @Transactional
    public void restoreStock(long productId, int quantity) {
        validateQuantity(quantity);

        Product product = getProduct(productId);

        if (isNotStockManaged(product)) {
            return;
        }

        productMapper.restoreLimitedStock(
                productId,
                quantity
        );
    }

    private Product getProduct(long productId) {
        Product product =
                productMapper.findSalesInfoById(productId);

        if (product == null) {
            throw new BusinessException(
                    ProductErrorCode.NOT_FOUND
            );
        }

        return product;
    }

    private boolean isNotStockManaged(Product product) {
        return product.getProductType() == ProductType.CUSTOM
                || product.getStockQuantity() == null;
    }

    private void validateQuantity(int quantity) {
        if (quantity < 1) {
            throw new BusinessException(
                    ProductErrorCode.INVALID_STOCK_QUANTITY
            );
        }
    }
}
