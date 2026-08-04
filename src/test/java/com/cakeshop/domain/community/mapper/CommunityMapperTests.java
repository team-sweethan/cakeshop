package com.cakeshop.domain.community.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.cakeshop.domain.community.dto.view.AdminPostDetailView;
import com.cakeshop.domain.community.dto.view.AdminPostListView;
import com.cakeshop.domain.community.dto.view.AdminPostSort;
import com.cakeshop.domain.community.dto.view.CommentCountView;
import com.cakeshop.domain.community.dto.view.CommentView;
import com.cakeshop.domain.community.dto.view.PostCategoryView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListView;
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
import org.springframework.dao.DuplicateKeyException;
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

    /**
     * 관리자 쪽 문장은 CommunityAdminMapper로 갈라져 있다(고객 경로와 노출 규칙이 정반대라
     * 나눴다). 검사는 한 클래스에 둔다 — 차단·기각이 고객 경로의 조회수·좋아요와 <b>같은
     * 게시글 행</b>을 만지므로, 같은 픽스처 위에서 확인해야 두 쪽이 어긋나는 순간이 잡힌다.
     */
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
     * 조회수순이 실제로 조회수로 정렬하는지 확인한다.
     *
     * <p>작성 시각을 <b>조회수와 반대 순서로</b> 준다. 시각이 같거나 같은 방향이면 정렬
     * 분기를 통째로 지우고 최신순으로 고정한 구현이 그대로 통과한다.
     */
    @Test
    void findPublishedPosts_sortByViews_ordersByViewCountDescending() {
        long few = insertPost("적게 본 글", PostStatus.PUBLISHED, BASE_TIME);
        long many = insertPost("많이 본 글", PostStatus.PUBLISHED, BASE_TIME.minusDays(1));

        setViewCount(few, 3);
        setViewCount(many, 100);

        List<PostListView> posts = findPage(1, 20, PostSort.VIEWS);

        assertThat(posts).extracting(PostListView::id).containsExactly(many, few);
    }

    /**
     * 조회수가 같은 글이 id 역순으로 갈리는지 확인한다(H28의 실제 데이터판).
     *
     * <p>조회수는 0이 대부분이라 <b>동점이 최신순보다 훨씬 잦다.</b> tiebreaker가 없으면
     * DB가 매 쿼리마다 다른 순서를 돌려줄 수 있고, 그러면 페이지 경계에서 글이 중복되거나
     * 사라진다. 여기서는 셋 다 조회수 0인 기본 상태를 그대로 쓴다.
     */
    @Test
    void findPublishedPosts_sortByViews_sameViewCount_ordersByIdDescending() {
        long first = insertPost("첫 번째", PostStatus.PUBLISHED, BASE_TIME);
        long second = insertPost("두 번째", PostStatus.PUBLISHED, BASE_TIME);
        long third = insertPost("세 번째", PostStatus.PUBLISHED, BASE_TIME);

        List<PostListView> posts = findPage(1, 20, PostSort.VIEWS);

        assertThat(posts).extracting(PostListView::id)
                .containsExactly(third, second, first);
    }

    /**
     * 조회수순에서도 노출 조건이 살아 있는지 확인한다.
     *
     * <p>새 분기에서 {@code status} 조건이 빠지면 <b>가장 많이 본 차단된 글이 목록 맨 위에</b>
     * 뜬다. 분기가 늘 때 조건 한 벌을 흘리는 것은 조각 3·5에서 두 번 겪은 유형이다.
     */
    @Test
    void findPublishedPosts_sortByViews_deletedAndBlockedPosts_areExcluded() {
        long visible = insertPost("노출", PostStatus.PUBLISHED, BASE_TIME);
        long blocked = insertPost("차단", PostStatus.BLOCKED, BASE_TIME);
        long deleted = insertPost("삭제", PostStatus.DELETED, BASE_TIME);

        setViewCount(visible, 1);
        setViewCount(blocked, 999);
        setViewCount(deleted, 998);

        List<PostListView> posts = findPage(1, 20, PostSort.VIEWS);

        assertThat(posts).extracting(PostListView::id).containsExactly(visible);
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
    void increaseViewCount_firstView_countsOnce() {
        long postId = insertPost("조회수", PostStatus.PUBLISHED, BASE_TIME);

        assertThat(view(postId, "M:1")).isEqualTo(1);
        assertThat(viewCountOf(postId)).isEqualTo(1);
        assertThat(communityMapper.countViews(postId)).isEqualTo(1);
    }

    /**
     * 같은 조회자의 연속 재조회가 세어지지 않는지 확인한다.
     *
     * <p>조회수가 순위를 정하는 이상 새로고침 한 번이 곧 순위 조작이다(DOMAIN.md 6.2).
     */
    @Test
    void increaseViewCount_sameViewerWithinWindow_doesNotCountAgain() {
        long postId = insertPost("재조회", PostStatus.PUBLISHED, BASE_TIME);

        assertThat(view(postId, "M:1")).isEqualTo(1);
        assertThat(view(postId, "M:1")).isZero();
        assertThat(view(postId, "M:1")).isZero();

        assertThat(viewCountOf(postId)).isEqualTo(1);
        assertThat(communityMapper.countViews(postId)).isEqualTo(1);
    }

    @Test
    void increaseViewCount_differentViewers_countEach() {
        long postId = insertPost("여러 조회자", PostStatus.PUBLISHED, BASE_TIME);

        assertThat(view(postId, "M:1")).isEqualTo(1);
        assertThat(view(postId, "M:2")).isEqualTo(1);
        // 비로그인 세션 키도 같은 규칙으로 센다.
        assertThat(view(postId, "S:abc")).isEqualTo(1);

        assertThat(viewCountOf(postId)).isEqualTo(3);
        assertThat(communityMapper.countViews(postId)).isEqualTo(3);
    }

    /**
     * 창을 벗어나면 다시 세는지 확인한다.
     *
     * <p>이것이 없으면 "무조건 0"으로 만들어도 위 테스트가 통과한다. 중복을 막는 것과
     * 조회를 영영 잃는 것은 다르다.
     */
    @Test
    void increaseViewCount_afterWindow_countsAgain() {
        long postId = insertPost("창 밖", PostStatus.PUBLISHED, BASE_TIME);
        view(postId, "M:1");

        ageLastView(postId, 11);

        assertThat(view(postId, "M:1")).isEqualTo(1);
        assertThat(viewCountOf(postId)).isEqualTo(2);
        assertThat(communityMapper.countViews(postId)).isEqualTo(2);
    }

    /**
     * 창의 경계가 실제로 10분인지 확인한다(DOMAIN.md 6.2).
     *
     * <p>위 테스트만 있으면 창이 1분이어도, 하루여도 똑같이 통과한다 — 11분 전 조회는
     * 어느 쪽에서도 창 밖이기 때문이다. 값이 조용히 틀어지는 것을 잡으려면 <b>안쪽</b>도
     * 함께 봐야 한다. 창이 좁아지면 조회수가 부풀고, 넓어지면 숫자가 멈춘 것처럼 보인다.
     */
    @Test
    void increaseViewCount_justInsideWindow_doesNotCountAgain() {
        long postId = insertPost("창 안", PostStatus.PUBLISHED, BASE_TIME);
        view(postId, "M:1");

        ageLastView(postId, 9);

        assertThat(view(postId, "M:1")).isZero();
        assertThat(viewCountOf(postId)).isEqualTo(1);
        assertThat(communityMapper.countViews(postId)).isEqualTo(1);
    }

    /**
     * 노출되지 않는 글은 숫자도 <b>이력</b>도 남기지 않는지 확인한다.
     *
     * <p>조회수만 막고 기록을 남기면 이력과 숫자가 갈라진다. 그러면 나중에 이력에서
     * 다시 셀 때 조용히 값이 늘어난다.
     */
    @Test
    void increaseViewCount_notPublishedPost_changesNothing() {
        long deletedPostId = insertPost("삭제된 글", PostStatus.DELETED, BASE_TIME);
        long blockedPostId = insertPost("차단된 글", PostStatus.BLOCKED, BASE_TIME);

        // 상세가 404인 글의 조회수를 올릴 이유가 없다. UPDATE의 status 조건이 이를 막는다.
        assertThat(view(deletedPostId, "M:1")).isZero();
        assertThat(view(blockedPostId, "M:1")).isZero();
        assertThat(viewCountOf(deletedPostId)).isZero();
        assertThat(viewCountOf(blockedPostId)).isZero();
        assertThat(communityMapper.countViews(deletedPostId)).isZero();
        assertThat(communityMapper.countViews(blockedPostId)).isZero();
    }

    @Test
    void increaseViewCount_unknownPost_changesNothing() {
        assertThat(communityMapper.increaseViewCount(-1L, "M:1")).isZero();
    }

    /**
     * 조회자 키가 없으면 세지 않는지 확인한다.
     *
     * <p>키 없이 세면 키가 없는 조회들이 전부 한 사람으로 합쳐지거나, 반대로 매번 다른
     * 사람으로 세어져 중복 방지가 사라진다. 어느 쪽이든 조용히 일어난다.
     */
    @Test
    void increaseViewCount_withoutViewerKey_changesNothing() {
        long postId = insertPost("키 없음", PostStatus.PUBLISHED, BASE_TIME);

        assertThat(communityMapper.increaseViewCount(postId, null)).isZero();
        assertThat(viewCountOf(postId)).isZero();
    }

    /**
     * 이력에 UNIQUE가 <b>없는</b> 것이 의도임을 고정한다.
     *
     * <p>굴러가는 창은 제약으로 표현할 수 없어 {@code uk_post_views_post_viewer_date}를
     * 지웠다(V20260804_102934). 이 테스트는 실패 가능성이 아니라 <b>되돌아올 위험</b>을
     * 막는다 — 나중에 누군가 "중복이 걱정되니 UNIQUE를 다시 걸자"고 하면, 조회자가 한
     * 게시글을 두 번째로 열어보는 순간부터 이 INSERT가 예외를 던져 상세가 500이 된다.
     * 그 순간은 시드 직후 로컬 확인에서는 나오지 않고 며칠 뒤 운영에서 나온다.
     *
     * <p>중복을 실제로 막는 것은 이 제약이 아니라 {@code increaseViewCount}가 먼저 거는
     * 배타 잠금이며, 그쪽은 {@code CommunityViewCountConcurrencyTests}가 지킨다(H14).
     */
    @Test
    void recordView_sameViewerTwice_isAllowedByTheSchema() {
        long postId = insertPost("중복 기록", PostStatus.PUBLISHED, BASE_TIME);
        communityMapper.recordView(postId, "M:1");

        assertThatCode(() -> communityMapper.recordView(postId, "M:1"))
                .doesNotThrowAnyException();
        assertThat(communityMapper.countViews(postId)).isEqualTo(2);
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

        communityMapper.increaseViewCount(postId, "M:1");

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

    /** 잠금 조회는 권한 판단에 필요한 두 값을 돌려준다. 없는 글이면 null이다. */
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

    /**
     * 같은 회원이 두 번 눌러도 행이 하나인지 확인한다.
     *
     * <p>UNIQUE 제약이 막아 주지만, 그 위반이 <b>예외로 새어 나오지 않는</b> 것까지가 규칙이다
     * (DOMAIN.md 6.5). 두 번째 호출이 터지면 재전송·더블클릭이 에러 화면이 된다.
     */
    @Test
    void insertLike_pressedTwice_keepsSingleRowWithoutError() {
        long postId = insertPost("좋아요 대상", PostStatus.PUBLISHED, BASE_TIME);

        communityMapper.insertLike(postId, memberId);
        communityMapper.insertLike(postId, memberId);

        assertThat(communityMapper.countLikes(postId)).isEqualTo(1);
    }

    /** 누른 적 없는 좋아요를 거둬도 에러가 아니다. 사용자가 원한 상태가 이미 이뤄져 있다. */
    @Test
    void deleteLike_neverLiked_changesNothingAndIsNotAnError() {
        long postId = insertPost("좋아요 대상", PostStatus.PUBLISHED, BASE_TIME);

        assertThat(communityMapper.deleteLike(postId, memberId)).isZero();
        assertThat(communityMapper.countLikes(postId)).isZero();
    }

    /**
     * 재계산이 실제 행 수를 다시 세는지 확인한다.
     *
     * <p>어긋난 값에서 시작해도 한 번의 재계산으로 맞아야 한다 — 증분이 아니라 재계산을
     * 택한 이유가 정확히 이것이다(DOMAIN.md 6.5). 증분이면 어긋난 값은 영원히 어긋난 채다.
     */
    @Test
    void recalculateLikeCount_recountsFromRowsEvenWhenTheCachedValueIsWrong() {
        long postId = insertPost("좋아요 대상", PostStatus.PUBLISHED, BASE_TIME);
        long otherMemberId = insertMember(
                "liker-" + System.nanoTime() + "@cakeshop.local", "다른 회원", "ACTIVE");

        communityMapper.insertLike(postId, memberId);
        communityMapper.insertLike(postId, otherMemberId);
        // 캐시가 틀어진 상태를 만든다. 증분 구현이라면 여기서부터 영영 틀린다.
        jdbcTemplate.update("UPDATE posts SET like_count = 99 WHERE id = ?", postId);

        communityMapper.recalculateLikeCount(postId);

        assertThat(likeCountOf(postId)).isEqualTo(2);
    }

    /**
     * 좋아요가 게시글을 "수정됨"으로 만들지 않는지 확인한다(H1c의 좋아요판).
     *
     * <p>{@code posts.updated_at}은 ON UPDATE CURRENT_TIMESTAMP라서 재계산 UPDATE만으로도
     * 값이 바뀐다. 그러면 좋아요를 받은 글마다 화면에 "(수정됨)"이 붙는다(DOMAIN.md 6.3).
     * 시드에 실제로 있던 버그이고, 화면에는 조용히 표시만 붙어서 원인을 찾기 어렵다.
     */
    @Test
    void recalculateLikeCount_doesNotMarkPostAsEdited() {
        long postId = insertPost("좋아요 대상", PostStatus.PUBLISHED, BASE_TIME);
        LocalDateTime before = updatedAtOf(postId);

        communityMapper.insertLike(postId, memberId);
        communityMapper.recalculateLikeCount(postId);

        assertThat(updatedAtOf(postId)).isEqualTo(before);
        assertThat(communityMapper.findPostById(postId).isEdited()).isFalse();
    }

    /** 눌러 뒀는지 여부는 회원마다 갈린다. 남이 누른 것이 내 버튼을 바꾸면 안 된다. */
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

    private List<PostListView> findPage(int page, int size) {
        return findPage(page, size, PostSort.LATEST);
    }

    private List<PostListView> findPage(int page, int size, PostSort sort) {
        return communityMapper.findPublishedPosts(categoryId, sort, size, (page - 1) * size);
    }

    private void setViewCount(long postId, long viewCount) {
        jdbcTemplate.update(
                "UPDATE posts SET view_count = ?, updated_at = updated_at WHERE id = ?",
                viewCount, postId);
    }

    /**
     * 상세 조회 한 번과 같은 일을 한다. 창 밖의 조회면 1, 창 안이면 0이다.
     *
     * <p>두 문장은 Service가 묶어서 부른다(CommunityService.getPostDetail). 순서가 있고
     * 조건이 붙어 있어서, 하나만 불러서는 규칙을 확인할 수 없다.
     */
    private int view(long postId, String viewerKey) {
        int counted = communityMapper.increaseViewCount(postId, viewerKey);

        if (counted > 0) {
            communityMapper.recordView(postId, viewerKey);
        }

        return counted;
    }

    private long viewCountOf(long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT view_count FROM posts WHERE id = ?", Long.class, postId);
    }

    /**
     * 이 게시글의 조회 이력을 {@code minutes}분 전으로 민다.
     *
     * <p>창 판단이 DB의 {@code NOW(6)}를 쓰므로 테스트가 시각을 주입할 자리가 없다.
     * 시계를 기다리는 대신 이력을 과거로 옮긴다 — 실제로 10분을 기다리는 테스트는 쓸 수 없다.
     */
    private void ageLastView(long postId, int minutes) {
        jdbcTemplate.update(
                "UPDATE post_views SET created_at = created_at - INTERVAL ? MINUTE"
                        + " WHERE post_id = ?",
                minutes, postId);
    }

    /** 같은 사람이 같은 글을 두 번 신고하면 UNIQUE 위반이다. 삼키지 않는다(DOMAIN.md 6.6). */
    @Test
    void insertReport_duplicateReporter_isRejectedByUniqueConstraint() {
        long postId = insertPost("신고 대상", PostStatus.PUBLISHED, BASE_TIME);
        long reporterId = insertReporter("dup");

        communityMapper.insertReport(postId, reporterId, "광고입니다");

        assertThatThrownBy(() -> communityMapper.insertReport(postId, reporterId, "또 신고"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    /**
     * 이미 신고했는지가 처리 상태와 무관한지 확인한다.
     *
     * <p>처리된 신고를 "없음"으로 보면 재신고가 열리는데, UNIQUE 제약은 그대로라 화면에는
     * 신고 성공이 아니라 500이 나온다(DOMAIN.md 6.6).
     */
    @Test
    void existsReport_afterReportIsClosed_staysTrue() {
        long postId = insertPost("신고 대상", PostStatus.PUBLISHED, BASE_TIME);
        long reporterId = insertReporter("closed");

        communityMapper.insertReport(postId, reporterId, "광고입니다");
        communityAdminMapper.closePendingReports(postId, ReportStatus.RESOLVED);

        assertThat(communityMapper.existsReport(postId, reporterId)).isTrue();
    }

    /** 차단은 상태·시각·사유·조치자를 함께 남긴다(DOMAIN.md 6.7). */
    @Test
    void blockPost_publishedPost_recordsWhoBlockedItAndWhy() {
        long postId = insertPost("차단 대상", PostStatus.PUBLISHED, BASE_TIME);
        long adminId = insertReporter("admin");

        assertThat(communityAdminMapper.blockPost(postId, "광고성 게시물", adminId)).isEqualTo(1);

        PostDetailView post = communityMapper.findPostById(postId);
        assertThat(post.status()).isEqualTo(PostStatus.BLOCKED);
        assertThat(post.blockedReason()).isEqualTo("광고성 게시물");
        assertThat(blockedBy(postId)).isEqualTo(adminId);
    }

    /**
     * 차단이 게시글을 "수정됨"으로 만들지 않는지 확인한다.
     *
     * <p>조회수·좋아요와 같은 자리다. updated_at 보존이 빠지면 차단된 글마다 작성자에게
     * "(수정됨)"이 붙는데, 작성자는 고친 적이 없어서 원인을 찾을 수 없다(DOMAIN.md 6.3).
     */
    @Test
    void blockPost_doesNotMarkPostAsEdited() {
        long postId = insertPost("차단 대상", PostStatus.PUBLISHED, BASE_TIME);
        long adminId = insertReporter("edit-admin");

        communityAdminMapper.blockPost(postId, "사유", adminId);

        assertThat(communityMapper.findPostById(postId).isEdited()).isFalse();
    }

    /** 이미 차단된 글에 다시 걸면 0행이다. 원래 조치 기록이 덮이지 않는다(DOMAIN.md 4.2). */
    @Test
    void blockPost_alreadyBlockedPost_changesNothing() {
        long postId = insertPost("이미 차단", PostStatus.PUBLISHED, BASE_TIME);
        long firstAdminId = insertReporter("first-admin");
        long secondAdminId = insertReporter("second-admin");

        communityAdminMapper.blockPost(postId, "첫 번째 사유", firstAdminId);

        assertThat(communityAdminMapper.blockPost(postId, "두 번째 사유", secondAdminId)).isZero();
        assertThat(communityMapper.findPostById(postId).blockedReason())
                .isEqualTo("첫 번째 사유");
        assertThat(blockedBy(postId)).isEqualTo(firstAdminId);
    }

    /** 작성자가 지운 글은 차단할 수 없다. 차단되면 지운 글이 되살아난다(DOMAIN.md 4.2). */
    @Test
    void blockPost_deletedPost_changesNothing() {
        long postId = insertPost("지워진 글", PostStatus.DELETED, BASE_TIME);
        long adminId = insertReporter("deleted-admin");

        assertThat(communityAdminMapper.blockPost(postId, "사유", adminId)).isZero();
        assertThat(communityMapper.findPostById(postId).status()).isEqualTo(PostStatus.DELETED);
    }

    /** 해제는 상태만 되돌리고 차단 기록은 남긴다(DOMAIN.md 4.2). */
    @Test
    void unblockPost_keepsBlockRecord() {
        long postId = insertPost("해제 대상", PostStatus.PUBLISHED, BASE_TIME);
        long adminId = insertReporter("unblock-admin");

        communityAdminMapper.blockPost(postId, "광고성 게시물", adminId);

        assertThat(communityAdminMapper.unblockPost(postId)).isEqualTo(1);

        PostDetailView post = communityMapper.findPostById(postId);
        assertThat(post.status()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(post.blockedReason()).isEqualTo("광고성 게시물");
        assertThat(blockedBy(postId)).isEqualTo(adminId);
    }

    /** 차단된 적 없는 글에는 해제할 것이 없다. 지워진 글이 되살아나지도 않는다. */
    @Test
    void unblockPost_nonBlockedPost_changesNothing() {
        long publishedId = insertPost("노출 중", PostStatus.PUBLISHED, BASE_TIME);
        long deletedId = insertPost("지워진 글", PostStatus.DELETED, BASE_TIME);

        assertThat(communityAdminMapper.unblockPost(publishedId)).isZero();
        assertThat(communityAdminMapper.unblockPost(deletedId)).isZero();
        assertThat(communityMapper.findPostById(deletedId).status())
                .isEqualTo(PostStatus.DELETED);
    }

    /** 신고를 닫을 때 이미 닫힌 신고는 건드리지 않는다(DOMAIN.md 6.6). */
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
                .extracting(ReportView::status)
                .containsExactlyInAnyOrder(ReportStatus.REJECTED, ReportStatus.RESOLVED);
    }

    /** 관리자 목록은 상태로 거르지 않는 것이 기본이다(DOMAIN.md 4.3). */
    @Test
    void findPostsForAdmin_withoutFilter_includesEveryStatus() {
        insertPost("노출", PostStatus.PUBLISHED, BASE_TIME);
        insertPost("차단", PostStatus.BLOCKED, BASE_TIME);
        insertPost("삭제", PostStatus.DELETED, BASE_TIME);

        assertThat(adminPosts(null, AdminPostSort.LATEST))
                .extracting(AdminPostListView::title)
                .contains("노출", "차단", "삭제");
    }

    @Test
    void findPostsForAdmin_withStatusFilter_returnsOnlyThatStatus() {
        insertPost("노출", PostStatus.PUBLISHED, BASE_TIME);
        insertPost("차단", PostStatus.BLOCKED, BASE_TIME);

        assertThat(adminPosts(PostStatus.BLOCKED, AdminPostSort.LATEST))
                .extracting(AdminPostListView::title)
                .containsExactly("차단");
    }

    /**
     * 신고 많은 순 정렬이 <b>미처리</b> 신고만 세는지 확인한다.
     *
     * <p>처리된 신고까지 세면 이미 조치한 글이 목록 맨 위에 영원히 남아, 진짜 처리할 글을
     * 가린다. 신고 수가 같아 보여서 화면으로는 구분되지 않는다.
     */
    @Test
    void findPostsForAdmin_sortedByReports_countsOnlyPendingOnes() {
        long pendingPostId = insertPost("미처리 신고 1건", PostStatus.PUBLISHED, BASE_TIME);
        long closedPostId = insertPost("처리된 신고 2건", PostStatus.PUBLISHED, BASE_TIME);

        communityMapper.insertReport(pendingPostId, insertReporter("p1"), "광고입니다");
        communityMapper.insertReport(closedPostId, insertReporter("c1"), "광고입니다");
        communityMapper.insertReport(closedPostId, insertReporter("c2"), "욕설입니다");
        communityAdminMapper.closePendingReports(closedPostId, ReportStatus.RESOLVED);

        List<AdminPostListView> posts = adminPosts(null, AdminPostSort.REPORTS);

        assertThat(posts).first()
                .extracting(AdminPostListView::title)
                .isEqualTo("미처리 신고 1건");
        assertThat(posts).filteredOn(post -> post.id() == closedPostId)
                .first()
                .extracting(AdminPostListView::pendingReportCount)
                .isEqualTo(0L);
    }

    /** 관리자 상세는 차단 기록까지 함께 읽는다. 차단된 적 없어도 행이 사라지지 않는다. */
    @Test
    void findPostByIdForAdmin_readsBlockRecordAndSurvivesWithoutIt() {
        long neverBlockedId = insertPost("차단된 적 없음", PostStatus.PUBLISHED, BASE_TIME);
        long blockedId = insertPost("차단됨", PostStatus.PUBLISHED, BASE_TIME);
        long adminId = insertReporter("detail-admin");

        communityAdminMapper.blockPost(blockedId, "광고성 게시물", adminId);

        AdminPostDetailView neverBlocked = communityAdminMapper.findPostByIdForAdmin(neverBlockedId);
        assertThat(neverBlocked).isNotNull();
        assertThat(neverBlocked.hasBlockRecord()).isFalse();
        assertThat(neverBlocked.blockedByNickname()).isNull();

        AdminPostDetailView blocked = communityAdminMapper.findPostByIdForAdmin(blockedId);
        assertThat(blocked.hasBlockRecord()).isTrue();
        assertThat(blocked.blockedReason()).isEqualTo("광고성 게시물");
        assertThat(blocked.blockedByNickname()).isNotNull();
    }

    /** 이 테스트 카테고리의 글만 본다. 다른 테스트가 남긴 글과 섞이지 않게 한다. */
    private List<AdminPostListView> adminPosts(PostStatus status, AdminPostSort sort) {
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
