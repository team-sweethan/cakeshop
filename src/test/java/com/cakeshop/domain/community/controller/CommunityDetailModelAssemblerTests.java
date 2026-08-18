package com.cakeshop.domain.community.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.community.dto.view.CommentSectionView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.service.CommunityCommentService;
import com.cakeshop.domain.community.service.CommunityReactionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

/**
 * 상세 화면 Model 조립이 소유하는 것은 <b>권한 플래그</b>다.
 *
 * <p>`canComment`·`canLike`·`likedByViewer`는 {@code CommunityControllerTests}가 요청 경로로 이미
 * 본다. 여기서는 그쪽이 보지 않는 `canEdit`·`canReport`·`alreadyReported`·`viewerId`를 고정한다 —
 * 이 넷은 <b>틀려도 서버에 오류가 없고</b>, 버튼이 사라지거나 남을 뿐이라 사람 눈으로도 자기
 * 계정으로는 잘 드러나지 않는다.
 */
class CommunityDetailModelAssemblerTests {

    private static final long POST_ID = 15L;
    private static final long AUTHOR_ID = 7L;
    private static final long OTHER_MEMBER_ID = 99L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 3, 1, 10, 0);

    private CommunityCommentService communityCommentService;
    private CommunityReactionService communityReactionService;
    private CommunityDetailModelAssembler assembler;

    @BeforeEach
    void setUp() {
        communityCommentService = mock(CommunityCommentService.class);
        communityReactionService = mock(CommunityReactionService.class);

        when(communityCommentService.getComments(anyLong(), any()))
                .thenReturn(new CommentSectionView(
                        List.of(), 0, 0, CommentSectionView.DEFAULT_LIMIT));

        assembler = new CommunityDetailModelAssembler(
                communityCommentService, communityReactionService);
    }

    /** 수정 버튼은 노출 중인 자기 글에만 나온다. */
    @ParameterizedTest(name = "{0} 글을 작성자={1}가 보면 canEdit={2}")
    @CsvSource(value = {
            "PUBLISHED, true,  true",
            "PUBLISHED, false, false",
            "BLOCKED,   true,  false",
            "PUBLISHED, NONE,  false"
    }, nullValues = "NONE")
    void canEdit_onlyForAuthorOfVisiblePost(
            PostStatus status, Boolean asAuthor, boolean expected) {
        Model model = new ConcurrentModel();

        assembler.assemble(model, postOf(status), viewerOf(asAuthor), null);

        assertThat(model.getAttribute("canEdit")).isEqualTo(expected);
    }

    /** 신고 버튼은 남의 글에만 나온다. 자기 글과 차단 글에는 없다. */
    @ParameterizedTest(name = "{0} 글을 작성자={1}가 보면 canReport={2}")
    @CsvSource(value = {
            "PUBLISHED, false, true",
            "PUBLISHED, true,  false",
            "BLOCKED,   false, false",
            "PUBLISHED, NONE,  false"
    }, nullValues = "NONE")
    void canReport_onlyForOthersVisiblePost(
            PostStatus status, Boolean asAuthor, boolean expected) {
        Model model = new ConcurrentModel();

        assembler.assemble(model, postOf(status), viewerOf(asAuthor), null);

        assertThat(model.getAttribute("canReport")).isEqualTo(expected);
    }

    /** 이미 신고했는지는 신고할 수 있는 사람에게만 묻는다. */
    @Test
    void alreadyReported_isAskedOnlyWhenReportable() {
        when(communityReactionService.isReportedBy(POST_ID, OTHER_MEMBER_ID)).thenReturn(true);
        Model model = new ConcurrentModel();

        assembler.assemble(model, postOf(PostStatus.PUBLISHED), OTHER_MEMBER_ID, null);

        assertThat(model.getAttribute("alreadyReported")).isEqualTo(true);
    }

    /** 자기 글에는 신고 여부를 조회하지 않는다. 물어 봐야 쓸 데가 없는 쿼리다. */
    @Test
    void alreadyReported_ownPost_doesNotQuery() {
        Model model = new ConcurrentModel();

        assembler.assemble(model, postOf(PostStatus.PUBLISHED), AUTHOR_ID, null);

        assertThat(model.getAttribute("alreadyReported")).isEqualTo(false);
        verify(communityReactionService, never()).isReportedBy(anyLong(), anyLong());
    }

    /**
     * 로그인 조회자의 ID가 실제로 Model에 실린다.
     *
     * <p>`detail.html`이 `viewerId == comment.memberId`로 <b>자기 댓글 삭제 버튼</b>을 가린다.
     * 바인딩이 사라지면 이 조건이 늘 거짓이 되어 로그인 사용자 전원이 그 버튼을 잃는데,
     * <b>비로그인 검사만으로는 잡히지 않는다</b> — 속성이 아예 없어도 `getAttribute`는 똑같이
     * `null`을 돌려주기 때문이다(PR #289 Codex 리뷰).
     */
    @Test
    void viewerId_loggedIn_isBound() {
        Model model = new ConcurrentModel();

        assembler.assemble(model, postOf(PostStatus.PUBLISHED), OTHER_MEMBER_ID, null);

        assertThat(model.getAttribute("viewerId")).isEqualTo(OTHER_MEMBER_ID);
    }

    /** 비로그인 조회자도 화면이 그려진다. 이때 viewerId는 null이다. */
    @Test
    void viewerId_anonymous_staysNull() {
        Model model = new ConcurrentModel();

        assembler.assemble(model, postOf(PostStatus.PUBLISHED), null, null);

        assertThat(model.getAttribute("viewerId")).isNull();
        assertThat(model.getAttribute("canEdit")).isEqualTo(false);
        assertThat(model.getAttribute("canReport")).isEqualTo(false);
    }

    /** 댓글 상한은 조립기가 정하지 않고 받은 값을 그대로 넘긴다. */
    @Test
    void commentLimit_isPassedThrough() {
        Model model = new ConcurrentModel();

        assembler.assemble(model, postOf(PostStatus.PUBLISHED), AUTHOR_ID, 40);

        verify(communityCommentService).getComments(POST_ID, 40);
    }

    private Long viewerOf(Boolean asAuthor) {
        if (asAuthor == null) {
            return null;
        }

        return asAuthor ? AUTHOR_ID : OTHER_MEMBER_ID;
    }

    private PostDetailView postOf(PostStatus status) {
        return new PostDetailView(
                POST_ID,
                AUTHOR_ID,
                1L,
                "질문",
                "제목",
                "본문",
                "글쓴이",
                false,
                status,
                status == PostStatus.BLOCKED ? "광고성 게시물" : null,
                10L,
                2L,
                CREATED_AT,
                CREATED_AT
        );
    }
}
