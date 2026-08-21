package com.cakeshop.domain.member;

/** 회원 입력과 저장 경로에서 공통으로 적용하는 닉네임 정책이다. */
public final class NicknamePolicy {

    private static final String RESERVED_NICKNAME = "관리자";

    private NicknamePolicy() {
    }

    public static boolean isAllowed(String nickname) {
        return !RESERVED_NICKNAME.equals(normalize(nickname));
    }

    public static String normalize(String nickname) {
        if (nickname == null) {
            return null;
        }

        String withoutFormatCharacters = nickname.codePoints()
                .filter(codePoint -> Character.getType(codePoint) != Character.FORMAT)
                .collect(StringBuilder::new,
                        StringBuilder::appendCodePoint,
                        StringBuilder::append)
                .toString();

        int start = 0;
        int end = withoutFormatCharacters.length();
        while (start < end) {
            int codePoint = withoutFormatCharacters.codePointAt(start);
            if (!isBoundaryWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (start < end) {
            int codePoint = withoutFormatCharacters.codePointBefore(end);
            if (!isBoundaryWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return withoutFormatCharacters.substring(start, end);
    }

    private static boolean isBoundaryWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint);
    }
}
