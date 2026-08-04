package com.cakeshop.domain.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cakeshop.domain.community.entity.CommentStatus;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.entity.ReportStatus;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 조각 0에서 추가한 migration 두 개를 검증한다.
 *
 * <p>하나는 상태 컬럼의 CHECK 제약이고, 다른 하나는 게시글 작성에 필요한 카테고리 참조
 * 데이터다. 둘 다 docs/community/DOMAIN.md의 결정을 DB에 못 박은 것이므로, 규칙이 코드에서만
 * 지켜지고 DB에서는 뚫리는 상황을 여기서 잡는다.
 *
 * <p>조각 5에서 post_reports.status 제약이 같은 이유로 합류했다.
 */
@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CommunitySchemaTests {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 10, 0);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void postCategories_afterMigration_containsMvpCategories() {
        List<String> codes = jdbcTemplate.queryForList(
                "SELECT code FROM post_categories ORDER BY sort_order", String.class);

        assertThat(codes).containsExactly("QNA", "REVIEW", "FREE");
    }

    @Test
    void postCategories_afterMigration_areAllActive() {
        // 카테고리 목록은 is_active = 1인 것만 노출한다(DOMAIN.md 6.8).
        // 초기 3종이 비활성으로 들어가면 글쓰기 화면에 선택지가 하나도 없다.
        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_categories WHERE is_active = 1", Integer.class);

        assertThat(activeCount).isEqualTo(3);
    }

    @Test
    void posts_undefinedStatus_isRejectedByCheckConstraint() {
        Long memberId = insertMember("post-status@cakeshop.local");
        Long categoryId = findCategoryId("QNA");

        assertThatThrownBy(() -> insertPost(memberId, categoryId, "ARCHIVED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void posts_definedStatuses_areAccepted() {
        Long memberId = insertMember("post-allowed@cakeshop.local");
        Long categoryId = findCategoryId("QNA");

        for (PostStatus status : PostStatus.values()) {
            insertPost(memberId, categoryId, status.name());
        }

        Integer inserted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM posts WHERE member_id = ?", Integer.class, memberId);
        assertThat(inserted).isEqualTo(PostStatus.values().length);
    }

    @Test
    void comments_undefinedStatus_isRejectedByCheckConstraint() {
        Long memberId = insertMember("comment-status@cakeshop.local");
        Long categoryId = findCategoryId("QNA");
        Long postId = insertPost(memberId, categoryId, PostStatus.PUBLISHED.name());

        assertThatThrownBy(() -> insertComment(postId, memberId, "HIDDEN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void comments_definedStatuses_areAccepted() {
        Long memberId = insertMember("comment-allowed@cakeshop.local");
        Long categoryId = findCategoryId("QNA");
        Long postId = insertPost(memberId, categoryId, PostStatus.PUBLISHED.name());

        for (CommentStatus status : CommentStatus.values()) {
            insertComment(postId, memberId, status.name());
        }

        Integer inserted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comments WHERE post_id = ?", Integer.class, postId);
        assertThat(inserted).isEqualTo(CommentStatus.values().length);
    }

    @Test
    void postReports_undefinedStatus_isRejectedByCheckConstraint() {
        Long memberId = insertMember("report-status@cakeshop.local");
        Long categoryId = findCategoryId("QNA");
        Long postId = insertPost(memberId, categoryId, PostStatus.PUBLISHED.name());

        assertThatThrownBy(() -> insertReport(postId, memberId, "CLOSED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void postReports_definedStatuses_areAccepted() {
        Long categoryId = findCategoryId("QNA");
        Long authorId = insertMember("report-author@cakeshop.local");
        Long postId = insertPost(authorId, categoryId, PostStatus.PUBLISHED.name());

        // UNIQUE(post_id, reporter_id)라 상태마다 신고자가 달라야 한다.
        for (ReportStatus status : ReportStatus.values()) {
            Long reporterId = insertMember("report-" + status.name() + "@cakeshop.local");
            insertReport(postId, reporterId, status.name());
        }

        Integer inserted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_reports WHERE post_id = ?", Integer.class, postId);
        assertThat(inserted).isEqualTo(ReportStatus.values().length);
    }

    private Long findCategoryId(String code) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM post_categories WHERE code = ?", Long.class, code);
    }

    private Long insertMember(String email) {
        jdbcTemplate.update(
                """
                INSERT INTO members (
                    email, password, nickname, phone, role, status, name, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                email, "encoded-password", "community-test", "010-0000-0000",
                "USER", "ACTIVE", "커뮤니티 테스트", CREATED_AT, CREATED_AT);

        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE email = ?", Long.class, email);
    }

    private Long insertPost(Long memberId, Long categoryId, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO posts (member_id, category_id, title, content, status)
                VALUES (?, ?, ?, ?, ?)
                """,
                memberId, categoryId, "제목", "본문", status);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private Long insertReport(Long postId, Long reporterId, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO post_reports (post_id, reporter_id, reason, status)
                VALUES (?, ?, ?, ?)
                """,
                postId, reporterId, "신고 사유", status);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private Long insertComment(Long postId, Long memberId, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO comments (post_id, member_id, content, status)
                VALUES (?, ?, ?, ?)
                """,
                postId, memberId, "댓글", status);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
