package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;

import com.cakeshop.domain.community.dto.view.PostImageView;
import com.cakeshop.domain.community.entity.PostImage;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityPostImageMapper;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.infra.FileStorageClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.web.multipart.MultipartFile;

/** 첨부 저장·삭제의 상한과 소유권 계약을 확인한다. */
class CommunityPostImageServiceTests {

    private static final long POST_ID = 42L;
    private static final long AUTHOR_ID = 7L;

    private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

    private CommunityPostImageMapper communityPostImageMapper;
    private CommunityImageValidator communityImageValidator;
    private FileStorageClient fileStorageClient;
    private CommunityPostImageService communityPostImageService;

    @BeforeEach
    void setUp() {
        communityPostImageMapper = mock(CommunityPostImageMapper.class);
        communityImageValidator = mock(CommunityImageValidator.class);
        fileStorageClient = mock(FileStorageClient.class);

        when(fileStorageClient.store(any(), anyString())).thenReturn("/uploads/community/a.jpg");

        communityPostImageService = new CommunityPostImageService(
                communityPostImageMapper,
                communityImageValidator,
                fileStorageClient
        );
    }

    @Test
    void attach_overLimit_rejectsBeforeStoringAnything() {
        assertThatThrownBy(() -> communityPostImageService.attach(POST_ID, files(6)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.IMAGE_LIMIT_EXCEEDED);

        verify(fileStorageClient, never()).store(any(), anyString());
        verify(communityPostImageMapper, never()).insertImage(any());
    }

    @Test
    void attach_atLimit_storesEveryFileInOrder() {
        when(communityPostImageMapper.findNextSortOrder(POST_ID)).thenReturn(0);

        communityPostImageService.attach(POST_ID, files(5));

        verify(fileStorageClient, times(5)).store(any(), anyString());
        verify(communityPostImageMapper, times(5)).insertImage(any(PostImage.class));
    }

    @Test
    void attach_oneFileInvalid_storesNothing() {
        List<MultipartFile> uploads = files(3);

        doThrow(new BusinessException(CommunityErrorCode.INVALID_IMAGE_FILE))
                .when(communityImageValidator).validate(uploads.get(1));

        assertThatThrownBy(() -> communityPostImageService.attach(POST_ID, uploads))
                .isInstanceOf(BusinessException.class);

        verify(fileStorageClient, never()).store(any(), anyString());
    }

    @Test
    void attach_emptyParts_areNotCounted() {
        List<MultipartFile> uploads = List.of(
                new MockMultipartFile("images", "", "application/octet-stream", new byte[0])
        );

        communityPostImageService.attach(POST_ID, uploads);

        verify(fileStorageClient, never()).store(any(), anyString());
        verify(communityPostImageMapper, never()).insertImage(any());
    }

    @Test
    void applyEdit_deleteThenAdd_countsRemainingOnly() {
        givenDeletable(11L);
        when(communityPostImageMapper.countImagesByPostId(POST_ID)).thenReturn(4);
        when(communityPostImageMapper.findNextSortOrder(POST_ID)).thenReturn(5);

        assertThatCode(() -> communityPostImageService.applyEdit(
                POST_ID, AUTHOR_ID, List.of(11L), files(1)))
                .doesNotThrowAnyException();

        verify(communityPostImageMapper).deleteImages(POST_ID, AUTHOR_ID, List.of(11L));
        verify(communityPostImageMapper).insertImage(any(PostImage.class));
    }

    @Test
    void applyEdit_remainingPlusNewOverLimit_rejects() {
        givenDeletable(11L);
        when(communityPostImageMapper.countImagesByPostId(POST_ID)).thenReturn(4);

        assertThatThrownBy(() -> communityPostImageService.applyEdit(
                POST_ID, AUTHOR_ID, List.of(11L), files(2)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.IMAGE_LIMIT_EXCEEDED);

        verify(fileStorageClient, never()).store(any(), anyString());
    }

    @Test
    void applyEdit_imageNotDeletable_rejectsAndDeletesNothing() {
        when(communityPostImageMapper.findDeletableImages(anyLong(), anyLong(), anyList()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> communityPostImageService.applyEdit(
                POST_ID, AUTHOR_ID, List.of(11L), List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.IMAGE_NOT_FOUND);

        verify(communityPostImageMapper, never()).deleteImages(anyLong(), anyLong(), anyList());
        verify(fileStorageClient, never()).delete(anyString());
    }

    @Test
    void applyEdit_partiallyDeletable_rejects() {
        when(communityPostImageMapper.findDeletableImages(anyLong(), anyLong(), anyList()))
                .thenReturn(List.of(new PostImageView(11L, "/uploads/community/a.jpg", 0)));

        assertThatThrownBy(() -> communityPostImageService.applyEdit(
                POST_ID, AUTHOR_ID, List.of(11L, 12L), List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.IMAGE_NOT_FOUND);

        verify(communityPostImageMapper, never()).deleteImages(anyLong(), anyLong(), anyList());
    }

    @Test
    void applyEdit_deletesStoredFileOnlyAfterCommit() {
        givenDeletable(11L);

        TransactionSynchronizationManager.initSynchronization();

        try {
            communityPostImageService.applyEdit(POST_ID, AUTHOR_ID, List.of(11L), List.of());

            verify(fileStorageClient, never()).delete(anyString());

            TransactionSynchronizationUtils.triggerAfterCommit();

            verify(fileStorageClient).delete("/uploads/community/a.jpg");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void attach_removesStoredFileWhenTransactionRollsBack() {
        when(communityPostImageMapper.findNextSortOrder(POST_ID)).thenReturn(0);

        TransactionSynchronizationManager.initSynchronization();

        try {
            communityPostImageService.attach(POST_ID, files(1));

            verify(fileStorageClient, never()).delete(anyString());

            TransactionSynchronizationUtils.invokeAfterCompletion(
                    TransactionSynchronizationManager.getSynchronizations(),
                    TransactionSynchronization.STATUS_ROLLED_BACK
            );

            verify(fileStorageClient).delete("/uploads/community/a.jpg");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void givenDeletable(long imageId) {
        when(communityPostImageMapper.findDeletableImages(anyLong(), anyLong(), anyList()))
                .thenReturn(List.of(new PostImageView(imageId, "/uploads/community/a.jpg", 0)));
    }

    private List<MultipartFile> files(int count) {
        return IntStream.range(0, count)
                .<MultipartFile>mapToObj(index -> new MockMultipartFile(
                        "images", index + ".jpg", "image/jpeg", JPEG_HEADER))
                .toList();
    }
}
