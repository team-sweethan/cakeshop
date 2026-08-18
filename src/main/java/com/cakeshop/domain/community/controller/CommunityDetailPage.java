package com.cakeshop.domain.community.controller;

import lombok.RequiredArgsConstructor;

import com.cakeshop.domain.community.dto.view.CommentSectionView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.service.CommunityCommentService;
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

    /** 상세 화면을 그린다. 진입점마다 이 한 줄만 부르면 Model 이 같아진다. */
    String render(Model model, PostDetailView post, Long viewerId, Integer commentLimit) {
        assemble(model, post, viewerId, commentLimit);

        return VIEW_NAME;
    }

    /*
     * 상세 화면으로 되돌아가는 주소를 만든다.
     *
     * <p>댓글을 더 펼친 상태에서 좋아요를 누르면 다시 접히면 안 된다. 그래서 요청에 실려 온 댓글
     * 상한을 주소에 보존하되, 기본값이면 붙이지 않아 주소가 지저분해지지 않게 한다.
     */
    String redirect(long postId, Integer commentLimit) {
        int limit = CommentSectionView.clampLimit(commentLimit);

        if (limit == CommentSectionView.DEFAULT_LIMIT) {
            return "redirect:/community/" + postId;
        }

        return "redirect:/community/" + postId + "?comments=" + limit;
    }

    void assemble(Model model, PostDetailView post, Long viewerId, Integer commentLimit) {
        model.addAttribute("post", post);
        model.addAttribute("viewerId", viewerId);
        model.addAttribute(
                "canEdit",
                viewerId != null && viewerId.equals(post.memberId()) && !post.isBlocked()
        );

        boolean canWrite = viewerId != null && !post.isBlocked();
        model.addAttribute("canComment", canWrite);
        model.addAttribute("canLike", canWrite);

        model.addAttribute(
                "likedByViewer",
                canWrite && communityReactionService.isLikedBy(post.id(), viewerId)
        );

        boolean canReport =
                viewerId != null && !viewerId.equals(post.memberId()) && !post.isBlocked();
        model.addAttribute("canReport", canReport);

        model.addAttribute(
                "alreadyReported",
                canReport && communityReactionService.isReportedBy(post.id(), viewerId)
        );

        model.addAttribute(
                "commentSection",
                communityCommentService.getComments(post.id(), commentLimit)
        );
    }
}
