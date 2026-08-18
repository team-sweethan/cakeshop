package com.cakeshop.global.infra;

/** 로컬과 S3 저장소가 공통으로 사용하는 도메인별 최상위 경로다. */
public enum FileStorageDirectory {

    PRODUCT("product"),
    REVIEW("review"),
    BANNER("banner"),
    CUSTOMER("customer"),
    STORE("store"),
    COMMUNITY("community");

    private final String path;

    // 도메인별 파일 저장 최상위 경로 설정
    FileStorageDirectory(String path) {
        this.path = path;
    }

    // 파일 객체 키와 로컬 상대 경로에 사용할 경로 반환
    public String getPath() {
        return path;
    }
}
