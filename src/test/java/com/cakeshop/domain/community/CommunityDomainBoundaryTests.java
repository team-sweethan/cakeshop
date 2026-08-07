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

    /** 낱말 단위로 본다. {@code p.member_id}는 members 참조가 아니다. */
    private static final Pattern MEMBERS_TABLE = Pattern.compile("\\bMEMBERS\\b");

    /**
     * 커뮤니티 SQL이 members 테이블을 건드리지 않는지 확인한다.
     *
     * <p>JOIN만이 아니라 어떤 형태의 참조도 잡는다. 서브쿼리나 {@code EXISTS}로 우회하면
     * JOIN이라는 낱말은 없지만 소유권을 넘는 것은 똑같다.
     */
    @Test
    void communityMapperXml_doesNotTouchMembersTable() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path mapperXml : mapperXmlFiles()) {
            if (MEMBERS_TABLE.matcher(strippedOf(mapperXml)).find()) {
                violations.add(mapperXml.toString());
            }
        }

        assertThat(violations)
                .as("커뮤니티 SQL은 members 를 조회하지 않는다. 작성자는"
                        + " MemberCommunityQueryService 로 받는다 (conventions.md 15.1, 조각 10)")
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
     */
    private String strippedOf(Path source, boolean upperCase) throws IOException {
        String text = Files.readString(source, StandardCharsets.UTF_8);

        String withoutComments = source.toString().endsWith(".java")
                ? withoutJavaComments(text)
                : text.replaceAll("(?s)<!--.*?-->", " ");

        return upperCase ? withoutComments.toUpperCase() : withoutComments;
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
