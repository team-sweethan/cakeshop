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

/**
 * 인기글 배치 하네스 — H21~H24, H30~H33 (docs/community/PLAN.md 조각 7b).
 *
 * <p><b>이 클래스는 트랜잭션 롤백을 쓰지 않는다.</b> 검사 대상 자체가 서비스의 트랜잭션
 * 경계이기 때문이다(H31). 테스트를 트랜잭션으로 감싸면 안쪽 rollback이 바깥 트랜잭션에
 * 묻혀 {@code @Transactional}을 떼도 통과한다. 대신 넣은 행을 {@link #cleanUp()}에서 직접
 * 지운다 — 좋아요·조회수 동시성 테스트와 같은 판단이다.
 *
 * <p><b>대상 날짜를 2025년으로 멀리 잡는다.</b> 순위는 카테고리로 좁혀지지 않아 컨테이너를
 * 공유하는 다른 테스트의 게시글까지 후보가 된다. 그쪽 데이터는 2026-03-01 아니면 실행
 * 시각이므로, 창을 2025-01로 두면 섞일 수가 없다. 테스트마다 카테고리를 새로 만드는
 * 다른 커뮤니티 검사들과 목적은 같고, 순위에는 카테고리 조건이 없어 수단만 다르다.
 */
@SpringBootTest
@MariaDbIntegrationTest
class PopularPostBatchTests {

    private static final LocalDate RANKING_DATE = LocalDate.of(2025, 1, 15);

    /** 창의 시작. 대상일 6일 전 00:00:00 <b>이상</b>이다(대상일 포함 7칸). */
    private static final LocalDateTime WINDOW_START = LocalDateTime.of(2025, 1, 9, 0, 0, 0);

    /** 창의 끝. 대상일 다음 날 00:00:00 <b>미만</b>이다. */
    private static final LocalDateTime WINDOW_END = LocalDateTime.of(2025, 1, 16, 0, 0, 0);

    /** 창 한가운데. 경계를 보지 않는 검사는 전부 이 시각을 쓴다. */
    private static final LocalDateTime INSIDE = LocalDateTime.of(2025, 1, 12, 12, 0);

    /**
     * DATETIME(6)의 한 칸. 경계 검사가 "직전"과 "직후"를 만들 때 쓴다.
     *
     * <p>1초가 아니라 1마이크로초인 것이 중요하다. 창의 끝을 '23:59:59 이하'로 잘못 적어도
     * 1초 단위로만 확인하면 통과한다 — 빠지는 것은 그 1초 안의 값들뿐이기 때문이다.
     */
    private static final long ONE_MICRO_IN_NANOS = 1_000L;

    @Autowired
    private PopularPostBatchService popularPostBatchService;

    /**
     * H31이 INSERT를 실패시켜야 해서 spy다. 기본 동작은 실제 매퍼 그대로이므로 다른
     * 검사에는 영향이 없다.
     *
     * <p>실패를 자연스럽게 일으킬 방법이 없다 — 재집계가 그 날짜를 먼저 지우고 시작하므로
     * 미리 심어 둔 행으로 제약을 위반시킬 수가 없다. 실패 경로를 검사에서 빼면
     * {@code @Transactional}이 빠져도 아무도 모른다.
     */
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

    // --- H22: 같은 날짜를 다시 집계해도 결과가 완전히 같다 ---

    /**
     * H22 — 확정을 지운 뒤 같은 날짜를 다시 집계하면 순위·점수·근거 수치가 모두 같다.
     *
     * <p><b>실행 기록만 지우고 순위는 남긴 채 부른다.</b> 그냥 두 번 부르면 D4가 두 번째를
     * 즉시 건너뛰어 집계 SQL이 아예 돌지 않는다 — 그러면 tiebreaker가 없어도 통과한다.
     * 실패한 날의 재실행이 실제로 밟는 경로가 이것이기도 하다.
     *
     * <p><b>동점을 반드시 넣는다.</b> 동점이 없는 데이터로만 확인하면 SQL에서 tiebreaker가
     * 빠져도 순위가 흔들릴 자리가 없어 통과한다(D4).
     */
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

    // --- H23: 창의 양쪽 경계 ---

    /**
     * H23 — 창의 시작은 <b>포함</b>이고 그 직전 한 칸은 빠진다.
     *
     * <p>한쪽만 보면 창이 1일이든 30일이든 똑같이 통과한다. 폭이 조용히 틀어지면 좁아진
     * 쪽은 인기글이 비고 넓어진 쪽은 오래된 글이 상위를 점유하는데, 둘 다 화면으로는
     * "그날은 그랬나 보다"와 구분되지 않는다.
     */
    @Test
    void createDailyRanking_windowStart_includesFirstMomentAndExcludesTheMomentBefore() {
        long inside = newPost("창 첫 순간");
        long outside = newPost("창 직전");

        addView(inside, WINDOW_START);
        addView(outside, WINDOW_START.minusNanos(ONE_MICRO_IN_NANOS));

        popularPostBatchService.createDailyRanking(RANKING_DATE);

        assertThat(rankedPostIds()).containsExactly(inside);
    }

