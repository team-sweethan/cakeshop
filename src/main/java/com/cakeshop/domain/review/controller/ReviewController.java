package com.cakeshop.domain.review.controller;

import java.util.Map;

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

import com.cakeshop.domain.order.dto.view.OrderReviewItemView;
import com.cakeshop.domain.order.dto.view.OrderReviewTargetView;
import com.cakeshop.domain.review.dto.form.ReviewEditForm;
import com.cakeshop.domain.review.dto.form.ReviewWriteForm;
import com.cakeshop.domain.review.dto.view.MyReviewView;
import com.cakeshop.domain.review.dto.view.ProductReviewView;
import com.cakeshop.domain.review.error.ReviewErrorCode;
import com.cakeshop.domain.review.service.ReviewService;
import com.cakeshop.global.common.paging.PageNavigation;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;
import com.cakeshop.global.security.MemberDetails;

// 고객이 보는 후기 화면의 요청 창구다. URL 하나가 메서드 하나에 붙는다.
// @Controller: 메서드가 돌려준 문자열을 화면 경로로 읽는다
//   "customer/review/my" -> src/main/resources/templates/customer/review/my.html
//   "redirect:/mypage/reviews" -> 화면을 그리지 않고 브라우저에게 그 주소로 다시 요청하라고 답한다
// @RequiredArgsConstructor: final 필드를 받는 생성자를 Lombok이 만들고, 스프링이 그 생성자로 ReviewService를 넣어준다
@Controller
@RequiredArgsConstructor
public class ReviewController {

    // Service가 던진 오류 코드를 폼의 어느 입력 칸에 붙일지 정한 표다
    // 예: IMAGE_TOO_LARGE 가 오면 "images" 칸 아래에 오류 문구가 붙는다
    // 표에 없는 코드는 폼으로 되돌리지 않고 그대로 위로 던진다 -> 공통 오류 화면으로 간다
    private static final Map<ReviewErrorCode, String> FORM_ERROR_FIELDS = Map.of(
            ReviewErrorCode.INVALID_IMAGE_FILE, "images",
            ReviewErrorCode.IMAGE_TOO_LARGE, "images",
            ReviewErrorCode.IMAGE_LIMIT_EXCEEDED, "images"
    );

    private final ReviewService reviewService;

    // 예시 요청: GET /products/12/reviews?page=2
    // {productId:\d+} 의 \d+ 는 "숫자 한 자리 이상"이라는 정규식 조건이다
    //   -> /products/12/reviews 는 들어오고 /products/abc/reviews 는 이 메서드로 오지 않는다
    // 로그인 없이도 볼 수 있는 화면이라 @AuthenticationPrincipal 매개변수가 없다
    @GetMapping("/products/{productId:\\d+}/reviews")
    public String productReviews(
            // @PathVariable("productId"): URL 안의 "12"를 잡아 long으로 바꿔 productId = 12L 로 넘긴다
            @PathVariable("productId") long productId,

            // @RequestParam: ?page=2 를 Integer 2 로 바꿔 넣고, 파라미터가 없으면 null을 넣는다
            // required = false 라서 int가 아니라 Integer다 - 값이 없을 때 담을 null 자리가 필요하다
            @RequestParam(name = "page", required = false) Integer page,
            Model model
    ) {
        // new PageRequest(몇 번째 쪽, 한 쪽 크기): 크기 자리의 null은 "기본 크기를 쓰라"는 뜻
        // PageResult<ProductReviewView>: ProductReviewView 여러 개와 페이지 정보를 함께 담는 상자
        /*
            PageResult<ProductReviewView>
            ├─ content : List<ProductReviewView>
            │   ├─ ProductReviewView
            │   │    ├─ id
            │   │    ├─ authorName      (탈퇴한 회원이면 "탈퇴한 회원")
            │   │    ├─ overallRating / tasteRating / designRating / serviceRating
            │   │    ├─ content
            │   │    ├─ createdAt
            │   │    ├─ images : List<ReviewImageView>
            │   │    └─ reply  : ReviewReplyView (사장님 답글, 없으면 null)
            │   └─ ...
            ├─ page          : 현재 쪽
            ├─ size          : 한 쪽 크기
            ├─ totalElements : 전체 후기 수
            └─ totalPages    : 전체 쪽 수
        */
        PageResult<ProductReviewView> reviews =
                reviewService.getProductReviews(productId, new PageRequest(page, null));

        // model.addAttribute("타임리프에서 쓸 이름", 넘길 객체)
        // PageNavigation.of(현재 쪽, 전체 쪽): 화면 아래 [1][2][3] 버튼을 그릴 재료를 계산한다
        model.addAttribute("productId", productId);
        model.addAttribute("reviews", reviews);
        model.addAttribute(
                "pageNavigation",
                PageNavigation.of(reviews.getPage(), reviews.getTotalPages()));

        return "customer/review/product";
    }

