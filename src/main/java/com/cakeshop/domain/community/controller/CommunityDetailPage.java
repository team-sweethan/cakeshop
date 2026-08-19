package com.cakeshop.domain.community.controller;

import lombok.RequiredArgsConstructor;

import com.cakeshop.domain.community.dto.view.CommentSectionView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.service.CommunityCommentService;
import com.cakeshop.domain.community.service.CommunityPostImageService;
import com.cakeshop.domain.community.service.CommunityReactionService;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/*
 * 상세 화면 하나를 통째로 맡는다 — Model 조립, 화면 이름, 그리고 그 화면으로 돌아가는 주소.
 *
 * <p>이 화면은 진입점이 여럿이다(상세 조회, 댓글 작성 실패, 신고 작성 실패, 그리고 댓글·좋아요·
 * 신고 뒤의 되돌아가기). 조립이나 주소가 흩어지면 <b>한 경로에만 빠져도 그 경로에서만</b> 버튼이
 * 사라지거나 댓글 구간이 첫 쪽으로 튄다. 진입점이 늘 때 여기만 보면 되도록 모아 둔다.
 *
 * <p><b>화면 이름을 여기에 둔 것은 조각 9의 판단을 뒤집은 것이다.</b> 그때는 Controller 가 하나뿐이라
 * "어느 화면으로 갈지는 Controller 의 몫"이 성립했다. 조각 10에서 Controller 가 셋이 되면서 같은
 * 이름과 같은 되돌아가기 규칙을 세 곳에 복제해야 하는 상황이 되어, 화면이 자기 이름을 갖는 편이
 * 싸졌다.
 */
@Component
@RequiredArgsConstructor
class CommunityDetailPage {

    private static final String VIEW_NAME = "customer/community/detail";

    private final CommunityCommentService communityCommentService;
    private final CommunityReactionService communityReactionService;
    private final CommunityPostImageService communityPostImageService;

    /** 상세 화면을 그린다. 진입점마다 이 한 줄만 부르면 Model 이 같아진다. */
    String render(Model model, PostDetailView post, Long viewerId, String comments, String replies) {
        assemble(
                model,
                post,
                viewerId,
                CommunityRequestParams.positiveInteger(comments),
                CommunityRequestParams.positiveLong(replies)
        );

        return VIEW_NAME;
    }

    /** 알림 대상 댓글을 포함한 상세 화면 전체를 렌더링한다. */
    String renderFocused(
            Model model, PostDetailView post, Long viewerId, long focusedCommentId) {
        assembleFocused(model, post, viewerId, focusedCommentId);

        return VIEW_NAME;
    }

    /*
     * 상세 화면으로 되돌아가는 주소를 만든다.
     *
     * <p>댓글을 더 펼친 상태에서 좋아요를 누르면 다시 접히면 안 된다. 답글을 펼쳐 둔 묶음도
     * 같다. 그래서 요청에 실려 온 두 값을 주소에 보존하되, 기본값이면 붙이지 않아 주소가
     * 지저분해지지 않게 한다.
     */
    String redirect(long postId, String comments, String replies) {
        return redirect(postId, comments, replies, null);
    }

    /*
     * 댓글 구역에서 한 일은 그 댓글 자리로 돌아간다.
     *
     * <p>앵커가 없으면 브라우저는 문서 맨 위에 착지한다. 그런데 확인해야 할 결과 — 방금 쓴 답글,
     * "삭제된 댓글입니다"로 바뀐 줄 — 은 <b>눌린 그 자리에 그대로 생긴다.</b> 화면은 멀쩡하고
     * 사용자만 20~200개 댓글을 다시 훑어 내려가야 하므로 고장으로 보이지도 않는다.
     *
     * <p>앵커 이름은 알림 딥링크가 이미 쓰는 {@code comment-{id}} 규약 그대로다
     * (specs/community-comment.md D4). 화면에 앵커 규약을 두 벌 두지 않는다.
     */
    String redirectToComment(long postId, String comments, String replies, long anchorCommentId) {
        return redirect(postId, comments, replies, anchorCommentId);
    }

    private String redirect(long postId, String comments, String replies, Long anchorCommentId) {
        int limit = CommentSectionView.clampLimit(CommunityRequestParams.positiveInteger(comments));
        Long expandedRootId = CommunityRequestParams.positiveLong(replies);

        StringBuilder url = new StringBuilder("redirect:/community/").append(postId);
        String separator = "?";

        if (limit != CommentSectionView.DEFAULT_LIMIT) {
            url.append(separator).append("comments=").append(limit);
            separator = "&";
        }

        if (expandedRootId != null) {
            url.append(separator).append("replies=").append(expandedRootId);
        }

        // 프래그먼트는 언제나 맨 끝이다. RedirectView 도 파라미터를 '#' 앞에 넣는다.
        if (anchorCommentId != null) {
            url.append("#comment-").append(anchorCommentId);
        }

        return url.toString();
    }

    void assemble(Model model, PostDetailView post, Long viewerId, Integer commentLimit, Long expandedRootId) {
        assembleCommon(model, post, viewerId);

        model.addAttribute(
                "commentSection",
                communityCommentService.getComments(post.id(), commentLimit, expandedRootId)
        );
        model.addAttribute("expandedRootId", expandedRootId);

        /*
         * 강조할 댓글이 없는 경로다. 펼치기·작성·삭제로 온 사람은 어느 댓글인지 이미 알고
         * 눌렀으므로 강조가 답이 아니라 소음이다. 강조는 알림에서 온 경로만 갖는다.
         */
        model.addAttribute("focusedCommentId", null);
    }

    private void assembleFocused(
            Model model, PostDetailView post, Long viewerId, long focusedCommentId) {
        assembleCommon(model, post, viewerId);

        CommentSectionView commentSection =
                communityCommentService.getFocusedComments(post.id(), focusedCommentId);
        Long expandedRootId = commentSection.threads().stream()
                .filter(thread -> thread.expanded())
                .map(thread -> thread.root().id())
                .findFirst()
                .orElse(null);

        model.addAttribute("commentSection", commentSection);
        model.addAttribute("expandedRootId", expandedRootId);

        // 200개 중 어느 것인지 모르고 들어온 경로다. 여기서만 그 줄을 표시한다.
        model.addAttribute("focusedCommentId", focusedCommentId);
    }

    private void assembleCommon(Model model, PostDetailView post, Long viewerId) {
        model.addAttribute("post", post);
        model.addAttribute("viewerId", viewerId);
        model.addAttribute(
                "canEdit",
                post.isAuthoredBy(viewerId) && !post.isBlocked()
        );

        boolean canWrite = viewerId != null && !post.isBlocked();
        model.addAttribute("canComment", canWrite);
        model.addAttribute("canLike", canWrite);

        model.addAttribute(
                "likedByViewer",
                canWrite && communityReactionService.isLikedBy(post.id(), viewerId)
        );

        boolean canReport =
                viewerId != null && !post.isAuthoredBy(viewerId) && !post.isBlocked();
        model.addAttribute("canReport", canReport);

        model.addAttribute(
                "alreadyReported",
                canReport && communityReactionService.isReportedBy(post.id(), viewerId)
        );

        model.addAttribute("postImages", communityPostImageService.getImages(post.id()));

    }
}
