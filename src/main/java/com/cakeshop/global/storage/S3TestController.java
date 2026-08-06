package com.cakeshop.global.storage;

import com.cakeshop.global.infra.FileStorageDirectory;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** local과 s3 프로필을 함께 사용할 때만 생성되는 연결 확인 Controller다. */
@Profile("local & s3")
@RestController
public class S3TestController {

    private final S3StorageService s3StorageService;

    // 실제 S3 연결 확인에 사용할 저장 서비스 주입
    public S3TestController(S3StorageService s3StorageService) {
        this.s3StorageService = s3StorageService;
    }

    // 로컬 PC에서 실제 S3 업로드 연결 확인
    @PostMapping("/api/local/s3-test/upload")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file
    ) {
        String imageUrl = s3StorageService.store(
                file,
                FileStorageDirectory.PRODUCT.getPath()
        );

        return ResponseEntity.ok(
                Map.of(
                        "message", "S3 업로드 성공",
                        "imageUrl", imageUrl
                )
        );
    }
}
