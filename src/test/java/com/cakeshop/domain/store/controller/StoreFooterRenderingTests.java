package com.cakeshop.domain.store.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class StoreFooterRenderingTests {

    @Test
    void footer_withoutStore_showsPreparationMessageWithoutFakeStoreInformation() {
        Context context = new Context();
        context.setVariable("store", null);

        String html = templateEngine().process("fragments/common/footer", context);

        assertThat(html)
            .contains("매장 정보를 준비 중입니다.")
            .doesNotContain("OO케이크", "서울시 OO구 OO로", "02-0000-0000");
    }

    private SpringTemplateEngine templateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        return templateEngine;
    }
}
