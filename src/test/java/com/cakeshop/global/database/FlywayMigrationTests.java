package com.cakeshop.global.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
        MigrationInfo[] applied = flyway.info().applied();

        List<String> appliedVersions = Arrays.stream(applied)
                .map(MigrationInfo::getVersion)
                .filter(Objects::nonNull)
                .map(Object::toString)
                .toList();
        List<String> failedScripts = Arrays.stream(applied)
                .filter(info -> info.getState().isFailed())
                .map(MigrationInfo::getScript)
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

        // 마이그레이션은 계속 늘어나므로 목록 전체를 고정하지 않는다.
        // 초기 세 개의 상대 순서와 "전부 성공했고 checksum이 맞다"만 지킨다.
        assertThat(appliedVersions)
                .containsSubsequence("0", "1", "3");
        assertThat(failedScripts)
                .isEmpty();
        assertThatCode(flyway::validate)
                .doesNotThrowAnyException();
        assertThat(stockColumnCount)
                .isOne();
        assertThat(memberNameColumnCount)
                .isOne();
        assertThat(localMemberCount)
                .isZero();
    }
}
