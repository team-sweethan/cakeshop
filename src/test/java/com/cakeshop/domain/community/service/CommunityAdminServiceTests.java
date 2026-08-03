package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cakeshop.domain.community.dto.view.PostLockView;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.entity.ReportStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.global.error.BusinessException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * 관리자 조치의 규칙을 고정한다(docs/community/DOMAIN.md 4.2, 6.6, 6.7).
 *
 * <p>여기서 잡는 것들은 화면으로는 정상으로 보인다. 이미 차단된 글을 다시 차단해도
 * 화면은 "차단했습니다"라고 말하고, 신고를 안 닫아도 게시글은 멀쩡히 가려진다.
 */
class CommunityAdminServiceTests {

    private static final long POST_ID = 42L;
    private static final long AUTHOR_ID = 7L;
    private static final long ADMIN_ID = 1L;

    private CommunityMapper communityMapper;
    private CommunityAdminService communityAdminService;

    @BeforeEach
    void setUp() {
        communityMapper = mock(CommunityMapper.class);
        communityAdminService = new CommunityAdminService(communityMapper);
    }

    /**
     * 차단이 <b>잠그고 → 바꾸고 → 신고를 닫는</b> 순서를 지키는지 확인한다.
     *
     * <p>잠금이 먼저인 것은 좋아요·조회수와 같은 이유다(H13·H15). posts 행에 쓰는 경로가
     * 늘 때마다 잠금 순서를 맞춰야 하고, 어긋나면 동시 요청에서 교착이 난다.
     */
    @Test
    void blockPost_publishedPost_locksThenBlocksThenClosesReports() {
        givenLockedPost(PostStatus.PUBLISHED);
        when(communityMapper.blockPost(POST_ID, "광고성 게시물", ADMIN_ID)).thenReturn(1);

        communityAdminService.blockPost(POST_ID, "광고성 게시물", ADMIN_ID);

        InOrder order = inOrder(communityMapper);
        order.verify(communityMapper).lockPost(POST_ID);
        order.verify(communityMapper).blockPost(POST_ID, "광고성 게시물", ADMIN_ID);
        order.verify(communityMapper).closePendingReports(POST_ID, ReportStatus.RESOLVED);
    }

