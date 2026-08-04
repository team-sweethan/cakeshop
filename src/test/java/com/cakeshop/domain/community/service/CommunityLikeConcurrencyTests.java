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
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.global.config.MariaDbIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * H2c — {@code posts.like_count}가 {@code post_likes} 실제 개수와 어긋나지 않는지,
 * <b>동시 요청 뒤에도</b> 확인한다.
 *
 * <p>여기서 잡는 것은 두 가지다.
 *
 * <p><b>하나는 교착이다.</b> {@code post_likes} INSERT는 FK 확인 때문에 부모인 {@code posts}
 * 행에 공유 잠금(S)을 걸고, 뒤따르는 {@code like_count} 재계산이 같은 행의 배타 잠금(X)을
 * 달라고 한다. 같은 글에 동시에 좋아요를 누른 요청 둘이 서로 S를 쥔 채 상대의 X를 기다리면
 * 그대로 교착이다. 조각 6의 조회수에서 맞은 것과 같은 모양이지만 해법은 다르다 — 조회수는
 * 순서를 뒤집어 풀었고, 여기서는 재계산이 INSERT 이후여야 새 행을 세므로 뒤집을 수 없다.
 * 그래서 {@code lockPost}가 X를 먼저 잡는다(docs/community/DOMAIN.md 6.5).
 *
 * <p><b>다른 하나는 숫자와 행의 어긋남이다.</b> 6.5가 증분 대신 재계산을 택한 이유가 이것이고,
 * 재계산이라도 잠금 순서가 틀리면 갱신이 유실될 수 있다.
 *
 * <p>둘 다 <b>동시 요청이 없으면 결과가 똑같아</b> 단일 스레드 테스트로는 절대 드러나지
 * 않는다. 교착은 인기 있는 글일수록 더 잘 터지고, 어긋난 숫자는 화면에 조금 다른 수로만
 * 나타나서 눈으로도 찾을 수 없다.
 *
 * <p><b>이 클래스는 트랜잭션 롤백을 쓰지 않는다.</b> {@code CommunityViewCountConcurrencyTests}와
 * 같은 이유다 — 테스트를 트랜잭션으로 감싸면 다른 스레드가 이 게시글을 아예 볼 수 없어
 * 경쟁 자체가 일어나지 않고, 통과하지만 아무것도 검증하지 않는 테스트가 된다. 대신 넣은
 * 행을 {@link #cleanUp()}에서 직접 지운다.
 */
@SpringBootTest
@MariaDbIntegrationTest
class CommunityLikeConcurrencyTests {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 3, 1, 10, 0);
    private static final int MEMBERS = 8;

    @Autowired
    private CommunityService communityService;

    @Autowired
    private CommunityMapper communityMapper;

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

    /** 롤백이 없으므로 직접 지운다. 자식 → 부모 순이다. */
    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM post_likes WHERE post_id = ?", postId);
        jdbcTemplate.update("DELETE FROM posts WHERE id = ?", postId);

        for (Long id : memberIds) {
            jdbcTemplate.update("DELETE FROM members WHERE id = ?", id);
        }

        jdbcTemplate.update("DELETE FROM post_categories WHERE id = ?", categoryId);
    }

    /**
     * 서로 다른 회원이 같은 글에 <b>동시에</b> 좋아요를 눌러도 교착 없이 모두 세어지는지.
     *
     * <p>이 테스트가 교착을 잡는 자리다. {@code lockPost}의 {@code FOR UPDATE}를 떼면
     * 여기서 터진다 — 단, 터지는 방식이 실패가 아니라 <b>예외</b>다. 그래서 아래
     * {@code execute}가 각 Future의 예외를 반드시 다시 던지게 해 두었다.
     */
    @Test
    void addLike_concurrentLikesByDifferentMembers_countsEachWithoutDeadlock() throws Exception {
        execute(memberIds, memberId -> communityService.addLike(postId, memberId));

        assertThat(likeCount())
                .as("서로 다른 회원의 좋아요는 모두 세어야 한다")
                .isEqualTo(MEMBERS);
        assertThat(likeCount())
                .as("like_count는 post_likes에서 다시 센 값이다. 어긋나면 6.5의 근거가 무너진다")
                .isEqualTo(communityMapper.countLikes(postId));
    }

    /**
     * 같은 회원이 <b>동시에</b> 여러 번 눌러도 한 번만 세어지는지.
     *
     * <p>위 테스트만 있으면 중복을 못 막아도 통과한다. 반대로 이 테스트만 있으면 "무조건 1"로
     * 만들어도 통과한다. 둘이 함께 있어야 좋아요를 잃지도, 부풀리지도 않는다.
     */
    @Test
    void addLike_concurrentLikesBySameMember_countsOnce() throws Exception {
        long memberId = memberIds.get(0);
        List<Long> sameMember = new ArrayList<>();

        for (int i = 0; i < MEMBERS; i++) {
            sameMember.add(memberId);
        }

        execute(sameMember, id -> communityService.addLike(postId, id));

        assertThat(likeCount())
                .as("멱등이므로 같은 회원이 여러 번 눌러도 1이다")
                .isEqualTo(1);
        assertThat(likeCount()).isEqualTo(communityMapper.countLikes(postId));
    }

    /**
     * 추가와 취소가 섞여 동시에 들어와도 숫자가 행과 맞는지.
     *
     * <p>추가만 도는 테스트로는 <b>두 경로가 서로 다른 순서로 잠그는 구현</b>을 잡지 못한다.
     * 그런 구현은 같은 종류의 요청끼리는 멀쩡하고 섞였을 때만 교착이다.
     */
    @Test
    void likeAndUnlike_concurrentMixed_keepsCountConsistentWithRows() throws Exception {
        // 전부 눌러 둔 상태에서 시작한다. 절반은 다시 누르고(멱등) 절반은 거둔다.
        for (Long memberId : memberIds) {
            communityService.addLike(postId, memberId);
        }

        execute(memberIds, memberId -> {
            if (memberIds.indexOf(memberId) % 2 == 0) {
                communityService.addLike(postId, memberId);
            } else {
                communityService.removeLike(postId, memberId);
            }
        });

        assertThat(likeCount())
                .as("절반이 거둬 갔으므로 절반이 남는다")
                .isEqualTo(MEMBERS / 2);
        assertThat(likeCount()).isEqualTo(communityMapper.countLikes(postId));
    }

    /**
     * 모든 스레드를 같은 순간에 출발시킨다.
     *
     * <p>배리어가 없으면 스레드가 순서대로 실행되어 경쟁이 일어나지 않고, 그러면 이 테스트는
     * 통과하지만 아무것도 검증하지 못한다.
     */
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
                // 교착은 여기서 예외로 드러난다. 삼키면 실패가 통과로 보인다.
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
}
