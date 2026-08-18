package com.cakeshop.domain.community.controller;

import com.cakeshop.domain.community.dto.form.CommentForm;
import com.cakeshop.domain.community.dto.form.ReportForm;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.service.CommunityCommentService;
import com.cakeshop.domain.community.service.CommunityPostService;
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

        PostDetailView post = communityPostService.getCommentablePost(postId, memberId);

        if (bindingResult.hasErrors()) {
            return communityDetailPage.render(model, post, memberId, comments, replies);
        }

        Long parentCommentId = CommunityRequestParams.positiveLong(replyTo);

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
