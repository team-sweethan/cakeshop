package com.cakeshop.domain.community;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
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
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 화면 명세 문서(`docs/community/SCREENS.md`)가 실제 화면과 어긋나지 않는지 확인한다.
 *
 * <p>문서는 그냥 두면 낡는다. 문구를 바꾸고 문서를 안 고쳐도 아무 일도 일어나지 않기 때문이다.
 * 그러면 다음 작업자가 문서를 믿고 잘못된 전제로 작업한다. 여기서 문서를 코드에 묶어,
 * <b>어긋나면 빌드가 깨지게</b> 만든다.
 *
 * <p>검사 방향은 문서 → 코드 한쪽뿐이다. 템플릿에 새 블록을 넣고 문서에 적지 않는 것은
 * 잡지 못한다. 그 한계는 SCREENS.md와 PLAN.md의 R7에 적어 두었다.
 */
class CommunityScreenDocTests {

    private static final Path SCREEN_DOC = Path.of("docs", "community", "SCREENS.md");
    private static final Path TEST_SOURCE_ROOT = Path.of("src", "test", "java");
    private static final List<String> TEMPLATE_DIRECTORIES =
            List.of("customer/community", "admin/community");
    private static final String IMPLEMENTED = "구현됨";
    private static final String MOCKUP = "목업";
    private static final String PLANNED = "계획";
    private static final String UNDECIDED = "미정";

    /** 문서가 화면 하나를 통째로 빠뜨리면, 그 화면은 아무 규칙도 없이 방치된다. */
    @Test
    void screenDoc_documentsEveryCommunityTemplate() throws IOException {
        Set<String> documented = new LinkedHashSet<>();

        for (Screen screen : screens()) {
            if (!screen.isPlanned()) {
                documented.add(screen.template());
            }
        }

        assertThat(documented)
                .as("SCREENS.md의 화면 목록이 실제 템플릿 파일과 같아야 한다")
                .containsExactlyInAnyOrderElementsOf(templates().keySet());
    }

    /**
     * `계획` 상태로 적은 화면은 아직 템플릿이 없어야 한다.
     *
     * <p>만들고 나서 상태 표기를 안 지우면, 문서는 "아직 없는 화면"이라고 말하는데 실제로는
     * 존재하는 상태가 된다. 그러면 위 목록 검사도 그 화면을 그냥 지나친다.
     */
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

    /**
     * 아직 없는 화면의 문구를 적어 두지 못하게 막는다.
     *
     * <p>템플릿이 `미정`인 계획 화면은 문자열을 대조할 상대가 없다. 그런 표를 허용하면
     * 검사에서 조용히 빠지면서 문서에는 "고정했다"고 남는다. 화면을 만들 때 `- 상태:`를
     * 먼저 바꾸게 하는 것이 이 검사의 목적이다.
     */
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

    /** 문구를 바꾸고 문서를 안 고치면 여기서 걸린다. */
    @Test
    void screenDoc_documentedStringsExistInTemplate() throws IOException {
        List<ScreenString> strings = documentedStrings();

        assertThat(strings)
                .as("SCREENS.md에서 화면 문자열을 하나도 못 읽었다면 표 형식이 깨진 것이다")
                .isNotEmpty();

        Map<String, String> templates = templates();

        for (ScreenString screenString : strings) {
            assertThat(templates.get(screenString.template()))
                    .as("%s에 `%s`가 없다. 문구를 바꿨다면 SCREENS.md도 함께 고친다",
                            screenString.template(), screenString.value())
                    .contains(screenString.value());
        }
    }

    /**
     * 문서가 "이 테스트가 지킨다"고 적은 테스트가 실재하는지 확인한다.
     *
     * <p>이 검사가 없으면 문서가 방어되지 않는 항목을 방어된다고 주장할 수 있다. 그러면
     * 하네스가 있다고 믿는 만큼 더 위험해진다.
     */
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

