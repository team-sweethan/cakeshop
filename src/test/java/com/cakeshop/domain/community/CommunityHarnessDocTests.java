package com.cakeshop.domain.community;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PLAN.md 하네스 표와 실제 테스트 클래스가 어긋나는 것을 잡는다(H36).
 *
 * <p><b>하네스를 42행 쌓는 동안 표 자체를 지키는 것이 없었다.</b> 표는 "이 규칙은 무엇이
 * 지키는가"를 묻는 사람이 가장 먼저 여는 곳인데, 테스트를 나누거나 이름을 바꾸면 표만 조용히
 * 낡는다. 코드도 테스트도 전부 초록불이고, 표를 따라간 다음 사람만 없는 클래스를 찾아 헤맨다.
 * 리뷰 도메인이 표가 다섯 줄일 때 같은 검사를 먼저 세웠고(리뷰 H5), 이쪽은 그 반대 순서라
 * 세우는 순간 쌓인 어긋남을 갚아야 했다 — 실제로 역방향에서 네 건이 나왔다.
 *
 * <p><b>양방향을 다 본다.</b> 정방향(표 → 테스트)만 두면 표에 적힌 것은 지켜지지만 <b>표에 아예
 * 없는 하네스</b>는 영원히 드러나지 않는다. 이 검사를 처음 돌렸을 때 정방향은 한 건도 걸리지
 * 않았고 어긋남은 전부 역방향에 있었다. 정방향만 넣었으면 "표가 현실과 맞다"는 초록불을 받고
 * 넘어갔을 것이다.
 *
 * <p><b>이 테스트가 보증하지 않는 것</b>: 형태 검사다. 표가 <b>없는</b> 테스트를 가리키는 것은
 * 잡지만, 표의 설명이 <b>틀린</b> 것은 못 잡는다. "H1b가 신고 목록까지 덮는다"처럼 실제보다 넓게
 * 적은 문장은 여기를 전부 통과한다. 그쪽은 사람이 하는 리뷰 몫이다.
 */
class CommunityHarnessDocTests {

    private static final Path PLAN_DOC = Path.of("docs", "community", "PLAN.md");
    private static final Path TEST_SOURCE_ROOT = Path.of("src", "test", "java");

    /**
     * 역방향이 훑는 범위.
     *
     * <p><b>커뮤니티 폴더만 본다.</b> `MemberCommunityMapperTests`처럼 회원 폴더에 있는 연동
     * 테스트는 커뮤니티가 쓰지만 그 폴더는 회원 담당자 영역이다. 커뮤니티 표가 남의 폴더에 있는
     * 테스트 목록까지 강제하면, 회원 쪽에서 테스트를 하나 늘릴 때마다 이 검사가 빨간불이 된다.
     */
    private static final Path COMMUNITY_TEST_ROOT =
            Path.of("src", "test", "java", "com", "cakeshop", "domain", "community");

    /** 하네스 표의 행. `| H0a | ... |`, `| H34 | ... |` */
    private static final Pattern HARNESS_ROW = Pattern.compile("^\\|\\s*(H\\d+[a-z]?)\\s*\\|");

    /** 백틱 안의 `XxxTests` 또는 `XxxTests.methodName`. */
    private static final Pattern QUOTED_TEST_REFERENCE =
            Pattern.compile("`([A-Z][A-Za-z0-9]*Tests)(?:\\.([A-Za-z0-9_]+))?`");

    /** `상태` 칸이 이것을 담고 있으면 지금 돌고 있는 하네스다. */
    private static final String APPLIED = "적용";

    /**
     * 표에 <b>없어도 되는</b> 커뮤니티 테스트.
     *
     * <p>지금은 비어 있다 — 커뮤니티 테스트 클래스 전부가 표의 어느 행엔가 적혀 있다. 앞으로
     * 하네스가 아닌 평범한 단위 테스트를 더할 때 이 검사가 빨간불이 되는데, <b>그때 표에 올릴지
     * 여기 적을지를 고르는 것이 이 목록의 쓸모다.</b> 자동으로 넘어가면 다음 하네스도 표 밖에
     * 남는다. 여기 적을 때는 왜 하네스가 아닌지를 한 줄로 남긴다.
     */
    private static final Set<String> UNLISTED_ON_PURPOSE = Set.of();

    // ------------------------------------------------------------------
    // H36 정방향
    // ------------------------------------------------------------------

    /**
     * H36a. `적용`인 하네스 행이 가리키는 테스트 클래스·메서드가 실존한다.
     *
     * <p><b>`적용`인 행만 본다.</b> 아직 안 만든 하네스(H3의 `위반 발생 시`)는 가리킬 테스트가 없는
     * 것이 정상이고, 그것까지 요구하면 계획을 적는 행위가 위반이 된다.
     *
     * <p><b>행마다 참조가 하나 이상 있는지도 함께 본다.</b> 전체 건수만 세면 한 행의 클래스 이름이
     * 망가져도 다른 행이 수를 채워 통과한다 — 리뷰 쪽에서 실제로 그렇게 빠져나갔다. 이름이
     * `CommunityMapperTestsGone`처럼 바뀌면 정규식에 걸리지 않아 <b>참조가 없는 행</b>이 되는데,
     * 없는 클래스를 가리키는 것보다 이쪽이 더 조용하다.
     *
     * <p><b>보증하지 않는 것</b>: 그 테스트가 <b>적힌 대로 검사하는지</b>는 못 본다. 메서드는 소스
     * 본문에 그 낱말이 있는지로 확인하므로 주석에만 있어도 통과한다. 행이 `+3`처럼 적어 둔 개수도
     * 보지 않는다.
     */
    @Test
    @DisplayName("H36a. 적용된 하네스 행이 가리키는 테스트 클래스·메서드가 실존한다")
    void appliedHarnessRows_pointToRealTests() {
        Map<String, Path> testClasses = testClassIndex();
        assertThat(testClasses).as("테스트 소스를 한 개도 못 찾았다").isNotEmpty();

        List<String> appliedRows = harnessRows().stream().filter(this::isApplied).toList();
        assertThat(appliedRows)
                .as("하네스 표에서 `적용` 행을 거의 못 읽었다. 표 형식이 바뀌면 이 검사는 조용히 빈 검사가 된다")
                .hasSizeGreaterThanOrEqualTo(40);

        List<String> problems = new ArrayList<>();

        for (String row : appliedRows) {
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

            if (referencesInRow == 0) {
                problems.add(harnessId + ": 적용인데 가리키는 테스트가 없다. `형태` 칸에 클래스 이름을 적는다");
            }
        }

        assertThat(problems).as("하네스 표가 없는 테스트를 가리킨다").isEmpty();
    }

