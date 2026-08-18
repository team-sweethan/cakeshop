package com.cakeshop.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ErrorTemplateAccessibilityTests {

    @ParameterizedTest
    @ValueSource(strings = {"404", "4xx", "500"})
    void errorDocument_declaresKoreanAsDefaultLanguage(String templateName)
            throws IOException {
        String resourcePath = "templates/error/" + templateName + ".html";

        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream(resourcePath)) {
            assertThat(input)
                    .as("오류 템플릿이 classpath에 존재해야 한다: %s", resourcePath)
                    .isNotNull();

            String template = new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );

            assertThat(template).contains("<html lang=\"ko\"");
        }
    }
}
