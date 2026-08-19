package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import com.cakeshop.domain.community.dto.query.NoticeDetailRow;
import com.cakeshop.domain.community.dto.query.NoticeListRow;
import com.cakeshop.domain.community.dto.view.NoticeView;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityNoticeMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.error.BusinessException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 고객 공지 목록·상세 조회와 노출 판단을 검증한다. */
class CommunityNoticeServiceTests {

    private static final long NOTICE_ID = 42L;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 1, 10, 0);
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 2, 1, 9, 0);

    private CommunityNoticeMapper communityNoticeMapper;

    private CommunityNoticeService communityNoticeService;

    @BeforeEach
    void setUp() {
        communityNoticeMapper = mock(CommunityNoticeMapper.class);
        communityNoticeService = new CommunityNoticeService(
                communityNoticeMapper,
                Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));
    }

    @Test
    void getNotices_usesRequestedPageWindow() {
        when(communityNoticeMapper.selectVisibleNotices(any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(communityNoticeMapper.countVisibleNotices(NOW)).thenReturn(41L);

        assertThat(communityNoticeService
                .getNotices(new PageRequest(2, PageRequest.DEFAULT_SIZE))
                .getTotalElements())
                .isEqualTo(41L);

        verify(communityNoticeMapper).selectVisibleNotices(NOW, 20, 20);
    }

    @Test
    void getNotices_withoutStartDate_showsRegisteredDate() {
        when(communityNoticeMapper.selectVisibleNotices(any(), anyInt(), anyInt()))
                .thenReturn(List.of(
                        row(1L, "즉시 노출", null),
                        row(2L, "예약 노출", NOW.minusDays(1))));

        assertThat(communityNoticeService
                .getNotices(new PageRequest(1, PageRequest.DEFAULT_SIZE))
                .getContent())
                .extracting(NoticeView::displayedAt)
                .containsExactly(CREATED_AT, NOW.minusDays(1));
    }

    @Test
    void getNotice_visibleNotice_isReturnedWithContent() {
        when(communityNoticeMapper.selectVisibleNoticeById(NOTICE_ID, NOW))
                .thenReturn(new NoticeDetailRow(NOTICE_ID, "제목", "본문", null, CREATED_AT));

        assertThat(communityNoticeService.getNotice(NOTICE_ID).content()).isEqualTo("본문");
    }

    @Test
    void getNotice_invisibleNotice_isNotFound() {
        when(communityNoticeMapper.selectVisibleNoticeById(anyLong(), any())).thenReturn(null);

        assertThatThrownBy(() -> communityNoticeService.getNotice(NOTICE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.NOTICE_NOT_FOUND);
    }

    private NoticeListRow row(long id, String title, LocalDateTime startsAt) {
        return new NoticeListRow(id, title, startsAt, CREATED_AT);
    }
}
