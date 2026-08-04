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
     * <p>주문 제작 상품과 무제한 재고 상품은 차감하지 않는다.
     * 반환값은 주문·결제 도메인이 차감 이력을 저장하는 데 사용한다.</p>
     *
     * @param productId 재고를 차감할 상품 식별자
     * @param quantity 차감할 수량
     * @return 유한 재고를 실제로 차감했으면 {@code true},
     *         차감 대상이 아니면 {@code false}
     * @throws BusinessException 상품이 없거나 판매 중이 아니거나
     *         재고가 부족한 경우
     */
    @Transactional
    public boolean decreaseStock(long productId, int quantity) {
        validateQuantity(quantity);

        Product product = getProductForUpdate(productId);

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new BusinessException(
                    ProductErrorCode.NOT_ON_SALE
            );
        }

        if (isNotStockManaged(product)) {
            return false;
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

        return true;
    }

    /**
     * 주문·결제 시점에 실제로 차감한 재고를 복구한다.
     *
     * <p>호출자는 저장한 차감 이력을 기준으로 이 메서드를 호출해야 한다.
     * 상품 유형이나 판매 상태가 차감 후 변경됐더라도 복구를 시도한다.
     * 복구 전에 무제한 재고로 전환됐다면 별도로 더할 유한 재고가
     * 없으므로 정상 처리한다.</p>
     *
     * @param productId 재고를 복구할 상품 식별자
     * @param quantity 복구할 수량
     * @throws BusinessException 상품이 없거나 수량이 잘못됐거나
     *         앞서 차감한 재고를 복구할 수 없는 경우
     */
    @Transactional
    public void restoreStock(long productId, int quantity) {
        validateQuantity(quantity);

        Product product = getProductForUpdate(productId);

        if (product.getStockQuantity() == null) {
            return;
        }

        int updatedRows = productMapper.restoreLimitedStock(
                productId,
                quantity
        );

        if (updatedRows == 1) {
            return;
        }

        throw new BusinessException(
                ProductErrorCode.STOCK_RESTORE_FAILED
        );
    }

    private Product getProductForUpdate(long productId) {
        Product product =
                productMapper.findSalesInfoByIdForUpdate(
                        productId
                );

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
