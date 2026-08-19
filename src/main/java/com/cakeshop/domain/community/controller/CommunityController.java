package com.cakeshop.domain.community.controller;

import java.util.List;
import java.util.Map;

import com.cakeshop.domain.community.dto.form.CommentForm;
import com.cakeshop.domain.community.dto.form.PostForm;
import com.cakeshop.domain.community.dto.form.ReportForm;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListView;
import com.cakeshop.domain.community.dto.view.PostSort;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.service.CommunityPostImageService;
import com.cakeshop.domain.community.service.CommunityPostService;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.common.paging.PageNavigation;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.security.MemberDetails;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

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

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 요청 처리
 * 설명 : CommunityController 요청을 받아 서비스 호출과 화면 이동을 처리한다.
 * ******************************
 */
@Controller
@RequiredArgsConstructor
public class CommunityController {

    /** 폼으로 돌려보낼 거절과 그 오류가 붙을 입력 칸이다. 여기 없는 거절은 그대로 올라간다. */
    private static final Map<CommunityErrorCode, String> FORM_ERROR_FIELDS = Map.of(
            CommunityErrorCode.CATEGORY_NOT_FOUND, "categoryId",
            CommunityErrorCode.INVALID_IMAGE_FILE, "images",
            CommunityErrorCode.IMAGE_TOO_LARGE, "images",
            CommunityErrorCode.IMAGE_LIMIT_EXCEEDED, "images",
            CommunityErrorCode.IMAGE_NOT_FOUND, "images"
    );

    private final CommunityPostService communityPostService;
    private final CommunityPostImageService communityPostImageService;
    private final CommunityDetailPage communityDetailPage;

    // 예시 요청: GET /community?categoryId=2&keyword=케이크&sort=POPULAR&page=3
    @GetMapping("/community")
    public String list(
            // 매개변수 4개
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String page,
            Model model
    ) {
        // 사용자 입력을 프로그램이 쓰기 좋은 형태로 바꾼다
        // 브라우저에서는 값이 전부 문자열로 들어온다
        // 스프링부트 내부에서는 Long, PortSort, int 같은 의미 있는 타입으로 바꾸는 과정
        Long selectedCategoryId = CommunityRequestParams.positiveLong(categoryId);
        String selectedKeyword = CommunityRequestParams.keyword(keyword);
        PostSort selectedSort = PostSort.from(sort);

        // PageRequest는 "몇 페이지 정보를 몇 개씩 조회할지"에 대한 정보를 담은 객체
        PageRequest pageRequest = new PageRequest(
                CommunityRequestParams.positiveInteger(page),
                PageRequest.DEFAULT_SIZE
        );

        // PageResult<PostListView>: PostListView 여러 개와 페이지 정보를 한 번에 담는 상자
            /*
                public class PageResult<T> {
                    private final List<T> content;
                    ...
                }
            */
        // T는 정해지지 않은 타입: 어떤 타입이든 넣을 수 있는 페이징 결과 상자
        // 아래 구문에서는 T = PostListView -> private final List<PostListView> content
        // PostListView 목록을 담고 있는 PageResult 타입의 변수 pageResult
        /*
            PageResult<PostListView>
            ├─ content : List<PostListView>
            │
            │   ├─ PostListView
            │   │    ├─ id
            │   │    ├─ categoryName
            │   │    ├─ title
            │   │    ├─ authorNickname
            │   │    ├─ viewCount
            │   │    ├─ likeCount
            │   │    └─ createdAt
            │   │
            │   ├─ PostListView
            │   ├─ PostListView
            │   └─ ...
            │
            ├─ page : 현재 페이지
            ├─ size : 한 페이지 크기
            ├─ totalElements : 전체 게시글 수
            └─ totalPages : 전체 페이지 수
        */
        PageResult<PostListView> pageResult =
                // public PageResult<PostListView> getPosts(categoryId, keyword, sort, pageRequest)
                communityPostService.getPosts(
                        selectedCategoryId, selectedKeyword, selectedSort, pageRequest);

        // Model에 화면 재료 담기
        // model.addAttribute("타임리프에서 쓰일 변수 이름", controller에서 사용되는 객체 이름)
        model.addAttribute("pageResult", pageResult);
        model.addAttribute(
                "pageNavigation",
                PageNavigation.of(pageResult.getPage(), pageResult.getTotalPages())
        );
        model.addAttribute("categories", communityPostService.getActiveCategories());
        model.addAttribute("selectedCategoryId", selectedCategoryId);
        model.addAttribute("selectedKeyword", selectedKeyword);
        model.addAttribute("selectedSort", selectedSort);
        model.addAttribute("sortOptions", PostSort.values());

        model.addAttribute(
                "popularSection",
                communityPostService.getPopularSection(
                        selectedCategoryId, selectedKeyword, pageRequest)
        );

        // Thymeleaf HTML 반환
        return "customer/community/list";
    }

