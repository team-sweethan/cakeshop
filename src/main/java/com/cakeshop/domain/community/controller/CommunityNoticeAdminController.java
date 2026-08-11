package com.cakeshop.domain.community.controller;

import com.cakeshop.domain.community.dto.form.NoticeForm;
import com.cakeshop.domain.community.dto.view.AdminNoticeDetailView;
import com.cakeshop.domain.community.dto.view.AdminNoticeListView;
import com.cakeshop.domain.community.service.CommunityNoticeAdminService;
import com.cakeshop.global.common.paging.PageNavigation;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.security.MemberDetails;

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
 * 작성일 : 2026-08-12
 * 기능 : 커뮤니티 요청 처리
 * 설명 : CommunityNoticeAdminController 요청을 받아 서비스 호출과 화면 이동을 처리한다.
 * ******************************
 */
@Controller
@RequiredArgsConstructor
public class CommunityNoticeAdminController {

    private final CommunityNoticeAdminService communityNoticeAdminService;

    @GetMapping("/admin/community/notices")
    public String list(
            @RequestParam(required = false) String page,
            Model model
    ) {
        PageRequest pageRequest =
                new PageRequest(parsePositiveInteger(page), PageRequest.DEFAULT_SIZE);

        PageResult<AdminNoticeListView> pageResult =
                communityNoticeAdminService.getNotices(pageRequest);

        model.addAttribute("pageResult", pageResult);
        model.addAttribute(
                "pageNavigation",
                PageNavigation.of(pageResult.getPage(), pageResult.getTotalPages())
        );

        return "admin/community/notice/list";
    }

    @GetMapping("/admin/community/notices/new")
    public String newForm(@ModelAttribute("noticeForm") NoticeForm noticeForm, Model model) {
        return prepareForm(model, null);
    }

    @PostMapping("/admin/community/notices/new")
    public String create(
            @Valid @ModelAttribute("noticeForm") NoticeForm noticeForm,
            BindingResult bindingResult,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return prepareForm(model, null);
        }

        communityNoticeAdminService.createNotice(noticeForm, memberDetails.getMemberId());

        redirectAttributes.addFlashAttribute("successMessage", "공지사항을 등록했습니다.");

        return "redirect:/admin/community/notices";
    }

    @GetMapping("/admin/community/notices/{noticeId:\\d+}/edit")
    public String editForm(
            @PathVariable("noticeId") long noticeId,
            @ModelAttribute("noticeForm") NoticeForm noticeForm,
            Model model
    ) {
        AdminNoticeDetailView notice = communityNoticeAdminService.getNotice(noticeId);

        noticeForm.setTitle(notice.title());
        noticeForm.setContent(notice.content());
        noticeForm.setStartsAt(notice.startsAt());
        noticeForm.setEndsAt(notice.endsAt());

        return prepareForm(model, notice);
    }

    @PostMapping("/admin/community/notices/{noticeId:\\d+}/edit")
    public String update(
            @PathVariable("noticeId") long noticeId,
            @Valid @ModelAttribute("noticeForm") NoticeForm noticeForm,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return prepareForm(model, communityNoticeAdminService.getNotice(noticeId));
        }

        communityNoticeAdminService.updateNotice(noticeId, noticeForm);

        redirectAttributes.addFlashAttribute("successMessage", "공지사항을 수정했습니다.");

        return "redirect:/admin/community/notices";
    }

    @PostMapping("/admin/community/notices/{noticeId:\\d+}/delete")
    public String delete(
            @PathVariable("noticeId") long noticeId,
            RedirectAttributes redirectAttributes
    ) {
        communityNoticeAdminService.deleteNotice(noticeId);

        redirectAttributes.addFlashAttribute("successMessage", "공지사항을 삭제했습니다.");

        return "redirect:/admin/community/notices";
    }

    private String prepareForm(Model model, AdminNoticeDetailView notice) {
        model.addAttribute("notice", notice);

        return "admin/community/notice/form";
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
