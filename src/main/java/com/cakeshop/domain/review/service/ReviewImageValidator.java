package com.cakeshop.domain.review.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.cakeshop.domain.review.error.ReviewErrorCode;
import com.cakeshop.global.error.BusinessException;

@Component
public class ReviewImageValidator {

    static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A
    };

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ReviewErrorCode.INVALID_IMAGE_FILE);
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ReviewErrorCode.IMAGE_TOO_LARGE);
        }

        ImageType extensionType = extensionType(file.getOriginalFilename());
        ImageType contentType = contentType(file.getContentType());
        ImageType signatureType = signatureType(file);

        if (extensionType == null
                || extensionType != contentType
                || extensionType != signatureType) {
            throw new BusinessException(ReviewErrorCode.INVALID_IMAGE_FILE);
        }
    }

    private ImageType extensionType(String originalFilename) {
        String extension = StringUtils.getFilenameExtension(originalFilename);

        if (!StringUtils.hasText(extension)) {
            return null;
        }

        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> ImageType.JPEG;
            case "png" -> ImageType.PNG;
            default -> null;
        };
    }

    private ImageType contentType(String contentType) {
        if ("image/jpeg".equalsIgnoreCase(contentType)) {
            return ImageType.JPEG;
        }

        if ("image/png".equalsIgnoreCase(contentType)) {
            return ImageType.PNG;
        }

        return null;
    }

    private ImageType signatureType(MultipartFile file) {
        byte[] header;

        try (InputStream inputStream = file.getInputStream()) {
            header = inputStream.readNBytes(PNG_SIGNATURE.length);
        } catch (IOException exception) {
            throw new BusinessException(ReviewErrorCode.INVALID_IMAGE_FILE);
        }

        if (isJpeg(header)) {
            return ImageType.JPEG;
        }

        if (isPng(header)) {
            return ImageType.PNG;
        }

        return null;
    }

    private boolean isJpeg(byte[] header) {
        return header.length >= 3
                && header[0] == (byte) 0xFF
                && header[1] == (byte) 0xD8
                && header[2] == (byte) 0xFF;
    }

    private boolean isPng(byte[] header) {
        if (header.length < PNG_SIGNATURE.length) {
            return false;
        }

        for (int index = 0; index < PNG_SIGNATURE.length; index++) {
            if (header[index] != PNG_SIGNATURE[index]) {
                return false;
            }
        }

        return true;
    }

    private enum ImageType {
        JPEG,
        PNG
    }
}
