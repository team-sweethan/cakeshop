package com.cakeshop.domain.community.service;

import java.util.List;

import com.cakeshop.domain.community.dto.view.PostCategoryView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListView;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// TODO: 작성·수정·삭제는 작성자 본인 검증
@Service
public class CommunityService {

    private final CommunityMapper communityMapper;

    public CommunityService(CommunityMapper communityMapper) {
        this.communityMapper = communityMapper;
    }

    /**
     * 노출 중인 게시글 목록을 조회한다.
     *
     * @param categoryId 카테고리 필터. {@code null}이면 전체 카테고리
     * @param pageRequest 페이지 요청. 크기는 호출자가 고정한다
     * @return 조건에 맞는 게시글 페이지
     */
    @Transactional(readOnly = true)
    public PageResult<PostListView> getPosts(Long categoryId, PageRequest pageRequest) {
        List<PostListView> posts = communityMapper.findPublishedPosts(
                categoryId,
                pageRequest.getSize(),
                pageRequest.getOffset()
        );

        long totalElements = communityMapper.countPublishedPosts(categoryId);

        return new PageResult<>(posts, pageRequest, totalElements);
    }

    /**
     * 게시글 상세를 조회하고 조회수를 올린다.
     *
     * <p>조회수 증가와 상세 조회를 한 트랜잭션에서 처리한다(DOMAIN.md 6.2). 노출되지 않는
     * 게시글은 Mapper의 UPDATE 조건에서 걸러지므로 조회수가 오르지 않는다.
     *
     * <p>노출 판단은 DOMAIN.md 4.3의 표를 그대로 따른다. 차단된 글은 작성자 본인에게만
     * 사유와 함께 보여 준다. 작성자는 차단된 글에 아무 조치도 할 수 없으므로(4.2),
     * 404까지 주면 글이 왜 사라졌는지 알 방법이 없다.
     *
     * @param postId 조회할 게시글 식별자
     * @param viewerId 조회하는 회원 식별자. 비로그인이면 {@code null}
     * @return 게시글 상세
     * @throws BusinessException 게시글이 없거나 요청자에게 노출할 수 없는 상태인 경우
     */
    @Transactional
    public PostDetailView getPostDetail(long postId, Long viewerId) {
        communityMapper.increaseViewCount(postId);

        PostDetailView post = communityMapper.findPostById(postId);

        if (post == null) {
            throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
        }

        if (!isVisibleTo(post, viewerId)) {
            // 삭제·차단·미존재를 구분하지 않는다. 구분하면 글의 존재가 드러난다.
            throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
        }

        return post;
    }

    /**
     * 화면의 카테고리 선택지를 조회한다.
     *
     * @return 활성 카테고리 목록
     */
    @Transactional(readOnly = true)
    public List<PostCategoryView> getActiveCategories() {
        return communityMapper.findActiveCategories();
    }

    /**
     * 고객 경로에서 이 게시글을 요청자에게 보여줄 수 있는지 판단한다.
     *
     * <p>관리자도 고객 경로에서는 일반 회원과 똑같이 취급한다. 모든 상태를 보려면
     * {@code /admin/community/{id}}로 들어와야 한다(DOMAIN.md 4.3).
     *
     * @param post 조회한 게시글
     * @param viewerId 조회하는 회원 식별자. 비로그인이면 {@code null}
     * @return 보여줄 수 있으면 {@code true}
     */
    private boolean isVisibleTo(PostDetailView post, Long viewerId) {
        return switch (post.status()) {
            case PUBLISHED -> true;
            case BLOCKED -> isAuthor(post, viewerId);
            case DELETED -> false;
        };
    }

    /**
     * 요청자가 게시글 작성자인지 확인한다.
     *
     * <p>요청으로 전달된 회원 ID가 아니라 인증된 사용자 기준으로 판단해야 하므로,
     * 호출자는 인증 정보에서 얻은 값만 넘긴다(AGENTS.md).
     *
     * @param post 대상 게시글
     * @param viewerId 조회하는 회원 식별자
     * @return 작성자 본인이면 {@code true}
     */
    private boolean isAuthor(PostDetailView post, Long viewerId) {
        return viewerId != null && viewerId.equals(post.memberId());
    }
}
