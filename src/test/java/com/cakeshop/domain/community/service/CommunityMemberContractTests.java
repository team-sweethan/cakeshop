package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;

import com.cakeshop.domain.community.dto.view.AdminPostDetailView;
import com.cakeshop.domain.community.dto.view.AdminPostSort;
import com.cakeshop.domain.community.dto.view.CommentSectionView;
import com.cakeshop.domain.community.dto.view.CommentView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListView;
import com.cakeshop.domain.community.dto.view.PostSort;
import com.cakeshop.domain.community.entity.CommentStatus;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.domain.member.service.MemberCommunityQueryService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.config.ClockConfig;
import com.cakeshop.global.config.MariaDbIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 커뮤니티와 회원 계약이 실제 DB에서 맞물리는지 확인한다(조각 10d).
 *
 * <p>조각 10 전에는 `members`를 INNER JOIN해서 작성자 표기를 SQL 한 문장이 끝냈다. 지금은
 * 커뮤니티 조회와 회원 조회 <b>두 문장</b>이 Service에서 합쳐진다. 양쪽을 각각 검사하는
 * 테스트는 이미 있지만, <b>합쳐진 결과</b>가 규칙대로인지는 여기서만 드러난다.
 *
 * <p>conventions.md 15.9의 마지막 문단이 이 자리를 가리킨다 — 원본 테이블의 어휘가 바뀌면
 * 조용히 어긋나고 컴파일도 테스트도 통과한다. {@code members.status}에 값이 하나 늘거나
 * {@code nickname}이 다른 컬럼으로 옮겨 가면 화면의 작성자가 전부 "탈퇴한 회원"이 되는데,
 * 커뮤니티 쪽 검사는 전부 초록불이다.
 *
 * <p>탈퇴 회원을 쓰는 것은 <b>DB로 만들 수 있는 유일한 경계</b>이기 때문이다. 회원 행이
 * 아예 없는 게시글은 {@code posts.member_id}가 NOT NULL FK라 만들 수 없다(DOMAIN.md 8).
 * 그쪽은 {@code CommunityServiceTests}가 mock으로 본다.
 */
@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        CommunityService.class,
        CommunityAdminService.class,
        MemberCommunityQueryService.class,
        ClockConfig.class})
class CommunityMemberContractTests {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 3, 1, 10, 0);

    private static final PageRequest FIRST_PAGE = new PageRequest(1, 20);

    @Autowired
    private CommunityService communityService;

    @Autowired
    private CommunityAdminService communityAdminService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long categoryId;
    private long activeMemberId;
    private long withdrawnMemberId;

    @BeforeEach
    void setUp() {
        String suffix = Long.toString(System.nanoTime());

        jdbcTemplate.update(
                """
                INSERT INTO post_categories (code, name, is_active, sort_order)
                VALUES (?, ?, 1, 999)
                """,
                "CONTRACT_" + suffix, "계약 테스트");
        categoryId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        activeMemberId = insertMember("활동회원", suffix + "-active", MemberStatus.ACTIVE);
        withdrawnMemberId = insertMember("탈퇴회원", suffix + "-gone", MemberStatus.WITHDRAWN);
    }

    /**
     * 탈퇴 회원의 글이 목록에서 사라지지 않고 표시명만 가려지는지 확인한다.
     *
     * <p>INNER JOIN이던 때는 회원 쪽 조건이 하나만 어긋나도 글이 통째로 빠졌고, 화면에는
     * "글이 없다"로 보여서 오류처럼 보이지 않았다(DOMAIN.md 8).
     */
    @Test
    void getPosts_withdrawnAuthor_keepsPostAndMasksName() {
        insertPost(withdrawnMemberId, "탈퇴 회원 글");
        insertPost(activeMemberId, "활동 회원 글");

        var posts = communityService.getPosts(categoryId, PostSort.LATEST, FIRST_PAGE).getContent();

        assertThat(posts)
                .extracting(PostListView::title, PostListView::authorName)
                .containsExactly(
                        tuple("활동 회원 글", "활동회원"),
                        tuple("탈퇴 회원 글", "탈퇴한 회원"));
    }

    @Test
    void getVisiblePost_withdrawnAuthor_masksName() {
        long postId = insertPost(withdrawnMemberId, "탈퇴 회원 글");

        PostDetailView post = communityService.getVisiblePost(postId, null);

        assertThat(post.authorName()).isEqualTo("탈퇴한 회원");
        assertThat(post.authorWithdrawn()).isTrue();
    }

    /** 탈퇴 회원의 댓글도 지우지 않고 표시명만 가린다(DOMAIN.md 8). */
    @Test
    void getComments_withdrawnAuthor_keepsCommentAndMasksName() {
        long postId = insertPost(activeMemberId, "댓글 달린 글");
        insertComment(postId, withdrawnMemberId);
        insertComment(postId, activeMemberId);

        CommentSectionView section = communityService.getComments(postId, null);

        assertThat(section.comments())
                .extracting(CommentView::authorName)
                .containsExactly("탈퇴한 회원", "활동회원");
    }

    @Test
    void adminGetPosts_withdrawnAuthor_keepsPostAndMasksName() {
        insertPost(withdrawnMemberId, "탈퇴 회원 글");

        var posts = communityAdminService
                .getPosts(null, AdminPostSort.LATEST, new PageRequest(1, 100))
                .getContent().stream()
                .filter(post -> "계약 테스트".equals(post.categoryName()))
                .toList();

        assertThat(posts).singleElement()
                .satisfies(post -> assertThat(post.authorName()).isEqualTo("탈퇴한 회원"));
    }

    /** 신고자와 차단 관리자도 같은 계약으로 채워진다(조각 10c). */
    @Test
    void adminGetReportsAndDetail_fillReporterAndBlockingAdmin() {
        long postId = insertPost(activeMemberId, "신고된 글");

        jdbcTemplate.update(
                """
                INSERT INTO post_reports (post_id, reporter_id, reason, status)
                VALUES (?, ?, '광고입니다', 'PENDING')
                """,
                postId, withdrawnMemberId);

        communityAdminService.blockPost(postId, "광고성 게시물", activeMemberId);

        assertThat(communityAdminService.getReports(postId)).singleElement()
                .satisfies(report ->
                        assertThat(report.reporterName()).isEqualTo("탈퇴한 회원"));

        AdminPostDetailView detail = communityAdminService.getPostDetail(postId);

        assertThat(detail.authorName()).isEqualTo("활동회원");
        assertThat(detail.blockedByNickname()).isEqualTo("활동회원");
    }

    private long insertMember(String nickname, String suffix, MemberStatus status) {
        jdbcTemplate.update(
                """
                INSERT INTO members (email, password, name, nickname, phone, role, status)
                VALUES (?, 'encoded-password', ?, ?, '010-0000-0000', 'USER', ?)
                """,
                suffix + "@cakeshop.local", nickname, nickname, status.name());

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertPost(long memberId, String title) {
        jdbcTemplate.update(
                """
                INSERT INTO posts (
                    member_id, category_id, title, content, status, created_at, updated_at
                )
                VALUES (?, ?, ?, '본문', ?, ?, ?)
                """,
                memberId, categoryId, title, PostStatus.PUBLISHED.name(), BASE_TIME, BASE_TIME);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertComment(long postId, long memberId) {
        jdbcTemplate.update(
                """
                INSERT INTO comments (post_id, member_id, content, status, created_at)
                VALUES (?, ?, '댓글', ?, ?)
                """,
                postId, memberId, CommentStatus.PUBLISHED.name(), BASE_TIME);
    }
}
