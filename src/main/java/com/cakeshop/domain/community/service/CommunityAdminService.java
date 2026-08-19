package com.cakeshop.domain.community.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;

import com.cakeshop.domain.community.dto.query.AdminPostDetailRow;
import com.cakeshop.domain.community.dto.view.AdminPostDetailView;
import com.cakeshop.domain.community.dto.query.AdminPostListRow;
import com.cakeshop.domain.community.dto.view.AdminPostListView;
import com.cakeshop.domain.community.dto.view.AdminPostSort;
import com.cakeshop.domain.community.dto.query.PostLockRow;
import com.cakeshop.domain.community.dto.query.ReportRow;
import com.cakeshop.domain.community.dto.view.ReportView;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.entity.ReportStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityAdminMapper;
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
 * 설명 : CommunityAdminService 기능의 권한, 상태, 트랜잭션을 관리한다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
public class CommunityAdminService {

    private final CommunityAdminMapper communityAdminMapper;

    private final CommunityMapper communityMapper;

    private final CommunityMemberViewLoader communityMemberViewLoader;

    @Transactional(readOnly = true)
    public PageResult<AdminPostListView> getPosts(
            PostStatus status, AdminPostSort sort, PageRequest pageRequest) {

        List<AdminPostListRow> rows = communityAdminMapper.findPostsForAdmin(
                status,
                sort,
                pageRequest.getSize(),
                pageRequest.getOffset()
        );

        long totalElements = communityAdminMapper.countPostsForAdmin(status);

        Map<Long, MemberCommunityView> authors =
                communityMemberViewLoader.findByIds(rows.stream().map(AdminPostListRow::memberId));

        List<AdminPostListView> posts = rows.stream()
                .map(row -> AdminPostListView.of(row, authors.get(row.memberId())))
                .toList();

        return new PageResult<>(posts, pageRequest, totalElements);
    }

    @Transactional(readOnly = true)
    public AdminPostDetailView getPostDetail(long postId) {
        AdminPostDetailRow post = communityAdminMapper.findPostByIdForAdmin(postId);

        if (post == null) {
            throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
        }

        // 작성자와 차단 관리자를 한 번에 받는다. 차단 기록이 없으면 blockedBy 가 null 이라
        // 조회 대상에서 빠지고, 화면의 차단 관리자 자리도 그대로 빈다(LEFT JOIN 이던 때와 같다).
        Map<Long, MemberCommunityView> members =
                communityMemberViewLoader.findByIds(Stream.of(post.memberId(), post.blockedBy()));

        return AdminPostDetailView.of(
                post,
                members.get(post.memberId()),
                post.blockedBy() == null ? null : members.get(post.blockedBy())
        );
    }

    @Transactional(readOnly = true)
    public List<ReportView> getReports(long postId) {
        List<ReportRow> rows = communityAdminMapper.findReportsByPost(postId);

        Map<Long, MemberCommunityView> reporters =
                communityMemberViewLoader.findByIds(rows.stream().map(ReportRow::reporterId));

        return rows.stream()
                .map(row -> ReportView.of(row, reporters.get(row.reporterId())))
                .toList();
    }

    @Transactional
    public void blockPost(long postId, String reason, long adminId) {
        requireTransition(postId, PostStatus.BLOCKED);

        requireApplied(communityAdminMapper.blockPost(postId, reason, adminId));

        closePendingReports(postId, ReportStatus.RESOLVED);
    }

    @Transactional
    public void unblockPost(long postId) {
        requireTransition(postId, PostStatus.PUBLISHED);

        requireApplied(communityAdminMapper.unblockPost(postId));
    }

    @Transactional
    public void rejectReports(long postId) {
        PostLockRow post = communityMapper.lockPost(postId);

        if (post == null) {
            throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
        }

        if (post.status() == PostStatus.DELETED) {
            throw new BusinessException(CommunityErrorCode.INVALID_POST_TRANSITION);
        }

        if (closePendingReports(postId, ReportStatus.REJECTED) == 0) {
            throw new BusinessException(CommunityErrorCode.INVALID_POST_TRANSITION);
        }
    }

    private int closePendingReports(long postId, ReportStatus next) {
        if (!ReportStatus.PENDING.canTransitionTo(next)) {
            throw new BusinessException(CommunityErrorCode.INVALID_POST_TRANSITION);
        }

        return communityAdminMapper.closePendingReports(postId, next);
    }

    private void requireTransition(long postId, PostStatus next) {
        PostLockRow post = communityMapper.lockPost(postId);

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
