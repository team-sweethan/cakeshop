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
import com.cakeshop.domain.community.dto.view.NoticeSectionView;
import com.cakeshop.domain.community.dto.view.PostCategoryView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListView;
import com.cakeshop.domain.community.dto.view.PostSort;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.service.CommunityNoticeService;
import com.cakeshop.domain.community.service.CommunityPostImageService;
import com.cakeshop.domain.community.service.CommunityReactionService;
import com.cakeshop.domain.community.service.CommunityCommentService;
import com.cakeshop.domain.community.service.CommunityPostService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CommunityControllerTests {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 3, 1, 10, 0);

    private CommunityPostService communityPostService;
    private CommunityCommentService communityCommentService;
    private CommunityNoticeService communityNoticeService;
    private CommunityReactionService communityReactionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        communityPostService = mock(CommunityPostService.class);
        communityCommentService = mock(CommunityCommentService.class);
        communityNoticeService = mock(CommunityNoticeService.class);
        communityReactionService = mock(CommunityReactionService.class);

        when(communityNoticeService.getListSection(any(), any()))
                .thenReturn(NoticeSectionView.empty());

        when(communityPostService.getPosts(any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(), new PageRequest(1, 20), 0));
        when(communityPostService.getActiveCategories())
                .thenReturn(List.of(new PostCategoryView(1L, "QNA", "질문")));
        when(communityCommentService.getComments(anyLong(), any(), any()))
                .thenReturn(new CommentSectionView(
                        List.of(), 0, 0, CommentSectionView.DEFAULT_LIMIT));

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new CommunityController(
                                communityPostService,
                                mock(CommunityPostImageService.class),
                                communityNoticeService,
                                // 상세 화면을 목으로 갈면 아래 Model 속성 검사가 전부 빈 값을 본다.
                                new CommunityDetailPage(
                                        communityCommentService, communityReactionService, mock(CommunityPostImageService.class))))
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

    /**
     * 목록 파라미터는 잘못된 값에도 오류 대신 기본값으로 떨어진다. 잘못된 카테고리는 무시하고,
     * 정렬값은 대소문자를 구분하지 않으며 모르는 값은 최신순, 잘못된 페이지는 1쪽이 된다.
     */
    @ParameterizedTest(name = "categoryId={0}, sort={1}, page={2}")
    @CsvSource(nullValues = "-", value = {
            "5, -, -, 5, LATEST, 1",
            "전체, -, -, -, LATEST, 1",
            "-, VIEWS, -, -, VIEWS, 1",
            "-, views, -, -, VIEWS, 1",
            "-, LIKES, -, -, LIKES, 1",
            "-, likes, -, -, LIKES, 1",
            "-, 'id; DROP TABLE posts', -, -, LATEST, 1",
            "-, -, abc, -, LATEST, 1"
    })
    void list_requestParameters_arePassedToServiceOrFallBackToDefaults(
            String categoryId,
            String sort,
            String page,
            Long expectedCategoryId,
            PostSort expectedSort,
            int expectedPage
    ) throws Exception {
        MockHttpServletRequestBuilder request = get("/community");

        if (categoryId != null) {
            request = request.param("categoryId", categoryId);
        }
        if (sort != null) {
            request = request.param("sort", sort);
        }
        if (page != null) {
            request = request.param("page", page);
        }

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedCategoryId", expectedCategoryId))
                .andExpect(model().attribute("selectedSort", expectedSort))
                .andExpect(model().attributeExists("sortOptions"));

        verify(communityPostService).getPosts(eq(expectedCategoryId), eq(expectedSort), any());
        assertThat(capturedPageRequest().getPage()).isEqualTo(expectedPage);
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
        when(communityPostService.getPostDetail(eq(15L), isNull(), anyString())).thenReturn(publishedPost());

        mockMvc.perform(get("/community/15"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/community/detail"))
                .andExpect(model().attributeExists("post"));

        verify(communityPostService).getPostDetail(eq(15L), isNull(), anyString());
    }

    /** 인증 회원 ID를 상세 조회에 전달한다. */
    @Test
    void detail_authenticatedViewer_passesAuthenticatedMemberId() throws Exception {
        MemberDetails principal = new MemberDetails(new MemberAuthenticationView(
                7L, "author@cakeshop.local", "dummy", "USER", true));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));

        when(communityPostService.getPostDetail(eq(15L), eq(7L), anyString())).thenReturn(publishedPost());

        mockMvc.perform(get("/community/15"))
                .andExpect(status().isOk());

        verify(communityPostService).getPostDetail(eq(15L), eq(7L), anyString());
    }

    /** 댓글 더 보기는 조회수를 올리지 않는다. */
    @Test
    void detail_loadingMoreComments_doesNotCountAsView() throws Exception {
        when(communityPostService.getVisiblePost(eq(15L), isNull())).thenReturn(publishedPost());

        mockMvc.perform(get("/community/15").param("comments", "40"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/community/detail"));

        verify(communityPostService).getVisiblePost(eq(15L), isNull());
        verify(communityPostService, never()).getPostDetail(anyLong(), any(), anyString());
    }

    /** 답글 펼치기도 이미 보고 있는 글 안에서의 이동이라 조회수를 올리지 않는다. */
    @Test
    void detail_expandingReplies_doesNotCountAsView() throws Exception {
        when(communityPostService.getVisiblePost(eq(15L), isNull())).thenReturn(publishedPost());

        mockMvc.perform(get("/community/15").param("replies", "8"))
                .andExpect(status().isOk());

        verify(communityCommentService).getComments(15L, null, 8L);
        verify(communityPostService, never()).getPostDetail(anyLong(), any(), anyString());
    }

    /** 잘못된 replies 값은 펼침 없음으로 떨어진다. */
    @Test
    void detail_invalidRepliesParameter_fallsBackToCollapsed() throws Exception {
        when(communityPostService.getVisiblePost(eq(15L), isNull())).thenReturn(publishedPost());

        mockMvc.perform(get("/community/15").param("replies", "전체"))
                .andExpect(status().isOk());

        verify(communityCommentService).getComments(15L, null, null);
    }

    /** 직접 상세 진입은 조회수를 반영한다. */
    @Test
    void detail_directEntry_stillCountsAsView() throws Exception {
        when(communityPostService.getPostDetail(eq(15L), isNull(), anyString()))
                .thenReturn(publishedPost());

        mockMvc.perform(get("/community/15")).andExpect(status().isOk());

        verify(communityPostService).getPostDetail(eq(15L), isNull(), anyString());
        verify(communityPostService, never()).getVisiblePost(anyLong(), any());
    }

    /** 조회자 키는 인증 정보로 만든다. */
    @Test
    void detail_viewerKey_comesFromAuthenticationNotFromRequest() throws Exception {
        authenticateAs(7L);
        when(communityPostService.getPostDetail(eq(15L), eq(7L), anyString()))
                .thenReturn(publishedPost());

        mockMvc.perform(get("/community/15")
                        // 요청의 조회자 키는 무시한다.
                        .param("viewerKey", "M:99"))
                .andExpect(status().isOk());

        assertThat(capturedViewerKey()).isEqualTo("M:7");
    }

    @Test
    void detail_anonymousViewer_usesSessionAsViewerKey() throws Exception {
        when(communityPostService.getPostDetail(eq(15L), isNull(), anyString()))
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
        when(communityPostService.createPost(any(), eq(7L))).thenReturn(42L);

        mockMvc.perform(post("/community")
                        .param("categoryId", "1")
                        .param("title", "제목")
                        .param("content", "본문")
                        .param("memberId", "99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community/42"));

        verify(communityPostService).createPost(any(), eq(7L));
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

        verify(communityPostService, never()).createPost(any(), anyLong());
    }

    @Test
    void editForm_bindsExistingValues() throws Exception {
        authenticateAs(7L);
        when(communityPostService.getEditablePost(15L, 7L)).thenReturn(publishedPost());

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

        verify(communityPostService).updatePost(eq(15L), any(), eq(7L));
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

        verify(communityPostService, never()).updatePost(anyLong(), any(), anyLong());
    }

    @Test
    void delete_redirectsToList() throws Exception {
        authenticateAs(7L);

        mockMvc.perform(post("/community/15/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community"))
                // 삭제 성공 메시지를 전달한다.
                .andExpect(flash().attribute("successMessage", "게시글을 삭제했습니다."));

        verify(communityPostService).deletePost(15L, 7L);
    }

    /** 수정 권한을 입력값보다 먼저 확인한다. */
    @Test
    void edit_invalidForm_checksPermissionBeforeValidation() throws Exception {
        authenticateAs(7L);
        doThrow(new BusinessException(CommunityErrorCode.POST_NOT_FOUND))
                .when(communityPostService).getEditablePost(15L, 7L);

        assertThatThrownBy(() -> mockMvc.perform(post("/community/15/edit")
                        .param("categoryId", "1")
                        .param("title", "제목")
                        .param("content", "   ")))
                .hasRootCauseInstanceOf(BusinessException.class);

        verify(communityPostService, never()).updatePost(anyLong(), any(), anyLong());
    }

    /** 비활성화된 카테고리는 폼 오류로 처리한다. */
    @Test
    void create_categoryDeactivatedWhileWriting_returnsFormWithFieldError() throws Exception {
        authenticateAs(7L);
        when(communityPostService.createPost(any(), eq(7L)))
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
                .when(communityPostService).updatePost(eq(15L), any(), eq(7L));

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
        when(communityPostService.createPost(any(), eq(7L)))
                .thenThrow(new BusinessException(CommunityErrorCode.POST_NOT_FOUND));

        assertThatThrownBy(() -> mockMvc.perform(post("/community")
                        .param("categoryId", "1")
                        .param("title", "제목")
                        .param("content", "본문")))
                .hasRootCauseInstanceOf(BusinessException.class);
    }

    @Test
    void detail_bindsCommentSectionAndForm() throws Exception {
        when(communityPostService.getPostDetail(eq(15L), isNull(), anyString())).thenReturn(publishedPost());

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
        when(communityPostService.getPostDetail(eq(15L), eq(7L), anyString())).thenReturn(publishedPost());

        mockMvc.perform(get("/community/15"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("canComment", true));
    }

    /** 차단 글에는 댓글을 작성할 수 없다. */
    @Test
    void detail_blockedPost_author_cannotComment() throws Exception {
        authenticateAs(7L);
        when(communityPostService.getPostDetail(eq(15L), eq(7L), anyString())).thenReturn(blockedPost());

        mockMvc.perform(get("/community/15"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("canComment", false));
    }

    /** 댓글 조회 수를 Service에 전달한다. */
    @Test
    void detail_commentsParameter_isPassedToService() throws Exception {
        // 댓글 더 보기 경로를 사용한다.
        when(communityPostService.getVisiblePost(eq(15L), isNull())).thenReturn(publishedPost());

        mockMvc.perform(get("/community/15").param("comments", "40"))
                .andExpect(status().isOk());

        verify(communityCommentService).getComments(15L, 40, null);
    }

    /** 잘못된 댓글 조회 수에는 기본값을 사용한다. */
    @Test
    void detail_invalidCommentsParameter_fallsBackToDefault() throws Exception {
        // 잘못된 값도 댓글 더 보기로 처리한다.
        when(communityPostService.getVisiblePost(eq(15L), isNull())).thenReturn(publishedPost());

        mockMvc.perform(get("/community/15").param("comments", "전체"))
                .andExpect(status().isOk());

        verify(communityCommentService).getComments(15L, null, null);
    }

    /** 비로그인은 좋아요 여부를 조회하지 않는다. */
    @Test
    void detail_anonymousViewer_doesNotAskWhetherLiked() throws Exception {
        when(communityPostService.getPostDetail(eq(15L), isNull(), anyString()))
                .thenReturn(publishedPost());

        mockMvc.perform(get("/community/15")).andExpect(status().isOk());

        verify(communityReactionService, never()).isLikedBy(anyLong(), anyLong());
    }

    @Test
    void detail_authenticatedViewer_asksWhetherLiked() throws Exception {
        authenticateAs(7L);
        when(communityPostService.getPostDetail(eq(15L), eq(7L), anyString()))
                .thenReturn(publishedPost());
        when(communityReactionService.isLikedBy(15L, 7L)).thenReturn(true);

        mockMvc.perform(get("/community/15"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("likedByViewer", true))
                .andExpect(model().attribute("canLike", true));
    }

    /** 차단 글에는 좋아요 기능을 열지 않는다. */
    @Test
    void detail_blockedPostAuthor_cannotLike() throws Exception {
        authenticateAs(7L);
        when(communityPostService.getPostDetail(eq(15L), eq(7L), anyString()))
                .thenReturn(blockedPost());

        mockMvc.perform(get("/community/15"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("canLike", false))
                .andExpect(model().attribute("likedByViewer", false));

        verify(communityReactionService, never()).isLikedBy(anyLong(), anyLong());
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
        verify(communityPostService).getPostDetail(anyLong(), any(), captor.capture());

        return captor.getValue();
    }

    private PageRequest capturedPageRequest() {
        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(communityPostService).getPosts(any(), any(), captor.capture());
        return captor.getValue();
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
