package com.cakeshop.domain.review.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.cakeshop.domain.review.dto.query.ReviewImageRow;
import com.cakeshop.domain.review.dto.view.ReviewImageView;
import com.cakeshop.domain.review.entity.ReviewImage;
import com.cakeshop.domain.review.error.ReviewErrorCode;
import com.cakeshop.domain.review.mapper.ReviewImageMapper;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.infra.FileStorageClient;
import com.cakeshop.global.infra.FileStorageDirectory;

@Service
@RequiredArgsConstructor
public class ReviewImageService {

    static final int MAX_IMAGES_PER_REVIEW = 3;

    private static final String IMAGE_DIRECTORY = FileStorageDirectory.REVIEW.getPath();
    private static final Logger log = LoggerFactory.getLogger(ReviewImageService.class);

    private final ReviewImageMapper reviewImageMapper;
    private final ReviewImageValidator reviewImageValidator;
    private final FileStorageClient fileStorageClient;

    List<String> store(List<MultipartFile> files) {
        List<MultipartFile> uploads = selectUploads(files);

        if (uploads.isEmpty()) {
            return List.of();
        }

        if (uploads.size() > MAX_IMAGES_PER_REVIEW) {
            throw new BusinessException(ReviewErrorCode.IMAGE_LIMIT_EXCEEDED);
        }

        // 한 장이라도 어긋나면 파일이나 DB 행을 하나도 만들지 않는다.
        uploads.forEach(reviewImageValidator::validate);

        List<String> imageUrls = new ArrayList<>(uploads.size());
        for (MultipartFile upload : uploads) {
            String imageUrl = storeFile(upload);

            // 뒤의 이미지 저장, 후기 집계 또는 다른 쓰기가 실패하면 참조 없는 파일을 지운다.
            registerRollbackCleanup(imageUrl);
            imageUrls.add(imageUrl);
        }

        return List.copyOf(imageUrls);
    }

    void attach(long reviewId, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }

        for (int sortOrder = 0; sortOrder < imageUrls.size(); sortOrder++) {
            try {
                ReviewImage image = ReviewImage.create(
                        reviewId, imageUrls.get(sortOrder), sortOrder);
                if (reviewImageMapper.insert(image) != 1 || image.getId() == null) {
                    throw new BusinessException(ReviewErrorCode.IMAGE_UPLOAD_FAILED);
                }
            } catch (RuntimeException exception) {
                if (exception instanceof BusinessException) {
                    throw exception;
                }
                throw new BusinessException(ReviewErrorCode.IMAGE_UPLOAD_FAILED);
            }
        }
    }

    Map<Long, List<ReviewImageView>> getImagesByReviewIds(List<Long> reviewIds) {
        List<Long> targets = reviewIds == null
                ? List.of()
                : reviewIds.stream().filter(Objects::nonNull).distinct().toList();

        if (targets.isEmpty()) {
            return Map.of();
        }

        return reviewImageMapper.findByReviewIds(targets).stream()
                .collect(Collectors.groupingBy(
                        ReviewImageRow::reviewId,
                        LinkedHashMap::new,
                        Collectors.mapping(ReviewImageView::from, Collectors.toList())));
    }

    private List<MultipartFile> selectUploads(List<MultipartFile> files) {
        // 파일을 고르지 않은 multipart 요청에는 빈 part가 하나 들어올 수 있다.
        return files == null
                ? List.of()
                : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
    }

    private String storeFile(MultipartFile file) {
        try {
            return fileStorageClient.store(file, IMAGE_DIRECTORY);
        } catch (RuntimeException exception) {
            throw new BusinessException(ReviewErrorCode.IMAGE_UPLOAD_FAILED);
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

    private void deleteStoredFileQuietly(String imageUrl) {
        try {
            fileStorageClient.delete(imageUrl);
        } catch (RuntimeException exception) {
            log.warn("후기 첨부 이미지 파일을 정리하지 못했습니다.");
        }
    }
}
