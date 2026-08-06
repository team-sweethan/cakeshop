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
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.global.config.MariaDbIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 같은 조회자의 <b>동시</b> 조회가 조회수를 부풀리지 않는지 확인한다.
 *
 * <p>이 설계의 핵심이 여기 있다. "이미 봤는가"를 애플리케이션이 판단하면 동시 요청 두 개가
 * 그 판단을 <b>함께</b> 통과할 수 있고, 그러면 둘 다 조회수를 올린다. 판단을
 * {@code increaseViewCount} 안에 두어 게시글 행의 배타 잠금을 쥔 채로 평가해야 하나만
 * 살아남는다(docs/community/DOMAIN.md 6.2).
 *
 * <p><b>이 클래스가 유일한 방어선이다.</b> 창이 날짜 칸이던 시절에는
 * {@code uk_post_views_post_viewer_date}가 마지막으로 한 번 더 걸렀지만, 굴러가는 10분
 * 창은 UNIQUE로 표현할 수 없어 제약을 지웠다(V20260804_102934). 잠금 순서가 깨져도
 * 이제 DB는 아무 말도 하지 않는다 — 여기서 잡지 못하면 조회수가 조용히 부푼다.
 *
 * <p>단일 스레드 테스트로는 절대 드러나지 않는 종류의 어긋남이고, 화면에는 숫자가 조금
 * 큰 모습으로만 나타나서 눈으로도 찾을 수 없다.
 *
 * <p><b>이 클래스만 트랜잭션 롤백을 쓰지 않는다.</b> {@code @MybatisTest}처럼 테스트를
 * 트랜잭션으로 감싸면 다른 스레드가 이 테스트의 게시글을 <b>아예 볼 수 없어</b> 경쟁 자체가
 * 일어나지 않는다. 통과하지만 아무것도 검증하지 않는 테스트가 된다. 대신 넣은 행을
 * {@link #cleanUp()}에서 직접 지운다.
 */
@SpringBootTest
@MariaDbIntegrationTest
class CommunityViewCountConcurrencyTests {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 3, 1, 10, 0);
    private static final int THREADS = 8;

    @Autowired
    private CommunityService communityService;

    @Autowired
    private CommunityMapper communityMapper;

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

    /** 롤백이 없으므로 직접 지운다. 자식 → 부모 순이다. */
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM post_views WHERE post_id = ?", postId);
        jdbcTemplate.update("DELETE FROM posts WHERE id = ?", postId);
        jdbcTemplate.update("DELETE FROM members WHERE id = ?", memberId);
        jdbcTemplate.update("DELETE FROM post_categories WHERE id = ?", categoryId);
    }

    @Test
    void getPostDetail_concurrentViewsBySameViewer_countOnlyOnce() throws Exception {
        runConcurrently(() -> communityService.getPostDetail(postId, null, "S:racer"));

        assertThat(viewCount())
                .as("같은 조회자의 동시 요청은 한 번만 세어야 한다")
                .isEqualTo(1);
        assertThat(viewCount())
                .as("view_count는 post_views에서 파생된 캐시다. 어긋나면 순위에 쓸 수 없다")
                .isEqualTo(communityMapper.countViews(postId));
    }

    /**
     * 서로 다른 조회자의 동시 요청은 모두 세어지는지 확인한다.
     *
     * <p>위 테스트만 있으면 "무조건 1"로 만들어도 통과한다. 중복을 막는 것과 조회를 잃는
     * 것은 다르다.
     */
    @Test
    void getPostDetail_concurrentViewsByDifferentViewers_countEach() throws Exception {
        List<String> keys = new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            keys.add("S:racer-" + i);
        }

        runConcurrentlyWith(keys);

        assertThat(viewCount()).isEqualTo(THREADS);
        assertThat(viewCount()).isEqualTo(communityMapper.countViews(postId));
    }

    /**
     * 창을 벗어난 뒤의 동시 재조회가 <b>정확히 한 번만</b> 더 세어지는지 확인한다.
     *
     * <p>위의 두 테스트는 창을 한 번도 넘지 않으므로, 창 조건을 통째로 지워도(즉 같은
     * 조회자를 영영 한 번만 세도) 그대로 통과한다. 창이 열린 <b>직후</b>가 이 설계에서
     * 가장 위험한 순간이다 — 그 순간 8개 요청이 모두 "10분 전 이력밖에 없다"를 함께
     * 읽으면 조회수가 한 번에 8 오른다. 날짜 칸 시절에는 UNIQUE가 그것까지 막았지만
     * 지금은 게시글 행의 배타 잠금뿐이다.
     */
    @Test
    void getPostDetail_concurrentViewsAfterWindow_countOnlyOnceMore() throws Exception {
        runConcurrently(() -> communityService.getPostDetail(postId, null, "S:racer"));
        ageViews(11);

        runConcurrently(() -> communityService.getPostDetail(postId, null, "S:racer"));

        assertThat(viewCount())
                .as("창이 열린 순간에도 동시 요청은 한 번만 세어야 한다")
                .isEqualTo(2);
        assertThat(viewCount()).isEqualTo(communityMapper.countViews(postId));
    }

    /** 시계를 기다릴 수 없으므로 이력을 과거로 민다. 창 판단이 DB의 {@code NOW(6)}를 쓴다. */
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
        execute(viewerKeys, key -> communityService.getPostDetail(postId, null, key));
    }

    /**
     * 모든 스레드를 같은 순간에 출발시킨다.
     *
     * <p>배리어가 없으면 스레드가 순서대로 실행되어 경쟁이 일어나지 않고, 그러면 이 테스트는
     * 통과하지만 아무것도 검증하지 못한다.
     */
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
                // 예외가 있었다면 여기서 드러난다. 삼키면 실패가 통과로 보인다.
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
}
