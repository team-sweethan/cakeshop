package com.cakeshop.domain.community.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.cakeshop.domain.community.dto.view.CommentSectionView;
import com.cakeshop.domain.community.entity.CommentStatus;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import com.cakeshop.global.security.MemberDetails;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * 커뮤니티 화면을 실제 Thymeleaf로 렌더링한다.
 *
 * <p>이 클래스가 소유하는 것은 네 가지다. 템플릿별 대표 렌더링(템플릿명과 Model 연결), 사용자 입력
 * escaping, 화면 결과가 실질적으로 달라지는 상태, 그리고 커뮤니티 고유의 인가 거절과 그 뒤의 DB 상태다.
 *
 * <p>업무 규칙은 다른 테스트가 소유한다. 상태별 노출·인기글 판정·댓글 상한은 {@code CommunityServiceTests},
 * Model 계약과 요청 파라미터 처리는 {@code CommunityControllerTests}, 조회수는
 * {@code CommunityViewCountTests}, 탈퇴 회원 마스킹은 {@code CommunityMemberContractTests},
 * SQL 결과는 {@code CommunityMapperTests}가 본다. docs/testing.md 8절에 따라 권한별 버튼, 안내 문구,
 * 집계 포맷과 HTML 조각은 여기에서 고정하지 않는다.
 */
