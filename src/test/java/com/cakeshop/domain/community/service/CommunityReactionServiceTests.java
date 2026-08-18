package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import com.cakeshop.domain.community.dto.form.ReportForm;
import com.cakeshop.domain.community.dto.query.PostDetailRow;
import com.cakeshop.domain.community.dto.query.PostLockRow;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityMapper;
import com.cakeshop.domain.community.mapper.CommunityPopularPostMapper;
import com.cakeshop.domain.member.service.MemberCommunityQueryService;
import com.cakeshop.global.error.BusinessException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;

/** 커뮤니티 좋아요·신고 Service의 권한과 상태 계약을 확인한다. */
class CommunityReactionServiceTests {

    private static final long POST_ID = 42L;
    private static final long AUTHOR_ID = 7L;
    private static final long OTHER_MEMBER_ID = 99L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 3, 1, 10, 0);

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private CommunityMapper communityMapper;
    private CommunityReactionService communityReactionService;

    @BeforeEach
    void setUp() {
        communityMapper = mock(CommunityMapper.class);

        MemberCommunityQueryService memberCommunityQueryService =
                mock(MemberCommunityQueryService.class);
        when(memberCommunityQueryService.getMembersByIds(anyList())).thenReturn(List.of());

        CommunityMemberViewLoader memberViewLoader =
                new CommunityMemberViewLoader(memberCommunityQueryService);

        communityReactionService = new CommunityReactionService(
                communityMapper,
                new CommunityPostAccessPolicy(),
                postService(memberViewLoader));
    }

    /*
     * 게시글 Service를 목으로 갈지 않는다. 신고에서 볼 수 없는 글을 거르는 것은 그쪽이고,
     * 목으로 두면 아래 비노출 글 표가 아무것도 검증하지 않게 된다.
     */
    private CommunityPostService postService(CommunityMemberViewLoader memberViewLoader) {
        return new CommunityPostService(
                communityMapper,
                memberViewLoader,
                new CommunityPostAccessPolicy(),
                new PopularPostReader(
                        mock(CommunityPopularPostMapper.class),
                        Clock.system(SEOUL)),
                mock(CommunityPostImageService.class));
    }

    /** 좋아요는 잠금, 저장, 재계산 순서로 처리한다. */
    @Test
    void addLike_publishedPost_locksThePostBeforeTouchingLikes() {
        givenLockedPost(PostStatus.PUBLISHED);

        communityReactionService.addLike(POST_ID, OTHER_MEMBER_ID);

        InOrder order = inOrder(communityMapper);
        order.verify(communityMapper).lockPost(POST_ID);
        order.verify(communityMapper).insertLike(POST_ID, OTHER_MEMBER_ID);
        order.verify(communityMapper).recalculateLikeCount(POST_ID);
    }

    /** 좋아요 취소도 잠금부터 처리한다. */
    @Test
    void removeLike_publishedPost_locksThePostBeforeTouchingLikes() {
        givenLockedPost(PostStatus.PUBLISHED);

        communityReactionService.removeLike(POST_ID, OTHER_MEMBER_ID);

        InOrder order = inOrder(communityMapper);
        order.verify(communityMapper).lockPost(POST_ID);
        order.verify(communityMapper).deleteLike(POST_ID, OTHER_MEMBER_ID);
        order.verify(communityMapper).recalculateLikeCount(POST_ID);
    }

    /** 삭제·미존재 글과 다른 작성자의 차단 글은 숨기고, 작성자에게만 차단을 알린다. */
    @ParameterizedTest(name = "{0} 글에 작성자={1}이 좋아요하면 {2}")
    @CsvSource(value = {
            "DELETED, false, POST_NOT_FOUND",
            "NONE,    false, POST_NOT_FOUND",
            "BLOCKED, false, POST_NOT_FOUND",
            "BLOCKED, true,  BLOCKED_POST"
    }, nullValues = "NONE")
    void addLike_notLikeablePost_isRejected(
            PostStatus status, boolean asAuthor, CommunityErrorCode expected) {
        givenLockedPost(status);

        assertThatThrownBy(() -> communityReactionService.addLike(POST_ID, memberIdOf(asAuthor)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);

        verify(communityMapper, never()).insertLike(anyLong(), anyLong());
        verify(communityMapper, never()).recalculateLikeCount(anyLong());
    }

    @Test
    void removeLike_deletedPost_isRejectedAndLeavesLikesUntouched() {
        givenLockedPost(PostStatus.DELETED);

        assertThatThrownBy(() -> communityReactionService.removeLike(POST_ID, OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);

        verify(communityMapper, never()).deleteLike(anyLong(), anyLong());
        verify(communityMapper, never()).recalculateLikeCount(anyLong());
    }

    /** 신고 사유를 정리해 저장한다. */
    @Test
    void reportPost_publishedPostOfOther_insertsReport() {
        givenPost(PostStatus.PUBLISHED);

        communityReactionService.reportPost(POST_ID, reportFormOf("광고입니다"), OTHER_MEMBER_ID);

        verify(communityMapper).insertReport(POST_ID, OTHER_MEMBER_ID, "광고입니다");
    }

    /** 중복 신고에는 명시적인 오류를 반환한다. */
    @Test
    void reportPost_alreadyReported_isRejected() {
        givenPost(PostStatus.PUBLISHED);
        when(communityMapper.existsReport(POST_ID, OTHER_MEMBER_ID)).thenReturn(true);

        assertThatThrownBy(() -> communityReactionService.reportPost(
                POST_ID, reportFormOf("또 신고"), OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.ALREADY_REPORTED);

        verify(communityMapper, never()).insertReport(anyLong(), anyLong(), any());
    }

    /** 중복 신고 경합도 같은 오류로 변환한다. */
    @Test
    void reportPost_duplicateKeyRace_isReportedAsAlreadyReported() {
        givenPost(PostStatus.PUBLISHED);
        when(communityMapper.insertReport(POST_ID, OTHER_MEMBER_ID, "광고입니다"))
                .thenThrow(new DuplicateKeyException("uk_post_reports_post_reporter"));

        assertThatThrownBy(() -> communityReactionService.reportPost(
                POST_ID, reportFormOf("광고입니다"), OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.ALREADY_REPORTED);
    }

    /** 자신의 글은 신고할 수 없다. */
    @Test
    void reportPost_ownPost_isRejected() {
        givenPost(PostStatus.PUBLISHED);

        assertThatThrownBy(() -> communityReactionService.reportPost(
                POST_ID, reportFormOf("내 글"), AUTHOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.OWN_POST_REPORT);

        verify(communityMapper, never()).insertReport(anyLong(), anyLong(), any());
    }

    /** 볼 수 없는 글은 신고할 수 없다. */
    @ParameterizedTest(name = "{0} 글을 신고하면 POST_NOT_FOUND")
    @CsvSource(value = {
            "DELETED",
            "BLOCKED",
            "NONE"
    }, nullValues = "NONE")
    void reportPost_invisiblePost_isRejectedAsNotFound(PostStatus status) {
        givenPost(status);

        assertThatThrownBy(() -> communityReactionService.reportPost(
                POST_ID, reportFormOf("사유"), OTHER_MEMBER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.POST_NOT_FOUND);

        verify(communityMapper, never()).insertReport(anyLong(), anyLong(), any());
    }

    private ReportForm reportFormOf(String reason) {
        ReportForm form = new ReportForm();
        form.setReason(reason);

        return form;
    }

    /** status가 null이면 없는 글로 둔다. */
    private void givenLockedPost(PostStatus status) {
        when(communityMapper.lockPost(POST_ID))
                .thenReturn(status == null ? null : new PostLockRow(AUTHOR_ID, status));
    }

    /** status가 null이면 없는 글로 둔다. */
    private void givenPost(PostStatus status) {
        when(communityMapper.findPostById(POST_ID))
                .thenReturn(status == null ? null : postRowOf(status));
    }

    private PostDetailRow postRowOf(PostStatus status) {
        return new PostDetailRow(
                POST_ID,
                AUTHOR_ID,
                1L,
                "질문",
                "제목",
                "본문",
                status,
                status == PostStatus.BLOCKED ? "광고성 게시물" : null,
                10L,
                2L,
                CREATED_AT,
                CREATED_AT
        );
    }

    /** 상태 표의 "작성자" 열을 실제 회원 ID로 바꾼다. */
    private static long memberIdOf(boolean asAuthor) {
        return asAuthor ? AUTHOR_ID : OTHER_MEMBER_ID;
    }
}
