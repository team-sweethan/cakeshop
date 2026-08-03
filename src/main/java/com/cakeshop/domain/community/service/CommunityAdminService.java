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
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 경로(/admin/community)의 조회와 조치를 맡는다.
 *
 * CommunityService와 갈라 둔 것은 <b>보는 규칙이 정반대</b>이기 때문이다. 고객 경로는
 * "노출 중인 글만"이 기본이고 그 판단이 거의 모든 메서드에 붙어 있는데, 관리자 경로는
 * 모든 상태를 보는 것이 기본이다(docs/community/DOMAIN.md 4.3). 한 클래스에 두면 두 규칙이
 * 섞여, 노출 판단을 빠뜨린 메서드가 고객 경로에서 호출되는 날이 온다.
 *
 * <b>관리자가 할 수 있는 일은 차단·해제·신고 기각뿐이다</b>(6.7). 게시글 삭제도 댓글
 * 삭제도 여기 없다 — 없는 것은 잊은 것이 아니라 규칙이다. 게시글 삭제는 작성자만 할 수
 * 있고(4.2의 BLOCKED -> DELETED 금지), 댓글 삭제는 그 댓글의 작성자만 할 수 있다(6.4).
 */
@Service
public class CommunityAdminService {

    private final CommunityMapper communityMapper;

    public CommunityAdminService(CommunityMapper communityMapper) {
        this.communityMapper = communityMapper;
    }

    /**
     * 관리자 목록. status가 null이면 모든 상태를 돌려준다.
     *
     * 정렬은 AdminPostSort로 좁혀서 넘긴다. 주소 문자열이 SQL까지 내려가면 안 된다.
     */
    @Transactional(readOnly = true)
    public PageResult<AdminPostListView> getPosts(
            PostStatus status, AdminPostSort sort, PageRequest pageRequest) {

        List<AdminPostListView> posts = communityMapper.findPostsForAdmin(
                status,
                sort,
                pageRequest.getSize(),
                pageRequest.getOffset()
        );

        long totalElements = communityMapper.countPostsForAdmin(status);

        return new PageResult<>(posts, pageRequest, totalElements);
    }

    /**
     * 관리자 상세. 상태와 무관하게 보여 준다 — 차단된 글의 본문을 볼 수 있는 유일한
     * 경로가 여기다(DOMAIN.md 4.3).
     *
     * 없는 글에만 404다. 고객 경로처럼 삭제·차단을 404로 숨기지 않는다. 숨길 상대가
     * 아니고, 숨기면 조치 이력을 확인할 방법이 사라진다.
     */
    @Transactional(readOnly = true)
    public AdminPostDetailView getPostDetail(long postId) {
        AdminPostDetailView post = communityMapper.findPostByIdForAdmin(postId);

        if (post == null) {
            throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
        }

        return post;
    }

    /** 관리자 상세에 실을 신고 내역. 처리된 신고도 함께 나온다. */
    @Transactional(readOnly = true)
    public List<ReportView> getReports(long postId) {
        return communityMapper.findReportsByPost(postId);
    }

    /**
     * 게시글을 차단하고, 그 글의 미처리 신고를 처리 완료로 닫는다.
     * adminId는 인증 정보에서 얻은 값이어야 한다.
     *
     * 둘이 한 트랜잭션인 것이 중요하다. 차단만 되고 신고가 남으면 관리자 목록에서 조치가
     * 안 된 것처럼 보이고, 신고만 닫히면 글은 그대로 노출된 채 아무도 다시 신고할 수 없다
     * (신고는 회원당 한 건뿐이다, DOMAIN.md 6.6).
     *
     * 게시글 행을 먼저 잠근다. 좋아요와 같은 이유다 — posts 행에 쓰는 경로이므로 잠금
     * 순서가 다른 경로와 어긋나면 교착이 난다(H13·H15).
     */
    @Transactional
    public void blockPost(long postId, String reason, long adminId) {
        requireTransition(postId, PostStatus.BLOCKED);

        requireApplied(communityMapper.blockPost(postId, reason, adminId));

        // 이 글에 대한 판단이 끝났으므로 대기 중이던 신고를 함께 닫는다. 남은 신고가
        // 없어도 정상이다 — 관리자가 신고 없이 직접 발견해 차단할 수도 있다.
        communityMapper.closePendingReports(postId, ReportStatus.RESOLVED);
    }

