package com.cakeshop.domain.member.service;

/** 회원 화면과 저장 경로에서 공통으로 적용하는 닉네임 정책이다. */
public final class NicknamePolicy {

    private static final String RESERVED_NICKNAME = "관리자";

    private NicknamePolicy() {
    }

    public static boolean isAllowed(String nickname) {
        return !RESERVED_NICKNAME.equals(normalize(nickname));
    }

    public static String normalize(String nickname) {
        return nickname == null ? null : nickname.strip();
    }
}
