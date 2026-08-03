package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListView;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
