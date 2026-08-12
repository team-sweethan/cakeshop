package com.cakeshop.domain.community.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;

import com.cakeshop.domain.community.dto.command.NoticeUpdateCommand;
import com.cakeshop.domain.community.dto.form.NoticeForm;
import com.cakeshop.domain.community.dto.query.AdminNoticeDetailRow;
import com.cakeshop.domain.community.dto.query.AdminNoticeListRow;
import com.cakeshop.domain.community.dto.query.NoticeLockRow;
import com.cakeshop.domain.community.dto.view.AdminNoticeDetailView;
import com.cakeshop.domain.community.dto.view.AdminNoticeListView;
import com.cakeshop.domain.community.entity.Notice;
import com.cakeshop.domain.community.entity.NoticeStatus;
import com.cakeshop.domain.community.error.CommunityErrorCode;
import com.cakeshop.domain.community.mapper.CommunityNoticeMapper;
import com.cakeshop.global.common.paging.PageRequest;
import com.cakeshop.global.common.paging.PageResult;
import com.cakeshop.global.error.BusinessException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-12
 * 기능 : 커뮤니티 비즈니스 로직
 * 설명 : CommunityNoticeAdminService 기능의 권한, 상태, 트랜잭션을 관리한다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
public class CommunityNoticeAdminService {

    private final CommunityNoticeMapper communityNoticeMapper;

    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResult<AdminNoticeListView> getNotices(PageRequest pageRequest) {
        List<AdminNoticeListRow> rows = communityNoticeMapper.selectAdminNotices(
                pageRequest.getSize(),
                pageRequest.getOffset()
        );

        long totalElements = communityNoticeMapper.countAdminNotices();

        LocalDateTime now = LocalDateTime.now(clock);

        List<AdminNoticeListView> notices = rows.stream()
                .map(row -> AdminNoticeListView.of(row, now))
                .toList();

        return new PageResult<>(notices, pageRequest, totalElements);
    }

    @Transactional(readOnly = true)
    public AdminNoticeDetailView getNotice(long noticeId) {
        AdminNoticeDetailRow row = communityNoticeMapper.selectAdminNoticeById(noticeId);

        if (row == null) {
            throw new BusinessException(CommunityErrorCode.NOTICE_NOT_FOUND);
        }

        return AdminNoticeDetailView.of(row, LocalDateTime.now(clock));
    }

    /**
     * 수정 화면이 열어도 되는 공지인지 확인하고 돌려준다.
     *
     * <p>`DELETED`는 종착 상태라 <b>폼 자체가 닫힌다</b>. 조회로 막지 않으면 목록에 링크가 없어도
     * 주소를 직접 친 관리자에게 제목·본문이 채워진 폼이 열리고, 실제 거절은 제출한 뒤에야 일어난다.
     * 게시글의 {@code getEditablePost}와 같은 자리다.</p>
     */
    @Transactional(readOnly = true)
    public AdminNoticeDetailView getEditableNotice(long noticeId) {
        AdminNoticeDetailView notice = getNotice(noticeId);

        if (!notice.editable()) {
            throw new BusinessException(CommunityErrorCode.INVALID_NOTICE_TRANSITION);
        }

        return notice;
    }

    @Transactional
    public void createNotice(NoticeForm form, long adminId) {
        communityNoticeMapper.insertNotice(Notice.create(
                form.getTitle(),
                form.getContent(),
                form.getStartsAt(),
                form.getEndsAt(),
                adminId
        ));
    }

    @Transactional
    public void updateNotice(long noticeId, NoticeForm form) {
        requirePublished(noticeId);

        requireApplied(communityNoticeMapper.updateNotice(new NoticeUpdateCommand(
                noticeId,
                form.getTitle(),
                form.getContent(),
                form.getStartsAt(),
                form.getEndsAt()
        )));
    }

    @Transactional
    public void deleteNotice(long noticeId) {
        requireTransition(noticeId, NoticeStatus.DELETED);

        requireApplied(communityNoticeMapper.deleteNotice(noticeId));
    }

    private void requirePublished(long noticeId) {
        NoticeLockRow notice = lock(noticeId);

        if (notice.status() != NoticeStatus.PUBLISHED) {
            throw new BusinessException(CommunityErrorCode.INVALID_NOTICE_TRANSITION);
        }
    }

    private void requireTransition(long noticeId, NoticeStatus next) {
        NoticeLockRow notice = lock(noticeId);

        if (!notice.status().canTransitionTo(next)) {
            throw new BusinessException(CommunityErrorCode.INVALID_NOTICE_TRANSITION);
        }
    }

    private NoticeLockRow lock(long noticeId) {
        NoticeLockRow notice = communityNoticeMapper.lockNotice(noticeId);

        if (notice == null) {
            throw new BusinessException(CommunityErrorCode.NOTICE_NOT_FOUND);
        }

        return notice;
    }

    private void requireApplied(int affectedRows) {
        if (affectedRows == 0) {
            throw new BusinessException(CommunityErrorCode.INVALID_NOTICE_TRANSITION);
        }
    }
}
