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

/**
 * 커뮤니티가 회원 도메인을 넘어다보지 않는지 고정한다(조각 10d).
 *
 * <p>조각 10에서 `members` 직접 JOIN 8곳을 걷어내고 {@code MemberCommunityQueryService}
 * 경유로 바꿨다(conventions.md 15.1). <b>여기서 막으려는 것은 되돌아오는 것이다</b> — JOIN
 * 하나를 다시 넣으면 화면 결과가 똑같고 테스트도 전부 통과한다. 빠르고 편하기까지 해서
 * 되돌아올 이유는 늘 있다.
 *
 * <p>형태 검사인 것은 다른 방법이 없기 때문이다. 조각 7a의 H28, 조각 1의 H1a와 같은 종류로,
 * <b>동작으로는 드러나지 않는 규칙</b>은 형태를 직접 적어 두는 것 말고 잡을 방법이 없다.
 *
 * <p><b>코드만이 아니라 문서도 본다</b>(조각 11a). 매퍼를 그대로 둔 채 {@code DOMAIN.md}의
 * SQL 예제에만 JOIN을 되살리면 코드 쪽 검사는 전부 통과하는데, <b>다음 사람이 코드보다 먼저
 * 읽는 것은 문서다.</b> 조각 10b가 JOIN을 걷어낸 뒤에도 6.1 예제가 그 상태로 남아 있었다.
 *
 * <p><b>여기서 다루지 않는 것</b>: "회원 행이 없는 게시글이 목록에서 빠지지 않는지"는 실제 DB로
 * 재현할 수 없다. {@code posts.member_id}가 members를 참조하는 NOT NULL FK라 그런 행을 만들
 * 수가 없다(DOMAIN.md 8). 탈퇴는 행을 지우는 것이 아니라 {@code WITHDRAWN} 상태로 두는 것이고,
 * 그쪽은 {@code CommunityMemberContractTests}가 실제 DB로 확인한다. FK가 없는 상황을 가정한
 * 조립 규칙은 {@code CommunityServiceTests}·{@code CommunityAdminServiceTests}가 맡는다.
 */
class CommunityDomainBoundaryTests {

    private static final Path JAVA_ROOT =
            Path.of("src", "main", "java", "com", "cakeshop", "domain", "community");

    /**
     * 파일 하나가 아니라 디렉터리다. 매퍼가 고객·관리자로 갈렸고 앞으로 더 갈릴 수 있는데,
     * 파일 이름을 박아 두면 <b>새 파일만 검사에서 빠진다</b> — 빠진 자리는 초록불로 보인다.
     */
    private static final Path MAPPER_DIRECTORY =
            Path.of("src", "main", "resources", "mapper", "community");

    /**
     * 커뮤니티 문서 전부와 도메인 진입점.
     *
     * <p>진입점({@code CLAUDE.md})이 {@code docs/}가 아니라 소스 트리에 있어 따로 붙인다.
     * 둘 다 Gradle의 {@code test} 입력에 등록돼 있다(build.gradle).
     */
    private static final Path DOC_DIRECTORY = Path.of("docs", "community");

    private static final Path DOMAIN_ENTRY_POINT = JAVA_ROOT.resolve("CLAUDE.md");

    /**
     * 마크다운 코드 블록. 언어 표시가 있든 없든 잡는다.
     *
     * <p>언어를 지우는 것만으로 검사를 비껴갈 수 있으면 안 된다. 여는 울타리 줄의 나머지를
     * 버리고 닫는 울타리까지를 본문으로 삼는다.
     *
     * <p><b>울타리는 줄 첫머리에서만 인정한다.</b> 줄 가운데의 세 백틱은 코드 블록이 아니라
     * 백틱을 글자로 보여 주려고 감싼 인라인 코드다 — 이 저장소 문서가 실제로 그렇게 쓴다.
     * 줄 아무 데서나 인정하면 그 지점부터 다음 백틱까지의 <b>산문 전체가 코드 블록으로
     * 둔갑</b>하고, 그 산문에는 규칙을 설명하느라 금지된 이름이 들어 있다.
     */
    private static final Pattern FENCED_CODE_BLOCK =
            Pattern.compile("(?ms)^```[^\\n]*\\n(.*?)^```");

    /**
     * 커뮤니티가 회원 도메인에서 쓸 수 있는 것 전부.
     *
     * <p>계약 하나와 그 반환 타입뿐이다. 여기에 무언가를 더하는 변경은 <b>새 연동 계약을
     * 만드는 일</b>이므로, 목록을 늘리기 전에 conventions.md 15.2·15.8과 회원 담당자 확인을
     * 거친다. 목록을 늘리는 것 자체가 그 신호가 되라고 화이트리스트로 뒀다.</p>
     */
    private static final Set<String> ALLOWED_MEMBER_TYPES = Set.of(
            "com.cakeshop.domain.member.service.MemberCommunityQueryService",
            "com.cakeshop.domain.member.dto.view.MemberCommunityView");

