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
                .filter(codePoint -> !isIgnoredForReservedNicknameComparison(codePoint))
                .collect(StringBuilder::new,
                        StringBuilder::appendCodePoint,
                        StringBuilder::append)
                .toString();
    }

    private static boolean isIgnoredForReservedNicknameComparison(int codePoint) {
        return Character.getType(codePoint) == Character.FORMAT
                || codePoint == 0x034F
                || codePoint == 0x115F
                || codePoint == 0x1160
                || codePoint == 0x17B4
                || codePoint == 0x17B5
                || isInRange(codePoint, 0x180B, 0x180F)
                || isInRange(codePoint, 0x200B, 0x200F)
                || isInRange(codePoint, 0x202A, 0x202E)
                || isInRange(codePoint, 0x2060, 0x206F)
                || codePoint == 0x3164
                || isInRange(codePoint, 0xFE00, 0xFE0F)
                || codePoint == 0xFEFF
                || codePoint == 0xFFA0
                || isInRange(codePoint, 0xFFF0, 0xFFF8)
                || isInRange(codePoint, 0x1BCA0, 0x1BCA3)
                || isInRange(codePoint, 0x1D173, 0x1D17A)
                || isInRange(codePoint, 0xE0000, 0xE0001)
                || isInRange(codePoint, 0xE0020, 0xE007F)
                || isInRange(codePoint, 0xE0100, 0xE01EF);
    }

    private static boolean isInRange(int codePoint, int start, int end) {
        return start <= codePoint && codePoint <= end;
    }

    private static boolean isBoundaryWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint);
    }
}
