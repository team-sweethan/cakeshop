package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.domain.member.service.MemberCommunityQueryService;
import com.cakeshop.global.config.ClockConfig;
import com.cakeshop.global.config.MariaDbIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** 조회수 캐시와 조회 이력의 일치를 확인한다. */
@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// Service 시계 설정을 함께 로드한다.
@Import({
        CommunityService.class,
        MemberCommunityQueryService.class,
        PopularPostReader.class,
        ClockConfig.class})
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

    /** 같은 조회자의 반복 조회는 한 번만 반영한다. */
    @Test
    void getPostDetail_sameViewerRefreshing_doesNotInflateViewCount() {
        long postId = insertPost(PostStatus.PUBLISHED);

        for (int i = 0; i < 10; i++) {
            communityService.getPostDetail(postId, memberId, "M:" + memberId);
        }

        assertThat(viewCountOf(postId)).isEqualTo(1);
        assertThatViewCountMatchesHistory(postId);
    }

    /** 댓글 더 보기는 조회수를 올리지 않는다. */
    @Test
    void getPostDetail_loadingMoreComments_doesNotInflateViewCount() {
        long postId = insertPost(PostStatus.PUBLISHED);
        String viewerKey = "S:reader";

        communityService.getPostDetail(postId, null, viewerKey);
        // 댓글 더 보기를 반복한다.
        communityService.getPostDetail(postId, null, viewerKey);
        communityService.getPostDetail(postId, null, viewerKey);
        communityService.getPostDetail(postId, null, viewerKey);

        assertThat(viewCountOf(postId)).isEqualTo(1);
    }

    /** 조회 창이 지난 뒤에도 댓글 더 보기는 조회수를 올리지 않는다. */
    @Test
    void getVisiblePost_afterWindowClosed_stillDoesNotCount() {
        long postId = insertPost(PostStatus.PUBLISHED);
        String viewerKey = "S:reader";

        communityService.getPostDetail(postId, null, viewerKey);
        agePostViews(postId, 11);

        // 조회 창 이후 댓글을 더 본다.
        communityService.getVisiblePost(postId, null);

        assertThat(viewCountOf(postId)).isEqualTo(1);
        assertThatViewCountMatchesHistory(postId);
    }

    /** 조회 이력을 창 밖으로 이동한다. */
    private void agePostViews(long postId, int minutes) {
        jdbcTemplate.update(
                "UPDATE post_views SET created_at = created_at - INTERVAL ? MINUTE"
                        + " WHERE post_id = ?",
                minutes, postId);
    }

    /** 비노출 글은 조회수와 이력을 남기지 않는다. */
    @Test
    void getPostDetail_notPublishedPost_leavesNeitherCountNorHistory() {
        long deletedPostId = insertPost(PostStatus.DELETED);
        long blockedPostId = insertPost(PostStatus.BLOCKED);

        // 삭제 글과 작성자의 차단 글을 조회한다.
        catchIgnored(() -> communityService.getPostDetail(deletedPostId, memberId, "M:1"));
        communityService.getPostDetail(blockedPostId, memberId, "M:" + memberId);

        assertThat(viewCountOf(deletedPostId)).isZero();
        assertThat(viewCountOf(blockedPostId)).isZero();
        assertThat(viewHistoryCount(deletedPostId)).isZero();
        assertThat(viewHistoryCount(blockedPostId)).isZero();
    }

    /** 조회는 게시글을 수정 상태로 만들지 않는다. */
    @Test
    void getPostDetail_doesNotMarkPostAsEdited() {
        long postId = insertPost(PostStatus.PUBLISHED);

        communityService.getPostDetail(postId, null, "S:aaa");

        assertThat(communityMapper.findPostById(postId).isEdited()).isFalse();
    }

    private void assertThatViewCountMatchesHistory(long postId) {
        assertThat(viewCountOf(postId))
                .as("view_count는 post_views에서 파생된 캐시다. 어긋나면 순위에 쓸 수 없다")
                .isEqualTo(viewHistoryCount(postId));
    }

    /** 데이터 상태 확인을 위해 예상 예외를 무시한다. */
    private void catchIgnored(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // 남은 데이터만 확인한다.
        }
    }

    private long viewCountOf(long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT view_count FROM posts WHERE id = ?", Long.class, postId);
    }

    private long viewHistoryCount(long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_views WHERE post_id = ?", Long.class, postId);
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
