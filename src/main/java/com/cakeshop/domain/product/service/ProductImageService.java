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

/** 관리자 상품 이미지 등록·교체·삭제 업무를 처리한다. */
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

        try {
            productImage.setSortOrder(
                    productMapper.findNextProductImageSortOrder(
                            productId
                    )
            );

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

    /**
     * 지정한 상품 이미지의 표시 순서는 유지하면서 저장 파일을 교체한다.
     *
     * <p>DB 트랜잭션이 롤백되면 새 파일을 삭제하고,
     * 커밋된 뒤에는 기존 파일을 삭제한다.</p>
     *
     * @param productId 이미지를 소유한 상품 식별자
     * @param imageId 교체할 상품 이미지 식별자
     * @param form 이미지 교체 입력값
     */
    @Transactional
    public void replaceImage(
            long productId,
            long imageId,
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

        ProductImage productImage =
                productMapper.findProductImageById(
                        productId,
                        imageId
                );

        if (productImage == null) {
            throw new BusinessException(
                    ProductErrorCode.IMAGE_NOT_FOUND
            );
        }

        String newImageUrl = storeReplacementImage(imageFile);
        boolean rollbackCleanupRegistered = false;

        try {
            rollbackCleanupRegistered =
                    registerRollbackCleanup(newImageUrl);

            int updatedRows = productMapper.updateProductImageUrl(
                    productId,
                    imageId,
                    newImageUrl
            );

            if (updatedRows != 1) {
                throw new BusinessException(
                        ProductErrorCode.IMAGE_REPLACE_FAILED
                );
            }

            registerCommitFileDeletion(
                    productImage.getImageUrl()
            );
        } catch (RuntimeException exception) {
            if (!rollbackCleanupRegistered) {
                deleteStoredFileQuietly(newImageUrl);
            }

            if (exception instanceof BusinessException) {
                throw exception;
            }

            throw new BusinessException(
                    ProductErrorCode.IMAGE_REPLACE_FAILED
            );
        }
    }

    /**
     * 지정한 상품의 이미지 정보를 삭제하고 커밋 후 저장 파일을 정리한다.
     *
     * <p>같은 상품의 이미지 등록·삭제를 직렬화하고,
     * DB 트랜잭션이 롤백되면 저장 파일은 유지한다.</p>
     *
     * @param productId 이미지를 소유한 상품 식별자
     * @param imageId 삭제할 상품 이미지 식별자
     */
    @Transactional
    public void deleteImage(
            long productId,
            long imageId
    ) {
        Product product = productMapper.findSalesInfoByIdForUpdate(
                productId
        );

        if (product == null) {
            throw new BusinessException(
                    ProductErrorCode.NOT_FOUND
            );
        }

        ProductImage productImage =
                productMapper.findProductImageById(
                        productId,
                        imageId
                );

        if (productImage == null) {
            throw new BusinessException(
                    ProductErrorCode.IMAGE_NOT_FOUND
            );
        }

        try {
            int deletedRows = productMapper.deleteProductImage(
                    productId,
                    imageId
            );

            if (deletedRows != 1) {
                throw new BusinessException(
                        ProductErrorCode.IMAGE_DELETE_FAILED
                );
            }
        } catch (RuntimeException exception) {
            if (exception instanceof BusinessException) {
                throw exception;
            }

            throw new BusinessException(
                    ProductErrorCode.IMAGE_DELETE_FAILED
            );
        }

        registerCommitFileDeletion(productImage.getImageUrl());
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

    private String storeReplacementImage(MultipartFile imageFile) {
        try {
            return fileStorageClient.store(
                    imageFile,
                    "product"
            );
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    ProductErrorCode.IMAGE_REPLACE_FAILED
            );
        }
    }

    private boolean registerRollbackCleanup(String imageUrl) {
        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {
            return false;
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

        return true;
    }

    private void registerCommitFileDeletion(String imageUrl) {
        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        deleteStoredFileQuietly(imageUrl);
                    }
                }
        );
    }

    private void deleteStoredFileQuietly(String imageUrl) {
        try {
            fileStorageClient.delete(imageUrl);
        } catch (RuntimeException exception) {
            log.warn(
                    "상품 이미지 저장 파일을 정리하지 못했습니다."
            );
        }
    }
}
