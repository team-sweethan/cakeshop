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
@Mapper
public interface CommunityAdminMapper {

    List<AdminPostListRow> findPostsForAdmin(
            @Param("status") PostStatus status,
            @Param("sort") AdminPostSort sort,
            @Param("size") int size,
            @Param("offset") int offset
    );

    long countPostsForAdmin(
            @Param("status") PostStatus status
    );

    AdminPostDetailRow findPostByIdForAdmin(
            @Param("postId") long postId
    );

    List<ReportRow> findReportsByPost(
            @Param("postId") long postId
    );

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