    private static final Pattern MEMBER_PACKAGE_REFERENCE =
            Pattern.compile("com\\.cakeshop\\.domain\\.member\\.[A-Za-z0-9_.]+");

    /**
     * 회원 도메인이 소유한 테이블 전부.
     *
     * <p>{@code members} 하나만 보면 부족하다. 회원 상태 이력을 관리자 조회에 직접 붙이는
     * 변경은 JOIN 대상만 다를 뿐 소유권을 넘는 것은 똑같은데, 테이블 이름 하나만 보는 검사는
     * 그대로 통과한다 (PR #144 Codex 리뷰).
     *
     * <p>{@code member_coupons}는 이름과 달리 <b>쿠폰 도메인 소유</b>라 여기에 넣지 않는다.
     * 이 검사가 보는 것은 회원 도메인 경계 하나다. 다른 도메인 경계는 그 도메인 담당자와
     * 합의해 따로 세운다(AGENTS.md 도메인 담당 표).
     */
    private static final List<String> MEMBER_OWNED_TABLES =
            List.of("MEMBERS", "SOCIAL_ACCOUNTS", "MEMBER_STATUS_HISTORIES");

    /** 낱말 단위로 본다. {@code p.member_id}는 members 참조가 아니다. */
    private static final Pattern MEMBER_OWNED_TABLE_REFERENCE = Pattern.compile(
            "\\b(" + String.join("|", MEMBER_OWNED_TABLES) + ")\\b");

    /**
     * 커뮤니티 SQL이 회원 도메인 소유 테이블을 건드리지 않는지 확인한다.
     *
     * <p>JOIN만이 아니라 어떤 형태의 참조도 잡는다. 서브쿼리나 {@code EXISTS}로 우회하면
     * JOIN이라는 낱말은 없지만 소유권을 넘는 것은 똑같다.
     */
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

    /**
     * 문서의 SQL 예제도 회원 도메인 소유 테이블을 건드리지 않는지 확인한다(조각 D-1).
     *
     * <p><b>매퍼만 보는 것으로는 부족하다.</b> 매퍼를 그대로 둔 채 {@code DOMAIN.md} 6.1의
     * 예제에만 JOIN을 되살리면 위 검사는 통과한다 — 실제로 조각 10b가 JOIN을 걷어낸 뒤에도
     * 그 예제가 {@code JOIN members}를 들고 남아 있었고, 정본이 8절과 정면으로 어긋난 채
     * 아무 검사도 울리지 않았다(PR #146, PLAN.md R32). <b>다음 사람이 코드보다 먼저 읽는
     * 것이 문서다</b> — 틀린 예제는 틀린 코드보다 오래 살아남는다.
     *
     * <p><b>산문이 아니라 코드 블록만 본다.</b> 규칙을 설명하려면 금지된 이름을 입에 올려야
     * 한다 — 8절의 "커뮤니티 SQL이 {@code members}를 JOIN해 직접 판정하지 않는다"가 바로
     * 그것이다. 산문까지 보면 <b>규칙을 적는 행위가 규칙 위반</b>이 되고, 그러면 다음 사람이
     * 지우는 것은 JOIN이 아니라 설명이다. 주석을 걷어내고 보는 것과 같은 이유다.
     */
    @Test
    void communityDocs_sqlExamplesDoNotTouchMemberOwnedTables() throws IOException {
        List<String> violations = new ArrayList<>();
        List<String> scannedBlocks = new ArrayList<>();

        for (Path doc : documents()) {
            Matcher blocks = FENCED_CODE_BLOCK.matcher(Files.readString(doc, StandardCharsets.UTF_8));

            while (blocks.find()) {
                String block = withoutSqlComments(blocks.group(1)).toUpperCase();
                scannedBlocks.add(block);

                Matcher matcher = MEMBER_OWNED_TABLE_REFERENCE.matcher(block);

                while (matcher.find()) {
                    violations.add(doc + " -> " + matcher.group());
                }
            }
        }

        assertThat(scannedBlocks)
                .as("문서에서 코드 블록을 하나도 못 읽었다면 울타리 형식이 바뀐 것이다."
                        + " 읽을 것이 없으면 이 검사는 영원히 초록불이다")
                .isNotEmpty();

        assertThat(scannedBlocks)
                .as("SQL 예제까지 실제로 읽고 있는지 확인한다. 텍스트 블록만 읽고 있으면"
                        + " 위 단언은 통과하지만 검사하는 것은 아무것도 없다")
                .anyMatch(block -> block.contains("FROM POSTS"));

        assertThat(violations)
                .as("문서의 SQL 예제도 회원 도메인 소유 테이블을 조회하지 않는다. 예제가"
                        + " 규칙보다 먼저 읽히므로 매퍼와 같은 기준으로 본다 (DOMAIN.md 8, R32)")
                .isEmpty();
    }

