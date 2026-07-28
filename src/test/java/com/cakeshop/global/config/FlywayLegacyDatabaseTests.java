package com.cakeshop.global.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Flyway 도입 이전에 만들어진 로컬 DB를 실제로 재현한다.
 *
 * <p>Spring context를 띄우지 않고 컨테이너를 직접 다룬다. 이 테스트가 필요로 하는 상태는
 * "테이블은 있는데 이력 테이블이 없어 기동이 실패하는 DB"라서 {@code @MariaDbIntegrationTest}로는
 * 만들 수 없다. Flyway가 실제로 어떤 error code를 내는지 확인하는 것이 목적이다.
 */
class FlywayLegacyDatabaseTests {

    private static final DockerImageName MARIA_DB_IMAGE = DockerImageName.parse("mariadb:11.4.10");

    private static MariaDBContainer mariaDb;

    @BeforeAll
    static void startContainer() {
        mariaDb = new MariaDBContainer(MARIA_DB_IMAGE);
        mariaDb.start();
    }

    @AfterAll
    static void stopContainer() {
        if (mariaDb != null) {
            mariaDb.stop();
        }
    }

    @Test
    void migrate_nonEmptySchemaWithoutHistoryTable_reportsLocalResetGuidance() throws SQLException {
        // docs/sql 의 DDL을 손으로 실행해 만든 로컬 DB를 흉내 낸다.
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE `legacy_manual_table` (`id` BIGINT PRIMARY KEY)");
        }

        FlywayMigrationStrategy strategy = new FlywayConfig().flywayMigrationStrategy();
        Flyway flyway = localLikeFlyway();

        assertThatThrownBy(() -> strategy.migrate(flyway))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("flyway_schema_history")
                .hasMessageContaining("src/main/resources/db/seed/seed-local.sql")
                .hasMessageContaining("gradlew")
                .hasMessageContaining("baseline-on-migrate")
                .cause()
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("Found non-empty schema(s)");
    }

    /** application.yml 의 루트 flyway 설정과 같은 조건으로 맞춘다. */
    private static Flyway localLikeFlyway() {
        return Flyway.configure()
                .dataSource(mariaDb.getJdbcUrl(), mariaDb.getUsername(), mariaDb.getPassword())
                .locations("classpath:db/migration")
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .outOfOrder(true)
                .load();
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                mariaDb.getJdbcUrl(), mariaDb.getUsername(), mariaDb.getPassword());
    }
}
