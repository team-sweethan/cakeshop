package com.cakeshop.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.flywaydb.core.api.CoreErrorCode;
import org.flywaydb.core.api.ErrorCode;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

class FlywayConfigTests {

    /** validate 세부 코드를 조회할 일이 없는 경우. 조회하면 테스트가 실패하도록 둔다. */
    private static Set<ErrorCode> notQueried() {
        throw new AssertionError("validate 세부 코드를 조회하면 안 되는 경로다");
    }

    @Test
    void translate_legacyLocalDatabase_addsResetGuidance() {
        FlywayException cause = new FlywayException(
                "Found non-empty schema(s) `cakeshop` but no schema history table.",
                CoreErrorCode.NON_EMPTY_SCHEMA_WITHOUT_SCHEMA_HISTORY_TABLE);

        RuntimeException translated = FlywayConfig.translate(cause, FlywayConfigTests::notQueried);

        assertThat(translated).isNotSameAs(cause).hasCause(cause);
        assertThat(translated.getMessage())
                .contains("flyway_schema_history")
                .contains("src/main/resources/db/seed/seed-local.sql")
                .contains("baseline-on-migrate");
    }

    @Test
    void translate_duplicateVersion_addsGeneratorGuidance() {
        FlywayException cause = new FlywayException(
                "Found more than one migration with version 20260729.101542",
                CoreErrorCode.DUPLICATE_VERSIONED_MIGRATION);

        RuntimeException translated = FlywayConfig.translate(cause, FlywayConfigTests::notQueried);

        assertThat(translated).isNotSameAs(cause).hasCause(cause);
        assertThat(translated.getMessage()).contains("newMigration");
    }

    @Test
    void translate_historyMismatchCode_addsRecoveryGuidance() {
        FlywayException cause = new FlywayException(
                "Migration checksum mismatch for migration version 1", CoreErrorCode.CHECKSUM_MISMATCH);

        RuntimeException translated = FlywayConfig.translate(cause, FlywayConfigTests::notQueried);

        assertThat(translated).isNotSameAs(cause).hasCause(cause);
        assertThat(translated.getMessage())
                .contains("flyway_schema_history")
                .contains("newMigration");
    }

    // validate 실패는 세부 원인이 무엇이든 최상위 코드가 VALIDATE_ERROR 하나다(DbValidate).
    // 세부 코드를 봐야 조치를 정할 수 있다.

    @Test
    void translate_validateErrorWithKnownDetails_addsRecoveryGuidance() {
        FlywayException cause = new FlywayException(
                "Validate failed: Migrations have failed validation", CoreErrorCode.VALIDATE_ERROR);

        RuntimeException translated = FlywayConfig.translate(
                cause, () -> Set.of(CoreErrorCode.CHECKSUM_MISMATCH,
                        CoreErrorCode.APPLIED_VERSIONED_MIGRATION_NOT_RESOLVED));

        assertThat(translated).isNotSameAs(cause).hasCause(cause);
        assertThat(translated.getMessage()).contains("flyway_schema_history");
    }

    @Test
    void translate_validateErrorWithUnsupportedDetail_returnsOriginalException() {
        FlywayException cause = new FlywayException(
                "Validate failed: Migrations have failed validation", CoreErrorCode.VALIDATE_ERROR);

        // 이전에 실패한 migration 이 남은 경우다. 조치가 달라서 안내를 붙이면 안 된다.
        RuntimeException translated = FlywayConfig.translate(
                cause, () -> Set.of(CoreErrorCode.CHECKSUM_MISMATCH,
                        CoreErrorCode.FAILED_VERSIONED_MIGRATION));

        assertThat(translated).isSameAs(cause);
    }

    @Test
    void translate_validateErrorWithoutDetails_returnsOriginalException() {
        FlywayException cause = new FlywayException(
                "Validate failed: Migrations have failed validation", CoreErrorCode.VALIDATE_ERROR);

        RuntimeException translated = FlywayConfig.translate(cause, Set::of);

        assertThat(translated).isSameAs(cause);
    }

    @Test
    void translate_unrelatedError_returnsOriginalException() {
        FlywayException cause = new FlywayException(
                "Unable to obtain connection from database", CoreErrorCode.DB_CONNECTION);

        assertThat(FlywayConfig.translate(cause, FlywayConfigTests::notQueried)).isSameAs(cause);
    }
}
