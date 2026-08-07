package com.cakeshop.domain.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 리뷰 문서끼리 어긋나는 것을 잡는다(PLAN.md 하네스 H1~H5).
 *
 * <p>문서를 DOMAIN/specs/PLAN/decisions/history로 나누면서 <b>어긋날 자리가 늘었다.</b> 파일이
 * 3개에서 12개가 됐고, 기능 하나를 추가할 때 손대야 하는 곳이 최소 세 곳(DOMAIN 1절 인벤토리,
 * specs 파일, PLAN 조각 표)이다. 그중 하나를 빠뜨리는 것이 이 구조에서 가장 흔할 실수다.
 *
 * <p><b>이 테스트가 보증하지 않는 것</b>: 전부 형태 검사다. 낡은 참조는 잡지만 <b>틀린 설명</b>은
 * 못 잡는다. "D1이 4곳에서 불린다"(실제 5곳) 같은 문장은 여기를 전부 통과한다. 그쪽은 사람이
 * 하는 리뷰 몫이고, 각 검사가 무엇을 보증하지 않는지는 PLAN.md 하네스 표에 행마다 적혀 있다.
 *
 * <p>코드와 문서를 대조하는 검사는 여기 없다. 리뷰는 문서가 코드보다 앞서 있어 "문서에 있는데
 * 코드에 없음"이 미구현이라 정상이고, 실제 드리프트는 <b>코드 → 문서</b> 쪽에서 난다. 그 방향은
 * 코드가 생기는 조각 3에 붙인다(PLAN.md 조각 3).
 */
class ReviewDocTests {

    private static final Path DOC_ROOT = Path.of("docs", "review");
    private static final Path DOMAIN_DOC = DOC_ROOT.resolve("DOMAIN.md");
    private static final Path PLAN_DOC = DOC_ROOT.resolve("PLAN.md");
    private static final Path SPECS_DIR = DOC_ROOT.resolve("specs");
    private static final Path TEST_SOURCE_ROOT = Path.of("src", "test", "java");

    /** H1이 훑는 자바 소스 뿌리. 주석에 문서 경로가 적히는 자리다. */
    private static final List<Path> REVIEW_JAVA_ROOTS =
            List.of(
                    Path.of("src", "main", "java", "com", "cakeshop", "domain", "review"),
                    Path.of("src", "test", "java", "com", "cakeshop", "domain", "review"));

    /** 문서 경로처럼 보이는 토큰. 글로브(`**\/*.md`)는 `*`에서 끊겨 걸리지 않는다. */
    private static final Pattern DOC_REFERENCE = Pattern.compile("[A-Za-z0-9_./-]+\\.md");

    /**
     * 문서 안에서 <b>경로로 취급할</b> 접두사. 나머지는 보지 않는다.
     *
     * <p>맨 이름(`DOMAIN.md 2.1`, `PLAN.md` R2)은 이 저장소 문서가 산문에서 늘 쓰는 표기라
     * 경로가 아니다. 그것까지 경로로 보면 <b>문서를 문서 이름으로 부르는 행위가 규칙 위반</b>이
     * 되고, 그러면 다음 사람이 지우는 것은 오타가 아니라 문장이다. 커뮤니티 H36이 산문을 빼고
     * 코드 블록만 보기로 한 것과 같은 판단이다.
     */
    private static final List<String> DOC_RELATIVE_PREFIXES =
            List.of("docs/", "../", "specs/", "decisions/", "history/");

    /**
     * 자바 주석에서는 저장소 뿌리 기준 경로만 본다.
     *
     * <p>`../DOMAIN.md` 같은 상대 표기는 문서 트리 안에서만 뜻이 있다. 자바 파일 옆에서 풀면
     * 엉뚱한 곳을 가리키므로, 자바 주석은 처음부터 `docs/...` 전체 경로로 적는다(`ReviewReply`
     * 선례).
     */
    private static final List<String> JAVA_PREFIXES = List.of("docs/");

