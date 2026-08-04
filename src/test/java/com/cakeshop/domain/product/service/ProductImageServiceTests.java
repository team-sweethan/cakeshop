package com.cakeshop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
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
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class ProductImageServiceTests {

    private static final String STORED_IMAGE_URL =
            "/uploads/product/202608/image.jpg";

    private static final String REPLACEMENT_IMAGE_URL =
            "/uploads/product/202608/replacement.png";

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
    void uploadImage_sortOrderLookupFails_deletesStoredFile() {
        ProductImageUploadForm form = uploadForm();
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(new Product());
        when(productMapper.countProductImagesByProductId(1L))
                .thenReturn(0);
        when(fileStorageClient.store(
                form.getImageFile(),
                "product"
        )).thenReturn(STORED_IMAGE_URL);
        when(productMapper.findNextProductImageSortOrder(1L))
                .thenThrow(new IllegalStateException());

        assertBusinessError(
                () -> productImageService.uploadImage(1L, form),
                ProductErrorCode.IMAGE_UPLOAD_FAILED
        );

        verify(fileStorageClient).delete(STORED_IMAGE_URL);
        verify(productMapper, never()).insertProductImage(any());
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

    @Test
    void uploadImages_moreThanFiveFiles_rejectsBeforeValidation() {
        MockMultipartFile imageFile = (MockMultipartFile) uploadForm()
                .getImageFile();
        List<MultipartFile> imageFiles = List.of(
                imageFile,
                imageFile,
                imageFile,
                imageFile,
                imageFile,
                imageFile
        );

        assertBusinessError(
                () -> productImageService.uploadImages(
                        1L,
                        imageFiles
                ),
                ProductErrorCode.IMAGE_LIMIT_EXCEEDED
        );

        verify(productImageValidator, never()).validate(any());
        verify(fileStorageClient, never()).store(any(), any());
    }

    @Test
    void uploadImages_invalidFile_rejectsBeforeAnyFileStorage() {
        MockMultipartFile first = (MockMultipartFile) uploadForm()
                .getImageFile();
        MockMultipartFile second = (MockMultipartFile) uploadForm()
                .getImageFile();
        doNothing().when(productImageValidator).validate(first);
        doThrow(new BusinessException(
                ProductErrorCode.INVALID_IMAGE_FILE
        )).when(productImageValidator).validate(second);

        assertBusinessError(
                () -> productImageService.uploadImages(
                        1L,
                        List.of(first, second)
                ),
                ProductErrorCode.INVALID_IMAGE_FILE
        );

        verify(fileStorageClient, never()).store(any(), any());
        verify(productMapper, never())
                .insertProductImage(any());
    }

    @Test
    void replaceImage_validFile_updatesUrlAndDeletesOldFileAfterCommit() {
        ProductImageUploadForm form = uploadForm();
        stubReadyToReplace(form);
        when(productMapper.updateProductImageUrl(
                1L,
                10L,
                REPLACEMENT_IMAGE_URL
        )).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();

        productImageService.replaceImage(1L, 10L, form);

        verify(productImageValidator).validate(
                form.getImageFile()
        );
        verify(productMapper).updateProductImageUrl(
                1L,
                10L,
                REPLACEMENT_IMAGE_URL
        );
        verify(productMapper, never())
                .countProductImagesByProductId(anyLong());
        verify(fileStorageClient, never()).delete(any());

        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager
                        .getSynchronizations();
        assertThat(synchronizations).hasSize(2);

        synchronizations.forEach(
                TransactionSynchronization::afterCommit
        );
        synchronizations.forEach(synchronization ->
                synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_COMMITTED
                )
        );

        verify(fileStorageClient).delete(STORED_IMAGE_URL);
        verify(fileStorageClient, never()).delete(
                REPLACEMENT_IMAGE_URL
        );
    }

    @Test
    void replaceImage_missingProduct_rejectsBeforeFileStorage() {
        ProductImageUploadForm form = uploadForm();
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(null);

        assertBusinessError(
                () -> productImageService.replaceImage(
                        1L,
                        10L,
                        form
                ),
                ProductErrorCode.NOT_FOUND
        );

        verify(productMapper, never()).findProductImageById(
                anyLong(),
                anyLong()
        );
        verify(fileStorageClient, never()).store(any(), any());
    }

    @Test
    void replaceImage_missingOrDifferentProductImage_rejectsStorage() {
        ProductImageUploadForm form = uploadForm();
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(new Product());
        when(productMapper.findProductImageById(1L, 10L))
                .thenReturn(null);

        assertBusinessError(
                () -> productImageService.replaceImage(
                        1L,
                        10L,
                        form
                ),
                ProductErrorCode.IMAGE_NOT_FOUND
        );

        verify(fileStorageClient, never()).store(any(), any());
        verify(productMapper, never()).updateProductImageUrl(
                anyLong(),
                anyLong(),
                any()
        );
    }

    @Test
    void replaceImage_fileStorageFails_returnsReplaceError() {
        ProductImageUploadForm form = uploadForm();
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(new Product());
        when(productMapper.findProductImageById(1L, 10L))
                .thenReturn(storedProductImage());
        when(fileStorageClient.store(
                form.getImageFile(),
                "product"
        )).thenThrow(new IllegalStateException());

        assertBusinessError(
                () -> productImageService.replaceImage(
                        1L,
                        10L,
                        form
                ),
                ProductErrorCode.IMAGE_REPLACE_FAILED
        );

        verify(productMapper, never()).updateProductImageUrl(
                anyLong(),
                anyLong(),
                any()
        );
    }

    @Test
    void replaceImage_databaseUpdateFails_deletesNewFileOnRollback() {
        ProductImageUploadForm form = uploadForm();
        stubReadyToReplace(form);
        when(productMapper.updateProductImageUrl(
                1L,
                10L,
                REPLACEMENT_IMAGE_URL
        )).thenThrow(new IllegalStateException());
        TransactionSynchronizationManager.initSynchronization();

        assertBusinessError(
                () -> productImageService.replaceImage(
                        1L,
                        10L,
                        form
                ),
                ProductErrorCode.IMAGE_REPLACE_FAILED
        );

        TransactionSynchronizationManager
                .getSynchronizations()
                .getFirst()
                .afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK
                );

        verify(fileStorageClient).delete(REPLACEMENT_IMAGE_URL);
        verify(fileStorageClient, never()).delete(STORED_IMAGE_URL);
    }

    @Test
    void replaceImage_databaseUpdatesNoRows_deletesNewFileOnRollback() {
        ProductImageUploadForm form = uploadForm();
        stubReadyToReplace(form);
        when(productMapper.updateProductImageUrl(
                1L,
                10L,
                REPLACEMENT_IMAGE_URL
        )).thenReturn(0);
        TransactionSynchronizationManager.initSynchronization();

        assertBusinessError(
                () -> productImageService.replaceImage(
                        1L,
                        10L,
                        form
                ),
                ProductErrorCode.IMAGE_REPLACE_FAILED
        );

        TransactionSynchronizationManager
                .getSynchronizations()
                .getFirst()
                .afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK
                );

        verify(fileStorageClient).delete(REPLACEMENT_IMAGE_URL);
        verify(fileStorageClient, never()).delete(STORED_IMAGE_URL);
    }

    @Test
    void replaceImage_transactionRollsBack_deletesNewFileOnly() {
        ProductImageUploadForm form = uploadForm();
        stubReadyToReplace(form);
        when(productMapper.updateProductImageUrl(
                1L,
                10L,
                REPLACEMENT_IMAGE_URL
        )).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();

        productImageService.replaceImage(1L, 10L, form);

        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager
                        .getSynchronizations();
        assertThat(synchronizations).hasSize(2);

        synchronizations.forEach(synchronization ->
                synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK
                )
        );

        verify(fileStorageClient).delete(REPLACEMENT_IMAGE_URL);
        verify(fileStorageClient, never()).delete(STORED_IMAGE_URL);
    }

    @Test
    void deleteImage_validImage_deletesFileAfterCommit() {
        ProductImage image = storedProductImage();
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(new Product());
        when(productMapper.findProductImageById(1L, 10L))
                .thenReturn(image);
        when(productMapper.deleteProductImage(1L, 10L))
                .thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();

        productImageService.deleteImage(1L, 10L);

        verify(productMapper).deleteProductImage(1L, 10L);
        verify(fileStorageClient, never()).delete(any());

        TransactionSynchronizationManager
                .getSynchronizations()
                .getFirst()
                .afterCommit();

        verify(fileStorageClient).delete(STORED_IMAGE_URL);
    }

    @Test
    void deleteImage_missingProduct_rejectsBeforeImageLookup() {
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(null);

        assertBusinessError(
                () -> productImageService.deleteImage(1L, 10L),
                ProductErrorCode.NOT_FOUND
        );

        verify(productMapper, never()).findProductImageById(
                anyLong(),
                anyLong()
        );
        verify(productMapper, never()).deleteProductImage(
                anyLong(),
                anyLong()
        );
        verify(fileStorageClient, never()).delete(any());
    }

    @Test
    void deleteImage_missingOrDifferentProductImage_rejectsDeletion() {
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(new Product());
        when(productMapper.findProductImageById(1L, 10L))
                .thenReturn(null);

        assertBusinessError(
                () -> productImageService.deleteImage(1L, 10L),
                ProductErrorCode.IMAGE_NOT_FOUND
        );

        verify(productMapper, never()).deleteProductImage(
                anyLong(),
                anyLong()
        );
        verify(fileStorageClient, never()).delete(any());
    }

    @Test
    void deleteImage_databaseDeleteFails_keepsStoredFile() {
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(new Product());
        when(productMapper.findProductImageById(1L, 10L))
                .thenReturn(storedProductImage());
        when(productMapper.deleteProductImage(1L, 10L))
                .thenThrow(new IllegalStateException());

        assertBusinessError(
                () -> productImageService.deleteImage(1L, 10L),
                ProductErrorCode.IMAGE_DELETE_FAILED
        );

        verify(fileStorageClient, never()).delete(any());
    }

    @Test
    void deleteImage_databaseDeletesNoRows_returnsDeleteError() {
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(new Product());
        when(productMapper.findProductImageById(1L, 10L))
                .thenReturn(storedProductImage());
        when(productMapper.deleteProductImage(1L, 10L))
                .thenReturn(0);

        assertBusinessError(
                () -> productImageService.deleteImage(1L, 10L),
                ProductErrorCode.IMAGE_DELETE_FAILED
        );

        verify(fileStorageClient, never()).delete(any());
    }

    @Test
    void deleteImage_transactionRollsBack_keepsStoredFile() {
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(new Product());
        when(productMapper.findProductImageById(1L, 10L))
                .thenReturn(storedProductImage());
        when(productMapper.deleteProductImage(1L, 10L))
                .thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();

        productImageService.deleteImage(1L, 10L);

        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager
                        .getSynchronizations();
        assertThat(synchronizations).hasSize(1);

        synchronizations.getFirst().afterCompletion(
                TransactionSynchronization.STATUS_ROLLED_BACK
        );

        verify(fileStorageClient, never()).delete(any());
    }

    @Test
    void deleteImage_fileDeletionFails_keepsCommittedDeletion() {
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(new Product());
        when(productMapper.findProductImageById(1L, 10L))
                .thenReturn(storedProductImage());
        when(productMapper.deleteProductImage(1L, 10L))
                .thenReturn(1);
        doThrow(new IllegalStateException())
                .when(fileStorageClient)
                .delete(STORED_IMAGE_URL);
        TransactionSynchronizationManager.initSynchronization();

        productImageService.deleteImage(1L, 10L);

        TransactionSynchronizationManager
                .getSynchronizations()
                .getFirst()
                .afterCommit();

        verify(productMapper).deleteProductImage(1L, 10L);
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

    private void stubReadyToReplace(ProductImageUploadForm form) {
        when(productMapper.findSalesInfoByIdForUpdate(1L))
                .thenReturn(new Product());
        when(productMapper.findProductImageById(1L, 10L))
                .thenReturn(storedProductImage());
        when(fileStorageClient.store(
                form.getImageFile(),
                "product"
        )).thenReturn(REPLACEMENT_IMAGE_URL);
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

    private ProductImage storedProductImage() {
        ProductImage image = new ProductImage();
        image.setId(10L);
        image.setProductId(1L);
        image.setImageUrl(STORED_IMAGE_URL);
        image.setSortOrder(0);
        return image;
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
