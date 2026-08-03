package com.cakeshop.domain.community.controller;

import com.cakeshop.domain.community.dto.view.PostListView;
import com.cakeshop.domain.community.service.CommunityService;
import com.cakeshop.global.common.paging.PageNavigation;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.security.MemberDetails;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

// /community 목록·상세·작성 (GET은 공개). 작성 화면은 아직 목업이며 조각 2에서 연결한다.
@Controller
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    /**
     * 커뮤니티 목록 화면을 반환한다.
     *
     * <p>페이지 크기는 20 고정이며 사용자가 바꿀 수 없다(DOMAIN.md 6.1). 그래서 size
     * 파라미터를 받지 않는다.
     *
     * @param categoryId 카테고리 필터 요청값
     * @param page 요청 페이지 번호
     * @param model 목록과 페이지 정보를 전달할 모델
     * @return 커뮤니티 목록 템플릿 경로
     */
    @GetMapping("/community")
    public String list(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String page,
            Model model
    ) {
        // 목록은 비로그인도 열 수 있는 공개 화면이다. 주소에 이상한 값이 들어와도
        // 오류 페이지 대신 기본 목록을 보여준다.
        Long selectedCategoryId = parsePositiveLong(categoryId);

        PageRequest pageRequest = new PageRequest(
                parsePositiveInteger(page),
                PageRequest.DEFAULT_SIZE
        );

        PageResult<PostListView> pageResult =
                communityService.getPosts(selectedCategoryId, pageRequest);

        model.addAttribute("pageResult", pageResult);
        model.addAttribute(
                "pageNavigation",
                PageNavigation.of(pageResult.getPage(), pageResult.getTotalPages())
        );
        model.addAttribute("categories", communityService.getActiveCategories());
        model.addAttribute("selectedCategoryId", selectedCategoryId);

        return "customer/community/list";
    }

    /**
     * 게시글 상세 화면을 반환한다.
     *
     * <p>상세 경로는 SecurityConfig의 {@code /community/{id:\\d+}} 공개 규칙에 맞춰
     * 숫자 식별자만 받는다.
     *
     * @param postId 조회할 게시글 식별자
     * @param memberDetails 인증된 사용자. 비로그인이면 {@code null}
     * @param model 게시글 정보를 전달할 모델
     * @return 게시글 상세 템플릿 경로
     */
    @GetMapping("/community/{postId:\\d+}")
    public String detail(
            @PathVariable long postId,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model
    ) {
        // 소유권 판단 기준은 요청 파라미터가 아니라 인증 정보다(AGENTS.md).
        Long viewerId = memberDetails == null ? null : memberDetails.getMemberId();

        model.addAttribute("post", communityService.getPostDetail(postId, viewerId));

        return "customer/community/detail";
    }

    @GetMapping("/community/new")
    public String createForm() {
        return "customer/community/form";
    }

    /**
     * 페이지 번호 문자열을 1 이상의 정수로 변환한다.
     *
     * @param value 요청으로 전달된 문자열
     * @return 1 이상의 정수, 변환할 수 없으면 {@code null}
     */
    private Integer parsePositiveInteger(String value) {
        Long parsed = parsePositiveLong(value);

        if (parsed == null || parsed > Integer.MAX_VALUE) {
            return null;
        }

        return parsed.intValue();
    }

    /**
     * 식별자 문자열을 1 이상의 정수로 변환한다.
     *
     * @param value 요청으로 전달된 문자열
     * @return 1 이상의 정수, 변환할 수 없으면 {@code null}
     */
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
