package com.cakeshop.domain.community.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.cakeshop.domain.community.dto.view.CommentCountView;
import com.cakeshop.domain.community.dto.view.CommentView;
import com.cakeshop.domain.community.dto.view.PostCategoryView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListView;
import com.cakeshop.domain.community.entity.Comment;
import com.cakeshop.domain.community.entity.CommentStatus;
import com.cakeshop.domain.community.entity.Post;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.global.config.MariaDbIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 커뮤니티 목록·상세 쿼리를 실제 MariaDB로 확인한다.
 *
 * <p>다른 테스트가 남긴 게시글과 섞이지 않도록 테스트 전용 카테고리를 만들고 그 카테고리로만
 * 조회한다.
 */
@MybatisTest
@MariaDbIntegrationTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CommunityMapperTests {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 3, 1, 10, 0);

    @Autowired
    private CommunityMapper communityMapper;

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

        List<PostListView> posts = findPage(1, 20);

        assertThat(posts).extracting(PostListView::title).containsExactly("노출");
    }

    @Test
    void findPublishedPosts_sameCreatedAt_ordersByIdDescending() {
        long first = insertPost("첫 번째", PostStatus.PUBLISHED, BASE_TIME);
        long second = insertPost("두 번째", PostStatus.PUBLISHED, BASE_TIME);
        long third = insertPost("세 번째", PostStatus.PUBLISHED, BASE_TIME);

        List<PostListView> posts = findPage(1, 20);

        assertThat(posts).extracting(PostListView::id)
                .containsExactly(third, second, first);
    }

    @Test
    void findPublishedPosts_newerPostsComeFirst() {
        long older = insertPost("어제 글", PostStatus.PUBLISHED, BASE_TIME.minusDays(1));
        long newer = insertPost("오늘 글", PostStatus.PUBLISHED, BASE_TIME);

        List<PostListView> posts = findPage(1, 20);

        assertThat(posts).extracting(PostListView::id).containsExactly(newer, older);
    }

    /**
     * 페이지 경계에서 중복·누락이 없는지 확인한다.
     *
     * <p>작성 시각이 모두 같은 상황이 가장 위험하다. id tiebreaker가 없으면 DB가 매 쿼리마다
     * 다른 순서를 돌려줄 수 있어 어떤 글은 두 페이지에 나오고 어떤 글은 사라진다.
     */
    @Test
    void findPublishedPosts_pagingAcrossPages_hasNoDuplicateOrMissingPost() {
        List<Long> inserted = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            inserted.add(insertPost("글 " + i, PostStatus.PUBLISHED, BASE_TIME));
        }

        List<Long> collected = new ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            collected.addAll(findPage(page, 2).stream().map(PostListView::id).toList());
        }

        assertThat(collected).hasSize(5);
        assertThat(collected).doesNotHaveDuplicates();
        assertThat(collected).containsExactlyInAnyOrderElementsOf(inserted);
    }

    @Test
    void findPublishedPosts_lastPage_returnsRemainingPostsOnly() {
        for (int i = 0; i < 5; i++) {
            insertPost("글 " + i, PostStatus.PUBLISHED, BASE_TIME);
        }

        assertThat(findPage(3, 2)).hasSize(1);
    }

    @Test
    void findPublishedPosts_pageBeyondRange_returnsEmptyList() {
        insertPost("하나뿐인 글", PostStatus.PUBLISHED, BASE_TIME);

        assertThat(findPage(99, 20)).isEmpty();
    }

    @Test
    void findPublishedPosts_deletedComments_areNotCounted() {
        long postId = insertPost("댓글 있는 글", PostStatus.PUBLISHED, BASE_TIME);
        insertComment(postId, CommentStatus.PUBLISHED);
        insertComment(postId, CommentStatus.PUBLISHED);
        insertComment(postId, CommentStatus.DELETED);

        List<PostListView> posts = findPage(1, 20);

        assertThat(posts).singleElement()
                .extracting(PostListView::commentCount)
                .isEqualTo(2L);
    }

    @Test
    void findPublishedPosts_withdrawnAuthor_isMarkedAsWithdrawn() {
        insertPost(withdrawnMemberId, "탈퇴 회원 글", PostStatus.PUBLISHED, BASE_TIME);

        PostListView post = findPage(1, 20).getFirst();

        assertThat(post.authorWithdrawn()).isTrue();
        assertThat(post.authorName()).isEqualTo("탈퇴한 회원");
    }

    @Test
    void findPublishedPosts_activeAuthor_showsNickname() {
        insertPost("일반 회원 글", PostStatus.PUBLISHED, BASE_TIME);

        PostListView post = findPage(1, 20).getFirst();

        assertThat(post.authorWithdrawn()).isFalse();
        assertThat(post.authorName()).isEqualTo("글쓴이");
    }

    @Test
    void findPublishedPosts_categoryFilter_returnsOnlyThatCategory() {
        insertPost("이 카테고리", PostStatus.PUBLISHED, BASE_TIME);
        insertPost(memberId, otherCategoryId, "다른 카테고리", PostStatus.PUBLISHED, BASE_TIME);

        assertThat(findPage(1, 20)).extracting(PostListView::title)
                .containsExactly("이 카테고리");
    }

    @Test
    void countPublishedPosts_countsOnlyPublishedPostsInCategory() {
        insertPost("노출 1", PostStatus.PUBLISHED, BASE_TIME);
        insertPost("노출 2", PostStatus.PUBLISHED, BASE_TIME);
        insertPost("삭제됨", PostStatus.DELETED, BASE_TIME);
        insertPost(memberId, otherCategoryId, "다른 카테고리", PostStatus.PUBLISHED, BASE_TIME);

        assertThat(communityMapper.countPublishedPosts(categoryId)).isEqualTo(2);
    }

    @Test
    void findPostById_blockedPost_isReturnedWithReason() {
        long postId = insertPost("차단된 글", PostStatus.BLOCKED, BASE_TIME);
        jdbcTemplate.update(
                "UPDATE posts SET blocked_reason = ?, blocked_at = ? WHERE id = ?",
                "광고성 게시물", BASE_TIME, postId);

        PostDetailView post = communityMapper.findPostById(postId);

        // 노출 판단은 Service가 한다. Mapper가 상태로 걸러 버리면 작성자에게 사유를 줄 수 없다.
        assertThat(post.status()).isEqualTo(PostStatus.BLOCKED);
        assertThat(post.blockedReason()).isEqualTo("광고성 게시물");
    }

    @Test
    void findPostById_deletedPost_isStillReturnedToService() {
        long postId = insertPost("삭제된 글", PostStatus.DELETED, BASE_TIME);

        assertThat(communityMapper.findPostById(postId)).isNotNull()
                .extracting(PostDetailView::status)
                .isEqualTo(PostStatus.DELETED);
    }

    @Test
    void findPostById_unknownId_returnsNull() {
        assertThat(communityMapper.findPostById(-1L)).isNull();
    }

    @Test
    void findPostById_publishedPost_mapsDisplayFields() {
        long postId = insertPost("제목", PostStatus.PUBLISHED, BASE_TIME);

        PostDetailView post = communityMapper.findPostById(postId);

        assertThat(post.memberId()).isEqualTo(memberId);
        assertThat(post.categoryId()).isEqualTo(categoryId);
        assertThat(post.categoryName()).isEqualTo("커뮤니티 테스트");
        assertThat(post.content()).isEqualTo("본문");
        assertThat(post.authorNickname()).isEqualTo("글쓴이");
        assertThat(post.isEdited()).isFalse();
    }

    @Test
    void increaseViewCount_publishedPost_increasesByOne() {
        long postId = insertPost("조회수", PostStatus.PUBLISHED, BASE_TIME);

        assertThat(communityMapper.increaseViewCount(postId)).isEqualTo(1);
        assertThat(viewCountOf(postId)).isEqualTo(1);
    }

    @Test
    void increaseViewCount_notPublishedPost_changesNothing() {
        long deletedPostId = insertPost("삭제된 글", PostStatus.DELETED, BASE_TIME);
        long blockedPostId = insertPost("차단된 글", PostStatus.BLOCKED, BASE_TIME);

        // 상세가 404인 글의 조회수를 올릴 이유가 없다. UPDATE의 status 조건이 이를 막는다.
        assertThat(communityMapper.increaseViewCount(deletedPostId)).isZero();
        assertThat(communityMapper.increaseViewCount(blockedPostId)).isZero();
        assertThat(viewCountOf(deletedPostId)).isZero();
        assertThat(viewCountOf(blockedPostId)).isZero();
    }

    /**
     * H1c — 조회수 증가가 {@code updated_at}을 건드리지 않는지 확인한다.
     *
     * <p>{@code posts.updated_at}은 ON UPDATE CURRENT_TIMESTAMP다. 조회수 UPDATE에서
     * 이 컬럼을 명시적으로 지정하지 않으면 값이 자동으로 바뀌고, 그러면
     * "updated_at != created_at이면 수정됨"(DOMAIN.md 6.3) 표시가 조회만으로 켜진다.
     * 화면에는 조용히 "(수정됨)"이 붙을 뿐이라 눈으로는 원인을 찾기 어렵다.
     */
    @Test
    void increaseViewCount_doesNotMarkPostAsEdited() {
        long postId = insertPost("조회만 한 글", PostStatus.PUBLISHED, BASE_TIME);

        communityMapper.increaseViewCount(postId);

        PostDetailView post = communityMapper.findPostById(postId);
        assertThat(post.updatedAt()).isEqualTo(post.createdAt());
        assertThat(post.isEdited()).isFalse();
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
    void findActiveCategories_containsMvpCategories() {
        assertThat(communityMapper.findActiveCategories())
                .extracting(PostCategoryView::code)
                .contains("QNA", "REVIEW", "FREE");
    }

    @Test
    void insertPost_fillsGeneratedIdAndStoresPublished() {
        Post post = postOf(memberId, categoryId, "새 글", "본문");

        communityMapper.insertPost(post);

        assertThat(post.getId()).isNotNull();
        PostDetailView saved = communityMapper.findPostById(post.getId());
        assertThat(saved.title()).isEqualTo("새 글");
        // 새 글은 언제나 PUBLISHED다. 컬럼 기본값에 맡기지 않고 SQL이 명시한다.
        assertThat(saved.status()).isEqualTo(PostStatus.PUBLISHED);
    }

    /** 새 글에는 "수정됨"이 켜져 있으면 안 된다(DOMAIN.md 6.3). */
    @Test
    void insertPost_newPost_isNotMarkedAsEdited() {
        Post post = postOf(memberId, categoryId, "새 글", "본문");

        communityMapper.insertPost(post);

        assertThat(communityMapper.findPostById(post.getId()).isEdited()).isFalse();
    }

    @Test
    void updatePost_author_updatesTitleContentAndCategory() {
        long postId = insertPost("원래 제목", PostStatus.PUBLISHED, BASE_TIME);
        Post post = editOf(postId, memberId, otherCategoryId, "고친 제목", "고친 본문");

        int updated = communityMapper.updatePost(post);

        assertThat(updated).isEqualTo(1);
        PostDetailView saved = communityMapper.findPostById(postId);
        assertThat(saved.title()).isEqualTo("고친 제목");
        assertThat(saved.content()).isEqualTo("고친 본문");
        assertThat(saved.categoryId()).isEqualTo(otherCategoryId);
    }

    /** 수정하면 "수정됨"이 켜져야 한다. 조회수 UPDATE와 반대로 updated_at을 보존하지 않는다. */
    @Test
    void updatePost_marksPostAsEdited() {
        long postId = insertPost("원래 제목", PostStatus.PUBLISHED, BASE_TIME);
        Post post = editOf(postId, memberId, categoryId, "고친 제목", "본문");

        communityMapper.updatePost(post);

        assertThat(communityMapper.findPostById(postId).isEdited()).isTrue();
    }

    /**
     * 소유권 조건이 SQL에도 있는지 확인한다.
     *
     * <p>Service가 먼저 막지만, 조건을 SQL에서 지우면 그 검증 하나가 유일한 방어가 된다.
     */
    @Test
    void updatePost_otherMember_updatesNothing() {
        long postId = insertPost("원래 제목", PostStatus.PUBLISHED, BASE_TIME);
        Post post = editOf(postId, withdrawnMemberId, categoryId, "가로챈 제목", "본문");

        assertThat(communityMapper.updatePost(post)).isZero();
        assertThat(communityMapper.findPostById(postId).title()).isEqualTo("원래 제목");
    }

    @Test
    void updatePost_blockedPost_updatesNothing() {
        long postId = insertPost("차단된 글", PostStatus.BLOCKED, BASE_TIME);
        Post post = editOf(postId, memberId, categoryId, "고친 제목", "본문");

        assertThat(communityMapper.updatePost(post)).isZero();
    }

    @Test
    void deletePost_author_marksPostAsDeleted() {
        long postId = insertPost("지울 글", PostStatus.PUBLISHED, BASE_TIME);

        assertThat(communityMapper.deletePost(postId, memberId)).isEqualTo(1);
        assertThat(communityMapper.findPostById(postId).status()).isEqualTo(PostStatus.DELETED);
    }

    /** 삭제는 행을 지우지 않는다. 댓글·좋아요·신고가 그대로 매달려 있어야 한다(DOMAIN.md 4.5). */
    @Test
    void deletePost_keepsChildComments() {
        long postId = insertPost("지울 글", PostStatus.PUBLISHED, BASE_TIME);
        insertComment(postId, CommentStatus.PUBLISHED);

        communityMapper.deletePost(postId, memberId);

        Long comments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM comments WHERE post_id = ?", Long.class, postId);
        assertThat(comments).isEqualTo(1L);
    }

    /**
     * 차단된 글은 삭제되지 않는다.
     *
     * <p>{@code BLOCKED -> DELETED} 금지(DOMAIN.md 4.2). 차단된 글은 신고·조치의 증거다.
     */
    @Test
    void deletePost_blockedPost_deletesNothing() {
        long postId = insertPost("차단된 글", PostStatus.BLOCKED, BASE_TIME);

        assertThat(communityMapper.deletePost(postId, memberId)).isZero();
        assertThat(communityMapper.findPostById(postId).status()).isEqualTo(PostStatus.BLOCKED);
    }

    @Test
    void deletePost_otherMember_deletesNothing() {
        long postId = insertPost("남의 글", PostStatus.PUBLISHED, BASE_TIME);

        assertThat(communityMapper.deletePost(postId, withdrawnMemberId)).isZero();
        assertThat(communityMapper.findPostById(postId).status()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    void existsActiveCategory_inactiveCategory_isFalse() {
        long inactiveId = insertCategory(
                "COMMUNITY_INACTIVE_" + System.nanoTime(), "비활성", false);

        assertThat(communityMapper.existsActiveCategory(categoryId)).isTrue();
        assertThat(communityMapper.existsActiveCategory(inactiveId)).isFalse();
    }

    @Test
    void findRecentComments_returnsNewestFirstWithinLimit() {
        long postId = insertPost("댓글 있는 글", PostStatus.PUBLISHED, BASE_TIME);
        insertComment(postId, "첫 번째", CommentStatus.PUBLISHED, BASE_TIME);
        insertComment(postId, "두 번째", CommentStatus.PUBLISHED, BASE_TIME.plusMinutes(1));
        insertComment(postId, "세 번째", CommentStatus.PUBLISHED, BASE_TIME.plusMinutes(2));

        // 잘라 내는 쪽이 과거여야 방금 쓴 댓글이 화면에 남는다(DOMAIN.md 6.4).
        assertThat(communityMapper.findRecentComments(postId, 2))
                .extracting(CommentView::content)
                .containsExactly("세 번째", "두 번째");
    }

    /**
     * limit 경계에서 순서가 흔들리지 않는지 확인한다.
     *
     * <p>작성 시각이 같은 댓글이 있으면 id tiebreaker 없이는 DB가 매 쿼리마다 다른 순서를
     * 돌려줄 수 있다. 그러면 "더 보기"를 눌렀을 때 어떤 댓글이 사라지거나 두 번 나온다.
     */
    @Test
    void findRecentComments_sameCreatedAt_ordersByIdDescending() {
        long postId = insertPost("동시 댓글", PostStatus.PUBLISHED, BASE_TIME);
        long first = insertComment(postId, "첫 번째", CommentStatus.PUBLISHED, BASE_TIME);
        long second = insertComment(postId, "두 번째", CommentStatus.PUBLISHED, BASE_TIME);
        long third = insertComment(postId, "세 번째", CommentStatus.PUBLISHED, BASE_TIME);

        assertThat(communityMapper.findRecentComments(postId, 20))
                .extracting(CommentView::id)
                .containsExactly(third, second, first);
    }

    /**
     * 삭제된 댓글이 목록에 남되 본문은 오지 않는지 확인한다.
     *
     * <p>자리 표시로 남기는 것은 DOMAIN.md 4.4다. 본문을 NULL로 지우는 것은 그 자리에
     * 쓰지 않는 값이기 때문이다 — 내려보내지 않으면 템플릿을 잘못 고쳐도 지워진 댓글이
     * 되살아나지 않는다.
     */
    @Test
    void findRecentComments_deletedComment_staysWithoutContent() {
        long postId = insertPost("삭제 댓글", PostStatus.PUBLISHED, BASE_TIME);
        insertComment(postId, "지워진 본문", CommentStatus.DELETED, BASE_TIME);

        CommentView comment = communityMapper.findRecentComments(postId, 20).getFirst();

        assertThat(comment.isDeleted()).isTrue();
        assertThat(comment.content()).isNull();
    }

    @Test
    void findRecentComments_otherPostComments_areExcluded() {
        long postId = insertPost("이 글", PostStatus.PUBLISHED, BASE_TIME);
        long otherPostId = insertPost("다른 글", PostStatus.PUBLISHED, BASE_TIME);
        insertComment(postId, "이 글의 댓글", CommentStatus.PUBLISHED, BASE_TIME);
        insertComment(otherPostId, "다른 글의 댓글", CommentStatus.PUBLISHED, BASE_TIME);

        assertThat(communityMapper.findRecentComments(postId, 20))
                .extracting(CommentView::content)
                .containsExactly("이 글의 댓글");
    }

    /** 탈퇴 회원의 댓글도 지우지 않고 표시명만 가린다(DOMAIN.md 8). */
    @Test
    void findRecentComments_withdrawnAuthor_showsPlaceholderName() {
        long postId = insertPost("탈퇴 회원 댓글", PostStatus.PUBLISHED, BASE_TIME);
        insertComment(postId, withdrawnMemberId, "댓글", CommentStatus.PUBLISHED, BASE_TIME);

        CommentView comment = communityMapper.findRecentComments(postId, 20).getFirst();

        assertThat(comment.authorWithdrawn()).isTrue();
        assertThat(comment.authorName()).isEqualTo("탈퇴한 회원");
    }

    /**
     * 두 개수가 서로 다른 것을 센다.
     *
     * <p>자리 표시를 포함한 행 수는 "더 보기"가 남았는지 판단하고, 노출 중인 수는 화면의
     * "댓글 N"이다. 하나로 합치면 둘 중 하나가 반드시 틀린다(DOMAIN.md 4.4).
     */
    @Test
    void countComments_countsPlaceholderRowsAndPublishedSeparately() {
        long postId = insertPost("댓글 개수", PostStatus.PUBLISHED, BASE_TIME);
        insertComment(postId, CommentStatus.PUBLISHED);
        insertComment(postId, CommentStatus.PUBLISHED);
        insertComment(postId, CommentStatus.DELETED);

        CommentCountView counts = communityMapper.countComments(postId);

        assertThat(counts.rowCount()).isEqualTo(3);
        assertThat(counts.publishedCount()).isEqualTo(2);
    }

    /** 댓글이 하나도 없으면 SUM이 NULL이라 COALESCE가 없으면 매핑에서 터진다. */
    @Test
    void countComments_postWithoutComments_isZero() {
        long postId = insertPost("댓글 없는 글", PostStatus.PUBLISHED, BASE_TIME);

        CommentCountView counts = communityMapper.countComments(postId);

        assertThat(counts.rowCount()).isZero();
        assertThat(counts.publishedCount()).isZero();
    }

    @Test
    void findCommentById_deletedComment_isStillReturnedWithoutContent() {
        long postId = insertPost("글", PostStatus.PUBLISHED, BASE_TIME);
        long commentId = insertComment(postId, "지워진 본문", CommentStatus.DELETED, BASE_TIME);

        CommentView comment = communityMapper.findCommentById(commentId);

        // 상태로 걸러 버리면 없는 댓글과 지워진 댓글을 Service가 구분할 수 없다.
        assertThat(comment).isNotNull();
        assertThat(comment.isDeleted()).isTrue();
        assertThat(comment.content()).isNull();
    }

    @Test
    void findCommentById_unknownId_returnsNull() {
        assertThat(communityMapper.findCommentById(-1L)).isNull();
    }

    @Test
    void insertComment_fillsGeneratedIdAndStoresPublished() {
        long postId = insertPost("글", PostStatus.PUBLISHED, BASE_TIME);
        Comment comment = Comment.create(postId, memberId, "새 댓글");

        communityMapper.insertComment(comment);

        assertThat(comment.getId()).isNotNull();
        CommentView saved = communityMapper.findCommentById(comment.getId());
        assertThat(saved.content()).isEqualTo("새 댓글");
        assertThat(saved.status()).isEqualTo(CommentStatus.PUBLISHED);
    }

    /**
     * 1차에 대댓글은 없다(DOMAIN.md 6.4).
     *
     * <p>컬럼은 V0에 있고 NULL을 허용하므로, INSERT에 끼어들어도 아무 오류 없이 저장된다.
     * 값이 들어간 뒤에는 화면에 나올 방법이 없는 데이터가 되고, 2차 대댓글 작업 때 "언제
     * 들어간 값인지" 모르는 행으로 남는다.
     */
    @Test
    void insertComment_leavesParentCommentIdNull() {
        long postId = insertPost("글", PostStatus.PUBLISHED, BASE_TIME);
        Comment comment = Comment.create(postId, memberId, "새 댓글");

        communityMapper.insertComment(comment);

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

    /**
     * 소유권 조건이 SQL에도 있는지 확인한다.
     *
     * <p>Service가 먼저 막지만, 조건을 SQL에서 지우면 그 검증 하나가 유일한 방어가 된다.
     */
    @Test
    void deleteComment_otherMember_deletesNothing() {
        long postId = insertPost("글", PostStatus.PUBLISHED, BASE_TIME);
        long commentId = insertComment(postId, "남의 댓글", CommentStatus.PUBLISHED, BASE_TIME);

        assertThat(communityMapper.deleteComment(commentId, postId, withdrawnMemberId)).isZero();
        assertThat(communityMapper.findCommentById(commentId).isDeleted()).isFalse();
    }

    /** 이미 지운 댓글을 다시 지우는 것이 성공으로 보이면 안 된다. */
    @Test
    void deleteComment_alreadyDeletedComment_deletesNothing() {
        long postId = insertPost("글", PostStatus.PUBLISHED, BASE_TIME);
        long commentId = insertComment(postId, "지운 댓글", CommentStatus.DELETED, BASE_TIME);

        assertThat(communityMapper.deleteComment(commentId, postId, memberId)).isZero();
    }

    /**
     * 다른 글의 주소로는 지울 수 없는지 확인한다.
     *
     * <p>post_id 조건이 없으면 댓글 번호만 맞으면 아무 글의 주소로나 삭제 요청을 만들 수
     * 있다. 화면에는 그 댓글이 없으므로 눈으로는 드러나지 않는다.
     */
    @Test
    void deleteComment_wrongPostId_deletesNothing() {
        long postId = insertPost("이 글", PostStatus.PUBLISHED, BASE_TIME);
        long otherPostId = insertPost("다른 글", PostStatus.PUBLISHED, BASE_TIME);
        long commentId = insertComment(postId, "내 댓글", CommentStatus.PUBLISHED, BASE_TIME);

        assertThat(communityMapper.deleteComment(commentId, otherPostId, memberId)).isZero();
        assertThat(communityMapper.findCommentById(commentId).isDeleted()).isFalse();
    }

    private Post postOf(long authorId, long postCategoryId, String title, String content) {
        return Post.create(authorId, postCategoryId, title, content);
    }

    private Post editOf(
            long postId, long authorId, long postCategoryId, String title, String content) {
        return Post.edit(postId, authorId, postCategoryId, title, content);
    }

    private List<PostListView> findPage(int page, int size) {
        return communityMapper.findPublishedPosts(categoryId, size, (page - 1) * size);
    }

    private long viewCountOf(long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT view_count FROM posts WHERE id = ?", Long.class, postId);
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
        // updated_at을 created_at과 같게 넣어야 "수정됨" 판정을 그대로 검증할 수 있다.
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
