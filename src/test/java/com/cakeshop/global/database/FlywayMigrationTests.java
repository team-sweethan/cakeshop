package com.cakeshop.global.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.cakeshop.global.config.MariaDbIntegrationTest;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@MariaDbIntegrationTest
class FlywayMigrationTests {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrations_applyCommonSchemaWithoutLocalSeedData() {
        List<String> appliedVersions = Arrays.stream(
                        flyway.info().applied()
                )
                .map(MigrationInfo::getVersion)
                .filter(Objects::nonNull)
                .map(Object::toString)
                .toList();

        Integer stockColumnCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'products'
                  AND column_name = 'stock_quantity'
                """,
                Integer.class
        );
        Integer memberNameColumnCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'members'
                  AND column_name = 'name'
                  AND is_nullable = 'NO'
                """,
                Integer.class
        );
        Integer localMemberCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM members
                WHERE email IN (
                    'admin@cakeshop.local',
                    'user@cakeshop.local'
                )
                """,
                Integer.class
        );

        assertThat(appliedVersions)
                .containsExactly("0", "1", "3");
        assertThat(stockColumnCount)
                .isOne();
        assertThat(memberNameColumnCount)
                .isOne();
        assertThat(localMemberCount)
                .isZero();
    }
}
