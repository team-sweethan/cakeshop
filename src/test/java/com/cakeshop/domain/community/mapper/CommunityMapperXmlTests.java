package com.cakeshop.domain.community.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
            "mapper/community/CommunityCommentMapper.xml", CommunityCommentMapper.class,
            "mapper/community/CommunityAdminMapper.xml", CommunityAdminMapper.class,
            "mapper/community/CommunityNoticeMapper.xml", CommunityNoticeMapper.class,
            "mapper/community/CommunityPopularPostMapper.xml", CommunityPopularPostMapper.class);

    private static final String NOTICE_MAPPER_XML = "mapper/community/CommunityNoticeMapper.xml";

    private static final String[] VISIBLE_NOTICE_STATEMENTS = {
            "selectVisibleNotices", "countVisibleNotices", "selectVisibleNoticeById"};

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
        assertThat(normalizedSql("findPublishedPosts", Map.of("sort", PostSort.LIKES)))
                .contains("ORDER BY P.LIKE_COUNT DESC, P.ID DESC");
    }

    /**
     * 댓글 트리의 규칙이 SQL 형태에 있음을 고정한다. 뿌리 목록의 답글 제외, 답글의 오래된 순,
     * 그리고 답글 조건부 INSERT의 세 조건(같은 글·뿌리·노출 중 부모)은 데이터가 적을 때
     * 형태가 무너져도 결과가 같아 보인다.
     */
    @Test
    void commentThreadStatements_keepTreeConditionsInSql() {
        assertThat(normalizedSql("findRecentRootComments"))
                .contains("PARENT_COMMENT_ID IS NULL")
                .contains("ORDER BY C.CREATED_AT DESC, C.ID DESC");

        assertThat(normalizedSql("findRepliesByParentId", Map.of("parentCommentId", 1L)))
                .contains("ORDER BY C.CREATED_AT DESC, C.ID DESC")
                .contains("C.POST_ID = ?");

        assertThat(normalizedSql("insertReply", Map.of("parentCommentId", 1L)))
                .contains("SELECT PARENT.POST_ID")
                .contains("PARENT.POST_ID = ?")
                .contains("PARENT.PARENT_COMMENT_ID IS NULL")
                .contains("PARENT.STATUS = 'PUBLISHED'")
                .contains("JOIN POSTS P ON P.ID = PARENT.POST_ID AND P.STATUS = 'PUBLISHED'");
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

    /** 좋아요 카운터는 노출 조건·중복 여부를 한 UPDATE 안에서 판단한다(increaseViewCount와 같은 모양). */
    @Test
    void likeCounterUpdates_checkStatusAndRowStateAtomically() {
        assertThat(normalizedSql("increaseLikeCount"))
                .contains("UPDATE POSTS P")
                .contains("P.LIKE_COUNT = P.LIKE_COUNT + 1")
                .contains("P.UPDATED_AT = P.UPDATED_AT")
                .contains("P.STATUS = 'PUBLISHED'")
                .contains("NOT EXISTS");

        assertThat(normalizedSql("decreaseLikeCount"))
                .contains("UPDATE POSTS P")
                .contains("P.LIKE_COUNT = P.LIKE_COUNT - 1")
                .contains("P.UPDATED_AT = P.UPDATED_AT")
                .contains("P.STATUS = 'PUBLISHED'")
                .contains("EXISTS")
                .doesNotContain("NOT EXISTS");
    }

    /** 중복 삼킴이 UPDATE 가드로 옮겨 갔으므로 INSERT는 맨몸이어야 한다 — UNIQUE가 경보로 남는다. */
    @Test
    void insertLike_hasNoDuplicateSwallowing() {
        assertThat(normalizedSql("insertLike"))
                .doesNotContain("ON DUPLICATE")
                .doesNotContain("IGNORE");
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

    @Test
    void visibleNoticeQueries_allCarryTheThreeConditions() {
        for (String statement : VISIBLE_NOTICE_STATEMENTS) {
            assertThat(normalizedSql(statement))
                    .as(statement)
                    .contains("N.STATUS = 'PUBLISHED'")
                    .contains("N.STARTS_AT IS NULL OR N.STARTS_AT <= ?")
                    .contains("N.ENDS_AT IS NULL OR ? < N.ENDS_AT");
        }
    }

    @Test
    void visibleNoticeCondition_isWrittenOnlyOnce() throws Exception {
        String xml = mapperXml(NOTICE_MAPPER_XML).replaceAll("\\s+", " ");

        assertThat(countOccurrences(xml, "n.starts_at IS NULL")).isEqualTo(1);
        assertThat(countOccurrences(xml, "n.ends_at IS NULL")).isEqualTo(1);
        assertThat(countOccurrences(xml, "<include refid=\"visibleNotice\"/>"))
                .isEqualTo(VISIBLE_NOTICE_STATEMENTS.length);
    }

    @Test
    void adminNoticeQueries_showEveryStatusAndPeriod() {
        for (String statement
                : new String[]{"selectAdminNotices", "countAdminNotices", "selectAdminNoticeById"}) {
            assertThat(normalizedSql(statement))
                    .as(statement)
                    .doesNotContain("N.STARTS_AT IS NULL", "N.STATUS = 'PUBLISHED'");
        }
    }

    @Test
    void visibleNotices_ordersByEffectiveDateWithIdTiebreaker() {
        assertThat(normalizedSql("selectVisibleNotices"))
                .contains("ORDER BY COALESCE(N.STARTS_AT, N.CREATED_AT) DESC, N.ID DESC");
    }

    @Test
    void adminNotices_orderByRegisteredAtWithIdTiebreaker() {
        assertThat(normalizedSql("selectAdminNotices"))
                .contains("ORDER BY N.CREATED_AT DESC, N.ID DESC");
    }

    @Test
    void noticeWrites_conditionOnCurrentStatus() {
        assertThat(normalizedSql("updateNotice")).contains("N.STATUS = 'PUBLISHED'");
        assertThat(normalizedSql("deleteNotice")).contains("N.STATUS = 'PUBLISHED'");
    }

    private String mapperXml(String resource) throws Exception {
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
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
        parameters.put("noticeId", 1L);
        parameters.put("now", LocalDateTime.of(2026, 8, 4, 10, 0));
        parameters.put("title", "제목");
        parameters.put("content", "본문");
        parameters.put("startsAt", null);
        parameters.put("endsAt", null);
        parameters.put("createdBy", 1L);
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
