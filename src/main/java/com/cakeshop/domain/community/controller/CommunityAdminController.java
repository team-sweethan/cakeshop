package com.cakeshop.domain.community.controller;

import com.cakeshop.domain.community.dto.form.BlockForm;
import com.cakeshop.domain.community.dto.view.AdminPostDetailView;
import com.cakeshop.domain.community.dto.view.AdminPostListView;
import com.cakeshop.domain.community.dto.view.AdminPostSort;
import com.cakeshop.domain.community.dto.view.ReportView;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.service.CommunityAdminService;
import com.cakeshop.domain.community.service.CommunityCommentService;
import com.cakeshop.domain.community.service.CommunityPostImageService;
import com.cakeshop.global.common.paging.PageNavigation;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.security.MemberDetails;

import jakarta.validation.Valid;

import java.util.List;

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
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 요청 처리
 * 설명 : CommunityAdminController 요청을 받아 서비스 호출과 화면 이동을 처리한다.
 * ******************************
 */
@Controller
@RequiredArgsConstructor
public class CommunityAdminController {

    // @RequiredArgsConstructor(lombok)가 아래 final 필드 3개를 받는 생성자를 만들어 주고,
    // 스프링이 그 생성자로 스프링 빈을 넣어 준다. 그래서 이 파일에 생성자가 보이지 않는다
    private final CommunityAdminService communityAdminService;
    private final CommunityCommentService communityCommentService;
    private final CommunityPostImageService communityPostImageService;

    // 예시 요청: GET /admin/community?status=BLOCKED&sort=REPORTS&page=2
    @GetMapping("/admin/community")
    public String list(
            // 셋 다 String으로 받는다: 브라우저가 보내는 값은 전부 문자열이고,
            // 이상한 값이 와도 400 대신 아래에서 null(= 조건 없음)로 흘려보내려는 것이다
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String page,
            Model model
    ) {
        // 문자열을 의미 있는 타입으로 바꾼다
        // "BLOCKED" -> PostStatus.BLOCKED, 못 알아보는 값이면 null = 상태 필터 없음(전체)
        PostStatus selectedStatus = parseStatus(status);
        AdminPostSort selectedSort = AdminPostSort.from(sort);

        // PageRequest는 "몇 페이지를 몇 개씩 볼지"를 담은 객체다
        PageRequest pageRequest =
                new PageRequest(parsePositiveInteger(page), PageRequest.DEFAULT_SIZE);

        // PageResult<AdminPostListView>: AdminPostListView 여러 개와 페이지 정보를 한 번에 담는 상자
        // T = AdminPostListView -> private final List<AdminPostListView> content
        /*
            PageResult<AdminPostListView>
            ├─ content : List<AdminPostListView>   <- 관리자 표에 한 줄씩 그려질 글들
            ├─ page : 현재 페이지
            ├─ size : 한 페이지 크기
            ├─ totalElements : 조건에 맞는 전체 글 수
            └─ totalPages : 전체 페이지 수
        */
        PageResult<AdminPostListView> pageResult =
                communityAdminService.getPosts(selectedStatus, selectedSort, pageRequest);

        // Model에 화면 재료 담기
        // model.addAttribute("타임리프에서 쓰일 변수 이름", controller에서 사용되는 객체 이름)
        model.addAttribute("pageResult", pageResult);
        model.addAttribute(
                "pageNavigation",
                PageNavigation.of(pageResult.getPage(), pageResult.getTotalPages())
        );

        // 지금 고른 값을 되돌려 준다 -> 화면의 필터가 눌린 상태 그대로 다시 그려진다
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("selectedSort", selectedSort);

        // values(): enum이 자동으로 갖는 메서드. 상수 전부를 배열로 준다 = 선택 상자에 채울 항목
        model.addAttribute("statusOptions", PostStatus.values());
        model.addAttribute("sortOptions", AdminPostSort.values());

        // Thymeleaf HTML 반환 (templates/admin/community/list.html)
        return "admin/community/list";
    }

    // 게시글 상세 조회 (관리자)
    // 예시 요청: GET /admin/community/37?comments=50&replies=12
    // postId: \\d+ 는 {postId}가 만족해야 하는 정규식 조건. \d+ 는 숫자 한 자리 이상이라는 뜻
    @GetMapping("/admin/community/{postId:\\d+}")
    public String detail(
            // {postId} = "37" 을 잡고 long 으로 변환해서 postId = 37L로 넘긴다
            @PathVariable("postId") long postId,

            // 댓글을 몇 개까지 펼칠지(comments), 어느 답글 묶음을 펼칠지(replies)
            @RequestParam(required = false) String comments,
            @RequestParam(required = false) String replies,

            // @ModelAttribute: 요청 파라미터를 폼 객체에 채워서 model에도 같은 이름으로 넣어준다
            // 차단 폼은 상세 화면에서 바로 쓰이므로 빈 객체라도 미리 준비해 둔다
            @ModelAttribute("blockForm") BlockForm blockForm,
            Model model
    ) {
        return prepareDetail(
                model,
                communityAdminService.getPostDetail(postId),
                parsePositiveInteger(comments),
                parsePositiveLong(replies)
        );
    }

