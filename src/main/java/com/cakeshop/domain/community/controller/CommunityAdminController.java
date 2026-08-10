package com.cakeshop.domain.community.controller;

import com.cakeshop.domain.community.dto.form.BlockForm;
import com.cakeshop.domain.community.dto.view.AdminPostDetailView;
import com.cakeshop.domain.community.dto.view.AdminPostListView;
import com.cakeshop.domain.community.dto.view.AdminPostSort;
import com.cakeshop.domain.community.dto.view.ReportView;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.service.CommunityAdminService;
import com.cakeshop.domain.community.service.CommunityService;
import com.cakeshop.global.common.paging.PageNavigation;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.security.MemberDetails;

import jakarta.validation.Valid;

import java.util.List;

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
 * 설명 : CommunityAdminController 요청을 받아 서비스 호출과 화면 이동을 처리한다.
 * ******************************
 */
@Controller
public class CommunityAdminController {

    private final CommunityAdminService communityAdminService;
    private final CommunityService communityService;

    public CommunityAdminController(
            CommunityAdminService communityAdminService, CommunityService communityService) {
        this.communityAdminService = communityAdminService;
        this.communityService = communityService;
    }

    @GetMapping("/admin/community")
    public String list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String page,
            Model model
    ) {
        PostStatus selectedStatus = parseStatus(status);
        AdminPostSort selectedSort = AdminPostSort.from(sort);

        PageRequest pageRequest =
                new PageRequest(parsePositiveInteger(page), PageRequest.DEFAULT_SIZE);

        PageResult<AdminPostListView> pageResult =
                communityAdminService.getPosts(selectedStatus, selectedSort, pageRequest);

        model.addAttribute("pageResult", pageResult);
        model.addAttribute(
                "pageNavigation",
                PageNavigation.of(pageResult.getPage(), pageResult.getTotalPages())
        );
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("selectedSort", selectedSort);

        return "admin/community/list";
    }

    @GetMapping("/admin/community/{postId:\\d+}")
    public String detail(
            @PathVariable("postId") long postId,
            @RequestParam(required = false) String comments,
            @ModelAttribute("blockForm") BlockForm blockForm,
            Model model
    ) {
        return prepareDetail(
                model, communityAdminService.getPostDetail(postId), parsePositiveInteger(comments));
    }

    @PostMapping("/admin/community/{postId:\\d+}/block")
    public String block(
            @PathVariable("postId") long postId,
            @Valid @ModelAttribute("blockForm") BlockForm blockForm,
            BindingResult bindingResult,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return prepareDetail(model, communityAdminService.getPostDetail(postId), null);
        }

        communityAdminService.blockPost(
                postId, blockForm.getReason(), memberDetails.getMemberId());

        redirectAttributes.addFlashAttribute("successMessage", "게시글을 차단했습니다.");

        return "redirect:/admin/community/" + postId;
    }

    @PostMapping("/admin/community/{postId:\\d+}/unblock")
    public String unblock(
            @PathVariable("postId") long postId,
            RedirectAttributes redirectAttributes
    ) {
        communityAdminService.unblockPost(postId);

        redirectAttributes.addFlashAttribute("successMessage", "차단을 해제했습니다.");

        return "redirect:/admin/community/" + postId;
    }

    @PostMapping("/admin/community/{postId:\\d+}/reports/reject")
    public String rejectReports(
            @PathVariable("postId") long postId,
            RedirectAttributes redirectAttributes
    ) {
        communityAdminService.rejectReports(postId);

        redirectAttributes.addFlashAttribute("successMessage", "신고를 기각했습니다.");

        return "redirect:/admin/community/" + postId;
    }

    private String prepareDetail(
            Model model, AdminPostDetailView post, Integer commentLimit) {
        List<ReportView> reports = communityAdminService.getReports(post.id());

        model.addAttribute("post", post);
        model.addAttribute("reports", reports);
        model.addAttribute("pendingReportCount", reports.stream().filter(ReportView::isPending).count());
        model.addAttribute("commentSection", communityService.getComments(post.id(), commentLimit));

        return "admin/community/detail";
    }

    private PostStatus parseStatus(String value) {
        if (value == null) {
            return null;
        }

        for (PostStatus status : PostStatus.values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }

        return null;
    }

    private Integer parsePositiveInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 && parsed <= Integer.MAX_VALUE ? (int) parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
