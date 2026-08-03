package com.cakeshop.domain.community.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
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

import com.cakeshop.domain.community.dto.view.PostCategoryView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListView;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.service.CommunityService;
import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.security.MemberDetails;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        when(communityService.getPostDetail(15L, null)).thenReturn(publishedPost());

        mockMvc.perform(get("/community/15"))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/community/detail"))
                .andExpect(model().attributeExists("post"));

        verify(communityService).getPostDetail(15L, null);
    }

    /** 소유권 판단 기준은 요청 파라미터가 아니라 인증 정보다(AGENTS.md). */
    @Test
    void detail_authenticatedViewer_passesAuthenticatedMemberId() throws Exception {
        MemberDetails principal = new MemberDetails(new MemberAuthenticationView(
                7L, "author@cakeshop.local", "dummy", "USER", true));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));

        when(communityService.getPostDetail(15L, 7L)).thenReturn(publishedPost());

        mockMvc.perform(get("/community/15"))
                .andExpect(status().isOk());

        verify(communityService).getPostDetail(15L, 7L);
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

    private void authenticateAs(long memberId) {
        MemberDetails principal = new MemberDetails(new MemberAuthenticationView(
                memberId, "author@cakeshop.local", "dummy", "USER", true));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
    }

    private PageRequest capturedPageRequest() {
        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(communityService).getPosts(any(), captor.capture());
        return captor.getValue();
    }

    private PostDetailView publishedPost() {
        return new PostDetailView(
                15L, 7L, 1L, "질문", "제목", "본문", "글쓴이", false,
                PostStatus.PUBLISHED, null, 10L, 2L, CREATED_AT, CREATED_AT);
    }
}
