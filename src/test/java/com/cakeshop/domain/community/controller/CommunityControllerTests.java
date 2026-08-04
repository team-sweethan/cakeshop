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

        when(communityService.getPosts(any(), any()))
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

        verify(communityService).getPosts(eq(5L), any());
    }

    /** 목록은 비로그인도 여는 공개 화면이라 주소가 망가져도 오류 페이지로 보내지 않는다. */
    @Test
    void list_invalidCategoryId_ignoresFilterInsteadOfFailing() throws Exception {
        mockMvc.perform(get("/community").param("categoryId", "전체"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedCategoryId", (Object) null));

        verify(communityService).getPosts(isNull(), any());
    }

    @Test
    void list_invalidPage_fallsBackToFirstPage() throws Exception {
        mockMvc.perform(get("/community").param("page", "abc"))
                .andExpect(status().isOk());

        assertThat(capturedPageRequest().getPage()).isEqualTo(1);
    }

    /** 페이지 크기는 20 고정이며 사용자가 바꿀 수 없다(DOMAIN.md 6.1). */
    @Test
    void list_pageSize_isFixedAndIgnoresSizeParameter() throws Exception {
        mockMvc.perform(get("/community").param("page", "2").param("size", "100"))
                .andExpect(status().isOk());

        PageRequest pageRequest = capturedPageRequest();
        assertThat(pageRequest.getSize()).isEqualTo(20);
        assertThat(pageRequest.getPage()).isEqualTo(2);
        assertThat(pageRequest.getOffset()).isEqualTo(20);
    }

    /**
     * 목록은 공개 화면이라 주소에 어떤 숫자든 들어올 수 있다.
     *
     * <p>페이지 번호가 크면 {@code (page - 1) * size}가 int를 넘어 음수 OFFSET이 되고,
     * DB가 이를 거부해 화면이 500으로 죽는다. 범위를 넘은 페이지는 빈 목록이어야 한다.
     */
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

    /** 소유권 판단 기준은 요청 파라미터가 아니라 인증 정보다(AGENTS.md). */
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

    /**
     * 댓글 "더 보기" 요청이 조회수를 올리지 않는지 확인한다(DOMAIN.md 6.2, PR #98 Codex 리뷰).
     *
     * <p>10분 창만으로는 막히지 않는다 — 상세를 10분 넘게 읽다가 누르면 창이 이미 닫혀
     * 그대로 +1이 된다. 창 안에서만 확인하는 테스트는 이 경계를 통째로 놓치고, 통과한다.
     * 그래서 창의 폭이 아니라 <b>경로가 갈리는지</b>를 본다.
     */
    @Test
    void detail_loadingMoreComments_doesNotCountAsView() throws Exception {
        when(communityService.getVisiblePost(eq(15L), isNull())).thenReturn(publishedPost());

        mockMvc.perform(get("/community/15").param("comments", "40"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/community/detail"));

        verify(communityService).getVisiblePost(eq(15L), isNull());
        verify(communityService, never()).getPostDetail(anyLong(), any(), anyString());
    }

    /** 반대쪽. 평범한 진입은 그대로 세어야 한다 — 위 테스트만 있으면 아무도 안 세도 통과한다. */
    @Test
    void detail_directEntry_stillCountsAsView() throws Exception {
        when(communityService.getPostDetail(eq(15L), isNull(), anyString()))
                .thenReturn(publishedPost());

        mockMvc.perform(get("/community/15")).andExpect(status().isOk());

        verify(communityService).getPostDetail(eq(15L), isNull(), anyString());
        verify(communityService, never()).getVisiblePost(anyLong(), any());
    }

    /**
     * 조회자 키가 인증 정보·세션에서만 오는지 확인한다(DOMAIN.md 6.2).
     *
     * <p>클라이언트가 정하는 값이면 매번 다른 키를 실어 보내는 것만으로 중복 방지가
     * 사라진다. 조회수가 순위를 정하는 이상 그건 곧 순위 조작이다.
     */
    @Test
    void detail_viewerKey_comesFromAuthenticationNotFromRequest() throws Exception {
        authenticateAs(7L);
        when(communityService.getPostDetail(eq(15L), eq(7L), anyString()))
                .thenReturn(publishedPost());

        mockMvc.perform(get("/community/15")
                        // 요청이 실어 보낸 키는 무시되어야 한다.
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

        // 비로그인은 세션 id로 센다. 접두사가 회원 키와 겹치면 서로 다른 사람이 합쳐진다.
        assertThat(capturedViewerKey()).startsWith("S:").isNotEqualTo("S:");
    }

    /** 상세는 숫자 경로만 받는다. SecurityConfig의 공개 규칙과 같은 범위여야 한다. */
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
                        .param("content", "본문"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community/42"));
    }

    /** 작성자는 요청 파라미터가 아니라 인증 정보에서 온다(AGENTS.md). */
    @Test
    void create_usesAuthenticatedMemberAsAuthor() throws Exception {
        authenticateAs(7L);
        when(communityService.createPost(any(), eq(7L))).thenReturn(42L);

        mockMvc.perform(post("/community")
                        .param("categoryId", "1")
                        .param("title", "제목")
                        .param("content", "본문")
                        // 남의 회원 번호를 실어 보내도 무시되어야 한다.
                        .param("memberId", "99"))
                .andExpect(status().is3xxRedirection());

        verify(communityService).createPost(any(), eq(7L));
    }

    /**
     * 검증 실패는 입력을 되돌려 준다.
     *
     * <p>리다이렉트로 처리하면 사용자가 쓰던 글이 사라진다. 긴 글일수록 손해가 크다.
     */
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
                // 이 값이 있어야 폼이 작성이 아니라 수정으로 전송된다.
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
                // 여기서 대상이 빠지면 재전송이 작성으로 나가 글이 하나 더 생긴다.
                .andExpect(model().attribute("editingPostId", 15L));

        verify(communityService, never()).updatePost(anyLong(), any(), anyLong());
    }

    @Test
    void delete_redirectsToList() throws Exception {
        authenticateAs(7L);

        mockMvc.perform(post("/community/15/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community"))
                // 삭제 결과는 목록에 남지 않는다. 알리지 않으면 지워졌는지 알 수 없다.
                .andExpect(flash().attribute("successMessage", "게시글을 삭제했습니다."));

        verify(communityService).deletePost(15L, 7L);
    }

    /**
     * 검증 실패보다 권한을 먼저 본다.
     *
     * <p>순서가 뒤집히면 남의 글 번호로 빈 본문을 보냈을 때 소유권도 상태도 확인하지 않은
     * 채 수정 화면이 200으로 열린다. 유효한 값을 보내야 그제서야 404가 나므로, 그 전까지는
     * 자기 글인 것처럼 보인다.
     */
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

    /**
     * 글을 쓰는 동안 분류가 비활성으로 바뀌면 입력을 잃지 않는다.
     *
     * <p>예외를 그대로 흘리면 공통 4xx 화면이 뜨고 쓰던 제목과 본문이 사라진다. 분류는
     * 화면에서 다시 고르면 되는 입력 오류다(conventions.md 9).
     */
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
                // 쓰던 입력이 그대로 돌아와야 한다.
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

    /** 소유권·상태 오류는 화면에서 고칠 수 없다. 폼으로 삼키지 않는다. */
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
                // 비로그인에게는 댓글 폼을 주지 않는다(DOMAIN.md 5).
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

    /** 차단된 글은 작성자만 열지만 댓글은 달 수 없다(DOMAIN.md 4.5). */
    @Test
    void detail_blockedPost_author_cannotComment() throws Exception {
        authenticateAs(7L);
        when(communityService.getPostDetail(eq(15L), eq(7L), anyString())).thenReturn(blockedPost());

        mockMvc.perform(get("/community/15"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("canComment", false));
    }

    /** "더 보기"가 실어 보낸 값이 그대로 Service에 넘어가야 펼친 상태가 유지된다. */
    @Test
    void detail_commentsParameter_isPassedToService() throws Exception {
        // comments가 있는 요청은 조회수를 올리지 않는 경로로 간다.
        when(communityService.getVisiblePost(eq(15L), isNull())).thenReturn(publishedPost());

        mockMvc.perform(get("/community/15").param("comments", "40"))
                .andExpect(status().isOk());

        verify(communityService).getComments(15L, 40);
    }

    /** 상세는 공개 화면이라 주소가 망가져도 오류 페이지 대신 기본 상태를 보여준다. */
    @Test
    void detail_invalidCommentsParameter_fallsBackToDefault() throws Exception {
        // 값이 망가져도 comments가 붙은 요청은 더 보기다. 조회수는 여기서도 올리지 않는다.
        when(communityService.getVisiblePost(eq(15L), isNull())).thenReturn(publishedPost());

        mockMvc.perform(get("/community/15").param("comments", "전체"))
                .andExpect(status().isOk());

        verify(communityService).getComments(15L, null);
    }

    @Test
    void addComment_validForm_redirectsToDetail() throws Exception {
        authenticateAs(7L);
        when(communityService.getCommentablePost(15L, 7L)).thenReturn(publishedPost());

        mockMvc.perform(post("/community/15/comments").param("content", "댓글 본문"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community/15"));

        verify(communityService).addComment(eq(15L), any(), eq(7L));
    }

    /** 작성자는 요청 파라미터가 아니라 인증 정보에서 온다(AGENTS.md). */
    @Test
    void addComment_usesAuthenticatedMemberAsAuthor() throws Exception {
        authenticateAs(7L);
        when(communityService.getCommentablePost(15L, 7L)).thenReturn(publishedPost());

        mockMvc.perform(post("/community/15/comments")
                        .param("content", "댓글 본문")
                        // 남의 회원 번호를 실어 보내도 무시되어야 한다.
                        .param("memberId", "99"))
                .andExpect(status().is3xxRedirection());

        verify(communityService).addComment(eq(15L), any(), eq(7L));
    }

    /**
     * 검증 실패는 리다이렉트하지 않고 상세를 다시 그린다(conventions.md 9).
     *
     * <p>여기서 {@code getPostDetail}을 부르면 잘못 보낸 댓글마다 조회수가 오른다.
     * 상세를 다시 그리는 것은 조회가 아니다.
     */
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

    /**
     * 검증 실패보다 게시글 상태를 먼저 본다.
     *
     * <p>순서가 뒤집히면 삭제된 글 번호로 빈 댓글을 보냈을 때 상태도 확인하지 않은 채
     * 그 글의 상세가 200으로 열린다(조각 2의 수정 화면과 같은 실수다).
     */
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

    /**
     * 검증에 실패해 상세를 다시 그릴 때도 펼친 상태를 잃지 않는지 확인한다.
     *
     * <p>등록에 실패한 사람은 어디로 간 것이 아니라 제자리다. 20건으로 접어 버리면
     * 오류 메시지와 함께 읽고 있던 댓글까지 사라진다(screens/detail.md).
     */
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

    /**
     * 삭제 후에도 펼친 댓글 수가 유지되는지 확인한다.
     *
     * <p>삭제에는 성공 메시지가 없고 지운 자리의 "삭제된 댓글입니다."가 결과를 보여 주는
     * 유일한 신호다. 20건으로 접어 돌려보내면 <b>최신 20건 밖의 댓글은 그 자리가 화면 밖으로
     * 나가</b> 사용자에게는 삭제가 안 된 것과 구분되지 않는다(screens/detail.md).
     *
     * <p>댓글이 20건 이하면 접든 말든 결과가 같아서 이 회귀는 눈으로 잡히지 않는다.
     */
    @Test
    void deleteComment_keepsExpandedCommentLimit() throws Exception {
        authenticateAs(7L);

        mockMvc.perform(post("/community/15/comments/8/delete").param("comments", "60"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community/15?comments=60"));
    }

    /**
     * 펼친 값이 주소로 나가기 전에 정수로 다시 쓰이는지 확인한다.
     *
     * <p>사용자가 보낸 문자열을 그대로 이으면 리다이렉트 주소에 임의의 값이 실린다.
     * 상한(200)과 기본값(20) 처리도 조회 경로와 같은 규칙을 쓴다.
     */
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

    /**
     * 비로그인 상세에서 "내가 눌렀는지"를 묻지 않는지 확인한다.
     *
     * <p>버튼이 없으므로 물어볼 것이 없고, 물으면 공개 화면인 상세마다 쿼리가 하나 는다.
     * 화면 결과는 어느 쪽이든 같아서 렌더링 테스트로는 드러나지 않는다.
     */
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

    /** 차단된 글을 보는 작성자에게는 좋아요를 열지 않는다. 댓글 폼과 같은 조건이다(4.5). */
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

    /**
     * 추가와 취소가 <b>서로 다른 주소</b>인지 확인한다.
     *
     * <p>하나의 주소를 번갈아 누르는 토글로 바꾸면 재전송·더블클릭이 두 번 실행되어 원래
     * 상태로 되돌아가고, 사용자는 눌렀는데 안 눌린 상태가 된다(DOMAIN.md 6.5). 한 번씩만
     * 눌러 보는 테스트로는 토글도 똑같이 통과하므로, 두 경로가 각자 자기 일만 하는지 본다.
     */
    @Test
    void likeRoutes_areSplitSoNeitherPathTogglesTheOther() throws Exception {
        authenticateAs(7L);

        mockMvc.perform(post("/community/15/likes"));
        verify(communityService).addLike(15L, 7L);
        verify(communityService, never()).removeLike(anyLong(), anyLong());

        mockMvc.perform(post("/community/15/likes/delete"));
        verify(communityService).removeLike(15L, 7L);
    }

    /**
     * 좋아요 뒤에도 펼친 댓글 수가 유지되는지 확인한다.
     *
     * <p>좋아요는 댓글 구역 <b>위에</b> 있다. 댓글을 펼쳐 놓고 좋아요를 눌렀다가 20건으로
     * 접혀 돌아오면 읽던 자리를 잃는다. 댓글 삭제와 같은 처리다(screens/detail.md).
     */
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

    /** 펼친 값은 주소로 나가기 전에 정수로 다시 쓰인다. 댓글 삭제와 같은 규칙이다. */
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
        verify(communityService).getPosts(any(), captor.capture());
        return captor.getValue();
    }

    @Test
    void report_validForm_redirectsToDetailWithMessage() throws Exception {
        authenticateAs(9L);
        when(communityService.getReportablePost(15L, 9L)).thenReturn(publishedPost());

        mockMvc.perform(post("/community/15/reports").param("reason", "광고입니다"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community/15"))
                // 신고는 화면에 흔적을 남기지 않는다. 알리지 않으면 접수됐는지 알 수 없다.
                .andExpect(flash().attribute("successMessage", "신고를 접수했습니다."));

        verify(communityService).reportPost(eq(15L), any(), eq(9L));
    }

    /** 신고자는 요청 파라미터가 아니라 인증 정보에서 온다(AGENTS.md). */
    @Test
    void report_usesAuthenticatedMemberAsReporter() throws Exception {
        authenticateAs(9L);
        when(communityService.getReportablePost(15L, 9L)).thenReturn(publishedPost());

        mockMvc.perform(post("/community/15/reports")
                        .param("reason", "광고입니다")
                        .param("reporterId", "99"))
                .andExpect(status().is3xxRedirection());

        verify(communityService).reportPost(eq(15L), any(), eq(9L));
    }

    /** 댓글과 같다 — 검증 실패는 상세를 다시 그리고, 조회수를 올리지 않는다. */
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

    /**
     * 권한을 검증 실패보다 먼저 본다. 순서가 뒤집히면 남의 삭제된 글 번호로 빈 신고를
     * 보냈을 때 그 글의 상세가 200으로 열린다(조각 2·3에서 같은 실수를 했다).
     */
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

    /** 신고 요청도 펼친 댓글 수를 유지한다. 좋아요와 같은 자리, 같은 이유다. */
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
