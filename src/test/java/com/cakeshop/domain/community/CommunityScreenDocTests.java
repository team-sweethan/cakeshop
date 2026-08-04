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

/**
 * 화면 명세 문서(`docs/community/screens/*.md`)가 실제 화면과 어긋나지 않는지 확인한다.
 *
 * <p>문서는 그냥 두면 낡는다. 문구를 바꾸고 문서를 안 고쳐도 아무 일도 일어나지 않기 때문이다.
 * 그러면 다음 작업자가 문서를 믿고 잘못된 전제로 작업한다. 여기서 문서를 코드에 묶어,
 * <b>어긋나면 빌드가 깨지게</b> 만든다.
 *
 * <p>명세는 <b>화면 하나에 파일 하나</b>다. 그래서 문자열 표가 어느 화면 것인지는 표가 들어
 * 있는 파일이 정한다 — 문서 안의 줄 순서에 기대지 않는다. 순서에 기대면 절 사이에 표를 하나
 * 끼워 넣는 것만으로 검사 대상 템플릿이 옆 화면으로 밀리는데, 그 어긋남은 통과하는 모습으로
 * 나타나서 눈에 띄지 않는다. 규약은 `docs/community/SCREENS.md`에 적어 두었다.
 *
 * <p>검사 방향은 문서 → 코드 한쪽뿐이다. 템플릿에 새 블록을 넣고 문서에 적지 않는 것은
 * 잡지 못한다. 그 한계는 SCREENS.md와 PLAN.md의 R7에 적어 두었다.
 */
class CommunityScreenDocTests {

