package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.community.dto.form.PostForm;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListView;
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

/**
 * 상세 접근 규칙(docs/community/DOMAIN.md 4.3 표)을 칸마다 고정한다.
 *
 * <p>이 규칙은 화면에서 확인하기 어렵다. 잘못 구현해도 대부분의 경우 정상으로 보이고,
 * 어긋나는 순간에는 남의 삭제된 글이 열리거나 작성자가 차단 사유를 못 보게 된다.
 */
class CommunityServiceTests {

    private static final long POST_ID = 42L;
    private static final long AUTHOR_ID = 7L;
    private static final long OTHER_MEMBER_ID = 99L;
    private static final long CATEGORY_ID = 1L;
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

        PostDetailView post = communityService.getPostDetail(POST_ID, null);

        assertThat(post.id()).isEqualTo(POST_ID);
    }

    @Test
    void getPostDetail_publishedPost_otherMember_returnsPost() {
        givenPost(PostStatus.PUBLISHED);

        assertThat(communityService.getPostDetail(POST_ID, OTHER_MEMBER_ID)).isNotNull();
    }

    @Test
    void getPostDetail_publishedPost_increasesViewCount() {
        givenPost(PostStatus.PUBLISHED);

        communityService.getPostDetail(POST_ID, null);

        verify(communityMapper).increaseViewCount(POST_ID);
    }

    @Test
    void getPostDetail_deletedPost_author_isNotFound() {
        givenPost(PostStatus.DELETED);

        // 작성자 본인에게도 404다. DELETED는 종착 상태이고 복구 기능이 없다(4.2, 4.3).
        assertThatThrownBy(() -> communityService.getPostDetail(POST_ID, AUTHOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);
    }

    @Test
    void getPostDetail_deletedPost_otherMember_isNotFound() {
        givenPost(PostStatus.DELETED);

        assertThatThrownBy(() -> communityService.getPostDetail(POST_ID, OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getPostDetail_blockedPost_author_returnsPostWithBlockedReason() {
        givenPost(PostStatus.BLOCKED);

        PostDetailView post = communityService.getPostDetail(POST_ID, AUTHOR_ID);

        // 작성자는 차단된 글에 아무 조치도 할 수 없다. 사유까지 가리면 이유를 알 길이 없다(4.3).
        assertThat(post.isBlocked()).isTrue();
        assertThat(post.blockedReason()).isEqualTo("광고성 게시물");
    }

    @Test
    void getPostDetail_blockedPost_otherMember_isNotFound() {
        givenPost(PostStatus.BLOCKED);

        assertThatThrownBy(() -> communityService.getPostDetail(POST_ID, OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getPostDetail_blockedPost_anonymousViewer_isNotFound() {
        givenPost(PostStatus.BLOCKED);

        assertThatThrownBy(() -> communityService.getPostDetail(POST_ID, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getPostDetail_unknownPost_isNotFound() {
        when(communityMapper.findPostById(POST_ID)).thenReturn(null);

        assertThatThrownBy(() -> communityService.getPostDetail(POST_ID, AUTHOR_ID))
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

        verify(communityMapper, never()).increaseViewCount(anyLong());
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

        verify(communityMapper, never()).increaseViewCount(anyLong());
    }

    @Test
    void deletePost_author_softDeletesPost() {
        givenPost(PostStatus.PUBLISHED);

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

    private Post capturedUpdate() {
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(communityMapper).updatePost(captor.capture());

        return captor.getValue();
    }

    private BusinessException catchBusinessException(Long viewerId) {
        try {
            communityService.getPostDetail(POST_ID, viewerId);
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
