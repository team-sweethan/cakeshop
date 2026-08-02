package com.cakeshop.domain.community.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XML을 파싱해 SQL의 <b>형태</b>를 고정한다. DB를 띄우지 않는다.
 *
 * <p>여기서 잡는 것은 "결과는 같지만 방식이 틀린" 변경이다. 결과가 같으니 화면으로도,
 * 실행 쿼리 수 측정으로도 드러나지 않는다(PLAN.md R2).
 */
class CommunityMapperXmlTests {

    private static final String RESOURCE = "mapper/community/CommunityMapper.xml";
    private static final String NAMESPACE = CommunityMapper.class.getName();

    private Configuration configuration;

    @BeforeEach
    void parseMapperXml() throws Exception {
        configuration = new Configuration();

        try (InputStream inputStream = Resources.getResourceAsStream(RESOURCE)) {
            new XMLMapperBuilder(
                    inputStream,
                    configuration,
                    RESOURCE,
                    configuration.getSqlFragments()
            ).parse();
        }
    }

    /** Mapper 인터페이스와 XML의 연결 누락을 잡는 안전장치 (선례: OrderMapperXmlTests). */
    @Test
    void mapperInterfaceAndXmlStatementsStaySynchronized() {
        Set<String> interfaceMethods = Arrays.stream(CommunityMapper.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        Set<String> xmlStatements = configuration.getMappedStatementNames().stream()
                .filter(name -> name.startsWith(NAMESPACE + "."))
                .map(name -> name.substring(NAMESPACE.length() + 1))
                .collect(Collectors.toSet());

        assertThat(xmlStatements).containsExactlyInAnyOrderElementsOf(interfaceMethods);
    }

    /**
     * H1a — 목록의 댓글 수 집계가 스칼라 서브쿼리 형태를 유지하는지 확인한다.
     *
     * <p>{@code LEFT JOIN comments ... GROUP BY p.id}로 바꿔도 화면 결과는 동일하다.
     * 하지만 LIMIT이 GROUP BY 이후에 적용되어 전체 게시글 × 전체 댓글을 집계한 뒤 20개를
     * 잘라내므로 게시글이 쌓일수록 느려진다(DOMAIN.md 6.1).
     */
    @Test
    void findPublishedPosts_aggregatesCommentCountWithScalarSubquery() {
        String sql = normalizedSql("findPublishedPosts");

        assertThat(sql).contains("SELECT COUNT(*) FROM COMMENTS");
        assertThat(sql).doesNotContain("GROUP BY");
        assertThat(sql).doesNotContain("JOIN COMMENTS");
    }

    /**
     * 목록 정렬에 id tiebreaker가 남아 있는지 확인한다.
     *
     * <p>{@code created_at}만으로 정렬하면 같은 시각에 작성된 글이 페이지 경계에서
     * 중복되거나 누락된다(DOMAIN.md 6.1).
     */
    @Test
    void findPublishedPosts_ordersByCreatedAtWithIdTiebreaker() {
        String sql = normalizedSql("findPublishedPosts");

        assertThat(sql).contains("ORDER BY P.CREATED_AT DESC, P.ID DESC");
    }

    /**
     * 노출 조건이 status 하나로만 판단되는지 확인한다.
     *
     * <p>{@code blocked_at IS NULL} 같은 조건이 늘어나면 새 쿼리에서 하나를 빠뜨려
     * 차단된 글이 노출된다(DOMAIN.md 4.1).
     */
    @Test
    void publishedPostQueries_judgeVisibilityByStatusOnly() {
        for (String statement : new String[]{"findPublishedPosts", "countPublishedPosts"}) {
            String sql = normalizedSql(statement);

            assertThat(sql).as(statement).contains("P.STATUS = 'PUBLISHED'");
            assertThat(sql).as(statement).doesNotContain("BLOCKED_AT");
        }
    }

    /**
     * 조회수 UPDATE가 {@code updated_at}을 명시적으로 보존하는지 확인한다.
     *
     * <p>{@code posts.updated_at}은 ON UPDATE CURRENT_TIMESTAMP다. 이 지정을 빼면
     * 조회만 해도 값이 바뀌어 "수정됨" 표시(DOMAIN.md 6.3)가 켜진다.
     * 실제 동작은 {@code CommunityMapperTests}가 DB로 확인한다.
     */
    @Test
    void increaseViewCount_keepsUpdatedAtUntouched() {
        String sql = normalizedSql("increaseViewCount");

        assertThat(sql).contains("UPDATED_AT = UPDATED_AT");
        assertThat(sql).contains("STATUS = 'PUBLISHED'");
    }

    /** 공백을 하나로 줄이고 대문자로 바꿔 들여쓰기·줄바꿈 차이를 무시한다. */
    private String normalizedSql(String statementId) {
        MappedStatement statement =
                configuration.getMappedStatement(NAMESPACE + "." + statementId);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("categoryId", null);
        parameters.put("postId", 1L);
        parameters.put("size", 20);
        parameters.put("offset", 0);

        return statement.getBoundSql(parameters)
                .getSql()
                .replaceAll("\\s+", " ")
                .toUpperCase();
    }
}
