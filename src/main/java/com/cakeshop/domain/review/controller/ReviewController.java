package com.cakeshop.domain.review.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.cakeshop.domain.order.dto.view.OrderReviewItemView;
import com.cakeshop.domain.order.dto.view.OrderReviewTargetView;
import com.cakeshop.domain.review.dto.form.ReviewWriteForm;
import com.cakeshop.domain.review.service.ReviewService;
import com.cakeshop.global.common.paging.PageNavigation;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.error.CommonErrorCode;
import com.cakeshop.global.security.MemberDetails;

import jakarta.validation.Valid;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-09
 * 기능 : 후기 작성 화면과 등록
 * 설명 : 회원 id 는 요청값이 아니라 인증 주체에서 가져온다. 자격 검증은 전부 Service 가 한다.
 * ******************************
 */
@Controller
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** A1 — 작성할 후기 목록. 비어 있는 것은 정상이므로 오류로 다루지 않는다. */
    @GetMapping("/mypage/reviews/writable")
    public String writableList(
            @RequestParam(name = "page", required = false) Integer page,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model
    ) {
        PageRequest pageRequest = new PageRequest(page, PageRequest.DEFAULT_SIZE);
        PageResult<OrderReviewItemView> pageResult =
                reviewService.getWritableOrderItems(memberDetails.getMemberId(), pageRequest);

        model.addAttribute("pageResult", pageResult);
        model.addAttribute(
                "pageNavigation",
                PageNavigation.of(pageResult.getPage(), pageResult.getTotalPages())
        );

        return "customer/review/writable";
    }

    /** A2 — 작성 폼. orderItemId 는 필수라 없거나 숫자가 아니면 400 이다. */
    @GetMapping("/reviews/new")
    public String form(
            @RequestParam("orderItemId") long orderItemId,
            @AuthenticationPrincipal MemberDetails memberDetails,
            @ModelAttribute("reviewWriteForm") ReviewWriteForm reviewWriteForm,
            Model model
    ) {
        OrderReviewTargetView target =
                reviewService.getWriteTarget(orderItemId, memberDetails.getMemberId());

        reviewWriteForm.setOrderItemId(orderItemId);
        model.addAttribute("target", target);

        return "customer/review/form";
    }

    /** A3 — 등록. 성공하면 A1 로 돌아가 방금 쓴 항목이 빠진 것으로 완료를 확인한다. */
    @PostMapping("/reviews")
    public String write(
            @Valid @ModelAttribute("reviewWriteForm") ReviewWriteForm reviewWriteForm,
            BindingResult bindingResult,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model
    ) {
        long memberId = memberDetails.getMemberId();

        // orderItemId 는 화면이 넣는 값이라 비어 있으면 우리 폼에서 온 요청이 아니다.
        // 대상을 못 그리므로 폼을 되돌려 줄 수도 없다(A2 와 같은 400).
        if (reviewWriteForm.getOrderItemId() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        if (bindingResult.hasErrors()) {
            // 폼을 다시 그리려면 대상 정보가 필요하다. 자격이 사라졌으면 여기서 걸린다.
            model.addAttribute("target", reviewService.getWriteTarget(reviewWriteForm.getOrderItemId(), memberId));

            return "customer/review/form";
        }

        reviewService.write(reviewWriteForm, memberId);

        return "redirect:/mypage/reviews/writable";
    }
}
