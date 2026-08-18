package com.cakeshop.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.web.multipart.MultipartFile;

import com.cakeshop.domain.review.dto.query.ReviewImageRow;
import com.cakeshop.domain.review.dto.view.ReviewImageView;
import com.cakeshop.domain.review.entity.ReviewImage;
import com.cakeshop.domain.review.error.ReviewErrorCode;
import com.cakeshop.domain.review.mapper.ReviewImageMapper;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.infra.FileStorageClient;

class ReviewImageServiceTests {

    private static final long REVIEW_ID = 42L;
    private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

    private ReviewImageMapper reviewImageMapper;
    private ReviewImageValidator reviewImageValidator;
    private FileStorageClient fileStorageClient;
    private ReviewImageService reviewImageService;

    @BeforeEach
    void setUp() {
        reviewImageMapper = mock(ReviewImageMapper.class);
        reviewImageValidator = mock(ReviewImageValidator.class);
        fileStorageClient = mock(FileStorageClient.class);

        when(fileStorageClient.store(any(), anyString())).thenReturn("/uploads/review/a.jpg");
        when(reviewImageMapper.insert(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, ReviewImage.class).setId(1L);
            return 1;
        });

        reviewImageService = new ReviewImageService(
                reviewImageMapper, reviewImageValidator, fileStorageClient);
    }

    @Test
    void attach_overLimit_rejectsBeforeStoringAnything() {
        assertThatThrownBy(() -> reviewImageService.attach(REVIEW_ID, files(4)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ReviewErrorCode.IMAGE_LIMIT_EXCEEDED);

        verify(fileStorageClient, never()).store(any(), anyString());
        verify(reviewImageMapper, never()).insert(any());
    }

    @Test
    void attach_atLimit_storesEveryFile() {
        reviewImageService.attach(REVIEW_ID, files(3));

        verify(fileStorageClient, times(3)).store(any(), anyString());
        verify(reviewImageMapper, times(3)).insert(any(ReviewImage.class));
    }

    @Test
    void attach_oneFileInvalid_storesNothing() {
        List<MultipartFile> uploads = files(3);
        doThrow(new BusinessException(ReviewErrorCode.INVALID_IMAGE_FILE))
                .when(reviewImageValidator).validate(uploads.get(1));

        assertThatThrownBy(() -> reviewImageService.attach(REVIEW_ID, uploads))
                .isInstanceOf(BusinessException.class);

        verify(fileStorageClient, never()).store(any(), anyString());
    }

    @Test
    void attach_emptyPart_isIgnored() {
        reviewImageService.attach(REVIEW_ID, List.of(
                new MockMultipartFile("images", "", "application/octet-stream", new byte[0])));

        verify(fileStorageClient, never()).store(any(), anyString());
    }

    @Test
    void attach_transactionRollback_removesStoredFile() {
        TransactionSynchronizationManager.initSynchronization();

        try {
            reviewImageService.attach(REVIEW_ID, files(1));

            verify(fileStorageClient, never()).delete(anyString());

            TransactionSynchronizationUtils.invokeAfterCompletion(
                    TransactionSynchronizationManager.getSynchronizations(),
                    TransactionSynchronization.STATUS_ROLLED_BACK);

            verify(fileStorageClient).delete("/uploads/review/a.jpg");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void getImagesByReviewIds_groupsRowsAndKeepsDisplayOrder() {
        when(reviewImageMapper.findByReviewIds(List.of(42L, 43L))).thenReturn(List.of(
                new ReviewImageRow(1L, 42L, "/uploads/review/a.jpg", 0),
                new ReviewImageRow(2L, 42L, "/uploads/review/b.jpg", 1),
                new ReviewImageRow(3L, 43L, "/uploads/review/c.jpg", 0)));

        Map<Long, List<ReviewImageView>> images =
                reviewImageService.getImagesByReviewIds(List.of(42L, 43L, 42L));

        assertThat(images.get(42L)).extracting(ReviewImageView::imageUrl)
                .containsExactly("/uploads/review/a.jpg", "/uploads/review/b.jpg");
        assertThat(images.get(43L)).extracting(ReviewImageView::imageUrl)
                .containsExactly("/uploads/review/c.jpg");
    }

    private List<MultipartFile> files(int count) {
        return IntStream.range(0, count)
                .<MultipartFile>mapToObj(index -> new MockMultipartFile(
                        "images", index + ".jpg", "image/jpeg", JPEG_HEADER))
                .toList();
    }
}
