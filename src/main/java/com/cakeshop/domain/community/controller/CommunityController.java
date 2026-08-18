package com.cakeshop.domain.community.controller;

import com.cakeshop.domain.community.dto.form.CommentForm;
import com.cakeshop.domain.community.dto.form.PostForm;
import com.cakeshop.domain.community.dto.form.ReportForm;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListView;
import com.cakeshop.domain.community.dto.view.PostSort;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.service.CommunityNoticeService;
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

    private final CommunityPostService communityPostService;
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
            return rejectCategoryOrRethrow(e, bindingResult, model, null);
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
            return rejectCategoryOrRethrow(e, bindingResult, model, postId);
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

    private String rejectCategoryOrRethrow(
            BusinessException e, BindingResult bindingResult, Model model, Long postId) {
        if (e.getErrorCode() != CommunityErrorCode.CATEGORY_NOT_FOUND) {
            throw e;
        }

        bindingResult.rejectValue(
                "categoryId", "categoryNotFound", e.getErrorCode().message());

        return prepareForm(model, postId);
    }

    private String prepareForm(Model model, Long postId) {
        model.addAttribute("categories", communityPostService.getActiveCategories());
        model.addAttribute("editingPostId", postId);

        return "customer/community/form";
    }

}
