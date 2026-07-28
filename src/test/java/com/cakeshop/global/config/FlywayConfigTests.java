package com.cakeshop.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.api.CoreErrorCode;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

class FlywayConfigTests {

    @Test
    void translate_legacyLocalDatabase_addsResetGuidance() {
        FlywayException cause = new FlywayException(
                "Found non-empty schema(s) `cakeshop` but no schema history table.",
                CoreErrorCode.NON_EMPTY_SCHEMA_WITHOUT_SCHEMA_HISTORY_TABLE);

        RuntimeException translated = FlywayConfig.translate(cause);

        assertThat(translated).isNotSameAs(cause).hasCause(cause);
        assertThat(translated.getMessage())
                .contains("flyway_schema_history")
                .contains("db/seed/seed-local.sql")
                .contains("baseline-on-migrate");
    }

    @Test
    void translate_historyMismatch_addsNewMigrationGuidance() {
        FlywayException cause = new FlywayException(
                "Migration checksum mismatch for migration version 1",
                CoreErrorCode.CHECKSUM_MISMATCH);

        RuntimeException translated = FlywayConfig.translate(cause);

        assertThat(translated).isNotSameAs(cause).hasCause(cause);
        assertThat(translated.getMessage())
                .contains("flyway_schema_history")
                .contains("newMigration");
    }

    @Test
    void translate_appliedMigrationNotResolved_addsNewMigrationGuidance() {
        FlywayException cause = new FlywayException(
                "Detected applied migration not resolved locally: 2",
                CoreErrorCode.APPLIED_VERSIONED_MIGRATION_NOT_RESOLVED);

        RuntimeException translated = FlywayConfig.translate(cause);

        assertThat(translated).isNotSameAs(cause).hasCause(cause);
        assertThat(translated.getMessage()).contains("newMigration");
    }

    @Test
    void translate_duplicateVersion_addsGeneratorGuidance() {
        FlywayException cause = new FlywayException(
                "Found more than one migration with version 20260729.101542",
                CoreErrorCode.DUPLICATE_VERSIONED_MIGRATION);

        RuntimeException translated = FlywayConfig.translate(cause);

        assertThat(translated).isNotSameAs(cause).hasCause(cause);
        assertThat(translated.getMessage()).contains("newMigration");
    }

    @Test
    void translate_unrelatedError_returnsOriginalException() {
        FlywayException cause = new FlywayException(
                "Unable to obtain connection from database", CoreErrorCode.DB_CONNECTION);

        assertThat(FlywayConfig.translate(cause)).isSameAs(cause);
    }
}
