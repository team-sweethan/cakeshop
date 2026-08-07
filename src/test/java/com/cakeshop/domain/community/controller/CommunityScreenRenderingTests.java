package com.cakeshop.domain.community.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.cakeshop.domain.community.dto.view.CommentSectionView;
import com.cakeshop.domain.community.entity.CommentStatus;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.config.MariaDbIntegrationTest;
import com.cakeshop.global.security.MemberDetails;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/** 커뮤니티 화면을 실제 Thymeleaf로 렌더링한다. */
@SpringBootTest
@MariaDbIntegrationTest
@Transactional
class CommunityScreenRenderingTests {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 3, 1, 10, 0);
    private static final int PAGE_SIZE = PageRequest.DEFAULT_SIZE;

    /** 인기글 화면 검사용 확정일. */
    private static final LocalDate RANKING_DATE = LocalDate.of(2099, 1, 2);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private long categoryId;
    private long emptyCategoryId;
    private long memberId;
    private long withdrawnMemberId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        String suffix = Long.toString(System.nanoTime());

        categoryId = insertCategory("SCREEN_" + suffix, "화면테스트");
        emptyCategoryId = insertCategory("SCREEN_EMPTY_" + suffix, "빈카테고리");
        memberId = insertMember("screen-" + suffix + "@cakeshop.local", "케이크덕후", "ACTIVE");
        withdrawnMemberId =
                insertMember("gone-" + suffix + "@cakeshop.local", "떠난회원", "WITHDRAWN");
    }

    @Test
    void communityList_rendersPostRow() throws Exception {
        long postId = insertPost(memberId, "딸기 케이크 후기", "본문", PostStatus.PUBLISHED);
        insertComment(postId);

        mockMvc.perform(get("/community").param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("커뮤니티")))
                .andExpect(content().string(containsString("딸기 케이크 후기")))
                .andExpect(content().string(containsString("케이크덕후")))
                .andExpect(content().string(containsString("댓글 1")))
                // 좋아요와 조회수 렌더링을 확인한다.
                .andExpect(content().string(containsString("좋아요 0")))
                .andExpect(content().string(containsString("조회 0")))
                .andExpect(content().string(containsString("화면테스트")));
    }

    @Test
    void communityList_withoutPosts_rendersEmptyMessage() throws Exception {
        mockMvc.perform(get("/community").param("categoryId", String.valueOf(emptyCategoryId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("아직 등록된 글이 없습니다.")));
    }

    @Test
    void communityList_withdrawnAuthor_showsPlaceholderName() throws Exception {
        insertPost(withdrawnMemberId, "탈퇴 회원 글", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community").param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("탈퇴한 회원")))
                .andExpect(content().string(not(containsString("떠난회원"))));
    }

    /** 여러 페이지이면 페이지 이동을 렌더링한다. */
    @Test
    void communityList_multiplePages_rendersPageNavigation() throws Exception {
        for (int i = 0; i < PAGE_SIZE + 1; i++) {
            insertPost(memberId, "글 " + i, "본문", PostStatus.PUBLISHED);
        }

        mockMvc.perform(get("/community").param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("페이지 이동")))
                .andExpect(content().string(containsString("이전")))
                .andExpect(content().string(containsString("다음")))
                // 페이지 링크에 필터를 유지한다.
                .andExpect(content().string(containsString(
                        "/community?categoryId=" + categoryId + "&amp;sort=LATEST&amp;page=2")));
    }

    /** 정렬 링크에 카테고리 필터를 유지한다. */
    @Test
    void communityList_sortLinks_keepCategoryFilter() throws Exception {
        mockMvc.perform(get("/community").param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("최신순")))
                .andExpect(content().string(containsString("조회수순")))
                .andExpect(content().string(containsString(
                        "/community?categoryId=" + categoryId + "&amp;sort=VIEWS")));
    }

    /** 카테고리 링크에 정렬값을 유지한다. */
    @Test
    void communityList_categoryLinks_keepSortOption() throws Exception {
        mockMvc.perform(get("/community").param("sort", "VIEWS"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "/community?categoryId=" + categoryId + "&amp;sort=VIEWS")));
    }

    /** 확정된 인기글과 기준일을 렌더링한다. */
    @Test
    void communityList_withConfirmedRanking_rendersPopularSection() throws Exception {
        long postId = insertPost(memberId, "이번 주 인기 케이크", "본문", PostStatus.PUBLISHED);
        insertRanking(1, postId);
        insertBatchRun(1);

        mockMvc.perform(get("/community"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h2>인기글</h2>")))
                .andExpect(content().string(containsString("이번 주 인기 케이크")))
                .andExpect(content().string(containsString("2099.01.02")))
                .andExpect(content().string(containsString("기준")));
    }

    /** 노출할 인기글이 없으면 영역을 숨긴다. */
    @Test
    void communityList_everyRankedPostHidden_omitsPopularSection() throws Exception {
        long blockedId = insertPost(memberId, "차단된 인기글", "본문", PostStatus.BLOCKED);
        insertRanking(1, blockedId);
        insertBatchRun(1);

        mockMvc.perform(get("/community"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<h2>인기글</h2>"))))
                .andExpect(content().string(not(containsString("차단된 인기글"))));
    }

    /** 삭제 성공 메시지를 렌더링한다. */
    @Test
    void communityList_afterDelete_showsSuccessMessage() throws Exception {
        mockMvc.perform(get("/community").flashAttr("successMessage", "게시글을 삭제했습니다."))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("게시글을 삭제했습니다.")));
    }

    /** 한 페이지이면 페이지 이동을 숨긴다. */
    @Test
    void communityList_singlePage_omitsPageNavigation() throws Exception {
        insertPost(memberId, "글 하나", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community").param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("페이지 이동"))));
    }

    /** 작성 화면에 활성 카테고리와 실제 전송 폼을 렌더링한다. */
    @Test
    void communityCreateForm_rendersActiveCategoriesAndPostsToServer() throws Exception {
        insertInactiveCategory();

        mockMvc.perform(get("/community/new").with(authentication(authorOf(memberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("글쓰기")))
                .andExpect(content().string(containsString("선택하세요")))
                .andExpect(content().string(containsString("등록")))
                .andExpect(content().string(containsString("화면테스트")))
                .andExpect(content().string(not(containsString("비활성분류"))))
                .andExpect(content().string(not(containsString("mock-notice"))))
                .andExpect(content().string(not(containsString("data-mock-form"))))
                // 범위 밖인 이미지 입력을 숨긴다.
                .andExpect(content().string(not(containsString("사진 첨부"))));
    }

    /** 수정 화면에 기존 값을 채운다. */
    @Test
    void communityEditForm_author_rendersExistingValues() throws Exception {
        long postId = insertPost(memberId, "고칠 제목", "고칠 본문", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community/" + postId + "/edit")
                        .with(authentication(authorOf(memberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("글 수정")))
                .andExpect(content().string(containsString("고칠 제목")))
                .andExpect(content().string(containsString("고칠 본문")));
    }

    /** 수정과 삭제 버튼은 작성자에게만 보여 준다. */
    @Test
    void communityDetail_author_showsEditAndDeleteButtons() throws Exception {
        long postId = insertPost(memberId, "내 글", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community/" + postId).with(authentication(authorOf(memberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("수정")))
                .andExpect(content().string(containsString("삭제")))
                .andExpect(content().string(containsString("/community/" + postId + "/edit")));
    }

    @Test
    void communityDetail_otherMember_hidesEditAndDeleteButtons() throws Exception {
        long postId = insertPost(memberId, "남의 글", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community/" + postId)
                        .with(authentication(authorOf(withdrawnMemberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        not(containsString("/community/" + postId + "/edit"))));
    }

    /** 차단 글 작성자에게 사유만 보여 주고 조작 버튼은 숨긴다. */
    @Test
    void communityDetail_blockedPostAuthor_showsReasonAndHidesActions() throws Exception {
        long postId = insertPost(memberId, "차단된 글", "본문", PostStatus.BLOCKED);
        insertComment(postId, memberId, "내 댓글", CommentStatus.PUBLISHED, BASE_TIME);
        jdbcTemplate.update(
                "UPDATE posts SET blocked_reason = ? WHERE id = ?", "광고성 게시물", postId);

        mockMvc.perform(get("/community/" + postId).with(authentication(authorOf(memberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("관리자가 차단한 게시글입니다.")))
                .andExpect(content().string(containsString("광고성 게시물")))
                .andExpect(content().string(
                        containsString("이 글은 다른 회원에게 보이지 않습니다.")))
                .andExpect(content().string(
                        not(containsString("/community/" + postId + "/edit"))))
                .andExpect(content().string(not(containsString("댓글 등록"))))
                .andExpect(content().string(
                        not(containsString("로그인하면 댓글을 쓸 수 있습니다."))))
                .andExpect(content().string(containsString("내 댓글")))
                .andExpect(content().string(not(containsString("댓글 삭제"))))
                .andExpect(content().string(containsString("좋아요 0")))
                .andExpect(content().string(not(containsString("/likes"))));
    }

    /** 비로그인은 작성 화면에서 로그인으로 이동한다. */
    @Test
    void communityCreateForm_anonymous_isSentToLogin() throws Exception {
        mockMvc.perform(get("/community/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void communityDetail_rendersContent() throws Exception {
        long postId = insertPost(memberId, "제목입니다", "첫 줄\n둘째 줄", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community/" + postId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("제목입니다")))
                .andExpect(content().string(containsString("첫 줄")))
                // 상세 진입으로 반영된 조회수를 확인한다.
                .andExpect(content().string(containsString("조회 1")))
                // 본문 줄바꿈을 유지한다.
                .andExpect(content().string(containsString("white-space:pre-wrap")));
    }

    /** 게시글 HTML을 이스케이프한다. */
    @Test
    void communityDetail_htmlInContent_isEscaped() throws Exception {
        String attack = "<script>alert('xss')</script>";
        long postId = insertPost(memberId, "제목", attack, PostStatus.PUBLISHED);

        mockMvc.perform(get("/community/" + postId))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(attack))))
                .andExpect(content().string(containsString("&lt;script&gt;")));
    }

    /** 수정된 글에 수정 표시를 렌더링한다. */
    @Test
    void communityDetail_editedPost_showsEditedMark() throws Exception {
        long postId = insertPost(memberId, "고친 글", "본문", PostStatus.PUBLISHED);
        jdbcTemplate.update(
                "UPDATE posts SET updated_at = ? WHERE id = ?", BASE_TIME.plusHours(1), postId);

        mockMvc.perform(get("/community/" + postId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("(수정됨)")));
    }

    @Test
    void communityDetail_titleWithHtml_isEscaped() throws Exception {
        long postId = insertPost(
                memberId, "<img src=x onerror=alert(1)>", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community/" + postId))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<img src=x"))));
    }

    @Test
    void communityDetail_deletedPost_returnsNotFound() throws Exception {
        long postId = insertPost(memberId, "삭제된 글", "본문", PostStatus.DELETED);

        mockMvc.perform(get("/community/" + postId))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("삭제된 글"))));
    }

    @Test
    void communityDetail_blockedPost_anonymousViewer_returnsNotFound() throws Exception {
        long postId = insertPost(memberId, "차단된 글", "본문", PostStatus.BLOCKED);
        jdbcTemplate.update(
                "UPDATE posts SET blocked_reason = ? WHERE id = ?", "광고성 게시물", postId);

        mockMvc.perform(get("/community/" + postId))
                .andExpect(status().isNotFound())
                // 비작성자에게 차단 사유를 숨긴다.
                .andExpect(content().string(not(containsString("광고성 게시물"))));
    }

    @Test
    void communityDetail_blockedPost_authenticatedOtherMember_returnsNotFound() throws Exception {
        long postId = insertPost(memberId, "차단된 글", "본문", PostStatus.BLOCKED);
        long otherId = insertMember(
                "other-" + System.nanoTime() + "@cakeshop.local", "다른회원", "ACTIVE");
        jdbcTemplate.update(
                "UPDATE posts SET blocked_reason = ? WHERE id = ?", "광고성 게시물", postId);

        mockMvc.perform(get("/community/" + postId).with(authentication(authorOf(otherId))))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("광고성 게시물"))));
    }

    @Test
    void communityDetail_unknownPost_returnsNotFound() throws Exception {
        mockMvc.perform(get("/community/99999999"))
                .andExpect(status().isNotFound());
    }

    /** 댓글 순서와 표시 개수를 렌더링한다. */
    @Test
    void communityDetail_rendersComments() throws Exception {
        long postId = insertPost(memberId, "댓글 있는 글", "본문", PostStatus.PUBLISHED);
        insertComment(postId, memberId, "먼저 쓴 댓글", CommentStatus.PUBLISHED, BASE_TIME);
        insertComment(postId, memberId, "나중에 쓴 댓글",
                CommentStatus.PUBLISHED, BASE_TIME.plusMinutes(1));
        insertComment(postId, memberId, "지워진 댓글",
                CommentStatus.DELETED, BASE_TIME.plusMinutes(2));

        String html = mockMvc.perform(get("/community/" + postId))
                .andExpect(status().isOk())
                // 삭제 댓글은 표시 개수에서 제외한다.
                .andExpect(content().string(containsString("댓글 2")))
                .andExpect(content().string(containsString("먼저 쓴 댓글")))
                .andExpect(content().string(containsString("나중에 쓴 댓글")))
                .andReturn().getResponse().getContentAsString();

        assertThat(html.indexOf("먼저 쓴 댓글")).isLessThan(html.indexOf("나중에 쓴 댓글"));
    }

    /** 삭제 댓글은 본문과 작성자 없이 자리만 남긴다. */
    @Test
    void communityDetail_deletedComment_showsPlaceholderWithoutContent() throws Exception {
        long postId = insertPost(memberId, "글", "본문", PostStatus.PUBLISHED);
        long commenterId = insertMember(
                "commenter-" + System.nanoTime() + "@cakeshop.local", "댓글쓴사람", "ACTIVE");
        insertComment(postId, commenterId, "지워진 본문", CommentStatus.DELETED, BASE_TIME);

        mockMvc.perform(get("/community/" + postId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("삭제된 댓글입니다.")))
                .andExpect(content().string(not(containsString("지워진 본문"))))
                // 삭제 댓글 작성자를 숨긴다.
                .andExpect(content().string(not(containsString("댓글쓴사람"))));
    }

    @Test
    void communityDetail_withoutComments_showsEmptyMessage() throws Exception {
        long postId = insertPost(memberId, "댓글 없는 글", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community/" + postId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("아직 댓글이 없습니다.")));
    }

    /** 댓글 HTML을 이스케이프한다. */
    @Test
    void communityDetail_htmlInComment_isEscaped() throws Exception {
        String attack = "<script>alert('comment')</script>";
        long postId = insertPost(memberId, "글", "본문", PostStatus.PUBLISHED);
        insertComment(postId, memberId, attack, CommentStatus.PUBLISHED, BASE_TIME);

        mockMvc.perform(get("/community/" + postId))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(attack))))
                .andExpect(content().string(containsString("&lt;script&gt;")));
    }

    @Test
    void communityDetail_authenticated_showsCommentForm() throws Exception {
        long postId = insertPost(memberId, "글", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community/" + postId)
                        .with(authentication(authorOf(withdrawnMemberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("댓글 등록")))
                .andExpect(content().string(
                        containsString("/community/" + postId + "/comments")));
    }

    /** 비로그인에게 댓글 폼 대신 로그인 안내를 보여 준다. */
    @Test
    void communityDetail_anonymous_showsLoginPromptInsteadOfForm() throws Exception {
        long postId = insertPost(memberId, "글", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community/" + postId))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("로그인하면 댓글을 쓸 수 있습니다.")))
                .andExpect(content().string(not(containsString("댓글 등록"))));
    }

    /** 자신의 댓글에만 삭제 버튼을 보여 준다. */
    @Test
    void communityDetail_ownComment_showsDeleteButton() throws Exception {
        long postId = insertPost(memberId, "글", "본문", PostStatus.PUBLISHED);
        insertComment(postId, memberId, "내 댓글", CommentStatus.PUBLISHED, BASE_TIME);

        mockMvc.perform(get("/community/" + postId).with(authentication(authorOf(memberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("댓글 삭제")));
    }

    /** 좋아요 전에는 추가 버튼을 보여 준다. */
    @Test
    void communityDetail_notLikedYet_showsLikeButton() throws Exception {
        long postId = insertPost(memberId, "글", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community/" + postId).with(authentication(authorOf(memberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("좋아요")))
                .andExpect(content().string(containsString("/likes")))
                .andExpect(content().string(not(containsString("좋아요 취소"))));
    }

    /** 좋아요 후에는 취소 버튼을 보여 준다. */
    @Test
    void communityDetail_alreadyLiked_showsCancelButton() throws Exception {
        long postId = insertPost(memberId, "글", "본문", PostStatus.PUBLISHED);
        insertLike(postId, memberId);

        mockMvc.perform(get("/community/" + postId).with(authentication(authorOf(memberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("좋아요 취소")))
                .andExpect(content().string(containsString("/likes/delete")));
    }

    /** 다른 회원의 좋아요는 내 버튼 상태를 바꾸지 않는다. */
    @Test
    void communityDetail_likedByAnotherMember_stillShowsLikeButtonToMe() throws Exception {
        long postId = insertPost(memberId, "글", "본문", PostStatus.PUBLISHED);
        insertLike(postId, memberId);

        mockMvc.perform(get("/community/" + postId)
                        .with(authentication(authorOf(withdrawnMemberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("좋아요 취소"))));
    }

    /** 비로그인에게는 좋아요 수만 보여 준다. */
    @Test
    void communityDetail_anonymous_showsLikeCountWithoutButton() throws Exception {
        long postId = insertPost(memberId, "글", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community/" + postId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("좋아요 0")))
                .andExpect(content().string(not(containsString("/likes"))));
    }

    @Test
    void communityDetail_otherMembersComment_hidesDeleteButton() throws Exception {
        long postId = insertPost(memberId, "글", "본문", PostStatus.PUBLISHED);
        insertComment(postId, memberId, "남의 댓글", CommentStatus.PUBLISHED, BASE_TIME);

        mockMvc.perform(get("/community/" + postId)
                        .with(authentication(authorOf(withdrawnMemberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("댓글 삭제"))));
    }

    /** 댓글 더 보기로 과거 댓글을 펼친다. */
    @Test
    void communityDetail_manyComments_showsLoadMoreForOlderComments() throws Exception {
        long postId = insertPost(memberId, "댓글 많은 글", "본문", PostStatus.PUBLISHED);

        for (int i = 0; i < CommentSectionView.DEFAULT_LIMIT + 1; i++) {
            insertComment(postId, memberId, "댓글 " + i,
                    CommentStatus.PUBLISHED, BASE_TIME.plusMinutes(i));
        }

        int nextLimit = CommentSectionView.DEFAULT_LIMIT + CommentSectionView.STEP;

        mockMvc.perform(get("/community/" + postId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이전 댓글 더 보기")))
                .andExpect(content().string(containsString("남은 댓글 1")))
                .andExpect(content().string(containsString(
                        "/community/" + postId + "?comments=" + nextLimit)))
                // 최초 화면에는 최근 댓글을 남긴다.
                .andExpect(content().string(not(containsString(">댓글 0<"))))
                .andExpect(content().string(containsString(
                        "댓글 " + CommentSectionView.DEFAULT_LIMIT)));

        // 더 보기 후 과거 댓글을 보여 준다.
        mockMvc.perform(get("/community/" + postId).param("comments", String.valueOf(nextLimit)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">댓글 0<")))
                .andExpect(content().string(not(containsString("이전 댓글 더 보기"))));
    }

    /** 댓글 상한을 넘으면 남은 댓글을 안내한다. */
    @Test
    void communityDetail_beyondMaxComments_saysSoInsteadOfHidingSilently() throws Exception {
        long postId = insertPost(memberId, "댓글 아주 많은 글", "본문", PostStatus.PUBLISHED);

        insertComments(postId, CommentSectionView.MAX_LIMIT + 1);

        mockMvc.perform(get("/community/" + postId)
                        .param("comments", String.valueOf(CommentSectionView.MAX_LIMIT)))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("오래된 댓글 일부는 표시하지 않습니다.")))
                .andExpect(content().string(not(containsString("이전 댓글 더 보기"))));
    }

    /** 로그인 회원에게 다른 회원 글의 신고 폼을 보여 준다. */
    @Test
    void communityDetail_otherMember_rendersReportForm() throws Exception {
        long postId = insertPost(memberId, "신고 대상 글", "본문", PostStatus.PUBLISHED);
        long otherId = insertMember(
                "reporter-" + System.nanoTime() + "@cakeshop.local", "신고자", "ACTIVE");

        mockMvc.perform(get("/community/" + postId).with(authentication(authorOf(otherId))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이 게시글 신고")))
                .andExpect(content().string(containsString("접수된 신고는 취소할 수 없습니다.")));
    }

    /** 자신의 글에는 신고 폼을 숨긴다. */
    @Test
    void communityDetail_author_hidesReportForm() throws Exception {
        long postId = insertPost(memberId, "내 글", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community/" + postId).with(authentication(authorOf(memberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("이 게시글 신고"))));
    }

    /** 신고한 회원에게는 접수 안내를 보여 준다. */
    @Test
    void communityDetail_alreadyReported_showsNoticeInsteadOfForm() throws Exception {
        long postId = insertPost(memberId, "이미 신고한 글", "본문", PostStatus.PUBLISHED);
        long reporterId = insertMember(
                "reported-" + System.nanoTime() + "@cakeshop.local", "신고자", "ACTIVE");
        insertReport(postId, reporterId, "PENDING");

        mockMvc.perform(get("/community/" + postId).with(authentication(authorOf(reporterId))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이미 신고한 게시글입니다.")))
                .andExpect(content().string(not(containsString("이 게시글 신고"))));
    }

    /** 관리자 목록에 실제 게시글과 상태를 렌더링한다. */
    @Test
    void communityAdminList_rendersForAdmin() throws Exception {
        insertPost(memberId, "관리자 목록에 보일 글", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(get("/admin/community").with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("커뮤니티 관리")))
                .andExpect(content().string(containsString("관리자 목록에 보일 글")))
                .andExpect(content().string(containsString("노출 중")))
                .andExpect(content().string(containsString("신고 많은 순")))
                .andExpect(content().string(
                        containsString("차단된 게시글은 고객 화면에서 열람이 차단됩니다.")))
                // 사용하지 않는 상태 문구를 숨긴다.
                .andExpect(content().string(not(containsString("mock-notice"))))
                .andExpect(content().string(not(containsString("작성자 검색"))));
    }

    /** 관리자 빈 목록 안내를 렌더링한다. */
    @Test
    void communityAdminList_withoutPosts_rendersEmptyMessage() throws Exception {
        mockMvc.perform(get("/admin/community")
                        .param("status", "DELETED")
                        .with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("조건에 맞는 게시글이 없습니다.")));
    }

    @Test
    void communityAdminDetail_rendersForAdmin() throws Exception {
        long postId = insertPost(memberId, "관리자 상세 글", "본문입니다", PostStatus.PUBLISHED);
        long reporterId = insertMember(
                "admin-report-" + System.nanoTime() + "@cakeshop.local", "신고자", "ACTIVE");
        insertReport(postId, reporterId, "PENDING");

        mockMvc.perform(get("/admin/community/" + postId).with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("게시글 정보")))
                .andExpect(content().string(containsString("모더레이션")))
                .andExpect(content().string(containsString("신고 내역")))
                .andExpect(content().string(containsString("차단 사유")))
                .andExpect(content().string(containsString("미처리")))
                .andExpect(content().string(containsString("신고 기각")))
                // 지원하지 않는 조치를 숨긴다.
                .andExpect(content().string(not(containsString("게시글 영구 삭제"))))
                .andExpect(content().string(not(containsString("mock-notice"))));
    }

    /** 관리자 상세에 차단 기록과 해제 버튼을 보여 준다. */
    @Test
    void communityAdminDetail_blockedPost_showsBlockRecordAndUnblock() throws Exception {
        long postId = insertPost(memberId, "차단된 글", "차단된 본문", PostStatus.BLOCKED);
        // 차단 관리자 데이터를 준비한다.
        long adminId = insertMember(
                "blocker-" + System.nanoTime() + "@cakeshop.local", "차단관리자", "ACTIVE");
        jdbcTemplate.update(
                """
                UPDATE posts
                SET blocked_at = ?, blocked_reason = ?, blocked_by = ?
                WHERE id = ?
                """,
                BASE_TIME, "광고성 게시물", adminId, postId);

        mockMvc.perform(get("/admin/community/" + postId).with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("차단됨")))
                .andExpect(content().string(containsString("광고성 게시물")))
                .andExpect(content().string(containsString("차단 해제")))
                // 관리자에게 차단 글 본문을 보여 준다.
                .andExpect(content().string(containsString("차단된 본문")))
                // 차단된 글에는 재차단 버튼을 숨긴다.
                .andExpect(content().string(not(containsString("차단하기"))));
    }

    /** 삭제 글에는 관리자 조치를 숨긴다. */
    @Test
    void communityAdminDetail_deletedPost_hidesModerationActions() throws Exception {
        long postId = insertPost(memberId, "지워진 글", "본문", PostStatus.DELETED);
        // 삭제 전 접수된 신고를 준비한다.
        long reporterId = insertMember(
                "deleted-report-" + System.nanoTime() + "@cakeshop.local", "신고자", "ACTIVE");
        insertReport(postId, reporterId, "PENDING");

        mockMvc.perform(get("/admin/community/" + postId).with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("삭제됨")))
                .andExpect(content().string(
                        containsString("작성자가 삭제한 게시글이라 조치할 수 없습니다.")))
                .andExpect(content().string(not(containsString("차단하기"))))
                .andExpect(content().string(not(containsString("차단 해제"))))
                // 삭제 글에는 신고 기각 버튼도 숨긴다.
                .andExpect(content().string(not(containsString("신고 기각"))));
    }

    /** 삭제 글의 신고 기각 요청도 거절한다. */
    @Test
    void communityAdminRejectReports_deletedPost_isRejectedAndLeavesReportPending()
            throws Exception {
        long postId = insertPost(memberId, "지워진 글", "본문", PostStatus.DELETED);
        long reporterId = insertMember(
                "deleted-reject-" + System.nanoTime() + "@cakeshop.local", "신고자", "ACTIVE");
        insertReport(postId, reporterId, "PENDING");

        mockMvc.perform(post("/admin/community/" + postId + "/reports/reject")
                        .with(csrf())
                        .with(authentication(admin())))
                .andExpect(status().isBadRequest());

        // 거절된 기각 요청은 신고 상태를 바꾸지 않는다.
        assertThat(reportStatusOf(postId)).isEqualTo("PENDING");
    }

    private String reportStatusOf(long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM post_reports WHERE post_id = ?", String.class, postId);
    }

    /** 비관리자는 관리자 화면을 열 수 없다. */
    @Test
    void communityAdmin_normalMember_isRejected() throws Exception {
        mockMvc.perform(get("/admin/community").with(authentication(authorOf(memberId))))
                .andExpect(status().isForbidden());
    }

    /** 일반 회원의 차단 요청을 Security에서 거절한다. */
    @Test
    void communityAdminBlock_normalMember_isRejected() throws Exception {
        long postId = insertPost(memberId, "차단 시도 대상", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(post("/admin/community/" + postId + "/block")
                        .param("reason", "마음에 안 듦")
                        .with(csrf())
                        .with(authentication(authorOf(memberId))))
                .andExpect(status().isForbidden());

        // 거절된 요청은 게시글을 바꾸지 않는다.
        assertThat(statusOf(postId)).isEqualTo(PostStatus.PUBLISHED.name());
    }

    /** 일반 회원의 신고 기각 요청을 거절한다. */
    @Test
    void communityAdminRejectReports_anonymous_isRedirectedToLogin() throws Exception {
        long postId = insertPost(memberId, "기각 시도 대상", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(post("/admin/community/" + postId + "/reports/reject").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    private void insertRanking(int ranking, long postId) {
        jdbcTemplate.update(
                """
                INSERT INTO daily_popular_posts (
                    ranking_date, ranking, post_id, popularity_score,
                    view_count, like_count, comment_count
                )
                VALUES (?, ?, ?, 100, 0, 0, 0)
                """,
                RANKING_DATE, ranking, postId);
    }

    private void insertBatchRun(int postCount) {
        jdbcTemplate.update(
                """
                INSERT INTO popular_post_batch_runs (ranking_date, post_count)
                VALUES (?, ?)
                """,
                RANKING_DATE, postCount);
    }

    private String statusOf(long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM posts WHERE id = ?", String.class, postId);
    }

    private Authentication authorOf(long id) {
        return authenticationOf(id, "USER");
    }

    private Authentication admin() {
        return authenticationOf(1L, "ADMIN");
    }

    private Authentication authenticationOf(long id, String role) {
        MemberDetails principal = new MemberDetails(
                new MemberAuthenticationView(id, "viewer@cakeshop.local", "x", role, true));

        return new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
    }

    private long insertCategory(String code, String name) {
        jdbcTemplate.update(
                """
                INSERT INTO post_categories (code, name, is_active, sort_order)
                VALUES (?, ?, 1, 999)
                """,
                code, name);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertInactiveCategory() {
        jdbcTemplate.update(
                """
                INSERT INTO post_categories (code, name, is_active, sort_order)
                VALUES (?, '비활성분류', 0, 999)
                """,
                "SCREEN_INACTIVE_" + System.nanoTime());
    }

    private long insertMember(String email, String nickname, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO members (
                    email, password, nickname, phone, role, status, name, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, 'USER', ?, ?, ?, ?)
                """,
                email, "encoded-password", nickname, "010-0000-0000", status,
                nickname, BASE_TIME, BASE_TIME);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertPost(long authorId, String title, String content, PostStatus status) {
        jdbcTemplate.update(
                """
                INSERT INTO posts (
                    member_id, category_id, title, content, status, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                authorId, categoryId, title, content, status.name(), BASE_TIME, BASE_TIME);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void insertComment(long postId) {
        insertComment(postId, memberId, "댓글", CommentStatus.PUBLISHED, BASE_TIME);
    }

    private void insertReport(long postId, long reporterId, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO post_reports (post_id, reporter_id, reason, status, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                postId, reporterId, "광고성 게시물입니다", status, BASE_TIME);
    }

    private void insertLike(long postId, long likerId) {
        jdbcTemplate.update(
                "INSERT INTO post_likes (post_id, member_id) VALUES (?, ?)", postId, likerId);
    }

    private void insertComment(
            long postId,
            long authorId,
            String content,
            CommentStatus status,
            LocalDateTime createdAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO comments (
                    post_id, member_id, content, status, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                postId, authorId, content, status.name(), createdAt, createdAt);
    }

    /** 댓글을 한 번에 삽입한다. */
    private void insertComments(long postId, int count) {
        List<Object[]> rows = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            rows.add(new Object[]{
                    postId, memberId, "댓글 " + i, CommentStatus.PUBLISHED.name(),
                    BASE_TIME.plusMinutes(i), BASE_TIME.plusMinutes(i)
            });
        }

        jdbcTemplate.batchUpdate(
                """
                INSERT INTO comments (
                    post_id, member_id, content, status, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                rows);
    }
}
