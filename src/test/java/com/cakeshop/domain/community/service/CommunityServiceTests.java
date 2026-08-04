package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.community.dto.form.CommentForm;
import com.cakeshop.domain.community.dto.form.PostForm;
import com.cakeshop.domain.community.dto.form.ReportForm;
import com.cakeshop.domain.community.dto.view.CommentCountView;
import com.cakeshop.domain.community.dto.view.CommentSectionView;
import com.cakeshop.domain.community.dto.view.CommentView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListView;
import com.cakeshop.domain.community.dto.view.PostLockView;
import com.cakeshop.domain.community.entity.Comment;
import com.cakeshop.domain.community.entity.CommentStatus;
import com.cakeshop.domain.community.entity.Post;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;

/**
 * 상세 접근 규칙(docs/community/DOMAIN.md 4.3 표)을 칸마다 고정한다.
 *
 * <p>이 규칙은 화면에서 확인하기 어렵다. 잘못 구현해도 대부분의 경우 정상으로 보이고,
 * 어긋나는 순간에는 남의 삭제된 글이 열리거나 작성자가 차단 사유를 못 보게 된다.
 */
class CommunityServiceTests {

    private static final long POST_ID = 42L;
    private static final long COMMENT_ID = 314L;
    private static final long AUTHOR_ID = 7L;
    private static final long OTHER_MEMBER_ID = 99L;
    private static final long CATEGORY_ID = 1L;
    /** 조회수 중복 방지 키. 회원이면 'M:{memberId}', 비로그인이면 'S:{sessionId}'다. */
    private static final String VIEWER_KEY = "M:7";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 3, 1, 10, 0);

    private CommunityMapper communityMapper;
    private CommunityService communityService;

    @BeforeEach
    void setUp() {
        communityMapper = mock(CommunityMapper.class);
        communityService = new CommunityService(communityMapper);
    }

    @Test
    void getPostDetail_publishedPost_anonymousViewer_returnsPost() {
        givenPost(PostStatus.PUBLISHED);

        PostDetailView post = communityService.getPostDetail(POST_ID, null, VIEWER_KEY);

        assertThat(post.id()).isEqualTo(POST_ID);
    }

    @Test
    void getPostDetail_publishedPost_otherMember_returnsPost() {
        givenPost(PostStatus.PUBLISHED);

        assertThat(communityService.getPostDetail(POST_ID, OTHER_MEMBER_ID, VIEWER_KEY)).isNotNull();
    }

    @Test
    void getPostDetail_firstViewOfTheDay_increasesViewCountAndRecordsHistory() {
        givenPost(PostStatus.PUBLISHED);
        givenViewRecorded(true);

        communityService.getPostDetail(POST_ID, null, VIEWER_KEY);

        verify(communityMapper).increaseViewCount(POST_ID, VIEWER_KEY);
        // 이력을 빠뜨리면 숫자만 오르고 근거가 없어진다. 다음 조회도 "첫 조회"가 된다.
        verify(communityMapper).recordView(POST_ID, VIEWER_KEY);
    }

    /**
     * 이미 오늘 센 조회면 이력을 다시 남기지 않는지 확인한다.
     *
     * <p>중복 판단은 조회수 UPDATE가 스스로 한다(0행이면 이미 센 조회다). Service는 그
     * 결과를 <b>따르기만</b> 해야 한다 — 무시하고 이력을 남기면 UNIQUE 위반으로 상세
     * 화면 전체가 죽는다. 조회수를 안 올리는 것과 이력을 안 남기는 것은 같은 판단이다.
     *
     * <p>숫자가 실제로 안 오르는지는 {@code CommunityViewCountTests}가 DB로 확인한다.
     */
    @Test
    void getPostDetail_repeatedViewSameDay_doesNotRecordHistoryAgain() {
        givenPost(PostStatus.PUBLISHED);
        givenViewRecorded(false);

        communityService.getPostDetail(POST_ID, null, VIEWER_KEY);

        verify(communityMapper, never()).recordView(anyLong(), any());
    }

    @Test
    void getPostDetail_deletedPost_author_isNotFound() {
        givenPost(PostStatus.DELETED);

        // 작성자 본인에게도 404다. DELETED는 종착 상태이고 복구 기능이 없다(4.2, 4.3).
        assertThatThrownBy(() -> communityService.getPostDetail(POST_ID, AUTHOR_ID, VIEWER_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);
    }

    @Test
    void getPostDetail_deletedPost_otherMember_isNotFound() {
        givenPost(PostStatus.DELETED);

        assertThatThrownBy(() -> communityService.getPostDetail(POST_ID, OTHER_MEMBER_ID, VIEWER_KEY))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getPostDetail_blockedPost_author_returnsPostWithBlockedReason() {
        givenPost(PostStatus.BLOCKED);

        PostDetailView post = communityService.getPostDetail(POST_ID, AUTHOR_ID, VIEWER_KEY);

        // 작성자는 차단된 글에 아무 조치도 할 수 없다. 사유까지 가리면 이유를 알 길이 없다(4.3).
        assertThat(post.isBlocked()).isTrue();
        assertThat(post.blockedReason()).isEqualTo("광고성 게시물");
    }

    @Test
    void getPostDetail_blockedPost_otherMember_isNotFound() {
        givenPost(PostStatus.BLOCKED);

        assertThatThrownBy(() -> communityService.getPostDetail(POST_ID, OTHER_MEMBER_ID, VIEWER_KEY))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getPostDetail_blockedPost_anonymousViewer_isNotFound() {
        givenPost(PostStatus.BLOCKED);

        assertThatThrownBy(() -> communityService.getPostDetail(POST_ID, null, VIEWER_KEY))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getPostDetail_unknownPost_isNotFound() {
        when(communityMapper.findPostById(POST_ID)).thenReturn(null);

        assertThatThrownBy(() -> communityService.getPostDetail(POST_ID, AUTHOR_ID, VIEWER_KEY))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);
    }

    /**
     * 없는 글과 가려진 글이 같은 응답을 주는지 확인한다.
     *
     * <p>둘을 구분하면 "그 자리에 글이 있다"는 사실이 드러난다(4.3).
     */
    @Test
    void getPostDetail_hiddenPostAndUnknownPost_shareTheSameErrorCode() {
        givenPost(PostStatus.DELETED);
        BusinessException hidden = catchBusinessException(OTHER_MEMBER_ID);

        when(communityMapper.findPostById(POST_ID)).thenReturn(null);
        BusinessException unknown = catchBusinessException(OTHER_MEMBER_ID);

        assertThat(hidden.getErrorCode()).isEqualTo(unknown.getErrorCode());
        assertThat(hidden.getErrorCode().status()).isEqualTo(404);
    }

    @Test
    void getPosts_passesPageSizeAndOffsetToMapper() {
        PostListView post = new PostListView(
                1L, "질문", "제목", "글쓴이", false, 0, 0, 0, CREATED_AT);

        when(communityMapper.findPublishedPosts(3L, 20, 40)).thenReturn(List.of(post));
        when(communityMapper.countPublishedPosts(3L)).thenReturn(45L);

        PageResult<PostListView> result =
                communityService.getPosts(3L, new PageRequest(3, 20));

        assertThat(result.getContent()).containsExactly(post);
        assertThat(result.getTotalElements()).isEqualTo(45L);
        assertThat(result.getTotalPages()).isEqualTo(3);
    }

    @Test
    void getPosts_doesNotTouchViewCount() {
        when(communityMapper.findPublishedPosts(null, 20, 0)).thenReturn(List.of());
        when(communityMapper.countPublishedPosts(null)).thenReturn(0L);

        communityService.getPosts(null, new PageRequest(1, 20));

        verify(communityMapper, never()).increaseViewCount(anyLong(), any());
    }

    @Test
    void createPost_savesFormValuesWithAuthenticatedAuthor() {
        givenActiveCategory();
        // 실제 MyBatis는 생성된 키를 넣어 준다(useGeneratedKeys). 목에는 그 동작이 없다.
        givenGeneratedPostId();

        long createdId = communityService.createPost(formOf(CATEGORY_ID, "제목", "본문"), AUTHOR_ID);

        assertThat(createdId).isEqualTo(POST_ID);

        Post saved = capturedInsert();
        // 작성자는 요청이 아니라 인증 정보에서 온다(AGENTS.md).
        assertThat(saved.getMemberId()).isEqualTo(AUTHOR_ID);
        assertThat(saved.getCategoryId()).isEqualTo(CATEGORY_ID);
        assertThat(saved.getTitle()).isEqualTo("제목");
        assertThat(saved.getContent()).isEqualTo("본문");
    }

    /**
     * 화면 선택지에 없는 카테고리로는 저장되지 않는지 확인한다.
     *
     * <p>비활성 카테고리는 드롭다운에 없지만, 요청을 직접 만들면 보낼 수 있다(DOMAIN.md 6.8).
     */
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

    /**
     * 공백만 입력한 글이 저장되지 않는지 확인한다.
     *
     * <p>trim 후 검증이다(DOMAIN.md 7). 폼이 값을 다듬어 두므로 빈 문자열이 되고,
     * {@code @NotBlank}가 컨트롤러 앞단에서 걸러 여기까지 오지 않는다. 이 테스트는
     * <b>다듬는 쪽</b>을 고정한다 — 다듬지 않으면 공백이 그대로 저장된다.
     */
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

    /** 남의 글은 존재를 흘리지 않는다. 403이 아니라 404다(DOMAIN.md 4.3). */
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

    /**
     * 차단된 글은 작성자도 고칠 수 없다.
     *
     * <p>버튼을 숨기는 것으로는 부족하다. 상세는 작성자에게 이미 본문과 사유를 보여주므로
     * 주소를 알고 있고, 화면 없이 요청만 보낼 수 있다(DOMAIN.md 4.2).
     *
     * <p>여기만 404가 아니라 403인 이유: 상대는 글의 존재를 이미 아는 작성자다. 숨길 것이
     * 없고, 404를 주면 왜 막혔는지 알 수 없다.
     */
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

    /** 수정 화면을 여는 것은 조회가 아니다. 조회수가 오르면 안 된다. */
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

    @Test
    void deletePost_otherMember_isNotFound() {
        givenPost(PostStatus.PUBLISHED);

        assertThatThrownBy(() -> communityService.deletePost(POST_ID, OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);

        verify(communityMapper, never()).deletePost(anyLong(), anyLong());
    }

    /**
     * 차단된 글은 작성자도 지울 수 없다.
     *
     * <p>{@code BLOCKED -> DELETED} 금지(DOMAIN.md 4.2). 차단된 글은 신고·조치의 증거라서
     * 작성자가 지워 없앨 수 있으면 안 된다.
     */
    @Test
    void deletePost_blockedPost_author_isRejectedAsBlocked() {
        givenPost(PostStatus.BLOCKED);

        assertThatThrownBy(() -> communityService.deletePost(POST_ID, AUTHOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.BLOCKED_POST);

        verify(communityMapper, never()).deletePost(anyLong(), anyLong());
    }

    /**
     * 검증과 UPDATE 사이에 상태가 바뀌어 아무 행도 안 바뀌면 성공으로 넘기지 않는다.
     *
     * <p>SQL의 소유권·상태 조건은 바로 이 순간을 막으라고 둔 것인데, 갱신 행 수를 버리면
     * <b>조건이 걸러 낸 순간이 성공으로 보인다.</b> 관리자가 그 찰나에 글을 차단하면
     * 아무것도 안 바뀌었는데 화면은 "삭제했습니다"라고 말한다.
     */
    @Test
    void deletePost_whenNothingWasDeleted_doesNotReportSuccess() {
        givenPost(PostStatus.PUBLISHED);
        // 검증은 통과했지만 그 사이 상태가 바뀌어 조건부 DELETE가 0행을 반환한 상황이다.
        when(communityMapper.deletePost(POST_ID, AUTHOR_ID)).thenReturn(0);

        assertThatThrownBy(() -> communityService.deletePost(POST_ID, AUTHOR_ID))
                .isInstanceOf(BusinessException.class);
    }

    /** 그 사이 관리자가 차단했다면 지금 상태에 맞는 403이어야 한다. */
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

    @Test
    void updatePost_whenNothingWasUpdated_doesNotReportSuccess() {
        givenPost(PostStatus.PUBLISHED);
        givenActiveCategory();
        when(communityMapper.updatePost(any())).thenReturn(0);

        assertThatThrownBy(() -> communityService.updatePost(
                POST_ID, formOf(CATEGORY_ID, "제목", "본문"), AUTHOR_ID))
                .isInstanceOf(BusinessException.class);
    }

    /** 상세를 다시 그리는 것은 조회가 아니다. 조회수가 오르면 안 된다. */
    @Test
    void getVisiblePost_doesNotIncreaseViewCount() {
        givenPost(PostStatus.PUBLISHED);

        communityService.getVisiblePost(POST_ID, AUTHOR_ID);

        verify(communityMapper, never()).increaseViewCount(anyLong(), any());
    }

    @Test
    void getVisiblePost_deletedPost_isNotFound() {
        givenPost(PostStatus.DELETED);

        assertThatThrownBy(() -> communityService.getVisiblePost(POST_ID, AUTHOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);
    }

    /**
     * SQL이 최신순으로 잘라 온 것을 화면 순서로 뒤집는지 확인한다.
     *
     * <p>잘라 내는 쪽은 과거여야 방금 쓴 댓글이 화면에 남고, 읽는 순서는 오래된 순이어야
     * 대화가 이어진다(DOMAIN.md 6.4). 뒤집기를 빠뜨리면 댓글이 거꾸로 읽히는데,
     * 댓글이 한두 개인 개발 화면에서는 드러나지 않는다.
     */
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

    /**
     * 주소로 들어온 limit이 상한에 걸리는지 확인한다.
     *
     * <p>{@code ?comments=99999999} 하나로 한 게시글의 댓글을 전부 메모리에 올릴 수 있다.
     * 막지 않으면 화면이 느려지는 정도가 아니라 요청 하나가 서버를 세운다.
     */
    @Test
    void getComments_hugeRequestedLimit_isCappedAtMax() {
        givenComments();

        communityService.getComments(POST_ID, Integer.MAX_VALUE);

        verify(communityMapper).findRecentComments(POST_ID, CommentSectionView.MAX_LIMIT);
    }

    /** 기본값보다 작은 값으로 줄이는 방향은 허용하지 않는다. */
    @Test
    void getComments_tinyRequestedLimit_fallsBackToDefault() {
        givenComments();

        communityService.getComments(POST_ID, 1);

        verify(communityMapper)
                .findRecentComments(POST_ID, CommentSectionView.DEFAULT_LIMIT);
    }

    /**
     * 두 개수를 따로 다루는지 확인한다.
     *
     * <p>화면의 "댓글 N"에는 자리 표시가 들어가면 안 되고, "더 보기"가 남았는지 판단할
     * 때는 자리 표시도 세야 한다(DOMAIN.md 4.4).
     */
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
        // 작성자는 요청이 아니라 인증 정보에서 온다(AGENTS.md).
        assertThat(saved.getMemberId()).isEqualTo(AUTHOR_ID);
        assertThat(saved.getContent()).isEqualTo("댓글 본문");
    }

    /**
     * 삭제된 게시글에는 댓글을 달 수 없다.
     *
     * <p>게시글을 soft delete해도 댓글 행은 그대로 남는다(DOMAIN.md 4.5). 이 검증이 없으면
     * 화면 없이 요청만 보내 삭제된 글에 댓글을 달 수 있고, 그 댓글은 어디에도 보이지 않는다.
     */
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

    /** 차단된 글에는 작성자도 댓글을 달 수 없다. 상세는 보이지만 손댈 수는 없다(4.2, 4.5). */
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

    /** 남의 차단된 글은 존재 자체를 알리지 않는다. 403이 아니라 404다. */
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

    /**
     * 남의 댓글은 지울 수 없다.
     *
     * <p>게시글 작성자에게도 관리자에게도 남의 댓글을 지울 권한은 없다. 관리자의 조치는
     * 게시글 차단뿐이다(DOMAIN.md 6.7).
     */
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

    /**
     * 다른 글에 달린 댓글을 이 글의 주소로 지울 수 없는지 확인한다.
     *
     * <p>댓글 번호만 맞으면 아무 글의 주소로나 삭제 요청을 만들 수 있으면 안 된다.
     * 화면에는 그 댓글이 없으므로 눈으로는 드러나지 않는다.
     */
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

    /**
     * 검증과 UPDATE 사이에 상태가 바뀌어 아무 행도 안 바뀌면 성공으로 넘기지 않는다.
     * 게시글 삭제와 같은 이유다 — 조건이 걸러 낸 순간이 성공으로 보이면 안 된다.
     */
    @Test
    void deleteComment_whenNothingWasDeleted_doesNotReportSuccess() {
        givenPost(PostStatus.PUBLISHED);
        givenComment(AUTHOR_ID, CommentStatus.PUBLISHED);
        when(communityMapper.deleteComment(COMMENT_ID, POST_ID, AUTHOR_ID)).thenReturn(0);

        assertThatThrownBy(
                () -> communityService.deleteComment(POST_ID, COMMENT_ID, AUTHOR_ID))
                .isInstanceOf(BusinessException.class);
    }

    /** 그 사이 관리자가 글을 차단했다면 지금 상태에 맞는 403이어야 한다. */
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

    /**
     * 공백만 입력한 댓글이 저장되지 않는지 확인한다.
     *
     * <p>게시글과 같은 규칙이다(DOMAIN.md 7). 폼이 값을 다듬어 두므로 빈 문자열이 되고,
     * {@code @NotBlank}가 컨트롤러 앞단에서 걸러 Service까지 오지 않는다. 이 테스트는
     * <b>다듬는 쪽</b>을 고정한다.
     */
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

    /**
     * 좋아요가 <b>잠그고 → 넣고 → 다시 센다</b>는 순서를 지키는지 확인한다.
     *
     * <p>순서 자체가 규칙이다(DOMAIN.md 6.5). 잠금을 뒤로 미루면 post_likes INSERT가 FK
     * 확인으로 게시글 행에 공유 잠금을 걸고, 재계산이 배타 잠금을 기다리면서 같은 글에 동시에
     * 좋아요를 누른 요청끼리 교착에 빠진다. 조각 6에서 맞은 것과 같은 모양이다.
     *
     * <p>동시 요청이 없으면 세 문장의 순서가 바뀌어도 결과가 똑같아서, 실제 교착은
     * {@code CommunityLikeConcurrencyTests}가 잡는다. 여기서는 순서를 <b>의도</b>로 고정한다.
     */
    @Test
    void addLike_publishedPost_locksThePostBeforeTouchingLikes() {
        givenLockedPost(PostStatus.PUBLISHED);

        communityService.addLike(POST_ID, OTHER_MEMBER_ID);

        InOrder order = inOrder(communityMapper);
        order.verify(communityMapper).lockPost(POST_ID);
        order.verify(communityMapper).insertLike(POST_ID, OTHER_MEMBER_ID);
        order.verify(communityMapper).recalculateLikeCount(POST_ID);
    }

    /** 취소도 같은 순서로 시작해야 한다. 두 경로가 다르게 잠그면 섞였을 때 교착이다. */
    @Test
    void removeLike_publishedPost_locksThePostBeforeTouchingLikes() {
        givenLockedPost(PostStatus.PUBLISHED);

        communityService.removeLike(POST_ID, OTHER_MEMBER_ID);

        InOrder order = inOrder(communityMapper);
        order.verify(communityMapper).lockPost(POST_ID);
        order.verify(communityMapper).deleteLike(POST_ID, OTHER_MEMBER_ID);
        order.verify(communityMapper).recalculateLikeCount(POST_ID);
    }

    /**
     * 노출되지 않는 글에는 좋아요를 남길 수 없다(DOMAIN.md 4.5).
     *
     * <p>게시글을 지워도 자식 행은 그대로 남기 때문에, 이 검증이 없으면 삭제된 글에 요청만
     * 따로 보내 좋아요를 누를 수 있다.
     */
    @Test
    void addLike_deletedPost_isRejectedAsNotFound() {
        givenLockedPost(PostStatus.DELETED);

        assertThatThrownBy(() -> communityService.addLike(POST_ID, OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);
    }

    @Test
    void addLike_missingPost_isRejectedAsNotFound() {
        when(communityMapper.lockPost(POST_ID)).thenReturn(null);

        assertThatThrownBy(() -> communityService.addLike(POST_ID, OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);
    }

    /** 남의 차단된 글은 404다. 403을 주면 그 자리에 글이 있다는 사실이 드러난다(4.3). */
    @Test
    void addLike_blockedPost_otherMember_isRejectedAsNotFound() {
        givenLockedPost(PostStatus.BLOCKED);

        assertThatThrownBy(() -> communityService.addLike(POST_ID, OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);
    }

    /**
     * 자기 차단된 글은 403이다. 상세에서 이미 본문과 사유를 본 상대라 숨길 것이 없고,
     * 404를 주면 왜 막혔는지 알 수 없다. 댓글과 같은 판단이다.
     */
    @Test
    void addLike_blockedPost_author_isRejectedAsBlocked() {
        givenLockedPost(PostStatus.BLOCKED);

        assertThatThrownBy(() -> communityService.addLike(POST_ID, AUTHOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.BLOCKED_POST);
    }

    /**
     * 거절된 요청이 좋아요에 손대지 않았는지 확인한다.
     *
     * <p>예외만 확인하면 "숫자는 이미 바꿔 놓고 그 뒤에 던지는" 구현도 통과한다.
     */
    @Test
    void addLike_rejectedPost_leavesLikesUntouched() {
        givenLockedPost(PostStatus.DELETED);

        assertThatThrownBy(() -> communityService.addLike(POST_ID, OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class);

        verify(communityMapper, never()).insertLike(anyLong(), anyLong());
        verify(communityMapper, never()).recalculateLikeCount(anyLong());
    }

    @Test
    void removeLike_deletedPost_isRejectedAndLeavesLikesUntouched() {
        givenLockedPost(PostStatus.DELETED);

        assertThatThrownBy(() -> communityService.removeLike(POST_ID, OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class);

        verify(communityMapper, never()).deleteLike(anyLong(), anyLong());
        verify(communityMapper, never()).recalculateLikeCount(anyLong());
    }

    /** 상세가 잠금 조회를 쓰지 않는지 확인한다. 읽기만 하는 화면이 게시글 행을 잠그면 안 된다. */
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

    /** recorded=true면 오늘 처음 본 조회, false면 이미 오늘 센 조회다. */
    private void givenViewRecorded(boolean recorded) {
        when(communityMapper.increaseViewCount(POST_ID, VIEWER_KEY)).thenReturn(recorded ? 1 : 0);
    }

    private void givenComments(CommentView... comments) {
        when(communityMapper.findRecentComments(anyLong(), anyInt()))
                .thenReturn(List.of(comments));
        when(communityMapper.countComments(POST_ID))
                .thenReturn(new CommentCountView(comments.length, comments.length));
    }

    private void givenComment(long authorId, CommentStatus status) {
        when(communityMapper.findCommentById(COMMENT_ID))
                .thenReturn(commentOf(COMMENT_ID, POST_ID, authorId, status));
    }

    private CommentView commentOf(long commentId, CommentStatus status) {
        return commentOf(commentId, POST_ID, AUTHOR_ID, status);
    }

    private CommentView commentOf(
            long commentId, long postId, long authorId, CommentStatus status) {
        return new CommentView(
                commentId,
                postId,
                authorId,
                "글쓴이",
                false,
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

    private Post capturedInsert() {
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(communityMapper).insertPost(captor.capture());

        return captor.getValue();
    }

    /**
     * 신고가 실제로 접수되는지 확인한다. 사유는 폼에서 다듬어진 그대로 내려간다.
     */
    @Test
    void reportPost_publishedPostOfOther_insertsReport() {
        givenPost(PostStatus.PUBLISHED);

        communityService.reportPost(POST_ID, reportFormOf("광고입니다"), OTHER_MEMBER_ID);

        verify(communityMapper).insertReport(POST_ID, OTHER_MEMBER_ID, "광고입니다");
    }

    /**
     * 중복 신고는 성공이 아니라 에러다(DOMAIN.md 6.6).
     *
     * <p>좋아요와 정반대인 자리다. 조용히 성공을 돌려주면 신고자는 접수됐다고 오해하는데
     * 실제로는 아무 일도 일어나지 않는다 — 재신고는 "내 신고가 처리되지 않았다"는 표현이다.
     */
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

    /**
     * 확인과 INSERT 사이에 같은 사람의 신고가 먼저 들어온 경우도 같은 응답이어야 한다.
     *
     * <p>확인만 두면 이 경합이 500으로 나가고, 사용자에게는 신고가 접수됐는지 아닌지조차
     * 알 수 없는 화면이 된다. UNIQUE 제약이 실제로 막아 주므로 응답만 맞춰 준다.
     */
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

    /** 자기 글은 신고할 수 없다. 자기 글이 문제라면 지우면 된다(DOMAIN.md 6.6). */
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

    /**
     * 노출되지 않는 글은 신고할 수 없다(DOMAIN.md 4.5). 게시글을 지워도 신고 행은 남으므로,
     * 이 검증이 없으면 삭제된 글에 요청만 따로 보내 신고를 쌓을 수 있다.
     */
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

    /** 남의 차단된 글은 404다. 403을 주면 그 자리에 글이 있다는 사실이 드러난다(4.3). */
    @Test
    void reportPost_blockedPost_otherMember_isRejectedAsNotFound() {
        givenPost(PostStatus.BLOCKED);

        assertThatThrownBy(() ->
                communityService.reportPost(POST_ID, reportFormOf("사유"), OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);
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

    private BusinessException catchBusinessException(Long viewerId) {
        try {
            communityService.getPostDetail(POST_ID, viewerId, VIEWER_KEY);
            throw new AssertionError("BusinessException이 발생하지 않았습니다.");
        } catch (BusinessException e) {
            return e;
        }
    }

    private void givenPost(PostStatus status) {
        when(communityMapper.findPostById(POST_ID)).thenReturn(new PostDetailView(
                POST_ID,
                AUTHOR_ID,
                1L,
                "질문",
                "제목",
                "본문",
                "글쓴이",
                false,
                status,
                status == PostStatus.BLOCKED ? "광고성 게시물" : null,
                10L,
                2L,
                CREATED_AT,
                CREATED_AT
        ));
    }
}
