package com.cakeshop.domain.community.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.cakeshop.domain.community.dto.view.AdminPostDetailRow;
import com.cakeshop.domain.community.dto.view.AdminPostListRow;
import com.cakeshop.domain.community.dto.view.AdminPostSort;
import com.cakeshop.domain.community.dto.view.CommentCountView;
import com.cakeshop.domain.community.dto.view.CommentRow;
import com.cakeshop.domain.community.dto.view.ReportRow;
import com.cakeshop.domain.community.dto.view.PopularPostView;
import com.cakeshop.domain.community.dto.view.PostCategoryView;
import com.cakeshop.domain.community.dto.view.PostDetailRow;
import com.cakeshop.domain.community.dto.view.PostListRow;
import com.cakeshop.domain.community.dto.view.PostSort;
import com.cakeshop.domain.community.dto.view.PostLockView;
import com.cakeshop.domain.community.dto.view.ReportView;
import com.cakeshop.domain.community.entity.Comment;
import com.cakeshop.domain.community.entity.CommentStatus;
import com.cakeshop.domain.community.entity.Post;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.entity.ReportStatus;
import com.cakeshop.global.config.MariaDbIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/** 커뮤니티 Mapper 계약을 실제 MariaDB로 확인한다. */
@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CommunityMapperTests {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 3, 1, 10, 0);

    /** 다른 테스트와 겹치지 않는 인기글 확정 날짜다. */
    private static final LocalDate RANKING_DATE = LocalDate.of(2099, 1, 2);

    @Autowired
    private CommunityMapper communityMapper;

    /** 관리자 Mapper도 같은 게시글 픽스처로 확인한다. */
    @Autowired
    private CommunityAdminMapper communityAdminMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long categoryId;
    private long otherCategoryId;
    private long memberId;
    private long withdrawnMemberId;

    @BeforeEach
    void setUp() {
        String suffix = Long.toString(System.nanoTime());

        categoryId = insertCategory("COMMUNITY_TEST_" + suffix, "커뮤니티 테스트", true);
        otherCategoryId = insertCategory("COMMUNITY_OTHER_" + suffix, "다른 카테고리", true);
        memberId = insertMember("community-" + suffix + "@cakeshop.local", "글쓴이", "ACTIVE");
        withdrawnMemberId =
                insertMember("withdrawn-" + suffix + "@cakeshop.local", "탈퇴예정", "WITHDRAWN");
    }

    @Test
    void findPublishedPosts_deletedAndBlockedPosts_areExcluded() {
        insertPost("노출", PostStatus.PUBLISHED, BASE_TIME);
        insertPost("삭제됨", PostStatus.DELETED, BASE_TIME);
        insertPost("차단됨", PostStatus.BLOCKED, BASE_TIME);

        List<PostListRow> posts = findPage(1, 20);

        assertThat(posts).extracting(PostListRow::title).containsExactly("노출");
    }

    @Test
    void findPublishedPosts_latestSort_ordersByCreatedAtThenIdDescending() {
        long first = insertPost("첫 번째", PostStatus.PUBLISHED, BASE_TIME);
        long second = insertPost("두 번째", PostStatus.PUBLISHED, BASE_TIME);
        long newest = insertPost("최신", PostStatus.PUBLISHED, BASE_TIME.plusDays(1));

        List<PostListRow> posts = findPage(1, 20);

        assertThat(posts).extracting(PostListRow::id)
                .containsExactly(newest, second, first);
    }

    /** 작성 시각과 반대인 조회수 순서를 검증한다. */
    @Test
    void findPublishedPosts_viewSort_ordersByViewCountThenIdDescending() {
        long fewFirst = insertPost("적게 본 글 1", PostStatus.PUBLISHED, BASE_TIME);
        long fewSecond = insertPost("적게 본 글 2", PostStatus.PUBLISHED, BASE_TIME.plusDays(1));
        long many = insertPost("많이 본 글", PostStatus.PUBLISHED, BASE_TIME.minusDays(1));

        setViewCount(fewFirst, 3);
        setViewCount(fewSecond, 3);
        setViewCount(many, 100);

        List<PostListRow> posts = findPage(1, 20, PostSort.VIEWS);

        assertThat(posts).extracting(PostListRow::id)
                .containsExactly(many, fewSecond, fewFirst);
    }

    /** 같은 작성 시각의 페이지 경계에서 중복과 누락을 확인한다. */
    @Test
    void findPublishedPosts_pagingAcrossPages_hasNoDuplicateOrMissingPost() {
        List<Long> inserted = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            inserted.add(insertPost("글 " + i, PostStatus.PUBLISHED, BASE_TIME));
        }

        List<Long> collected = new ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            collected.addAll(findPage(page, 2).stream().map(PostListRow::id).toList());
        }

        assertThat(collected).hasSize(5);
        assertThat(collected).doesNotHaveDuplicates();
        assertThat(collected).containsExactlyInAnyOrderElementsOf(inserted);
    }

    @Test
    void findPublishedPosts_deletedComments_areNotCounted() {
        long postId = insertPost("댓글 있는 글", PostStatus.PUBLISHED, BASE_TIME);
        insertComment(postId, CommentStatus.PUBLISHED);
        insertComment(postId, CommentStatus.PUBLISHED);
        insertComment(postId, CommentStatus.DELETED);

        List<PostListRow> posts = findPage(1, 20);

        assertThat(posts).singleElement()
                .extracting(PostListRow::commentCount)
                .isEqualTo(2L);
    }

    @Test
    void findPublishedPosts_categoryFilter_returnsOnlyThatCategory() {
        insertPost("이 카테고리", PostStatus.PUBLISHED, BASE_TIME);
        insertPost("삭제됨", PostStatus.DELETED, BASE_TIME);
        insertPost(memberId, otherCategoryId, "다른 카테고리", PostStatus.PUBLISHED, BASE_TIME);

        assertThat(findPage(1, 20)).extracting(PostListRow::title)
                .containsExactly("이 카테고리");
        assertThat(communityMapper.countPublishedPosts(categoryId)).isEqualTo(1);
    }

    @Test
    void findPostById_blockedPost_isReturnedWithReason() {
        long postId = insertPost("차단된 글", PostStatus.BLOCKED, BASE_TIME);
        jdbcTemplate.update(
                "UPDATE posts SET blocked_reason = ?, blocked_at = ? WHERE id = ?",
                "광고성 게시물", BASE_TIME, postId);

        PostDetailRow post = communityMapper.findPostById(postId);

        // 노출 상태는 Service가 판단한다.
        assertThat(post.status()).isEqualTo(PostStatus.BLOCKED);
        assertThat(post.blockedReason()).isEqualTo("광고성 게시물");
    }

    @Test
    void findPostById_deletedPost_isStillReturnedToService() {
        long postId = insertPost("삭제된 글", PostStatus.DELETED, BASE_TIME);

        assertThat(communityMapper.findPostById(postId)).isNotNull()
                .extracting(PostDetailRow::status)
                .isEqualTo(PostStatus.DELETED);
    }

    @Test
    void findPostById_publishedPost_mapsDisplayFields() {
        long postId = insertPost("제목", PostStatus.PUBLISHED, BASE_TIME);

        PostDetailRow post = communityMapper.findPostById(postId);

        assertThat(post.memberId()).isEqualTo(memberId);
        assertThat(post.categoryId()).isEqualTo(categoryId);
        assertThat(post.categoryName()).isEqualTo("커뮤니티 테스트");
        assertThat(post.content()).isEqualTo("본문");
        assertThat(post.isEdited()).isFalse();
    }

    /** 조회자 키가 없으면 세지 않는다. */
    @Test
    void increaseViewCount_withoutViewerKey_changesNothing() {
        long postId = insertPost("키 없음", PostStatus.PUBLISHED, BASE_TIME);

        assertThat(communityMapper.increaseViewCount(postId, null)).isZero();
        assertThat(viewCountOf(postId)).isZero();
    }

    /** 같은 조회자의 이력 행은 여러 번 저장할 수 있다. */
    @Test
    void recordView_sameViewerTwice_isAllowedByTheSchema() {
        long postId = insertPost("중복 기록", PostStatus.PUBLISHED, BASE_TIME);
        communityMapper.recordView(postId, "M:1");

        assertThatCode(() -> communityMapper.recordView(postId, "M:1"))
                .doesNotThrowAnyException();
        assertThat(viewHistoryCount(postId)).isEqualTo(2);
    }

    @Test
    void recordView_unknownPost_violatesForeignKey() {
        assertThatThrownBy(() -> communityMapper.recordView(Long.MAX_VALUE, "S:missing"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findActiveCategories_inactiveCategory_isExcluded() {
        long inactiveId = insertCategory(
                "COMMUNITY_INACTIVE_" + System.nanoTime(), "비활성 카테고리", false);

        List<PostCategoryView> categories = communityMapper.findActiveCategories();

        assertThat(categories).extracting(PostCategoryView::id)
                .contains(categoryId)
                .doesNotContain(inactiveId);
    }

    @Test
    void insertPost_newPost_isPublishedAndNotEdited() {
        Post post = postOf(memberId, categoryId, "새 글", "본문");

        communityMapper.insertPost(post);

        assertThat(post.getId()).isNotNull();
        PostDetailRow saved = communityMapper.findPostById(post.getId());
        assertThat(saved.title()).isEqualTo("새 글");
        assertThat(saved.status()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(saved.isEdited()).isFalse();
    }

    @Test
    void updatePost_author_updatesFieldsAndMarksPostAsEdited() {
        long postId = insertPost("원래 제목", PostStatus.PUBLISHED, BASE_TIME);
        Post post = editOf(postId, memberId, otherCategoryId, "고친 제목", "고친 본문");

        int updated = communityMapper.updatePost(post);

        assertThat(updated).isEqualTo(1);
        PostDetailRow saved = communityMapper.findPostById(postId);
        assertThat(saved.title()).isEqualTo("고친 제목");
        assertThat(saved.content()).isEqualTo("고친 본문");
        assertThat(saved.categoryId()).isEqualTo(otherCategoryId);
        assertThat(saved.isEdited()).isTrue();
    }

    @Test
    void updatePost_wrongOwnerOrBlockedPost_updatesNothing() {
        long otherMemberPostId = insertPost("남의 글", PostStatus.PUBLISHED, BASE_TIME);
        long blockedPostId = insertPost("차단된 글", PostStatus.BLOCKED, BASE_TIME);
        Post otherMemberEdit = editOf(
                otherMemberPostId, withdrawnMemberId, categoryId, "가로챈 제목", "본문");
        Post blockedPostEdit = editOf(
                blockedPostId, memberId, categoryId, "고친 제목", "본문");

        assertThat(communityMapper.updatePost(otherMemberEdit)).isZero();
        assertThat(communityMapper.updatePost(blockedPostEdit)).isZero();
        assertThat(communityMapper.findPostById(otherMemberPostId).title()).isEqualTo("남의 글");
        assertThat(communityMapper.findPostById(blockedPostId).title()).isEqualTo("차단된 글");
    }

    @Test
    void deletePost_author_softDeletesPostAndKeepsComments() {
        long postId = insertPost("지울 글", PostStatus.PUBLISHED, BASE_TIME);
        insertComment(postId, CommentStatus.PUBLISHED);

        assertThat(communityMapper.deletePost(postId, memberId)).isEqualTo(1);
        assertThat(communityMapper.findPostById(postId).status()).isEqualTo(PostStatus.DELETED);
        Long comments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comments WHERE post_id = ?", Long.class, postId);
        assertThat(comments).isEqualTo(1L);
    }

    @Test
    void deletePost_wrongOwnerOrBlockedPost_deletesNothing() {
        long otherMemberPostId = insertPost("남의 글", PostStatus.PUBLISHED, BASE_TIME);
        long blockedPostId = insertPost("차단된 글", PostStatus.BLOCKED, BASE_TIME);

        assertThat(communityMapper.deletePost(otherMemberPostId, withdrawnMemberId)).isZero();
        assertThat(communityMapper.deletePost(blockedPostId, memberId)).isZero();
        assertThat(communityMapper.findPostById(otherMemberPostId).status())
                .isEqualTo(PostStatus.PUBLISHED);
        assertThat(communityMapper.findPostById(blockedPostId).status())
                .isEqualTo(PostStatus.BLOCKED);
    }

    @Test
    void existsActiveCategory_inactiveCategory_isFalse() {
        long inactiveId = insertCategory(
                "COMMUNITY_INACTIVE_" + System.nanoTime(), "비활성", false);

        assertThat(communityMapper.existsActiveCategory(categoryId)).isTrue();
        assertThat(communityMapper.existsActiveCategory(inactiveId)).isFalse();
    }

    @Test
    void findRecentComments_filtersAndOrdersNewestWithinLimit() {
        long postId = insertPost("이 글", PostStatus.PUBLISHED, BASE_TIME);
        long otherPostId = insertPost("다른 글", PostStatus.PUBLISHED, BASE_TIME);
        long first = insertComment(postId, "첫 번째", CommentStatus.PUBLISHED, BASE_TIME);
        long second = insertComment(postId, "두 번째", CommentStatus.PUBLISHED, BASE_TIME);
        long newest = insertComment(
                postId, "최신", CommentStatus.PUBLISHED, BASE_TIME.plusMinutes(1));
        insertComment(otherPostId, "다른 글의 댓글", CommentStatus.PUBLISHED, BASE_TIME.plusDays(1));

        assertThat(communityMapper.findRecentComments(postId, 2))
                .extracting(CommentRow::id)
                .containsExactly(newest, second)
                .doesNotContain(first);
    }

    @Test
    void findRecentComments_deletedComment_staysWithoutContent() {
        long postId = insertPost("삭제 댓글", PostStatus.PUBLISHED, BASE_TIME);
        insertComment(postId, "지워진 본문", CommentStatus.DELETED, BASE_TIME);

        CommentRow comment = communityMapper.findRecentComments(postId, 20).getFirst();

        assertThat(comment.isDeleted()).isTrue();
        assertThat(comment.content()).isNull();
    }

    @Test
    void countComments_publishedDeletedAndEmptyRows_returnsExpectedCounts() {
        long postId = insertPost("댓글 개수", PostStatus.PUBLISHED, BASE_TIME);
        long emptyPostId = insertPost("댓글 없는 글", PostStatus.PUBLISHED, BASE_TIME);
        insertComment(postId, CommentStatus.PUBLISHED);
        insertComment(postId, CommentStatus.PUBLISHED);
        insertComment(postId, CommentStatus.DELETED);

        CommentCountView counts = communityMapper.countComments(postId);
        CommentCountView emptyCounts = communityMapper.countComments(emptyPostId);

        assertThat(counts.rowCount()).isEqualTo(3);
        assertThat(counts.publishedCount()).isEqualTo(2);
        assertThat(emptyCounts.rowCount()).isZero();
        assertThat(emptyCounts.publishedCount()).isZero();
    }

    @Test
    void findCommentById_deletedComment_isStillReturnedWithoutContent() {
        long postId = insertPost("글", PostStatus.PUBLISHED, BASE_TIME);
        long commentId = insertComment(postId, "지워진 본문", CommentStatus.DELETED, BASE_TIME);

        CommentRow comment = communityMapper.findCommentById(commentId);

        assertThat(comment).isNotNull();
        assertThat(comment.isDeleted()).isTrue();
        assertThat(comment.content()).isNull();
    }

    @Test
    void insertComment_newComment_isPublishedWithoutParent() {
        long postId = insertPost("글", PostStatus.PUBLISHED, BASE_TIME);
        Comment comment = Comment.create(postId, memberId, "새 댓글");

        communityMapper.insertComment(comment);

        assertThat(comment.getId()).isNotNull();
        CommentRow saved = communityMapper.findCommentById(comment.getId());
        assertThat(saved.content()).isEqualTo("새 댓글");
        assertThat(saved.status()).isEqualTo(CommentStatus.PUBLISHED);
        Long parents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comments WHERE id = ? AND parent_comment_id IS NOT NULL",
                Long.class, comment.getId());
        assertThat(parents).isZero();
    }

    @Test
    void deleteComment_author_marksCommentAsDeleted() {
        long postId = insertPost("글", PostStatus.PUBLISHED, BASE_TIME);
        long commentId = insertComment(postId, "지울 댓글", CommentStatus.PUBLISHED, BASE_TIME);

        assertThat(communityMapper.deleteComment(commentId, postId, memberId)).isEqualTo(1);
        assertThat(communityMapper.findCommentById(commentId).isDeleted()).isTrue();
    }

    @Test
    void deleteComment_wrongOwnerStatusOrPost_deletesNothing() {
        long postId = insertPost("이 글", PostStatus.PUBLISHED, BASE_TIME);
        long otherPostId = insertPost("다른 글", PostStatus.PUBLISHED, BASE_TIME);
        long publishedCommentId = insertComment(
                postId, "댓글", CommentStatus.PUBLISHED, BASE_TIME);
        long deletedCommentId = insertComment(
                postId, "지운 댓글", CommentStatus.DELETED, BASE_TIME);

        assertThat(communityMapper.deleteComment(
                publishedCommentId, postId, withdrawnMemberId)).isZero();
        assertThat(communityMapper.deleteComment(
                publishedCommentId, otherPostId, memberId)).isZero();
        assertThat(communityMapper.deleteComment(
                deletedCommentId, postId, memberId)).isZero();
        assertThat(communityMapper.findCommentById(publishedCommentId).isDeleted()).isFalse();
    }

    /** 잠금 조회는 작성자와 상태를 반환한다. */
    @Test
    void lockPost_returnsAuthorAndStatus() {
        long blocked = insertPost("차단됨", PostStatus.BLOCKED, BASE_TIME);

        PostLockView locked = communityMapper.lockPost(blocked);

        assertThat(locked).isNotNull();
        assertThat(locked.memberId()).isEqualTo(memberId);
        assertThat(locked.status()).isEqualTo(PostStatus.BLOCKED);
    }

    @Test
    void lockPost_missingPost_returnsNull() {
        assertThat(communityMapper.lockPost(999_999_999L)).isNull();
    }

    @Test
    void insertLike_unknownPost_violatesForeignKey() {
        assertThatThrownBy(() -> communityMapper.insertLike(Long.MAX_VALUE, memberId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteLike_neverLiked_changesNothingAndIsNotAnError() {
        long postId = insertPost("좋아요 대상", PostStatus.PUBLISHED, BASE_TIME);

        assertThat(communityMapper.deleteLike(postId, memberId)).isZero();
        assertThat(likeRowsCount(postId)).isZero();
    }

    @Test
    void recalculateLikeCount_wrongCache_recountsRowsWithoutMarkingPostAsEdited() {
        long postId = insertPost("좋아요 대상", PostStatus.PUBLISHED, BASE_TIME);
        long otherMemberId = insertMember(
                "liker-" + System.nanoTime() + "@cakeshop.local", "다른 회원", "ACTIVE");

        communityMapper.insertLike(postId, memberId);
        communityMapper.insertLike(postId, otherMemberId);
        jdbcTemplate.update(
                "UPDATE posts SET like_count = 99, updated_at = updated_at WHERE id = ?", postId);
        LocalDateTime before = updatedAtOf(postId);

        communityMapper.recalculateLikeCount(postId);

        assertThat(likeCountOf(postId)).isEqualTo(2);
        assertThat(updatedAtOf(postId)).isEqualTo(before);
        assertThat(communityMapper.findPostById(postId).isEdited()).isFalse();
    }

    @Test
    void existsLike_isPerMember() {
        long postId = insertPost("좋아요 대상", PostStatus.PUBLISHED, BASE_TIME);
        long otherMemberId = insertMember(
                "liker-" + System.nanoTime() + "@cakeshop.local", "다른 회원", "ACTIVE");

        communityMapper.insertLike(postId, memberId);

        assertThat(communityMapper.existsLike(postId, memberId)).isTrue();
        assertThat(communityMapper.existsLike(postId, otherMemberId)).isFalse();
    }

    private long likeCountOf(long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT like_count FROM posts WHERE id = ?", Long.class, postId);
    }

    private long likeRowsCount(long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_likes WHERE post_id = ?", Long.class, postId);
    }

    private LocalDateTime updatedAtOf(long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT updated_at FROM posts WHERE id = ?", LocalDateTime.class, postId);
    }

    private Post postOf(long authorId, long postCategoryId, String title, String content) {
        return Post.create(authorId, postCategoryId, title, content);
    }

    private Post editOf(
            long postId, long authorId, long postCategoryId, String title, String content) {
        return Post.edit(postId, authorId, postCategoryId, title, content);
    }

    private List<PostListRow> findPage(int page, int size) {
        return findPage(page, size, PostSort.LATEST);
    }

    private List<PostListRow> findPage(int page, int size, PostSort sort) {
        return communityMapper.findPublishedPosts(categoryId, sort, size, (page - 1) * size);
    }

    private void setViewCount(long postId, long viewCount) {
        jdbcTemplate.update(
                "UPDATE posts SET view_count = ?, updated_at = updated_at WHERE id = ?",
                viewCount, postId);
    }

    private long viewCountOf(long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT view_count FROM posts WHERE id = ?", Long.class, postId);
    }

    private long viewHistoryCount(long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_views WHERE post_id = ?", Long.class, postId);
    }

    @Test
    void insertReport_duplicateReporter_isRejectedByUniqueConstraint() {
        long postId = insertPost("신고 대상", PostStatus.PUBLISHED, BASE_TIME);
        long reporterId = insertReporter("dup");

        communityMapper.insertReport(postId, reporterId, "광고입니다");

        assertThatThrownBy(() -> communityMapper.insertReport(postId, reporterId, "또 신고"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void existsReport_afterReportIsClosed_staysTrue() {
        long postId = insertPost("신고 대상", PostStatus.PUBLISHED, BASE_TIME);
        long reporterId = insertReporter("closed");

        communityMapper.insertReport(postId, reporterId, "광고입니다");
        communityAdminMapper.closePendingReports(postId, ReportStatus.RESOLVED);

        assertThat(communityMapper.existsReport(postId, reporterId)).isTrue();
    }

    @Test
    void blockPost_publishedPost_recordsBlockWithoutMarkingPostAsEdited() {
        long postId = insertPost("차단 대상", PostStatus.PUBLISHED, BASE_TIME);
        long adminId = insertReporter("admin");

        assertThat(communityAdminMapper.blockPost(postId, "광고성 게시물", adminId)).isEqualTo(1);

        PostDetailRow post = communityMapper.findPostById(postId);
        assertThat(post.status()).isEqualTo(PostStatus.BLOCKED);
        assertThat(post.blockedReason()).isEqualTo("광고성 게시물");
        assertThat(blockedBy(postId)).isEqualTo(adminId);
        assertThat(post.isEdited()).isFalse();
    }

    @Test
    void blockPost_nonPublishedPost_changesNothing() {
        long blockedId = insertPost("이미 차단", PostStatus.PUBLISHED, BASE_TIME);
        long deletedId = insertPost("지워진 글", PostStatus.DELETED, BASE_TIME);
        long firstAdminId = insertReporter("first-admin");
        long secondAdminId = insertReporter("second-admin");

        communityAdminMapper.blockPost(blockedId, "첫 번째 사유", firstAdminId);

        assertThat(communityAdminMapper.blockPost(
                blockedId, "두 번째 사유", secondAdminId)).isZero();
        assertThat(communityAdminMapper.blockPost(deletedId, "사유", secondAdminId)).isZero();
        assertThat(communityMapper.findPostById(blockedId).blockedReason())
                .isEqualTo("첫 번째 사유");
        assertThat(blockedBy(blockedId)).isEqualTo(firstAdminId);
        assertThat(communityMapper.findPostById(deletedId).status()).isEqualTo(PostStatus.DELETED);
    }

    @Test
    void unblockPost_keepsBlockRecord() {
        long postId = insertPost("해제 대상", PostStatus.PUBLISHED, BASE_TIME);
        long adminId = insertReporter("unblock-admin");

        communityAdminMapper.blockPost(postId, "광고성 게시물", adminId);

        assertThat(communityAdminMapper.unblockPost(postId)).isEqualTo(1);

        PostDetailRow post = communityMapper.findPostById(postId);
        assertThat(post.status()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(post.blockedReason()).isEqualTo("광고성 게시물");
        assertThat(blockedBy(postId)).isEqualTo(adminId);
    }

    @Test
    void unblockPost_nonBlockedPost_changesNothing() {
        long publishedId = insertPost("노출 중", PostStatus.PUBLISHED, BASE_TIME);
        long deletedId = insertPost("지워진 글", PostStatus.DELETED, BASE_TIME);

        assertThat(communityAdminMapper.unblockPost(publishedId)).isZero();
        assertThat(communityAdminMapper.unblockPost(deletedId)).isZero();
        assertThat(communityMapper.findPostById(deletedId).status())
                .isEqualTo(PostStatus.DELETED);
    }

    @Test
    void closePendingReports_leavesAlreadyClosedReportsUntouched() {
        long postId = insertPost("신고 여럿", PostStatus.PUBLISHED, BASE_TIME);
        long firstReporterId = insertReporter("r1");
        long secondReporterId = insertReporter("r2");

        communityMapper.insertReport(postId, firstReporterId, "광고입니다");
        communityAdminMapper.closePendingReports(postId, ReportStatus.REJECTED);

        communityMapper.insertReport(postId, secondReporterId, "욕설입니다");

        assertThat(communityAdminMapper.closePendingReports(postId, ReportStatus.RESOLVED))
                .isEqualTo(1);
        assertThat(communityAdminMapper.countPendingReports(postId)).isZero();
        assertThat(communityAdminMapper.findReportsByPost(postId))
                .extracting(ReportRow::status)
                .containsExactlyInAnyOrder(ReportStatus.REJECTED, ReportStatus.RESOLVED);
    }

    @Test
    void findPostsForAdmin_optionalStatus_filtersOnlyWhenProvided() {
        insertPost("노출", PostStatus.PUBLISHED, BASE_TIME);
        insertPost("차단", PostStatus.BLOCKED, BASE_TIME);
        insertPost("삭제", PostStatus.DELETED, BASE_TIME);

        assertThat(adminPosts(null, AdminPostSort.LATEST))
                .extracting(AdminPostListRow::title)
                .contains("노출", "차단", "삭제");
        assertThat(adminPosts(PostStatus.BLOCKED, AdminPostSort.LATEST))
                .extracting(AdminPostListRow::title)
                .containsExactly("차단");
    }

    @Test
    void findPostsForAdmin_sortedByReports_countsOnlyPendingOnes() {
        long pendingPostId = insertPost("미처리 신고 1건", PostStatus.PUBLISHED, BASE_TIME);
        long closedPostId = insertPost("처리된 신고 2건", PostStatus.PUBLISHED, BASE_TIME);

        communityMapper.insertReport(pendingPostId, insertReporter("p1"), "광고입니다");
        communityMapper.insertReport(closedPostId, insertReporter("c1"), "광고입니다");
        communityMapper.insertReport(closedPostId, insertReporter("c2"), "욕설입니다");
        communityAdminMapper.closePendingReports(closedPostId, ReportStatus.RESOLVED);

        List<AdminPostListRow> posts = adminPosts(null, AdminPostSort.REPORTS);

        assertThat(posts).first()
                .extracting(AdminPostListRow::title)
                .isEqualTo("미처리 신고 1건");
        assertThat(posts).filteredOn(post -> post.id() == closedPostId)
                .first()
                .extracting(AdminPostListRow::pendingReportCount)
                .isEqualTo(0L);
    }

    @Test
    void findPostByIdForAdmin_readsBlockRecordAndSurvivesWithoutIt() {
        long neverBlockedId = insertPost("차단된 적 없음", PostStatus.PUBLISHED, BASE_TIME);
        long blockedId = insertPost("차단됨", PostStatus.PUBLISHED, BASE_TIME);
        long adminId = insertReporter("detail-admin");

        communityAdminMapper.blockPost(blockedId, "광고성 게시물", adminId);

        AdminPostDetailRow neverBlocked = communityAdminMapper.findPostByIdForAdmin(neverBlockedId);
        assertThat(neverBlocked).isNotNull();
        assertThat(neverBlocked.blockedAt()).isNull();
        assertThat(neverBlocked.blockedBy()).isNull();

        AdminPostDetailRow blocked = communityAdminMapper.findPostByIdForAdmin(blockedId);
        assertThat(blocked.blockedAt()).isNotNull();
        assertThat(blocked.blockedReason()).isEqualTo("광고성 게시물");
        assertThat(blocked.blockedBy()).isEqualTo(adminId);
    }

    @Test
    void findPopularPosts_excludesPostsHiddenAfterRanking() {
        long visible = insertPost("노출", PostStatus.PUBLISHED, BASE_TIME);
        long blocked = insertPost("차단", PostStatus.BLOCKED, BASE_TIME);
        long deleted = insertPost("삭제", PostStatus.DELETED, BASE_TIME);

        insertRanking(RANKING_DATE, 1, blocked, 300);
        insertRanking(RANKING_DATE, 2, visible, 200);
        insertRanking(RANKING_DATE, 3, deleted, 100);

        assertThat(communityMapper.findPopularPosts(RANKING_DATE, 10))
                .extracting(PopularPostView::postId)
                .containsExactly(visible);
    }

    @Test
    void findPopularPosts_dateRankingAndLimit_returnsCurrentTopRowsWithDisplayFields() {
        long first = insertPost("1위", PostStatus.PUBLISHED, BASE_TIME);
        long second = insertPost("2위", PostStatus.PUBLISHED, BASE_TIME);
        long third = insertPost("3위", PostStatus.PUBLISHED, BASE_TIME);
        long yesterday = insertPost("어제 1위", PostStatus.PUBLISHED, BASE_TIME);

        insertRanking(RANKING_DATE, 1, first, 10);
        insertRanking(RANKING_DATE, 2, second, 20);
        insertRanking(RANKING_DATE, 3, third, 999);
        insertRanking(RANKING_DATE.minusDays(1), 1, yesterday, 1_000);

        assertThat(communityMapper.findPopularPosts(RANKING_DATE, 2))
                .satisfiesExactly(
                        popular -> {
                            assertThat(popular.ranking()).isEqualTo(1);
                            assertThat(popular.title()).isEqualTo("1위");
                            assertThat(popular.categoryName()).isEqualTo("커뮤니티 테스트");
                        },
                        popular -> {
                            assertThat(popular.ranking()).isEqualTo(2);
                            assertThat(popular.title()).isEqualTo("2위");
                            assertThat(popular.categoryName()).isEqualTo("커뮤니티 테스트");
                        });
    }

    @Test
    void findLatestRankingDate_returnsRunDateEvenWhenNothingWasRanked() {
        long postId = insertPost("1위", PostStatus.PUBLISHED, BASE_TIME);

        insertRanking(RANKING_DATE.minusDays(1), 1, postId, 100);
        insertBatchRun(RANKING_DATE.minusDays(1), 1);
        insertBatchRun(RANKING_DATE, 0);

        assertThat(communityMapper.findLatestRankingDate()).isEqualTo(RANKING_DATE);
        assertThat(communityMapper.findPopularPosts(RANKING_DATE, 10)).isEmpty();
    }

    private void insertRanking(LocalDate rankingDate, int ranking, long postId, long score) {
        jdbcTemplate.update(
                """
                INSERT INTO daily_popular_posts (
                    ranking_date, ranking, post_id, popularity_score,
                    view_count, like_count, comment_count
                )
                VALUES (?, ?, ?, ?, 0, 0, 0)
                """,
                rankingDate, ranking, postId, score);
    }

    private void insertBatchRun(LocalDate rankingDate, int postCount) {
        jdbcTemplate.update(
                """
                INSERT INTO popular_post_batch_runs (ranking_date, post_count)
                VALUES (?, ?)
                """,
                rankingDate, postCount);
    }

    /** 테스트 카테고리의 관리자 게시글만 조회한다. */
    private List<AdminPostListRow> adminPosts(PostStatus status, AdminPostSort sort) {
        return communityAdminMapper.findPostsForAdmin(status, sort, 100, 0).stream()
                .filter(post -> "커뮤니티 테스트".equals(post.categoryName()))
                .toList();
    }

    private long blockedBy(long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT blocked_by FROM posts WHERE id = ?", Long.class, postId);
    }

    private long insertReporter(String tag) {
        return insertMember(
                "reporter-" + tag + "-" + System.nanoTime() + "@cakeshop.local",
                "신고자", "ACTIVE");
    }

    private long insertCategory(String code, String name, boolean active) {
        jdbcTemplate.update(
                """
                INSERT INTO post_categories (code, name, is_active, sort_order)
                VALUES (?, ?, ?, 999)
                """,
                code, name, active ? 1 : 0);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
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

    private long insertPost(String title, PostStatus status, LocalDateTime createdAt) {
        return insertPost(memberId, categoryId, title, status, createdAt);
    }

    private long insertPost(
            long authorId, String title, PostStatus status, LocalDateTime createdAt) {
        return insertPost(authorId, categoryId, title, status, createdAt);
    }

    private long insertPost(
            long authorId,
            long postCategoryId,
            String title,
            PostStatus status,
            LocalDateTime createdAt
    ) {
        // 생성·수정 시각을 같게 저장한다.
        jdbcTemplate.update(
                """
                INSERT INTO posts (
                    member_id, category_id, title, content, status, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                authorId, postCategoryId, title, "본문", status.name(), createdAt, createdAt);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertComment(long postId, CommentStatus status) {
        insertComment(postId, "댓글", status, BASE_TIME);
    }

    private long insertComment(
            long postId, String content, CommentStatus status, LocalDateTime createdAt) {
        return insertComment(postId, memberId, content, status, createdAt);
    }

    private long insertComment(
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

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
