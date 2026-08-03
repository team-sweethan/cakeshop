package com.cakeshop.domain.community.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.cakeshop.domain.community.dto.view.PostCategoryView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListView;
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
        jdbcTemplate.update(
                """
                INSERT INTO comments (post_id, member_id, content, status)
                VALUES (?, ?, ?, ?)
                """,
                postId, memberId, "댓글", status.name());
    }
}
