package com.cakeshop.domain.community.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

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
    private static final int PAGE_SIZE = PageRequest.DEFAULT_SIZE;

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
                // 좋아요·조회는 표현식이 만든다. 자리 표시가 아니라 렌더링 결과를 본다.
                // th:if 로 span 이 통째로 사라져도 여기서 걸려야 한다.
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

    /**
     * 쪽 이동 블록이 실제로 그려지는지 확인한다.
     *
     * <p>이 블록은 글이 한 쪽 크기(20건)를 넘어야만 나타난다. 개발 중에는 글이 몇 건뿐이라
     * 화면에 아예 없고, 표현식이 깨져도 눈에 띄지 않는다(SCREENS.md 목록).
     */
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
                // 번호는 주소로 이동한다. 새로고침·뒤로가기에서 필터와 쪽이 유지되어야 한다.
                //
                // page=2 와 categoryId 를 따로 찾으면 안 된다. categoryId 는 상단 필터
                // 링크에도 있어서, 쪽 링크가 필터를 잃어버려도 응답 어딘가에서는 둘 다
                // 발견된다. 링크 하나에 함께 있는지 확인해야 회귀를 잡는다.
                .andExpect(content().string(containsString(
                        "/community?categoryId=" + categoryId + "&amp;page=2")));
    }

    /** 한 쪽에 다 들어가면 쪽 이동 블록 자체가 없어야 한다. */
    @Test
    void communityList_singlePage_omitsPageNavigation() throws Exception {
        insertPost(memberId, "글 하나", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community").param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("페이지 이동"))));
    }

    /**
     * 작성 화면은 아직 목업이다(PLAN.md 조각 2).
     *
     * <p>목업 안내가 사라지면 사용자는 저장되지 않는 폼을 진짜로 오해한다. 조각 2에서 저장
     * 경로를 붙일 때 이 테스트가 함께 바뀌어야 한다.
     */
    @Test
    void communityCreateForm_stillRendersMockNotice() throws Exception {
        mockMvc.perform(get("/community/new").with(authentication(authorOf(memberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("글쓰기")))
                .andExpect(content().string(containsString("mock-notice")))
                .andExpect(content().string(containsString("data-mock-form")));
    }

    /**
     * 작성 화면은 로그인이 필요하다(DOMAIN.md 5).
     *
     * <p>목록·상세와 달리 공개 경로가 아니다. `local` 프로필의 목업 미리보기에서만 예외로
     * 열리므로, 그 예외가 다른 프로필로 새어 나오면 여기서 걸린다.
     *
     * <p>3xx인지만 보면 안 된다. 목적지를 확인하지 않으면 보안 실패 처리기가 홈이나 오류
     * 화면으로 보내도록 바뀌어 <b>사용자가 로그인할 방법이 없어져도</b> 통과한다.
     */
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
                // 상세를 한 번 열었으므로 1이다. 표현식이 그린 결과를 본다.
                .andExpect(content().string(containsString("조회 1")))
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

    /**
     * 수정된 글에만 `(수정됨)`이 켜지는지 확인한다.
     *
     * <p>이 표시는 이력 테이블 없이 {@code updated_at > created_at}으로만 판단한다
     * (SCREENS.md 상세). 조건이 뒤집히거나 표시가 사라져도 평소 화면은 멀쩡해 보인다.
     */
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
                .andExpect(content().string(containsString("광고성 게시물")))
                .andExpect(content().string(
                        containsString("이 글은 다른 회원에게 보이지 않습니다.")));
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

    /**
     * 관리자 커뮤니티 화면은 아직 하드코딩 목업이다(SCREENS.md 관리자 목록).
     *
     * <p>목업이라도 렌더링은 되어야 한다. 컨트롤러가 뷰 이름만 반환하므로, 지금까지 이 두
     * 화면은 어떤 테스트도 열어 본 적이 없었다.
     */
    @Test
    void communityAdminList_rendersForAdmin() throws Exception {
        mockMvc.perform(get("/admin/community").with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("커뮤니티 관리")))
                .andExpect(content().string(containsString("mock-notice")))
                .andExpect(content().string(
                        containsString("제재된 게시글은 고객 화면에서 열람이 차단됩니다.")));
    }

    @Test
    void communityAdminDetail_rendersForAdmin() throws Exception {
        mockMvc.perform(get("/admin/community/15").with(authentication(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("게시글 정보")))
                .andExpect(content().string(containsString("모더레이션")))
                .andExpect(content().string(containsString("신고 내역")))
                .andExpect(content().string(containsString("mock-notice")));
    }

    /** 관리자 화면은 관리자만 연다(DOMAIN.md 5). 목업이라고 열려 있으면 안 된다. */
    @Test
    void communityAdmin_normalMember_isRejected() throws Exception {
        mockMvc.perform(get("/admin/community").with(authentication(authorOf(memberId))))
                .andExpect(status().isForbidden());
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
