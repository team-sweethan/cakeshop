package com.cakeshop.domain.community.mapper;

import java.util.List;

import com.cakeshop.domain.community.dto.query.AdminPostDetailRow;
import com.cakeshop.domain.community.dto.query.AdminPostListRow;
import com.cakeshop.domain.community.dto.view.AdminPostSort;
import com.cakeshop.domain.community.dto.query.ReportRow;
import com.cakeshop.domain.community.entity.PostStatus;
import com.cakeshop.domain.community.entity.ReportStatus;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-05
 * 기능 : 커뮤니티 데이터 접근
 * 설명 : CommunityAdminMapper 기능에 필요한 조회와 변경을 수행한다.
 * ******************************
 */
// 구현 클래스가 없는데도 동작하는 이유:
//   @Mapper 인터페이스를 MyBatis 가 스캔해서 실행 시점에 구현체를 대신 만들어 준다.
//   메서드 이름 = src/main/resources/mapper/community/CommunityAdminMapper.xml 의 태그 id.
//
// 고객용 CommunityMapper 와 갈라 둔 쪽이다 — 여기 SQL 은 차단된 글까지 다 보여 준다.
@Mapper
public interface CommunityAdminMapper {

    // 못 찾으면 null. 관리자 화면은 삭제·차단된 글도 열 수 있어야 해서 고객용 조회와 SQL 이 다르다.
    AdminPostDetailRow findPostByIdForAdmin(
            @Param("postId") long postId
    );

    // status 가 null 이면 상태 조건을 걸지 않는다(= 전체 보기).
    List<AdminPostListRow> findPostsForAdmin(
            @Param("status") PostStatus status,
            @Param("sort") AdminPostSort sort,
            @Param("size") int size,
            @Param("offset") int offset
    );

    long countPostsForAdmin(
            @Param("status") PostStatus status
    );

    List<ReportRow> findReportsByPost(
            @Param("postId") long postId
    );

    // 차단·해제·신고 처리는 전부 UPDATE 다. int 반환값 = 바뀐 행 수, 0이면 대상이 없었다는 뜻.
    int blockPost(
            @Param("postId") long postId,
            @Param("reason") String reason,
            @Param("adminId") long adminId
    );

    int unblockPost(
            @Param("postId") long postId
    );

    int closePendingReports(
            @Param("postId") long postId,
            @Param("status") ReportStatus status
    );
}
