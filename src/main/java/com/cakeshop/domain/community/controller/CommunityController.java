package com.cakeshop.domain.community.controller;

import com.cakeshop.domain.community.dto.form.CommentForm;
import com.cakeshop.domain.community.dto.form.PostForm;
import com.cakeshop.domain.community.dto.form.ReportForm;
import com.cakeshop.domain.community.dto.view.CommentSectionView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListView;
import com.cakeshop.domain.community.dto.view.PostSort;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.service.CommunityCommentService;
import com.cakeshop.domain.community.service.CommunityNoticeService;
import com.cakeshop.domain.community.service.CommunityService;
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

    private final CommunityService communityService;
    private final CommunityCommentService communityCommentService;
    private final CommunityNoticeService communityNoticeService;

    @GetMapping("/community")
    public String list(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String page,
            Model model
    ) {
        Long selectedCategoryId = parsePositiveLong(categoryId);
        PostSort selectedSort = PostSort.from(sort);

        PageRequest pageRequest = new PageRequest(
                parsePositiveInteger(page),
                PageRequest.DEFAULT_SIZE
        );

        PageResult<PostListView> pageResult =
                communityService.getPosts(selectedCategoryId, selectedSort, pageRequest);

        model.addAttribute("pageResult", pageResult);
        model.addAttribute(
                "pageNavigation",
                PageNavigation.of(pageResult.getPage(), pageResult.getTotalPages())
        );
        model.addAttribute("categories", communityService.getActiveCategories());
        model.addAttribute("selectedCategoryId", selectedCategoryId);
        model.addAttribute("selectedSort", selectedSort);
        model.addAttribute("sortOptions", PostSort.values());

        model.addAttribute(
                "noticeSection",
                communityNoticeService.getListSection(selectedCategoryId, pageRequest)
        );
        model.addAttribute(
                "popularSection",
                communityService.getPopularSection(selectedCategoryId, pageRequest)
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
                ? communityService.getPostDetail(postId, viewerId, viewerKeyOf(viewerId, request))
                : communityService.getVisiblePost(postId, viewerId);

        return prepareDetail(model, post, viewerId, comments);
    }

    private String viewerKeyOf(Long viewerId, HttpServletRequest request) {
        if (viewerId != null) {
            return "M:" + viewerId;
        }

        return "S:" + request.getSession().getId();
    }

    @PostMapping("/community/{postId:\\d+}/comments")
    public String addComment(
            @PathVariable("postId") long postId,
            @RequestParam(name = "comments", required = false) String comments,
            @Valid @ModelAttribute("commentForm") CommentForm commentForm,
            BindingResult bindingResult,
            @ModelAttribute("reportForm") ReportForm reportForm,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model
    ) {
        long memberId = memberDetails.getMemberId();

        PostDetailView post = communityService.getCommentablePost(postId, memberId);

        if (bindingResult.hasErrors()) {
            return prepareDetail(model, post, memberId, comments);
        }

        communityCommentService.addComment(postId, commentForm, memberId);

        return "redirect:/community/" + postId;
    }

    @PostMapping("/community/{postId:\\d+}/comments/{commentId:\\d+}/delete")
    public String deleteComment(
            @PathVariable("postId") long postId,
            @PathVariable("commentId") long commentId,
            @RequestParam(name = "comments", required = false) String comments,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        communityCommentService.deleteComment(postId, commentId, memberDetails.getMemberId());

        return redirectToDetail(postId, comments);
    }

    @PostMapping("/community/{postId:\\d+}/likes")
    public String addLike(
            @PathVariable("postId") long postId,
            @RequestParam(name = "comments", required = false) String comments,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        communityService.addLike(postId, memberDetails.getMemberId());

        return redirectToDetail(postId, comments);
    }

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

        PostDetailView post = communityService.getReportablePost(postId, memberId);

        if (bindingResult.hasErrors()) {
            return prepareDetail(model, post, memberId, comments);
        }

        communityService.reportPost(postId, reportForm, memberId);

        redirectAttributes.addFlashAttribute("successMessage", "신고를 접수했습니다.");

        return redirectToDetail(postId, comments);
    }

    @PostMapping("/community/{postId:\\d+}/likes/delete")
    public String removeLike(
            @PathVariable("postId") long postId,
            @RequestParam(name = "comments", required = false) String comments,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        communityService.removeLike(postId, memberDetails.getMemberId());

        return redirectToDetail(postId, comments);
    }

    private String redirectToDetail(long postId, String comments) {
        int limit = CommentSectionView.clampLimit(parsePositiveInteger(comments));

        if (limit == CommentSectionView.DEFAULT_LIMIT) {
            return "redirect:/community/" + postId;
        }

        return "redirect:/community/" + postId + "?comments=" + limit;
    }

    private String prepareDetail(
            Model model, PostDetailView post, Long viewerId, String comments) {
        model.addAttribute("post", post);
        model.addAttribute("viewerId", viewerId);
        model.addAttribute(
                "canEdit",
                viewerId != null && viewerId.equals(post.memberId()) && !post.isBlocked()
        );

        boolean canWrite = viewerId != null && !post.isBlocked();
        model.addAttribute("canComment", canWrite);
        model.addAttribute("canLike", canWrite);

        model.addAttribute(
                "likedByViewer",
                canWrite && communityService.isLikedBy(post.id(), viewerId)
        );

        boolean canReport =
                viewerId != null && !viewerId.equals(post.memberId()) && !post.isBlocked();
        model.addAttribute("canReport", canReport);

        model.addAttribute(
                "alreadyReported",
                canReport && communityService.isReportedBy(post.id(), viewerId)
        );

        model.addAttribute(
                "commentSection",
                communityCommentService.getComments(post.id(), parsePositiveInteger(comments))
        );

        return "customer/community/detail";
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
            postId = communityService.createPost(form, memberDetails.getMemberId());
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
                communityService.getEditablePost(postId, memberDetails.getMemberId());

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

    @PostMapping("/community/{postId:\\d+}/delete")
    public String delete(
            @PathVariable("postId") long postId,
            @AuthenticationPrincipal MemberDetails memberDetails,
            RedirectAttributes redirectAttributes
    ) {
        communityService.deletePost(postId, memberDetails.getMemberId());

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
        model.addAttribute("categories", communityService.getActiveCategories());
        model.addAttribute("editingPostId", postId);

        return "customer/community/form";
    }

    private Integer parsePositiveInteger(String value) {
        Long parsed = parsePositiveLong(value);

        if (parsed == null || parsed > Integer.MAX_VALUE) {
            return null;
        }

        return parsed.intValue();
    }

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
