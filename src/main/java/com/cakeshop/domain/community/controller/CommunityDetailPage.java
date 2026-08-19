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

    /*
     * 댓글 구역만 그린다(조각 9). 전체 화면과 같은 Model 조립을 거쳐 같은 프래그먼트를 내므로,
     * 스크립트가 갈아끼운 화면과 링크로 다시 연 화면이 어긋날 수 없다.
     */
    String renderCommentSection(
            Model model, PostDetailView post, Long viewerId, String comments, String replies) {
        render(model, post, viewerId, comments, replies);

        return VIEW_NAME + " :: commentSection";
    }

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

    /*
     * 상세 화면으로 되돌아가는 주소를 만든다.
     *
     * <p>댓글을 더 펼친 상태에서 좋아요를 누르면 다시 접히면 안 된다. 답글을 펼쳐 둔 묶음도
     * 같다. 그래서 요청에 실려 온 두 값을 주소에 보존하되, 기본값이면 붙이지 않아 주소가
     * 지저분해지지 않게 한다.
     */
    String redirect(long postId, String comments, String replies) {
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

        return url.toString();
    }

    void assemble(Model model, PostDetailView post, Long viewerId, Integer commentLimit, Long expandedRootId) {
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

        model.addAttribute(
                "commentSection",
                communityCommentService.getComments(post.id(), commentLimit, expandedRootId)
        );
        model.addAttribute("expandedRootId", expandedRootId);
    }
}
