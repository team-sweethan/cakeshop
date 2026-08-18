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
import com.cakeshop.domain.community.service.CommunityNoticeService;
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
    private final CommunityNoticeService communityNoticeService;
    private final CommunityDetailPage communityDetailPage;

    @GetMapping("/community")
    public String list(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String page,
            Model model
    ) {
        Long selectedCategoryId = CommunityRequestParams.positiveLong(categoryId);
        PostSort selectedSort = PostSort.from(sort);

        PageRequest pageRequest = new PageRequest(
                CommunityRequestParams.positiveInteger(page),
                PageRequest.DEFAULT_SIZE
        );

        PageResult<PostListView> pageResult =
                communityPostService.getPosts(selectedCategoryId, selectedSort, pageRequest);

        model.addAttribute("pageResult", pageResult);
        model.addAttribute(
                "pageNavigation",
                PageNavigation.of(pageResult.getPage(), pageResult.getTotalPages())
        );
        model.addAttribute("categories", communityPostService.getActiveCategories());
        model.addAttribute("selectedCategoryId", selectedCategoryId);
        model.addAttribute("selectedSort", selectedSort);
        model.addAttribute("sortOptions", PostSort.values());

        model.addAttribute(
                "noticeSection",
                communityNoticeService.getListSection(selectedCategoryId, pageRequest)
        );
        model.addAttribute(
                "popularSection",
                communityPostService.getPopularSection(selectedCategoryId, pageRequest)
        );

        return "customer/community/list";
    }

    @GetMapping("/community/{postId:\\d+}")
    public String detail(
            @PathVariable("postId") long postId,
            @RequestParam(name = "comments", required = false) String comments,
            @ModelAttribute("commentForm") CommentForm commentForm,
            @ModelAttribute("reportForm") ReportForm reportForm,
            @AuthenticationPrincipal MemberDetails memberDetails,
            HttpServletRequest request,
            Model model
    ) {
        Long viewerId = memberDetails == null ? null : memberDetails.getMemberId();

        PostDetailView post = comments == null
                ? communityPostService.getPostDetail(postId, viewerId, viewerKeyOf(viewerId, request))
                : communityPostService.getVisiblePost(postId, viewerId);

        return communityDetailPage.render(model, post, viewerId, comments);
    }

    private String viewerKeyOf(Long viewerId, HttpServletRequest request) {
        if (viewerId != null) {
            return "M:" + viewerId;
        }

        return "S:" + request.getSession().getId();
    }

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
