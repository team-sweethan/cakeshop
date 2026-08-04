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
                        "/community?categoryId=" + categoryId + "&amp;sort=LATEST&amp;page=2")));
    }

    /**
     * 정렬 링크가 카테고리 필터를 잃지 않는지 확인한다.
     *
     * <p>필터와 정렬은 서로의 현재 값을 함께 실어야 한다. 안 실으면 분류를 고른 뒤
     * 조회수순을 누르는 순간 <b>분류가 조용히 풀린다</b> — 목록은 멀쩡히 그려지고 글만
     * 늘어나서, 사용자에게는 정렬이 이상하게 동작한 것처럼 보인다.
     *
     * <p>쪽 이동 링크와 같은 이유로 <b>한 링크 안에</b> 둘 다 있는지를 본다. 따로 찾으면
     * 상단 필터 링크가 categoryId를 갖고 있어서 정렬이 그것을 잃어버려도 통과한다.
     */
    @Test
    void communityList_sortLinks_keepCategoryFilter() throws Exception {
        mockMvc.perform(get("/community").param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("최신순")))
                .andExpect(content().string(containsString("조회수순")))
                .andExpect(content().string(containsString(
                        "/community?categoryId=" + categoryId + "&amp;sort=VIEWS")));
    }

    /**
     * 카테고리 링크가 반대로 정렬을 잃지 않는지 확인한다.
     *
     * <p>위 테스트의 반대쪽이다. 한쪽만 보면 <b>한 방향만 값을 싣는 구현</b>이 통과하는데,
     * 실제로 사용자가 밟는 것은 "조회수순을 고른 뒤 분류를 바꾸는" 순서이기도 하다.
     */
    @Test
    void communityList_categoryLinks_keepSortOption() throws Exception {
        mockMvc.perform(get("/community").param("sort", "VIEWS"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "/community?categoryId=" + categoryId + "&amp;sort=VIEWS")));
    }

    /**
     * 삭제 성공 메시지가 목록에 실제로 그려지는지 확인한다.
     *
     * <p>삭제 결과는 목록 어디에도 남지 않는다. 이 안내가 사라지면 사용자는 글이 지워졌는지
     * 알 수 없고, 화면은 평소와 똑같아 보이므로 아무도 눈치채지 못한다.
     */
    @Test
    void communityList_afterDelete_showsSuccessMessage() throws Exception {
        mockMvc.perform(get("/community").flashAttr("successMessage", "게시글을 삭제했습니다."))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("게시글을 삭제했습니다.")));
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
     * 작성 화면의 분류 선택지가 DB의 활성 카테고리인지 확인한다.
     *
     * <p>조각 2 이전에는 `후기/질문/자유/레시피`가 화면에 적혀 있었다. 비활성 카테고리가
     * 선택지에 남는 것은 화면만 봐서는 알 수 없다(DOMAIN.md 6.8).
     *
     * <p>목업 표식이 남아 있으면 안 된다. `data-mock-form`이 붙은 폼은 스크립트가 가로채
     * 서버로 보내지 않으므로, 저장 경로를 붙여 놓고도 <b>저장된 것처럼 보이는데 안 되는</b>
     * 화면이 된다.
     */
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
                // 이미지 첨부는 1차 범위 밖이다. 저장할 곳이 없는 입력이 화면에 남으면
                // 사용자는 첨부가 되는 줄 안다(SCREENS.md 만들지 않는 화면).
                .andExpect(content().string(not(containsString("사진 첨부"))));
    }

    /** 수정 화면은 기존 값이 채워진 채로 열린다. */
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

    /**
     * 수정·삭제 버튼은 작성자에게만 보인다.
     *
     * <p>남의 화면에 버튼이 뜨면 눌러 봐야 404를 받는다. 실제로 막는 것은 Service지만,
     * 버튼이 보이는 것 자체가 "이 글에 손댈 수 있다"는 거짓말이다.
     */
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

    /**
     * 차단된 글에는 작성자에게도 수정·삭제 버튼이 없다.
     *
     * <p>작성자는 차단된 글 본문과 사유까지 보지만 할 수 있는 일은 없다(DOMAIN.md 4.2).
     * 이 화면은 "차단된 글을 작성자가 연다"는 드문 조건에서만 그려진다.
     */
    @Test
    void communityDetail_blockedPost_author_hidesEditAndDeleteButtons() throws Exception {
        long postId = insertPost(memberId, "차단된 글", "본문", PostStatus.BLOCKED);

        mockMvc.perform(get("/community/" + postId).with(authentication(authorOf(memberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        not(containsString("/community/" + postId + "/edit"))));
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
     * 댓글이 오래된 순으로 그려지고 삭제된 것은 개수에서 빠지는지 확인한다.
     *
     * <p>개수와 순서 둘 다 화면에서만 드러난다. 개수가 자리 표시를 세면 "댓글 3"인데 두 개만
     * 보이고, 순서가 뒤집히면 대화가 거꾸로 읽힌다(DOMAIN.md 4.4, 6.4).
     */
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
                // 자리 표시는 개수에 넣지 않는다.
                .andExpect(content().string(containsString("댓글 2")))
                .andExpect(content().string(containsString("먼저 쓴 댓글")))
                .andExpect(content().string(containsString("나중에 쓴 댓글")))
                .andReturn().getResponse().getContentAsString();

        assertThat(html.indexOf("먼저 쓴 댓글")).isLessThan(html.indexOf("나중에 쓴 댓글"));
    }

    /**
     * 삭제된 댓글이 자리 표시로 남고 본문은 사라지는지 확인한다.
     *
     * <p>지운 사람이 지우기를 원한 것이 바로 그 본문이다. SQL이 NULL로 지우므로 화면까지
     * 내려가지 않는다(DOMAIN.md 4.4).
     *
     * <p>댓글 작성자를 게시글 작성자와 다른 회원으로 둔다. 같은 회원이면 글쓴이 이름이
     * 본문 위에 이미 나와 있어서, 자리 표시가 이름을 흘려도 이 검사가 통과한다.
     */
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
                // 작성자도 내보내지 않는다. 자리만 남긴다.
                .andExpect(content().string(not(containsString("댓글쓴사람"))));
    }

    @Test
    void communityDetail_withoutComments_showsEmptyMessage() throws Exception {
        long postId = insertPost(memberId, "댓글 없는 글", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community/" + postId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("아직 댓글이 없습니다.")));
    }

    /** 댓글 본문의 HTML도 그대로 실행되면 안 된다. 게시글 본문과 같은 규칙이다(DOMAIN.md 7). */
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

    /**
     * 비로그인에게는 댓글 폼 대신 안내가 보이는지 확인한다.
     *
     * <p>폼을 열어 두면 다 쓰고 나서 로그인으로 튕긴다. 조각 2에서 글쓰기 화면에 대해
     * 같은 판단을 했다.
     */
    @Test
    void communityDetail_anonymous_showsLoginPromptInsteadOfForm() throws Exception {
        long postId = insertPost(memberId, "글", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community/" + postId))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("로그인하면 댓글을 쓸 수 있습니다.")))
                .andExpect(content().string(not(containsString("댓글 등록"))));
    }

    /**
     * 차단된 글에는 작성자에게도 댓글 폼이 없는지 확인한다.
     *
     * <p>안내도 띄우지 않는다. 로그인해도 댓글을 달 수 없으므로 "로그인하면 쓸 수 있다"는
     * 거짓이 된다. 이 화면은 "차단된 글을 작성자가 연다"는 드문 조건에서만 그려진다.
     */
    @Test
    void communityDetail_blockedPost_author_hidesCommentForm() throws Exception {
        long postId = insertPost(memberId, "차단된 글", "본문", PostStatus.BLOCKED);

        mockMvc.perform(get("/community/" + postId).with(authentication(authorOf(memberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("댓글 등록"))))
                .andExpect(content().string(
                        not(containsString("로그인하면 댓글을 쓸 수 있습니다."))));
    }

    /** 삭제 버튼은 자기 댓글에만 보인다. 남의 화면에 뜨면 눌러 봐야 404다. */
    @Test
    void communityDetail_ownComment_showsDeleteButton() throws Exception {
        long postId = insertPost(memberId, "글", "본문", PostStatus.PUBLISHED);
        insertComment(postId, memberId, "내 댓글", CommentStatus.PUBLISHED, BASE_TIME);

        mockMvc.perform(get("/community/" + postId).with(authentication(authorOf(memberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("댓글 삭제")));
    }

    /**
     * 차단된 글에서는 자기 댓글에도 삭제 버튼이 없는지 확인한다.
     *
     * <p>삭제도 게시글이 {@code PUBLISHED}여야 한다(DOMAIN.md 6.4). 소유권만 보고 버튼을
     * 그리면 <b>눌러도 403만 나오는 죽은 버튼</b>이 남는데, 이 화면은 "차단된 글을 작성자가
     * 연다"는 드문 조건에서만 그려져서 개발 중에는 마주칠 일이 없다. 댓글 폼은 이미
     * 숨기고 있었으므로 두 조건이 갈라져 있던 자리다.
     */
    @Test
    void communityDetail_blockedPostAuthor_hasNoDeadCommentDeleteButton() throws Exception {
        long postId = insertPost(memberId, "차단된 글", "본문", PostStatus.BLOCKED);
        insertComment(postId, memberId, "내 댓글", CommentStatus.PUBLISHED, BASE_TIME);

        mockMvc.perform(get("/community/" + postId).with(authentication(authorOf(memberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("내 댓글")))
                .andExpect(content().string(not(containsString("댓글 삭제"))));
    }

    /** 아직 안 누른 회원에게는 누르는 버튼이 보인다. */
    @Test
    void communityDetail_notLikedYet_showsLikeButton() throws Exception {
        long postId = insertPost(memberId, "글", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community/" + postId).with(authentication(authorOf(memberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("좋아요")))
                .andExpect(content().string(containsString("/likes")))
                .andExpect(content().string(not(containsString("좋아요 취소"))));
    }

    /**
     * 이미 누른 회원에게는 거두는 버튼으로 바뀌는지 확인한다.
     *
     * <p>양쪽이 멱등이라 <b>버튼이 안 바뀌어도 기능은 맞는다</b> — 그래서 동작 테스트로는
     * 드러나지 않는다. 드러나는 것은 사용자가 자기가 누른 상태인지 화면에서 알 수 없을 때뿐이다.
     */
    @Test
    void communityDetail_alreadyLiked_showsCancelButton() throws Exception {
        long postId = insertPost(memberId, "글", "본문", PostStatus.PUBLISHED);
        insertLike(postId, memberId);

        mockMvc.perform(get("/community/" + postId).with(authentication(authorOf(memberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("좋아요 취소")))
                .andExpect(content().string(containsString("/likes/delete")));
    }

    /**
     * 남이 누른 좋아요가 내 버튼을 바꾸지 않는지 확인한다.
     *
     * <p>{@code existsLike}에서 회원 조건이 빠져도 <b>숫자는 맞고 버튼만 틀린다.</b>
     * 좋아요가 하나라도 있는 글에서는 아무도 안 눌러도 모두에게 "좋아요 취소"가 보인다.
     */
    @Test
    void communityDetail_likedByAnotherMember_stillShowsLikeButtonToMe() throws Exception {
        long postId = insertPost(memberId, "글", "본문", PostStatus.PUBLISHED);
        insertLike(postId, memberId);

        mockMvc.perform(get("/community/" + postId)
                        .with(authentication(authorOf(withdrawnMemberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("좋아요 취소"))));
    }

    /** 비로그인에게는 숫자만 있고 버튼이 없다. */
    @Test
    void communityDetail_anonymous_showsLikeCountWithoutButton() throws Exception {
        long postId = insertPost(memberId, "글", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community/" + postId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("좋아요 0")))
                .andExpect(content().string(not(containsString("/likes"))));
    }

    /**
     * 차단된 글에는 작성자에게도 좋아요 버튼이 없는지 확인한다.
     *
     * <p>댓글 삭제 버튼과 같은 자리다 — 남겨 두면 눌러도 403만 나오는 죽은 버튼이 되고,
     * 이 화면은 "차단된 글을 작성자가 연다"는 드문 조건에서만 그려져 개발 중에는 마주치지
     * 않는다(DOMAIN.md 4.5).
     */
    @Test
    void communityDetail_blockedPostAuthor_hasNoDeadLikeButton() throws Exception {
        long postId = insertPost(memberId, "차단된 글", "본문", PostStatus.BLOCKED);

        mockMvc.perform(get("/community/" + postId).with(authentication(authorOf(memberId))))
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

    /**
     * "더 보기"가 실제로 그려지고 과거를 펼치는지 확인한다.
     *
     * <p>이 블록은 댓글이 한 화면 분량을 넘어야만 나타난다. 개발 중에는 댓글이 몇 건뿐이라
     * 화면에 아예 없고, 표현식이 깨져도 눈에 띄지 않는다.
     *
     * <p>잘라 내는 쪽이 과거인 것도 함께 본다. 가장 오래된 댓글은 처음에 안 보이고 가장
     * 최근 댓글은 보여야 한다 — 반대가 되면 방금 쓴 댓글이 화면 밖에 남는다.
     */
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
                // 가장 오래된 것이 잘리고 가장 최근 것은 남는다.
                .andExpect(content().string(not(containsString(">댓글 0<"))))
                .andExpect(content().string(containsString(
                        "댓글 " + CommentSectionView.DEFAULT_LIMIT)));

        // 더 보기를 따라가면 잘렸던 댓글이 나타난다.
        mockMvc.perform(get("/community/" + postId).param("comments", String.valueOf(nextLimit)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">댓글 0<")))
                .andExpect(content().string(not(containsString("이전 댓글 더 보기"))));
    }

    /**
     * 상한에 막혀 못 보여주는 댓글이 있으면 화면이 그 사실을 말하는지 확인한다.
     *
     * <p>링크를 조용히 감추면 "댓글이 여기까지"로 보이는데 그것은 거짓이다. 이 상태는
     * 댓글이 {@code MAX_LIMIT}을 넘어야만 나오므로 사람 눈으로는 영원히 발견되지 않는다.
     */
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

    /**
     * 신고 폼은 남의 글에만, 로그인 회원에게만 열린다(DOMAIN.md 6.6).
     *
     * <p>펼쳐야 보이는 블록이라 표현식이 깨져도 평소 화면에서는 눈에 띄지 않는다.
     */
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

    /** 자기 글에는 신고 자리가 없다. 자기 글이 문제라면 지우면 된다(DOMAIN.md 6.6). */
    @Test
    void communityDetail_author_hidesReportForm() throws Exception {
        long postId = insertPost(memberId, "내 글", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(get("/community/" + postId).with(authentication(authorOf(memberId))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("이 게시글 신고"))));
    }

    /**
     * 이미 신고한 사람에게는 폼 대신 안내가 보인다.
     *
     * <p>폼을 그대로 두면 다시 눌렀을 때 오류 화면으로 튀는데, 그건 사용자가 잘못한 것이
     * 아니라 이미 접수된 것이다. 신고에는 취소가 없어서 이 상태는 되돌아가지 않는다.
     */
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

    /**
     * 관리자 목록은 조각 5에서 목업을 걷어내고 실제 데이터를 그린다.
     *
     * <p>상태 어휘가 문서와 같은 말인지도 함께 본다. 화면만 `정상`/`제재`로 남으면 같은
     * 상태를 두고 화면·문서·코드가 다른 것을 가리키게 된다(DOMAIN.md 6.7).
     */
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
                // 목업이 남긴 규칙 밖 어휘와 기능이 되살아나지 않는지 함께 본다.
                .andExpect(content().string(not(containsString("mock-notice"))))
                .andExpect(content().string(not(containsString("작성자 검색"))));
    }

    /** 조건에 맞는 글이 없는 목록. 평소 화면에 없어서 표현식이 깨져도 드러나지 않는다. */
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
                // 규칙에 없는 조치가 되살아나지 않는지 본다(DOMAIN.md 6.7).
                .andExpect(content().string(not(containsString("게시글 영구 삭제"))))
                .andExpect(content().string(not(containsString("mock-notice"))));
    }

    /**
     * 차단된 글의 관리자 상세. 본문을 볼 수 있는 유일한 화면이고(DOMAIN.md 4.3),
     * 차단 기록과 해제 버튼이 함께 나온다.
     */
    @Test
    void communityAdminDetail_blockedPost_showsBlockRecordAndUnblock() throws Exception {
        long postId = insertPost(memberId, "차단된 글", "차단된 본문", PostStatus.BLOCKED);
        // blocked_by는 members FK다. 차단한 관리자가 실제로 있어야 한다.
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
                // 차단된 글의 본문은 여기서만 보인다.
                .andExpect(content().string(containsString("차단된 본문")))
                // 이미 차단된 글에 차단 버튼을 남기면 눌러도 400만 나오는 죽은 버튼이 된다.
                .andExpect(content().string(not(containsString("차단하기"))));
    }

    /**
     * 작성자가 지운 글에는 아무 조치도 할 수 없다(DOMAIN.md 4.2의 DELETED 종착).
     * 조건부 안내라 평소 화면에 없다.
     */
    @Test
    void communityAdminDetail_deletedPost_hidesModerationActions() throws Exception {
        long postId = insertPost(memberId, "지워진 글", "본문", PostStatus.DELETED);
        // 글이 노출 중일 때 접수된 신고는 작성자가 지워도 PENDING으로 남는다(DOMAIN.md 4.5).
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
                // 미처리 신고가 남아 있어도 기각 버튼은 없다. 하나만 조건에 걸면
                // "조치할 수 없습니다" 안내와 기각 버튼이 나란히 보인다(PR #96 Codex 리뷰).
                .andExpect(content().string(not(containsString("신고 기각"))));
    }

    /**
     * 작성자가 지운 글에는 기각도 막힌다. 버튼을 감추는 것은 안내일 뿐이고 막는 것은
     * Service다 — 관리자는 이 화면에서 주소를 알게 되므로 요청만 따로 보낼 수 있다.
     */
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

        // 신고는 PENDING 그대로다. 지운 글의 신고를 자동으로 닫지 않는다(DOMAIN.md 4.5).
        assertThat(reportStatusOf(postId)).isEqualTo("PENDING");
    }

    private String reportStatusOf(long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM post_reports WHERE post_id = ?", String.class, postId);
    }

    /** 관리자 화면은 관리자만 연다(DOMAIN.md 5). */
    @Test
    void communityAdmin_normalMember_isRejected() throws Exception {
        mockMvc.perform(get("/admin/community").with(authentication(authorOf(memberId))))
                .andExpect(status().isForbidden());
    }

    /**
     * 차단은 화면을 감추는 것이 아니라 Security가 막는다(DOMAIN.md 5).
     *
     * <p>일반 회원에게 버튼이 안 보이는 것은 안내일 뿐이다. 주소는 이 문서에 적혀 있고
     * 요청은 따로 만들 수 있으므로, 막는 자리는 언제나 Security와 Service다.
     */
    @Test
    void communityAdminBlock_normalMember_isRejected() throws Exception {
        long postId = insertPost(memberId, "차단 시도 대상", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(post("/admin/community/" + postId + "/block")
                        .param("reason", "마음에 안 듦")
                        .with(csrf())
                        .with(authentication(authorOf(memberId))))
                .andExpect(status().isForbidden());

        // 요청이 정말 아무것도 바꾸지 않았는지까지 본다. 403이 나도 조치가 끝난 뒤라면
        // 막은 것이 아니다.
        assertThat(statusOf(postId)).isEqualTo(PostStatus.PUBLISHED.name());
    }

    /** 신고 기각도 같다. 관리자 조치 경로는 전부 Security 뒤에 있어야 한다. */
    @Test
    void communityAdminRejectReports_anonymous_isRedirectedToLogin() throws Exception {
        long postId = insertPost(memberId, "기각 시도 대상", "본문", PostStatus.PUBLISHED);

        mockMvc.perform(post("/admin/community/" + postId + "/reports/reject").with(csrf()))
                .andExpect(status().is3xxRedirection());
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

    /** 상한 검사에는 댓글이 수백 건 필요하다. 한 번에 넣어 왕복을 줄인다. */
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
