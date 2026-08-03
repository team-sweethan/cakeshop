package com.cakeshop.domain.community;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 1차 댓글의 범위를 코드에서 고정한다.
 *
 * <p>1차 댓글은 <b>1단계, 작성·삭제만</b>이다(docs/community/DOMAIN.md 6.4). 두 규칙 모두
 * 어겨도 아무 오류가 나지 않는다는 공통점이 있다.
 *
 * <ul>
 *   <li>{@code parent_comment_id}는 V0에 이미 있는 NULL 허용 컬럼이다. INSERT에 끼워 넣어도
 *       그냥 저장되고, 화면에 나올 방법이 없는 데이터가 조용히 쌓인다. 2차 대댓글 작업 때
 *       "언제 어떤 규칙으로 들어간 값인지" 모르는 행으로 남는다.
 *   <li>댓글 수정은 만들면 그대로 동작한다. 범위 밖인 것은 기능이 어려워서가 아니라
 *       <b>정하지 않았기 때문</b>이고, 정하지 않은 채 생긴 기능은 규칙 없이 굳는다.
 * </ul>
 *
 * <p>DB 제약으로 막지 않는 것은 의도한 것이다. 2차에 정식으로 대댓글을 구현할 예정이라
 * 곧 떼야 할 제약을 걸지 않는다(6.4). 그 자리를 이 테스트가 대신한다.
 *
 * <p>시드 파일 쪽은 {@code CommunitySeedTests}가 같은 규칙을 따로 고정한다.
 */
class CommunityCommentScopeTests {

    private static final Path JAVA_ROOT =
            Path.of("src", "main", "java", "com", "cakeshop", "domain", "community");
    private static final Path MAPPER_XML =
            Path.of("src", "main", "resources", "mapper", "community", "CommunityMapper.xml");
    private static final List<Path> TEMPLATE_DIRECTORIES = List.of(
            Path.of("src", "main", "resources", "templates", "customer", "community"),
            Path.of("src", "main", "resources", "templates", "admin", "community"));

    /**
     * 대댓글 식별자가 코드 어디에도 없는지 확인한다.
     *
     * <p>주석은 걷어내고 본다. 규칙을 설명하는 주석("여기에는 두지 않는다")이 그 자체로
     * 위반으로 잡히면, 다음 사람은 설명을 지워서 초록불을 만든다.
     */
    @Test
    void communitySources_doNotMentionParentCommentId() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path source : scannedSources()) {
            String code = strippedOf(source);

            if (code.contains("PARENT_COMMENT_ID") || code.contains("PARENTCOMMENTID")) {
                violations.add(source.toString());
            }
        }

        assertThat(violations)
                .as("1차에 대댓글은 없다. parent_comment_id를 코드에 등장시키지 않는다"
                        + " (DOMAIN.md 6.4)")
                .isEmpty();
    }

    /**
     * 댓글 수정 경로가 생기지 않았는지 확인한다.
     *
     * <p>댓글은 작성·삭제만이다(DOMAIN.md 6.4). 삭제는 {@code SET STATUS = 'DELETED'}이므로
     * 본문을 바꾸는 UPDATE가 있다면 그것은 수정 기능이다.
     */
    @Test
    void commentSql_hasNoUpdatePathForContent() throws IOException {
        String sql = strippedOf(MAPPER_XML);

        assertThat(sql)
                .as("댓글에는 수정이 없다(DOMAIN.md 6.4). 규칙을 바꾸려면 문서를 먼저 고친다")
                .doesNotContain("UPDATE COMMENTS SET CONTENT");
    }

    private List<Path> scannedSources() throws IOException {
        List<Path> sources = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(JAVA_ROOT)) {
            sources.addAll(paths.filter(path -> path.toString().endsWith(".java")).sorted().toList());
        }

        assertThat(sources).as("커뮤니티 자바 소스를 하나도 못 읽었다면 경로가 바뀐 것이다").isNotEmpty();

        assertThat(MAPPER_XML).as("매퍼 XML이 있어야 한다").exists();
        sources.add(MAPPER_XML);

        for (Path directory : TEMPLATE_DIRECTORIES) {
            try (Stream<Path> paths = Files.list(directory)) {
                sources.addAll(
                        paths.filter(path -> path.toString().endsWith(".html")).sorted().toList());
            }
        }

        return sources;
    }

    /** 파일에서 주석을 걷어내고 대문자로 바꾼다. 확장자에 따라 주석 문법이 다르다. */
    private String strippedOf(Path source) throws IOException {
        String text = Files.readString(source, StandardCharsets.UTF_8);

        String withoutComments = source.toString().endsWith(".java")
                ? withoutJavaComments(text)
                : text.replaceAll("(?s)<!--.*?-->", " ");

        return withoutComments.toUpperCase();
    }

    /**
     * 자바 주석만 걷어낸다. 문자열·문자 리터럴 안은 건드리지 않는다.
     *
     * <p>정규식으로 {@code //}부터 줄 끝까지를 지우면 {@code "https://..."} 같은 리터럴이
     * 줄 나머지를 통째로 먹는다. 그렇게 비는 자리는 <b>실패가 아니라 통과의 모습</b>으로
     * 나타나서 눈에 띄지 않는다.
     */
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

    /** 여는 따옴표에서 닫는 따옴표 다음까지. 역슬래시 이스케이프를 건너뛴다. */
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