    /**
     * 없어졌지만 <b>일부러 이름을 부르는</b> 문서.
     *
     * <p>문서 재배치가 이 둘을 지웠고, PLAN.md 결정 로그와 migration 주석 설명이 그 사실을
     * 적으면서 이름을 부른다. <b>없앤 파일을 없앴다고 적는 것이 검사 위반이 되면 안 된다.</b>
     * 되살아나면 이 목록에서 빼는 것이 그때 할 일이다.
     */
    private static final Set<String> REMOVED_DOCS_NAMED_ON_PURPOSE =
            Set.of("docs/review/SPEC.md", "docs/review/FLOW.md");

    /** DOMAIN.md 1절 기능 표의 행. `| **A1** | ... | `review-write.md` |` */
    private static final Pattern INVENTORY_ROW =
            Pattern.compile("^\\|\\s*\\*\\*([A-E]\\d+)\\*\\*\\s*\\|.*$");

    /** specs/ 안의 기능 절 제목. `### A1. 작성할 후기 목록` */
    private static final Pattern FEATURE_HEADING = Pattern.compile("^###\\s+([A-E]\\d+)\\.\\s");

    /** PLAN.md 조각 표의 행. `| 0 | 준비 | ... |` */
    private static final Pattern SLICE_ROW = Pattern.compile("^\\|\\s*(\\d+)\\s*\\|");

    /** PLAN.md 하네스 표의 행. `| H1 | ... |` */
    private static final Pattern HARNESS_ROW = Pattern.compile("^\\|\\s*(H\\d+)\\s*\\|");

    /** 백틱 안의 `XxxTests` 또는 `XxxTests.methodName`. */
    private static final Pattern QUOTED_TEST_REFERENCE =
            Pattern.compile("`([A-Z][A-Za-z0-9]*Tests)(?:\\.([A-Za-z0-9_]+))?`");

    /** 인벤토리 `조각` 열에서 맨 앞 숫자만 뽑는다. `8 (2차)` → `8`, `2 (#33)` → `2` */
    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\s*(\\d+)");

    // ------------------------------------------------------------------
    // H1
    // ------------------------------------------------------------------

    /**
     * H1. 문서와 리뷰 자바 소스가 가리키는 문서 경로가 실존한다.
     *
     * <p>파일을 옮기면 가리키던 곳이 조용히 죽는다. 컴파일도 테스트도 통과하고, 그 경로를 따라간
     * 다음 사람만 막힌다. 문서 재배치에서 SPEC.md·FLOW.md를 지우면서 실제로 6개 파일이 이
     * 상태가 됐다.
     *
     * <p><b>보증하지 않는 것</b>: 경로가 맞다는 것뿐이다. 그 문서에 실제로 그 내용이 있는지는 보지
     * 않는다. `DOMAIN.md 2.1`을 가리키면서 2.1이 다른 규칙이어도 통과한다.
     *
     * <p><b>일부러 좁힌 것</b>: 맨 이름(`PLAN.md` R2, `DOMAIN.md` 2.1)은 경로로 보지 않는다.
     * 이유는 {@link #DOC_RELATIVE_PREFIXES}에 적었다. 그래서 `domain/review/CLAUDE.md` 같은
     * 줄임 표기도 검사 밖이다 — 실제 경로는 `src/main/java/...` 아래라 접두사 규칙에 걸리지 않는다.
     *
     * <p><b>일부러 보지 않는 곳</b>: 머지된 Flyway migration. 체크섬 때문에 주석 한 글자도 고칠 수
     * 없어 검사 대상에 넣으면 영원히 빨간불이다. 근거는 PLAN.md 하네스 절.
     */
    @Test
    @DisplayName("H1. 문서와 리뷰 소스가 가리키는 문서 경로가 전부 실존한다")
    void documentReferences_allResolve() {
        List<Path> scanned = new ArrayList<>(markdownFilesUnder(DOC_ROOT));
        for (Path root : REVIEW_JAVA_ROOTS) {
            scanned.addAll(filesUnder(root, ".java"));
        }

        List<String> broken = new ArrayList<>();
        int checkedCount = 0;

        for (Path file : scanned) {
            List<String> prefixes =
                    file.getFileName().toString().endsWith(".java")
                            ? JAVA_PREFIXES
                            : DOC_RELATIVE_PREFIXES;
            Matcher matcher = DOC_REFERENCE.matcher(read(file));
            while (matcher.find()) {
                String reference = matcher.group();
                if (prefixes.stream().noneMatch(reference::startsWith)) {
                    continue;
                }
                if (REMOVED_DOCS_NAMED_ON_PURPOSE.contains(reference)) {
                    continue;
                }
                checkedCount++;
                if (!resolves(file, reference)) {
                    broken.add(file + " -> " + reference);
                }
            }
        }

        // 검사가 살아 있는지부터 본다. 한 건도 못 읽으면 아래 단언은 아무것도 검사하지 않는다.
        assertThat(scanned).as("훑을 문서와 소스가 있어야 한다").isNotEmpty();
        assertThat(checkedCount)
                .as("경로 참조를 하나도 못 찾았다면 정규식이나 접두사 목록이 죽은 것이다")
                .isGreaterThanOrEqualTo(20);

        assertThat(broken).as("가리키는 문서가 없다").isEmpty();
    }