    // 1. 폼 검증 -> 2. 실패면 상세 화면 다시 그리기 -> 3. 성공이면 차단 후 상세로 redirect
    @PostMapping("/admin/community/{postId:\\d+}/block")
    public String block(
            @PathVariable("postId") long postId,

            // @Valid: BlockForm에 붙은 검증 어노테이션(@NotBlank 등)을 스프링이 대신 검사한다
            // 검사 결과는 예외가 아니라 바로 뒤의 BindingResult에 담긴다. 그래서 두 매개변수는 붙어 있어야 한다
            @Valid @ModelAttribute("blockForm") BlockForm blockForm,
            BindingResult bindingResult,

            // @AuthenticationPrincipal: Spring Security가 현재 인증 객체의 Principal을 꺼내서 넣어준다
            // 여기서는 "누가 차단했는지"를 기록하려고 memberId를 쓴다
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            // 입력한 사유가 살아 있는 채로 상세 화면을 다시 만든다 (redirect가 아니라 그 자리에서 렌더링)
            return prepareDetail(model, communityAdminService.getPostDetail(postId), null, null);
        }

        communityAdminService.blockPost(
                postId, blockForm.getReason(), memberDetails.getMemberId());

        // addFlashAttribute: redirect 뒤 화면까지만 살아 있는 일회성 값. 세션에 잠깐 담겼다가 사라진다
        redirectAttributes.addFlashAttribute("successMessage", "게시글을 차단했습니다.");

        // "redirect:" 접두사가 붙으면 화면 이름이 아니라 브라우저에게 새 요청을 시키는 주소가 된다
        // POST 뒤에 redirect를 두는 이유: 새로고침해도 차단이 다시 실행되지 않게 하려고
        return "redirect:/admin/community/" + postId;
    }

    // 차단 해제에는 폼이 없다: 받을 입력이 없으니 @Valid도 BindingResult도 필요 없다
    @PostMapping("/admin/community/{postId:\\d+}/unblock")
    public String unblock(
            @PathVariable("postId") long postId,
            RedirectAttributes redirectAttributes
    ) {
        communityAdminService.unblockPost(postId);

        redirectAttributes.addFlashAttribute("successMessage", "차단을 해제했습니다.");

        return "redirect:/admin/community/" + postId;
    }

    // 신고 기각 = 글은 그대로 두고 쌓인 신고만 처리 완료로 바꾼다
    // 예시 요청: POST /admin/community/37/reports/reject
    @PostMapping("/admin/community/{postId:\\d+}/reports/reject")
    public String rejectReports(
            @PathVariable("postId") long postId,
            RedirectAttributes redirectAttributes
    ) {
        communityAdminService.rejectReports(postId);

        redirectAttributes.addFlashAttribute("successMessage", "신고를 기각했습니다.");

        return "redirect:/admin/community/" + postId;
    }

    // 상세 화면 한 장을 만드는 데 필요한 재료를 전부 model에 담는 헬퍼
    // detail()과 block()이 같은 화면을 그려야 해서 한곳에 모아 둔 것이다
    private String prepareDetail(
            Model model, AdminPostDetailView post, Integer commentLimit, Long expandedRootId) {
        List<ReportView> reports = communityAdminService.getReports(post.id());

        model.addAttribute("post", post);
        model.addAttribute("reports", reports);
        model.addAttribute("postImages", communityPostImageService.getImages(post.id()));

        // reports.stream()...count(): 목록을 훑어 조건에 맞는 것만 세는 문법
        // filter(ReportView::isPending) 는 filter(r -> r.isPending()) 의 줄임(메서드 참조)이고, 결과는 long
        model.addAttribute("pendingReportCount", reports.stream().filter(ReportView::isPending).count());

        // commentLimit = 몇 개까지 보여줄지, expandedRootId = 펼쳐 둘 답글 묶음의 부모 댓글 id
        model.addAttribute(
                "commentSection",
                communityCommentService.getComments(post.id(), commentLimit, expandedRootId));
        model.addAttribute("expandedRootId", expandedRootId);

        return "admin/community/detail";
    }

    // "blocked" 처럼 대소문자가 섞여 와도 PostStatus.BLOCKED로 알아본다
    // 못 알아보면 예외 대신 null: 목록에서 "상태 조건 없음"을 뜻한다
    private PostStatus parseStatus(String value) {
        if (value == null) {
            return null;
        }

        for (PostStatus status : PostStatus.values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }

        return null;
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

    // 위와 같은 규칙이지만 상한이 없다. id처럼 큰 값을 받는 자리에서 쓴다
    private Long parsePositiveLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
