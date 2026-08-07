package com.cakeshop.domain.community.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.cakeshop.domain.community.dto.view.AdminPostSort;
import com.cakeshop.domain.community.dto.view.PostSort;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 커뮤니티 Mapper SQL의 핵심 구조를 검증한다. */
class CommunityMapperXmlTests {

    private static final Map<String, Class<?>> MAPPERS = Map.of(
            "mapper/community/CommunityMapper.xml", CommunityMapper.class,
            "mapper/community/CommunityAdminMapper.xml", CommunityAdminMapper.class);

    private Configuration configuration;

    @BeforeEach
    void parseMapperXml() throws Exception {
        configuration = new Configuration();

        for (String resource : MAPPERS.keySet()) {
            try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(
                        inputStream, configuration, resource, configuration.getSqlFragments())
                        .parse();
            }
        }
    }

    @Test
    void mapperInterfacesAndXmlStatementsStaySynchronized() {
        for (Class<?> mapper : MAPPERS.values()) {
            Set<String> interfaceMethods = Arrays.stream(mapper.getDeclaredMethods())
                    .map(Method::getName)
                    .collect(Collectors.toSet());
            String namespace = mapper.getName();
            Set<String> xmlStatements = configuration.getMappedStatementNames().stream()
                    .filter(name -> name.startsWith(namespace + "."))
                    .map(name -> name.substring(namespace.length() + 1))
                    .collect(Collectors.toSet());

            assertThat(xmlStatements).as(mapper.getSimpleName())
                    .containsExactlyInAnyOrderElementsOf(interfaceMethods);
        }
    }

    @Test
    void findPublishedPosts_aggregatesCommentCountWithScalarSubquery() {
        String sql = normalizedSql("findPublishedPosts");

        assertThat(sql).contains("SELECT COUNT(*) FROM COMMENTS");
        assertThat(sql).doesNotContain("GROUP BY", "JOIN COMMENTS");
    }

    @Test
    void findPublishedPosts_mapsEverySortToExpectedBoundSql() {
        assertThat(normalizedSql("findPublishedPosts", Map.of("sort", PostSort.LATEST)))
                .contains("ORDER BY P.CREATED_AT DESC, P.ID DESC");
        assertThat(normalizedSql("findPublishedPosts", Map.of("sort", PostSort.VIEWS)))
                .contains("ORDER BY P.VIEW_COUNT DESC, P.ID DESC");
    }

    @Test
    void communityMapperXml_doesNotUseStringSubstitution() throws Exception {
        for (String resource : MAPPERS.keySet()) {
            try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
                String xml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                assertThat(xml).as(resource).doesNotContain("${");
            }
        }
    }

    @Test
    void publishedPostQueries_judgeVisibilityByStatusOnly() {
        for (String statement : new String[]{"findPublishedPosts", "countPublishedPosts"}) {
            assertThat(normalizedSql(statement))
                    .as(statement)
                    .contains("P.STATUS = 'PUBLISHED'")
                    .doesNotContain("BLOCKED_AT");
        }
    }

    @Test
    void increaseViewCount_filtersDuplicatesWhileLockingPostFirst() {
        assertThat(normalizedSql("increaseViewCount"))
                .contains("UPDATE POSTS P")
                .contains("NOT EXISTS")
                .contains("FROM POST_VIEWS")
                .contains("CREATED_AT > NOW(6) - INTERVAL 10 MINUTE");
    }

    @Test
    void lockPost_locksOnlyPostRow() {
        assertThat(normalizedSql("lockPost"))
                .contains("FROM POSTS", "FOR UPDATE")
                .doesNotContain("JOIN");
    }

    @Test
    void findPostsForAdmin_countsPendingReportsWithScalarSubquery() {
        String sql = normalizedSql("findPostsForAdmin");

        assertThat(sql).contains("SELECT COUNT(*) FROM POST_REPORTS");
        assertThat(sql).doesNotContain("GROUP BY", "JOIN POST_REPORTS");
    }

    @Test
    void findPostsForAdmin_mapsEverySortToExpectedBoundSql() {
        assertThat(normalizedSql("findPostsForAdmin", Map.of("sort", AdminPostSort.LATEST)))
                .contains("ORDER BY P.CREATED_AT DESC, P.ID DESC");
        assertThat(normalizedSql("findPostsForAdmin", Map.of("sort", AdminPostSort.REPORTS)))
                .contains("ORDER BY PENDING_REPORT_COUNT DESC, P.ID DESC");
    }

    @Test
    void insertDailyRanking_appliesWindowToEverySource() {
        String sql = normalizedSql("insertDailyRanking");

        assertThat(countOccurrences(sql, "CREATED_AT >= ? - INTERVAL 6 DAY")).isEqualTo(3);
        assertThat(countOccurrences(sql, "CREATED_AT < ? + INTERVAL 1 DAY")).isEqualTo(3);
        assertThat(sql).contains("FROM POST_VIEWS", "FROM POST_LIKES", "FROM COMMENTS");
    }

    @Test
    void insertDailyRanking_breaksTiesInSelectionAndRanking() {
        String sql = normalizedSql("insertDailyRanking");

        assertThat(sql)
                .contains("ROW_NUMBER() OVER (ORDER BY S.POPULARITY_SCORE DESC, S.POST_ID DESC)")
                .contains("ORDER BY POPULARITY_SCORE DESC, E.POST_ID DESC LIMIT ?");
    }

    private int countOccurrences(String text, String target) {
        return (text.length() - text.replace(target, "").length()) / target.length();
    }

    private String normalizedSql(String statementId) {
        return normalizedSql(statementId, Map.of());
    }

    private String normalizedSql(String statementId, Map<String, Object> overrides) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("categoryId", null);
        parameters.put("postId", 1L);
        parameters.put("commentId", 1L);
        parameters.put("memberId", 1L);
        parameters.put("size", 20);
        parameters.put("offset", 0);
        parameters.put("limit", 20);
        parameters.put("viewerKey", "M:1");
        parameters.put("reporterId", 1L);
        parameters.put("reason", "사유");
        parameters.put("adminId", 1L);
        parameters.put("status", null);
        parameters.put("sort", null);
        parameters.put("rankingDate", LocalDate.of(2026, 8, 4));
        parameters.put("postCount", 20);
        parameters.putAll(overrides);

        return statementOf(statementId).getBoundSql(parameters).getSql()
                .replaceAll("\\s+", " ")
                .trim()
                .toUpperCase();
    }

    private MappedStatement statementOf(String statementId) {
        for (Class<?> mapper : MAPPERS.values()) {
            String name = mapper.getName() + "." + statementId;
            if (configuration.hasStatement(name)) {
                return configuration.getMappedStatement(name);
            }
        }

        throw new IllegalArgumentException("커뮤니티 Mapper에 없는 문장이다: " + statementId);
    }
}
