package com.cakeshop.global.infra;

import org.springframework.web.multipart.MultipartFile;

// 저장 위치와 무관하게 도메인 서비스가 사용하는 공용 파일 저장 인터페이스
public interface FileStorageClient {

    // 파일을 저장하고 DB와 화면에서 사용할 접근 경로 또는 URL 반환
    String store(MultipartFile file, String directory);

    // store()가 반환한 접근 경로 또는 URL에 해당하는 파일 삭제
    void delete(String path);
}
