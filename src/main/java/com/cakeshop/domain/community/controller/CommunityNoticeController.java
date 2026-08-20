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

    // @RequiredArgsConstructor(lombok)가 이 final 필드를 받는 생성자를 만들고, 스프링이 그리로 빈을 넣어 준다
    // 관리자용과 달리 고객용 Service 하나만 쓴다: 여기서는 읽기만 하기 때문이다
    private final CommunityNoticeService communityNoticeService;

    // 예시 요청: GET /community/notices?page=2
    @GetMapping("/community/notices")
    public String list(
            // page를 String으로 받는다: "abc" 같은 값이 와도 400 대신 아래에서 null(= 1페이지)로 흘려보내려고
            @RequestParam(required = false) String page,
            Model model
    ) {
        // PageRequest는 "몇 페이지를 몇 개씩 볼지"를 담은 객체다
        PageRequest pageRequest =
                new PageRequest(parsePositiveInteger(page), PageRequest.DEFAULT_SIZE);

        // PageResult<NoticeView>: NoticeView 여러 개와 페이지 정보를 한 번에 담는 상자
        // T = NoticeView -> private final List<NoticeView> content
        PageResult<NoticeView> pageResult = communityNoticeService.getNotices(pageRequest);

        // Model에 화면 재료 담기
        // model.addAttribute("타임리프에서 쓰일 변수 이름", controller에서 사용되는 객체 이름)
        model.addAttribute("pageResult", pageResult);

        // PageNavigation.of(현재 페이지, 전체 페이지): 화면 아래 [1][2][3] 묶음을 계산해 준다
        model.addAttribute(
                "pageNavigation",
                PageNavigation.of(pageResult.getPage(), pageResult.getTotalPages())
        );

        // Thymeleaf HTML 반환 (templates/customer/community/notice/list.html)
        return "customer/community/notice/list";
    }

    // 공지 상세 조회
    // noticeId: \\d+ 는 {noticeId}가 만족해야 하는 정규식 조건. \d+ 는 숫자 한 자리 이상이라는 뜻
    // 예시 요청: GET /community/notices/12 -> {noticeId} = "12" 를 long으로 바꿔 noticeId = 12L
    @GetMapping("/community/notices/{noticeId:\\d+}")
    public String detail(@PathVariable("noticeId") long noticeId, Model model) {
        // 노출 기간 밖이거나 삭제된 공지를 거르는 일은 Service 쪽 조회가 맡는다
        // 없으면 여기서 null이 올라오는 게 아니라 Service가 예외를 던져 오류 화면으로 간다
        model.addAttribute("notice", communityNoticeService.getNotice(noticeId));

        return "customer/community/notice/detail";
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
