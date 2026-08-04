package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.global.config.MariaDbIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code posts.view_count}가 {@code post_views} 이력과 어긋나지 않는지 확인한다.
 *
 * <p>조회수는 이제 정렬·순위의 근거 데이터이고, {@code view_count}는 이력에서 파생된
 * <b>캐시</b>다(docs/community/DOMAIN.md 6.2). 좋아요와 달리 매번 재계산하지 않고
 * 증분으로 갱신하기로 했는데, 증분은 한 번 어긋나면 스스로 복구되지 않는다.
 *
 * <p>그래서 "증분을 쓰되 일치는 검사한다"가 6.2의 약속이고, 이 클래스가 그 약속이다.
 * 어긋남은 화면에 숫자가 조금 다른 모습으로만 나타나서 눈으로는 찾을 수 없다.
 */
@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CommunityService.class)
class CommunityViewCountTests {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 3, 1, 10, 0);

    @Autowired
    private CommunityService communityService;

    @Autowired
    private CommunityMapper communityMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                "VIEW_COUNT_" + suffix, "조회수 테스트");
        categoryId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbcTemplate.update(
                """
                INSERT INTO members (
                    email, password, nickname, phone, role, status, name, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, 'USER', 'ACTIVE', ?, ?, ?)
                """,
                "view-count-" + suffix + "@cakeshop.local", "encoded-password",
                "조회수", "010-0000-0000", "조회수", BASE_TIME, BASE_TIME);
        memberId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test
    void getPostDetail_differentViewers_countEachOnce() {
        long postId = insertPost(PostStatus.PUBLISHED);

        communityService.getPostDetail(postId, null, "S:aaa");
        communityService.getPostDetail(postId, null, "S:bbb");
        communityService.getPostDetail(postId, memberId, "M:" + memberId);

        assertThat(viewCountOf(postId)).isEqualTo(3);
        assertThatViewCountMatchesHistory(postId);
    }

    /**
     * 같은 사람이 새로고침을 반복해도 숫자가 오르지 않는지 확인한다.
     *
     * <p>이 조각이 존재하는 이유 자체다. 조회수가 순위를 정하는데 F5 한 번이 곧 순위
     * 조작이면, 순위는 아무 의미가 없다(DOMAIN.md 6.2).
     */
    @Test
    void getPostDetail_sameViewerRefreshing_doesNotInflateViewCount() {
        long postId = insertPost(PostStatus.PUBLISHED);

        for (int i = 0; i < 10; i++) {
            communityService.getPostDetail(postId, memberId, "M:" + memberId);
        }

        assertThat(viewCountOf(postId)).isEqualTo(1);
        assertThatViewCountMatchesHistory(postId);
    }

    /**
     * 댓글 "더 보기"가 조회수를 부풀리지 않는지 확인한다(PLAN.md R13).
     *
     * <p>더 보기는 상세를 같은 주소로 다시 여는 것이라 조각 3까지는 클릭마다 +1이었다.
     * 댓글이 많은 글일수록 조회수가 저절로 올라가므로, 순위 신호로 삼으면 자가 인플레가
     * 된다. 창 안에서는 같은 조회자의 재조회로 처리되어 막힌다.
     */
    @Test
    void getPostDetail_loadingMoreComments_doesNotInflateViewCount() {
        long postId = insertPost(PostStatus.PUBLISHED);
        String viewerKey = "S:reader";

        communityService.getPostDetail(postId, null, viewerKey);
        // "이전 댓글 더 보기"를 세 번 누른 것과 같다. 주소의 comments 값만 달라진다.
        communityService.getPostDetail(postId, null, viewerKey);
        communityService.getPostDetail(postId, null, viewerKey);
        communityService.getPostDetail(postId, null, viewerKey);

        assertThat(viewCountOf(postId)).isEqualTo(1);
    }

    /**
     * 창이 닫힌 뒤의 더 보기도 세지 않는지 확인한다(PR #98 Codex 리뷰 P2).
     *
     * <p><b>위 테스트는 창을 한 번도 넘지 않아 이 경계를 놓친다.</b> 상세를 10분 넘게 읽다가
     * `더 보기`를 누르면 창이 이미 닫혀 있고, 그때 창에만 기대면 그대로 +1이 된다. 막는
     * 것은 창이 아니라 <b>경로가 갈리는 것</b>이다 — 더 보기는 조회수를 올리지 않는
     * {@code getVisiblePost}로 간다(CommunityController.detail).
     *
     * <p>시각은 DB의 {@code NOW(6)}라 주입할 자리가 없으므로 이력을 과거로 민다(H19와 같은
     * 방식). 11분이면 창 밖이다.
     */
    @Test
    void getVisiblePost_afterWindowClosed_stillDoesNotCount() {
        long postId = insertPost(PostStatus.PUBLISHED);
        String viewerKey = "S:reader";

        communityService.getPostDetail(postId, null, viewerKey);
        agePostViews(postId, 11);

        // 창이 닫힌 뒤 "이전 댓글 더 보기"를 누른 것과 같다.
        communityService.getVisiblePost(postId, null);

        assertThat(viewCountOf(postId)).isEqualTo(1);
        assertThatViewCountMatchesHistory(postId);
    }

    /** 이력을 과거로 민다. 창 판단이 DB 시계를 쓰므로 시각을 주입할 자리가 없다(H19). */
    private void agePostViews(long postId, int minutes) {
        jdbcTemplate.update(
                "UPDATE post_views SET created_at = created_at - INTERVAL ? MINUTE"
                        + " WHERE post_id = ?",
                minutes, postId);
    }

    /**
     * 노출되지 않는 글은 숫자도 이력도 남기지 않는지 확인한다.
     *
     * <p>둘 중 하나만 막으면 이력과 숫자가 갈라진다.
     */
    @Test
    void getPostDetail_notPublishedPost_leavesNeitherCountNorHistory() {
        long deletedPostId = insertPost(PostStatus.DELETED);
        long blockedPostId = insertPost(PostStatus.BLOCKED);

        // 삭제된 글은 작성자에게도 404다. 차단된 글은 작성자에게만 열린다.
        catchIgnored(() -> communityService.getPostDetail(deletedPostId, memberId, "M:1"));
        communityService.getPostDetail(blockedPostId, memberId, "M:" + memberId);

        assertThat(viewCountOf(deletedPostId)).isZero();
        assertThat(viewCountOf(blockedPostId)).isZero();
        assertThat(communityMapper.countViews(deletedPostId)).isZero();
        assertThat(communityMapper.countViews(blockedPostId)).isZero();
    }

    /** 조회가 게시글을 "수정됨"으로 만들지 않는지 확인한다(DOMAIN.md 6.3). */
    @Test
    void getPostDetail_doesNotMarkPostAsEdited() {
        long postId = insertPost(PostStatus.PUBLISHED);

        communityService.getPostDetail(postId, null, "S:aaa");

        assertThat(communityMapper.findPostById(postId).isEdited()).isFalse();
    }

    private void assertThatViewCountMatchesHistory(long postId) {
        assertThat(viewCountOf(postId))
                .as("view_count는 post_views에서 파생된 캐시다. 어긋나면 순위에 쓸 수 없다")
                .isEqualTo(communityMapper.countViews(postId));
    }

    /**
     * 예외를 무시하고 실행한다.
     *
     * <p>노출되지 않는 글은 404가 정상이고, 여기서 보려는 것은 그 예외가 아니라
     * <b>남은 숫자와 이력</b>이다.
     */
    private void catchIgnored(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // 이 테스트가 보는 것은 예외가 아니라 남은 데이터다.
        }
    }

    private long viewCountOf(long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT view_count FROM posts WHERE id = ?", Long.class, postId);
    }

    private long insertPost(PostStatus status) {
        jdbcTemplate.update(
                """
                INSERT INTO posts (
                    member_id, category_id, title, content, status, created_at, updated_at
                )
                VALUES (?, ?, '제목', '본문', ?, ?, ?)
                """,
                memberId, categoryId, status.name(), BASE_TIME, BASE_TIME);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
