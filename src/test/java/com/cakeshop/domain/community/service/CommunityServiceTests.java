package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

import com.cakeshop.domain.community.dto.form.CommentForm;
import com.cakeshop.domain.community.dto.form.PostForm;
import com.cakeshop.domain.community.dto.form.ReportForm;
import com.cakeshop.domain.community.dto.view.CommentCountView;
import com.cakeshop.domain.community.dto.view.CommentSectionView;
import com.cakeshop.domain.community.dto.view.CommentRow;
import com.cakeshop.domain.community.dto.view.CommentView;
import com.cakeshop.domain.community.dto.view.PopularPostView;
import com.cakeshop.domain.community.dto.view.PopularSectionView;
import com.cakeshop.domain.community.dto.view.PostDetailRow;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListRow;
import com.cakeshop.domain.community.dto.view.PostListView;
import com.cakeshop.domain.community.dto.view.PostLockView;
import com.cakeshop.domain.community.dto.view.PostSort;
import com.cakeshop.domain.community.entity.Comment;
import com.cakeshop.domain.community.entity.CommentStatus;
import com.cakeshop.domain.community.entity.Post;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.domain.member.dto.view.MemberCommunityView;
import com.cakeshop.domain.member.service.MemberCommunityQueryService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;

/** 커뮤니티 Service의 권한과 상태 계약을 확인한다. */
class CommunityServiceTests {

