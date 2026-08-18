package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.dto.view.AdminPostSort;
import com.cakeshop.domain.community.dto.view.PostSort;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.domain.member.service.MemberCommunityQueryService;
import com.cakeshop.global.config.ClockConfig;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import com.cakeshop.global.infra.FileStorageClient;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 주요 조회의 쿼리 수가 데이터 건수에 따라 증가하지 않는지 확인한다. */
@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        CommunityPostService.class,
        CommunityCommentService.class,
        CommunityAdminService.class,
        MemberCommunityQueryService.class,
        CommunityMemberViewLoader.class,
        CommunityPostAccessPolicy.class,
        CommunityPostImageService.class,
        CommunityImageValidator.class,
        PopularPostReader.class,
        ClockConfig.class})
class CommunityQueryCountTests {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 3, 1, 10, 0);

    /** 목록, 개수, 작성자 조회 횟수다. */
    private static final int EXPECTED_QUERY_COUNT = 3;

    /** 뿌리 목록, 전체 개수, 뿌리별 답글 수, 작성자 조회 횟수다. */
    private static final int EXPECTED_COMMENT_QUERY_COUNT = 4;

    /** 위 넷에 펼친 묶음의 답글 목록 조회 하나가 더해진다. */
    private static final int EXPECTED_EXPANDED_COMMENT_QUERY_COUNT = 5;

    /** 관리자 목록, 개수, 작성자 조회 횟수다. */
    private static final int EXPECTED_ADMIN_QUERY_COUNT = 3;

    @MockitoBean
    private FileStorageClient fileStorageClient;

    @Autowired
    private CommunityPostService communityPostService;

    @Autowired
    private CommunityCommentService communityCommentService;

    @Autowired
    private CommunityAdminService communityAdminService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ExecutedQueryCounter queryCounter;

    private long categoryId;

    @BeforeEach
    void setUp() {
        String suffix = Long.toString(System.nanoTime());

        jdbcTemplate.update(
                """
                INSERT INTO post_categories (code, name, is_active, sort_order)
                VALUES (?, ?, 1, 999)
                """,
                "QUERY_COUNT_" + suffix, "쿼리 수 테스트");
        categoryId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

    }

    /** N+1 조회를 드러내도록 서로 다른 작성자를 만든다. */
    private long newMember() {
        String suffix = Long.toString(System.nanoTime());

        jdbcTemplate.update(
                """
                INSERT INTO members (
                    email, password, nickname, phone, role, status, name, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, 'USER', 'ACTIVE', ?, ?, ?)
                """,
                "query-count-" + suffix + "@cakeshop.local", "encoded-password",
                "쿼리수" + suffix, "010-0000-0000", "쿼리수", BASE_TIME, BASE_TIME);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test
    void getPosts_queryCount_doesNotGrowWithPostCount() {
        insertPosts(3, 2);

        queryCounter.reset();
        communityPostService.getPosts(categoryId, PostSort.LATEST, new PageRequest(1, 20));
        int withFewPosts = queryCounter.count();

        insertPosts(20, 2);

        queryCounter.reset();
        communityPostService.getPosts(categoryId, PostSort.LATEST, new PageRequest(1, 20));
        int withManyPosts = queryCounter.count();

        assertThat(withFewPosts).isEqualTo(EXPECTED_QUERY_COUNT);
        assertThat(withManyPosts).isEqualTo(EXPECTED_QUERY_COUNT);
    }

    @Test
    void getPostDetail_queryCount_isFixed() {
        long postId = insertPost();

        queryCounter.reset();
        communityPostService.getPostDetail(postId, null, "M:1");

        // 상세와 작성자를 한 번씩 조회한다.
        assertThat(queryCounter.count()).isEqualTo(2);
    }

    @Test
    void getComments_queryCount_doesNotGrowWithCommentCount() {
        long postId = insertPost();
        insertComments(postId, 3);

        queryCounter.reset();
        communityCommentService.getComments(postId, null, null);
        int withFewComments = queryCounter.count();

        insertComments(postId, 20);

        queryCounter.reset();
        communityCommentService.getComments(postId, null, null);
        int withManyComments = queryCounter.count();

        assertThat(withFewComments).isEqualTo(EXPECTED_COMMENT_QUERY_COUNT);
        assertThat(withManyComments).isEqualTo(EXPECTED_COMMENT_QUERY_COUNT);
    }

    /** 묶음을 펼쳐도 조회는 한 번만 늘고, 답글 수와는 무관하다. */
    @Test
    void getComments_expandedThread_addsSingleQueryRegardlessOfReplyCount() {
        long postId = insertPost();
        long rootId = insertRootComment(postId);
        insertReplies(postId, rootId, 3);

        queryCounter.reset();
        communityCommentService.getComments(postId, null, rootId);
        int withFewReplies = queryCounter.count();

        insertReplies(postId, rootId, 20);

        queryCounter.reset();
        communityCommentService.getComments(postId, null, rootId);
        int withManyReplies = queryCounter.count();

        assertThat(withFewReplies).isEqualTo(EXPECTED_EXPANDED_COMMENT_QUERY_COUNT);
        assertThat(withManyReplies).isEqualTo(EXPECTED_EXPANDED_COMMENT_QUERY_COUNT);
    }

    @Test
    void adminGetPosts_queryCount_doesNotGrowWithPostCount() {
        insertPosts(3, 0);

        queryCounter.reset();
        communityAdminService.getPosts(null, AdminPostSort.LATEST, new PageRequest(1, 100));
        int withFewPosts = queryCounter.count();

        insertPosts(20, 0);

        queryCounter.reset();
        communityAdminService.getPosts(null, AdminPostSort.LATEST, new PageRequest(1, 100));
        int withManyPosts = queryCounter.count();

        assertThat(withFewPosts).isEqualTo(EXPECTED_ADMIN_QUERY_COUNT);
        assertThat(withManyPosts).isEqualTo(EXPECTED_ADMIN_QUERY_COUNT);
    }

    private long insertRootComment(long postId) {
        jdbcTemplate.update(
                """
                INSERT INTO comments (post_id, member_id, content, status)
                VALUES (?, ?, '뿌리 댓글', 'PUBLISHED')
                """,
                postId, newMember());

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertReplies(long postId, long parentId, int count) {
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update(
                    """
                    INSERT INTO comments (post_id, member_id, parent_comment_id, content, status)
                    VALUES (?, ?, ?, '답글', 'PUBLISHED')
                    """,
                    postId, newMember(), parentId);
        }
    }

    private void insertComments(long postId, int count) {
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update(
                    """
                    INSERT INTO comments (post_id, member_id, content, status)
                    VALUES (?, ?, '댓글', 'PUBLISHED')
                    """,
                    postId, newMember());
        }
    }

    private void insertPosts(int postCount, int commentsPerPost) {
        for (int i = 0; i < postCount; i++) {
            long postId = insertPost();

            for (int c = 0; c < commentsPerPost; c++) {
                jdbcTemplate.update(
                        """
                        INSERT INTO comments (post_id, member_id, content, status)
                        VALUES (?, ?, '댓글', 'PUBLISHED')
                        """,
                        postId, newMember());
            }
        }
    }

    private long insertPost() {
        jdbcTemplate.update(
                """
                INSERT INTO posts (
                    member_id, category_id, title, content, status, created_at, updated_at
                )
                VALUES (?, ?, '제목', '본문', ?, ?, ?)
                """,
                newMember(), categoryId, PostStatus.PUBLISHED.name(), BASE_TIME, BASE_TIME);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @TestConfiguration
    static class QueryCountingConfig {

        @Bean
        ExecutedQueryCounter executedQueryCounter() {
            return new ExecutedQueryCounter();
        }
    }

    /** MyBatis SELECT 실행 횟수를 센다. */
    @Intercepts(@Signature(
            type = Executor.class,
            method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}
    ))
    static class ExecutedQueryCounter implements Interceptor {

        private final AtomicInteger executed = new AtomicInteger();

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            executed.incrementAndGet();
            return invocation.proceed();
        }

        void reset() {
            executed.set(0);
        }

        int count() {
            return executed.get();
        }
    }
}
