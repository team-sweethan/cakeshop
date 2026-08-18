package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.global.error.BusinessException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/** 첨부 파일의 형식·용량 검증 계약을 확인한다. */
class CommunityImageValidatorTests {

    private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

    private static final byte[] PNG_HEADER = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private final CommunityImageValidator validator = new CommunityImageValidator();

    @Test
    void validate_jpegAndPng_pass() {
        assertThatCode(() -> validator.validate(file("a.jpg", "image/jpeg", JPEG_HEADER)))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(file("a.png", "image/png", PNG_HEADER)))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_extensionAloneMismatched_rejects() {
        assertThatThrownBy(() -> validator.validate(file("a.png", "image/jpeg", JPEG_HEADER)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.INVALID_IMAGE_FILE);
    }

    @Test
    void validate_contentTypeAloneMismatched_rejects() {
        assertThatThrownBy(() -> validator.validate(file("a.jpg", "image/png", JPEG_HEADER)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.INVALID_IMAGE_FILE);
    }

    @Test
    void validate_signatureAloneMismatched_rejects() {
        assertThatThrownBy(() -> validator.validate(file("a.jpg", "image/jpeg", PNG_HEADER)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.INVALID_IMAGE_FILE);
    }

    @Test
    void validate_unsupportedExtension_rejects() {
        assertThatThrownBy(() -> validator.validate(file("a.gif", "image/gif", JPEG_HEADER)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.INVALID_IMAGE_FILE);
    }

    @Test
    void validate_overFiveMegabytes_rejects() {
        byte[] payload = new byte[(int) CommunityImageValidator.MAX_FILE_SIZE + 1];

        System.arraycopy(JPEG_HEADER, 0, payload, 0, JPEG_HEADER.length);

        assertThatThrownBy(() -> validator.validate(file("a.jpg", "image/jpeg", payload)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.IMAGE_TOO_LARGE);
    }

    @Test
    void validate_emptyOrMissing_rejects() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.INVALID_IMAGE_FILE);

        assertThatThrownBy(() -> validator.validate(file("a.jpg", "image/jpeg", new byte[0])))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.INVALID_IMAGE_FILE);
    }

    private MultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("images", name, contentType, content);
    }
}
