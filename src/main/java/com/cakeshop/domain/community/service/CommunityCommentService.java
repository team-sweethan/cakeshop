package com.cakeshop.domain.community.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;

import com.cakeshop.domain.community.dto.form.CommentForm;
import com.cakeshop.domain.community.dto.query.CommentCountRow;
import com.cakeshop.domain.community.dto.query.CommentRow;
import com.cakeshop.domain.community.dto.query.ReplyCountRow;
import com.cakeshop.domain.community.dto.view.CommentSectionView;
import com.cakeshop.domain.community.dto.view.CommentThreadView;
import com.cakeshop.domain.community.dto.view.CommentView;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.entity.Comment;
import com.cakeshop.domain.community.entity.CommentStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityCommentMapper;
import com.cakeshop.domain.member.dto.view.MemberCommunityView;
import com.cakeshop.global.error.BusinessException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-18
 * 기능 : 커뮤니티 비즈니스 로직
 * 설명 : CommunityCommentService 기능의 권한, 상태, 트랜잭션을 관리한다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
public class CommunityCommentService {

    private static final Comparator<CommentRow> NEWEST_COMMENT_FIRST =
            Comparator.comparing(CommentRow::createdAt).reversed()
                    .thenComparing(CommentRow::id, Comparator.reverseOrder());

    private final CommunityCommentMapper communityCommentMapper;
    private final CommunityMemberViewLoader communityMemberViewLoader;

    /*
     * 댓글을 달 수 있는 글인지는 게시글 쪽이 판단한다. 여기서 다시 읽으면 노출 규칙이
     * 두 벌이 되고, 그 두 벌은 관리자가 글을 차단하는 순간 갈린다.
     */
    private final CommunityPostService communityPostService;

    private final CommunityCommentNotificationService communityCommentNotificationService;

    @Transactional(readOnly = true)
    public CommentSectionView getComments(long postId, Integer requestedLimit, Long expandedRootId) {
        return getComments(postId, requestedLimit, expandedRootId, null);
    }

    /** 알림 deep link가 가리킨 댓글은 현재 댓글 창 밖이어도 제한 안에서 반드시 포함한다. */
    @Transactional(readOnly = true)
    public CommentSectionView getFocusedComments(long postId, long focusedCommentId) {
        return getComments(postId, null, null, focusedCommentId);
    }

    private CommentSectionView getComments(
            long postId,
            Integer requestedLimit,
            Long expandedRootId,
            Long focusedCommentId
    ) {
        int limit = CommentSectionView.clampLimit(requestedLimit);

        CommentRow focused = focusedCommentId == null
                ? null
                : requireFocusedComment(postId, focusedCommentId);

        List<CommentRow> roots = new ArrayList<>(
                communityCommentMapper.findRecentRootComments(postId, limit));

        Long focusedRootId = null;
        if (focused != null) {
            focusedRootId = focused.isReply() ? focused.parentCommentId() : focused.id();
            CommentRow focusedRoot = focused.isReply()
                    ? requireFocusedComment(postId, focusedRootId)
                    : focused;
            includeWithinLimit(roots, focusedRoot, limit);
        }

        CommentCountRow counts = communityCommentMapper.countComments(postId);
        Map<Long, Long> replyCounts = countReplies(roots);

        /*
         * 창 밖의 뿌리는 펼치지 않는다 — 주소로 임의의 id가 들어와도 화면에 없는 묶음을
         * 조회하지 않는다. 답글 상한이 뿌리 상한과 같은 값인 이유도 같다(주소 하나로
         * 전부 메모리에 올릴 수 없어야 한다).
         */
        Long requestedExpandedRootId = focused != null && focused.isReply()
                ? focusedRootId
                : expandedRootId;
        Long expanded = roots.stream()
                .map(CommentRow::id)
                .filter(id -> id.equals(requestedExpandedRootId))
                .findFirst()
                .orElse(null);

        List<CommentRow> replies = new ArrayList<>(expanded == null
                ? List.of()
                : communityCommentMapper.findRepliesByParentId(
                        expanded, postId, CommentSectionView.MAX_LIMIT));

        if (focused != null && focused.isReply()) {
            includeWithinLimit(replies, focused, CommentSectionView.MAX_LIMIT);
        }

        Map<Long, MemberCommunityView> authors = communityMemberViewLoader.findByIds(
                Stream.concat(roots.stream(), replies.stream()).map(CommentRow::memberId));

        // 답글도 뿌리처럼 최신 쪽을 남기고 화면은 오래된 순이다 — 그래서 여기서 뒤집는다
        List<CommentView> replyViews = replies.reversed().stream()
                .map(row -> CommentView.of(row, authors.get(row.memberId())))
                .toList();

        List<CommentThreadView> threads = roots.reversed().stream()
                .map(row -> {
                    CommentView root = CommentView.of(row, authors.get(row.memberId()));
                    long replyCount = replyCounts.getOrDefault(row.id(), 0L);

                    return row.id().equals(expanded)
                            ? CommentThreadView.expanded(root, replyCount, replyViews)
                            : CommentThreadView.collapsed(root, replyCount);
                })
                .toList();

        return new CommentSectionView(
                threads,
                counts.publishedCount(),
                counts.rootRowCount(),
                limit
        );
    }

