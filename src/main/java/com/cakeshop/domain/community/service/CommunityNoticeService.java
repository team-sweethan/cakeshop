package com.cakeshop.domain.community.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;

import com.cakeshop.domain.community.dto.query.NoticeDetailRow;
import com.cakeshop.domain.community.dto.query.NoticeListRow;
import com.cakeshop.domain.community.dto.view.NoticeDetailView;
import com.cakeshop.domain.community.dto.view.NoticeSectionView;
import com.cakeshop.domain.community.dto.view.NoticeView;
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
 * 설명 : CommunityNoticeService 기능의 권한, 상태, 트랜잭션을 관리한다.
 * ******************************
 */
@Service
@RequiredArgsConstructor
public class CommunityNoticeService {

    /* 커뮤니티 목록 고정 행이 쓰는 건수. 메인은 다른 값을 쓴다(`CommunityHomeQueryService`). */
    private static final int LIST_SECTION_LIMIT = 3;

    private static final int FIRST_PAGE = 1;

    private static final int SECTION_OFFSET = 0;

    private final CommunityNoticeMapper communityNoticeMapper;

    private final Clock clock;

    /**
     * 커뮤니티 목록의 공지 고정 행 영역.
     *
     * <p>1쪽이고 카테고리 필터가 없을 때만 싣는다. 이 조건은 인기글과 같은 이유로 Controller가
     * 아니라 여기 있다 — 화면이 늘 때마다 한 벌씩 늘고, 두 벌이 되는 순간 갈린다.</p>
     */
    @Transactional(readOnly = true)
    public NoticeSectionView getListSection(Long categoryId, PageRequest pageRequest) {
        if (categoryId != null || pageRequest.getPage() != FIRST_PAGE) {
            return NoticeSectionView.empty();
        }

        return readSection(LIST_SECTION_LIMIT);
    }

    @Transactional(readOnly = true)
    public PageResult<NoticeView> getNotices(PageRequest pageRequest) {
        LocalDateTime now = LocalDateTime.now(clock);

        List<NoticeView> notices = communityNoticeMapper.selectVisibleNotices(
                        now, pageRequest.getSize(), pageRequest.getOffset()).stream()
                .map(NoticeView::of)
                .toList();

        return new PageResult<>(
                notices, pageRequest, communityNoticeMapper.countVisibleNotices(now));
    }

    /**
     * 공지 상세. 노출 기간 밖이거나 삭제된 공지는 <b>관리자에게도</b> 404다.
     *
     * <p>403이 아닌 이유는 게시글과 같다(`DOMAIN.md` 4.3) — 403은 그 자리에 글이 있다는 사실을
     * 흘린다. 관리자가 전부 보는 경로는 `/admin/community/notices`다.</p>
     */
    @Transactional(readOnly = true)
    public NoticeDetailView getNotice(long noticeId) {
        NoticeDetailRow row = communityNoticeMapper.selectVisibleNoticeById(
                noticeId, LocalDateTime.now(clock));

        if (row == null) {
            throw new BusinessException(CommunityErrorCode.NOTICE_NOT_FOUND);
        }

        return NoticeDetailView.of(row);
    }

    /** 자리별 건수는 부르는 쪽이 아니라 커뮤니티가 갖는다. 인기글 D2와 같은 이유다. */
    NoticeSectionView readSection(int limit) {
        List<NoticeListRow> rows = communityNoticeMapper.selectVisibleNotices(
                LocalDateTime.now(clock), limit, SECTION_OFFSET);

        if (rows.isEmpty()) {
            return NoticeSectionView.empty();
        }

        return new NoticeSectionView(rows.stream().map(NoticeView::of).toList());
    }
}
