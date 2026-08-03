package com.cakeshop.domain.community.controller;

import com.cakeshop.domain.community.dto.form.CommentForm;
import com.cakeshop.domain.community.dto.form.PostForm;
import com.cakeshop.domain.community.dto.view.CommentSectionView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListView;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.service.CommunityService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.common.paging.PageNavigation;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.security.MemberDetails;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// /community 목록·상세·작성·수정·삭제. 목록과 상세 GET만 공개다(DOMAIN.md 5).
@Controller
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    /**
     * 커뮤니티 목록 화면. 페이지 크기는 20 고정이며 사용자가 바꿀 수 없어서(DOMAIN.md 6.1)
     * size 파라미터를 받지 않는다.
     */
    @GetMapping("/community")
    public String list(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String page,
            Model model
    ) {
        // 목록은 비로그인도 열 수 있는 공개 화면이다. 주소에 이상한 값이 들어와도
        // 오류 페이지 대신 기본 목록을 보여준다.
        Long selectedCategoryId = parsePositiveLong(categoryId);

        PageRequest pageRequest = new PageRequest(
                parsePositiveInteger(page),
                PageRequest.DEFAULT_SIZE
        );

        PageResult<PostListView> pageResult =
                communityService.getPosts(selectedCategoryId, pageRequest);

        model.addAttribute("pageResult", pageResult);
        model.addAttribute(
                "pageNavigation",
                PageNavigation.of(pageResult.getPage(), pageResult.getTotalPages())
        );
        model.addAttribute("categories", communityService.getActiveCategories());
        model.addAttribute("selectedCategoryId", selectedCategoryId);

        return "customer/community/list";
    }

    /**
     * 게시글 상세 화면. 비로그인도 열 수 있어서 memberDetails가 null일 수 있다.
     * 경로는 SecurityConfig의 /community/{id:\\d+} 공개 규칙에 맞춰 숫자 식별자만 받는다.
     *
     * comments는 "더 보기"가 실어 보내는 값으로, 댓글을 몇 건까지 보여줄지다. 주소에 담아
     * 두면 새로고침·뒤로가기에서 펼친 상태가 유지되고 JS 없이도 동작한다.
     */
    @GetMapping("/community/{postId:\\d+}")
    public String detail(
            @PathVariable("postId") long postId,
            @RequestParam(name = "comments", required = false) String comments,
            @ModelAttribute("commentForm") CommentForm commentForm,
            @AuthenticationPrincipal MemberDetails memberDetails,
            HttpServletRequest request,
            Model model
    ) {
        // 소유권 판단 기준은 요청 파라미터가 아니라 인증 정보다(AGENTS.md).
        Long viewerId = memberDetails == null ? null : memberDetails.getMemberId();

        PostDetailView post =
                communityService.getPostDetail(postId, viewerId, viewerKeyOf(viewerId, request));

        return prepareDetail(model, post, viewerId, comments);
    }

    /**
     * 조회수 중복 방지에 쓸 조회자 키(DOMAIN.md 6.2). 회원이면 회원 번호, 비로그인이면
     * 세션 id다.
     *
     * 요청에서 받지 않는다. 클라이언트가 정하는 값이면 매번 다른 키를 보내는 것만으로
     * 중복 방지가 사라진다.
     *
     * 비로그인에게 세션이 없으면 여기서 만들어진다. 상세는 공개 화면이라 세션 없이도
     * 열리는데, 키가 없으면 셀 수가 없다. 다만 이 방어는 <b>사람의 반복 조회까지</b>다 —
     * 쿠키를 받지 않는 클라이언트는 매 요청이 새 세션이라 그대로 뚫린다(PLAN.md R12).
     */
    private String viewerKeyOf(Long viewerId, HttpServletRequest request) {
        if (viewerId != null) {
            return "M:" + viewerId;
        }

        return "S:" + request.getSession().getId();
    }

    /**
     * 댓글을 저장하고 상세로 보낸다. 검증에 실패하면 입력을 유지한 채 상세를 다시 그린다.
     *
     * 게시글 상태를 검증 실패보다 먼저 본다. 순서가 뒤집히면 삭제된 글 번호로 빈 댓글을
     * 보냈을 때 그 글의 상세가 200으로 열린다(조각 2의 수정 화면과 같은 실수다).
     */
    @PostMapping("/community/{postId:\\d+}/comments")
    public String addComment(
            @PathVariable("postId") long postId,
            @RequestParam(name = "comments", required = false) String comments,
            @Valid @ModelAttribute("commentForm") CommentForm commentForm,
            BindingResult bindingResult,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model
    ) {
        long memberId = memberDetails.getMemberId();

        PostDetailView post = communityService.getCommentablePost(postId, memberId);

        if (bindingResult.hasErrors()) {
            // 여기서 getPostDetail을 부르면 잘못 보낸 댓글마다 조회수가 오른다.
            // comments를 그대로 넘긴다 — 어디로 간 것이 아니라 제자리이므로 접으면 안 된다.
            return prepareDetail(model, post, memberId, comments);
        }

        communityService.addComment(postId, commentForm, memberId);

        return "redirect:/community/" + postId;
    }

    /**
     * 댓글을 삭제하고 상세로 보낸다.
     * GET이 아니라 POST인 것은 게시글 삭제와 같은 이유다.
     *
     * 성공 메시지를 남기지 않는다. 지운 자리에 "삭제된 댓글입니다"가 그대로 보이므로
     * 결과가 화면에 이미 있다 — 목록에서 흔적 없이 사라지는 게시글 삭제와 다르다.
     *
     * 그래서 <b>펼친 상태를 유지한 채</b> 돌려보낸다. 자리 표시가 유일한 신호인데 20건으로
     * 접어 버리면 최신 20건 밖의 댓글은 그 자리가 화면 밖으로 나가고, 사용자에게는 삭제가
     * 안 된 것과 구분되지 않는다. 작성은 반대로 접어도 된다 — 새 댓글은 언제나 최신 20건
     * 안에 있다(screens/detail.md).
     */
    @PostMapping("/community/{postId:\\d+}/comments/{commentId:\\d+}/delete")
    public String deleteComment(
            @PathVariable("postId") long postId,
            @PathVariable("commentId") long commentId,
            @RequestParam(name = "comments", required = false) String comments,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        communityService.deleteComment(postId, commentId, memberDetails.getMemberId());

        return redirectToDetail(postId, comments);
    }

    /**
     * 상세로 되돌리되 펼친 댓글 수를 유지한다.
     *
     * 받은 문자열을 그대로 잇지 않고 정수로 바꿔 다시 쓴다. 주소에 들어갈 값이므로
     * 사용자가 보낸 문자열이 그대로 나가면 안 된다. 기본값이면 아예 붙이지 않는다 —
     * 평범한 삭제에까지 주소가 길어질 이유가 없다.
     */
    private String redirectToDetail(long postId, String comments) {
        int limit = CommentSectionView.clampLimit(parsePositiveInteger(comments));

        if (limit == CommentSectionView.DEFAULT_LIMIT) {
            return "redirect:/community/" + postId;
        }

        return "redirect:/community/" + postId + "?comments=" + limit;
    }

    /**
     * 상세 화면에 필요한 모델을 채운다. comments는 "더 보기"가 보낸 값이며 없으면 null이다.
     *
     * canEdit·canComment는 안내일 뿐이고 실제로 막는 것은 Service다. 작성자는 이 화면에서
     * 주소를 알게 되므로 버튼 없이 요청만 따로 보낼 수 있다.
     */
    private String prepareDetail(
            Model model, PostDetailView post, Long viewerId, String comments) {
        model.addAttribute("post", post);
        model.addAttribute("viewerId", viewerId);
        // 차단된 글은 작성자 본인만 여기까지 오지만 고칠 수도 지울 수도 없다(DOMAIN.md 4.2).
        model.addAttribute(
                "canEdit",
                viewerId != null && viewerId.equals(post.memberId()) && !post.isBlocked()
        );
        // 노출되지 않는 글에는 댓글을 달 수 없다(DOMAIN.md 4.5). 여기 오는 차단된 글은
        // 작성자 본인의 것뿐이고, 그 사람에게도 댓글 폼을 주지 않는다.
        model.addAttribute("canComment", viewerId != null && !post.isBlocked());
        model.addAttribute(
                "commentSection",
                communityService.getComments(post.id(), parsePositiveInteger(comments))
        );

        return "customer/community/detail";
    }

    /** 글쓰기 화면. */
    @GetMapping("/community/new")
    public String createForm(@ModelAttribute("form") PostForm form, Model model) {
        return prepareForm(model, null);
    }

    /** 게시글을 저장하고 상세로 보낸다. 검증에 실패하면 입력을 유지한 채 글쓰기 화면을 다시 그린다. */
    @PostMapping("/community")
    public String create(
            @Valid @ModelAttribute("form") PostForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            return prepareForm(model, null);
        }

        long postId;

        try {
            postId = communityService.createPost(form, memberDetails.getMemberId());
        } catch (BusinessException e) {
            return rejectCategoryOrRethrow(e, bindingResult, model, null);
        }

        return "redirect:/community/" + postId;
    }

    /**
     * 수정 화면. 폼에 기존 값을 채워 넣는다.
     *
     * 작성 화면과 같은 템플릿을 쓴다(선례: ProductAdminController). 수정 항목이 작성
     * 항목과 같아서 폼 마크업을 두 벌로 두면 한쪽만 고치는 일이 생긴다.
     */
    @GetMapping("/community/{postId:\\d+}/edit")
    public String editForm(
            @PathVariable("postId") long postId,
            @ModelAttribute("form") PostForm form,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model
    ) {
        PostDetailView post =
                communityService.getEditablePost(postId, memberDetails.getMemberId());

        form.setCategoryId(post.categoryId());
        form.setTitle(post.title());
        form.setContent(post.content());

        return prepareForm(model, postId);
    }

    /** 게시글을 수정하고 상세로 보낸다. 검증에 실패하면 입력을 유지한 채 수정 화면을 다시 그린다. */
    @PostMapping("/community/{postId:\\d+}/edit")
    public String edit(
            @PathVariable("postId") long postId,
            @Valid @ModelAttribute("form") PostForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model
    ) {
        // 권한을 검증 실패보다 먼저 본다. 순서가 뒤집히면 남의 글 번호로 빈 본문을 보냈을 때
        // 소유권도 상태도 확인하지 않은 채 수정 화면이 200으로 열린다. 유효한 값을 보내야
        // 그제서야 404가 나는 화면은, 열려 있는 동안 자기 글인 것처럼 보인다.
        communityService.getEditablePost(postId, memberDetails.getMemberId());

        if (bindingResult.hasErrors()) {
            return prepareForm(model, postId);
        }

        try {
            communityService.updatePost(postId, form, memberDetails.getMemberId());
        } catch (BusinessException e) {
            return rejectCategoryOrRethrow(e, bindingResult, model, postId);
        }

        return "redirect:/community/" + postId;
    }

    /**
     * 게시글을 삭제하고 목록으로 보낸다.
     * GET이 아니라 POST다. 링크 미리보기나 크롤러가 눌러서 글이 지워지면 안 된다.
     */
    @PostMapping("/community/{postId:\\d+}/delete")
    public String delete(
            @PathVariable("postId") long postId,
            @AuthenticationPrincipal MemberDetails memberDetails,
            RedirectAttributes redirectAttributes
    ) {
        communityService.deletePost(postId, memberDetails.getMemberId());

        // 삭제 결과는 목록에 남지 않는다. 알리지 않으면 지워졌는지 알 수 없다.
        redirectAttributes.addFlashAttribute("successMessage", "게시글을 삭제했습니다.");

        return "redirect:/community";
    }

    /**
     * 분류 선택 오류면 폼으로 되돌리고, 그 밖의 업무 예외는 그대로 올린다.
     * postId는 작성이면 null이다.
     *
     * 글을 쓰는 동안 관리자가 그 분류를 비활성으로 바꾸면 저장이 거절된다. 이때 예외를
     * 그대로 흘리면 GlobalExceptionHandler가 공통 4xx 화면을 그리고 쓰던 제목과 본문이
     * 사라진다. 분류는 화면에서 다시 고르면 되는 입력 오류이므로 해당 필드에 붙여
     * 돌려준다(conventions.md 9).
     *
     * 소유권·상태 오류(404·403)는 화면에서 고칠 수 없으므로 여기서 삼키지 않는다.
     */
    private String rejectCategoryOrRethrow(
            BusinessException e, BindingResult bindingResult, Model model, Long postId) {
        if (e.getErrorCode() != CommunityErrorCode.CATEGORY_NOT_FOUND) {
            throw e;
        }

        bindingResult.rejectValue(
                "categoryId", "categoryNotFound", e.getErrorCode().message());

        return prepareForm(model, postId);
    }

    /**
     * 작성·수정 화면에 공통으로 필요한 모델을 채운다. postId는 작성 화면이면 null이다.
     *
     * 분류 선택지는 목록 필터와 같은 활성 카테고리를 쓴다. 화면에 직접 적어 두면
     * 비활성 카테고리가 선택지에 남는다(DOMAIN.md 6.8).
     */
    private String prepareForm(Model model, Long postId) {
        model.addAttribute("categories", communityService.getActiveCategories());
        model.addAttribute("editingPostId", postId);

        return "customer/community/form";
    }

    /** 페이지 번호 문자열을 1 이상의 정수로 변환한다. 변환할 수 없으면 null이다. */
    private Integer parsePositiveInteger(String value) {
        Long parsed = parsePositiveLong(value);

        if (parsed == null || parsed > Integer.MAX_VALUE) {
            return null;
        }

        return parsed.intValue();
    }

    /** 식별자 문자열을 1 이상의 정수로 변환한다. 변환할 수 없으면 null이다. */
    private Long parsePositiveLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
