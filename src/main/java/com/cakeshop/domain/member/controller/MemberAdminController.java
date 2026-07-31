package com.cakeshop.domain.member.controller;

import com.cakeshop.domain.member.dto.form.MemberAdminListType;
import com.cakeshop.domain.member.dto.form.MemberAdminSearchCondition;
import com.cakeshop.domain.member.dto.view.MemberAdminDetailView;
import com.cakeshop.domain.member.dto.view.MemberAdminListView;
import com.cakeshop.domain.member.entity.MemberStatus;
import com.cakeshop.domain.member.service.MemberAdminService;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MemberAdminController {

    private static final int DEFAULT_MEMBER_PAGE_SIZE = 10;

    private final MemberAdminService memberAdminService;

    public MemberAdminController(MemberAdminService memberAdminService) {
        this.memberAdminService = memberAdminService;
    }

    // 관리자 회원 목록
    @GetMapping("/admin/members")
    public String members(
            @ModelAttribute("condition")
            MemberAdminSearchCondition condition,
            BindingResult bindingResult,
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            Model model) {
        condition.setListType(MemberAdminListType.from(tab));

        if (bindingResult.hasFieldErrors("status")) {
            condition.setStatus(null);
        }

        Integer requestedPage = parsePositiveInteger(page);
        Integer requestedSize = parsePositiveInteger(size);
        int memberPageSize =
                requestedSize == null
                        ? DEFAULT_MEMBER_PAGE_SIZE
                        : requestedSize;

        PageResult<MemberAdminListView> pageResult =
                memberAdminService.getMembers(
                        condition,
                        new PageRequest(requestedPage, memberPageSize));

        model.addAttribute("pageResult", pageResult);
        model.addAttribute(
                "memberStatuses",
                new MemberStatus[] {
                    MemberStatus.ACTIVE,
                    MemberStatus.SUSPENDED
                });

        return "admin/member/list";
    }

    // 관리자 회원 상세
    @GetMapping("/admin/members/{memberId}")
    public String memberDetail(
            @PathVariable Long memberId,
            Model model) {
        MemberAdminDetailView member =
                memberAdminService.getMemberDetail(memberId);

        model.addAttribute("member", member);
        return "admin/member/detail";
    }

    private Integer parsePositiveInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            int parsed = Integer.parseInt(value);

            return parsed > 0
                    ? parsed
                    : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