    /** H23 — 창의 끝은 대상일 <b>다음 날 00:00:00 미만</b>이다. 그 순간부터 빠진다. */
    @Test
    void createDailyRanking_windowEnd_includesLastMomentAndExcludesNextMidnight() {
        long inside = newPost("창 마지막 순간");
        long outside = newPost("다음 날 자정");

        addView(inside, WINDOW_END.minusNanos(ONE_MICRO_IN_NANOS));
        addView(outside, WINDOW_END);

        popularPostBatchService.createDailyRanking(RANKING_DATE);

        assertThat(rankedPostIds()).containsExactly(inside);
    }

    // --- H24: 가중치 ---

    /**
     * H24 — 가중치가 실제로 25/15/1이다.
     *
     * <p>좋아요 하나(25점)가 조회 24회(24점)를 이기는 <b>바로 그 지점</b>을 잡는다.
     * 원안의 5/3/1로 되돌아가면 좋아요가 5점이 되어 조회 24회가 이기므로 이 검사가 뒤집힌다.
     * 계수가 조용히 틀어져도 화면은 멀쩡하고 순위만 이상해진다.
     */
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

    // --- H30: 댓글은 건수가 아니라 사람 수 ---

    /**
     * H30 — 한 회원이 댓글을 여러 개 달아도 15점이고, 서로 다른 두 회원이면 30점이다.
     *
     * <p><b>정상 데이터로는 두 구현이 구분되지 않는다.</b> 사람마다 하나씩 다는 흔한 경우에
     * {@code COUNT(*)}와 {@code COUNT(DISTINCT member_id)}가 같은 값을 내므로, 같은 회원이
     * 여러 번 다는 데이터를 일부러 만들지 않으면 건수로 되돌아가도 통과한다. 이 자리가
     * 뚫리면 계정 하나로 TOP 20을 점유할 수 있다.
     */
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

