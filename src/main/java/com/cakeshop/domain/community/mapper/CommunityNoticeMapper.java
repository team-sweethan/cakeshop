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
// 구현 클래스가 없는데도 동작하는 이유:
//   @Mapper 인터페이스를 MyBatis 가 스캔해서 실행 시점에 구현체를 대신 만들어 준다.
//   메서드 이름 = src/main/resources/mapper/community/CommunityNoticeMapper.xml 의 태그 id.
//
// 같은 공지 테이블을 두 벌로 읽는다.
//   selectVisible*  : 고객용. now 를 받아 게시 기간 안에 든 공지만 고른다.
//   selectAdmin*    : 관리자용. now 가 없다 = 예약·종료된 공지까지 전부 보인다.
@Mapper
public interface CommunityNoticeMapper {

    // 고객용 — now 는 자바가 계산해 넘긴다. DB 의 NOW() 를 쓰지 않아야 테스트가 시각을 고정할 수 있다.
    // 기간이 지났거나 아직 시작 전이면 null 이 온다.
    NoticeDetailRow selectVisibleNoticeById(
            @Param("noticeId") long noticeId,
            @Param("now") LocalDateTime now
    );

    List<NoticeListRow> selectVisibleNotices(
            @Param("now") LocalDateTime now,
            @Param("size") int size,
            @Param("offset") int offset
    );

    long countVisibleNotices(
            @Param("now") LocalDateTime now
    );

    // 관리자용 — 반환 타입이 Admin* 으로 다르다. 노출 기간·상태 같은 관리 칸이 더 붙기 때문이다.
    AdminNoticeDetailRow selectAdminNoticeById(
            @Param("noticeId") long noticeId
    );

    List<AdminNoticeListRow> selectAdminNotices(
            @Param("size") int size,
            @Param("offset") int offset
    );

    // 인자가 하나도 없으면 @Param 도 필요 없다.
    long countAdminNotices();

    // 수정·삭제 직전에 그 행을 잠그고 현재 상태만 읽어 온다.
    NoticeLockRow lockNotice(
            @Param("noticeId") long noticeId
    );

    int insertNotice(Notice notice);

    int updateNotice(NoticeUpdateCommand command);

    int deleteNotice(
            @Param("noticeId") long noticeId
    );
}
