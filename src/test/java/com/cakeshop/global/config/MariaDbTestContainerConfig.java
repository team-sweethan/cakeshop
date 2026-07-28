package com.cakeshop.global.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class MariaDbTestContainerConfig {

    private static final DockerImageName MARIA_DB_IMAGE =
            DockerImageName.parse("mariadb:11.4.10");

    @Bean
    @ServiceConnection
    MariaDBContainer mariaDbContainer() {
        return new MariaDBContainer(MARIA_DB_IMAGE);
    }
}
