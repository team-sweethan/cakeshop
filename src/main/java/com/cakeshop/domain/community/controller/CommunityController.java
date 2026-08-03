package com.cakeshop.domain.community.controller;

import com.cakeshop.domain.community.dto.form.PostForm;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListView;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.service.CommunityService;
import com.cakeshop.global.error.BusinessException;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// /community 목록·상세·작성·수정·삭제. 목록과 상세 GET만 공개다(DOMAIN.md 5).
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
            @PathVariable("postId") long postId,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model
    ) {
        // 소유권 판단 기준은 요청 파라미터가 아니라 인증 정보다(AGENTS.md).
        Long viewerId = memberDetails == null ? null : memberDetails.getMemberId();

        PostDetailView post = communityService.getPostDetail(postId, viewerId);

        model.addAttribute("post", post);
        // 차단된 글은 작성자 본인만 여기까지 오지만 고칠 수도 지울 수도 없다(DOMAIN.md 4.2).
        // 버튼을 숨기는 것은 안내일 뿐이고, 실제로 막는 것은 Service다.
        model.addAttribute(
                "canEdit",
                viewerId != null && viewerId.equals(post.memberId()) && !post.isBlocked()
        );

        return "customer/community/detail";
    }

    /**
     * 글쓰기 화면을 반환한다.
     *
     * @param form 빈 입력 폼
     * @param model 카테고리 선택지를 전달할 모델
     * @return 글쓰기 템플릿 경로
     */
    @GetMapping("/community/new")
    public String createForm(@ModelAttribute("form") PostForm form, Model model) {
        return prepareForm(model, null);
    }

    /**
     * 게시글을 저장하고 상세로 보낸다.
     *
     * @param form 입력 폼
     * @param bindingResult 입력 검증 결과
     * @param memberDetails 인증된 사용자
     * @param model 카테고리 선택지를 전달할 모델
     * @return 저장 성공 시 상세로 리다이렉트, 검증 실패 시 글쓰기 화면
     */
    @PostMapping("/community")
    public String create(
            @Valid @ModelAttribute("form") PostForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            return prepareForm(model, null);
        }

        long postId;

        try {
            postId = communityService.createPost(form, memberDetails.getMemberId());
        } catch (BusinessException e) {
            return rejectCategoryOrRethrow(e, bindingResult, model, null);
        }

        return "redirect:/community/" + postId;
    }

    /**
     * 수정 화면을 반환한다.
     *
     * <p>작성 화면과 같은 템플릿을 쓴다(선례: {@code ProductAdminController}). 수정 항목이
     * 작성 항목과 같아서 폼 마크업을 두 벌로 두면 한쪽만 고치는 일이 생긴다.
     *
     * @param postId 수정할 게시글 식별자
     * @param form 입력 폼. 기존 값으로 채워 넣는다
     * @param memberDetails 인증된 사용자
     * @param model 카테고리 선택지와 대상 식별자를 전달할 모델
     * @return 글쓰기·수정 템플릿 경로
     */
    @GetMapping("/community/{postId:\\d+}/edit")
    public String editForm(
            @PathVariable("postId") long postId,
            @ModelAttribute("form") PostForm form,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model
    ) {
        PostDetailView post =
                communityService.getEditablePost(postId, memberDetails.getMemberId());

        form.setCategoryId(post.categoryId());
        form.setTitle(post.title());
        form.setContent(post.content());

        return prepareForm(model, postId);
    }

    /**
     * 게시글을 수정하고 상세로 보낸다.
     *
     * @param postId 수정할 게시글 식별자
     * @param form 입력 폼
     * @param bindingResult 입력 검증 결과
     * @param memberDetails 인증된 사용자
     * @param model 카테고리 선택지와 대상 식별자를 전달할 모델
     * @return 수정 성공 시 상세로 리다이렉트, 검증 실패 시 수정 화면
     */
    @PostMapping("/community/{postId:\\d+}/edit")
    public String edit(
            @PathVariable("postId") long postId,
            @Valid @ModelAttribute("form") PostForm form,
            BindingResult bindingResult,
            @AuthenticationPrincipal MemberDetails memberDetails,
            Model model
    ) {
        // 권한을 검증 실패보다 먼저 본다. 순서가 뒤집히면 남의 글 번호로 빈 본문을 보냈을 때
        // 소유권도 상태도 확인하지 않은 채 수정 화면이 200으로 열린다. 유효한 값을 보내야
        // 그제서야 404가 나는 화면은, 열려 있는 동안 자기 글인 것처럼 보인다.
        communityService.getEditablePost(postId, memberDetails.getMemberId());

        if (bindingResult.hasErrors()) {
            return prepareForm(model, postId);
        }

        try {
            communityService.updatePost(postId, form, memberDetails.getMemberId());
        } catch (BusinessException e) {
            return rejectCategoryOrRethrow(e, bindingResult, model, postId);
        }

        return "redirect:/community/" + postId;
    }

    /**
     * 게시글을 삭제하고 목록으로 보낸다.
     *
     * <p>GET이 아니라 POST다. 링크 미리보기나 크롤러가 눌러서 글이 지워지면 안 된다.
     *
     * @param postId 삭제할 게시글 식별자
     * @param memberDetails 인증된 사용자
     * @param redirectAttributes 성공 메시지를 담을 flash 속성
     * @return 목록으로 리다이렉트
     */
    @PostMapping("/community/{postId:\\d+}/delete")
    public String delete(
            @PathVariable("postId") long postId,
            @AuthenticationPrincipal MemberDetails memberDetails,
            RedirectAttributes redirectAttributes
    ) {
        communityService.deletePost(postId, memberDetails.getMemberId());

        // 삭제 결과는 목록에 남지 않는다. 알리지 않으면 지워졌는지 알 수 없다.
        redirectAttributes.addFlashAttribute("successMessage", "게시글을 삭제했습니다.");

        return "redirect:/community";
    }

    /**
     * 분류 선택 오류면 폼으로 되돌리고, 그 밖의 업무 예외는 그대로 올린다.
     *
     * <p>글을 쓰는 동안 관리자가 그 분류를 비활성으로 바꾸면 저장이 거절된다. 이때 예외를
     * 그대로 흘리면 GlobalExceptionHandler가 공통 4xx 화면을 그리고 <b>쓰던 제목과 본문이
     * 사라진다.</b> 분류는 화면에서 다시 고르면 되는 입력 오류이므로 해당 필드에 붙여
     * 돌려준다(conventions.md 9).
     *
     * <p>소유권·상태 오류(404·403)는 화면에서 고칠 수 없으므로 여기서 삼키지 않는다.
     *
     * @param e 발생한 업무 예외
     * @param bindingResult 오류를 붙일 검증 결과
     * @param model 화면 모델
     * @param postId 수정 대상 식별자. 작성이면 {@code null}
     * @return 입력을 유지한 작성·수정 화면
     * @throws BusinessException 분류 오류가 아닌 경우 그대로 다시 던진다
     */
    private String rejectCategoryOrRethrow(
            BusinessException e, BindingResult bindingResult, Model model, Long postId) {
        if (e.getErrorCode() != CommunityErrorCode.CATEGORY_NOT_FOUND) {
            throw e;
        }

        bindingResult.rejectValue(
                "categoryId", "categoryNotFound", e.getErrorCode().message());

        return prepareForm(model, postId);
    }

    /**
     * 작성·수정 화면에 공통으로 필요한 모델을 채운다.
     *
     * <p>분류 선택지는 목록 필터와 같은 활성 카테고리를 쓴다. 화면에 직접 적어 두면
     * 비활성 카테고리가 선택지에 남는다(DOMAIN.md 6.8).
     *
     * @param model 화면 모델
     * @param postId 수정 대상 식별자. 작성 화면이면 {@code null}
     * @return 글쓰기·수정 템플릿 경로
     */
    private String prepareForm(Model model, Long postId) {
        model.addAttribute("categories", communityService.getActiveCategories());
        model.addAttribute("editingPostId", postId);

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
