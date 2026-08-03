package com.cakeshop.domain.product.service;

import com.cakeshop.domain.product.dto.form.ProductImageUploadForm;
import com.cakeshop.domain.product.entity.Product;
import com.cakeshop.domain.product.entity.ProductImage;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.mapper.ProductMapper;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.infra.FileStorageClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** 관리자 상품 이미지 업로드 업무를 처리한다. */
@Service
public class ProductImageService {

    static final int MAX_IMAGES_PER_PRODUCT = 5;

    private static final Logger log = LoggerFactory.getLogger(
            ProductImageService.class
    );

    private final ProductMapper productMapper;
    private final ProductImageValidator productImageValidator;
    private final FileStorageClient fileStorageClient;

    public ProductImageService(
            ProductMapper productMapper,
            ProductImageValidator productImageValidator,
            FileStorageClient fileStorageClient
    ) {
        this.productMapper = productMapper;
        this.productImageValidator = productImageValidator;
        this.fileStorageClient = fileStorageClient;
    }

    /**
     * 상품 이미지를 검증해 저장하고 표시 순서와 함께 등록한다.
     *
     * <p>같은 상품의 동시 업로드를 직렬화해 최대 5장 제한과
     * 표시 순서 계산이 서로 충돌하지 않도록 한다.</p>
     *
     * @param productId 이미지를 추가할 상품 식별자
     * @param form 이미지 업로드 입력값
     * @return 등록된 상품 이미지 식별자
     */
    @Transactional
    public long uploadImage(
            long productId,
            ProductImageUploadForm form
    ) {
        MultipartFile imageFile = form == null
                ? null
                : form.getImageFile();

        productImageValidator.validate(imageFile);

        Product product = productMapper.findSalesInfoByIdForUpdate(
                productId
        );

        if (product == null) {
            throw new BusinessException(
                    ProductErrorCode.NOT_FOUND
            );
        }

        int imageCount =
                productMapper.countProductImagesByProductId(
                        productId
                );

        if (imageCount >= MAX_IMAGES_PER_PRODUCT) {
            throw new BusinessException(
                    ProductErrorCode.IMAGE_LIMIT_EXCEEDED
            );
        }

        String imageUrl = storeImage(imageFile);
        ProductImage productImage = new ProductImage();

        productImage.setProductId(productId);
        productImage.setImageUrl(imageUrl);
        productImage.setSortOrder(
                productMapper.findNextProductImageSortOrder(
                        productId
                )
        );

        try {
            int insertedRows =
                    productMapper.insertProductImage(
                            productImage
                    );

            if (insertedRows != 1 || productImage.getId() == null) {
                throw new BusinessException(
                        ProductErrorCode.IMAGE_UPLOAD_FAILED
                );
            }
        } catch (RuntimeException exception) {
            deleteStoredFileQuietly(imageUrl);

            if (exception instanceof BusinessException) {
                throw exception;
            }

            throw new BusinessException(
                    ProductErrorCode.IMAGE_UPLOAD_FAILED
            );
        }

        registerRollbackCleanup(imageUrl);

        return productImage.getId();
    }

    private String storeImage(MultipartFile imageFile) {
        try {
            return fileStorageClient.store(
                    imageFile,
                    "product"
            );
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    ProductErrorCode.IMAGE_UPLOAD_FAILED
            );
        }
    }

    private void registerRollbackCleanup(String imageUrl) {
        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            deleteStoredFileQuietly(imageUrl);
                        }
                    }
                }
        );
    }

    private void deleteStoredFileQuietly(String imageUrl) {
        try {
            fileStorageClient.delete(imageUrl);
        } catch (RuntimeException exception) {
            log.warn(
                    "상품 이미지 업로드 실패 후 저장 파일을 정리하지 못했습니다."
            );
        }
    }
}
