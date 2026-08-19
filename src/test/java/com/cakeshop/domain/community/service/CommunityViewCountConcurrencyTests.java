package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.global.config.MariaDbIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** 동시 조회가 조회수를 잃거나 부풀리지 않는지 확인한다. */
@SpringBootTest
@MariaDbIntegrationTest
class CommunityViewCountConcurrencyTests {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 3, 1, 10, 0);
    private static final int THREADS = 8;

    @Autowired
    private CommunityPostService communityPostService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long categoryId;
    private long memberId;
    private long postId;

    @BeforeEach
    void setUp() {
        String suffix = Long.toString(System.nanoTime());

        jdbcTemplate.update(
                """
                INSERT INTO post_categories (code, name, is_active, sort_order)
                VALUES (?, ?, 1, 999)
                """,
                "VIEW_RACE_" + suffix, "동시성 테스트");
        categoryId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbcTemplate.update(
                """
                INSERT INTO members (
                    email, password, nickname, phone, role, status, name, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, 'USER', 'ACTIVE', ?, ?, ?)
                """,
                "view-race-" + suffix + "@cakeshop.local", "encoded-password",
                "동시성", "010-0000-0000", "동시성", BASE_TIME, BASE_TIME);
        memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbcTemplate.update(
                """
                INSERT INTO posts (
                    member_id, category_id, title, content, status, created_at, updated_at
                )
                VALUES (?, ?, '제목', '본문', ?, ?, ?)
                """,
                memberId, categoryId, PostStatus.PUBLISHED.name(), BASE_TIME, BASE_TIME);
        postId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /** 픽스처를 자식부터 정리한다. */
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM post_views WHERE post_id = ?", postId);
        jdbcTemplate.update("DELETE FROM posts WHERE id = ?", postId);
        jdbcTemplate.update("DELETE FROM members WHERE id = ?", memberId);
        jdbcTemplate.update("DELETE FROM post_categories WHERE id = ?", categoryId);
    }

    @Test
    void getPostDetail_concurrentViewsBySameViewer_countOnlyOnce() throws Exception {
        runConcurrently(() -> communityPostService.getPostDetail(postId, null, "S:racer"));

        assertThat(viewCount())
                .as("같은 조회자의 동시 요청은 한 번만 세어야 한다")
                .isEqualTo(1);
        assertThat(viewCount())
                .as("view_count는 post_views에서 파생된 캐시다. 어긋나면 순위에 쓸 수 없다")
                .isEqualTo(viewHistoryCount());
    }

    /** 서로 다른 조회자의 동시 요청을 모두 반영한다. */
    @Test
    void getPostDetail_concurrentViewsByDifferentViewers_countEach() throws Exception {
        List<String> keys = new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            keys.add("S:racer-" + i);
        }

        runConcurrentlyWith(keys);

        assertThat(viewCount()).isEqualTo(THREADS);
        assertThat(viewCount()).isEqualTo(viewHistoryCount());
    }

    /** 조회 창 이후 동시 재조회는 한 번만 추가 반영한다. */
    @Test
    void getPostDetail_concurrentViewsAfterWindow_countOnlyOnceMore() throws Exception {
        runConcurrently(() -> communityPostService.getPostDetail(postId, null, "S:racer"));
        ageViews(11);

        runConcurrently(() -> communityPostService.getPostDetail(postId, null, "S:racer"));

        assertThat(viewCount())
                .as("창이 열린 순간에도 동시 요청은 한 번만 세어야 한다")
                .isEqualTo(2);
        assertThat(viewCount()).isEqualTo(viewHistoryCount());
    }

    /** 조회 이력을 창 밖으로 이동한다. */
    private void ageViews(int minutes) {
        jdbcTemplate.update(
                "UPDATE post_views SET created_at = created_at - INTERVAL ? MINUTE"
                        + " WHERE post_id = ?",
                minutes, postId);
    }

    private void runConcurrently(Runnable action) throws Exception {
        List<String> unused = new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            unused.add(null);
        }

        execute(unused, key -> action.run());
    }

    private void runConcurrentlyWith(List<String> viewerKeys) throws Exception {
        execute(viewerKeys, key -> communityPostService.getPostDetail(postId, null, key));
    }

    /** 모든 작업을 같은 시점에 시작한다. */
    private void execute(List<String> arguments, java.util.function.Consumer<String> action)
            throws Exception {
        CyclicBarrier startLine = new CyclicBarrier(arguments.size());
        ExecutorService pool = Executors.newFixedThreadPool(arguments.size());

        try {
            List<Callable<Void>> calls = new ArrayList<>();

            for (String argument : arguments) {
                calls.add(() -> {
                    startLine.await(10, TimeUnit.SECONDS);
                    action.accept(argument);
                    return null;
                });
            }

            for (Future<Void> done : pool.invokeAll(calls)) {
                // 작업 스레드의 예외를 전달한다.
                done.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    private long viewCount() {
        return jdbcTemplate.queryForObject(
                "SELECT view_count FROM posts WHERE id = ?", Long.class, postId);
    }

    private long viewHistoryCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_views WHERE post_id = ?", Long.class, postId);
    }
}