    /**
     * 이미 차단된 글은 다시 차단할 수 없다(DOMAIN.md 4.2의 전이 규칙).
     *
     * <p>막지 않으면 원래 조치의 시각과 사유가 덮여 사라진다. 화면에는 성공으로 보이고,
     * 지워진 것은 "언제 왜 처음 막았는지"라 나중에 확인할 방법이 없다.
     */
    @Test
    void blockPost_alreadyBlocked_isRejected() {
        givenLockedPost(PostStatus.BLOCKED);

        assertThatThrownBy(() -> communityAdminService.blockPost(POST_ID, "사유", ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.INVALID_POST_TRANSITION);

        verify(communityMapper, never()).blockPost(anyLong(), any(), anyLong());
        verify(communityMapper, never()).closePendingReports(anyLong(), any());
    }

    /** 작성자가 지운 글은 종착 상태다. 차단하면 지운 글이 되살아난다(DOMAIN.md 4.2). */
    @Test
    void blockPost_deletedPost_isRejected() {
        givenLockedPost(PostStatus.DELETED);

        assertThatThrownBy(() -> communityAdminService.blockPost(POST_ID, "사유", ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.INVALID_POST_TRANSITION);
    }

    @Test
    void blockPost_missingPost_isRejectedAsNotFound() {
        when(communityMapper.lockPost(POST_ID)).thenReturn(null);

        assertThatThrownBy(() -> communityAdminService.blockPost(POST_ID, "사유", ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);
    }

    /**
     * 차단이 실제로 한 행을 바꾸지 못했으면 신고까지 닫지 않는다.
     *
     * <p>갱신 행 수를 버리면 글은 그대로 노출된 채 신고만 닫힌다. 그러면 아무도 그 글을
     * 다시 신고할 수 없고(회원당 한 건뿐이다), 관리자 목록에서는 처리된 것으로 보인다.
     */
    @Test
    void blockPost_updateAffectedNoRow_doesNotCloseReports() {
        givenLockedPost(PostStatus.PUBLISHED);
        when(communityMapper.blockPost(POST_ID, "사유", ADMIN_ID)).thenReturn(0);

        assertThatThrownBy(() -> communityAdminService.blockPost(POST_ID, "사유", ADMIN_ID))
                .isInstanceOf(BusinessException.class);

        verify(communityMapper, never()).closePendingReports(anyLong(), any());
    }

    /**
     * 차단 해제는 신고 상태를 되돌리지 않는다(DOMAIN.md 6.6).
     *
     * <p>RESOLVED는 "그때 조치했다"는 기록이지 지금 차단 중이라는 뜻이 아니다. 되돌리면
     * 이미 처리한 신고가 관리자 목록에 다시 나타난다.
     */
    @Test
    void unblockPost_blockedPost_leavesReportsClosed() {
        givenLockedPost(PostStatus.BLOCKED);
        when(communityMapper.unblockPost(POST_ID)).thenReturn(1);

        communityAdminService.unblockPost(POST_ID);

        verify(communityMapper).unblockPost(POST_ID);
        verify(communityMapper, never()).closePendingReports(anyLong(), any());
    }

    /** 차단된 적 없는 글에는 해제할 것이 없다. 성공으로 넘기면 화면만 거짓말을 한다. */
    @Test
    void unblockPost_publishedPost_isRejected() {
        givenLockedPost(PostStatus.PUBLISHED);

        assertThatThrownBy(() -> communityAdminService.unblockPost(POST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.INVALID_POST_TRANSITION);

        verify(communityMapper, never()).unblockPost(anyLong());
    }

    /** 기각은 게시글을 건드리지 않는다. "차단하지 않기로 했다"도 조치다(DOMAIN.md 6.6). */
    @Test
    void rejectReports_pendingReports_closesThemWithoutTouchingPost() {
        givenAdminPost();
        when(communityMapper.closePendingReports(POST_ID, ReportStatus.REJECTED)).thenReturn(2);

        communityAdminService.rejectReports(POST_ID);

        verify(communityMapper).closePendingReports(POST_ID, ReportStatus.REJECTED);
        verify(communityMapper, never()).blockPost(anyLong(), any(), anyLong());
        verify(communityMapper, never()).unblockPost(anyLong());
    }

    /**
     * 닫을 신고가 없으면 성공으로 넘기지 않는다.
     *
     * <p>화면에는 "기각했습니다"가 나오는데 아무 일도 일어나지 않은 상태다. 두 번 눌렀거나
     * 다른 관리자가 먼저 처리한 경우인데, 성공으로 답하면 그 사실이 사라진다.
     */
    @Test
    void rejectReports_withoutPendingReports_isRejected() {
        givenAdminPost();
        when(communityMapper.closePendingReports(POST_ID, ReportStatus.REJECTED)).thenReturn(0);

        assertThatThrownBy(() -> communityAdminService.rejectReports(POST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.INVALID_POST_TRANSITION);
    }

    /**
     * 관리자 상세는 상태로 거르지 않는다(DOMAIN.md 4.3). 없는 글에만 404다 —
     * 고객 경로처럼 삭제·차단을 숨기면 조치 이력을 확인할 방법이 사라진다.
     */
    @Test
    void getPostDetail_missingPost_isRejectedAsNotFound() {
        when(communityMapper.findPostByIdForAdmin(POST_ID)).thenReturn(null);

        assertThatThrownBy(() -> communityAdminService.getPostDetail(POST_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);
    }

    private void givenLockedPost(PostStatus status) {
        when(communityMapper.lockPost(POST_ID))
                .thenReturn(new PostLockView(AUTHOR_ID, status));
    }

    private void givenAdminPost() {
        when(communityMapper.findPostByIdForAdmin(POST_ID))
                .thenReturn(new com.cakeshop.domain.community.dto.view.AdminPostDetailView(
                        POST_ID, AUTHOR_ID, "질문", "제목", "본문", "글쓴이", false,
                        PostStatus.PUBLISHED, null, null, null, 0, 0, null, null));
    }
}
