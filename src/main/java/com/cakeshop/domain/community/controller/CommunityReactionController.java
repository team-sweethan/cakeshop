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

    // @RequiredArgsConstructor(lombok)가 아래 final 필드 둘을 받는 생성자를 만들어 준다
    // communityDetailPage: 상세 화면을 그리거나 그 화면으로 돌려보내는 일만 모아 둔 도우미
    private final CommunityReactionService communityReactionService;
    private final CommunityDetailPage communityDetailPage;

    // 좋아요 누르기
    // 예시 요청: POST /community/37/likes?comments=50&replies=12
    // postId: \\d+ 는 {postId}가 만족해야 하는 정규식 조건. \d+ 는 숫자 한 자리 이상이라는 뜻
    @PostMapping("/community/{postId:\\d+}/likes")
    public String addLike(
            // {postId} = "37" 을 잡고 long 으로 변환해서 postId = 37L로 넘긴다
            @PathVariable("postId") long postId,

            // 좋아요와 상관없는 값들을 굳이 받아 두는 이유: 아래 redirect에 그대로 다시 붙여서
            // 댓글을 펼쳐 보던 상태 그대로 상세 화면으로 돌아가게 하려는 것이다
            @RequestParam(name = "comments", required = false) String comments,
            @RequestParam(name = "replies", required = false) String replies,

            // @AuthenticationPrincipal: Spring Security가 현재 인증 객체의 Principal을 꺼내서 넣어준다
            // 좋아요는 로그인해야 되는 기능이라 여기서는 null을 따로 다루지 않는다
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        // 같은 사람이 두 번 눌러도 하나로 유지하는 판단은 Service가 한다
        communityReactionService.addLike(postId, memberDetails.getMemberId());

        // "redirect:/community/37?comments=50&replies=12" 같은 문자열을 만들어 돌려준다
        // POST 뒤에 redirect를 두는 이유: 새로고침해도 좋아요가 다시 눌리지 않게 하려고
        return communityDetailPage.redirect(postId, comments, replies);
    }

    // 좋아요 취소. 위와 대칭이라 Service 호출 한 줄만 다르다
    // 예시 요청: POST /community/37/likes/delete
    @PostMapping("/community/{postId:\\d+}/likes/delete")
    public String removeLike(
            @PathVariable("postId") long postId,
            @RequestParam(name = "comments", required = false) String comments,
            @RequestParam(name = "replies", required = false) String replies,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        communityReactionService.removeLike(postId, memberDetails.getMemberId());

        return communityDetailPage.redirect(postId, comments, replies);
    }

    // 게시글 신고
    // 1. 신고 가능한 글인지 확인 -> 2. 폼 검증 실패면 상세 화면 다시 그리기 -> 3. 접수하고 redirect
    @PostMapping("/community/{postId:\\d+}/reports")
    public String report(
            @PathVariable("postId") long postId,
            @RequestParam(name = "comments", required = false) String comments,
            @RequestParam(name = "replies", required = false) String replies,

            // @Valid: ReportForm에 붙은 검증 어노테이션을 스프링이 대신 검사한다
            // 결과는 예외가 아니라 바로 뒤의 BindingResult에 담기므로 두 매개변수는 붙어 있어야 한다
            @Valid @ModelAttribute("reportForm") ReportForm reportForm,
            BindingResult bindingResult,

            // 검증에 실패하면 상세 화면을 통째로 다시 그리는데, 그 화면에는 댓글 입력 폼도 들어 있다
            // 그래서 신고와 상관없어 보이는 commentForm까지 여기서 함께 받아 둔다
            @ModelAttribute("commentForm") CommentForm commentForm,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        long memberId = memberDetails.getMemberId();

        // 검증보다 먼저 부른다: 내 글인지·이미 신고했는지 같은 판단은 Service가 하고,
        // 실패하면 아래에서 화면을 다시 그릴 재료로도 이 post가 그대로 쓰인다
        PostDetailView post = communityReactionService.getReportablePost(postId, memberId);

        if (bindingResult.hasErrors()) {
            // redirect가 아니라 그 자리에서 렌더링 -> 입력한 신고 사유와 오류 문구가 화면에 남는다
            return communityDetailPage.render(model, post, memberId, comments, replies);
        }

        communityReactionService.reportPost(postId, reportForm, memberId);

        // addFlashAttribute: redirect 뒤 화면까지만 살아 있는 일회성 값
        redirectAttributes.addFlashAttribute("successMessage", "신고를 접수했습니다.");

        return communityDetailPage.redirect(postId, comments, replies);
    }
}
