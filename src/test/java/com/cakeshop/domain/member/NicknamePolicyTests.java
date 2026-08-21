package com.cakeshop.domain.member;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NicknamePolicyTests {

    @Test
    void normalize_trimsUnicodeSpaceSeparatorsAtNicknameBoundaries() {
        String nickname = "\u00A0관리자\u202F";

        assertThat(NicknamePolicy.normalize(nickname)).isEqualTo("관리자");
        assertThat(NicknamePolicy.isAllowed(nickname)).isFalse();
        assertThat(NicknamePolicy.isAllowed("관리자님")).isTrue();
    }

    @Test
    void isAllowed_rejectsInvisibleFormatCharactersWithinReservedNickname() {
        String nickname = "관\u200B리자";

        assertThat(NicknamePolicy.normalize(nickname)).isEqualTo(nickname);
        assertThat(NicknamePolicy.isAllowed(nickname)).isFalse();
    }

    @Test
    void isAllowed_rejectsDefaultIgnorableCharacterWithinReservedNickname() {
        String nickname = "관\u034F리자";

        assertThat(NicknamePolicy.normalize(nickname)).isEqualTo(nickname);
        assertThat(NicknamePolicy.isAllowed(nickname)).isFalse();
    }

    @Test
    void isAllowed_rejectsNfdReservedNickname() {
        String nickname = "관리자";

        assertThat(NicknamePolicy.normalize(nickname)).isEqualTo("관리자");
        assertThat(NicknamePolicy.isAllowed(nickname)).isFalse();
    }

    @Test
    void isAllowed_rejectsNfdReservedNicknameWithDefaultIgnorableCharacter() {
        String nickname = "ᄀ\u034Fᅪᆫ리자";

        assertThat(NicknamePolicy.isAllowed(nickname)).isFalse();
    }

    @Test
    void normalize_preservesZeroWidthJoinerInEmojiNickname() {
        String nickname = "👩‍💻";

        assertThat(NicknamePolicy.normalize(nickname)).isEqualTo(nickname);
        assertThat(NicknamePolicy.isAllowed(nickname)).isTrue();
    }
}
