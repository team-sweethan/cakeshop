package com.cakeshop.domain.community.service;

import java.util.List;

import com.cakeshop.domain.community.dto.view.AdminPostDetailView;
import com.cakeshop.domain.community.dto.view.AdminPostListView;
import com.cakeshop.domain.community.dto.view.AdminPostSort;
import com.cakeshop.domain.community.dto.view.PostLockView;
import com.cakeshop.domain.community.dto.view.ReportView;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.entity.ReportStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityAdminMapper;
import com.cakeshop.domain.community.mapper.CommunityMapper;
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
 * 설명 : CommunityAdminService 기능의 권한, 상태, 트랜잭션을 관리한다.
 * ******************************
 */
@Service
public class CommunityAdminService {

    private final CommunityAdminMapper communityAdminMapper;

    private final CommunityMapper communityMapper;

    public CommunityAdminService(
            CommunityAdminMapper communityAdminMapper, CommunityMapper communityMapper) {

        this.communityAdminMapper = communityAdminMapper;
        this.communityMapper = communityMapper;
    }

    @Transactional(readOnly = true)
    public PageResult<AdminPostListView> getPosts(
            PostStatus status, AdminPostSort sort, PageRequest pageRequest) {

        List<AdminPostListView> posts = communityAdminMapper.findPostsForAdmin(
                status,
                sort,
                pageRequest.getSize(),
                pageRequest.getOffset()
        );

        long totalElements = communityAdminMapper.countPostsForAdmin(status);

        return new PageResult<>(posts, pageRequest, totalElements);
    }

    @Transactional(readOnly = true)
    public AdminPostDetailView getPostDetail(long postId) {
        AdminPostDetailView post = communityAdminMapper.findPostByIdForAdmin(postId);

        if (post == null) {
            throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
        }

        return post;
    }

    @Transactional(readOnly = true)
    public List<ReportView> getReports(long postId) {
        return communityAdminMapper.findReportsByPost(postId);
    }

    @Transactional
    public void blockPost(long postId, String reason, long adminId) {
        requireTransition(postId, PostStatus.BLOCKED);

        requireApplied(communityAdminMapper.blockPost(postId, reason, adminId));

        communityAdminMapper.closePendingReports(postId, ReportStatus.RESOLVED);
    }

    @Transactional
    public void unblockPost(long postId) {
        requireTransition(postId, PostStatus.PUBLISHED);

        requireApplied(communityAdminMapper.unblockPost(postId));
    }

    @Transactional
    public void rejectReports(long postId) {
        PostLockView post = communityMapper.lockPost(postId);

        if (post == null) {
            throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
        }

        if (post.status() == PostStatus.DELETED) {
            throw new BusinessException(CommunityErrorCode.INVALID_POST_TRANSITION);
        }

        if (communityAdminMapper.closePendingReports(postId, ReportStatus.REJECTED) == 0) {
            throw new BusinessException(CommunityErrorCode.INVALID_POST_TRANSITION);
        }
    }

    private void requireTransition(long postId, PostStatus next) {
        PostLockView post = communityMapper.lockPost(postId);

        if (post == null) {
            throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
        }

        if (!post.status().canTransitionTo(next)) {
            throw new BusinessException(CommunityErrorCode.INVALID_POST_TRANSITION);
        }
    }

    private void requireApplied(int affectedRows) {
        if (affectedRows == 0) {
            throw new BusinessException(CommunityErrorCode.INVALID_POST_TRANSITION);
        }
    }
}