    // 예시 요청: GET /mypage/reviews?page=1
    // 위 상품 후기 목록과 화면 구성은 비슷하지만 담기는 타입이 다르다
    //   ProductReviewView(남이 보는 후기) vs MyReviewView(productName·orderNumber·status가 더 있다)
    @GetMapping("/mypage/reviews")
    public String myList(
            @RequestParam(name = "page", required = false) Integer page,

            /*
                1. 로그인 성공
                2. Spring Security가 Authentication 생성
                3. SecurityContext에 저장
                4. 그 안의 principal이 MemberDetails
                5. @AuthenticationPrincipal이 그 principal을 꺼내 이 자리에 넣어준다
            */
            // 즉 memberId는 화면이 보낸 파라미터가 아니라 세션의 인증에서 온 값이다
            // "내 후기"의 주인을 여기서 정하므로 요청이 보낸 회원 번호를 믿지 않아도 된다
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

    // 예시 요청: GET /mypage/reviews/482  (알림을 눌러 그 후기로 바로 들어오는 자리)
    // 위 myList와 같은 화면(customer/review/my)을 돌려주되, 482번 후기가 들어 있는 쪽을 Service가 골라 준다
    //   -> 브라우저는 주소 뒤 앵커를 따라 그 후기까지 스크롤한다
    // page 파라미터가 없다: 몇 쪽을 보여줄지는 사용자가 아니라 reviewId가 정하기 때문이다
    @GetMapping("/mypage/reviews/{reviewId:\\d+}")
    public String myFocusedList(
            @PathVariable("reviewId") long reviewId,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model
    ) {
        PageResult<MyReviewView> reviews = reviewService.getFocusedMyReviews(
                memberDetails.getMemberId(), reviewId);

        model.addAttribute("reviews", reviews);
        model.addAttribute(
                "pageNavigation",
                PageNavigation.of(reviews.getPage(), reviews.getTotalPages()));

        return "customer/review/my";
    }

    // 예시 요청: GET /mypage/reviews/writable?page=1
    // 여기 담기는 것은 후기가 아니라 "아직 후기를 안 쓴 주문 상품"(OrderReviewItemView)이다
    //   -> 이 목록에서 orderItemId를 들고 아래 form(GET /reviews/new)으로 넘어가며 작성이 시작된다
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

    // 예시 요청: GET /reviews/new?orderItemId=97
    // @RequestParam("orderItemId"): 쿼리스트링 값을 long으로 바꿔 넣는다
    //   required 기본값이 true라서 이 파라미터가 없으면 메서드에 들어오지도 못하고 400이 난다
    // @ModelAttribute("reviewWriteForm"): 빈 ReviewWriteForm을 새로 만들어 매개변수로 주고,
    //   같은 이름으로 model에도 넣어준다 -> 화면의 th:object="${reviewWriteForm}" 가 이 객체를 본다
    // 흐름: 1. 이 주문 상품에 후기를 쓸 자격이 있는지 Service가 확인 -> 2. 폼에 orderItemId를 심고 -> 3. 화면 재료를 담는다
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

    // 예시 요청: POST /reviews  (위 폼의 전송. 사진이 붙어 multipart/form-data로 온다)
    // @ModelAttribute: 요청 파라미터 이름과 같은 setter를 찾아 폼 객체를 채운다
    //   overallRating=5 -> reviewWriteForm.setOverallRating(5)
    // @Valid: 채운 다음 폼 필드에 붙은 @NotNull·@Min·@Size 검사를 돌린다
    // BindingResult: 그 검사 결과가 담긴다. @Valid 붙은 매개변수 바로 다음 자리에 있어야
    //   예외로 튀지 않고 이 객체로 들어온다
    //
    // 흐름: 1. 형식 검증 실패 -> 폼 다시 그리기
    //      2. Service가 거절 -> 표에 있는 코드만 입력 칸 오류로 바꿔 폼 다시 그리기
    //      3. 성공 -> 내 후기 목록으로 redirect
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

        try {
            reviewService.write(reviewWriteForm, memberId);
        } catch (BusinessException exception) {
            String field = FORM_ERROR_FIELDS.get(exception.getErrorCode());

            if (field == null) {
                throw exception;
            }

            // rejectValue(입력 칸 이름, 오류 코드, 화면에 보일 문구): 그 칸에 오류를 붙인다
            // -> 화면의 th:errors="*{images}" 자리에 문구가 나온다
            bindingResult.rejectValue(
                    field,
                    exception.getErrorCode().code(),
                    exception.getErrorCode().message());

            model.addAttribute(
                    "target",
                    reviewService.getWriteTarget(reviewWriteForm.getOrderItemId(), memberId));

            return "customer/review/form";
        }

        return "redirect:/mypage/reviews";
    }

    // 예시 요청: GET /reviews/482/edit
    // 수정 폼은 빈 칸이 아니라 지금 값이 채워진 채로 열려야 한다
    //   -> Service가 준 review의 값을 폼 객체 setter로 하나씩 옮겨 담는다
    //   -> @ModelAttribute 덕에 그 폼 객체는 이미 model에 있으므로 화면이 그대로 읽어 간다
    // getEditableReview: 내 후기가 맞는지, 수정할 수 있는 상태인지까지 Service가 확인해서 준다
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

    // 예시 요청: POST /reviews/482/edit
    // 수정에는 사진이 없어서 write와 달리 FORM_ERROR_FIELDS 표를 거치지 않는다
    // 검증 실패로 화면을 다시 그릴 때 review를 다시 조회한다
    //   -> 상품명·주문번호처럼 폼에 없는 값은 model이 비면 화면에서 사라지기 때문이다
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

    // 예시 요청: POST /reviews/482/delete
    // 지우는 요청이라 GET이 아니라 POST다 - 주소를 여는 것만으로 삭제되면 안 된다
    // 그릴 화면이 없어 Model 매개변수도 없다. 지운 뒤 목록으로 redirect만 한다
    @PostMapping("/reviews/{reviewId:\\d+}/delete")
    public String delete(
            @PathVariable("reviewId") long reviewId,
            @AuthenticationPrincipal MemberDetails memberDetails
    ) {
        reviewService.delete(reviewId, memberDetails.getMemberId());

        return "redirect:/mypage/reviews";
    }
}
