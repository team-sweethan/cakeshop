package com.cakeshop.domain.community.service;

import lombok.RequiredArgsConstructor;

import com.cakeshop.domain.community.dto.form.ReportForm;
import com.cakeshop.domain.community.dto.query.PostLockRow;
import com.cakeshop.domain.community.dto.view.PostDetailView;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.global.error.BusinessException;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-18
 * 기능 : 커뮤니티 비즈니스 로직
 * 설명 : CommunityReactionService 기능의 권한, 상태, 트랜잭션을 관리한다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
public class CommunityReactionService {

    private final CommunityMapper communityMapper;
    private final CommunityPostAccessPolicy communityPostAccessPolicy;

    /*
     * 신고는 글을 읽어야 성립하고, 노출 판단은 게시글 쪽이 갖는다. 여기서 다시 읽으면
     * 규칙이 두 벌이 된다. 좋아요는 잠금이 필요해 이 경로를 쓰지 않는다(아래 참조).
     */
    private final CommunityPostService communityPostService;

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
        PostDetailView post = communityPostService.getVisiblePost(postId, memberId);

        if (communityPostAccessPolicy.isAuthor(post.memberId(), memberId)) {
            throw new BusinessException(CommunityErrorCode.OWN_POST_REPORT);
        }

        if (communityMapper.existsReport(postId, memberId)) {
            throw new BusinessException(CommunityErrorCode.ALREADY_REPORTED);
        }

        return post;
    }

    /*
     * 좋아요만 게시글 Service를 거치지 않고 `lockPost`로 직접 읽는다. 뒤따르는 재계산이
     * 같은 `posts` 행에 배타 잠금을 걸어야 하고, 그 잠금을 여기서 먼저 잡지 않으면
     * H13과 같은 모양의 교착이 된다. 읽는 방법이 다를 뿐 판단하는 규칙은 신고와 같다.
     */
    private void requireLikeablePost(long postId, long memberId) {
        PostLockRow post = communityMapper.lockPost(postId);

        if (post == null) {
            throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
        }

        communityPostAccessPolicy.requireVisible(post.status(), post.memberId(), memberId);
        communityPostAccessPolicy.requirePublished(post.status());
    }
}
