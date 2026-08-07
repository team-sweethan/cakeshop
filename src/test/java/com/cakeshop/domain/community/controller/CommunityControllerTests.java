package com.cakeshop.domain.community.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.community.dto.view.CommentSectionView;
import com.cakeshop.domain.community.dto.view.PostCategoryView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListView;
import com.cakeshop.domain.community.dto.view.PostSort;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.service.CommunityService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.security.MemberDetails;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CommunityControllerTests {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 3, 1, 10, 0);

    private CommunityService communityService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        communityService = mock(CommunityService.class);

        when(communityService.getPosts(any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(), new PageRequest(1, 20), 0));
        when(communityService.getActiveCategories())
                .thenReturn(List.of(new PostCategoryView(1L, "QNA", "질문")));
        when(communityService.getComments(anyLong(), any()))
                .thenReturn(new CommentSectionView(
                        List.of(), 0, 0, CommentSectionView.DEFAULT_LIMIT));

        mockMvc = MockMvcBuilders
                .standaloneSetup(new CommunityController(communityService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_bindsPostsAndCategories() throws Exception {
        mockMvc.perform(get("/community"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/community/list"))
                .andExpect(model().attributeExists("pageResult"))
                .andExpect(model().attributeExists("pageNavigation"))
                .andExpect(model().attributeExists("categories"))
                .andExpect(model().attribute("selectedCategoryId", (Object) null));
    }

    @Test
    void list_categoryFilter_isPassedToService() throws Exception {
        mockMvc.perform(get("/community").param("categoryId", "5"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedCategoryId", 5L));

        verify(communityService).getPosts(eq(5L), any(), any());
    }

    /** 잘못된 카테고리는 무시한다. */
    @Test
    void list_invalidCategoryId_ignoresFilterInsteadOfFailing() throws Exception {
        mockMvc.perform(get("/community").param("categoryId", "전체"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedCategoryId", (Object) null));

        verify(communityService).getPosts(isNull(), any(), any());
    }

    @Test
    void list_sortOption_isPassedToService() throws Exception {
        mockMvc.perform(get("/community").param("sort", "VIEWS"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedSort", PostSort.VIEWS));

        verify(communityService).getPosts(any(), eq(PostSort.VIEWS), any());
    }

    /** 정렬값은 대소문자를 구분하지 않는다. */
    @Test
    void list_lowerCaseSortOption_isAccepted() throws Exception {
        mockMvc.perform(get("/community").param("sort", "views"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedSort", PostSort.VIEWS));
    }

    /** 잘못된 정렬값에는 최신순을 사용한다. */
    @Test
    void list_invalidSortOption_fallsBackToLatestInsteadOfFailing() throws Exception {
        mockMvc.perform(get("/community").param("sort", "id; DROP TABLE posts"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedSort", PostSort.LATEST));

        verify(communityService).getPosts(any(), eq(PostSort.LATEST), any());
    }

    @Test
    void list_invalidPage_fallsBackToFirstPage() throws Exception {
        mockMvc.perform(get("/community").param("page", "abc"))
                .andExpect(status().isOk());

        assertThat(capturedPageRequest().getPage()).isEqualTo(1);
    }

    /** 페이지 크기는 20으로 고정한다. */
    @Test
    void list_pageSize_isFixedAndIgnoresSizeParameter() throws Exception {
        mockMvc.perform(get("/community").param("page", "2").param("size", "100"))
                .andExpect(status().isOk());

        PageRequest pageRequest = capturedPageRequest();
        assertThat(pageRequest.getSize()).isEqualTo(20);
        assertThat(pageRequest.getPage()).isEqualTo(2);
        assertThat(pageRequest.getOffset()).isEqualTo(20);
    }

    /** 큰 페이지 번호도 안전하게 처리한다. */
    @Test
    void list_hugePage_doesNotOverflowOffset() throws Exception {
        mockMvc.perform(get("/community").param("page", String.valueOf(Integer.MAX_VALUE)))
                .andExpect(status().isOk());

        assertThat(capturedPageRequest().getOffset()).isNotNegative();
    }

    @Test
    void detail_anonymousViewer_passesNullMemberId() throws Exception {
        when(communityService.getPostDetail(eq(15L), isNull(), anyString())).thenReturn(publishedPost());

        mockMvc.perform(get("/community/15"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/community/detail"))
                .andExpect(model().attributeExists("post"));

        verify(communityService).getPostDetail(eq(15L), isNull(), anyString());
    }

    /** 인증 회원 ID를 상세 조회에 전달한다. */
    @Test
    void detail_authenticatedViewer_passesAuthenticatedMemberId() throws Exception {
        MemberDetails principal = new MemberDetails(new MemberAuthenticationView(
                7L, "author@cakeshop.local", "dummy", "USER", true));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));

        when(communityService.getPostDetail(eq(15L), eq(7L), anyString())).thenReturn(publishedPost());

        mockMvc.perform(get("/community/15"))
                .andExpect(status().isOk());

        verify(communityService).getPostDetail(eq(15L), eq(7L), anyString());
    }

    /** 댓글 더 보기는 조회수를 올리지 않는다. */
    @Test
    void detail_loadingMoreComments_doesNotCountAsView() throws Exception {
        when(communityService.getVisiblePost(eq(15L), isNull())).thenReturn(publishedPost());

        mockMvc.perform(get("/community/15").param("comments", "40"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/community/detail"));

        verify(communityService).getVisiblePost(eq(15L), isNull());
        verify(communityService, never()).getPostDetail(anyLong(), any(), anyString());
    }

    /** 직접 상세 진입은 조회수를 반영한다. */
    @Test
    void detail_directEntry_stillCountsAsView() throws Exception {
        when(communityService.getPostDetail(eq(15L), isNull(), anyString()))
                .thenReturn(publishedPost());

        mockMvc.perform(get("/community/15")).andExpect(status().isOk());

        verify(communityService).getPostDetail(eq(15L), isNull(), anyString());
        verify(communityService, never()).getVisiblePost(anyLong(), any());
    }

    /** 조회자 키는 인증 정보로 만든다. */
    @Test
    void detail_viewerKey_comesFromAuthenticationNotFromRequest() throws Exception {
        authenticateAs(7L);
        when(communityService.getPostDetail(eq(15L), eq(7L), anyString()))
                .thenReturn(publishedPost());

        mockMvc.perform(get("/community/15")
                        // 요청의 조회자 키는 무시한다.
                        .param("viewerKey", "M:99"))
                .andExpect(status().isOk());

        assertThat(capturedViewerKey()).isEqualTo("M:7");
    }

    @Test
    void detail_anonymousViewer_usesSessionAsViewerKey() throws Exception {
        when(communityService.getPostDetail(eq(15L), isNull(), anyString()))
                .thenReturn(publishedPost());

        mockMvc.perform(get("/community/15"))
                .andExpect(status().isOk());

        // 비로그인은 세션 키를 사용한다.
        assertThat(capturedViewerKey()).startsWith("S:").isNotEqualTo("S:");
    }

    /** 상세 경로에는 숫자 ID만 허용한다. */
    @Test
    void detail_nonNumericPath_isNotHandledAsPostId() throws Exception {
        mockMvc.perform(get("/community/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/community/form"));
    }

    @Test
    void create_validForm_redirectsToCreatedPost() throws Exception {
        authenticateAs(7L);
        when(communityService.createPost(any(), eq(7L))).thenReturn(42L);

        mockMvc.perform(post("/community")
                        .param("categoryId", "1")
                        .param("title", "제목")
                        .param("content", "본문")
                        .param("memberId", "99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community/42"));

        verify(communityService).createPost(any(), eq(7L));
    }

    /** 검증 실패 시 작성 폼을 다시 보여 준다. */
    @Test
    void create_blankTitle_returnsFormWithCategories() throws Exception {
        authenticateAs(7L);

        mockMvc.perform(post("/community")
                        .param("categoryId", "1")
                        .param("title", "   ")
                        .param("content", "본문"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/community/form"))
                .andExpect(model().attributeHasFieldErrors("form", "title"))
                .andExpect(model().attributeExists("categories"));

        verify(communityService, never()).createPost(any(), anyLong());
    }

    @Test
    void editForm_bindsExistingValues() throws Exception {
        authenticateAs(7L);
        when(communityService.getEditablePost(15L, 7L)).thenReturn(publishedPost());

        mockMvc.perform(get("/community/15/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/community/form"))
                // 수정 대상을 모델에 유지한다.
                .andExpect(model().attribute("editingPostId", 15L))
                .andExpect(model().attributeExists("categories"));
    }

    @Test
    void edit_validForm_redirectsToDetail() throws Exception {
        authenticateAs(7L);

        mockMvc.perform(post("/community/15/edit")
                        .param("categoryId", "1")
                        .param("title", "고친 제목")
                        .param("content", "고친 본문"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community/15"));

        verify(communityService).updatePost(eq(15L), any(), eq(7L));
    }

    @Test
    void edit_blankContent_returnsFormKeepingEditTarget() throws Exception {
        authenticateAs(7L);

        mockMvc.perform(post("/community/15/edit")
                        .param("categoryId", "1")
                        .param("title", "제목")
                        .param("content", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/community/form"))
                .andExpect(model().attributeHasFieldErrors("form", "content"))
                // 검증 실패 후에도 수정 대상을 유지한다.
                .andExpect(model().attribute("editingPostId", 15L));

        verify(communityService, never()).updatePost(anyLong(), any(), anyLong());
    }

    @Test
    void delete_redirectsToList() throws Exception {
        authenticateAs(7L);

        mockMvc.perform(post("/community/15/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community"))
                // 삭제 성공 메시지를 전달한다.
                .andExpect(flash().attribute("successMessage", "게시글을 삭제했습니다."));

        verify(communityService).deletePost(15L, 7L);
    }

    /** 수정 권한을 입력값보다 먼저 확인한다. */
    @Test
    void edit_invalidForm_checksPermissionBeforeValidation() throws Exception {
        authenticateAs(7L);
        doThrow(new BusinessException(CommunityErrorCode.POST_NOT_FOUND))
                .when(communityService).getEditablePost(15L, 7L);

        assertThatThrownBy(() -> mockMvc.perform(post("/community/15/edit")
                        .param("categoryId", "1")
                        .param("title", "제목")
                        .param("content", "   ")))
                .hasRootCauseInstanceOf(BusinessException.class);

        verify(communityService, never()).updatePost(anyLong(), any(), anyLong());
    }

    /** 비활성화된 카테고리는 폼 오류로 처리한다. */
    @Test
    void create_categoryDeactivatedWhileWriting_returnsFormWithFieldError() throws Exception {
        authenticateAs(7L);
        when(communityService.createPost(any(), eq(7L)))
                .thenThrow(new BusinessException(CommunityErrorCode.CATEGORY_NOT_FOUND));

        mockMvc.perform(post("/community")
                        .param("categoryId", "1")
                        .param("title", "쓰던 제목")
                        .param("content", "쓰던 본문"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/community/form"))
                .andExpect(model().attributeHasFieldErrors("form", "categoryId"))
                // 작성 중이던 입력을 유지한다.
                .andExpect(model().attribute("form",
                        hasProperty("title", equalTo("쓰던 제목"))))
                .andExpect(model().attributeExists("categories"));
    }

    @Test
    void edit_categoryDeactivatedWhileWriting_returnsFormKeepingEditTarget() throws Exception {
        authenticateAs(7L);
        doThrow(new BusinessException(CommunityErrorCode.CATEGORY_NOT_FOUND))
                .when(communityService).updatePost(eq(15L), any(), eq(7L));

        mockMvc.perform(post("/community/15/edit")
                        .param("categoryId", "1")
                        .param("title", "제목")
                        .param("content", "본문"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "categoryId"))
                .andExpect(model().attribute("editingPostId", 15L));
    }

    /** 카테고리 외 업무 오류는 폼 오류로 바꾸지 않는다. */
    @Test
    void create_nonCategoryBusinessError_isNotSwallowedIntoForm() throws Exception {
        authenticateAs(7L);
        when(communityService.createPost(any(), eq(7L)))
                .thenThrow(new BusinessException(CommunityErrorCode.POST_NOT_FOUND));

        assertThatThrownBy(() -> mockMvc.perform(post("/community")
                        .param("categoryId", "1")
                        .param("title", "제목")
                        .param("content", "본문")))
                .hasRootCauseInstanceOf(BusinessException.class);
    }

    @Test
    void detail_bindsCommentSectionAndForm() throws Exception {
        when(communityService.getPostDetail(eq(15L), isNull(), anyString())).thenReturn(publishedPost());

        mockMvc.perform(get("/community/15"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("commentSection"))
                .andExpect(model().attributeExists("commentForm"))
                // 비로그인에게는 댓글 폼을 주지 않는다.
                .andExpect(model().attribute("canComment", false));
    }

    @Test
    void detail_authenticatedViewer_canComment() throws Exception {
        authenticateAs(7L);
        when(communityService.getPostDetail(eq(15L), eq(7L), anyString())).thenReturn(publishedPost());

        mockMvc.perform(get("/community/15"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("canComment", true));
    }

    /** 차단 글에는 댓글을 작성할 수 없다. */
    @Test
    void detail_blockedPost_author_cannotComment() throws Exception {
        authenticateAs(7L);
        when(communityService.getPostDetail(eq(15L), eq(7L), anyString())).thenReturn(blockedPost());

        mockMvc.perform(get("/community/15"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("canComment", false));
    }

    /** 댓글 조회 수를 Service에 전달한다. */
    @Test
    void detail_commentsParameter_isPassedToService() throws Exception {
        // 댓글 더 보기 경로를 사용한다.
        when(communityService.getVisiblePost(eq(15L), isNull())).thenReturn(publishedPost());

        mockMvc.perform(get("/community/15").param("comments", "40"))
                .andExpect(status().isOk());

        verify(communityService).getComments(15L, 40);
    }

    /** 잘못된 댓글 조회 수에는 기본값을 사용한다. */
    @Test
    void detail_invalidCommentsParameter_fallsBackToDefault() throws Exception {
        // 잘못된 값도 댓글 더 보기로 처리한다.
        when(communityService.getVisiblePost(eq(15L), isNull())).thenReturn(publishedPost());

        mockMvc.perform(get("/community/15").param("comments", "전체"))
                .andExpect(status().isOk());

        verify(communityService).getComments(15L, null);
    }

    @Test
    void addComment_validForm_redirectsToDetail() throws Exception {
        authenticateAs(7L);
        when(communityService.getCommentablePost(15L, 7L)).thenReturn(publishedPost());

        mockMvc.perform(post("/community/15/comments")
                        .param("content", "댓글 본문")
                        .param("memberId", "99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community/15"));

        verify(communityService).addComment(eq(15L), any(), eq(7L));
    }

    /** 댓글 검증 실패 시 조회수 없이 상세를 다시 그린다. */
    @Test
    void addComment_blankContent_redrawsDetailWithoutCountingAView() throws Exception {
        authenticateAs(7L);
        when(communityService.getCommentablePost(15L, 7L)).thenReturn(publishedPost());

        mockMvc.perform(post("/community/15/comments").param("content", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/community/detail"))
                .andExpect(model().attributeHasFieldErrors("commentForm", "content"))
                .andExpect(model().attributeExists("commentSection"));

        verify(communityService, never()).addComment(anyLong(), any(), anyLong());
        verify(communityService, never()).getPostDetail(anyLong(), any(), anyString());
    }

    /** 댓글 작성 가능 상태를 입력값보다 먼저 확인한다. */
    @Test
    void addComment_invalidForm_checksPostStateBeforeValidation() throws Exception {
        authenticateAs(7L);
        when(communityService.getCommentablePost(15L, 7L))
                .thenThrow(new BusinessException(CommunityErrorCode.POST_NOT_FOUND));

        assertThatThrownBy(() -> mockMvc.perform(
                        post("/community/15/comments").param("content", "   ")))
                .hasRootCauseInstanceOf(BusinessException.class);

        verify(communityService, never()).addComment(anyLong(), any(), anyLong());
    }

    /** 댓글 검증 실패 후에도 펼친 범위를 유지한다. */
    @Test
    void addComment_invalidForm_keepsExpandedCommentLimit() throws Exception {
        authenticateAs(7L);
        when(communityService.getCommentablePost(15L, 7L)).thenReturn(publishedPost());

        mockMvc.perform(post("/community/15/comments")
                        .param("content", "   ")
                        .param("comments", "40"))
                .andExpect(status().isOk());

        verify(communityService).getComments(15L, 40);
    }

    @Test
    void deleteComment_redirectsToDetail() throws Exception {
        authenticateAs(7L);

        mockMvc.perform(post("/community/15/comments/8/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community/15"));

        verify(communityService).deleteComment(15L, 8L, 7L);
    }

    /** 댓글 삭제 후에도 펼친 범위를 유지한다. */
    @Test
    void deleteComment_keepsExpandedCommentLimit() throws Exception {
        authenticateAs(7L);

        mockMvc.perform(post("/community/15/comments/8/delete").param("comments", "60"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community/15?comments=60"));
    }

    /** 댓글 조회 수를 정규화해 리다이렉트한다. */
    @ParameterizedTest
    @CsvSource({
            "abc, /community/15",
            "-1, /community/15",
            "20, /community/15",
            "99999999, /community/15?comments=200",
            "'40 OR 1=1', /community/15"
    })
    void deleteComment_rewritesCommentLimitAsInteger(String requested, String expectedUrl)
            throws Exception {
        authenticateAs(7L);

        mockMvc.perform(post("/community/15/comments/8/delete").param("comments", requested))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(expectedUrl));
    }

    /** 비로그인은 좋아요 여부를 조회하지 않는다. */
    @Test
    void detail_anonymousViewer_doesNotAskWhetherLiked() throws Exception {
        when(communityService.getPostDetail(eq(15L), isNull(), anyString()))
                .thenReturn(publishedPost());

        mockMvc.perform(get("/community/15")).andExpect(status().isOk());

        verify(communityService, never()).isLikedBy(anyLong(), anyLong());
    }

    @Test
    void detail_authenticatedViewer_asksWhetherLiked() throws Exception {
        authenticateAs(7L);
        when(communityService.getPostDetail(eq(15L), eq(7L), anyString()))
                .thenReturn(publishedPost());
        when(communityService.isLikedBy(15L, 7L)).thenReturn(true);

        mockMvc.perform(get("/community/15"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("likedByViewer", true))
                .andExpect(model().attribute("canLike", true));
    }

    /** 차단 글에는 좋아요 기능을 열지 않는다. */
    @Test
    void detail_blockedPostAuthor_cannotLike() throws Exception {
        authenticateAs(7L);
        when(communityService.getPostDetail(eq(15L), eq(7L), anyString()))
                .thenReturn(blockedPost());

        mockMvc.perform(get("/community/15"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("canLike", false))
                .andExpect(model().attribute("likedByViewer", false));

        verify(communityService, never()).isLikedBy(anyLong(), anyLong());
    }

    @Test
    void addLike_redirectsToDetail() throws Exception {
        authenticateAs(7L);

        mockMvc.perform(post("/community/15/likes"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community/15"));

        verify(communityService).addLike(15L, 7L);
    }

    @Test
    void removeLike_redirectsToDetail() throws Exception {
        authenticateAs(7L);

        mockMvc.perform(post("/community/15/likes/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community/15"));

        verify(communityService).removeLike(15L, 7L);
    }

    /** 좋아요 추가와 취소 경로를 분리한다. */
    @Test
    void likeRoutes_areSplitSoNeitherPathTogglesTheOther() throws Exception {
        authenticateAs(7L);

        mockMvc.perform(post("/community/15/likes"));
        verify(communityService).addLike(15L, 7L);
        verify(communityService, never()).removeLike(anyLong(), anyLong());

        mockMvc.perform(post("/community/15/likes/delete"));
        verify(communityService).removeLike(15L, 7L);
    }

    /** 좋아요 처리 후에도 펼친 댓글 범위를 유지한다. */
    @ParameterizedTest
    @CsvSource({
            "/community/15/likes",
            "/community/15/likes/delete"
    })
    void likeRoutes_keepExpandedCommentLimit(String path) throws Exception {
        authenticateAs(7L);

        mockMvc.perform(post(path).param("comments", "60"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community/15?comments=60"));
    }

    /** 좋아요 리다이렉트의 댓글 수를 정규화한다. */
    @ParameterizedTest
    @CsvSource({
            "abc, /community/15",
            "-1, /community/15",
            "20, /community/15",
            "99999999, /community/15?comments=200",
            "'40 OR 1=1', /community/15"
    })
    void addLike_rewritesCommentLimitAsInteger(String requested, String expectedUrl)
            throws Exception {
        authenticateAs(7L);

        mockMvc.perform(post("/community/15/likes").param("comments", requested))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(expectedUrl));
    }

    private void authenticateAs(long memberId) {
        MemberDetails principal = new MemberDetails(new MemberAuthenticationView(
                memberId, "author@cakeshop.local", "dummy", "USER", true));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
    }

    private String capturedViewerKey() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(communityService).getPostDetail(anyLong(), any(), captor.capture());

        return captor.getValue();
    }

    private PageRequest capturedPageRequest() {
        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(communityService).getPosts(any(), any(), captor.capture());
        return captor.getValue();
    }

    @Test
    void report_validForm_redirectsToDetailWithMessage() throws Exception {
        authenticateAs(9L);
        when(communityService.getReportablePost(15L, 9L)).thenReturn(publishedPost());

        mockMvc.perform(post("/community/15/reports")
                        .param("reason", "광고입니다")
                        .param("reporterId", "99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community/15"))
                .andExpect(flash().attribute("successMessage", "신고를 접수했습니다."));

        verify(communityService).reportPost(eq(15L), any(), eq(9L));
    }

    /** 신고 검증 실패 시 조회수 없이 상세를 다시 그린다. */
    @Test
    void report_blankReason_redrawsDetailWithoutCountingAView() throws Exception {
        authenticateAs(9L);
        when(communityService.getReportablePost(15L, 9L)).thenReturn(publishedPost());

        mockMvc.perform(post("/community/15/reports").param("reason", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/community/detail"))
                .andExpect(model().attributeHasFieldErrors("reportForm", "reason"))
                .andExpect(model().attributeExists("commentSection"));

        verify(communityService, never()).reportPost(anyLong(), any(), anyLong());
        verify(communityService, never()).getPostDetail(anyLong(), any(), anyString());
    }

    /** 신고 가능 상태를 입력값보다 먼저 확인한다. */
    @Test
    void report_invisiblePost_checksPermissionBeforeValidation() {
        authenticateAs(9L);
        when(communityService.getReportablePost(15L, 9L))
                .thenThrow(new BusinessException(CommunityErrorCode.POST_NOT_FOUND));

        assertThatThrownBy(() ->
                mockMvc.perform(post("/community/15/reports").param("reason", "   ")))
                .hasRootCauseInstanceOf(BusinessException.class);

        verify(communityService, never()).reportPost(anyLong(), any(), anyLong());
    }

    /** 신고 후에도 펼친 댓글 범위를 유지한다. */
    @Test
    void report_keepsExpandedComments() throws Exception {
        authenticateAs(9L);
        when(communityService.getReportablePost(15L, 9L)).thenReturn(publishedPost());

        mockMvc.perform(post("/community/15/reports")
                        .param("reason", "광고입니다")
                        .param("comments", "60"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community/15?comments=60"));
    }

    private PostDetailView publishedPost() {
        return new PostDetailView(
                15L, 7L, 1L, "질문", "제목", "본문", "글쓴이", false,
                PostStatus.PUBLISHED, null, 10L, 2L, CREATED_AT, CREATED_AT);
    }

    private PostDetailView blockedPost() {
        return new PostDetailView(
                15L, 7L, 1L, "질문", "제목", "본문", "글쓴이", false,
                PostStatus.BLOCKED, "광고성 게시물", 10L, 2L, CREATED_AT, CREATED_AT);
    }
}
