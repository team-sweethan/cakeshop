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
import java.util.function.Consumer;

import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.global.config.MariaDbIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** 동시 좋아요 후 캐시와 실제 행 수가 일치하는지 확인한다. */
@SpringBootTest
@MariaDbIntegrationTest
class CommunityLikeConcurrencyTests {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 3, 1, 10, 0);
    private static final int MEMBERS = 8;

    @Autowired
    private CommunityReactionService communityReactionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long categoryId;
    private long postId;
    private final List<Long> memberIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String suffix = Long.toString(System.nanoTime());

        jdbcTemplate.update(
                """
                INSERT INTO post_categories (code, name, is_active, sort_order)
                VALUES (?, ?, 1, 999)
                """,
                "LIKE_RACE_" + suffix, "좋아요 동시성");
        categoryId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        for (int i = 0; i < MEMBERS; i++) {
            jdbcTemplate.update(
                    """
                    INSERT INTO members (
                        email, password, nickname, phone, role, status, name, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, 'USER', 'ACTIVE', ?, ?, ?)
                    """,
                    "like-race-" + i + "-" + suffix + "@cakeshop.local", "encoded-password",
                    "좋아요" + i, "010-0000-0000", "좋아요" + i, BASE_TIME, BASE_TIME);
            memberIds.add(jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
        }

        jdbcTemplate.update(
                """
                INSERT INTO posts (
                    member_id, category_id, title, content, status, created_at, updated_at
                )
                VALUES (?, ?, '제목', '본문', ?, ?, ?)
                """,
                memberIds.get(0), categoryId, PostStatus.PUBLISHED.name(), BASE_TIME, BASE_TIME);
        postId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /** 픽스처를 자식부터 정리한다. */
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM post_likes WHERE post_id = ?", postId);
        jdbcTemplate.update("DELETE FROM posts WHERE id = ?", postId);

        for (Long id : memberIds) {
            jdbcTemplate.update("DELETE FROM members WHERE id = ?", id);
        }

        jdbcTemplate.update("DELETE FROM post_categories WHERE id = ?", categoryId);
    }

    /** 서로 다른 회원의 동시 좋아요를 모두 반영한다. */
    @Test
    void addLike_concurrentLikesByDifferentMembers_countsEachWithoutDeadlock() throws Exception {
        execute(memberIds, memberId -> communityReactionService.addLike(postId, memberId));

        assertThat(likeCount())
                .as("서로 다른 회원의 좋아요는 모두 세어야 한다")
                .isEqualTo(MEMBERS);
        assertThat(likeCount())
                .as("like_count는 post_likes에서 다시 센 값이다. 어긋나면 6.5의 근거가 무너진다")
                .isEqualTo(likeRowsCount());
    }

    /** 같은 회원의 동시 좋아요는 한 번만 반영한다. */
    @Test
    void addLike_concurrentLikesBySameMember_countsOnce() throws Exception {
        long memberId = memberIds.get(0);
        List<Long> sameMember = new ArrayList<>();

        for (int i = 0; i < MEMBERS; i++) {
            sameMember.add(memberId);
        }

        execute(sameMember, id -> communityReactionService.addLike(postId, id));

        assertThat(likeCount())
                .as("멱등이므로 같은 회원이 여러 번 눌러도 1이다")
                .isEqualTo(1);
        assertThat(likeCount()).isEqualTo(likeRowsCount());
    }

    /** 동시 추가와 취소 후에도 캐시와 행 수가 일치한다. */
    @Test
    void likeAndUnlike_concurrentMixed_keepsCountConsistentWithRows() throws Exception {
        // 절반은 재추가하고 절반은 취소한다.
        for (Long memberId : memberIds) {
            communityReactionService.addLike(postId, memberId);
        }

        execute(memberIds, memberId -> {
            if (memberIds.indexOf(memberId) % 2 == 0) {
                communityReactionService.addLike(postId, memberId);
            } else {
                communityReactionService.removeLike(postId, memberId);
            }
        });

        assertThat(likeCount())
                .as("절반이 거둬 갔으므로 절반이 남는다")
                .isEqualTo(MEMBERS / 2);
        assertThat(likeCount()).isEqualTo(likeRowsCount());
    }

    /** 모든 작업을 같은 시점에 시작한다. */
    private void execute(List<Long> arguments, Consumer<Long> action) throws Exception {
        CyclicBarrier startLine = new CyclicBarrier(arguments.size());
        ExecutorService pool = Executors.newFixedThreadPool(arguments.size());

        try {
            List<Callable<Void>> calls = new ArrayList<>();

            for (Long argument : arguments) {
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

    private long likeCount() {
        return jdbcTemplate.queryForObject(
                "SELECT like_count FROM posts WHERE id = ?", Long.class, postId);
    }

    private long likeRowsCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_likes WHERE post_id = ?", Long.class, postId);
    }
}
