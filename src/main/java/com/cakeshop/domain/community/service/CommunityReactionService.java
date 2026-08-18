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
     * 규칙이 두 벌이 된다. 좋아요는 정상 경로에서 글을 읽지 않는다(아래 참조).
     */
    private final CommunityPostService communityPostService;

    /*
     * 좋아요는 조건부 원자 UPDATE 로 시작한다(specs/community-reaction.md A8).
     *
     * 카운터 증감이 노출 조건과 중복 여부를 한 문장에서 판단하면서 posts 행의 배타
     * 잠금을 먼저 잡는다 — 조회수(increaseViewCount, H13)와 같은 모양이고, 잠금 순서
     * 규율(posts 먼저)도 그대로다. 0행이면 그때만 글을 잠가 읽어 이유를 가른다:
     * 없는 글·비노출 글이면 던지고, PUBLISHED 인데 0행이면 원하는 상태가 이미
     * 이뤄져 있는 것이라 멱등 성공이다.
     */
    @Transactional
    public void addLike(long postId, long memberId) {
        if (communityMapper.increaseLikeCount(postId, memberId) == 0) {
            requireLikeablePost(postId, memberId);
            return;
        }

        communityMapper.insertLike(postId, memberId);
    }

    @Transactional
    public void removeLike(long postId, long memberId) {
        if (communityMapper.decreaseLikeCount(postId, memberId) == 0) {
            requireLikeablePost(postId, memberId);
            return;
        }

        if (communityMapper.deleteLike(postId, memberId) != 1) {
            // 카운터는 줄었는데 지울 행이 없다. EXISTS 를 통과한 뒤라 있을 수 없는
            // 상태이고, 그대로 커밋하면 카운터가 실제 행 수보다 작아진다. 전체를 되돌린다.
            throw new IllegalStateException(
                    "좋아요 행이 카운터와 어긋납니다. postId=" + postId + ", memberId=" + memberId);
        }
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
     * 좋아요의 0행 실패만 여기로 온다. UPDATE 가 이미 배타 잠금을 잡는 정상 경로에는
     * 읽기가 없고, 실패했을 때만 글을 잠가 읽어 이유를 가른다 — 게시글 Service 를
     * 거치지 않는 것은 신고와 달리 상세 조합이 필요 없어서다. 판단 규칙은 신고와 같다.
     *
     * 여기를 끝까지 통과하면 글이 PUBLISHED 라는 뜻이고, 그런데도 UPDATE 가 0행이었다면
     * 좋아요 상태가 이미 목표와 같았던 것이다 — 호출자는 멱등 성공으로 끝낸다.
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