    private static final Path SCREEN_DOC_DIRECTORY = Path.of("docs", "community", "screens");
    private static final Path SCREEN_INDEX = Path.of("docs", "community", "SCREENS.md");
    private static final Path TEST_SOURCE_ROOT = Path.of("src", "test", "java");
    /**
     * 인덱스 표를 찾는 기준. 머리글이 <b>정확히</b> 이것인 표 하나만 읽는다.
     *
     * <p>첫 열이 `화면`인 것만 보면 "만들지 않는 화면" 표까지 걸린다. 그 표의 두 번째 열은
     * 산문이라 주소로 읽히고, 그러면 존재하지도 않는 명세 파일을 찾게 된다.
     */
    private static final List<String> INDEX_HEADER = List.of("화면", "주소", "상태", "조각", "파일");
    /** `[screens/list.md](screens/list.md)` 에서 링크가 가리키는 쪽을 꺼낸다. */
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^\\]]*\\]\\(([^)]+)\\)");
    /**
     * 명세 파일 제목(`# 목록 — &#96;GET /community&#96;`)에서 주소를 꺼낸다.
     *
     * <p>제목 줄의 <b>맨 끝</b> 백틱 묶음만 본다. 화면 이름에 백틱이 들어가도 주소가 밀리지
     * 않는다.
     */
    private static final Pattern HEADING_ADDRESS = Pattern.compile("^#\\s+.*`([^`]+)`\\s*$");
    private static final List<String> TEMPLATE_DIRECTORIES =
            List.of("customer/community", "admin/community");
    private static final String IMPLEMENTED = "구현됨";
    private static final String MOCKUP = "목업";
    private static final String PLANNED = "계획";
    private static final String UNDECIDED = "미정";
    private static final String CONDITION_PACKAGE = "org.junit.jupiter.api.condition";
    /**
     * `not(`으로 감싼 것과 맨몸을 구분하려고 앞자락을 함께 잡는다.
     *
     * <p>{@code Matchers.not(}처럼 한정한 호출도, 안쪽을 한정한
     * {@code Matchers.not(Matchers.containsString(...))}도 잡아야 한다. 정적 import를
     * 지우는 것만으로 부정 assertion이 긍정으로 집계되면, 문구가 <b>없다</b>고 검증하는
     * 테스트를 문서가 방어선으로 기록하게 된다. 공백도 흘려 보낸다 — 바이트코드가 같은
     * 수정이 검사 결과를 바꾸면 안 된다.
     *
     * <p>여기서 막는 것은 <b>표기법</b>이지 의미가 아니다. 이 검사가 어디까지 보증하는지는
     * SCREENS.md에 적어 두었다.
     */
    private static final Pattern CONTAINS_STRING = Pattern.compile(
            "((?:[\\w.]+\\s*\\.\\s*)?not\\s*\\(\\s*)?"
                    + "(?:[\\w.]+\\s*\\.\\s*)?"
                    + "containsString\\s*\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\)");
    /**
     * AssertJ의 {@code .contains("...")}도 "있다고 단언"으로 센다.
     *
     * <p>{@code assertThat(html).contains("커뮤니티")}는 Hamcrest
     * {@code containsString("커뮤니티")}와 뜻이 같은데, 표기만 다르다는 이유로 하네스가
     * 못 알아보면 <b>화면은 멀쩡한데 문서 검사가 깨진다</b>. 그건 문서가 낡은 것이 아니라
     * 하네스가 좁은 것이다. 검사를 무르게 하는 게 아니라 인식하는 표기를 넓힌다.
     *
     * <p>인자를 통째로 잡고 안에서 리터럴만 추린다. {@code contains("a", "b")}처럼 여러 개를
     * 한 번에 쓰는 것이 AssertJ에서는 흔하기 때문이다. 괄호가 중첩된 인자는 잡지 않는다 —
     * 그 경우는 기존처럼 못 알아보고, 통과시키는 쪽이 아니라 <b>실패하는</b> 쪽이다.
     *
     * <p>부정형 {@code doesNotContain(...)}은 "contains(" 꼴이 아니라서 애초에 걸리지 않는다.
     * 여기서도 막는 것은 <b>표기</b>지 의미가 아니다.
     */
    private static final Pattern ASSERTJ_CONTAINS =
            Pattern.compile("\\.\\s*contains\\s*\\(([^()]*)\\)");
    /** 인자 목록에서 문자열 리터럴만 추린다. */
    private static final Pattern STRING_LITERAL =
            Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

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
                .as("screens/*.md의 화면 목록이 실제 템플릿 파일과 같아야 한다")
                .containsExactlyInAnyOrderElementsOf(templates().keySet());
    }

    /**
     * SCREENS.md의 인덱스 표가 실제 명세 파일과 어긋나지 않는지 확인한다.
     *
     * <p>인덱스는 사람이 화면 목록을 훑는 유일한 자리다. 그런데 <b>같은 사실을 두 번</b>
     * 적어 둔 곳이기도 하다 — 상태와 주소는 각 명세 파일에도 있다. 두 벌로 적힌 것은
     * 한쪽만 고치면 조용히 갈라지고, 갈라진 인덱스는 "화면 목록"이라는 이유로 계속 읽힌다.
     *
     * <p>그래서 세 가지를 묶는다. 파일 목록이 양방향으로 같은가(화면을 추가하고 인덱스에
     * 안 적는 것이 여기서 걸린다), 상태 칸이 그 파일의 `- 상태:`와 같은가, 주소 칸이 그
     * 파일에 실제로 적혀 있는가.
     *
     * <p>`조각`과 `화면` 이름은 코드에 대응물이 없어 검사하지 않는다. 이 표에서 사람만
     * 읽는 칸은 그 둘뿐이다.
     */
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
     * <p>그래서 참조된 테스트가 그 문구를 <b>응답에 있다고 단언하는지</b>까지 확인한다. 본문에
     * 등장하기만 하는 것으로는 부족하다 — 입력 fixture로 쓰거나
     * {@code not(containsString(...))}으로 <b>없다고</b> 단언해도 등장은 하기 때문이다.
     * 그러면 화면에서 문구가 사라진 상태를 방어한다고 문서가 정반대로 주장하게 된다.
     * 그래서 {@code containsString("...")}과 {@code .contains("...")}의 인자만 세고,
     * {@code not(...)}으로 감싼 것은 뺀다.
     *
     * <p>한 문구를 여러 테스트가 다른 층위에서 받칠 수 있으므로 <b>하나라도</b> 확인하면
     * 통과로 본다.
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

    /**
     * 메서드 본문에서 <b>있다고 단언한</b> 문자열만 모은다.
     *
     * <p>Hamcrest {@code containsString("x")}와 AssertJ {@code .contains("x")} 두 표기를
     * 모두 센다. 같은 뜻인데 한쪽만 알아보면, 표기를 바꾼 것만으로 문서 검사가 깨진다.
     *
     * <p>{@code not(...)}으로 감싼 것은 뺀다. 정규식이 왼쪽부터 훑으므로
     * {@code not(containsString("x"))}는 첫 그룹이 잡히고, 맨몸
     * {@code containsString("x")}는 잡히지 않는다.
     */
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

        return bodyFrom(source, source.indexOf('{', start), reference);
    }

    /**
     * 여는 중괄호에서 짝이 맞는 닫는 중괄호까지를 잘라 낸다.
     *
     * <p>중괄호만 세면 안 된다. 문자열·문자·텍스트 블록 안의 {@code &#123;}와 {@code &#125;}는
     * Java 블록이 아니다. JSON이나 CSS를 담은 테스트에서 {@code "&#125;"} 하나가 본문을
     * 실제 끝보다 <b>일찍 자르고</b>(뒤쪽 assertion이 통째로 빠진다), {@code "&#123;"}는
     * 다음 메서드까지 본문으로 빨아들이거나 끝을 못 찾아 빌드를 세운다. 앞의 것은 조용히
     * 검사를 비게 만들어서 더 나쁘다.
     *
     * <p>주석은 공백으로 바꿔 함께 걷어낸다.
     */
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
                    // 첫 글자는 여는 중괄호다.
                    return body.substring(1);
                }

                body.append(c);
                i++;
            }
        }

        throw new IllegalStateException("메서드 본문이 닫히지 않았다: " + reference);
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

    /**
     * 문서가 가리키는 것이 <b>실제로 실행되는</b> 테스트인지 확인한다.
     *
     * <p>소스에서 {@code void 이름(} 문자열만 찾으면 {@code @Test}를 떼거나
     * {@code @Disabled}를 붙여도 그대로 통과한다. 그러면 문서는 실행되지 않는 테스트가
     * 화면을 지킨다고 주장하게 된다 — 하네스가 있다고 믿는 만큼 더 위험하다. 그래서
     * 리플렉션으로 애너테이션까지 확인한다.
     *
     * <p>{@code @Disabled}만으로는 부족하다. {@code @DisabledOnOs},
     * {@code @DisabledIfEnvironmentVariable}, {@code @EnabledOnOs}처럼 <b>조건부로</b>
     * 끄는 애너테이션이 붙으면 CI에서는 건너뛰는데 여기서는 통과한다. 그러면 문서가
     * <b>CI에서 실행되지 않는 테스트</b>를 방어선으로 기록한다. 조건이 어떻게 평가될지는
     * 환경에 달렸으므로, 이 자리에서 흉내 내지 않고 <b>전부 거절</b>한다. 화면을 무조건
     * 지켜야 하는 검사에 환경 조건을 다는 것 자체가 문서와 어긋나는 일이다.
     */
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

    /**
     * 실행을 막거나 조건에 걸 수 있는 애너테이션을 골라낸다.
     *
     * <p>{@code @Disabled}와 {@code org.junit.jupiter.api.condition} 패키지 전부다. 이름을
     * 하나씩 나열하면 JUnit이 새 조건을 추가할 때 조용히 구멍이 난다 — 그 구멍은 검사가
     * 통과하는 모습으로 나타나서 눈에 띄지 않는다.
     */
    private List<String> conditionalOff(Annotation[] annotations) {
        return Stream.of(annotations)
                .map(Annotation::annotationType)
                .filter(type -> type.equals(Disabled.class)
                        || type.getPackageName().equals(CONDITION_PACKAGE))
                .map(Class::getSimpleName)
                .toList();
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

    private List<Screen> screens() throws IOException {
        List<Screen> screens = new ArrayList<>();

        for (Path doc : screenDocs()) {
            screens.add(screenOf(doc));
        }

        return screens;
    }

    /**
     * 화면 명세 파일 하나에서 화면 하나를 읽는다. 화면은 `- 상태:`와 `- 템플릿:` 줄로
     * 자신을 밝힌다.
     *
     * <p>파일 하나에 화면이 <b>정확히 하나</b>여야 한다. 둘을 적으면 문자열 표를 어느 화면에
     * 붙일지가 다시 줄 순서 문제가 되고, 하나도 없으면 그 파일은 아무것도 검사하지 않으면서
     * 명세인 척 남는다. 둘 다 통과하는 모습으로 나타나므로 여기서 세운다.
     */
    private Screen screenOf(Path doc) throws IOException {
        List<Screen> found = new ArrayList<>();
        String status = null;

        for (String line : Files.readAllLines(doc, StandardCharsets.UTF_8)) {
            String parsedStatus = valueOf(line, "- 상태: ");

            if (parsedStatus != null) {
                // "구현됨 (조각 1)"처럼 뒤에 근거가 붙는다.
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

    /**
     * SCREENS.md에서 인덱스 표의 행을 읽는다.
     *
     * <p>`파일` 칸은 마크다운 링크다. 사람이 눌러 갈 수 있어야 해서 링크로 두었는데, 그러면
     * 링크가 가리키는 곳과 검사하는 곳이 갈라질 수 있다. 그래서 표시 문구가 아니라
     * <b>링크가 가리키는 쪽</b>을 읽는다 — 깨진 링크는 여기서 존재하지 않는 파일이 된다.
     */
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

    /**
     * 명세 파일의 제목 줄에서 주소를 읽는다.
     *
     * <p>파일 전체에 인덱스의 주소 문자열이 <b>들어 있는지</b>만 보면 약하다. 제목을 새 경로로
     * 바꿔도 산문이나 예시에 옛 `GET /...`이 한 번 남아 있으면 통과하고, 인덱스는 낡은 주소를
     * 계속 보여 준다. 검사가 지키려는 것은 "인덱스의 주소가 이 화면의 주소와 같다"이므로
     * 제목에서 뽑아 <b>같은지</b>를 본다.
     */
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

    /** 마크다운 링크에서 가리키는 쪽을. 링크가 아니면 칸 전체를 경로로 본다. */
    private String linkTargetOf(String cell) {
        Matcher matcher = MARKDOWN_LINK.matcher(cell.trim());

        return matcher.find() ? matcher.group(1) : unquote(cell);
    }

    /**
     * 명세 파일 목록. 이름순으로 고정해, 어느 파일이 실패했는지가 실행마다 달라지지 않게 한다.
     */
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
        // 속성값은 큰따옴표와 작은따옴표 둘 다 쓸 수 있다. 한쪽만 보면
        // th:text='|추천 ${...}|' 로 바꾸면서 자리 표시를 남기는 것을 놓친다.
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

    /** 표에서는 문자열을 백틱으로 감싸 공백과 기호가 잘리지 않게 한다. */
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