    /**
     * 문서가 연결한 테스트가 <b>그 문구를 실제로 확인하는지</b> 본다.
     *
     * <p>실행되는 테스트인지만 보면 부족하다. 살아 있는 테스트를 아무렇게나 연결해도 통과하기
     * 때문이다. 실제로 그런 줄이 있었다 — 목록의 `좋아요`와 `조회`가
     * {@code communityList_rendersPostRow}에 걸려 있었지만 그 메서드는 두 문구를 전혀
     * assert하지 않았다. 그 span에 {@code th:if="${false}"}만 붙이면 문구도 테스트도 그대로라
     * 하네스는 전부 통과하는데 사용자는 두 값을 볼 수 없다.
     *
     * <p>그래서 참조된 테스트 <b>본문</b>에 그 문구가 등장하는지까지 확인한다. 한 문구를 여러
     * 테스트가 다른 층위에서 받칠 수 있으므로 <b>하나라도</b> 확인하면 통과로 본다.
     */
    @Test
    void screenDoc_referencedTestsAssertTheDocumentedString() throws Exception {
        List<DocRow> rows = documentedRows().stream()
                .filter(row -> !row.screen().isPlanned() && !row.references().isEmpty())
                .toList();

        assertThat(rows)
                .as("테스트가 연결된 문자열 행을 하나도 못 읽었다면 표 형식이 깨진 것이다")
                .isNotEmpty();

        for (DocRow row : rows) {
            List<String> bodies = new ArrayList<>();

            for (String reference : row.references()) {
                bodies.add(testMethodBody(reference));
            }

            assertThat(bodies)
                    .as("`%s`를 %s가 확인하지 않는다. 그 문구를 실제로 assert하는 줄을 넣거나,"
                                    + " 고정하지 않았다면 `없음`으로 적는다",
                            row.value(), row.references())
                    .anyMatch(body -> body.contains(row.value()));
        }
    }