    // ------------------------------------------------------------------
    // H36 역방향
    // ------------------------------------------------------------------

    /**
     * H36b. 커뮤니티 테스트 클래스가 전부 하네스 표에 적혀 있다.
     *
     * <p>정방향이 막는 것은 "표가 낡는 것"이고 이쪽이 막는 것은 <b>"표가 비는 것"</b>이다. 둘은
     * 다르다 — 하네스급 테스트를 새로 쓰고 표에 안 올리면 표는 여전히 전부 실재하는 클래스를
     * 가리키므로 정방향은 초록불이다. 그러면 그 규칙은 <b>지켜지고 있는데 아무도 모르는</b> 상태가
     * 되고, 다음 사람이 같은 것을 다시 만들거나 그 테스트를 군더더기로 보고 지운다.
     *
     * <p>이 검사를 처음 넣었을 때 네 개가 걸렸고 넷 다 하네스급이었다 — 댓글·신고 상태 전이 표
     * 둘(H0a가 "댓글 상태"를 말하면서 `PostStatusTests`만 인용하고 있었다), 롤백 셋
     * (`CommunityTransactionTests`), 스케줄러 날짜 하나(PLAN 산문에만 있었다). <b>"역방향은 소음일
     * 것"이라던 예상이 틀렸다.</b>
     *
     * <p><b>표의 모든 행을 인용으로 친다</b>(`적용`이 아닌 행도). 아직 안 붙인 하네스 행이 이름을
     * 적어 두는 것도 "표에 있다"이기 때문이다.
     *
     * <p><b>보증하지 않는 것</b>: 그 행이 그 테스트를 <b>제대로</b> 설명하는지는 못 본다. 이름만
     * 어딘가에 적혀 있으면 통과한다.
     */
    @Test
    @DisplayName("H36b. 커뮤니티 테스트 클래스가 전부 하네스 표에 적혀 있다")
    void communityTests_areAllListedInHarnessTable() {
        Set<String> citedAnywhere = new LinkedHashSet<>();
        for (String row : harnessRows()) {
            Matcher matcher = QUOTED_TEST_REFERENCE.matcher(row);
            while (matcher.find()) {
                citedAnywhere.add(matcher.group(1));
            }
        }
        assertThat(citedAnywhere).as("표에서 테스트 이름을 한 개도 못 읽었다").isNotEmpty();

        List<String> communityTests =
                filesUnder(COMMUNITY_TEST_ROOT, "Tests.java").stream()
                        .map(this::classNameOf)
                        .sorted()
                        .toList();
        assertThat(communityTests)
                .as("커뮤니티 테스트를 거의 못 찾았다. 경로가 바뀌면 이 검사는 조용히 빈 검사가 된다")
                .hasSizeGreaterThanOrEqualTo(20);

        List<String> unlisted =
                communityTests.stream()
                        .filter(name -> !citedAnywhere.contains(name))
                        .filter(name -> !UNLISTED_ON_PURPOSE.contains(name))
                        .toList();

        assertThat(unlisted)
                .as("하네스 표에 없는 커뮤니티 테스트가 있다. 표에 올리거나 UNLISTED_ON_PURPOSE 에 이유와 함께 적는다")
                .isEmpty();
    }

    // ------------------------------------------------------------------
    // 파싱 도우미
    // ------------------------------------------------------------------

    private List<String> harnessRows() {
        List<String> rows =
                read(PLAN_DOC).lines().filter(line -> HARNESS_ROW.matcher(line).find()).toList();
        assertThat(rows).as("하네스 표를 한 행도 읽지 못했다").isNotEmpty();
        return rows;
    }

    /** `| H0a | 무엇 | 형태 | 상태 |` 에서 네 번째 칸이 `적용`인지. */
    private boolean isApplied(String row) {
        String[] columns = row.split("\\|", -1);
        return columns.length >= 5 && columns[4].contains(APPLIED);
    }

    /** 테스트 클래스 이름 → 소스 파일. 도메인을 가리지 않는다 — 표가 남의 도메인 테스트를 인용한다(H1a의 `OrderMapperXmlTests`). */
    private Map<String, Path> testClassIndex() {
        Map<String, Path> index = new LinkedHashMap<>();
        for (Path source : filesUnder(TEST_SOURCE_ROOT, ".java")) {
            index.put(classNameOf(source), source);
        }
        return index;
    }

    private String classNameOf(Path source) {
        String fileName = source.getFileName().toString();
        return fileName.substring(0, fileName.length() - ".java".length());
    }

    private String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "?";
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
            throw new UncheckedIOException("테스트 소스를 훑지 못했다: " + root, e);
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
