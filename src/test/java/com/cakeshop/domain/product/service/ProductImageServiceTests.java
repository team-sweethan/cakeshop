package com.cakeshop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.cakeshop.domain.product.dto.form.ProductImageUploadForm;
import com.cakeshop.domain.product.entity.Product;
import com.cakeshop.domain.product.entity.ProductImage;
import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.domain.product.mapper.ProductMapper;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.infra.FileStorageClient;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class ProductImageServiceTests {

    private static final String STORED_IMAGE_URL =
            "/uploads/product/202608/image.jpg";

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductImageValidator productImageValidator;

    @Mock
    private FileStorageClient fileStorageClient;

    @InjectMocks
    private ProductImageService productImageService;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager
                .isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void uploadImage_validFile_insertsImageWithNextOrder() {
        ProductImageUploadForm form = uploadForm();
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(new Product());
        when(productMapper.countProductImagesByProductId(1L))
                .thenReturn(2);
        when(productMapper.findNextProductImageSortOrder(1L))
                .thenReturn(2);
        when(fileStorageClient.store(
                form.getImageFile(),
                "product"
        )).thenReturn(STORED_IMAGE_URL);
        when(productMapper.insertProductImage(any()))
                .thenAnswer(invocation -> {
                    ProductImage image = invocation.getArgument(0);
                    image.setId(10L);
                    return 1;
                });

        long imageId = productImageService.uploadImage(1L, form);

        ArgumentCaptor<ProductImage> imageCaptor =
                ArgumentCaptor.forClass(ProductImage.class);
        verify(productImageValidator).validate(
                form.getImageFile()
        );
        verify(productMapper).insertProductImage(
                imageCaptor.capture()
        );
        assertThat(imageId).isEqualTo(10L);
        assertThat(imageCaptor.getValue().getProductId())
                .isEqualTo(1L);
        assertThat(imageCaptor.getValue().getImageUrl())
                .isEqualTo(STORED_IMAGE_URL);
        assertThat(imageCaptor.getValue().getSortOrder())
                .isEqualTo(2);
        verify(fileStorageClient, never()).delete(any());
    }

    @Test
    void uploadImage_missingProduct_rejectsBeforeFileStorage() {
        ProductImageUploadForm form = uploadForm();
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(null);

        assertBusinessError(
                () -> productImageService.uploadImage(1L, form),
                ProductErrorCode.NOT_FOUND
        );

        verify(fileStorageClient, never()).store(any(), any());
        verify(productMapper, never()).insertProductImage(any());
    }

    @Test
    void uploadImage_fiveImagesExist_rejectsImageLimit() {
        ProductImageUploadForm form = uploadForm();
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(new Product());
        when(productMapper.countProductImagesByProductId(1L))
                .thenReturn(ProductImageService.MAX_IMAGES_PER_PRODUCT);

        assertBusinessError(
                () -> productImageService.uploadImage(1L, form),
                ProductErrorCode.IMAGE_LIMIT_EXCEEDED
        );

        verify(fileStorageClient, never()).store(any(), any());
        verify(productMapper, never()).insertProductImage(any());
    }

    @Test
    void uploadImage_fileStorageFails_returnsUploadError() {
        ProductImageUploadForm form = uploadForm();
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(new Product());
        when(productMapper.countProductImagesByProductId(1L))
                .thenReturn(0);
        when(fileStorageClient.store(
                form.getImageFile(),
                "product"
        )).thenThrow(new IllegalStateException());

        assertBusinessError(
                () -> productImageService.uploadImage(1L, form),
                ProductErrorCode.IMAGE_UPLOAD_FAILED
        );

        verify(productMapper, never()).insertProductImage(any());
    }

    @Test
    void uploadImage_databaseInsertFails_deletesStoredFile() {
        ProductImageUploadForm form = uploadForm();
        stubReadyToStore(form);
        when(productMapper.insertProductImage(any()))
                .thenThrow(new IllegalStateException());

        assertBusinessError(
                () -> productImageService.uploadImage(1L, form),
                ProductErrorCode.IMAGE_UPLOAD_FAILED
        );

        verify(fileStorageClient).delete(STORED_IMAGE_URL);
    }

    @Test
    void uploadImage_transactionRollsBack_deletesStoredFile() {
        ProductImageUploadForm form = uploadForm();
        stubReadyToStore(form);
        when(productMapper.insertProductImage(any()))
                .thenAnswer(invocation -> {
                    ProductImage image = invocation.getArgument(0);
                    image.setId(10L);
                    return 1;
                });
        TransactionSynchronizationManager.initSynchronization();

        productImageService.uploadImage(1L, form);

        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager
                        .getSynchronizations();
        assertThat(synchronizations).hasSize(1);

        synchronizations.getFirst().afterCompletion(
                TransactionSynchronization.STATUS_ROLLED_BACK
        );

        verify(fileStorageClient).delete(STORED_IMAGE_URL);
    }

    private void stubReadyToStore(ProductImageUploadForm form) {
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(new Product());
        when(productMapper.countProductImagesByProductId(1L))
                .thenReturn(0);
        when(productMapper.findNextProductImageSortOrder(1L))
                .thenReturn(0);
        when(fileStorageClient.store(
                form.getImageFile(),
                "product"
        )).thenReturn(STORED_IMAGE_URL);
    }

    private ProductImageUploadForm uploadForm() {
        ProductImageUploadForm form =
                new ProductImageUploadForm();
        form.setImageFile(new MockMultipartFile(
                "imageFile",
                "cake.jpg",
                "image/jpeg",
                new byte[] {
                        (byte) 0xFF,
                        (byte) 0xD8,
                        (byte) 0xFF
                }
        ));
        return form;
    }

    private void assertBusinessError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            ProductErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(
                                exception.getErrorCode()
                        ).isEqualTo(expectedErrorCode)
                );
    }
}
