package com.cakeshop.domain.community.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;

import com.cakeshop.domain.community.dto.command.PostUpdateCommand;
import com.cakeshop.domain.community.dto.form.PostForm;
import com.cakeshop.domain.community.dto.view.PopularSectionView;
import com.cakeshop.domain.community.dto.view.PostCategoryView;
import com.cakeshop.domain.community.dto.query.PostDetailRow;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.query.PostListRow;
import com.cakeshop.domain.community.dto.view.PostListView;
import com.cakeshop.domain.community.dto.view.PostSort;
import com.cakeshop.domain.community.entity.Post;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.domain.member.dto.view.MemberCommunityView;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 비즈니스 로직
 * 설명 : CommunityPostService 기능의 권한, 상태, 트랜잭션을 관리한다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
public class CommunityPostService {

    /* 목록 화면이 쓰는 건수. 메인은 다른 값을 쓴다(`CommunityHomeQueryService`). */
    private static final int POPULAR_POST_LIMIT = 10;

    private static final int FIRST_PAGE = 1;

    private final CommunityMapper communityMapper;
    private final CommunityMemberViewLoader communityMemberViewLoader;
    private final CommunityPostAccessPolicy communityPostAccessPolicy;
    private final PopularPostReader popularPostReader;
    private final CommunityPostImageService communityPostImageService;

    @Transactional(readOnly = true)
    public PageResult<PostListView> getPosts(
            Long categoryId,
            PostSort sort,
            PageRequest pageRequest
    ) {
        List<PostListRow> rows = communityMapper.findPublishedPosts(
                categoryId,
                sort,
                pageRequest.getSize(),
                pageRequest.getOffset()
        );

        long totalElements = communityMapper.countPublishedPosts(categoryId);

        Map<Long, MemberCommunityView> authors =
                communityMemberViewLoader.findByIds(rows.stream().map(PostListRow::memberId));

        List<PostListView> posts = rows.stream()
                .map(row -> PostListView.of(row, authors.get(row.memberId())))
                .toList();

        return new PageResult<>(posts, pageRequest, totalElements);
    }

    @Transactional(readOnly = true)
    public PopularSectionView getPopularSection(Long categoryId, PageRequest pageRequest) {
        if (categoryId != null || pageRequest.getPage() != FIRST_PAGE) {
            return PopularSectionView.empty();
        }

        return popularPostReader.read(POPULAR_POST_LIMIT);
    }

    @Transactional
    public PostDetailView getPostDetail(long postId, Long viewerId, String viewerKey) {
        if (communityMapper.increaseViewCount(postId, viewerKey) > 0) {
            communityMapper.recordView(postId, viewerKey);
        }

        return requireVisiblePost(postId, viewerId);
    }

    @Transactional(readOnly = true)
    public PostDetailView getVisiblePost(long postId, Long viewerId) {
        return requireVisiblePost(postId, viewerId);
    }

    @Transactional(readOnly = true)
    public List<PostCategoryView> getActiveCategories() {
        return communityMapper.findActiveCategories();
    }

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

        // 같은 트랜잭션 안에서 붙인다. 나누면 글은 올라갔는데 첨부만 빠진 글이 생긴다.
        communityPostImageService.attach(post.getId(), form.getImages());

        return post.getId();
    }

    @Transactional(readOnly = true)
    public PostDetailView getEditablePost(long postId, long editorId) {
        return requireEditablePost(postId, editorId);
    }

    @Transactional
    public void updatePost(long postId, PostForm form, long editorId) {
        requireEditablePost(postId, editorId);
        requireActiveCategory(form.getCategoryId());

        PostUpdateCommand command = new PostUpdateCommand(
                postId,
                editorId,
                form.getCategoryId(),
                form.getTitle(),
                form.getContent()
        );

        requireApplied(communityMapper.updatePost(command), postId, editorId);

        communityPostImageService.applyEdit(
                postId,
                editorId,
                form.getDeleteImageIds(),
                form.getImages()
        );
    }

    @Transactional
    public void deletePost(long postId, long editorId) {
        requireEditablePost(postId, editorId);

        requireApplied(communityMapper.deletePost(postId, editorId), postId, editorId);
    }

    @Transactional(readOnly = true)
    public PostDetailView getCommentablePost(long postId, long memberId) {
        return requireCommentablePost(postId, memberId);
    }

    private PostDetailView requireCommentablePost(long postId, Long memberId) {
        PostDetailView post = requireVisiblePost(postId, memberId);

        communityPostAccessPolicy.requirePublished(post.status());

        return post;
    }

    private PostDetailView requireVisiblePost(long postId, Long viewerId) {
        PostDetailRow post = requireFoundPost(postId);

        communityPostAccessPolicy.requireVisible(post.status(), post.memberId(), viewerId);

        return toDetailView(post);
    }

    private void requireApplied(int affectedRows, long postId, long editorId) {
        if (affectedRows > 0) {
            return;
        }

        requireEditablePost(postId, editorId);

        throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
    }

    private PostDetailView requireEditablePost(long postId, long editorId) {
        PostDetailRow post = requireFoundPost(postId);

        communityPostAccessPolicy.requireEditable(post.status(), post.memberId(), editorId);

        return toDetailView(post);
    }

    private PostDetailRow requireFoundPost(long postId) {
        PostDetailRow post = communityMapper.findPostById(postId);

        if (post == null) {
            throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
        }

        return post;
    }

    /**
     * 매퍼가 읽어 온 상세 한 줄에 작성자를 붙여 화면용 DTO로 만든다.
     *
     * <p>노출·소유권 판단은 {@link PostDetailRow}만으로 끝나므로, 상세가 실제로 화면으로
     * 나가는 자리에서만 회원을 조회한다.</p>
     */
    private PostDetailView toDetailView(PostDetailRow row) {
        return PostDetailView.of(row, communityMemberViewLoader.findByIds(Stream.of(row.memberId())).get(row.memberId()));
    }

    private void requireActiveCategory(Long categoryId) {
        if (!communityMapper.existsActiveCategory(categoryId)) {
            throw new BusinessException(CommunityErrorCode.CATEGORY_NOT_FOUND);
        }
    }

}
