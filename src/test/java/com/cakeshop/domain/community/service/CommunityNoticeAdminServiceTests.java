package com.cakeshop.domain.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import com.cakeshop.domain.community.dto.command.NoticeUpdateCommand;
import com.cakeshop.domain.community.dto.form.NoticeForm;
import com.cakeshop.domain.community.dto.query.AdminNoticeDetailRow;
import com.cakeshop.domain.community.dto.query.AdminNoticeListRow;
import com.cakeshop.domain.community.dto.query.NoticeLockRow;
import com.cakeshop.domain.community.dto.view.AdminNoticeListView;
import com.cakeshop.domain.community.dto.view.NoticeDisplayStatus;
import com.cakeshop.domain.community.entity.Notice;
import com.cakeshop.domain.community.entity.NoticeStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityNoticeMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.error.BusinessException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 관리자 공지 조치 규칙과 노출 상태 판정을 검증한다. */
class CommunityNoticeAdminServiceTests {

    private static final long NOTICE_ID = 42L;
    private static final long ADMIN_ID = 1L;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 1, 10, 0);
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 2, 1, 9, 0);

    private CommunityNoticeMapper communityNoticeMapper;

    private CommunityNoticeAdminService communityNoticeAdminService;

    @BeforeEach
    void setUp() {
        communityNoticeMapper = mock(CommunityNoticeMapper.class);
        communityNoticeAdminService = new CommunityNoticeAdminService(
                communityNoticeMapper,
                Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));
    }

    @Test
    void getNotices_openPeriod_isVisible() {
        assertDisplayStatus(NoticeStatus.PUBLISHED, null, null, NoticeDisplayStatus.VISIBLE);
    }

    @Test
    void getNotices_startBoundary_includesTheExactInstantAndExcludesJustBefore() {
        assertDisplayStatus(NoticeStatus.PUBLISHED, NOW, null, NoticeDisplayStatus.VISIBLE);
        assertDisplayStatus(
                NoticeStatus.PUBLISHED, NOW.plusNanos(1_000), null, NoticeDisplayStatus.SCHEDULED);
    }

    @Test
    void getNotices_endBoundary_excludesTheExactInstantAndIncludesJustBefore() {
        assertDisplayStatus(NoticeStatus.PUBLISHED, null, NOW, NoticeDisplayStatus.ENDED);
        assertDisplayStatus(
                NoticeStatus.PUBLISHED, null, NOW.plusNanos(1_000), NoticeDisplayStatus.VISIBLE);
    }

    @Test
    void getNotices_deletedNotice_staysDeletedInsideItsPeriod() {
        assertDisplayStatus(
                NoticeStatus.DELETED,
                NOW.minusDays(1),
                NOW.plusDays(1),
                NoticeDisplayStatus.DELETED);
    }

    @Test
    void createNotice_passesFormValuesAndAdminId() {
        NoticeForm form = form("공지 제목", "공지 본문", NOW.plusDays(1), NOW.plusDays(2));

        communityNoticeAdminService.createNotice(form, ADMIN_ID);

        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(communityNoticeMapper).insertNotice(captor.capture());

        Notice saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("공지 제목");
        assertThat(saved.getContent()).isEqualTo("공지 본문");
        assertThat(saved.getStartsAt()).isEqualTo(NOW.plusDays(1));
        assertThat(saved.getEndsAt()).isEqualTo(NOW.plusDays(2));
        assertThat(saved.getCreatedBy()).isEqualTo(ADMIN_ID);
    }

    @Test
    void updateNotice_deletedNotice_isRejectedAndDoesNotWrite() {
        when(communityNoticeMapper.lockNotice(NOTICE_ID))
                .thenReturn(new NoticeLockRow(NOTICE_ID, NoticeStatus.DELETED));

        assertThatThrownBy(() -> communityNoticeAdminService.updateNotice(
                NOTICE_ID, form("제목", "본문", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.INVALID_NOTICE_TRANSITION);

        verify(communityNoticeMapper, never()).updateNotice(any(NoticeUpdateCommand.class));
    }

    @Test
    void updateNotice_missingNotice_isNotFound() {
        when(communityNoticeMapper.lockNotice(NOTICE_ID)).thenReturn(null);

        assertThatThrownBy(() -> communityNoticeAdminService.updateNotice(
                NOTICE_ID, form("제목", "본문", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.NOTICE_NOT_FOUND);
    }

    @Test
    void deleteNotice_alreadyDeleted_isRejectedAndDoesNotWrite() {
        when(communityNoticeMapper.lockNotice(NOTICE_ID))
                .thenReturn(new NoticeLockRow(NOTICE_ID, NoticeStatus.DELETED));

        assertThatThrownBy(() -> communityNoticeAdminService.deleteNotice(NOTICE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.INVALID_NOTICE_TRANSITION);

        verify(communityNoticeMapper, never()).deleteNotice(anyLong());
    }

    @Test
    void deleteNotice_noAffectedRow_isNotSwallowed() {
        when(communityNoticeMapper.lockNotice(NOTICE_ID))
                .thenReturn(new NoticeLockRow(NOTICE_ID, NoticeStatus.PUBLISHED));
        when(communityNoticeMapper.deleteNotice(NOTICE_ID)).thenReturn(0);

        assertThatThrownBy(() -> communityNoticeAdminService.deleteNotice(NOTICE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.INVALID_NOTICE_TRANSITION);
    }

    @Test
    void getNotice_missingNotice_isNotFound() {
        when(communityNoticeMapper.selectAdminNoticeById(NOTICE_ID)).thenReturn(null);

        assertThatThrownBy(() -> communityNoticeAdminService.getNotice(NOTICE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.NOTICE_NOT_FOUND);
    }

    @Test
    void getNotice_deletedNotice_isStillReadableForTheAdminList() {
        when(communityNoticeMapper.selectAdminNoticeById(NOTICE_ID))
                .thenReturn(detailRow(NoticeStatus.DELETED));

        assertThat(communityNoticeAdminService.getNotice(NOTICE_ID).editable()).isFalse();
    }

    @Test
    void getEditableNotice_deletedNotice_isRejectedBeforeTheFormOpens() {
        when(communityNoticeMapper.selectAdminNoticeById(NOTICE_ID))
                .thenReturn(detailRow(NoticeStatus.DELETED));

        assertThatThrownBy(() -> communityNoticeAdminService.getEditableNotice(NOTICE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommunityErrorCode.INVALID_NOTICE_TRANSITION);
    }

    @Test
    void getEditableNotice_publishedNotice_isReturned() {
        when(communityNoticeMapper.selectAdminNoticeById(NOTICE_ID))
                .thenReturn(detailRow(NoticeStatus.PUBLISHED));

        assertThat(communityNoticeAdminService.getEditableNotice(NOTICE_ID).id())
                .isEqualTo(NOTICE_ID);
    }

    private AdminNoticeDetailRow detailRow(NoticeStatus status) {
        return new AdminNoticeDetailRow(
                NOTICE_ID, "제목", "본문", status, null, null, CREATED_AT, CREATED_AT);
    }

    private void assertDisplayStatus(
            NoticeStatus status,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            NoticeDisplayStatus expected) {

        when(communityNoticeMapper.selectAdminNotices(anyInt(), anyInt())).thenReturn(List.of(
                new AdminNoticeListRow(NOTICE_ID, "제목", status, startsAt, endsAt, CREATED_AT)));
        when(communityNoticeMapper.countAdminNotices()).thenReturn(1L);

        List<AdminNoticeListView> notices = communityNoticeAdminService
                .getNotices(new PageRequest(1, PageRequest.DEFAULT_SIZE))
                .getContent();

        assertThat(notices).singleElement()
                .extracting(AdminNoticeListView::displayStatus)
                .isEqualTo(expected);
    }

    private NoticeForm form(
            String title, String content, LocalDateTime startsAt, LocalDateTime endsAt) {

        NoticeForm form = new NoticeForm();
        form.setTitle(title);
        form.setContent(content);
        form.setStartsAt(startsAt);
        form.setEndsAt(endsAt);
        return form;
    }
}
