package com.cakeshop.domain.community.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.community.dto.view.CommentSectionView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.service.CommunityCommentService;
import com.cakeshop.domain.community.service.CommunityPostService;
import com.cakeshop.domain.community.service.CommunityPostImageService;
import com.cakeshop.domain.community.service.CommunityReactionService;
import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.security.MemberDetails;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 댓글 요청 경로의 Service 호출과 화면 이동을 확인한다. */
class CommunityCommentControllerTests {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 3, 1, 10, 0);

    private CommunityPostService communityPostService;
    private CommunityCommentService communityCommentService;
    private CommunityReactionService communityReactionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        communityPostService = mock(CommunityPostService.class);
        communityCommentService = mock(CommunityCommentService.class);
        communityReactionService = mock(CommunityReactionService.class);

        when(communityCommentService.getComments(anyLong(), any(), any()))
                .thenReturn(new CommentSectionView(
                        List.of(), 0, 0, CommentSectionView.DEFAULT_LIMIT));

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new CommunityCommentController(
                                communityPostService,
                                communityCommentService,
                                // 상세 화면을 목으로 갈면 재렌더링 검사가 빈 Model 을 본다.
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
    void addComment_validForm_redirectsToDetail() throws Exception {
        authenticateAs(7L);
        when(communityPostService.getCommentablePost(15L, 7L)).thenReturn(publishedPost());

        mockMvc.perform(post("/community/15/comments")
                        .param("content", "댓글 본문")
                        .param("memberId", "99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community/15"));

        verify(communityCommentService).addComment(eq(15L), any(), eq(7L));
    }

    /** 댓글 검증 실패 시 조회수 없이 상세를 다시 그린다. */
    @Test
    void addComment_blankContent_redrawsDetailWithoutCountingAView() throws Exception {
        authenticateAs(7L);
        when(communityPostService.getCommentablePost(15L, 7L)).thenReturn(publishedPost());

        mockMvc.perform(post("/community/15/comments").param("content", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/community/detail"))
                .andExpect(model().attributeHasFieldErrors("commentForm", "content"))
                .andExpect(model().attributeExists("commentSection"));

        verify(communityCommentService, never()).addComment(anyLong(), any(), anyLong());
        verify(communityPostService, never()).getPostDetail(anyLong(), any(), anyString());
    }

    /** 댓글 작성 가능 상태를 입력값보다 먼저 확인한다. */
    @Test
    void addComment_invalidForm_checksPostStateBeforeValidation() {
        authenticateAs(7L);
        when(communityPostService.getCommentablePost(15L, 7L))
                .thenThrow(new BusinessException(CommunityErrorCode.POST_NOT_FOUND));

        assertThatThrownBy(() -> mockMvc.perform(
                        post("/community/15/comments").param("content", "   ")))
                .hasRootCauseInstanceOf(BusinessException.class);

        verify(communityCommentService, never()).addComment(anyLong(), any(), anyLong());
    }

    /** 검증 실패는 리다이렉트가 아니라 재렌더링이므로 Service에 전달하는 범위로 확인한다. */
    @Test
    void addComment_invalidForm_keepsExpandedCommentLimit() throws Exception {
        authenticateAs(7L);
        when(communityPostService.getCommentablePost(15L, 7L)).thenReturn(publishedPost());

        mockMvc.perform(post("/community/15/comments")
                        .param("content", "   ")
                        .param("comments", "40"))
                .andExpect(status().isOk());

        verify(communityCommentService).getComments(15L, 40, null);
    }

    /** 답글은 방금 쓴 것이 보여야 하므로 그 묶음을 펼친 채로 돌아간다. */
    @Test
    void addComment_withReplyTo_addsReplyAndReturnsWithThreadExpanded() throws Exception {
        authenticateAs(7L);
        when(communityPostService.getCommentablePost(15L, 7L)).thenReturn(publishedPost());

        mockMvc.perform(post("/community/15/comments")
                        .param("content", "답글 본문")
                        .param("replyTo", "8")
                        .param("comments", "40"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community/15?comments=40&replies=8"));

        verify(communityCommentService).addReply(eq(15L), eq(8L), any(), eq(7L));
        verify(communityCommentService, never()).addComment(anyLong(), any(), anyLong());
    }

    /** 망가진 replyTo 는 뿌리 댓글로 강등하지 않고 거절한다. */
    @Test
    void addComment_malformedReplyTo_isRejected() {
        authenticateAs(7L);

        assertThatThrownBy(() -> mockMvc.perform(post("/community/15/comments")
                        .param("content", "답글 본문")
                        .param("replyTo", "abc")))
                .hasRootCauseInstanceOf(BusinessException.class);

        verify(communityCommentService, never()).addComment(anyLong(), any(), anyLong());
        verify(communityCommentService, never())
                .addReply(anyLong(), anyLong(), any(), anyLong());
    }

    /** 빈 replyTo 도 부재가 아니라 망가진 값이다 — 뿌리 댓글로 강등하지 않고 거절한다. */
    @Test
    void addComment_blankReplyTo_isRejected() {
        authenticateAs(7L);

        assertThatThrownBy(() -> mockMvc.perform(post("/community/15/comments")
                        .param("content", "답글 본문")
                        .param("replyTo", "   ")))
                .hasRootCauseInstanceOf(BusinessException.class);

        verify(communityCommentService, never()).addComment(anyLong(), any(), anyLong());
        verify(communityCommentService, never())
                .addReply(anyLong(), anyLong(), any(), anyLong());
    }

    /** 답글 검증 실패는 실패한 폼이 어느 묶음인지 화면에 알린다. */
    @Test
    void addComment_blankReply_marksFailedThread() throws Exception {
        authenticateAs(7L);
        when(communityPostService.getCommentablePost(15L, 7L)).thenReturn(publishedPost());

        mockMvc.perform(post("/community/15/comments")
                        .param("content", "   ")
                        .param("replyTo", "8")
                        .param("replies", "8"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("failedReplyTo", 8L));

        verify(communityCommentService, never())
                .addReply(anyLong(), anyLong(), any(), anyLong());
    }

    @Test
    void deleteComment_redirectsToDetail() throws Exception {
        authenticateAs(7L);

        mockMvc.perform(post("/community/15/comments/8/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community/15"));

        verify(communityCommentService).deleteComment(15L, 8L, 7L);
    }

    /** 펼친 묶음 안에서 답글을 지워도 그 묶음이 접히지 않는다. */
    @Test
    void deleteComment_keepsExpandedThread() throws Exception {
        authenticateAs(7L);

        mockMvc.perform(post("/community/15/comments/9/delete")
                        .param("replies", "8"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community/15?replies=8"));
    }

    private void authenticateAs(long memberId) {
        MemberDetails principal = new MemberDetails(new MemberAuthenticationView(
                memberId, "author@cakeshop.local", "dummy", "USER", true));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
    }

    private PostDetailView publishedPost() {
        return new PostDetailView(
                15L, 7L, 1L, "질문", "제목", "본문", "글쓴이", false,
                PostStatus.PUBLISHED, null, 10L, 2L, CREATED_AT, CREATED_AT);
    }
}
