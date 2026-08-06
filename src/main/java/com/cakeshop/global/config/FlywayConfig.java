package com.cakeshop.global.config;

import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.CoreErrorCode;
import org.flywaydb.core.api.ErrorCode;
import org.flywaydb.core.api.FlywayException;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway 실패 중 조치 방법이 정해져 있는 것만 안내 메시지로 바꿔 다시 던진다.
 * 원인을 아는 오류만 감싸고 나머지는 원본 예외를 그대로 돌려준다.
 * 실제 SQL 오류를 엉뚱한 조언으로 덮지 않기 위해서다.
 */
@Configuration
public class FlywayConfig {

    /** 로컬 DB가 Flyway 도입 이전 상태다. 수동 DDL로 만들어 이력 테이블이 없다. */
    private static final Set<ErrorCode> LEGACY_LOCAL_DATABASE = Set.of(
            CoreErrorCode.NON_EMPTY_SCHEMA_WITHOUT_SCHEMA_HISTORY_TABLE);

    /**
     * 이력 테이블과 db/migration 의 내용이 어긋났다.
     * migration 스크립트 실행 자체가 실패한 경우(FAILED_*)는 조치가 달라서 넣지 않는다.
     */
    private static final Set<ErrorCode> HISTORY_MISMATCH = Set.of(
            CoreErrorCode.CHECKSUM_MISMATCH,
            CoreErrorCode.DESCRIPTION_MISMATCH,
            CoreErrorCode.TYPE_MISMATCH,
            CoreErrorCode.APPLIED_VERSIONED_MIGRATION_NOT_RESOLVED,
            CoreErrorCode.APPLIED_REPEATABLE_MIGRATION_NOT_RESOLVED,
            CoreErrorCode.RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED,
            CoreErrorCode.RESOLVED_REPEATABLE_MIGRATION_NOT_APPLIED);

    /** 서로 다른 브랜치에서 만든 migration 의 버전이 겹쳤다. */
    private static final Set<ErrorCode> DUPLICATE_VERSION = Set.of(
            CoreErrorCode.DUPLICATE_VERSIONED_MIGRATION,
            CoreErrorCode.DUPLICATE_REPEATABLE_MIGRATION);

    // 콘솔 코드페이지에 따라 한글이 깨질 수 있어, 실행에 필요한 명령과 경로는 ASCII 로 적는다.
    // 명령은 그대로 복사해 실행할 수 있어야 하므로 wrapper 호출과 저장소 루트 기준 경로를 쓴다.
    private static final String LEGACY_LOCAL_DATABASE_GUIDE = """
            로컬 DB가 Flyway 관리 이전 상태입니다.

              데이터베이스에 테이블은 있지만 flyway_schema_history 가 없습니다.
              Flyway 도입 전에 DDL을 직접 실행해 만든 DB라면 정상입니다.

            조치: README '기존 로컬 DB 완전 초기화' 절차를 한 번만 실행하세요.
              1. DROP DATABASE / CREATE DATABASE
              2. .\\gradlew.bat bootRun --args="--spring.profiles.active=local"
                 (mac, linux: ./gradlew bootRun --args="--spring.profiles.active=local")
              3. MariaDB 클라이언트에서 아래 파일을 실행
                 src/main/resources/db/seed/seed-local.sql

            baseline-on-migrate 를 임의로 켜지 마세요.
            스키마가 어긋난 채로 '적용됨' 도장만 찍힙니다.
            """;

    private static final String HISTORY_MISMATCH_GUIDE = """
            flyway_schema_history 와 db/migration 의 내용이 일치하지 않습니다.

              이미 적용된 migration 이 수정됐거나,
              이력에만 있고 파일에는 없는 버전이 있습니다.

            조치
              * 머지된 migration 을 고쳤다면 되돌리고 새 파일로 변경하세요.
                  .\\gradlew.bat newMigration -Pdesc=<snake_case>
                  (mac, linux: ./gradlew newMigration -Pdesc=<snake_case>)
              * 그래도 실패하면 README '기존 로컬 DB 완전 초기화' 절차로
                로컬 DB 를 다시 만드세요.

            flyway_schema_history 를 직접 수정하지 마세요.
            """;

    private static final String DUPLICATE_VERSION_GUIDE = """
            같은 버전의 migration 파일이 둘 이상 있습니다.

              다른 브랜치에서 만든 파일과 버전이 겹쳤을 때 생깁니다.

            조치: 나중에 만든 파일을 지우고 새로 만든 뒤 내용을 옮기세요.
                  .\\gradlew.bat newMigration -Pdesc=<snake_case>
                  (mac, linux: ./gradlew newMigration -Pdesc=<snake_case>)

            migration 파일명은 직접 짓지 마세요. 버전은 생성 시각으로 자동으로 찍힙니다.
            """;

    private static final String BORDER = "*".repeat(70);

    @Bean
    FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            try {
                flyway.migrate();
            } catch (FlywayException e) {
                throw translate(e, () -> invalidMigrationCodes(flyway));
            }
        };
    }

    /**
     * 아는 원인이면 안내를 붙여 감싸고, 아니면 원본 예외를 그대로 돌려준다.
     * validationDetails 는 validate 실패의 세부 error code 이며 필요할 때만 조회한다.
     */
    static RuntimeException translate(FlywayException cause, Supplier<Set<ErrorCode>> validationDetails) {
        ErrorCode errorCode = cause.getErrorCode();

        if (LEGACY_LOCAL_DATABASE.contains(errorCode)) {
            return withGuidance(LEGACY_LOCAL_DATABASE_GUIDE, cause);
        }
        if (DUPLICATE_VERSION.contains(errorCode)) {
            return withGuidance(DUPLICATE_VERSION_GUIDE, cause);
        }
        if (HISTORY_MISMATCH.contains(errorCode)) {
            return withGuidance(HISTORY_MISMATCH_GUIDE, cause);
        }

        // validate 실패는 세부 원인이 무엇이든 VALIDATE_ERROR 하나로 올라온다(DbValidate).
        // 세부 코드를 따로 조회해 전부 아는 원인일 때만 안내를 붙인다.
        // 하나라도 모르는 원인이 섞이면 원본 예외가 더 정확하다.
        if (CoreErrorCode.VALIDATE_ERROR.equals(errorCode)) {
            Set<ErrorCode> details = validationDetails.get();
            if (!details.isEmpty() && HISTORY_MISMATCH.containsAll(details)) {
                return withGuidance(HISTORY_MISMATCH_GUIDE, cause);
            }
        }

        return cause;
    }

    private static Set<ErrorCode> invalidMigrationCodes(Flyway flyway) {
        try {
            return flyway.validateWithResult().invalidMigrations.stream()
                    .map(invalid -> invalid.errorDetails)
                    .filter(Objects::nonNull)
                    .map(details -> details.errorCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (RuntimeException e) {
            // 세부 원인을 못 얻으면 안내를 붙이지 않는다.
            return Set.of();
        }
    }

    private static IllegalStateException withGuidance(String guide, FlywayException cause) {
        String separator = System.lineSeparator();
        String message = separator + BORDER + separator + guide + BORDER;
        return new IllegalStateException(message, cause);
    }
}
