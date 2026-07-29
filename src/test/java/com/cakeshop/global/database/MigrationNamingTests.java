package com.cakeshop.global.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 마이그레이션 파일명 규약을 지키는지 확인한다.
 *
 * <p>여러 사람이 같은 시간대에 브랜치를 나눠 작업하면 버전 번호가 겹치는데, Git은 파일명이
 * 다르면 조용히 둘 다 머지한다. 충돌은 머지 뒤 앱을 띄울 때야 드러나므로 여기서 먼저 잡는다.
 *
 * <p>DB도 컨테이너도 쓰지 않고 클래스패스 리소스만 훑는다.
 */
class MigrationNamingTests {

    /** {@code gradlew newMigration}이 만들어내는 형식. */
    private static final Pattern GENERATED_NAME =
            Pattern.compile("^V(\\d{8}_\\d{6})__[a-z0-9]+(?:_[a-z0-9]+)*\\.sql$");

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("uuuuMMdd_HHmmss")
                    .withResolverStyle(ResolverStyle.STRICT);

    /** 규약 도입 전부터 있던 파일. 이미 RDS와 팀원 로컬에 적용돼서 rename할 수 없다. */
    private static final Set<String> LEGACY_NAMES = Set.of(
            "V0__initial_schema.sql",
            "V1__add_product_stock.sql",
            "V3__add_member_name.sql"
    );

    private static final String HOW_TO_CREATE = """

            마이그레이션 파일은 직접 만들지 않는다. 아래 명령으로 생성한다.
              gradlew newMigration -Pdesc=add_coupon_table""";

    @Test
    void migrationVersionsAreUnique() throws IOException {
        Map<String, List<String>> byVersion = new LinkedHashMap<>();
        for (String name : resourceNames("classpath*:db/migration/*.sql")) {
            flywayVersion(name).ifPresent(version ->
                    byVersion.computeIfAbsent(version, key -> new ArrayList<>()).add(name)
            );
        }

        List<String> duplicated = byVersion.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getKey() + " ← " + String.join(", ", entry.getValue()))
                .toList();

        assertThat(duplicated)
                .as("같은 버전을 가진 마이그레이션이 있다. 이대로 머지하면 앱이 기동하지 않는다."
                        + HOW_TO_CREATE)
                .isEmpty();
    }

    @Test
    void migrationFileNamesFollowGeneratorFormat() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String name : resourceNames("classpath*:db/migration/*.sql")) {
            if (LEGACY_NAMES.contains(name)) {
                continue;
            }
            Matcher matcher = GENERATED_NAME.matcher(name);
            if (!matcher.matches()) {
                // 분 단위(V20260729_1015__x.sql)가 대표적인 위반이다. Flyway는 버전 조각을
                // 숫자로 비교하므로 1015 < 101542가 되어 나중에 만든 파일이 먼저 실행된다.
                violations.add(name + " (형식: V<yyyyMMdd>_<HHmmss>__<snake_case>.sql)");
                continue;
            }
            try {
                LocalDateTime.parse(matcher.group(1), STAMP);
            } catch (DateTimeParseException e) {
                violations.add(name + " (실재하지 않는 시각: " + matcher.group(1) + ")");
            }
        }

        assertThat(violations)
                .as("파일명 규약을 어긴 마이그레이션이 있다." + HOW_TO_CREATE)
                .isEmpty();
    }

    @Test
    void seedDirectoryHasNoVersionedMigrations() throws IOException {
        List<String> versioned = resourceNames("classpath*:db/seed/*.sql").stream()
                .filter(name -> name.startsWith("V") && name.contains("__"))
                .toList();

        assertThat(versioned)
                .as("""
                        db/seed는 Flyway가 스캔하지 않는 디렉터리다. 버전 접두어가 붙은 파일이
                        있으면 마이그레이션으로 착각하기 쉽다. 이름에서 V<버전>__를 뺀다.""")
                .isEmpty();
    }

    @Test
    void legacyLocalSeedDirectoryIsGone() throws IOException {
        assertThat(resourceNames("classpath*:db/local/*.sql"))
                .as("""
                        db/local은 db/migration과 하나의 버전 타임라인을 공유하던 옛 시드 위치다.
                        되살아나면 시드 수정이 다시 전원 DB 재생성을 부른다. db/seed를 쓴다.""")
                .isEmpty();
    }

    private static Optional<String> flywayVersion(String fileName) {
        int separator = fileName.indexOf("__");
        if (!fileName.startsWith("V") || separator <= 1) {
            return Optional.empty();
        }
        // Flyway는 '_'와 '.'를 모두 버전 구분자로 본다. V1_0과 V1.0은 같은 버전이다.
        return Optional.of(fileName.substring(1, separator).replace('_', '.'));
    }

    private static List<String> resourceNames(String locationPattern) throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(locationPattern);
        return Arrays.stream(resources)
                .map(Resource::getFilename)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }
}
