package com.cakeshop.global.common.paging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PageNavigationTests {

    @Test
    void of_emptyResult_returnsFirstPageOnly() {
        PageNavigation navigation = PageNavigation.of(1, 0);

        assertThat(navigation.startPage()).isEqualTo(1);
        assertThat(navigation.endPage()).isEqualTo(1);
        assertThat(navigation.hasPreviousBlock()).isFalse();
        assertThat(navigation.hasNextBlock()).isFalse();
    }
}