@SpringBootTest
@MariaDbIntegrationTest
@Transactional
class CommunityScreenRenderingTests {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 3, 1, 10, 0);

    /** 인기글 화면 검사용 확정일. */
    private static final LocalDate RANKING_DATE = LocalDate.of(2099, 1, 2);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private long categoryId;
    private long emptyCategoryId;
    private long memberId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        String suffix = Long.toString(System.nanoTime());

        categoryId = insertCategory("SCREEN_" + suffix, "화면테스트");
        emptyCategoryId = insertCategory("SCREEN_EMPTY_" + suffix, "빈카테고리");
        memberId = insertMember("screen-" + suffix + "@cakeshop.local", "케이크덕후", "ACTIVE");
    }

    @Test
    void communityList_rendersPostRow() throws Exception {
        insertPost(memberId, "딸기 케이크 후기", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community").param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("딸기 케이크 후기")))
                .andExpect(content().string(containsString("케이크덕후")))
                .andExpect(content().string(containsString("화면테스트")));
    }

    @Test
    void communityList_withoutPosts_rendersEmptyMessage() throws Exception {
        mockMvc.perform(get("/community").param("categoryId", String.valueOf(emptyCategoryId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("아직 등록된 글이 없습니다.")));
    }

    /** 확정된 인기글이 있으면 목록 위에 별도 영역을 렌더링한다. */
    @Test
    void communityList_withConfirmedRanking_rendersPopularSection() throws Exception {
        long postId = insertPost(memberId, "이번 주 인기 케이크", "본문", PostStatus.PUBLISHED);
        insertRanking(1, postId);
        insertBatchRun(1);

        mockMvc.perform(get("/community"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("인기글")))
                .andExpect(content().string(containsString("이번 주 인기 케이크")));
    }

    /** 작성 화면에는 활성 카테고리만 채운다. */
    @Test
    void communityCreateForm_rendersActiveCategories() throws Exception {
        insertInactiveCategory();

        mockMvc.perform(get("/community/new").with(authentication(authorOf(memberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("화면테스트")))
                .andExpect(content().string(not(containsString("비활성분류"))));
    }

    /** 수정 화면에 기존 값을 채운다. */
    @Test
    void communityEditForm_author_rendersExistingValues() throws Exception {
        long postId = insertPost(memberId, "고칠 제목", "고칠 본문", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community/" + postId + "/edit")
                        .with(authentication(authorOf(memberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("고칠 제목")))
                .andExpect(content().string(containsString("고칠 본문")));
    }

    /** 저장 경로가 있는 작성 화면은 비로그인에게 열지 않는다(SecurityConfig의 커뮤니티 고유 규칙). */
    @Test
    void communityCreateForm_anonymous_isSentToLogin() throws Exception {
        mockMvc.perform(get("/community/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void communityDetail_rendersContent() throws Exception {
        long postId = insertPost(memberId, "제목입니다", "첫 줄\n둘째 줄", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community/" + postId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("제목입니다")))
                .andExpect(content().string(containsString("첫 줄")));
    }

    /** 게시글 HTML을 이스케이프한다. */
    @Test
    void communityDetail_htmlInContent_isEscaped() throws Exception {
        String attack = "<script>alert('xss')</script>";
        long postId = insertPost(memberId, "제목", attack, PostStatus.PUBLISHED);

        mockMvc.perform(get("/community/" + postId))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(attack))))
                .andExpect(content().string(containsString("&lt;script&gt;")));
    }

    @Test
    void communityDetail_titleWithHtml_isEscaped() throws Exception {
        long postId = insertPost(
                memberId, "<img src=x onerror=alert(1)>", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community/" + postId))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<img src=x"))));
    }

    /** 댓글 HTML을 이스케이프한다. */
    @Test
    void communityDetail_htmlInComment_isEscaped() throws Exception {
        String attack = "<script>alert('comment')</script>";
        long postId = insertPost(memberId, "글", "본문", PostStatus.PUBLISHED);
        insertComment(postId, memberId, attack, CommentStatus.PUBLISHED, BASE_TIME);

        mockMvc.perform(get("/community/" + postId))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(attack))))
                .andExpect(content().string(containsString("&lt;script&gt;")));
    }

    /** 차단 글 작성자에게는 본문 대신 사유와 안내를 보여 주고 수정 경로를 끊는다. */
    @Test
    void communityDetail_blockedPostAuthor_showsReasonAndHidesActions() throws Exception {
        long postId = insertPost(memberId, "차단된 글", "본문", PostStatus.BLOCKED);
        jdbcTemplate.update(
                "UPDATE posts SET blocked_reason = ? WHERE id = ?", "광고성 게시물", postId);

        mockMvc.perform(get("/community/" + postId).with(authentication(authorOf(memberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("관리자가 차단한 게시글입니다.")))
                .andExpect(content().string(containsString("광고성 게시물")))
                .andExpect(content().string(
                        not(containsString("/community/" + postId + "/edit"))));
    }

    /** 작성자가 아닌 조회자에게는 차단 사유가 응답 본문에 남지 않는다. */
    @Test
    void communityDetail_blockedPost_anonymousViewer_returnsNotFound() throws Exception {
        long postId = insertPost(memberId, "차단된 글", "본문", PostStatus.BLOCKED);
        jdbcTemplate.update(
                "UPDATE posts SET blocked_reason = ? WHERE id = ?", "광고성 게시물", postId);

        mockMvc.perform(get("/community/" + postId))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("광고성 게시물"))));
    }

    /** 로그인 회원에게는 서버로 전송되는 댓글 폼을 렌더링한다. */
    @Test
    void communityDetail_authenticated_showsCommentForm() throws Exception {
        long postId = insertPost(memberId, "글", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community/" + postId).with(authentication(authorOf(memberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("/community/" + postId + "/comments")));
    }

    /** 더 보기 전에는 오래된 댓글을 감추고, 요청하면 같은 화면에 펼친다. */
    @Test
    void communityDetail_manyComments_showsLoadMoreForOlderComments() throws Exception {
        long postId = insertPost(memberId, "댓글 많은 글", "본문", PostStatus.PUBLISHED);

        for (int i = 0; i < CommentSectionView.DEFAULT_LIMIT + 1; i++) {
            insertComment(postId, memberId, "댓글 " + i,
                    CommentStatus.PUBLISHED, BASE_TIME.plusMinutes(i));
        }

        int nextLimit = CommentSectionView.DEFAULT_LIMIT + CommentSectionView.STEP;

        mockMvc.perform(get("/community/" + postId))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("댓글 0"))));

        mockMvc.perform(get("/community/" + postId).param("comments", String.valueOf(nextLimit)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("댓글 0")));
    }

    /** 관리자 목록에 실제 게시글을 렌더링한다. */
    @Test
    void communityAdminList_rendersForAdmin() throws Exception {
        insertPost(memberId, "관리자 목록에 보일 글", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(get("/admin/community").with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("관리자 목록에 보일 글")))
                .andExpect(content().string(containsString("케이크덕후")));
    }

    /** 관리자 상세에 게시글과 신고 내역을 함께 렌더링한다. */
    @Test
    void communityAdminDetail_rendersForAdmin() throws Exception {
        long postId = insertPost(memberId, "관리자 상세 글", "본문입니다", PostStatus.PUBLISHED);
        long reporterId = insertMember(
                "admin-report-" + System.nanoTime() + "@cakeshop.local", "신고한사람", "ACTIVE");
        insertReport(postId, reporterId, "PENDING");

        mockMvc.perform(get("/admin/community/" + postId).with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("관리자 상세 글")))
                .andExpect(content().string(containsString("본문입니다")))
                .andExpect(content().string(containsString("신고한사람")))
                .andExpect(content().string(containsString("광고성 게시물입니다")));
    }

    /** 일반 회원의 차단 요청을 Security에서 거절한다. */
    @Test
    void communityAdminBlock_normalMember_isRejected() throws Exception {
        long postId = insertPost(memberId, "차단 시도 대상", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(post("/admin/community/" + postId + "/block")
                        .param("reason", "마음에 안 듦")
                        .with(csrf())
                        .with(authentication(authorOf(memberId))))
                .andExpect(status().isForbidden());

        // 거절된 요청은 게시글을 바꾸지 않는다.
        assertThat(statusOf(postId)).isEqualTo(PostStatus.PUBLISHED.name());
    }

    /** 삭제 글의 신고 기각 요청도 거절한다. */
    @Test
    void communityAdminRejectReports_deletedPost_isRejectedAndLeavesReportPending()
            throws Exception {
        long postId = insertPost(memberId, "지워진 글", "본문", PostStatus.DELETED);
        long reporterId = insertMember(
                "deleted-reject-" + System.nanoTime() + "@cakeshop.local", "신고자", "ACTIVE");
        insertReport(postId, reporterId, "PENDING");

        mockMvc.perform(post("/admin/community/" + postId + "/reports/reject")
                        .with(csrf())
                        .with(authentication(admin())))
                .andExpect(status().isBadRequest());

        // 거절된 기각 요청은 신고 상태를 바꾸지 않는다.
        assertThat(reportStatusOf(postId)).isEqualTo("PENDING");
    }

    private void insertRanking(int ranking, long postId) {
        jdbcTemplate.update(
                """
                INSERT INTO daily_popular_posts (
                    ranking_date, ranking, post_id, popularity_score,
                    view_count, like_count, comment_count
                )
                VALUES (?, ?, ?, 100, 0, 0, 0)
                """,
                RANKING_DATE, ranking, postId);
    }

    private void insertBatchRun(int postCount) {
        jdbcTemplate.update(
                """
                INSERT INTO popular_post_batch_runs (ranking_date, post_count)
                VALUES (?, ?)
                """,
                RANKING_DATE, postCount);
    }

    private String statusOf(long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM posts WHERE id = ?", String.class, postId);
    }

    private String reportStatusOf(long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM post_reports WHERE post_id = ?", String.class, postId);
    }

    private Authentication authorOf(long id) {
        return authenticationOf(id, "USER");
    }

    private Authentication admin() {
        return authenticationOf(1L, "ADMIN");
    }

    private Authentication authenticationOf(long id, String role) {
        MemberDetails principal = new MemberDetails(
                new MemberAuthenticationView(id, "viewer@cakeshop.local", "x", role, true));

        return new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
    }

    private long insertCategory(String code, String name) {
        jdbcTemplate.update(
                """
                INSERT INTO post_categories (code, name, is_active, sort_order)
                VALUES (?, ?, 1, 999)
                """,
                code, name);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertInactiveCategory() {
        jdbcTemplate.update(
                """
                INSERT INTO post_categories (code, name, is_active, sort_order)
                VALUES (?, '비활성분류', 0, 999)
                """,
                "SCREEN_INACTIVE_" + System.nanoTime());
    }

    private long insertMember(String email, String nickname, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO members (
                    email, password, nickname, phone, role, status, name, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, 'USER', ?, ?, ?, ?)
                """,
                email, "encoded-password", nickname, "010-0000-0000", status,
                nickname, BASE_TIME, BASE_TIME);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertPost(long authorId, String title, String content, PostStatus status) {
        jdbcTemplate.update(
                """
                INSERT INTO posts (
                    member_id, category_id, title, content, status, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                authorId, categoryId, title, content, status.name(), BASE_TIME, BASE_TIME);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertReport(long postId, long reporterId, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO post_reports (post_id, reporter_id, reason, status, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                postId, reporterId, "광고성 게시물입니다", status, BASE_TIME);
    }

    private void insertComment(
            long postId,
            long authorId,
            String content,
            CommentStatus status,
            LocalDateTime createdAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO comments (
                    post_id, member_id, content, status, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                postId, authorId, content, status.name(), createdAt, createdAt);
    }
}
