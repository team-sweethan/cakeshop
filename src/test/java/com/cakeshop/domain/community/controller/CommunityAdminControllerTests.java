package com.cakeshop.domain.community.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.cakeshop.domain.community.entity.ReportStatus;
import com.cakeshop.domain.community.service.CommunityAdminService;
import com.cakeshop.domain.community.service.CommunityCommentService;
import com.cakeshop.domain.community.service.CommunityPostImageService;
import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.security.MemberDetails;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 관리자 화면의 파라미터와 조치 경로를 검증한다. */
class CommunityAdminControllerTests {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 3, 1, 10, 0);

    private CommunityAdminService communityAdminService;
    private CommunityCommentService communityCommentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        communityAdminService = mock(CommunityAdminService.class);
        communityCommentService = mock(CommunityCommentService.class);

        when(communityAdminService.getPosts(any(), any(), any(PageRequest.class)))
                .thenReturn(new PageResult<AdminPostListView>(
                        List.of(), new PageRequest(1, 20), 0));
        when(communityAdminService.getPostDetail(anyLong())).thenReturn(publishedPost());
        when(communityAdminService.getReports(anyLong())).thenReturn(List.of());
        when(communityCommentService.getComments(anyLong(), any(), any()))
                .thenReturn(new CommentSectionView(
                        List.of(), 0, 0, CommentSectionView.DEFAULT_LIMIT));

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new CommunityAdminController(communityAdminService, communityCommentService, mock(CommunityPostImageService.class)))
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
                .andExpect(view().name("admin/community/list"))
                .andExpect(model().attributeExists("statusOptions", "sortOptions"));

        verify(communityAdminService)
                .getPosts(eq(PostStatus.BLOCKED), eq(AdminPostSort.REPORTS), any());
    }

    @Test
    void list_page_isPassedAsPageRequest() throws Exception {
        mockMvc.perform(get("/admin/community").param("page", "3"))
                .andExpect(status().isOk());

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(communityAdminService)
                .getPosts(isNull(), eq(AdminPostSort.LATEST), captor.capture());
        assertThat(captor.getValue().getPage()).isEqualTo(3);
        assertThat(captor.getValue().getSize()).isEqualTo(PageRequest.DEFAULT_SIZE);
    }

    /** 알 수 없는 상태는 전체 상태 조회로 처리한다. */
    @ParameterizedTest
    @ValueSource(strings = {"HIDDEN", ""})
    void list_unknownStatus_fallsBackToEveryStatus(String status) throws Exception {
        mockMvc.perform(get("/admin/community").param("status", status))
                .andExpect(status().isOk());

        verify(communityAdminService).getPosts(isNull(), eq(AdminPostSort.LATEST), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"views", "1=1"})
    void list_unknownSort_fallsBackToLatest(String sort) throws Exception {
        mockMvc.perform(get("/admin/community").param("sort", sort))
                .andExpect(status().isOk());

        verify(communityAdminService).getPosts(isNull(), eq(AdminPostSort.LATEST), any());
    }

    @Test
    void detail_rendersAdminDetail() throws Exception {
        CommentSectionView comments = new CommentSectionView(List.of(), 2, 3, 40);
        when(communityAdminService.getReports(15L)).thenReturn(List.of(
                new com.cakeshop.domain.community.dto.view.ReportView(
                        1L, "신고자", false, "사유", ReportStatus.PENDING, CREATED_AT),
                new com.cakeshop.domain.community.dto.view.ReportView(
                        2L, "신고자", false, "사유", ReportStatus.RESOLVED, CREATED_AT)));
        when(communityCommentService.getComments(15L, 40, null)).thenReturn(comments);

        mockMvc.perform(get("/admin/community/15").param("comments", "40"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/community/detail"))
                .andExpect(model().attribute("commentSection", comments))
                .andExpect(model().attribute("pendingReportCount", 1L));
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

    /** 빈 사유로 게시글을 차단하지 않는다. */
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
    void block_reasonOver500Characters_doesNotCallService() throws Exception {
        authenticateAsAdmin(3L);

        mockMvc.perform(post("/admin/community/15/block")
                        .param("reason", "가".repeat(501)))
                .andExpect(status().isOk())
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
