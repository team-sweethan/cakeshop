package com.cakeshop.domain.community.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;

import com.cakeshop.domain.community.dto.command.PostUpdateCommand;
import com.cakeshop.domain.community.dto.form.CommentForm;
import com.cakeshop.domain.community.dto.form.PostForm;
import com.cakeshop.domain.community.dto.form.ReportForm;
import com.cakeshop.domain.community.dto.query.CommentCountRow;
import com.cakeshop.domain.community.dto.view.CommentSectionView;
import com.cakeshop.domain.community.dto.query.CommentRow;
import com.cakeshop.domain.community.dto.view.CommentView;
import com.cakeshop.domain.community.dto.view.PopularSectionView;
import com.cakeshop.domain.community.dto.view.PostCategoryView;
import com.cakeshop.domain.community.dto.query.PostDetailRow;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.query.PostListRow;
import com.cakeshop.domain.community.dto.view.PostListView;
import com.cakeshop.domain.community.dto.query.PostLockRow;
import com.cakeshop.domain.community.dto.view.PostSort;
import com.cakeshop.domain.community.entity.Comment;
import com.cakeshop.domain.community.entity.CommentStatus;
import com.cakeshop.domain.community.entity.Post;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.domain.member.dto.view.MemberCommunityView;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 비즈니스 로직
 * 설명 : CommunityService 기능의 권한, 상태, 트랜잭션을 관리한다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
public class CommunityService {

    /* 목록 화면이 쓰는 건수. 메인은 다른 값을 쓴다(`CommunityHomeQueryService`). */
    private static final int POPULAR_POST_LIMIT = 10;

    private static final int FIRST_PAGE = 1;

