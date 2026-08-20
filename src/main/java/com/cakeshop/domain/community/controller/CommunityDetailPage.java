package com.cakeshop.domain.community.controller;

import lombok.RequiredArgsConstructor;

import com.cakeshop.domain.community.dto.view.CommentSectionView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.service.CommunityCommentService;
import com.cakeshop.domain.community.service.CommunityPostImageService;
import com.cakeshop.domain.community.service.CommunityReactionService;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

// 상세 화면 한 장을 만드는 담당. Controller 가 아니라 @Component 다 (URL 을 직접 받지 않는다)
// 하는 일은 두 가지
//   1. render/renderFocused : Model 에 화면 재료를 담고 화면 이름("customer/community/detail")을 돌려준다
//   2. redirect/redirectToComment : 이 화면으로 되돌아갈 주소 문자열을 만든다
// class 앞에 public 이 없다 = 같은 패키지(controller) 안에서만 쓸 수 있다
@Component
@RequiredArgsConstructor
class CommunityDetailPage {

    private static final String VIEW_NAME = "customer/community/detail";

    private final CommunityCommentService communityCommentService;
    private final CommunityReactionService communityReactionService;
    private final CommunityPostImageService communityPostImageService;

    // 상세 화면을 그린다
    // comments·replies 는 주소에서 온 String 이라 여기서 숫자로 바꿔 넘긴다
    //   comments = "40" -> Integer 40 (댓글을 40개까지 펼친 상태)
    //   replies  = "128" -> Long 128L (128번 댓글의 답글 묶음이 열린 상태)
    // 반환값 String 이 화면 이름이다. 스프링이 이 이름으로 템플릿 파일을 찾는다
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

    // 위 render 와 화면 이름은 같고, 재료 담는 방법만 다르다 (assembleFocused)
    // focusedCommentId 가 long(래퍼가 아닌 기본형)인 것은 "반드시 있다"는 뜻이다
    String renderFocused(
            Model model, PostDetailView post, Long viewerId, long focusedCommentId) {
        assembleFocused(model, post, viewerId, focusedCommentId);

        return VIEW_NAME;
    }

    // 상세 화면으로 되돌아가는 주소를 만든다 (앵커 없음)
    // 결과 예시: "redirect:/community/37?comments=40&replies=128"
    // "redirect:" 접두사가 붙은 문자열을 돌려주면 스프링은 화면을 그리지 않고 재요청을 시킨다
    String redirect(long postId, String comments, String replies) {
        return redirect(postId, comments, replies, null);
    }

    // 위 redirect 와 같은 주소 끝에 #comment-{id} 앵커를 붙인다
    // 결과 예시: "redirect:/community/37?replies=128#comment-131"
    // 앵커가 있으면 브라우저가 그 위치까지 스크롤해서 착지한다
    String redirectToComment(long postId, String comments, String replies, long anchorCommentId) {
        return redirect(postId, comments, replies, anchorCommentId);
    }

    // 화면 재료를 Model 에 담는다. private 이 아닌 이유는 다른 Controller 도 직접 부르기 때문
    // model.addAttribute("이름", 값) -> 템플릿에서 ${이름} 으로 꺼내 쓴다
    void assemble(Model model, PostDetailView post, Long viewerId, Integer commentLimit, Long expandedRootId) {
        assembleCommon(model, post, viewerId);

        model.addAttribute(
                "commentSection",
                communityCommentService.getComments(post.id(), commentLimit, expandedRootId)
        );
        model.addAttribute("expandedRootId", expandedRootId);

        // 이 경로에서는 강조할 댓글이 없다. null 을 담아 두면 화면이 강조 표시를 건너뛴다
        model.addAttribute("focusedCommentId", null);
    }

    // 여기부터는 private 헬퍼다

    // 주소 문자열을 조각조각 붙인다. 위의 redirect·redirectToComment 둘이 함께 쓴다
    // StringBuilder: String 을 + 로 여러 번 잇는 대신 한 통에 쌓았다가 마지막에 toString()
    // separator 변수가 하는 일: 첫 파라미터는 "?", 그다음부터는 "&"
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

    // 강조할 댓글이 정해진 경로. 그 댓글이 속한 묶음을 찾아 펼친 상태로 만든다
    // stream() 4단계: filter(펼쳐진 것만) -> map(뿌리 댓글 id 로) -> findFirst(첫 개) -> orElse(없으면 null)
    // findFirst 는 Optional<Long> 을 주므로 orElse(null) 로 값을 꺼낸다
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

    // 두 경로가 똑같이 담는 재료. 대부분은 "이 버튼을 보여줄까" 하는 true/false 다
    // canEdit / canComment / canLike / canReport 를 화면이 th:if 로 읽는다
    // 화면에서 감추는 것과 별개로 실제 차단은 Service 가 다시 검사한다
    private void assembleCommon(Model model, PostDetailView post, Long viewerId) {
        model.addAttribute("post", post);
        model.addAttribute("viewerId", viewerId);
        model.addAttribute(
                "canEdit",
                post.isAuthoredBy(viewerId) && !post.isBlocked()
        );

        // 로그인했고 차단된 글이 아니면 댓글·좋아요 둘 다 열린다
        boolean canWrite = viewerId != null && !post.isBlocked();
        model.addAttribute("canComment", canWrite);
        model.addAttribute("canLike", canWrite);

        // && 는 앞이 false 면 뒤를 아예 실행하지 않는다 -> 비로그인일 때 조회 쿼리가 안 나간다
        model.addAttribute(
                "likedByViewer",
                canWrite && communityReactionService.isLikedBy(post.id(), viewerId)
        );

        // 신고만 조건이 하나 더 붙는다 — 내 글은 신고 대상이 아니다
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
