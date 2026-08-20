package com.cakeshop.domain.cart.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class CartMessageAreaTemplateTests {

    @Test
    void cartFeedback_usesMessageAreaWithoutDuplicatingSuccessToast() throws IOException {
        String cartTemplate = read("templates/customer/cart/list.html");
        String commonScript = read("static/js/app.js");

        assertThat(cartTemplate)
                .contains("fragments/common/alert :: alert")
                .doesNotContain("fragments/common/alert :: popup");
        assertThat(commonScript)
                .doesNotContain("showToast('장바구니'")
                .doesNotContain("textContent.includes('장바구니')");
    }

    private String read(String location) throws IOException {
        return new ClassPathResource(location)
                .getContentAsString(StandardCharsets.UTF_8);
    }
}
