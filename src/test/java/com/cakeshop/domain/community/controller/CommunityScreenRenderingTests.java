package com.cakeshop.domain.community.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.member.dto.view.MemberAuthenticationView;
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

/**
 * 커뮤니티 화면이 실제로 렌더링되는지 확인한다.
 *
 * <p>Controller 단위 테스트는 뷰 <b>이름</b>만 확인한다. 템플릿 표현식이 깨져 있어도 통과하므로
 * 화면이 500으로 죽는 것을 잡지 못한다. 여기서는 Thymeleaf를 실제로 돌린다.
 */
@SpringBootTest
@MariaDbIntegrationTest
@Transactional
class CommunityScreenRenderingTests {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 3, 1, 10, 0);

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
                .andExpect(content().string(containsString("딸기 케이크 후기")))
                .andExpect(content().string(containsString("케이크덕후")))
                .andExpect(content().string(containsString("댓글 1")))
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

    @Test
    void communityDetail_rendersContent() throws Exception {
        long postId = insertPost(memberId, "제목입니다", "첫 줄\n둘째 줄", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community/" + postId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("제목입니다")))
                .andExpect(content().string(containsString("첫 줄")))
                // 줄바꿈은 태그가 아니라 CSS로 살린다(DOMAIN.md 7).
                .andExpect(content().string(containsString("white-space:pre-wrap")));
    }

    /**
     * 본문의 HTML이 그대로 실행되지 않는지 확인한다.
     *
     * <p>본문은 순수 텍스트다. 템플릿에서 {@code th:utext}를 쓰는 순간 저장된 문자열이
     * 그대로 스크립트가 된다(DOMAIN.md 7). 화면은 멀쩡해 보이므로 눈으로는 잡히지 않는다.
     */
    @Test
    void communityDetail_htmlInContent_isEscaped() throws Exception {
        String attack = "<script>alert('xss')</script>";
        long postId = insertPost(memberId, "제목", attack, PostStatus.PUBLISHED);

        mockMvc.perform(get("/community/" + postId))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(attack))))
                .andExpect(content().string(containsString("&lt;script&gt;")));
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
                // 차단 사유는 작성자에게만 보여준다. 남에게는 존재 자체를 알리지 않는다.
                .andExpect(content().string(not(containsString("광고성 게시물"))));
    }

    @Test
    void communityDetail_unknownPost_returnsNotFound() throws Exception {
        mockMvc.perform(get("/community/99999999"))
                .andExpect(status().isNotFound());
    }

    /**
     * 작성자에게만 열리는 차단 안내가 실제로 렌더링되는지 확인한다.
     *
     * <p>이 블록은 "차단된 글을 작성자가 연다"는 드문 조건에서만 그려진다. 평소 화면에
     * 나타나지 않으므로 표현식이 깨져도 아무도 모른 채 지나간다(DOMAIN.md 4.3).
     */
    @Test
    void communityDetail_blockedPost_author_showsBlockedReason() throws Exception {
        long postId = insertPost(memberId, "차단된 글", "본문", PostStatus.BLOCKED);
        jdbcTemplate.update(
                "UPDATE posts SET blocked_reason = ? WHERE id = ?", "광고성 게시물", postId);

        mockMvc.perform(get("/community/" + postId).with(authentication(authorOf(memberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("관리자가 차단한 게시글입니다.")))
                .andExpect(content().string(containsString("광고성 게시물")));
    }

    /** 삭제는 종착 상태다. 작성자에게도 404다(DOMAIN.md 4.2, 4.3). */
    @Test
    void communityDetail_deletedPost_author_returnsNotFound() throws Exception {
        long postId = insertPost(memberId, "삭제된 글", "본문", PostStatus.DELETED);

        mockMvc.perform(get("/community/" + postId).with(authentication(authorOf(memberId))))
                .andExpect(status().isNotFound());
    }

    /** 남의 차단된 글은 사유는커녕 존재 자체가 드러나지 않아야 한다. */
    @Test
    void communityDetail_blockedPost_otherMember_returnsNotFound() throws Exception {
        long postId = insertPost(memberId, "차단된 글", "본문", PostStatus.BLOCKED);
        jdbcTemplate.update(
                "UPDATE posts SET blocked_reason = ? WHERE id = ?", "광고성 게시물", postId);

        mockMvc.perform(get("/community/" + postId)
                        .with(authentication(authorOf(withdrawnMemberId))))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("광고성 게시물"))));
    }

    private Authentication authorOf(long id) {
        MemberDetails principal = new MemberDetails(
                new MemberAuthenticationView(id, "viewer@cakeshop.local", "x", "USER", true));

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
        jdbcTemplate.update(
                """
                INSERT INTO comments (post_id, member_id, content, status)
                VALUES (?, ?, '댓글', 'PUBLISHED')
                """,
                postId, memberId);
    }
}