        // 건수로 세면 셋(45점)이 둘(30점)을 이겨 순서가 뒤집힌다.
        assertThat(rankedPostIds()).containsExactly(twoMembersOnce, oneMemberThrice);
        assertThat(scoreOf(oneMemberThrice)).isEqualTo(15L);
        assertThat(scoreOf(twoMembersOnce)).isEqualTo(30L);
        assertThat(commentCountOf(oneMemberThrice)).isEqualTo(1L);
    }

    /**
     * H26 — 거둔 좋아요는 점수에서 빠진다.
     *
     * <p>원본을 그때그때 세기 때문에 저절로 맞는다. <b>이것이 카운터 테이블을 두지 않은
     * 이유(D2) 그 자체다</b> — 올리기만 하는 카운터였다면 취소·재좋아요를 반복해 점수를
     * 무한히 올릴 수 있었다. 나중에 "배치가 느리니 미리 세어 두자"는 이야기가 나오면
     * 이 검사가 먼저 깨진다.
     */
    @Test
    void createDailyRanking_canceledLike_isNotScored() {
        long postId = newPost("좋아요를 거둔 글");

        addLike(postId, INSIDE);
        jdbcTemplate.update("DELETE FROM post_likes WHERE post_id = ?", postId);

        addView(postId, INSIDE);

        popularPostBatchService.createDailyRanking(RANKING_DATE);

        // 좋아요가 남아 있었다면 26점이다.
        assertThat(scoreOf(postId)).isEqualTo(1L);
    }

    /** H26 — 노출 중이 아닌 댓글은 점수에 들어가지 않는다. 화면에서 자리 표시로만 남는 것들이다. */
    @Test
    void createDailyRanking_deletedComment_isNotScored() {
        long postId = newPost("지운 댓글만 있는 글");

        addComment(postId, memberId, INSIDE, CommentStatus.DELETED);

        popularPostBatchService.createDailyRanking(RANKING_DATE);

        assertThat(rankedPostIds()).isEmpty();
    }

    // --- D5: 선정 단계에서도 비노출 글을 뺀다 ---

    /**
     * 지워지거나 차단된 글은 창 안의 활동을 그대로 갖고 있어도 선정되지 않는다.
     *
     * <p>게시글을 지워도 자식 행은 남으므로(DOMAIN.md 4.5) 삭제 직전에 인기였던 글일수록
     * 상위를 차지한다. 화면에서만 거르면 그런 글이 스냅샷의 칸을 먹어 목록이 10건보다
     * 적게 나오거나 통째로 빈다.
     */
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

    // --- H31: 실패하면 아무것도 남기지 않는다 ---

    /**
     * H31 — 재집계가 실패하면 이전 스냅샷이 그대로 남고 실행 기록도 생기지 않는다.
     *
     * <p><b>실행 기록 없이 순위 행만 심어 두고 부른다.</b> 기록까지 심으면 D4가 그 날짜를
     * 즉시 건너뛰어 두 번째 호출이 실패 지점에 닿지도 못하고, 그러면 {@code @Transactional}을
     * 떼도 이 검사가 통과한다.
     *
     * <p><b>H22로는 이 자리가 절대 드러나지 않는다</b> — 그쪽은 성공한 실행 둘을 비교할 뿐이라
     * 트랜잭션이 없어도 같은 결과가 나온다. 그리고 실패했을 때의 화면은 폴백 때문에 전날
     * 목록으로 멀쩡히 보인다 — 그날 순위가 통째로 사라진 것을 아무도 눈치채지 못한다.
     */
    @Test
    void createDailyRanking_insertFails_keepsPreviousSnapshotAndRecordsNoRun() {
        long postId = newPost("이전 스냅샷의 글");
        seedRankingRow(postId);

        doThrow(new IllegalStateException("집계 실패"))
                .when(communityMapper).insertDailyRanking(any(LocalDate.class), anyInt());

        assertThatThrownBy(() -> popularPostBatchService.createDailyRanking(RANKING_DATE))
                .isInstanceOf(IllegalStateException.class);

        // DELETE만 커밋되고 INSERT가 죽는 상태가 되면 안 된다.
        assertThat(rankedPostIds()).containsExactly(postId);
        assertThat(batchRunCount()).isZero();
    }

    // --- H32: 활동이 0인 날도 확정으로 기록된다 ---

    /**
     * H32 — 순위가 0건이어도 실행 기록에는 행이 남는다.
     *
     * <p>남지 않으면 "안 돈 날"과 "돌았는데 0건인 날"이 둘 다 행 없음이라 구분되지 않고,
     * 화면의 최신 확정일이 옛 날짜로 계속 폴백해 <b>7일 창 밖의 오래된 글이 무기한
     * 걸린다.</b> 그런데 화면은 멀쩡해 보인다 — 순위가 안 바뀌는 것은 활동이 뜸한 날과
     * 구분되지 않는다. 이 표가 존재하는 이유 그 자체라, "실행 기록 테이블은 군더더기
     * 아니냐"는 이야기가 나오면 이 검사가 먼저 깨진다.
     */
    @Test
    void createDailyRanking_noActivityInWindow_recordsRunWithZeroPosts() {
        long postId = newPost("창 밖에서만 활동한 글");
        addView(postId, WINDOW_START.minusDays(1));

        popularPostBatchService.createDailyRanking(RANKING_DATE);

        assertThat(rankedPostIds()).isEmpty();
        assertThat(batchRunCount()).isEqualTo(1);
        assertThat(recordedPostCount()).isZero();
    }

    // --- H33: 확정된 날짜는 다시 돌려도 바뀌지 않는다 ---

    /**
     * H33 — 성공한 날짜를 원본이 변한 뒤에 다시 돌려도 확정된 순위가 그대로다.
     *
     * <p><b>H22로는 절대 안 잡힌다</b> — 그쪽은 원본을 건드리지 않고 부르므로 덮어쓰는
     * 구현도 같은 결과를 낸다. 스냅샷이 스냅샷인 이유가 이 검사이고, D4의 건너뛰기가
     * 실제로 살아 있는지도 여기서 본다.
     */
    @Test
    void createDailyRanking_confirmedDateRerunAfterSourcesChange_keepsSnapshot() {
        long postId = newPost("좋아요를 받은 글");
        addLike(postId, INSIDE);
        addComment(postId, memberId, INSIDE);

        popularPostBatchService.createDailyRanking(RANKING_DATE);
        List<Map<String, Object>> before = rankingRows();

        // 원본을 바꾼다 — 좋아요를 거두고 댓글을 지운다.
        jdbcTemplate.update("DELETE FROM post_likes WHERE post_id = ?", postId);
        jdbcTemplate.update(
                "UPDATE comments SET status = 'DELETED' WHERE post_id = ?", postId);

        popularPostBatchService.createDailyRanking(RANKING_DATE);

        assertThat(rankingRows()).isEqualTo(before);
        assertThat(scoreOf(postId)).isEqualTo(40L);
    }

    // --- 조회 helper ---

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

    // --- 픽스처 helper ---

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

    /**
     * viewed_on은 채우지 않는다. 날짜 창 시절의 잔재 컬럼이고 신버전은 비워 두기로 했다
     * (V20260804_102934). 픽스처가 채우면 실제 쓰기 경로와 다른 데이터로 검사하게 된다.
     */
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
