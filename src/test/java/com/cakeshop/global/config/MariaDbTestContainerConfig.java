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

    /**
     * 컨테이너는 UTC 로 뜨고, 연결만 서울 기준으로 맞춘다.
     *
     * 운영과 같은 조합을 재현하려는 것이다(application.yml, PLAN.md D10). 컨테이너까지
     * 서울로 띄우면 세션 시간대가 빠져도 모든 테스트가 통과해 버려서, 날짜 경계를 보는
     * 하네스가 정작 막으려던 어긋남을 못 잡는다 — 로컬이 우연히 서울이라 통과하고
     * 운영에서만 틀어지는 것과 같은 자리다(H19가 가르쳐 준 종류).
     *
     * 값이 이름이 아니라 오프셋인 이유도 application.yml 과 같다: 컨테이너의
     * mysql.time_zone_name 이 비어 있어 'Asia/Seoul' 로는 연결이 실패한다.
     */
    @Bean
    @ServiceConnection
    MariaDBContainer mariaDbContainer() {
        return new MariaDBContainer(MARIA_DB_IMAGE)
                .withUrlParam("connectionTimeZone", "+09:00")
                .withUrlParam("forceConnectionTimeZoneToSession", "true");
    }
}
