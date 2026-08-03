package com.cakeshop.domain.community.service;

import java.util.List;

import com.cakeshop.domain.community.dto.form.PostForm;
import com.cakeshop.domain.community.dto.view.PostCategoryView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListView;
import com.cakeshop.domain.community.entity.Post;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * 게시글을 저장한다.
     *
     * @param form 입력값. 제목·본문은 폼에서 이미 다듬어져 있다
     * @param authorId 작성자 식별자. 인증 정보에서 얻은 값이어야 한다
     * @return 저장된 게시글 식별자
     * @throws BusinessException 활성 카테고리가 아닌 경우
     */
    @Transactional
    public long createPost(PostForm form, long authorId) {
        requireActiveCategory(form.getCategoryId());

        Post post = Post.create(
                authorId,
                form.getCategoryId(),
                form.getTitle(),
                form.getContent()
        );

        communityMapper.insertPost(post);

        return post.getId();
    }

    /**
     * 수정 화면에 채울 게시글을 조회한다.
     *
     * <p>상세와 달리 조회수를 올리지 않는다. 자기 글을 고치러 들어온 것은 조회가 아니다.
     *
     * @param postId 수정할 게시글 식별자
     * @param editorId 요청한 회원 식별자
     * @return 수정 대상 게시글
     * @throws BusinessException 대상이 없거나, 남의 글이거나, 차단된 글인 경우
     */
    @Transactional(readOnly = true)
    public PostDetailView getEditablePost(long postId, long editorId) {
        return requireEditablePost(postId, editorId);
    }

    /**
     * 게시글을 수정한다.
     *
     * @param postId 수정할 게시글 식별자
     * @param form 입력값
     * @param editorId 요청한 회원 식별자. 인증 정보에서 얻은 값이어야 한다
     * @throws BusinessException 대상이 없거나, 남의 글이거나, 차단된 글이거나,
     *         활성 카테고리가 아닌 경우
     */
    @Transactional
    public void updatePost(long postId, PostForm form, long editorId) {
        requireEditablePost(postId, editorId);
        requireActiveCategory(form.getCategoryId());

        Post post = Post.edit(
                postId,
                editorId,
                form.getCategoryId(),
                form.getTitle(),
                form.getContent()
        );

        communityMapper.updatePost(post);
    }

    /**
     * 게시글을 삭제 상태로 바꾼다.
     *
     * <p>행을 지우지 않는 soft delete다(DOMAIN.md 4.2). 댓글·좋아요·신고는 그대로 둔다(4.5).
     *
     * @param postId 삭제할 게시글 식별자
     * @param editorId 요청한 회원 식별자. 인증 정보에서 얻은 값이어야 한다
     * @throws BusinessException 대상이 없거나, 남의 글이거나, 차단된 글인 경우
     */
    @Transactional
    public void deletePost(long postId, long editorId) {
        requireEditablePost(postId, editorId);

        communityMapper.deletePost(postId, editorId);
    }

    /**
     * 요청자가 이 게시글을 고치거나 지울 수 있는지 확인하고 게시글을 돌려준다.
     *
     * <p>상태별 응답은 상세 접근 규칙(DOMAIN.md 4.3)을 그대로 따르되 차단된 글만 다르다.
     *
     * <ul>
     *   <li>없는 글·남의 글·{@code DELETED} → 404. 남에게 403을 주면 그 자리에 글이
     *       있다는 사실이 드러난다</li>
     *   <li>작성자의 {@code BLOCKED} 글 → 403. 상세에서 이미 본문과 차단 사유를 보여 준
     *       상대이므로 존재를 숨길 것이 없고, 404를 주면 왜 막혔는지 알 수 없다.
     *       차단된 글에 작성자가 할 수 있는 일은 없다(4.2)</li>
     * </ul>
     *
     * @param postId 대상 게시글 식별자
     * @param editorId 요청한 회원 식별자
     * @return 수정·삭제할 수 있는 게시글
     * @throws BusinessException 위 조건에 걸리는 경우
     */
    private PostDetailView requireEditablePost(long postId, long editorId) {
        PostDetailView post = communityMapper.findPostById(postId);

        if (post == null || !isAuthor(post, editorId) || post.status() == PostStatus.DELETED) {
            throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
        }

        if (post.status() == PostStatus.BLOCKED) {
            throw new BusinessException(CommunityErrorCode.BLOCKED_POST);
        }

        return post;
    }

    /**
     * 화면 선택지에 있는 카테고리인지 확인한다.
     *
     * <p>비활성 카테고리는 필터에도 글쓰기 선택지에도 나오지 않는다(DOMAIN.md 6.8).
     * 화면에 안 보이는 것과 저장할 수 없는 것은 다르므로 여기서 막는다.
     *
     * @param categoryId 확인할 카테고리 식별자
     * @throws BusinessException 활성 카테고리가 아닌 경우
     */
    private void requireActiveCategory(Long categoryId) {
        if (!communityMapper.existsActiveCategory(categoryId)) {
            throw new BusinessException(CommunityErrorCode.CATEGORY_NOT_FOUND);
        }
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
