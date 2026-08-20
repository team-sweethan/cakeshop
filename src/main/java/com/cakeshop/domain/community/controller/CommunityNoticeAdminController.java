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

    // @RequiredArgsConstructor(lombok)가 이 final 필드를 받는 생성자를 만들고, 스프링이 그리로 빈을 넣어 준다
    private final CommunityNoticeAdminService communityNoticeAdminService;

    // 예시 요청: GET /admin/community/notices?page=2
    @GetMapping("/admin/community/notices")
    public String list(
            // page를 String으로 받는다: "abc" 같은 값이 와도 400 대신 아래에서 null(= 1페이지)로 흘려보내려고
            @RequestParam(required = false) String page,
            Model model
    ) {
        // PageRequest는 "몇 페이지를 몇 개씩 볼지"를 담은 객체다
        PageRequest pageRequest =
                new PageRequest(parsePositiveInteger(page), PageRequest.DEFAULT_SIZE);

        // PageResult<AdminNoticeListView>: AdminNoticeListView 여러 개와 페이지 정보를 함께 담는 상자
        // T = AdminNoticeListView -> private final List<AdminNoticeListView> content
        PageResult<AdminNoticeListView> pageResult =
                communityNoticeAdminService.getNotices(pageRequest);

        // Model에 화면 재료 담기
        // model.addAttribute("타임리프에서 쓰일 변수 이름", controller에서 사용되는 객체 이름)
        model.addAttribute("pageResult", pageResult);

        // PageNavigation.of(현재 페이지, 전체 페이지): 화면 아래 [1][2][3] 묶음을 계산해 준다
        model.addAttribute(
                "pageNavigation",
                PageNavigation.of(pageResult.getPage(), pageResult.getTotalPages())
        );

        // Thymeleaf HTML 반환 (templates/admin/community/notice/list.html)
        return "admin/community/notice/list";
    }

    // 등록 폼 화면. 빈 NoticeForm을 @ModelAttribute로 받아 두면 타임리프가 th:object로 그걸 잡는다
    // 두 번째 인자 null = "고치는 중인 공지가 없다" -> 폼이 등록 모드로 그려진다
    @GetMapping("/admin/community/notices/new")
    public String newForm(@ModelAttribute("noticeForm") NoticeForm noticeForm, Model model) {
        return prepareForm(model, null);
    }

    // 1. 폼 검증 -> 2. 실패면 폼 다시 그리기 -> 3. 성공이면 등록하고 목록으로 redirect
    @PostMapping("/admin/community/notices/new")
    public String create(
            // @Valid: NoticeForm에 붙은 검증 어노테이션을 스프링이 대신 검사한다
            // 결과는 예외가 아니라 바로 뒤의 BindingResult에 담기므로 두 매개변수는 붙어 있어야 한다
            @Valid @ModelAttribute("noticeForm") NoticeForm noticeForm,
            BindingResult bindingResult,

            // @AuthenticationPrincipal: Spring Security가 현재 인증 객체의 Principal을 꺼내서 넣어준다
            // 여기서는 "누가 등록했는지"를 남기려고 memberId를 쓴다
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            // 입력값이 살아 있는 채로 폼을 다시 그린다 (redirect가 아니라 그 자리에서 렌더링)
            return prepareForm(model, null);
        }

        communityNoticeAdminService.createNotice(noticeForm, memberDetails.getMemberId());

        // addFlashAttribute: redirect 뒤 화면까지만 살아 있는 일회성 값
        redirectAttributes.addFlashAttribute("successMessage", "공지사항을 등록했습니다.");

        // POST 뒤에 redirect를 두는 이유: 새로고침해도 등록이 다시 실행되지 않게 하려고
        return "redirect:/admin/community/notices";
    }

    // 수정 폼 화면
    // noticeId: \\d+ 는 {noticeId}가 만족해야 하는 정규식 조건. \d+ 는 숫자 한 자리 이상이라는 뜻
    // 예시 요청: GET /admin/community/notices/12/edit
    @GetMapping("/admin/community/notices/{noticeId:\\d+}/edit")
    public String editForm(
            // {noticeId} = "12" 를 잡고 long 으로 변환해서 noticeId = 12L로 넘긴다
            @PathVariable("noticeId") long noticeId,
            @ModelAttribute("noticeForm") NoticeForm noticeForm,
            Model model
    ) {
        AdminNoticeDetailView notice = communityNoticeAdminService.getEditableNotice(noticeId);

        // 저장된 값을 폼 객체에 옮겨 담는다 -> 화면이 열릴 때 입력 칸이 채워진 상태로 그려진다
        // noticeForm은 스프링이 만들어 model에도 넣어 둔 그 객체라서, 여기서 채우면 화면까지 그대로 간다
        noticeForm.setTitle(notice.title());
        noticeForm.setContent(notice.content());
        noticeForm.setStartsAt(notice.startsAt());
        noticeForm.setEndsAt(notice.endsAt());

        return prepareForm(model, notice);
    }

    // 1. 폼 검증 -> 2. 실패면 폼 다시 그리기 -> 3. 성공이면 수정하고 목록으로 redirect
    @PostMapping("/admin/community/notices/{noticeId:\\d+}/edit")
    public String update(
            @PathVariable("noticeId") long noticeId,
            @Valid @ModelAttribute("noticeForm") NoticeForm noticeForm,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            // 폼을 다시 그리려면 "무엇을 고치는 중인지"가 또 필요해서 여기서 한 번 더 조회한다
            return prepareForm(model, communityNoticeAdminService.getEditableNotice(noticeId));
        }

        communityNoticeAdminService.updateNotice(noticeId, noticeForm);

        redirectAttributes.addFlashAttribute("successMessage", "공지사항을 수정했습니다.");

        return "redirect:/admin/community/notices";
    }

    // 삭제에는 받을 입력이 없다: 그래서 @Valid도 BindingResult도 Model도 필요 없다
    // 예시 요청: POST /admin/community/notices/12/delete
    @PostMapping("/admin/community/notices/{noticeId:\\d+}/delete")
    public String delete(
            @PathVariable("noticeId") long noticeId,
            RedirectAttributes redirectAttributes
    ) {
        communityNoticeAdminService.deleteNotice(noticeId);

        redirectAttributes.addFlashAttribute("successMessage", "공지사항을 삭제했습니다.");

        return "redirect:/admin/community/notices";
    }

    // 등록·수정이 같은 form.html 한 장을 쓴다
    // notice == null 이면 등록, 값이 있으면 수정 — 화면은 이 값으로 제목과 보낼 주소를 고른다
    private String prepareForm(Model model, AdminNoticeDetailView notice) {
        model.addAttribute("notice", notice);

        return "admin/community/notice/form";
    }

    // "3" -> 3, "0"·"-1"·"abc"·"999999999999" -> null (호출한 쪽이 기본값을 쓴다)
    // 반환 타입이 int가 아니라 Integer인 이유: "값이 없음"을 null로 표현해야 해서다
    // 일단 long으로 읽고 Integer 범위를 넘는지 보는 이유: int로 바로 읽으면 넘칠 때 예외로 끝나기 때문
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
