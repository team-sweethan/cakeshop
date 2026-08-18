package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import java.time.LocalDateTime;

import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.entity.ReportStatus;
import com.cakeshop.domain.community.mapper.CommunityAdminMapper;
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.global.config.MariaDbIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 커뮤니티 쓰기 작업의 rollback을 실제 트랜잭션으로 검증한다. */
@SpringBootTest
@MariaDbIntegrationTest
class CommunityTransactionTests {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 3, 1, 10, 0);

    @Autowired
    private CommunityReactionService communityReactionService;

    @Autowired
    private CommunityAdminService communityAdminService;

    @MockitoSpyBean
    private CommunityMapper communityMapper;

    @MockitoSpyBean
    private CommunityAdminMapper communityAdminMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long categoryId;
    private long authorId;
    private long memberId;
    private long adminId;
    private long postId;

    @BeforeEach
    void setUp() {
        String suffix = Long.toString(System.nanoTime());

        jdbcTemplate.update(
                "INSERT INTO post_categories (code, name, is_active, sort_order) VALUES (?, ?, 1, 999)",
                "TX_" + suffix, "트랜잭션 테스트");
        categoryId = lastInsertId();
        authorId = insertMember("tx-author-" + suffix + "@cakeshop.local", "작성자", "USER");
        memberId = insertMember("tx-member-" + suffix + "@cakeshop.local", "회원", "USER");
        adminId = insertMember("tx-admin-" + suffix + "@cakeshop.local", "관리자", "ADMIN");

        jdbcTemplate.update(
                """
                INSERT INTO posts (
                    member_id, category_id, title, content, status, created_at, updated_at
                ) VALUES (?, ?, '제목', '본문', 'PUBLISHED', ?, ?)
                """,
                authorId, categoryId, CREATED_AT, CREATED_AT);
        postId = lastInsertId();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM post_reports WHERE post_id = ?", postId);
        jdbcTemplate.update("DELETE FROM post_likes WHERE post_id = ?", postId);
        jdbcTemplate.update("DELETE FROM posts WHERE id = ?", postId);
        jdbcTemplate.update("DELETE FROM members WHERE id IN (?, ?, ?)", authorId, memberId, adminId);
        jdbcTemplate.update("DELETE FROM post_categories WHERE id = ?", categoryId);
    }

    @Test
    void blockPost_reportClosingFails_rollsBackPostBlock() {
        jdbcTemplate.update(
                """
                INSERT INTO post_reports (post_id, reporter_id, reason, status)
                VALUES (?, ?, '신고 사유', 'PENDING')
                """,
                postId, memberId);
        doThrow(new IllegalStateException("test failure"))
                .when(communityAdminMapper)
                .closePendingReports(postId, ReportStatus.RESOLVED);

        assertThatThrownBy(() -> communityAdminService.blockPost(postId, "차단 사유", adminId))
                .isInstanceOf(IllegalStateException.class);

        assertThat(postStatus()).isEqualTo(PostStatus.PUBLISHED.name());
        assertThat(reportStatus()).isEqualTo(ReportStatus.PENDING.name());
    }

    @Test
    void addLike_recalculationFails_rollsBackInsertedLike() {
        doThrow(new IllegalStateException("test failure"))
                .when(communityMapper)
                .recalculateLikeCount(postId);

        assertThatThrownBy(() -> communityReactionService.addLike(postId, memberId))
                .isInstanceOf(IllegalStateException.class);

        assertThat(likeRows()).isZero();
        assertThat(likeCount()).isZero();
    }

    @Test
    void removeLike_recalculationFails_restoresDeletedLike() {
        jdbcTemplate.update(
                "INSERT INTO post_likes (post_id, member_id) VALUES (?, ?)", postId, memberId);
        jdbcTemplate.update(
                "UPDATE posts SET like_count = 1, updated_at = updated_at WHERE id = ?", postId);
        doThrow(new IllegalStateException("test failure"))
                .when(communityMapper)
                .recalculateLikeCount(postId);

        assertThatThrownBy(() -> communityReactionService.removeLike(postId, memberId))
                .isInstanceOf(IllegalStateException.class);

        assertThat(likeRows()).isEqualTo(1);
        assertThat(likeCount()).isEqualTo(1);
    }

    private long insertMember(String email, String nickname, String role) {
        jdbcTemplate.update(
                """
                INSERT INTO members (
                    email, password, nickname, phone, role, status, name, created_at, updated_at
                ) VALUES (?, 'encoded-password', ?, '010-0000-0000', ?, 'ACTIVE', ?, ?, ?)
                """,
                email, nickname, role, nickname, CREATED_AT, CREATED_AT);
        return lastInsertId();
    }

    private long lastInsertId() {
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private String postStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM posts WHERE id = ?", String.class, postId);
    }

    private String reportStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM post_reports WHERE post_id = ?", String.class, postId);
    }

    private long likeRows() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_likes WHERE post_id = ?", Long.class, postId);
    }

    private long likeCount() {
        return jdbcTemplate.queryForObject(
                "SELECT like_count FROM posts WHERE id = ?", Long.class, postId);
    }
}
