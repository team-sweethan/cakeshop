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

    // @RequiredArgsConstructor: final 필드 셋을 받는 생성자를 롬복이 대신 만들어 준다
    // communityPostService 는 글을 읽기만 한다 (댓글 가능한 글인지 확인 + 화면 다시 그릴 재료)
    private final CommunityPostService communityPostService;
    private final CommunityCommentService communityCommentService;
    private final CommunityDetailPage communityDetailPage;

    // 댓글·답글 작성. 답글은 replyTo 가 실려 온다
    // 예시 요청: POST /community/37/comments              -> parentCommentId = null (뿌리 댓글)
    //            POST /community/37/comments?replyTo=128  -> parentCommentId = 128L (답글)
    // 1. replyTo 파싱 -> 2. 글 확인 -> 3. 형식 검증 -> 4. 저장 -> 5. 그 댓글 자리로 redirect
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

        // parentCommentId 가 null 이 되는 경우는 둘이다
        //   replyTo 자체가 안 왔다 (replyTo == null)      -> 뿌리 댓글, 정상
        //   replyTo 는 왔는데 값이 이상하다 ("", "abc")     -> 아래 if 가 잡는다
        Long parentCommentId = CommunityRequestParams.positiveLong(replyTo);

        // 두 번째 경우를 여기서 끊는다. 안 끊으면 답글 요청이 조용히 뿌리 댓글로 저장된다
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
            long newCommentId = communityCommentService.addComment(postId, commentForm, memberId);

            // 새 댓글은 언제나 최신 20건 안에 있으므로 기본 분량으로, 그 줄로 돌아간다
            return "redirect:/community/" + postId + "#comment-" + newCommentId;
        }

        long newReplyId =
                communityCommentService.addReply(postId, parentCommentId, commentForm, memberId);

        // redirectToComment(postId, comments, replies, anchor) 로 만들어지는 주소:
        //   redirect:/community/37?replies=128#comment-131
        // 세 번째 인자에 String.valueOf(parentCommentId) 를 넣어 그 묶음을 펼친 채로 돌아간다
        return communityDetailPage.redirectToComment(
                postId, comments, String.valueOf(parentCommentId), newReplyId);
    }

    // 예시 요청: POST /community/37/comments/128/delete
    // 삭제는 폼 입력이 없으니 @Valid·BindingResult 도 없고 Model 도 안 받는다
    // 화면을 그리지 않고 주소만 돌려주기 때문이다
    @PostMapping("/community/{postId:\\d+}/comments/{commentId:\\d+}/delete")
    public String deleteComment(
            @PathVariable("postId") long postId,
            @PathVariable("commentId") long commentId,
            @RequestParam(name = "comments", required = false) String comments,
            @RequestParam(name = "replies", required = false) String replies,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        communityCommentService.deleteComment(postId, commentId, memberDetails.getMemberId());

        // 지운 줄은 사라지지 않고 "삭제된 댓글입니다"로 남는다. 그 자리로 돌아가야 결과가 보인다.
        return communityDetailPage.redirectToComment(postId, comments, replies, commentId);
    }
}