    /**
     * 참조를 세 자리에서 찾아본다 — 저장소 뿌리, 참조한 파일의 옆, `docs/review/` 아래.
     *
     * <p>이 저장소 문서가 세 표기를 섞어 쓴다. `docs/community/PLAN.md`(뿌리 기준),
     * `../DOMAIN.md`·`product-rating.md`(옆 기준), `specs/review-write.md`(docs/review 기준).
     * 셋 중 하나라도 있으면 통과다 — <b>넓게 잡는 대신 죽은 경로는 확실히 잡는다.</b> 오탐이
     * 놓침보다 나쁘기 때문이다(PLAN.md 하네스 절).
     */
    private boolean resolves(Path referencingFile, String reference) {
        Path parent = referencingFile.getParent();
        List<Path> candidates =
                List.of(
                        Path.of(reference),
                        parent == null ? Path.of(reference) : parent.resolve(reference),
                        DOC_ROOT.resolve(reference));
        return candidates.stream().anyMatch(candidate -> Files.isRegularFile(candidate.normalize()));
    }

    // ------------------------------------------------------------------
    // H2
    // ------------------------------------------------------------------

    /**
     * H2. DOMAIN.md 1절 기능 표의 모든 ID가 `spec` 열이 가리킨 파일에 절로 정확히 한 번 있다.
     *
     * <p>1절 표가 ID → 파일 라우팅 표다. 여기가 어긋나면 기능을 찾으러 간 사람이 빈손으로
     * 돌아온다. <b>"정확히 한 번"이 중요하다</b> — 옮기다 남긴 사본이 두 곳에 있으면 한쪽만 고치는
     * 일이 반드시 생긴다.
     *
     * <p><b>보증하지 않는 것</b>: 절의 내용은 보지 않는다. 제목만 있고 본문이 비어도 통과한다.
     */
    @Test
    @DisplayName("H2. 기능 표의 모든 ID가 지정된 specs 파일에 정확히 한 번 있다")
    void everyFeatureId_hasExactlyOneSection_inItsSpecFile() {
        Map<String, String> routing = featureRouting();
        assertThat(routing).as("1절 기능 표를 한 행도 읽지 못했다").hasSizeGreaterThanOrEqualTo(21);

        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> entry : routing.entrySet()) {
            String id = entry.getKey();
            String specFileName = entry.getValue();
            Path specFile = SPECS_DIR.resolve(specFileName);

            if (!Files.isRegularFile(specFile)) {
                problems.add(id + ": spec 열이 가리킨 " + specFile + " 가 없다");
                continue;
            }

            long count =
                    read(specFile)
                            .lines()
                            .filter(line -> matchesFeatureHeading(line, id))
                            .count();
            if (count != 1) {
                problems.add(id + ": " + specFileName + " 안의 `### " + id + ".` 절이 " + count + "개다");
            }
        }

