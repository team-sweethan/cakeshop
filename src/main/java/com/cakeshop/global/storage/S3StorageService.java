package com.cakeshop.global.storage;

import com.cakeshop.global.infra.FileStorageClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** s3 프로필에서 로컬 파일 저장소를 대신하는 공용 S3 저장소 구현체다. */
@Component
@Profile("s3")
public class S3StorageService implements FileStorageClient {

    private static final DateTimeFormatter MONTH =
            DateTimeFormatter.ofPattern("yyyyMM");

    private final S3Client s3Client;
    private final String bucket;
    private final String baseUrl;
    private final Clock clock;

    // S3 클라이언트와 저장 대상 버킷·공개 URL 설정
    @Autowired
    public S3StorageService(
            S3Client s3Client,
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.s3.base-url}") String baseUrl,
            Clock clock) {
        this.clock = clock;
        if (!StringUtils.hasText(bucket)) {
            throw new IllegalArgumentException("aws.s3.bucket 설정이 비어 있습니다.");
        }
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("aws.s3.base-url 설정이 비어 있습니다.");
        }
        this.s3Client = s3Client;
        this.bucket = bucket.trim();
        this.baseUrl = stripTrailingSlash(baseUrl.trim());
    }

    // Test-friendly constructor without explicit Clock
    public S3StorageService(S3Client s3Client,
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.s3.base-url}") String baseUrl) {
        this(s3Client, bucket, baseUrl, Clock.systemDefaultZone());
    }

    // 파일을 S3에 저장하고 화면과 DB에서 사용할 공개 URL 반환
    @Override
    public String store(MultipartFile file, String directory) {
        validateFile(file);
        validateDirectory(directory);

        String objectKey = createObjectKey(file, directory.trim());
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        try {
            s3Client.putObject(
                    request,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException exception) {
            throw new UncheckedIOException("업로드할 파일을 읽지 못했습니다.", exception);
        } catch (SdkException exception) {
            throw new IllegalStateException("S3 파일 업로드에 실패했습니다.", exception);
        }

        return baseUrl + "/" + objectKey;
    }

    // 이 저장소가 발급한 공개 URL에 해당하는 S3 객체 삭제
    @Override
    public void delete(String path) {
        String objectKey = extractObjectKey(path);
        if (objectKey == null) {
            return;
        }

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        try {
            s3Client.deleteObject(request);
        } catch (SdkException exception) {
            throw new IllegalStateException("S3 파일 삭제에 실패했습니다.", exception);
        }
    }

    // 비어 있는 파일 업로드 차단
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("저장할 파일이 비어 있습니다.");
        }
    }

    // 빈 경로와 경로 탈출 문자열 차단
    private void validateDirectory(String directory) {
        if (!StringUtils.hasText(directory)
                || directory.contains("..")
                || directory.contains("/")
                || directory.contains("\\")) {
            throw new IllegalArgumentException("허용되지 않는 S3 저장 경로입니다.");
        }
    }

    // 도메인·연월·UUID를 조합한 중복 없는 S3 객체 키 생성
    private String createObjectKey(MultipartFile file, String directory) {
        String extension = extension(file.getOriginalFilename());
        return directory
                + "/"
                + LocalDate.now(clock).format(MONTH)
                + "/"
                + UUID.randomUUID()
                + extension;
    }

    // 이 저장소가 반환한 공개 URL에서 삭제할 S3 객체 키 추출
    private String extractObjectKey(String path) {
        String prefix = baseUrl + "/";
        if (!StringUtils.hasText(path) || !path.startsWith(prefix)) {
            return null;
        }

        String objectKey = path.substring(prefix.length());
        return StringUtils.hasText(objectKey) ? objectKey : null;
    }

    // 원본 파일명의 확장자를 소문자로 정규화
    private String extension(String originalFilename) {
        String extension = StringUtils.getFilenameExtension(originalFilename);
        return StringUtils.hasText(extension)
                ? "." + extension.toLowerCase(Locale.ROOT)
                : "";
    }

    // 공개 기본 URL 끝의 슬래시 제거
    private String stripTrailingSlash(String value) {
        return value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }
}
