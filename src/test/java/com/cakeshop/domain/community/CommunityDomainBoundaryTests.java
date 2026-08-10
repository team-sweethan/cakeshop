package com.cakeshop.domain.community;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** 커뮤니티가 회원 도메인의 공개 계약만 사용하도록 고정한다. */
class CommunityDomainBoundaryTests {

    private static final Path JAVA_ROOT =
            Path.of("src", "main", "java", "com", "cakeshop", "domain", "community");

    /** 새 매퍼도 검사하도록 디렉터리 전체를 읽는다. */
    private static final Path MAPPER_DIRECTORY =
            Path.of("src", "main", "resources", "mapper", "community");

    /** 합의된 회원 조회 계약과 반환 타입만 허용한다. */
    private static final Set<String> ALLOWED_MEMBER_TYPES = Set.of(
            "com.cakeshop.domain.member.service.MemberCommunityQueryService",
            "com.cakeshop.domain.member.dto.view.MemberCommunityView");

    private static final Pattern MEMBER_PACKAGE_REFERENCE =
            Pattern.compile("com\\.cakeshop\\.domain\\.member\\.[A-Za-z0-9_.]+");

    /** 회원 도메인이 소유한 테이블 목록이다. */
    private static final List<String> MEMBER_OWNED_TABLES =
            List.of("MEMBERS", "SOCIAL_ACCOUNTS", "MEMBER_STATUS_HISTORIES");

    /** 테이블 이름을 낱말 단위로 찾는다. */
    private static final Pattern MEMBER_OWNED_TABLE_REFERENCE = Pattern.compile(
            "\\b(" + String.join("|", MEMBER_OWNED_TABLES) + ")\\b");

    /** 커뮤니티 SQL의 모든 회원 소유 테이블 참조를 금지한다. */
    @Test
    void communityMapperXml_doesNotTouchMemberOwnedTables() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path mapperXml : mapperXmlFiles()) {
            Matcher matcher = MEMBER_OWNED_TABLE_REFERENCE.matcher(strippedOf(mapperXml));

            while (matcher.find()) {
                violations.add(mapperXml + " -> " + matcher.group());
            }
        }

        assertThat(violations)
                .as("커뮤니티 SQL은 회원 도메인 소유 테이블을 조회하지 않는다. 작성자는"
                        + " MemberCommunityQueryService 로 받는다 (conventions.md 15.1, 조각 10)")
                .isEmpty();
    }

    /** 커뮤니티 코드가 합의된 회원 계약만 정확히 사용하는지 확인한다. */
    @Test
    void communitySources_useExactlyAgreedMemberContract() throws IOException {
        Set<String> referenced = new LinkedHashSet<>();

        for (Path source : javaSources()) {
            Matcher matcher = MEMBER_PACKAGE_REFERENCE.matcher(strippedOf(source, false));

            while (matcher.find()) {
                referenced.add(matcher.group());
            }
        }

        assertThat(referenced)
                .as("커뮤니티는 합의된 회원 계약만 사용해야 한다")
                .containsExactlyInAnyOrderElementsOf(ALLOWED_MEMBER_TYPES);
    }

    /** 커뮤니티 매퍼 XML 전체를 읽는다. */
    private List<Path> mapperXmlFiles() throws IOException {
        List<Path> mappers;

        try (Stream<Path> paths = Files.list(MAPPER_DIRECTORY)) {
            mappers = paths.filter(path -> path.toString().endsWith(".xml")).sorted().toList();
        }

        assertThat(mappers).as("커뮤니티 매퍼 XML이 있어야 한다").isNotEmpty();

        return mappers;
    }

    private List<Path> javaSources() throws IOException {
        List<Path> sources;

        try (Stream<Path> paths = Files.walk(JAVA_ROOT)) {
            sources = paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }

        assertThat(sources).as("커뮤니티 자바 소스를 하나도 못 읽었다면 경로가 바뀐 것이다").isNotEmpty();

        return sources;
    }

    private String strippedOf(Path source) throws IOException {
        return strippedOf(source, true);
    }

    /** 소스 종류에 맞게 주석을 제거한다. */
    private String strippedOf(Path source, boolean upperCase) throws IOException {
        String text = Files.readString(source, StandardCharsets.UTF_8);

        String withoutComments = source.toString().endsWith(".java")
                ? withoutJavaComments(text)
                : withoutSqlComments(text.replaceAll("(?s)<!--.*?-->", " "));

        return upperCase ? withoutComments.toUpperCase() : withoutComments;
    }

    /** SQL 줄 주석과 블록 주석을 제거한다. */
    private String withoutSqlComments(String sql) {
        return sql
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("--[^\\n]*", " ");
    }

    /** 문자열과 문자 리터럴을 보존하며 자바 주석만 제거한다. */
    private String withoutJavaComments(String source) {
        StringBuilder code = new StringBuilder();
        int i = 0;

        while (i < source.length()) {
            if (source.startsWith("//", i)) {
                int end = source.indexOf('\n', i);
                i = end < 0 ? source.length() : end;
                code.append(' ');
            } else if (source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2);
                i = end < 0 ? source.length() : end + 2;
                code.append(' ');
            } else if (source.startsWith("\"\"\"", i)) {
                int end = source.indexOf("\"\"\"", i + 3);
                int next = end < 0 ? source.length() : end + 3;
                code.append(source, i, next);
                i = next;
            } else if (source.charAt(i) == '"' || source.charAt(i) == '\'') {
                int end = endOfLiteral(source, i);
                code.append(source, i, end);
                i = end;
            } else {
                code.append(source.charAt(i));
                i++;
            }
        }

        return code.toString();
    }

    /** 이스케이프를 건너뛰며 리터럴 끝을 찾는다. */
    private int endOfLiteral(String source, int open) {
        char quote = source.charAt(open);

        for (int i = open + 1; i < source.length(); i++) {
            char c = source.charAt(i);

            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return i + 1;
            }
        }

        return source.length();
    }
}
