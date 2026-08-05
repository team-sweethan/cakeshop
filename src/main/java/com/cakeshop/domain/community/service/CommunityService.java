package com.cakeshop.domain.community.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.cakeshop.domain.community.dto.form.CommentForm;
import com.cakeshop.domain.community.dto.form.PostForm;
import com.cakeshop.domain.community.dto.form.ReportForm;
import com.cakeshop.domain.community.dto.view.CommentCountView;
import com.cakeshop.domain.community.dto.view.CommentSectionView;
import com.cakeshop.domain.community.dto.view.CommentView;
import com.cakeshop.domain.community.dto.view.PopularPostView;
import com.cakeshop.domain.community.dto.view.PopularSectionView;
import com.cakeshop.domain.community.dto.view.PostCategoryView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.dto.view.PostListView;
import com.cakeshop.domain.community.dto.view.PostLockView;
import com.cakeshop.domain.community.dto.view.PostSort;
import com.cakeshop.domain.community.entity.Comment;
import com.cakeshop.domain.community.entity.Post;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class CommunityService {

    private static final Logger log = LoggerFactory.getLogger(CommunityService.class);

    private static final int POPULAR_POST_LIMIT = 10;

    private static final int FIRST_PAGE = 1;

    private static final LocalTime STALE_WARNING_GRACE_UNTIL = LocalTime.of(1, 0);

    private final CommunityMapper communityMapper;
    private final Clock clock;

    public CommunityService(CommunityMapper communityMapper, Clock clock) {
        this.communityMapper = communityMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResult<PostListView> getPosts(
            Long categoryId,
            PostSort sort,
            PageRequest pageRequest
    ) {
        List<PostListView> posts = communityMapper.findPublishedPosts(
                categoryId,
                sort,
                pageRequest.getSize(),
                pageRequest.getOffset()
        );

        long totalElements = communityMapper.countPublishedPosts(categoryId);

        return new PageResult<>(posts, pageRequest, totalElements);
    }

    @Transactional(readOnly = true)
    public PopularSectionView getPopularSection(Long categoryId, PageRequest pageRequest) {
        if (categoryId != null || pageRequest.getPage() != FIRST_PAGE) {
            return PopularSectionView.empty();
        }

        LocalDate rankingDate = communityMapper.findLatestRankingDate();

        if (rankingDate == null) {
            return PopularSectionView.empty();
        }

        warnIfRankingIsStale(rankingDate);

        List<PopularPostView> popularPosts =
                communityMapper.findPopularPosts(rankingDate, POPULAR_POST_LIMIT);

        if (popularPosts.isEmpty()) {
            return PopularSectionView.empty();
        }

        return new PopularSectionView(rankingDate, popularPosts);
    }

    private void warnIfRankingIsStale(LocalDate rankingDate) {
        LocalDateTime now = LocalDateTime.now(clock);

        if (!rankingDate.isBefore(now.toLocalDate().minusDays(1))) {
            return;
        }

        if (now.toLocalTime().isBefore(STALE_WARNING_GRACE_UNTIL)) {
            return;
        }

        log.warn(
                "인기글 확정 날짜가 어제보다 오래됐습니다. 배치가 돌지 않았을 수 있습니다."
                        + " latestRankingDate={}, now={}",
                rankingDate,
                now
        );
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

        Post post = Post.edit(
                postId,
                editorId,
                form.getCategoryId(),
                form.getTitle(),
                form.getContent()
        );

        requireApplied(communityMapper.updatePost(post), postId, editorId);
    }

    @Transactional
    public void deletePost(long postId, long editorId) {
        requireEditablePost(postId, editorId);

        requireApplied(communityMapper.deletePost(postId, editorId), postId, editorId);
    }

    @Transactional(readOnly = true)
    public CommentSectionView getComments(long postId, Integer requestedLimit) {
        int limit = CommentSectionView.clampLimit(requestedLimit);

        List<CommentView> recent = communityMapper.findRecentComments(postId, limit);
        CommentCountView counts = communityMapper.countComments(postId);

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
        requireOwnComment(postId, commentId, memberId);

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

        if (isAuthor(post, memberId)) {
            throw new BusinessException(CommunityErrorCode.OWN_POST_REPORT);
        }

        if (communityMapper.existsReport(postId, memberId)) {
            throw new BusinessException(CommunityErrorCode.ALREADY_REPORTED);
        }

        return post;
    }

    private void requireLikeablePost(long postId, long memberId) {
        PostLockView post = communityMapper.lockPost(postId);

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
        requireOwnComment(postId, commentId, memberId);

        throw new BusinessException(CommunityErrorCode.COMMENT_NOT_FOUND);
    }

    private PostDetailView requireCommentablePost(long postId, Long memberId) {
        PostDetailView post = requireVisiblePost(postId, memberId);

        if (post.status() != PostStatus.PUBLISHED) {
            throw new BusinessException(CommunityErrorCode.BLOCKED_POST);
        }

        return post;
    }

    private void requireOwnComment(long postId, long commentId, long memberId) {
        CommentView comment = communityMapper.findCommentById(commentId);

        if (comment == null
                || !comment.postId().equals(postId)
                || !comment.memberId().equals(memberId)
                || comment.isDeleted()) {
            throw new BusinessException(CommunityErrorCode.COMMENT_NOT_FOUND);
        }
    }

    private PostDetailView requireVisiblePost(long postId, Long viewerId) {
        PostDetailView post = communityMapper.findPostById(postId);

        if (post == null || !isVisibleTo(post, viewerId)) {
            throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
        }

        return post;
    }

    private void requireApplied(int affectedRows, long postId, long editorId) {
        if (affectedRows > 0) {
            return;
        }

        requireEditablePost(postId, editorId);

        throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
    }

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

    private void requireActiveCategory(Long categoryId) {
        if (!communityMapper.existsActiveCategory(categoryId)) {
            throw new BusinessException(CommunityErrorCode.CATEGORY_NOT_FOUND);
        }
    }

    private boolean isVisibleTo(PostDetailView post, Long viewerId) {
        return switch (post.status()) {
            case PUBLISHED -> true;
            case BLOCKED -> isAuthor(post, viewerId);
            case DELETED -> false;
        };
    }

    private boolean isAuthor(PostDetailView post, Long viewerId) {
        return viewerId != null && viewerId.equals(post.memberId());
    }
}
