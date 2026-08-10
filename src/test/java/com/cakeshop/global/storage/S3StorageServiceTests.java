package com.cakeshop.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.global.infra.FileStorageDirectory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceTests {

    private static final String BUCKET = "cakeshop-test";
    private static final String BASE_URL = "https://cdn.example.com";
    private static final String KEY_PREFIX = "local-test";
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-10T00:00:00Z"),
            ZoneId.of("Asia/Seoul"));

    @Mock
    private S3Client s3Client;

    private S3StorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new S3StorageService(
                s3Client,
                BUCKET,
                BASE_URL + "/",
                KEY_PREFIX,
                FIXED_CLOCK);
    }

    @Test
    void store_validFile_uploadsAndReturnsPublicUrl() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "cake.JPG",
                "image/jpeg",
                "image".getBytes(StandardCharsets.UTF_8));
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String result = storageService.store(
                file,
                FileStorageDirectory.PRODUCT.getPath());

        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertThat(requestCaptor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(requestCaptor.getValue().key())
                .startsWith(KEY_PREFIX + "/product/202608/")
                .endsWith(".jpg");
        assertThat(result).isEqualTo(BASE_URL + "/" + requestCaptor.getValue().key());
    }

    @Test
    void store_pathTraversalDirectory_rejectsBeforeUpload() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cake.jpg", "image/jpeg", new byte[] {1});

        assertThatThrownBy(() -> storageService.store(file, "../product"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(s3Client, never()).putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class));
    }

    @Test
    void store_validFile_closesInputStream() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        InputStream inputStream = mock(InputStream.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("cake.jpg");
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getSize()).thenReturn(5L);
        when(file.getInputStream()).thenReturn(inputStream);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        storageService.store(file, FileStorageDirectory.PRODUCT.getPath());

        verify(inputStream).close();
    }

    @Test
    void delete_ownPublicUrl_deletesObject() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        storageService.delete(BASE_URL + "/" + KEY_PREFIX + "/product/202608/image.jpg");

        ArgumentCaptor<DeleteObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(requestCaptor.getValue().key())
                .isEqualTo(KEY_PREFIX + "/product/202608/image.jpg");
    }

    @Test
    void delete_foreignUrl_ignoresRequest() {
        storageService.delete("https://other.example.com/product/image.jpg");

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void delete_differentEnvironmentPrefix_ignoresRequest() {
        storageService.delete(BASE_URL + "/rds/product/202608/image.jpg");

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void constructor_invalidKeyPrefix_rejectsConfiguration() {
        assertThatThrownBy(() -> new S3StorageService(
                s3Client,
                BUCKET,
                BASE_URL,
                "../rds",
                FIXED_CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
