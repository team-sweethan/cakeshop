package com.cakeshop.domain.community.controller;

import com.cakeshop.domain.community.dto.form.CommentForm;
import com.cakeshop.domain.community.dto.form.ReportForm;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.service.CommunityReactionService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-18
 * 기능 : 커뮤니티 요청 처리
 * 설명 : CommunityReactionController 좋아요·신고 요청을 받아 서비스 호출과 화면 이동을 처리한다.
 * ******************************
 */
@Controller
@RequiredArgsConstructor
public class CommunityReactionController {

    private final CommunityReactionService communityReactionService;
    private final CommunityDetailPage communityDetailPage;

    @PostMapping("/community/{postId:\\d+}/likes")
    public String addLike(
            @PathVariable("postId") long postId,
            @RequestParam(name = "comments", required = false) String comments,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        communityReactionService.addLike(postId, memberDetails.getMemberId());

        return communityDetailPage.redirect(postId, comments);
    }

    @PostMapping("/community/{postId:\\d+}/likes/delete")
    public String removeLike(
            @PathVariable("postId") long postId,
            @RequestParam(name = "comments", required = false) String comments,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        communityReactionService.removeLike(postId, memberDetails.getMemberId());

        return communityDetailPage.redirect(postId, comments);
    }

    /*
     * 신고 폼이 실패해도 상세 화면을 다시 그려야 해서 `commentForm` 을 함께 받는다. 상세 화면이
     * 두 폼을 모두 쓰기 때문이고, 댓글 쪽도 같은 이유로 `reportForm` 을 받는다.
     */
    @PostMapping("/community/{postId:\\d+}/reports")
    public String report(
            @PathVariable("postId") long postId,
            @RequestParam(name = "comments", required = false) String comments,
            @Valid @ModelAttribute("reportForm") ReportForm reportForm,
            BindingResult bindingResult,
            @ModelAttribute("commentForm") CommentForm commentForm,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        long memberId = memberDetails.getMemberId();

        PostDetailView post = communityReactionService.getReportablePost(postId, memberId);

        if (bindingResult.hasErrors()) {
            return communityDetailPage.render(model, post, memberId, comments);
        }

        communityReactionService.reportPost(postId, reportForm, memberId);

        redirectAttributes.addFlashAttribute("successMessage", "신고를 접수했습니다.");

        return communityDetailPage.redirect(postId, comments);
    }
}
