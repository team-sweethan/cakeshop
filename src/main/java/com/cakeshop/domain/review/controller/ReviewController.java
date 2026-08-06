package com.cakeshop.domain.review.controller;

import com.cakeshop.domain.review.dto.form.ReviewForm;
import com.cakeshop.domain.review.dto.view.ReviewTargetView;
import com.cakeshop.domain.review.dto.view.WritableReviewView;
import com.cakeshop.domain.review.service.ReviewService;
import com.cakeshop.global.common.paging.PageNavigation;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.security.MemberDetails;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-06
 * 기능 : 후기 작성 요청 처리
 * 설명 : 작성할 후기 목록·작성 폼·후기 등록을 처리한다. 조각 1(#109).
 * ******************************
 */
@Controller
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** A1. 작성할 후기 목록. */
    @GetMapping("/mypage/reviews/writable")
    public String writableReviews(
            @AuthenticationPrincipal MemberDetails member,
            @RequestParam(required = false) String page,
            Model model
    ) {
        PageRequest pageRequest =
                new PageRequest(parsePage(page), PageRequest.DEFAULT_SIZE);

        PageResult<WritableReviewView> pageResult =
                reviewService.getWritableReviews(member.getMemberId(), pageRequest);

        model.addAttribute("pageResult", pageResult);
        model.addAttribute("pageNavigation",
                PageNavigation.of(pageResult.getPage(), pageResult.getTotalPages()));

        return "customer/review/writable";
    }

    /**
     * A2. 후기 작성 폼.
     *
     * <p>{@code orderItemId} 는 필수다. 같은 상품을 픽업한 주문이 여러 건일 수 있어 어느 주문
     * 상품인지 화면이 정할 수 없기 때문에, 진입은 A1 목록을 거친다.
     */
    @GetMapping("/reviews/new")
    public String reviewForm(
            @AuthenticationPrincipal MemberDetails member,
            @RequestParam(required = false) Long orderItemId,
            @ModelAttribute("reviewForm") ReviewForm reviewForm,
            Model model
    ) {
        ReviewTargetView target =
                reviewService.getReviewTarget(member.getMemberId(), orderItemId);

        reviewForm.setOrderItemId(target.orderItemId());
        model.addAttribute("target", target);

        return "customer/review/form";
    }

    /**
     * A3. 후기 등록.
     *
     * <p>성공하면 A1 로 돌려보낸다. 방금 쓴 항목이 목록에서 빠진 것으로 완료를 확인한다 —
     * 조각 1 시점에는 쓴 후기를 볼 화면이 아직 없다.
     */
    @PostMapping("/reviews")
    public String createReview(
            @AuthenticationPrincipal MemberDetails member,
            @Valid @ModelAttribute("reviewForm") ReviewForm reviewForm,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            // 폼을 다시 그리려면 주문 상품 정보가 필요하다. 자격 검증도 여기서 다시 걸린다.
            model.addAttribute("target", reviewService.getReviewTarget(
                    member.getMemberId(), reviewForm.getOrderItemId()));
            return "customer/review/form";
        }

        reviewService.createReview(member.getMemberId(), reviewForm);

        return "redirect:/mypage/reviews/writable";
    }

    /** 숫자가 아닌 page 는 1로 떨어뜨린다. 목록이 500 이 되면 안 된다. */
    private Integer parsePage(String page) {
        if (page == null || page.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(page.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