    private static final long POST_ID = 42L;
    private static final long COMMENT_ID = 314L;
    private static final long AUTHOR_ID = 7L;
    private static final long OTHER_MEMBER_ID = 99L;
    private static final long CATEGORY_ID = 1L;
    /** 요청 대상이 아닌 다른 글. */
    private static final long OTHER_POST_ID = 999L;
    /** 회원 조회자 키. */
    private static final String VIEWER_KEY = "M:7";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 3, 1, 10, 0);

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** 인기글 경고를 확인할 기준 시각. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 10, 0);

    /** 경고가 발생하지 않는 최신 확정일. */
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 3, 9);

    private static final PageRequest FIRST_PAGE = new PageRequest(1, 20);

    private CommunityMapper communityMapper;
    private MemberCommunityQueryService memberCommunityQueryService;
    private CommunityService communityService;

    @BeforeEach
    void setUp() {
        communityMapper = mock(CommunityMapper.class);
        memberCommunityQueryService = mock(MemberCommunityQueryService.class);
        when(memberCommunityQueryService.getMembersByIds(anyList())).thenReturn(List.of());
        communityService = new CommunityService(
                communityMapper, memberCommunityQueryService, fixedClockAt(NOW));
    }

    /** 서울 기준 고정 시계를 만든다. */
    private static Clock fixedClockAt(LocalDateTime now) {
        return Clock.fixed(now.atZone(SEOUL).toInstant(), SEOUL);
    }

    /** 상태 표의 "작성자" 열을 실제 회원 ID로 바꾼다. */
    private static long memberIdOf(boolean asAuthor) {
        return asAuthor ? AUTHOR_ID : OTHER_MEMBER_ID;
    }

    @Test
    void getPostDetail_publishedPost_anonymousViewer_returnsPost() {
        givenPost(PostStatus.PUBLISHED);

        PostDetailView post = communityService.getPostDetail(POST_ID, null, VIEWER_KEY);

        assertThat(post.id()).isEqualTo(POST_ID);
        assertThat(post.title()).isEqualTo("제목");
        assertThat(post.content()).isEqualTo("본문");
        assertThat(post.viewCount()).isEqualTo(10L);
        assertThat(post.isBlocked()).isFalse();
        assertThat(post.blockedReason()).isNull();
    }

    @Test
    void getPostDetail_countedView_increasesViewCountAndRecordsHistory() {
        givenPost(PostStatus.PUBLISHED);
        givenViewRecorded(true);

        communityService.getPostDetail(POST_ID, null, VIEWER_KEY);

        verify(communityMapper).increaseViewCount(POST_ID, VIEWER_KEY);
        // 조회 이력과 조회수를 함께 반영한다.
        verify(communityMapper).recordView(POST_ID, VIEWER_KEY);
    }

    /** 창 안의 중복 조회는 이력을 남기지 않는다. */
    @Test
    void getPostDetail_viewWithinWindow_doesNotRecordHistoryAgain() {
        givenPost(PostStatus.PUBLISHED);
        givenViewRecorded(false);

        communityService.getPostDetail(POST_ID, null, VIEWER_KEY);

        verify(communityMapper, never()).recordView(anyLong(), any());
    }

    @Test
    void getPostDetail_deletedPost_author_isNotFound() {
        givenPost(PostStatus.DELETED);

        // 삭제 글은 작성자에게도 숨긴다.
        assertThatThrownBy(() -> communityService.getPostDetail(POST_ID, AUTHOR_ID, VIEWER_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);
    }

    @Test
    void getPostDetail_blockedPost_author_returnsPostWithBlockedReason() {
        givenPost(PostStatus.BLOCKED);

        PostDetailView post = communityService.getPostDetail(POST_ID, AUTHOR_ID, VIEWER_KEY);

        // 작성자에게는 차단 사유를 보여 준다.
        assertThat(post.isBlocked()).isTrue();
        assertThat(post.blockedReason()).isEqualTo("광고성 게시물");
    }

    @Test
    void getPostDetail_blockedPost_otherMember_isNotFound() {
        givenPost(PostStatus.BLOCKED);

        assertThatThrownBy(() -> communityService.getPostDetail(POST_ID, OTHER_MEMBER_ID, VIEWER_KEY))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);
                    assertThat(exception.getErrorCode().status()).isEqualTo(404);
                });
    }

    @Test
    void getPostDetail_unknownPost_isNotFound() {
        when(communityMapper.findPostById(POST_ID)).thenReturn(null);

        assertThatThrownBy(() -> communityService.getPostDetail(POST_ID, AUTHOR_ID, VIEWER_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);
    }

    @Test
    void getPosts_passesSortPageSizeAndOffsetToMapper() {
        PostListRow post = new PostListRow(
                1L, AUTHOR_ID, "질문", "제목", 0, 0, 0, CREATED_AT);

        when(communityMapper.findPublishedPosts(3L, PostSort.VIEWS, 20, 40))
                .thenReturn(List.of(post));
        when(communityMapper.countPublishedPosts(3L)).thenReturn(45L);
        givenAuthors(new MemberCommunityView(AUTHOR_ID, "글쓴이", false));

        PageResult<PostListView> result =
                communityService.getPosts(3L, PostSort.VIEWS, new PageRequest(3, 20));

        assertThat(result.getContent())
                .containsExactly(PostListView.of(post, new MemberCommunityView(AUTHOR_ID, "글쓴이", false)));
        assertThat(result.getTotalElements()).isEqualTo(45L);
        assertThat(result.getTotalPages()).isEqualTo(3);
    }

    /** 회원 정보가 없으면 작성자만 가린다. */
    @Test
    void getPosts_missingAuthor_keepsPostAndMasksAuthor() {
        PostListRow post = new PostListRow(
                1L, AUTHOR_ID, "질문", "제목", 0, 0, 0, CREATED_AT);

        when(communityMapper.findPublishedPosts(null, PostSort.LATEST, 20, 0))
                .thenReturn(List.of(post));
        when(communityMapper.countPublishedPosts(null)).thenReturn(1L);
        givenAuthors();

        PageResult<PostListView> result =
                communityService.getPosts(null, PostSort.LATEST, FIRST_PAGE);

        assertThat(result.getContent()).singleElement()
                .satisfies(view -> assertThat(view.authorName()).isEqualTo("탈퇴한 회원"));
    }

    /** 댓글 작성자를 회원 계약으로 채운다. */
    @Test
    void getComments_fillsAuthorFromMemberContract() {
        givenComments(commentOf(1L, CommentStatus.PUBLISHED));
        givenAuthors(new MemberCommunityView(AUTHOR_ID, "글쓴이", false));

        CommentSectionView section = communityService.getComments(POST_ID, null);

        assertThat(section.comments()).singleElement()
                .satisfies(comment -> assertThat(comment.authorName()).isEqualTo("글쓴이"));
    }

    /** 탈퇴 회원의 댓글 작성자명을 가린다. */
    @Test
    void getComments_withdrawnAuthor_showsPlaceholderName() {
        givenComments(commentOf(1L, CommentStatus.PUBLISHED));
        givenAuthors(new MemberCommunityView(AUTHOR_ID, "글쓴이", true));

        CommentSectionView section = communityService.getComments(POST_ID, null);

        assertThat(section.comments()).singleElement()
                .satisfies(comment -> assertThat(comment.authorName()).isEqualTo("탈퇴한 회원"));
    }

    private void givenAuthors(MemberCommunityView... authors) {
        when(memberCommunityQueryService.getMembersByIds(anyList()))
                .thenReturn(List.of(authors));
    }

    @Test
    void getPopularSection_firstPageWithoutFilter_returnsLatestConfirmedRanking() {
        givenConfirmedRanking(YESTERDAY, popular(1, 11L), popular(2, 22L));

        PopularSectionView section = communityService.getPopularSection(null, FIRST_PAGE);

        assertThat(section.isEmpty()).isFalse();
        assertThat(section.rankingDate()).isEqualTo(YESTERDAY);
        assertThat(section.posts()).extracting(PopularPostView::postId).containsExactly(11L, 22L);
        // 인기글은 10건만 조회한다.
        verify(communityMapper).findPopularPosts(YESTERDAY, 10);
    }

    /** 인기글은 필터 없는 목록의 첫 페이지에만 노출한다. */
    @ParameterizedTest(name = "categoryId={0}, page={1}이면 빈 영역")
    @CsvSource(value = {
            "1,    1",
            "NONE, 2"
    }, nullValues = "NONE")
    void getPopularSection_filteredOrLaterPage_returnsEmptySection(Long categoryId, int page) {
        PopularSectionView section =
                communityService.getPopularSection(categoryId, new PageRequest(page, 20));

        assertThat(section.isEmpty()).isTrue();
        assertThat(section.rankingDate()).isNull();
        verify(communityMapper, never()).findLatestRankingDate();
        verify(communityMapper, never()).findPopularPosts(any(), anyInt());
    }

    /** 확정일이 없으면 인기글을 조회하지 않는다. */
    @Test
    void getPopularSection_noConfirmedRun_returnsEmptyWithoutQueryingPosts() {
        when(communityMapper.findLatestRankingDate()).thenReturn(null);

        PopularSectionView section = communityService.getPopularSection(null, FIRST_PAGE);

        assertThat(section.isEmpty()).isTrue();
        assertThat(section.rankingDate()).isNull();
        verify(communityMapper, never()).findPopularPosts(any(), anyInt());
    }

    /** 노출할 인기글이 없으면 빈 영역을 반환한다. */
    @Test
    void getPopularSection_everyRankedPostHidden_returnsEmptySection() {
        givenConfirmedRanking(YESTERDAY);

        PopularSectionView section = communityService.getPopularSection(null, FIRST_PAGE);

        assertThat(section.isEmpty()).isTrue();
        assertThat(section.rankingDate()).isNull();
    }

    /**
     * 확정일이 어제보다 오래됐을 때만 경고한다. 배치가 도는 새벽에는 아직 낡은 것이 정상이므로
     * 유예 시간 안에서는 경고하지 않고, 확정 이력 자체가 없는 첫 배포 직후도 고장이 아니다.
     */
    @ParameterizedTest(name = "확정일={0}, 현재={1}이면 경고={2}")
    @CsvSource(value = {
            "2026-03-08, 2026-03-10T10:00, true",
            "2026-03-09, 2026-03-10T10:00, false",
            "2026-03-08, 2026-03-10T00:30, false",
            "NONE,       2026-03-10T10:00, false"
    }, nullValues = "NONE")
    void getPopularSection_staleRanking_warnsOnlyAfterGrace(
            LocalDate rankingDate, LocalDateTime now, boolean expectWarning) {
        CommunityService serviceAt = new CommunityService(
                communityMapper, memberCommunityQueryService, fixedClockAt(now));
        givenConfirmedRanking(rankingDate, popular(1, 11L));

        List<String> warnings = warningsWhile(() -> serviceAt.getPopularSection(null, FIRST_PAGE));

        assertThat(warnings).hasSize(expectWarning ? 1 : 0);
        assertThat(warnings).allSatisfy(
                warning -> assertThat(warning).contains(String.valueOf(rankingDate)));
    }

    @Test
    void createPost_savesFormValuesWithAuthenticatedAuthor() {
        givenActiveCategory();
        // 목에 생성 키 반영을 설정한다.
        givenGeneratedPostId();

        long createdId = communityService.createPost(formOf(CATEGORY_ID, "제목", "본문"), AUTHOR_ID);

        assertThat(createdId).isEqualTo(POST_ID);

        Post saved = capturedInsert();
        // 작성자는 인증 정보에서 가져온다.
        assertThat(saved.getMemberId()).isEqualTo(AUTHOR_ID);
        assertThat(saved.getCategoryId()).isEqualTo(CATEGORY_ID);
        assertThat(saved.getTitle()).isEqualTo("제목");
        assertThat(saved.getContent()).isEqualTo("본문");
    }

    /** 비활성 카테고리에는 글을 작성할 수 없다. */
    @Test
    void createPost_inactiveCategory_isRejected() {
        when(communityMapper.existsActiveCategory(CATEGORY_ID)).thenReturn(false);

        assertThatThrownBy(
                () -> communityService.createPost(formOf(CATEGORY_ID, "제목", "본문"), AUTHOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.CATEGORY_NOT_FOUND);

        verify(communityMapper, never()).insertPost(any());
    }

    /** 게시글과 댓글 입력값은 같은 규칙으로 정리한다. */
    @Test
    void form_trimsOuterSpacesAndPreservesInnerLineBreaks() {
        PostForm post = formOf(CATEGORY_ID, "   ", "  첫 줄\n둘째 줄  ");

        assertThat(post.getTitle()).isEmpty();
        assertThat(post.getContent()).isEqualTo("첫 줄\n둘째 줄");

        assertThat(commentFormOf("  첫 줄\n둘째 줄  ").getContent()).isEqualTo("첫 줄\n둘째 줄");
        assertThat(commentFormOf("   ").getContent()).isEmpty();
    }

    @Test
    void updatePost_author_updatesPost() {
        givenPost(PostStatus.PUBLISHED);
        givenActiveCategory();
        when(communityMapper.updatePost(any())).thenReturn(1);

        communityService.updatePost(POST_ID, formOf(CATEGORY_ID, "고친 제목", "고친 본문"), AUTHOR_ID);

        Post updated = capturedUpdate();
        assertThat(updated.getId()).isEqualTo(POST_ID);
        assertThat(updated.getMemberId()).isEqualTo(AUTHOR_ID);
        assertThat(updated.getTitle()).isEqualTo("고친 제목");
    }

    /** 다른 작성자의 글과 삭제 글은 찾을 수 없는 것으로, 차단 글은 작성자에게도 차단으로 처리한다. */
    @ParameterizedTest(name = "{0} 글을 작성자={1}이 수정하면 {2}")
    @CsvSource({
            "PUBLISHED, false, POST_NOT_FOUND",
            "BLOCKED,   true,  BLOCKED_POST",
            "DELETED,   true,  POST_NOT_FOUND"
    })
    void updatePost_notEditablePost_isRejected(
            PostStatus status, boolean asAuthor, CommunityErrorCode expected) {
        givenPost(status);

        assertThatThrownBy(() -> communityService.updatePost(
                POST_ID, formOf(CATEGORY_ID, "제목", "본문"), memberIdOf(asAuthor)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(expected);

        verify(communityMapper, never()).updatePost(any());
    }

    @Test
    void updatePost_inactiveCategory_isRejected() {
        givenPost(PostStatus.PUBLISHED);
        when(communityMapper.existsActiveCategory(CATEGORY_ID)).thenReturn(false);

        assertThatThrownBy(() -> communityService.updatePost(
                POST_ID, formOf(CATEGORY_ID, "제목", "본문"), AUTHOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.CATEGORY_NOT_FOUND);

        verify(communityMapper, never()).updatePost(any());
    }

    /** 수정 화면 조회는 조회수를 올리지 않는다. */
    @Test
    void getEditablePost_doesNotIncreaseViewCount() {
        givenPost(PostStatus.PUBLISHED);

        communityService.getEditablePost(POST_ID, AUTHOR_ID);

        verify(communityMapper, never()).increaseViewCount(anyLong(), any());
    }

    @Test
    void deletePost_author_softDeletesPost() {
        givenPost(PostStatus.PUBLISHED);
        when(communityMapper.deletePost(POST_ID, AUTHOR_ID)).thenReturn(1);

        communityService.deletePost(POST_ID, AUTHOR_ID);

        verify(communityMapper).deletePost(POST_ID, AUTHOR_ID);
    }

    /** 조건부 삭제가 0행이면 성공으로 처리하지 않고, 경합 시점의 상태로 실패를 구분한다. */
    @ParameterizedTest(name = "0행 삭제 후 글이 {0}이면 {1}")
    @CsvSource({
            "PUBLISHED, POST_NOT_FOUND",
            "BLOCKED,   BLOCKED_POST"
    })
    void deletePost_whenNothingWasDeleted_isRejectedByCurrentStatus(
            PostStatus statusAfterDelete, CommunityErrorCode expected) {
        givenPost(PostStatus.PUBLISHED);
        when(communityMapper.deletePost(POST_ID, AUTHOR_ID)).thenAnswer(invocation -> {
            givenPost(statusAfterDelete);
            return 0;
        });

        assertThatThrownBy(() -> communityService.deletePost(POST_ID, AUTHOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(expected);
    }

    /** 최신 댓글을 오래된 순으로 보여 준다. */
    @Test
    void getComments_returnsCommentsOldestFirst() {
        givenComments(
                commentOf(3L, CommentStatus.PUBLISHED),
                commentOf(2L, CommentStatus.PUBLISHED),
                commentOf(1L, CommentStatus.PUBLISHED));

        CommentSectionView section = communityService.getComments(POST_ID, null);

        assertThat(section.comments()).extracting(CommentView::id)
                .containsExactly(1L, 2L, 3L);
    }

    /** 요청값이 없거나 너무 작으면 기본값을, 너무 크면 상한을 사용한다. */
    @ParameterizedTest(name = "요청 limit={0}이면 조회 limit={1}")
    @MethodSource("requestedCommentLimits")
    void getComments_requestedLimit_isClampedToAllowedRange(Integer requested, int expected) {
        givenComments();

        communityService.getComments(POST_ID, requested);

        verify(communityMapper).findRecentComments(POST_ID, expected);
    }

    private static Stream<Arguments> requestedCommentLimits() {
        return Stream.of(
                arguments(null, CommentSectionView.DEFAULT_LIMIT),
                arguments(1, CommentSectionView.DEFAULT_LIMIT),
                arguments(Integer.MAX_VALUE, CommentSectionView.MAX_LIMIT));
    }

    /** 표시 댓글 수와 전체 행 수를 구분한다. */
    @Test
    void getComments_countsPlaceholdersForLoadMoreButNotForDisplayedCount() {
        when(communityMapper.findRecentComments(anyLong(), anyInt()))
                .thenReturn(List.of(commentOf(1L, CommentStatus.DELETED)));
        when(communityMapper.countComments(POST_ID)).thenReturn(new CommentCountView(5L, 3L));

        CommentSectionView section = communityService.getComments(POST_ID, null);

        assertThat(section.publishedCount()).isEqualTo(3L);
        assertThat(section.hasMore()).isTrue();
        assertThat(section.hiddenCount()).isEqualTo(4L);
    }

    @Test
    void addComment_savesContentWithAuthenticatedAuthor() {
        givenPost(PostStatus.PUBLISHED);

        communityService.addComment(POST_ID, commentFormOf("댓글 본문"), AUTHOR_ID);

        Comment saved = capturedComment();
        assertThat(saved.getPostId()).isEqualTo(POST_ID);
        // 작성자는 인증 정보에서 가져온다.
        assertThat(saved.getMemberId()).isEqualTo(AUTHOR_ID);
        assertThat(saved.getContent()).isEqualTo("댓글 본문");
    }

    /** 삭제 글과 다른 작성자의 차단 글은 숨기고, 작성자에게만 차단을 알린다. */
    @ParameterizedTest(name = "{0} 글에 작성자={1}이 댓글을 달면 {2}")
    @CsvSource({
            "DELETED, true,  POST_NOT_FOUND",
            "BLOCKED, true,  BLOCKED_POST",
            "BLOCKED, false, POST_NOT_FOUND"
    })
    void addComment_notCommentablePost_isRejected(
            PostStatus status, boolean asAuthor, CommunityErrorCode expected) {
        givenPost(status);

        assertThatThrownBy(() -> communityService.addComment(
                POST_ID, commentFormOf("댓글"), memberIdOf(asAuthor)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(expected);

        verify(communityMapper, never()).insertComment(any());
    }

    @Test
    void deleteComment_author_softDeletesComment() {
        givenPost(PostStatus.PUBLISHED);
        givenComment(AUTHOR_ID, CommentStatus.PUBLISHED);
        when(communityMapper.deleteComment(COMMENT_ID, POST_ID, AUTHOR_ID)).thenReturn(1);

        communityService.deleteComment(POST_ID, COMMENT_ID, AUTHOR_ID);

        verify(communityMapper).deleteComment(COMMENT_ID, POST_ID, AUTHOR_ID);
    }

    /** 다른 작성자의 댓글, 다른 글의 댓글과 이미 삭제된 댓글은 모두 같은 오류로 숨긴다. */
    @ParameterizedTest(name = "같은 글={0}, 본인 댓글={1}, 상태={2}")
    @CsvSource({
            "true,  false, PUBLISHED",
            "false, true,  PUBLISHED",
            "true,  true,  DELETED"
    })
    void deleteComment_notOwnDeletableComment_isNotFound(
            boolean onSamePost, boolean ownComment, CommentStatus status) {
        givenPost(PostStatus.PUBLISHED);
        when(communityMapper.findCommentById(COMMENT_ID)).thenReturn(commentOf(
                COMMENT_ID,
                onSamePost ? POST_ID : OTHER_POST_ID,
                memberIdOf(ownComment),
                status));

        assertThatThrownBy(
                () -> communityService.deleteComment(POST_ID, COMMENT_ID, AUTHOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.COMMENT_NOT_FOUND);

        verify(communityMapper, never()).deleteComment(anyLong(), anyLong(), anyLong());
    }

    /** 조건부 댓글 삭제가 0행이면 성공으로 처리하지 않고, 경합 시점의 글 상태로 실패를 구분한다. */
    @ParameterizedTest(name = "0행 삭제 후 글이 {0}이면 {1}")
    @CsvSource({
            "PUBLISHED, COMMENT_NOT_FOUND",
            "BLOCKED,   BLOCKED_POST"
    })
    void deleteComment_whenNothingWasDeleted_isRejectedByCurrentStatus(
            PostStatus statusAfterDelete, CommunityErrorCode expected) {
        givenPost(PostStatus.PUBLISHED);
        givenComment(AUTHOR_ID, CommentStatus.PUBLISHED);
        when(communityMapper.deleteComment(COMMENT_ID, POST_ID, AUTHOR_ID))
                .thenAnswer(invocation -> {
                    givenPost(statusAfterDelete);
                    return 0;
                });

        assertThatThrownBy(
                () -> communityService.deleteComment(POST_ID, COMMENT_ID, AUTHOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(expected);
    }

    private CommentForm commentFormOf(String content) {
        CommentForm form = new CommentForm();
        form.setContent(content);

        return form;
    }

    /** 좋아요는 잠금, 저장, 재계산 순서로 처리한다. */
    @Test
    void addLike_publishedPost_locksThePostBeforeTouchingLikes() {
        givenLockedPost(PostStatus.PUBLISHED);

        communityService.addLike(POST_ID, OTHER_MEMBER_ID);

        InOrder order = inOrder(communityMapper);
        order.verify(communityMapper).lockPost(POST_ID);
        order.verify(communityMapper).insertLike(POST_ID, OTHER_MEMBER_ID);
        order.verify(communityMapper).recalculateLikeCount(POST_ID);
    }

    /** 좋아요 취소도 잠금부터 처리한다. */
    @Test
    void removeLike_publishedPost_locksThePostBeforeTouchingLikes() {
        givenLockedPost(PostStatus.PUBLISHED);

        communityService.removeLike(POST_ID, OTHER_MEMBER_ID);

        InOrder order = inOrder(communityMapper);
        order.verify(communityMapper).lockPost(POST_ID);
        order.verify(communityMapper).deleteLike(POST_ID, OTHER_MEMBER_ID);
        order.verify(communityMapper).recalculateLikeCount(POST_ID);
    }

    /** 삭제·미존재 글과 다른 작성자의 차단 글은 숨기고, 작성자에게만 차단을 알린다. */
    @ParameterizedTest(name = "{0} 글에 작성자={1}이 좋아요하면 {2}")
    @CsvSource(value = {
            "DELETED, false, POST_NOT_FOUND",
            "NONE,    false, POST_NOT_FOUND",
            "BLOCKED, false, POST_NOT_FOUND",
            "BLOCKED, true,  BLOCKED_POST"
    }, nullValues = "NONE")
    void addLike_notLikeablePost_isRejected(
            PostStatus status, boolean asAuthor, CommunityErrorCode expected) {
        givenLockedPost(status);

        assertThatThrownBy(() -> communityService.addLike(POST_ID, memberIdOf(asAuthor)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);

        verify(communityMapper, never()).insertLike(anyLong(), anyLong());
        verify(communityMapper, never()).recalculateLikeCount(anyLong());
    }

    @Test
    void removeLike_deletedPost_isRejectedAndLeavesLikesUntouched() {
        givenLockedPost(PostStatus.DELETED);

        assertThatThrownBy(() -> communityService.removeLike(POST_ID, OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);

        verify(communityMapper, never()).deleteLike(anyLong(), anyLong());
        verify(communityMapper, never()).recalculateLikeCount(anyLong());
    }

    /** status가 null이면 없는 글로 둔다. */
    private void givenLockedPost(PostStatus status) {
        when(communityMapper.lockPost(POST_ID))
                .thenReturn(status == null ? null : new PostLockView(AUTHOR_ID, status));
    }

    private Comment capturedComment() {
        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(communityMapper).insertComment(captor.capture());

        return captor.getValue();
    }

    /** 조회수 반영 결과를 설정한다. */
    private void givenViewRecorded(boolean recorded) {
        when(communityMapper.increaseViewCount(POST_ID, VIEWER_KEY)).thenReturn(recorded ? 1 : 0);
    }

    private void givenComments(CommentRow... comments) {
        when(communityMapper.findRecentComments(anyLong(), anyInt()))
                .thenReturn(List.of(comments));
        when(communityMapper.countComments(POST_ID))
                .thenReturn(new CommentCountView(comments.length, comments.length));
    }

    private void givenComment(long authorId, CommentStatus status) {
        when(communityMapper.findCommentById(COMMENT_ID))
                .thenReturn(commentOf(COMMENT_ID, POST_ID, authorId, status));
    }

    private CommentRow commentOf(long commentId, CommentStatus status) {
        return commentOf(commentId, POST_ID, AUTHOR_ID, status);
    }

    private CommentRow commentOf(
            long commentId, long postId, long authorId, CommentStatus status) {
        return new CommentRow(
                commentId,
                postId,
                authorId,
                status == CommentStatus.DELETED ? null : "댓글 본문",
                status,
                CREATED_AT
        );
    }

    private PostForm formOf(Long categoryId, String title, String content) {
        PostForm form = new PostForm();
        form.setCategoryId(categoryId);
        form.setTitle(title);
        form.setContent(content);

        return form;
    }

    private void givenGeneratedPostId() {
        when(communityMapper.insertPost(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, Post.class).setId(POST_ID);
            return 1;
        });
    }

    private void givenActiveCategory() {
        when(communityMapper.existsActiveCategory(CATEGORY_ID)).thenReturn(true);
    }

    private void givenConfirmedRanking(LocalDate rankingDate, PopularPostView... posts) {
        when(communityMapper.findLatestRankingDate()).thenReturn(rankingDate);
        when(communityMapper.findPopularPosts(rankingDate, 10)).thenReturn(List.of(posts));
    }

    private static PopularPostView popular(int ranking, long postId) {
        return new PopularPostView(ranking, postId, "질문", "제목" + ranking);
    }

    /** 실행 중 발생한 CommunityService 경고를 모은다. */
    private List<String> warningsWhile(Runnable action) {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(CommunityService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            action.run();
        } finally {
            // 다음 테스트와 로그를 분리한다.
            logger.detachAppender(appender);
            appender.stop();
        }

        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private Post capturedInsert() {
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(communityMapper).insertPost(captor.capture());

        return captor.getValue();
    }

    /** 신고 사유를 정리해 저장한다. */
    @Test
    void reportPost_publishedPostOfOther_insertsReport() {
        givenPost(PostStatus.PUBLISHED);

        communityService.reportPost(POST_ID, reportFormOf("광고입니다"), OTHER_MEMBER_ID);

        verify(communityMapper).insertReport(POST_ID, OTHER_MEMBER_ID, "광고입니다");
    }

    /** 중복 신고에는 명시적인 오류를 반환한다. */
    @Test
    void reportPost_alreadyReported_isRejected() {
        givenPost(PostStatus.PUBLISHED);
        when(communityMapper.existsReport(POST_ID, OTHER_MEMBER_ID)).thenReturn(true);

        assertThatThrownBy(() ->
                communityService.reportPost(POST_ID, reportFormOf("또 신고"), OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.ALREADY_REPORTED);

        verify(communityMapper, never()).insertReport(anyLong(), anyLong(), any());
    }

    /** 중복 신고 경합도 같은 오류로 변환한다. */
    @Test
    void reportPost_duplicateKeyRace_isReportedAsAlreadyReported() {
        givenPost(PostStatus.PUBLISHED);
        when(communityMapper.insertReport(POST_ID, OTHER_MEMBER_ID, "광고입니다"))
                .thenThrow(new DuplicateKeyException("uk_post_reports_post_reporter"));

        assertThatThrownBy(() ->
                communityService.reportPost(POST_ID, reportFormOf("광고입니다"), OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.ALREADY_REPORTED);
    }

    /** 자신의 글은 신고할 수 없다. */
    @Test
    void reportPost_ownPost_isRejected() {
        givenPost(PostStatus.PUBLISHED);

        assertThatThrownBy(() ->
                communityService.reportPost(POST_ID, reportFormOf("내 글"), AUTHOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.OWN_POST_REPORT);

        verify(communityMapper, never()).insertReport(anyLong(), anyLong(), any());
    }

    /** 볼 수 없는 글은 신고할 수 없다. */
    @ParameterizedTest(name = "{0} 글을 신고하면 POST_NOT_FOUND")
    @CsvSource(value = {
            "DELETED",
            "BLOCKED",
            "NONE"
    }, nullValues = "NONE")
    void reportPost_invisiblePost_isRejectedAsNotFound(PostStatus status) {
        givenPost(status);

        assertThatThrownBy(() ->
                communityService.reportPost(POST_ID, reportFormOf("사유"), OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);

        verify(communityMapper, never()).insertReport(anyLong(), anyLong(), any());
    }

    private ReportForm reportFormOf(String reason) {
        ReportForm form = new ReportForm();
        form.setReason(reason);

        return form;
    }

    private Post capturedUpdate() {
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(communityMapper).updatePost(captor.capture());

        return captor.getValue();
    }

    /** status가 null이면 없는 글로 둔다. */
    private void givenPost(PostStatus status) {
        when(communityMapper.findPostById(POST_ID))
                .thenReturn(status == null ? null : postRowOf(status));
    }

    private PostDetailRow postRowOf(PostStatus status) {
        return new PostDetailRow(
                POST_ID,
                AUTHOR_ID,
                1L,
                "질문",
                "제목",
                "본문",
                status,
                status == PostStatus.BLOCKED ? "광고성 게시물" : null,
                10L,
                2L,
                CREATED_AT,
                CREATED_AT
        );
    }
}