    private final CommunityMapper communityMapper;
    private final CommunityMemberViewLoader communityMemberViewLoader;
    private final PopularPostReader popularPostReader;

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
    }

    @Transactional
    public void deletePost(long postId, long editorId) {
        requireEditablePost(postId, editorId);

        requireApplied(communityMapper.deletePost(postId, editorId), postId, editorId);
    }

    @Transactional(readOnly = true)
    public CommentSectionView getComments(long postId, Integer requestedLimit) {
        int limit = CommentSectionView.clampLimit(requestedLimit);

        List<CommentRow> rows = communityMapper.findRecentComments(postId, limit);
        CommentCountRow counts = communityMapper.countComments(postId);

        Map<Long, MemberCommunityView> authors =
                communityMemberViewLoader.findByIds(rows.stream().map(CommentRow::memberId));

        List<CommentView> recent = rows.stream()
                .map(row -> CommentView.of(row, authors.get(row.memberId())))
                .toList();

        return new CommentSectionView(
                List.copyOf(recent.reversed()),
                counts.publishedCount(),
                counts.rowCount(),
                limit
        );
    }

    @Transactional(readOnly = true)
    public PostDetailView getCommentablePost(long postId, long memberId) {
        return requireCommentablePost(postId, memberId);
    }

    @Transactional
    public void addComment(long postId, CommentForm form, long authorId) {
        requireCommentablePost(postId, authorId);

        communityMapper.insertComment(
                Comment.create(postId, authorId, form.getContent()));
    }

    @Transactional
    public void deleteComment(long postId, long commentId, long memberId) {
        requireCommentablePost(postId, memberId);
        requireOwnCommentTransition(
                postId, commentId, memberId, CommentStatus.DELETED);

        requireCommentApplied(
                communityMapper.deleteComment(commentId, postId, memberId),
                postId,
                commentId,
                memberId
        );
    }

    @Transactional
    public void addLike(long postId, long memberId) {
        requireLikeablePost(postId, memberId);

        communityMapper.insertLike(postId, memberId);
        communityMapper.recalculateLikeCount(postId);
    }

    @Transactional
    public void removeLike(long postId, long memberId) {
        requireLikeablePost(postId, memberId);

        communityMapper.deleteLike(postId, memberId);
        communityMapper.recalculateLikeCount(postId);
    }

    @Transactional(readOnly = true)
    public boolean isLikedBy(long postId, long memberId) {
        return communityMapper.existsLike(postId, memberId);
    }

    @Transactional
    public void reportPost(long postId, ReportForm form, long reporterId) {
        requireReportablePost(postId, reporterId);

        try {
            communityMapper.insertReport(postId, reporterId, form.getReason());
        } catch (DuplicateKeyException e) {
            throw new BusinessException(CommunityErrorCode.ALREADY_REPORTED);
        }
    }

    @Transactional(readOnly = true)
    public PostDetailView getReportablePost(long postId, long memberId) {
        return requireReportablePost(postId, memberId);
    }

    @Transactional(readOnly = true)
    public boolean isReportedBy(long postId, long memberId) {
        return communityMapper.existsReport(postId, memberId);
    }

    private PostDetailView requireReportablePost(long postId, long memberId) {
        PostDetailView post = requireVisiblePost(postId, memberId);

        if (isAuthor(post.memberId(), memberId)) {
            throw new BusinessException(CommunityErrorCode.OWN_POST_REPORT);
        }

        if (communityMapper.existsReport(postId, memberId)) {
            throw new BusinessException(CommunityErrorCode.ALREADY_REPORTED);
        }

        return post;
    }

    private void requireLikeablePost(long postId, long memberId) {
        PostLockRow post = communityMapper.lockPost(postId);

        if (post == null
                || post.status() == PostStatus.DELETED
                || (post.status() == PostStatus.BLOCKED
                        && !Long.valueOf(memberId).equals(post.memberId()))) {
            throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
        }

        if (post.status() != PostStatus.PUBLISHED) {
            throw new BusinessException(CommunityErrorCode.BLOCKED_POST);
        }
    }

    private void requireCommentApplied(
            int affectedRows, long postId, long commentId, long memberId) {
        if (affectedRows > 0) {
            return;
        }

        requireCommentablePost(postId, memberId);
        requireOwnCommentTransition(
                postId, commentId, memberId, CommentStatus.DELETED);

        throw new BusinessException(CommunityErrorCode.COMMENT_NOT_FOUND);
    }

    private PostDetailView requireCommentablePost(long postId, Long memberId) {
        PostDetailView post = requireVisiblePost(postId, memberId);

        if (post.status() != PostStatus.PUBLISHED) {
            throw new BusinessException(CommunityErrorCode.BLOCKED_POST);
        }

        return post;
    }

    private void requireOwnCommentTransition(
            long postId,
            long commentId,
            long memberId,
            CommentStatus next) {
        // 소유권 판단에만 쓰므로 작성자 표기가 필요 없고, 그래서 회원 조회도 붙지 않는다.
        CommentRow comment = communityMapper.findCommentById(commentId);

        if (comment == null
                || !comment.postId().equals(postId)
                || !comment.memberId().equals(memberId)
                || !comment.status().canTransitionTo(next)) {
            throw new BusinessException(CommunityErrorCode.COMMENT_NOT_FOUND);
        }
    }

    private PostDetailView requireVisiblePost(long postId, Long viewerId) {
        PostDetailRow post = communityMapper.findPostById(postId);

        if (post == null || !isVisibleTo(post, viewerId)) {
            throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
        }

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
        PostDetailRow post = communityMapper.findPostById(postId);

        if (post == null
                || !isAuthor(post.memberId(), editorId)
                || post.status() == PostStatus.DELETED) {
            throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
        }

        if (post.status() == PostStatus.BLOCKED) {
            throw new BusinessException(CommunityErrorCode.BLOCKED_POST);
        }

        return toDetailView(post);
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

    private boolean isVisibleTo(PostDetailRow post, Long viewerId) {
        return switch (post.status()) {
            case PUBLISHED -> true;
            case BLOCKED -> isAuthor(post.memberId(), viewerId);
            case DELETED -> false;
        };
    }

    private boolean isAuthor(Long postMemberId, Long viewerId) {
        return viewerId != null && viewerId.equals(postMemberId);
    }
}
