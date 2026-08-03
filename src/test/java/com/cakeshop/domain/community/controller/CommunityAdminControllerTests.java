package com.cakeshop.domain.community.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.community.dto.view.AdminPostDetailView;
import com.cakeshop.domain.community.dto.view.AdminPostListView;
import com.cakeshop.domain.community.dto.view.AdminPostSort;
import com.cakeshop.domain.community.dto.view.CommentSectionView;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.service.CommunityAdminService;
import com.cakeshop.domain.community.service.CommunityService;
import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.security.MemberDetails;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 관리자 화면의 파라미터 처리와 조치 경로를 확인한다.
 *
 * <p>접근 제한은 여기서 보지 않는다. Security 설정이 붙지 않은 standaloneSetup이라
 * "관리자만 연다"는 {@code CommunityScreenRenderingTests}가 실제 필터 체인으로 확인한다.
 */
class CommunityAdminControllerTests {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 3, 1, 10, 0);

    private CommunityAdminService communityAdminService;
    private CommunityService communityService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        communityAdminService = mock(CommunityAdminService.class);
        communityService = mock(CommunityService.class);

        when(communityAdminService.getPosts(any(), any(), any(PageRequest.class)))
                .thenReturn(new PageResult<AdminPostListView>(
                        List.of(), new PageRequest(1, 20), 0));
        when(communityAdminService.getPostDetail(anyLong())).thenReturn(publishedPost());
        when(communityAdminService.getReports(anyLong())).thenReturn(List.of());
        when(communityService.getComments(anyLong(), any()))
                .thenReturn(new CommentSectionView(
                        List.of(), 0, 0, CommentSectionView.DEFAULT_LIMIT));

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new CommunityAdminController(communityAdminService, communityService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_statusAndSort_arePassedToService() throws Exception {
        mockMvc.perform(get("/admin/community")
                        .param("status", "BLOCKED")
                        .param("sort", "REPORTS"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/community/list"));

        verify(communityAdminService)
                .getPosts(eq(PostStatus.BLOCKED), eq(AdminPostSort.REPORTS), any());
    }

    /**
     * 모르는 값은 오류가 아니라 기본값이다(DOMAIN.md 6.7).
     *
     * <p>고객 목록과 같은 처리다. 주소가 망가진 것과 권한이 없는 것은 다르고, 관리자에게
     * 오류 페이지를 주면 목록을 다시 찾아 들어가야 한다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"HIDDEN", "정상", "'; DROP TABLE posts; --", ""})
    void list_unknownStatus_fallsBackToEveryStatus(String status) throws Exception {
        mockMvc.perform(get("/admin/community").param("status", status))
                .andExpect(status().isOk());

        verify(communityAdminService).getPosts(isNull(), eq(AdminPostSort.LATEST), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"views", "인기순", "1=1"})
    void list_unknownSort_fallsBackToLatest(String sort) throws Exception {
        mockMvc.perform(get("/admin/community").param("sort", sort))
                .andExpect(status().isOk());

        verify(communityAdminService).getPosts(isNull(), eq(AdminPostSort.LATEST), any());
    }

    @Test
    void detail_rendersAdminDetail() throws Exception {
        mockMvc.perform(get("/admin/community/15"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/community/detail"))
                .andExpect(model().attributeExists("post", "reports", "commentSection"));
    }

    /** 차단한 관리자는 요청 파라미터가 아니라 인증 정보에서 온다(AGENTS.md). */
    @Test
    void block_usesAuthenticatedAdminAsBlocker() throws Exception {
        authenticateAsAdmin(3L);

        mockMvc.perform(post("/admin/community/15/block")
                        .param("reason", "광고성 게시물")
                        // 남의 회원 번호를 실어 보내도 무시되어야 한다.
                        .param("adminId", "99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/community/15"));

        verify(communityAdminService).blockPost(15L, "광고성 게시물", 3L);
    }

    /**
     * 사유 없이 차단하지 않는다(DOMAIN.md 4.3).
     *
     * <p>사유는 작성자에게 그대로 보이는 값이다. 비어 있으면 차단된 글에서 작성자가 왜
     * 막혔는지 알 방법이 없다 — 차단이 처벌이 아니라 교정으로 작동하려면 필요하다.
     */
    @Test
    void block_blankReason_redrawsDetail() throws Exception {
        authenticateAsAdmin(3L);

        mockMvc.perform(post("/admin/community/15/block").param("reason", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/community/detail"))
                .andExpect(model().attributeHasFieldErrors("blockForm", "reason"));

        verify(communityAdminService, never()).blockPost(anyLong(), anyString(), anyLong());
    }

    @Test
    void unblock_redirectsToDetail() throws Exception {
        mockMvc.perform(post("/admin/community/15/unblock"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/community/15"));

        verify(communityAdminService).unblockPost(15L);
    }

    @Test
    void rejectReports_redirectsToDetail() throws Exception {
        mockMvc.perform(post("/admin/community/15/reports/reject"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/community/15"));

        verify(communityAdminService).rejectReports(15L);
    }

    private void authenticateAsAdmin(long memberId) {
        MemberDetails principal = new MemberDetails(new MemberAuthenticationView(
                memberId, "admin@cakeshop.local", "dummy", "ADMIN", true));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
    }

    private AdminPostDetailView publishedPost() {
        return new AdminPostDetailView(
                15L, 7L, "질문", "제목", "본문", "글쓴이", false,
                PostStatus.PUBLISHED, null, null, null, 10L, 2L, CREATED_AT, CREATED_AT);
    }
}
