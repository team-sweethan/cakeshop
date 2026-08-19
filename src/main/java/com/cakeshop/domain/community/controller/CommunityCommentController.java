package com.cakeshop.domain.community.controller;

import com.cakeshop.domain.community.dto.form.CommentForm;
import com.cakeshop.domain.community.dto.form.ReportForm;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.service.CommunityCommentService;
import com.cakeshop.domain.community.service.CommunityPostService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.security.MemberDetails;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-18
 * 기능 : 커뮤니티 요청 처리
 * 설명 : CommunityCommentController 댓글 요청을 받아 서비스 호출과 화면 이동을 처리한다.
 * ******************************
 */
@Controller
@RequiredArgsConstructor
public class CommunityCommentController {

    /*
     * 댓글을 달 수 있는 글인지는 게시글 쪽이 판단한다. 검증 실패로 상세를 다시 그릴 때 그 글이
     * 필요해서 여기서도 부른다 — 판단이 두 벌이 되지 않게 읽기만 한다.
     */
    private final CommunityPostService communityPostService;

    private final CommunityCommentService communityCommentService;
    private final CommunityDetailPage communityDetailPage;

    // 댓글·답글 작성. 답글은 replyTo 가 실려 온다
    @PostMapping("/community/{postId:\\d+}/comments")
    public String addComment(
            @PathVariable("postId") long postId,
            @RequestParam(name = "comments", required = false) String comments,
            @RequestParam(name = "replies", required = false) String replies,
            @RequestParam(name = "replyTo", required = false) String replyTo,
            @Valid @ModelAttribute("commentForm") CommentForm commentForm,
            BindingResult bindingResult,
            @ModelAttribute("reportForm") ReportForm reportForm,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model
    ) {
        long memberId = memberDetails.getMemberId();

        Long parentCommentId = CommunityRequestParams.positiveLong(replyTo);

        /*
         * replyTo 가 실려 왔는데 값이 망가졌으면 빈 값까지 거절한다. 부재와 파싱 실패를 구분하지
         * 않으면 변조된 답글 요청이 조용히 뿌리 댓글로 강등되어 저장된다. 답글 폼은 항상 값을
         * 싣고 뿌리 폼은 파라미터 자체가 없으므로, 빈 값도 정상 경로가 아니다.
         */
        if (replyTo != null && parentCommentId == null) {
            throw new BusinessException(CommunityErrorCode.COMMENT_NOT_FOUND);
        }

        PostDetailView post = communityPostService.getCommentablePost(postId, memberId);

        if (bindingResult.hasErrors()) {
            // 실패한 폼이 어느 쪽(뿌리·답글)인지 화면이 알아야 오류가 그 폼에 그려진다
            model.addAttribute("failedReplyTo", parentCommentId);

            return communityDetailPage.render(model, post, memberId, comments, replies);
        }

        if (parentCommentId == null) {
            communityCommentService.addComment(postId, commentForm, memberId);

            // 새 댓글은 언제나 최신 20건 안에 있으므로 기본 분량으로 돌아간다
            return "redirect:/community/" + postId;
        }

        communityCommentService.addReply(postId, parentCommentId, commentForm, memberId);

        // 방금 쓴 답글이 보여야 하므로 그 묶음을 펼친 채로 돌아간다
        return communityDetailPage.redirect(postId, comments, String.valueOf(parentCommentId));
    }

    @PostMapping("/community/{postId:\\d+}/comments/{commentId:\\d+}/delete")
    public String deleteComment(
            @PathVariable("postId") long postId,
            @PathVariable("commentId") long commentId,
            @RequestParam(name = "comments", required = false) String comments,
            @RequestParam(name = "replies", required = false) String replies,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        communityCommentService.deleteComment(postId, commentId, memberDetails.getMemberId());

        return communityDetailPage.redirect(postId, comments, replies);
    }
}
