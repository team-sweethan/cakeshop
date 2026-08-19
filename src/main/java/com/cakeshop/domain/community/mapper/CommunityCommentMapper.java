package com.cakeshop.domain.community.mapper;

import java.util.List;

import com.cakeshop.domain.community.dto.query.CommentCountRow;
import com.cakeshop.domain.community.dto.query.CommentRow;
import com.cakeshop.domain.community.dto.query.ReplyCountRow;
import com.cakeshop.domain.community.entity.Comment;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ******************************
 * 작성자 : HyunGyu-Cho
 * 담당자 : 현규
 * 작성일 : 2026-08-19
 * 기능 : 커뮤니티 댓글 데이터 접근
 * 설명 : CommunityCommentMapper 댓글 조회와 변경을 수행한다.
 * ******************************
 */
@Mapper
public interface CommunityCommentMapper {

    List<CommentRow> findRecentRootComments(
            @Param("postId") long postId,
            @Param("limit") int limit
    );

    List<CommentRow> findRepliesByParentId(
            @Param("parentCommentId") long parentCommentId,
            @Param("postId") long postId,
            @Param("limit") int limit
    );

    List<ReplyCountRow> countRepliesByParentIds(
            @Param("parentIds") List<Long> parentIds
    );

    CommentCountRow countComments(
            @Param("postId") long postId
    );

    CommentRow findCommentById(
            @Param("commentId") long commentId
    );

    int insertComment(Comment comment);

    int insertReply(
            @Param("postId") long postId,
            @Param("parentCommentId") long parentCommentId,
            @Param("memberId") long memberId,
            @Param("content") String content
    );

    int deleteComment(
            @Param("commentId") long commentId,
            @Param("postId") long postId,
            @Param("memberId") long memberId
    );
}
