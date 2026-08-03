package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.config.MariaDbIntegrationTest;

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

/**
 * H1b — 목록 조회가 게시글 수와 무관하게 정해진 횟수의 쿼리만 실행하는지 확인한다.
 *
 * <p>목록 1회 + 총 개수 1회, 합쳐서 2회다. 여기서 잡으려는 것은 댓글 수·작성자·카테고리를
 * 게시글마다 따로 조회하는 형태(N+1)로 바뀌는 변경이다. 화면 결과가 같아 눈으로는 드러나지
 * 않고, 게시글이 늘어야 느려지므로 개발 데이터에서는 멀쩡해 보인다.
 *
 * <p>쿼리 <b>형태</b>가 {@code GROUP BY}로 바뀌는 것은 이 테스트로 잡히지 않는다.
 * 그때도 쿼리는 여전히 2회다(PLAN.md R2). 그쪽은 H1a가 맡는다.
 */
@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CommunityService.class)
class CommunityQueryCountTests {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 3, 1, 10, 0);

    /** 목록 조회 한 번에 실행되어야 하는 쿼리 수. 목록 1 + 총 개수 1. */
    private static final int EXPECTED_QUERY_COUNT = 2;

    /** 댓글 구역 한 번에 실행되어야 하는 쿼리 수. 댓글 목록 1 + 개수 1. */
    private static final int EXPECTED_COMMENT_QUERY_COUNT = 2;

    @Autowired
    private CommunityService communityService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ExecutedQueryCounter queryCounter;

    private long categoryId;
    private long memberId;

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

        jdbcTemplate.update(
                """
                INSERT INTO members (
                    email, password, nickname, phone, role, status, name, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, 'USER', 'ACTIVE', ?, ?, ?)
                """,
                "query-count-" + suffix + "@cakeshop.local", "encoded-password",
                "쿼리수", "010-0000-0000", "쿼리수", BASE_TIME, BASE_TIME);
        memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test
    void getPosts_queryCount_doesNotGrowWithPostCount() {
        insertPosts(3, 2);

        queryCounter.reset();
        communityService.getPosts(categoryId, new PageRequest(1, 20));
        int withFewPosts = queryCounter.count();

        insertPosts(20, 2);

        queryCounter.reset();
        communityService.getPosts(categoryId, new PageRequest(1, 20));
        int withManyPosts = queryCounter.count();

        assertThat(withFewPosts).isEqualTo(EXPECTED_QUERY_COUNT);
        assertThat(withManyPosts).isEqualTo(EXPECTED_QUERY_COUNT);
    }

    @Test
    void getPostDetail_queryCount_isFixed() {
        long postId = insertPost();

        queryCounter.reset();
        communityService.getPostDetail(postId, null, "M:1");

        // 조회 기록(INSERT)과 조회수 UPDATE는 query가 아니라 update로 실행되므로
        // SELECT는 상세 1회뿐이다.
        //
        // 중복 판단을 "이미 봤는지 SELECT로 확인" 하는 형태로 바꾸면 이 수가 늘어난다.
        // DB의 UNIQUE가 판단하게 두면 늘지 않는다 — 그게 6.2가 제약을 쓰는 이유이기도 하다.
        assertThat(queryCounter.count()).isEqualTo(1);
    }

    /**
     * 댓글 구역이 댓글 수와 무관하게 정해진 횟수의 쿼리만 실행하는지 확인한다.
     *
     * <p>목록 1회 + 개수 1회, 합쳐서 2회다. 개수를 "전체 행"과 "노출 중"으로 따로 세느라
     * 쿼리를 하나 더 날리거나, 작성자를 댓글마다 조회하는 형태(N+1)로 바뀌는 것을 잡는다.
     * 둘 다 화면 결과가 같아서 눈으로는 드러나지 않는다.
     */
    @Test
    void getComments_queryCount_doesNotGrowWithCommentCount() {
        long postId = insertPost();
        insertComments(postId, 3);

        queryCounter.reset();
        communityService.getComments(postId, null);
        int withFewComments = queryCounter.count();

        insertComments(postId, 20);

        queryCounter.reset();
        communityService.getComments(postId, null);
        int withManyComments = queryCounter.count();

        assertThat(withFewComments).isEqualTo(EXPECTED_COMMENT_QUERY_COUNT);
        assertThat(withManyComments).isEqualTo(EXPECTED_COMMENT_QUERY_COUNT);
    }

    private void insertComments(long postId, int count) {
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update(
                    """
                    INSERT INTO comments (post_id, member_id, content, status)
                    VALUES (?, ?, '댓글', 'PUBLISHED')
                    """,
                    postId, memberId);
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
                        postId, memberId);
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
                memberId, categoryId, PostStatus.PUBLISHED.name(), BASE_TIME, BASE_TIME);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @TestConfiguration
    static class QueryCountingConfig {

        @Bean
        ExecutedQueryCounter executedQueryCounter() {
            return new ExecutedQueryCounter();
        }
    }

    /** MyBatis Executor를 통과하는 SELECT 실행 횟수를 센다. */
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