    private CommentRow requireFocusedComment(long postId, long commentId) {
        CommentRow comment = communityCommentMapper.findCommentById(commentId);

        if (comment == null || !comment.postId().equals(postId)) {
            throw new BusinessException(CommunityErrorCode.COMMENT_NOT_FOUND);
        }

        return comment;
    }

    /*
     * 알림 대상이 현재 창 밖이면 최신 목록의 가장 오래된 한 행을 대신한다. 이렇게 해야 뿌리·답글
     * 상한을 늘리지 않으면서도 deep link의 목적지는 반드시 화면에 남는다.
     */
    private void includeWithinLimit(List<CommentRow> comments, CommentRow target, int limit) {
        if (comments.stream().anyMatch(comment -> comment.id().equals(target.id()))) {
            return;
        }

        if (comments.size() >= limit) {
            comments.removeLast();
        }

        comments.add(target);
        comments.sort(NEWEST_COMMENT_FIRST);
    }

    private Map<Long, Long> countReplies(List<CommentRow> roots) {
        if (roots.isEmpty()) {
            return Map.of();
        }

        return communityCommentMapper
                .countRepliesByParentIds(roots.stream().map(CommentRow::id).toList())
                .stream()
                .collect(Collectors.toMap(ReplyCountRow::parentId, ReplyCountRow::replyCount));
    }

    @Transactional
    public void addComment(long postId, CommentForm form, long authorId) {
        PostDetailView post = communityPostService.getCommentablePost(postId, authorId);

        Comment comment = Comment.create(postId, authorId, form.getContent());
        communityCommentMapper.insertComment(comment);

        communityCommentNotificationService.notifyNewComment(
                postId, comment.getId(), post.memberId(), authorId);
    }

    /*
     * 답글의 깊이·같은 글·부모 노출 조건은 insertReply 한 문장이 지킨다(0행이면 거절).
     * 부모를 먼저 읽고 조건문으로 거르면 읽기와 쓰기 사이에 부모가 삭제될 수 있다.
     */
    @Transactional
    public void addReply(long postId, long parentCommentId, CommentForm form, long authorId) {
        communityPostService.getCommentablePost(postId, authorId);

        Comment reply = Comment.createReply(
                postId, parentCommentId, authorId, form.getContent());

        if (communityCommentMapper.insertReply(reply) == 0) {
            throw new BusinessException(CommunityErrorCode.COMMENT_NOT_FOUND);
        }

        communityCommentNotificationService.notifyNewReply(
                postId, reply.getId(), parentCommentId, authorId);
    }

    @Transactional
    public void deleteComment(long postId, long commentId, long memberId) {
        communityPostService.getCommentablePost(postId, memberId);
        requireOwnCommentTransition(
                postId, commentId, memberId, CommentStatus.DELETED);

        requireCommentApplied(
                communityCommentMapper.deleteComment(commentId, postId, memberId),
                postId,
                commentId,
                memberId
        );
    }

    private void requireCommentApplied(
            int affectedRows, long postId, long commentId, long memberId) {
        if (affectedRows > 0) {
            return;
        }

        communityPostService.getCommentablePost(postId, memberId);
        requireOwnCommentTransition(
                postId, commentId, memberId, CommentStatus.DELETED);

        throw new BusinessException(CommunityErrorCode.COMMENT_NOT_FOUND);
    }

    private void requireOwnCommentTransition(
            long postId,
            long commentId,
            long memberId,
            CommentStatus next) {
        // 소유권 판단에만 쓰므로 작성자 표기가 필요 없고, 그래서 회원 조회도 붙지 않는다.
        CommentRow comment = communityCommentMapper.findCommentById(commentId);

        if (comment == null
                || !comment.postId().equals(postId)
                || !comment.memberId().equals(memberId)
                || !comment.status().canTransitionTo(next)) {
            throw new BusinessException(CommunityErrorCode.COMMENT_NOT_FOUND);
        }
    }
}
