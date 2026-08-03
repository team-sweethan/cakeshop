package com.cakeshop.domain.community.controller;

import com.cakeshop.domain.community.dto.form.BlockForm;
import com.cakeshop.domain.community.dto.view.AdminPostDetailView;
import com.cakeshop.domain.community.dto.view.AdminPostListView;
import com.cakeshop.domain.community.dto.view.AdminPostSort;
import com.cakeshop.domain.community.dto.view.ReportView;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.service.CommunityAdminService;
import com.cakeshop.domain.community.service.CommunityService;
import com.cakeshop.global.common.paging.PageNavigation;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.security.MemberDetails;

import jakarta.validation.Valid;

import java.util.List;

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
 * /admin/community 목록·상세와 게시글 차단·해제·신고 기각.
 *
 * 접근 제한은 SecurityConfig의 /admin/** → hasRole("ADMIN")이 맡는다(DOMAIN.md 5).
 * 화면에서 버튼을 감추는 것은 안내일 뿐이고, 막는 것은 언제나 Security와 Service다.
 *
 * <b>게시글 삭제도 댓글 삭제도 여기 없다.</b> 관리자의 조치는 차단과 해제, 그리고 신고
 * 기각뿐이다(6.7). 목업에 있던 그 버튼들은 규칙보다 먼저 그려진 것이라 조각 5에서 걷어냈다.
 */
@Controller
public class CommunityAdminController {

    private final CommunityAdminService communityAdminService;
    private final CommunityService communityService;

    public CommunityAdminController(
            CommunityAdminService communityAdminService, CommunityService communityService) {
        this.communityAdminService = communityAdminService;
        this.communityService = communityService;
    }

    /**
     * 관리자 목록. 상태 필터와 정렬만 받는다(DOMAIN.md 6.7).
     *
     * 고객 목록과 같이 주소에 이상한 값이 들어와도 오류 페이지 대신 기본 목록을 보여준다.
     * 관리자 화면이라고 다를 이유가 없다 — 주소가 망가진 것과 권한이 없는 것은 다르다.
     */
    @GetMapping("/admin/community")
    public String list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String page,
            Model model
    ) {
        PostStatus selectedStatus = parseStatus(status);
        AdminPostSort selectedSort = AdminPostSort.from(sort);

        PageRequest pageRequest =
                new PageRequest(parsePositiveInteger(page), PageRequest.DEFAULT_SIZE);

        PageResult<AdminPostListView> pageResult =
                communityAdminService.getPosts(selectedStatus, selectedSort, pageRequest);

        model.addAttribute("pageResult", pageResult);
        model.addAttribute(
                "pageNavigation",
                PageNavigation.of(pageResult.getPage(), pageResult.getTotalPages())
        );
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("selectedSort", selectedSort);

        return "admin/community/list";
    }

    /**
     * 관리자 상세. 차단된 글의 본문을 볼 수 있는 유일한 화면이다(DOMAIN.md 4.3).
     *
     * 댓글도 함께 보여 주지만 손댈 수는 없다. 댓글 삭제 권한은 그 댓글의 작성자에게만
     * 있다(6.4).
     */
    @GetMapping("/admin/community/{postId:\\d+}")
    public String detail(
            @PathVariable("postId") long postId,
            @ModelAttribute("blockForm") BlockForm blockForm,
            Model model
    ) {
        return prepareDetail(model, communityAdminService.getPostDetail(postId));
    }

    /**
     * 게시글을 차단하고 상세로 돌아간다. 사유가 비면 상세를 다시 그린다.
     *
     * 사유를 필수로 두는 것은 그 값이 작성자에게 그대로 보이기 때문이다(DOMAIN.md 4.3).
     * 차단된 글에 작성자가 할 수 있는 일은 없으므로, 사유가 없으면 글이 왜 막혔는지
     * 알 방법이 영영 없다.
     */
    @PostMapping("/admin/community/{postId:\\d+}/block")
    public String block(
            @PathVariable("postId") long postId,
            @Valid @ModelAttribute("blockForm") BlockForm blockForm,
            BindingResult bindingResult,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return prepareDetail(model, communityAdminService.getPostDetail(postId));
        }

        communityAdminService.blockPost(
                postId, blockForm.getReason(), memberDetails.getMemberId());

        redirectAttributes.addFlashAttribute("successMessage", "게시글을 차단했습니다.");

        return "redirect:/admin/community/" + postId;
    }

    /**
     * 차단을 해제하고 상세로 돌아간다. 입력값이 없으므로 폼도 없다 —
     * blocked_* 는 되돌리지 않는다(DOMAIN.md 4.2).
     */
    @PostMapping("/admin/community/{postId:\\d+}/unblock")
    public String unblock(
            @PathVariable("postId") long postId,
            RedirectAttributes redirectAttributes
    ) {
        communityAdminService.unblockPost(postId);

        redirectAttributes.addFlashAttribute("successMessage", "차단을 해제했습니다.");

        return "redirect:/admin/community/" + postId;
    }

    /**
     * 신고를 기각하고 상세로 돌아간다. 게시글은 그대로 둔다(DOMAIN.md 6.6).
     *
     * GET이 아니라 POST인 것은 게시글 삭제와 같은 이유다. 링크 미리보기나 크롤러가
     * 눌러서 신고가 닫히면 안 된다.
     */
    @PostMapping("/admin/community/{postId:\\d+}/reports/reject")
    public String rejectReports(
            @PathVariable("postId") long postId,
            RedirectAttributes redirectAttributes
    ) {
        communityAdminService.rejectReports(postId);

        redirectAttributes.addFlashAttribute("successMessage", "신고를 기각했습니다.");

        return "redirect:/admin/community/" + postId;
    }

    /**
     * 관리자 상세 화면의 모델을 채운다.
     *
     * 댓글은 고객 화면과 같은 Service를 쓴다. 관리자용으로 따로 만들면 자리 표시나 개수
     * 규칙(DOMAIN.md 4.4)이 두 벌이 되어 한쪽만 고치는 날이 온다.
     */
    private String prepareDetail(Model model, AdminPostDetailView post) {
        List<ReportView> reports = communityAdminService.getReports(post.id());

        model.addAttribute("post", post);
        model.addAttribute("reports", reports);
        // 이미 읽어 온 목록에서 센다. 같은 수를 DB에 다시 묻는 쿼리를 늘리지 않는다.
        model.addAttribute("pendingReportCount", reports.stream().filter(ReportView::isPending).count());
        model.addAttribute("commentSection", communityService.getComments(post.id(), null));

        return "admin/community/detail";
    }

    /**
     * 상태 필터 문자열을 PostStatus로 바꾼다. 모르는 값과 빈 값은 "전체"(null)다.
     * 정렬과 같은 처리이고, 이유도 같다 — 주소가 망가졌다고 오류 페이지를 주지 않는다.
     */
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

    /** 페이지 번호 문자열을 1 이상의 정수로 변환한다. 변환할 수 없으면 null이다. */
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
