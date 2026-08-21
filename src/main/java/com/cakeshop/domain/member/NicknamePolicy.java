package com.cakeshop.domain.member;

import java.text.Normalizer;

/** 회원 입력과 저장 경로에서 공통으로 적용하는 닉네임 정책이다. */
public final class NicknamePolicy {

    private static final String RESERVED_NICKNAME = "관리자";

    private NicknamePolicy() {
    }

    public static boolean isAllowed(String nickname) {
        return !RESERVED_NICKNAME.equals(normalizeForReservedNicknameComparison(nickname));
    }

    public static String normalize(String nickname) {
        if (nickname == null) {
            return null;
        }

        String normalizedNickname = Normalizer.normalize(nickname, Normalizer.Form.NFC);
        int start = 0;
        int end = normalizedNickname.length();
        while (start < end) {
            int codePoint = normalizedNickname.codePointAt(start);
            if (!isBoundaryWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (start < end) {
            int codePoint = normalizedNickname.codePointBefore(end);
            if (!isBoundaryWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return normalizedNickname.substring(start, end);
    }

    private static String normalizeForReservedNicknameComparison(String nickname) {
        String normalizedNickname = normalize(nickname);
        return normalizedNickname == null ? null : normalizedNickname.codePoints()
                .filter(codePoint -> Character.getType(codePoint) != Character.FORMAT)
                .collect(StringBuilder::new,
                        StringBuilder::appendCodePoint,
                        StringBuilder::append)
                .toString();
    }

    private static boolean isBoundaryWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint);
    }
}
