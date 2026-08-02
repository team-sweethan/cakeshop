package com.cakeshop.global.common.paging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 페이지 요청 값 보정 규칙을 고정한다.
 *
 * <p>{@code page}와 {@code size}는 공개 화면의 요청 파라미터라 누구나 임의의 값을 넣을 수
 * 있다. 여기서 걸러지지 않으면 그대로 SQL의 {@code LIMIT}/{@code OFFSET}이 된다.
 */
class PageRequestTests {

    @Test
    void normalRequest_isKeptAsIs() {
        PageRequest pageRequest = new PageRequest(3, 20);

        assertThat(pageRequest.getPage()).isEqualTo(3);
        assertThat(pageRequest.getSize()).isEqualTo(20);
        assertThat(pageRequest.getOffset()).isEqualTo(40);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void pageBelowOne_fallsBackToFirstPage(int page) {
        assertThat(new PageRequest(page, 20).getPage()).isEqualTo(1);
    }

    @Test
    void nullValues_fallBackToDefaults() {
        PageRequest pageRequest = new PageRequest(null, null);

        assertThat(pageRequest.getPage()).isEqualTo(1);
        assertThat(pageRequest.getSize()).isEqualTo(PageRequest.DEFAULT_SIZE);
        assertThat(pageRequest.getOffset()).isZero();
    }

    @Test
    void sizeAboveMax_isCapped() {
        assertThat(new PageRequest(1, 1000).getSize()).isEqualTo(PageRequest.MAX_SIZE);
    }

    /**
     * 큰 페이지 번호가 음수 OFFSET으로 뒤집히지 않는지 확인한다.
     *
     * <p>{@code (page - 1) * size}는 int 연산이다. 상한이 없으면 {@code page=2147483647},
     * {@code size=20}에서 정확히 {@code -40}이 되고, MariaDB는 음수 OFFSET을 거부해 공개
     * 목록 화면이 500으로 죽는다. <b>결과가 그럴듯한 빈 목록이 아니라 예외라서, 큰 값을
     * 넣어 보기 전까지는 드러나지 않는다.</b>
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 20, PageRequest.MAX_SIZE})
    void hugePage_neverProducesNegativeOffset(int size) {
        PageRequest pageRequest = new PageRequest(Integer.MAX_VALUE, size);

        assertThat(pageRequest.getOffset())
                .as("OFFSET은 음수가 될 수 없다")
                .isNotNegative();
        assertThat(pageRequest.getPage()).isPositive();
    }
}
