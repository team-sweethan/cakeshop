package com.cakeshop.domain.community.controller;

import com.cakeshop.domain.community.dto.view.NoticeView;
import com.cakeshop.domain.community.service.CommunityNoticeService;
import com.cakeshop.global.common.paging.PageNavigation;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-12
 * 기능 : 커뮤니티 요청 처리
 * 설명 : CommunityNoticeController 요청을 받아 서비스 호출과 화면 이동을 처리한다.
 * ******************************
 */
@Controller
@RequiredArgsConstructor
public class CommunityNoticeController {

    private final CommunityNoticeService communityNoticeService;

    @GetMapping("/community/notices")
    public String list(
            @RequestParam(required = false) String page,
            Model model
    ) {
        PageRequest pageRequest =
                new PageRequest(parsePositiveInteger(page), PageRequest.DEFAULT_SIZE);

        PageResult<NoticeView> pageResult = communityNoticeService.getNotices(pageRequest);

        model.addAttribute("pageResult", pageResult);
        model.addAttribute(
                "pageNavigation",
                PageNavigation.of(pageResult.getPage(), pageResult.getTotalPages())
        );

        return "customer/community/notice/list";
    }

    @GetMapping("/community/notices/{noticeId:\\d+}")
    public String detail(@PathVariable("noticeId") long noticeId, Model model) {
        model.addAttribute("notice", communityNoticeService.getNotice(noticeId));

        return "customer/community/notice/detail";
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
