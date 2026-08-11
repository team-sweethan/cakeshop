package com.cakeshop.domain.community.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.cakeshop.domain.community.dto.command.NoticeUpdateCommand;
import com.cakeshop.domain.community.dto.query.AdminNoticeDetailRow;
import com.cakeshop.domain.community.dto.query.AdminNoticeListRow;
import com.cakeshop.domain.community.dto.query.NoticeDetailRow;
import com.cakeshop.domain.community.dto.query.NoticeListRow;
import com.cakeshop.domain.community.dto.query.NoticeLockRow;
import com.cakeshop.domain.community.entity.Notice;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-12
 * 기능 : 커뮤니티 데이터 접근
 * 설명 : CommunityNoticeMapper 기능에 필요한 조회와 변경을 수행한다.
 * ******************************
 */
@Mapper
public interface CommunityNoticeMapper {

    List<NoticeListRow> selectVisibleNotices(
            @Param("now") LocalDateTime now,
            @Param("size") int size,
            @Param("offset") int offset
    );

    long countVisibleNotices(
            @Param("now") LocalDateTime now
    );

    NoticeDetailRow selectVisibleNoticeById(
            @Param("noticeId") long noticeId,
            @Param("now") LocalDateTime now
    );

    List<AdminNoticeListRow> selectAdminNotices(
            @Param("size") int size,
            @Param("offset") int offset
    );

    long countAdminNotices();

    AdminNoticeDetailRow selectAdminNoticeById(
            @Param("noticeId") long noticeId
    );

    NoticeLockRow lockNotice(
            @Param("noticeId") long noticeId
    );

    int insertNotice(Notice notice);

    int updateNotice(NoticeUpdateCommand command);

    int deleteNotice(
            @Param("noticeId") long noticeId
    );
}