        assertThat(problems).as("기능 표와 specs/ 가 어긋난다").isEmpty();
    }

    // ------------------------------------------------------------------
    // H3
    // ------------------------------------------------------------------

    /**
     * H3. 인벤토리 3자가 일치한다 — DOMAIN 1절, specs/ 절 제목, PLAN 조각 표.
     *
     * <p><b>이 파일의 핵심이다.</b> 기능을 하나 추가하면서 세 곳 중 하나를 빠뜨리는 것이 이
     * 구조에서 가장 흔할 실수인데, 빠뜨린 결과가 오류가 아니라 <b>침묵</b>이다. specs에만 쓰면
     * 인벤토리에서 안 보이고, 인벤토리에만 쓰면 명세가 없는 기능이 되고, 조각 표에 없으면 언제
     * 하는지 아무도 모른다.
     *
     * <p>H2와 방향이 다르다. H2는 인벤토리 → specs 한 방향이라 <b>specs에만 있는 절</b>은 못
     * 잡는다. 여기서 반대쪽을 본다.
     *
     * <p><b>보증하지 않는 것</b>: `현재` 열이 실제 코드와 맞는지는 보지 않는다. 조각 0이 머지된
     * 뒤에도 E1·E2·E3가 `없음`으로 남아 있던 자리가 정확히 그것이고, 그쪽은 조각 3의 문서↔코드
     * 검사가 맡는다.
     */
    @Test
    @DisplayName("H3. DOMAIN 1절·specs/·PLAN 조각 표의 인벤토리가 서로 어긋나지 않는다")
    void inventories_agree() {
        Set<String> inventoryIds = new TreeSet<>(featureRouting().keySet());
        Set<String> specIds = new TreeSet<>(featureIdsInSpecs());
        Set<Integer> planSlices = sliceNumbersInPlan();
        Set<Integer> inventorySlices = sliceNumbersInInventory();

        assertThat(inventoryIds).as("1절 기능 표를 못 읽었다").isNotEmpty();
        assertThat(specIds).as("specs/ 의 기능 절을 못 읽었다").isNotEmpty();
        assertThat(planSlices).as("PLAN 조각 표를 못 읽었다").isNotEmpty();

        assertThat(specIds).as("DOMAIN 1절 기능 표와 specs/ 의 기능 절 집합이 다르다").isEqualTo(inventoryIds);
        assertThat(planSlices)
                .as("1절 `조각` 열이 PLAN 조각 표에 없는 조각을 가리킨다")
                .containsAll(inventorySlices);
    }

    // ------------------------------------------------------------------
    // H4
    // ------------------------------------------------------------------

    /**
     * H4. 문서가 서로의 역할을 침범하지 않는다.
     *
     * <p>나눈 이유가 축을 가르는 것이었다 — specs는 <b>무엇을 보장하나</b>, PLAN은 <b>지금 어디까지</b>.
     * 침범이 시작되면 같은 사실이 두 곳에 생기고, 그때부터 한쪽이 낡는다. 나누기 전 SPEC.md가
     * 여섯 가지 일을 한꺼번에 하던 상태로 되돌아가는 경로가 이것이다.
     *
     * <p><b>보증하지 않는 것</b>: 형태만 본다. 결정 로그 표를 만들지 않고 <b>산문으로</b> 결정 경위를
     * 풀어 적는 것은 못 잡는다.
     */
    @Test
    @DisplayName("H4. specs/ 에 결정 로그가 없고 PLAN.md 에 기능 절이 없다")
    void documents_stayInTheirLane() {
        List<String> problems = new ArrayList<>();

        for (Path spec : markdownFilesUnder(SPECS_DIR)) {
            if (read(spec).contains("결정 로그")) {
                problems.add(spec + ": 결정 로그는 PLAN.md 가 소유한다");
            }
        }

        read(PLAN_DOC)
                .lines()
                .filter(line -> FEATURE_HEADING.matcher(line).find())
                .forEach(line -> problems.add("PLAN.md: 기능 절은 specs/ 가 소유한다 -> " + line.trim()));

        assertThat(problems).as("문서가 서로의 역할을 침범했다").isEmpty();
    }

    // ------------------------------------------------------------------
    // H5
    // ------------------------------------------------------------------

    /**
     * H5. 하네스 표의 각 행이 가리키는 테스트 클래스·메서드가 실존한다.
     *
     * <p><b>커뮤니티가 하네스를 36개 쌓고 나서야 이 대조 수단이 없다는 것을 발견했다.</b> 표가 자란
     * 뒤에 붙이면 그때까지 쌓인 어긋남을 한꺼번에 갚아야 하므로, 리뷰는 표가 다섯 줄일 때 세운다.
     *
     * <p>표에 적힌 클래스 이름이 낡는 경로는 평범하다 — 테스트를 나누거나 이름을 바꾸면 표는
     * 그대로 남는다. 그러면 "이건 무엇이 지키나"를 물은 사람이 없는 테스트를 찾아 헤맨다.
     *
     * <p><b>행마다 참조가 하나 이상 있는지도 함께 본다.</b> 전체 건수만 세면 한 행의 클래스 이름이
     * 망가져도 다른 행이 수를 채워 통과한다 — 이 검사를 처음 시험할 때 실제로 그렇게 빠져나갔다.
     * 이름을 `ReviewDocTestsMissing`처럼 바꾸면 정규식에 걸리지 않아 <b>참조가 없는 행</b>이 되고,
     * 없는 클래스를 가리키는 것보다 이쪽이 더 조용하다.
     *
     * <p><b>보증하지 않는 것</b>: 그 테스트가 <b>적힌 대로 검사하는지</b>는 못 본다. 클래스와 메서드가
     * 있는지만 본다. 메서드 이름은 소스 본문에 그 낱말이 있는지로 확인하므로, 주석에만 있어도
     * 통과한다.
     */
    @Test
    @DisplayName("H5. 하네스 표가 가리키는 테스트 클래스·메서드가 실존한다")
    void harnessTable_pointsToRealTests() {
        Map<String, Path> testClasses = testClassIndex();
        assertThat(testClasses).as("테스트 소스를 한 개도 못 찾았다").isNotEmpty();

        List<String> harnessRows =
                read(PLAN_DOC).lines().filter(line -> HARNESS_ROW.matcher(line).find()).toList();
        assertThat(harnessRows).as("하네스 표를 한 행도 읽지 못했다").hasSizeGreaterThanOrEqualTo(5);

        List<String> problems = new ArrayList<>();

        for (String row : harnessRows) {
            String harnessId = firstGroup(HARNESS_ROW, row);
            Matcher matcher = QUOTED_TEST_REFERENCE.matcher(row);
            int referencesInRow = 0;

            while (matcher.find()) {
                referencesInRow++;
                String className = matcher.group(1);
                String methodName = matcher.group(2);

                Path source = testClasses.get(className);
                if (source == null) {
                    problems.add(harnessId + ": 그런 테스트 클래스가 없다 -> " + className);
                    continue;
                }
                if (methodName != null && !read(source).contains(methodName)) {
                    problems.add(harnessId + ": " + className + " 에 " + methodName + " 가 없다");
                }
            }

            // 행마다 본다. 전체 건수만 세면 한 행의 이름이 망가져도 다른 행이 수를 채워
            // 조용히 통과한다 — 실제로 이 검사를 처음 시험할 때 그렇게 빠져나갔다.
            if (referencesInRow == 0) {
                problems.add(harnessId + ": 이 행이 가리키는 테스트가 없다. 형 열에 클래스 이름을 적는다");
            }
        }

        assertThat(problems).as("하네스 표가 없는 테스트를 가리킨다").isEmpty();
    }

    // ------------------------------------------------------------------
    // 파싱 도우미
    // ------------------------------------------------------------------

    /** DOMAIN.md 1절 기능 표를 ID → spec 파일 이름으로 읽는다. */
    private Map<String, String> featureRouting() {
        Map<String, String> routing = new LinkedHashMap<>();
        for (String line : read(DOMAIN_DOC).lines().toList()) {
            Matcher matcher = INVENTORY_ROW.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String[] columns = line.split("\\|", -1);
            // ["", ID, 기능, 액터, 경로, 현재, 조각, spec, ""]
            if (columns.length < 9) {
                continue;
            }
            routing.put(matcher.group(1), unquote(columns[7]));
        }
        return routing;
    }

    /** DOMAIN.md 1절 `조각` 열의 숫자. `8 (2차)`·`2 (#33)` 처럼 꼬리가 붙는다. */
    private Set<Integer> sliceNumbersInInventory() {
        Set<Integer> slices = new LinkedHashSet<>();
        for (String line : read(DOMAIN_DOC).lines().toList()) {
            if (!INVENTORY_ROW.matcher(line).matches()) {
                continue;
            }
            String[] columns = line.split("\\|", -1);
            if (columns.length < 9) {
                continue;
            }
            Matcher number = LEADING_NUMBER.matcher(columns[6]);
            if (number.find()) {
                slices.add(Integer.parseInt(number.group(1)));
            }
        }
        return slices;
    }

    /** PLAN.md 조각 표의 조각 번호. */
    private Set<Integer> sliceNumbersInPlan() {
        Set<Integer> slices = new LinkedHashSet<>();
        for (String line : read(PLAN_DOC).lines().toList()) {
            Matcher matcher = SLICE_ROW.matcher(line);
            if (matcher.find()) {
                slices.add(Integer.parseInt(matcher.group(1)));
            }
        }
        return slices;
    }

    /** specs/ 안의 모든 `### <ID>.` 절 제목. */
    private Set<String> featureIdsInSpecs() {
        Set<String> ids = new LinkedHashSet<>();
        for (Path spec : markdownFilesUnder(SPECS_DIR)) {
            for (String line : read(spec).lines().toList()) {
                Matcher matcher = FEATURE_HEADING.matcher(line);
                if (matcher.find()) {
                    ids.add(matcher.group(1));
                }
            }
        }
        return ids;
    }

    /** 테스트 클래스 이름 → 소스 파일. 도메인을 가리지 않는다 — 표가 남의 도메인 테스트를 인용할 수 있다. */
    private Map<String, Path> testClassIndex() {
        Map<String, Path> index = new LinkedHashMap<>();
        for (Path source : filesUnder(TEST_SOURCE_ROOT, ".java")) {
            String fileName = source.getFileName().toString();
            index.put(fileName.substring(0, fileName.length() - ".java".length()), source);
        }
        return index;
    }

    private boolean matchesFeatureHeading(String line, String id) {
        Matcher matcher = FEATURE_HEADING.matcher(line);
        return matcher.find() && matcher.group(1).equals(id);
    }

    private String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "?";
    }

    private String unquote(String column) {
        return column.replace("`", "").replace("*", "").trim();
    }

    private List<Path> markdownFilesUnder(Path root) {
        return filesUnder(root, ".md");
    }

    private List<Path> filesUnder(Path root, String suffix) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("문서를 훑지 못했다: " + root, e);
        }
    }

    private String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("문서를 읽지 못했다: " + path, e);
        }
    }
}
