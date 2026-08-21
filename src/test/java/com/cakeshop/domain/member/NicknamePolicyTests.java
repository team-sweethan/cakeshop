package com.cakeshop.domain.member;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NicknamePolicyTests {

    @Test
    void normalize_trimsUnicodeSpaceSeparatorsAtNicknameBoundaries() {
        String nickname = "\u00A0\u200B관리자\u202F";

        assertThat(NicknamePolicy.normalize(nickname)).isEqualTo("관리자");
        assertThat(NicknamePolicy.isAllowed(nickname)).isFalse();
        assertThat(NicknamePolicy.isAllowed("관리자님")).isTrue();
    }

    @Test
    void normalize_removesInvisibleFormatCharactersWithinReservedNickname() {
        String nickname = "관\u200B리자";

        assertThat(NicknamePolicy.normalize(nickname)).isEqualTo("관리자");
        assertThat(NicknamePolicy.isAllowed(nickname)).isFalse();
    }
}
