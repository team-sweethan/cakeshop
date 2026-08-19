package com.cakeshop.global.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class MariaDbTestContainerConfig {

    private static final DockerImageName MARIA_DB_IMAGE =
            DockerImageName.parse("mariadb:11.4.10");

    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    /**
     * 컨테이너는 JVM 당 하나만 띄우고, 격리는 Context 마다 데이터베이스를 따로 파서 얻는다.
     *
     * 예전에는 컨테이너 자체가 Context 빈이라 Context 가 갈릴 때마다 MariaDB 를 통째로 새로
     * 세웠다(측정: 기동 5.2초 x 14회). 격리에 필요한 것은 서버가 아니라 스키마가 다른 것이므로,
     * 서버는 공유하고 데이터베이스만 나눈다. Context 가 닫힐 때 그 데이터베이스를 지우므로
     * {@code @DirtiesContext} 가 DB 를 초기화하던 성질도 그대로 남는다.
     *
     * 커넥션 상한을 올리는 이유는 Context 캐시가 살아 있는 동안 Context 마다 커넥션 풀이
     * 하나씩 붙기 때문이다. 전체 실행에서 Context 27개가 동시에 살아 있어 기본값 151로는
     * 모자란다(Too many connections).
     *
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
    private static final MariaDBContainer CONTAINER = startSharedContainer();

    private static MariaDBContainer startSharedContainer() {
        MariaDBContainer container = new MariaDBContainer(MARIA_DB_IMAGE)
                .withCommand("--max-connections=500")
                .withUrlParam("connectionTimeZone", "+09:00")
                .withUrlParam("forceConnectionTimeZoneToSession", "true");
        container.start();

        return container;
    }

    @Bean(destroyMethod = "drop")
    IsolatedDatabase isolatedDatabase() {
        return new IsolatedDatabase("ctx_" + DATABASE_SEQUENCE.incrementAndGet());
    }

    @Bean
    JdbcConnectionDetails jdbcConnectionDetails(IsolatedDatabase database) {
        return new JdbcConnectionDetails() {

            @Override
            public String getUsername() {
                return CONTAINER.getUsername();
            }

            @Override
            public String getPassword() {
                return CONTAINER.getPassword();
            }

            @Override
            public String getJdbcUrl() {
                return database.jdbcUrl();
            }
        };
    }

    static final class IsolatedDatabase {

        private final String name;

        IsolatedDatabase(String name) {
            this.name = name;

            runAsRoot("CREATE DATABASE `%s` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
                    .formatted(name));
            runAsRoot("GRANT ALL PRIVILEGES ON `%s`.* TO '%s'@'%%'"
                    .formatted(name, CONTAINER.getUsername()));
        }

        String jdbcUrl() {
            return CONTAINER.getJdbcUrl()
                    .replace("/" + CONTAINER.getDatabaseName() + "?", "/" + name + "?");
        }

        void drop() {
            runAsRoot("DROP DATABASE IF EXISTS `%s`".formatted(name));
        }

        private static void runAsRoot(String sql) {
            try (Connection connection = DriverManager.getConnection(
                            CONTAINER.getJdbcUrl(), "root", CONTAINER.getPassword());
                    Statement statement = connection.createStatement()) {
                statement.execute(sql);
            } catch (SQLException e) {
                throw new IllegalStateException("테스트 데이터베이스 준비에 실패했다: " + sql, e);
            }
        }
    }
}