    /**
     * 차단을 해제한다. blocked_at·blocked_reason·blocked_by는 그대로 둔다(DOMAIN.md 4.2).
     *
     * <b>신고 상태도 되돌리지 않는다.</b> RESOLVED는 "그때 조치했다"는 기록이지 지금 차단
     * 중이라는 뜻이 아니다. 되돌리면 이미 처리한 신고가 관리자 목록에 다시 나타나고,
     * 같은 글을 몇 번 조치했는지 알 수 없게 된다.
     */
    @Transactional
    public void unblockPost(long postId) {
        requireTransition(postId, PostStatus.PUBLISHED);

        requireApplied(communityMapper.unblockPost(postId));
    }

    /**
     * 신고를 기각한다. 게시글은 그대로 두고 미처리 신고만 닫는다(DOMAIN.md 6.6).
     *
     * 기각이 필요한 이유는 "차단하지 않기로 했다"도 조치이기 때문이다. 닫을 길이 없으면
     * 문제없는 글이 신고 목록 맨 위에 영원히 남아, 진짜 처리할 글을 가린다.
     *
     * 닫을 신고가 하나도 없으면 성공으로 넘기지 않는다. 화면에는 "기각했습니다"라고
     * 나오는데 아무 일도 일어나지 않은 상태다.
     */
    @Transactional
    public void rejectReports(long postId) {
        getPostDetail(postId);

        if (communityMapper.closePendingReports(postId, ReportStatus.REJECTED) == 0) {
            throw new BusinessException(CommunityErrorCode.INVALID_POST_TRANSITION);
        }
    }

    /**
     * 게시글 행을 잠그고, 지금 상태에서 목표 상태로 갈 수 있는지 확인한다.
     *
     * 판단은 PostStatus.canTransitionTo가 한다(DOMAIN.md 4.2). 여기서 조건을 새로 적으면
     * 전이 규칙이 두 벌이 되고, enum 쪽만 고치는 날 이 경로만 옛 규칙으로 남는다.
     *
     * 잠금 조회가 posts만 읽는 것은 좋아요와 같은 이유다 — FOR UPDATE는 조인한 테이블의
     * 행까지 잠그므로, 카테고리를 조인하면 차단 한 번에 같은 분류의 글이 전부 줄을 선다.
     */
    private void requireTransition(long postId, PostStatus next) {
        PostLockView post = communityMapper.lockPost(postId);

        if (post == null) {
            throw new BusinessException(CommunityErrorCode.POST_NOT_FOUND);
        }

        if (!post.status().canTransitionTo(next)) {
            throw new BusinessException(CommunityErrorCode.INVALID_POST_TRANSITION);
        }
    }

    /**
     * 조건부 UPDATE가 실제로 한 행을 바꿨는지 확인한다. 고객 경로의 requireApplied와 같은
     * 이유다 — 갱신 행 수를 버리면 SQL의 status 조건이 걸러 낸 순간이 성공으로 보인다.
     *
     * 여기서는 상태를 이미 잠근 채로 확인했으므로 0행이 나올 수 없다. 그래도 성공으로
     * 넘기지 않는 것은, 나올 수 없는 일이 났다면 잠금이나 조건이 깨진 것이기 때문이다.
     */
    private void requireApplied(int affectedRows) {
        if (affectedRows == 0) {
            throw new BusinessException(CommunityErrorCode.INVALID_POST_TRANSITION);
        }
    }
}
