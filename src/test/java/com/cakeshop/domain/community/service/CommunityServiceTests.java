package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    void getPostDetail_publishedPost_anonymousViewer_returnsPost() {
        givenPost(PostStatus.PUBLISHED);

        PostDetailView post = communityService.getPostDetail(POST_ID, null, VIEWER_KEY);

        assertThat(post.id()).isEqualTo(POST_ID);
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
    void getPosts_passesPageSizeAndOffsetToMapper() {
        PostListRow post = new PostListRow(
                1L, AUTHOR_ID, "질문", "제목", 0, 0, 0, CREATED_AT);

        when(communityMapper.findPublishedPosts(3L, PostSort.LATEST, 20, 40))
                .thenReturn(List.of(post));
        when(communityMapper.countPublishedPosts(3L)).thenReturn(45L);
        givenAuthors(new MemberCommunityView(AUTHOR_ID, "글쓴이", false));

        PageResult<PostListView> result =
                communityService.getPosts(3L, PostSort.LATEST, new PageRequest(3, 20));

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
    void getPosts_doesNotTouchViewCount() {
        when(communityMapper.findPublishedPosts(null, PostSort.LATEST, 20, 0))
                .thenReturn(List.of());
        when(communityMapper.countPublishedPosts(null)).thenReturn(0L);

        communityService.getPosts(null, PostSort.LATEST, new PageRequest(1, 20));

        verify(communityMapper, never()).increaseViewCount(anyLong(), any());
    }

    /** 정렬 기준을 Mapper에 그대로 전달한다. */
    @Test
    void getPosts_passesSortToMapperUnchanged() {
        when(communityMapper.findPublishedPosts(null, PostSort.VIEWS, 20, 0))
                .thenReturn(List.of());
        when(communityMapper.countPublishedPosts(null)).thenReturn(0L);

        communityService.getPosts(null, PostSort.VIEWS, new PageRequest(1, 20));

        verify(communityMapper).findPublishedPosts(null, PostSort.VIEWS, 20, 0);
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

    /** 카테고리 목록에서는 인기글을 조회하지 않는다. */
    @Test
    void getPopularSection_categoryFiltered_doesNotQueryAtAll() {
        PopularSectionView section = communityService.getPopularSection(CATEGORY_ID, FIRST_PAGE);

        assertThat(section.isEmpty()).isTrue();
        verify(communityMapper, never()).findLatestRankingDate();
        verify(communityMapper, never()).findPopularPosts(any(), anyInt());
    }

    @Test
    void getPopularSection_secondPage_doesNotQueryAtAll() {
        PopularSectionView section =
                communityService.getPopularSection(null, new PageRequest(2, 20));

        assertThat(section.isEmpty()).isTrue();
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

    /** 확정일이 오래되면 경고한다. */
    @Test
    void getPopularSection_rankingOlderThanYesterday_warns() {
        givenConfirmedRanking(YESTERDAY.minusDays(1), popular(1, 11L));

        List<String> warnings = warningsWhile(
                () -> communityService.getPopularSection(null, FIRST_PAGE));

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0)).contains("2026-03-08");
    }

    @Test
    void getPopularSection_yesterdayRanking_doesNotWarn() {
        givenConfirmedRanking(YESTERDAY, popular(1, 11L));

        assertThat(warningsWhile(() -> communityService.getPopularSection(null, FIRST_PAGE)))
                .isEmpty();
    }

    /** 새벽 유예 시간에는 경고하지 않는다. */
    @Test
    void getPopularSection_beforeGraceEnds_doesNotWarnEvenIfStale() {
        CommunityService atDawn = new CommunityService(
                communityMapper,
                memberCommunityQueryService,
                fixedClockAt(LocalDateTime.of(2026, 3, 10, 0, 30)));
        givenConfirmedRanking(YESTERDAY.minusDays(1), popular(1, 11L));

        assertThat(warningsWhile(() -> atDawn.getPopularSection(null, FIRST_PAGE))).isEmpty();
    }

    /** 확정 이력이 없으면 경고하지 않는다. */
    @Test
    void getPopularSection_noConfirmedRun_doesNotWarn() {
        when(communityMapper.findLatestRankingDate()).thenReturn(null);

        assertThat(warningsWhile(() -> communityService.getPopularSection(null, FIRST_PAGE)))
                .isEmpty();
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

    /** 게시글 입력값을 정리한다. */
    @Test
    void postForm_trimsTitleAndPreservesInnerLineBreaks() {
        PostForm form = formOf(CATEGORY_ID, "   ", "  첫 줄\n둘째 줄  ");

        assertThat(form.getTitle()).isEmpty();
        assertThat(form.getContent()).isEqualTo("첫 줄\n둘째 줄");
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

    /** 다른 작성자의 글은 찾을 수 없는 것으로 처리한다. */
    @Test
    void updatePost_otherMember_isNotFound() {
        givenPost(PostStatus.PUBLISHED);

        assertThatThrownBy(() -> communityService.updatePost(
                POST_ID, formOf(CATEGORY_ID, "제목", "본문"), OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);

        verify(communityMapper, never()).updatePost(any());
    }

    /** 작성자도 차단된 글을 수정할 수 없다. */
    @Test
    void updatePost_blockedPost_author_isRejectedAsBlocked() {
        givenPost(PostStatus.BLOCKED);

        assertThatThrownBy(() -> communityService.updatePost(
                POST_ID, formOf(CATEGORY_ID, "제목", "본문"), AUTHOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.BLOCKED_POST);

        verify(communityMapper, never()).updatePost(any());
    }

    @Test
    void updatePost_deletedPost_author_isNotFound() {
        givenPost(PostStatus.DELETED);

        assertThatThrownBy(() -> communityService.updatePost(
                POST_ID, formOf(CATEGORY_ID, "제목", "본문"), AUTHOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);
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

    /** 조건부 삭제가 0행이면 성공으로 처리하지 않는다. */
    @Test
    void deletePost_whenNothingWasDeleted_doesNotReportSuccess() {
        givenPost(PostStatus.PUBLISHED);
        // 조건부 삭제가 0행인 경합 상황이다.
        when(communityMapper.deletePost(POST_ID, AUTHOR_ID)).thenReturn(0);

        assertThatThrownBy(() -> communityService.deletePost(POST_ID, AUTHOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);
    }

    /** 삭제 경합 중 차단되면 차단 오류를 반환한다. */
    @Test
    void deletePost_whenPostBecameBlocked_isRejectedAsBlocked() {
        givenPost(PostStatus.PUBLISHED);
        when(communityMapper.deletePost(POST_ID, AUTHOR_ID)).thenAnswer(invocation -> {
            givenPost(PostStatus.BLOCKED);
            return 0;
        });

        assertThatThrownBy(() -> communityService.deletePost(POST_ID, AUTHOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.BLOCKED_POST);
    }

    /** 상세 재조회는 조회수를 올리지 않는다. */
    @Test
    void getVisiblePost_doesNotIncreaseViewCount() {
        givenPost(PostStatus.PUBLISHED);

        communityService.getVisiblePost(POST_ID, AUTHOR_ID);

        verify(communityMapper, never()).increaseViewCount(anyLong(), any());
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

    @Test
    void getComments_withoutRequestedLimit_usesDefault() {
        givenComments();

        communityService.getComments(POST_ID, null);

        verify(communityMapper)
                .findRecentComments(POST_ID, CommentSectionView.DEFAULT_LIMIT);
    }

    /** 댓글 조회 수를 상한으로 제한한다. */
    @Test
    void getComments_hugeRequestedLimit_isCappedAtMax() {
        givenComments();

        communityService.getComments(POST_ID, Integer.MAX_VALUE);

        verify(communityMapper).findRecentComments(POST_ID, CommentSectionView.MAX_LIMIT);
    }

    /** 작은 요청값에는 기본 조회 수를 사용한다. */
    @Test
    void getComments_tinyRequestedLimit_fallsBackToDefault() {
        givenComments();

        communityService.getComments(POST_ID, 1);

        verify(communityMapper)
                .findRecentComments(POST_ID, CommentSectionView.DEFAULT_LIMIT);
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

    /** 삭제된 글에는 댓글을 작성할 수 없다. */
    @Test
    void addComment_deletedPost_isNotFound() {
        givenPost(PostStatus.DELETED);

        assertThatThrownBy(
                () -> communityService.addComment(POST_ID, commentFormOf("댓글"), AUTHOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);

        verify(communityMapper, never()).insertComment(any());
    }

    /** 작성자도 차단된 글에 댓글을 작성할 수 없다. */
    @Test
    void addComment_blockedPost_author_isRejectedAsBlocked() {
        givenPost(PostStatus.BLOCKED);

        assertThatThrownBy(
                () -> communityService.addComment(POST_ID, commentFormOf("댓글"), AUTHOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.BLOCKED_POST);

        verify(communityMapper, never()).insertComment(any());
    }

    /** 다른 작성자의 차단 글은 숨긴다. */
    @Test
    void addComment_blockedPost_otherMember_isNotFound() {
        givenPost(PostStatus.BLOCKED);

        assertThatThrownBy(
                () -> communityService.addComment(POST_ID, commentFormOf("댓글"), OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);
    }

    @Test
    void deleteComment_author_softDeletesComment() {
        givenPost(PostStatus.PUBLISHED);
        givenComment(AUTHOR_ID, CommentStatus.PUBLISHED);
        when(communityMapper.deleteComment(COMMENT_ID, POST_ID, AUTHOR_ID)).thenReturn(1);

        communityService.deleteComment(POST_ID, COMMENT_ID, AUTHOR_ID);

        verify(communityMapper).deleteComment(COMMENT_ID, POST_ID, AUTHOR_ID);
    }

    /** 다른 작성자의 댓글은 삭제할 수 없다. */
    @Test
    void deleteComment_otherMember_isNotFound() {
        givenPost(PostStatus.PUBLISHED);
        givenComment(OTHER_MEMBER_ID, CommentStatus.PUBLISHED);

        assertThatThrownBy(
                () -> communityService.deleteComment(POST_ID, COMMENT_ID, AUTHOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.COMMENT_NOT_FOUND);

        verify(communityMapper, never()).deleteComment(anyLong(), anyLong(), anyLong());
    }

    @Test
    void deleteComment_alreadyDeletedComment_isNotFound() {
        givenPost(PostStatus.PUBLISHED);
        givenComment(AUTHOR_ID, CommentStatus.DELETED);

        assertThatThrownBy(
                () -> communityService.deleteComment(POST_ID, COMMENT_ID, AUTHOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.COMMENT_NOT_FOUND);

        verify(communityMapper, never()).deleteComment(anyLong(), anyLong(), anyLong());
    }

    /** 다른 글의 댓글은 삭제할 수 없다. */
    @Test
    void deleteComment_commentOfAnotherPost_isNotFound() {
        givenPost(PostStatus.PUBLISHED);
        when(communityMapper.findCommentById(COMMENT_ID))
                .thenReturn(commentOf(COMMENT_ID, 999L, AUTHOR_ID, CommentStatus.PUBLISHED));

        assertThatThrownBy(
                () -> communityService.deleteComment(POST_ID, COMMENT_ID, AUTHOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.COMMENT_NOT_FOUND);

        verify(communityMapper, never()).deleteComment(anyLong(), anyLong(), anyLong());
    }

    /** 조건부 댓글 삭제가 0행이면 성공으로 처리하지 않는다. */
    @Test
    void deleteComment_whenNothingWasDeleted_doesNotReportSuccess() {
        givenPost(PostStatus.PUBLISHED);
        givenComment(AUTHOR_ID, CommentStatus.PUBLISHED);
        when(communityMapper.deleteComment(COMMENT_ID, POST_ID, AUTHOR_ID)).thenReturn(0);

        assertThatThrownBy(
                () -> communityService.deleteComment(POST_ID, COMMENT_ID, AUTHOR_ID))
                .isInstanceOf(BusinessException.class);
    }

    /** 댓글 삭제 경합 중 글이 차단되면 차단 오류를 반환한다. */
    @Test
    void deleteComment_whenPostBecameBlocked_isRejectedAsBlocked() {
        givenPost(PostStatus.PUBLISHED);
        givenComment(AUTHOR_ID, CommentStatus.PUBLISHED);
        when(communityMapper.deleteComment(COMMENT_ID, POST_ID, AUTHOR_ID))
                .thenAnswer(invocation -> {
                    givenPost(PostStatus.BLOCKED);
                    return 0;
                });

        assertThatThrownBy(
                () -> communityService.deleteComment(POST_ID, COMMENT_ID, AUTHOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.BLOCKED_POST);
    }

    /** 댓글 입력값을 정리한다. */
    @Test
    void commentForm_trimsContentAndPreservesInnerLineBreaks() {
        CommentForm form = commentFormOf("  첫 줄\n둘째 줄  ");

        assertThat(form.getContent()).isEqualTo("첫 줄\n둘째 줄");
        assertThat(commentFormOf("   ").getContent()).isEmpty();
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

    /** 삭제된 글에는 좋아요를 남길 수 없다. */
    @Test
    void addLike_deletedPost_isRejectedAsNotFound() {
        givenLockedPost(PostStatus.DELETED);

        assertThatThrownBy(() -> communityService.addLike(POST_ID, OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);

        verify(communityMapper, never()).insertLike(anyLong(), anyLong());
        verify(communityMapper, never()).recalculateLikeCount(anyLong());
    }

    @Test
    void addLike_missingPost_isRejectedAsNotFound() {
        when(communityMapper.lockPost(POST_ID)).thenReturn(null);

        assertThatThrownBy(() -> communityService.addLike(POST_ID, OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);
    }

    /** 다른 작성자의 차단 글은 숨긴다. */
    @Test
    void addLike_blockedPost_otherMember_isRejectedAsNotFound() {
        givenLockedPost(PostStatus.BLOCKED);

        assertThatThrownBy(() -> communityService.addLike(POST_ID, OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);
    }

    /** 작성자의 차단 글에는 차단 오류를 반환한다. */
    @Test
    void addLike_blockedPost_author_isRejectedAsBlocked() {
        givenLockedPost(PostStatus.BLOCKED);

        assertThatThrownBy(() -> communityService.addLike(POST_ID, AUTHOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.BLOCKED_POST);
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

    /** 상세 조회는 게시글 행을 잠그지 않는다. */
    @Test
    void getPostDetail_doesNotLockThePostRow() {
        givenPost(PostStatus.PUBLISHED);

        communityService.getPostDetail(POST_ID, AUTHOR_ID, VIEWER_KEY);

        verify(communityMapper, never()).lockPost(anyLong());
    }

    private void givenLockedPost(PostStatus status) {
        when(communityMapper.lockPost(POST_ID))
                .thenReturn(new PostLockView(AUTHOR_ID, status));
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

    /** 삭제된 글은 신고할 수 없다. */
    @Test
    void reportPost_deletedPost_isRejectedAsNotFound() {
        givenPost(PostStatus.DELETED);

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

    private void givenPost(PostStatus status) {
        when(communityMapper.findPostById(POST_ID)).thenReturn(new PostDetailRow(
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
        ));
    }
}
