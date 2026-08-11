package com.cakeshop.domain.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cakeshop.domain.community.entity.CommentStatus;
import com.cakeshop.domain.community.entity.NoticeStatus;
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

/** 커뮤니티 참조 데이터, 제약, 인덱스를 검증한다. */
@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CommunitySchemaTests {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 10, 0);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void postCategories_afterMigration_haveExpectedCodesOrderAndStatus() {
        List<String> categories = jdbcTemplate.queryForList(
                """
                SELECT CONCAT(code, ':', sort_order, ':', is_active)
                FROM post_categories
                ORDER BY sort_order
                """,
                String.class);

        assertThat(categories).containsExactly("QNA:1:1", "REVIEW:2:1", "FREE:3:1");
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

    @Test
    void communityNotices_undefinedStatus_isRejectedByCheckConstraint() {
        Long memberId = insertMember("notice-status@cakeshop.local");

        assertThatThrownBy(() -> insertNotice(memberId, "BLOCKED", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void communityNotices_definedStatuses_areAccepted() {
        Long memberId = insertMember("notice-allowed@cakeshop.local");

        for (NoticeStatus status : NoticeStatus.values()) {
            insertNotice(memberId, status.name(), null, null);
        }

        Integer inserted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM community_notices WHERE created_by = ?",
                Integer.class, memberId);
        assertThat(inserted).isEqualTo(NoticeStatus.values().length);
    }

    @Test
    void communityNotices_invertedPeriod_isRejectedByCheckConstraint() {
        Long memberId = insertMember("notice-period@cakeshop.local");

        assertThatThrownBy(() -> insertNotice(
                memberId, NoticeStatus.PUBLISHED.name(), CREATED_AT.plusDays(1), CREATED_AT))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertNotice(
                memberId, NoticeStatus.PUBLISHED.name(), CREATED_AT, CREATED_AT))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void communityNotices_openPeriod_isAccepted() {
        Long memberId = insertMember("notice-open-period@cakeshop.local");

        insertNotice(memberId, NoticeStatus.PUBLISHED.name(), CREATED_AT, null);
        insertNotice(memberId, NoticeStatus.PUBLISHED.name(), null, CREATED_AT);

        Integer inserted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM community_notices WHERE created_by = ?",
                Integer.class, memberId);
        assertThat(inserted).isEqualTo(2);
    }

    @Test
    void communityIndexes_haveRequiredColumnPrefixes() {
        assertIndexPrefix("posts", null, "status,view_count,id");
        assertIndexPrefix("comments", null, "created_at,post_id");
        assertIndexPrefix("post_views", true, "created_at,post_id");
        assertIndexPrefix("post_likes", true, "created_at,post_id");
        assertIndexPrefix("post_views", true, "post_id,viewer_key,created_at");

        List<String> indexes = jdbcTemplate.queryForList(
                """
                SELECT DISTINCT index_name
                FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'post_views'
                """,
                String.class);
        assertThat(indexes).doesNotContain("uk_post_views_post_viewer_date");
    }

    private void assertIndexPrefix(String tableName, Boolean nonUnique, String prefix) {
        List<IndexDefinition> indexes = jdbcTemplate.query(
                """
                SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns,
                       MAX(non_unique) AS non_unique
                FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = ?
                GROUP BY index_name
                """,
                (resultSet, rowNum) -> new IndexDefinition(
                        resultSet.getString("columns"), resultSet.getBoolean("non_unique")),
                tableName);

        assertThat(indexes).anySatisfy(index -> {
            assertThat(index.columns()).startsWith(prefix);
            if (nonUnique != null) {
                assertThat(index.nonUnique()).isEqualTo(nonUnique);
            }
        });
    }

    private record IndexDefinition(String columns, boolean nonUnique) {
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

    private Long insertNotice(
            Long memberId, String status, LocalDateTime startsAt, LocalDateTime endsAt) {

        jdbcTemplate.update(
                """
                INSERT INTO community_notices (
                    title, content, status, starts_at, ends_at, created_by
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                "공지 제목", "공지 본문", status, startsAt, endsAt, memberId);

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
