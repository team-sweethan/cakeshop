package com.cakeshop.domain.review.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.cakeshop.domain.review.error.ReviewErrorCode;
import com.cakeshop.global.error.BusinessException;

class ReviewImageValidatorTests {

    private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

    private static final byte[] PNG_HEADER = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private final ReviewImageValidator validator = new ReviewImageValidator();

    @Test
    void validate_jpegAndPng_pass() {
        assertThatCode(() -> validator.validate(file("a.jpg", "image/jpeg", JPEG_HEADER)))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(file("a.png", "image/png", PNG_HEADER)))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_extensionMimeAndSignatureMustAllMatch() {
        assertInvalid(file("a.png", "image/jpeg", JPEG_HEADER));
        assertInvalid(file("a.jpg", "image/png", JPEG_HEADER));
        assertInvalid(file("a.jpg", "image/jpeg", PNG_HEADER));
    }

    @Test
    void validate_overFiveMegabytes_rejects() {
        byte[] payload = new byte[(int) ReviewImageValidator.MAX_FILE_SIZE + 1];
        System.arraycopy(JPEG_HEADER, 0, payload, 0, JPEG_HEADER.length);

        assertThatThrownBy(() -> validator.validate(file("a.jpg", "image/jpeg", payload)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ReviewErrorCode.IMAGE_TOO_LARGE);
    }

    private void assertInvalid(MultipartFile file) {
        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ReviewErrorCode.INVALID_IMAGE_FILE);
    }

    private MultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("images", name, contentType, content);
    }
}