    // 게시글 상세 조회
    // postId: \\d+ 는 {postId}가 만족해야 하는 정규식 조건
    // \d+: 숫자 한 자리 이상이라는 뜻
    @GetMapping("/community/{postId:\\d+}")
    public String detail(
            // {postId} = "37" 을 잡고 long 으로 변환해서 postId = 37L로 넘긴다
            @PathVariable("postId") long postId,
            @RequestParam(name = "comments", required = false) String comments,
            @RequestParam(name = "replies", required = false) String replies,

            // CommentForm: 사용자가 댓글을 입력할 때 사용할 폼 데이터 객체
            // 즉, 상세 페이지에서 댓글 입력 폼이 사용할 객체를 준비한다
            @ModelAttribute("commentForm") CommentForm commentForm,

            // ReportForm: 신고 폼
            @ModelAttribute("reportForm") ReportForm reportForm,

            /*
                1. 로그인 성공
                2. Spring Security
                3. Authentication 생성
                4. SecurityContext에 저장
                5. principal 에 MemberDetails 존재
                6. @AuthenticationPrincipal: Spring Security가 현재 인증 객체의 Principal을 꺼내서 넣어준다
            */
            // MemberDetails는 memberId, displayName을 추가로 요구함
            // 즉, Spring Security User에서 상속하는
                // username = "abc@test.com"
                // password = "..."
                // authorities = [ROLE_USER]
            // 외에
                // memberId = 37
                // displayName = "현규"
            // 가 추가된다
            @AuthenticationPrincipal MemberDetails memberDetails,

            // 현재 들어온 HTTP 요청 자체를 표현하는 객체
            // 브라우저가 보낸 요청과 관련된 여러 정보를 가짐
                // request.getSession()
                // request.getHeader(...)
                // request.getRequestURI()
                // request.getMethod()
                // request.getCookies()
            // 실제로 viewerKeyOf(viewerId, request) 에서 request.getSession().getId()로 세션 ID를 가져온다
            // 따라서 request를 받는 이유: 비로그인 사용자를 구분할 세션ID가 필요하기 때문이다
            HttpServletRequest request,
            Model model
    ) {
        // 로그인하지 않은 사용자는: viewerId = null
        Long viewerId = memberDetails == null ? null : memberDetails.getMemberId();

        // 댓글 더 보기처럼 답글 펼치기도 이미 보고 있는 글 안에서의 이동이라 조회로 세지 않는다
        PostDetailView post = comments == null && replies == null
                ? communityPostService.getPostDetail(postId, viewerId, viewerKeyOf(viewerId, request))
                : communityPostService.getVisiblePost(postId, viewerId);

        // prepareDetail 안에서 model.addAttribute(...) 가 여러개 있음
        return communityDetailPage.render(model, post, viewerId, comments, replies);
    }

