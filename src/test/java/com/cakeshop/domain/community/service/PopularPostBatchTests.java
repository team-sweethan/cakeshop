package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cakeshop.domain.community.entity.CommentStatus;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.global.config.MariaDbIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 인기글 배치 계약을 실제 MariaDB로 확인한다. */
@SpringBootTest
@MariaDbIntegrationTest
class PopularPostBatchTests {

    private static final LocalDate RANKING_DATE = LocalDate.of(2025, 1, 15);

    /** 집계 창 시작 시각. */
    private static final LocalDateTime WINDOW_START = LocalDateTime.of(2025, 1, 9, 0, 0, 0);

    /** 집계 창 종료 시각. */
    private static final LocalDateTime WINDOW_END = LocalDateTime.of(2025, 1, 16, 0, 0, 0);

    /** 집계 창 내부 시각. */
    private static final LocalDateTime INSIDE = LocalDateTime.of(2025, 1, 12, 12, 0);

    /** DATETIME(6)의 최소 간격. */
    private static final long ONE_MICRO_IN_NANOS = 1_000L;

    @Autowired
    private PopularPostBatchService popularPostBatchService;

    /** INSERT 실패를 재현할 Mapper spy. */
    @MockitoSpyBean
    private CommunityMapper communityMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long categoryId;
    private long memberId;
    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> postIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String suffix = Long.toString(System.nanoTime());

        categoryId = insertCategory("POPULAR_TEST_" + suffix);
        memberId = insertMember("popular-" + suffix + "@cakeshop.local");

