package com.cakeshop.domain.community.service;

import java.util.List;
import java.util.Objects;

import lombok.RequiredArgsConstructor;

import com.cakeshop.domain.community.dto.view.PostImageView;
import com.cakeshop.domain.community.entity.PostImage;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityPostImageMapper;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.infra.FileStorageClient;
import com.cakeshop.global.infra.FileStorageDirectory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-18
 * 기능 : 커뮤니티 첨부 이미지 처리
 * 설명 : CommunityPostImageService 첨부의 저장·삭제와 파일 정리를 관리한다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
public class CommunityPostImageService {

    static final int MAX_IMAGES_PER_POST = 5;

    private static final String IMAGE_DIRECTORY = FileStorageDirectory.COMMUNITY.getPath();

    private static final Logger log = LoggerFactory.getLogger(CommunityPostImageService.class);

    private final CommunityPostImageMapper communityPostImageMapper;
    private final CommunityImageValidator communityImageValidator;
    private final FileStorageClient fileStorageClient;

    @Transactional(readOnly = true)
    public List<PostImageView> getImages(long postId) {
        return communityPostImageMapper.findImagesByPostId(postId);
    }

    /**
     * 새 글의 첨부를 저장한다. 글을 만드는 트랜잭션 안에서 돌아야 첨부만 남거나
     * 글만 남는 상태가 생기지 않는다.
     */
    void attach(long postId, List<MultipartFile> files) {
        List<MultipartFile> uploads = selectUploads(files);

        if (uploads.isEmpty()) {
            return;
        }

        requireWithinLimit(uploads.size());
        store(postId, uploads);
    }

    /**
     * 수정 화면의 저장 한 번을 처리한다.
     *
     * <p><b>지우기를 먼저 적용하고 남는 자리로 상한을 센다.</b> 순서를 뒤집으면 다섯 장을 채운
     * 글에서 한 장 지우고 한 장 올리는 교체가 상한에 걸린다.
     */
    void applyEdit(
            long postId,
            long editorId,
            List<Long> deleteImageIds,
            List<MultipartFile> files
    ) {
        List<MultipartFile> uploads = selectUploads(files);

        removeImages(postId, editorId, deleteImageIds);

        if (uploads.isEmpty()) {
            return;
        }

        requireWithinLimit(
                communityPostImageMapper.countImagesByPostId(postId) + uploads.size()
        );

        store(postId, uploads);
    }

    private void store(long postId, List<MultipartFile> uploads) {
        // 한 장이라도 어긋나면 아무것도 저장하지 않는다. 도중에 막히면 일부만 붙는다.
        uploads.forEach(communityImageValidator::validate);

        int sortOrder = communityPostImageMapper.findNextSortOrder(postId);

        for (MultipartFile upload : uploads) {
            String imageUrl = fileStorageClient.store(upload, IMAGE_DIRECTORY);

            // 뒤에서 롤백이 나면 이 파일은 아무도 참조하지 않는 쓰레기가 된다.
            registerRollbackCleanup(imageUrl);

            communityPostImageMapper.insertImage(
                    PostImage.create(postId, imageUrl, sortOrder++)
            );
        }
    }

    private void removeImages(long postId, long editorId, List<Long> imageIds) {
        List<Long> targets = imageIds == null
                ? List.of()
                : imageIds.stream().filter(Objects::nonNull).distinct().toList();

        if (targets.isEmpty()) {
            return;
        }

        List<PostImageView> deletable =
                communityPostImageMapper.findDeletableImages(postId, editorId, targets);

        // 남의 글·차단된 글의 첨부는 조회 자체가 비어 온다. 조용히 건너뛰지 않고 거절한다.
        if (deletable.size() != targets.size()) {
            throw new BusinessException(CommunityErrorCode.IMAGE_NOT_FOUND);
        }

        communityPostImageMapper.deleteImages(postId, editorId, targets);

        // 커밋 전에 지우면 뒤에 롤백이 났을 때 행은 살아 있는데 파일만 없어진다.
        deletable.forEach(image -> registerCommitFileDeletion(image.imageUrl()));
    }

    private List<MultipartFile> selectUploads(List<MultipartFile> files) {
        // 파일을 고르지 않아도 빈 part 가 실려 온다. 그것까지 세면 상한이 잘못 걸린다.
        return files == null
                ? List.of()
                : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
    }

    private void requireWithinLimit(int totalImages) {
        if (totalImages > MAX_IMAGES_PER_POST) {
            throw new BusinessException(CommunityErrorCode.IMAGE_LIMIT_EXCEEDED);
        }
    }

    private void registerRollbackCleanup(String imageUrl) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
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

    private void registerCommitFileDeletion(String imageUrl) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
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
            log.warn("커뮤니티 첨부 이미지 파일을 정리하지 못했습니다.");
        }
    }
}