    /*
     * 알림이 가리키는 댓글을 여는 서버 렌더링 경로다. 일반 GET 한 번으로 상세 전체를 만들고,
     * 답글이면 Service가 부모 묶음을 펼친다. 이미 보고 있는 글 안의 이동이라 조회수는 올리지 않는다.
     */
    @GetMapping("/community/{postId:\\d+}/comments/{commentId:\\d+}")
    public String commentDetail(
            @PathVariable("postId") long postId,
            @PathVariable("commentId") long commentId,
            @ModelAttribute("commentForm") CommentForm commentForm,
            @ModelAttribute("reportForm") ReportForm reportForm,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model
    ) {
        Long viewerId = memberDetails == null ? null : memberDetails.getMemberId();
        PostDetailView post = communityPostService.getVisiblePost(postId, viewerId);

        return communityDetailPage.renderFocused(model, post, viewerId, commentId);
    }

    private String viewerKeyOf(Long viewerId, HttpServletRequest request) {
        if (viewerId != null) {
            return "M:" + viewerId;
        }

        return "S:" + request.getSession().getId();
    }

    // 새로운 포스트 작성
    @GetMapping("/community/new")
    public String createForm(@ModelAttribute("form") PostForm form, Model model) {
        return prepareForm(model, null);
    }

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
            postId = communityPostService.createPost(form, memberDetails.getMemberId());
        } catch (BusinessException e) {
            return rejectFormErrorOrRethrow(e, bindingResult, model, null);
        }

        return "redirect:/community/" + postId;
    }

    @GetMapping("/community/{postId:\\d+}/edit")
    public String editForm(
            @PathVariable("postId") long postId,
            @ModelAttribute("form") PostForm form,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model
    ) {
        PostDetailView post =
                communityPostService.getEditablePost(postId, memberDetails.getMemberId());

        form.setCategoryId(post.categoryId());
        form.setTitle(post.title());
        form.setContent(post.content());

        return prepareForm(model, postId);
    }

    @PostMapping("/community/{postId:\\d+}/edit")
    public String edit(
            @PathVariable("postId") long postId,
            @Valid @ModelAttribute("form") PostForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model
    ) {
        communityPostService.getEditablePost(postId, memberDetails.getMemberId());

        if (bindingResult.hasErrors()) {
            return prepareForm(model, postId);
        }

        try {
            communityPostService.updatePost(postId, form, memberDetails.getMemberId());
        } catch (BusinessException e) {
            return rejectFormErrorOrRethrow(e, bindingResult, model, postId);
        }

        return "redirect:/community/" + postId;
    }

    @PostMapping("/community/{postId:\\d+}/delete")
    public String delete(
            @PathVariable("postId") long postId,
            @AuthenticationPrincipal MemberDetails memberDetails,
            RedirectAttributes redirectAttributes
    ) {
        communityPostService.deletePost(postId, memberDetails.getMemberId());

        redirectAttributes.addFlashAttribute("successMessage", "게시글을 삭제했습니다.");

        return "redirect:/community";
    }

    /*
     * 폼에서 고칠 수 있는 거절은 폼으로 돌려보내고 나머지는 그대로 올린다.
     *
     * <p>첨부 오류가 여기 있는 이유는 장수 상한이 폼 혼자 알 수 없는 값이기 때문이다 — 이미
     * 붙어 있는 장수와 함께 세야 해서 Service 가 판단하고, 그 결과가 오류 화면이 아니라 입력한
     * 제목·본문이 살아 있는 폼으로 돌아와야 한다.
     */
    private String rejectFormErrorOrRethrow(
            BusinessException e, BindingResult bindingResult, Model model, Long postId) {
        String field = FORM_ERROR_FIELDS.get(e.getErrorCode());

        if (field == null) {
            throw e;
        }

        bindingResult.rejectValue(field, e.getErrorCode().code(), e.getErrorCode().message());

        return prepareForm(model, postId);
    }

    private String prepareForm(Model model, Long postId) {
        model.addAttribute("categories", communityPostService.getActiveCategories());
        model.addAttribute("editingPostId", postId);
        model.addAttribute(
                "postImages",
                postId == null ? List.of() : communityPostImageService.getImages(postId)
        );

        return "customer/community/form";
    }

}
