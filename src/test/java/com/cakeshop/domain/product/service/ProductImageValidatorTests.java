package com.cakeshop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cakeshop.domain.product.error.ProductErrorCode;
import com.cakeshop.global.error.BusinessException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ProductImageValidatorTests {

    private final ProductImageValidator validator =
            new ProductImageValidator();

    @Test
    void validate_jpegExtensionMimeAndSignatureMatch_acceptsFile() {
        MockMultipartFile file = new MockMultipartFile(
                "imageFile",
                "cake.JPG",
                "image/jpeg",
                jpegBytes()
        );

        assertThatCode(() -> validator.validate(file))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_pngExtensionMimeAndSignatureMatch_acceptsFile() {
        MockMultipartFile file = new MockMultipartFile(
                "imageFile",
                "cake.png",
                "image/png",
                pngBytes()
        );

        assertThatCode(() -> validator.validate(file))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_emptyFile_rejectsInvalidImage() {
        MockMultipartFile file = new MockMultipartFile(
                "imageFile",
                "cake.jpg",
                "image/jpeg",
                new byte[0]
        );

        assertBusinessError(
                () -> validator.validate(file),
                ProductErrorCode.INVALID_IMAGE_FILE
        );
    }

    @Test
    void validate_fileLargerThanFiveMegabytes_rejectsLargeImage() {
        MockMultipartFile file = new MockMultipartFile(
                "imageFile",
                "cake.jpg",
                "image/jpeg",
                new byte[(int) ProductImageValidator.MAX_FILE_SIZE + 1]
        );

        assertBusinessError(
                () -> validator.validate(file),
                ProductErrorCode.IMAGE_TOO_LARGE
        );
    }

    @Test
    void validate_extensionMimeAndSignatureMismatch_rejectsInvalidImage() {
        MockMultipartFile file = new MockMultipartFile(
                "imageFile",
                "cake.png",
                "image/png",
                jpegBytes()
        );

        assertBusinessError(
                () -> validator.validate(file),
                ProductErrorCode.INVALID_IMAGE_FILE
        );
    }

    @Test
    void validate_unsupportedFormat_rejectsInvalidImage() {
        MockMultipartFile file = new MockMultipartFile(
                "imageFile",
                "cake.gif",
                "image/gif",
                "GIF89a".getBytes()
        );

        assertBusinessError(
                () -> validator.validate(file),
                ProductErrorCode.INVALID_IMAGE_FILE
        );
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

    private byte[] jpegBytes() {
        return new byte[] {
                (byte) 0xFF,
                (byte) 0xD8,
                (byte) 0xFF,
                0x00
        };
    }

    private byte[] pngBytes() {
        return new byte[] {
                (byte) 0x89,
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A
        };
    }
}
