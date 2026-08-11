package com.cakeshop.domain.community;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** community_conventions.md에서 정한 로컬 DTO 경계를 고정한다. */
class CommunityDtoBoundaryTests {

    private static final Path DTO_ROOT = Path.of(
            "src", "main", "java", "com", "cakeshop", "domain", "community", "dto");

    @Test
    void viewPackage_containsNoMapperInternalRows() throws IOException {
        List<String> fileNames = javaFiles(DTO_ROOT.resolve("view")).stream()
                .map(path -> path.getFileName().toString())
                .toList();

        assertThat(fileNames)
                .as("dto/view에는 Mapper 내부 Row·잠금·집계 결과를 두지 않는다")
                .noneMatch(name -> name.endsWith("Row.java"))
                .doesNotContain("PostLockView.java", "CommentCountView.java");
    }

    @Test
    void queryPackage_doesNotDependOnViewPackage() throws IOException {
        List<Path> violations = javaFiles(DTO_ROOT.resolve("query")).stream()
                .filter(this::referencesViewPackage)
                .toList();

        assertThat(violations)
                .as("Query DTO는 화면 View를 알지 않는다")
                .isEmpty();
    }

    private List<Path> javaFiles(Path directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    private boolean referencesViewPackage(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8)
                    .contains("com.cakeshop.domain.community.dto.view");
        } catch (IOException e) {
            throw new IllegalStateException("DTO 소스를 읽지 못했습니다: " + path, e);
        }
    }
}
