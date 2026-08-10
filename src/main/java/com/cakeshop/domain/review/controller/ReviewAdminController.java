package com.cakeshop.domain.review.controller;

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

import com.cakeshop.domain.review.dto.form.ReviewReplyForm;
import com.cakeshop.domain.review.dto.view.AdminReviewDetailView;
import com.cakeshop.domain.review.dto.view.AdminReviewListView;
import com.cakeshop.domain.review.dto.view.AdminReviewRating;
import com.cakeshop.domain.review.entity.ReviewStatus;
import com.cakeshop.domain.review.service.ReviewAdminService;
import com.cakeshop.global.common.paging.PageNavigation;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.security.MemberDetails;

@Controller
@RequiredArgsConstructor
public class ReviewAdminController {

    private final ReviewAdminService reviewAdminService;

    @GetMapping("/admin/reviews")
    public String list(
            @RequestParam(name = "writer", required = false) String writer,
            @RequestParam(name = "product", required = false) String product,
            @RequestParam(name = "rating", required = false) String rating,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false) Integer page,
            Model model
    ) {
        AdminReviewRating selectedRating = AdminReviewRating.from(rating);
        ReviewStatus selectedStatus = parseStatus(status);

        PageResult<AdminReviewListView> reviews = reviewAdminService.getReviews(
                writer, product, selectedRating, selectedStatus, new PageRequest(page, null));

        model.addAttribute("reviews", reviews);
        model.addAttribute(
                "pageNavigation",
                PageNavigation.of(reviews.getPage(), reviews.getTotalPages()));
        model.addAttribute("writer", writer);
        model.addAttribute("product", product);
        model.addAttribute("selectedRating", selectedRating);
        model.addAttribute("selectedStatus", selectedStatus);

        return "admin/review/list";
    }

    @GetMapping("/admin/reviews/{reviewId:\\d+}")
    public String detail(
            @PathVariable("reviewId") long reviewId,
            @ModelAttribute("reviewReplyForm") ReviewReplyForm reviewReplyForm,
            Model model
    ) {
        AdminReviewDetailView review = reviewAdminService.getReviewDetail(reviewId);

        if (review.getReply() != null) {
            reviewReplyForm.setContent(review.getReply().getContent());
        }

        model.addAttribute("review", review);

        return "admin/review/detail";
    }

    @PostMapping("/admin/reviews/{reviewId:\\d+}/replies")
    public String reply(
            @PathVariable("reviewId") long reviewId,
            @Valid @ModelAttribute("reviewReplyForm") ReviewReplyForm reviewReplyForm,
            BindingResult bindingResult,
            @AuthenticationPrincipal MemberDetails memberDetails,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("review", reviewAdminService.getReviewDetail(reviewId));

            return "admin/review/detail";
        }

        reviewAdminService.reply(reviewId, reviewReplyForm, memberDetails.getMemberId());

        redirectAttributes.addFlashAttribute("successMessage", "답글을 등록했습니다.");

        return "redirect:/admin/reviews/" + reviewId;
    }

    @PostMapping("/admin/reviews/{reviewId:\\d+}/replies/edit")
    public String editReply(
            @PathVariable("reviewId") long reviewId,
            @Valid @ModelAttribute("reviewReplyForm") ReviewReplyForm reviewReplyForm,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("review", reviewAdminService.getReviewDetail(reviewId));

            return "admin/review/detail";
        }

        reviewAdminService.editReply(reviewId, reviewReplyForm);

        redirectAttributes.addFlashAttribute("successMessage", "답글을 수정했습니다.");

        return "redirect:/admin/reviews/" + reviewId;
    }

    @PostMapping("/admin/reviews/{reviewId:\\d+}/block")
    public String block(
            @PathVariable("reviewId") long reviewId,
            RedirectAttributes redirectAttributes
    ) {
        reviewAdminService.block(reviewId);

        redirectAttributes.addFlashAttribute("successMessage", "후기를 숨김 처리했습니다.");

        return "redirect:/admin/reviews/" + reviewId;
    }

    @PostMapping("/admin/reviews/{reviewId:\\d+}/unblock")
    public String unblock(
            @PathVariable("reviewId") long reviewId,
            RedirectAttributes redirectAttributes
    ) {
        reviewAdminService.unblock(reviewId);

        redirectAttributes.addFlashAttribute("successMessage", "숨김을 해제했습니다.");

        return "redirect:/admin/reviews/" + reviewId;
    }

    private ReviewStatus parseStatus(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        for (ReviewStatus status : ReviewStatus.values()) {
            if (status.name().equalsIgnoreCase(normalized)) {
                return status;
            }
        }

        return null;
    }
}
