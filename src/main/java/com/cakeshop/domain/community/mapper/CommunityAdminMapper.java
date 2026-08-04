package com.cakeshop.domain.community.mapper;

import java.util.List;

import com.cakeshop.domain.community.dto.view.AdminPostDetailView;
import com.cakeshop.domain.community.dto.view.AdminPostListView;
import com.cakeshop.domain.community.dto.view.AdminPostSort;
import com.cakeshop.domain.community.dto.view.ReportView;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.entity.ReportStatus;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 관리자 경로(/admin/community)의 조회와 조치.
 *
 * CommunityMapper와 갈라 둔 것은 CommunityService와 CommunityAdminService를 갈라 둔 것과
 * 같은 이유다 — <b>보는 규칙이 정반대</b>다. 고객 쪽은 "노출 중인 글만"이 기본이고
 * (publishedPostConditions), 관리자 쪽은 모든 상태를 보는 것이 기본이다
 * (docs/community/DOMAIN.md 4.3, adminPostConditions). 두 조각이 한 파일에 있으면 새 쿼리에서
 * 어느 쪽을 include해야 하는지가 흐려진다.
 *
 * <b>lockPost는 여기 없다.</b> 차단·해제·기각도 게시글 행을 먼저 잠그지만(H17), 그 잠금은
 * 조회수·좋아요와 <b>같은 행을 같은 순서로</b> 잡아야 하는 규칙이라 근거를 한곳에 둔다.
 * 관리자 조치도 CommunityMapper.lockPost를 그대로 쓴다 — 잠금 문장이 둘이 되는 순간
 * "어느 쪽이 먼저인가"를 따질 자리가 생긴다.
 *
 * <b>여기에 게시글·댓글 삭제는 없다.</b> 없는 것은 잊은 것이 아니라 규칙이다(6.7).
 */
@Mapper
public interface CommunityAdminMapper {

    /**
     * 관리자 목록. status가 null이면 모든 상태를 돌려준다.
     *
     * 고객 목록과 달리 상태로 거르지 않는 것이 기본이다. 관리자는 삭제·차단된 글까지
     * 본다(DOMAIN.md 4.3).
     */
    List<AdminPostListView> findPostsForAdmin(
            @Param("status") PostStatus status,
            @Param("sort") AdminPostSort sort,
            @Param("size") int size,
            @Param("offset") int offset
    );

    /** 관리자 목록의 전체 개수. 필터 조건은 findPostsForAdmin과 같은 조각을 공유한다. */
    long countPostsForAdmin(
            @Param("status") PostStatus status
    );

    /**
     * 관리자 상세. 상태로 거르지 않으며 차단 기록까지 함께 읽는다.
     * 없으면 null이다.
     */
    AdminPostDetailView findPostByIdForAdmin(
            @Param("postId") long postId
    );

    /**
     * 관리자 상세에 실을 신고 내역. 최신순이며 상태로 거르지 않는다 —
     * 처리된 신고도 "무엇을 보고 조치했는지"의 기록이라 남겨서 보여 준다.
     */
    List<ReportView> findReportsByPost(
            @Param("postId") long postId
    );

    /** 한 게시글의 미처리 신고 수. 조치 여부를 판단하고 하네스가 확인하는 데 쓴다. */
    long countPendingReports(
            @Param("postId") long postId
    );

    /**
     * 노출 중인 게시글을 차단한다. 대상이 없거나 PUBLISHED가 아니면 0행이다.
     *
     * status 조건이 전이 규칙을 SQL 쪽에서도 지킨다(DOMAIN.md 4.2) — 이미 차단된 글을
     * 다시 차단하면 차단 시각과 사유가 덮여 원래 조치 기록이 사라지고, 작성자가 지운 글은
     * 되살아난다.
     */
    int blockPost(
            @Param("postId") long postId,
            @Param("reason") String reason,
            @Param("adminId") long adminId
    );

    /**
     * 차단된 게시글을 다시 노출한다. 대상이 없거나 BLOCKED가 아니면 0행이다.
     *
     * blocked_at·blocked_reason·blocked_by를 NULL로 되돌리지 않는다(DOMAIN.md 4.2).
     * "과거에 차단된 적이 있다"는 관리자에게 유용한 이력이고, 노출은 status가 정한다.
     */
    int unblockPost(
            @Param("postId") long postId
    );

    /**
     * 이 게시글의 미처리 신고를 한꺼번에 닫는다. 이미 처리된 신고는 건드리지 않는다.
     *
     * 조치의 단위가 게시글이라 신고도 게시글 단위로 닫는다(DOMAIN.md 6.6). 차단이면
     * RESOLVED, 기각이면 REJECTED가 들어온다. 값은 enum이라 문자열이 새로 만들어지지 않는다.
     */
    int closePendingReports(
            @Param("postId") long postId,
            @Param("status") ReportStatus status
    );
}