    /**
     * 커뮤니티 코드가 회원 도메인에서 허용된 계약만 쓰는지 확인한다.
     *
     * <p>Entity와 Mapper를 콕 집어 막지 않고 <b>허용 목록으로 뒤집었다.</b> 금지 목록은 새로
     * 생기는 것을 못 잡는다 — 회원 쪽에 Service가 하나 더 생기면 그것을 직접 가져다 쓰는
     * 변경이 조용히 통과한다. 15.4는 "Mapper는 데이터 소유 도메인 내부 Service에서만"이고,
     * 커뮤니티가 볼 수 있는 것은 합의된 공개 계약 하나뿐이다.
     */
    @Test
    void communitySources_useOnlyAgreedMemberContract() throws IOException {
        Set<String> referenced = new LinkedHashSet<>();

        for (Path source : javaSources()) {
            Matcher matcher = MEMBER_PACKAGE_REFERENCE.matcher(strippedOf(source, false));

            while (matcher.find()) {
                referenced.add(matcher.group());
            }
        }

        assertThat(referenced)
                .as("커뮤니티는 회원 도메인의 공개 계약만 쓴다. 다른 Entity·Mapper·Service 를"
                        + " 직접 참조하지 않는다 (conventions.md 15.1·15.4, 조각 10)")
                .isSubsetOf(ALLOWED_MEMBER_TYPES);
    }

    /**
     * 허용 목록이 살아 있는지 확인한다.
     *
     * <p>위 검사는 참조가 <b>하나도 없어도</b> 통과한다. 계약 이름이 바뀌었는데 아무도 목록을
     * 고치지 않으면, 실제로는 새 이름을 쓰면서 검사는 초록불인 상태가 된다.
     */
    @Test
    void allowedMemberContract_isActuallyUsed() throws IOException {
        Set<String> referenced = new LinkedHashSet<>();

        for (Path source : javaSources()) {
            Matcher matcher = MEMBER_PACKAGE_REFERENCE.matcher(strippedOf(source, false));

            while (matcher.find()) {
                referenced.add(matcher.group());
            }
        }

        assertThat(referenced)
                .as("허용 목록이 실제로 쓰이는 이름인지 확인한다. 이름이 바뀌면 목록도 고친다")
                .containsExactlyInAnyOrderElementsOf(ALLOWED_MEMBER_TYPES);
    }

    /** 커뮤니티 매퍼 XML 전부. 하나도 못 읽었다면 경로가 바뀐 것이다. */
    private List<Path> mapperXmlFiles() throws IOException {
        List<Path> mappers;

        try (Stream<Path> paths = Files.list(MAPPER_DIRECTORY)) {
            mappers = paths.filter(path -> path.toString().endsWith(".xml")).sorted().toList();
        }

        assertThat(mappers).as("커뮤니티 매퍼 XML이 있어야 한다").isNotEmpty();

        return mappers;
    }

    /** 커뮤니티 문서 전부와 진입점. 디렉터리로 훑으므로 새 문서가 저절로 들어온다. */
    private List<Path> documents() throws IOException {
        List<Path> documents = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(DOC_DIRECTORY)) {
            paths.filter(path -> path.toString().endsWith(".md")).sorted().forEach(documents::add);
        }

        assertThat(documents).as("커뮤니티 문서를 하나도 못 읽었다면 경로가 바뀐 것이다").isNotEmpty();
        assertThat(DOMAIN_ENTRY_POINT).as("도메인 진입점이 있어야 한다").exists();

        documents.add(DOMAIN_ENTRY_POINT);

        return documents;
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

    /**
     * 파일에서 주석을 걷어낸다. 확장자에 따라 주석 문법이 다르다.
     *
     * <p>주석을 걷어내는 이유는 {@code CommunityCommentScopeTests}와 같다 — 규칙을 설명하는
     * 주석이 그 자체로 위반이 되면, 다음 사람은 설명을 지워서 초록불을 만든다.
     *
     * <p>XML은 XML 주석만이 아니라 <b>SQL 주석</b>({@literal --} 줄 주석과 블록 주석)도
     * 걷어낸다. "여기서는 members 를 JOIN 하지 않고 회원 계약을 쓴다"고 SQL 옆에 적어 두는
     * 것은 아주 자연스러운 일인데, 그것만으로 CI가 빨간불이 되면 다음 사람이 지우는 것은
     * JOIN이 아니라 설명이다 (PR #144 Codex 리뷰). XML 주석을 먼저 걷어내야 한다 —
     * {@code <!--} 안에 {@code --}가 들어 있어서 순서를 바꾸면 서로 잡아먹는다.</p>
     */
    private String strippedOf(Path source, boolean upperCase) throws IOException {
        String text = Files.readString(source, StandardCharsets.UTF_8);

        String withoutComments = source.toString().endsWith(".java")
                ? withoutJavaComments(text)
                : withoutSqlComments(text.replaceAll("(?s)<!--.*?-->", " "));

        return upperCase ? withoutComments.toUpperCase() : withoutComments;
    }

    /** SQL 주석을 걷어낸다. 줄 주석({@literal --} 부터 줄 끝까지)과 블록 주석 둘 다. */
    private String withoutSqlComments(String sql) {
        return sql
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("--[^\\n]*", " ");
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
