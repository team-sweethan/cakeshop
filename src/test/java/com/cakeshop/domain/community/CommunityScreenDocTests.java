package com.cakeshop.domain.community;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** 화면 명세, 템플릿, 렌더링 테스트의 일치 여부를 검증한다. */
class CommunityScreenDocTests {

    private static final Path SCREEN_DOC_DIRECTORY = Path.of("docs", "community", "screens");
    private static final Path SCREEN_INDEX = Path.of("docs", "community", "SCREENS.md");
    private static final Path TEST_SOURCE_ROOT = Path.of("src", "test", "java");
    /** 화면 인덱스 표의 정확한 머리글이다. */
    private static final List<String> INDEX_HEADER = List.of("화면", "주소", "상태", "조각", "파일");
    /** 마크다운 링크의 대상 경로를 찾는다. */
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^\\]]*\\]\\(([^)]+)\\)");
    /** 명세 제목 끝의 화면 주소를 찾는다. */
    private static final Pattern HEADING_ADDRESS = Pattern.compile("^#\\s+.*`([^`]+)`\\s*$");
    private static final List<String> TEMPLATE_DIRECTORIES =
            List.of("customer/community", "admin/community");
    private static final String IMPLEMENTED = "구현됨";
    private static final String MOCKUP = "목업";
    private static final String PLANNED = "계획";
    private static final String UNDECIDED = "미정";
    private static final String CONDITION_PACKAGE = "org.junit.jupiter.api.condition";
    /** 부정형을 제외한 Hamcrest 문구 단언을 찾는다. */
    private static final Pattern CONTAINS_STRING = Pattern.compile(
            "((?:[\\w.]+\\s*\\.\\s*)?not\\s*\\(\\s*)?"
                    + "(?:[\\w.]+\\s*\\.\\s*)?"
                    + "containsString\\s*\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\)");
    /** AssertJ의 긍정형 문구 단언을 찾는다. */
    private static final Pattern ASSERTJ_CONTAINS =
            Pattern.compile("\\.\\s*contains\\s*\\(([^()]*)\\)");
    /** 인자 목록의 문자열 리터럴을 찾는다. */
    private static final Pattern STRING_LITERAL =
            Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** 모든 커뮤니티 템플릿이 문서화됐는지 확인한다. */
    @Test
    void screenDoc_documentsEveryCommunityTemplate() throws IOException {
        Set<String> documented = new LinkedHashSet<>();

        for (Screen screen : screens()) {
            if (!screen.isPlanned()) {
                documented.add(screen.template());
            }
        }

        assertThat(documented)
                .as("screens/*.md의 화면 목록이 실제 템플릿 파일과 같아야 한다")
                .containsExactlyInAnyOrderElementsOf(templates().keySet());
    }

    /** 화면 인덱스와 명세의 파일, 상태, 주소를 비교한다. */
    @Test
    void screenIndex_matchesScreenDocs() throws IOException {
        List<IndexRow> rows = indexRows();

        assertThat(rows)
                .as("SCREENS.md의 인덱스 표를 못 읽었다면 머리글이 %s가 아닌 것이다", INDEX_HEADER)
                .isNotEmpty();

        assertThat(rows.stream().map(IndexRow::doc).toList())
                .as("인덱스 표와 screens/ 의 파일 목록이 같아야 한다."
                        + " 화면을 추가했다면 SCREENS.md에도 줄을 더한다")
                .containsExactlyInAnyOrderElementsOf(screenDocs());

        for (IndexRow row : rows) {
            assertThat(row.status())
                    .as("%s의 상태가 인덱스와 다르다. 정본은 명세 파일의 `- 상태:` 줄이다",
                            row.doc())
                    .isEqualTo(screenOf(row.doc()).status());

            assertThat(headingAddressOf(row.doc()))
                    .as("인덱스가 %s의 주소를 `%s`라고 적었는데 그 파일의 제목은 다르다",
                            row.doc(), row.address())
                    .isEqualTo(row.address());
        }
    }

    /** 계획 상태 화면에는 템플릿이 없어야 한다. */
    @Test
    void screenDoc_plannedScreensHaveNoTemplateYet() throws IOException {
        Map<String, String> templates = templates();
        List<Screen> screens = screens();

        assertThat(screens)
                .as("화면 절을 하나도 못 읽었다면 문서 형식이 깨진 것이다")
                .isNotEmpty();

        for (Screen screen : screens) {
            assertThat(screen.status())
                    .as("`- 상태:`는 정해진 세 값 중 하나여야 한다 (%s)", screen.template())
                    .isIn(IMPLEMENTED, MOCKUP, PLANNED);

            if (screen.isPlanned() && !UNDECIDED.equals(screen.template())) {
                assertThat(templates)
                        .as("%s는 `계획`으로 적혀 있는데 템플릿이 이미 있다. 상태 표기를 고친다",
                                screen.template())
                        .doesNotContainKey(screen.template());
            }
        }
    }

    /** 계획 상태 화면에는 문자열 표가 없어야 한다. */
    @Test
    void screenDoc_plannedScreensHaveNoStringTable() throws IOException {
        List<String> violations = new ArrayList<>();

        forEachStringRow((screen, cells) -> {
            if (screen.isPlanned()) {
                violations.add(screen.template() + " — " + unquote(cells.get(0)));
            }
        });

        assertThat(violations)
                .as("`계획` 화면에는 문자열 표를 두지 않는다. 만들면서 상태를 바꾸고 표를 채운다")
                .isEmpty();
    }

    /** 문서의 화면 문구가 템플릿에 존재하는지 확인한다. */
    @Test
    void screenDoc_documentedStringsExistInTemplate() throws IOException {
        List<ScreenString> strings = documentedStrings();

        assertThat(strings)
                .as("screens/*.md에서 화면 문자열을 하나도 못 읽었다면 표 형식이 깨진 것이다")
                .isNotEmpty();

        Map<String, String> templates = templates();

        for (ScreenString screenString : strings) {
            assertThat(templates.get(screenString.template()))
                    .as("%s에 `%s`가 없다. 문구를 바꿨다면 화면 명세도 함께 고친다",
                            screenString.template(), screenString.value())
                    .contains(screenString.value());
        }
    }

    /** 문서가 참조한 테스트가 실제로 실행되는지 확인한다. */
    @Test
    void screenDoc_referencedTestsExist() throws Exception {
        List<String> references = documentedTestReferences();

        assertThat(references)
                .as("고정한 테스트 칸을 하나도 못 읽었다면 표 형식이 깨진 것이다")
                .isNotEmpty();

        for (String reference : references) {
            int separator = reference.lastIndexOf('.');
            String className = reference.substring(0, separator);
            String methodName = reference.substring(separator + 1);

            assertRunnableTest(className, methodName, reference);
        }
    }

    /** 참조한 테스트가 문서의 화면 문구를 긍정 단언하는지 확인한다. */
    @Test
    void screenDoc_referencedTestsAssertTheDocumentedString() throws Exception {
        List<DocRow> rows = documentedRows().stream()
                .filter(row -> !row.screen().isPlanned() && !row.references().isEmpty())
                .toList();

        assertThat(rows)
                .as("테스트가 연결된 문자열 행을 하나도 못 읽었다면 표 형식이 깨진 것이다")
                .isNotEmpty();

        for (DocRow row : rows) {
            List<String> asserted = new ArrayList<>();

            for (String reference : row.references()) {
                asserted.addAll(assertedStrings(testMethodBody(reference)));
            }

            assertThat(asserted)
                    .as("`%s`가 응답에 있다고 %s가 단언하지 않는다."
                                    + " `containsString(\"...\")`이나 `.contains(\"...\")`으로"
                                    + " 확인하는 줄을 넣거나,"
                                    + " 고정하지 않았다면 `없음`으로 적는다",
                            row.value(), row.references())
                    .anyMatch(value -> value.contains(row.value()));
        }
    }

    /** 메서드 본문의 긍정형 문자열 단언을 모은다. */
    private List<String> assertedStrings(String body) {
        List<String> values = new ArrayList<>();
        Matcher matcher = CONTAINS_STRING.matcher(body);

        while (matcher.find()) {
            if (matcher.group(1) == null) {
                values.add(matcher.group(2));
            }
        }

        Matcher assertJ = ASSERTJ_CONTAINS.matcher(body);

        while (assertJ.find()) {
            Matcher literal = STRING_LITERAL.matcher(assertJ.group(1));

            while (literal.find()) {
                values.add(literal.group(1));
            }
        }

        return values;
    }

    /** 참조된 테스트 메서드의 본문만 읽는다. */
    private String testMethodBody(String reference) throws IOException {
        int separator = reference.lastIndexOf('.');
        String source = Files.readString(
                sourceOf(reference.substring(0, separator)), StandardCharsets.UTF_8);
        String methodName = reference.substring(separator + 1);

        int start = source.indexOf(" " + methodName + "(");

        assertThat(start).as("소스에서 %s를 찾지 못했다", reference).isNotNegative();

        return bodyFrom(source, source.indexOf('{', start), reference);
    }

    /** 리터럴과 주석을 제외하고 메서드 블록 끝을 찾는다. */
    private String bodyFrom(String source, int open, String reference) {
        StringBuilder body = new StringBuilder();
        int depth = 0;
        int i = open;

        while (i < source.length()) {
            if (source.startsWith("//", i)) {
                int end = source.indexOf('\n', i);
                i = end < 0 ? source.length() : end;
                body.append(' ');
            } else if (source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2);
                i = end < 0 ? source.length() : end + 2;
                body.append(' ');
            } else if (source.startsWith("\"\"\"", i)) {
                int end = source.indexOf("\"\"\"", i + 3);
                int next = end < 0 ? source.length() : end + 3;
                body.append(source, i, next);
                i = next;
            } else if (source.charAt(i) == '"' || source.charAt(i) == '\'') {
                int end = endOfLiteral(source, i);
                body.append(source, i, end);
                i = end;
            } else {
                char c = source.charAt(i);

                if (c == '{') {
                    depth++;
                } else if (c == '}' && --depth == 0) {
                    // 여는 중괄호를 센다.
                    return body.substring(1);
                }

                body.append(c);
                i++;
            }
        }

        throw new IllegalStateException("메서드 본문이 닫히지 않았다: " + reference);
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

    /** 참조된 메서드가 비활성화되지 않은 JUnit 테스트인지 확인한다. */
    private void assertRunnableTest(String className, String methodName, String reference)
            throws Exception {
        Class<?> testClass = Class.forName(qualifiedNameOf(className));

        assertThat(conditionalOff(testClass.getAnnotations()))
                .as("%s가 속한 클래스가 꺼져 있다", reference)
                .isEmpty();

        List<Method> methods = Stream.of(testClass.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .toList();

        assertThat(methods).as("화면 명세가 가리키는 %s가 없다", reference).isNotEmpty();

        assertThat(methods)
                .as("%s가 JUnit이 실행하는 테스트가 아니다"
                                + " (@Test 없음, 또는 @Disabled·조건부 비활성화)",
                        reference)
                .anyMatch(method ->
                        (method.isAnnotationPresent(Test.class)
                                || method.isAnnotationPresent(ParameterizedTest.class))
                                && conditionalOff(method.getAnnotations()).isEmpty());
    }

    /** 테스트를 비활성화할 수 있는 애너테이션을 찾는다. */
    private List<String> conditionalOff(Annotation[] annotations) {
        return Stream.of(annotations)
                .map(Annotation::annotationType)
                .filter(type -> type.equals(Disabled.class)
                        || type.getPackageName().equals(CONDITION_PACKAGE))
                .map(Class::getSimpleName)
                .toList();
    }

    /** 테스트 소스 경로에서 전체 클래스 이름을 구한다. */
    private String qualifiedNameOf(String className) throws IOException {
        String relative = TEST_SOURCE_ROOT.relativize(sourceOf(className)).toString();

        return relative
                .substring(0, relative.length() - ".java".length())
                .replace('\\', '.')
                .replace('/', '.');
    }

    private Path sourceOf(String className) throws IOException {
        try (Stream<Path> paths = Files.walk(TEST_SOURCE_ROOT)) {
            return paths
                    .filter(path -> path.getFileName().toString().equals(className + ".java"))
                    .findFirst()
                    .orElseThrow(() ->
                            new IllegalStateException("테스트 클래스를 찾을 수 없다: " + className));
        }
    }

    private List<Screen> screens() throws IOException {
        List<Screen> screens = new ArrayList<>();

        for (Path doc : screenDocs()) {
            screens.add(screenOf(doc));
        }

        return screens;
    }

    /** 명세 파일에서 하나의 화면 상태와 템플릿을 읽는다. */
    private Screen screenOf(Path doc) throws IOException {
        List<Screen> found = new ArrayList<>();
        String status = null;

        for (String line : Files.readAllLines(doc, StandardCharsets.UTF_8)) {
            String parsedStatus = valueOf(line, "- 상태: ");

            if (parsedStatus != null) {
                // 상태 뒤의 근거 표기는 제외한다.
                status = parsedStatus.split("\\s+")[0];
                continue;
            }

            String template = valueOf(line, "- 템플릿: ");

            if (template != null) {
                assertThat(status)
                        .as("%s의 %s 앞에 `- 상태:` 줄이 있어야 한다", doc, template)
                        .isNotNull();

                found.add(new Screen(status, template));
                status = null;
            }
        }

        assertThat(found)
                .as("화면 명세 파일 하나에는 화면을 정확히 하나만 적는다 (%s)", doc)
                .hasSize(1);

        return found.get(0);
    }

    /** 화면 인덱스 표의 행과 링크 대상을 읽는다. */
    private List<IndexRow> indexRows() throws IOException {
        assertThat(SCREEN_INDEX)
                .as("화면 명세 인덱스가 있어야 한다. 없다면 이 검사의 전제가 사라진 것이다")
                .exists();

        List<String> lines = Files.readAllLines(SCREEN_INDEX, StandardCharsets.UTF_8);
        List<IndexRow> rows = new ArrayList<>();
        boolean inIndexTable = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            if (!line.startsWith("|")) {
                inIndexTable = false;
                continue;
            }

            if (isSeparatorRow(line)) {
                inIndexTable = i > 0 && INDEX_HEADER.equals(cellsOf(lines.get(i - 1)));
                continue;
            }

            if (inIndexTable) {
                List<String> cells = cellsOf(line);

                rows.add(new IndexRow(
                        SCREEN_INDEX.getParent().resolve(linkTargetOf(cells.get(4))),
                        unquote(cells.get(1)),
                        unquote(cells.get(2))));
            }
        }

        return rows;
    }

    /** 명세 제목에서 화면 주소를 읽는다. */
    private String headingAddressOf(Path doc) throws IOException {
        for (String line : Files.readAllLines(doc, StandardCharsets.UTF_8)) {
            if (!line.startsWith("#")) {
                continue;
            }

            Matcher matcher = HEADING_ADDRESS.matcher(line.trim());

            assertThat(matcher.matches())
                    .as("%s의 제목은 `# 화면 이름 — `GET /주소`` 꼴이어야 한다: %s",
                            doc, line)
                    .isTrue();

            return matcher.group(1);
        }

        throw new IllegalStateException("제목 줄이 없다: " + doc);
    }

    /** 링크가 아니면 셀 전체를 경로로 사용한다. */
    private String linkTargetOf(String cell) {
        Matcher matcher = MARKDOWN_LINK.matcher(cell.trim());

        return matcher.find() ? matcher.group(1) : unquote(cell);
    }

    /** 명세 파일을 이름순으로 읽는다. */
    private List<Path> screenDocs() throws IOException {
        assertThat(SCREEN_DOC_DIRECTORY)
                .as("화면 명세 폴더가 있어야 한다. 없다면 이 테스트의 전제가 사라진 것이다")
                .isDirectory();

        try (Stream<Path> paths = Files.list(SCREEN_DOC_DIRECTORY)) {
            List<Path> docs = paths
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .toList();

            assertThat(docs).as("화면 명세 파일이 하나도 없다").isNotEmpty();

            return docs;
        }
    }

    private Map<String, String> templates() throws IOException {
        Map<String, String> templates = new LinkedHashMap<>();

        for (String directory : TEMPLATE_DIRECTORIES) {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:templates/" + directory + "/*.html");

            for (Resource resource : resources) {
                templates.put(
                        directory + "/" + resource.getFilename(),
                        visibleMarkup(new String(
                                resource.getInputStream().readAllBytes(),
                                StandardCharsets.UTF_8)));
            }
        }

        return templates;
    }

    /** HTML 주석과 표현식이 덮어쓰는 자리 표시자를 제거한다. */
    private String visibleMarkup(String template) {
        String withoutComments = template.replaceAll("(?s)<!--.*?-->", " ");

        // 여는 태그는 유지하고 표현식이 덮어쓰는 본문만 제거한다.
        return withoutComments.replaceAll(
                "(?s)(th:u?text=(?:\"[^\"]*\"|'[^']*')[^>]*>)[^<]*", "$1");
    }

    private List<ScreenString> documentedStrings() throws IOException {
        List<ScreenString> strings = new ArrayList<>();

        forEachStringRow((screen, cells) -> {
            if (!screen.isPlanned()) {
                strings.add(new ScreenString(screen.template(), unquote(cells.get(0))));
            }
        });

        return strings;
    }

    private List<String> documentedTestReferences() throws IOException {
        List<String> references = new ArrayList<>();

        for (DocRow row : documentedRows()) {
            references.addAll(row.references());
        }

        return references;
    }

    /** 문자열 표에서 문구와 연결된 테스트를 읽는다. */
    private List<DocRow> documentedRows() throws IOException {
        List<DocRow> rows = new ArrayList<>();

        forEachStringRow((screen, cells) -> {
            List<String> references = Stream.of(cells.get(cells.size() - 1).split(","))
                    .map(this::unquote)
                    // "없음"은 테스트 참조가 없음을 뜻한다.
                    .filter(reference -> reference.contains("."))
                    .toList();

            rows.add(new DocRow(screen, unquote(cells.get(0)), references));
        });

        return rows;
    }

    /** 문자열 표의 행만 화면 템플릿과 함께 전달한다. */
    private void forEachStringRow(RowConsumer consumer) throws IOException {
        for (Path doc : screenDocs()) {
            Screen screen = screenOf(doc);
            List<String> lines = Files.readAllLines(doc, StandardCharsets.UTF_8);
            boolean inStringTable = false;

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();

                if (!line.startsWith("|")) {
                    inStringTable = false;
                    continue;
                }

                if (isSeparatorRow(line)) {
                    inStringTable = i > 0 && "문자열".equals(cellsOf(lines.get(i - 1)).get(0));
                    continue;
                }

                if (inStringTable) {
                    consumer.accept(screen, cellsOf(line));
                }
            }
        }
    }

    private String valueOf(String line, String prefix) {
        String trimmed = line.trim();

        if (!trimmed.startsWith(prefix)) {
            return null;
        }

        return unquote(trimmed.substring(prefix.length()));
    }

    private boolean isSeparatorRow(String line) {
        return line.chars().allMatch(c -> c == '|' || c == '-' || c == ':' || c == ' ');
    }

    private List<String> cellsOf(String row) {
        String trimmed = row.trim();
        String inner = trimmed.substring(1, trimmed.length() - 1);

        return Stream.of(inner.split("\\|", -1)).map(String::trim).toList();
    }

    /** 표 셀을 감싼 백틱을 제거한다. */
    private String unquote(String cell) {
        String trimmed = cell.trim();

        if (trimmed.startsWith("`") && trimmed.endsWith("`") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1);
        }

        return trimmed;
    }

    private record Screen(String status, String template) {

        private boolean isPlanned() {
            return PLANNED.equals(status);
        }
    }

    private record ScreenString(String template, String value) {
    }

    private record IndexRow(Path doc, String address, String status) {
    }

    private record DocRow(Screen screen, String value, List<String> references) {
    }

    @FunctionalInterface
    private interface RowConsumer {
        void accept(Screen screen, List<String> cells);
    }
}
