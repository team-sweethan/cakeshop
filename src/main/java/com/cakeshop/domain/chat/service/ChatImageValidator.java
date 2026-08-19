package com.cakeshop.domain.chat.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import com.cakeshop.domain.chat.error.ChatErrorCode;
import com.cakeshop.global.error.BusinessException;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

// 채팅 전용 이미지 파일 검증기 (확장자, MIME, 파일 시그니처, 5MB 크기 제한)
@Component
public class ChatImageValidator {

    static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5MB

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A
    };

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ChatErrorCode.INVALID_IMAGE_FILE);
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ChatErrorCode.IMAGE_TOO_LARGE);
        }

        ImageType extensionType = extensionType(file.getOriginalFilename());
        ImageType contentType = contentType(file.getContentType());
        ImageType signatureType = signatureType(file);

        if (extensionType == null || extensionType != contentType || extensionType != signatureType) {
            throw new BusinessException(ChatErrorCode.INVALID_IMAGE_FILE);
        }
    }

    private ImageType extensionType(String originalFilename) {
        String extension = StringUtils.getFilenameExtension(originalFilename);
        if (!StringUtils.hasText(extension)) return null;

        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> ImageType.JPEG;
            case "png" -> ImageType.PNG;
            default -> null;
        };
    }

    private ImageType contentType(String contentType) {
        if ("image/jpeg".equalsIgnoreCase(contentType)) return ImageType.JPEG;
        if ("image/png".equalsIgnoreCase(contentType)) return ImageType.PNG;
        return null;
    }

    private ImageType signatureType(MultipartFile file) {
        byte[] header;
        try (InputStream inputStream = file.getInputStream()) {
            header = inputStream.readNBytes(PNG_SIGNATURE.length);
        } catch (IOException exception) {
            throw new BusinessException(ChatErrorCode.INVALID_IMAGE_FILE);
        }

        if (isJpeg(header)) return ImageType.JPEG;
        if (isPng(header)) return ImageType.PNG;
        return null;
    }

    private boolean isJpeg(byte[] header) {
        return header.length >= 3
                && header[0] == (byte) 0xFF
                && header[1] == (byte) 0xD8
                && header[2] == (byte) 0xFF;
    }

    private boolean isPng(byte[] header) {
        if (header.length < PNG_SIGNATURE.length) return false;
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (header[i] != PNG_SIGNATURE[i]) return false;
        }
        return true;
    }

    private enum ImageType {
        JPEG, PNG
    }
}
