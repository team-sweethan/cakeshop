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

/** 커뮤니티와 회원 조회 계약의 실제 DB 조립 결과를 검증한다. */
@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        CommunityService.class,
        CommunityAdminService.class,
        MemberCommunityQueryService.class,
        CommunityMemberViewLoader.class,
        CommunityPostAccessPolicy.class,
        PopularPostReader.class,
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
    private long adminMemberId;

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

        activeMemberId = insertMember("활동회원", suffix + "-active", "USER", MemberStatus.ACTIVE);
        withdrawnMemberId = insertMember("탈퇴회원", suffix + "-gone", "USER", MemberStatus.WITHDRAWN);
        adminMemberId = insertMember("차단관리자", suffix + "-admin", "ADMIN", MemberStatus.ACTIVE);
    }

    /** 탈퇴 회원의 글을 유지하고 표시명만 가린다. */
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

    /** 탈퇴 회원의 댓글도 표시명만 가린다. */
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

    /** 신고자와 차단 관리자를 각 위치에 조립한다. */
    @Test
    void adminGetReportsAndDetail_fillReporterAndBlockingAdmin() {
        long postId = insertPost(activeMemberId, "신고된 글");

        jdbcTemplate.update(
                """
                INSERT INTO post_reports (post_id, reporter_id, reason, status)
                VALUES (?, ?, '광고입니다', 'PENDING')
                """,
                postId, withdrawnMemberId);

        communityAdminService.blockPost(postId, "광고성 게시물", adminMemberId);

        assertThat(communityAdminService.getReports(postId)).singleElement()
                .satisfies(report ->
                        assertThat(report.reporterName()).isEqualTo("탈퇴한 회원"));

        AdminPostDetailView detail = communityAdminService.getPostDetail(postId);

        assertThat(detail.authorName()).isEqualTo("활동회원");
        assertThat(detail.blockedByNickname()).isEqualTo("차단관리자");
    }

    /** 실명, 닉네임, 역할을 구분한 회원 fixture를 만든다. */
    private long insertMember(String nickname, String suffix, String role, MemberStatus status) {
        jdbcTemplate.update(
                """
                INSERT INTO members (email, password, name, nickname, phone, role, status)
                VALUES (?, 'encoded-password', ?, ?, '010-0000-0000', ?, ?)
                """,
                suffix + "@cakeshop.local", nickname + "실명", nickname, role, status.name());

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
