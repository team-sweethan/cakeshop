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

// 관리자 후기 화면의 요청 창구다. 고객용 ReviewController와 URL 앞자리(/admin)로 갈린다.
// 여기 메서드에 권한 검사 코드가 없는 이유: SecurityConfig가 "/admin", "/admin/**" 전체를
//   hasRole("ADMIN")으로 막고 있어서 ADMIN이 아니면 메서드에 닿지도 못한다
//   -> 화면에서 버튼을 숨기는 방식이 아니라 URL 단위로 막는다
@Controller
@RequiredArgsConstructor
public class ReviewAdminController {

    private final ReviewAdminService reviewAdminService;

    // 예시 요청: GET /admin/reviews?writer=현규&product=딸기케이크&rating=3&status=BLOCKED&page=2
    // 검색 조건 4개 + 페이지가 전부 @RequestParam이고 required = false라 하나도 없이 들어올 수 있다
    //   -> 조건을 비우고 GET /admin/reviews 만 부르면 전체 목록이다
    // rating·status를 String으로 받는 이유: 브라우저에서는 어차피 문자열로 오고,
    //   "3"이나 "BLOCKED"처럼 모르는 값이 섞여도 예외 대신 "전체"로 떨어뜨려야 하기 때문이다
    //   (enum 타입으로 바로 받으면 이상한 값에 400이 난다)
    @GetMapping("/admin/reviews")
    public String list(
            @RequestParam(name = "writer", required = false) String writer,
            @RequestParam(name = "product", required = false) String product,
            @RequestParam(name = "rating", required = false) String rating,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false) Integer page,
            Model model
    ) {
        // 문자열을 의미 있는 타입으로 바꾸는 자리
        // "3" -> AdminReviewRating.THREE_OR_LESS (min = null, max = 3), 모르는 값이면 ALL
        // "BLOCKED" -> ReviewStatus.BLOCKED, 모르는 값이면 null(= 상태 조건 없음)
        AdminReviewRating selectedRating = AdminReviewRating.from(rating);
        ReviewStatus selectedStatus = parseStatus(status);

        PageResult<AdminReviewListView> reviews = reviewAdminService.getReviews(
                writer, product, selectedRating, selectedStatus, new PageRequest(page, null));

        // 목록과 페이지 버튼 재료
        model.addAttribute("reviews", reviews);
        model.addAttribute(
                "pageNavigation",
                PageNavigation.of(reviews.getPage(), reviews.getTotalPages()));

        // 방금 검색한 조건을 그대로 돌려준다 -> 검색하고 나서도 입력칸에 값이 남아 있다
        model.addAttribute("writer", writer);
        model.addAttribute("product", product);
        model.addAttribute("selectedRating", selectedRating);
        model.addAttribute("selectedStatus", selectedStatus);

        // values(): enum이 자동으로 갖는 메서드로 상수 전체를 배열로 준다
        //   -> 화면의 <select> 안 <option> 목록을 이걸로 그린다
        model.addAttribute("ratingOptions", AdminReviewRating.values());
        model.addAttribute("statusOptions", ReviewStatus.values());

        return "admin/review/list";
    }

    // 예시 요청: GET /admin/reviews/482
    // @ModelAttribute("reviewReplyForm"): 빈 ReviewReplyForm을 만들어 매개변수로 주고 model에도 넣는다
    //   -> 답글 입력 폼이 쓸 객체를 상세 화면에서 미리 준비하는 것이다
    // 이미 답글이 달려 있으면 그 내용을 폼에 채워 넣는다 -> 같은 화면이 "작성"과 "수정" 둘 다에 쓰인다
    @GetMapping("/admin/reviews/{reviewId:\\d+}")
    public String detail(
            @PathVariable("reviewId") long reviewId,
            @ModelAttribute("reviewReplyForm") ReviewReplyForm reviewReplyForm,
            Model model
    ) {
        AdminReviewDetailView review = reviewAdminService.getReviewDetail(reviewId);

        if (review.reply() != null) {
            reviewReplyForm.setContent(review.reply().content());
        }

        model.addAttribute("review", review);

        return "admin/review/detail";
    }

    // 예시 요청: POST /admin/reviews/482/replies  (답글 새로 달기)
    // @Valid + BindingResult: 폼에 붙은 @NotBlank·@Size 검사를 돌리고 그 결과를 BindingResult에 담는다
    //   두 매개변수는 반드시 이 순서로 붙어 있어야 검증 실패가 예외 대신 아래 if로 들어온다
    // @AuthenticationPrincipal: 답글을 "누가" 달았는지는 폼이 아니라 로그인한 관리자에게서 가져온다
    // RedirectAttributes: redirect 뒤에도 한 번만 살아남는 값을 담는 자리다
    //   addFlashAttribute는 세션에 잠깐 넣었다가 다음 화면이 읽으면 지운다
    //   -> 그래서 새로고침해도 "답글을 등록했습니다."가 다시 뜨지 않는다 (model.addAttribute는 redirect를 못 넘는다)
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
            // 실패할 때만 redirect가 아니라 화면 이름을 돌려준다
            //   -> 입력하던 내용과 오류 문구를 그대로 들고 상세 화면을 다시 그린다
            // 이때 review는 model에 없으므로 다시 조회해서 담아야 화면이 깨지지 않는다
            model.addAttribute("review", reviewAdminService.getReviewDetail(reviewId));

            return "admin/review/detail";
        }

        reviewAdminService.reply(reviewId, reviewReplyForm, memberDetails.getMemberId());

        redirectAttributes.addFlashAttribute("successMessage", "답글을 등록했습니다.");

        return "redirect:/admin/reviews/" + reviewId;
    }

    // 예시 요청: POST /admin/reviews/482/replies/edit  (이미 달린 답글 고치기)
    // 위 reply와 거의 같지만 @AuthenticationPrincipal이 없다
    //   -> 새로 다는 것이 아니라 이미 있는 답글의 내용만 바꾸는 것이라 작성자를 다시 정할 일이 없다
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

    // 예시 요청: POST /admin/reviews/482/block  (후기를 고객 화면에서 가린다)
    // 폼 객체도 BindingResult도 없다 - 받을 입력이 reviewId 하나뿐이라 검증할 것이 없다
    // 상태를 바꾸는 요청이라 GET이 아니라 POST다
    @PostMapping("/admin/reviews/{reviewId:\\d+}/block")
    public String block(
            @PathVariable("reviewId") long reviewId,
            RedirectAttributes redirectAttributes
    ) {
        reviewAdminService.block(reviewId);

        redirectAttributes.addFlashAttribute("successMessage", "후기를 숨김 처리했습니다.");

        return "redirect:/admin/reviews/" + reviewId;
    }

    // 예시 요청: POST /admin/reviews/482/unblock  (가려 둔 후기를 다시 보이게 한다)
    // block의 짝이다. 둘 다 같은 상세 화면으로 돌아가며 결과 문구만 다르다
    @PostMapping("/admin/reviews/{reviewId:\\d+}/unblock")
    public String unblock(
            @PathVariable("reviewId") long reviewId,
            RedirectAttributes redirectAttributes
    ) {
        reviewAdminService.unblock(reviewId);

        redirectAttributes.addFlashAttribute("successMessage", "숨김을 해제했습니다.");

        return "redirect:/admin/reviews/" + reviewId;
    }

    // 위 list가 부르는 헬퍼다. 문자열 -> ReviewStatus 변환을 직접 한다
    // ReviewStatus.valueOf("blocked")는 예외를 던지지만, 여기서는 모르는 값을 null로 돌려준다
    //   -> null = "상태 조건 없음"이므로 이상한 파라미터가 와도 오류 화면 대신 전체 목록이 나온다
    // status.name(): enum 상수의 이름을 문자열로 준다 (BLOCKED -> "BLOCKED")
    // equalsIgnoreCase: 대소문자를 무시하고 비교한다 -> "blocked"도 "BLOCKED"로 받아 준다
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
