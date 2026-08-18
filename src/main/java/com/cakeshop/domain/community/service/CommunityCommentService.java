package com.cakeshop.domain.community.service;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;

import com.cakeshop.domain.community.dto.form.CommentForm;
import com.cakeshop.domain.community.dto.query.CommentCountRow;
import com.cakeshop.domain.community.dto.query.CommentRow;
import com.cakeshop.domain.community.dto.view.CommentSectionView;
import com.cakeshop.domain.community.dto.view.CommentView;
import com.cakeshop.domain.community.entity.Comment;
import com.cakeshop.domain.community.entity.CommentStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityMapper;
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

    private final CommunityMapper communityMapper;
    private final CommunityMemberViewLoader communityMemberViewLoader;

    /*
     * 댓글을 달 수 있는 글인지는 게시글 쪽이 판단한다. 여기서 다시 읽으면 노출 규칙이
     * 두 벌이 되고, 그 두 벌은 관리자가 글을 차단하는 순간 갈린다.
     */
    private final CommunityPostService communityPostService;

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

    @Transactional
    public void addComment(long postId, CommentForm form, long authorId) {
        communityPostService.getCommentablePost(postId, authorId);

        communityMapper.insertComment(
                Comment.create(postId, authorId, form.getContent()));
    }

    @Transactional
    public void deleteComment(long postId, long commentId, long memberId) {
        communityPostService.getCommentablePost(postId, memberId);
        requireOwnCommentTransition(
                postId, commentId, memberId, CommentStatus.DELETED);

        requireCommentApplied(
                communityMapper.deleteComment(commentId, postId, memberId),
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
        CommentRow comment = communityMapper.findCommentById(commentId);

        if (comment == null
                || !comment.postId().equals(postId)
                || !comment.memberId().equals(memberId)
                || !comment.status().canTransitionTo(next)) {
            throw new BusinessException(CommunityErrorCode.COMMENT_NOT_FOUND);
        }
    }
}
