package com.cakeshop.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.unit.DataSize;

class ReviewMultipartConfigurationTests {

    @Test
    void multipartTransportLimit_exceedsReviewBusinessLimit() throws IOException {
        PropertySource<?> application = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .getFirst();

        Object configured = application.getProperty("spring.servlet.multipart.max-file-size");

        assertThat(configured).isNotNull();
        assertThat(DataSize.parse(configured.toString()).toBytes())
                .isGreaterThan(ReviewImageValidator.MAX_FILE_SIZE);
    }
}
