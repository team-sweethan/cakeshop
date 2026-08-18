package com.cakeshop.domain.community.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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

/**
 * 좋아요·신고 요청 경로의 Service 호출과 화면 이동을 확인한다.
 *
 * <p>이 Controller 는 게시글 Service 를 아예 주입받지 않는다. 그래서 검증 실패로 상세를 다시
 * 그려도 <b>조회수를 올릴 방법이 구조적으로 없다</b> — 분리 전에는 그것을
 * {@code verify(never()).getPostDetail(...)} 로 확인했지만, 지금은 생성자가 그 자리를 대신한다.
 */
class CommunityReactionControllerTests {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 3, 1, 10, 0);

    private CommunityCommentService communityCommentService;
    private CommunityReactionService communityReactionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        communityCommentService = mock(CommunityCommentService.class);
        communityReactionService = mock(CommunityReactionService.class);

        when(communityCommentService.getComments(anyLong(), any()))
                .thenReturn(new CommentSectionView(
                        List.of(), 0, 0, CommentSectionView.DEFAULT_LIMIT));

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new CommunityReactionController(
                                communityReactionService,
                                // 상세 화면을 목으로 갈면 재렌더링 검사가 빈 Model 을 본다.
                                new CommunityDetailPage(
                                        communityCommentService, communityReactionService)))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addLike_redirectsToDetail() throws Exception {
        authenticateAs(7L);

        mockMvc.perform(post("/community/15/likes"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community/15"));

        verify(communityReactionService).addLike(15L, 7L);
        // 좋아요 추가와 취소 경로는 분리돼 있어 서로를 토글하지 않는다.
        verify(communityReactionService, never()).removeLike(anyLong(), anyLong());
    }

    @Test
    void removeLike_redirectsToDetail() throws Exception {
        authenticateAs(7L);

        mockMvc.perform(post("/community/15/likes/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community/15"));

        verify(communityReactionService).removeLike(15L, 7L);
        verify(communityReactionService, never()).addLike(anyLong(), anyLong());
    }

    @Test
    void report_validForm_redirectsToDetailWithMessage() throws Exception {
        authenticateAs(9L);
        when(communityReactionService.getReportablePost(15L, 9L)).thenReturn(publishedPost());

        mockMvc.perform(post("/community/15/reports")
                        .param("reason", "광고입니다")
                        .param("reporterId", "99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/community/15"))
                .andExpect(flash().attribute("successMessage", "신고를 접수했습니다."));

        verify(communityReactionService).reportPost(eq(15L), any(), eq(9L));
    }

    /** 신고 검증 실패 시 상세를 다시 그린다. */
    @Test
    void report_blankReason_redrawsDetail() throws Exception {
        authenticateAs(9L);
        when(communityReactionService.getReportablePost(15L, 9L)).thenReturn(publishedPost());

        mockMvc.perform(post("/community/15/reports").param("reason", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name("customer/community/detail"))
                .andExpect(model().attributeHasFieldErrors("reportForm", "reason"))
                .andExpect(model().attributeExists("commentSection"));

        verify(communityReactionService, never()).reportPost(anyLong(), any(), anyLong());
    }

    /** 신고 가능 상태를 입력값보다 먼저 확인한다. */
    @Test
    void report_invisiblePost_checksPermissionBeforeValidation() {
        authenticateAs(9L);
        when(communityReactionService.getReportablePost(15L, 9L))
                .thenThrow(new BusinessException(CommunityErrorCode.POST_NOT_FOUND));

        assertThatThrownBy(() ->
                mockMvc.perform(post("/community/15/reports").param("reason", "   ")))
                .hasRootCauseInstanceOf(BusinessException.class);

        verify(communityReactionService, never()).reportPost(anyLong(), any(), anyLong());
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
