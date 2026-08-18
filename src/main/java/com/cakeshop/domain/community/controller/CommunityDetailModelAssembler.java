package com.cakeshop.domain.community.controller;

import lombok.RequiredArgsConstructor;

import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.service.CommunityCommentService;
import com.cakeshop.domain.community.service.CommunityReactionService;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/*
 * 상세 화면이 쓰는 Model 을 한자리에서 채운다.
 *
 * <p>세 진입점(상세 조회, 댓글 검증 실패, 신고 검증 실패)이 같은 화면을 그리므로, 이 조립이
 * 흩어지면 한 경로에만 속성이 빠져도 그 화면에서만 버튼이 사라진다. 진입점이 늘 때 여기만
 * 보면 되도록 모아 둔다.
 *
 * <p>화면 이름은 여기서 정하지 않는다. 어느 화면으로 갈지는 Controller 의 몫이다.
 */
@Component
@RequiredArgsConstructor
class CommunityDetailModelAssembler {

    private final CommunityCommentService communityCommentService;
    private final CommunityReactionService communityReactionService;

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