        clearRankings();
    }

    @AfterEach
    void cleanUp() {
        clearRankings();

        for (long postId : postIds) {
            jdbcTemplate.update("DELETE FROM post_views WHERE post_id = ?", postId);
            jdbcTemplate.update("DELETE FROM post_likes WHERE post_id = ?", postId);
            jdbcTemplate.update("DELETE FROM comments WHERE post_id = ?", postId);
            jdbcTemplate.update("DELETE FROM posts WHERE id = ?", postId);
        }
        postIds.clear();

        for (long id : memberIds) {
            jdbcTemplate.update("DELETE FROM members WHERE id = ?", id);
        }
        memberIds.clear();

        jdbcTemplate.update("DELETE FROM post_categories WHERE id = ?", categoryId);
    }

    /** 같은 날짜를 재집계해도 순위와 점수가 같다. */
    @Test
    void createDailyRanking_recomputedForSameDate_producesIdenticalRows() {
        long first = newPost("동점 A");
        long second = newPost("동점 B");
        long third = newPost("혼자 위");

        addViews(first, 10, INSIDE);
        addViews(second, 10, INSIDE);
        addLike(third, INSIDE);

        popularPostBatchService.createDailyRanking(RANKING_DATE);
        List<Map<String, Object>> before = rankingRows();

        deleteBatchRunOnly();
        popularPostBatchService.createDailyRanking(RANKING_DATE);

        assertThat(rankingRows()).isEqualTo(before);
        assertThat(before).hasSize(3);
    }

    /** 집계 시작은 포함하고 직전 값은 제외한다. */
    @Test
    void createDailyRanking_windowStart_includesFirstMomentAndExcludesTheMomentBefore() {
        long inside = newPost("창 첫 순간");
        long outside = newPost("창 직전");

        addView(inside, WINDOW_START);
        addView(outside, WINDOW_START.minusNanos(ONE_MICRO_IN_NANOS));

        popularPostBatchService.createDailyRanking(RANKING_DATE);

        assertThat(rankedPostIds()).containsExactly(inside);
    }

    /** 집계 종료 직전은 포함하고 종료 값은 제외한다. */
    @Test
    void createDailyRanking_windowEnd_includesLastMomentAndExcludesNextMidnight() {
        long inside = newPost("창 마지막 순간");
        long outside = newPost("다음 날 자정");

        addView(inside, WINDOW_END.minusNanos(ONE_MICRO_IN_NANOS));
        addView(outside, WINDOW_END);

        popularPostBatchService.createDailyRanking(RANKING_DATE);

        assertThat(rankedPostIds()).containsExactly(inside);
    }

    /** 좋아요 한 건은 조회 24건보다 높은 점수다. */
    @Test
    void createDailyRanking_weights_oneLikeOutranksTwentyFourViews() {
        long liked = newPost("좋아요 하나");
        long viewed = newPost("조회 스물넷");

        addLike(liked, INSIDE);
        addViews(viewed, 24, INSIDE);

        popularPostBatchService.createDailyRanking(RANKING_DATE);

        assertThat(rankedPostIds()).containsExactly(liked, viewed);
        assertThat(scoreOf(liked)).isEqualTo(25L);
        assertThat(scoreOf(viewed)).isEqualTo(24L);
    }

    /** 댓글 점수는 댓글 수가 아니라 작성자 수로 계산한다. */
    @Test
    void createDailyRanking_commentScore_countsDistinctMembersNotComments() {
        long oneMemberThrice = newPost("한 사람이 셋");
        long twoMembersOnce = newPost("두 사람이 하나씩");

        addComment(oneMemberThrice, memberId, INSIDE);
        addComment(oneMemberThrice, memberId, INSIDE.plusMinutes(1));
        addComment(oneMemberThrice, memberId, INSIDE.plusMinutes(2));

        addComment(twoMembersOnce, memberId, INSIDE);
        addComment(twoMembersOnce, newMember(), INSIDE);

        popularPostBatchService.createDailyRanking(RANKING_DATE);

        // 댓글 수로 세면 순서가 뒤집힌다.
        assertThat(rankedPostIds()).containsExactly(twoMembersOnce, oneMemberThrice);
        assertThat(scoreOf(oneMemberThrice)).isEqualTo(15L);
        assertThat(scoreOf(twoMembersOnce)).isEqualTo(30L);
        assertThat(commentCountOf(oneMemberThrice)).isEqualTo(1L);
    }

    /** 취소한 좋아요는 점수에서 제외한다. */
    @Test
    void createDailyRanking_canceledLike_isNotScored() {
        long postId = newPost("좋아요를 거둔 글");

        addLike(postId, INSIDE);
        jdbcTemplate.update("DELETE FROM post_likes WHERE post_id = ?", postId);

        addView(postId, INSIDE);

        popularPostBatchService.createDailyRanking(RANKING_DATE);

        // 남은 조회 점수만 반영한다.
        assertThat(scoreOf(postId)).isEqualTo(1L);
    }

    /** 삭제 댓글은 점수에서 제외한다. */
    @Test
    void createDailyRanking_deletedComment_isNotScored() {
        long postId = newPost("지운 댓글만 있는 글");

        addComment(postId, memberId, INSIDE, CommentStatus.DELETED);

        popularPostBatchService.createDailyRanking(RANKING_DATE);

        assertThat(rankedPostIds()).isEmpty();
    }

    /** 삭제·차단 글은 인기글에서 제외한다. */
    @Test
    void createDailyRanking_deletedAndBlockedPosts_areNotSelected() {
        long published = newPost("노출", PostStatus.PUBLISHED);
        long deleted = newPost("삭제됨", PostStatus.DELETED);
        long blocked = newPost("차단됨", PostStatus.BLOCKED);

        addViews(published, 5, INSIDE);
        addViews(deleted, 100, INSIDE);
        addViews(blocked, 100, INSIDE);

        popularPostBatchService.createDailyRanking(RANKING_DATE);

        assertThat(rankedPostIds()).containsExactly(published);
    }

    /** 재집계 실패 시 기존 순위와 실행 기록을 rollback한다. */
    @Test
    void createDailyRanking_insertFails_keepsPreviousSnapshotAndRecordsNoRun() {
        long postId = newPost("이전 스냅샷의 글");
        seedRankingRow(postId);

        doThrow(new IllegalStateException("집계 실패"))
                .when(communityMapper).insertDailyRanking(any(LocalDate.class), anyInt());

        assertThatThrownBy(() -> popularPostBatchService.createDailyRanking(RANKING_DATE))
                .isInstanceOf(IllegalStateException.class);

        // 기존 순위를 보존한다.
        assertThat(rankedPostIds()).containsExactly(postId);
        assertThat(batchRunCount()).isZero();
    }

    /** 활동이 없어도 0건 실행 기록을 남긴다. */
    @Test
    void createDailyRanking_noActivityInWindow_recordsRunWithZeroPosts() {
        long postId = newPost("창 밖에서만 활동한 글");
        addView(postId, WINDOW_START.minusDays(1));

        popularPostBatchService.createDailyRanking(RANKING_DATE);

        assertThat(rankedPostIds()).isEmpty();
        assertThat(batchRunCount()).isEqualTo(1);
        assertThat(recordedPostCount()).isZero();
    }

    /** 확정된 날짜는 원본이 바뀌어도 다시 집계하지 않는다. */
    @Test
    void createDailyRanking_confirmedDateRerunAfterSourcesChange_keepsSnapshot() {
        long postId = newPost("좋아요를 받은 글");
        addLike(postId, INSIDE);
        addComment(postId, memberId, INSIDE);

        popularPostBatchService.createDailyRanking(RANKING_DATE);
        List<Map<String, Object>> before = rankingRows();

        // 확정 후 원본을 변경한다.
        jdbcTemplate.update("DELETE FROM post_likes WHERE post_id = ?", postId);
        jdbcTemplate.update(
                "UPDATE comments SET status = 'DELETED' WHERE post_id = ?", postId);

        popularPostBatchService.createDailyRanking(RANKING_DATE);

        assertThat(rankingRows()).isEqualTo(before);
        assertThat(scoreOf(postId)).isEqualTo(40L);
    }

    private List<Map<String, Object>> rankingRows() {
        return jdbcTemplate.queryForList(
                """
                SELECT ranking, post_id, popularity_score, view_count, like_count, comment_count
                FROM daily_popular_posts
                WHERE ranking_date = ?
                ORDER BY ranking
                """,
                RANKING_DATE);
    }

    private List<Long> rankedPostIds() {
        return jdbcTemplate.queryForList(
                """
                SELECT post_id
                FROM daily_popular_posts
                WHERE ranking_date = ?
                ORDER BY ranking
                """,
                Long.class, RANKING_DATE);
    }

    private long scoreOf(long postId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT popularity_score
                FROM daily_popular_posts
                WHERE ranking_date = ? AND post_id = ?
                """,
                Long.class, RANKING_DATE, postId);
    }

    private long commentCountOf(long postId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT comment_count
                FROM daily_popular_posts
                WHERE ranking_date = ? AND post_id = ?
                """,
                Long.class, RANKING_DATE, postId);
    }

    private int batchRunCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM popular_post_batch_runs WHERE ranking_date = ?",
                Integer.class, RANKING_DATE);
    }

    private int recordedPostCount() {
        return jdbcTemplate.queryForObject(
                "SELECT post_count FROM popular_post_batch_runs WHERE ranking_date = ?",
                Integer.class, RANKING_DATE);
    }

    private void clearRankings() {
        jdbcTemplate.update("DELETE FROM daily_popular_posts WHERE ranking_date = ?", RANKING_DATE);
        jdbcTemplate.update(
                "DELETE FROM popular_post_batch_runs WHERE ranking_date = ?", RANKING_DATE);
    }

    private void deleteBatchRunOnly() {
        jdbcTemplate.update(
                "DELETE FROM popular_post_batch_runs WHERE ranking_date = ?", RANKING_DATE);
    }

    private void seedRankingRow(long postId) {
        jdbcTemplate.update(
                """
                INSERT INTO daily_popular_posts (
                    ranking_date, ranking, post_id, popularity_score,
                    view_count, like_count, comment_count
                )
                VALUES (?, 1, ?, 999, 999, 0, 0)
                """,
                RANKING_DATE, postId);
    }

    private long newPost(String title) {
        return newPost(title, PostStatus.PUBLISHED);
    }

    private long newPost(String title, PostStatus status) {
        jdbcTemplate.update(
                """
                INSERT INTO posts (
                    member_id, category_id, title, content, status, created_at, updated_at
                )
                VALUES (?, ?, ?, '본문', ?, ?, ?)
                """,
                memberId, categoryId, title, status.name(), INSIDE, INSIDE);

        long postId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        postIds.add(postId);

        return postId;
    }

    private void addViews(long postId, int count, LocalDateTime createdAt) {
        for (int i = 0; i < count; i++) {
            addView(postId, createdAt);
        }
    }

    /** 조회 이력은 현재 쓰기 경로와 같은 값으로 저장한다. */
    private void addView(long postId, LocalDateTime createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO post_views (post_id, viewer_key, created_at)
                VALUES (?, ?, ?)
                """,
                postId, "S:" + System.nanoTime(), createdAt);
    }

    private void addLike(long postId, LocalDateTime createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO post_likes (post_id, member_id, created_at)
                VALUES (?, ?, ?)
                """,
                postId, newMember(), createdAt);
    }

    private void addComment(long postId, long authorId, LocalDateTime createdAt) {
        addComment(postId, authorId, createdAt, CommentStatus.PUBLISHED);
    }

    private void addComment(
            long postId, long authorId, LocalDateTime createdAt, CommentStatus status) {
        jdbcTemplate.update(
                """
                INSERT INTO comments (
                    post_id, member_id, content, status, created_at, updated_at
                )
                VALUES (?, ?, '댓글', ?, ?, ?)
                """,
                postId, authorId, status.name(), createdAt, createdAt);
    }

    private long newMember() {
        return insertMember("popular-" + System.nanoTime() + "@cakeshop.local");
    }

    private long insertMember(String email) {
        jdbcTemplate.update(
                """
                INSERT INTO members (
                    email, password, nickname, phone, role, status, name, created_at, updated_at
                )
                VALUES (?, 'encoded-password', ?, '010-0000-0000', 'USER', 'ACTIVE', ?, ?, ?)
                """,
                email, "인기글테스터", "인기글테스터", INSIDE, INSIDE);

        long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        memberIds.add(id);

        return id;
    }

    private long insertCategory(String code) {
        jdbcTemplate.update(
                """
                INSERT INTO post_categories (code, name, is_active, sort_order)
                VALUES (?, '인기글 테스트', 1, 999)
                """,
                code);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
