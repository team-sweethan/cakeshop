package com.cakeshop.domain.review.controller;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.cakeshop.domain.order.dto.view.OrderReviewItemView;
import com.cakeshop.domain.order.dto.view.OrderReviewTargetView;
import com.cakeshop.domain.review.dto.form.ReviewEditForm;
import com.cakeshop.domain.review.dto.form.ReviewWriteForm;
import com.cakeshop.domain.review.dto.view.MyReviewView;
import com.cakeshop.domain.review.dto.view.ProductReviewView;
import com.cakeshop.domain.review.service.ReviewService;
import com.cakeshop.global.common.paging.PageNavigation;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.security.MemberDetails;

@Controller
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/mypage/reviews/writable")
    public String writableList(
            @RequestParam(name = "page", required = false) Integer page,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model
    ) {
        PageResult<OrderReviewItemView> writableItems = reviewService.getWritableOrderItems(
                memberDetails.getMemberId(), new PageRequest(page, null));

        model.addAttribute("writableItems", writableItems);
        model.addAttribute(
                "pageNavigation",
                PageNavigation.of(writableItems.getPage(), writableItems.getTotalPages()));

        return "customer/review/writable";
    }

    @GetMapping("/products/{productId:\\d+}/reviews")
    public String productReviews(
            @PathVariable("productId") long productId,
            @RequestParam(name = "page", required = false) Integer page,
            Model model
    ) {
        PageResult<ProductReviewView> reviews =
                reviewService.getProductReviews(productId, new PageRequest(page, null));

        model.addAttribute("productId", productId);
        model.addAttribute("reviews", reviews);
        model.addAttribute(
                "pageNavigation",
                PageNavigation.of(reviews.getPage(), reviews.getTotalPages()));

        return "customer/review/product";
    }

    @GetMapping("/mypage/reviews")
    public String myList(
            @RequestParam(name = "page", required = false) Integer page,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model
    ) {
        PageResult<MyReviewView> reviews = reviewService.getMyReviews(
                memberDetails.getMemberId(), new PageRequest(page, null));

        model.addAttribute("reviews", reviews);
        model.addAttribute(
                "pageNavigation",
                PageNavigation.of(reviews.getPage(), reviews.getTotalPages()));

        return "customer/review/my";
    }

    @GetMapping("/reviews/new")
    public String form(
            @RequestParam("orderItemId") long orderItemId,
            @AuthenticationPrincipal MemberDetails memberDetails,
            @ModelAttribute("reviewWriteForm") ReviewWriteForm reviewWriteForm,
            Model model
    ) {
        OrderReviewTargetView target =
                reviewService.getWriteTarget(orderItemId, memberDetails.getMemberId());

        reviewWriteForm.setOrderItemId(target.orderItemId());
        model.addAttribute("target", target);

        return "customer/review/form";
    }

    @PostMapping("/reviews")
    public String write(
            @Valid @ModelAttribute("reviewWriteForm") ReviewWriteForm reviewWriteForm,
            BindingResult bindingResult,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model
    ) {
        long memberId = memberDetails.getMemberId();

        if (bindingResult.hasErrors()) {
            // 다시 그릴 때도 Service 를 거친다. 폼에서 온 orderItemId 는 그 자체로 신뢰할 수 없다.
            model.addAttribute(
                    "target",
                    reviewService.getWriteTarget(reviewWriteForm.getOrderItemId(), memberId));

            return "customer/review/form";
        }

        reviewService.write(reviewWriteForm, memberId);

        return "redirect:/mypage/reviews";
    }

    @GetMapping("/reviews/{reviewId:\\d+}/edit")
    public String editForm(
            @PathVariable("reviewId") long reviewId,
            @AuthenticationPrincipal MemberDetails memberDetails,
            @ModelAttribute("reviewEditForm") ReviewEditForm reviewEditForm,
            Model model
    ) {
        MyReviewView review =
                reviewService.getEditableReview(reviewId, memberDetails.getMemberId());

        reviewEditForm.setOverallRating(review.overallRating());
        reviewEditForm.setTasteRating(review.tasteRating());
        reviewEditForm.setDesignRating(review.designRating());
        reviewEditForm.setServiceRating(review.serviceRating());
        reviewEditForm.setContent(review.content());
        model.addAttribute("review", review);

        return "customer/review/edit";
    }

    @PostMapping("/reviews/{reviewId:\\d+}/edit")
    public String edit(
            @PathVariable("reviewId") long reviewId,
            @Valid @ModelAttribute("reviewEditForm") ReviewEditForm reviewEditForm,
            BindingResult bindingResult,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model
    ) {
        long memberId = memberDetails.getMemberId();

        if (bindingResult.hasErrors()) {
            model.addAttribute("review", reviewService.getEditableReview(reviewId, memberId));

            return "customer/review/edit";
        }

        reviewService.edit(reviewId, reviewEditForm, memberId);

        return "redirect:/mypage/reviews";
    }

    @PostMapping("/reviews/{reviewId:\\d+}/delete")
    public String delete(
            @PathVariable("reviewId") long reviewId,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        reviewService.delete(reviewId, memberDetails.getMemberId());

        return "redirect:/mypage/reviews";
    }
}