    /**
     * 참조된 테스트 메서드의 본문을 소스에서 잘라 온다.
     *
     * <p>클래스 전체를 보면 다른 메서드가 우연히 같은 문구를 쓰는 것만으로 통과하므로
     * 메서드 하나로 좁힌다. 주석은 걷어낸다 — 문구를 <b>설명</b>만 하고 확인하지 않는 것이
     * 정확히 이 검사가 막으려는 상태다.
     */
    private String testMethodBody(String reference) throws IOException {
        int separator = reference.lastIndexOf('.');
        String source = Files.readString(
                sourceOf(reference.substring(0, separator)), StandardCharsets.UTF_8);
        String methodName = reference.substring(separator + 1);

        int start = source.indexOf(" " + methodName + "(");

        assertThat(start).as("소스에서 %s를 찾지 못했다", reference).isNotNegative();

        int open = source.indexOf('{', start);
        int depth = 0;

        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);

            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return stripComments(source.substring(open + 1, i));
            }
        }

        throw new IllegalStateException("메서드 본문이 닫히지 않았다: " + reference);
    }

    private String stripComments(String body) {
        return body.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    /**
     * 문서가 가리키는 것이 <b>실제로 실행되는</b> 테스트인지 확인한다.
     *
     * <p>소스에서 {@code void 이름(} 문자열만 찾으면 {@code @Test}를 떼거나
     * {@code @Disabled}를 붙여도 그대로 통과한다. 그러면 문서는 실행되지 않는 테스트가
     * 화면을 지킨다고 주장하게 된다 — 하네스가 있다고 믿는 만큼 더 위험하다. 그래서
     * 리플렉션으로 애너테이션까지 확인한다.
     */
    private void assertRunnableTest(String className, String methodName, String reference)
            throws Exception {
        Class<?> testClass = Class.forName(qualifiedNameOf(className));

        assertThat(testClass.isAnnotationPresent(Disabled.class))
                .as("%s가 속한 클래스가 @Disabled 상태다", reference)
                .isFalse();

        List<Method> methods = Stream.of(testClass.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .toList();

        assertThat(methods).as("SCREENS.md가 가리키는 %s가 없다", reference).isNotEmpty();

        assertThat(methods)
                .as("%s가 JUnit이 실행하는 테스트가 아니다 (@Test 없음 또는 @Disabled)",
                        reference)
                .anyMatch(method ->
                        (method.isAnnotationPresent(Test.class)
                                || method.isAnnotationPresent(ParameterizedTest.class))
                                && !method.isAnnotationPresent(Disabled.class));
    }

    /** 테스트 소스 파일을 찾아 경로에서 패키지를 되돌린다. */
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

    /** 문서에서 화면 절을 읽는다. 각 절은 `- 상태:`와 `- 템플릿:` 줄로 자신을 밝힌다. */
    private List<Screen> screens() throws IOException {
        List<Screen> screens = new ArrayList<>();
        String status = null;

        for (String line : readScreenDoc()) {
            String parsedStatus = valueOf(line, "- 상태: ");

            if (parsedStatus != null) {
                // "구현됨 (조각 1)"처럼 뒤에 근거가 붙는다.
                status = parsedStatus.split("\\s+")[0];
                continue;
            }

            String template = valueOf(line, "- 템플릿: ");

            if (template != null) {
                assertThat(status)
                        .as("%s 앞에 `- 상태:` 줄이 있어야 한다", template)
                        .isNotNull();

                screens.add(new Screen(status, template));
                status = null;
            }
        }

        return screens;
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

    /**
     * 화면에 실제로 나오지 않는 부분을 걷어낸다.
     *
     * <p>둘 다 템플릿 원문에는 있지만 사용자는 보지 못한다.
     *
     * <ul>
     *   <li>HTML 주석 — 규칙을 설명하는 한국어 주석이 있어서, 남겨 두면 주석에만 있는
     *       문구를 문서가 "화면 문구"라고 주장해도 통과한다.
     *   <li>{@code th:text}/{@code th:utext} 요소의 본문 — 표현식이 덮어쓰는 자리 표시다.
     *       {@code <span th:text="'좋아요 ' + ...">좋아요 0</span>}에서 표현식만
     *       {@code '추천 '}으로 바꾸면 화면은 바뀌는데 자리 표시가 남아 검사가 통과한다.
     * </ul>
     */
    private String visibleMarkup(String template) {
        String withoutComments = template.replaceAll("(?s)<!--.*?-->", " ");

        // 여는 태그(th:text 부터 '>' 까지)는 남기고 그 뒤 본문만 지운다.
        return withoutComments.replaceAll("(?s)(th:u?text=\"[^\"]*\"[^>]*>)[^<]*", "$1");
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

    /**
     * 문자열 표의 각 행을 (템플릿, 문구, 고정한 테스트들)로 읽는다.
     *
     * <p>마지막 칸에는 테스트를 쉼표로 여러 개 적을 수 있다. 한 문구를 서로 다른 층위에서
     * 받치는 경우가 있어서다 — 예를 들어 `(수정됨)`은 렌더링 테스트가 표시를, 매퍼 테스트가
     * 그 표시를 켜는 조건을 지킨다.
     */
    private List<DocRow> documentedRows() throws IOException {
        List<DocRow> rows = new ArrayList<>();

        forEachStringRow((screen, cells) -> {
            List<String> references = Stream.of(cells.get(cells.size() - 1).split(","))
                    .map(this::unquote)
                    // "없음"은 아직 테스트로 고정하지 않았다는 정직한 표기다.
                    .filter(reference -> reference.contains("."))
                    .toList();

            rows.add(new DocRow(screen, unquote(cells.get(0)), references));
        });

        return rows;
    }

    /**
     * 첫 열이 `문자열`인 표의 각 행을, 그 표가 속한 화면의 템플릿과 함께 넘긴다.
     *
     * <p>모델 표나 상태 표까지 읽으면 "PUBLISHED" 같은 값이 템플릿에 있는지 찾게 되므로
     * 표 머리글로 구분한다.
     */
    private void forEachStringRow(RowConsumer consumer) throws IOException {
        List<String> lines = readScreenDoc();
        List<Screen> screens = screens();
        int screenIndex = -1;
        boolean inStringTable = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            if (valueOf(line, "- 템플릿: ") != null) {
                screenIndex++;
                continue;
            }

            if (!line.startsWith("|")) {
                inStringTable = false;
                continue;
            }

            if (isSeparatorRow(line)) {
                inStringTable = i > 0 && "문자열".equals(cellsOf(lines.get(i - 1)).get(0));
                continue;
            }

            if (inStringTable && screenIndex >= 0) {
                consumer.accept(screens.get(screenIndex), cellsOf(line));
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

    /** 표에서는 문자열을 백틱으로 감싸 공백과 기호가 잘리지 않게 한다. */
    private String unquote(String cell) {
        String trimmed = cell.trim();

        if (trimmed.startsWith("`") && trimmed.endsWith("`") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1);
        }

        return trimmed;
    }

    private List<String> readScreenDoc() throws IOException {
        assertThat(SCREEN_DOC)
                .as("화면 명세가 있어야 한다. 없다면 이 테스트의 전제가 사라진 것이다")
                .exists();

        return Files.readAllLines(SCREEN_DOC, StandardCharsets.UTF_8);
    }

    private record Screen(String status, String template) {

        private boolean isPlanned() {
            return PLANNED.equals(status);
        }
    }

    private record ScreenString(String template, String value) {
    }

    private record DocRow(Screen screen, String value, List<String> references) {
    }

    @FunctionalInterface
    private interface RowConsumer {
        void accept(Screen screen, List<String> cells);
    }
}
