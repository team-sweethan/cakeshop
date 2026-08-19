package com.cakeshop.domain.community.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.community.dto.view.CommentSectionView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.service.CommunityCommentService;
import com.cakeshop.domain.community.service.CommunityPostService;
import com.cakeshop.domain.community.service.CommunityPostImageService;
import com.cakeshop.domain.community.service.CommunityReactionService;
import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.global.security.MemberDetails;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 상세 화면으로 <b>되돌아가는 모든 경로</b>가 같은 규칙을 쓰는지 본다.
 *
 * <p>이 검사가 따로 있는 이유는 그 경로들이 <b>두 Controller 에 나뉘어 있기</b> 때문이다. 한쪽
 * Controller 의 검사로 쪼개면 "모든 경로가 그렇다"는 문장이 사라지고, 새 경로가 늘 때 빠진 것을
 * 알아볼 자리가 없어진다. 그래서 둘을 함께 등록한다.
 */
class CommunityDetailRedirectTests {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 3, 1, 10, 0);

    private CommunityReactionService communityReactionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CommunityPostService communityPostService = mock(CommunityPostService.class);
        CommunityCommentService communityCommentService = mock(CommunityCommentService.class);
        communityReactionService = mock(CommunityReactionService.class);

        when(communityCommentService.getComments(anyLong(), any(), any()))
                .thenReturn(new CommentSectionView(
                        List.of(), 0, 0, CommentSectionView.DEFAULT_LIMIT));

        CommunityDetailPage detailPage =
                new CommunityDetailPage(communityCommentService, communityReactionService, mock(CommunityPostImageService.class));

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new CommunityCommentController(
                                communityPostService, communityCommentService, detailPage),
                        new CommunityReactionController(communityReactionService, detailPage))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 상세로 돌아가는 모든 경로가 펼친 댓글 범위를 유지한다. 신고 경로만 사유가 필요하고
     * 나머지 경로는 넘긴 사유 파라미터를 무시한다.
     *
     * <p><b>착지점도 함께 고정한다.</b> 댓글 구역에서 한 일은 {@code #comment-{id}} 로 그 댓글
     * 자리에 돌아가고, 게시글 반응(좋아요·신고)은 앵커 없이 문서 맨 위다. 기대 주소를 경로마다
     * 적어 두는 이유는 <b>둘이 갈라져 있다는 사실 자체가 결정</b>이기 때문이다 — 한 열로 묶으면
     * 나중에 어느 쪽이 규칙이고 어느 쪽이 빠뜨린 것인지 구분할 자리가 없어진다.
     */
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "/community/15/comments/8/delete, /community/15?comments=60#comment-8",
            "/community/15/likes, /community/15?comments=60",
            "/community/15/likes/delete, /community/15?comments=60",
            "/community/15/reports, /community/15?comments=60"
    })
    void detailRedirect_expandedCommentLimit_isKeptOnEveryPath(String path, String expectedUrl)
            throws Exception {
        authenticateAs(7L);
        when(communityReactionService.getReportablePost(15L, 7L)).thenReturn(publishedPost());

        mockMvc.perform(post(path)
                        .param("comments", "60")
                        .param("reason", "광고입니다"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(expectedUrl));
    }

    /**
     * 리다이렉트 주소의 댓글 조회 수를 정수 범위로 정규화한다. 정규화는 경로마다 같은 코드가
     * 하므로 대표 경로 하나로 확인하고, 경로별 적용 여부는 위 표가 확인한다.
     *
     * <p>대표 경로가 댓글 삭제라 앵커가 함께 붙는다. <b>앵커는 언제나 맨 끝이다</b> — 파라미터
     * 뒤에 와야 브라우저가 앵커로 읽는다.
     */
    @ParameterizedTest(name = "comments={0} -> {1}")
    @CsvSource({
            "abc, /community/15#comment-8",
            "-1, /community/15#comment-8",
            "20, /community/15#comment-8",
            "99999999, /community/15?comments=200#comment-8",
            "'40 OR 1=1', /community/15#comment-8"
    })
    void detailRedirect_commentLimit_isRewrittenAsInteger(String requested, String expectedUrl)
            throws Exception {
        authenticateAs(7L);

        mockMvc.perform(post("/community/15/comments/8/delete").param("comments", requested))
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

    private PostDetailView publishedPost() {
        return new PostDetailView(
                15L, 7L, 1L, "질문", "제목", "본문", "글쓴이", false,
                PostStatus.PUBLISHED, null, 10L, 2L, CREATED_AT, CREATED_AT);
    }
}
