package com.cakeshop.domain.community;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** 1차 댓글의 SQL 범위를 확인한다. */
class CommunityCommentScopeTests {

    private static final Path MAPPER_DIRECTORY =
            Path.of("src", "main", "resources", "mapper", "community");
    private static final Pattern UPDATE_STATEMENT =
            Pattern.compile("(?is)<update\\b.*?</update>");

    @Test
    void communitySources_doNotMentionParentCommentId() throws IOException {
        for (Path mapperXml : mapperXmlFiles()) {
            assertThat(read(mapperXml))
                    .as("1차 댓글 SQL에는 parent_comment_id가 없어야 한다: " + mapperXml)
                    .doesNotContainIgnoringCase("parent_comment_id");
        }
    }

    @Test
    void commentSql_hasNoUpdatePathForContent() throws IOException {
        for (Path mapperXml : mapperXmlFiles()) {
            Matcher updates = UPDATE_STATEMENT.matcher(read(mapperXml));

            while (updates.find()) {
                String sql = updates.group().replaceAll("\\s+", " ").toUpperCase();

                if (sql.contains("UPDATE COMMENTS")) {
                    assertThat(sql)
                            .as("댓글 UPDATE는 content를 변경하지 않아야 한다: " + mapperXml)
                            .doesNotMatch("(?s).*\\bCONTENT\\s*=.*");
                }
            }
        }
    }

    private List<Path> mapperXmlFiles() throws IOException {
        List<Path> mappers;

        try (Stream<Path> paths = Files.list(MAPPER_DIRECTORY)) {
            mappers = paths.filter(path -> path.toString().endsWith(".xml")).sorted().toList();
        }

        assertThat(mappers).as("커뮤니티 Mapper XML이 있어야 한다").isNotEmpty();
        return mappers;
    }

    private String read(Path source) throws IOException {
        return Files.readString(source, StandardCharsets.UTF_8);
    }
}
