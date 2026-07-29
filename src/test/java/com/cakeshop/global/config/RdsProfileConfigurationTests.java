package com.cakeshop.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class RdsProfileConfigurationTests {

    @Test
    void rdsProfile_configuration_disablesFlyway() throws IOException {
        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));

        assertThat(propertySources)
                .filteredOn(source ->
                        "rds".equals(source.getProperty("spring.config.activate.on-profile")))
                .singleElement()
                .satisfies(source ->
                        assertThat(source.getProperty("spring.flyway.enabled"))
                                .isEqualTo(false));
    }
}
