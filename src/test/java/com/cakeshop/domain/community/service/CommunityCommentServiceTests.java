package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

import com.cakeshop.domain.community.dto.form.CommentForm;
import com.cakeshop.domain.community.dto.query.CommentCountRow;
import com.cakeshop.domain.community.dto.query.CommentRow;
import com.cakeshop.domain.community.dto.query.PostDetailRow;
import com.cakeshop.domain.community.dto.view.CommentSectionView;
import com.cakeshop.domain.community.dto.view.CommentView;
import com.cakeshop.domain.community.entity.Comment;
import com.cakeshop.domain.community.entity.CommentStatus;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.domain.community.mapper.CommunityPopularPostMapper;
import com.cakeshop.domain.member.dto.view.MemberCommunityView;
import com.cakeshop.domain.member.service.MemberCommunityQueryService;
import com.cakeshop.global.error.BusinessException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

/** 커뮤니티 댓글 Service의 권한과 상태 계약을 확인한다. */
class CommunityCommentServiceTests {

    private static final long POST_ID = 42L;
    private static final long COMMENT_ID = 314L;
    private static final long AUTHOR_ID = 7L;
    private static final long OTHER_MEMBER_ID = 99L;
    /** 요청 대상이 아닌 다른 글. */
    private static final long OTHER_POST_ID = 999L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 3, 1, 10, 0);

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private CommunityMapper communityMapper;
    private MemberCommunityQueryService memberCommunityQueryService;
    private CommunityCommentService communityCommentService;

    @BeforeEach
    void setUp() {
        communityMapper = mock(CommunityMapper.class);
        memberCommunityQueryService = mock(MemberCommunityQueryService.class);
        when(memberCommunityQueryService.getMembersByIds(anyList())).thenReturn(List.of());

        CommunityMemberViewLoader memberViewLoader =
                new CommunityMemberViewLoader(memberCommunityQueryService);

        communityCommentService = new CommunityCommentService(
                communityMapper,
                memberViewLoader,
                postService(memberViewLoader));
    }

    /*
     * 게시글 Service를 목으로 갈지 않는다. 댓글을 달 수 있는 글인지 판단하는 것은 그쪽이고,
     * 목으로 두면 아래 차단 글 표가 아무것도 검증하지 않게 된다.
     */
    private CommunityService postService(CommunityMemberViewLoader memberViewLoader) {
        return new CommunityService(
                communityMapper,
                memberViewLoader,
                new CommunityPostAccessPolicy(),
                new PopularPostReader(
                        mock(CommunityPopularPostMapper.class),
                        Clock.system(SEOUL)));
    }

    /** 댓글 작성자를 회원 계약으로 채운다. */
    @Test
    void getComments_fillsAuthorFromMemberContract() {
        givenComments(commentOf(1L, CommentStatus.PUBLISHED));
        givenAuthors(new MemberCommunityView(AUTHOR_ID, "글쓴이", false));

        CommentSectionView section = communityCommentService.getComments(POST_ID, null);

        assertThat(section.comments()).singleElement()
                .satisfies(comment -> assertThat(comment.authorName()).isEqualTo("글쓴이"));
    }

    /** 탈퇴 회원의 댓글 작성자명을 가린다. */
    @Test
    void getComments_withdrawnAuthor_showsPlaceholderName() {
        givenComments(commentOf(1L, CommentStatus.PUBLISHED));
        givenAuthors(new MemberCommunityView(AUTHOR_ID, "글쓴이", true));

        CommentSectionView section = communityCommentService.getComments(POST_ID, null);

        assertThat(section.comments()).singleElement()
                .satisfies(comment -> assertThat(comment.authorName()).isEqualTo("탈퇴한 회원"));
    }

    /** 최신 댓글을 오래된 순으로 보여 준다. */
    @Test
    void getComments_returnsCommentsOldestFirst() {
        givenComments(
                commentOf(3L, CommentStatus.PUBLISHED),
                commentOf(2L, CommentStatus.PUBLISHED),
                commentOf(1L, CommentStatus.PUBLISHED));

        CommentSectionView section = communityCommentService.getComments(POST_ID, null);

        assertThat(section.comments()).extracting(CommentView::id)
                .containsExactly(1L, 2L, 3L);
    }

    /** 요청값이 없거나 너무 작으면 기본값을, 너무 크면 상한을 사용한다. */
    @ParameterizedTest(name = "요청 limit={0}이면 조회 limit={1}")
    @MethodSource("requestedCommentLimits")
    void getComments_requestedLimit_isClampedToAllowedRange(Integer requested, int expected) {
        givenComments();

        communityCommentService.getComments(POST_ID, requested);

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
        when(communityMapper.countComments(POST_ID)).thenReturn(new CommentCountRow(5L, 3L));

        CommentSectionView section = communityCommentService.getComments(POST_ID, null);

        assertThat(section.publishedCount()).isEqualTo(3L);
        assertThat(section.hasMore()).isTrue();
        assertThat(section.hiddenCount()).isEqualTo(4L);
    }

    @Test
    void addComment_savesContentWithAuthenticatedAuthor() {
        givenPost(PostStatus.PUBLISHED);

        communityCommentService.addComment(POST_ID, commentFormOf("댓글 본문"), AUTHOR_ID);

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

        assertThatThrownBy(() -> communityCommentService.addComment(
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

        communityCommentService.deleteComment(POST_ID, COMMENT_ID, AUTHOR_ID);

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
                () -> communityCommentService.deleteComment(POST_ID, COMMENT_ID, AUTHOR_ID))
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
                () -> communityCommentService.deleteComment(POST_ID, COMMENT_ID, AUTHOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(expected);
    }

    private void givenAuthors(MemberCommunityView... authors) {
        when(memberCommunityQueryService.getMembersByIds(anyList()))
                .thenReturn(List.of(authors));
    }

    private CommentForm commentFormOf(String content) {
        CommentForm form = new CommentForm();
        form.setContent(content);

        return form;
    }

    private Comment capturedComment() {
        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(communityMapper).insertComment(captor.capture());

        return captor.getValue();
    }

    private void givenComments(CommentRow... comments) {
        when(communityMapper.findRecentComments(anyLong(), anyInt()))
                .thenReturn(List.of(comments));
        when(communityMapper.countComments(POST_ID))
                .thenReturn(new CommentCountRow(comments.length, comments.length));
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

    /** 상태 표의 "작성자" 열을 실제 회원 ID로 바꾼다. */
    private static long memberIdOf(boolean asAuthor) {
        return asAuthor ? AUTHOR_ID : OTHER_MEMBER_ID;
    }
}
